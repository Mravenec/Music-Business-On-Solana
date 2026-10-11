"""
OPTIONAL fixture loader only.

Judge / Colosseum demo: do NOT run this file. Use setup_db.py, then create
users, venues, bands, and payments through the React app with DevNet wallets.

This script loads 0.database/mocks/mocks.sql (fake bands/accounts). It must
never run against a demo you show judges.
"""

from __future__ import annotations

import subprocess
import sys
import time
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from db_config import MOCKS, REMOTE_MOCKS_DIR  # noqa: E402
from ensure_db import ensure_db  # noqa: E402

MOCK_FILE = "mocks.sql"


def _run(cmd: list[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, text=True, encoding="utf-8", errors="replace", **kwargs)


def load_mocks() -> None:
    cfg = ensure_db()
    name = cfg["CONTAINER_NAME"]
    password = cfg["MARIADB_ROOT_PASSWORD"]

    print("=" * 60)
    print(f"Load mocks -> {name} / {MOCKS.name}/")
    print("=" * 60)
    print("Demo/test data only — never run this against production.")

    _run(["docker", "exec", name, "mkdir", "-p", REMOTE_MOCKS_DIR], check=True)
    _run(["docker", "exec", name, "sh", "-c", f"rm -rf {REMOTE_MOCKS_DIR}/*"], check=False)
    _run(["docker", "cp", f"{MOCKS}/.", f"{name}:{REMOTE_MOCKS_DIR}/"], check=True)

    local_path = MOCKS / MOCK_FILE
    if not local_path.is_file():
        print(f"ERROR: {MOCK_FILE} not found in {MOCKS}")
        sys.exit(1)

    print(f"Running {MOCK_FILE} ...")
    sql_command = f"mariadb -uroot -v < {REMOTE_MOCKS_DIR}/{MOCK_FILE}"
    full = [
        "docker", "exec", "-i",
        "-e", f"MYSQL_PWD={password}",
        "-w", REMOTE_MOCKS_DIR,
        name, "sh", "-c", sql_command,
    ]
    process = None
    for attempt in range(1, 6):
        process = _run(full, check=False, capture_output=True)
        if process.returncode == 0:
            break
        err = (process.stderr or process.stdout or "").strip()
        if "1045" in err or "Access denied" in err:
            print(f"Auth not ready yet (attempt {attempt}/5) - retrying...")
            time.sleep(2)
            continue
        break

    if not process or process.returncode != 0:
        print(f"\nERROR loading {MOCK_FILE}")
        print((process.stderr if process else "") or (process.stdout if process else ""))
        sys.exit((process.returncode if process else 1) or 1)

    print("\nOK - mocks loaded from 0.database/mocks/mocks.sql")


if __name__ == "__main__":
    load_mocks()
