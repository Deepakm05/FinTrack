---
title: Surface PC sync status in the console, Windows toasts and MCP
labels: [area/pc, area/sync, priority/P2, size/S]
milestone: M3 Alerts & auto-sync
depends_on: [026]
suggested_agent: sonnet · medium
---
## Problem
Once sync runs in the background (026), failures are invisible. The console header shows only when the PC last synced (pc/fintrack_pc/static/console.html:104). MCP `get_period` reports last sync but not failure (mcp_server.py:55-61) (R2-F8, R2-I8).

## Use case / flow
[08 auto sync](../flows/08-auto-sync.md)

## Proposed approach
- `GET /api/sync-status` on the console (same Host check + X-FinTrack header) returns `sync_state`, `last_ok_at`, `last_error` (sanitized), `next_try_at` and `pending_push`.
- Console banner: amber "Phone unreachable since 14:05 — is PC link on and USB connected?". Red "Re-pair needed (token rejected)" or "Different phone detected".
- Windows toast after N minutes failing (default 30), once per state change, via a dependency-free fallback (PowerShell `New-BurntToastNotification` if present, otherwise skip). Optional.
- MCP `get_period` and `sync_phone` include `sync_state`, so Claude can tell the user.

## Acceptance criteria
- [ ] State `unreachable` for 35 min → one toast (mocked notifier), not repeated every loop.
- [ ] Console shows the red banner for `auth`, and the banner disappears after a successful sync.
- [ ] `get_period` JSON contains `sync_state`.
- [ ] `last_error` never contains the token or the full URL with a query string.

## Test fixtures needed
Console test client against seeded meta states.

## Files likely touched
`pc/fintrack_pc/console.py`, `static/console.html`, `mcp_server.py`, `store.py`, `pc/tests/test_console.py`, `test_mcp.py`.

## Out of scope / open questions
- Console auth hardening (per-session token) is in 040. This endpoint must adopt it when that lands.
