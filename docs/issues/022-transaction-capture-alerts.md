---
title: Notify the user privately each time a payment is captured
labels: [area/alerts, area/security, priority/P0, size/M]
milestone: M3 Alerts & auto-sync
depends_on: [007, 021]
suggested_agent: sonnet · high
---
## Problem
Capture is silent. `TransactionStore.insert` only calls `notifyChanged()` (android/.../data/TransactionStore.kt:104-105). SmsReceiver and the listener post nothing (sms/SmsReceiver.kt:24-26; notify/PaymentNotificationListener.kt:34). The only notification is the PC-link FGS (server/LinkService.kt:56-78). Users don't know a payment was captured, or missed, until they open the app (R2-F1, R2-I1). Alerts must not leak amounts on the lock screen (R4-R11, SEC-13), and FinTrack must never parse its own alerts (R2-F12, R2-I3).

## Use case / flow
[06 capture alert and categorize](../flows/06-capture-alert-and-categorize.md)

## Proposed approach
- `notify/TxnAlerter` creates channels `payments` (default importance) and `attention` (high, used by 024).
- Post only when `insert` returns `NEW` or `LINKED_POSSIBLE` (007). `UPDATED` / lifecycle merges **update** the existing alert (notification id derived from the main uid). `LINKED_AUTO` updates the main record's alert to "2 sources".
- Text: "Spent ₹249 at Swiggy" / "Received ₹500 from Ramesh" / "Possible duplicate: ₹349 Swiggy — tap to check". Sub text: source + time.
- Privacy: `VISIBILITY_PRIVATE` with a `publicVersion` "FinTrack · New payment captured". The lock-screen amount is an opt-in setting, **off** by default.
- Burst: ≥3 within 60 s → group with a summary "3 payments · ₹612". Inbox catch-up → one summary "Imported N older payments", no per-row alerts.
- Tap → MainActivity with the uid extra (FLAG_IMMUTABLE) → detail sheet. Action "Categorize" (wired in 029, hidden until then).
- Settings: alerts on/off, show amount on lock screen (off), alert for credits (off), alert for transfers (off).
- Loop guard: the listener ignores `packageName == context.packageName` (also in 015).

## Acceptance criteria
- [ ] One bank SMS `Rs 249 debited … SWIGGY` → exactly one alert "Spent ₹249 at Swiggy".
- [ ] The same SMS via receiver + import → still one alert.
- [ ] Swiggy notif then bank SMS 30 s later (AUTO) → one alert, updated to "2 sources".
- [ ] 4 captures in 60 s → one group summary.
- [ ] Lock-screen render (public version) contains no "₹" and no merchant.
- [ ] Tap opens the sheet for the right uid.
- [ ] A FinTrack alert delivered to the listener → no row and no counter change.

## Test fixtures needed
Outcome → alert-decision unit tests (pure function). A Robolectric test for channel and visibility.

## Files likely touched
New `notify/TxnAlerter.kt`, `sms/SmsIngest.kt`, `notify/PaymentNotificationListener.kt`, `data/Prefs.kt`, `ui/MainActivity.kt` (intent extra), `ui/screens/AppContent.kt` (settings), `AndroidManifest.xml`.

## Out of scope / open questions
- Writing categories from the alert is 029. Health/attention alerts are 024.
