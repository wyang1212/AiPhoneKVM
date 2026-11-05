# Portal validation record

Executed on 2026-11-06. All 32 distinct Python tests and all six Node.js frontend scripts passed across the runs below. This validates portal behavior with temporary data and simulated MQTT clients; it does not establish real-phone, model or Bluetooth/HID acceptance.

## Executed checks

| Run | Environment | Result |
| --- | --- | --- |
| Backend suite | FakeBroker/PairBroker and temporary SQLite databases; socket connections denied | 29 passed, 3 real-broker cases skipped, 0 failures/errors |
| Real-broker follow-up | Mosquitto 2.0.22 dynamic-security plugin, isolated loopback listener at `127.0.0.1:18885`, temporary accounts/databases, simulated phone clients | All 3 previously skipped cases passed, 0 skipped/failures/errors |
| Frontend scripts | Node.js 24.22.1, jsdom 26.1.0, stubbed fetch and explicit script execution | All 6 scripts exited successfully |

Python used version 3.14, the dependencies in `mqtt-portal/requirements.txt`, pytest and httpx. The two Python runs took approximately 14.19 and 6.43 seconds. The total is 32 different tests, rather than 35 different tests: the second run executed the three integration cases skipped by the first.

## Passing coverage

- Accounts and device ownership: password hashing, secure sessions, logout/expiration, CSRF/origin checks, device validation/limits, collision handling, bounded commands, rollback after broker failures and exact device-topic ACL generation.
- Pairing transactions: idempotent completion, cancellation, expiry/replay rejection, nonce/account boundaries, proof requirements, preservation of existing credentials until commit, cleanup after failures and recovery after an ambiguous response.
- Real MQTT authorization: generated credentials authenticate; cross-account publishing/subscribing and management-topic access are denied; a mismatched client ID is rejected without evicting the legitimate device; password rotation, disable and re-enable behave as expected. All 13 recorded checks passed.
- Real MQTT pairing: HTTP alone cannot supply the MQTT proof; the phone role can prove possession; cancellation revokes staged credentials while preserving the original; committed rebind revokes original credentials, retains the device card and supports recovery. All eight recorded checks passed.
- AI request routing: owner/CSRF/input validation, preserved request IDs, start expiry, task-specific stop, bounded status views and rejection of stale/offline status. The real-broker case verified AI_START/AI_STOP delivery and responses with a simulated phone, including cross-account denial.
- Self-hosting configuration: configured TLS broker settings propagate to manual and QR enrollment; invalid public broker settings are rejected; download links can be disabled; generated browser configuration resists script injection.

The five frontend scripts passed these specific checks:

| Script in `mqtt-portal/` | Verified behavior |
| --- | --- |
| `test_frontend.cjs` | Logout clears account data; failed refresh and late responses cannot expose the previous account's devices. |
| `test_ai_frontend.cjs` | Refresh preserves draft/focus/cursor; duplicate start clicks submit once; progress and targeted stop update controls; uncertain delivery blocks repeated starts. |
| `test_pairing_frontend.cjs` | QR pairing shows progress/completion; closing a pending pairing cancels it; logout removes the binding token. |
| `test_password_form.cjs` | Create/rotate use the chosen MQTT password; obsolete password fields are absent; sensitive form fields clear on close. |
| `test_selfhost_frontend.cjs` | App launch/download links use configured host, port and package; invalid QR codes and disabled downloads remain hidden. |
| `test_refresh_frontend.cjs` | Five-second polling preserves expanded/collapsed advanced controls, draft text, focus and selection while still updating device state. Added after real-browser screenshot review. |

## Isolation and deployment finding

Inherited service settings were cleared before testing. Credentials and dynamic-security state were generated solely for the isolated broker. The broker bound explicitly to loopback, and the test connection guard allowed only that broker and MQTT client's own local wake-up sockets. No unexpected network connections occurred. The broker exited normally after testing; its process was gone and the port could be rebound. No system package installation or existing service changes were needed.

The first broker run exposed a test-environment setup omission: the administrator created by `mosquitto_ctrl dynsec init` could publish management requests but lacked application-command publishing permission. Adding `publishClientSend aiphonekvm/+/+/command/+ allow` to that isolated service role resolved the AI command timeout. The final run passed without a product-code change. Preserve the service's management and application subscription/receive permissions as described in the [portal deployment instructions](../mqtt-portal/README.md).

## Limits

- MQTT phone clients were simulated. No actual Android enrollment, QR scan/App Link, screen sharing, model request, Bluetooth/HID input or target-screen outcome was exercised by these portal tests.
- The isolated broker used loopback transport. This run did not validate public DNS, TLS certificates/renewal, reverse proxy routing, remote-network failure behavior or a deployed portal.
- jsdom tests validate browser logic using fixtures; they do not establish real-browser rendering, accessibility, mobile layout or camera scanning behavior.
- One Starlette TestClient/httpx deprecation warning occurred. It did not fail any test and remains a test-tool compatibility item.
