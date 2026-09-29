"""
Wipe and recreate the harness database (requires human approval for schema changes).
Ensures the container is running first.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from ensure_db import ensure_db  # noqa: E402


def drop_db() -> None:
    cfg = ensure_db()
    name = cfg["CONTAINER_NAME"]
    password = cfg["MARIADB_ROOT_PASSWORD"]
    database = cfg["MARIADB_DATABASE"]

    # Extra schemas that canonical_sql/ may create (multi-schema:
    # each has FKs into `database` or into another extra schema, so drop order
    # matters — a referenced schema must drop after whatever references it, or
    # DROP DATABASE fails with a foreign key constraint error). eh8s_onchain
    # references both the hub and eh8s_academy, so it drops first. setup_db.py's
    # 00_run_all.sql recreates these itself via CREATE DATABASE IF NOT EXISTS —
    # this script only needs to drop them, not recreate them.
    extra_schemas = ["eh8s_onchain", "eh8s_ops", "eh8s_academy", "eh8s_catalog", "eh8s_role"]

    print("=" * 60)
    print(f"Drop DB -> container={name}  database={database} + {', '.join(extra_schemas)}")
    print("=" * 60)
    print("WARNING: this destroys all data in those schemas.")

    drop_extra = "".join(f"DROP DATABASE IF EXISTS `{s}`; " for s in extra_schemas)
    sql = (
        f"{drop_extra}"
        f"DROP DATABASE IF EXISTS `{database}`; "
        f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    )
    cmd = [
        "docker",
        "exec",
        "-i",
        name,
        "mariadb",
        "-uroot",
        f"-p{password}",
        "-e",
        sql,
    ]
    process = subprocess.run(
        cmd,
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=True,
    )
    if process.returncode == 0:
        print(f"OK - database `{database}` recreated (empty). Run setup_db.py to reload SQL.")
    else:
        print("ERROR during drop")
        print(process.stderr or process.stdout)
        sys.exit(process.returncode or 1)


if __name__ == "__main__":
    drop_db()
