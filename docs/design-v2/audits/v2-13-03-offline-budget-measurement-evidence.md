# V2-13-03 measured evidence — 1024×1024 offline generation budget

- Date: 2026-07-21（original Task measurement）; re-measured 2026-07-21 after the same-day cell-budget follow-up fix
- Host: local offline JVM（OpenJDK 21）、not Paper／FAWE
- Command: `LANDFORMCRAFT_V21303_EVIDENCE_DIR=docs/design-v2/audits/v2-13-03-evidence ./scripts/measure/v2-13-03-offline-budget-run.sh`
- Machine-readable: [v2-13-03-evidence/offline-budget-measurement.json](v2-13-03-evidence/offline-budget-measurement.json)
- Human summary: [v2-13-03-evidence/offline-budget-measurement.txt](v2-13-03-evidence/offline-budget-measurement.txt)
- The runner always overwrites the committed JSON／TXT with the latest run, so those two files reflect the **current** (post-fix) state below. The original rejection measurement is preserved here in prose only.

## Current summary (post cell-budget fix)

| Field | Value |
|---|---|
| Dimensions | 1024×1024（1_048_576 cells） |
| Scale／Export admission | PASS（tileCount 64＝MEDIUM／Export max、estimatedResidentBytes 62_914_560 ＜ mediumRetainedBytes 100_663_296） |
| Preview cell budget admits | **true**（max raised to 1_048_576） |
| Macro raster budget admits | **true**（max raised to 1_048_576） |
| End-to-end completed | **true** |
| Blocking stage | `NONE` |

## Stage table (post cell-budget fix)

| Stage | Outcome | wallSeconds | heapDeltaBytes | diskBytesAfterStage |
|---|---|---:|---:|---:|
| SCALE_EXPORT_ADMISSION | PASS | 0.002 | 0 | 0 |
| FIELD_SIDECARS_BLUEPRINT_VALIDATION_PREVIEW | **PASS** | 2.144 | 74_218_776 | 21_200_265 |

Field sidecars, Blueprint compile (all plan budgets, including the raised hydrology GraphWorkBudget), the coastal validation artifact, preview render, and all 64 tile schematics complete without a cell-budget rejection.

## Original measurement (2026-07-21, before the follow-up fix — historical)

| Field | Value |
|---|---|
| Preview cell budget admits | false（max 1_000_000） |
| Macro raster budget admits | false（max 1_000_000） |
| End-to-end completed | false |
| First blocking stage | `HYDROLOGY_GRAPH_WORK_BUDGET` |
| Blocking detail | `hydrology graph/work budget is outside trusted bounds`（`GraphWorkBudget.globalCellCount` ≤ 1_000_000） |

`FIELD_SIDECARS_BLUEPRINT_VALIDATION_PREVIEW` was `REJECT` (0.431 s, heapΔ 12_256_736, ~21 MiB written) — field sidecars were written before Blueprint compile failed closed on the hydrology cell budget; preview PNG read-back and Release publish were not reached.

## Interpretation

Request／Schema／`GenerationBounds` horizontal ceilings admit 1024, and Scale／Export MEDIUM budgets admit the 64-tile／~60 MiB descriptor working set. At Task close, the frozen **1_000_000-cell** subsystem budgets（hydrology GraphWorkBudget first, then preview／macro raster）rejected 1024², and per Task Scope the budgets were not raised within `V2-13-03`. A same-day follow-up fix raised those budgets (and every equivalent `globalCellCount`/raster/scan cell ceiling in the Blueprint compile path) to `ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS = 1_048_576`; re-measurement above shows the offline `surface-2_5d` pipeline now completes end to end for 1024×1024.
