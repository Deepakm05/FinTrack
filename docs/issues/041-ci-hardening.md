---
title: Harden CI workflows (pinned actions, least privilege, no debug-key releases)
labels: [area/security, priority/P2, size/S]
milestone: M4 Security hardening
depends_on: []
suggested_agent: sonnet · medium
---
## Problem
`.github/workflows/android.yml` pins actions by tag (`actions/checkout@v4`, `gradle/actions/setup-gradle@v4`…), not by commit SHA. It grants `contents: write` to the whole job (used to publish UI screenshots). The release build falls back to the debug key when the signing secrets are missing, so a public repo can publish a debug-signed APK that users then cannot update over (R4-S13).

## Use case / flow
Release / install (README §1–2).

## Proposed approach
- Pin every third-party action to a full commit SHA, with the tag in a comment. Add Dependabot for `github-actions`.
- Default `permissions: contents: read`. Grant `contents: write` only to the screenshot-publish job/step.
- If the signing secrets are absent, build only the debug variant and skip the release artifact (no silent debug-signed "release").
- Apply the same pinning to `pc.yml`.

## Acceptance criteria
- [ ] Every `uses:` line references a 40-char SHA.
- [ ] The top-level permissions are read-only. Only the screenshot job has write.
- [ ] A fork PR without secrets → no `app-release.apk` artifact is produced.
- [ ] A dependabot.yml exists for github-actions.

## Test fixtures needed
None (workflow review). Optionally actionlint in CI.

## Files likely touched
`.github/workflows/android.yml`, `.github/workflows/pc.yml`, new `.github/dependabot.yml`.

## Out of scope / open questions
- None.
