from email.message import EmailMessage

from fintrack_pc import sync
from fintrack_pc.email_import import parse_email
from fintrack_pc.reconcile import reconcile
from fintrack_pc.statement_import import parse_statement

T0 = 1791000000000  # 2026-10-03 (Asia/Kolkata midday-ish); dates below use txn_date explicitly
MIN = 60_000


def email_row(uid, amount, sender_key, at, body="receipt", date="2026-10-10"):
    return {"uid": uid, "sender": "email:x", "sender_key": sender_key, "body": body, "sent_at": at, "received_at": at,
            "txn_date": date, "date_from_body": False, "amount_paise": amount * 100, "direction": "DEBIT",
            "source_type": "EMAIL", "dup_of": None, "dup_score": None}


def stmt_row(uid, amount, date, body="UPI", account="HDFC", at=T0):
    return {"uid": uid, "sender": "statement:" + account, "sender_key": account, "body": body, "sent_at": at,
            "received_at": at, "txn_date": date, "date_from_body": True, "amount_paise": amount * 100,
            "direction": "DEBIT", "source_type": "STATEMENT", "dup_of": None, "dup_score": None}


def counted_total(app):
    return app.store.summary(None, None)["total"]


def test_rapido_email_links_to_upi_debit_to_personal_vpa(app, phone):
    phone.add("bank", 103, body="Rs 103 debited via UPI to 98XXXX@ybl", sent_at=T0)
    sync.pull(app.store, app.phone)
    app.store.insert_local([email_row("e1", 103, "RAPIDO", T0 + 2 * MIN, body="Ride Invoice | Rapido ride Total ₹103")])
    assert reconcile(app.store)["linked"] == 1
    e = app.store.get("e1")
    assert e["dup_of"] == "bank" and e["dup_state"] == "auto"
    assert counted_total(app) == 103
    rows, _ = app.store.list_transactions()
    bank = next(r for r in rows if r["uid"] == "bank")
    assert bank["linked"][0]["sender_key"] == "RAPIDO"  # Claude sees it was a ride


def test_email_first_then_phone_sync_moves_email_under_bank(app, phone):
    app.store.insert_local([email_row("e1", 245, "UBER", T0)])
    reconcile(app.store)
    app.store.set_labels([("e1", "Transport")], "manual")
    phone.add("bank", 245, body="Rs 245 debited UPI", sent_at=T0 + 60_000)
    out = sync.sync(app.store, app.phone)  # sync runs reconcile
    assert out["linked_on_pc"] == 1
    assert app.store.get("e1")["dup_of"] == "bank"
    bank = app.store.get("bank")
    assert bank["category"] == "Transport" and bank["label_source"] == "manual"  # label carried over
    assert phone.labels["bank"] == "Transport"
    assert counted_total(app) == 245


def test_statement_confirms_tracked_and_adds_missed(app, phone):
    phone.add("tea1", 40, sender="GPAY", source="UPI_APP", date="2026-10-10", sent_at=T0)
    sync.pull(app.store, app.phone)
    app.store.insert_local([
        stmt_row("s1", 40, "2026-10-10", "UPI-RAMESH TEA"),
        stmt_row("s2", 40, "2026-10-10", "UPI-RAMESH TEA"),   # second tea the phone missed
        stmt_row("s3", 999, "2026-10-08", "UPI-SOMETHING"),   # missed entirely
    ])
    reconcile(app.store)
    linked = [u for u in ("s1", "s2") if app.store.get(u)["dup_of"] == "tea1"]
    assert len(linked) == 1  # one statement line per payment
    assert counted_total(app) == 40 + 40 + 999


def test_email_and_statement_without_phone_record(app):
    app.store.insert_local([email_row("e1", 349, "SWIGGY", T0, date="2026-10-09"), stmt_row("s1", 349, "2026-10-09", "UPI-SWIGGY")])
    reconcile(app.store)
    assert app.store.get("s1")["dup_of"] == "e1"
    assert counted_total(app) == 349


def test_repeat_merchant_emails(app):
    app.store.insert_local([email_row("a1", 1299, "AMAZON", T0), email_row("a2", 1299, "AMAZON", T0 + 5 * MIN),
                            email_row("a3", 1299, "AMAZON", T0 + 2 * 24 * 60 * MIN)])
    reconcile(app.store)
    assert app.store.get("a2")["dup_of"] == "a1" and app.store.get("a2")["dup_state"] == "auto"
    assert app.store.get("a3")["dup_state"] == "possible"  # 2 days later: maybe a second order, you decide
    assert counted_total(app) == 1299 * 2


def test_rejected_link_is_not_recreated(app, phone):
    phone.add("bank", 103, sent_at=T0)
    sync.pull(app.store, app.phone)
    app.store.insert_local([email_row("e1", 103, "RAPIDO", T0 + MIN)])
    reconcile(app.store)
    app.store.set_dup_decision("e1", "rejected")
    assert reconcile(app.store)["linked"] == 0
    assert counted_total(app) == 206
    # PC-only rows are never sent to the phone
    assert all(l["uid"] != "e1" for l in app.store.pending_push()["labels"])
