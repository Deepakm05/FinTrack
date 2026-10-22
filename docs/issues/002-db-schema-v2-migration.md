---
title: Introduce a phone DB migration framework and schema v2
labels: [area/db, priority/P0, size/M]
milestone: M1 Accurate capture core
depends_on: []
suggested_agent: opus · high
---
## Problem
The schema is v1 and `onUpgrade` is a no-op (android/.../data/TransactionStore.kt:21, :51). `SourceType.valueOf` / `Direction.valueOf` throw on an unknown value (:200), so adding a new source type or downgrading crashes the app. Almost every planned feature needs new columns or tables: notification identity, kind (SPEND/TRANSFER/REFUND), origin package, channel, ref hash, app rules and the rejected/review table (R1M-F19, R1M-I7, R1B-F17, R1B-I6, R4 SEC-12 "schema v2 migration").

## Use case / flow
[00 overview](../flows/00-overview.md) and all capture flows.

## Proposed approach
- A stepwise `migrate(db, from, to)` with one function per version, all run in one transaction. `onDowngrade` refuses with a clear error (no silent data loss).
- v2 `txn` columns: `identity_key TEXT` (unique when not null), `notif_key TEXT`, `origin_pkg TEXT`, `channel TEXT` (SMS|NOTIFICATION), `kind TEXT NOT NULL DEFAULT 'SPEND'`, `ref_hash TEXT`, `parser TEXT` (tuned|generic), `category_source TEXT`.
- New tables: `app_rule(package PK, label, type, enabled, tuned, created_at, updated_at)`, `app_rule_history`, `rejected(id, origin, reason, masked_text ≤200, amount_paise, seen_at)`, `capture_stats(day, origin, seen, parsed, rejected, dropped)`, `meta(key, value)` for watermarks.
- Backfill: `channel` from `source_type`, `origin_pkg` from known sender keys, `kind = SPEND`. Recompute dedup keys only where an identity rule needs them.
- Safe enum mapping: an unknown `source_type`/`direction` maps to a fallback, the row is marked, and nothing crashes.
- `/api/status` adds `schema_version` and the supported `source_types` / `kinds`.

## Acceptance criteria
- [ ] Upgrading a v1 DB with 500 fixture rows keeps all rows, uids, versions and categories. The new columns are backfilled.
- [ ] A fresh install creates v2 directly. A test asserts `onCreate` == v1 + migrations (same schema dump).
- [ ] A row with `source_type = 'FUTURE_TYPE'` loads without a crash and is excluded from totals.
- [ ] Downgrade throws a handled error and is not silently wiped.
- [ ] `/api/status` returns `"schema_version": 2`.

## Test fixtures needed
A v1 DB built by the current `onCreate` with made-up rows (BANK ₹349 debit, MERCHANT_APP ₹349, linked dup), using Robolectric or an in-memory SQLite helper.

## Files likely touched
`data/TransactionStore.kt`, `data/TxnRow.kt`, `core/Models.kt` (Kind enum), `server/ApiServer.kt` (status), new `data/Migrations.kt`, tests.

## Out of scope / open questions
- PC changes are in 003.
- Whether `ref_hash` uses a device-local HMAC key stored in Prefs (proposed) or in the Keystore. Decide together with 007/010.
