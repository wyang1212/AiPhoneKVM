// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
'use strict';
const config = window.AIPHONE_CONFIG || {download_url: '', android_package: 'vip.wyang.aiphonekvm.debug'};
const download = document.getElementById('download-app');
if (config.download_url) { download.href = config.download_url; download.hidden = false; }
const parts = new URLSearchParams(location.hash.slice(1));
if (parts.get('v') === '1' && /^[A-Za-z0-9_-]{43}$/.test(parts.get('token') || '')) {
  document.getElementById('launch-app').href = 'intent://' + location.host + '/pair#Intent;scheme=https;package=' + config.android_package + ';S.enrollment_token=' + parts.get('token') + ';end';
} else {
  document.getElementById('launch-app').hidden = true;
  document.getElementById('link-error').textContent = '绑定链接无效，请在网站重新生成二维码。';
}
