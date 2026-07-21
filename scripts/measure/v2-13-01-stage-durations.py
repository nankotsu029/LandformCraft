#!/usr/bin/env python3
"""V2-13-01 placement stage-duration evidence emitter.

Turns a set of observed placement lifecycle transition timestamps into the committed
per-stage duration evidence. This is the offline, deterministic half of the measurement
runner: it does not touch Paper or the world, it only shapes evidence.

Input (``--transitions``): a JSON object

    {
      "passId": "pass1",
      "dimensions": "1000x1000",
      "profile": "fawe-2.15.2",
      "capturedAt": "2026-07-21T00:00:00Z",
      "marks": {
        "PLAN_ISSUED": <epoch_seconds>,
        "CONFIRMATION_ISSUED": <epoch_seconds>,
        "CONFIRM_SUBMITTED": <epoch_seconds>,
        "SNAPSHOT_COMPLETE": <epoch_seconds>,
        "EXECUTE_SUBMITTED": <epoch_seconds>,
        "SETTLING": <epoch_seconds>,
        "VERIFYING": <epoch_seconds>,
        "APPLIED": <epoch_seconds>,
        "UNDO_SUBMITTED": <epoch_seconds>,
        "UNDONE": <epoch_seconds>
      }
    }

Outputs two committed files next to ``--out-json`` / ``--out-txt``:
  * a JSON conforming to ``operational-metrics-snapshot-v2.schema.json`` with six
    SECONDS samples (the six PLACEMENT_STAGE_DURATION_* labels), and
  * a human-readable summary naming the bottleneck stage.

The label vocabulary, stage boundaries and bottleneck tie-break (lifecycle order,
earliest stage wins) match ``PlacementStageDurationAnalysisV2`` exactly.
"""
import argparse
import hashlib
import json
import sys

# Label -> (start_mark, end_mark). Order is the lifecycle order and the bottleneck tie-break order.
STAGES = [
    ("PLACEMENT_STAGE_DURATION_PLAN", "PLAN_ISSUED", "CONFIRMATION_ISSUED"),
    ("PLACEMENT_STAGE_DURATION_SNAPSHOT", "CONFIRM_SUBMITTED", "SNAPSHOT_COMPLETE"),
    ("PLACEMENT_STAGE_DURATION_APPLY", "EXECUTE_SUBMITTED", "SETTLING"),
    ("PLACEMENT_STAGE_DURATION_SETTLE", "SETTLING", "VERIFYING"),
    ("PLACEMENT_STAGE_DURATION_VERIFY", "VERIFYING", "APPLIED"),
    ("PLACEMENT_STAGE_DURATION_UNDO", "UNDO_SUBMITTED", "UNDONE"),
]
REQUIRED_MARKS = sorted({m for _, a, b in STAGES for m in (a, b)})


def stage_seconds(marks):
    result = {}
    for label, start, end in STAGES:
        for mark in (start, end):
            if mark not in marks:
                raise SystemExit(f"missing transition mark: {mark}")
        delta = int(round(marks[end] - marks[start]))
        if delta < 0:
            # Poll granularity can reorder near-simultaneous transitions; clamp, never fabricate.
            delta = 0
        result[label] = delta
    return result


def bottleneck(seconds):
    top_label, best, total = STAGES[0][0], -1, 0
    for label, _, _ in STAGES:
        value = seconds[label]
        total += value
        if value > best:
            best, top_label = value, label
    return top_label, best, total


def build_snapshot(captured_at, seconds):
    # Samples sorted by label name to match the Java canonical normalization.
    samples = [
        {"label": label, "unit": "SECONDS", "value": seconds[label]}
        for label, _, _ in STAGES
    ]
    samples.sort(key=lambda s: s["label"])
    body = {
        "schemaVersion": 1,
        "contractVersion": "operational-metrics-snapshot-v1",
        "capturedAt": captured_at,
        "samples": samples,
    }
    # Evidence-integrity digest over the checksum-free canonical bytes. This locks the emitted
    # file; the authoritative canonical checksum is recomputed by PlacementStageDurationAnalysisV2
    # in Java during verification.
    digest = hashlib.sha256(
        json.dumps(body, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    body["canonicalChecksum"] = digest
    return body


def read_marks_tsv(path):
    """Read a ``NAME<TAB>epoch`` file (last write per name wins) into a marks dict."""
    marks = {}
    with open(path, "r", encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if len(parts) != 2:
                raise SystemExit(f"malformed marks line: {raw!r}")
            marks[parts[0]] = float(parts[1])
    return marks


def main(argv=None):
    parser = argparse.ArgumentParser(description="V2-13-01 stage-duration evidence emitter")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--transitions", help="JSON with a 'marks' object")
    source.add_argument("--marks", help="TSV of 'NAME<TAB>epoch' recorded by the host runner")
    parser.add_argument("--pass-id", default="unknown")
    parser.add_argument("--dimensions", default="unknown")
    parser.add_argument("--profile", default="unknown")
    parser.add_argument("--captured-at", default="1970-01-01T00:00:00Z")
    parser.add_argument("--out-json", required=True)
    parser.add_argument("--out-txt", required=True)
    args = parser.parse_args(argv)

    if args.transitions:
        with open(args.transitions, "r", encoding="utf-8") as handle:
            data = json.load(handle)
        marks = data.get("marks", {})
        captured_at = data.get("capturedAt", args.captured_at)
    else:
        marks = read_marks_tsv(args.marks)
        data = {"passId": args.pass_id, "dimensions": args.dimensions, "profile": args.profile}
        captured_at = args.captured_at
    seconds = stage_seconds(marks)
    top_label, best, total = bottleneck(seconds)

    snapshot = build_snapshot(captured_at, seconds)
    with open(args.out_json, "w", encoding="utf-8") as handle:
        json.dump(snapshot, handle, indent=2)
        handle.write("\n")

    fraction = 0.0 if total == 0 else best / total
    lines = [
        f"passId={data.get('passId', 'unknown')}",
        f"dimensions={data.get('dimensions', 'unknown')}",
        f"profile={data.get('profile', 'unknown')}",
        f"capturedAt={captured_at}",
        "--- stage durations (seconds) ---",
    ]
    for label, _, _ in STAGES:
        lines.append(f"{label}={seconds[label]}")
    lines.append(f"total_stage_seconds={total}")
    lines.append(f"bottleneck_stage={top_label}")
    lines.append(f"bottleneck_seconds={best}")
    lines.append(f"bottleneck_fraction={fraction:.4f}")
    with open(args.out_txt, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")

    print(f"bottleneck={top_label} seconds={best} fraction={fraction:.4f} total={total}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
