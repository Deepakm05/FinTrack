# 08 — Auto sync (phone ↔ PC)

**Actors:** user, phone LinkService + ApiServer (NanoHTTPD :8765), PC `fintrack` CLI / console / MCP server, PC store, Windows Task Scheduler (optional), adb (USB mode).

**Preconditions:** the phone and PC are paired (token, and later a pinned certificate). The PC link is ON on the phone. USB mode needs `adb forward tcp:8765 tcp:8765`.

## Current flow

1. Sync (`pull → reconcile → push`, pc/fintrack_pc/sync.py:43-51) runs only from `fintrack sync` (cli.py), the console button (console.py) or the MCP `sync_phone` tool (mcp_server.py:83-86). ⚠ No auto sync (R2-F3).
2. Default link mode is **Wi-Fi**, plain HTTP, bound to 0.0.0.0 (data/Prefs.kt:29; server/LinkService.kt:32). ⚠ A token sniffed on the LAN gives full history (`/api/changes?since=0`), category wipe (`/api/push`) and a 10-year SMS rescan (`/api/import`) (server/ApiServer.kt:52-90; R4-S2).
3. The PC stores a fixed `phone_url` (pc/fintrack_pc/config.py:17). ⚠ A DHCP change breaks every sync silently (R2-F9).
4. ⚠ The PC trusts any "phone": a spoofed device can overwrite `email:`/`stmt:` rows (the uid is not namespaced), a device_id change silently resets the cursor, and bodies are not re-masked (pc/fintrack_pc/store.py:157-173; sync.py:12-15; R4-S3).
5. `lastPcSyncAt` changes on any pull or push. There is no failure state on either side (R2-F8).
6. Push retries are not automatic but are safe (`push_pending`, conflict-aware `mark_pushed`) (pc store.py:483-507; R2-F13).
7. NanoHTTPD has unbounded threads before auth, no body-size limit and leaks `e.toString()` (ApiServer.kt:36, :106-111; R4-S7).

## Target flow

1. **Safe transport first**: USB-only is the default and Wi-Fi is an explicit opt-in with a warning. When on, Wi-Fi binds to the wlan address, not 0.0.0.0 (032). Later: a self-signed TLS key in the Android Keystore, paired with `fintrack pair` over adb, and the PC pins the cert fingerprint + device_id (033).
2. `fintrack watch` (and optionally `fintrack console --auto-sync`) polls `GET /api/status` every 60 s. It compares `max_version` with the stored phone_version and the local `push_pending` count, skips if nothing changed, and otherwise runs `sync()` under a lock shared with manual sync (026).
3. Failure handling: PhoneError → backoff 1, 2, 4… capped at 15 min with jitter, reset on success. `meta.sync_state` = ok | unreachable | auth | device_changed, plus `last_error` and `next_try_at`. **401 stops retries** ("re-pair needed"). A device_id or cert change stops sync and asks for re-pairing (R4-R14, R4-R15; 026, 034).
4. The PC validates phone rows: the uid is namespaced by device_id, it refuses to overwrite `email:`/`stmt:` rows, it re-masks bodies with the shared masker, and it rejects unknown schema versions (003, 034).
5. Status surface: `/api/sync-status` on the console, a banner "Phone unreachable since 14:05 — is PC link on?", a Windows toast after N minutes of failing, and MCP `get_period` includes the sync state (027).
6. Phone address resilience: the Link screen shows the current URL + QR. A NetworkCallback refreshes the FGS text. The PC reports "unreachable" plainly and stores the last good URL. No mDNS (028).
7. Phone side: `/api/status` reports `schema_version` and the list of `source_types`. The phone records last pull and last push separately for the health card (002, 024).
8. Task Scheduler docs: run `fintrack watch` at logon (026).

## Edge cases

- Phone asleep / Doze: the FGS keeps the server up. Polls simply fail and back off.
- USB unplugged: `adb forward` disappears → unreachable state, auto-recovery when replugged and forwarded again (watch can optionally re-run `adb forward` if adb is on PATH — open question).
- Manual sync during watch: the lock serializes them. The second waits or returns "sync in progress".
- Token regenerated on the phone: 401 → stop → banner "Re-pair needed".
- Concurrent label edits (phone categorize, issue 029): the PC wins if its row changed since the last sync.

## Issues

[002](../issues/002-db-schema-v2-migration.md) · [003](../issues/003-pc-schema-v2-compat.md) · [026](../issues/026-pc-auto-sync-watch.md) · [027](../issues/027-pc-sync-status-surface.md) · [028](../issues/028-phone-address-resilience.md) · [032](../issues/032-usb-default-wifi-binding.md) · [033](../issues/033-pinned-tls-pairing.md) · [034](../issues/034-pc-validate-phone-rows.md) · [037](../issues/037-nanohttpd-limits.md)
