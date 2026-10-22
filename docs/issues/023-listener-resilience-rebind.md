---
title: Recover the notification listener after OEM kills and replay missed notifications
labels: [area/notifications, priority/P0, size/M]
milestone: M3 Alerts & auto-sync
depends_on: [007, 008]
suggested_agent: sonnet · high
---
## Problem
The listener has no `onListenerConnected` / `onListenerDisconnected`, never calls `requestRebind`, and never replays `getActiveNotifications()` (android/.../notify/PaymentNotificationListener.kt:21-50). After an OEM kill (MIUI, ColorOS…) or a system unbind, capture stops silently. Payment notifications posted meanwhile are lost. The UI only checks the settings string (:45-48; ui/MainActivity.kt:90), so it says "on" while the service is dead (R1B-F1, R2-F5, R1M-F16; R1B-I1, R1M-I8, R2-I4 part).

## Use case / flow
[07 recovery and health](../flows/07-recovery-and-health.md)

## Proposed approach
- `onListenerConnected`: record `lastListenerConnectAt`. Replay `getActiveNotifications()` through the same path as `onNotificationPosted` (package check first, then identity dedup from 007, so it is idempotent). Trigger SMS catch-up (008).
- `onListenerDisconnected`: record `lastListenerDisconnectAt` and call `requestRebind(ComponentName(this, PaymentNotificationListener::class.java))`.
- App open and the daily health job: if access is enabled but `lastConnect < lastDisconnect` (or never), call `requestRebind`. Optionally toggle the component enabled state (documented OEM workaround), behind a flag.
- Expose `ListenerHealth {enabledInSettings, connected, lastConnectAt, lastNotificationAt}` for 024.
- Replay alerts: the replay runs with alerts suppressed except for genuinely NEW rows less than 1 h old.

## Acceptance criteria
- [ ] Simulated reconnect with 5 active notifications (2 already stored) → 3 new rows, 0 duplicates.
- [ ] `onListenerDisconnected` → `requestRebind` called once with the right component (Robolectric shadow).
- [ ] App open with enabled-but-disconnected state → rebind requested and health shows "Reconnecting".
- [ ] A replayed ongoing ride notification → still dropped (006).
- [ ] No unselected package is read during replay (spy test from 015).

## Test fixtures needed
Fake active-notification arrays (Swiggy paid, Rapido ongoing, GPay paid).

## Files likely touched
`notify/PaymentNotificationListener.kt`, `data/Prefs.kt` (timestamps), `ui/MainActivity.kt` (rebind on open), new `notify/ListenerHealth.kt`.

## Out of scope / open questions
- Battery-optimization prompts and the Home card are in 024.
- **Unverified**: how reliably `requestRebind` works on MIUI/HyperOS and ColorOS after a force-stop. Real-device testing is needed (R2 caveats).
