# 07 — Recovery and health (reboot, OEM kill, listener rebind, health card)

**Actors:** user, Android system (boot, Doze, notification-listener binding), OEM battery managers (MIUI, ColorOS, Funtouch), FinTrack SmsReceiver, PaymentNotificationListener, LinkService, the planned BootReceiver / health check.

**Preconditions:** the app was set up (SMS + notification access, optionally PC link ON). Then one of these happens: reboot, app update, force-stop, OEM kill, revoked notification access, or the listener unbound by the system.

## Current flow

1. **SMS**: the manifest receiver survives reboot, so SMS keeps flowing unless OEM Autostart blocks it (R2-F4, R1B-F2). ⚠ SMS that arrived while blocked are lost. There is no automatic inbox catch-up (R1M-F12).
2. **Listener**: ⚠ there is no `onListenerConnected` / `onListenerDisconnected`, no `requestRebind`, and no `getActiveNotifications()` replay (notify/PaymentNotificationListener.kt:21-50; R1B-F1, R2-F5, R1M-F16). After an OEM kill, capture silently stops. Notifications posted while unbound are lost.
3. The UI checks only the settings flag `enabled_notification_listeners` (listener :45-48; MainActivity.kt:90). ⚠ It shows "on" while the service is dead.
4. **PC link**: there is no BOOT_COMPLETED receiver. `running` is a process-local static (server/LinkService.kt:86). START_STICKY (:46) does not survive a reboot. START_NOT_STICKY when the server fails to bind (:42). ⚠ After a reboot or process death the link stays off until the user opens the app (R2-F4).
5. ⚠ No "last captured" or "sync failing" state anywhere. `lastPcSyncAt` changes on any pull or push (data/Prefs.kt:38-40; server/ApiServer.kt:95-98; R2-F8).

## Target flow

1. **BootReceiver** (BOOT_COMPLETED + MY_PACKAGE_REPLACED) runs the SMS inbox catch-up from the watermark (008) and records `lastBootAt`.
2. Same receiver: restart LinkService **only if** the user left it ON (`linkWanted` pref) **and** the link is in a safe mode (USB-only, or pinned-TLS once 033 ships). It never auto-starts plaintext Wi-Fi (R4-R13). If the start throws ForegroundServiceStartNotAllowedException (Android 12+), post "Tap to resume PC link" instead (025).
3. **Listener lifecycle**: `onListenerConnected` records `lastListenerConnectAt`, replays `getActiveNotifications()` through the normal path (identity dedup makes this idempotent, 007) and triggers SMS catch-up. `onListenerDisconnected` records the time and calls `requestRebind(ComponentName)` (023).
4. **Health check**: a daily alarm (and app open) computes listener connected? (last connect after last disconnect), last notification seen per selected app, last SMS seen, last capture, last PC sync, alerts permission. If the listener is enabled in settings but has not connected for N hours, or there is no SMS/notification for 3 days while the phone is in use, post an **attention** alert: "FinTrack isn't reading payment apps — tap to fix" (024).
5. **Home health card**: green / amber / red rows (SMS, Payment apps, Alerts, PC link, Last sync) with fix buttons: open notification-access settings, battery-optimization exemption (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS rationale + OEM Autostart guidance), re-grant (024).
6. **Coverage view** (later): per bank, whether small payments arrive by SMS, notification or email, given HDFC ≤₹100 and the RBI 2027 >₹500 rule (unverified) (030).

## Edge cases

- Android 14/15 restrictions on starting a `specialUse` FGS from BOOT_COMPLETED are **unverified** (R2-F11). Fallback: the resume notification.
- A listener replay after reconnect can re-deliver 20 active notifications. Identity dedup means 0 new rows and no alerts.
- The user revoked notification access deliberately: the card shows "Payment apps: off", with no nagging alerts after the first one.
- Direct-boot / locked after reboot: the SQLite DB is credential-encrypted storage, so catch-up waits for the first unlock (BOOT_COMPLETED fires after unlock).
- Doze: the daily job uses an inexact alarm (`setAndAllowWhileIdle`). No exact-alarm permission is needed.

## Issues

[008](../issues/008-sms-capture-resilience.md) · [023](../issues/023-listener-resilience-rebind.md) · [024](../issues/024-capture-health-card.md) · [025](../issues/025-boot-restart-safe-link.md) · [030](../issues/030-small-payment-coverage-view.md) · [032](../issues/032-usb-default-wifi-binding.md) · [033](../issues/033-pinned-tls-pairing.md)
