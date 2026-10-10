"""Two-way sync: pull new / changed transactions from the phone, push PC decisions back."""

from __future__ import annotations

from .phone import PhoneClient
from .store import Store, now_ms


def pull(store: Store, phone: PhoneClient) -> dict:
    status = phone.status()
    device = status.get("device_id", "")
    if store.get_meta("device_id") != device:
        # New phone or reinstalled app: its rows have new ids, so read everything again.
        store.set_meta("device_id", device)
        store.set_meta("phone_version", "0")
    since = int(store.get_meta("phone_version", "0") or 0)
    pulled = 0
    while True:
        page = phone.changes(since)
        rows = page.get("rows", [])
        if rows:
            pulled += store.upsert_from_phone(rows)
            since = max(r["version"] for r in rows)
            store.set_meta("phone_version", str(since))
        if not page.get("has_more") or not rows:
            break
    store.set_meta("last_pull_at", str(now_ms()))
    return {"pulled": pulled, "phone_transactions": status.get("count")}


def push(store: Store, phone: PhoneClient) -> dict:
    payload = store.pending_push()
    if not payload["labels"] and "categories" not in payload:
        return {"pushed_labels": 0}
    phone.push(payload)
    store.mark_pushed(payload)
    store.set_meta("last_push_at", str(now_ms()))
    return {"pushed_labels": len(payload["labels"]), "pushed_categories": "categories" in payload}


def sync(store: Store, phone: PhoneClient) -> dict:
    result = pull(store, phone)
    result.update(push(store, phone))
    return result
