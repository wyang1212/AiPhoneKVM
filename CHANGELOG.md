# Changelog

## 2.9.4

- Prepare a standalone public-source release with non-resolving example website, model and update addresses. Keep saved device settings private and exclude operational history, credentials, device data and build output from export.
- Expand upstream/third-party attribution and offline license texts, including receiver-side notices.
- Recognize both current `-android.apk` and legacy `-debug.apk` controller update names while excluding companion packages.
- Serialize HTTP configuration commits with runtime task state; capture model endpoint/model/credential as one consistent request profile.
- Ensure cancelled text input releases the captured original target, and wait for text release before leaving control.
- Clear stale camera framing drafts on stop/reopen and use a responsive home layout for short landscape screens.
- Add isolated, synthetic-state page screenshots and document the distinction between visual checks and physical/model acceptance.

## 2.9.3

Combine home Bluetooth status, discovery and selection entry points into one device-management entry. Keep selected and connected devices distinct; module selection starts the actual control connection.

## 2.9.2

Color keyboard groups consistently in portrait/landscape, with red F/J keycaps.

## 2.9.1

Remove the redundant common toolbar from directly entered control modules.

## 2.9

Move website configuration/pairing into the AI Website tab.

## Earlier AiPhoneKVM changes

Unified the former local PhoneKVM and AiPhoneKVM work in version 2.0. Added independent home modules, ordering, Xbox/FC layouts, screen companion, bounded AI tasks, explicit voice drafts, configurable self-hosted websites and optional calibrated camera observation. These are modifications to upstream code, not upstream-author releases; see NOTICE.md for source relationships and licenses.
