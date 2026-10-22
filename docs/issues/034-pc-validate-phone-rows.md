---
title: Validate and namespace phone rows on the PC and pin the paired device
labels: [area/security, area/pc, priority/P1, size/S]
milestone: M4 Security hardening
depends_on: [003]
suggested_agent: opus · medium
---
## Problem
The PC trusts whatever answers at `phone_url` (R4-S3, SEC-4):
- `upsert_from_phone` does `ON CONFLICT(uid) DO UPDATE` (pc/fintrack_pc/store.py:157-173). The uid is not namespaced, so a spoofed "phone" can send `uid = email:…`/`stmt:…` and overwrite PC-only rows.
- A device_id change silently resets the sync cursor (pc/fintrack_pc/sync.py:12-15). A different phone, or an attacker, starts a fresh full pull.
- Bodies are stored as sent. A buggy or old phone that sends unmasked text puts raw numbers into the PC DB and MCP output.
Auto sync must stop on device change (R4-R14).

## Use case / flow
[08 auto sync](../flows/08-auto-sync.md)

## Proposed approach
- Accept phone rows only if `source_type` is in the phone's declared set (003) and the uid does not start with a PC prefix (`email:`, `stmt:`). Reject otherwise and count it.
- Pin `device_id` at first successful sync (`meta.paired_device_id`). A mismatch → `sync_state=device_changed`, no pull/push, and a clear CLI/console message with `fintrack pair --reset` to accept.
- Re-mask every incoming body with the shared masker (010 vectors) and cap the length to 1,000 chars.
- Validate types: amount int ≥0, direction/kind enums, ISO dates.

## Acceptance criteria
- [ ] A phone row with uid `email:abc` → rejected, the existing email row is unchanged.
- [ ] Status returns device_id `B` after pairing with `A` → sync aborts with `device_changed`, and the cursor is not reset.
- [ ] An incoming body `Ref 612345678901 Rs 349` is stored as `Ref XXXX Rs 349`.
- [ ] `amount_paise: "349"` (string) or negative → row rejected.

## Test fixtures needed
Malicious/malformed `/api/changes` payloads in `pc/tests/tests_helpers.py`.

## Files likely touched
`pc/fintrack_pc/store.py`, `sync.py`, `textrules.py`, `cli.py`, `pc/tests/test_store_sync.py`.

## Out of scope / open questions
- Cert pinning is 033. This issue pins only device_id.
