#!/usr/bin/env python3
"""Publish bounded ShopSphere Docker telemetry; never request environment values."""
import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time

PROJECT = "shopsphere-prod"


def docker(*args):
    return subprocess.check_output(["docker", *args], text=True, timeout=20)


def memory_bytes(value):
    match = re.fullmatch(r"([0-9.]+)\s*(B|kB|KB|KiB|MB|MiB|GB|GiB|TB|TiB)", value.strip())
    if not match:
        raise ValueError("Unsupported Docker memory unit")
    units = {"B": 1, "kB": 1000, "KB": 1000, "KiB": 1024,
             "MB": 1000**2, "MiB": 1024**2, "GB": 1000**3,
             "GiB": 1024**3, "TB": 1000**4, "TiB": 1024**4}
    return float(match[1]) * units[match[2]]


def metric(name, value, container=None):
    label = "" if container is None else '{name=' + json.dumps(container) + '}'
    return f"{name}{label} {value}\n"


def collect():
    ids = docker("ps", "--all", "--quiet", "--filter",
                 f"label=com.docker.compose.project={PROJECT}").split()
    if not ids or len(ids) > 40 or any(not re.fullmatch(r"[0-9a-f]{12,64}", item) for item in ids):
        raise RuntimeError("Unexpected ShopSphere container inventory")
    template = ('{"name":{{json .Name}},"status":{{json .State.Status}},'
                '"started":{{json .State.StartedAt}},"restarts":{{.RestartCount}},'
                '"healthy":{{if .State.Health}}{{if eq .State.Health.Status "healthy"}}true{{else}}false{{end}}{{else}}false{{end}}}')
    states = [json.loads(line) for line in docker("inspect", "--format", template, *ids).splitlines()]
    stats = {entry["Name"]: entry for entry in (
        json.loads(line) for line in docker("stats", "--no-stream", "--format", "{{json .}}", *ids).splitlines())}
    lines = []
    for state in states:
        name = state["name"].lstrip("/")
        if not name.startswith(PROJECT + "-"):
            raise RuntimeError("Unexpected container name")
        running = state["status"] == "running"
        lines.append(metric("shopsphere_container_healthy", int(running and state["healthy"]), name))
        lines.append(metric("shopsphere_container_restarts_total", state["restarts"], name))
        if running:
            sample = stats[name]
            used, limit = sample["MemUsage"].split("/")
            lines.append(metric("container_memory_working_set_bytes", memory_bytes(used), name))
            lines.append(metric("container_spec_memory_limit_bytes", memory_bytes(limit), name))
            lines.append(metric("shopsphere_container_cpu_percent", float(sample["CPUPerc"].rstrip("%")), name))
            started = datetime.datetime.fromisoformat(state["started"].replace("Z", "+00:00")).timestamp()
            lines.append(metric("container_start_time_seconds", started, name))
    lines.append(metric("shopsphere_container_metrics_timestamp_seconds", time.time()))
    return "".join(lines)


def publish(directory, content):
    directory = Path(directory)
    if not directory.is_dir():
        raise RuntimeError("Metrics directory must be provisioned first")
    fd, temporary = tempfile.mkstemp(prefix=".containers-", dir=directory)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, 0o644)
        os.replace(temporary, directory / "containers.prom")
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


if __name__ == "__main__":
    try:
        publish(sys.argv[1], collect())
    except Exception as exc:
        # Preserve the previous complete file; freshness alert detects failure.
        print(f"Container metrics collection failed ({type(exc).__name__})", file=sys.stderr)
        sys.exit(1)
