# Third-party notices / 第三方来源与许可证

Audited: 2026-11-05. Versions are the resolved Gradle runtime graph and the pinned portal requirements, not guessed latest versions. Android modules/BOM constraints are enumerated in each app’s `assets/third_party/DEPENDENCIES.json`. The dependency libraries below are consumed unchanged unless a source-derived modification is explicitly noted.

## Android source and runtime components

| Component | Relationship / use | License | Upstream | Included text |
|---|---|---|---|---|
| Atharok / BT Remote | Android UI, settings and Bluetooth HID source; modified | GNU GPL version 3 | https://gitlab.com/Atharok/BtRemote | `LICENSE`, `src/LICENSE`, original source notices |
| jqssun / android-bt-remote | General keyboard, mouse and media fork; modified | GNU GPL version 3 | https://github.com/jqssun/android-bt-remote | `LICENSE`, `UPSTREAM-README.md`, original source notices |
| antgroup / BlueHIDFlow | Protocol/workflow reference; no ESP32 firmware shipped | Apache-2.0 | https://github.com/antgroup/BlueHIDFlow | `LICENSE.BlueHIDFlow`, `BlueHIDFlow-LICENSE.txt` |
| Reorderable 3.1.0 | Home-screen drag and drop; unchanged Maven dependency | Apache-2.0 | https://github.com/Calvin-LL/Reorderable/tree/v3.1.0 | `Reorderable-LICENSE.txt`, `Reorderable-NOTICE.txt` |
| ZXing Android Embedded 4.3.0 / ZXing core 3.4.1 | QR decoding; unchanged Maven dependencies | Apache-2.0 | https://github.com/journeyapps/zxing-android-embedded/tree/v4.3.0 | `LICENSE.zxing-android-embedded`, `LICENSE.zxing`, `NOTICE.zxing` |
| Eclipse Paho MQTT Java 1.2.5 | MQTT client; unchanged Maven dependency | EDL-1.0 chosen from upstream EPL-2.0 / EDL-1.0 options | https://github.com/eclipse-paho/paho.mqtt.java/tree/v1.2.5 | `Eclipse-Distribution-License-1.0.txt`, `Paho-about.html`, `Paho-NOTICE.txt` |
| NanoHTTPD 2.3.1 | Local HTTP server; unchanged Maven dependency | BSD-3-Clause | https://github.com/NanoHttpd/nanohttpd/tree/nanohttpd-project-2.3.1 | `NanoHTTPD-LICENSE.txt` |
| AndroidX / Jetpack / Compose / Material icons (except relocated Protobuf below) | Android UI, lifecycle, storage, navigation, widgets, work scheduling | Apache-2.0 | https://github.com/androidx/androidx | `Apache-2.0.txt`; resolved coordinates in `DEPENDENCIES.json` |
| AndroidX relocated Protobuf: datastore-preferences-external-protobuf 1.2.1 / glance-appwidget-external-protobuf 1.1.1 | Protocol Buffers runtime repackaged by AndroidX | BSD-3-Clause | https://github.com/protocolbuffers/protobuf | `AndroidX-Protobuf-LICENSE.txt`, `Protobuf-LICENSE.txt`; exact artifact licenses verified from their POMs |
| Kotlin 2.4.0 / coroutines 1.9.0 / serialization 1.11.0 | Kotlin runtime and asynchronous/JSON support | Apache-2.0 | https://github.com/JetBrains/kotlin | `Apache-2.0.txt`; each project retains its original ownership |
| Koin 4.2.1 / Stately 2.1.0 | Dependency injection and concurrency collections | Apache-2.0 | https://github.com/InsertKoinIO/koin | `Apache-2.0.txt`; Stately: https://github.com/touchlab/Stately |
| Okio 3.9.1 (controller) / 3.16.4 (capture) | I/O; unchanged indirect dependencies | Apache-2.0 | https://github.com/square/okio | `Apache-2.0.txt` |
| OkHttp Android 5.3.2 (capture) | Screenshot helper HTTP client; unchanged Maven dependency | Apache-2.0 | https://github.com/square/okhttp/tree/parent-5.3.2 | capture `Apache-2.0.txt`, `HTTP-client-NOTICE.txt` |
| JetBrains annotations / JSpecify / Guava ListenableFuture | Indirect annotation and future support | Apache-2.0 | https://github.com/JetBrains/java-annotations | `Apache-2.0.txt`; https://github.com/jspecify/jspecify and https://github.com/google/guava |

Android local text paths above are relative to `src/app/src/main/assets/third_party/`, except those explicitly marked capture/root. Original copyright statements and license terms remain authoritative. Main upstream authors are Atharok and jqssun plus their contributors; other owners include Calvin Liang, ZXing/JourneyApps contributors, Eclipse/Paho contributors, NanoHTTPD contributors, the Android Open Source Project, JetBrains, Google, Square, Touchlab and Koin contributors. No additional year range or exclusive ownership is invented here.

## Self-hosted portal dependencies

These are server dependencies declared in `mqtt-portal/requirements.txt`, not Android APK runtime libraries. Their original wheel/sdist license files and attribution must remain present when redistributing an installed server/container. Public package metadata for each pinned version is the evidence link below.

| Package | Version | Declared license | Metadata |
|---|---|---|---|
| annotated-doc | 0.0.5 | MIT | [PyPI](https://pypi.org/pypi/annotated-doc/0.0.5/json) |
| annotated-types | 0.8.0 | MIT | [PyPI](https://pypi.org/pypi/annotated-types/0.8.0/json) |
| anyio | 4.15.1 | MIT | [PyPI](https://pypi.org/pypi/anyio/4.15.1/json) |
| click | 8.5.0 | BSD-3-Clause | [PyPI](https://pypi.org/pypi/click/8.5.0/json) |
| fastapi | 0.162.0 | MIT | [PyPI](https://pypi.org/pypi/fastapi/0.162.0/json) |
| h11 | 0.16.0 | MIT | [PyPI](https://pypi.org/pypi/h11/0.16.0/json) |
| idna | 3.20 | BSD-3-Clause | [PyPI](https://pypi.org/pypi/idna/3.20/json) |
| opentelemetry-api | 1.45.1 | Apache-2.0 | [PyPI](https://pypi.org/pypi/opentelemetry-api/1.45.1/json) |
| paho-mqtt | 2.1.0 | EPL-2.0 OR BSD-3-Clause | [PyPI](https://pypi.org/pypi/paho-mqtt/2.1.0/json) |
| pydantic | 2.14.1 | MIT | [PyPI](https://pypi.org/pypi/pydantic/2.14.1/json) |
| pydantic_core | 2.50.1 | MIT | [PyPI](https://pypi.org/pypi/pydantic_core/2.50.1/json) |
| starlette | 1.10.0 | BSD-3-Clause | [PyPI](https://pypi.org/pypi/starlette/1.10.0/json) |
| typing-inspection | 0.4.4 | MIT | [PyPI](https://pypi.org/pypi/typing-inspection/0.4.4/json) |
| typing_extensions | 4.16.0 | PSF-2.0 | [PyPI](https://pypi.org/pypi/typing_extensions/4.16.0/json) |
| uvicorn | 0.54.0 | BSD-3-Clause | [PyPI](https://pypi.org/pypi/uvicorn/0.54.0/json) |
| qrcode | 8.2 | BSD-3-Clause plus preserved MIT-derived QR code attribution | [PyPI](https://pypi.org/pypi/qrcode/8.2/json) |

The qrcode 8.2 distribution retains notices for Lincoln Loop, pyqrnative, Sam Curren and Kazuhiko Arase, including the original MIT attribution and QR Code trademark statement. The complete qrcode 8.2 distribution license is copied in `LICENSES/qrcode-8.2.txt`; retain it rather than replacing it with a generic BSD template. Eclipse Paho Python offers EPL-2.0 OR BSD-3-Clause according to its pinned package metadata; the BSD-3-Clause option is used for this distribution.

## Build/test tools and deployment dependencies

The project uses Gradle, Android Gradle Plugin, Android SDK tooling, Kotlin/Compose tooling, JUnit and AndroidX test dependencies for development. They are build/test tools, not a claim that all are packaged in a release APK. Android test artifacts are not release application artifacts. Self-hosted deployment may install Python and Eclipse Mosquitto; those installed programs retain their own upstream licenses and notices. Mosquitto is not bundled in the Android APK.

## Redistribution and change notices

- Preserve upstream license/copyright/NOTICE files and mark modified upstream source files prominently. Record the modification date and describe the changes. New application code and modified GPL-derived source remain under GNU GPL version 3.
- Distribute complete corresponding source and build instructions for the exact APK version. A current source tree must not be paired with an unrelated old binary.
- Apache/BSD/EDL components retain their individual license terms and applicable attribution. Paho is distributed using the permissive EDL-1.0 (Java) / BSD-3-Clause (Python) alternatives; retain the original dual-license information.
- Protocol/reference attribution does not grant permission to imply upstream endorsement or use upstream trademarks as this application’s brand.
- This inventory records inspected components. It is not a claim that unspecified assets or future dependencies have been audited. Add new dependencies and copied code to this file and retain their original notices before release.
