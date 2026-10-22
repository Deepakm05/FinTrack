---
title: Make SMS capture self-healing with watermark inbox catch-up and per-message handling
labels: [area/sms, priority/P0, size/M]
milestone: M1 Accurate capture core
depends_on: [007]
suggested_agent: sonnet · high
---
## Problem
- ⚠ When OEM Autostart (MIUI/ColorOS) blocks the manifest receiver, the SMS is lost for good. Inbox import runs only from a manual button or a PC call (android/.../ui/MainActivity.kt:141-148; server/ApiServer.kt:86-88; R1M-F12, R1B-F2).
- ⚠ One throwing message aborts the rest of the batch (sms/SmsReceiver.kt:24-30; R1M-F18). Grouping by sender glues two different SMS from one sender (:18-19; R1B-F21).
- ⚠ All-numeric senders (≥7 digits), common for roaming DLT, are dropped (core/SenderClassifier.kt:57; R1M-F17, R1B-F19).
- The DLT suffix is stripped but not used: a `-P` (promotional) bank header is treated like a transactional one (R3B §Sender IDs).

## Use case / flow
[01 SMS capture](../flows/01-sms-capture.md), [07 recovery](../flows/07-recovery-and-health.md)

## Proposed approach
- Receiver: group parts by sender **and** consecutive message parts. Use one try per message. Log only counts, never bodies.
- Persist `sms_watermark` (max DATE processed) in `meta`. `catchUp()` = `importInbox(watermark − 1 day)`, triggered on app open, on listener connect (023 calls it), on a new `BootReceiver` (BOOT_COMPLETED, MY_PACKAGE_REPLACED) and from a daily inexact alarm. Identity (007) makes repeats free.
- Catch-up runs suppress per-row alerts and produce one summary (022).
- Sender suffix: `-P` → promo unless the body has a strong completed-txn pattern **and** a bank key. `-T/-S/-G` are normal.
- Numeric sender + bank-like body (a/c, card, UPI, debited) → Review queue (018), not stored as a transaction.

## Acceptance criteria
- [ ] Three SMS in one intent where #2 makes the parser throw → #1 and #3 stored.
- [ ] Two separate SMS from `VM-HDFCBK-S` in one intent → 2 rows, not 1 glued row.
- [ ] Simulate a blocked receiver: an SMS dated 09:00 is in the inbox, the app opens at 18:00 → row added, watermark advanced, no duplicate on the next open.
- [ ] BootReceiver triggers catch-up (Robolectric test of the receiver → ingest call).
- [ ] `JD-HDFCBK-P: Get 10% off on your card XX1234…` → ignored. `JD-HDFCBK-T: Rs 250 debited from A/c XX1234…` → stored.
- [ ] Sender `+919812345678` with `Rs 300 debited from A/c XX1234` → Review queue entry, no txn.

## Test fixtures needed
Intent batches (multi-sender, multi-part), inbox cursor fakes, suffix variants (made up).

## Files likely touched
`sms/SmsReceiver.kt`, `sms/SmsIngest.kt`, new `sms/BootReceiver.kt`, `AndroidManifest.xml` (RECEIVE_BOOT_COMPLETED), `core/SenderClassifier.kt`, `ui/MainActivity.kt` (on-open trigger), `data/Prefs.kt` or `meta`.

## Out of scope / open questions
- LinkService restart on boot belongs to 025. It extends this BootReceiver.
- **Unverified**: whether MIUI Autostart-off really blocks SMS_RECEIVED to manifest receivers on current MIUI/HyperOS, and which OEMs set DATE_SENT=0.
