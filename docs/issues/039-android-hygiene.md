---
title: Android hygiene — sensitive clipboard, FLAG_SECURE, data-extraction rules
labels: [area/security, priority/P2, size/S]
milestone: M4 Security hardening
depends_on: []
suggested_agent: sonnet · medium
---
## Problem
- "Copy token" uses a plain `ClipData` (android/.../ui/MainActivity.kt:133), so clipboard previews and keyboard clipboard history show it (R4-S9).
- No `FLAG_SECURE`: the Link screen (token) and transaction screens appear in screenshots and the recents preview (ui/screens/LinkScreen.kt:126-135; R4-S9).
- `allowBackup="false"` is set (AndroidManifest.xml:14), but there is no `dataExtractionRules`, so Android 12+ device-to-device transfer can still copy the unencrypted DB and prefs (R4-S10).
- The README never tells users to turn USB debugging off after installing (R4-S10).

## Use case / flow
[05](../flows/05-app-picker-setup.md), [08](../flows/08-auto-sync.md)

## Proposed approach
- Clipboard: `ClipDescription.EXTRA_IS_SENSITIVE = true` (API 33+, also set the legacy extra key for older keyboards). Auto-clear after 60 s if the clip is still ours.
- `window.setFlags(FLAG_SECURE)` for the whole activity (simplest), or at least for the Link and Review/Capture-log screens.
- `android:dataExtractionRules="@xml/data_extraction_rules"` that excludes everything from cloud backup and device transfer. Keep `fullBackupContent` for API ≤30.
- README note (shared with 042): "After installing, turn off USB debugging unless you use USB link mode".

## Acceptance criteria
- [ ] The copied token clip has the sensitive extra (unit test on the built ClipData).
- [ ] A screenshot attempt on the Link screen is blocked (manual), and the recents thumbnail is blank.
- [ ] `adb shell bmgr` / D2D test: no DB in the transfer (manual checklist).
- [ ] Manifest test asserts that `dataExtractionRules` exists.

## Test fixtures needed
None beyond unit tests on ClipData construction.

## Files likely touched
`ui/MainActivity.kt`, `AndroidManifest.xml`, new `res/xml/data_extraction_rules.xml`, `README.md`.

## Out of scope / open questions
- SQLCipher/Keystore DB encryption: deferred (Low, it adds a dependency).
- `FLAG_SECURE` breaks the ScreenshotTest? It is Robolectric-based, so it should be unaffected. Verify.
