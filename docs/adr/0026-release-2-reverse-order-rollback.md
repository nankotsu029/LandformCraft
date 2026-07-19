# 0026: Release 2 rollbackはsnapshot済みenvelopeの逆順復元とfull verifyだけを成功とする

- Status: Accepted
- Date: 2026-07-19
- Decision scope: V2-6-08

## Context

V2-6-06／V2-6-07はapply・settle・verifyの失敗を`RECOVERY_REQUIRED`へ分類し、復元を後続Taskへ委ねた。失敗したplacementを放置するとworldに部分適用が残り、tileごとの再applyやsnapshot外の推測復元はAGENTS.mdの「失敗時はsnapshotを逆順復元する」「snapshot外副作用をrollback可能と仮定しない」に反する。復元の成功もapplyと同じ強度の証明（exact stream verify）を要する。

## Decision

V2-6-08で次を固定する。

- `PlacementRollbackServiceV2`（`core.v2.placement.rollback`）— 永続化済み`RECOVERY_REQUIRED` journal（全tile `SNAPSHOTTED`／`APPLIED`）だけを入力に、`ROLLING_BACK →` terminal `ROLLED_BACK`へ進める。
- mutation前preflight — plan／envelope／reservation／snapshot binding、durable reservation ownership、`PlacementSnapshotAllCompilerV2.loadPublished`によるpublished snapshotのstrict再検証、全snapshot fileのstrict decodeとsealed index checksum照合、union effect envelopeの被覆完全性。missing／tampered／coverage gapはworld mutationゼロで拒否する。
- reverse-order restore — snapshot済みeffect envelopeを逆canonical tile-index順、tile内はX→Z→Y canonical順のbounded `PlacementRestoreSliceV2`（`release-2-placement-restore-slice-v1`）で書き戻す。receiptはscheduler受理・main-thread実行・resource closeを証明し、tile完了ごとに`ROLLING_BACK` journalへcanonical `RESTORED` suffixをcheckpointする（`PlacementJournalV2` invariant）。
- rollback settle／full verify — 全tile復元後に`PlacementSettleVerifyPolicyV2`のbounded settle（effect envelope外update拒否）を行い、union effect envelope全体をsnapshot baselineとexact stream比較（checksum一致）した時だけ`ROLLED_BACK`（全tile `RESTORED`）をsealする。
- finalization — `ROLLED_BACK` seal後にregion／disk reservation leaseを解放する。published snapshotは削除せずrecovery証拠として保持する（retentionは別Task）。
- partial failure classification — restore／settle／verify／cancel／shutdown／budget失敗は`PlacementRollbackFailureCodeV2`で分類し`RECOVERY_REQUIRED`へ戻す。部分`RESTORED` checkpointは保持し、成功を偽らない。

## Consequences

- rollbackはsnapshot-allが証明した範囲（effect envelope）だけを復元し、snapshot外副作用は対象にしない。
- `ROLLED_BACK`はexact verify済みの復元だけを意味し、restore slice完了やtile checkpointは成功ではない。
- observer timeout／cancelは受理済みrestoreを取消さず、曖昧な状態は`RECOVERY_REQUIRED`のままoperator判断（V2-6-10 Recovery）へ残る。
- v1 placement／rollback／Undo／Release 1意味は変更しない。

## Alternatives

- 適用済みtileだけを復元する案は、settleでenvelope内へ波及した副作用を見逃すため不採用（全snapshot tileを復元する）。
- restore後のverifyを省略しreceipt数で成功判定する案は、world driftや復元漏れを証明できないため不採用。
- 成功時にsnapshotを即削除する案は、Undo／Recoveryの証拠を失うため不採用。
