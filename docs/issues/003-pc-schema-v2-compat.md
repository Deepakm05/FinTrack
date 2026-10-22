---
title: Make the PC accept versioned phone schemas, new source types and kinds
labels: [area/pc, area/sync, priority/P1, size/S]
milestone: M1 Accurate capture core
depends_on: [002]
suggested_agent: sonnet · medium
---
## Problem
The PC hard-codes the phone source types `PHONE_SOURCES = ("BANK","DELIVERY","UPI_APP","MERCHANT_APP")` (pc/fintrack_pc/store.py:70). It derives `PUSH_FLAG` (:74) and ranking (pc/fintrack_pc/reconcile.py:28-31) from them. A new type such as `BANK_APP` would rank as a statement, be excluded from push and be compared with phone rows by the PC (reconcile.py:43-44). Spend totals ignore the planned `kind` (TRANSFER/REFUND). The PC never checks the phone's schema version (R1M-F19, R1B-F18, R1B-I9).

## Use case / flow
[08 auto sync](../flows/08-auto-sync.md), [05 picker](../flows/05-app-picker-setup.md)

## Proposed approach
- Read `schema_version` + `source_types` from `/api/status`. Refuse to sync (clear error) on an unknown major version.
- Store `source_type`, `kind`, `channel` and `origin_pkg` as received. Treat any type the phone lists as a phone source (a dynamic set persisted in `meta`) instead of a constant.
- `spending_summary` / console totals: SPEND counts, REFUND nets, TRANSFER excluded.
- Rank: BANK, BANK_APP, UPI_APP, MERCHANT_APP, DELIVERY (all phone, rank 0 against email/statement).

## Acceptance criteria
- [ ] Syncing a v2 phone row with `source_type=BANK_APP` stores it, pushes its labels and ranks it as a phone source.
- [ ] A `kind=TRANSFER` ₹1000 row does not appear in `spending_summary` totals. A `kind=REFUND` ₹220 row reduces the category total by ₹220.
- [ ] Phone `schema_version: 99` → sync aborts with "Phone app is newer than this PC tool — update fintrack".
- [ ] Old v1 phone (no schema_version) still syncs.

## Test fixtures needed
Fake `/api/status` and `/api/changes` payloads in `pc/tests/tests_helpers.py` (v1 and v2).

## Files likely touched
`pc/fintrack_pc/store.py`, `reconcile.py`, `sync.py`, `phone.py`, `mcp_server.py` (summary), `pc/tests/test_store_sync.py`, `test_reconcile.py`.

## Out of scope / open questions
- Device-id pinning and uid namespacing are in 034.
- Tombstones for deselected apps (R1B P3) are deferred. Rows are kept by product decision.
