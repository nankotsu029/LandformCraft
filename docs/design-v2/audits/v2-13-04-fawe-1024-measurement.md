# V2-13-04 FAWE 1024×1024 real-world placement measurement — audit

- Status: **COMPLETE**（2026-07-21）— harness committed, offline-verified, and the 2-pass + Recovery
  drill measurement run on an isolated FAWE 2.15.2 Paper JVM. Both passes reached
  `APPLIED → full effect-envelope verify → UNDONE`; APPLY is the bottleneck at ~79.5% (matching the
  V2-13-01 1000² shape); peak RSS ~5.2 GiB with no swap. Evidence:
  [measured evidence](v2-13-04-fawe-1024-measurement-evidence.md).
- Task: `V2-13-04`（旧PROPOSAL-B5。無効化済み`V2-6-17`後継系列と同じ実機手法）
- Prerequisites: `V2-13-01`（工程別計測、完了）、`V2-13-03`（1024² offline E2E成立、完了）
- Evidence: [v2-13-04-fawe-1024-measurement-evidence.md](v2-13-04-fawe-1024-measurement-evidence.md)
- Runbook: [v2-13-04-fawe-1024-measurement-runbook.md](../../smoke/v2-13-04-fawe-1024-measurement-runbook.md)
- Runner: `scripts/measure/v2-13-04-fawe-run.sh` + `scripts/measure/v2-13-01-stage-durations.py`
- Fixture: `MeasurementSurfaceFixtureV2.build1024` / `V21304MeasurementFixtureExporterTest`

## Methodology

Measured with the V2-11-04/05 methodology: standalone `run-fawe/` paperclip JVM (Gradle `runServer`
不使用), FAWE 2.15.2 only (WorldEdit plugin absent), measurement profile ceiling 1024×1024, RCON,
server-PID-only telemetry. The Paper JVM was isolated by stopping idle Gradle daemons first; peak RSS
was ~5.2 GiB against ~5.9 GiB free with swap flat at ~25 MiB (no paging), so the timings are not
memory-pressure contaminated. Two passes (cold pass1 / warm pass2) plus a failure/recovery drill ran
in ~1h16m total. Stage seconds come from the committed emitter and carry deterministic canonical
checksums; they were not filled from mocks, another build, or a different FAWE version.

## Delivered this Task (offline-verified)

| Deliverable | State |
|---|---|
| `MeasurementSurfaceFixtureV2.build1024`（MEDIUM 1024²、64 tiles、solid-only） | Committed; offline export → publish → verify **PASS** |
| `V21304MeasurementFixtureExporterTest` | Committed; **PASS** with `LANDFORMCRAFT_V21304_EXPORT_DIR` set |
| `scripts/measure/v2-13-04-fawe-run.sh`（stage-mark 2-pass + Recovery drill、v2 verbs、`DIM=1023`） | Committed; `bash -n` syntax **PASS** |
| Runbook + this audit + evidence skeleton | Committed |
| Per-stage emitter reuse（`v2-13-01-stage-durations.py`、closed `PLACEMENT_STAGE_DURATION_*` labels） | Reused as-is |

The 1024² **offline** surface pipeline (fields → Blueprint compile → coastal validation → preview →
64-tile schematic → Release publish/verify) completes E2E on this build, confirming the fixture the
placement measurement will apply is producible within the MEDIUM budget (consistent with V2-13-03).

## Acceptance

| Gate | Result |
|---|---|
| Standalone Paper JVM（`run-fawe/` paperclip、Gradle runServer 不使用） | PASS |
| FAWE 2.15.2 only（WorldEdit plugin 禁止） | PASS |
| Measurement profile only（ceiling 1024×1024） | PASS |
| Pass1 `APPLIED` → full effect-envelope verify → `UNDONE`（wall 2287 s） | PASS |
| Pass2（warm）同lifecycle（wall 2283 s） | PASS |
| Per-stage durations committed、bottleneck named（APPLY 1650/1647 s、~79.5%） | PASS |
| Recovery drill（plan → SIGKILL before confirmation → restart → doctor、no RECOVERY_REQUIRED orphan） | PASS |
| Disk reservation ceiling（`DISK_BUDGET_EXCEEDED` なし、0 hits） | PASS |
| PID-only RSS／telemetry（peak ~5.2 GiB、no swap） | PASS |
| v1 help／doctor smoke | PASS |
| Catalog／`PlacementDimensionLimitV2` 昇格しない | PASS（明示 non-claim、本Task非Scope） |

## Non-claims

- **No catalog promotion** and **no `PlacementDimensionLimitV2` change**; the measured-dimension
  promotion is a separate approval Task **after** V2-13-04.
- 1024 is not represented as `SUPPORTED` / `testedMaximum`.
- No pre-declared SLO; durations are host-specific wall-clock decomposition.
- Safety ordering and verify scope are unchanged; measurement changes no generation/placement checksum.
- WorldEdit-standalone runtime is out of Scope for this Task.

## Next

The measured evidence is committed. Catalog promotion of the measured 1024 dimension remains a
**separate approval Task** and is not started here — `PlacementDimensionLimitV2`, the sealed catalog,
and Paper capability columns are unchanged, and 1024 is not claimed `SUPPORTED`/`testedMaximum`. The
V2-13 parent Phase stays `進行中` (no integration-audit Task; human approval required to close it, and
`V2-13-05` is held as condition-not-met).
