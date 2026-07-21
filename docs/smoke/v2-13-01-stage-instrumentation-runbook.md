# V2-13-01 placement stage instrumentation + 1000×1000 baseline runbook

Decomposes the V2-11-05 total placement wall time (~106 min/pass) into **six per-stage
durations** (PLAN / SNAPSHOT / APPLY / SETTLE / VERIFY / UNDO) on the same real-host
methodology, and emits committed per-stage evidence so the largest bottleneck stage is named.

- Audit: [v2-13-01-stage-instrumentation.md](../design-v2/audits/v2-13-01-stage-instrumentation.md)
- Runner: `scripts/measure/v2-13-01-stage-instrumentation-run.sh`
- Evidence emitter (offline, testable): `scripts/measure/v2-13-01-stage-durations.py`
- Committed example shape: [placement-stage-durations-v2.json](../../examples/v2/operations/placement-stage-durations-v2.json)

This Task is **real-machine dependent**. Without a dedicated high-memory measurement host it is
`BLOCKED_EXTERNAL`: the instrumentation (labels, emitter, runner) is committed and unit-verified,
but the per-stage seconds themselves must be produced on the measurement host and committed via the
human review step. Do not fill in stage seconds from mocks, other builds, or a different FAWE
version.

## 0. Preconditions (identical to V2-11-05)

- Dedicated high-memory host, free RAM for Xmx 8G + forceloaded ~3969 chunks
- Java 21
- `run-fawe/paper-1.21.11-132.jar` and `run-fawe/plugins/FastAsyncWorldEdit-Paper-2.15.2.jar`
- No WorldEdit jar in `run-fawe/plugins/`
- Measurement profile written by the runner (never raise the catalog dimension by hand)

```bash
./gradlew shadowJar
ls run-fawe/plugins/FastAsyncWorldEdit*.jar
test ! -e run-fawe/plugins/worldedit*.jar
```

## 1. Run

```bash
LANDFORMCRAFT_V21301_XMX=8G LANDFORMCRAFT_V21301_XMS=2G \
  bash scripts/measure/v2-13-01-stage-instrumentation-run.sh
```

The runner reuses the V2-11-05 lifecycle (stone-seal, ≤256-chunk keep-load strips, journal
`SNAPSHOT_COMPLETE` completion gate, cold pass1 / warm pass2) and adds only **stage-mark capture**:
it records a wall-clock epoch the first time each observable journal transition is seen, writing
`marks.tsv` per pass, then calls the emitter.

Stage boundaries (each stage = work between a submit and its terminal state; idle operator gaps are
excluded):

| Stage label | From mark | To mark |
|---|---|---|
| `PLACEMENT_STAGE_DURATION_PLAN` | `PLAN_ISSUED` | `CONFIRMATION_ISSUED` |
| `PLACEMENT_STAGE_DURATION_SNAPSHOT` | `CONFIRM_SUBMITTED` | `SNAPSHOT_COMPLETE` |
| `PLACEMENT_STAGE_DURATION_APPLY` | `EXECUTE_SUBMITTED` | `SETTLING` |
| `PLACEMENT_STAGE_DURATION_SETTLE` | `SETTLING` | `VERIFYING` |
| `PLACEMENT_STAGE_DURATION_VERIFY` | `VERIFYING` | `APPLIED` |
| `PLACEMENT_STAGE_DURATION_UNDO` | `UNDO_SUBMITTED` | `UNDONE` |

Precision is bounded by the 3 s journal poll interval; report it with the evidence.

## 2. Outputs (committed evidence)

Per pass, under `build/measure/v2-13-01/evidence/fawe/pass{1,2}/`:

- `stage-durations.json` — conforms to `operational-metrics-snapshot-v2.schema.json` with six
  SECONDS samples (the closed `PLACEMENT_STAGE_DURATION_*` labels).
- `stage-durations.txt` — human summary ending in `bottleneck_stage=…` / `bottleneck_seconds=…`
  / `bottleneck_fraction=…`.

Also collect (per the Task Scope, alongside the V2-11-05 fields): cold/warm, median/max, per-stage
duration, allocation/GC, heap/RSS, disk read/write, loaded-chunk peak, FAWE queue depth, snapshot
bytes, mutation count, effect-envelope count. Telemetry stays PID-only; never record raw paths or
secrets.

## 3. Verify + commit

1. Confirm `stage-durations.json` validates against `operational-metrics-snapshot-v2.schema.json`.
2. Feed the six seconds into `PlacementStageDurationAnalysisV2` (Java) to obtain the authoritative
   canonical checksum and bottleneck; it must agree with the emitter's `bottleneck_stage`.
3. Copy the two passes' `stage-durations.{json,txt}` and the aggregated metrics into the audit
   evidence file and have a human confirm the numbers before the audit is marked COMPLETE.

## 4. Non-claims

- No catalog promotion, no dimension-limit change, no safety-order or verify-scope change.
- Stage durations are wall-clock decomposition only; they do not change any generation checksum.
- WorldEdit-standalone runtime is out of Scope for this Task.
