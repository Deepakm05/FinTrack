---
title: Show capture health on Home and raise attention alerts when capture stops
labels: [area/alerts, priority/P1, size/M]
milestone: M3 Alerts & auto-sync
depends_on: [022, 023]
suggested_agent: sonnet · high
---
## Problem
There is no "last captured" or "sync failing" state anywhere. `lastPcSyncAt` changes on any pull or push (android/.../data/Prefs.kt:38-40; server/ApiServer.kt:95-98). When SMS or listener capture dies, the user finds out weeks later (R2-F5, R2-F8; R2-I4). OEM battery killers and Doze need explicit user action (R2 caveats; README §2 step 5).

## Use case / flow
[07 recovery and health](../flows/07-recovery-and-health.md)

## Proposed approach
- Record `lastSmsAt`, `lastNotifAt` (per app via `capture_stats`), `lastCapturedAt`, `lastPcPullAt`, `lastPcPushAt`, `lastBootAt`, and rejected/dropped counters.
- **Health card** on Home with rows: SMS, Payment apps (listener connected? from 023), Alerts (from 021), PC link, Last sync. Each row is green/amber/red with a fix button: notification access, battery optimization (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with rationale), OEM Autostart guidance text per manufacturer, re-grant.
- **Daily health job** (inexact alarm, also on app open): conditions → an `attention` channel alert (from 022):
  - listener enabled but not connected for >6 h → "FinTrack isn't reading payment apps — tap to fix";
  - no SMS and no notification captured in 3 days while there was SMS inbox activity → "No payments captured since Mon";
  - review queue has ≥5 items → "5 texts need review".
- At most one attention alert per condition per day. The card stays until fixed.

## Acceptance criteria
- [ ] Listener disconnected 7 h → one attention alert. The card's "Payment apps" row is red with a "Fix" that opens notification-access settings.
- [ ] After reconnect the row turns green and no further alert is sent.
- [ ] `lastPcPullAt` and `lastPcPushAt` are shown separately ("Pulled 5 min ago · Pushed 2 h ago").
- [ ] Battery optimization not exempted → amber row with a prompt. Exempted → green.
- [ ] Health evaluation is a pure function with unit tests for each condition.

## Test fixtures needed
HealthInput → HealthState table tests (made-up timestamps).

## Files likely touched
New `core/Health.kt`, new `notify/HealthJob.kt` (+ receiver), `data/Prefs.kt`, `ui/model/UiModels.kt`, `ui/screens/HomeScreen.kt`, `ui/screens/Components.kt`, `server/ApiServer.kt` (split pull/push timestamps), `AndroidManifest.xml`.

## Out of scope / open questions
- The per-bank small-payment coverage view is 030.
- OEM guidance text needs device verification.
