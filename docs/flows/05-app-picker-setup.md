# 05 — App picker setup (manual)

**Actors:** user, FinTrack picker screen, Android PackageManager (launcher-intent query), notification listener, generic parser, review queue.

**Preconditions:** notification access is granted. Product decisions: **manual** selection only. The user picks which installed apps FinTrack reads and sets each one's type (Bank / UPI / Merchant). Known apps keep their tuned rules. Other apps use a conservative generic parser. **No auto-suggest**: the text of an app that is not selected is never read.

## Current flow

1. `APPS` is a hard-coded list of 16 packages (NotificationParser.kt:16-33). `appFor` is a global constant lookup (:35). Nothing is stored in Prefs (R1B P2).
2. The listener checks `appFor(sbn.packageName)` first (listener :22). This is good: an app outside the list is never read (R4 "done well").
3. ⚠ Bank apps cannot be added. Small-UPI users whose bank posts app notifications instead of SMS get nothing (R3M §Thresholds).
4. ⚠ Instamart and other merchants cannot be added (R3M §Packages).
5. Risks if a picker is added naively: picking an SMS app (Messages, Truecaller) would double-capture every bank SMS as BANK, and the pair would never link because the source type is the same (R1B P1). Package-derived sender keys hit substring brand lookups (UBER/SBI/UPI) (SenderClassifier.kt:61; TitleExtractor.kt:40,67; R1B-F24). Chat and OTP notifications from a selected bank app could be stored (R4-R2, R4-R3). No tombstones, so the PC keeps rows the phone deselected (R1B P3, pc/fintrack_pc/store.py:157-173).

## Target flow

1. **Picker screen** (Settings → "Apps FinTrack reads"): it lists launchable apps through a `<queries>` launcher intent, never QUERY_ALL_PACKAGES (R4-R10). It excludes FinTrack itself, the default SMS app, known SMS / dialer / caller-ID apps (Messages, Truecaller) and the system UI (016).
2. The 7 merchant apps + Instamart + the 5 UPI apps are pre-seeded as **selected** on migration (keeps today's behaviour) with locked tuned rules and their existing sender keys (004, 016).
3. For any other app the user picks a type: Bank → `BANK_APP`, UPI → `UPI_APP`, Merchant → `MERCHANT_APP`. Its sender key is `PKG:<package>`, matched exactly and never by substring (016, 017).
4. The selection is stored in the `app_rule` table (002). The listener holds an immutable `@Volatile` snapshot. **The selected-package check is the first statement of `onNotificationPosted`**, before `extras` are touched (R4-R1; 015).
5. Even for a selected app the listener drops its own package, group summaries, CATEGORY_MESSAGE / CATEGORY_CALL, MessagingStyle / conversation notifications and ongoing / progress notifications (R4-R2; 015, 006).
6. OTP gate before persistence (010). The generic parser requires a **currency amount AND a completed-transaction verb**, plus no skip or promo words. Otherwise the text is ignored, or goes to review if it has a currency amount (R4-R4; 017).
7. Rejected texts with a currency amount → Review queue (masked, ≤200 chars, purged after 7 days, never synced) (018). Everything else only increments a per-app counter.
8. A "What FinTrack read" screen shows per-app counts (seen / parsed / rejected / dropped), the last N masked texts and the picker change history (R4-R9; 019).
9. Deselect: reading stops at once. Existing rows are kept. Review-queue rows for that app are purged (R4-R8).

## Edge cases

- A bank app notification plus the same bank's SMS: BANK_APP and BANK are different source types, so they link cross-source (rank BANK 0 < BANK_APP 1). With a `channel` column the matcher can also treat "same bank, two channels" as an AUTO pair ≤10 min (017).
- The user selects a messaging app anyway (for example a bank that alerts over WhatsApp): MessagingStyle is still dropped, so nothing is captured, and the picker shows "This app's messages can't be read".
- Package renamed or uninstalled: the rule stays dormant and is shown greyed out.
- The PC receives `BANK_APP` rows. It must rank them as phone sources (003).

## Issues

[002](../issues/002-db-schema-v2-migration.md) · [003](../issues/003-pc-schema-v2-compat.md) · [004](../issues/004-merchant-alias-catalog.md) · [010](../issues/010-masker-hardening-otp-gate.md) · [015](../issues/015-picker-security-guardrails.md) · [016](../issues/016-manual-app-picker.md) · [017](../issues/017-generic-parser-bank-app-type.md) · [018](../issues/018-review-queue.md) · [019](../issues/019-what-fintrack-read-screen.md)
