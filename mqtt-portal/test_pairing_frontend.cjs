// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
'use strict';
const { JSDOM } = require('jsdom');
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const w = new JSDOM(fs.readFileSync(path.join(__dirname, 'static/index.html'), 'utf8'), {url:'https://portal.test',runScripts:'outside-only'}).window, d=w.document;
w.HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','')};
w.HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');this.dispatchEvent(new w.Event('close'))};
w.HTMLCanvasElement.prototype.getContext=()=>({fillRect(){},fillStyle:''});
let poll, state='waiting', cancelled=[], generation=0;
w.setInterval=fn=>{poll=fn;return 1}; w.clearInterval=()=>{};
const ok=data=>({ok:true,status:200,json:async()=>data});
w.fetch=async(url,opt={})=>{
 if(url==='/api/me')return ok({username:'owner',csrf:'csrf'});
 if(url==='/api/devices')return ok({devices:state==='complete'?[{id:'m11',name:'M11',device_id:'M11',enabled:1,online:false}]:[],mqtt:true});
 if(url==='/api/logout')return ok({ok:true});
 if(url==='/api/pairings'){generation++;return ok({id:'code'+generation,url:'https://portal.test:9443/pair#v=1&token='+'a'.repeat(43),expires:Date.now()/1000+300,matrix:[[true,false],[false,true]]})}
 if(url.startsWith('/api/pairings/')&&opt.method==='DELETE'){cancelled.push(url);return ok({ok:true})}
 if(url.startsWith('/api/pairings/'))return ok({state});
 throw Error('Unexpected request '+url);
};
w.AIPHONE_CONFIG={mqtt_uri:'ssl://broker.example:8883',download_url:'https://releases.example/apps/',android_package:'org.example.control'};
const settle=async()=>{for(let i=0;i<8;i++)await new Promise(r=>setImmediate(r))};
(async()=>{
 w.eval(fs.readFileSync(path.join(__dirname,'static/app.js'),'utf8'));await settle();
 d.getElementById('add-device').click();await settle();
 assert(d.getElementById('pair-dialog').open);assert(!d.getElementById('device-dialog').open);
 assert(!d.getElementById('pair-qr').hidden);assert(d.getElementById('pair-state').textContent.includes('等待手机扫码'));
 assert(d.getElementById('pair-open').href.includes('intent://portal.test:9443/pair#Intent;'));
 assert(d.getElementById('pair-open').href.includes('package=org.example.control;'));
 assert.equal(d.getElementById('public-mqtt').textContent,'ssl://broker.example:8883');
 assert.equal(d.querySelector('.download-app').href,'https://releases.example/apps/');
 state='scanned';await poll();assert(d.getElementById('pair-state').textContent.includes('手机上确认'));
 state='complete';await poll();await settle();assert(!d.getElementById('pair-dialog').open);assert.equal(d.getElementById('total-count').textContent,'1');
 assert(!cancelled.length,'Successful pairing must not be cancelled on close');
 state='waiting';d.getElementById('add-device').click();await settle();d.querySelector('#pair-dialog .close-dialog').click();await settle();assert(cancelled.length===1);
 d.getElementById('add-device').click();await settle();d.getElementById('logout').click();await settle();
 assert(!d.getElementById('pair-dialog').open);assert(d.getElementById('pair-qr').hidden);assert.equal(d.getElementById('pair-open').getAttribute('href'),'#');
 console.log('PASS: add opens QR, progress and success update, close cancels, logout hides binding token');w.close();
})().catch(e=>{console.error(e);w.close();process.exitCode=1});
