---
title: Encrypt the PC link with a pinned self-signed certificate and adb pairing
labels: [area/security, area/sync, priority/P1, size/L]
milestone: M4 Security hardening
depends_on: [032]
suggested_agent: opus · high
---
## Problem
Even after 032, Wi-Fi mode is plaintext HTTP with a long-lived bearer token (R4-S2). Auto sync (026) and boot restart (025) are only safe in USB mode until the link is encrypted and the PC can tell its phone from an impostor (R4-R13, R4-R14, SEC-3, SEC-14).

## Use case / flow
[08 auto sync](../flows/08-auto-sync.md)

## Proposed approach
- Phone: generate an EC P-256 key in the Android Keystore (non-exportable), with a self-signed cert (CN=fintrack-<device_id>). NanoHTTPD `makeSecure` uses an SSLServerSocketFactory backed by that key (or the replacement server if 037 swaps NanoHTTPD).
- Pairing: `fintrack pair` runs `adb forward`, calls `GET /api/pair` over USB loopback **only**, and receives `{device_id, cert_sha256, token}`. The PC stores the fingerprint and device_id and switches `phone_url` to `https://<wifi-ip>:8765`.
- PC client: a custom `ssl` context that accepts **only** the pinned fingerprint (no CA). A mismatch → `device_changed` state, stop (026/034).
- Token rotation on re-pair. `/api/pair` is refused on non-loopback.
- After this, boot restart in Wi-Fi mode is allowed (025).

## Acceptance criteria
- [ ] After `fintrack pair`, `fintrack sync` over Wi-Fi uses HTTPS. A packet capture shows no plaintext JSON (manual).
- [ ] Replace the phone cert (reinstall) → the PC refuses with "Phone identity changed — run fintrack pair".
- [ ] `/api/pair` over the Wi-Fi interface → 403.
- [ ] A unit test for PC fingerprint pinning, with good and bad certs.
- [ ] HTTP (plaintext) on the Wi-Fi interface is disabled once pairing is active.

## Test fixtures needed
Generated test certs (PC side), and Robolectric/instrumented tests for Keystore if feasible (otherwise a manual checklist).

## Files likely touched
`server/LinkService.kt`, `server/ApiServer.kt`, new `server/TlsIdentity.kt`, `data/Prefs.kt`, `ui/screens/LinkScreen.kt`, `pc/fintrack_pc/phone.py`, `cli.py`, `config.py`, tests.

## Out of scope / open questions
- Does NanoHTTPD 2.3.1's `makeSecure` work with Keystore-backed keys on API 26? **Unverified**. It may force the server swap planned in 037.
