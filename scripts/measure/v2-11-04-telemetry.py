#!/usr/bin/env python3
"""Sample heap / RSS / GC of a single Paper server PID for V2-11-04."""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import time
from pathlib import Path


def read_rss_bytes(pid: int) -> int | None:
    status = Path(f"/proc/{pid}/status")
    if not status.exists():
        return None
    for line in status.read_text().splitlines():
        if line.startswith("VmRSS:"):
            parts = line.split()
            return int(parts[1]) * 1024
    return None


def jcmd(pid: int, *args: str) -> str:
    try:
        completed = subprocess.run(
            ["jcmd", str(pid), *args],
            check=False,
            capture_output=True,
            text=True,
            timeout=15,
        )
        return (completed.stdout or "") + (completed.stderr or "")
    except (OSError, subprocess.TimeoutExpired) as exc:
        return f"jcmd-failed: {exc}"


def sample(pid: int) -> dict:
    rss = read_rss_bytes(pid)
    gc = jcmd(pid, "GC.heap_info")
    vm = jcmd(pid, "VM.native_memory", "summary")
    return {
        "ts": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "pid": pid,
        "rss_bytes": rss,
        "gc_heap_info": gc.strip(),
        "native_memory_summary_head": "\n".join(vm.strip().splitlines()[:40]),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pid", type=int, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--interval-seconds", type=float, default=15.0)
    parser.add_argument("--once", action="store_true")
    args = parser.parse_args()

    args.out.parent.mkdir(parents=True, exist_ok=True)
    samples: list[dict] = []
    peak_rss = 0
    while True:
        if not Path(f"/proc/{args.pid}").exists():
            break
        point = sample(args.pid)
        samples.append(point)
        if point["rss_bytes"]:
            peak_rss = max(peak_rss, point["rss_bytes"])
        args.out.write_text(
            json.dumps(
                {
                    "pid": args.pid,
                    "peak_rss_bytes": peak_rss,
                    "samples": samples,
                },
                indent=2,
            )
            + "\n"
        )
        if args.once:
            break
        time.sleep(args.interval_seconds)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
