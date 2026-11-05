# AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
import json

import pytest
from fastapi.testclient import TestClient
from app import create_app
from test_portal import ORIGIN, FakeBroker, signup, add
from test_pairing import begin, enroll, PairBroker


def test_custom_broker_for_manual_and_qr_enrollment(tmp_path, monkeypatch):
    monkeypatch.setenv('PUBLIC_MQTT_URI', 'ssl://broker.example.org:8883')
    monkeypatch.setenv('APK_DOWNLOAD_URL', 'https://releases.example.org/apps/')
    monkeypatch.setenv('ANDROID_PACKAGE', 'org.example.control')
    app = create_app(tmp_path, ORIGIN, PairBroker())
    owner = signup(app, 'selfhost')
    assert add(owner).json()['connection']['uri'] == 'ssl://broker.example.org:8883'
    code, body = begin(owner)
    assert code['url'].startswith(ORIGIN + '/pair#')
    prepared = enroll(TestClient(app, base_url=ORIGIN, headers={'Origin': ORIGIN}), body)
    assert prepared['connection']['uri'] == 'ssl://broker.example.org:8883'
    js = owner.get('/assets/config.js')
    config = json.loads(js.text.removeprefix('window.AIPHONE_CONFIG = ').removesuffix(';'))
    assert config == {'mqtt_uri': 'ssl://broker.example.org:8883',
                      'download_url': 'https://releases.example.org/apps/', 'android_package': 'org.example.control'}
    assert 'password' not in js.text and 'binding_secret' not in js.text
    for path in ['/', '/pair']:
        page = owner.get(path).text
        assert page.index('/assets/config.js') < page.index('/assets/' + ('app.js' if path == '/' else 'pair.js'))
        assert 'wyang' not in page


@pytest.mark.parametrize('uri', ['tcp://broker.example:1883', 'ssl://user:secret@broker.example:8883',
                                      'ssl://broker.example:8883/path', 'ssl://broker.example', 'ssl://broker.example:70000'])
def test_invalid_public_broker_rejected(tmp_path, monkeypatch, uri):
    monkeypatch.setenv('PUBLIC_MQTT_URI', uri)
    with pytest.raises(ValueError):
        create_app(tmp_path, ORIGIN, FakeBroker())


def test_download_link_can_be_disabled_and_config_cannot_inject_script(tmp_path, monkeypatch):
    monkeypatch.setenv('APK_DOWNLOAD_URL', '')
    app = create_app(tmp_path, ORIGIN, FakeBroker())
    assert '"download_url": ""' in TestClient(app).get('/assets/config.js').text
    monkeypatch.setenv('APK_DOWNLOAD_URL', 'https://downloads.example/</script>')
    app = create_app(tmp_path / 'second', ORIGIN, FakeBroker())
    assert '</script>' not in TestClient(app).get('/assets/config.js').text
