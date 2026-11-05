# AiPhoneKVM self-hosted portal

This optional FastAPI/SQLite and MQTT portal is part of AiPhoneKVM. It provides account-scoped devices, short-lived pairing transactions and device-scoped MQTT credentials. It is not an online service supplied by this repository.

## Configuration

Use your own reachable HTTPS origin and TLS MQTT broker. All defaults in `compose.yml` are non-resolving examples:

- `PUBLIC_ORIGIN=https://controller.example.invalid`
- `PUBLIC_MQTT_URI=ssl://mqtt.example.invalid:8883`
- `APK_DOWNLOAD_URL=https://downloads.example.invalid/` (or empty to hide downloading)
- `ANDROID_PACKAGE`: the actual package you build and distribute.
- `DATA_DIR` and `BROKER_CONFIG`: local runtime paths. Store broker credentials in a separate mounted secrets directory.

Create the broker configuration expected by `broker.py` using your own administrative credentials. Never commit it, the SQLite database, TLS private keys or generated dynamic-security files. `broker.dynamic.conf` is an example; adapt plugin paths, listener binding, broker permissions and TLS certificates to your environment. The Traefik file is a routing example, not a drop-in production deployment.

The portal's server-side dynamic-security role needs both management RPC and device-command permissions. `mosquitto_ctrl dynsec init` creates an administrator that can publish management requests, but does not grant publishing to application command topics. Add this ACL to the portal service's role:

```text
publishClientSend aiphonekvm/+/+/command/+ allow
```

Keep its existing `publishClientSend` permission for `$CONTROL/dynamic-security/#`, and retain subscription and `publishClientReceive` permissions for `$CONTROL/dynamic-security/#`, `aiphonekvm/#` and the compatibility prefix `bluehidflow/#`. `Broker.start()` subscribes to management responses and both application prefixes. If you use narrower ACLs, preserve all three subscriptions and the responses/status messages they receive. Missing command-publish permission can leave registration working while control requests time out with HTTP 503. Apply these service permissions only to the server-side role; generated phone/controller roles remain limited to their own device's exact topics.

`static/assetlinks.json` deliberately uses an all-zero signing-certificate fingerprint. Replace it with your own release certificate fingerprint before enabling verified App Links. Do not use a third party's fingerprint or signing key.

Install Python dependencies from `requirements.txt`, or build with `Dockerfile`. After configuring a broker and environment, start the local portal bound to loopback and place a TLS reverse proxy in front. `compose.yml` uses host networking and therefore requires reviewing actual listener addresses before use.

## Pairing and control

On the controller, use the home “Bluetooth devices” entry to select the target, then enter AI automation. Website settings and scanning are in the Website tab. Website QR codes are accepted only for the selected HTTPS origin. Bindings and recovery journals are origin-scoped; changing a site must not send credentials from the previous site.

The local HTTP console and the website portal are separate interfaces. Do not expose an unauthenticated device listener on the public internet. Media/keyboard support depends on the controlled device.

## Tests and licenses

Run isolated tests with temporary data, test broker credentials and a temporary account. The `test_*integration*` tests require their documented live dependencies; they must not be pointed at a personal deployment by default. Frontend tests require Node.js and jsdom. Runtime dependency versions and their licenses are listed in the repository's THIRD_PARTY_NOTICES.md; the QR code package's full attribution is preserved in LICENSES/.

Executed portal test results and their limits are recorded in [PORTAL_QA.md](../docs/PORTAL_QA.md).
