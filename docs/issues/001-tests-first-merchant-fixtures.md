---
title: Add failing test fixtures that document SMS + 7 merchant-app capture behaviour
labels: [area/parser, area/matching, priority/P0, size/M]
milestone: M1 Accurate capture core
depends_on: []
suggested_agent: opus · medium
---
## Problem
Current tests cover one happy path each for Swiggy, Uber, Rapido and Ola, plus Zomato refunds (android/app/src/test/java/com/fintrack/core/NotificationParserTest.kt:75-111). There are **no** Blinkit/Zepto notification tests. Ongoing rides, placed→delivered, wallet top-up/spend, tips, partial wallet, "saved ₹X" ordering, cancellation fees, legal-name payees and refund lag are all untested. DuplicateMatcherTest has no MERCHANT_APP, near-amount or refund-lag cases (R1M test gaps; R1M-I10). Every later M1/M2 issue needs these fixtures as its definition of done.

## Use case / flow
[01](../flows/01-sms-capture.md), [02](../flows/02-food-quick-commerce-order.md), [03](../flows/03-ride-payment.md), [04](../flows/04-refunds-reversals-wallet-topups.md)

## Proposed approach
- Write fixtures **as failing tests** marked with the issue that will fix them (e.g. `@Ignore("fixed by #011")`, or an `expectedFailures` list), so CI stays green and each later PR un-ignores its own tests.
- One shared fixture file per app (made-up texts) feeding the parser tests and a scenario-level "store + matcher" test that asserts the **counted total** for a sequence of signals.
- Mirror the scenarios that touch the PC in `pc/tests/test_reconcile.py`.

## Acceptance criteria
- [ ] Each of Swiggy, Instamart, Zomato, Blinkit, Zepto, Rapido, Uber and Ola has at least placed/paid/delivered (or ride ongoing/ended/paid), refund and promo fixtures.
- [ ] Scenario: Swiggy `Order placed ₹349` + `You paid ₹349` + bank `Rs 349 debited … SWIGGY LIMITED` → counted total ₹349 (fails today).
- [ ] Scenario: 5 ongoing `Ride fare ₹103 · captain 3 min away` updates → 0 rows (fails today).
- [ ] Scenario: Rapido `Pay ₹103 to your captain` + `Payment of ₹103 received` → ₹103 (fails today).
- [ ] Scenario: bank `trf to Swiggy Rs 1000` + `₹1000 added to Swiggy Money` + later `₹349 paid using Swiggy Money` → spend ₹349.
- [ ] Scenario: `You saved ₹50 · Order total ₹349` → amount ₹349.
- [ ] Scenario: `Paid to Balaji Stores Rs 500` → amount ₹500.
- [ ] Scenario: two GPay `Paid ₹20 to RAMESH TEA` 1 min apart with different refs → 2 rows (passes today only if refs differ before masking — document the result).
- [ ] Scenario: Zomato refund + bank credit 4 days later → one ₹220 refund.
- [ ] The current test suite still passes. New failing cases are skipped with an issue reference.

## Test fixtures needed
This issue **is** the fixtures. Use made-up amounts and masked numbers (XX1234), and person names like "RAMESH KUMAR".

## Files likely touched
`android/app/src/test/java/com/fintrack/core/{NotificationParserTest,SmsParserTest,DuplicateMatcherTest}.kt`, a new `MerchantScenarioTest.kt` + fixtures object, `pc/tests/test_reconcile.py`.

## Out of scope / open questions
- All notification wording is **unverified** (R3M §Notification wording). Fixtures are illustrative until device samples arrive through issue 019, then replaced.
- No production code changes.
