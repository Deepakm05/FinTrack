# 04 — Refunds, reversals and wallet top-ups

**Actors:** user, merchant app (refund notification / SMS), bank (credit or reversal SMS, days later), wallet (Swiggy Money, Zomato Money, Ola Money, Uber Cash, Rapido Wallet, UPI Lite), FinTrack parsers / matcher / totals.

**Preconditions:** the original payment was captured, or it was a wallet / cash payment with no bank signal.

## Current flow

### Refund
1. Zomato "Refund of ₹220 initiated / will be credited to your original payment method" → MERCHANT_REFUND → CREDIT immediately (NotificationParser.kt:80, :120-126). ⧉ The bank credit arrives 3–7 days later, outside the 24 h WEAK window (DuplicateMatcher.kt:16, :63), so the refund is counted twice (R1M-F9, R1B-F15). Some refunds take more than 8 days (R3M §Refunds, anecdotal).
2. ⚠ Spend totals never net refunds. Home shows debits only (R1M-F9; ui/model/UiModels.kt).

### Reversal of a failed UPI payment
3. Bank "Rs 349 debited…" is counted. Later "Rs 349 reversed to A/c XX1234 as txn failed" is dropped because NOT_A_TXN contains "failed" (SmsParser.kt:22). ⚠ The debit stays counted (R1B-F10, R1M-F9).

### Wallet top-up
4. Bank "Rs 1000 debited, trf to Swiggy" is counted as spend (SmsParser.kt:19-23 has no top-up rule) (R1M-F3, R1B-F9).
5. Swiggy "₹1000 added to Swiggy Money" is silently skipped (NotificationParser.kt:38-42 `added to`). ⚠ No record of the top-up.
6. ⧉ A later "₹349 paid using Swiggy Money" is counted as spend again, so ₹1,349 is counted for ₹1,000 of real money out.
7. UPI Lite load and spends: the SMS rules don't skip Lite loads, so the load and each Lite spend both count (R1B-F9).
8. ⚠ The SKIP list contains `pending` and `added to`, so a real payment with the footer "cashback pending" is dropped (R1B-F23).

### Card bill
9. "Payment of Rs 5,000 received towards your Credit Card XX5678" = DEBIT, plus the account debit SMS for ₹5,000 = DEBIT. Both are BANK, so they never link, and the bill is counted twice on top of the purchases (SmsParser.kt:25-29, :94-104; R1M-F10, R1B-F6).

## Target flow

1. New `kind` column: SPEND / REFUND / TRANSFER (002). Totals = SPEND − matched or standalone REFUND. TRANSFER rows are shown in the list, greyed out and excluded from totals (012).
2. **Wallet top-up** ("added to <brand> Money/Wallet/Cash", "add money", "UPI Lite", "load", bank "trf to Swiggy" next to a top-up notification) = TRANSFER. A bank debit whose payee is a wallet brand within 10 min of a top-up notification links to it, and both stay TRANSFER (012).
3. **Wallet spend** ("paid using Swiggy Money", "paid via Ola Money") = SPEND from the merchant notification, with no bank counterpart expected (012).
4. **Refund initiated** = REFUND in a pending state. It is matched to a bank CREDIT within 14 days that has a brand/alias mention or the word "refund": AUTO when ≤10 days and mentioned, POSSIBLE otherwise. An unmatched pending refund counts once (014).
5. **Reversal**: "reversed / reversal / credited back" wins over "failed" → CREDIT of kind REFUND, linked to the original debit by the same amount and keyed ref hash, or by amount ≤3 days. Net = 0 (014).
6. **Card bill**: "received towards your card" / "payment … credit card" = TRANSFER, and the account debit to the card issuer links to it as TRANSFER (005).
7. **Cancellation fee**: "Cancellation fee ₹50 charged" = SPEND ₹50 even though "cancelled" is in the text (014).
8. Narrow SKIP: drop `pending` and `added to` as global skips. Use them only in phrase form ("payment pending", "money added to wallet") (005, 012).

## Edge cases

- Partial refund (₹349 order, ₹120 refunded): match on the refund amount, not the order amount.
- Refund to Zomato Money instead of the source = a CREDIT to the wallet with no bank signal. It is a REFUND that nets spend, with no bank match expected.
- Ola card hold reversal (flow 03): REFUND/TRANSFER pairing so net = 0.
- Two refunds of the same amount from different merchants in the same week: the brand mention is required for AUTO.
- Example (made up): Zomato `Refund of ₹220 initiated for order #XXXX` (12 Oct) → `VM-ICICIB-S: Rs 220.00 credited to A/c XX1234 by ETERNAL LTD refund` (16 Oct) → one ₹220 refund, and October spend reduced by ₹220.

## Issues

[002](../issues/002-db-schema-v2-migration.md) · [005](../issues/005-parser-amount-direction-hardening.md) · [012](../issues/012-wallet-topups-as-transfers.md) · [013](../issues/013-ride-payments-driver-upi-and-card-hold.md) · [014](../issues/014-refunds-reversals-cancellation-fees.md)
