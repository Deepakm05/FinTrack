"""Command line: fintrack setup | status | sync | console | mcp | export."""

from __future__ import annotations

import argparse
import json
import socket
import sys
import webbrowser
from pathlib import Path

from .config import Config
from .console import serve, to_csv
from .mcp_server import App, build_server
from .phone import PhoneClient, PhoneError
from . import sync as syncer


def port_free(port: int) -> bool:
    with socket.socket() as s:
        return s.connect_ex(("127.0.0.1", port)) != 0


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="fintrack", description="FinTrack PC side")
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("setup", help="save the phone address and token")
    s.add_argument("--phone-url", required=True, help="e.g. http://192.168.1.5:8765 or http://127.0.0.1:8765 for USB")
    s.add_argument("--token", required=True, help="access token shown in the phone app")
    s.add_argument("--console-port", type=int, default=8800)
    sub.add_parser("status", help="check the phone connection and show counts")
    sub.add_parser("sync", help="pull from the phone and push labels back")
    c = sub.add_parser("console", help="open the web console")
    c.add_argument("--no-browser", action="store_true")
    m = sub.add_parser("mcp", help="run the MCP server on stdio (used by Claude Desktop / Claude Code)")
    m.add_argument("--no-console", action="store_true", help="don't also start the web console")
    e = sub.add_parser("export", help="export the training dataset")
    e.add_argument("--format", choices=["csv", "jsonl"], default="csv")
    e.add_argument("--out", type=Path, required=True)
    e.add_argument("--include-unreviewed", action="store_true")
    args = p.parse_args(argv)

    if args.cmd == "setup":
        cfg = Config(phone_url=args.phone_url.rstrip("/"), token=args.token, console_port=args.console_port)
        path = cfg.save()
        print(f"Saved {path}")
        try:
            st = PhoneClient(cfg.phone_url, cfg.token).status()
            print(f"Connected to phone: {st.get('count')} transactions stored on the phone.")
        except PhoneError as err:
            print(f"Saved, but could not reach the phone yet: {err}")
        return 0

    app = App()

    if args.cmd == "status":
        try:
            print(json.dumps(app.phone.status(), indent=2))
        except PhoneError as err:
            print(err)
        print(json.dumps(app.store.counts(), indent=2))
        return 0

    if args.cmd == "sync":
        try:
            print(json.dumps(syncer.sync(app.store, app.phone), indent=2))
        except PhoneError as err:
            print(err, file=sys.stderr)
            return 1
        return 0

    if args.cmd == "console":
        url = f"http://localhost:{app.config.console_port}"
        print(f"FinTrack console on {url}  (Ctrl+C to stop)")
        if not args.no_browser:
            webbrowser.open(url)
        serve(app, app.config.console_port)
        return 0

    if args.cmd == "mcp":
        # stdout belongs to the MCP protocol here: never print to it.
        if not args.no_console and port_free(app.config.console_port):
            serve(app, app.config.console_port, in_thread=True)
        build_server(app).run("stdio")
        return 0

    if args.cmd == "export":
        rows = app.store.dataset(include_unreviewed=args.include_unreviewed)
        if args.format == "jsonl":
            args.out.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")
        else:
            args.out.write_text(to_csv(rows), encoding="utf-8")
        print(f"Wrote {len(rows)} rows to {args.out}")
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
