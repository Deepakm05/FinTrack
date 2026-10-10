from starlette.testclient import TestClient

from fintrack_pc import sync
from fintrack_pc.console import build_console

H = {"X-FinTrack": "1"}


def client(app):
    return TestClient(build_console(app), base_url="http://localhost:8800")


def test_page_and_overview(app):
    c = client(app)
    assert "FinTrack" in c.get("/").text
    assert c.get("/api/overview").json()["counts"]["total"] == 0


def test_rejects_other_hosts_and_missing_header(app):
    evil = TestClient(build_console(app), base_url="http://evil.example")
    assert evil.get("/api/overview").status_code == 403
    assert client(app).post("/api/sync", json={}).status_code == 403


def test_edit_review_and_export(app, phone):
    phone.add("a", 349, body="Rs 349 spent at SWIGGY")
    phone.add("b", 999, body="Rs 999 spent at AMAZON")
    c = client(app)
    assert c.post("/api/sync", json={}, headers=H).json()["pulled"] == 2
    app.store.set_labels([("a", "Food & Dining"), ("b", "Food & Dining")], "claude")

    unreviewed = c.get("/api/transactions", params={"status": "unreviewed"}).json()
    assert unreviewed["total"] == 2
    assert c.post("/api/accept", json={"uids": ["a"]}, headers=H).json()["accepted"] == 1
    assert c.post("/api/label", json={"uids": ["b"], "category": "Shopping"}, headers=H).json()["updated"] == 1

    csv_text = c.get("/api/dataset/export", params={"format": "csv"}).text
    assert csv_text.splitlines()[0].startswith("uid,text,")
    assert len(csv_text.strip().splitlines()) == 3
    assert c.get("/api/dataset/stats").json()["claude_labels_corrected"] == 1

    r = c.post("/api/categories", json={"action": "add", "name": "Pets"}, headers=H)
    assert r.json()["category"] == "Pets"
    bad = c.post("/api/label", json={"uids": ["a"], "category": "Nope"}, headers=H).json()
    assert bad["errors"]


def test_sync_reports_unreachable_phone(app):
    import httpx
    from fintrack_pc.phone import PhoneClient

    def down(_):
        raise httpx.ConnectError("refused")

    app._phone = PhoneClient("http://phone.test", "t", transport=httpx.MockTransport(down))
    r = client(app).post("/api/sync", json={}, headers=H)
    assert r.status_code == 502 and "not reachable" in r.json()["error"]
