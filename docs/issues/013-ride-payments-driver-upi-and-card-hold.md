---
title: Link ride-end records to UPI payments made to the driver, and handle Ola card holds
labels: [area/matching, priority/P1, size/M]
milestone: M2 Merchant apps
depends_on: [004, 009, 011]
suggested_agent: opus · high
---
## Problem
- A ride-end "Pay ₹103 to your captain" is recorded as a DEBIT (android/.../core/NotificationParser.kt:72-77, :101-102). That is right for cash. When the user pays the captain's **personal** VPA, the bank SMS/UPI notification names a person, so there is no brand mention and the link relies on time only (R1M-F5). A rounding difference (₹103 fare, ₹100 paid) means no link at all, so both count (R1M-F4). With the same amount and no mention, the current AUTO rule (≤2 h) can merge an unrelated payment (R1M-F11).
- Rapido auto captains and Uber/Ola pay-the-driver models make this the common case (R3M §Rapido/Uber/Ola, rec 4).
- Ola authorises the saved card for the estimated fare at booking, even for cash rides. The hold SMS counts as spend, and the reversal may be dropped or unlinked (R3M §Ola; R1B-F10).

## Use case / flow
[03 ride payment](../flows/03-ride-payment.md)

## Proposed approach
- Driver link: a candidate debit (BANK/UPI_APP) whose payee is a person (not a catalog brand or bank), within 0–30 min **after** the ride-end record, with |Δ| ≤ max(₹5, 3%) → POSSIBLE. AUTO only if it is the single candidate and the gap is ≤10 min. The main record stays the bank/UPI debit (rank). The title becomes "<Brand> ride · <person>".
- Cash: no candidate within 30 min → the ride record counts. The PC email "Paid by Cash" confirms it.
- Card hold: "authorised/blocked/hold/pre-auth … OLA" or ANI → kind TRANSFER, pending. A later reversal / "released" of the same amount ≤7 days → net 0. If no reversal and a card payment for the ride appears, convert to SPEND (or link).
- A notification tag `ride=true` comes from the catalog.

## Acceptance criteria
- [ ] Rapido `Ride completed. Pay ₹103 to your captain` (20:10) + GPay `Paid ₹100 to RAMESH KUMAR` (20:14) → one counted ₹100, POSSIBLE→AUTO (single candidate ≤10 min), title "Rapido ride · Ramesh Kumar".
- [ ] Same ride, no debit within 30 min → ₹103 counted (cash).
- [ ] Uber `Trip total ₹212` + bank `Rs 212 debited to SURESH P` 25 min later → POSSIBLE.
- [ ] Ola card `Rs 350 authorised on card XX5678 at ANI TECHNOLOGIES` + `Rs 350 hold released` next day → spend ₹0. The ride paid by cash ₹318 → ₹318.
- [ ] An unrelated `Rs 100 to KIRANA STORE` 90 min after a ₹100 ride → not linked.

## Test fixtures needed
From 001: ride cash, ride UPI-to-captain, fare rounding, Ola hold + release (made up).

## Files likely touched
`core/DuplicateMatcher.kt`, `core/TitleExtractor.kt`, `core/SmsParser.kt` (hold/release phrases), `data/TransactionStore.kt`, tests.

## Out of scope / open questions
- **Unverified**: the Ola card-hold wording and whether a release SMS is sent at all. Ride-end notification wording for all three apps.
- How to tell a person name from a merchant name for an unknown payee: heuristic (no "PVT/LTD/STORE/TECH" and 2–3 words). Needs real samples.
