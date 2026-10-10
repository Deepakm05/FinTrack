"""PC database: the main copy of categories, labels, duplicate decisions and the label history.

The phone owns the transactions (it parses the SMS). The PC owns everything decided about them.
Every label change is appended to ``label_history`` and never overwritten, so the history
doubles as a training dataset for a future ML model.
"""

from __future__ import annotations

import sqlite3
import time
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import date, datetime
from pathlib import Path
from typing import Iterable, Iterator

DEFAULT_CATEGORIES = [
    "Food & Dining", "Groceries", "Shopping", "Transport", "Fuel", "Bills & Utilities", "Rent",
    "Entertainment", "Health", "Travel", "Education", "Transfers", "Investments", "Other",
]

LABEL_SOURCES = ("manual", "claude", "model")

SCHEMA = """
CREATE TABLE IF NOT EXISTS txn (
    uid TEXT PRIMARY KEY,
    sender TEXT NOT NULL,
    sender_key TEXT NOT NULL,
    body TEXT NOT NULL,
    sent_at INTEGER NOT NULL,
    received_at INTEGER NOT NULL,
    txn_date TEXT NOT NULL,
    date_from_body INTEGER NOT NULL,
    amount_paise INTEGER NOT NULL,
    direction TEXT NOT NULL,
    source_type TEXT NOT NULL,
    dup_of TEXT,
    dup_score REAL,
    dup_state_phone TEXT,
    dup_decision TEXT,
    category TEXT,
    label_source TEXT,
    reviewed INTEGER NOT NULL DEFAULT 0,
    labeled_at INTEGER,
    phone_version INTEGER NOT NULL DEFAULT 0,
    push_pending INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS txn_date_idx ON txn(txn_date);
CREATE INDEX IF NOT EXISTS txn_dup_idx ON txn(dup_of);
CREATE TABLE IF NOT EXISTS category (
    name TEXT PRIMARY KEY COLLATE NOCASE,
    created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS label_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uid TEXT NOT NULL,
    category TEXT,
    previous_category TEXT,
    source TEXT NOT NULL,
    action TEXT NOT NULL,
    at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS label_history_uid ON label_history(uid);
CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT);
"""

# A row counts in totals unless it is linked as a duplicate that was not rejected / only "possible".
COUNTED = (
    "(t.dup_of IS NULL OR COALESCE(t.dup_decision, t.dup_state_phone) IN ('possible', 'rejected'))"
)

PHONE_FIELDS = (
    "sender", "sender_key", "body", "sent_at", "received_at", "txn_date", "date_from_body",
    "amount_paise", "direction", "source_type", "dup_of", "dup_score",
)


class StoreError(ValueError):
    """A request that cannot be applied (unknown category, unknown transaction, ...)."""


def now_ms() -> int:
    return int(time.time() * 1000)


@dataclass
class LabelResult:
    updated: list[str] = field(default_factory=list)
    skipped: list[dict] = field(default_factory=list)
    errors: list[dict] = field(default_factory=list)

    def as_dict(self) -> dict:
        return {"updated": len(self.updated), "skipped": self.skipped, "errors": self.errors}


class Store:
    def __init__(self, path: Path | str):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as db:
            db.executescript(SCHEMA)
            if db.execute("SELECT value FROM meta WHERE key = 'seeded'").fetchone() is None:
                ts = now_ms()
                db.executemany(
                    "INSERT OR IGNORE INTO category(name, created_at) VALUES (?, ?)",
                    [(c, ts) for c in DEFAULT_CATEGORIES],
                )
                db.execute("INSERT INTO meta(key, value) VALUES ('seeded', '1')")
                self._set_meta(db, "categories_dirty", "1")

    @contextmanager
    def connect(self) -> Iterator[sqlite3.Connection]:
        """One short-lived connection per operation: safe across threads and processes."""
        db = sqlite3.connect(self.path, timeout=10)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA journal_mode=WAL")
        try:
            with db:
                yield db
        finally:
            db.close()

    # ------------------------------------------------------------------ meta

    def get_meta(self, key: str, default: str | None = None) -> str | None:
        with self.connect() as db:
            row = db.execute("SELECT value FROM meta WHERE key = ?", (key,)).fetchone()
        return row["value"] if row else default

    def set_meta(self, key: str, value: str) -> None:
        with self.connect() as db:
            self._set_meta(db, key, value)

    @staticmethod
    def _set_meta(db: sqlite3.Connection, key: str, value: str) -> None:
        db.execute("INSERT INTO meta(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value", (key, value))

    # ------------------------------------------------------------------ rows from the phone

    def upsert_from_phone(self, rows: Iterable[dict]) -> int:
        """Inserts new phone rows and refreshes phone-owned fields. Labels on the PC are kept."""
        n = 0
        with self.connect() as db:
            for r in rows:
                values = [r[f] if f != "date_from_body" else int(bool(r[f])) for f in PHONE_FIELDS]
                db.execute(
                    f"""INSERT INTO txn(uid, {', '.join(PHONE_FIELDS)}, dup_state_phone, phone_version)
                        VALUES (?, {', '.join('?' for _ in PHONE_FIELDS)}, ?, ?)
                        ON CONFLICT(uid) DO UPDATE SET
                        {', '.join(f'{f} = excluded.{f}' for f in PHONE_FIELDS)},
                        dup_state_phone = excluded.dup_state_phone,
                        phone_version = excluded.phone_version""",
                    [r["uid"], *values, r.get("dup_state"), r["version"]],
                )
                n += 1
        return n

    # ------------------------------------------------------------------ categories

    def categories(self) -> list[str]:
        with self.connect() as db:
            return [r["name"] for r in db.execute("SELECT name FROM category ORDER BY name COLLATE NOCASE")]

    def category_counts(self) -> list[dict]:
        with self.connect() as db:
            rows = db.execute(
                """SELECT c.name, COUNT(t.uid) AS n FROM category c
                   LEFT JOIN txn t ON t.category = c.name COLLATE NOCASE
                   GROUP BY c.name ORDER BY c.name COLLATE NOCASE"""
            ).fetchall()
        return [{"name": r["name"], "transactions": r["n"]} for r in rows]

    def add_category(self, name: str) -> str:
        name = " ".join(name.split())
        if not name:
            raise StoreError("category name is empty")
        with self.connect() as db:
            existing = db.execute("SELECT name FROM category WHERE name = ?", (name,)).fetchone()
            if existing:
                return existing["name"]
            db.execute("INSERT INTO category(name, created_at) VALUES (?, ?)", (name, now_ms()))
            self._set_meta(db, "categories_dirty", "1")
        return name

    def rename_category(self, old: str, new: str) -> dict:
        """Renames a category. If [new] already exists the two are merged."""
        new = " ".join(new.split())
        if not new:
            raise StoreError("new name is empty")
        with self.connect() as db:
            src = db.execute("SELECT name FROM category WHERE name = ?", (old,)).fetchone()
            if src is None:
                raise StoreError(f"unknown category: {old}")
            old = src["name"]
            target = db.execute("SELECT name FROM category WHERE name = ?", (new,)).fetchone()
            merged = target is not None and target["name"] != old
            if target is None or target["name"] == old:
                # Plain rename (also handles a change of letter case only).
                db.execute("DELETE FROM category WHERE name = ?", (old,))
                db.execute("INSERT INTO category(name, created_at) VALUES (?, ?)", (new, now_ms()))
            else:
                new = target["name"]
                db.execute("DELETE FROM category WHERE name = ?", (old,))
            uids = [r["uid"] for r in db.execute("SELECT uid FROM txn WHERE category = ? COLLATE NOCASE", (old,))]
            ts = now_ms()
            db.executemany(
                "UPDATE txn SET category = ?, push_pending = 1 WHERE uid = ?", [(new, u) for u in uids]
            )
            db.executemany(
                """INSERT INTO label_history(uid, category, previous_category, source, action, at)
                   VALUES (?, ?, ?, 'manual', 'rename', ?)""",
                [(u, new, old, ts) for u in uids],
            )
            self._set_meta(db, "categories_dirty", "1")
        return {"category": new, "merged": merged, "transactions": len(uids)}

    def delete_category(self, name: str) -> None:
        with self.connect() as db:
            used = db.execute("SELECT COUNT(*) FROM txn WHERE category = ? COLLATE NOCASE", (name,)).fetchone()[0]
            if used:
                raise StoreError(f"'{name}' is used by {used} transactions; rename/merge it instead")
            db.execute("DELETE FROM category WHERE name = ?", (name,))
            self._set_meta(db, "categories_dirty", "1")

    # ------------------------------------------------------------------ labels

    def set_labels(self, assignments: Iterable[tuple[str, str | None]], source: str) -> LabelResult:
        """Applies (uid, category) pairs.

        Rules: a manual label is always reviewed; Claude / model labels wait for review and
        never overwrite a manual label. Linked duplicates get the same label as their main row.
        """
        if source not in LABEL_SOURCES:
            raise StoreError(f"unknown label source: {source}")
        result = LabelResult()
        with self.connect() as db:
            cats = {r["name"].lower(): r["name"] for r in db.execute("SELECT name FROM category")}
            for uid, category in assignments:
                canonical = None
                if category is not None:
                    canonical = cats.get(category.strip().lower())
                    if canonical is None:
                        result.errors.append({"uid": uid, "error": f"unknown category '{category}' (add it first)"})
                        continue
                row = db.execute("SELECT * FROM txn WHERE uid = ?", (uid,)).fetchone()
                if row is None:
                    result.errors.append({"uid": uid, "error": "unknown transaction"})
                    continue
                if source != "manual" and row["label_source"] == "manual":
                    result.skipped.append({"uid": uid, "reason": f"kept manual label '{row['category']}'"})
                    continue
                targets = [uid] + [
                    r["uid"] for r in db.execute("SELECT uid FROM txn WHERE dup_of = ?", (uid,))
                ]
                for target in targets:
                    self._apply_label(db, target, canonical, source)
                result.updated.append(uid)
        return result

    def _apply_label(self, db: sqlite3.Connection, uid: str, category: str | None, source: str) -> None:
        row = db.execute("SELECT category, label_source FROM txn WHERE uid = ?", (uid,)).fetchone()
        if row is None:
            return
        if source != "manual" and row["label_source"] == "manual":
            return
        if row["category"] == category and row["label_source"] == source:
            return
        if category is None:
            action = "clear"
        elif source == "manual" and row["label_source"] in ("claude", "model") and row["category"] != category:
            action = "correct"
        else:
            action = "label"
        ts = now_ms()
        db.execute(
            """UPDATE txn SET category = ?, label_source = ?, reviewed = ?, labeled_at = ?, push_pending = 1
               WHERE uid = ?""",
            (category, None if category is None else source, 1 if source == "manual" and category else 0, ts, uid),
        )
        db.execute(
            """INSERT INTO label_history(uid, category, previous_category, source, action, at)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (uid, category, row["category"], source, action, ts),
        )

    def accept(self, uids: Iterable[str]) -> int:
        """Marks Claude / model labels as reviewed (correct as they are)."""
        n = 0
        ts = now_ms()
        with self.connect() as db:
            for uid in uids:
                row = db.execute("SELECT category, label_source, reviewed FROM txn WHERE uid = ?", (uid,)).fetchone()
                if row is None or row["category"] is None or row["reviewed"]:
                    continue
                db.execute("UPDATE txn SET reviewed = 1 WHERE uid = ?", (uid,))
                db.execute(
                    """INSERT INTO label_history(uid, category, previous_category, source, action, at)
                       VALUES (?, ?, ?, 'manual', 'accept', ?)""",
                    (uid, row["category"], row["category"], ts),
                )
                n += 1
        return n

    def set_dup_decision(self, uid: str, decision: str | None) -> None:
        """confirmed = really a duplicate (not counted), rejected = separate purchase (counted)."""
        if decision not in (None, "confirmed", "rejected"):
            raise StoreError(f"bad decision: {decision}")
        with self.connect() as db:
            row = db.execute("SELECT dup_of FROM txn WHERE uid = ?", (uid,)).fetchone()
            if row is None or row["dup_of"] is None:
                raise StoreError("transaction is not linked as a duplicate")
            db.execute("UPDATE txn SET dup_decision = ?, push_pending = 1 WHERE uid = ?", (decision, uid))

    # ------------------------------------------------------------------ queries

    def list_transactions(
        self,
        start: str | None = None,
        end: str | None = None,
        status: str = "all",
        direction: str = "ALL",
        category: str | None = None,
        include_duplicates: bool = True,
        query: str | None = None,
        limit: int = 200,
        offset: int = 0,
    ) -> tuple[list[dict], int]:
        where, args = ["1 = 1"], []
        if start:
            where.append("t.txn_date >= ?"); args.append(start)
        if end:
            where.append("t.txn_date <= ?"); args.append(end)
        if direction in ("DEBIT", "CREDIT"):
            where.append("t.direction = ?"); args.append(direction)
        if category:
            where.append("t.category = ? COLLATE NOCASE"); args.append(category)
        if query:
            where.append("(t.body LIKE ? OR t.sender_key LIKE ?)"); args += [f"%{query}%", f"%{query}%"]
        if status == "uncategorized":
            where.append("t.category IS NULL")
        elif status == "unreviewed":
            where.append("t.category IS NOT NULL AND t.reviewed = 0")
        elif status == "duplicates":
            where.append("t.dup_of IS NOT NULL")
        if not include_duplicates and status != "duplicates":
            where.append(COUNTED)
        sql_where = " AND ".join(where)
        with self.connect() as db:
            total = db.execute(f"SELECT COUNT(*) FROM txn t WHERE {sql_where}", args).fetchone()[0]
            rows = db.execute(
                f"""SELECT t.*, {COUNTED} AS counted FROM txn t WHERE {sql_where}
                    ORDER BY t.txn_date DESC, t.sent_at DESC LIMIT ? OFFSET ?""",
                [*args, limit, offset],
            ).fetchall()
        return [self._row(r) for r in rows], total

    def get(self, uid: str) -> dict | None:
        with self.connect() as db:
            r = db.execute(f"SELECT t.*, {COUNTED} AS counted FROM txn t WHERE uid = ?", (uid,)).fetchone()
        return self._row(r) if r else None

    @staticmethod
    def _row(r: sqlite3.Row) -> dict:
        d = dict(r)
        d["amount"] = round(d["amount_paise"] / 100, 2)
        d["dup_state"] = d.get("dup_decision") or d.get("dup_state_phone")
        d["counted"] = bool(d.get("counted", 1))
        d["date_from_body"] = bool(d["date_from_body"])
        d["reviewed"] = bool(d["reviewed"])
        d["sent_time"] = datetime.fromtimestamp(d["sent_at"] / 1000).strftime("%Y-%m-%d %H:%M")
        return d

    def summary(self, start: str | None, end: str | None, group_by: str = "category", direction: str = "DEBIT") -> dict:
        key = {
            "category": "COALESCE(t.category, 'Uncategorized')",
            "month": "substr(t.txn_date, 1, 7)",
            "day": "t.txn_date",
            "sender": "t.sender_key",
        }.get(group_by)
        if key is None:
            raise StoreError(f"bad group_by: {group_by}")
        where, args = [COUNTED, "t.direction = ?"], [direction]
        if start:
            where.append("t.txn_date >= ?"); args.append(start)
        if end:
            where.append("t.txn_date <= ?"); args.append(end)
        with self.connect() as db:
            rows = db.execute(
                f"""SELECT {key} AS grp, COUNT(*) AS n, SUM(t.amount_paise) AS paise FROM txn t
                    WHERE {' AND '.join(where)} GROUP BY grp ORDER BY paise DESC""",
                args,
            ).fetchall()
        groups = [{"group": r["grp"], "transactions": r["n"], "amount": round(r["paise"] / 100, 2)} for r in rows]
        return {
            "start": start, "end": end, "direction": direction, "group_by": group_by,
            "total": round(sum(g["amount"] for g in groups), 2),
            "transactions": sum(g["transactions"] for g in groups),
            "groups": groups,
        }

    def counts(self) -> dict:
        with self.connect() as db:
            r = db.execute(
                f"""SELECT COUNT(*) AS total,
                       SUM(CASE WHEN category IS NULL AND {COUNTED} THEN 1 ELSE 0 END) AS uncategorized,
                       SUM(CASE WHEN category IS NOT NULL AND reviewed = 0 THEN 1 ELSE 0 END) AS unreviewed,
                       SUM(CASE WHEN dup_of IS NOT NULL AND dup_decision IS NULL THEN 1 ELSE 0 END) AS duplicates_to_check,
                       SUM(push_pending) AS push_pending
                   FROM txn t"""
            ).fetchone()
        return {k: (r[k] or 0) for k in r.keys()}

    # ------------------------------------------------------------------ push to phone

    def pending_push(self) -> dict:
        with self.connect() as db:
            rows = db.execute("SELECT uid, category, dup_decision FROM txn WHERE push_pending = 1").fetchall()
            dirty = db.execute("SELECT value FROM meta WHERE key = 'categories_dirty'").fetchone()
        payload: dict = {
            "labels": [{"uid": r["uid"], "category": r["category"]} for r in rows],
            "dup_states": [{"uid": r["uid"], "state": r["dup_decision"]} for r in rows if r["dup_decision"]],
        }
        if dirty and dirty["value"] == "1":
            payload["categories"] = self.categories()
        return payload

    def mark_pushed(self, payload: dict) -> None:
        with self.connect() as db:
            # Rows edited again while the push was in flight stay pending.
            db.executemany(
                "UPDATE txn SET push_pending = 0 WHERE uid = ? AND category IS ?",
                [(l["uid"], l["category"]) for l in payload["labels"]],
            )
            if "categories" in payload:
                self._set_meta(db, "categories_dirty", "0")

    # ------------------------------------------------------------------ dataset

    def dataset(self, include_unreviewed: bool = False) -> list[dict]:
        """Training rows: reviewed labels only (manual + accepted Claude labels) unless asked otherwise."""
        cond = "t.category IS NOT NULL" + ("" if include_unreviewed else " AND t.reviewed = 1")
        with self.connect() as db:
            rows = db.execute(
                f"""SELECT t.*, EXISTS(SELECT 1 FROM label_history h WHERE h.uid = t.uid AND h.action = 'correct') AS corrected,
                       (SELECT h.category FROM label_history h WHERE h.uid = t.uid AND h.source IN ('claude', 'model')
                        ORDER BY h.id DESC LIMIT 1) AS machine_label
                    FROM txn t WHERE {cond} ORDER BY t.sent_at"""
            ).fetchall()
        out = []
        for r in rows:
            sent = datetime.fromtimestamp(r["sent_at"] / 1000)
            out.append({
                "uid": r["uid"],
                "text": r["body"],
                "sender": r["sender_key"],
                "source_type": r["source_type"],
                "direction": r["direction"],
                "amount": round(r["amount_paise"] / 100, 2),
                "txn_date": r["txn_date"],
                "weekday": date.fromisoformat(r["txn_date"]).strftime("%a"),
                "hour": sent.hour,
                "category": r["category"],
                "label_source": r["label_source"],
                "reviewed": bool(r["reviewed"]),
                "was_corrected": bool(r["corrected"]),
                "machine_label": r["machine_label"],
                "is_duplicate": r["dup_of"] is not None,
            })
        return out

    def dataset_stats(self) -> dict:
        with self.connect() as db:
            per_cat = db.execute(
                """SELECT category, COUNT(*) AS n FROM txn WHERE category IS NOT NULL AND reviewed = 1
                   GROUP BY category ORDER BY n DESC"""
            ).fetchall()
            by_source = db.execute(
                """SELECT label_source, reviewed, COUNT(*) AS n FROM txn WHERE category IS NOT NULL
                   GROUP BY label_source, reviewed"""
            ).fetchall()
            corrections = db.execute("SELECT COUNT(DISTINCT uid) FROM label_history WHERE action = 'correct'").fetchone()[0]
            accepts = db.execute("SELECT COUNT(DISTINCT uid) FROM label_history WHERE action = 'accept'").fetchone()[0]
        return {
            "training_rows": sum(r["n"] for r in per_cat),
            "per_category": [{"category": r["category"], "rows": r["n"]} for r in per_cat],
            "by_source": [
                {"source": r["label_source"], "reviewed": bool(r["reviewed"]), "rows": r["n"]} for r in by_source
            ],
            "claude_labels_corrected": corrections,
            "claude_labels_accepted": accepts,
        }

    def history(self, uid: str) -> list[dict]:
        with self.connect() as db:
            return [dict(r) for r in db.execute("SELECT * FROM label_history WHERE uid = ? ORDER BY id", (uid,))]
