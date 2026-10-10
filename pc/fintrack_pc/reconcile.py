"""PC-side duplicate matching across every source.

The phone already links its own records (bank SMS, UPI app notification, delivery SMS). The PC adds email
receipts / bank alert emails and bank statement lines, and links them to the phone's records, or to each
other, when they describe the same payment.

Rules that keep this safe with sync:
* The PC never changes a phone row's link (the phone owns those). A PC row (email / statement) becomes the
  duplicate when it matches a phone row.
* Between PC rows, an email is the main record over a statement line.
* One payment gets at most one line per bank statement account, so two ₹40 teas on the same day stay two.
"""

from __future__ import annotations

import sqlite3
from datetime import date

from .store import PC_SOURCES, PHONE_SOURCES, PUSH_FLAG, Store, now_ms
from .textrules import brand_in

MIN = 60_000
HOUR = 60 * MIN
DAY = 24 * HOUR
AUTO_THRESHOLD = 0.7


def _rank(row) -> int:
    if row["source_type"] in PHONE_SOURCES:
        return 0
    return 1 if row["source_type"] == "EMAIL" else 2


def _mentions(a, b) -> bool:
    return bool(brand_in(f"{a['body']} {a['sender_key']}") & brand_in(f"{b['body']} {b['sender_key']}"))


def score(a, b) -> float | None:
    """How likely two records are the same payment, or None if they can't be."""
    if a["amount_paise"] != b["amount_paise"] or a["direction"] != b["direction"]:
        return None
    sa, sb = a["source_type"], b["source_type"]
    if sa in PHONE_SOURCES and sb in PHONE_SOURCES:
        return None  # the phone decides these
    mention = _mentions(a, b)
    if "STATEMENT" in (sa, sb):
        if sa == sb == "STATEMENT":
            return None
        days = abs((date.fromisoformat(a["txn_date"]) - date.fromisoformat(b["txn_date"])).days)
        base = {0: 0.8, 1: 0.5}.get(days)
    else:
        gap = abs(a["sent_at"] - b["sent_at"])
        if sa == sb == "EMAIL":
            if a["sender_key"] != b["sender_key"]:
                return None
            # Same merchant, same amount: a second email about the same order (placed / shipped).
            base = 0.8 if gap <= 30 * MIN else 0.4 if gap <= 3 * DAY else None
        elif gap <= 10 * MIN:
            base = 0.9
        elif gap <= 2 * HOUR:
            base = 0.7
        elif gap <= DAY and mention:
            base = 0.4
        else:
            base = None
    if base is None:
        return None
    return min(1.0, base + (0.1 if mention else 0.0))


def _group_main(row) -> str:
    """The main record of a row's group. A link you rejected makes the row a payment of its own."""
    if row["dup_of"] is None or row["dup_decision"] == "rejected":
        return row["uid"]
    return row["dup_of"]


def _statement_accounts(db: sqlite3.Connection, main_uid: str) -> set[str]:
    rows = db.execute(
        "SELECT sender_key FROM txn WHERE source_type = 'STATEMENT' AND (uid = ? OR dup_of = ?)", (main_uid, main_uid)
    )
    return {r[0] for r in rows}


def _best_group(db: sqlite3.Connection, r) -> tuple[str, float] | None:
    cands = db.execute(
        """SELECT * FROM txn WHERE uid != ? AND amount_paise = ? AND direction = ? AND sent_at BETWEEN ? AND ?""",
        (r["uid"], r["amount_paise"], r["direction"], r["sent_at"] - 3 * DAY, r["sent_at"] + 3 * DAY),
    ).fetchall()
    own_accounts = _statement_accounts(db, r["uid"])
    best: tuple[float, int, str] | None = None
    for m in cands:
        main = _group_main(m)
        if main == r["uid"]:
            continue
        s = score(r, m)
        if s is None:
            continue
        if own_accounts & _statement_accounts(db, main):
            continue  # both groups already have a line from the same statement: two separate payments
        gap = abs(r["sent_at"] - m["sent_at"])
        if best is None or (s, -gap) > (best[0], -best[1]):
            best = (s, gap, main)
    return (best[2], best[0]) if best else None


def _merge(db: sqlite3.Connection, r, target_uid: str, s: float) -> None:
    t = db.execute("SELECT * FROM txn WHERE uid = ?", (target_uid,)).fetchone()
    if (_rank(t), t["sent_at"]) <= (_rank(r), r["sent_at"]):
        main, moved = t, r
    else:
        main, moved = r, t  # only happens when t is a PC row too
    state = "auto" if s >= AUTO_THRESHOLD else "possible"
    db.execute(
        "UPDATE txn SET dup_of = ?, dup_score = ?, dup_state_pc = ? WHERE uid = ?",
        (main["uid"], s, state, moved["uid"]),
    )
    db.execute("UPDATE txn SET dup_of = ? WHERE dup_of = ?", (main["uid"], moved["uid"]))
    if main["category"] is None and moved["category"] is not None:
        ts = now_ms()
        db.execute(
            f"""UPDATE txn SET category = ?, label_source = ?, reviewed = ?, labeled_at = ?, push_pending = {PUSH_FLAG}
                WHERE uid = ?""",
            (moved["category"], moved["label_source"], moved["reviewed"], ts, main["uid"]),
        )
        db.execute(
            """INSERT INTO label_history(uid, category, previous_category, source, action, at)
               VALUES (?, ?, NULL, ?, 'inherit', ?)""",
            (main["uid"], moved["category"], moved["label_source"] or "manual", ts),
        )


def reconcile(store: Store) -> dict:
    """Links email / statement rows to the records they duplicate. Safe to run any number of times."""
    linked = 0
    with store.connect() as db:
        # Keep PC links one level deep if the phone has since linked their target to another row.
        db.execute(
            f"""UPDATE txn SET dup_of = (SELECT p.dup_of FROM txn p WHERE p.uid = txn.dup_of)
                WHERE source_type IN ('EMAIL', 'STATEMENT') AND dup_of IN (
                    SELECT uid FROM txn WHERE dup_of IS NOT NULL AND COALESCE(dup_decision, '') != 'rejected')"""
        )
        pending = db.execute(
            f"""SELECT uid FROM txn WHERE source_type IN ({','.join('?' for _ in PC_SOURCES)}) AND dup_of IS NULL
                ORDER BY CASE source_type WHEN 'EMAIL' THEN 0 ELSE 1 END, sent_at""",
            PC_SOURCES,
        ).fetchall()
        for (uid,) in pending:
            r = db.execute("SELECT * FROM txn WHERE uid = ?", (uid,)).fetchone()
            if r is None or r["dup_of"] is not None:
                continue  # already merged under another row in this pass
            found = _best_group(db, r)
            if found:
                _merge(db, r, *found)
                linked += 1
    return {"linked": linked}
