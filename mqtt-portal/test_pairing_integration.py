# AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
"""Real dynamic-security proof and credential handoff, isolated local port only."""
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
from test_portal import signup, ORIGIN

@pytest.mark.skipif(not os.getenv('PORTAL_TEST_ADMIN'), reason='isolated broker required')
def test_real_enrollment_proof_handoff_and_rollback(tmp_path):
    cfg = json.loads(Path(os.environ['PORTAL_TEST_ADMIN']).read_text())
    assert cfg['port'] == 18885
    bridge = Broker(cfg)
    app = create_app(tmp_path, ORIGIN, bridge)
    sockets=[]
    def connect(c):
        event=threading.Event(); state={}
        cli=mqtt.Client(mqtt.CallbackAPIVersion.VERSION2,client_id=c['client_id'])
        cli.username_pw_set(c['username'],c['password'])
        def ready(c,u,f,r,p):state['accepted']=not r.is_failure;event.set()
        cli.on_connect=ready;cli.connect('127.0.0.1',18885,20);cli.loop_start();sockets.append(cli)
        assert event.wait(5)
        return cli,state['accepted']
    def prove(c,p):
        cli,accepted=connect(c);assert accepted
        cli.publish(c['topic_prefix']+'status/'+c['device_id'],json.dumps({'pairing_proof':p}),qos=1).wait_for_publish(5)
        end=time.monotonic()+5
        while time.monotonic()<end:
            with bridge.lock:
                data=bridge.statuses.get(c['topic_prefix']+'status/'+c['device_id'],({},0))[0]
            if data.get('pairing_proof')==p:break
            time.sleep(.02)
        else:raise AssertionError('proof not received')
        cli.disconnect();cli.loop_stop()
    def begin(owner,secret=''):
        code=owner.post('/api/pairings').json()
        return {'token':code['url'].split('token=')[1],'nonce':uuid.uuid4().hex+uuid.uuid4().hex,'device_id':'PAIR_'+tmp_path.name[-10:],'name':'SIMULATED QR PHONE','binding_secret':secret}
    checks={}
    with TestClient(app,base_url=ORIGIN):
        owner=signup(app,'pair_'+uuid.uuid4().hex[:8]);phone=TestClient(app,base_url=ORIGIN,headers={'Origin':ORIGIN})
        assert bridge.ready.wait(6)
        try:
            body=begin(owner);p=phone.post('/api/enrollment/prepare',json=body).json();c=p['connection']
            assert phone.post('/api/enrollment/finish',json=body).status_code==409
            checks['http_cannot_fake_mqtt_proof']=True
            prove(c,p['challenge']);assert phone.post('/api/enrollment/finish',json=body).status_code==200
            listing=owner.get('/api/devices').json()['devices'];assert len(listing)==1
            checks['real_phone_role_proof_completes_binding']=True
            assert not listing[0]['online'];checks['pairing_does_not_fake_control_service_online']=True
            original=listing[0]
            body2=begin(owner,p['binding_secret']);p2=phone.post('/api/enrollment/prepare',json=body2).json()
            _,old_ok=connect(c);assert old_ok
            checks['old_credentials_work_until_new_confirmed']=True
            assert phone.post('/api/enrollment/recover',json=body2).json()['state']=='cancelled'
            _,new_ok=connect(p2['connection']);assert not new_ok
            _,old_ok=connect(c);assert old_ok
            checks['cancel_revokes_staged_and_preserves_original']=True
            body3=begin(owner,p['binding_secret']);p3=phone.post('/api/enrollment/prepare',json=body3).json()
            prove(p3['connection'],p3['challenge']);assert phone.post('/api/enrollment/finish',json=body3).status_code==200
            _,old_ok=connect(c);assert not old_ok
            _,new_ok=connect(p3['connection']);assert new_ok
            checks['commit_revokes_original_and_accepts_new']=True
            listing=owner.get('/api/devices').json()['devices'];assert len(listing)==1 and listing[0]['id']==original['id']
            checks['same_account_rebind_keeps_original_card']=True
            assert phone.post('/api/enrollment/recover',json=body3).json()['state']=='complete'
            checks['lost_finish_response_recovers_without_rollback']=True
        finally:
            for cli in sockets:cli.disconnect();cli.loop_stop()
            for row in owner.get('/api/devices').json()['devices']:
                owner.delete('/api/devices/'+row['id'])
            app.state.pairing_sweep()
    target=Path(__file__).parent.parent/'evidence'/'portal-pairing-broker.json'
    target.write_text(json.dumps(checks,indent=2))
    assert len(checks)==8 and all(checks.values())
