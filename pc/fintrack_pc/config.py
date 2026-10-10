"""Settings stored in ~/.fintrack/config.json (override the folder with FINTRACK_HOME)."""

from __future__ import annotations

import json
import os
from dataclasses import asdict, dataclass
from pathlib import Path


def home_dir() -> Path:
    return Path(os.environ.get("FINTRACK_HOME", Path.home() / ".fintrack"))


@dataclass
class Config:
    phone_url: str = "http://127.0.0.1:8765"
    token: str = ""
    console_port: int = 8800

    @property
    def db_path(self) -> Path:
        return home_dir() / "fintrack.db"

    @classmethod
    def load(cls) -> "Config":
        path = home_dir() / "config.json"
        data = json.loads(path.read_text()) if path.exists() else {}
        cfg = cls(**{k: v for k, v in data.items() if k in cls.__dataclass_fields__})
        cfg.phone_url = os.environ.get("FINTRACK_PHONE_URL", cfg.phone_url)
        cfg.token = os.environ.get("FINTRACK_TOKEN", cfg.token)
        return cfg

    def save(self) -> Path:
        home_dir().mkdir(parents=True, exist_ok=True)
        path = home_dir() / "config.json"
        path.write_text(json.dumps(asdict(self), indent=2))
        try:
            path.chmod(0o600)  # the token grants access to your SMS data
        except OSError:
            pass
        return path
