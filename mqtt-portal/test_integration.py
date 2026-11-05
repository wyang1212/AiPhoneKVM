# AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
"""Run only against the isolated broker named in PORTAL_TEST_ADMIN."""
import json
import os
import threading
import time
import uuid
from pathlib import Path

import paho.mqtt.client as mqtt
import pytest
from fastapi.testclient import TestClient

from app import create_app
from broker import Broker


@pytest.mark.skipif(not os.getenv("PORTAL_TEST_ADMIN"), reason="requires isolated dynamic-security broker")
def test_real_broker_registration_isolation_rotation_and_revoke(tmp_path):
    config = json.loads(Path(os.environ["PORTAL_TEST_ADMIN"]).read_text())
    assert config["port"] == 18885, "Never run integration tests against the production management port"
    bridge = Broker(config)
    origin = "https://portal.test"
    app = create_app(tmp_path, origin, bridge)
    checks = {}
    clients = []

    def connect(credentials, wrong_id=False, wrong_password=False):
        event, state = threading.Event(), {}
        ident = "wrong-" + uuid.uuid4().hex if wrong_id else credentials["client_id"]
        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=ident, protocol=mqtt.MQTTv5)
        client.username_pw_set(credentials["username"], "wrong-password" if wrong_password else credentials["password"])
        def connected(c, userdata, flags, reason, properties): state["reason"] = reason; event.set()
        client.on_connect = connected
        client.connect("127.0.0.1", config["port"], 20)
        client.loop_start()
        clients.append(client)
        assert event.wait(6)
        return client, state["reason"]

    def publish(client, topic, payload):
        event, state = threading.Event(), {}
        def published(c, userdata, mid, reason, properties): state["reason"] = reason; event.set()
        client.on_publish = published
        client.publish(topic, json.dumps(payload), qos=1)
        assert event.wait(6)
        return state["reason"]

    with TestClient(app, base_url=origin) as alice:
        bob = TestClient(app, base_url=origin)
        for client, name in ((alice, "alice_" + uuid.uuid4().hex[:5]), (bob, "bob_" + uuid.uuid4().hex[:5])):
            client.headers["Origin"] = origin
            response = client.post("/api/register", json={"username": name, "password": "dummy_Integration-password-123"})
            assert response.status_code == 200
            client.headers["X-CSRF-Token"] = response.json()["csrf"]
        assert bridge.ready.wait(6)
        records = []
        try:
            for client in (alice, bob):
                response = client.post("/api/devices", json={"name": "隔离测试设备", "device_id": "TEST_" + uuid.uuid4().hex[:12], "mqtt_password": "dummy_Chosen-test-MQTT-123"})
                assert response.status_code == 200, response.text
                records.append(response.json())
            a, b = records
            aphone, accepted = connect(a["connection"]["phone"])
            assert not accepted.is_failure
            aremote, accepted = connect(a["connection"]["controller"])
            assert not accepted.is_failure
            bremote, accepted = connect(b["connection"]["controller"])
            assert not accepted.is_failure
            checks["generated_credentials_authenticate"] = True
            atopic = a["connection"]["topic_prefix"]
            aid = a["device"]["device_id"]
            status = {"device_id": aid, "running": True, "status": "connected", "bluetoothConnected": True,
                      "target": "SIMULATED TEST DEVICE", "timestamp": int(time.time() * 1000)}
            assert not publish(aphone, atopic + "status/" + aid, status).is_failure
            end = time.monotonic() + 5
            while time.monotonic() < end:
                listing = alice.get("/api/devices").json()["devices"]
                if listing[0]["online"]: break
                time.sleep(.05)
            checks["device_status_arrives_in_portal"] = listing[0]["online"]
            checks["other_account_api_denied"] = bob.post(f"/api/devices/{a['device']['id']}/command", json={"action": "HOME"}).status_code == 404
            checks["cross_tenant_publish_denied"] = publish(bremote, atopic + "command/" + aid, {"action": "WAIT"}).is_failure
            checks["management_topic_denied"] = publish(aremote, "$CONTROL/dynamic-security/v1", {"commands": [{"command": "getDefaultACLAccess"}]}).is_failure
            sub_event, reasons = threading.Event(), []
            def subscribed(c, userdata, mid, codes, properties): reasons.extend(codes); sub_event.set()
            bremote.on_subscribe = subscribed; bremote.subscribe(atopic + "#", qos=1); assert sub_event.wait(5)
            checks["cross_tenant_subscription_denied"] = all(r.is_failure for r in reasons)
            spoof = dict(b["connection"]["controller"], client_id=a["connection"]["phone"]["client_id"])
            _, denied = connect(spoof)
            checks["client_id_spoof_denied"] = denied.is_failure
            checks["victim_connection_survives_spoof"] = not publish(aphone, atopic + "status/" + aid, status).is_failure
            response = alice.post(f"/api/devices/{a['device']['id']}/rotate", json={"mqtt_password": "dummy_New-chosen-MQTT-456"})
            assert response.status_code == 200, response.text
            _, denied = connect(a["connection"]["phone"])
            checks["old_password_revoked"] = denied.is_failure
            current = response.json()["connection"]
            checks["chosen_password_used_exactly"] = current["phone"]["password"] == current["controller"]["password"] == "dummy_New-chosen-MQTT-456"
            newphone, accepted = connect(current["phone"])
            checks["rotated_password_works"] = not accepted.is_failure
            assert alice.post(f"/api/devices/{a['device']['id']}/enabled", json={"enabled": False}).status_code == 200
            _, denied = connect(current["controller"])
            checks["disabled_device_cannot_reconnect"] = denied.is_failure
            assert alice.post(f"/api/devices/{a['device']['id']}/enabled", json={"enabled": True}).status_code == 200
            _, accepted = connect(current["controller"])
            checks["reenabled_device_can_connect"] = not accepted.is_failure
            assert all(checks.values()), checks
        finally:
            for client in clients: client.disconnect(); client.loop_stop()
            for owner, record in zip((alice, bob), records):
                owner.delete(f"/api/devices/{record['device']['id']}")
            if os.getenv("PORTAL_TEST_REPORT"):
                Path(os.environ["PORTAL_TEST_REPORT"]).write_text(json.dumps(checks, indent=2))
