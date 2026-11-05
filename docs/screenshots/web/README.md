# 网页截图（UI TEST）

本目录含 37 张由 CUA 实际截取、未经图像编辑的 JPEG：29 张网站页面/状态/弹窗，8 张原局域网控制台各区。截图时逐页确认实际可见内容；在线截图确实显示示例设备卡片，退出后确认设备 DOM 已清空。

网站使用产品原始 HTML / JS / CSS，局域网页面使用 `WebConsole.kt` 的原始 HTML。仅由本机 loopback fixture 返回明示 `UI_TEST` 的假账号/设备/状态、`example.invalid` 地址和无效示例字符串。页面及弹窗都显示 UI TEST 标记。二维码不指向生产地址；局域网画面区展示的 SVG 明确写有 SYNTHETIC FRAME，**不是手机截图**。

这些截图证明桌面浏览器的布局和状态覆盖，不证明真实登录隔离、一次性绑定、MQTT、模型、蓝牙或共享屏幕功能已通过实机验收。移动端界面与授权双手机实测由另外的清单记录。截图使用当前 Edge 显示环境；本子任务无法使用 IAB，没有修改浏览器外观设置。

浏览器原生改名/停用/移除的 prompt/confirm 层不受 CUA 标签截图接口支持，因此本目录未伪造这些弹窗图片。改名 prompt 已观察到；CUA 焦点接口卡住后仅关闭本任务创建的标签并换新标签继续。结束时两个专用标签均已关闭，没有修改用户已有标签。

检查中修复了“五秒轮询自动收起设备高级设置”的真实前端问题；`test_refresh_frontend.cjs` 先失败、修复后通过，并验证任务草稿、焦点、光标与在线/离线控件更新。原有五个前端脚本也全部通过。W10 是修复后点击刷新仍展开的真实截图。

`manifest.json` 提供每图说明、JPEG尺寸、SHA-256与验收边界。复现方法见 `evidence/privacy-audit-20261105/web-ui-fixture/README.md`。

| 编号 | 截图 | 覆盖内容 |
| --- | --- | --- |
| L01 | [L01-lan-initial-UI_TEST.jpg](L01-lan-initial-UI_TEST.jpg) | 原局域网控制台初始页 |
| L02 | [L02-lan-connected-UI_TEST.jpg](L02-lan-connected-UI_TEST.jpg) | 局域网读取合成状态（physicalValidation=false） |
| L03 | [L03-lan-task-running-UI_TEST.jpg](L03-lan-task-running-UI_TEST.jpg) | 局域网模拟运行与操作互斥 |
| L04 | [L04-lan-task-stopped-UI_TEST.jpg](L04-lan-task-stopped-UI_TEST.jpg) | 局域网模拟任务已停止 |
| L05 | [L05-lan-command-UI_TEST.jpg](L05-lan-command-UI_TEST.jpg) | 默认STATUS命令与明确未执行的合成响应 |
| L06 | [L06-lan-synthetic-frame-UI_TEST.jpg](L06-lan-synthetic-frame-UI_TEST.jpg) | 原画面区展示UI TEST合成SVG（不是手机截图） |
| L07 | [L07-lan-settings-UI_TEST.jpg](L07-lan-settings-UI_TEST.jpg) | 备用尺寸、HID比例、无效MQTT示例和CA入口 |
| L08 | [L08-lan-logs-UI_TEST.jpg](L08-lan-logs-UI_TEST.jpg) | 设置下部、CA、保存入口、合成记录及许可说明 |
| W01 | [W01-login-UI_TEST.jpg](W01-login-UI_TEST.jpg) | 登录标签与登录表单 |
| W02 | [W02-register-UI_TEST.jpg](W02-register-UI_TEST.jpg) | 注册标签与确认密码 |
| W03 | [W03-empty-UI_TEST.jpg](W03-empty-UI_TEST.jpg) | 已登录设备空态 |
| W04 | [W04-pair-UI_TEST.jpg](W04-pair-UI_TEST.jpg) | 扫码添加：等待手机扫码 |
| W05 | [W05-pair-advanced-UI_TEST.jpg](W05-pair-advanced-UI_TEST.jpg) | 扫码高级设置与手动入口 |
| W06 | [W06-online-UI_TEST.jpg](W06-online-UI_TEST.jpg) | 已登录且真实可见的在线示例设备 |
| W07 | [W07-manual-device-UI_TEST.jpg](W07-manual-device-UI_TEST.jpg) | 手动添加示例表单（显示无效密码） |
| W08 | [W08-connection-UI_TEST.jpg](W08-connection-UI_TEST.jpg) | 连接参数弹窗（example.invalid和无效凭据） |
| W09 | [W09-guide-UI_TEST.jpg](W09-guide-UI_TEST.jpg) | 连接指南弹窗 |
| W10 | [W10-device-advanced-UI_TEST.jpg](W10-device-advanced-UI_TEST.jpg) | 修复后刷新仍保留高级设置展开 |
| W11 | [W11-password-UI_TEST.jpg](W11-password-UI_TEST.jpg) | MQTT密码表单（无效示例，取消） |
| W12 | [W12-offline-UI_TEST.jpg](W12-offline-UI_TEST.jpg) | 离线示例设备与操作禁用 |
| W13 | [W13-ai-running-UI_TEST.jpg](W13-ai-running-UI_TEST.jpg) | AI模拟运行中 |
| W14 | [W14-ai-stopped-UI_TEST.jpg](W14-ai-stopped-UI_TEST.jpg) | AI模拟停止结果 |
| W15 | [W15-ai-completed-UI_TEST.jpg](W15-ai-completed-UI_TEST.jpg) | AI模拟完成状态 |
| W16 | [W16-ai-blocked-UI_TEST.jpg](W16-ai-blocked-UI_TEST.jpg) | AI模拟需要处理状态 |
| W17 | [W17-ai-failed-UI_TEST.jpg](W17-ai-failed-UI_TEST.jpg) | AI模拟失败状态 |
| W18 | [W18-broker-warning-UI_TEST.jpg](W18-broker-warning-UI_TEST.jpg) | 消息服务重连提示 |
| W19 | [W19-no-model-UI_TEST.jpg](W19-no-model-UI_TEST.jpg) | 未配置AI模型提示 |
| W20 | [W20-no-bluetooth-UI_TEST.jpg](W20-no-bluetooth-UI_TEST.jpg) | 未连接被控端提示 |
| W21 | [W21-old-client-UI_TEST.jpg](W21-old-client-UI_TEST.jpg) | 旧主控端升级提示 |
| W22 | [W22-pair-scanned-UI_TEST.jpg](W22-pair-scanned-UI_TEST.jpg) | 二维码扫码后待确认的合成状态 |
| W23 | [W23-pair-configuring-UI_TEST.jpg](W23-pair-configuring-UI_TEST.jpg) | 二维码配置中的合成状态 |
| W24 | [W24-pair-expired-UI_TEST.jpg](W24-pair-expired-UI_TEST.jpg) | 二维码过期与刷新入口 |
| W25 | [W25-pair-cancelled-UI_TEST.jpg](W25-pair-cancelled-UI_TEST.jpg) | 配对取消提示 |
| W26 | [W26-pair-landing-UI_TEST.jpg](W26-pair-landing-UI_TEST.jpg) | 配对落地页与App/下载入口 |
| W27 | [W27-pair-invalid-UI_TEST.jpg](W27-pair-invalid-UI_TEST.jpg) | 失效绑定链接与App入口隐藏 |
| W28 | [W28-register-mismatch-UI_TEST.jpg](W28-register-mismatch-UI_TEST.jpg) | 真实前端确认密码不一致校验 |
| W29 | [W29-logout-UI_TEST.jpg](W29-logout-UI_TEST.jpg) | 退出后登录页与设备DOM清空 |
