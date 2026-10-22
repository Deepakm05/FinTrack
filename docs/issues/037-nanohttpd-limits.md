---
title: Bound the phone HTTP server (threads, body size, error leakage) and plan a replacement
labels: [area/security, area/sync, priority/P1, size/M]
milestone: M4 Security hardening
depends_on: []
suggested_agent: opus · medium
---
## Problem
The phone link uses NanoHTTPD 2.3.1 (android/app/build.gradle.kts:57), which is unmaintained (R4-S7, SEC-8):
- The default AsyncRunner spawns one thread per connection **before** auth, so a LAN client can exhaust threads and memory (server/LinkService.kt:37).
- `readJson` calls `parseBody` with no size cap (server/ApiServer.kt:106-111), so a huge POST is buffered to disk/memory.
- `e.toString()` is returned to the client (ApiServer.kt:36), leaking class names and messages.

## Use case / flow
[08 auto sync](../flows/08-auto-sync.md)

## Proposed approach
- A custom `AsyncRunner` with a bounded pool (4 threads, queue 8). Reject with 503 when full.
- Check `Content-Length` before `parseBody`: >256 KB → 413. Missing on POST → 411.
- Generic errors: `{"error":"internal error"}`, with details only in logcat (no bodies).
- Keep the auth check before any body parsing (already true).
- A short design note (in the issue thread) evaluating a replacement: the JDK `com.sun.net.httpserver` is not on Android, Ktor CIO adds dependencies, or a minimal hand-rolled server. Decide alongside 033 TLS needs.

## Acceptance criteria
- [ ] 50 concurrent unauthenticated connections → at most 4 worker threads. Excess get 503 and the app stays responsive.
- [ ] POST `/api/push` with a 1 MB body → 413, nothing parsed.
- [ ] Force an exception in a route → the response body is exactly `{"error":"internal error"}`.
- [ ] The existing PC sync tests still pass against a real phone (manual) or a mocked server.

## Test fixtures needed
JVM unit tests constructing ApiServer on a random port (NanoHTTPD runs on the JVM).

## Files likely touched
`server/ApiServer.kt`, `server/LinkService.kt`, new `server/BoundedRunner.kt`, tests.

## Out of scope / open questions
- Full server replacement: a separate follow-up once 033 decides the TLS path.
