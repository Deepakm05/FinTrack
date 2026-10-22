---
title: Read full notification text safely and skip ongoing / progress notifications
labels: [area/notifications, priority/P0, size/S]
milestone: M1 Accurate capture core
depends_on: [001]
suggested_agent: opus · medium
---
## Problem
- The listener reads only `EXTRA_TITLE` + `EXTRA_BIG_TEXT`/`EXTRA_TEXT` (android/.../notify/PaymentNotificationListener.kt:26-28). Amounts in `EXTRA_TEXT_LINES` (InboxStyle), `EXTRA_SUB_TEXT`, `EXTRA_SUMMARY_TEXT` or `EXTRA_TITLE_BIG` are missed (R1M-F16, R1B-F3).
- There is no skip for ongoing / foreground-service / progress notifications. Ride and order trackers ("Ride fare ₹103 · captain 3 min away") match `fare` (NotificationParser.kt:67-70) and insert a DEBIT on every update (R1M-F2, R1B-F4).
- The listener passes no `sbn.key`, so identity (007) has nothing to key on.

## Use case / flow
[02](../flows/02-food-quick-commerce-order.md), [03](../flows/03-ride-payment.md), [05](../flows/05-app-picker-setup.md)

## Proposed approach
- A pure-Kotlin `core/NotificationText` takes a plain data snapshot (flags, category, template, title, bigTitle, text, bigText, textLines, subText, summaryText) and returns a list of candidate texts or a drop reason. It is unit-testable without Android.
- Drop: `FLAG_ONGOING_EVENT`, `FLAG_FOREGROUND_SERVICE`, `EXTRA_PROGRESS_MAX > 0`, `FLAG_GROUP_SUMMARY`, `CATEGORY_MESSAGE`, `CATEGORY_CALL`, MessagingStyle templates (security R4-R2 — see conflict note).
- Combine title + big title + big text/text + sub text. Each InboxStyle line is parsed as its own candidate.
- The listener builds the snapshot **after** the package check, which stays first. It passes `sbn.key`, `n.when` and `postTime` downstream.
- Record a drop reason counter (used by 019/024).

## Acceptance criteria
- [ ] Snapshot with ongoing flag + `Ride fare ₹103 · captain 3 min away` → dropped (`ongoing`).
- [ ] InboxStyle with lines `₹349 paid for order #XXXX` and `Your order is out for delivery` → one candidate ₹349.
- [ ] Amount only in sub text (`Paid · ₹89`) → captured.
- [ ] A MessagingStyle notification from a selected app → dropped (`messaging`).
- [ ] A group summary → dropped. Its children are parsed normally.
- [ ] `sbn.key` reaches the store insert call (signature only; used by 007).

## Test fixtures needed
Snapshot fixtures for Rapido/Uber/Ola ongoing trips, Swiggy InboxStyle, a Zepto sub-text amount (made up).

## Files likely touched
New `core/NotificationText.kt`, `notify/PaymentNotificationListener.kt`, tests. `NotificationParser.parse(pkg, title, text, at)` already takes plain text, so it is **not** modified here. This keeps the issue parallel with 005.

## Out of scope / open questions
- **Conflict resolved**: R1B-I3 proposed reading MESSAGES and expanding summaries. R4-R2 requires dropping MessagingStyle and group summaries. Security wins, because chat content must never be read.
- **Unverified**: whether ride apps actually set FLAG_ONGOING_EVENT on trip trackers, and whether any merchant puts the amount only in a summary. Verify on device.
