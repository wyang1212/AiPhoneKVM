// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
'use strict';
const portalConfig = window.AIPHONE_CONFIG || {download_url: '', mqtt_uri: '', android_package: 'vip.wyang.aiphonekvm.debug'};
const $ = id => document.getElementById(id);
let csrf = '', registerMode = false, account = '', devices = [], connection = null, passwordDevice = null, refreshTimer, toastTimer, busy = 0, accountGeneration = 0, refreshSequence = 0;
function node(tag, text, className) { const e = document.createElement(tag); if (text !== undefined) e.textContent = text; if (className) e.className = className; return e; }
function toast(message) { clearTimeout(toastTimer); $('toast').textContent = message; $('toast').hidden = false; toastTimer = setTimeout(() => $('toast').hidden = true, 6000); }
async function api(path, method = 'GET', body) {
  const requestGeneration = accountGeneration;
  const headers = {}; if (method !== 'GET') { headers['Content-Type'] = 'application/json'; if (csrf) headers['X-CSRF-Token'] = csrf; }
  let response;
  try { response = await fetch(path, { method, headers, credentials: 'same-origin', body: method === 'GET' ? undefined : JSON.stringify(body || {}) }); }
  catch { throw new Error('网络连接暂不可用，请稍后重试'); }
  const data = await response.json().catch(() => ({}));
  if (requestGeneration !== accountGeneration) { const e = new Error('账号已切换，已忽略原请求'); e.stale = true; throw e; }
  if (!response.ok) { const e = new Error(typeof data.detail === 'string' ? data.detail : '请求未完成，请检查填写内容'); e.status = response.status; throw e; }
  return data;
}
function authMode(mode) {
  registerMode = mode; $('login-tab').setAttribute('aria-selected', String(!mode)); $('register-tab').setAttribute('aria-selected', String(mode));
  $('auth-title').textContent = mode ? '创建你的账号' : '欢迎回来'; $('auth-subtitle').textContent = mode ? '从一台主控端开始，连接自己的设备。' : '登录后，继续管理你的设备。';
  $('auth-submit').replaceChildren(node('span', mode ? '注册并进入控制台' : '登录控制台'), node('span', '→'));
  $('confirm-wrap').hidden = !mode; $('confirm-password').required = mode; $('password').autocomplete = mode ? 'new-password' : 'current-password'; $('auth-error').textContent = '';
}
function resetWorkspace() {
  clearInterval(pairTimer); pairGeneration++; pairing = null;
  accountGeneration++; devices = []; connection = null; passwordDevice = null;
  for (const dialog of document.querySelectorAll('dialog[open]')) dialog.close();
  $('connection-details').replaceChildren(); $('device-list').replaceChildren(); $('device-form').reset(); $('mqtt-password-form').reset();
  resetMqttVisibility();
  for (const id of ['total-count', 'online-count', 'list-count']) $(id).textContent = '0';
  $('empty-state').hidden = false; $('broker-warning').hidden = true; $('dashboard-error').textContent = ''; $('add-device').disabled = false;
}
function signedOut() { csrf = ''; account = ''; clearInterval(refreshTimer); resetWorkspace(); $('console').hidden = true; $('loading').hidden = true; $('auth').hidden = false; }
async function signedIn(data) {
  resetWorkspace(); csrf = data.csrf; account = data.username; $('auth').hidden = true; $('loading').hidden = true; $('console').hidden = false; $('account-name').textContent = account; $('avatar').textContent = account[0].toUpperCase();
  $('auth-form').reset(); await refresh(); clearInterval(refreshTimer); refreshTimer = setInterval(() => { if (!document.hidden && !document.querySelector('dialog[open]') && !busy) refresh(); }, 5000);
}
function button(text, action, className = 'secondary') { const e = node('button', text, className); e.addEventListener('click', async () => { e.disabled = true; busy++; try { await action(); } catch (error) { if (!error.stale) toast(error.message); } finally { e.disabled = false; busy--; } }); return e; }
function updateTaskPanel(panel, d) {
  const task = d.ai_task || {}, pending = panel.dataset.pending === 'true';
  const labels = { idle: '尚未开始', running: '正在执行', stopping: '正在停止', completed: '已完成', blocked: '需要处理', failed: '执行失败', cancelled: '已停止', limit_reached: '达到运行上限' };
  panel.querySelector('.ai-state').textContent = !d.online ? '主控端离线，无法获取任务状态' : (task.label || labels[task.status] || '尚未开始') + (task.rounds ? ` · 第 ${task.rounds} 轮 / 已操作 ${task.steps || 0} 步` : '');
  panel.querySelector('.ai-result').textContent = [task.summary, task.message].filter(Boolean).join(' · ');
  const current = panel.querySelector('.ai-current'); current.textContent = task.goal ? '当前任务：' + task.goal : ''; current.hidden = !task.goal;
  panel.querySelector('.ai-hint').textContent = !d.online ? '先在手机选择 AI 自动化和被控端，并保持自动化开启。' : !d.remote_ai ? '请升级主控端到支持网页 AI 任务的版本，再重新开启自动化。' : !d.model_configured ? '请先在主控端配置 AI 模型。' : !d.bluetooth_connected ? '请在主控端连接要操作的被控端。' : '操作对象：' + d.target + '。使用手机上的模型；最多 30 步 / 15 分钟。';
  panel.querySelector('.ai-upgrade').hidden = !portalConfig.download_url || !d.online || d.remote_ai;
  const uncertain = Number(panel.dataset.uncertainUntil || 0) > Date.now();
  panel.querySelector('.ai-start').disabled = pending || uncertain || !d.online || !d.remote_ai || !d.model_configured || !d.bluetooth_connected || task.active || !panel.querySelector('textarea').value.trim();
  panel.querySelector('.ai-stop').disabled = pending || !d.online || !d.remote_ai || !task.active || !task.taskId;
}
function taskPanel(d) {
  const panel = node('section', undefined, 'ai-task'); panel.append(node('h4', 'AI 自动化'));
  const label = node('label', '任务目标'); const input = node('textarea'); input.id = 'goal-' + d.id; label.htmlFor = input.id;
  input.maxLength = 2000; input.rows = 3; input.placeholder = '例如：打开设置，查看当前系统版本'; panel.append(label, input);
  panel.append(node('p', '', 'ai-hint'));
  const upgrade = node('a', '下载最新版 AiPhoneKVM 主控端', 'ai-upgrade'); upgrade.href = portalConfig.download_url; upgrade.target = '_blank'; upgrade.rel = 'noopener'; panel.append(upgrade);
  const progress = node('div', undefined, 'ai-progress'); progress.setAttribute('role', 'status'); progress.setAttribute('aria-live', 'polite');
  progress.append(node('strong', '', 'ai-state'), node('p', '', 'ai-current'), node('p', '', 'ai-result')); panel.append(progress);
  const error = node('p', '', 'error ai-error'); error.setAttribute('role', 'alert'); panel.append(error);
  const controls = node('div', undefined, 'ai-controls');
  async function run(operation) {
    const latest = devices.find(item => item.id === d.id); if (!latest) return;
    panel.dataset.pending = 'true'; error.textContent = ''; busy++; updateTaskPanel(panel, latest);
    try {
      const body = { request_id: crypto.randomUUID() };
      if (operation === 'start') body.goal = input.value.trim(); else body.task_id = latest.ai_task?.taskId;
      const data = await api(`/api/devices/${d.id}/ai/${operation}`, 'POST', body);
      if (data.response.status !== 'SUCCESS') { const rejected = new Error(data.response.error || data.response.message || '主控端未接受任务，请刷新状态'); rejected.definitive = true; throw rejected; }
      latest.ai_task = data.response.data; updateTaskPanel(panel, latest);
      toast(operation === 'start' ? '主控端已开始 AI 任务' : '任务已停止，已执行的操作不会撤销');
    } catch (e) {
      if (!e.stale) {
        error.textContent = e.message;
        if (!e.definitive && (!e.status || e.status >= 500)) {
          panel.dataset.uncertainUntil = String(Date.now() + 25000);
          error.textContent += '。可能已送达，请先查看任务状态；25 秒内暂停再次启动。';
        }
      }
    } finally {
      panel.dataset.pending = 'false'; busy--; if (devices.some(item => item.id === d.id)) { updateTaskPanel(panel, devices.find(item => item.id === d.id)); await refresh(); }
    }
  }
  const start = node('button', '启动 AI 任务', 'primary ai-start'), stop = node('button', '停止任务', 'secondary danger ai-stop');
  start.onclick = () => run('start'); stop.onclick = () => run('stop'); controls.append(start, stop); panel.append(controls);
  input.addEventListener('input', () => updateTaskPanel(panel, devices.find(item => item.id === d.id) || d));
  updateTaskPanel(panel, d); return panel;
}
function renderDevice(d) {
  const card = node('section', undefined, 'device-card'); card.dataset.deviceId = d.id; const top = node('div', undefined, 'device-top'); top.append(node('div', '▣', 'device-symbol'));
  const title = node('div', undefined, 'device-title'); title.append(node('h3', d.name), node('p', 'ID ' + d.device_id)); top.append(title);
  const status = node('span', undefined, 'device-state' + (d.online ? ' active' : '')); status.append(node('i', undefined, 'status-dot' + (d.online ? ' online' : '')), node('span', !d.enabled ? '已停用' : d.online ? '在线' : '等待连接')); top.append(status); card.append(top);
  const info = node('div', undefined, 'device-info'); const link = node('div', '蓝牙状态：'); link.append(node('span', d.bluetooth_connected ? '已连接' + (d.target ? ' · ' + d.target : '') : '等待主控端连接')); info.append(link); const type = node('div', '连接方式：'); type.append(node('span', 'MQTT / TLS')); info.append(type); card.append(info);
  const actions = node('div', undefined, 'device-actions');
  const advanced = node('details'); advanced.append(node('summary', '高级设置'));
  advanced.append(button('设置 MQTT 密码', async () => { passwordDevice = d.id; $('mqtt-password-form').reset(); $('mqtt-password-error').textContent = ''; $('password-device-name').textContent = d.name; $('password-dialog').showModal(); $('new-mqtt-password').focus(); }));
  actions.append(advanced);
  actions.append(button('改名', async () => { const name = prompt('设备名称', d.name); if (name === null) return; await api(`/api/devices/${d.id}/name`, 'POST', { name }); await refresh(); }));
  actions.append(button(d.enabled ? '停用连接' : '启用连接', async () => { if (d.enabled && !confirm('停用后，主控端和控制端的MQTT连接都会断开。继续吗？')) return; await api(`/api/devices/${d.id}/enabled`, 'POST', { enabled: !d.enabled }); await refresh(); toast(d.enabled ? '连接已停用' : '已启用，请等待主控端重新连接'); }));
  actions.append(button('移除', async () => { if (!confirm('移除后，这台设备的连接凭据将失效。确定移除吗？')) return; await api(`/api/devices/${d.id}`, 'DELETE'); await refresh(); toast('设备已移除'); }, 'secondary danger'));
  const controls = node('div', undefined, 'remote-controls');
  for (const [label, action] of [['查询状态', 'GET_STATUS'], ['主页', 'HOME'], ['返回', 'BACK']]) {
    const b = button(label, async () => { const data = await api(`/api/devices/${d.id}/command`, 'POST', { action }); if (data.response.status !== 'SUCCESS') throw new Error(data.response.error || data.response.message || '操作未完成，请检查主控端状态'); toast(action === 'GET_STATUS' ? '主控端已回复，蓝牙' + (data.response.data?.bluetoothConnected ? '已连接' : '未连接') : '指令已发送，请查看被控端确认结果'); await refresh(); });
    b.disabled = !d.enabled || !d.online || (d.ai_task?.active && action !== 'GET_STATUS'); controls.append(b);
  }
  actions.append(controls); card.append(taskPanel(d), actions); return card;
}
async function refresh() {
  const sequence = ++refreshSequence;
  try {
    const data = await api('/api/devices'); if (sequence !== refreshSequence) return; devices = data.devices; $('dashboard-error').textContent = ''; $('total-count').textContent = String(devices.length); $('list-count').textContent = String(devices.length); $('online-count').textContent = String(devices.filter(d => d.online).length); $('empty-state').hidden = devices.length !== 0; $('broker-warning').hidden = data.mqtt;
    const cards = new Map(Array.from($('device-list').children, card => [card.dataset.deviceId, card]));
    // Keep the live textarea mounted, including focus/cursor/IME composition, during polling.
    for (const d of devices) {
      let card = cards.get(d.id);
      if (!card) { card = renderDevice(d); $('device-list').append(card); }
      else {
        const updated = renderDevice(d);
        updated.querySelector('.device-actions details').open = card.querySelector('.device-actions details').open;
        for (const selector of ['.device-top', '.device-info', '.device-actions']) card.querySelector(selector).replaceWith(updated.querySelector(selector));
        updateTaskPanel(card.querySelector('.ai-task'), d);
      }
      cards.delete(d.id);
    }
    for (const card of cards.values()) card.remove();
    $('add-device').disabled = devices.length >= 5;
  } catch (error) { if (error.stale) return; if (error.status === 401) signedOut(); else $('dashboard-error').textContent = error.message; }
}
function resetMqttVisibility() { for (const e of document.querySelectorAll('.toggle-mqtt-password')) { $(e.dataset.field).type = 'password'; e.textContent = '显示'; } }
function openManualDevice() { $('device-form').reset(); resetMqttVisibility(); $('device-error').textContent = ''; $('device-dialog').showModal(); $('device-name').focus(); }
function connectionRow(label, value) {
  const row = node('div', undefined, 'connection-row'); row.append(node('span', label), node('code', value || '沿用原客户端 ID'));
  row.append(button('复制', async () => { await navigator.clipboard.writeText(value || ''); toast('已复制'); }, 'text-button')); return row;
}
function showConnection(data) {
  connection = data; const details = $('connection-details'); details.replaceChildren(connectionRow('服务器地址', data.uri), connectionRow('主题前缀', data.topic_prefix), connectionRow('设备 ID', data.device_id));
  for (const [role, title] of [['phone', '主控端 · 填到手机'], ['controller', '控制端 · 电脑或其他客户端使用']]) {
    const section = node('section', undefined, 'connection-group'); section.append(node('h3', title), connectionRow('用户名', data[role].username), connectionRow('密码', data[role].password));
    if (role === 'controller') section.append(connectionRow('客户端 ID', data[role].client_id)); details.append(section);
  }
  $('connection-dialog').showModal();
}
$('login-tab').onclick = () => authMode(false); $('register-tab').onclick = () => authMode(true);
$('show-password').onclick = () => { const visible = $('password').type === 'password'; $('password').type = visible ? 'text' : 'password'; $('show-password').textContent = visible ? '隐藏' : '显示'; $('show-password').setAttribute('aria-label', visible ? '隐藏密码' : '显示密码'); };
$('auth-form').addEventListener('submit', async event => {
  event.preventDefault(); $('auth-error').textContent = ''; if (registerMode && $('password').value !== $('confirm-password').value) { $('auth-error').textContent = '两次输入的密码不一致'; return; }
  $('auth-submit').disabled = true; try { const data = await api(registerMode ? '/api/register' : '/api/login', 'POST', { username: $('username').value, password: $('password').value }); await signedIn(data); } catch (error) { $('auth-error').textContent = error.message; } finally { $('auth-submit').disabled = false; }
});
$('logout').onclick = async () => { try { await api('/api/logout', 'POST'); signedOut(); } catch (error) { toast(error.message); } };
for (const id of ['add-device', 'empty-add']) $(id).onclick = openDevice;
for (const id of ['open-guide', 'strip-guide']) $(id).onclick = () => $('guide-dialog').showModal();
$('manual-device').onclick = () => { $('pair-dialog').close(); openManualDevice(); };
$('pair-refresh').onclick = openDevice;
$('refresh').onclick = () => refresh();
for (const e of document.querySelectorAll('.close-dialog')) e.addEventListener('click', () => e.closest('dialog').close());
for (const e of document.querySelectorAll('.toggle-mqtt-password')) e.addEventListener('click', () => { const field = $(e.dataset.field); field.type = field.type === 'password' ? 'text' : 'password'; e.textContent = field.type === 'password' ? '显示' : '隐藏'; });
$('password-dialog').addEventListener('close', () => { passwordDevice = null; $('mqtt-password-form').reset(); resetMqttVisibility(); });
$('connection-dialog').addEventListener('close', () => { connection = null; $('connection-details').replaceChildren(); });
$('device-form').addEventListener('submit', async event => {
  event.preventDefault(); $('device-error').textContent = ''; $('device-submit').disabled = true; busy++;
  try { const data = await api('/api/devices', 'POST', { name: $('device-name').value, device_id: $('device-id').value.trim(), mqtt_password: $('device-mqtt-password').value }); $('device-dialog').close(); $('device-form').reset(); await refresh(); if (data.connection) showConnection(data.connection); else toast('设备已添加'); }
  catch (error) { $('device-error').textContent = error.message; } finally { $('device-submit').disabled = false; busy--; }
});
$('mqtt-password-form').addEventListener('submit', async event => {
  event.preventDefault(); if (!passwordDevice) return; $('mqtt-password-error').textContent = ''; $('mqtt-password-submit').disabled = true; busy++;
  try { const data = await api(`/api/devices/${passwordDevice}/rotate`, 'POST', { mqtt_password: $('new-mqtt-password').value }); $('password-dialog').close(); await refresh(); showConnection(data.connection); }
  catch (error) { if (!error.stale) $('mqtt-password-error').textContent = error.message; } finally { $('mqtt-password-submit').disabled = false; busy--; }
});
$('copy-connection').onclick = async () => { if (!connection) return; try { await navigator.clipboard.writeText(JSON.stringify(connection, null, 2)); toast('已复制连接参数，请妥善保存'); } catch { toast('无法访问剪贴板，请逐项选择并复制'); } };
let pairing = null, pairTimer, pairGeneration = 0, pairPolling = false;
function drawQR(matrix) {
  const canvas = $('pair-qr'), scale = 7; canvas.width = canvas.height = matrix.length * scale;
  const ctx = canvas.getContext('2d'); if (!ctx) return;
  ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height); ctx.fillStyle = '#111';
  matrix.forEach((row, y) => row.forEach((black, x) => { if (black) ctx.fillRect(x * scale, y * scale, scale, scale); }));
}
async function openDevice() {
  const generation = ++pairGeneration; clearInterval(pairTimer); pairPolling = false;
  if (!$('pair-dialog').open) $('pair-dialog').showModal();
  $('pair-error').textContent = ''; $('pair-state').textContent = '正在生成二维码…'; $('pair-open').hidden = true;
  $('pair-qr').hidden = true; $('pair-refresh').disabled = true;
  try {
    const data = await api('/api/pairings', 'POST');
    if (generation !== pairGeneration || !$('pair-dialog').open) { api('/api/pairings/' + data.id, 'DELETE').catch(() => {}); return; }
    pairing = data; drawQR(data.matrix); $('pair-qr').hidden = false;
    // Explicit Android launch also works when a browser keeps same-domain HTTPS links in its own tab.
    const pairUrl = new URL(data.url);
    $('pair-open').href = 'intent://' + pairUrl.host + pairUrl.pathname + '#Intent;scheme=https;package=' + portalConfig.android_package + ';S.enrollment_token=' + new URLSearchParams(pairUrl.hash.slice(1)).get('token') + ';S.browser_fallback_url=' + encodeURIComponent(data.url) + ';end';
    $('pair-open').hidden = false;
    const tick = async () => {
      if (generation !== pairGeneration || !pairing) return;
      const left = Math.max(0, Math.ceil(data.expires - Date.now() / 1000));
      if (!left) { clearInterval(pairTimer); $('pair-state').textContent = '二维码已过期，请刷新'; $('pair-qr').hidden = true; $('pair-open').hidden = true; return; }
      if (!pairPolling) {
        pairPolling = true;
        try {
          const state = await api('/api/pairings/' + data.id);
          if (generation !== pairGeneration) return;
          if (state.state === 'complete') {
            pairing = null; clearInterval(pairTimer); $('pair-dialog').close(); await refresh(); toast('主控端已绑定，MQTT 连接验证通过；请在手机选择被控端并开启自动化'); return;
          }
          const text = { waiting: '等待手机扫码', scanned: '请在手机上确认绑定账号', configuring: '正在保存配置并验证连接', cancelled: '配对已取消，请刷新二维码', expired: '二维码已过期，请刷新' }[state.state] || '等待手机连接';
          $('pair-state').textContent = text + ' · ' + Math.floor(left / 60) + ':' + String(left % 60).padStart(2, '0');
          $('pair-error').textContent = '';
        } catch (error) { if (!error.stale && generation === pairGeneration) $('pair-error').textContent = error.message; }
        finally { pairPolling = false; }
      }
    };
    tick(); pairTimer = setInterval(tick, 2000);
  } catch (error) { if (!error.stale && generation === pairGeneration) { $('pair-state').textContent = '未能生成二维码'; $('pair-error').textContent = error.message; } }
  finally { if (generation === pairGeneration) $('pair-refresh').disabled = false; }
}
$('pair-dialog').addEventListener('close', () => {
  clearInterval(pairTimer); pairGeneration++; const old = pairing; pairing = null;
  $('pair-qr').hidden = true; $('pair-open').href = '#'; $('pair-open').hidden = true;
  if (old) api('/api/pairings/' + old.id, 'DELETE').catch(() => {});
});
$('public-mqtt').textContent = portalConfig.mqtt_uri || '按部署配置连接';
for (const link of document.querySelectorAll('.download-app')) { link.hidden = !portalConfig.download_url; if (portalConfig.download_url) link.href = portalConfig.download_url; }
api('/api/me').then(signedIn).catch(error => { if (error.stale) return; signedOut(); if (error.status !== 401) $('auth-error').textContent = error.message; });
