# V2-11-04 FAWE 500×500 real-world measurement — audit

- Status: **COMPLETE**（2026-07-20）
- Task: `V2-11-04`（無効化済み`V2-6-16`の後継。同IDは復活させない）
- Evidence: [v2-11-04-fawe-500-measurement-evidence.md](v2-11-04-fawe-500-measurement-evidence.md)
- Runbook: [v2-11-04-fawe-500-measurement-runbook.md](../../smoke/v2-11-04-fawe-500-measurement-runbook.md)

## Acceptance

| Gate | Result |
|---|---|
| Standalone Paper JVM（`run-fawe/` paperclip、Gradle runServer 不使用） | PASS |
| FAWE 2.15.2 only（WorldEdit plugin 禁止） | PASS |
| Measurement profile only（ceiling 500×500） | PASS |
| `APPLIED` → full effect-envelope verify → `UNDONE`（Pass1） | PASS |
| 反復 Pass2 同lifecycle | PASS |
| PID-only RSS／telemetry | PASS（peak ≈ 6.08 GiB） |
| Failure／recovery drill（plan→SIGKILL→restart） | PASS |
| v1 help／doctor smoke | PASS |
| Catalog 昇格しない | PASS（明示 non-claim） |

## Notes

- Paper 1.21 の `forceload` 引数は **block column X/Z**（chunk index ではない）。誤って chunk index を渡すと envelope の大半が unload のままになり、`fill` が `That position is not loaded` になる。
- Undo exact verify は natural terrain（gravity／fluid）上だと settle 後に mismatch し得る。measurement では envelope を stone-seal してから plan する。
- Pass 間で稼働中 JVM の `placement-v2` を消すと plan がハングする。store 掃除は Paper 再起動後に行う。

## Next

`V2-11-05`（1000×1000、同一 FAWE／`run-fawe` 手法）。500 の catalog 昇格は行わず `V2-11-06` 待ち。
