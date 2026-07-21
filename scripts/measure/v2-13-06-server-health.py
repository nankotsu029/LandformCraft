#!/usr/bin/env python3
"""Poll Paper's read-only MSPT/TPS commands during V2-13-06 calibration.

Raw command output is retained because Paper output formatting is runtime-owned. The parser only
extracts slash-delimited millisecond triples and never fabricates a value when the runtime format
is unknown.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path


FORMAT_CODE = re.compile(r"(?:\x1b\[[0-9;]*m|§.)")
MSPT_TRIPLE = re.compile(
    r"(?<![A-Za-z0-9_.-])"
    r"([0-9]+(?:\.[0-9]+)?)\s*/\s*"
    r"([0-9]+(?:\.[0-9]+)?)\s*/\s*"
    r"([0-9]+(?:\.[0-9]+)?)(?![A-Za-z0-9_.-])"
)


def command(rcon: Path, password: str, port: int, value: str) -> tuple[int, str]:
    try:
        completed = subprocess.run(
            [
                sys.executable,
                str(rcon),
                "--password",
                password,
                "--port",
                str(port),
                "--timeout",
                "10",
                value,
            ],
            check=False,
            capture_output=True,
            text=True,
            timeout=15,
        )
        output = (completed.stdout or "") + (completed.stderr or "")
        return completed.returncode, FORMAT_CODE.sub("", output).strip()
    except (OSError, subprocess.TimeoutExpired) as exc:
        return 127, f"probe-failed: {exc}"


def parse_mspt(output: str) -> tuple[list[float], list[float]]:
    values: list[float] = []
    averages: list[float] = []
    for average, minimum, maximum in MSPT_TRIPLE.findall(output):
        triple = [float(average), float(minimum), float(maximum)]
        values.extend(triple)
        averages.append(triple[0])
    return values, averages


def write_snapshot(path: Path, samples: list[dict]) -> None:
    successful = [sample for sample in samples if sample["mspt_return_code"] == 0]
    max_tick = max(
        (max(sample["mspt_values_ms"]) for sample in successful if sample["mspt_values_ms"]),
        default=None,
    )
    max_average = max(
        (max(sample["mspt_window_averages_ms"])
         for sample in successful if sample["mspt_window_averages_ms"]),
        default=None,
    )
    body = {
        "contractVersion": "v2-13-06-paper-health-sample-v1",
        "successfulMsptSamples": len(successful),
        "maximumObservedTickMillis": max_tick,
        "maximumObservedWindowAverageMspt": max_average,
        "samples": samples,
    }
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(body, indent=2) + "\n")
    temporary.replace(path)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rcon", type=Path, required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--stop-file", type=Path, required=True)
    parser.add_argument("--interval-seconds", type=float, default=10.0)
    args = parser.parse_args()

    args.out.parent.mkdir(parents=True, exist_ok=True)
    samples: list[dict] = []
    while not args.stop_file.exists():
        mspt_code, mspt_output = command(args.rcon, args.password, args.port, "mspt")
        tps_code, tps_output = command(args.rcon, args.password, args.port, "tps")
        values, averages = parse_mspt(mspt_output) if mspt_code == 0 else ([], [])
        samples.append({
            "capturedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "mspt_return_code": mspt_code,
            "mspt_output": mspt_output,
            "mspt_values_ms": values,
            "mspt_window_averages_ms": averages,
            "tps_return_code": tps_code,
            "tps_output": tps_output,
        })
        write_snapshot(args.out, samples)
        time.sleep(args.interval_seconds)
    write_snapshot(args.out, samples)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
