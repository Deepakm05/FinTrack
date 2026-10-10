"""MCP server: the tools Claude uses to read and categorize your expenses."""

from __future__ import annotations

import logging
from datetime import date
from typing import Annotated, Literal

from mcp.server.mcpserver import MCPServer
from pydantic import BaseModel, Field

from .config import Config
from .phone import PhoneClient, PhoneError
from .store import Store, StoreError
from . import sync as syncer
from .email_import import EmailError, import_email as run_email_import
from .statement_import import StatementError, import_statement as run_statement_import

log = logging.getLogger("fintrack")

INSTRUCTIONS = """FinTrack holds expenses from the user's bank and delivery-app SMS, GPay / PhonePe / Paytm notifications,
receipt and bank-alert emails, and bank statements (source_type BANK, DELIVERY, UPI_APP, MERCHANT_APP, EMAIL, STATEMENT;
masked text, date, amount). Each row's "linked" list holds the other records of the same payment: use them too
when categorizing (e.g. a UPI debit to a personal UPI ID with a linked Rapido email is Transport).
To categorize: call list_categories, then list_transactions(status="uncategorized"), then categorize with
existing category names. Only add a category if nothing fits. Labels you set stay "unreviewed" until the user
accepts or corrects them in the FinTrack console; never try to overwrite a manual label.
Rows with dup_of set are another record of the same payment (e.g. the GPay notification or Swiggy SMS for a bank
debit) and are excluded from totals."""


class Assignment(BaseModel):
    uid: str = Field(description="Transaction uid from list_transactions")
    category: str = Field(description="An existing category name (see list_categories)")


class App:
    """Lazily opened store and phone client, shared by the tools."""

    def __init__(self, config: Config | None = None, store: Store | None = None, phone: PhoneClient | None = None):
        self.config = config or Config.load()
        self.store = store or Store(self.config.db_path)
        self._phone = phone

    @property
    def phone(self) -> PhoneClient:
        if self._phone is None:
            self._phone = PhoneClient(self.config.phone_url, self.config.token)
        return self._phone

    def try_sync(self) -> str | None:
        """Syncs if the phone is reachable; returns a note instead of failing when it is not."""
        try:
            syncer.sync(self.store, self.phone)
            return None
        except PhoneError as e:
            return f"Phone not synced, showing data from the last sync. {e}"

    def try_push(self) -> str | None:
        try:
            syncer.push(self.store, self.phone)
            return None
        except PhoneError as e:
            return f"Saved on the PC; will send to the phone on the next sync. ({e})"


def _month_bounds(month: str | None) -> tuple[str | None, str | None]:
    if not month:
        return None, None
    y, m = (int(x) for x in month.split("-"))
    start = date(y, m, 1)
    end = date(y + (m == 12), m % 12 + 1, 1)
    return start.isoformat(), date.fromordinal(end.toordinal() - 1).isoformat()


def build_server(app: App) -> MCPServer:
    mcp = MCPServer(name="fintrack", instructions=INSTRUCTIONS)

    @mcp.tool()
    def sync_phone() -> dict:
        """Pull new transactions from the phone and push categories back to it."""
        try:
            return syncer.sync(app.store, app.phone) | {"counts": app.store.counts()}
        except PhoneError as e:
            return {"error": str(e)}

    @mcp.tool()
    def list_categories() -> dict:
        """All categories with how many transactions use each."""
        return {"categories": app.store.category_counts()}

    @mcp.tool()
    def add_category(name: str) -> dict:
        """Add a new category. Prefer existing categories; add one only when nothing fits."""
        try:
            name = app.store.add_category(name)
        except StoreError as e:
            return {"error": str(e)}
        return {"added": name, "note": app.try_push()}

    @mcp.tool()
    def list_transactions(
        month: Annotated[str | None, Field(description="YYYY-MM, e.g. 2026-10")] = None,
        start_date: Annotated[str | None, Field(description="YYYY-MM-DD (ignored when month is given)")] = None,
        end_date: Annotated[str | None, Field(description="YYYY-MM-DD (ignored when month is given)")] = None,
        status: Literal["uncategorized", "unreviewed", "all"] = "uncategorized",
        direction: Literal["DEBIT", "CREDIT", "ALL"] = "DEBIT",
        source: Literal["BANK", "DELIVERY", "UPI_APP", "MERCHANT_APP", "EMAIL", "STATEMENT", "ALL"] = "ALL",
        include_duplicates: bool = False,
        limit: Annotated[int, Field(ge=1, le=500)] = 100,
        sync_first: bool = True,
    ) -> dict:
        """List transactions with their masked SMS text, so you can decide categories."""
        note = app.try_sync() if sync_first else None
        start, end = _month_bounds(month) if month else (start_date, end_date)
        rows, total = app.store.list_transactions(
            start=start, end=end, status=status, direction=direction,
            source=None if source == "ALL" else source,
            include_duplicates=include_duplicates, limit=limit,
        )
        keep = ("uid", "txn_date", "sent_time", "amount", "direction", "source_type", "sender_key", "body",
                "category", "label_source", "reviewed", "dup_of", "dup_state", "linked")
        return {
            "total_matching": total,
            "returned": len(rows),
            "transactions": [{k: r[k] for k in keep} for r in rows],
            "note": note,
        }

    @mcp.tool()
    def categorize(assignments: list[Assignment]) -> dict:
        """Set categories, e.g. [{"uid": "...", "category": "Food & Dining"}]. Up to 500 per call."""
        result = app.store.set_labels([(a.uid, a.category) for a in assignments[:500]], source="claude")
        return result.as_dict() | {"note": app.try_push()}

    @mcp.tool()
    def spending_summary(
        month: Annotated[str | None, Field(description="YYYY-MM")] = None,
        start_date: str | None = None,
        end_date: str | None = None,
        group_by: Literal["category", "month", "day", "sender"] = "category",
        direction: Literal["DEBIT", "CREDIT"] = "DEBIT",
    ) -> dict:
        """Totals (duplicates excluded). DEBIT = spending, CREDIT = money received."""
        start, end = _month_bounds(month) if month else (start_date, end_date)
        try:
            return app.store.summary(start, end, group_by=group_by, direction=direction)
        except StoreError as e:
            return {"error": str(e)}

    @mcp.tool()
    def import_older_sms(days: Annotated[int, Field(ge=1, le=3650)] = 30) -> dict:
        """Ask the phone to scan its SMS inbox for the last N days, then sync."""
        try:
            result = app.phone.import_inbox(days)
            return result | syncer.sync(app.store, app.phone)
        except PhoneError as e:
            return {"error": str(e)}

    @mcp.tool()
    def import_email(days: Annotated[int, Field(ge=1, le=365)] = 30) -> dict:
        """Read order / ride receipts and bank alert emails from Gmail for the last N days and link duplicates."""
        try:
            return run_email_import(app.store, app.config.email_address, app.config.email_app_password, days,
                                    host=app.config.email_host)
        except EmailError as e:
            return {"error": str(e)}

    @mcp.tool()
    def import_statement(
        path: Annotated[str, Field(description="Full path to the statement file on this PC (CSV, XLSX, XLS or PDF)")],
        account: Annotated[str, Field(description="Short account name, e.g. HDFC or SBI")],
        password: Annotated[str | None, Field(description="PDF password, if the statement has one")] = None,
    ) -> dict:
        """Import a bank statement: lines already tracked are linked as duplicates, the rest are added as missed payments."""
        try:
            return run_statement_import(app.store, path, account, password)
        except (StatementError, OSError) as e:
            return {"error": str(e)}

    @mcp.prompt()
    def categorize_month(month: str) -> str:
        """Categorize every uncategorized expense in a month."""
        return (
            f"Categorize all my uncategorized FinTrack expenses for {month}. First call list_categories, then "
            f"list_transactions(month='{month}', status='uncategorized'), then categorize them in batches. "
            "Use the masked SMS text and sender to decide. Afterwards show spending_summary for the month "
            "and list anything you were unsure about."
        )

    return mcp
