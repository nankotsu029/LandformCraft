# V2-13-03 1024×1024 offline generation budget measurement — audit

- Status: **COMPLETE**（2026-07-21）— measured within frozen MEDIUM／Export／cell budgets; end-to-end offline did **not** complete at Task close.
- **2026-07-21 follow-up fix（same day, after Task close）:** the frozen `1_000_000`-cell subsystem budgets that this Task found blocking (`HydrologyPlanV2.GraphWorkBudget.globalCellCount`, `PreviewDimensionBudgetV2.MAXIMUM_CELLS`, `MacroLandWaterTopologyPlanV2.MAXIMUM_RASTER_CELLS`, and the equivalent `globalCellCount` gate mirrored in `ClimatePlanV2`／`SnowPlanV2`／`GeologyPlanV2`／`StrataPlanV2`／`MaterialProfilePlanV2`／`FeatureMaterialProfilePlanV2`／`WaterConditionPlanV2`／`EcologyPlanV2`／`HydrologyRoutingArtifactV2`／`EnvironmentValidatorV2.MAX_SCAN_CELLS`, plus the mirrored JSON Schema `maximum: 1000000`) were raised to a new shared constant, `ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS = MEDIUM_HORIZONTAL_CEILING² = 1_048_576`. Re-running this Task's own probe now completes the offline `surface-2_5d` pipeline (field sidecars → Blueprint compile → coastal validation artifact → preview render → all 64 tile schematics) end to end for 1024×1024 with no cell-budget rejection. Updated evidence below. This fix did not touch `PlacementDimensionLimitV2`, Paper placement, or LARGE, and does not claim 1024 is `SUPPORTED`/`testedMaximum` — it only removes the specific cell-count ceilings this Task identified as the blocking gates.
- Measured evidence: [v2-13-03-offline-budget-measurement-evidence.md](v2-13-03-offline-budget-measurement-evidence.md)
- Task: `V2-13-03`（Track C, V2-13）。前提: `V2-13-02` 済。
- Runner: `scripts/measure/v2-13-03-offline-budget-run.sh`
- Fixture: `examples/v2/diagnostic/medium-1024-square.request-v2.json`

## Acceptance

| Gate | Result |
|---|---|
| 1024専用fixture | PASS（1024×1024 request example） |
| Scale／Export MEDIUM admission | PASS（tiles=64／64、estimated descriptor resident 60 MiB ＜ retained 96 MiB） |
| 既存 cell budget 内での E2E（生成→validation→preview→export→self-verify） | **FAIL closed** — first gate `HYDROLOGY_GRAPH_WORK_BUDGET`（`globalCellCount` ≤ 1_000_000） |
| budget拒否境界の確認 | PASS（preview 1_000_000 cells、macro raster 1_000_000、hydrology GraphWorkBudget） |
| 予算を広げず超過を報告して停止 | PASS（Task内では予算拡張なし。**同日の別途follow-up修正でcell予算を拡張し、本表は当時の記録として保持する**） |
| 1024を SUPPORTED／testedMaximum と表現しない | PASS（follow-up後も不変） |

## Measured result (2026-07-21, offline JVM)

- cells = 1_048_576（1024²）
- `SCALE_EXPORT_ADMISSION` PASS（~0.002 s）
- field sidecars wrote ~20 MiB then Blueprint compile rejected at hydrology GraphWorkBudget（~0.43 s wall、heapΔ ~12 MiB）
- blockingStage = `HYDROLOGY_GRAPH_WORK_BUDGET`
- Further gates that would also reject（not reached in this run, unit-confirmed）:
  - `PreviewDimensionBudgetV2.MAXIMUM_CELLS` = 1_000_000
  - `MacroLandWaterTopologyPlanV2.MAXIMUM_RASTER_CELLS` = 1_000_000

## Route residual closed in this Task

- `GenerationBounds.MAX_HORIZONTAL_SIZE` 1000→1024（`model`は`model.v2`へ依存できないため literal。`ScaleDimensionPolicyV2Test`で MEDIUM 天井と一致を固定）
- `TopDownCoordinateMapping` target 寸法 1000→1024

## Non-claims (as measured at Task close, 2026-07-21)

- No preview／hydrology／macro cell budget raise — **superseded same day by the follow-up fix above**
- No `PlacementDimensionLimitV2` change、no Paper placement、no LARGE — still true after the follow-up
- No SLO、no SUPPORTED／testedMaximum claim for 1024 — still true after the follow-up
- Offline Release for 1024×1024 is **not** publishable under current cell budgets（blocks `V2-13-04` fixture build until a follow-up budget redesign Task）— **superseded**: the offline `surface-2_5d` pipeline now completes end to end for 1024×1024 (see follow-up evidence)

## Follow-up measured result (2026-07-21, offline JVM, after the cell-budget fix)

- cells = 1_048_576（1024²）
- `SCALE_EXPORT_ADMISSION` PASS（~0.002 s）
- `FIELD_SIDECARS_BLUEPRINT_VALIDATION_PREVIEW` PASS（~2.1 s wall、heapΔ ~71 MiB、~21 MiB on disk）: field sidecars, Blueprint compile (all plan budgets, including hydrology GraphWorkBudget), coastal validation artifact, preview render, and all 64 tile schematics complete without a cell-budget rejection
- `blockingStage` = `NONE`, `endToEndCompleted` = `true`
- `PreviewDimensionBudgetV2.MAXIMUM_CELLS` and `MacroLandWaterTopologyPlanV2.MAXIMUM_RASTER_CELLS` are now `1_048_576` (unit-confirmed, admitting 1024×1024)
- Not covered by this offline probe: Paper/FAWE placement (`PlacementDimensionLimitV2` unchanged at 1000×1000; separate gate) and Release 2 self-verify beyond the strict codec read-backs already exercised by tile/field/validation/preview writers

## Next

`V2-13-04`（1024 FAWE placement measurement）。本Taskからは開始しない。cell budgetのfollow-up修正によりoffline 1024² generationは成立しているが、FAWE配置実測は別途未着手。
