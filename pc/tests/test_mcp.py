import asyncio
import json

from fintrack_pc.mcp_server import build_server


from tests_helpers import call  # noqa: E402



def test_tools_listed(app):
    names = {t.name for t in asyncio.run(build_server(app).list_tools())}
    assert {"sync_phone", "list_transactions", "categorize", "list_categories", "add_category",
            "spending_summary", "import_older_sms", "import_email", "import_statement", "get_period"} <= names


def test_categorize_flow(app, phone):
    phone.add("a", 349, body="Rs 349 spent at SWIGGY")
    phone.add("b", 60, body="Rs 60 spent at UBER")
    server = build_server(app)
    listed = call(server, "list_transactions", {"month": "2026-10"})
    assert listed["total_matching"] == 2 and listed["note"] is None
    out = call(server, "categorize", {"assignments": [
        {"uid": "a", "category": "Food & Dining"}, {"uid": "b", "category": "Transport"}]})
    assert out["updated"] == 2 and out["note"] is None
    assert phone.labels == {"a": "Food & Dining", "b": "Transport"}
    summary = call(server, "spending_summary", {"month": "2026-10"})
    assert summary["total"] == 409
    assert call(server, "list_transactions", {"month": "2026-10"})["total_matching"] == 0
