---
title: Add a conservative generic parser and the BANK_APP source type for picker-selected apps
labels: [area/parser, area/matching, priority/P1, size/M]
milestone: M2 Merchant apps
depends_on: [016]
suggested_agent: opus · high
---
## Problem
Apps the user selects that have no tuned rules need a parser that rarely produces false positives. Bank-app notifications have no source type today. Using BANK would make "bank app notif + same bank's SMS" a same-type pair that is never linked (android/.../core/DuplicateMatcher.kt:50; R1B-F5, R1B-I8). `rank()` (:38-43) and the PC rank have only 4 types (R1M-I9, R3B issue 3).

## Use case / flow
[05](../flows/05-app-picker-setup.md)

## Proposed approach
- `SourceType.BANK_APP`. Rank: BANK 0, BANK_APP 1, UPI_APP 2, MERCHANT_APP 3, DELIVERY 4. A safe enum fallback already exists (002). The PC is aware of it (003).
- Generic parser: accept only if (a) there is a currency amount (₹/Rs/INR), (b) a completed-transaction verb (debited, credited, paid, sent, received, spent, charged, refunded), (c) no SKIP/PROMO/REMINDER/OTP phrase, and (d) direction is unambiguous. Otherwise, with a currency amount → review queue (018) with the reason, without one → counter only (R4-R4, R4-R5).
- Merchant-type generic apps: debit only on "paid/charged/payment successful". Refund → CREDIT.
- Matching for generic rows: AUTO only with a label-name mention or ≤10 min. BANK_APP ↔ BANK of the same bank (by catalog bank key) ≤10 min → AUTO.
- A `parser` column (`tuned`/`generic`) is shown in the detail sheet.

## Acceptance criteria
- [ ] A selected bank app `Rs 1,250.00 debited from A/c XX1234 to AMAZON PAY` → BANK_APP DEBIT ₹1,250. HDFC SMS with the same amount 1 min later → AUTO link, the SMS is main.
- [ ] `Get ₹100 cashback on your next payment` → not stored, not reviewed (promo).
- [ ] `Your statement of ₹4,560 is ready` → review queue (amount, no completed verb), reason `no_txn_verb`.
- [ ] `₹299 paid for Premium plan` from a generic Merchant app → MERCHANT_APP DEBIT.
- [ ] An unknown enum string from a newer DB → fallback, no crash.

## Test fixtures needed
Generic bank-app and merchant-app texts (made up), negatives (promo, statement ready, OTP).

## Files likely touched
`core/Models.kt`, new `core/GenericParser.kt`, `core/NotificationParser.kt`, `core/DuplicateMatcher.kt`, `core/TitleExtractor.kt`, `ui/screens/TxnDetailSheet.kt`, tests.

## Out of scope / open questions
- **Conflict resolved**: R1M-I9 offered "BANK_APP type or a channel-aware matcher". This issue uses BANK_APP **and** the `channel` column from 002.
- Tuned rules for new apps come later, from samples collected via 019 (R3B issue 4).
