---
title: Show an inferred payment method on each merged payment
labels: [area/matching, priority/P2, size/M]
milestone: M2 Merchant apps
depends_on: [011, 012, 013]
suggested_agent: opus · medium
---
## Problem
Users can't tell why a Swiggy order has no GPay notification, or why a ride shows a person's name. In-app UPI (Swiggy since Aug 2024, Zepto since Jan 2026) normally produces **no** UPI-app notification. Wallet, COD and cash produce no bank signal (R3M signal matrix, rec 2; R3M issue 2). Without a method tag, "missing" signals look like capture bugs, and the health/coverage logic (030) cannot tell normal from abnormal.

## Use case / flow
[02](../flows/02-food-quick-commerce-order.md), [03](../flows/03-ride-payment.md)

## Proposed approach
- Derive `method` from the merged group: UPI intent (bank + UPI-app + merchant), in-app UPI (bank + merchant, no UPI-app), wallet (merchant "paid using … Money"), card (issuer card SMS), cash/COD (merchant/ride only + "cash"/"COD", or no debit within the window), to driver (013 link), unknown.
- Display only: a chip in the detail sheet, "Paid via in-app UPI · 2 sources". It **never** changes totals or links.
- Synced as an informational field (optional for the PC).

## Acceptance criteria
- [ ] Bank `Rs 349 debited … SWIGGY` + Swiggy `You paid ₹349` with no GPay → "In-app UPI".
- [ ] Bank + GPay + Swiggy → "UPI (Google Pay)".
- [ ] Swiggy `₹349 paid using Swiggy Money` → "Wallet".
- [ ] Rapido ride with no debit in 30 min → "Cash".
- [ ] Totals identical before and after the feature (a regression test over the 001 scenarios).

## Test fixtures needed
Merged-group scenarios from 001.

## Files likely touched
New `core/PaymentMethod.kt`, `ui/model/UiModels.kt`, `ui/screens/TxnDetailSheet.kt`, `server/ApiServer.kt` (optional field).

## Out of scope / open questions
- The signal matrix timing and the in-app UPI rollout per user are **unverified**. The tag may be wrong. Label it "likely".
