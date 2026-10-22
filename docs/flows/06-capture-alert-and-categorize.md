# 06 — Capture alert and categorize

**Actors:** user, FinTrack capture path (SMS receiver / listener), `TransactionStore`, the planned `TxnAlerter`, Android notification system (channels, lock screen), PC (labels source of truth).

**Preconditions:** POST_NOTIFICATIONS granted on API 33+ (always allowed on API 26–32). A transaction has just been captured.

## Current flow

1. `TransactionStore.insert` stores the row and calls only `notifyChanged()` (data/TransactionStore.kt:104-105). ⚠ No user-visible alert. The user must open the app to see it (R2-F1).
2. The only notification FinTrack posts is the PC-link foreground-service notification (server/LinkService.kt:56-78).
3. POST_NOTIFICATIONS is requested only inside the SMS grant (ui/MainActivity.kt:115-117). ⚠ If denied, alerts and the FGS notification are both hidden and the user isn't told (R2-F2).
4. Categorize: the detail sheet shows the category, but the phone has no write path. The sync JSON carries no category from the phone (ui/screens/TxnDetailSheet.kt:77-80; server/ApiServer.kt:116-131; R2-F7). Categories are set on the PC only (applyFromPc, TransactionStore.kt:156).
5. ⧉ A possible duplicate is counted twice until the PC reviews it, with no prompt on the phone (TxnDetailSheet.kt:84-92; R2-F10).
6. Loop safety is fine today because `appFor` is an allowlist without FinTrack's own package, but it must stay so once alerts exist (R2-F12).

## Target flow

1. Notifications get their own onboarding step with a rationale. After 2 denials it deep-links to app notification settings. An "Alerts off" state is shown on Home (021).
2. `insert` returns an outcome: `NEW`, `UPDATED` (identity hit), `LINKED_AUTO`, `LINKED_POSSIBLE` or `DUPLICATE_IGNORED` (007, 022).
3. On `NEW` (and on `LINKED_POSSIBLE`) `TxnAlerter` posts to the **payments** channel: "Spent ₹249 at Swiggy · Bank SMS · 8:42 pm". The notification id is derived from the uid, so a later lifecycle merge **updates** the same alert instead of posting a new one. `UPDATED` and `LINKED_AUTO` post nothing new (022).
4. Privacy: `VISIBILITY_PRIVATE` with a public version "FinTrack: new payment captured" (no amount, no merchant). Showing amounts on the lock screen is opt-in and off by default (R4-R11; 022).
5. Burst: ≥3 alerts within 60 s collapse into a group summary ("3 payments captured · ₹612") (022).
6. Tap → MainActivity opens the detail sheet for that uid (PendingIntent FLAG_IMMUTABLE). For a POSSIBLE link the sheet shows "Same payment?" Yes / No (009, 022).
7. The action "Categorize" opens the sheet with the top suggestions. The choice is written on the phone with `category_source=phone`, a version bump and a pending flag. The PC accepts it if its own row is unchanged since the last sync; otherwise the PC wins and logs it (029).
8. Loop safety: the listener ignores FinTrack's own package explicitly (015), and alerts never go through the parser.
9. Settings: alerts on/off, hide amounts on lock screen (default on), alert on credits (default off) (022).

## Edge cases

- Inbox catch-up imports 40 old SMS: no alert per row. One summary "Imported 40 older payments" (022, 008).
- Notification update (identity hit): no second alert.
- A Swiggy order followed by the bank SMS 30 s later: one alert, updated in place to "Spent ₹349 at Swiggy · 2 sources".
- TRANSFER kind (wallet top-up): a quiet alert "₹1000 moved to Swiggy Money (not counted)", or none, depending on the setting.
- Alerts disabled at the channel level: the health card shows "Alerts off" (024).

## Issues

[007](../issues/007-stable-record-identity.md) · [009](../issues/009-cross-source-matching-tuning.md) · [015](../issues/015-picker-security-guardrails.md) · [021](../issues/021-notification-permission-onboarding.md) · [022](../issues/022-transaction-capture-alerts.md) · [024](../issues/024-capture-health-card.md) · [029](../issues/029-categorize-from-phone.md)
