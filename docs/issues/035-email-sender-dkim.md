---
title: Authenticate receipt-email senders (domain suffix + DKIM) and shrink stored snippets
labels: [area/security, area/pc, priority/P1, size/M]
milestone: M4 Security hardening
depends_on: [010]
suggested_agent: opus · high
---
## Problem
Email sender checks are substring matches on the address: `any(w in addr for w in words)` (pc/fintrack_pc/email_import.py:118-127). So `swiggy@evil.example` passes as Swiggy. There is no DKIM/SPF check. A spoofed "receipt" can inject fake transactions, **and** its body text reaches Claude through `list_transactions.body` (pc/fintrack_pc/mcp_server.py:136), which is a prompt-injection path. Snippets also keep the delivery address, name and phone (email_import.py:141, :161, :172) (R4-S6, SEC-6).

## Use case / flow
PC email import, which confirms [02](../flows/02-food-quick-commerce-order.md)/[03](../flows/03-ride-payment.md)/[04](../flows/04-refunds-reversals-wallet-topups.md) records.

## Proposed approach
- Sender allowlist by **registrable domain suffix** (`@swiggy.in`, `@zomato.com`, `@rapido.bike`, `@uber.com`…), taken from the 004 catalog email domains.
- Require `Authentication-Results: dkim=pass header.d=<allowed domain>` as added by Gmail (parse it, don't verify crypto locally). Missing or failed → skip, and count `email_unauthenticated`.
- Snippet: keep only the amount line, the order/ride id (masked) and the merchant. Drop address blocks, names and phone. Apply the hardened masker (010). Cap at 300 chars.

## Acceptance criteria
- [ ] From `orders@swiggy.in.evil.example` → skipped.
- [ ] From `noreply@swiggy.in` with `dkim=pass header.d=swiggy.in` → imported.
- [ ] From `noreply@swiggy.in` with `dkim=fail` → skipped and counted.
- [ ] A Rapido receipt with `Drop: 12 MG Road, Ramesh, +91 98123 45678` → the snippet contains no address and no phone.

## Test fixtures needed
Made-up `.eml` fixtures with Authentication-Results variants.

## Files likely touched
`pc/fintrack_pc/email_import.py`, `textrules.py`, `pc/tests/test_email.py`.

## Out of scope / open questions
- Bank alert emails: which banks DKIM-sign from which domains needs checking on real mail (**unverified**).
- Marking MCP output as untrusted is 036.
