from fintrack_pc import sync
from fintrack_pc.store import DEFAULT_CATEGORIES


def test_pull_is_incremental_and_paged(app, phone):
    phone.page_size = 2
    for i in range(5):
        phone.add(f"u{i}", 100 + i)
    assert sync.pull(app.store, app.phone)["pulled"] == 5
    phone.add("u5", 50)
    assert sync.pull(app.store, app.phone)["pulled"] == 1
    assert app.store.counts()["total"] == 6


def test_new_device_resets_cursor(app, phone):
    phone.add("a", 10)
    sync.pull(app.store, app.phone)
    phone.device_id = "device-2"
    phone.rows.clear(); phone.version = 0
    phone.add("b", 20)
    assert sync.pull(app.store, app.phone)["pulled"] == 1


def test_default_categories_are_pushed_once(app, phone):
    sync.sync(app.store, app.phone)
    assert phone.categories == sorted(DEFAULT_CATEGORIES, key=str.lower)
    phone.categories = []
    sync.sync(app.store, app.phone)
    assert phone.categories == []  # nothing changed, nothing re-sent


def test_manual_beats_claude_and_history_is_kept(app, phone):
    phone.add("a", 349, body="Rs 349 spent at SWIGGY")
    sync.pull(app.store, app.phone)
    s = app.store
    assert s.set_labels([("a", "shopping")], "claude").updated == ["a"]
    row = s.get("a")
    assert row["category"] == "Shopping" and row["label_source"] == "claude" and not row["reviewed"]

    s.set_labels([("a", "Food & Dining")], "manual")
    res = s.set_labels([("a", "Groceries")], "claude")
    assert res.skipped and s.get("a")["category"] == "Food & Dining"
    actions = [h["action"] for h in s.history("a")]
    assert actions == ["label", "correct"]


def test_unknown_category_and_unknown_txn_rejected(app, phone):
    phone.add("a", 10)
    sync.pull(app.store, app.phone)
    res = app.store.set_labels([("a", "Pizza"), ("zzz", "Other")], "claude")
    assert len(res.errors) == 2 and not res.updated


def test_labels_and_dup_decisions_reach_the_phone(app, phone):
    phone.add("bank", 349, body="Rs 349 spent at SWIGGY")
    phone.add("swg", 349, sender="SWIGGY", source="DELIVERY", dup_of="bank", dup_state="auto")
    sync.pull(app.store, app.phone)
    app.store.set_labels([("bank", "Food & Dining")], "manual")
    # the linked delivery SMS gets the same label
    assert app.store.get("swg")["category"] == "Food & Dining"
    app.store.set_dup_decision("swg", "rejected")
    out = sync.push(app.store, app.phone)
    assert out["pushed_labels"] == 2
    assert phone.labels == {"bank": "Food & Dining", "swg": "Food & Dining"}
    assert phone.dup_states == {"swg": "rejected"}
    assert app.store.counts()["push_pending"] == 0


def test_duplicates_excluded_from_summary_unless_rejected(app, phone):
    phone.add("bank", 349, body="at SWIGGY")
    phone.add("swg", 349, sender="SWIGGY", source="DELIVERY", dup_of="bank", dup_state="auto")
    phone.add("maybe", 100, sender="ZOMATO", source="DELIVERY", dup_of="bank2", dup_state="possible")
    phone.add("bank2", 100)
    phone.add("salary", 50000, direction="CREDIT")
    sync.pull(app.store, app.phone)
    s = app.store.summary("2026-10-01", "2026-10-31")
    assert s["total"] == 349 + 100 + 100  # possible duplicates still count
    app.store.set_dup_decision("maybe", "confirmed")
    assert app.store.summary("2026-10-01", "2026-10-31")["total"] == 449
    app.store.set_dup_decision("swg", "rejected")
    assert app.store.summary("2026-10-01", "2026-10-31")["total"] == 798


def test_rename_and_merge_categories(app, phone):
    phone.add("a", 10); phone.add("b", 20)
    sync.pull(app.store, app.phone)
    s = app.store
    s.set_labels([("a", "Fuel")], "manual")
    s.set_labels([("b", "Transport")], "claude")
    r = s.rename_category("Fuel", "Transport")
    assert r["merged"] and r["transactions"] == 1
    assert "Fuel" not in s.categories()
    assert s.get("a")["category"] == "Transport" and s.get("a")["label_source"] == "manual"
    r = s.rename_category("Transport", "Travel & Transport")
    assert not r["merged"] and r["transactions"] == 2
    assert s.history("a")[-1]["action"] == "rename"


def test_dataset_only_reviewed_rows(app, phone):
    phone.add("a", 349, body="Rs 349 spent at SWIGGY")
    phone.add("b", 999, body="Rs 999 spent at AMAZON")
    phone.add("c", 60, body="Rs 60 spent at UBER")
    sync.pull(app.store, app.phone)
    s = app.store
    s.set_labels([("a", "Food & Dining"), ("b", "Shopping"), ("c", "Transport")], "claude")
    s.accept(["a"])
    s.set_labels([("b", "Groceries")], "manual")
    rows = {r["uid"]: r for r in s.dataset()}
    assert set(rows) == {"a", "b"}
    assert rows["b"]["was_corrected"] and rows["b"]["machine_label"] == "Shopping"
    assert not rows["a"]["was_corrected"] and rows["a"]["label_source"] == "claude"
    assert len(s.dataset(include_unreviewed=True)) == 3
    stats = s.dataset_stats()
    assert stats["training_rows"] == 2 and stats["claude_labels_corrected"] == 1 and stats["claude_labels_accepted"] == 1


def test_wrong_token_gives_clear_error(app, phone, tmp_path):
    import httpx
    import pytest
    from fintrack_pc.phone import PhoneClient, PhoneError
    bad = PhoneClient("http://phone.test", "nope", transport=httpx.MockTransport(phone.handler))
    with pytest.raises(PhoneError, match="token"):
        bad.status()


def test_upi_notifications_filter_and_dedupe(app, phone):
    phone.add("bank", 349, body="Rs 349 debited via UPI to swiggy@icici")
    phone.add("gpay", 349, sender="GPAY", source="UPI_APP", body="Paid ₹349 to Swiggy", dup_of="bank", dup_state="auto")
    phone.add("swg", 349, sender="SWIGGY", source="DELIVERY", dup_of="bank", dup_state="auto")
    phone.add("tea", 40, sender="GPAY", source="UPI_APP", body="₹40 paid to Ramesh Tea Stall")
    sync.pull(app.store, app.phone)
    rows, total = app.store.list_transactions(source="UPI_APP")
    assert total == 2 and {r["uid"] for r in rows} == {"gpay", "tea"}
    assert app.store.summary(None, None)["total"] == 349 + 40
    app.store.set_labels([("bank", "Food & Dining")], "manual")
    assert app.store.get("gpay")["category"] == app.store.get("swg")["category"] == "Food & Dining"
