// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
'use strict';
const {JSDOM} = require('jsdom');
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const html = fs.readFileSync(path.join(__dirname, 'static/pair.html'), 'utf8');
const script = fs.readFileSync(path.join(__dirname, 'static/pair.js'), 'utf8');
for (const valid of [true, false]) {
  const dom = new JSDOM(html, {url: 'https://selfhost.example:9443/pair#v=1&token=' + (valid ? 'a'.repeat(43) : 'bad'), runScripts:'outside-only'});
  const w = dom.window, d = w.document;
  w.AIPHONE_CONFIG = {android_package:'org.example.remote', download_url:valid ? 'https://releases.example/app' : ''};
  w.eval(script);
  if (valid) {
    assert(d.getElementById('launch-app').href.startsWith('intent://selfhost.example:9443/pair#Intent;'));
    assert(d.getElementById('launch-app').href.includes('package=org.example.remote;'));
    assert.equal(d.getElementById('download-app').href, 'https://releases.example/app');
    assert(!d.getElementById('download-app').hidden);
  } else {
    assert(d.getElementById('launch-app').hidden);
    assert(d.getElementById('download-app').hidden);
  }
  w.close();
}
console.log('PASS: self-hosted landing uses current host/port/package/download; invalid QR and disabled download stay hidden');
