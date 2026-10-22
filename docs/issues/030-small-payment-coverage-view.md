---
title: Track small-payment alert gaps per bank and show a coverage view
labels: [area/alerts, area/sms, priority/P2, size/M]
milestone: M3 Alerts & auto-sync
depends_on: [024]
suggested_agent: sonnet · medium
---
## Problem
Banks are dropping SMS for small payments. HDFC has sent no SMS for UPI debits ≤₹100 / credits ≤₹500 since 25 Jun 2024. A reported RBI direction (24 Jun 2026) would make SMS mandatory only above ₹500 from 1 Jan 2027, with email mandatory at any amount. Quick-commerce and ride payments are the most exposed (R3M §Thresholds; R3B §Signal coverage; R3M rec 5, issue 5; R3B issue 6). FinTrack has no way to show which signals cover which amounts, so users don't know when to rely on notifications or email.

## Use case / flow
[07 recovery and health](../flows/07-recovery-and-health.md), [01 SMS](../flows/01-sms-capture.md)

## Proposed approach
- For each bank key, compute over 60 days: the smallest SMS-captured debit, and the count of debits ≤₹100 / ₹100–500 / >₹500 by channel (SMS / UPI notif / bank-app notif / merchant notif), plus PC email where synced.
- Coverage view (a Settings sub-screen): "HDFC: no SMS below ₹100 seen — UPI-app notifications cover 23 small payments". A suggestion to select the UPI/bank app in the picker (016) or set up email (PC).
- An expected-source hint per bank (a static table: HDFC ≤₹100 no SMS). The RBI rule is applied only after the circular is verified.

## Acceptance criteria
- [ ] Fixture: 10 HDFC SMS debits all >₹100 + 6 GPay ≤₹100 with no SMS → the view shows "HDFC: small payments via Google Pay only".
- [ ] A bank with SMS for ₹20 debits → "SMS covers all amounts".
- [ ] No data → an empty state with no false warning.
- [ ] Pure computation function with unit tests.

## Test fixtures needed
Synthetic 60-day row sets (made up).

## Files likely touched
New `core/Coverage.kt`, `ui/screens/CoverageScreen.kt`, `ui/model/UiModels.kt`, `data/TransactionStore.kt` (query).

## Out of scope / open questions
- **Unverified**: the RBI circular (24 Jun 2026) and its 1 Jan 2027 date. Thresholds for banks other than HDFC were not found. UPI Lite SMS behaviour is conflicting.
