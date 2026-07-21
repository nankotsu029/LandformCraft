# V2-13-04 FAWE 1024×1024 placement measurement runbook

Release 2 **1024×1024** real-world placement measurement (2 pass + Recovery drill) for
**Paper 1.21.11 + FastAsyncWorldEdit-Paper 2.15.2 only** (WorldEdit plugin forbidden). Same real-host
methodology as [V2-11-05](v2-11-05-fawe-1000-measurement-runbook.md) and
[V2-13-01](v2-13-01-stage-instrumentation-runbook.md); the only differences are the dimensions
(1024×1024, `DIM=1023`) and an explicit failure/recovery drill after the two passes.

- Audit: [v2-13-04-fawe-1024-measurement.md](../design-v2/audits/v2-13-04-fawe-1024-measurement.md)
- Evidence: [v2-13-04-fawe-1024-measurement-evidence.md](../design-v2/audits/v2-13-04-fawe-1024-measurement-evidence.md)
- Runner: `scripts/measure/v2-13-04-fawe-run.sh`
- Per-stage emitter (offline, testable): `scripts/measure/v2-13-01-stage-durations.py`

This Task is **real-machine dependent**. Without a dedicated high-memory measurement host it is
`BLOCKED_EXTERNAL`: the harness (fixture builder, runner, emitter, this runbook) is committed and
unit/offline verified, but the per-stage seconds and 2-pass + Recovery evidence must be produced on
the isolated measurement host and committed via the human review step. Do **not** fill in numbers
from mocks, another build, a different FAWE version, or a shared/contended host.

## 0. Preconditions (identical to V2-11-05 / V2-13-01)

- Dedicated high-memory host, free RAM for Xmx 8G + forceloaded 1024×1024 (64×64 = 4096 chunks).
- Java 21.
- `run-fawe/paper-1.21.11-132.jar` and `run-fawe/plugins/FastAsyncWorldEdit-Paper-2.15.2.jar`.
- No WorldEdit jar in `run-fawe/plugins/`.
- Measurement profile written by the runner (never raise the catalog dimension by hand).

```bash
./gradlew shadowJar
ls run-fawe/plugins/FastAsyncWorldEdit*.jar
test ! -e run-fawe/plugins/worldedit*.jar
```

## 1. Run

```bash
LANDFORMCRAFT_V21304_XMX=8G LANDFORMCRAFT_V21304_XMS=2G \
  bash scripts/measure/v2-13-04-fawe-run.sh
```

The runner:

1. Exports the 1024×1024 solid-surface Release fixture via
   `V21304MeasurementFixtureExporterTest` (`MeasurementSurfaceFixtureV2.build1024`, 64 tiles,
   tile size 128, cell count = `MEDIUM_MAXIMUM_CELLS` = 1024²).
2. Boots a standalone `java -jar paper-1.21.11-132.jar` in `run-fawe/` (RCON 25576) — not Gradle
   `runServer`.
3. Enables the measurement profile (ceiling 1024×1024); asserts the WorldEdit plugin is absent.
4. Stone-seals the world and keep-loads the envelope in ≤256-chunk strips.
5. Pass1 (cold) / Pass2 (warm): `plan → confirm → snapshot-all → containment → apply → settle →
   full effect-envelope verify → Undo`, recording per-stage wall-clock marks each pass.
6. Waits for durable journal `SNAPSHOT_COMPLETE` (never the transient `SNAPSHOTTING`).
7. Restarts Paper between passes and wipes the placement store safely (never while the JVM runs).
8. Recovery drill: `plan → SIGKILL before confirm → restart → doctor / v2 status`.
9. Emits committed per-stage evidence via `v2-13-01-stage-durations.py`.

## 2. forceload note

Paper 1.21 caps a single `forceload add` at **256 chunks**. 1024×1024 (64×64 = 4096 chunks) cannot be
forceloaded in one call. The runner keep-loads 64-block (4-chunk) wide × full-Z (64-chunk) strips =
256 chunks each, exactly at the cap. If a host rejects the boundary value, narrow the strip to
48 blocks (3 chunks → 192 chunks) — this does not change the measured lifecycle.

## 3. Stage boundaries

| Stage label | From mark | To mark |
|---|---|---|
| `PLACEMENT_STAGE_DURATION_PLAN` | `PLAN_ISSUED` | `CONFIRMATION_ISSUED` |
| `PLACEMENT_STAGE_DURATION_SNAPSHOT` | `CONFIRM_SUBMITTED` | `SNAPSHOT_COMPLETE` |
| `PLACEMENT_STAGE_DURATION_APPLY` | `EXECUTE_SUBMITTED` | `SETTLING` |
| `PLACEMENT_STAGE_DURATION_SETTLE` | `SETTLING` | `VERIFYING` |
| `PLACEMENT_STAGE_DURATION_VERIFY` | `VERIFYING` | `APPLIED` |
| `PLACEMENT_STAGE_DURATION_UNDO` | `UNDO_SUBMITTED` | `UNDONE` |

Precision is bounded by the 3 s journal poll interval; report it with the evidence. Also collect the
V2-11-05 fields: cold/warm, median/max, per-stage duration, allocation/GC, heap/RSS, disk read/write,
loaded-chunk peak, FAWE queue depth, snapshot bytes, mutation count, effect-envelope count. Telemetry
stays PID-only; never record raw paths or secrets.

## 4. Verify + commit

1. Confirm each pass `stage-durations.json` validates against
   `operational-metrics-snapshot-v2.schema.json`.
2. Feed the six seconds into `PlacementStageDurationAnalysisV2` (Java) for the authoritative
   canonical checksum and bottleneck; it must agree with the emitter's `bottleneck_stage`.
3. Copy the two passes' `stage-durations.{json,txt}`, the Recovery-drill observations, and the
   aggregated metrics into the audit evidence file, and have a human confirm the numbers before the
   audit is marked COMPLETE.

## 5. Non-claims

- **No catalog promotion** and **no `PlacementDimensionLimitV2` change** — catalog promotion of the
  measured dimension is a separate approval Task **after** V2-13-04 completes.
- No pre-declared SLO (e.g. "under 10 minutes"); durations are host-specific wall-clock decomposition.
- Measurement changes no generation/placement checksum.
- WorldEdit-standalone runtime is out of Scope for this Task.
