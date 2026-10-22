---
title: Keep the PC connected when the phone's Wi-Fi address changes
labels: [area/sync, priority/P2, size/S]
milestone: M3 Alerts & auto-sync
depends_on: [026]
suggested_agent: sonnet · medium
---
## Problem
In Wi-Fi mode the PC stores a fixed `phone_url` (pc/fintrack_pc/config.py:17). DHCP gives the phone a new IP and auto sync fails forever. The FGS notification text is computed once at start (android/.../server/LinkService.kt:33) and goes stale (R2-F9, R2-I9).

## Use case / flow
[08 auto sync](../flows/08-auto-sync.md)

## Proposed approach
- Phone: register a `ConnectivityManager.NetworkCallback` while LinkService runs. On a link-property change, refresh the FGS text and rebind the server to the new wlan address (Wi-Fi binding from 032).
- The Link screen shows the current URL + a QR code (URL only, never the token) for `fintrack setup --phone-url`.
- PC: on `unreachable`, say plainly "Phone not reachable at 192.168.1.5:8765 — check the address on the phone's Link screen". Store `last_good_url`. `fintrack setup --phone-url` keeps the other config fields (see 038).
- No mDNS/NSD discovery (keeps the attack surface small).

## Acceptance criteria
- [ ] Simulated IP change → the FGS text updates within 5 s, and the server answers on the new address.
- [ ] The Link screen QR decodes to `http://<ip>:8765` and contains no token.
- [ ] PC unreachable message includes the configured address.
- [ ] USB mode is unaffected.

## Test fixtures needed
A fake network callback in Robolectric, and a PC message test.

## Files likely touched
`server/LinkService.kt`, `ui/screens/LinkScreen.kt`, `pc/fintrack_pc/sync.py`, `cli.py`, `config.py`.

## Out of scope / open questions
- With 032, Wi-Fi becomes opt-in, so this matters less. Keep it P2.
- QR generation without adding a dependency: hand-rolled or skip the QR. Open question.
