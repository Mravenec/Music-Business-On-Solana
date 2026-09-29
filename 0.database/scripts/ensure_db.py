"""
Ensure the project MariaDB Docker container exists and is running.

- Does NOT install Docker Desktop / Engine (must already be installed).
- If the project container is missing → docker compose up -d (create + start + publish port).
- If it exists but is stopped → docker start.
- Waits until MariaDB accepts connections.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from db_config import COMPOSE_FILE, ENV_FILE, ENV_EXAMPLE, load_env  # noqa: E402


def _run(cmd: list[str], *, check: bool = True, capture: bool = False) -> subprocess.CompletedProcess:
    return subprocess.run(
        cmd,
        check=check,
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=capture,
    )


def _docker_available() -> None:
    if not shutil.which("docker"):
        print("ERROR: Docker CLI not found. Install Docker Desktop (or Engine) first.")
        sys.exit(1)
    probe = _run(["docker", "info"], check=False, capture=True)
    if probe.returncode != 0:
        print("ERROR: Docker daemon is not running. Start Docker Desktop and retry.")
        if probe.stderr:
            print(probe.stderr.strip())
        sys.exit(1)


def _container_state(name: str) -> str | None:
    """Return 'running', 'exited', etc., or None if the container does not exist."""
    r = _run(
        ["docker", "inspect", "-f", "{{.State.Status}}", name],
        check=False,
        capture=True,
    )
    if r.returncode != 0:
        return None
    return (r.stdout or "").strip() or None


def _compose_cmd(cfg: dict[str, str]) -> list[str]:
    cmd = ["docker", "compose", "-f", str(COMPOSE_FILE)]
    if ENV_FILE.is_file():
        cmd.extend(["--env-file", str(ENV_FILE)])
    elif ENV_EXAMPLE.is_file():
        cmd.extend(["--env-file", str(ENV_EXAMPLE)])
    return cmd


def _compose_up(cfg: dict[str, str]) -> None:
    env = {**os.environ, **cfg}
    cmd = _compose_cmd(cfg) + ["up", "-d"]
    print(f"Creating/starting via compose: {' '.join(cmd)}")
    result = subprocess.run(cmd, check=False, text=True, encoding="utf-8", errors="replace", env=env)
    if result.returncode != 0:
        port = cfg["HOST_PORT"]
        print(
            f"ERROR: docker compose failed (often host port {port} already in use).\n"
            f"  Fix: copy 0.database/.env.example to 0.database/.env and set HOST_PORT to a free port."
        )
        sys.exit(result.returncode)


def _wait_ready(cfg: dict[str, str], timeout_s: int = 90) -> None:
    name = cfg["CONTAINER_NAME"]
    password = cfg["MARIADB_ROOT_PASSWORD"]
    print(f"Waiting for MariaDB in '{name}' (up to {timeout_s}s)...")
    deadline = time.time() + timeout_s
    last_err = ""
    while time.time() < deadline:
        r = _run(
            [
                "docker",
                "exec",
                "-e",
                f"MYSQL_PWD={password}",
                name,
                "mariadb",
                "-uroot",
                "-e",
                "SELECT 1;",
            ],
            check=False,
            capture=True,
        )
        if r.returncode == 0:
            print("MariaDB is ready.")
            return
        last_err = (r.stderr or r.stdout or "").strip()
        time.sleep(2)
    print("ERROR: MariaDB did not become ready in time.")
    if last_err:
        print(last_err)
    sys.exit(1)


def ensure_db() -> dict[str, str]:
    _docker_available()
    if not COMPOSE_FILE.is_file():
        print(f"ERROR: missing compose file: {COMPOSE_FILE}")
        sys.exit(1)

    if not ENV_FILE.is_file() and ENV_EXAMPLE.is_file():
        print(f"Note: no .env - using {ENV_EXAMPLE.name} defaults (copy to .env to customize).")

    cfg = load_env()
    name = cfg["CONTAINER_NAME"]
    port = cfg["HOST_PORT"]
    db = cfg["MARIADB_DATABASE"]

    print("=" * 60)
    print(f"Ensure DB -> container={name}  host_port={port}  database={db}")
    print("=" * 60)

    state = _container_state(name)
    if state is None:
        print(f"Container '{name}' not found - creating with docker compose...")
        _compose_up(cfg)
    elif state == "running":
        print(f"Container '{name}' already running.")
    elif state in ("created", "exited", "paused"):
        print(f"Container '{name}' exists but is '{state}' - starting...")
        start = _run(["docker", "start", name], check=False, capture=True)
        if start.returncode != 0:
            print((start.stderr or start.stdout or "").strip())
            print("Start failed - recreating with docker compose up -d...")
            _run(["docker", "rm", "-f", name], check=False)
            _compose_up(cfg)
    else:
        print(f"Container '{name}' status '{state}' - recreating via compose...")
        _run(["docker", "rm", "-f", name], check=False)
        _compose_up(cfg)

    state = _container_state(name)
    if state != "running":
        print(f"WARNING: status is '{state}' - retrying compose up -d...")
        _compose_up(cfg)

    _wait_ready(cfg)

    print(f"OK - listening on localhost:{port}  (jdbc:mariadb://localhost:{port}/{db})")
    print(f"     user=root  password={cfg['MARIADB_ROOT_PASSWORD']}")
    return cfg


def main() -> None:
    ensure_db()


if __name__ == "__main__":
    main()
