"""Local web console (http://localhost:8800): review, edit and sync categories; export the dataset."""

from __future__ import annotations

import csv
import io
import json
import tempfile
from datetime import date
from pathlib import Path

from starlette.applications import Starlette
from starlette.concurrency import run_in_threadpool
from starlette.middleware import Middleware
from starlette.requests import Request
from starlette.responses import FileResponse, JSONResponse, Response
from starlette.routing import Route
from starlette.types import ASGIApp, Receive, Scope, Send

from .email_import import EmailError, import_email
from . import periods
from .phone import PhoneError
from .statement_import import StatementError, import_statement
from .store import StoreError
from . import sync as syncer

STATIC = Path(__file__).parent / "static"
ALLOWED_HOSTS = {"localhost", "127.0.0.1", "[::1]"}


class LocalOnly:
    """Blocks other websites from driving the console: checks the Host header (DNS rebinding) and
    requires a custom header on writes (which browsers won't send cross-site without CORS approval)."""

    def __init__(self, app: ASGIApp):
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] == "http":
            headers = {k.decode().lower(): v.decode() for k, v in scope["headers"]}
            host = headers.get("host", "")
            host = host.split("]")[0] + "]" if host.startswith("[") else host.split(":")[0]
            if host not in ALLOWED_HOSTS:
                await JSONResponse({"error": "console is only reachable as localhost"}, 403)(scope, receive, send)
                return
            if scope["method"] not in ("GET", "HEAD") and headers.get("x-fintrack") != "1":
                await JSONResponse({"error": "missing X-FinTrack header"}, 403)(scope, receive, send)
                return
        await self.app(scope, receive, send)


def build_console(app) -> Starlette:
    """[app] is a fintrack_pc.mcp_server.App (store + phone client)."""
    store = app.store

    async def index(_: Request) -> Response:
        return FileResponse(STATIC / "console.html")

    async def transactions(req: Request) -> Response:
        q = req.query_params
        rows, total = store.list_transactions(
            start=q.get("start") or None,
            end=q.get("end") or None,
            status=q.get("status", "all"),
            direction=q.get("direction", "ALL"),
            category=q.get("category") or None,
            source=q.get("source") or None,
            query=q.get("q") or None,
            include_duplicates=True,
            limit=min(int(q.get("limit", 300)), 2000),
            offset=int(q.get("offset", 0)),
        )
        return JSONResponse({"total": total, "rows": rows})

    async def period(req: Request) -> Response:
        start_day = store.cycle_start_day()
        month = req.query_params.get("month")
        p = periods.for_month(month, start_day) if month else periods.containing(date.today(), start_day)
        return JSONResponse(p.as_dict() | {"starts_on": periods.describe(start_day)})

    async def overview(_: Request) -> Response:
        return JSONResponse({
            "current_period": periods.containing(date.today(), store.cycle_start_day()).as_dict(),
            "counts": store.counts(),
            "categories": store.category_counts(),
            "last_pull_at": store.get_meta("last_pull_at"),
            "last_push_at": store.get_meta("last_push_at"),
            "phone_url": app.config.phone_url,
        })

    async def label(req: Request) -> Response:
        body = await req.json()
        category = body.get("category")
        result = store.set_labels([(uid, category) for uid in body["uids"]], source="manual")
        return JSONResponse(result.as_dict())

    async def accept(req: Request) -> Response:
        body = await req.json()
        return JSONResponse({"accepted": store.accept(body["uids"])})

    async def duplicate(req: Request) -> Response:
        body = await req.json()
        store.set_dup_decision(body["uid"], body.get("decision"))
        return JSONResponse({"ok": True})

    async def history(req: Request) -> Response:
        return JSONResponse({"history": store.history(req.path_params["uid"])})

    async def categories(req: Request) -> Response:
        body = await req.json()
        action = body.get("action")
        if action == "add":
            return JSONResponse({"category": store.add_category(body["name"])})
        if action == "rename":
            return JSONResponse(store.rename_category(body["old"], body["new"]))
        if action == "delete":
            store.delete_category(body["name"])
            return JSONResponse({"ok": True})
        raise StoreError(f"unknown action: {action}")

    async def do_sync(_: Request) -> Response:
        try:
            return JSONResponse(syncer.sync(store, app.phone) | {"counts": store.counts()})
        except PhoneError as e:
            return JSONResponse({"error": str(e)}, 502)

    async def email_import(req: Request) -> Response:
        body = await req.json()
        cfg = app.config
        try:
            result = await run_in_threadpool(import_email, store, cfg.email_address, cfg.email_app_password,
                                             int(body.get("days", 30)), cfg.email_host)
        except EmailError as e:
            return JSONResponse({"error": str(e)}, 400)
        return JSONResponse(result)

    async def statement_import(req: Request) -> Response:
        q = req.query_params
        suffix = Path(q.get("filename", "statement.csv")).suffix.lower() or ".csv"
        data = await req.body()
        if not data:
            raise StoreError("empty file")
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / f"statement{suffix}"
            path.write_bytes(data)
            try:
                result = await run_in_threadpool(import_statement, store, path, q.get("account") or "BANK",
                                                 q.get("password") or None)
            except StatementError as e:
                return JSONResponse({"error": str(e)}, 400)
        return JSONResponse(result)

    async def summary(req: Request) -> Response:
        q = req.query_params
        return JSONResponse(store.summary(q.get("start") or None, q.get("end") or None,
                                          group_by=q.get("group_by", "category"), direction=q.get("direction", "DEBIT")))

    async def dataset_stats(_: Request) -> Response:
        return JSONResponse(store.dataset_stats())

    async def dataset_export(req: Request) -> Response:
        fmt = req.query_params.get("format", "csv")
        rows = store.dataset(include_unreviewed=req.query_params.get("all") == "1")
        if fmt == "jsonl":
            body = "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows)
            return Response(body, media_type="application/x-ndjson",
                            headers={"Content-Disposition": 'attachment; filename="fintrack-dataset.jsonl"'})
        return Response(to_csv(rows), media_type="text/csv",
                        headers={"Content-Disposition": 'attachment; filename="fintrack-dataset.csv"'})

    async def on_store_error(_: Request, exc: Exception) -> Response:
        return JSONResponse({"error": str(exc)}, 400)

    return Starlette(
        routes=[
            Route("/", index),
            Route("/api/overview", overview),
            Route("/api/period", period),
            Route("/api/transactions", transactions),
            Route("/api/label", label, methods=["POST"]),
            Route("/api/accept", accept, methods=["POST"]),
            Route("/api/duplicate", duplicate, methods=["POST"]),
            Route("/api/history/{uid}", history),
            Route("/api/categories", categories, methods=["POST"]),
            Route("/api/sync", do_sync, methods=["POST"]),
            Route("/api/summary", summary),
            Route("/api/email/import", email_import, methods=["POST"]),
            Route("/api/statement/import", statement_import, methods=["POST"]),
            Route("/api/dataset/stats", dataset_stats),
            Route("/api/dataset/export", dataset_export),
        ],
        middleware=[Middleware(LocalOnly)],
        exception_handlers={StoreError: on_store_error, KeyError: on_store_error},
    )


def to_csv(rows: list[dict]) -> str:
    buf = io.StringIO()
    if rows:
        writer = csv.DictWriter(buf, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    return buf.getvalue()


def serve(app, port: int, in_thread: bool = False):
    """Runs the console with uvicorn. In MCP mode it runs in a thread and must not print to stdout."""
    import uvicorn

    config = uvicorn.Config(build_console(app), host="127.0.0.1", port=port, log_config=None,
                            access_log=False, log_level="warning")
    server = uvicorn.Server(config)
    if not in_thread:
        server.run()
        return server
    import threading

    threading.Thread(target=server.run, name="fintrack-console", daemon=True).start()
    return server
