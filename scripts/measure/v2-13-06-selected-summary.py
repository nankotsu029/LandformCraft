#!/usr/bin/env python3
"""Build bounded evidence for the selected-slice 1000/1024 two-pass re-measurement."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import datetime
from pathlib import Path


STAGES = (
    "PLACEMENT_STAGE_DURATION_PLAN",
    "PLACEMENT_STAGE_DURATION_SNAPSHOT",
    "PLACEMENT_STAGE_DURATION_APPLY",
    "PLACEMENT_STAGE_DURATION_SETTLE",
    "PLACEMENT_STAGE_DURATION_VERIFY",
    "PLACEMENT_STAGE_DURATION_UNDO",
)
BASELINES = {
    "1000x1000": {"applySeconds": [1571, 1572], "lifecycleSeconds": [1975, 1975]},
    "1024x1024": {"applySeconds": [1647, 1648], "lifecycleSeconds": [2072, 2072]},
}
MUTATIONS = {"1000x1000": 2_000_000, "1024x1024": 2_097_152}
HEAP_INFO = re.compile(r"heap\s+total\s+([0-9]+)K,\s+used\s+([0-9]+)K")
GC_PAUSE = re.compile(r"\bPause\b.*\s([0-9]+(?:\.[0-9]+)?)ms$")


def load_json(path: Path) -> dict:
    return json.loads(path.read_text())


def stages(path: Path) -> dict[str, int]:
    values = {
        sample["label"]: int(sample["value"])
        for sample in load_json(path)["samples"]
    }
    missing = set(STAGES) - values.keys()
    if missing:
        raise SystemExit(f"{path}: missing stage values {sorted(missing)}")
    return values


def marks(path: Path) -> dict[str, int]:
    return {
        name: int(epoch)
        for name, epoch in (line.split() for line in path.read_text().splitlines())
    }


def captured_epoch(sample: dict) -> float:
    return datetime.fromisoformat(sample["capturedAt"].replace("Z", "+00:00")).timestamp()


def health(directory: Path) -> dict:
    samples = load_json(directory / "paper-health.json").get("samples", [])
    passes = []
    for pass_id in ("pass1", "pass2"):
        observed = marks(directory / pass_id / "marks.tsv")
        start = observed["EXECUTE_SUBMITTED"] + 10
        end = observed.get("SETTLING", observed["APPLIED"]) + 10
        selected = [
            sample for sample in samples
            if start <= captured_epoch(sample) <= end
            and sample.get("mspt_return_code") == 0
            and sample.get("mspt_values_ms")
        ]
        if not selected:
            return {
                "available": False,
                "passes": [],
                "unavailableReason": "RCON sampler returned no successful APPLY-window samples",
            }
        passes.append({
            "passId": pass_id,
            "successfulMsptSamples": len(selected),
            "maximumObservedTickMillis": max(
                sample["mspt_values_ms"][2] for sample in selected),
            "maximumObservedWindowAverageMspt": max(
                sample["mspt_window_averages_ms"][0] for sample in selected),
        })
    return {
        "available": True,
        "passes": passes,
        "maximumObservedTickMillis": max(
            item["maximumObservedTickMillis"] for item in passes),
        "maximumObservedWindowAverageMspt": max(
            item["maximumObservedWindowAverageMspt"] for item in passes),
    }


def resources(directory: Path) -> dict:
    rss = []
    committed = []
    used = []
    heap_samples = 0
    for name in ("telemetry.json", "telemetry-pass2.json", "telemetry-restart.json"):
        path = directory / name
        if not path.exists():
            continue
        telemetry = load_json(path)
        rss.append(int(telemetry.get("peak_rss_bytes", 0)))
        for sample in telemetry.get("samples", []):
            match = HEAP_INFO.search(sample.get("gc_heap_info", ""))
            if match:
                heap_samples += 1
                committed.append(int(match.group(1)) * 1024)
                used.append(int(match.group(2)) * 1024)
    pauses = []
    for name in ("gc-pass1.log", "gc-pass2.log", "gc-restart.log"):
        path = directory / name
        if path.exists():
            for line in path.read_text(errors="replace").splitlines():
                match = GC_PAUSE.search(line)
                if match:
                    pauses.append(float(match.group(1)))
    return {
        "peakRssBytes": max(rss, default=0),
        "peakCommittedHeapBytes": max(committed, default=0),
        "peakUsedHeapBytes": max(used, default=0),
        "heapInfoSamples": heap_samples,
        "gcPauseCount": len(pauses),
        "maximumGcPauseMillis": max(pauses, default=0.0),
    }


def terminal_journals(directory: Path) -> list[dict]:
    result = []
    for pass_id in ("pass1", "pass2"):
        applied = load_json(directory / pass_id / "journal-applied.json")
        undone = load_json(directory / pass_id / "journal-undone.json")
        if applied["state"] != "APPLIED" or undone["state"] != "UNDONE":
            raise SystemExit(f"{directory}: invalid journal states for {pass_id}")
        result.append({
            "passId": pass_id,
            "appliedState": applied["state"],
            "undoneState": undone["state"],
            "planChecksum": applied["planChecksum"],
            "appliedJournalChecksum": applied["journalChecksum"],
            "undoneJournalChecksum": undone["journalChecksum"],
        })
    return result


def watchdog_triggered(directory: Path) -> bool:
    patterns = ("watchdog", "server has not responded", "a single server tick took")
    for name in ("paper.stdout", "paper-pass2.stdout", "paper-restart.stdout",
                 "final-server.log"):
        path = directory / name
        if path.exists():
            content = path.read_text(errors="replace").lower()
            if any(pattern in content for pattern in patterns):
                return True
    return False


def summary_value(path: Path, key: str) -> str:
    prefix = f"{key}="
    for line in path.read_text().splitlines():
        if line.startswith(prefix):
            return line.removeprefix(prefix)
    raise SystemExit(f"{path}: missing {key}")


def dimension_record(root: Path, dimension: str) -> dict:
    directory = root / dimension
    snapshots = [
        stages(directory / pass_id / "stage-durations.json")
        for pass_id in ("pass1", "pass2")
    ]
    apply_seconds = [item[STAGES[2]] for item in snapshots]
    lifecycle_seconds = [sum(item[label] for label in STAGES) for item in snapshots]
    baseline = BASELINES[dimension]
    measured_health = health(directory)
    return {
        "dimensions": dimension,
        "mutationCount": MUTATIONS[dimension],
        "applySeconds": apply_seconds,
        "lifecycleSeconds": lifecycle_seconds,
        "blocksPerSecond": [round(MUTATIONS[dimension] / value, 3) for value in apply_seconds],
        "baseline32": baseline,
        "applyImprovementFraction": round(
            1 - (sum(apply_seconds) / 2) / (sum(baseline["applySeconds"]) / 2), 6),
        "lifecycleImprovementFraction": round(
            1 - (sum(lifecycle_seconds) / 2)
            / (sum(baseline["lifecycleSeconds"]) / 2), 6),
        "health": measured_health,
        **resources(directory),
        "schedulerQueueDepthUpperBound": 1,
        "cancelResponseUpperBoundMillis": (
            measured_health["maximumObservedTickMillis"]
            if measured_health["available"] else None),
        "journals": terminal_journals(directory),
        "watchdogTriggered": watchdog_triggered(directory),
        "manifestChecksum": summary_value(directory / "summary.txt", "manifest_sha256"),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--calibration", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    dimensions = [dimension_record(args.root, value) for value in BASELINES]
    calibration = load_json(args.calibration)
    calibration_selected = next(
        item for item in calibration["candidates"] if item["sliceBlocks"] == 1024)
    jar_checksum = summary_value(args.root / "summary.txt", "build_jar_sha256")
    body = {
        "contractVersion": "v2-13-06-selected-slice-evidence-v1",
        "selectedSliceBlocks": 1024,
        "profile": "Paper 1.21.11 + FAWE 2.15.2 standalone",
        "buildJarChecksum": jar_checksum,
        "dimensions": dimensions,
        "calibrationSafetyEvidence": {
            "source": str(args.calibration),
            "evidenceChecksum": calibration["evidenceChecksum"],
            "successfulMsptSamples": calibration_selected["successfulMsptSamples"],
            "maximumObservedTickMillis": calibration_selected["maximumObservedTickMillis"],
            "maximumObservedWindowAverageMspt":
                calibration_selected["maximumObservedWindowAverageMspt"],
            "cancelResponseUpperBoundMillis":
                calibration_selected["cancelResponseUpperBoundMillis"],
            "peakRssBytes": calibration_selected["peakRssBytes"],
            "peakUsedHeapBytes": calibration_selected["peakUsedHeapBytes"],
            "maximumGcPauseMillis": calibration_selected["maximumGcPauseMillis"],
            "watchdogTriggered": calibration_selected["watchdogTriggered"],
        },
        "gates": {
            "applyImprovementGatePassed": all(
                item["applyImprovementFraction"] >= 0.30 for item in dimensions),
            "lifecycleImprovementGatePassed": all(
                item["lifecycleImprovementFraction"] >= 0.20 for item in dimensions),
            "tickAndMsptGatePassed":
                calibration_selected["maximumObservedTickMillis"] < 50.0
                and calibration_selected["maximumObservedWindowAverageMspt"] < 50.0,
            "gcGatePassed": all(item["maximumGcPauseMillis"] <= 200.0 for item in dimensions),
            "watchdogGatePassed": not any(item["watchdogTriggered"] for item in dimensions),
            "terminalJournalGatePassed": all(
                all(journal["appliedState"] == "APPLIED"
                    and journal["undoneState"] == "UNDONE"
                    for journal in item["journals"])
                for item in dimensions),
            "recovery": "1024x1024 plan->SIGKILL before confirmation->restart->doctor",
        },
    }
    canonical = json.dumps(body, sort_keys=True, separators=(",", ":")).encode()
    body["evidenceChecksum"] = hashlib.sha256(canonical).hexdigest()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(body, indent=2) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
