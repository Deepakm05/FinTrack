# 02 — Food / quick-commerce order (Swiggy, Instamart, Zomato, Blinkit, Zepto)

**Actors:** user, merchant app (notification + DLT SMS + receipt email), UPI app (GPay/PhonePe…), bank (SMS), FinTrack listener / parsers / matcher, PC reconcile.

**Preconditions:** SMS permission and notification access granted. The merchant app is selected (the current hard-coded `APPS` list, or the picker once 016 lands). Instamart (`in.swiggy.android.instamart`) is a **separate app that is not in APPS today**, so its notifications are ignored (R3M §Packages; NotificationParser.kt:16-33).

## Which signals each payment method produces (R3M; timing unverified)

| Method | Bank SMS | UPI-app notif | Merchant notif / SMS | Email (PC) |
|---|---|---|---|---|
| UPI intent (opens GPay/PhonePe) | yes, if above the bank threshold | yes | yes | yes |
| In-app UPI (Swiggy since Aug 2024, Zepto since Jan 2026; Zomato pilot) | yes | **no (normal)** | yes | yes |
| Wallet (Swiggy Money, Zomato Money, also usable on Blinkit) | **no** (the top-up made the debit) | no | yes | yes |
| Partial wallet + UPI | yes (the UPI part only) | maybe | yes (the full total) | yes |
| Card | issuer SMS (e.g. "pyu swiggy") | no | yes | yes |
| COD / cash | no | no | yes (maybe) | yes |
| Pay-later / BNPL | partner SMS later (unverified) | no | yes | yes |

## Current flow

1. The listener accepts only packages in `APPS` (notify/PaymentNotificationListener.kt:22). ⚠ Instamart is ignored.
2. Group summaries are dropped. Only TITLE + BIG_TEXT/TEXT are read (listener :24-28). ⚠ TEXT_LINES / SUB_TEXT content is missed (R1M-F16, R1B-F3).
3. MERCHANT_PAID matches paid/charged/order total/fare… (NotificationParser.kt:67-70). ⧉ Each update to an order notification has new text, so it gets a new dedup_key and a new row (TransactionStore.kt:211-215; R1M-F2, R1B-F4).
4. Amount: the first amount wins (NotificationParser.kt:105). ⚠ "You saved ₹50 · Order total ₹349" stores ₹50 (R1M-F7, R1B-F11).
5. ⧉ Same-source pairs are never linked (DuplicateMatcher.kt:50; TransactionStore.kt:117 NOT EXISTS). Swiggy "placed ₹349" + "you paid ₹349" = 2 rows. Two Zomato SMS = 2 rows (R1M-F1, R1B-F5).
6. Cross-source: exact amount only (DuplicateMatcher.kt:49). ⧉ A ₹30 tip, or ₹100 wallet + ₹249 UPI, leaves the bank ₹249 and the Swiggy ₹349 unlinked, and both count (R1M-F4).
7. ⚠ The bank SMS shows the legal name ("BUNDL TECHNOLOGIES", "BLINK COMMERCE", "KIRANAKART", "ETERNAL"), which gets no brand bonus, so the weak 24 h link fails and the title shows "Bundl Technologies" (SenderClassifier.kt:9-27, TitleExtractor.kt:37-44; R1M-F6). Note: Ola's "ani technologies" is already listed (SenderClassifier.kt:26).
8. ⧉ A same amount within 2 h with no mention is AUTO (DuplicateMatcher.kt:60-65). An unrelated ₹349 payment can be hidden (R1M-F11, R1B-F13).
9. POSSIBLE links still count, and nothing prompts the user (data/TxnRow.kt:27; R1B-F16, R2-F10).
10. The PC links email ↔ phone but never phone ↔ phone (pc/fintrack_pc/reconcile.py:43-44). New source types would be misranked (R1M-F19).

## Target flow

1. The picker (or the known-app list until then) includes Instamart, mapped to brand Swiggy (004, 016).
2. NotificationText reads title, big text, text lines and sub text. It drops group summaries, ongoing/progress notifications, MessagingStyle and CATEGORY_MESSAGE (006, 015).
3. Amount preference: paid / order total / charged first; saved / discount / MRP / item total / cashback skipped (005).
4. Identity `pkg + sbn.key + when`: an updated notification replaces its row (007).
5. **Lifecycle merge**: the same brand (MERCHANT_APP or DELIVERY), the same direction, amount within ±₹2, and a gap ≤3 h → one record. The later "paid" amount wins over "placed". "Delivered" with no amount never creates a row (011).
6. **Cross-source**: the alias catalog (Swiggy → Bundl / Swiggy Ltd; Blinkit → Blink Commerce / Grofers; Zepto → Kiranakart; Zomato → Eternal) counts as a mention. Near amounts (|Δ| ≤ max(₹5, 3%)) count only with a mention, and only as POSSIBLE. AUTO needs a mention or a gap ≤10 min (004, 009).
7. Wallet: top-up = TRANSFER, a wallet-paid order = SPEND from the merchant notification (012).
8. A missing UPI-app notification for in-app UPI is normal and is not treated as a gap (R3M rec 2).
9. One alert per merged order (022). A POSSIBLE link shows a badge and a one-tap confirm (009).

## Edge cases

- "Eternal" can be Zomato or Blinkit. Disambiguate by the merchant notification in the same window (R3M §Ambiguities).
- Two identical ₹349 orders 40 min apart, both with "you paid": no placed/delivered pairing, so they link at most POSSIBLE, never AUTO.
- COD: only the merchant notification or email exists, so it counts once at the "paid" / "delivered with amount" stage. Zomato COD change credited to Zomato Money is a TRANSFER-in to the wallet, not income (R3M §Zomato).
- Tip added after delivery ("You added a ₹30 tip"): this is a separate ₹30 SPEND unless the bank debit equals total + tip (then a near-amount POSSIBLE).
- Example (made up): Swiggy `Order placed! ₹349 · Paid via UPI` → `Your order is on the way` → bank `Rs 349.00 debited A/c XX1234 to SWIGGY LIMITED` → one counted row.

## Issues

[001](../issues/001-tests-first-merchant-fixtures.md) · [004](../issues/004-merchant-alias-catalog.md) · [005](../issues/005-parser-amount-direction-hardening.md) · [006](../issues/006-notification-text-extraction.md) · [007](../issues/007-stable-record-identity.md) · [009](../issues/009-cross-source-matching-tuning.md) · [011](../issues/011-order-ride-lifecycle-merge.md) · [012](../issues/012-wallet-topups-as-transfers.md) · [020](../issues/020-payment-method-tag.md)
