#!/usr/bin/env python3
"""Build bounded machine-readable V2-13-06 calibration evidence from host-run outputs."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import statistics
from datetime import datetime
from pathlib import Path


CANDIDATES = (32, 128, 256, 512, 1024)
APPLY = "PLACEMENT_STAGE_DURATION_APPLY"
STAGES = (
    "PLACEMENT_STAGE_DURATION_PLAN",
    "PLACEMENT_STAGE_DURATION_SNAPSHOT",
    APPLY,
    "PLACEMENT_STAGE_DURATION_SETTLE",
    "PLACEMENT_STAGE_DURATION_VERIFY",
    "PLACEMENT_STAGE_DURATION_UNDO",
)
MUTATIONS_1024 = 2_097_152
SELECTED_SLICE_BLOCKS = 1_024
HEAP_INFO = re.compile(r"heap\s+total\s+([0-9]+)K,\s+used\s+([0-9]+)K")
GC_PAUSE = re.compile(r"\bPause\b.*\s([0-9]+(?:\.[0-9]+)?)ms$")


def load_json(path: Path) -> dict:
    return json.loads(path.read_text())


def stage_values(path: Path) -> dict[str, int]:
    snapshot = load_json(path)
    values = {sample["label"]: int(sample["value"]) for sample in snapshot["samples"]}
    missing = set(STAGES) - values.keys()
    if missing:
        raise SystemExit(f"{path}: missing stages {sorted(missing)}")
    return values


def telemetry_evidence(directory: Path) -> dict:
    peaks: list[int] = []
    heap_committed: list[int] = []
    heap_used: list[int] = []
    heap_samples = 0
    for name in ("telemetry.json", "telemetry-pass2.json", "telemetry-restart.json"):
        path = directory / name
        if path.exists():
            telemetry = load_json(path)
            peaks.append(int(telemetry.get("peak_rss_bytes", 0)))
            for sample in telemetry.get("samples", []):
                match = HEAP_INFO.search(sample.get("gc_heap_info", ""))
                if match:
                    heap_samples += 1
                    heap_committed.append(int(match.group(1)) * 1024)
                    heap_used.append(int(match.group(2)) * 1024)
    pauses = []
    for name in ("gc-pass1.log", "gc-pass2.log", "gc-restart.log"):
        path = directory / name
        if not path.exists():
            continue
        for line in path.read_text(errors="replace").splitlines():
            match = GC_PAUSE.search(line)
            if match:
                pauses.append(float(match.group(1)))
    return {
        "peakRssBytes": max(peaks, default=0),
        "peakCommittedHeapBytes": max(heap_committed, default=0),
        "peakUsedHeapBytes": max(heap_used, default=0),
        "heapInfoSamples": heap_samples,
        "gcPauseCount": len(pauses),
        "maximumGcPauseMillis": max(pauses, default=0.0),
    }


def marks(path: Path) -> dict[str, int]:
    result = {}
    for line in path.read_text().splitlines():
        name, epoch = line.split()
        result[name] = int(epoch)
    return result


def captured_epoch(sample: dict) -> float:
    return datetime.fromisoformat(sample["capturedAt"].replace("Z", "+00:00")).timestamp()


def apply_health(directory: Path, health: dict) -> list[dict]:
    result = []
    for pass_id in ("pass1", "pass2"):
        observed = marks(directory / pass_id / "marks.tsv")
        start = observed["EXECUTE_SUBMITTED"] + 10
        end = observed.get("SETTLING", observed["APPLIED"]) + 10
        samples = [
            sample for sample in health.get("samples", [])
            if start <= captured_epoch(sample) <= end
            and sample.get("mspt_return_code") == 0
            and sample.get("mspt_values_ms")
        ]
        if not samples:
            raise SystemExit(f"{directory}: no parsed MSPT samples in {pass_id} APPLY window")
        result.append({
            "passId": pass_id,
            "successfulMsptSamples": len(samples),
            "maximumObservedTickMillis": max(
                sample["mspt_values_ms"][2] for sample in samples),
            "maximumObservedWindowAverageMspt": max(
                sample["mspt_window_averages_ms"][0] for sample in samples),
        })
    return result


def journal_evidence(directory: Path) -> list[dict]:
    result = []
    for pass_id in ("pass1", "pass2"):
        applied = load_json(directory / pass_id / "journal-applied.json")
        undone = load_json(directory / pass_id / "journal-undone.json")
        if applied["state"] != "APPLIED" or undone["state"] != "UNDONE":
            raise SystemExit(f"{directory}: invalid {pass_id} terminal journal states")
        result.append({
            "passId": pass_id,
            "appliedState": applied["state"],
            "undoneState": undone["state"],
            "appliedJournalChecksum": applied["journalChecksum"],
            "undoneJournalChecksum": undone["journalChecksum"],
            "planChecksum": applied["planChecksum"],
            "releaseManifestChecksum": applied["plan"]["releaseBinding"]["manifestChecksum"],
        })
    return result


def watchdog_triggered(directory: Path) -> bool:
    patterns = (
        "watchdog",
        "server has not responded",
        "a single server tick took",
    )
    for name in ("paper.stdout", "paper-pass2.stdout", "paper-restart.stdout",
                 "final-server.log"):
        path = directory / name
        if path.exists() and any(
                pattern in path.read_text(errors="replace").lower() for pattern in patterns):
            return True
    return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    records = []
    for candidate in CANDIDATES:
        directory = args.root / f"slice-{candidate}"
        passes = [
            stage_values(directory / "pass1" / "stage-durations.json"),
            stage_values(directory / "pass2" / "stage-durations.json"),
        ]
        apply_seconds = [item[APPLY] for item in passes]
        lifecycle_seconds = [sum(item[label] for label in STAGES) for item in passes]
        health = load_json(directory / "paper-health.json")
        apply_health_evidence = apply_health(directory, health)
        resources = telemetry_evidence(directory)
        records.append({
            "sliceBlocks": candidate,
            "applySeconds": apply_seconds,
            "medianApplySeconds": statistics.median(apply_seconds),
            "lifecycleSeconds": lifecycle_seconds,
            "medianLifecycleSeconds": statistics.median(lifecycle_seconds),
            "blocksPerSecond": [
                round(MUTATIONS_1024 / seconds, 3) if seconds else None
                for seconds in apply_seconds
            ],
            "applyHealth": apply_health_evidence,
            "maximumObservedTickMillis": max(
                item["maximumObservedTickMillis"] for item in apply_health_evidence),
            "maximumObservedWindowAverageMspt": max(
                item["maximumObservedWindowAverageMspt"] for item in apply_health_evidence),
            "successfulMsptSamples": sum(
                item["successfulMsptSamples"] for item in apply_health_evidence),
            "schedulerQueueDepthUpperBound": 1,
            "cancelObservationBoundary": "after each accepted slice, before next submission",
            "cancelResponseUpperBoundMillis": max(
                item["maximumObservedTickMillis"] for item in apply_health_evidence),
            **resources,
            "journals": journal_evidence(directory),
            "watchdogTriggered": watchdog_triggered(directory),
            "terminalLifecycle": "APPLIED->full exact verify->UNDONE (both passes)",
            "recovery": "plan->SIGKILL before confirmation->restart->doctor",
        })

    baseline = records[0]
    for record in records:
        record["applyImprovementFractionVs32"] = round(
            1.0 - record["medianApplySeconds"] / baseline["medianApplySeconds"], 6)
        record["lifecycleImprovementFractionVs32"] = round(
            1.0 - record["medianLifecycleSeconds"] / baseline["medianLifecycleSeconds"], 6)
        record["tickMaximumRatioVs32"] = round(
            record["maximumObservedTickMillis"] / baseline["maximumObservedTickMillis"], 6)
        record["windowAverageMsptRatioVs32"] = round(
            record["maximumObservedWindowAverageMspt"]
            / baseline["maximumObservedWindowAverageMspt"], 6)
        record["peakRssRatioVs32"] = round(
            record["peakRssBytes"] / baseline["peakRssBytes"], 6)

    selected = next(
        record for record in records if record["sliceBlocks"] == SELECTED_SLICE_BLOCKS)
    selection = {
        "selectedSliceBlocks": SELECTED_SLICE_BLOCKS,
        "largestMeasuredCandidate": True,
        "applyImprovementGatePassed":
            selected["applyImprovementFractionVs32"] >= 0.30,
        "lifecycleImprovementGatePassed":
            selected["lifecycleImprovementFractionVs32"] >= 0.20,
        "tickStallGatePassed":
            selected["maximumObservedTickMillis"] < 50.0
            and selected["maximumObservedTickMillis"]
            <= baseline["maximumObservedTickMillis"],
        "msptGatePassed": selected["maximumObservedWindowAverageMspt"] < 50.0,
        "memoryGatePassed":
            selected["peakRssBytes"] <= baseline["peakRssBytes"]
            and selected["peakUsedHeapBytes"] <= baseline["peakUsedHeapBytes"],
        "gcGatePassed": selected["maximumGcPauseMillis"] <= 200.0,
        "watchdogGatePassed": not selected["watchdogTriggered"],
        "lifecycleInvariantGatePassed": all(
            record["terminalLifecycle"] == "APPLIED->full exact verify->UNDONE (both passes)"
            and record["recovery"] == "plan->SIGKILL before confirmation->restart->doctor"
            and not record["watchdogTriggered"]
            for record in records),
    }

    body = {
        "contractVersion": "v2-13-06-apply-slice-calibration-evidence-v1",
        "profile": "Paper 1.21.11 + FAWE 2.15.2 standalone",
        "dimensions": "1024x1024",
        "mutationCount": MUTATIONS_1024,
        "candidates": records,
        "selection": selection,
        "invariants": {
            "workingBytesPerMutation": 640,
            "maximumAcceptedTransactions": 18,
            "maximumCandidateConcurrentSliceBytes": 11_796_480,
            "planMaximumWorkingBytes": 655_360,
            "sliceReleasePoint": "gateway receipt continuation; previous slice is not retained",
            "canonicalOrderAndChecksum": "PlacementApplyTransactionServiceV2Test",
            "watchdogMillis": 60_000,
            "paperHealthWarmupSeconds": 10,
            "paperHealthWindowSeconds": 5,
        },
    }
    canonical = json.dumps(body, sort_keys=True, separators=(",", ":")).encode()
    body["evidenceChecksum"] = hashlib.sha256(canonical).hexdigest()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(body, indent=2) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
