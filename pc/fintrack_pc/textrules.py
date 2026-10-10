"""Shared text rules for PC-side sources (email receipts, bank alert emails, bank statements).

Same approach as the phone: fixed patterns, no AI. Masking mirrors android/.../core/Masker.kt.
"""

from __future__ import annotations

import re
from datetime import date, datetime
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP

MASK = "XXXX"

_PARTLY_MASKED = re.compile(r"(?<![A-Za-z0-9])(?=[0-9Xx*#]*\d)(?=[0-9Xx*#]*[Xx*#])[0-9Xx*#]{3,}(?![A-Za-z0-9])")
_AFTER_KEYWORD = re.compile(
    r"(?i)((?<![a-z])(?:a/c|a/c\.|ac|acct|account|card|cc)(?:\s*(?:no\.?|number|num|ending(?:\s+(?:with|in))?|"
    r"ends?(?:\s+(?:with|in))?))?\s*[:.#-]?\s*)(\d[\d -]*\d|\d)(?!\d)"
)
_LONG_DIGITS = re.compile(r"(?<!\d)\d{9,}(?!\d)")
_REPEATED_MASK = re.compile(r"XXXX(?:[ -]?XXXX)+")


def mask(text: str) -> str:
    """Replaces every account / card / long reference number with XXXX."""
    out = _PARTLY_MASKED.sub(MASK, text)
    out = _AFTER_KEYWORD.sub(lambda m: m.group(1) + MASK, out)
    out = _LONG_DIGITS.sub(MASK, out)
    return _REPEATED_MASK.sub(MASK, out)


def clean(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


# --------------------------------------------------------------------------- amounts

_NUM = r"(\d[\d,]*(?:\.\d{1,2})?)"
_CUR = r"(?:rs\.?|inr|₹|rupees)"
_AMOUNT_PREFIX = re.compile(rf"(?i)(?<![a-z]){_CUR}\s*:?\s*{_NUM}")
_AMOUNT_AFTER_VERB = re.compile(
    rf"(?i)(?<![a-z])(?:debited|credited|deducted|spent|paid|sent|withdrawn|received)\s+(?:by|for|with|of)?\s*{_NUM}(?![\d/:-])"
)
_AMOUNT_SUFFIX = re.compile(rf"(?i)(?<![\d,.]){_NUM}\s*(?:/-|rs(?![a-z])|inr(?![a-z])|rupees)")
_NOT_SPEND = re.compile(r"(?i)(bal|balance|avl|available|limit|outstanding|o/s)")


def to_paise(raw: str) -> int | None:
    s = str(raw).replace(",", "").replace("₹", "").strip().rstrip(".")
    s = re.sub(r"(?i)\s*(cr|dr)\.?$", "", s).strip()
    if not s or s in ("-", "--"):
        return None
    try:
        value = (Decimal(s) * 100).quantize(Decimal("1"), rounding=ROUND_HALF_UP)
    except InvalidOperation:
        return None
    return abs(int(value))


def first_spend_amount(text: str) -> int | None:
    """First amount that isn't a balance / limit figure (bank-alert style text)."""
    matches = list(_AMOUNT_PREFIX.finditer(text)) or list(_AMOUNT_AFTER_VERB.finditer(text)) \
        or list(_AMOUNT_SUFFIX.finditer(text))
    for m in matches:
        window = text[max(0, m.start() - 20):m.start()]
        cut = window.rfind(". ")
        if cut >= 0:
            window = window[cut + 2:]
        if _NOT_SPEND.search(window):
            continue
        paise = to_paise(m.group(1))
        if paise:
            return paise
    return None


# --------------------------------------------------------------------------- direction

_DEBIT = re.compile(
    r"(?<![a-z])(debited|debit of|spent|paid|sent|withdrawn|withdrawal|purchase|charged|deducted|txn of|"
    r"transaction of|payment of|transferred)(?![a-z])"
)
_CREDIT = re.compile(r"(?<![a-z])(credited|credit of|received|deposited|refund|refunded|reversed|reversal)(?![a-z])")


def bank_direction(lower: str) -> str | None:
    d, c = _DEBIT.search(lower), _CREDIT.search(lower)
    if not d and not c:
        return None
    if not c:
        return "DEBIT"
    if not d:
        return "CREDIT"
    return "DEBIT" if d.start() <= c.start() else "CREDIT"


# --------------------------------------------------------------------------- dates

_DATE_FORMATS = (
    "%d/%m/%Y", "%d/%m/%y", "%d-%m-%Y", "%d-%m-%y", "%d.%m.%Y", "%d.%m.%y",
    "%d-%b-%Y", "%d-%b-%y", "%d %b %Y", "%d %b %y", "%d-%B-%Y", "%d %B %Y",
    "%Y-%m-%d", "%Y/%m/%d", "%b %d, %Y", "%d%b%Y", "%d%b%y",
)


def parse_date(value) -> date | None:
    """Parses a statement date cell: datetime / date objects or common Indian bank text formats."""
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    if value is None:
        return None
    s = clean(str(value))
    s = re.sub(r"\s+\d{1,2}:\d{2}(:\d{2})?(\s*[AP]M)?$", "", s, flags=re.I)  # drop a trailing time
    for fmt in _DATE_FORMATS:
        try:
            return datetime.strptime(s, fmt).date()
        except ValueError:
            continue
    return None


# --------------------------------------------------------------------------- merchants

#: Merchant / app brand -> words that identify it in any text (email, SMS, statement narration).
BRANDS: dict[str, tuple[str, ...]] = {
    "SWIGGY": ("swiggy", "instamart"),
    "ZOMATO": ("zomato",),
    "BLINKIT": ("blinkit", "grofers"),
    "ZEPTO": ("zepto",),
    "AMAZON": ("amazon",),
    "FLIPKART": ("flipkart",),
    "BIGBASKET": ("bigbasket", "bbnow"),
    "MYNTRA": ("myntra",),
    "DUNZO": ("dunzo",),
    "UBER": ("uber",),
    "RAPIDO": ("rapido",),
    "OLA": ("olacabs", "ola cabs", "ani technologies"),
    "BOOKMYSHOW": ("bookmyshow",),
    "DOMINOS": ("domino",),
}

#: Bank alert email senders: brand key -> words in the From address.
BANK_EMAIL_KEYS: dict[str, tuple[str, ...]] = {
    "HDFCBK": ("hdfcbank",),
    "ICICIB": ("icicibank",),
    "SBI": ("sbi.co.in", "sbicard", "onlinesbi"),
    "AXISBK": ("axisbank",),
    "KOTAKB": ("kotak",),
    "IDFCFB": ("idfcfirstbank",),
    "YESBNK": ("yesbank",),
    "INDUSB": ("indusind",),
    "PNB": ("pnb",),
    "BOB": ("bankofbaroda",),
    "FEDBNK": ("federalbank",),
}


def brand_in(text: str) -> set[str]:
    lower = text.lower()
    return {key for key, words in BRANDS.items() if any(w in lower for w in words)}
