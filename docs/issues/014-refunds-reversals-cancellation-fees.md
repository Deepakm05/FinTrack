---
title: Handle refunds, failed-payment reversals and cancellation fees without double counting
labels: [area/parser, area/matching, priority/P1, size/M]
milestone: M2 Merchant apps
depends_on: [002, 005, 009]
suggested_agent: opus · high
---
## Problem
- ⧉ "Refund of ₹220 initiated / will be credited" from Zomato or Swiggy becomes a CREDIT immediately (android/.../core/NotificationParser.kt:80, :120-126). The bank credit comes 3–7 days later (sometimes more than 8, R3M §Refunds), outside the 24 h window (DuplicateMatcher.kt:16, :63), so it is counted twice (R1M-F9, R1B-F15).
- ⚠ A bank reversal "Rs 349 reversed to A/c XX1234 as txn failed" is dropped because NOT_A_TXN contains `failed` (SmsParser.kt:22), so the original debit stays counted (R1B-F10).
- ⚠ Totals never net refunds (R1M-F9).
- "Cancellation fee ₹50 charged" contains "cancelled"-type words and may be skipped by MERCHANT_SKIP (NotificationParser.kt:79). It must count (R1M-I4).

## Use case / flow
[04](../flows/04-refunds-reversals-wallet-topups.md)

## Proposed approach
- Kind REFUND for refund/refunded/credited back/reversed/reversal. A refund **initiated** is `pending=true`.
- Reversal wins over failure: if the text has reversal words and an amount, parse it as CREDIT/REFUND even if it says "failed".
- Refund matching: a REFUND notification ↔ bank CREDIT within **14 days** with the same amount and (catalog mention OR the word "refund"/"reversal") → AUTO if ≤10 days and mentioned, otherwise POSSIBLE. A reversal ↔ original debit by ref_hash, or by amount ≤3 days.
- Totals: spend = SPEND − counted REFUND (per period). A pending refund counts once. When its bank credit links, the credit is the main record and the notification becomes a dup.
- Cancellation fee phrase → SPEND even with cancel words. "Ride cancelled" without a fee → ignored.
- PC `reconcile.py` uses the same 14-day window for email refunds.

## Acceptance criteria
- [ ] Zomato `Refund of ₹220 initiated` (12 Oct) + bank `Rs 220.00 credited … ETERNAL refund` (16 Oct) → one ₹220 refund. October spend is reduced by ₹220 once.
- [ ] Bank `Rs 349 debited` + `Rs 349 reversed to A/c XX1234 as txn failed` → net 0.
- [ ] Partial refund ₹120 on a ₹349 order → net ₹229.
- [ ] Rapido `Cancellation fee ₹50 charged` → ₹50 SPEND. `Your ride was cancelled` → nothing.
- [ ] Refund credit 13 days later without mention → POSSIBLE. 16 days later → unlinked.

## Test fixtures needed
From 001: refund lag, reversal "failed", partial refund, cancellation fee.

## Files likely touched
`core/SmsParser.kt`, `core/NotificationParser.kt`, `core/DuplicateMatcher.kt`, `data/TransactionStore.kt`, `ui/model/UiModels.kt` (net totals), `pc/fintrack_pc/reconcile.py`, tests.

## Out of scope / open questions
- **Conflict resolved**: R1M proposed a 10-day refund window, R3M proposed 14. This issue searches 14 days and allows AUTO only ≤10 days with a mention.
- Refunds into wallets (Zomato Money) are REFUND with no bank match expected.
