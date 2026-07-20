# V2-11-05 FAWE 1000×1000 real-world measurement — audit

- Status: **COMPLETE**（2026-07-20）
- Task: `V2-11-05`（無効化済み`V2-6-17`の後継。同IDは復活させない）
- Evidence: [v2-11-05-fawe-1000-measurement-evidence.md](v2-11-05-fawe-1000-measurement-evidence.md)
- Runbook: [v2-11-05-fawe-1000-measurement-runbook.md](../../smoke/v2-11-05-fawe-1000-measurement-runbook.md)

## Acceptance

| Gate | Result |
|---|---|
| Standalone Paper JVM（`run-fawe/` paperclip、Gradle runServer 不使用） | PASS |
| FAWE 2.15.2 only（WorldEdit plugin 禁止） | PASS |
| Measurement profile only（ceiling 1000×1000） | PASS |
| `APPLIED` → full effect-envelope verify → `UNDONE`（Pass1） | PASS |
| 反復 Pass2 同lifecycle | PASS |
| Scheduler slice／queue long-run（admission failure なし） | PASS |
| Disk reservation ceiling（`DISK_BUDGET_EXCEEDED` なし） | PASS |
| PID-only RSS／telemetry | PASS（peak ≈ 6.02 GiB） |
| Failure／recovery drill（plan→SIGKILL→restart） | PASS |
| v1 help／doctor smoke | PASS |
| Catalog 昇格しない | PASS（明示 non-claim） |

## Notes

- Paper 1.21 の単一 `forceload add` は最大 **256 chunks**。1000×1000（63×63=3969 chunks）は幅64 block（4 chunks）×長さ1000の strip で keep-load する。
- Confirm 完了判定は durable journal の `SNAPSHOT_COMPLETE` を正とする。ログの `state: SNAPSHOT` は `SNAPSHOTTING` に誤マッチするため使わない。
- MEDIUM envelope（〜2M mutation overrides）で `Map.copyOf`→`ImmutableCollections.MapN` が confirm／Undo 準備を十数分以上ブロックした。`PlacementExpectedBlockResolverV2`／`PlacementUndoServiceV2`／`PlacementRollbackServiceV2` は exclusive `HashMap` を `Collections.unmodifiableMap` で包むよう修正した（意味は不変、防御的 deep-copy をやめただけ）。
- Pass 間は Paper 再起動後に placement store を掃除（稼働中 JVM の store 削除は禁止）。

## Next

`V2-11-06`（実測寸法の catalog 昇格。本 Task は 1000 を昇格しない）。
