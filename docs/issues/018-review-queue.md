---
title: Add a masked review queue for rejected texts that carried an amount
labels: [area/parser, area/notifications, priority/P1, size/M]
milestone: M2 Merchant apps
depends_on: [002, 010, 015]
suggested_agent: sonnet · high
---
## Problem
Every rejection is silent. Parser returns are null with no reason (android/.../core/SmsParser.kt:64-78; NotificationParser.kt:89-105). Exceptions go to logcat only (notify/PaymentNotificationListener.kt:34-38; sms/SmsReceiver.kt:27-28). Store conflicts are ignored (data/TransactionStore.kt:77). The user cannot see or recover a missed payment (R1M-F15, R1B-F8, R2-F6; R1M-I6, R1B-I10).

## Use case / flow
[01](../flows/01-sms-capture.md), [05](../flows/05-app-picker-setup.md)

## Proposed approach
- Parsers return `ParseResult = Accepted(parsed) | Rejected(reason, amountPaise?) | Ignored(reason)`. Reasons: `no_amount`, `no_txn_verb`, `promo`, `skip_phrase`, `reminder`, `numeric_sender`, `otp` (otp is never queued).
- Queue only when `Rejected` **and** a currency amount was found **and** the origin is a selected app, a bank-like SMS or a numeric bank-like sender (008). Otherwise only increment `capture_stats`.
- Store: hardened masked text ≤200 chars, origin, reason, amount, seen_at. Guardrails from 015: 7-day purge, 300-row cap, never synced or exposed to MCP.
- UI: a "Review (3)" chip on Home → a list with **Add as payment** (amount and direction editable, inserted as a normal row with `parser=manual`, synced) and **Ignore**. "Delete all".
- Exceptions in capture → counter `errors` + review entry only if the text had an amount.

## Acceptance criteria
- [ ] `Rs 2,100 transferred via IMPS to RAMESH K` from an unknown alphanumeric sender without a/c/card/UPI words → review row `no_txn_verb` or `unknown_sender`, masked.
- [ ] `50% off on orders above ₹199` → counter only, no review row.
- [ ] `123456 is your OTP for ₹349` → no review row.
- [ ] Add as payment → a txn row appears, is synced on the next `/api/changes`, and the review row is removed.
- [ ] A review row 8 days old is gone after the daily job.

## Test fixtures needed
Rejected-with-amount and rejected-without-amount cases per reason (made up).

## Files likely touched
`core/SmsParser.kt`, `core/NotificationParser.kt`, `core/Models.kt`, `sms/SmsIngest.kt`, `notify/PaymentNotificationListener.kt`, `data/TransactionStore.kt`, new `ui/screens/ReviewScreen.kt`, `ui/model/UiModels.kt`.

## Out of scope / open questions
- Retention: 7 days (security) instead of 30 days (capture reports). See the 015 conflict note.
- Exporting review rows as test fixtures is in 019.
