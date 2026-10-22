---
title: Harden amount and direction extraction in SMS and notification parsers
labels: [area/parser, priority/P0, size/M]
milestone: M1 Accurate capture core
depends_on: [001, 002]
suggested_agent: opus · high
---
## Problem
- **First amount wins**: "You saved ₹50 · Order total ₹349" or "Item total ₹420, paid ₹349" stores the wrong amount, so the link fails and the order is counted twice (android/.../core/SmsParser.kt:113-123; NotificationParser.kt:105; R1M-F7, R1B-F11).
- **Balance filter without word boundary**: `NOT_SPEND_CONTEXT = (bal|balance|avl|…)` (SmsParser.kt:45, :125-130) matches inside "Iqbal", "Balaji", "Global" and "Kabali", so the amount is skipped and the SMS silently dropped (R1M-F8, R1B-F7).
- **Card bill**: "Payment of Rs 5,000 received towards your Credit Card" → DEBIT, because "payment of" comes before "received" (SmsParser.kt:25-29, :94-104). Counted as spend on top of the account debit (R1M-F10, R1B-F6).
- **Over-broad SKIP**: `pending` and `added to` drop "₹349 paid · cashback pending" (NotificationParser.kt:38-42; R1B-F23).
- **Autopay pre-debit notices** ("will be debited on 15-10") are skipped already. Execution SMS must count (R3B §Signal coverage) — keep and test.

## Use case / flow
[01](../flows/01-sms-capture.md), [02](../flows/02-food-quick-commerce-order.md), [04](../flows/04-refunds-reversals-wallet-topups.md)

## Proposed approach
- Amount candidates are scored: near paid/debited/charged/order total/total paid/amount paid → preferred. Near saved/discount/MRP/item total/cashback/you save/delivery fee waived → excluded. Balance/limit → excluded. Ties go to the first.
- Balance context: word-boundary regex `(?<![a-z])(bal|balance|avl bal|available|limit|outstanding|o/s)(?![a-z])`.
- Card-bill phrases ("received towards your (credit )?card", "payment … credited to your card", "thank you for your payment of") → kind TRANSFER, direction as stated.
- SKIP becomes phrase-level: "payment pending", "is pending", "processing", "money added to wallet".

## Acceptance criteria
- [ ] `You saved ₹50 on this order. Order total ₹349` → ₹349.
- [ ] `Item total ₹420 · Discount ₹71 · Paid ₹349` → ₹349.
- [ ] `Rs 500 paid to Balaji Stores from A/c XX1234. Avl Bal Rs 12,000` → ₹500 DEBIT.
- [ ] `Iqbal paid you ₹500` (GPay) → ₹500 CREDIT.
- [ ] `Payment of Rs 5,000.00 received towards your Credit Card XX5678` → kind TRANSFER, excluded from spend.
- [ ] `₹349 paid to Swiggy · cashback pending` → ₹349 DEBIT.
- [ ] All existing SmsParserTest/NotificationParserTest cases still pass. The 001 fixtures for this issue are un-ignored.

## Test fixtures needed
From 001: saved/total, item total, Balaji/Iqbal/Global Mart, card bill, cashback-pending.

## Files likely touched
`core/SmsParser.kt`, `core/NotificationParser.kt`, `core/Models.kt` (kind on ParsedSms), tests. PC `textrules.py` amount helper if shared.

## Out of scope / open questions
- Wallet/UPI Lite TRANSFER rules are in 012. Refund/reversal rules are in 014.
- Timezone at parse (R1B-F22) is left as is (low). Noted for 008.
