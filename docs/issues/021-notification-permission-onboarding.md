---
title: Make notification permission its own onboarding step with an "alerts off" state
labels: [area/alerts, priority/P1, size/S]
milestone: M3 Alerts & auto-sync
depends_on: []
suggested_agent: sonnet · medium
---
## Problem
POST_NOTIFICATIONS is requested only as part of the SMS grant (android/.../ui/MainActivity.kt:115-117). A user who denies it, or who granted SMS before this was added, never sees capture alerts (022). The PC-link foreground notification is also hidden, and the app never says so (R2-F2).

## Use case / flow
[06 capture alert](../flows/06-capture-alert-and-categorize.md)

## Proposed approach
- A separate Home setup row: "Payment alerts — Allow". It explains: "FinTrack posts one private notification when it captures a payment".
- API 33+: runtime request. After 2 denials (`shouldShowRequestPermissionRationale` false), the button deep-links to `Settings.ACTION_APP_NOTIFICATION_SETTINGS`.
- API 26–32: no prompt. Check `NotificationManager.areNotificationsEnabled()` and the channel importance.
- `SetupState` gains `alertsEnabled`. Home shows "Alerts off" when denied or the payments channel is blocked.
- Remove POST_NOTIFICATIONS from the SMS grant list.

## Acceptance criteria
- [ ] Fresh install on API 34: granting SMS does **not** show the notification prompt. The separate row does.
- [ ] Denied twice → the button opens app notification settings.
- [ ] Payments channel turned off in system settings → Home shows "Alerts off".
- [ ] API 30 emulator: the row shows "On" without a prompt.
- [ ] UiStateBuilderTest covers alertsEnabled true/false.

## Test fixtures needed
UiStateBuilder states and a screenshot test of the setup row.

## Files likely touched
`ui/MainActivity.kt`, `ui/model/UiModels.kt`, `ui/screens/HomeScreen.kt`, `ui/screens/Components.kt`, tests.

## Out of scope / open questions
- The channels themselves are created in 022.
