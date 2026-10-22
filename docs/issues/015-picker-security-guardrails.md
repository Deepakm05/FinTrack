---
title: Enforce security guardrails for app selection, generic parsing and the review queue
labels: [area/security, area/notifications, priority/P0, size/M]
milestone: M2 Merchant apps
depends_on: [010]
suggested_agent: opus · high
---
## Problem
The manual picker lets users select arbitrary apps (bank apps, unknown merchants). Without guardrails, a selected app's chat, OTP or call notifications could be read and stored, the listener could read extras before the allowlist check, and the review queue could hold sensitive text indefinitely. Today the allowlist check is correctly first (android/.../notify/PaymentNotificationListener.kt:22) because `APPS` is a constant. That must survive the move to user data (R4 SEC-12, R4-R1..R10). Self-parse loops must stay impossible once FinTrack posts alerts (R2-F12).

## Use case / flow
[05 picker](../flows/05-app-picker-setup.md), [06 alerts](../flows/06-capture-alert-and-categorize.md)

## Proposed approach
Land the guardrails **before** the picker (016) and review queue (018), as policy code + tests:
- R1: the first statement of `onNotificationPosted` is `snapshot.contains(sbn.packageName)`. A test spies on the StatusBarNotification to prove `getNotification()`/extras are not touched for unselected packages.
- R2: drop own package, FLAG_GROUP_SUMMARY, CATEGORY_MESSAGE/CALL, MessagingStyle/conversation shortcut notifications, even for selected apps (with 006).
- R3: OTP gate before persistence (from 010), applied to both review and transactions.
- R4: the generic parser requires a currency amount AND a completed-transaction verb (enforced in 017's tests).
- R5–R8 for the rejected table: only texts with a currency amount, else counter only. Hardened mask, ≤200 chars, purge after 7 days, cap 300 rows. Never synced or exposed to MCP unless promoted. "Delete all" and per-app delete. Deselecting an app purges its rows.
- R10: `<queries>` with a launcher intent. A lint/test asserts QUERY_ALL_PACKAGES is absent from the manifest.

## Acceptance criteria
- [ ] A spy test: a notification from `com.example.chat` (unselected) → zero calls to `getNotification()`.
- [ ] A selected bank app posts a MessagingStyle chat `Hi, your RM here` → dropped, `messaging` counter +1.
- [ ] FinTrack's own alert `Spent ₹249 at Swiggy` reaches the listener → ignored even if the package is somehow selected.
- [ ] A rejected text older than 7 days is purged by the daily job. The 301st row evicts the oldest.
- [ ] `/api/changes` and MCP never return review rows.
- [ ] The manifest test fails if QUERY_ALL_PACKAGES is added.

## Test fixtures needed
Fake SBN spy, MessagingStyle/category snapshots, rejected-table purge clock.

## Files likely touched
`notify/PaymentNotificationListener.kt`, new `notify/AppSnapshot.kt`, `core/NotificationText.kt`, new `data/ReviewStore.kt` (rejected-table DAO + purge, reused by 018; it keeps out of `TransactionStore.kt`), `AndroidManifest.xml` (`<queries>`), tests.

## Out of scope / open questions
- **Conflict resolved**: review-queue retention. R1M-I6/R1B-I10 said 30 days / 300 rows. R4-R6 says ≤7 days, ≤200 chars. This issue uses 7 days, 200 chars and a 300-row cap.
- R9 (audit screen) is issue 019.
