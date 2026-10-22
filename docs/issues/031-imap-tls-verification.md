---
title: Verify TLS certificates and set timeouts for Gmail IMAP import
labels: [area/security, area/pc, priority/P0, size/S]
milestone: M4 Security hardening
depends_on: []
suggested_agent: opus · medium
---
## Problem
`email_import` connects with `imaplib.IMAP4_SSL(host)` and passes no `ssl_context` (pc/fintrack_pc/email_import.py:203, :206, :242). R4 confirmed that on Python 3.12 this path does not verify the server certificate. Anyone on the network path (public Wi-Fi, hostile router) can intercept the login and steal the Gmail **app password**, which grants full mailbox read access. There is also no timeout, so a stalled server hangs the console/MCP call (R4-S1, SEC-1). **Severity: High.**

## Use case / flow
PC email import (README §4). Feeds [02](../flows/02-food-quick-commerce-order.md)/[03](../flows/03-ride-payment.md) confirmations.

## Proposed approach
- Build `ssl.create_default_context()` (`CERT_REQUIRED`, `check_hostname=True`, minimum TLS 1.2) and pass it as `ssl_context=`. Set `timeout=30`.
- Keep the injectable `imap_factory` for tests, but make the default factory a function that applies the context and timeout.
- Map `ssl.SSLCertVerificationError` to a clear error: "Gmail's certificate could not be verified — your network may be intercepting traffic. Not sending your password."
- Never include the password in exceptions or logs.

## Acceptance criteria
- [ ] A unit test asserts the default factory is called with an `ssl_context` whose `verify_mode == CERT_REQUIRED` and `check_hostname` is True.
- [ ] A test with a local self-signed IMAPS server (or a mocked SSL error) → import fails with the clear message, and `login` is never called.
- [ ] A timeout of 30 s is passed. A hung server raises within the timeout (mocked).
- [ ] The existing `pc/tests/test_email.py` passes.

## Test fixtures needed
Mock IMAP factory capturing kwargs, and a fake SSL error.

## Files likely touched
`pc/fintrack_pc/email_import.py`, `pc/tests/test_email.py`.

## Out of scope / open questions
- Moving the app password to the OS keyring is 038. Sender DKIM is 035.
