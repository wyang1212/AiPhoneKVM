# AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
"""Real MQTT routing with a simulated phone; this does not claim model/HID acceptance."""
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
from test_portal import signup, add, ORIGIN


@pytest.mark.skipif(not os.getenv('PORTAL_TEST_ADMIN'), reason='isolated broker only')
def test_real_mqtt_ai_goal_stop_and_tenant_boundary(tmp_path):
    config = json.loads(Path(os.environ['PORTAL_TEST_ADMIN']).read_text())
    assert config['port'] == 18885
    bridge = Broker(config)
    app = create_app(tmp_path, ORIGIN, bridge)
    phone = None
    received = []
    with TestClient(app, base_url=ORIGIN):
        alice, bob = signup(app, 'ai_' + uuid.uuid4().hex[:8]), signup(app, 'bob_' + uuid.uuid4().hex[:8])
        response = add(alice, 'AI_' + uuid.uuid4().hex[:8]).json()
        device, creds = response['device'], response['connection']['phone']
        try:
            connected = threading.Event()
            phone = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=creds['client_id'])
            phone.username_pw_set(creds['username'], creds['password'])
            prefix = response['connection']['topic_prefix']; target = device['device_id']
            state = {'taskId': str(uuid.uuid4()), 'active': False, 'status': 'idle'}
            def publish_status():
                phone.publish(prefix + 'status/' + target, json.dumps({'running': True, 'status': 'connected', 'timestamp': int(time.time()*1000), 'remoteAi': 1, 'modelConfigured': True, 'bluetoothConnected': True, 'aiTask': state}), qos=1, retain=True)
            def on_connect(c,u,f,r,p):
                assert not r.is_failure
                c.subscribe(prefix + 'command/' + target, qos=1)
            def on_subscribe(*args):
                publish_status(); connected.set()
            def on_message(c,u,m):
                command = json.loads(m.payload); received.append(command)
                assert not m.retain
                if command['action'] == 'AI_START':
                    state.update(active=True, status='running', goal=command['params']['goal'])
                else:
                    assert command['action'] == 'AI_STOP' and command['params']['taskId'] == state['taskId']
                    state.update(active=False, status='cancelled')
                publish_status()
                phone.publish(prefix + 'response/' + target, json.dumps({'id': command['id'], 'status': 'SUCCESS', 'data': state}), qos=1)
            phone.on_connect=on_connect; phone.on_subscribe=on_subscribe; phone.on_message=on_message
            phone.connect('127.0.0.1', 18885); phone.loop_start(); assert connected.wait(5)
            root = f"/api/devices/{device['id']}/ai"
            request = {'request_id': str(uuid.uuid4()), 'goal': '打开设置，查看系统版本'}
            assert bob.post(root+'/start', json=request).status_code == 404
            assert not received
            result = alice.post(root+'/start', json=request)
            assert result.status_code == 200 and result.json()['response']['data']['active']
            assert received[0]['id'] == request['request_id'] and received[0]['params']['goal'] == request['goal']
            assert 0 < received[0]['params']['expiresAt'] - time.time()*1000 < 20000
            listed = alice.get('/api/devices').json()['devices'][0]
            assert listed['remote_ai'] and listed['ai_task']['goal'] == request['goal']
            result = alice.post(root+'/stop', json={'request_id': str(uuid.uuid4()), 'task_id': state['taskId']})
            assert result.status_code == 200 and not result.json()['response']['data']['active']
            assert len(received) == 2
        finally:
            if phone:
                phone.disconnect(); phone.loop_stop()
            alice.delete('/api/devices/'+device['id'])
