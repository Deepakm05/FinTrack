---
title: Add a "What FinTrack read" audit screen with masked sample export for tests
labels: [area/notifications, area/security, priority/P1, size/M]
milestone: M2 Merchant apps
depends_on: [016, 018]
suggested_agent: sonnet · high
---
## Problem
Notification wording for every merchant app is **unverified** (R3M §Notification wording; R3B §Method). Tuned rules and test fixtures (001) are guesses until real samples exist. Users also have no way to audit what the listener saw, parsed, rejected or dropped per app, or how the picker selection changed over time (R4-R9; R3M rec/issue 6; R3B issue 2).

## Use case / flow
[05 picker](../flows/05-app-picker-setup.md), [07 health](../flows/07-recovery-and-health.md)

## Proposed approach
- Screen per selected app: today / 7-day counts of seen, parsed, rejected and dropped by reason (ongoing, messaging, summary, otp, promo), last captured time, and the picker change history.
- "Recent texts" shows the last 20 **masked** texts for that app (accepted rows from txn, rejected from review). The raw text is never kept.
- **Export samples**: the user picks rows, which are copied (clipboard with EXTRA_IS_SENSITIVE, or a share intent) as a Kotlin/JSON fixture snippet `{pkg, title, text, flags, expected: ?}` with the masker applied twice, ready to paste into tests or a GitHub issue. A clear warning: "Check the text for names before sharing".
- Counters come from `capture_stats` (002), written by the listener, SmsIngest and the parsers.

## Acceptance criteria
- [ ] After 3 Swiggy notifications (1 parsed, 1 ongoing dropped, 1 promo rejected without amount) the screen shows seen 3 / parsed 1 / dropped 1 / rejected 1.
- [ ] Recent texts show `You paid ₹349 for order #XXXX`, never an unmasked ref or phone.
- [ ] Exported snippet for an Uber row is valid JSON and contains no 6+ digit runs.
- [ ] Picker history shows `12 Oct 21:04 enabled com.example.bank (Bank)`.
- [ ] The screen uses FLAG_SECURE (no screenshots or recents preview).

## Test fixtures needed
Counter aggregation unit tests. Export-format golden file.

## Files likely touched
New `ui/screens/CaptureLogScreen.kt`, `ui/model/UiModels.kt`, `data/TransactionStore.kt` (stats DAO), `notify/PaymentNotificationListener.kt` (counters), `sms/SmsIngest.kt`.

## Out of scope / open questions
- No automatic upload anywhere. Export is manual only.
- Whether to keep the last 20 masked texts of **dropped** notifications: proposed no (counter only), per R4-R5.
