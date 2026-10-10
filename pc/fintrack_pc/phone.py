"""HTTP client for the FinTrack phone app."""

from __future__ import annotations

import json

import httpx


class PhoneError(RuntimeError):
    pass


class PhoneClient:
    def __init__(self, base_url: str, token: str, transport: httpx.BaseTransport | None = None, timeout: float = 15):
        self.base_url = base_url.rstrip("/")
        self._client = httpx.Client(
            base_url=self.base_url,
            headers={"Authorization": f"Bearer {token}"},
            timeout=timeout,
            transport=transport,
        )

    def _call(self, method: str, path: str, **kwargs) -> dict:
        try:
            resp = self._client.request(method, path, **kwargs)
        except httpx.TransportError as e:
            raise PhoneError(
                f"Phone not reachable at {self.base_url}. Is PC link ON in the app, and is the phone on the same "
                f"Wi-Fi (or, for USB, did you run 'adb forward tcp:8765 tcp:8765')? ({e.__class__.__name__})"
            ) from e
        if resp.status_code == 401:
            raise PhoneError("The phone rejected the token. Copy it again from the app and run 'fintrack setup'.")
        if resp.status_code >= 400:
            raise PhoneError(f"Phone returned {resp.status_code}: {resp.text[:200]}")
        return resp.json()

    def status(self) -> dict:
        return self._call("GET", "/api/status")

    def changes(self, since: int, limit: int = 500) -> dict:
        return self._call("GET", "/api/changes", params={"since": since, "limit": limit})

    def push(self, payload: dict) -> dict:
        return self._post("/api/push", payload)

    def import_inbox(self, days: int) -> dict:
        return self._post("/api/import", {"days": days}, timeout=300)

    def _post(self, path: str, payload: dict, **kwargs) -> dict:
        # The phone's HTTP server assumes ASCII unless the charset is explicit.
        return self._call(
            "POST", path, content=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={"Content-Type": "application/json; charset=utf-8"}, **kwargs,
        )

    def close(self) -> None:
        self._client.close()
