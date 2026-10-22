---
title: Merge same-source order and ride lifecycle signals into one record
labels: [area/matching, priority/P0, size/L]
milestone: M2 Merchant apps
depends_on: [001, 007, 009]
suggested_agent: opus · high
---
## Problem
Two signals from the same source type are never linked (android/.../core/DuplicateMatcher.kt:50). Candidates already linked to a row of the same type are excluded (data/TransactionStore.kt:117 `NOT EXISTS`). So:
- ⧉ Swiggy "Order placed ₹349" + "You paid ₹349" = 2 rows. Two Zomato SMS for one order = 2 rows (R1M-F1, R1B-F5).
- ⧉ Rapido "Pay ₹103 to your captain" (RIDE_DONE, NotificationParser.kt:72-77, :101-102) + "Payment of ₹103 received" (MERCHANT_PAID :67-70) = 2 rows. Plus a UPI debit, that is up to 3× the spend (R1M-F1, R1M-F5).
- Card SMS + account SMS from the same bank, and (after the picker) bank-app notification + bank SMS, are also same-type pairs (R1B-F5).

## Use case / flow
[02](../flows/02-food-quick-commerce-order.md), [03](../flows/03-ride-payment.md)

## Proposed approach
- A same-source rule in the matcher, applied when `brand(a) == brand(b)` (catalog 004), the source is MERCHANT_APP or DELIVERY, the direction is the same, and |Δamount| ≤ ₹2. Window ≤3 h for food/quick commerce, ≤2 h for rides, measured from the earlier signal.
- **AUTO** when at least one of the pair is a lifecycle stage (placed / confirmed / delivered-with-amount / ride completed / pay-to-captain / payment received). **POSSIBLE** when both are plain "you paid" (it could be a second order).
- Main record: the most final stage (paid > delivered > placed). Its amount wins (tip/adjustment within ±₹2). The other stages link to it as AUTO dups. A later cross-source bank SMS links to the main record (one level deep, as today).
- UPI_APP and BANK same-source pairs **never** merge without the same ref_hash (two ₹20 teas stay two).
- Relax the `NOT EXISTS` candidate filter for same-brand lifecycle pairs.
- The PC receives `dup_of` normally (no PC change beyond 003).

## Acceptance criteria
- [ ] Swiggy `Order placed · ₹349` (19:02) + `You paid ₹349` (19:03) + `Delivered!` (19:40, no amount) → 1 counted row ₹349, and "Delivered" creates no row.
- [ ] Rapido `Pay ₹103 to your captain` + `Payment of ₹103 received` → 1 row.
- [ ] Zomato SMS `Order placed ₹512` + Zomato SMS `Payment of ₹512 confirmed` → 1 row.
- [ ] Two Swiggy `You paid ₹199` 50 min apart, no placed stage → POSSIBLE, both counted until confirmed.
- [ ] Two GPay `Paid ₹20 to RAMESH TEA` 5 min apart (different refs) → 2 rows.
- [ ] Uber `Trip total ₹212` then `Fare updated: ₹213.50` → 1 row ₹213.50.

## Test fixtures needed
From 001: Swiggy/Instamart/Zomato/Blinkit/Zepto placed→paid→delivered, Rapido/Uber/Ola ride end + payment received.

## Files likely touched
`core/DuplicateMatcher.kt`, `core/NotificationParser.kt` (stage tag on ParsedSms), `core/SmsParser.kt` (stage tag for DELIVERY), `data/TransactionStore.kt` (candidates), tests.

## Out of scope / open questions
- **Conflict resolved**: R1B-I4 proposed AUTO ≤60 min for same-package pairs. R1M-I1 proposed ≤3 h food / ≤2 h rides with ±₹2. This issue uses the R1M windows but requires a lifecycle stage for AUTO.
- **Unverified**: real stage wordings and the gap between placed and paid for each app.
