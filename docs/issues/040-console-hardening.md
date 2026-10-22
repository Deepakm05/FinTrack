---
title: Harden the web console and statement parsing (session token, upload limits, parse limits)
labels: [area/security, area/pc, priority/P2, size/M]
milestone: M4 Security hardening
depends_on: []
suggested_agent: opus · medium
---
## Problem
- Console GET endpoints are unauthenticated to any local process. The Host check + `X-FinTrack` header middleware (pc/fintrack_pc/console.py:28-49) stops browsers, not local malware or other users on a shared PC (R4-S11).
- Statement upload has no size cap. The PDF password travels in the query string (console.py:137-148), so it ends up in logs and history (R4-S11).
- Statement parsing (openpyxl/xlrd/pdfplumber) has no size, page or time limits (pc/fintrack_pc/statement_import.py:27-73). Dependencies are unpinned, and the pdfminer.six advisory needs checking (pc/pyproject.toml:19) (R4-S12, SEC-11).

## Use case / flow
PC console (README §6), statement import (README §5).

## Proposed approach
- A per-session random token, generated at `serve()` start and injected into the served HTML. It is required as a header on **all** `/api/*` routes (GET included). The token rotates on restart.
- The upload body limit is 20 MB. The PDF password moves to a POST form field.
- Parse limits: max 500 PDF pages, max 50k spreadsheet rows, a 60 s wall clock (run in a worker with a timeout).
- Pin minimum safe versions (`pdfminer.six` ≥ the patched release, `pdfplumber`, `openpyxl`) after checking advisories. Add `pip-audit` to the PC CI.

## Acceptance criteria
- [ ] `GET /api/transactions` without the session header → 403. With it → 200.
- [ ] A 25 MB upload → 413.
- [ ] The password in the query string is ignored. The password in the form body works.
- [ ] A 600-page PDF → refused with "too many pages".
- [ ] The CI job runs `pip-audit` (or equivalent) and fails on known-vulnerable pins.

## Test fixtures needed
Console test client and synthetic oversized files (generated in tmp).

## Files likely touched
`pc/fintrack_pc/console.py`, `static/console.html`, `statement_import.py`, `pc/pyproject.toml`, `.github/workflows/pc.yml`, `pc/tests/test_console.py`, `test_statement.py`.

## Out of scope / open questions
- Which pdfminer.six advisory applies is **unverified**. Check it at implementation time.
