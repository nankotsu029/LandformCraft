# 0027: Release 2 Undoはoperation-bound confirmとworld-drift preflight後の逆順復元だけを成功とする

- Status: Accepted
- Date: 2026-07-19
- Decision scope: V2-6-09

## Context

V2-6-07はterminal `APPLIED`（全tile `VERIFIED`）をexact verify済み成功としてsealする。完了済みoperationを安全に戻すには、v1 Undoと分離したRelease 2経路で、actor-bound confirmation、current-world drift検査、snapshot厳密再検証、逆順restore、settle／full verifyを強制する必要がある。force overwriteやsnapshot外副作用の推測復元はAGENTS.mdに反する。

## Decision

V2-6-09で次を固定する。

- `PlacementUndoPlanV2` — sealed apply planを書き換えず、UNDO confirmation／retention transition／expected applied stream checksumを別契約へ載せる。
- prepare（`PlacementUndoPrepareCompilerV2`）— UNDO reservation（同placementIdでlease置換）→actor-bound UNDO confirmation発行→UndoPlan seal→journalは`APPLIED`のまま（confirmation準備メッセージ）。world mutationなし。
- execute（`PlacementUndoServiceV2`）— terminal `APPLIED`＋prepared UndoPlanを入力に、confirmation verify／consume、actor照合、expected applied streamとのcurrent-world drift preflight（不一致は`WORLD_DRIFT`、mutationゼロ、force禁止）、published snapshot strict再検証、`UNDOING`（VERIFIED prefix＋RESTORED suffix）での逆順`PlacementRestoreSliceV2` restore、bounded settle、snapshot baseline exact verify、terminal `UNDONE`＋reservation解放。snapshotは`KEEP_SNAPSHOTS_FOR_CLEANUP`で保持。
- Paper explicit path — `PlacementUndoApplicationServiceV2.isRelease2Path()`でv1 `PlacementApplicationService` Undoと識別する。
- failure — mutate後は`RECOVERY_REQUIRED`；成功を偽らない。

## Consequences

- Undoは現在worldがexpected applied streamと一致する時だけ開始でき、drift時の上書きは拒否する。
- `UNDONE`はbaseline exact verify済み復元だけを意味し、restore sliceやtile checkpointは成功ではない。
- v1 placement／Undo意味は変更しない。startup Recoveryは`V2-6-10`へ残す。

## Alternatives

- apply planへUNDO confirmationを上書きする案はsnapshot／evidence bindingを壊すため不採用。
- drift時にforce overwriteする案はAGENTS.mdと非Scopeに反するため不採用。
- 成功時にsnapshotを即削除する案はRecovery／cleanup証拠を失うため不採用。
