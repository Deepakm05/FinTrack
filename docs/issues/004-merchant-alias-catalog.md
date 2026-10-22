---
title: Create one merchant alias catalog (brand, packages, sender keys, legal names) and add Instamart
labels: [area/matching, area/parser, priority/P0, size/S]
milestone: M1 Accurate capture core
depends_on: []
suggested_agent: opus · medium
---
## Problem
Bank/UPI SMS often show a merchant's legal or old name. The current brand table (android/.../core/SenderClassifier.kt:9-27) does not know Bundl / Swiggy Limited (Swiggy), Blink Commerce (Blinkit), Kiranakart (Zepto), Roppen (Rapido), Eternal (Zomato) or Uber India Systems. So these payments get no brand bonus in matching (core/DuplicateMatcher.kt:52-58, :63), the weak link fails, and the title shows "Bundl Technologies" (core/TitleExtractor.kt:37-44) (R1M-F6, R3M alias table, R3M rec 1). Instamart (`in.swiggy.android.instamart`) is a separate app that is missing from `NotificationParser.APPS` (:16-33), so its notifications are ignored (R3M §Packages, rec 6). Brand data is scattered across SenderClassifier, NotificationParser, TitleExtractor and PC `textrules.py`.
Verified: Ola already has `"ani technologies"` (SenderClassifier.kt:26), and Swiggy already has sender key `INSTMT` + body name `instamart` (:10).

## Use case / flow
[02](../flows/02-food-quick-commerce-order.md), [03](../flows/03-ride-payment.md)

## Proposed approach
- A `MerchantCatalog` in `core/` with brand → {packages, SMS sender keys, body names, legal/old names, email sender domains, wallet names (Swiggy Money…), ride flag}. Kept **separate** lists for SMS text vs email/notification senders.
- SenderClassifier, TitleExtractor, NotificationParser.APPS and the matcher read from it. The PC `textrules.brand_in` mirrors it from a shared JSON test-vector file.
- Matching is word-boundary, not substring, to avoid the `UBER`/`SBI`/`UPI` collisions (R1B-F24).
- Add Instamart → brand Swiggy, type MERCHANT_APP.
- Ambiguity: "Eternal" maps to {Zomato, Blinkit}, so it is a mention for either.

## Acceptance criteria
- [ ] `Rs 349 debited A/c XX1234 to BUNDL TECHNOLOGIES PVT` → title "Swiggy", brand mention true for a Swiggy notification.
- [ ] `KIRANAKART TECHNOLOGIES` → Zepto. `BLINK COMMERCE` → Blinkit. `ROPPEN TRANSPORTATION` → Rapido. `UBER INDIA SYSTEMS` → Uber. `ETERNAL LIMITED` → Zomato or Blinkit.
- [ ] An Instamart notification `₹412 paid for your Instamart order` → MERCHANT_APP, title Swiggy Instamart.
- [ ] `"subscriber"` does not match Uber, and `"SBIN"` handling is unchanged.
- [ ] The PC test loads the same alias JSON and agrees on every vector.

## Test fixtures needed
An alias vector JSON (made-up SMS lines per alias) shared by `TitleExtractorTest`, `DuplicateMatcherTest` and `pc/tests/test_reconcile.py`.

## Files likely touched
New `core/MerchantCatalog.kt`, `core/SenderClassifier.kt`, `core/TitleExtractor.kt`, `core/NotificationParser.kt` (APPS), `pc/fintrack_pc/textrules.py`, tests.

## Out of scope / open questions
- **Unverified**: whether bank SMS actually show "Bundl", "Blink Commerce", "Kiranakart", "Roppen", "Eternal" or "ANI". Gateway names (Juspay, Cashfree, Razorpay, "pyu") are unconfirmed and are not added as aliases until seen (R3M).
- No published payee VPAs were found, so VPA-host matching is deferred.
