"""Shared MariaDB Docker config (0.database)."""

from __future__ import annotations

import os
from pathlib import Path

DB_ROOT = Path(__file__).resolve().parents[1]
COMPOSE_FILE = DB_ROOT / "docker-compose.yml"
ENV_FILE = DB_ROOT / ".env"
ENV_EXAMPLE = DB_ROOT / ".env.example"
CANONICAL_SQL = DB_ROOT / "canonical_sql"
REMOTE_SQL_DIR = "/tmp/sql_app"
MOCKS = DB_ROOT / "mocks"
REMOTE_MOCKS_DIR = "/tmp/mocks_app"

DEFAULTS = {
    "CONTAINER_NAME": "app-mariadb",
    "HOST_PORT": "3306",
    "MARIADB_ROOT_PASSWORD": "123456",
    "MARIADB_DATABASE": "app",
    "MARIADB_IMAGE": "mariadb:10.11",
}


def load_env() -> dict[str, str]:
    cfg = dict(DEFAULTS)
    for path in (ENV_EXAMPLE, ENV_FILE):
        if not path.is_file():
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            key = key.strip()
            value = value.strip().strip('"').strip("'")
            if key:
                cfg[key] = value
    for key in DEFAULTS:
        if key in os.environ and os.environ[key].strip():
            cfg[key] = os.environ[key].strip()
    return cfg


def jdbc_url(cfg: dict[str, str] | None = None) -> str:
    c = cfg or load_env()
    return f"jdbc:mariadb://localhost:{c['HOST_PORT']}/{c['MARIADB_DATABASE']}"
