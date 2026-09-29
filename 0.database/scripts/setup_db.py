"""
Ensure MariaDB container is up, then load canonical_sql/ into the database.
"""

from __future__ import annotations

import subprocess
import sys
import time
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from db_config import CANONICAL_SQL, REMOTE_SQL_DIR  # noqa: E402
from ensure_db import ensure_db  # noqa: E402


def _run(cmd: list[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, text=True, encoding="utf-8", errors="replace", **kwargs)


def setup_db() -> None:
    cfg = ensure_db()
    name = cfg["CONTAINER_NAME"]
    password = cfg["MARIADB_ROOT_PASSWORD"]
    entry = CANONICAL_SQL / "00_run_all.sql"

    if not entry.is_file():
        print(f"ERROR: missing {entry}")
        sys.exit(1)

    print("=" * 60)
    print(f"Setup DB -> {name} / load {CANONICAL_SQL.name}/")
    print("=" * 60)

    _run(["docker", "exec", name, "mkdir", "-p", REMOTE_SQL_DIR], check=True)
    _run(
        ["docker", "exec", name, "sh", "-c", f"rm -rf {REMOTE_SQL_DIR}/*"],
        check=False,
    )
    _run(
        ["docker", "cp", f"{CANONICAL_SQL}/.", f"{name}:{REMOTE_SQL_DIR}/"],
        check=True,
    )

    sql_command = f"mariadb -uroot -v < {REMOTE_SQL_DIR}/00_run_all.sql"
    full = [
        "docker",
        "exec",
        "-i",
        "-e",
        f"MYSQL_PWD={password}",
        "-w",
        REMOTE_SQL_DIR,
        name,
        "sh",
        "-c",
        sql_command,
    ]
    print("Running 00_run_all.sql ...")
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

    if process and process.returncode == 0:
        print("\nOK - schema applied from canonical_sql/")
        for line in (process.stdout or "").strip().split("\n")[-30:]:
            if line.strip():
                print(line)
    else:
        print("\nERROR during setup")
        print((process.stderr if process else "") or (process.stdout if process else ""))
        sys.exit((process.returncode if process else 1) or 1)


if __name__ == "__main__":
    setup_db()
