---
title: Update README for the picker, link safety, sideloading and out-of-scope decisions
labels: [area/docs, priority/P2, size/S]
milestone: M4 Security hardening
depends_on: [016, 032]
suggested_agent: sonnet · low
---
## Problem
README §2–3 and §7 describe the hard-coded app list, Wi-Fi as the first link option and substring-based duplicate rules. After M1–M4 these will be wrong. Several research conclusions belong in user docs (R3B issue 8; R4-S10; R3B §Fetch alternatives):
- Google's developer-verification programme (2026–27) for sideloaded apps. `adb install` stays exempt (R3B §Sideload).
- Turn off USB debugging after installing unless you use USB link mode (R4-S10).
- Why Account Aggregator is not used, and why accessibility-service screen reading was rejected.
- In-app UPI normally produces no GPay/PhonePe notification. Small payments may have no bank SMS (HDFC ≤₹100; the RBI 2027 rule is unverified).

## Use case / flow
[00 overview](../flows/00-overview.md)

## Proposed approach
- §2: replace the fixed app list with "Choose apps" (picker), the privacy promise (unselected apps are never read), and the tuned vs generic parser.
- §3: USB link first. Wi-Fi as an opt-in with pinned TLS (when 033 lands). `fintrack watch`.
- §7: kind (spend/refund/transfer), lifecycle merge, near-amount POSSIBLE, refund window, alerts.
- New "What FinTrack can't see" section: cash without a receipt, wallets you didn't select, banks without SMS, with the coverage view link.
- New "Decisions" section: AA, accessibility, no auto-suggest.

## Acceptance criteria
- [ ] README contains no stale "reads notifications only from …" fixed list.
- [ ] The link section recommends USB first, and the Wi-Fi warning matches the app text.
- [ ] The sideload / USB-debugging notes are present.
- [ ] Every rule number in §7 matches the constants in code (reviewer checks against DuplicateMatcher).

## Test fixtures needed
None.

## Files likely touched
`README.md`.

## Out of scope / open questions
- Translate after the features ship. Write it alongside each feature PR if preferred, and close this issue then.
