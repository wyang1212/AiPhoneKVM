// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
// Render the real frontend and exercise its polling callback with synthetic API data.
const { JSDOM } = require('jsdom');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const html = fs.readFileSync(path.join(__dirname, 'static/index.html'), 'utf8');
const source = fs.readFileSync(process.env.PORTAL_TEST_JS || path.join(__dirname, 'static/app.js'), 'utf8');
const dom = new JSDOM(html, { url: 'https://portal.example.invalid', runScripts: 'outside-only' });
const window = dom.window;
let poll, online = true;
window.setInterval = callback => { poll = callback; return 1; };
window.clearInterval = () => {};
Object.defineProperty(window.document, 'hidden', { value: false });
window.fetch = async url => {
  const data = url === '/api/me' ? { username: 'UI_TEST_ACCOUNT', csrf: 'UI_TEST_CSRF' } :
    url === '/api/devices' ? { mqtt: true, devices: [{ id: 'UI_TEST_001', name: 'UI_TEST Controller',
      device_id: 'UI_TEST_CONTROLLER', enabled: true, online, bluetooth_connected: online,
      remote_ai: true, model_configured: true, target: 'UI_TEST Target', ai_task: { active: false, status: 'idle' } }] } : null;
  assert(data, 'Unexpected request: ' + url);
  return { ok: true, status: 200, json: async () => data };
};
async function settle() { for (let i = 0; i < 6; i++) await new Promise(resolve => setImmediate(resolve)); }
(async () => {
  window.eval(source);
  await settle();
  const document = window.document;
  const details = () => document.querySelector('.device-actions details');
  const goal = document.querySelector('.ai-task textarea');
  goal.value = 'UI_TEST unfinished goal'; goal.focus(); goal.setSelectionRange(3, 7);
  goal.dispatchEvent(new window.Event('input', { bubbles: true }));
  assert.equal(document.querySelector('.ai-start').disabled, false);
  details().open = true;
  await poll(); await settle();
  assert.equal(details().open, true, 'Polling must preserve an expanded device advanced section');
  assert.equal(document.querySelector('.ai-task textarea'), goal, 'Polling must retain the live goal field');
  assert.equal(document.activeElement, goal);
  assert.equal(goal.value, 'UI_TEST unfinished goal');
  assert.equal(goal.selectionStart, 3); assert.equal(goal.selectionEnd, 7);
  online = false;
  await poll(); await settle();
  assert.equal(details().open, true, 'Expansion must persist even when device status changes');
  assert.equal(document.querySelector('.ai-start').disabled, true, 'Offline changes must still disable AI start');
  assert.equal(document.querySelector('.remote-controls button').disabled, true, 'Offline controls must still update');
  details().open = false;
  online = true;
  await poll(); await settle();
  assert.equal(details().open, false, 'A user-collapsed advanced section must remain collapsed');
  assert.equal(document.querySelector('.ai-start').disabled, false);
  assert.equal(document.querySelector('.remote-controls button').disabled, false);
  console.log('PASS: polling preserves advanced expansion and goal editing while updating online controls');
  window.close();
})().catch(error => { console.error(error.message); window.close(); process.exitCode = 1; });
