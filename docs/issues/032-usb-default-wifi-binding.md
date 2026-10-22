---
title: Make USB-only the default PC link mode and bind Wi-Fi mode to the Wi-Fi interface
labels: [area/security, area/sync, priority/P0, size/S]
milestone: M4 Security hardening
depends_on: []
suggested_agent: opus · medium
---
## Problem
The default link mode is Wi-Fi (`sp.getBoolean(KEY_WIFI_MODE, true)`, android/.../data/Prefs.kt:29). It serves **plain HTTP** on `0.0.0.0` (server/LinkService.kt:32), so it also listens on mobile data, hotspot and VPN interfaces. The bearer token never expires. Anyone who sniffs it on the LAN gets the full history (`/api/changes?since=0`), can wipe categories (`/api/push` deletes and replaces the category table, data/TransactionStore.kt:162) and can trigger a 10-year SMS rescan (`/api/import`, server/ApiServer.kt:86-90) (R4-S2, SEC-2, R4-R16). **Severity: High.**

## Use case / flow
[08 auto sync](../flows/08-auto-sync.md), [07 recovery](../flows/07-recovery-and-health.md) (boot restart depends on safe mode)

## Proposed approach
- Default `wifiMode = false` for new installs. Existing users keep their setting but see a one-time banner: "Wi-Fi link is unencrypted — switch to USB or keep Wi-Fi on trusted networks only".
- Wi-Fi mode binds to the wlan IPv4 address (`wifiAddress()`), never `0.0.0.0`. No Wi-Fi → do not start, and show "Connect to Wi-Fi".
- Turning Wi-Fi mode on needs an explicit confirm dialog with the warning text.
- `/api/import` caps `days` to 90 unless the request comes over USB (loopback).
- README §3: USB first, Wi-Fi as an advanced option.

## Acceptance criteria
- [ ] Fresh install → Link screen shows "USB only". The server binds 127.0.0.1.
- [ ] Wi-Fi mode on a phone with wlan 192.168.1.5 and rmnet up → the server is reachable on 192.168.1.5 only (a socket-bind test on the chosen host string).
- [ ] Wi-Fi mode with no Wi-Fi → the server does not start, and `lastError` = "No Wi-Fi network".
- [ ] Existing Wi-Fi user → the banner shows once.
- [ ] `/api/import {"days":3650}` over Wi-Fi → clamped to 90.

## Test fixtures needed
A Prefs default test, host-selection pure function tests.

## Files likely touched
`data/Prefs.kt`, `server/LinkService.kt`, `server/ApiServer.kt`, `ui/screens/LinkScreen.kt`, `ui/MainActivity.kt`, `README.md`.

## Out of scope / open questions
- Encryption for Wi-Fi is 033 (pinned TLS).
