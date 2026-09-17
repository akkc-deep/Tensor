#!/usr/bin/env python3
"""Run the real integrity browser closure and four packaged regressions in owned MySQL."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone


ROOT = Path(__file__).resolve().parents[1]
CONTROL_PLANE = ROOT / "control-plane"
EVIDENCE_DIR = ROOT / "docs" / "verification" / "data-integrity-t13" / "real"
IMAGE = "mysql:8.4.6"
BASE_URL = "http://127.0.0.1:8080"
NODE_BIN = Path.home() / ".nvm" / "versions" / "node" / "v24.15.0" / "bin"
MYSQL_BIN = Path("/usr/local/mysql/bin")
OLD_PREFIXES = {
    "download-outcomes": "tensor_m14_t02_",
    "dataset-query": "tensor_m14_t03_",
    "tushare-metadata": "tensor_m14_t04_",
    "fixture-flow": "tensor_issue018_t12_fixture_",
}
SUITE_STOP_TIMEOUT_SECONDS = 30
SUITE_KILL_TIMEOUT_SECONDS = 5


class LauncherInterrupted(RuntimeError):
    pass


def install_interrupt_handlers() -> None:
    interrupted: int | None = None

    def handle_interrupt(number: int, _frame: object) -> None:
        nonlocal interrupted
        if interrupted is None:
            interrupted = number
            raise LauncherInterrupted(f"Received signal {signal.Signals(number).name}")

    signal.signal(signal.SIGINT, handle_interrupt)
    signal.signal(signal.SIGTERM, handle_interrupt)


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def atomic_private_write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{secrets.token_hex(6)}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(text)
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        if temporary.exists():
            temporary.unlink()


def command_output(command: list[str], env: dict[str, str]) -> str:
    completed = subprocess.run(
        command,
        cwd=ROOT,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=True,
    )
    return completed.stdout.strip()


def validate_jar(raw: str) -> tuple[Path, str]:
    path = Path(raw)
    if not path.is_absolute():
        raise ValueError("--acceptance-jar must be an actual absolute path")
    resolved = path.resolve(strict=True)
    if path != resolved or path.is_symlink() or not path.is_file():
        raise ValueError("--acceptance-jar must be an actual absolute ordinary file")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    return path, digest


def tool_environment() -> tuple[dict[str, str], dict[str, str]]:
    if not NODE_BIN.is_dir() or not MYSQL_BIN.is_dir():
        raise RuntimeError("Required Node 24.15.0 or MySQL 8.4 client directory is unavailable")
    env = os.environ.copy()
    java_home = Path(env.get("JAVA_HOME", ""))
    if not (java_home / "bin" / "java").is_file():
        raise RuntimeError("JAVA_HOME must identify the Java 21 runtime")
    env["PATH"] = os.pathsep.join((str(NODE_BIN), str(MYSQL_BIN), str(java_home / "bin"), env.get("PATH", "")))
    versions = {
        "node": command_output(["node", "--version"], env),
        "mysqlClient": command_output(["mysql", "--version"], env),
        "docker": command_output(["docker", "--version"], env),
    }
    java = command_output(["java", "-version"], env)
    if versions["node"] != "v24.15.0":
        raise RuntimeError("Node must be exactly v24.15.0")
    if "Ver 8.4." not in versions["mysqlClient"]:
        raise RuntimeError("MySQL client must be 8.4.x")
    match = re.search(r'version "([^"]+)"', java)
    if not match or not match.group(1).startswith("21"):
        raise RuntimeError("Java must be version 21")
    versions["java"] = match.group(1)
    return env, versions


def docker_output(arguments: list[str], env: dict[str, str]) -> str:
    return command_output(["docker", *arguments], env)


def wait_for_mysql(defaults_file: Path, env: dict[str, str]) -> str:
    deadline = time.monotonic() + 120
    command = [
        "mysql",
        f"--defaults-file={defaults_file}",
        "--no-login-paths",
        "--batch",
        "--skip-column-names",
        "--execute=SELECT VERSION(), @@log_bin_trust_function_creators;",
    ]
    while time.monotonic() < deadline:
        completed = subprocess.run(
            command,
            cwd=ROOT,
            env=env,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        )
        if completed.returncode == 0:
            version, trust = completed.stdout.strip().split("\t", 1)
            if not version.startswith("8.4."):
                raise RuntimeError("Owned MySQL server is not 8.4.x")
            if trust != "1":
                raise RuntimeError("Owned MySQL does not permit restricted trigger creation")
            return version
        time.sleep(0.5)
    raise RuntimeError("Owned MySQL did not become ready within 120 seconds")


def suite_environment(base: dict[str, str]) -> dict[str, str]:
    env = base.copy()
    for key in list(env):
        if key.startswith(("TENSOR_", "SPRING_", "SERVER_", "MYSQL_", "M14_")):
            del env[key]
    return env


def process_group_exists(group_id: int) -> bool:
    try:
        os.killpg(group_id, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def wait_for_process_group(group_id: int, timeout: float) -> bool:
    deadline = time.monotonic() + timeout
    while process_group_exists(group_id):
        if time.monotonic() >= deadline:
            return False
        time.sleep(0.05)
    return True


def terminate_suite(process: subprocess.Popen[bytes]) -> None:
    group_id = process.pid
    if process_group_exists(group_id):
        try:
            os.killpg(group_id, signal.SIGTERM)
        except ProcessLookupError:
            pass
    try:
        process.wait(timeout=SUITE_STOP_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired:
        pass
    if wait_for_process_group(group_id, SUITE_STOP_TIMEOUT_SECONDS):
        return
    if process_group_exists(group_id):
        try:
            os.killpg(group_id, signal.SIGKILL)
        except ProcessLookupError:
            pass
    try:
        process.wait(timeout=SUITE_KILL_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired as error:
        raise RuntimeError("Owned suite process did not exit after SIGKILL") from error
    if not wait_for_process_group(group_id, SUITE_KILL_TIMEOUT_SECONDS):
        raise RuntimeError("Owned suite process group remained after SIGKILL")


def run_suite(name: str, spec: str, environment: dict[str, str], npm: Path) -> dict[str, object]:
    started = time.monotonic()
    print(f"[T13] running {name}", flush=True)
    process = subprocess.Popen(
        [str(npm), "--prefix", str(CONTROL_PLANE), "run", "test:e2e", "--", spec],
        cwd=ROOT,
        env=environment,
        start_new_session=True,
    )
    try:
        exit_code = process.wait()
    finally:
        if process_group_exists(process.pid):
            terminate_suite(process)
    duration = round(time.monotonic() - started, 3)
    print(f"[T13] {name}: exit={exit_code} duration={duration}s", flush=True)
    return {"name": name, "exitCode": exit_code, "durationSeconds": duration}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--acceptance-jar", required=True)
    arguments = parser.parse_args()
    install_interrupt_handlers()

    started_at = utc_now()
    container_name: str | None = None
    temporary_directory: Path | None = None
    cleanup = {"containerRemoved": False, "temporaryCredentialsRemoved": False}
    suites: list[dict[str, object]] = []
    failure: BaseException | None = None
    jar_path: Path | None = None
    jar_sha256: str | None = None
    versions: dict[str, str] = {}
    server_version: str | None = None
    aliases = ["t13", *OLD_PREFIXES]

    try:
        jar_path, jar_sha256 = validate_jar(arguments.acceptance_jar)
        base_env, versions = tool_environment()
        npm = NODE_BIN / "npm"
        if not npm.is_file():
            raise RuntimeError("Node 24.15.0 npm executable is unavailable")

        suffix = secrets.token_hex(8)
        container_name = f"tensor-integrity-t13-{suffix}"
        schemas = {"t13": f"tensor_integrity_t13_{suffix}"}
        schemas.update({name: f"{prefix}{suffix}" for name, prefix in OLD_PREFIXES.items()})
        username = f"tensor_t13_{secrets.token_hex(4)}"
        password = secrets.token_hex(32)
        root_password = secrets.token_hex(32)
        # Colima shares the repository's /Users path, while macOS's /var/folders temp root
        # is outside the VM. Keep the private directory in the worktree and always remove it.
        temporary_directory = Path(tempfile.mkdtemp(prefix=".tensor-integrity-t13-", dir=ROOT))
        os.chmod(temporary_directory, 0o700)
        root_file = temporary_directory / "root-password"
        init_file = temporary_directory / "001-init.sql"
        atomic_private_write(root_file, f"{root_password}\n")
        statements = [
            *[
                f"CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;"
                for schema in schemas.values()
            ],
            f"CREATE USER '{username}'@'%' IDENTIFIED BY '{password}';",
            *[
                f"GRANT CREATE,SELECT,INSERT,UPDATE,ALTER,INDEX,REFERENCES,TRIGGER ON `{schema}`.* TO '{username}'@'%';"
                for schema in schemas.values()
            ],
        ]
        atomic_private_write(init_file, "\n".join(statements) + "\n")

        docker_output([
            "run", "--detach", "--name", container_name,
            "--publish", "127.0.0.1::3306",
            "--env", "MYSQL_ROOT_PASSWORD_FILE=/run/secrets/root-password",
            "--mount", f"type=bind,src={root_file},dst=/run/secrets/root-password,readonly",
            "--mount", f"type=bind,src={init_file},dst=/docker-entrypoint-initdb.d/001-init.sql,readonly",
            IMAGE,
            "--character-set-server=utf8mb4",
            "--collation-server=utf8mb4_0900_as_cs",
            "--log-bin-trust-function-creators=ON",
        ], base_env)
        port_output = docker_output(["port", container_name, "3306/tcp"], base_env)
        match = re.fullmatch(r"127\.0\.0\.1:([0-9]+)", port_output)
        if not match:
            raise RuntimeError("Owned MySQL did not publish one loopback port")
        port = int(match.group(1))
        if port < 1 or port > 65535:
            raise RuntimeError("Owned MySQL published an invalid port")

        defaults_file = temporary_directory / "mysql-defaults.cnf"
        atomic_private_write(
            defaults_file,
            f"[client]\nhost=127.0.0.1\nport={port}\nuser={username}\npassword={password}\nprotocol=TCP\n",
        )
        server_version = wait_for_mysql(defaults_file, base_env)

        fields = (
            "TENSOR_DB_URL", "TENSOR_DB_USERNAME", "TENSOR_DB_PASSWORD",
            "M14_DB_SCHEMA", "M14_MYSQL_DEFAULTS_FILE",
        )

        def database_entry(schema: str) -> dict[str, str]:
            values = {
                "TENSOR_DB_URL": f"jdbc:mysql://127.0.0.1:{port}/{schema}",
                "TENSOR_DB_USERNAME": username,
                "TENSOR_DB_PASSWORD": password,
                "M14_DB_SCHEMA": schema,
                "M14_MYSQL_DEFAULTS_FILE": str(defaults_file),
            }
            if tuple(values) != fields:
                raise RuntimeError("Internal packaged environment field mismatch")
            return values

        packaged_file = temporary_directory / "packaged-environments.json"
        packaged = {name: database_entry(schemas[name]) for name in OLD_PREFIXES}
        atomic_private_write(packaged_file, json.dumps(packaged, separators=(",", ":")) + "\n")

        common = suite_environment(base_env)
        common.update({
            "ACCEPTANCE_JAR": str(jar_path),
            "ISSUE_017_ACCEPTANCE_JAR_SHA256": jar_sha256,
            "PLAYWRIGHT_BASE_URL": BASE_URL,
        })
        t13_environment = common | database_entry(schemas["t13"])
        suites.append(run_suite("integrity-fixture", "e2e/integrity-fixture.spec.js", t13_environment, npm))

        packaged_environment = common | {"TENSOR_PACKAGED_E2E_ENV_FILE": str(packaged_file)}
        for name in OLD_PREFIXES:
            suites.append(run_suite(name, f"e2e/{name}.spec.js", packaged_environment, npm))
    except BaseException as error:
        failure = error
    finally:
        if container_name is not None:
            try:
                cleanup_env = locals().get("base_env", os.environ.copy())
                subprocess.run(
                    ["docker", "rm", "--force", "--volumes", container_name],
                    cwd=ROOT,
                    env=cleanup_env,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    check=True,
                )
                cleanup["containerRemoved"] = True
            except BaseException as error:
                failure = ExceptionGroup("T13 run and container cleanup failed", [item for item in (failure, error) if item])
        if temporary_directory is not None:
            try:
                shutil.rmtree(temporary_directory)
                cleanup["temporaryCredentialsRemoved"] = not temporary_directory.exists()
            except BaseException as error:
                failure = ExceptionGroup("T13 run and credential cleanup failed", [item for item in (failure, error) if item])

    summary = {
        "version": 1,
        "task": "DATA-INTEGRITY-T13",
        "startedAt": started_at,
        "finishedAt": utc_now(),
        "jarSha256": jar_sha256,
        "mysqlImage": IMAGE,
        "mysqlServer": server_version,
        "tools": versions,
        "schemaAliases": aliases,
        "suites": suites,
        "cleanup": cleanup,
        "passed": failure is None and len(suites) == 5 and all(item["exitCode"] == 0 for item in suites),
    }
    atomic_private_write(EVIDENCE_DIR / "launcher-summary.json", json.dumps(summary, ensure_ascii=False, indent=2) + "\n")

    if failure is not None:
        print(f"T13 launcher failed: {type(failure).__name__}", file=sys.stderr)
        return 1
    failed = [item["name"] for item in suites if item["exitCode"] != 0]
    if failed:
        print(f"T13 suites failed: {', '.join(failed)}", file=sys.stderr)
        return 1
    print(f"T13 safe summary: {EVIDENCE_DIR / 'launcher-summary.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
