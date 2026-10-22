---
title: Tune cross-source duplicate matching (aliases, near amounts, safer AUTO, possible-duplicate prompt)
labels: [area/matching, priority/P0, size/M]
milestone: M1 Accurate capture core
depends_on: [004, 007]
suggested_agent: opus · high
---
## Problem
- ⧉ Exact amount only (android/.../core/DuplicateMatcher.kt:49; TransactionStore.kt:114; pc/fintrack_pc/reconcile.py:40). Tips, partial wallet (₹100 wallet + ₹249 UPI), Uber fare adjustments and cash rounding leave both signals counted (R1M-F4).
- ⧉ Same amount ≤2 h with no merchant mention scores 0.7 = AUTO (DuplicateMatcher.kt:19, :60-65), so an unrelated ₹100 debit can be hidden behind a Rapido ₹100 cash ride (R1M-F11, R1B-F13).
- Gaps use `sent_at` (the SMSC clock can be skewed) against device time for notifications. `received_at` is unused (TransactionStore.kt:115; R1B-F14).
- POSSIBLE links still count (data/TxnRow.kt:27, by design), but the phone never asks the user to confirm (R1B-F16, R2-F10).

## Use case / flow
[02](../flows/02-food-quick-commerce-order.md), [03](../flows/03-ride-payment.md)

## Proposed approach
- Use `received_at` for the gap when channels differ (SMS vs notification).
- Mention = any alias from the 004 catalog in the main record's text, or a sender-key/brand match.
- Score: exact amount + gap ≤10 min → AUTO. Exact + ≤2 h + mention → AUTO. Exact + ≤2 h without mention → **POSSIBLE (0.6)**. Exact + ≤24 h + mention → POSSIBLE. Near amount |Δ| ≤ max(₹5, 3%) **only with a mention** and ≤2 h → POSSIBLE.
- `dupCandidates` gets an amount range query (index already on amount_paise, sent_at).
- Phone UI: a "Possible duplicate" badge plus Same / Different in the detail sheet. Writes `dup_state` confirmed/rejected locally with a version bump. The PC still wins on conflicts.
- Mirror the near-amount and mention rules in `reconcile.py` for email ↔ phone.

## Acceptance criteria
- [ ] Swiggy app `You paid ₹349` + bank `Rs 369.00 debited … SWIGGY LIMITED` 6 min later → POSSIBLE, both visible, Home counts ₹369 + ₹349 until confirmed. After confirm → ₹369.
- [ ] Rapido `Pay ₹100 to your captain` + unrelated bank `Rs 100 debited to KIRANA STORE` 90 min later → POSSIBLE, not AUTO.
- [ ] Bank `Rs 349 debited … BUNDL TECHNOLOGIES` + Swiggy `You paid ₹349` 3 min apart → AUTO.
- [ ] Notification at 20:00 device time + SMS with SMSC 17:30 but received 20:01 → treated as a 1-min gap.
- [ ] PC: email `Amount paid ₹212` + phone `Rs 198 debited UBER INDIA` → POSSIBLE.

## Test fixtures needed
Pairs from 001 (tip, partial wallet, fare adjustment, unrelated same-amount).

## Files likely touched
`core/DuplicateMatcher.kt`, `data/TransactionStore.kt` (dupCandidates, local dup decision), `ui/screens/TxnDetailSheet.kt`, `ui/model/UiModels.kt`, `pc/fintrack_pc/reconcile.py`, tests.

## Out of scope / open questions
- Same-source merges are in 011. Driver-UPI linking is in 013. Refund windows are in 014.
- Near-amount tolerance max(₹5, 3%) is a starting value. Tune it from real data.
