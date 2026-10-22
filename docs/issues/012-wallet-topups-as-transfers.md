---
title: Treat wallet and UPI Lite top-ups as transfers and count wallet spends once
labels: [area/parser, area/matching, priority/P0, size/M]
milestone: M2 Merchant apps
depends_on: [002, 005, 009]
suggested_agent: opus · high
---
## Problem
- ⧉ A bank SMS for a wallet load ("Rs 1000 debited … trf to Swiggy") counts as spend, because SmsParser has no top-up rule (android/.../core/SmsParser.kt:19-23). Each wallet-paid order is then counted again from the merchant notification (R1M-F3, R1B-F9).
- ⚠ The merchant's own "₹1000 added to Swiggy Money" is silently dropped by `added to|add money|top-?up` in SKIP (NotificationParser.kt:38-42), so there is no record that would let the bank debit be explained.
- UPI Lite: the load and every Lite spend are both counted (R1B-F9).
- Wallets in scope: Swiggy Money, Zomato Money (also usable on Blinkit since Jan 2025), Ola Money, Uber Cash, Rapido Wallet, Paytm wallet, UPI Lite (R3M; R3B §Signal coverage).

## Use case / flow
[04](../flows/04-refunds-reversals-wallet-topups.md), [02](../flows/02-food-quick-commerce-order.md), [03](../flows/03-ride-payment.md)

## Proposed approach
- Kind TRANSFER for: "added to <wallet>", "add money", "wallet (top-up|load|recharge)", "UPI Lite (load|top-up|added)", and a bank debit whose payee is a catalog wallet name.
- A bank TRANSFER-candidate debit to a wallet brand within 10 min of a wallet top-up notification → linked. Both are TRANSFER.
- A bank debit to a brand that is **not** paired with an order or top-up within the window stays SPEND (it is an ordinary order).
- Wallet spend ("paid using Swiggy Money", "paid via Ola Money", "Uber Cash applied") → SPEND, with no bank counterpart expected.
- Partial wallet: `₹100 from Swiggy Money + ₹249 via UPI` → SPEND ₹349 from the merchant record. The bank ₹249 links to it as a near-part (POSSIBLE with mention, from 009).
- Totals and the UI exclude TRANSFER and show it greyed: "Moved to Swiggy Money".
- Narrow the SKIP list (shared with 005).

## Acceptance criteria
- [ ] Bank `Rs 1000.00 debited from A/c XX1234 trf to SWIGGY` + Swiggy `₹1000 added to Swiggy Money` (2 min) → spend ₹0, one TRANSFER pair.
- [ ] Later `₹349 paid using Swiggy Money` → spend ₹349.
- [ ] `₹500 added to Rapido Wallet` + bank debit to `ROPPEN TRANSPORTATION` → TRANSFER.
- [ ] `₹2000 loaded to UPI Lite` → TRANSFER. A GPay Lite `Paid ₹30 via UPI Lite` → SPEND ₹30.
- [ ] Partial: Swiggy `Paid ₹349 (₹100 Swiggy Money + ₹249 UPI)` + bank `Rs 249 debited … SWIGGY` → spend ₹349 after confirm.
- [ ] A plain Swiggy order bank debit ₹349 with no top-up notification → SPEND.

## Test fixtures needed
From 001: wallet top-up/spend per wallet, UPI Lite load/spend, partial wallet.

## Files likely touched
`core/SmsParser.kt`, `core/NotificationParser.kt`, `core/MerchantCatalog.kt` (wallet names), `core/DuplicateMatcher.kt`, `data/TransactionStore.kt`, `ui/model/UiModels.kt` (totals), tests.

## Out of scope / open questions
- **Unverified**: whether bank SMS for wallet loads name the brand or a gateway, and the UPI Lite SMS behaviour (per-txn vs daily summary; sources conflict, R3M §Thresholds).
- Uber Cash details unverified. Ola Money Postpaid status unverified (R3M §Ola).
