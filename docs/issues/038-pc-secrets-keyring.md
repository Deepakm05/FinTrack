---
title: Store PC secrets in the OS keyring and stop `fintrack setup` erasing email credentials
labels: [area/security, area/pc, priority/P1, size/M]
milestone: M4 Security hardening
depends_on: []
suggested_agent: opus · medium
---
## Problem
- `~/.fintrack/config.json` holds the phone token and the Gmail app password in plaintext. `path.chmod(0o600)` (pc/fintrack_pc/config.py:43) has no effect on Windows ACLs (R4-S8).
- `fintrack setup --token <token>` puts the token in argv and shell history (pc/fintrack_pc/cli.py:33).
- **Bug**: `setup` builds a fresh `Config(phone_url=…, token=…, console_port=…)` and saves it (cli.py:57-58). That **erases the saved email address and app password**.
- `config.json` is not in `.gitignore`. It lives outside the repo by default, so this is defensive only (verified: the .gitignore has no entry).

## Use case / flow
PC setup (README §3–4), [08 auto sync](../flows/08-auto-sync.md)

## Proposed approach
- Use `keyring` (Windows Credential Manager / macOS Keychain / Secret Service) for `token` and `email_app_password`. config.json keeps only non-secrets plus a `secrets_in_keyring: true` flag. Fall back to the file, with a warning, if no backend exists.
- `setup` loads the existing config and updates only the given fields.
- The token comes from a prompt (`getpass`) or the `FINTRACK_TOKEN` env var. `--token` is kept but deprecated, with a warning.
- Migrate existing plaintext secrets into the keyring on the first run, then blank them in the file.
- Add `config.json` and `.fintrack/` to `.gitignore`.

## Acceptance criteria
- [ ] After `email-setup` then `setup --phone-url …`, the email credentials are still present (regression test for the bug).
- [ ] With a fake keyring backend, config.json contains no token or password.
- [ ] `setup` without `--token` prompts (getpass mocked).
- [ ] Migration moves existing secrets and the file is rewritten without them.

## Test fixtures needed
A `keyring` in-memory backend fixture, tmp HOME.

## Files likely touched
`pc/fintrack_pc/config.py`, `cli.py`, `pc/pyproject.toml` (keyring dependency), `.gitignore`, `pc/tests/`.

## Out of scope / open questions
- Whether to make `keyring` an optional extra or a hard dependency: proposed hard dependency (small, pure Python).
