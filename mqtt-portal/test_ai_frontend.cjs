// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
'use strict';
const { JSDOM } = require('jsdom');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const w=new JSDOM(fs.readFileSync(path.join(__dirname,'static/index.html'),'utf8'),{url:'https://portal.test',runScripts:'outside-only'}).window,d=w.document;
w.setInterval=()=>1;w.clearInterval=()=>{};
const ok=data=>({ok:true,status:200,json:async()=>data});
const taskId='26f4e0b0-f0ba-4a5f-b0a7-50ac09415822';
let task={status:'idle',active:false},fail=false,starts=0,stops=0;
const device=()=>({id:'m11',name:'M11',device_id:'M11_ID',enabled:1,online:true,bluetooth_connected:true,target:'测试被控端',remote_ai:true,model_configured:true,ai_task:task});
w.fetch=async(url,opt={})=>{
 if(url==='/api/me')return ok({username:'demo',csrf:'csrf'});
 if(url==='/api/devices')return ok({devices:[device()],mqtt:true});
 if(url.endsWith('/ai/start')){starts++;const b=JSON.parse(opt.body);assert.equal(b.goal,'打开设置，查看版本');assert.match(b.request_id,/^[0-9a-f-]{36}$/);if(fail)throw Error('lost response');task={status:'running',active:true,taskId,goal:b.goal,rounds:1,steps:0};return ok({response:{status:'SUCCESS',data:task}})}
 if(url.endsWith('/ai/stop')){stops++;assert.equal(JSON.parse(opt.body).task_id,taskId);task={...task,status:'cancelled',active:false,message:'已停止'};return ok({response:{status:'SUCCESS',data:task}})}
 throw Error('Unexpected '+url);
};
const settle=async()=>{for(let i=0;i<10;i++)await new Promise(r=>setImmediate(r))};
(async()=>{
 w.eval(fs.readFileSync(path.join(__dirname,'static/app.js'),'utf8'));await settle();
 const input=d.querySelector('.ai-task textarea');input.focus();input.value='打开设置，查看版本';input.setSelectionRange(3,5);input.dispatchEvent(new w.Event('input'));
 assert(!d.querySelector('.ai-start').disabled);
 d.getElementById('refresh').click();await settle();
 assert.equal(input,d.querySelector('.ai-task textarea'));assert.equal(d.activeElement,input);assert.equal(input.selectionStart,3);assert.equal(input.value,'打开设置，查看版本');
 d.querySelector('.ai-start').click();d.querySelector('.ai-start').click();await settle();assert.equal(starts,1);assert(d.querySelector('.ai-start').disabled);assert(!d.querySelector('.ai-stop').disabled);
 assert(d.querySelector('.ai-state').textContent.includes('正在执行'));
 assert(Array.from(d.querySelectorAll('.remote-controls button')).find(b=>b.textContent==='主页').disabled);
 d.querySelector('.ai-stop').click();await settle();assert.equal(stops,1);assert(!d.querySelector('.ai-start').disabled);assert(d.querySelector('.ai-stop').disabled);
 fail=true;d.querySelector('.ai-start').click();await settle();assert(d.querySelector('.ai-start').disabled);assert(d.querySelector('.ai-error').textContent.includes('可能已送达'));assert.equal(starts,2);
 d.getElementById('refresh').click();await settle();assert(d.querySelector('.ai-start').disabled,'uncertain start must stay disabled across polling');
 console.log('PASS: draft/focus/cursor survive refresh; start once; progress; targeted stop; uncertain delivery blocks repeat');w.close();
})().catch(e=>{console.error(e);w.close();process.exitCode=1});
