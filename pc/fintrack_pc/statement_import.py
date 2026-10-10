"""Imports a bank statement (CSV, Excel .xlsx / .xls, or PDF) as the monthly ground truth.

Lines that match something already tracked (SMS, notification, email) are linked as duplicates;
lines that match nothing are added as payments FinTrack missed. Importing the same or an
overlapping statement again adds nothing new.
"""

from __future__ import annotations

import csv
import hashlib
import io
import re
from datetime import datetime, time
from pathlib import Path

from .store import Store
from .textrules import clean, mask, parse_date, to_paise


class StatementError(ValueError):
    pass


# --------------------------------------------------------------------------- reading files

def read_table(path: Path | str, password: str | None = None) -> list[list]:
    path = Path(path)
    ext = path.suffix.lower()
    if ext in (".csv", ".txt"):
        raw = path.read_bytes()
        for enc in ("utf-8-sig", "cp1252", "latin-1"):
            try:
                text = raw.decode(enc)
                break
            except UnicodeDecodeError:
                continue
        try:
            dialect = csv.Sniffer().sniff(text[:4096], delimiters=",;\t|")
        except csv.Error:
            dialect = csv.excel
        return [row for row in csv.reader(io.StringIO(text), dialect)]
    if ext == ".xlsx":
        openpyxl = _need("openpyxl")
        wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
        return [list(r) for ws in wb.worksheets for r in ws.iter_rows(values_only=True)]
    if ext == ".xls":
        xlrd = _need("xlrd")
        book = xlrd.open_workbook(str(path))
        rows = []
        for sheet in book.sheets():
            for i in range(sheet.nrows):
                row = []
                for cell in sheet.row(i):
                    if cell.ctype == xlrd.XL_CELL_DATE:
                        row.append(xlrd.xldate.xldate_as_datetime(cell.value, book.datemode))
                    else:
                        row.append(cell.value)
                rows.append(row)
        return rows
    if ext == ".pdf":
        pdfplumber = _need("pdfplumber")
        rows = []
        try:
            with pdfplumber.open(str(path), password=password or "") as pdf:
                for page in pdf.pages:
                    for table in page.extract_tables():
                        rows.extend(table)
        except Exception as e:  # wrong / missing password, scanned PDF, ...
            raise StatementError(f"Could not read the PDF ({e.__class__.__name__}). If it is password protected, "
                                 "pass the password (often your customer ID or date of birth).") from e
        return rows
    raise StatementError(f"Unsupported file type {ext}. Use CSV, XLSX, XLS or PDF.")


def _need(module: str):
    try:
        return __import__(module)
    except ImportError as e:
        raise StatementError(f"Reading this file needs '{module}'. Run: pip install -e \".[statements]\"") from e


# --------------------------------------------------------------------------- finding columns

def _norm(cell) -> str:
    return re.sub(r"[^a-z/ ]", "", clean(str(cell or "")).lower()).strip()


_DESC = ("narration", "description", "particulars", "transaction details", "details", "remarks", "transaction remarks")
_DEBIT = ("withdrawal", "withdrawals", "debit", "debits", "dr", "dr amount", "money out")
_CREDIT = ("deposit", "deposits", "credit", "credits", "cr", "cr amount", "money in")
_TYPE = ("dr/cr", "cr/dr", "drcr", "type", "dr / cr", "transaction type")


def _find(cells: list[str], names: tuple[str, ...], exclude: tuple[str, ...] = ()) -> int | None:
    """First column named exactly like one of [names], or starting with it as a word ("withdrawal amt")."""
    for i, c in enumerate(cells):
        if any(c == n or c.startswith(n + " ") for n in names) and not any(x in c for x in exclude):
            return i
    return None


def find_columns(row: list) -> dict | None:
    cells = [_norm(c) for c in row]
    date_i = None
    for i, c in enumerate(cells):
        if "date" in c and "value" not in c:
            date_i = i
            break
    if date_i is None:
        date_i = next((i for i, c in enumerate(cells) if "date" in c), None)
    desc_i = _find(cells, _DESC)
    debit_i = _find(cells, _DEBIT, exclude=("credit", "/"))
    credit_i = _find(cells, _CREDIT, exclude=("debit", "/"))
    amount_i = next((i for i, c in enumerate(cells) if c in ("amount", "txn amount", "transaction amount", "amount inr")), None)
    type_i = next((i for i, c in enumerate(cells) if c in _TYPE), None)
    balance_i = next((i for i, c in enumerate(cells) if "balance" in c), None)
    if date_i is None or desc_i is None:
        return None
    if debit_i is None and credit_i is None and amount_i is None:
        return None
    return {"date": date_i, "desc": desc_i, "debit": debit_i, "credit": credit_i,
            "amount": amount_i, "type": type_i, "balance": balance_i}


# --------------------------------------------------------------------------- parsing rows

def _cell(row: list, i: int | None):
    return row[i] if i is not None and i < len(row) else None


def parse_statement(rows: list[list], account: str) -> list[dict]:
    account = re.sub(r"[^A-Z0-9]", "", account.upper()) or "BANK"
    cols, out, seen = None, [], {}
    for row in rows:
        if not row or all(c in (None, "") for c in row):
            continue
        header = find_columns(row)
        if header:
            cols = header  # (PDFs repeat the header on every page)
            continue
        if cols is None:
            continue
        when = parse_date(_cell(row, cols["date"]))
        desc = clean(str(_cell(row, cols["desc"]) or ""))
        if when is None:
            # A wrapped narration line in a PDF: no date, just more text for the line above.
            amounts_empty = all(to_paise(_cell(row, cols[k]) or "") is None for k in ("debit", "credit", "amount"))
            if out and desc and amounts_empty:
                out[-1]["_desc"] += " " + desc
            continue
        debit = to_paise(_cell(row, cols["debit"]) or "")
        credit = to_paise(_cell(row, cols["credit"]) or "")
        if cols["amount"] is not None and not debit and not credit:
            raw = str(_cell(row, cols["amount"]) or "")
            kind = str(_cell(row, cols["type"]) or "").strip().lower()
            value = to_paise(raw)
            if value:
                is_credit = kind.startswith("c") or bool(re.search(r"(?i)cr\.?\s*$", raw))
                credit, debit = (value, None) if is_credit else (None, value)
        if not debit and not credit:
            continue
        direction, amount = ("DEBIT", debit) if debit else ("CREDIT", credit)
        out.append({"_date": when, "_desc": desc, "_amount": amount, "_dir": direction,
                    "_balance": str(_cell(row, cols["balance"]) or "")})

    result = []
    for r in out:
        base = f"{account}|{r['_date']}|{clean(r['_desc'])}|{r['_amount']}|{r['_dir']}|{r['_balance']}"
        n = seen.get(base, 0)
        seen[base] = n + 1
        ts = int(datetime.combine(r["_date"], time(12, 0)).astimezone().timestamp() * 1000)
        result.append({
            "uid": "stmt:" + hashlib.sha1(f"{base}|{n}".encode()).hexdigest(),
            "sender": f"statement:{account}",
            "sender_key": account,
            "body": mask(clean(r["_desc"]))[:600],
            "sent_at": ts,
            "received_at": ts,
            "txn_date": r["_date"].isoformat(),
            "date_from_body": True,
            "amount_paise": r["_amount"],
            "direction": r["_dir"],
            "source_type": "STATEMENT",
            "dup_of": None,
            "dup_score": None,
        })
    return result


def import_statement(store: Store, path: Path | str, account: str, password: str | None = None) -> dict:
    from .reconcile import reconcile

    rows = parse_statement(read_table(path, password), account)
    if not rows:
        raise StatementError("No transactions found. Check that the file has a header row with Date, "
                             "Narration/Description and Debit/Credit (or Amount) columns.")
    added = store.insert_local(rows)
    result = reconcile(store)
    with store.connect() as db:
        missed = db.execute(
            "SELECT COUNT(*) FROM txn WHERE source_type = 'STATEMENT' AND dup_of IS NULL AND uid IN (%s)"
            % ",".join("?" for _ in rows), [r["uid"] for r in rows],
        ).fetchone()[0]
    return {
        "lines_in_file": len(rows),
        "new_lines": added,
        "already_tracked": len(rows) - missed,
        "missed_before": missed,
        "first_date": min(r["txn_date"] for r in rows),
        "last_date": max(r["txn_date"] for r in rows),
        **result,
    }
