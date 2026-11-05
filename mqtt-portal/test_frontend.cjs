// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
// Exercise real page events and deferred network failures in a disposable DOM.
const { JSDOM } = require('jsdom');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const html = fs.readFileSync(path.join(__dirname, 'static/index.html'), 'utf8');
const source = fs.readFileSync(process.env.PORTAL_TEST_JS || path.join(__dirname, 'static/app.js'), 'utf8');
const dom = new JSDOM(html, { url: 'https://portal.test', runScripts: 'outside-only' });
const window = dom.window;
window.setInterval = () => 1;
window.clearInterval = () => {};
let signedInAs = 'alice';
let deferAlice = false, releaseAlice;
const sensitiveName = 'ALICE-PRIVATE-DEVICE';
window.fetch = async url => {
  if (url === '/api/me') return response({ username: 'alice', csrf: 'alice-csrf' });
  if (url === '/api/logout') { signedInAs = ''; return response({ ok: true }); }
  if (url === '/api/login') { signedInAs = 'bob'; return response({ username: 'bob', csrf: 'bob-csrf' }); }
  if (url === '/api/devices') {
    if (signedInAs === 'bob') throw Error('Simulated offline network');
    if (deferAlice) return new Promise(resolve => releaseAlice = resolve);
    return response({ devices: [{ id: 'private-alice-id', name: sensitiveName, device_id: 'PRIVATE_ID', enabled: 1,
      online: false, bluetooth_connected: false, target: '' }], mqtt: true });
  }
  throw Error('Unexpected request: ' + url);
};
function response(data) { return { ok: true, status: 200, json: async () => data }; }
async function settle() { for (let i = 0; i < 6; i++) await new Promise(resolve => setImmediate(resolve)); }
(async () => {
  window.eval(source);
  await settle();
  assert(window.document.getElementById('device-list').textContent.includes(sensitiveName), 'Fixture user must initially see her device');
  deferAlice = true;
  window.document.getElementById('refresh').click();
  await settle();
  window.document.getElementById('logout').click();
  await settle();
  assert(!window.document.getElementById('device-list').textContent.includes(sensitiveName), 'Logout must remove previous account device data from DOM');
  window.document.getElementById('username').value = 'bob';
  window.document.getElementById('password').value = 'Bob-test-password';
  window.document.getElementById('auth-form').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true }));
  await settle();
  assert.equal(window.document.getElementById('account-name').textContent, 'bob');
  assert.equal(window.document.getElementById('console').hidden, false);
  assert(!window.document.getElementById('console').textContent.includes(sensitiveName), 'Failed refresh for new user must never reveal previous user device');
  assert.equal(window.document.getElementById('total-count').textContent, '0');
  releaseAlice(response({ devices: [{ id: 'late-alice-id', name: sensitiveName, device_id: 'LATE_PRIVATE_ID', enabled: 1, online: false }], mqtt: true }));
  await settle();
  assert(!window.document.getElementById('console').textContent.includes(sensitiveName), 'Late response from previous account must be discarded');
  console.log('PASS: logout clears data; failed refresh and late old-account response do not leak devices');
  window.close();
})().catch(error => { console.error(error.message); window.close(); process.exitCode = 1; });
