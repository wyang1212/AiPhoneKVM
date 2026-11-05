# AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
import json
import time
import uuid
from types import SimpleNamespace

from broker import Broker
from test_portal import setup, signup, add


def request(**extra):
    return {"request_id": str(uuid.uuid4()), **extra}


def test_ai_api_ownership_csrf_and_validation(setup):
    app, broker = setup
    alice, bob = signup(app, "alice"), signup(app, "bob")
    ident = add(alice).json()["device"]["id"]
    root = f"/api/devices/{ident}/ai"
    before = len(broker.calls)
    for op in ("start", "stop", "status"):
        assert bob.post(f"{root}/{op}", json=request(goal="查看版本")).status_code == 404
    assert len(broker.calls) == before
    for goal in ("", " ", 1, "x" * 2001):
        assert alice.post(f"{root}/start", json=request(goal=goal)).status_code == 400
    assert alice.post(f"{root}/start", json={"goal": "目标"}).status_code == 400
    assert alice.post(f"{root}/stop", json=request(task_id="wrong")).status_code == 400
    alice.headers.pop("X-CSRF-Token")
    assert alice.post(f"{root}/start", json=request(goal="目标")).status_code == 403
    assert len(broker.calls) == before


def test_start_and_stop_have_stable_ids_deadline_and_target(setup):
    app, broker = setup
    alice = signup(app, "alice")
    ident = add(alice).json()["device"]["id"]
    root = f"/api/devices/{ident}/ai"
    body = request(goal="  查看系统版本  ")
    assert alice.post(f"{root}/start", json=body).status_code == 200
    _, args = broker.calls[-1]
    assert args[0]["id"] == ident and args[1] == "AI_START" and args[3] == body["request_id"]
    assert args[2]["goal"] == "查看系统版本"
    assert 18000 < args[2]["expiresAt"] - time.time() * 1000 <= 20000
    task = str(uuid.uuid4())
    assert alice.post(f"{root}/stop", json=request(task_id=task)).status_code == 200
    assert broker.calls[-1][1][1:3] == ("AI_STOP", {"taskId": task})
    alice.post(f"/api/devices/{ident}/enabled", json={"enabled": False})
    before = len(broker.calls)
    assert alice.post(f"{root}/start", json=body).status_code == 409
    assert len(broker.calls) == before


def test_broker_task_view_is_bounded_and_ignores_offline_retained_status():
    broker = Broker({"username": "test", "password": "test", "port": 18885})
    device = {"topic_prefix": "aiphonekvm/test/", "device_id": "test", "enabled": 1}
    data = {"running": True, "status": "connected", "timestamp": time.time() * 1000,
            "remoteAi": 1, "modelConfigured": True, "bluetoothConnected": True,
            "aiTask": {"taskId": str(uuid.uuid4()), "status": "running", "active": True,
                       "goal": "x" * 2500, "steps": "malformed", "summary": "done", "apiKey": "private"}}
    message = SimpleNamespace(topic="aiphonekvm/test/status/test", payload=json.dumps(data).encode())
    broker._message(None, None, message)
    status = broker.status(device)
    assert status["remote_ai"] and status["model_configured"]
    assert len(status["ai_task"]["goal"]) == 2000 and status["ai_task"]["steps"] == 0
    assert "private" not in json.dumps(status)
    data["timestamp"] -= 100000
    message.payload = json.dumps(data).encode(); broker._message(None, None, message)
    assert broker.status(device)["ai_task"] is None
    assert not broker.status(device)["remote_ai"]


def test_broker_command_preserves_explicit_request_id_and_goal():
    broker = Broker({"username": "test", "password": "test", "port": 18885})
    calls = []
    broker._request = lambda *args: calls.append(args) or [{"status": "SUCCESS"}]
    ident = str(uuid.uuid4())
    broker.command({"topic_prefix": "aiphonekvm/test/", "device_id": "ID"}, "AI_START", {"goal": "查看版本"}, ident)
    key, topic, payload, _, _ = calls[0]
    assert key == ("aiphonekvm/test/response/ID", ident)
    assert topic == "aiphonekvm/test/command/ID" and payload["id"] == ident
    assert payload["params"] == {"goal": "查看版本"}
