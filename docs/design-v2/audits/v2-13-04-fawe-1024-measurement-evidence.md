# V2-13-04 FAWE 1024×1024 placement measurement — measured evidence

- Status: **COMPLETE**（2026-07-21）
- See: [audit](v2-13-04-fawe-1024-measurement.md)、[runbook](../../smoke/v2-13-04-fawe-1024-measurement-runbook.md)
- Runner: `scripts/measure/v2-13-04-fawe-run.sh` + `scripts/measure/v2-13-01-stage-durations.py`
- Machine-readable per-stage evidence (committed): [pass1](v2-13-04-evidence/pass1-stage-durations.json)、[pass2](v2-13-04-evidence/pass2-stage-durations.json)（human: [pass1.txt](v2-13-04-evidence/pass1-stage-durations.txt)、[pass2.txt](v2-13-04-evidence/pass2-stage-durations.txt)）
- Local machine evidence directory (not committed): `build/measure/v2-13-04/evidence/fawe/`

## Machine-verifiable fields

```text
Profile: FAWE 2.15.2 standalone (WorldEdit plugin absent)
Boot: java -jar paper-1.21.11-132.jar in run-fawe/ (no Gradle runServer)
Xmx: 8G / Xms: 2G
Build JAR SHA-256: 67b5b30f627daedaac40484a4485fd61146467f6de3473b9c890790c56c9b7c9
Manifest canonical SHA-256: 40b0281f2595114aa1ab2dcc14e6d79ea796240b684dc6530b08f86a8216b2fc
Release: v2-13-04-measure-1024 / surface-2_5d / 1024×1024 solid y=0..1 (64 tiles, tile size 128)
Command surface: v2 place plan|confirm|execute, v2 undo plan|execute (r2 route retired in V2-12-06)
Pass1 (cold) placement ID: 79a76674-8029-4e29-8258-0897f25dfcc3
Pass1 terminal: APPLIED -> full effect-envelope verify -> UNDONE (wall_seconds=2287)
Pass2 (warm) placement ID: 43ac55e1-13b3-40b4-9771-e2140c290d02
Pass2 terminal: APPLIED -> full effect-envelope verify -> UNDONE (wall_seconds=2283)
Peak RSS (bytes): pass1 5561868288 (~5.18 GiB), pass2 5618065408 (~5.23 GiB); PID-only telemetry
Admission: no DISK_BUDGET_EXCEEDED / DISK_SHORTAGE (0 hits both passes)
Recovery drill: plan -> SIGKILL before confirmation issued -> restart -> doctor clean, no RECOVERY_REQUIRED orphan (telemetry-restart peak RSS ~0.98 GiB)
v1 regression: lfc help + lfc doctor issued on same FAWE profile
Stage-duration canonical checksums: pass1 c5db77efd90526fdb474dcef101f05637a7811bd063d7645a62bba5182e9ec91, pass2 0919b4a2be002d1c331892a2f7094f1f5c4b7f5895deba0de0c36d6a973bd60d
Operator / date: agent / 2026-07-21
```

## Per-stage wall-clock durations (seconds)

Stage boundaries are submit→terminal per the runbook; idle/setup gaps (stone-seal, forceload,
inter-stage latency) are excluded, so the six stages sum to the *work* time, not the full pass wall.
Poll granularity 3 s.

| Stage | Pass1 (cold) | Pass2 (warm) | Share (pass1) |
|---|---:|---:|---:|
| `PLACEMENT_STAGE_DURATION_PLAN` | 4 | 4 | 0.2% |
| `PLACEMENT_STAGE_DURATION_SNAPSHOT` (confirm→snapshot-all + containment + Undo baseline) | 209 | 209 | 10.1% |
| **`PLACEMENT_STAGE_DURATION_APPLY`** (canonical block-state stream → FAWE) | **1650** | **1647** | **79.6%** |
| `PLACEMENT_STAGE_DURATION_SETTLE` | 0 | 0 | 0.0% |
| `PLACEMENT_STAGE_DURATION_VERIFY` (full effect-envelope exact verify) | 27 | 27 | 1.3% |
| `PLACEMENT_STAGE_DURATION_UNDO` | 184 | 184 | 8.9% |
| **total stage seconds** | **2074** | **2071** | 100% |

**Bottleneck (both passes): `PLACEMENT_STAGE_DURATION_APPLY` — 1647–1650 s, ~79.5% of stage time**
(`bottleneck_fraction` pass1=0.7956, pass2=0.7953). The two passes are within 3 s per stage
(cold≈warm); `SETTLE` completed within one poll interval.

## Comparison to the V2-13-01 1000×1000 baseline

| Stage (pass1) | 1000² (V2-13-01) | 1024² (V2-13-04) | Δ |
|---|---:|---:|---:|
| PLAN | 4 | 4 | 0 |
| SNAPSHOT | 199 | 209 | +10 |
| **APPLY** | **1572** | **1650** | **+78 (~5.0%)** |
| SETTLE | 0 | 0 | 0 |
| VERIFY | 24 | 27 | +3 |
| UNDO | 176 | 184 | +8 |
| total | 1975 | 2074 | +99 (~5.0%) |

The +5% total tracks the +4.9% cell-count increase (1024²/1000² = 1.049); the stage *shape* is
unchanged and APPLY remains the dominant stage at ~79.5% in both dimensions. No new bottleneck
emerges at 1024².

## Host note

Measured on a 14 GiB host after stopping idle Gradle daemons to isolate the Paper JVM. Peak server
RSS was ~5.2 GiB against ~5.9 GiB free at start; swap stayed flat at ~25 MiB for the whole run (no
paging), so the timings are not memory-pressure contaminated. PID-only telemetry throughout.

## Non-claims

- No catalog promotion, no `PlacementDimensionLimitV2` change (measured-dimension promotion is a
  separate approval Task after V2-13-04).
- 1024 is not represented as `SUPPORTED` / `testedMaximum`.
- No SLO declaration; durations are host-specific wall-clock decomposition.
- Measurement changes no generation/placement checksum; safety ordering and verify scope unchanged.
- WorldEdit-standalone runtime not exercised (out of Scope).
