# V2-13-01 Placement stage instrumentation + 1000 baseline re-measurement — audit

- Status: **COMPLETE**（2026-07-21）— instrumentation committed and verified, and the on-host
  1000×1000 re-measurement was executed on the FAWE 2.15.2 measurement host（cold pass1 / warm
  pass2, both APPLIED→full verify→UNDONE）。
- Measured evidence: [v2-13-01-stage-instrumentation-evidence.md](v2-13-01-stage-instrumentation-evidence.md)
- Task: `V2-13-01`（Track C, V2-13）。前提: なし。
- Runbook: [v2-13-01-stage-instrumentation-runbook.md](../../smoke/v2-13-01-stage-instrumentation-runbook.md)
- Runner: `scripts/measure/v2-13-01-stage-instrumentation-run.sh`, `scripts/measure/v2-13-01-stage-durations.py`

## Measured result (2026-07-21, FAWE 2.15.2 standalone)

工程別秒数（cold pass1 / warm pass2、両passほぼ一致、単位秒）: PLAN 4/4、SNAPSHOT 199/200、
**APPLY 1572/1571**、SETTLE 0/0、VERIFY 24/24、UNDO 176/176、total 1975/1975。
**最大ボトルネック工程 = `PLACEMENT_STAGE_DURATION_APPLY`（~1571–1572 s、~79.5%、両pass一致）**。
canonical block-state stream の FAWE 適用が支配的で、per-block `Map` を持つ SNAPSHOT（~10%）＋
UNDO（~9%）は合計 ~19% にとどまる。V2-11-05 が in-Task で修正した confirm/Undo `Map` stall（>12分）
は解消済み（SNAPSHOT は ~200 s）。詳細は
[evidence](v2-13-01-stage-instrumentation-evidence.md)。

**`V2-13-05` への含意:** 「Map／allocation が主要ボトルネックと確認された場合のみ実行可」という
条件付き承認の条件は**不成立**（APPLY 支配）。`V2-13-05` は現Scopeでは実行せず、最適化対象は
FAWE apply 経路である旨を記録する（実行判断は別途）。

## Command-surface correction during measurement

初回実行は旧 `r2 plan/execute/undo-plan` を用いて `V2_UNKNOWN_VERB: unknown v2 verb 'r2'` で失敗した
（`r2` 経路は V2-12-06 で退役）。runner を現行 `v2 place plan|confirm|execute` / `v2 undo plan|execute`
と `v2-confirm-*`／`v2-undo-*` confirmation file へ修正し、plan-only smoke で確認後に本計測を実行した。

## What this Task decomposes

V2-11-05 measured the 1000×1000 placement lifecycle at a **total** ~106 min/pass
（pass1 `wall_seconds=6342`, pass2 `wall_seconds=6436`; see
[v2-11-05 evidence](v2-11-05-fawe-1000-measurement-evidence.md)) and noted that MEDIUM-envelope
`Map` construction stalled confirm/Undo setup for >12 min. This Task adds the instrumentation to
split that total into six per-stage wall-clock durations so the bottleneck stage is named from
committed evidence rather than inferred.

## Delivered (host-independent, verified)

| Item | Detail |
|---|---|
| Closed duration vocabulary | `OperationalMetricUnitV2.SECONDS` + six additive `OperationalMetricLabelV2.PLACEMENT_STAGE_DURATION_{PLAN,SNAPSHOT,APPLY,SETTLE,VERIFY,UNDO}`; `SampleV2` unit binding → SECONDS |
| Runtime isolation | `OperationalMetricsCollectorV2.RUNTIME_LABELS` fixes the runtime snapshot to its existing 17 labels, so runtime snapshots and their canonical checksums are unchanged |
| Bottleneck analysis | `PlacementStageDurationAnalysisV2` — deterministic largest-stage identification (lifecycle-order tie-break) + canonical duration-only `OperationalMetricsSnapshotV2` builder |
| Committed evidence format | Emitter `v2-13-01-stage-durations.py` produces schema-conforming `stage-durations.json` + human `stage-durations.txt` (bottleneck line); example `examples/v2/operations/placement-stage-durations-v2.json` |
| Host runner | `v2-13-01-stage-instrumentation-run.sh` reuses the V2-11-05 lifecycle and adds only stage-mark capture; no safety-order / verify-scope change |
| Schema | `operational-metrics-snapshot-v2.schema.json` gains the six labels + `SECONDS` unit (additive; `$id` / `contractVersion` frozen) |
| ADR | [ADR 0031](../../adr/0031-release-2-operational-metrics-diagnostics-retention.md) additive note |

## Verification (this session)

```text
./gradlew test --tests '…PlacementStageDurationAnalysisV2Test'
                --tests '…OperationalOperationsV2Test'
                --tests '…format.SchemaContractTest'   → PASS
./gradlew test   → PASS (full suite)
./gradlew build  → PASS
```

The instrumentation (labels, analysis, emitter, schema) is unit-verified above; the real per-stage
seconds come from the on-host run below.

## On-host measurement (2026-07-21) — executed

- Host: FAWE 2.15.2 standalone Paper JVM in `run-fawe/` (no Gradle runServer), Xmx 8G, RCON,
  PID-only telemetry — the V2-11-04/05 methodology.
- Both passes reached `APPLIED → full effect-envelope verify → UNDONE`; no disk-admission failures.
- Per-stage seconds, bottleneck (APPLY), and machine-verifiable fields:
  [measured evidence](v2-13-01-stage-instrumentation-evidence.md) and committed
  [pass1](v2-13-01-evidence/pass1-stage-durations.json) / [pass2](v2-13-01-evidence/pass2-stage-durations.json).
- The initial attempt failed on the retired `r2` verb; the runner was corrected to the current
  `v2 place` / `v2 undo` surface and a plan-only smoke confirmed it before the full run.

## Non-claims

- No catalog promotion, no `PlacementDimensionLimitV2` change, no 1024 route/Schema change
  (`V2-13-02`), no SLO declaration.
- Measurement is wall-clock decomposition only; no generation/placement checksum changes.
- `V2-13-05` (per-block `Map` packing) was conditional on this measurement confirming
  `Map`/allocation as the dominant bottleneck; the measurement shows **APPLY** dominates, so that
  condition is **not met**.

## Next

`V2-13-02`（MEDIUM 1024 route/Schema extension；前提 `V2-8-02` 済で独立着手可）。本Taskからは開始しない。
