---
title: Add `fintrack watch` for automatic, change-driven PC sync with backoff
labels: [area/sync, area/pc, priority/P1, size/M]
milestone: M3 Alerts & auto-sync
depends_on: [003, 034]
suggested_agent: sonnet · high
---
## Problem
Sync runs only when called from the CLI, the console button or MCP `sync_phone` (pc/fintrack_pc/sync.py:43-51; cli.py; console.py; mcp_server.py:83-86). Labels and new phone rows sit unsynced until the user remembers (R2-F3, R2-I7). Security: auto sync must stop on 401 and must not keep using a token against a changed device (R4-R14, R4-R15, SEC-14).

## Use case / flow
[08 auto sync](../flows/08-auto-sync.md)

## Proposed approach
- `fintrack watch [--interval 60]`: loop → `GET /api/status` → if `max_version` ≠ the stored `phone_version` or local `push_pending` > 0 → `sync()`. Otherwise sleep.
- One file/DB lock is shared by watch, manual CLI sync, console and MCP. A concurrent call waits ≤30 s, then reports "sync in progress".
- Errors: PhoneError/timeout → exponential backoff 1→15 min with ±20% jitter, reset on success. Write `meta.sync_state` (ok|unreachable|auth|device_changed|schema), `last_error`, `last_ok_at` and `next_try_at`.
- 401 → `auth`, stop retrying until config changes ("re-pair needed"). A device_id change → `device_changed`, stop (pin from 034).
- Never log the token or the Authorization header.
- `fintrack console --auto-sync` runs the same loop in a thread. Docs: Task Scheduler "At log on" example.

## Acceptance criteria
- [ ] Phone max_version unchanged and nothing pending → no `/api/changes` call (mock).
- [ ] New phone rows → one sync, then idle.
- [ ] 3 consecutive connection errors → delays ~1, 2, 4 min (with jitter), then the state is `unreachable`.
- [ ] 401 → state `auth`, no further requests in the next 10 loop ticks.
- [ ] Manual `fintrack sync` during a watch sync → serialized, no double push.
- [ ] Log output contains no token (a test greps the captured logs).

## Test fixtures needed
Fake PhoneClient with a scripted status/err sequence, and a frozen clock.

## Files likely touched
`pc/fintrack_pc/cli.py`, `sync.py`, `store.py` (meta), `console.py`, `phone.py`, `pc/tests/test_store_sync.py`, README section.

## Out of scope / open questions
- Phone → PC push is not planned (NAT, no PC listener) (R2 flows).
- Optionally re-running `adb forward` in USB mode when unreachable: open question.
