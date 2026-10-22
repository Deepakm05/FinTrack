---
title: Restart the PC link after reboot or update, but only in a safe link mode
labels: [area/sync, area/security, priority/P1, size/M]
milestone: M3 Alerts & auto-sync
depends_on: [008, 032]
suggested_agent: sonnet · high
---
## Problem
After a reboot or process death the PC link stays off. There is no BOOT_COMPLETED receiver. `running` is a process-local static (android/.../server/LinkService.kt:86). START_STICKY (:46) does not survive a reboot, and a bind failure returns START_NOT_STICKY (:42). So auto sync (026) fails until the user opens the app (R2-F4, R2-I6). Security: auto-starting a plaintext Wi-Fi server on boot would expose the token-protected API on any network the phone joins (R4-R13, SEC-14).
*Corrected refs: R2 cited LinkService.kt:39/:35/:84. The verified lines are :46/:42/:86.*

## Use case / flow
[07 recovery and health](../flows/07-recovery-and-health.md), [08 auto sync](../flows/08-auto-sync.md)

## Proposed approach
- Persist `linkWanted` in Prefs. It is set by the user's toggle, not by process state.
- Extend the BootReceiver from 008 (BOOT_COMPLETED, MY_PACKAGE_REPLACED): if `linkWanted` **and** (mode == USB **or** pinned-TLS pairing is active from 033) → `startForegroundService(LinkService)`.
- Wi-Fi plaintext mode → do **not** start. Post "PC link is paused after restart — tap to resume" instead.
- Catch `ForegroundServiceStartNotAllowedException` (API 31+) → the same resume notification.
- LinkService start failure → record `lastError`. The health card (024) shows it.

## Acceptance criteria
- [ ] USB mode, link ON, reboot → `/api/status` answers through `adb forward` within 2 min of unlock.
- [ ] Wi-Fi mode, link ON, reboot → the server is not listening and the resume notification is shown.
- [ ] Link OFF before reboot → nothing starts and no notification.
- [ ] App update (MY_PACKAGE_REPLACED) in USB mode → the link is restarted.
- [ ] A Robolectric test covers the decision table (mode × wanted × pairing).

## Test fixtures needed
Decision-table unit tests. Manual device checklist (Android 13, 14, 15; MIUI, Samsung).

## Files likely touched
`sms/BootReceiver.kt` (from 008, already registered for BOOT_COMPLETED + MY_PACKAGE_REPLACED, so no manifest change), `server/LinkService.kt`, `data/Prefs.kt`, `ui/MainActivity.kt` (setLink writes linkWanted).

## Out of scope / open questions
- **Conflict resolved**: R2-I6 restarts the link whenever it was wanted. R4-R13 allows it only in USB or pinned-TLS mode. Security wins.
- **Unverified**: whether a `specialUse` FGS may start from BOOT_COMPLETED on Android 14/15 (R2-F11).
