# AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
"""Self-service accounts and device-scoped credentials for AiPhoneKVM."""
import base64
import asyncio
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import threading
import time
import uuid
from contextlib import asynccontextmanager, contextmanager
from pathlib import Path
from urllib.parse import urlparse

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, JSONResponse, Response

from broker import Broker, BrokerError

HASH_LIMIT = threading.BoundedSemaphore(2)
USERNAME = re.compile(r"[a-zA-Z0-9_]{3,32}\Z")
DEVICE_ID = re.compile(r"[a-zA-Z0-9_-]{1,64}\Z")


def password_hash(value):
    salt = secrets.token_bytes(16)
    with HASH_LIMIT:
        key = hashlib.scrypt(value.encode(), salt=salt, n=32768, r=8, p=3, maxmem=67108864)
    return "s1$" + base64.b64encode(salt).decode() + "$" + base64.b64encode(key).decode()


def password_matches(value, encoded):
    try:
        version, salt, expected = encoded.split("$")
        if version != "s1":
            return False
        with HASH_LIMIT:
            actual = hashlib.scrypt(value.encode(), salt=base64.b64decode(salt), n=32768, r=8, p=3,
                                    maxmem=67108864)
        return hmac.compare_digest(actual, base64.b64decode(expected))
    except (ValueError, TypeError):
        return False


def create_app(data_dir=None, origin=None, broker=None):
    folder = Path(data_dir or os.getenv("DATA_DIR", "/data"))
    folder.mkdir(parents=True, exist_ok=True)
    origin = (origin or os.environ["PUBLIC_ORIGIN"]).rstrip("/")
    site = urlparse(origin)
    if (site.scheme != 'https' or not site.hostname or site.username or site.password or site.path or site.query or site.fragment or
            site.netloc.endswith(':') or (site.port is not None and not 1 <= site.port <= 65535)):
        raise ValueError('PUBLIC_ORIGIN must be an HTTPS origin without path or credentials')
    host = '[' + site.hostname + ']' if ':' in site.hostname else site.hostname
    origin = 'https://' + host + (':' + str(site.port) if site.port not in (None, 443) else '')
    public_mqtt = os.getenv('PUBLIC_MQTT_URI', 'ssl://mqtt.example.invalid:8883')
    mqtt_url = urlparse(public_mqtt)
    if (mqtt_url.scheme != 'ssl' or not mqtt_url.hostname or not mqtt_url.port or
            mqtt_url.username or mqtt_url.password or mqtt_url.path or mqtt_url.query or mqtt_url.fragment):
        raise ValueError('PUBLIC_MQTT_URI must be ssl://host:port')
    download_url = os.getenv('APK_DOWNLOAD_URL', 'https://downloads.example.invalid/')
    download = urlparse(download_url)
    if download_url and (download.scheme != 'https' or not download.hostname or download.username or download.password):
        raise ValueError('APK_DOWNLOAD_URL must be an HTTPS URL or empty')
    android_package = os.getenv('ANDROID_PACKAGE', 'vip.wyang.aiphonekvm.debug')
    if not re.fullmatch(r'[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+', android_package):
        raise ValueError('Invalid ANDROID_PACKAGE')
    secure = urlparse(origin).scheme == "https"
    cookie = "__Host-aiphone_session" if secure else "aiphone_session"
    db_path = folder / "portal.sqlite3"
    mutation_lock = threading.RLock()
    bridge = broker or Broker(json.loads(Path(os.environ["BROKER_CONFIG"]).read_text()))

    @contextmanager
    def db():
        conn = sqlite3.connect(db_path, timeout=15)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys=ON")
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    with db() as conn:
        conn.executescript("""
            PRAGMA journal_mode=WAL;
            CREATE TABLE IF NOT EXISTS users(
                id TEXT PRIMARY KEY, username TEXT UNIQUE NOT NULL, password TEXT NOT NULL, created REAL NOT NULL);
            CREATE TABLE IF NOT EXISTS sessions(
                token TEXT PRIMARY KEY, user_id TEXT REFERENCES users(id) ON DELETE CASCADE,
                csrf TEXT NOT NULL, expires REAL NOT NULL);
            CREATE TABLE IF NOT EXISTS devices(
                id TEXT PRIMARY KEY, user_id TEXT REFERENCES users(id), name TEXT NOT NULL,
                device_id TEXT UNIQUE NOT NULL, topic_prefix TEXT UNIQUE NOT NULL,
                phone_user TEXT UNIQUE NOT NULL, controller_user TEXT UNIQUE NOT NULL,
                phone_client_id TEXT NOT NULL, controller_client_id TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1, legacy INTEGER NOT NULL DEFAULT 0, created REAL NOT NULL);
            CREATE TABLE IF NOT EXISTS legacy(
                device_id TEXT PRIMARY KEY, controller_user TEXT NOT NULL, proof TEXT NOT NULL,
                record TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS limits(bucket TEXT PRIMARY KEY, started REAL NOT NULL, count INTEGER NOT NULL);
        """)
    try:
        os.chmod(db_path, 0o600)
    except PermissionError:
        pass

    @asynccontextmanager
    async def lifespan(app):
        bridge.start()
        async def cleanup():
            while True:
                await asyncio.sleep(30)
                try:
                    await asyncio.to_thread(app.state.pairing_sweep)
                except Exception:
                    pass  # Durable outbox retries after broker or SQLite recovery.
        task = asyncio.create_task(cleanup())
        try:
            await asyncio.to_thread(app.state.pairing_sweep)
            yield
        finally:
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass
            bridge.close()

    app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
    app.state.db = db
    app.state.bridge = bridge

    @app.middleware("http")
    async def security(request, call_next):
        if request.method in ("POST", "PUT", "DELETE", "PATCH"):
            if request.headers.get("origin") != origin:
                return JSONResponse({"detail": "请求来源无效，请刷新页面后重试"}, status_code=403)
            try:
                length = int(request.headers.get("content-length", "0"))
            except ValueError:
                length = 999999
            if length > 8192 or length < 0 or request.headers.get("transfer-encoding"):
                return JSONResponse({"detail": "请求过大"}, status_code=413)
        response = await call_next(request)
        response.headers.update({
            "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff",
            "X-Frame-Options": "DENY", "Referrer-Policy": "no-referrer",
            "Content-Security-Policy": "default-src 'self'; script-src 'self'; style-src 'self'; "
                "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
            "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
        })
        if secure:
            response.headers["Strict-Transport-Security"] = "max-age=31536000"
        return response

    @app.exception_handler(BrokerError)
    async def broker_error(request, error):
        return JSONResponse({"detail": str(error)}, status_code=503)

    def ip(request):
        peer = request.client.host if request.client else "unknown"
        if peer in ("127.0.0.1", "::1"):
            return request.headers.get("x-forwarded-for", peer).split(",")[-1].strip()[:80]
        return peer

    def limit(bucket, maximum, seconds):
        with db() as conn:
            conn.execute("BEGIN IMMEDIATE")
            now = time.time()
            conn.execute("DELETE FROM limits WHERE started < ?", (now - 86400,))
            row = conn.execute("SELECT * FROM limits WHERE bucket=?", (bucket,)).fetchone()
            if not row or row["started"] + seconds <= now:
                conn.execute("INSERT OR REPLACE INTO limits VALUES(?,?,1)", (bucket, now))
            elif row["count"] >= maximum:
                raise HTTPException(429, "操作过于频繁，请稍后重试")
            else:
                conn.execute("UPDATE limits SET count=count+1 WHERE bucket=?", (bucket,))

    def session(request, mutate=False):
        token = request.cookies.get(cookie, "")
        if len(token) > 128:
            raise HTTPException(401, "请先登录")
        digest = hashlib.sha256(token.encode()).hexdigest()
        with db() as conn:
            row = conn.execute("SELECT sessions.*, users.username FROM sessions JOIN users ON users.id=user_id "
                               "WHERE token=? AND expires>?", (digest, time.time())).fetchone()
        if not row:
            raise HTTPException(401, "请先登录")
        if mutate and not hmac.compare_digest(request.headers.get("x-csrf-token", ""), row["csrf"]):
            raise HTTPException(403, "登录验证已更新，请刷新页面")
        return dict(row)

    def login_response(user_id, username):
        token, csrf = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
        with db() as conn:
            conn.execute("DELETE FROM sessions WHERE expires<?", (time.time(),))
            conn.execute("INSERT INTO sessions VALUES(?,?,?,?)",
                         (hashlib.sha256(token.encode()).hexdigest(), user_id, csrf, time.time() + 604800))
        response = JSONResponse({"username": username, "csrf": csrf})
        response.set_cookie(cookie, token, max_age=604800, httponly=True, secure=secure,
                            samesite="strict", path="/")
        return response

    def credentials(body):
        username, password = body.get("username"), body.get("password")
        if not isinstance(username, str) or not USERNAME.fullmatch(username.strip()):
            raise HTTPException(400, "用户名需为3–32位字母、数字或下划线")
        if not isinstance(password, str) or not 10 <= len(password) <= 128:
            raise HTTPException(400, "密码需为10–128个字符")
        return username.strip().lower(), password

    def mqtt_password(body):
        value = body.get("mqtt_password")
        if not isinstance(value, str) or not 8 <= len(value) <= 128:
            raise HTTPException(400, "请设置8–128个字符的MQTT密码")
        return {"phone": value, "controller": value}

    def owned(conn, user, ident):
        row = conn.execute("SELECT * FROM devices WHERE id=? AND user_id=?", (ident, user["user_id"])).fetchone()
        if not row:
            raise HTTPException(404, "设备不存在")
        return dict(row)

    def view(device):
        return {key: device[key] for key in ("id", "name", "device_id", "topic_prefix", "phone_user",
                "controller_user", "phone_client_id", "controller_client_id", "enabled", "legacy")} | bridge.status(device)

    def connection(device, passwords):
        return {"uri": public_mqtt, "device_id": device["device_id"],
                "topic_prefix": device["topic_prefix"],
                "phone": {"username": device["phone_user"], "password": passwords["phone"],
                          "client_id": device["phone_client_id"]},
                "controller": {"username": device["controller_user"], "password": passwords["controller"],
                               "client_id": device["controller_client_id"]}}

    @app.get("/api/health")
    def health():
        return {"ok": True, "mqtt": bridge.ready.is_set()}

    @app.post("/api/register")
    def register(request: Request, body: dict):
        limit("register:" + ip(request), 5, 3600)
        username, password = credentials(body)
        encoded = password_hash(password)
        with db() as conn:
            conn.execute("BEGIN IMMEDIATE")
            if conn.execute("SELECT COUNT(*) FROM users").fetchone()[0] >= 10000:
                raise HTTPException(503, "注册名额暂满，请稍后再试")
            ident = uuid.uuid4().hex
            try:
                conn.execute("INSERT INTO users VALUES(?,?,?,?)", (ident, username, encoded, time.time()))
            except sqlite3.IntegrityError:
                raise HTTPException(409, "这个用户名已被使用")
        return login_response(ident, username)

    @app.post("/api/login")
    def login(request: Request, body: dict):
        limit("login-ip:" + ip(request), 30, 900)
        username, password = credentials(body)
        limit("login-user:" + username, 20, 900)
        with db() as conn:
            row = conn.execute("SELECT * FROM users WHERE username=?", (username,)).fetchone()
        # Same expensive operation for an unknown username; no plaintext passwords are persisted.
        encoded = row["password"] if row else app.state.dummy_password
        if not password_matches(password, encoded) or not row:
            raise HTTPException(401, "用户名或密码不正确")
        return login_response(row["id"], username)

    app.state.dummy_password = password_hash(secrets.token_urlsafe(32))

    @app.get("/api/me")
    def me(request: Request):
        user = session(request)
        return {"username": user["username"], "csrf": user["csrf"]}

    @app.post("/api/logout")
    def logout(request: Request):
        user = session(request, True)
        app.state.pairing_cancel_user(user["user_id"])
        with db() as conn:
            conn.execute("DELETE FROM sessions WHERE token=?", (user["token"],))
        response = JSONResponse({"ok": True})
        response.delete_cookie(cookie, path="/", secure=secure, httponly=True, samesite="strict")
        return response

    @app.get("/api/devices")
    def devices(request: Request):
        user = session(request)
        with db() as conn:
            rows = conn.execute("SELECT * FROM devices WHERE user_id=? ORDER BY created DESC", (user["user_id"],)).fetchall()
        return {"devices": [view(dict(row)) for row in rows], "limit": 5, "mqtt": bridge.ready.is_set()}

    @app.post("/api/devices")
    def add_device(request: Request, body: dict):
        user = session(request, True)
        limit("create:" + user["user_id"], 15, 3600)
        name, device_id = body.get("name"), body.get("device_id")
        if not isinstance(name, str) or not 1 <= len(name.strip()) <= 40:
            raise HTTPException(400, "设备名称需为1–40个字符")
        if not isinstance(device_id, str) or not DEVICE_ID.fullmatch(device_id.strip()):
            raise HTTPException(400, "请填写主控端显示的有效设备ID")
        device_id = device_id.strip()
        with mutation_lock, db() as conn:
            conn.execute("BEGIN IMMEDIATE")
            if conn.execute("SELECT COUNT(*) FROM devices WHERE user_id=?", (user["user_id"],)).fetchone()[0] >= 5:
                raise HTTPException(400, "每个账号最多添加5台主控端")
            app.state.pairing_check_device_id(conn, device_id)
            existing = conn.execute("SELECT user_id FROM devices WHERE device_id=?", (device_id,)).fetchone()
            if existing:
                if existing["user_id"] == user["user_id"]:
                    raise HTTPException(409, "这台主控端已在你的设备列表，请关闭添加窗口，点击设备卡片上的“设置 MQTT 密码”")
                raise HTTPException(409, "该设备ID已绑定，请检查ID")
            legacy = conn.execute("SELECT * FROM legacy WHERE device_id=?", (device_id,)).fetchone()
            if legacy:
                proof = body.get("legacy_password", "")
                if not isinstance(proof, str) or not password_matches(proof, legacy["proof"]):
                    raise HTTPException(409, "这台设备已预留，请联系管理员绑定到你的账号")
                device = json.loads(legacy["record"])
                device.update(id=uuid.uuid4().hex, user_id=user["user_id"], name=name.strip(), created=time.time(), enabled=1, legacy=1)
                passwords = None
            else:
                ident = uuid.uuid4().hex
                device = {"id": ident, "user_id": user["user_id"], "name": name.strip(), "device_id": device_id,
                          "topic_prefix": f"aiphonekvm/{user['user_id']}/{ident}/", "phone_user": "phone-" + ident,
                          "controller_user": "remote-" + ident, "phone_client_id": "aiphone-" + device_id,
                          "controller_client_id": "remote-" + ident, "enabled": 1, "legacy": 0, "created": time.time()}
                passwords = mqtt_password(body)
            columns = list(device)
            conn.execute("INSERT INTO devices (" + ",".join(columns) + ") VALUES(" + ",".join("?" for _ in columns) + ")", tuple(device.values()))
            if passwords:
                bridge.provision(device, passwords)
            if legacy:
                conn.execute("DELETE FROM legacy WHERE device_id=?", (device_id,))
        return {"device": view(device), "connection": connection(device, passwords) if passwords else None}

    @app.post("/api/devices/{ident}/enabled")
    def enabled(ident: str, request: Request, body: dict):
        user = session(request, True)
        if type(body.get("enabled")) is not bool:
            raise HTTPException(400, "启用状态无效")
        limit("manage:" + user["user_id"], 60, 600)
        with mutation_lock, db() as conn:
            device = owned(conn, user, ident)
            app.state.pairing_check_free(conn, ident)
            bridge.enabled(device, body["enabled"])
            conn.execute("UPDATE devices SET enabled=? WHERE id=?", (int(body["enabled"]), ident))
        return {"ok": True}

    @app.post("/api/devices/{ident}/rotate")
    def rotate(ident: str, request: Request, body: dict):
        user = session(request, True)
        limit("rotate:" + user["user_id"], 12, 3600)
        with mutation_lock, db() as conn:
            device = owned(conn, user, ident)
            app.state.pairing_check_free(conn, ident)
            passwords = mqtt_password(body)
            try:
                bridge.rotate(device, passwords)
            except BrokerError:
                conn.execute("UPDATE devices SET enabled=0 WHERE id=?", (ident,))
                conn.commit()
                raise
        return {"connection": connection(device, passwords)}

    @app.delete("/api/devices/{ident}")
    def remove(ident: str, request: Request):
        user = session(request, True)
        limit("manage:" + user["user_id"], 60, 600)
        with mutation_lock, db() as conn:
            device = owned(conn, user, ident)
            app.state.pairing_check_free(conn, ident)
            bridge.remove(device)
            conn.execute("DELETE FROM devices WHERE id=?", (ident,))
        return {"ok": True}

    @app.post("/api/devices/{ident}/command")
    def command(ident: str, request: Request, body: dict):
        user = session(request, True)
        limit("command:" + user["user_id"], 40, 60)
        action = body.get("action")
        if action not in ("GET_STATUS", "HOME", "BACK", "VOLUME_UP", "VOLUME_DOWN"):
            raise HTTPException(400, "不支持这个操作")
        with db() as conn:
            device = owned(conn, user, ident)
        if not device["enabled"]:
            raise HTTPException(409, "请先启用设备")
        return {"response": bridge.command(device, action)}

    @app.post("/api/devices/{ident}/name")
    def rename(ident: str, request: Request, body: dict):
        user = session(request, True)
        name = body.get("name")
        if not isinstance(name, str) or not 1 <= len(name.strip()) <= 40:
            raise HTTPException(400, "设备名称需为1–40个字符")
        with mutation_lock, db() as conn:
            owned(conn, user, ident)
            app.state.pairing_check_free(conn, ident)
            conn.execute("UPDATE devices SET name=? WHERE id=?", (name.strip(), ident))
        return {"ok": True}

    @app.post("/api/devices/{ident}/ai/{operation}")
    def ai_task(ident: str, operation: str, request: Request, body: dict):
        user = session(request, True)
        limit("ai:" + user["user_id"], 40, 60)
        with db() as conn:
            device = owned(conn, user, ident)
            app.state.pairing_check_free(conn, ident)
        if operation not in ("start", "stop", "status"):
            raise HTTPException(404, "操作不存在")
        if not device["enabled"]:
            raise HTTPException(409, "请先启用设备")
        if operation == "status":
            return {"response": bridge.command(device, "GET_STATUS")}
        # UUID stays unchanged for a retried request; the phone rejects duplicates and expired starts.
        request_id = body.get("request_id")
        try:
            if not isinstance(request_id, str) or str(uuid.UUID(request_id)) != request_id:
                raise ValueError()
        except (ValueError, AttributeError):
            raise HTTPException(400, "请求编号无效，请刷新页面")
        if operation == "start":
            goal = body.get("goal")
            if not isinstance(goal, str) or not 1 <= len(goal.strip()) <= 2000:
                raise HTTPException(400, "请填写任务目标（最多2000字）")
            params = {"goal": goal.strip(), "expiresAt": int(time.time() * 1000) + 20000}
            action = "AI_START"
        else:
            task_id = body.get("task_id")
            if not isinstance(task_id, str) or not re.fullmatch(r"[0-9a-f-]{36}", task_id):
                raise HTTPException(400, "请先刷新任务状态再停止")
            params, action = {"taskId": task_id}, "AI_STOP"
        return {"response": bridge.command(device, action, params, request_id)}

    from pairing import install_pairing
    install_pairing(app, db, folder, origin, bridge, mutation_lock, session, limit, ip, view, public_mqtt)
    static = Path(__file__).parent / "static"

    @app.get("/")
    def index():
        return FileResponse(static / "index.html")

    @app.get("/pair")
    def pair_landing():
        return FileResponse(static / "pair.html")

    @app.get("/.well-known/assetlinks.json")
    def app_links():
        # Public signing fingerprints, never private signing material.
        path = static / "assetlinks.json"
        if not path.exists():
            raise HTTPException(404)
        return FileResponse(path, media_type="application/json")

    @app.get("/assets/config.js")
    def public_config():
        config = {'mqtt_uri': public_mqtt, 'download_url': download_url, 'android_package': android_package}
        script = 'window.AIPHONE_CONFIG = ' + json.dumps(config).replace('<', '\\u003c') + ';'
        return Response(script, media_type='application/javascript', headers={'Cache-Control': 'no-store'})

    @app.get("/assets/{name}")
    def asset(name: str):
        if name not in ("app.js", "style.css", "pair.js"):
            raise HTTPException(404)
        return FileResponse(static / name)

    return app


if os.getenv("PUBLIC_ORIGIN"):
    app = create_app()
