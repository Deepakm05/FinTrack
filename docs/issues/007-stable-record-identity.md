---
title: Give every capture a stable identity and update rows in place
labels: [area/db, area/matching, priority/P0, size/M]
milestone: M1 Accurate capture core
depends_on: [002, 006]
suggested_agent: opus · high
---
## Problem
`dedup_key = sha256(senderKey | minute | maskedBody)` (android/.../data/TransactionStore.kt:211-215) with `CONFLICT_IGNORE` (:77) causes three bugs:
- ⧉ An updated notification with new text becomes a new row (R1M-F2, R1B-F4).
- ⧉ One SMS seen by the receiver (SMSC timestamp) and by inbox import (DATE_SENT, or DATE when DATE_SENT=0, sms/SmsIngest.kt:35) can fall in different minutes → 2 rows (R1M-F13, R1B-F20, R1B-F14).
- ⚠ Two genuine ₹20 payments to the same payee in the same minute collapse into one, because the refs are masked to XXXX (core/Masker.kt:19; R1M-F14, R1B-F12).
`insert` returns only a Boolean, so the alerts (022) cannot tell new from updated.

## Use case / flow
[01](../flows/01-sms-capture.md), [02](../flows/02-food-quick-commerce-order.md), [06](../flows/06-capture-alert-and-categorize.md)

## Proposed approach
- **Notification identity** = `pkg + sbn.key + when`. A hit means the row is updated in place (amount, body, kind) with a version bump, and the matcher re-runs.
- **SMS identity** = `senderKey + maskedBody`, sent time within ±10 min (query on an index), plus `ref_hash` when present.
- **ref_hash** = HMAC-SHA256(device-local secret, raw UTR/ref digits) computed **before** masking. It is stored, never synced, and the raw ref is never stored. Two otherwise-identical texts with different ref_hash are different payments.
- `insert` returns `Outcome {NEW, UPDATED, LINKED_AUTO, LINKED_POSSIBLE, IGNORED_DUPLICATE}` with the uid.
- Rows from before the migration keep their dedup_key, and new rows also fill `identity_key`.

## Acceptance criteria
- [ ] The same SMS ingested at 10:00:58 (receiver) and with DATE 10:01:03 (import) → 1 row, outcome IGNORED_DUPLICATE.
- [ ] A Swiggy notification key `0|in.swiggy.android|42|null|10123` posted with `Order total ₹349`, then updated to `Order total ₹369` → 1 row with amount ₹369, version bumped, outcome UPDATED.
- [ ] Two GPay `Paid ₹20 to RAMESH TEA` in the same minute with refs 612300000001 / 612300000002 → 2 rows.
- [ ] A replay of 20 active notifications after reconnect (023) → 0 new rows.
- [ ] Outcome values are exposed to the callers (SmsIngest, listener).

## Test fixtures needed
Receiver/import timestamp pairs, notification key/update pairs, same-minute ₹20 pairs (from 001).

## Files likely touched
`data/TransactionStore.kt`, `sms/SmsIngest.kt`, `notify/PaymentNotificationListener.kt` (call site), `core/Masker.kt` (expose ref extraction), `data/Prefs.kt` (HMAC key), tests.

## Out of scope / open questions
- Same-source lifecycle merge (different notifications of one order) is in 011.
- **Conflict resolved**: masker hardening (010) masks refs ≥6 digits, which would worsen the collapse. ref_hash removes that dependency, so 010 depends on this issue.
