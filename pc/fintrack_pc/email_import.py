"""Reads order / ride receipts and bank alert emails from Gmail (IMAP + app password).

Covers payments that leave no SMS or notification: in-app UPI payments, UPI Lite, wallets, cash on delivery,
Uber / Rapido rides paid at the end of the trip, and the small UPI payments some banks only alert by email.
Only a short, masked snippet of each email is stored, never the full email.
"""

from __future__ import annotations

import email
import hashlib
import imaplib
import re
from datetime import date, datetime, timedelta
from email.message import EmailMessage
from email.policy import default as default_policy
from email.utils import parseaddr, parsedate_to_datetime
from html.parser import HTMLParser
from typing import Iterator

from .store import Store
from .textrules import (
    BANK_EMAIL_KEYS, _CUR, _NUM, bank_direction, clean, first_spend_amount, mask, to_paise,
)

#: Words searched in the From header (IMAP FROM is a substring match).
MERCHANT_SENDERS: dict[str, tuple[str, ...]] = {
    "SWIGGY": ("swiggy",), "ZOMATO": ("zomato",), "BLINKIT": ("blinkit",), "ZEPTO": ("zepto",),
    "AMAZON": ("amazon.in",), "FLIPKART": ("flipkart",), "BIGBASKET": ("bigbasket",), "MYNTRA": ("myntra",),
    "UBER": ("uber.com",), "RAPIDO": ("rapido",), "OLA": ("olacabs", "olamoney"),
    "BOOKMYSHOW": ("bookmyshow",), "DOMINOS": ("dominos",), "DUNZO": ("dunzo",),
}

_BLOCK_TAGS = {"p", "div", "br", "tr", "li", "table", "h1", "h2", "h3", "h4", "td", "th"}


class _HtmlText(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts: list[str] = []
        self._skip = 0

    def handle_starttag(self, tag, attrs):
        if tag in ("style", "script", "head"):
            self._skip += 1
        if tag in _BLOCK_TAGS:
            self.parts.append("\n")

    def handle_endtag(self, tag):
        if tag in ("style", "script", "head") and self._skip:
            self._skip -= 1
        if tag in _BLOCK_TAGS:
            self.parts.append("\n")

    def handle_data(self, data):
        if not self._skip:
            self.parts.append(data)


def html_to_text(html: str) -> str:
    p = _HtmlText()
    p.feed(html)
    lines = [clean(line) for line in "".join(p.parts).splitlines()]
    return "\n".join(line for line in lines if line)


def message_text(msg: EmailMessage) -> str:
    # The HTML part is the complete receipt; plain-text parts are often a "view in browser" stub.
    part = msg.get_body(preferencelist=("html", "plain"))
    if part is None:
        return ""
    content = part.get_content()
    if part.get_content_subtype() == "html":
        return html_to_text(content)
    return "\n".join(clean(line) for line in content.splitlines() if line.strip())


# --------------------------------------------------------------------------- receipt rules

_PAID_LABELS = r"amount paid|total paid|you paid|paid amount|amount charged|total charged|paid via|paid using|paid by"
_TOTAL_LABELS = (r"grand total|order total|bill total|total amount|total payable|net payable|trip total|total fare|"
                 r"ride fare|total bill|order amount|net amount|amount payable|invoice total")
_TOTAL_GENERIC = r"total(?!\s*(?:savings|saved|discount|tax|taxes|items?|gst|quantity|qty|mrp))"


def _label_amounts(labels: str, text: str) -> list[int]:
    # Receipts are often tables: the label and the amount can sit in neighbouring cells (separate lines).
    rx = re.compile(rf"(?i)(?<![a-z])(?:{labels})(?![a-z])[^\d₹]{{0,30}}?{_CUR}\s*:?\s*{_NUM}")
    return [p for p in (to_paise(m.group(1)) for m in rx.finditer(text)) if p]


def receipt_total(text: str) -> int | None:
    """The amount actually paid: "Amount paid" beats "Order total" beats a plain "Total" (largest one)."""
    for labels in (_PAID_LABELS, _TOTAL_LABELS, _TOTAL_GENERIC):
        found = _label_amounts(labels, text)
        if found:
            return max(found) if labels is _TOTAL_GENERIC else found[-1]
    return None


_RECEIPT_WORDS = re.compile(r"(?i)(?<![a-z])(order|receipt|invoice|trip|ride|booking|bill|payment|paid|delivered|placed)(?![a-z])")
_CANCELLED = re.compile(r"(?i)(?<![a-z])(cancelled|canceled|failed|declined)(?![a-z])")
_REFUND = re.compile(rf"(?i)(?<![a-z])refund(?:ed)?(?![a-z])[^\d₹]{{0,40}}?{_CUR}\s*:?\s*{_NUM}")
_BANK_SKIP = re.compile(
    r"(?i)(?<![a-z])(otp|one[- ]time password|is due|due on|due date|amount due|will be debited|will be deducted|"
    r"failed|declined|unsuccessful|requested|statement for|e-?statement)(?![a-z])"
)


def _email_time(msg: EmailMessage) -> datetime:
    try:
        dt = parsedate_to_datetime(msg["Date"])
    except (TypeError, ValueError):
        dt = None
    if dt is None:
        return datetime.now().astimezone()
    return dt.astimezone()  # local time


def _sender_key(from_addr: str) -> tuple[str, str] | None:
    """('MERCHANT' | 'BANK', key) from the sender address."""
    addr = from_addr.lower()
    for key, words in BANK_EMAIL_KEYS.items():
        if any(w in addr for w in words):
            return "BANK", key
    for key, words in MERCHANT_SENDERS.items():
        if any(w in addr for w in words):
            return "MERCHANT", key
    return None


def message_key(msg: EmailMessage) -> str:
    mid = (msg["Message-ID"] or "").strip()
    raw = mid or f"{msg['From']}|{msg['Date']}|{msg['Subject']}"
    return hashlib.sha1(raw.encode("utf-8", "replace")).hexdigest()


def parse_email(msg: EmailMessage) -> dict | None:
    """One email -> a FinTrack row, or None if it isn't a receipt / transaction alert."""
    display, addr = parseaddr(msg["From"] or "")
    kind = _sender_key(addr or display)
    if kind is None:
        return None
    subject = clean(str(msg["Subject"] or ""))
    text = message_text(msg)
    full = f"{subject}\n{text}"
    when = _email_time(msg)

    if kind[0] == "MERCHANT":
        refund = _REFUND.search(full)
        if refund:
            amount, direction, anchor = to_paise(refund.group(1)), "CREDIT", refund.start()
        else:
            if _CANCELLED.search(subject) or not _RECEIPT_WORDS.search(full):
                return None
            amount, direction = receipt_total(full), "DEBIT"
            anchor = full.lower().find("total")
        if not amount:
            return None
        # Snippet: subject, the start of the email (merchant / restaurant / trip), and the line with the total.
        head = clean(text[:220])
        around = clean(full[max(0, anchor - 60):anchor + 80]) if anchor >= 0 else ""
        snippet = " | ".join(x for x in (subject, head, around) if x)
    else:
        lower = clean(full).lower()
        if _BANK_SKIP.search(lower):
            return None
        direction = bank_direction(lower)
        amount = first_spend_amount(clean(full))
        if not direction or not amount:
            return None
        snippet = " | ".join(x for x in (subject, clean(text[:400])) if x)

    return {
        "uid": "email:" + message_key(msg),
        "sender": f"email:{addr or display}",
        "sender_key": kind[1],
        "body": mask(snippet)[:600],
        "sent_at": int(when.timestamp() * 1000),
        "received_at": int(when.timestamp() * 1000),
        "txn_date": when.date().isoformat(),
        "date_from_body": False,
        "amount_paise": amount,
        "direction": direction,
        "source_type": "EMAIL",
        "dup_of": None,
        "dup_score": None,
    }


# --------------------------------------------------------------------------- IMAP

class EmailError(RuntimeError):
    pass


def search_words() -> list[str]:
    words = [w for ws in MERCHANT_SENDERS.values() for w in ws] + [w for ws in BANK_EMAIL_KEYS.values() for w in ws]
    return sorted(set(words))


def fetch(address: str, app_password: str, since: date, skip_keys: set[str] | None = None,
          host: str = "imap.gmail.com", imap_factory=imaplib.IMAP4_SSL) -> Iterator[tuple[str, EmailMessage]]:
    """Yields (message_key, message) for receipt / bank emails since [since], read-only (nothing marked read)."""
    try:
        conn = imap_factory(host)
        conn.login(address, app_password)
    except (imaplib.IMAP4.error, OSError) as e:
        raise EmailError(
            f"Could not sign in to {host} as {address}. Use a Google App Password (myaccount.google.com/apppasswords), "
            f"not your normal password, and make sure IMAP is on in Gmail settings. ({e})"
        ) from e
    try:
        typ, _ = conn.select('"[Gmail]/All Mail"', readonly=True)
        if typ != "OK":
            conn.select("INBOX", readonly=True)
        uids: set[bytes] = set()
        for word in search_words():
            typ, data = conn.uid("search", None, "SINCE", since.strftime("%d-%b-%Y"), "FROM", f'"{word}"')
            if typ == "OK" and data and data[0]:
                uids.update(data[0].split())
        for uid in sorted(uids, key=int):
            typ, data = conn.uid("fetch", uid, "(BODY.PEEK[HEADER.FIELDS (MESSAGE-ID FROM DATE SUBJECT)])")
            if typ != "OK" or not data or not isinstance(data[0], tuple):
                continue
            header = email.message_from_bytes(data[0][1], policy=default_policy)
            key = message_key(header)
            if skip_keys and key in skip_keys:
                continue
            typ, data = conn.uid("fetch", uid, "(BODY.PEEK[])")
            if typ != "OK" or not data or not isinstance(data[0], tuple):
                continue
            yield key, email.message_from_bytes(data[0][1], policy=default_policy)
    finally:
        try:
            conn.logout()
        except Exception:
            pass


def import_email(store: Store, address: str, app_password: str, days: int = 30, host: str = "imap.gmail.com",
                 imap_factory=imaplib.IMAP4_SSL) -> dict:
    """Fetches new emails, stores the ones that are transactions, then links duplicates."""
    from .reconcile import reconcile

    if not address or not app_password:
        raise EmailError("Email is not set up. Run: fintrack email-setup --address you@gmail.com --app-password ...")
    since = date.today() - timedelta(days=days)
    seen = store.seen_email_keys()
    rows, marks, scanned = [], [], 0
    for key, msg in fetch(address, app_password, since, seen, host=host, imap_factory=imap_factory):
        scanned += 1
        row = parse_email(msg)
        marks.append((key, "transaction" if row else "skipped"))
        if row:
            rows.append(row)
    added = store.insert_local(rows)
    store.mark_email_seen(marks)
    return {"emails_checked": scanned, "transactions_added": added, **reconcile(store)}

