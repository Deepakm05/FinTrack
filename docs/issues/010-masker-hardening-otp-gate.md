---
title: Harden the Masker and add an OTP gate before anything is persisted
labels: [area/security, area/parser, priority/P1, size/M]
milestone: M1 Accurate capture core
depends_on: [001, 007]
suggested_agent: opus · high
---
## Problem
- The Masker misses alphabetic VPAs (`ramesh.k@okaxis`), emails, phone numbers with spaces or +91, card numbers in 4-digit groups without the word "card", ref/account numbers of 8 digits or fewer, Aadhaar, PAN and balances (android/.../core/Masker.kt:11-26; MaskerTest.kt). The same gaps exist in PC `pc/fintrack_pc/textrules.py:14-28`. Bodies go to a cloud LLM through MCP (R4-S4).
- `NotificationParser` has no OTP filter. `SmsParser` keeps an OTP text when it also has a past-tense verb (core/SmsParser.kt:68), so the code is stored unmasked (R4-S5).
- Required before the picker, the generic parser and the review queue (R4-R3, R4-R6, R4-R12).

## Use case / flow
[01](../flows/01-sms-capture.md), [05](../flows/05-app-picker-setup.md)

## Proposed approach
- Masker rules, in order: VPAs `[\w.-]+@[a-z]+` → `XXXX@handle`, emails, phones (`(\+91[\s-]?)?[6-9]\d{4}\s?\d{5}`), grouped 12–19 digit cards, Aadhaar `\d{4}\s\d{4}\s\d{4}`, PAN `[A-Z]{5}\d{4}[A-Z]`, ref/UTR/RRN/txn id ≥6 digits after a keyword, then any run of ≥9 digits (existing rule). Amounts and dates are kept. Balance amounts after "Avl Bal" → `Rs XXXX`.
- OTP gate (both parsers, before the store): OTP/verification/one-time/passcode keyword + a 4–8 digit code → drop, counter only, **never** to the review queue.
- Shared test vectors (`masker_vectors.json`) are run by MaskerTest **and** a pytest, so phone and PC stay identical.
- Payee person names are **not** masked (titles need them). Document this as an accepted risk.

## Acceptance criteria
- [ ] `Paid ₹40 to ramesh.k@okaxis` → `Paid ₹40 to XXXX@okaxis`.
- [ ] `Call +91 98123 45678` → `Call XXXX`. `PAN ABCDE1234F` → `PAN XXXX`. `4111 1111 1111 1111` → `XXXX`.
- [ ] `UPI Ref 612345` → `UPI Ref XXXX`. `₹349 on 12-10-26` is unchanged.
- [ ] `123456 is your OTP for txn of Rs 349 at SWIGGY` → not stored, `otp_dropped` counter +1, no review row.
- [ ] A GPay notification `Your verification code is 482913` → dropped.
- [ ] The PC `textrules.mask` passes the same vector file.

## Test fixtures needed
`masker_vectors.json` (made up), OTP SMS/notification variants.

## Files likely touched
`core/Masker.kt`, `core/SmsParser.kt`, `core/NotificationParser.kt`, `MaskerTest.kt`, `pc/fintrack_pc/textrules.py`, `pc/tests/` new vector test.

## Out of scope / open questions
- Re-masking existing stored rows: a one-off migration step (optional, after 002). The PC re-masks on receipt (034).
- Hardening changes masked bodies, so identity relies on ref_hash (007). That is why this issue depends on 007.
