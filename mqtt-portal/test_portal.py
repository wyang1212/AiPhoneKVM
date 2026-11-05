# AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
import json
import threading
import time

import pytest
from fastapi.testclient import TestClient

from app import create_app, password_hash
from broker import BrokerError, device_roles

ORIGIN = "https://portal.test"


class FakeBroker:
    def __init__(self):
        self.ready = threading.Event()
        self.ready.set()
        self.calls = []
        self.fail = False

    def start(self): pass
    def close(self): pass
    def status(self, device): return {"online": False, "bluetooth_connected": False, "target": "", "last_seen": None}
    def provision(self, *args):
        self.calls.append(("provision", args))
        if self.fail: raise BrokerError("test unavailable")
    def enabled(self, *args): self.calls.append(("enabled", args))
    def rotate(self, *args): self.calls.append(("rotate", args))
    def remove(self, *args): self.calls.append(("remove", args))
    def command(self, *args): self.calls.append(("command", args)); return {"status": "SUCCESS"}


@pytest.fixture
def setup(tmp_path):
    broker = FakeBroker()
    app = create_app(tmp_path, ORIGIN, broker)
    return app, broker


def signup(app, name):
    client = TestClient(app, base_url=ORIGIN)
    client.headers["Origin"] = ORIGIN
    response = client.post("/api/register", json={"username": name, "password": "Test-passphrase-123"})
    assert response.status_code == 200
    client.headers["X-CSRF-Token"] = response.json()["csrf"]
    return client


def add(client, device_id="TEST_DEVICE", **extra):
    return client.post("/api/devices", json={"name": "我的手机", "device_id": device_id, "mqtt_password": "Chosen-MQTT-123", **extra})


def test_registration_hashes_and_secure_sessions(setup):
    app, broker = setup
    client = signup(app, "Alice")
    assert client.get("/api/me").json()["username"] == "alice"
    cookie = next(iter(client.cookies.jar))
    assert cookie.secure and cookie.has_nonstandard_attr("HttpOnly")
    with app.state.db() as db:
        user = db.execute("SELECT * FROM users").fetchone()
        stored = db.execute("SELECT * FROM sessions").fetchone()
    assert user["password"].startswith("s1$") and "Test-passphrase" not in user["password"]
    assert cookie.value != stored["token"]
    assert client.post("/api/register", json={"username": "ALICE", "password": "another-password"}).status_code == 409


def test_csrf_and_origin(setup):
    app, broker = setup
    client = signup(app, "alice")
    client.headers.pop("X-CSRF-Token")
    assert add(client).status_code == 403
    client.headers["Origin"] = "https://attacker.test"
    assert client.post("/api/login", json={"username": "alice", "password": "Test-passphrase-123"}).status_code == 403
    client.headers["Origin"] = ORIGIN
    assert client.post("/api/login", content="x" * 9000).status_code == 413
    assert not broker.calls


def test_tenant_boundary_for_all_mutations(setup):
    app, broker = setup
    alice, bob = signup(app, "alice"), signup(app, "bob")
    created = add(alice).json()
    ident = created["device"]["id"]
    assert created["connection"]["phone"]["password"] == created["connection"]["controller"]["password"] == "Chosen-MQTT-123"
    assert bob.get("/api/devices").json()["devices"] == []
    before = len(broker.calls)
    for path, body in [("enabled", {"enabled": False}), ("rotate", {}), ("command", {"action": "HOME"})]:
        assert bob.post(f"/api/devices/{ident}/{path}", json=body).status_code == 404
    assert bob.delete(f"/api/devices/{ident}").status_code == 404
    assert len(broker.calls) == before
    listing = alice.get("/api/devices").json()
    assert "password" not in json.dumps(listing)
    assert created["connection"]["phone"]["password"] not in json.dumps(listing)


def test_device_collision_validation_and_quota(setup):
    app, broker = setup
    alice, bob = signup(app, "alice"), signup(app, "bob")
    assert add(alice).status_code == 200
    assert add(bob).status_code == 409
    assert add(bob, "x/#").status_code == 400
    for i in range(4): assert add(alice, f"TEST_{i}").status_code == 200
    assert add(alice, "TOO_MANY").status_code == 400


def test_broker_failure_rolls_back_device(setup):
    app, broker = setup
    alice = signup(app, "alice")
    broker.fail = True
    assert add(alice).status_code == 503
    assert alice.get("/api/devices").json()["devices"] == []
    broker.fail = False
    assert add(alice).status_code == 200


def test_legacy_requires_existing_password_and_preserves_connection(setup):
    app, broker = setup
    alice = signup(app, "alice")
    record = {"device_id": "LEGACY", "topic_prefix": "bluehidflow/", "phone_user": "old-phone", "controller_user": "old-remote",
              "phone_client_id": "aiphone-LEGACY", "controller_client_id": ""}
    with app.state.db() as db:
        db.execute("INSERT INTO legacy VALUES(?,?,?,?)", ("LEGACY", "old-remote", password_hash("Existing-mqtt-password"), json.dumps(record)))
    assert add(alice, "LEGACY").status_code == 409
    assert add(alice, "LEGACY", legacy_password="wrong").status_code == 409
    response = add(alice, "LEGACY", legacy_password="Existing-mqtt-password")
    assert response.status_code == 200 and response.json()["connection"] is None
    assert response.json()["device"]["topic_prefix"] == "bluehidflow/"
    assert broker.calls == []


def test_logout_and_session_expiration(setup):
    app, broker = setup
    alice = signup(app, "alice")
    assert alice.post("/api/logout", json={}).status_code == 200
    assert alice.get("/api/me").status_code == 401
    response = alice.post("/api/login", json={"username": "alice", "password": "Test-passphrase-123"})
    assert response.status_code == 200
    with app.state.db() as db: db.execute("UPDATE sessions SET expires=?", (time.time() - 1,))
    assert alice.get("/api/devices").status_code == 401


def test_web_commands_are_bounded(setup):
    app, broker = setup
    alice = signup(app, "alice")
    ident = add(alice).json()["device"]["id"]
    assert alice.post(f"/api/devices/{ident}/command", json={"action": "$CONTROL"}).status_code == 400
    assert alice.post(f"/api/devices/{ident}/enabled", json={"enabled": "false"}).status_code == 400
    assert alice.post(f"/api/devices/{ident}/enabled", json={"enabled": False}).status_code == 200


def test_generated_acl_has_exact_topics_and_no_wildcards():
    d = {"topic_prefix": "aiphonekvm/owner/device/", "device_id": "ID", "phone_user": "p", "controller_user": "c"}
    phone, controller = device_roles(d)
    assert all("#" not in acl["topic"] and "+" not in acl["topic"] for role in (phone, controller) for acl in role["acls"])
    assert {acl["topic"] for acl in phone["acls"] if acl["acltype"] == "publishClientSend"} == {"aiphonekvm/owner/device/" + kind + "/ID" for kind in ("response", "status", "log")}
    assert [acl["topic"] for acl in controller["acls"] if acl["acltype"] == "publishClientSend"] == ["aiphonekvm/owner/device/command/ID"]


def test_self_selected_mqtt_password_validation_and_ownership(setup):
    app, broker = setup
    alice, bob = signup(app, "alice"), signup(app, "bob")
    assert add(alice, mqtt_password="short").status_code == 400
    created = add(alice, mqtt_password="Remember-me-42").json()
    assert created["connection"]["phone"]["password"] == "Remember-me-42"
    ident = created["device"]["id"]
    assert alice.post(f"/api/devices/{ident}/rotate", json={}).status_code == 400
    assert alice.post(f"/api/devices/{ident}/rotate", json={"mqtt_password": "short"}).status_code == 400
    count = len(broker.calls)
    assert bob.post(f"/api/devices/{ident}/rotate", json={"mqtt_password": "Another-pass-42"}).status_code == 404
    assert len(broker.calls) == count
    response = alice.post(f"/api/devices/{ident}/rotate", json={"mqtt_password": "My-new-password"})
    assert response.status_code == 200
    assert response.json()["connection"]["phone"]["password"] == "My-new-password"
    assert response.json()["connection"]["controller"]["password"] == "My-new-password"
    assert broker.calls[-1][0] == "rotate" and broker.calls[-1][1][1]["phone"] == "My-new-password"
    assert "My-new-password" not in json.dumps(alice.get('/api/devices').json())
