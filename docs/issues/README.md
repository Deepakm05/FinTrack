# FinTrack draft issues — index

These are planning drafts built from five research reports (capture, payments, sync/alerts, security). They have **not** been filed on GitHub yet. Each file uses frontmatter (title, labels, milestone, depends_on, suggested_agent), which `gh issue create` can take once a human reviews it. Use-case flows are in [`../flows/`](../flows/00-overview.md).

**Finding-id key:** `R1M-Fn` = capture audit (SMS + 7 merchant apps), `R1B-Fn` = broad capture audit, `R2-Fn` = sync & alerts, `R3M` / `R3B` = payments research (merchant / broad, cited by section), `R4-Sn` = security finding, `R4-Rn` = security requirement for a planned feature, `SEC-n` = R4's proposed issue.

## Index

| id | title | milestone | priority | size | depends_on | suggested agent |
|---|---|---|---|---|---|---|
| [001](001-tests-first-merchant-fixtures.md) | Add failing test fixtures for SMS + 7 merchant apps | M1 | P0 | M | — | opus · medium |
| [002](002-db-schema-v2-migration.md) | Phone DB migration framework + schema v2 | M1 | P0 | M | — | opus · high |
| [003](003-pc-schema-v2-compat.md) | PC accepts versioned schema, new source types, kinds | M1 | P1 | S | 002 | sonnet · medium |
| [004](004-merchant-alias-catalog.md) | Merchant alias catalog (legal names) + Instamart | M1 | P0 | S | — | opus · medium |
| [005](005-parser-amount-direction-hardening.md) | Harden amount and direction extraction | M1 | P0 | M | 001, 002 | opus · high |
| [006](006-notification-text-extraction.md) | Read full notification text, skip ongoing/progress | M1 | P0 | S | 001 | opus · medium |
| [007](007-stable-record-identity.md) | Stable record identity, update in place | M1 | P0 | M | 002, 006 | opus · high |
| [008](008-sms-capture-resilience.md) | SMS watermark catch-up + per-message handling | M1 | P0 | M | 007 | sonnet · high |
| [009](009-cross-source-matching-tuning.md) | Cross-source matching: aliases, near amounts, safer AUTO | M1 | P0 | M | 004, 007 | opus · high |
| [010](010-masker-hardening-otp-gate.md) | Masker hardening + OTP gate (security) | M1 | P1 | M | 001, 007 | opus · high |
| [011](011-order-ride-lifecycle-merge.md) | Same-source order/ride lifecycle merge | M2 | P0 | L | 001, 007, 009 | opus · high |
| [012](012-wallet-topups-as-transfers.md) | Wallet / UPI Lite top-ups as transfers | M2 | P0 | M | 002, 005, 009 | opus · high |
| [013](013-ride-payments-driver-upi-and-card-hold.md) | Ride ↔ driver-UPI linking, Ola card hold | M2 | P1 | M | 004, 009, 011 | opus · high |
| [014](014-refunds-reversals-cancellation-fees.md) | Refunds, reversals, cancellation fees | M2 | P1 | M | 002, 005, 009 | opus · high |
| [015](015-picker-security-guardrails.md) | Picker / generic-parser / review-queue guardrails (security) | M2 | P0 | M | 010 | opus · high |
| [016](016-manual-app-picker.md) | Manual app picker | M2 | P0 | L | 002, 004, 015 | opus · high |
| [017](017-generic-parser-bank-app-type.md) | Conservative generic parser + BANK_APP | M2 | P1 | M | 016 | opus · high |
| [018](018-review-queue.md) | Masked review queue for rejected texts | M2 | P1 | M | 002, 010, 015 | sonnet · high |
| [019](019-what-fintrack-read-screen.md) | "What FinTrack read" audit + sample export | M2 | P1 | M | 016, 018 | sonnet · high |
| [020](020-payment-method-tag.md) | Inferred payment-method tag (display only) | M2 | P2 | M | 011, 012, 013 | opus · medium |
| [021](021-notification-permission-onboarding.md) | Notification permission as its own step | M3 | P1 | S | — | sonnet · medium |
| [022](022-transaction-capture-alerts.md) | Private capture alerts (lock-screen safe) | M3 | P0 | M | 007, 021 | sonnet · high |
| [023](023-listener-resilience-rebind.md) | Listener rebind + active-notification replay | M3 | P0 | M | 007, 008 | sonnet · high |
| [024](024-capture-health-card.md) | Health card + attention alerts | M3 | P1 | M | 022, 023 | sonnet · high |
| [025](025-boot-restart-safe-link.md) | Boot restart of PC link, safe mode only (security) | M3 | P1 | M | 008, 032 | sonnet · high |
| [026](026-pc-auto-sync-watch.md) | `fintrack watch` auto sync with backoff | M3 | P1 | M | 003, 034 | sonnet · high |
| [027](027-pc-sync-status-surface.md) | PC sync status in console / toast / MCP | M3 | P2 | S | 026 | sonnet · medium |
| [028](028-phone-address-resilience.md) | Phone Wi-Fi address resilience | M3 | P2 | S | 026 | sonnet · medium |
| [029](029-categorize-from-phone.md) | Categorize on phone, sync to PC | M3 | P2 | L | 002, 022, 026 | sonnet · high |
| [030](030-small-payment-coverage-view.md) | Small-payment coverage view per bank | M3 | P2 | M | 024 | sonnet · medium |
| [031](031-imap-tls-verification.md) | IMAP TLS verification + timeout | M4 | **P0 (High)** | S | — | opus · medium |
| [032](032-usb-default-wifi-binding.md) | USB-only default, Wi-Fi bound to wlan | M4 | **P0 (High)** | S | — | opus · medium |
| [033](033-pinned-tls-pairing.md) | Pinned self-signed TLS + adb pairing | M4 | P1 | L | 032 | opus · high |
| [034](034-pc-validate-phone-rows.md) | PC validates/namespaces phone rows, pins device | M4 | P1 | S | 003 | opus · medium |
| [035](035-email-sender-dkim.md) | Email sender domain + DKIM, smaller snippets | M4 | P1 | M | 010 | opus · high |
| [036](036-mcp-untrusted-content.md) | MCP text marked untrusted, import path allowlist | M4 | P1 | S | — | opus · medium |
| [037](037-nanohttpd-limits.md) | NanoHTTPD thread/body/error limits | M4 | P1 | M | — | opus · medium |
| [038](038-pc-secrets-keyring.md) | PC secrets in keyring; fix setup erasing email creds | M4 | P1 | M | — | opus · medium |
| [039](039-android-hygiene.md) | Sensitive clipboard, FLAG_SECURE, extraction rules | M4 | P2 | S | — | sonnet · medium |
| [040](040-console-hardening.md) | Console session token, upload + parse limits | M4 | P2 | M | — | opus · medium |
| [041](041-ci-hardening.md) | CI: SHA-pinned actions, least privilege | M4 | P2 | S | — | sonnet · medium |
| [042](042-docs-readme-updates.md) | README: picker, link safety, sideload, decisions | M4 | P2 | S | 016, 032 | sonnet · low |

Totals: M1 10 · M2 10 · M3 10 · M4 12 = **42 issues**. Note that 025/026 (M3) depend on 032/034 (M4). Those two security issues are scheduled early (waves 2–3) for that reason.

## Suggested parallel waves

**Strict serialization points:** `data/TransactionStore.kt`, `core/DuplicateMatcher.kt`, `core/NotificationParser.kt`, `core/SmsParser.kt`, `notify/PaymentNotificationListener.kt`, `pc/fintrack_pc/store.py`. No two issues in one wave edit the same strict file.
**Soft-shared files** (small additive edits, expect a rebase): `ui/model/UiModels.kt`, `data/Prefs.kt`, `ui/MainActivity.kt`, `AndroidManifest.xml`, `core/TitleExtractor.kt`.

| wave | issues (run in parallel) | why grouped |
|---|---|---|
| W1 | 001, 002, 004, 021, 031, 036, 041 | no dependencies. Tests / DB / catalog / onboarding UI / PC-only fixes |
| W2 | 003, 005, 006, 032, 038 | parsers ∥ listener extraction ∥ PC compat ∥ link default ∥ PC secrets |
| W3 | 007, 034, 037, 039, 040 | store identity ∥ PC validation ∥ server limits ∥ hygiene ∥ console |
| W4 | 008, 009, 010, 026 | SMS resilience ∥ matcher ∥ masker/OTP ∥ PC watch |
| W5 | 011, 023, 027, 033, 035 | lifecycle merge ∥ listener rebind ∥ PC status ∥ TLS ∥ DKIM |
| W6 | 012, 022, 028 | wallets ∥ alerts ∥ address resilience |
| W7 | 013, 015, 025 | ride linking ∥ guardrails ∥ boot restart |
| W8 | 016, 024 | picker ∥ health card |
| W9 | 014, 030 | refunds ∥ coverage (030 adds a read-only query in a new file) |
| W10 | 017, 029 | generic parser ∥ phone categorize |
| W11 | 018, 042 | review queue ∥ README |
| W12 | 019, 020 | audit screen ∥ method tag |

**Critical path (capture-core lane, one opus agent end-to-end is simplest):** 002 → 007 → 009 → 011 → 012 → 013 → 014 → 017 → 018. Every other lane can be given to other agents: listener (006 → 023 → 015 → 016 → 019), alerts (021 → 022 → 024 → 030 → 029), PC (003 → 034 → 026 → 027/028), link security (032 → 037 → 033 → 025), PC security (031, 036, 038, 040, 035, 041).

## Source-finding coverage (merges)

| Merged into | Source findings / proposals |
|---|---|
| 001 | R1M-I10, R1M test gaps |
| 002 | R1M-F19, R1M-I7, R1B-F17, R1B-I6, SEC-12 (schema part) |
| 003 | R1M-F19, R1B-F18, R1B-I9 |
| 004 | R1M-F6, R3M alias table + rec 1/6 + issue 1/7 (Instamart), R1B-F24 |
| 005 | R1M-F7, F8, F10, R1M-I5, R1B-F6, F7, F11, F23, R1B-I5, R3B §autopay |
| 006 | R1M-F2 (ongoing), R1M-F16 (fields), R1B-F3, R1B-I3, R4-R2 |
| 007 | R1M-F2 (updates), F13, F14, R1B-F4, F12, F14, F20, R1B-I4 (identity part) |
| 008 | R1M-F12, F17, F18, R1M-I8 (SMS part), R1B-F2, F19, F21, R1B-I2, R3B issue 5 |
| 009 | R1M-F4, F11, R1M-I3, R1B-F13, F14, F16, R1B-I11, R2-F10 |
| 010 | R4-S4, S5, R3, R12, SEC-5 |
| 011 | R1M-F1, F5, R1M-I1, R1B-F5, R1B-I4 (same-source part) |
| 012 | R1M-F3, R1M-I2, R1B-F9, R3M issue 3, R3B issue 7 |
| 013 | R1M-F5, F11, R3M issue 4, R3M §Ola (card hold) |
| 014 | R1M-F9, R1M-I4, R1B-F10, F15, R3M issue 7 (refund window) |
| 015 | R4-R1–R8, R10, SEC-12 (guardrail part), R2-F12, R2-I3 (loop part) |
| 016 | R1M-I9, R1B-I7, R1B P1/P2, R1B-F24, R3B issue 1, R4-R10 |
| 017 | R1B-I8, R3B issue 3, R4-R4 |
| 018 | R1M-F15, R1M-I6, R1B-F8, R1B-I10, R2-F6, R4-R5–R8 |
| 019 | R4-R9, R3M issue 6, R3B issue 2 (and seed for R3B issue 4) |
| 020 | R3M issue 2 |
| 021 | R2-F2, R2-I2 |
| 022 | R2-F1, R2-I1, R2-I3, R4-R11, SEC-13 |
| 023 | R1B-F1, R1B-I1, R1M-F16 (catch-up), R1M-I8 (listener part), R2-F5, R2-I4 (rebind part) |
| 024 | R2-F5, F8, R2-I4 |
| 025 | R2-F4, F11, R2-I6, R4-R13, SEC-14 (boot part) |
| 026 | R2-F3, F13, R2-I7, R4-R14, R15, SEC-14 (sync part) |
| 027 | R2-F8, R2-I8 |
| 028 | R2-F9, R2-I9 |
| 029 | R2-F7, R2-I5 |
| 030 | R3M issue 5, R3B issue 6, R3M rec 5 |
| 031–041 | SEC-1, SEC-2 (+R4-R16), SEC-3, SEC-4 (R4-S3), SEC-6, SEC-7, SEC-8, SEC-9, SEC-10 (039), SEC-11, R4-S13 |
| 042 | R3B issue 8, R4-S10 (README part) |

Deferred or not turned into issues: R1B-F22 (timezone at parse, Low, noted in flow 01), R1B P3 (PC tombstones: not needed because deselected rows are kept), R3B issue 4 (tuned rules for new apps: write them after 019 collects samples), card-on-UPI linking (R3B issue 7, needs samples), Account Aggregator and accessibility service (rejected by decision).

## Conflicts between reports and how they were resolved

1. **Review-queue retention**: R1M/R1B said 30 days / 300 rows. R4-R6 says ≤7 days, ≤200 chars. → **7 days, 200 chars, 300-row cap** (security wins) (015, 018).
2. **MessagingStyle and group summaries**: R1B-F3/I3 said to read MESSAGES and expand summaries. R4-R2 says to drop them. → **Drop**. Read only TEXT_LINES / SUB_TEXT / SUMMARY_TEXT / TITLE_BIG on normal notifications (006).
3. **Same-source merge window**: R1B-I4 said AUTO ≤60 min. R1M-I1 said ±₹2, ≤3 h food / ≤2 h rides. → R1M windows, but **AUTO only when a lifecycle stage is present**. Two plain "you paid" → POSSIBLE (011).
4. **Refund window**: R1M 10 days vs R3M 14 days. → Search **14 days**. AUTO ≤10 days with a brand/"refund" mention, otherwise POSSIBLE (014).
5. **Boot restart of the PC link**: R2-I6 said restart whenever wanted. R4-R13 says only USB / pinned TLS. → **Security wins**. Wi-Fi plaintext gets a "tap to resume" notification instead (025).
6. **Masker hardening vs same-minute collapse**: masking ≥6-digit refs (R4-R12) makes R1M-F14 worse. → Identity uses a **device-local HMAC of the raw ref computed before masking** (never synced). 010 depends on 007.
7. **Bank-app source type**: R1M-I9 offered "BANK_APP or a channel-aware matcher". → **Both**: `SourceType.BANK_APP` plus a `channel` column (002, 017).
8. **Ride-end "Pay ₹X"**: R1M treats it as the DEBIT. R3M treats the pay prompt as a duplicate of the UPI debit. → It is the **spend record for cash**. A person-named UPI debit links to it (bank is the main record) (013).
9. **Order placed**: R3M said "possible-dup source only". R1M said merge. → "Placed" creates a provisional row that later stages **merge into**. "Delivered" without an amount never creates a row (011).

## Corrections made while verifying evidence

- R2-F4 cites `LinkService.kt:39` (START_STICKY), `:35` (START_NOT_STICKY) and `:84` (`running`). The actual lines are **:46, :42, :86**. R2-F1's FGS notification is at **LinkService.kt:56-78**, not 49-60.
- R1M-F9 cites `UiModels.kt:~106` for "possible counts". The rule is `TxnRow.countsInTotals` at **data/TxnRow.kt:27**, used at UiModels.kt:85.
- R3M lists ANI Technologies (Ola) as an unverified alias to add. It is **already present** (`"ani technologies"`, SenderClassifier.kt:26). Swiggy already has sender key `INSTMT` and body name `instamart` (:10). Only the Instamart **package** is missing from `NotificationParser.APPS`.
- R4-S8 "config.json not gitignored": correct, but the file lives at `~/.fintrack/config.json`, outside the repo, so the risk is defensive only.
- R4-S6 email sender check: verified at email_import.py:118-127 (R4 cited 120-129).
- All other cited lines checked (DuplicateMatcher, NotificationParser, SmsParser, listener, SmsReceiver/SmsIngest, Masker, TransactionStore, ApiServer, Prefs, PC store/reconcile/config/cli) match.

## Verify on device (facts marked unverified)

- [ ] **Notification wording per app**: capture real samples (via 019, or manually masked) for Swiggy, **Instamart**, Zomato, Blinkit, Zepto, Rapido, Uber and Ola. Cover placed / paid / delivered / ride ongoing / ride end / payment received / refund initiated / cancellation fee / wallet top-up / wallet spend. Does each app post a "you paid" notification at all?
- [ ] **Ongoing flags**: do ride and order trackers set `FLAG_ONGOING_EVENT` / `FLAG_FOREGROUND_SERVICE` / progress? Is any amount only in TEXT_LINES or SUB_TEXT?
- [ ] **Instamart**: is it a separate notifying app (`in.swiggy.android.instamart`)? Does its SMS use `INSTMT`?
- [ ] **Ola card hold**: is an authorisation/hold SMS sent for cash rides with a saved card? What wording, and is a release/reversal SMS sent?
- [ ] **Legal names in bank/UPI SMS**: do the payee fields show "Bundl", "Swiggy Limited", "Blink Commerce", "Grofers", "Kiranakart", "Roppen", "Eternal", "Uber India Systems", "ANI Technologies", or gateway names (Juspay, Cashfree, Razorpay, "pyu")? Check across HDFC, SBI, ICICI, Axis, Kotak.
- [ ] **Driver / captain UPI**: does the payee show a personal name, and how long after ride end does the debit arrive?
- [ ] **Wallets**: Uber Cash, Ola Money Postpaid status, Zomato BNPL availability, wording of Swiggy Money / Rapido Wallet loads in bank SMS.
- [ ] **UPI Lite**: per-transaction SMS vs a daily summary (sources conflict).
- [ ] **RBI circular (24 Jun 2026)**: confirm SMS is mandatory only above ₹500 from **1 Jan 2027**, and email is mandatory with no minimum. Confirm HDFC's ≤₹100 / ≤₹500 thresholds still apply.
- [ ] **Signal timing**: the gap between merchant notification, UPI-app notification and bank SMS for intent UPI vs in-app UPI.
- [ ] **OEM behaviour**: whether MIUI/HyperOS Autostart-off blocks SMS_RECEIVED; whether `requestRebind` recovers the listener after a force-stop on MIUI/ColorOS; which OEMs write DATE_SENT = 0.
- [ ] **Android 14/15**: whether a `specialUse` FGS may start from BOOT_COMPLETED.
- [ ] **NanoHTTPD TLS** (`makeSecure`) with Keystore-backed keys on API 26+.
