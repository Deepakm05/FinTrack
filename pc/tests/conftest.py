"""A fake phone that speaks the same HTTP API as the Android app, plus shared fixtures."""

from __future__ import annotations

import json

import httpx
import pytest

from fintrack_pc.config import Config
from fintrack_pc.mcp_server import App
from fintrack_pc.phone import PhoneClient
from fintrack_pc.store import Store

TOKEN = "test-token"


class FakePhone:
    def __init__(self):
        self.device_id = "device-1"
        self.rows: dict[str, dict] = {}
        self.version = 0
        self.categories: list[str] = []
        self.labels: dict[str, str | None] = {}
        self.dup_states: dict[str, str] = {}
        self.page_size = 500
        self.cycle_start_day = 1

    def add(self, uid, amount, body="Rs spent", sender="HDFCBK", source="BANK", direction="DEBIT",
            date="2026-10-10", sent_at=1791000000000, dup_of=None, dup_state=None):
        self.version += 1
        self.rows[uid] = {
            "uid": uid, "sender": "VM-" + sender, "sender_key": sender, "body": body,
            "sent_at": sent_at, "received_at": sent_at + 1000, "txn_date": date, "date_from_body": True,
            "amount_paise": int(round(amount * 100)), "direction": direction, "source_type": source,
            "dup_of": dup_of, "dup_score": 0.9 if dup_of else None, "dup_state": dup_state, "version": self.version,
        }

    def handler(self, request: httpx.Request) -> httpx.Response:
        if request.headers.get("authorization") != f"Bearer {TOKEN}":
            return httpx.Response(401, json={"error": "missing or wrong token"})
        path = request.url.path
        if path == "/api/status":
            return httpx.Response(200, json={"device_id": self.device_id, "count": len(self.rows), "max_version": self.version,
                                             "cycle_start_day": self.cycle_start_day})
        if path == "/api/changes":
            since = int(request.url.params.get("since", 0))
            limit = min(int(request.url.params.get("limit", 500)), self.page_size)
            rows = sorted((r for r in self.rows.values() if r["version"] > since), key=lambda r: r["version"])[:limit]
            return httpx.Response(200, json={"rows": rows, "max_version": self.version, "has_more": len(rows) == limit})
        if path == "/api/push":
            body = json.loads(request.content)
            if "categories" in body:
                self.categories = body["categories"]
            for l in body.get("labels", []):
                self.labels[l["uid"]] = l["category"]
            for d in body.get("dup_states", []):
                self.dup_states[d["uid"]] = d["state"]
            return httpx.Response(200, json={"applied": len(body.get("labels", []))})
        if path == "/api/import":
            return httpx.Response(200, json={"scanned": 10, "added": 0})
        return httpx.Response(404, json={"error": "no such endpoint"})


@pytest.fixture
def phone():
    return FakePhone()


@pytest.fixture
def app(tmp_path, phone, monkeypatch):
    monkeypatch.setenv("FINTRACK_HOME", str(tmp_path))
    cfg = Config(phone_url="http://phone.test:8765", token=TOKEN)
    client = PhoneClient(cfg.phone_url, TOKEN, transport=httpx.MockTransport(phone.handler))
    return App(config=cfg, store=Store(tmp_path / "fintrack.db"), phone=client)
