# AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
"""Short-lived, device-confirmed enrollment. No MQTT plaintext is stored in SQLite."""
import base64
import hashlib
import hmac
import json
import os
import re
import secrets
import time
import uuid

import qrcode
from fastapi import HTTPException, Request
from broker import BrokerError


def install_pairing(app, db, folder, origin, bridge, lock, session, limit, ip, view, public_mqtt):
    key_file = folder / 'enrollment.key'
    try:
        fd = os.open(key_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'wb') as stream:
            stream.write(secrets.token_bytes(32))
            stream.flush()
            os.fsync(stream.fileno())
    except FileExistsError:
        pass
    key = key_file.read_bytes()
    if len(key) != 32:
        raise RuntimeError('Invalid enrollment key')
    with db() as conn:
        conn.executescript('''
        CREATE TABLE IF NOT EXISTS pairings(
          id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          token_hash TEXT UNIQUE NOT NULL, expires REAL NOT NULL, created REAL NOT NULL,
          state TEXT NOT NULL, nonce_hash TEXT, stage TEXT, previous TEXT, device_id TEXT);
        CREATE TABLE IF NOT EXISTS device_bindings(
          id TEXT PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE, secret_hash TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS retired_pairing_clients(id TEXT PRIMARY KEY, record TEXT NOT NULL);
        ''')

    def digest(value):
        return hashlib.sha256(value.encode()).hexdigest()

    def secret(row, purpose):
        return base64.urlsafe_b64encode(hmac.digest(key,
            (row['id'] + ':' + (row['nonce_hash'] or '') + ':' + purpose).encode(), 'sha256')).decode().rstrip('=')

    def retire(conn, record):
        conn.execute('INSERT OR REPLACE INTO retired_pairing_clients VALUES(?,?)',
                     (record['phone_user'], json.dumps(record)))

    def expire(conn, row, state='expired'):
        if row['stage']:
            retire(conn, json.loads(row['stage']))
        conn.execute('UPDATE pairings SET state=? WHERE id=?', (state, row['id']))

    def sweep():
        with lock, db() as conn:
            for row in conn.execute("SELECT * FROM pairings WHERE expires<? AND state IN ('waiting','scanned','provisioning','configuring')", (time.time(),)).fetchall():
                expire(conn, row)
            conn.execute("DELETE FROM pairings WHERE expires<? AND state IN ('expired','cancelled')", (time.time() - 86400,))
            for row in conn.execute('SELECT * FROM retired_pairing_clients LIMIT 8').fetchall():
                if not bridge.ready.is_set():
                    break
                try:
                    bridge.remove(json.loads(row['record']), missing_ok=True)
                except BrokerError:
                    break  # Retry durably; do not stall every HTTP request during a broker outage.
                conn.execute('DELETE FROM retired_pairing_clients WHERE id=?', (row['id'],))

    app.state.pairing_sweep = sweep

    def cancel_user(user_id):
        with lock, db() as conn:
            for row in conn.execute("SELECT * FROM pairings WHERE user_id=? AND state IN ('waiting','scanned','provisioning','configuring')", (user_id,)).fetchall():
                expire(conn, row, 'cancelled')
        sweep()

    app.state.pairing_cancel_user = cancel_user

    def check_free(conn, ident):
        row = conn.execute("SELECT 1 FROM pairings WHERE state IN ('provisioning','configuring') AND expires>? AND json_extract(previous,'$.id')=?", (time.time(), ident)).fetchone()
        if row:
            raise HTTPException(409, '手机正在扫码配置，请先完成或取消配对')

    app.state.pairing_check_free = check_free

    def check_device_id(conn, device_id, pairing_id=''):
        pending = conn.execute("SELECT 1 FROM pairings WHERE device_id=? AND id<>? AND state IN ('provisioning','configuring') AND expires>?",
                               (device_id, pairing_id, time.time())).fetchone()
        if pending:
            raise HTTPException(409, '该手机已有待完成配对，请先完成或取消')
        retired = conn.execute("SELECT 1 FROM retired_pairing_clients WHERE json_extract(record,'$.device_id')=?", (device_id,)).fetchone()
        if retired:
            raise HTTPException(503, '旧连接正在清理，请稍后重新扫码')

    app.state.pairing_check_device_id = check_device_id

    def token_row(conn, body, completed=False):
        token = body.get('token', '')
        if not isinstance(token, str) or not re.fullmatch(r'[A-Za-z0-9_-]{43}', token):
            raise HTTPException(410, '二维码已失效，请在网站重新生成')
        row = conn.execute('SELECT * FROM pairings WHERE token_hash=?', (digest(token),)).fetchone()
        if not row:
            raise HTTPException(410, '二维码已失效，请在网站重新生成')
        row = dict(row)
        if completed and row['state'] == 'complete':
            return row
        if row['expires'] <= time.time() or row['state'] in ('cancelled', 'expired', 'complete'):
            raise HTTPException(410, '二维码已过期或已使用，请在网站重新生成')
        return row

    def nonce_check(row, body):
        nonce = body.get('nonce', '')
        if not isinstance(nonce, str) or not re.fullmatch(r'[A-Za-z0-9_-]{32,100}', nonce):
            raise HTTPException(400, '手机请求标识无效')
        hashed = digest(nonce)
        if row['nonce_hash'] and not hmac.compare_digest(row['nonce_hash'], hashed):
            raise HTTPException(409, '本二维码已由另一台手机领取，请重新生成')
        return hashed

    def response(row):
        stage = json.loads(row['stage'])
        return {'state': row['state'], 'challenge': secret(row, 'proof'), 'binding_secret': secret(row, 'binding'),
                'connection': {'uri': public_mqtt, 'device_id': stage['device_id'],
                   'topic_prefix': stage['topic_prefix'], 'username': stage['phone_user'],
                   'password': secret(row, 'phone'), 'client_id': stage['phone_client_id']}}

    @app.post('/api/pairings')
    def create(request: Request):
        user = session(request, True)
        limit('pair-create:' + user['user_id'], 30, 3600)
        sweep()
        token, ident, now = secrets.token_urlsafe(32), uuid.uuid4().hex, time.time()
        with lock, db() as conn:
            for row in conn.execute("SELECT * FROM pairings WHERE user_id=? AND state IN ('waiting','scanned','provisioning','configuring')", (user['user_id'],)).fetchall():
                expire(conn, row, 'cancelled')
            conn.execute('INSERT INTO pairings(id,user_id,token_hash,expires,created,state) VALUES(?,?,?,?,?,?)',
                (ident, user['user_id'], digest(token), now + 300, now, 'waiting'))
        url = origin + '/pair#v=1&token=' + token
        qr = qrcode.QRCode(border=4, error_correction=qrcode.constants.ERROR_CORRECT_M)
        qr.add_data(url)
        qr.make(fit=True)
        return {'id': ident, 'url': url, 'expires': now + 300, 'matrix': qr.get_matrix()}

    @app.get('/api/pairings/{ident}')
    def status(ident: str, request: Request):
        user = session(request)
        sweep()
        with db() as conn:
            row = conn.execute('SELECT * FROM pairings WHERE id=? AND user_id=?', (ident, user['user_id'])).fetchone()
        if not row:
            raise HTTPException(404, '配对请求不存在')
        return {'state': row['state'], 'expires': row['expires'], 'device_id': row['device_id'] if row['state'] == 'complete' else None}

    @app.delete('/api/pairings/{ident}')
    def cancel(ident: str, request: Request):
        user = session(request, True)
        with lock, db() as conn:
            row = conn.execute('SELECT * FROM pairings WHERE id=? AND user_id=?', (ident, user['user_id'])).fetchone()
            if not row:
                raise HTTPException(404, '配对请求不存在')
            if row['state'] != 'complete':
                expire(conn, row, 'cancelled')
        sweep()
        return {'ok': True}

    @app.post('/api/enrollment/inspect')
    def inspect(request: Request, body: dict):
        limit('pair-inspect:' + ip(request), 100, 600)
        with lock, db() as conn:
            row = token_row(conn, body)
            username = conn.execute('SELECT username FROM users WHERE id=?', (row['user_id'],)).fetchone()[0]
            if row['state'] == 'waiting':
                conn.execute("UPDATE pairings SET state='scanned' WHERE id=?", (row['id'],))
        return {'version': 1, 'account': username[:3] + '****' + username[-4:] if username.isdigit() and len(username) > 7 else username, 'expires': row['expires']}

    @app.post('/api/enrollment/prepare')
    def prepare(request: Request, body: dict):
        limit('pair-prepare:' + ip(request), 80, 600)
        with lock, db() as conn:
            row = token_row(conn, body)
            nonce_hash = nonce_check(row, body)
            device_id = body.get('device_id', '')
            if not isinstance(device_id, str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,64}', device_id):
                raise HTTPException(400, '手机设备标识无效')
            if row['stage']:
                if row['state'] != 'configuring':
                    raise HTTPException(409, '连接配置尚未完成，请在手机恢复上次配对')
                if row['device_id'] != device_id:
                    raise HTTPException(409, '本二维码已绑定另一设备请求')
                return response(row)
            check_device_id(conn, device_id, row['id'])
            existing = conn.execute('SELECT * FROM devices WHERE device_id=?', (device_id,)).fetchone()
            row['nonce_hash'] = nonce_hash
            if existing:
                existing = dict(existing)
                if existing['user_id'] != row['user_id']:
                    raise HTTPException(409, '该手机已绑定其他账号，请先由原账号解除绑定')
                if not existing['enabled']:
                    raise HTTPException(409, '该设备已停用，请先在网站启用')
                bound = conn.execute('SELECT secret_hash FROM device_bindings WHERE id=?', (existing['id'],)).fetchone()
                supplied = body.get('binding_secret', '')
                valid = isinstance(supplied, str) and len(supplied) <= 128 and bound and hmac.compare_digest(bound[0], digest(supplied))
                if not valid and not bridge.pairing_proven(existing, secret(row, 'migration'), row['created']):
                    # The old phone can prove ownership by publishing this challenge using its existing role.
                    # Do not reserve the QR until proof succeeds, preventing an ID guess from locking it.
                    return {'proof_required': True, 'challenge': secret(row, 'migration')}
                check_free(conn, existing['id'])
            else:
                if conn.execute('SELECT 1 FROM legacy WHERE device_id=?', (device_id,)).fetchone():
                    raise HTTPException(409, '该设备已预留，请先由原账号完成归属确认')
                count = conn.execute('SELECT COUNT(*) FROM devices WHERE user_id=?', (row['user_id'],)).fetchone()[0]
                pending = conn.execute("SELECT COUNT(*) FROM pairings WHERE user_id=? AND state IN ('provisioning','configuring') AND previous IS NULL", (row['user_id'],)).fetchone()[0]
                if count + pending >= 5:
                    raise HTTPException(400, '每个账号最多添加5台主控端')
            ident = existing['id'] if existing else uuid.uuid4().hex
            name = str(body.get('name') or 'AiPhoneKVM')[:40].strip() or 'AiPhoneKVM'
            stage = {'id': ident, 'user_id': row['user_id'], 'name': existing['name'] if existing else name,
                     'device_id': device_id, 'topic_prefix': f"aiphonekvm/{row['user_id']}/{row['id']}/",
                     'phone_user': 'phone-' + row['id'], 'controller_user': 'remote-' + row['id'],
                     'phone_client_id': 'aiphone-' + device_id, 'controller_client_id': 'remote-' + row['id'],
                     'enabled': 1, 'legacy': 0, 'created': existing['created'] if existing else time.time()}
            # No device row is changed until the phone persists and proves the staged connection.
            row.update(stage=json.dumps(stage), device_id=device_id, previous=json.dumps(existing) if existing else None, state='provisioning')
            conn.execute('UPDATE pairings SET state=?,nonce_hash=?,stage=?,previous=?,device_id=? WHERE id=?',
                         (row['state'], nonce_hash, row['stage'], row['previous'], device_id, row['id']))
            # Journal before the external mutation: a crash can never leave untracked broker credentials.
            conn.commit()
            try:
                bridge.provision(stage, {'phone': secret(row, 'phone'), 'controller': secret(row, 'controller')})
            except BrokerError:
                expire(conn, row, 'cancelled')
                conn.commit()
                raise
            row['state'] = 'configuring'
            conn.execute("UPDATE pairings SET state='configuring' WHERE id=?", (row['id'],))
        return response(row)

    @app.post('/api/enrollment/finish')
    def finish(request: Request, body: dict):
        limit('pair-finish:' + ip(request), 120, 600)
        with lock, db() as conn:
            row = token_row(conn, body, completed=True)
            nonce_check(row, body)
            if row['state'] == 'complete':
                return {'ok': True, 'state': 'complete'}
            if row['state'] != 'configuring' or not row['stage']:
                raise HTTPException(409, '请先在手机确认配对')
            stage = json.loads(row['stage'])
            if not bridge.pairing_proven(stage, secret(row, 'proof'), row['created']):
                raise HTTPException(409, '尚未验证手机MQTT连接，请在手机重试')
            existing = conn.execute('SELECT * FROM devices WHERE device_id=?', (row['device_id'],)).fetchone()
            previous = json.loads(row['previous']) if row['previous'] else None
            if previous:
                if not existing or existing['user_id'] != row['user_id'] or existing['phone_user'] != previous['phone_user']:
                    raise HTTPException(409, '设备绑定已变化，请取消本次配对后重试')
                columns = [c for c in stage if c != 'id']
                conn.execute('UPDATE devices SET ' + ','.join(c + '=?' for c in columns) + ' WHERE id=?',
                             tuple(stage[c] for c in columns) + (stage['id'],))
                retire(conn, previous)
            else:
                if existing:
                    raise HTTPException(409, '设备已被绑定，请重新生成二维码')
                columns = list(stage)
                conn.execute('INSERT INTO devices(' + ','.join(columns) + ') VALUES(' + ','.join('?' for _ in columns) + ')', tuple(stage.values()))
            conn.execute('INSERT OR REPLACE INTO device_bindings VALUES(?,?)', (stage['id'], digest(secret(row, 'binding'))))
            conn.execute("UPDATE pairings SET state='complete' WHERE id=?", (row['id'],))
        sweep()
        return {'ok': True, 'state': 'complete'}

    @app.post('/api/enrollment/recover')
    def recover(request: Request, body: dict):
        """Resolve an ambiguous finish before restoring the phone's old configuration."""
        limit('pair-recover:' + ip(request), 120, 600)
        token = body.get('token', '')
        if not isinstance(token, str) or len(token) > 128:
            raise HTTPException(400, '配对标识无效')
        with lock, db() as conn:
            row = conn.execute('SELECT * FROM pairings WHERE token_hash=?', (digest(token),)).fetchone()
            if not row:
                # Completed receipts are retained, so a missing token never hides a successful finish.
                return {'state': 'cancelled'}
            nonce_check(dict(row), body)
            if row['state'] == 'complete':
                return {'state': 'complete'}
            expire(conn, row, 'cancelled')
        sweep()
        return {'state': 'cancelled'}
