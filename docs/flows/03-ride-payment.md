# 03 — Ride payment (Rapido, Uber, Ola)

**Actors:** user, ride app (ongoing trip notification, ride-end notification, email invoice), captain/driver (personal UPI ID), UPI app, bank / card issuer (SMS), FinTrack listener / parser / matcher, PC email import.

**Preconditions:** the ride app is selected and notification access is on. Payment can be cash, UPI to the captain's **personal** VPA, in-app UPI/card/wallet (Rapido Wallet, Ola Money, Uber Cash), or Ola's saved card (R3M §Rapido/Uber/Ola).

## Signals per method (R3M; wording and timing unverified)

| Method | Ride app | Bank / UPI | Email |
|---|---|---|---|
| Cash | ride-end "Pay ₹98" / "trip total ₹98" | none | invoice "Paid by Cash" |
| UPI to captain / driver | ride-end amount, maybe "payment received" | bank SMS + UPI notif naming a **person** ("RAMESH K") | invoice |
| In-app UPI / card | ride-end + "payment successful" | bank / issuer SMS ("UBER INDIA SYSTEMS", "ROPPEN") | invoice |
| Wallet (Rapido Wallet, Ola Money, Uber Cash) | ride-end + paid-by-wallet | none (the top-up made the debit) | invoice |
| Ola saved card | ride end | card **authorisation hold** for the estimated fare at booking, even for cash rides, later reversed | invoice |

## Current flow

1. During the trip the ride app posts an ongoing notification ("Ride fare ₹103 · captain 3 min away"). ⧉ No FLAG_ONGOING / foreground-service skip (listener :21-40). `fare` matches MERCHANT_PAID (NotificationParser.kt:67-70), and only "estimate" is skipped (:79). Each update has new text, so it adds a new DEBIT (TransactionStore.kt:211-215; R1M-F2, R1B-F4).
2. At ride end, "Pay ₹103 to your captain" matches RIDE_DONE → DEBIT (NotificationParser.kt:72-77, :101-102). This is right for cash.
3. ⧉ Then "Payment of ₹103 received" also matches MERCHANT_PAID ("payment received" = you paid). It is the same source type, so it is never linked: 2 rows (DuplicateMatcher.kt:50; R1M-F1, R1M-F5).
4. UPI to the captain: the bank SMS names a person, not the brand. The ride notification (MERCHANT_APP) and the bank SMS link only on exact amount and time (DuplicateMatcher.kt:60-65). ⚠/⧉ A cash rounding mismatch (₹103 fare, ₹100 paid) gives no link and both count (R1M-F4). ⧉ Same amount within 2 h with no mention = AUTO, so an unrelated ₹100 debit can be merged with a ₹100 cash ride (R1M-F11).
5. Ola card hold: the hold SMS is counted as a debit, and the reversal is dropped if it says "failed" or not parsed as a credit (R3M §Ola; R1B-F10). ⧉ Fare counted twice (once as the hold, once as the ride/cash).
6. Uber fare adjustment after the trip (₹212 → ₹198): exact-amount matching fails (R1M-F4).
7. The PC email invoice links to the phone UPI debit (README §7), but phone ↔ phone pairs are left to the phone (reconcile.py:43-44).

## Target flow

1. Drop ongoing / foreground-service / progress notifications from ride apps before parsing (006).
2. Identity `pkg + sbn.key + when`, so a ride-end notification updated in place stays one row (007).
3. Lifecycle merge: ride app, same direction, amount within ±₹2, ≤2 h → "Pay ₹103", "trip total ₹103" and "payment of ₹103 received" become one record (011).
4. Ride-to-driver linking: a bank / UPI debit to a **person-named** payee within 30 min after ride end, with an amount within max(₹5, 3%), becomes a POSSIBLE link to the ride record. The title is relabelled "Rapido ride" and shows the captain's name. With exactly one candidate and a gap ≤10 min, the link can be AUTO (013).
5. Cash: no bank signal, so the ride record is the spend. The PC email "Paid by Cash" confirms it.
6. Ola card hold: an authorisation / "blocked" / "hold" SMS is not a spend. It is a pending row (kind TRANSFER or hidden) that is cancelled by the matching reversal, or turns into spend when the ride is paid by that card (013, 014).
7. Wallet rides: SPEND from the ride notification. The earlier wallet top-up is a TRANSFER (012).
8. AUTO needs a mention or ≤10 min (009). One alert per ride (022).

## Edge cases

- The ride-end amount often comes **before** payment (R3B §Merchant focus). The lifecycle window starts at ride end.
- Two short rides of the same amount within 2 h (₹45 + ₹45): the second ride-end has a different `sbn.key`/`when` and no lifecycle pairing, so it stays separate (test fixture).
- Cancelled ride with a cancellation fee: "Cancellation fee ₹50 charged" = ₹50 SPEND, while "Ride cancelled" with no fee is ignored (014).
- Uber auto subscription model (Feb 2025): the fare is agreed with the driver, so the UPI debit names the driver (R3M §Uber).
- Example (made up): Rapido `Ride completed! Pay ₹103 to your captain` → GPay `Paid ₹100 to RAMESH KUMAR` 4 min later → one ride, ₹100 spent (POSSIBLE link, title "Rapido ride").

## Issues

[001](../issues/001-tests-first-merchant-fixtures.md) · [006](../issues/006-notification-text-extraction.md) · [007](../issues/007-stable-record-identity.md) · [009](../issues/009-cross-source-matching-tuning.md) · [011](../issues/011-order-ride-lifecycle-merge.md) · [012](../issues/012-wallet-topups-as-transfers.md) · [013](../issues/013-ride-payments-driver-upi-and-card-hold.md) · [014](../issues/014-refunds-reversals-cancellation-fees.md)
