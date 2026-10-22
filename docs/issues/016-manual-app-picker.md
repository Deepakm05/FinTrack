---
title: Add a manual app picker that controls which apps FinTrack reads
labels: [area/picker, area/notifications, priority/P0, size/L]
milestone: M2 Merchant apps
depends_on: [002, 004, 015]
suggested_agent: opus · high
---
## Problem
The apps FinTrack reads are a hard-coded list of 16 packages (android/.../core/NotificationParser.kt:16-33, `appFor` :35). Users cannot add their bank app (needed as banks drop SMS for small payments, R3M §Thresholds), Instamart or another merchant, and cannot turn a listed app off (R1B P2, R1M-I9, R3B issue 1). Naive picker risks: selecting the default SMS app or Truecaller double-captures every bank SMS as BANK (R1B P1), and package-derived sender keys collide with substring brand lookups (core/SenderClassifier.kt:61; TitleExtractor.kt:40,67; R1B-F24).

## Use case / flow
[05 app picker setup](../flows/05-app-picker-setup.md)

## Proposed approach
- A Settings → "Apps FinTrack reads" screen lists launchable apps through `<queries>` (015). It excludes FinTrack, the default SMS app (`Telephony.Sms.getDefaultSmsPackage`), and a denylist (Google Messages, Samsung Messages, Truecaller, dialers).
- Each row has a switch plus a type (Bank / UPI / Merchant). Known apps (the 16 + Instamart) are shown as "Tuned" and their type is locked.
- Stored in `app_rule` (002) with history in `app_rule_history`. The migration seeds today's 16 + Instamart as enabled.
- The listener uses an immutable `@Volatile` snapshot rebuilt on change. `NotificationParser.parse` takes the rule (tuned → existing rules, otherwise → generic parser from 017).
- Sender key for non-catalog apps is `PKG:<package>`, compared exactly, never by substring.
- Deselect keeps existing rows and purges review rows for that app (015).
- Home setup card: "Reading 9 apps · Edit".

## Acceptance criteria
- [ ] After upgrade, the same 16 apps + Instamart are enabled. Behaviour is unchanged (existing tests pass).
- [ ] Disabling Swiggy → a Swiggy `You paid ₹349` is not read (spy test) and no row is added.
- [ ] Selecting `com.example.bank` as Bank → `₹1,250 debited from A/c XX1234` captured as BANK_APP with sender key `PKG:com.example.bank`.
- [ ] Google Messages and Truecaller are not listed.
- [ ] A package named `com.uberfans.app` does not resolve to brand Uber.
- [ ] The picker change history is visible in 019.

## Test fixtures needed
A fake PackageManager list, rule snapshots, parser routing tests.

## Files likely touched
New `ui/screens/AppPickerScreen.kt`, `ui/screens/AppContent.kt`, `ui/model/UiModels.kt`, `notify/PaymentNotificationListener.kt`, `core/NotificationParser.kt`, `core/SenderClassifier.kt`, `core/TitleExtractor.kt`, new `data/AppRuleStore.kt` (app_rule DAO; it keeps out of `TransactionStore.kt`), `AndroidManifest.xml`.

## Out of scope / open questions
- **Product decision**: no auto-suggest. The text of unselected apps is never read, even to suggest.
- PC tombstones for deselected apps (R1B P3): not needed, rows are kept.
- BANK_APP type and the generic parser are in 017.
