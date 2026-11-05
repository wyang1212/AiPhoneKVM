# AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
import hashlib
import json
import time

import pytest
from fastapi.testclient import TestClient
from app import create_app
from test_portal import FakeBroker, signup, add, ORIGIN


class PairBroker(FakeBroker):
    def __init__(self):
        super().__init__()
        self.proofs = set()
    def pairing_proven(self, d, challenge, since):
        return (d['phone_user'], challenge) in self.proofs
    def remove(self, *args, **kwargs):
        self.calls.append(('remove', args))


@pytest.fixture
def setup(tmp_path):
    broker = PairBroker()
    app = create_app(tmp_path, ORIGIN, broker)
    client = signup(app, 'alice')
    phone = TestClient(app, base_url=ORIGIN, headers={'Origin': ORIGIN})
    return app, broker, client, phone


def begin(client):
    r = client.post('/api/pairings')
    assert r.status_code == 200, r.text
    code = r.json()
    return code, {'token': code['url'].split('token=')[1], 'nonce': 'a' * 43, 'device_id': 'MY_PHONE', 'name': 'M11'}


def enroll(phone, body):
    r = phone.post('/api/enrollment/prepare', json=body)
    assert r.status_code == 200, r.text
    return r.json()


def prove(broker, prepared):
    broker.proofs.add((prepared['connection']['username'], prepared['challenge']))


def test_zero_manual_fields_and_idempotent_finish(setup):
    app, broker, client, phone = setup
    code, body = begin(client)
    assert code['matrix'] and isinstance(code['matrix'][0][0], bool)
    assert 'password' not in code['url'] and '#v=1&token=' in code['url']
    assert phone.post('/api/enrollment/inspect', json=body).json()['account'] == 'alice'
    p = enroll(phone, body)
    assert p == enroll(phone, body)  # Lost response does not mint another identity.
    assert client.get('/api/devices').json()['devices'] == []
    assert phone.post('/api/enrollment/finish', json=body).status_code == 409
    prove(broker, p)
    assert phone.post('/api/enrollment/finish', json=body).json()['ok']
    assert phone.post('/api/enrollment/finish', json=body).json()['ok']
    assert phone.post('/api/enrollment/prepare', json=body).status_code == 410
    assert client.get('/api/pairings/' + code['id']).json()['state'] == 'complete'
    listing = client.get('/api/devices').json()['devices']
    assert len(listing) == 1 and listing[0]['name'] == 'M11'
    assert p['connection']['password'] not in json.dumps(listing)
    with app.state.db() as db:
        dump = '\n'.join(db.iterdump())
    for value in [body['token'], body['nonce'], p['binding_secret'], p['connection']['password']]:
        assert value not in dump


def test_qr_cancel_expire_and_replay(setup):
    app, broker, client, phone = setup
    code, body = begin(client)
    prepared = enroll(phone, body)
    assert phone.post('/api/enrollment/prepare', json={**body, 'nonce': 'b'*43}).status_code == 409
    with app.state.db() as db:
        db.execute('UPDATE pairings SET expires=?', (time.time()-1,))
    assert phone.post('/api/enrollment/finish', json=body).status_code == 410
    assert client.get('/api/pairings/' + code['id']).json()['state'] == 'expired'
    assert any(c[0] == 'remove' for c in broker.calls)
    assert client.get('/api/devices').json()['devices'] == []
    code, body = begin(client)
    client.delete('/api/pairings/' + code['id'])
    assert phone.post('/api/enrollment/inspect', json=body).status_code == 410


def test_existing_m11_requires_phone_proof_and_preserves_until_commit(setup):
    app, broker, client, phone = setup
    old = add(client, 'MY_PHONE').json()['device']
    code, body = begin(client)
    challenge = enroll(phone, body)
    assert challenge['proof_required']
    assert len(broker.calls) == 1  # No new clients and no old credential revocation.
    broker.proofs.add((old['phone_user'], challenge['challenge']))
    prepared = enroll(phone, body)
    assert client.get('/api/devices').json()['devices'][0]['phone_user'] == old['phone_user']
    assert client.post('/api/devices/' + old['id'] + '/rotate', json={'mqtt_password': 'Some-pass-123'}).status_code == 409
    prove(broker, prepared)
    assert phone.post('/api/enrollment/finish', json=body).status_code == 200
    devices = client.get('/api/devices').json()['devices']
    assert len(devices) == 1 and devices[0]['id'] == old['id']
    assert devices[0]['name'] == old['name']
    assert devices[0]['phone_user'] != old['phone_user']
    assert any(c[0] == 'remove' and c[1][0]['phone_user'] == old['phone_user'] for c in broker.calls)
    # Later same-account rebind proves the installation without the old MQTT password.
    code, again = begin(client)
    p = enroll(phone, {**again, 'binding_secret': prepared['binding_secret']})
    assert 'connection' in p


def test_cross_account_and_other_nonce_denied(setup):
    app, broker, client, phone = setup
    add(client, 'MY_PHONE')
    bob = signup(app, 'bob')
    code, body = begin(bob)
    assert phone.post('/api/enrollment/prepare', json=body).status_code == 409
    assert client.get('/api/pairings/' + code['id']).status_code == 404
    assert client.delete('/api/pairings/' + code['id']).status_code == 404
    body['device_id'] = 'NEW_PHONE'
    p = enroll(phone, body)
    assert phone.post('/api/enrollment/recover', json={**body, 'nonce': 'b'*43}).status_code == 409
    assert phone.post('/api/enrollment/recover', json=body).json()['state'] == 'cancelled'
    assert phone.post('/api/enrollment/finish', json=body).status_code == 410


def test_ambiguous_finish_resolved_before_rollback(setup):
    app, broker, client, phone = setup
    code, body = begin(client)
    p = enroll(phone, body)
    prove(broker, p)
    phone.post('/api/enrollment/finish', json=body)
    assert phone.post('/api/enrollment/recover', json=body).json()['state'] == 'complete'
    assert len(client.get('/api/devices').json()['devices']) == 1


def test_five_device_limit_and_csrf(setup):
    app, broker, client, phone = setup
    assert phone.post('/api/pairings').status_code == 401
    for i in range(5):
        assert add(client, 'PHONE' + str(i)).status_code == 200
    code, body = begin(client)
    assert phone.post('/api/enrollment/prepare', json=body).status_code == 400
    client.headers.pop('X-CSRF-Token')
    assert client.post('/api/pairings').status_code == 403


def test_logout_revokes_qr_and_failed_provision_has_durable_cleanup(setup):
    app, broker, client, phone = setup
    code, body = begin(client)
    p = enroll(phone, body)
    assert client.post('/api/logout').status_code == 200
    assert phone.post('/api/enrollment/finish', json=body).status_code == 410
    assert any(c[0] == 'remove' for c in broker.calls)
    client = signup(app, 'owner2')
    code, body = begin(client)
    broker.fail = True
    assert phone.post('/api/enrollment/prepare', json=body).status_code == 503
    with app.state.db() as db:
        row = db.execute('SELECT * FROM pairings WHERE id=?', (code['id'],)).fetchone()
        assert row['state'] == 'cancelled' and row['stage']
        assert db.execute('SELECT COUNT(*) FROM retired_pairing_clients').fetchone()[0] == 1


def test_pending_global_device_id_cannot_spoof_another_accounts_client(setup):
    app, broker, alice, phone = setup
    code, body = begin(alice)
    enroll(phone, body)
    bob = signup(app, 'bob')
    code2, body2 = begin(bob)
    assert phone.post('/api/enrollment/prepare', json=body2).status_code == 409
    assert add(bob, 'MY_PHONE').status_code == 409
    alice.delete('/api/pairings/' + code['id'])
    assert phone.post('/api/enrollment/prepare', json=body2).status_code == 200
