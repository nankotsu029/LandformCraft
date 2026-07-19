# 0028: Release 2 Recoveryは保守的分類とconfirmation-bound rollback／acceptだけを許可する

- Status: Accepted
- Date: 2026-07-19
- Decision scope: V2-6-10

## Context

restart後のRelease 2 operationは、永続journal、published snapshot artifact、現在worldという3種の証拠しか持たない。V2-6-08／09はrollback／Undoの実行機構を固定したが、どのjournal状態からどの操作が安全かを決める分類、中断されたrollback／Undoの再整合、accept-as-appliedの根拠、snapshot retentionの後始末は未定義だった。曖昧な状態を成功へ分類したり、証拠なしに自動修復することはAGENTS.mdに反する。

## Decision

V2-6-10で次を固定する。

- 分類（`PlacementRecoveryServiceV2.diagnose`）— journal／artifact／world証拠だけからの決定論的で読み取り専用な分類。pre-mutation状態（`PLANNED`〜`SNAPSHOT_COMPLETE`）は`NO_WORLD_MUTATION`（lease解放のみ）、terminal（`APPLIED`／`ROLLED_BACK`／`UNDONE`）は`ALREADY_TERMINAL`、mutated状態はpublished snapshotのstrict再検証・durable lease所有・binding照合・union effect envelope全体のbounded exact world scanの後にのみ`SAFE_TO_ROLLBACK`となる。canonical block source（manifest checksum・capability set・overlay ordinal照合済み）から再構築したexpected applied streamとworld scanが完全一致した場合だけ`SAFE_TO_ACCEPT`を加える。証拠が欠損・改変・不整合なら`MANUAL_INTERVENTION_REQUIRED`とし、自動actionを一切提示しない。
- prepare — 診断が許すactionだけを、actor-bound一回用confirmation（`RECOVERY_ROLLBACK`／`RECOVERY_ACCEPT`、TTL 10分、平文非永続）付きのsealed `PlacementRecoveryPlanV2`（`release-2-placement-recovery-plan-v1`）へ固める。診断のjournal checksumが古い場合は拒否し、worldもjournalも変更しない。
- rollback execute — confirmation verify／consume・actor照合・lease確認の後、中断journal（`APPLYING`／`SETTLING`／`VERIFYING`／`ROLLING_BACK`／`UNDOING`、または復元進捗を含む`RECOVERY_REQUIRED`）を決定論的に再整合（VERIFIED→APPLIED、RESTORED→SNAPSHOTTED）して`RECOVERY_REQUIRED`へreseal後、V2-6-08 rollback transactionへ委譲する。snapshot証拠のないtileは停止する。
- accept execute — confirmation consume後にbaseline・expected applied streamをsealed plan証拠と再照合し、新たなfull exact world scanが再び完全一致した時だけterminal `APPLIED`（全tile `VERIFIED`）をsealしてleaseを解放する。driftは`WORLD_DRIFT`でmutationゼロ拒否。snapshotは`KEEP_SNAPSHOTS_FOR_CLEANUP`で保持する。
- cleanup retention — terminal `ROLLED_BACK`／`UNDONE`だけを対象に、dry-run planへexact file setとbytesを固定し、実行時にjournal checksumとfile setが不変であることを再検証してから削除する。retention file／byte budgetを事前admissionする。
- Paper explicit path — `PlacementRecoveryApplicationServiceV2.isRelease2Path()`／`PaperPlacementRecoveryServiceV2`でv1 recovery（`PlacementApplicationService.diagnoseRecovery`等）と識別する。v1 recoveryの意味は変更しない。

## Consequences

- 全persisted journal状態に安全なoperator action（lease解放／rollback／accept／cleanup／manual）が定義され、ambiguityは常にmanualへ落ちる。
- acceptはfull exact scanの証拠なしに成立せず、部分的一致やsample検査での成功宣言は不可能である。
- 中断されたrollback／Undoは同じsnapshot証拠から常に全envelope復元へ収束し、resume可否をtile checkpointから推測しない。
- published snapshotの削除は、terminal journalへのdry-run bound cleanupだけが行える。

## Alternatives

- journal状態からの自動accept（scanなし）は偽の成功を許すため不採用。
- 中断rollbackのtile単位resume（RESTORED tileをskip）はtile内部の部分書込みを区別できないため不採用。
- recovery専用のrestore実装はV2-6-08と復元意味が分岐するため不採用。
