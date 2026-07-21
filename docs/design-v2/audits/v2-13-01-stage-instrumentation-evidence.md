# V2-13-01 placement stage instrumentation + 1000×1000 baseline — measured evidence

- Status: **COMPLETE**（2026-07-21）
- See: [audit](v2-13-01-stage-instrumentation.md)、[runbook](../../smoke/v2-13-01-stage-instrumentation-runbook.md)
- Runner: `scripts/measure/v2-13-01-stage-instrumentation-run.sh` + `scripts/measure/v2-13-01-stage-durations.py`
- Machine-readable per-stage evidence (committed): [pass1](v2-13-01-evidence/pass1-stage-durations.json)、[pass2](v2-13-01-evidence/pass2-stage-durations.json)（human: [pass1.txt](v2-13-01-evidence/pass1-stage-durations.txt)、[pass2.txt](v2-13-01-evidence/pass2-stage-durations.txt)）
- Local machine evidence directory (not committed): `build/measure/v2-13-01/evidence/fawe/`

## Machine-verifiable fields

```text
Profile: FAWE 2.15.2 standalone (WorldEdit plugin absent)
Boot: java -jar paper-1.21.11-132.jar in run-fawe/ (no Gradle runServer)
Xmx: 8G / Xms: 2G
Build JAR SHA-256: 1913a1740e6c5a15b9085e4e3f534a8e7a163eba230aa8fb4787889ad74b1e39
Manifest canonical SHA-256: d88db212800a56879c2f6baa35bf3b535c0d326c02bf06980de4b3637fb5a861
Release: v2-11-05-measure-1000 / surface-2_5d / 1000×1000 solid y=0..1 (64 tiles, tile size 128)
Command surface: v2 place plan|confirm|execute, v2 undo plan|execute (r2 route retired in V2-12-06)
Pass1 (cold) placement ID: 1227b3e1-eb2e-43bb-a85b-acd0875d7d23
Pass1 terminal: APPLIED -> full effect-envelope verify -> UNDONE (wall_seconds=2185)
Pass2 (warm) placement ID: 228abba8-23dd-4fab-9cd8-3549d05f91b6
Pass2 terminal: APPLIED -> full effect-envelope verify -> UNDONE (wall_seconds=2184)
Peak RSS (bytes): pass1 5357629440 (~4.99 GiB), pass2 5508845568 (~5.13 GiB); PID-only telemetry
Admission: no DISK_BUDGET_EXCEEDED / DISK_SHORTAGE
Operator / date: agent / 2026-07-21
```

## Per-stage wall-clock durations (seconds)

Stage boundaries are submit→terminal per the runbook; idle/setup gaps (stone-seal, forceload,
inter-stage latency) are excluded, so the six stages sum to the *work* time, not the full pass wall.
Poll granularity 3 s.

| Stage | Pass1 (cold) | Pass2 (warm) | Share (pass1) |
|---|---:|---:|---:|
| `PLACEMENT_STAGE_DURATION_PLAN` | 4 | 4 | 0.2% |
| `PLACEMENT_STAGE_DURATION_SNAPSHOT` (confirm→snapshot-all + containment + Undo baseline) | 199 | 200 | 10.1% |
| **`PLACEMENT_STAGE_DURATION_APPLY`** (canonical block-state stream → FAWE) | **1572** | **1571** | **79.6%** |
| `PLACEMENT_STAGE_DURATION_SETTLE` | 0 | 0 | 0.0% |
| `PLACEMENT_STAGE_DURATION_VERIFY` (full effect-envelope exact verify) | 24 | 24 | 1.2% |
| `PLACEMENT_STAGE_DURATION_UNDO` | 176 | 176 | 8.9% |
| **total stage seconds** | **1975** | **1975** | 100% |

**Bottleneck (both passes): `PLACEMENT_STAGE_DURATION_APPLY` — ~1571–1572 s, ~79.5% of stage time.**
The two passes are within 1 s per stage (cold≈warm). `SETTLE` completed within one poll interval.

## Interpretation

- The dominant stage is **APPLY** — writing the resolved canonical XYZ block-state stream to the
  world through FAWE — not the per-block `Map` construction. `SNAPSHOT` (which builds the
  `PlacementExpectedBlockResolverV2` / Undo baseline maps) is only ~10% and `UNDO` ~9%; the >12 min
  confirm/Undo `Map` stall that V2-11-05 fixed in-Task is gone (SNAPSHOT is now ~200 s).
- **Consequence for `V2-13-05`** (per-block `Map` packing, conditional): its gate — "execute only if
  V2-13-01 confirms `Map`/allocation is the dominant bottleneck" — is **not met**. `Map`-bearing
  stages (SNAPSHOT + UNDO) are ~19% combined vs APPLY's ~79.5%. `V2-13-05` should therefore not
  proceed as scoped; the productive optimization target is the FAWE apply path.

## Non-claims

- No catalog promotion, no `PlacementDimensionLimitV2` change (that is `V2-13-02`/`V2-13-04`).
- No SLO declaration; durations are host-specific wall-clock decomposition.
- Measurement changes no generation/placement checksum.
- WorldEdit-standalone runtime not exercised (out of Scope).
