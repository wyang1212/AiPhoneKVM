// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
const { JSDOM } = require('jsdom');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const dom = new JSDOM(fs.readFileSync(path.join(__dirname, 'static/index.html'), 'utf8'), { url: 'https://portal.test', runScripts: 'outside-only' });
const w = dom.window, d = w.document;
w.HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
w.HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); this.dispatchEvent(new w.Event('close')); };
w.setInterval = () => 1; w.clearInterval = () => {};
const records = [{ id: 'm11', name: 'M11', device_id: 'EXISTING', enabled: 1, online: false }];
let createBody, changeBody;
const response = value => ({ ok: true, status: 200, json: async () => value });
const connection = password => ({ uri: 'ssl://mqtt.example:443', device_id: 'TEST', topic_prefix: 'test/',
  phone: { username: 'phone-test', password, client_id: 'aiphone-TEST' },
  controller: { username: 'remote-test', password, client_id: 'remote-TEST' } });
w.fetch = async (url, options = {}) => {
  if (url === '/api/me') return response({ username: 'owner', csrf: 'test-csrf' });
  if (url === '/api/devices' && options.method === 'POST') {
    createBody = JSON.parse(options.body);
    const item = { id: 'new', name: createBody.name, device_id: createBody.device_id, enabled: 1, online: false };
    records.push(item); return response({ device: item, connection: connection(createBody.mqtt_password) });
  }
  if (url === '/api/devices') return response({ devices: records, mqtt: true });
  if (url === '/api/devices/m11/rotate') { changeBody = JSON.parse(options.body); return response({ connection: connection(changeBody.mqtt_password) }); }
  throw Error('Unexpected request: ' + url);
};
async function settle() { for (let i = 0; i < 6; i++) await new Promise(resolve => setImmediate(resolve)); }
(async () => {
  w.eval(fs.readFileSync(path.join(__dirname, 'static/app.js'), 'utf8'));
  await settle();
  assert.equal(d.getElementById('legacy-password'), null, 'No mysterious old password field');
  [...d.querySelectorAll('.device-actions button')].find(e => e.textContent === '设置 MQTT 密码').click();
  assert(d.getElementById('password-dialog').open);
  d.getElementById('new-mqtt-password').value = 'My-chosen-password';
  d.getElementById('mqtt-password-form').dispatchEvent(new w.Event('submit', { cancelable: true }));
  await settle();
  assert.deepEqual(changeBody, { mqtt_password: 'My-chosen-password' });
  assert(d.getElementById('connection-details').textContent.includes('My-chosen-password'));
  d.querySelector('#connection-dialog .close-dialog').click();
  assert.equal(d.getElementById('new-mqtt-password').value, '');
  d.getElementById('manual-device').click();
  d.getElementById('device-name').value = 'New phone'; d.getElementById('device-id').value = 'NEW_PHONE';
  d.getElementById('device-mqtt-password').value = 'Another-chosen-password';
  d.getElementById('device-form').dispatchEvent(new w.Event('submit', { cancelable: true }));
  await settle();
  assert.equal(createBody.mqtt_password, 'Another-chosen-password');
  assert.equal(createBody.legacy_password, undefined);
  assert.equal(d.getElementById('total-count').textContent, '2');
  assert(d.getElementById('connection-details').textContent.includes('Another-chosen-password'));
  console.log('PASS: add and change use the chosen password, no old-password input, fields cleared');
  w.close();
})().catch(error => { console.error(error); w.close(); process.exitCode = 1; });
