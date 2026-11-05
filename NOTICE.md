# 来源、许可与修改声明

AiPhoneKVM 是经过修改的独立项目，不是下列上游项目的官方发行版。原项目名称仅用于标明来源，不代表原作者认可或为本项目提供支持。

## 主体许可与来源

本项目主体按 GNU General Public License version 3 发布，全文见根目录 `LICENSE` 和 `src/LICENSE`。原作者与贡献者的版权、许可和署名继续适用；本文不将第三方代码重新声明为本项目原创。

- [Atharok / BT Remote](https://gitlab.com/Atharok/BtRemote)：Android 界面、设置、蓝牙 HID 及遥控基础代码，GNU GPL version 3。本项目在此基础上修改，保留其源码和许可证。上游介绍保留在 `src/README.md`。
- [jqssun / android-bt-remote](https://github.com/jqssun/android-bt-remote)：通用蓝牙键盘、鼠标和媒体遥控改进及相关资源，GNU GPL version 3。本项目采用其派生代码并继续修改；原上游说明保留在 `UPSTREAM-README.md`。它进一步基于 Atharok / BT Remote。
- [antgroup / BlueHIDFlow](https://github.com/antgroup/BlueHIDFlow)：命令协议和自动化流程参考，Apache-2.0。本项目使用 Android Bluetooth Classic HID，不分发其 ESP32/BLE 固件；Apache 许可副本保留在 `LICENSE.BlueHIDFlow`。不能将协议参考表述为完整固件移植。

## 本项目的修改

修改记录截至 2026-11-05。新增或调整的内容包括 Android 主控自动化、截图助手和可选无障碍增强控制、HTTP/MQTT 控制、视觉模型适配、网站绑定与自部署服务、目标循环与语音草稿、摄像头观察、手柄及独立功能首页、排序、键盘配色和设备管理流程。具体行为和验收限制以本仓库 README、CHANGELOG 和验收报告为准。

修改后的主体代码继续采用 GNU GPL version 3；没有取得原作者未授予的商标或其他权利。独立第三方库保留各自许可，不能把所有依赖统称为 GPL 代码。

## 依赖与声明

完整来源、实际依赖版本、许可选择和本地文本路径见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。Android 两端将许可证及归属说明放在各自 `assets/third_party/`。应用内“第三方库”列出主要来源与依赖；截图助手提供“开源声明”入口。

发布 APK 时同时提供该版本完整对应源码和构建说明，保留本文件、第三方声明、原许可及显著修改说明。对应源码仓库为 [AiPhoneKVM](https://github.com/wyang1212/AiPhoneKVM)。不要把仅含链接而没有所分发代码的旧源码包误记为新 APK 的对应源码。
