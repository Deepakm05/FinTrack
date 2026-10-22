# 01 — SMS capture

**Actors:** user, Android telephony (SMS_RECEIVED broadcast), OEM battery manager (MIUI/ColorOS Autostart), FinTrack `SmsReceiver` / `SmsIngest` / `SmsParser` / `TransactionStore`, bank and merchant DLT senders.

**Preconditions:** RECEIVE_SMS + READ_SMS granted. The app has been opened at least once since install. The phone may be rebooted or the app killed at any time.

## Current flow

1. The SMS arrives and the manifest receiver fires (sms/SmsReceiver.kt:12). ⚠ If Autostart is off on MIUI/ColorOS, the broadcast is never delivered and nothing re-scans later (R1M-F12, R1B-F2). Inbox import runs only from a manual button or a PC call (ui/MainActivity.kt:141-148, server/ApiServer.kt:86-88).
2. Parts are grouped by sender (SmsReceiver.kt:18-19). ⚠ Two separate SMS from the same sender in one intent are glued together (R1B-F21).
3. A single thread ingests every message. ⚠ One exception aborts the rest of the batch (SmsReceiver.kt:24-30, R1M-F18).
4. The sender is normalized (core/SenderClassifier.kt:54-59). The DLT prefix and the `-S/-T/-P` suffix are stripped, and the suffix is not used as a signal (R3B §Sender IDs). ⚠ All-numeric senders with ≥7 digits are dropped (:57), including roaming DLT SMS (R1M-F17).
5. OTP gate: an OTP is kept if it also has a past-tense verb (core/SmsParser.kt:68). ⚠ An OTP code can be stored unmasked (R4-S5).
6. NOT_A_TXN (SmsParser.kt:19-23). ⚠ "…reversed as txn failed" is dropped because of "failed", so the original debit stays counted (R1B-F10, R1M-F9).
7. Classify and pick a direction (SmsParser.kt:94-104). ⚠ "Payment of Rs 5,000 received towards your card" becomes a DEBIT and is counted alongside the account debit (R1M-F10, R1B-F6). ⧉ Wallet top-up debits ("trf to Swiggy") count as spend (R1M-F3, R1B-F9).
8. Amount: the first non-balance amount wins (SmsParser.kt:113-123). ⚠ "Iqbal", "Balaji" and "Global" trip the balance filter because it has no word boundary, so the amount is lost and the SMS silently dropped (:45, :125-130; R1M-F8, R1B-F7). ⚠ "You saved ₹50 … total ₹349" gives the wrong amount (R1M-F7).
9. Masking (core/Masker.kt:11-26). ⚠ Gaps: alphabetic VPAs, payee names, spaced phone numbers, ≤8-digit refs, PAN, Aadhaar (R4-S4).
10. Store: `dedup_key = sha256(senderKey | minute | body)` (data/TransactionStore.kt:211-215), CONFLICT_IGNORE (:77). ⧉ The receiver uses the SMSC timestamp, while import uses DATE_SENT or falls back to DATE (sms/SmsIngest.kt:35), so the same SMS can land in different minutes and produce two rows (R1M-F13, R1B-F20). ⚠ Two real ₹20 payments to the same payee in the same minute collapse into one, because the refs are masked to XXXX (core/Masker.kt:19; R1M-F14, R1B-F12).
11. Cross-source match (core/DuplicateMatcher.kt:48-67). See flow 02.
12. Rejections leave no trace (R1M-F15, R1B-F8). No alert is posted (R2-F1).

## Target flow

1. The receiver handles each message separately and joins parts only when they are consecutive parts of one message. Each message runs inside its own try (issue 008).
2. A watermark (newest processed SMS date) is persisted. Inbox catch-up runs from `watermark − 1 day` on app open, on listener connect, on BOOT_COMPLETED and from a daily job (008).
3. Sender handling: a `-P` suffix on a bank header means promotional by default. `-T`/`-S` are allowed. A numeric sender whose text is bank-like goes to the Review queue instead of being dropped (008, 018).
4. OTP gate: an OTP keyword plus a 4–8 digit code means the message is dropped before persistence and never enters the Review queue (010).
5. Parser: "reversal/reversed" wins over "failed". "received towards your card" is a TRANSFER. Wallet / UPI Lite / add-money debits are TRANSFER. Amount preference: paid/total/debited first; saved/discount/MRP/item total/cashback are skipped. The balance context uses word boundaries (005, 012, 014).
6. Hardened masker, plus a keyed hash of the raw ref/UTR computed before masking (007, 010).
7. Identity: sender key + masked body + sent time within ±10 min means the same SMS. Receiver and import of one SMS give one row (007).
8. Cross-source matching with aliases and near-amount rules (009). A capture alert is posted (022).

## Edge cases

- DATE_SENT = 0 on some OEMs: import uses DATE. This is covered by the ±10 min identity.
- Multipart SMS that arrive in two intents. Unverified. Needs device testing.
- HDFC sends no SMS for UPI debits ≤₹100 / credits ≤₹500 (since 25 Jun 2024). From 1 Jan 2027, RBI may make SMS mandatory only above ₹500 (R3M §Thresholds, **circular unverified**). Small payments then rely on notifications or email (030).
- Timezone: the parse uses `ZoneId.systemDefault()` (SmsParser.kt:63), so a date can be wrong while travelling (R1B-F22, low).
- Example (made up): `VM-HDFCBK-S: Rs.349.00 debited from A/c XX1234 to BUNDL TECHNOLOGIES on 12-10-26. Ref 6123XXXX.`

## Issues

[005](../issues/005-parser-amount-direction-hardening.md) · [007](../issues/007-stable-record-identity.md) · [008](../issues/008-sms-capture-resilience.md) · [010](../issues/010-masker-hardening-otp-gate.md) · [012](../issues/012-wallet-topups-as-transfers.md) · [014](../issues/014-refunds-reversals-cancellation-fees.md) · [018](../issues/018-review-queue.md) · [030](../issues/030-small-payment-coverage-view.md)
