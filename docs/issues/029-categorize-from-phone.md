---
title: Let the user categorize a payment on the phone and sync it to the PC
labels: [area/sync, area/alerts, priority/P2, size/L]
milestone: M3 Alerts & auto-sync
depends_on: [002, 022, 026]
suggested_agent: sonnet · high
---
## Problem
The phone can display a category but cannot set one. The detail sheet is read-only (android/.../ui/screens/TxnDetailSheet.kt:77-80). The sync JSON carries no category from the phone (server/ApiServer.kt:116-131). Only the PC writes categories (`applyFromPc`, data/TransactionStore.kt:156). A "Categorize" action on a capture alert therefore has nowhere to write (R2-F7, R2-I5).

## Use case / flow
[06 capture alert and categorize](../flows/06-capture-alert-and-categorize.md)

## Proposed approach
- Phone: a category picker in the detail sheet (categories already pushed by the PC) plus the "Categorize" action on the alert (022). Writing sets `category`, `category_source='phone'`, `category_version++` and bumps the row version.
- `/api/changes` includes `category`, `category_source` and `category_version`.
- PC: accept a phone label only if the PC row's label is unchanged since the last sync (compare `labeled_at`/version). Otherwise the PC wins and the conflict is logged in `label_history` as `phone_conflict`. Accepted phone labels are `reviewed=true`, `label_source='phone'` (manual user action).
- After a PC push, the phone stops marking the row pending.

## Acceptance criteria
- [ ] Phone sets ₹249 Swiggy → "Food". The next sync → the PC row category is Food, source phone, reviewed.
- [ ] The PC changed the same row to "Groceries" after the last sync, and the phone sets "Food" → the PC keeps Groceries, a history row is logged, and the phone receives Groceries on push.
- [ ] Claude can't overwrite a phone label (same rule as manual console labels).
- [ ] The alert "Categorize" action opens the sheet with the picker focused.

## Test fixtures needed
PC store tests for accept/conflict. Phone store tests for version bumps.

## Files likely touched
`ui/screens/TxnDetailSheet.kt`, `ui/model/UiModels.kt`, `data/TransactionStore.kt`, `server/ApiServer.kt`, `notify/TxnAlerter.kt`, `pc/fintrack_pc/store.py`, `sync.py`, tests.

## Out of scope / open questions
- Does a phone label count as a "reviewed" training row? Proposed yes (it is the user). Confirm with the dataset policy in README §6.
