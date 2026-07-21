# 0034: Release 2 Paper配置を明示version dispatchのproduction lifecycleとして接続する

- Status: Accepted
- Date: 2026-07-19
- Amended: 2026-07-20（V2-6-21 P0 durability／Undo reservation／command security）
- Decision scope: V2-6-21

## Context

V2-6-01〜13はplan、envelope、reservation-bound confirmation、snapshot-all、containment、apply、settle/full verify、rollback、Undo、Recoveryを個別に実装し、V2-6-20はstrict Release 2 directory／ZIPからcanonical block streamを再構築した。しかしPaper pluginはこれらを所有・接続せず、既存`/lfc apply`はRelease 1のままだった。実機smokeはpublic end-to-end経路がないため実行不能だった。

## Decision

1. `Release2PlacementApplicationServiceV2`をRelease 2専用facadeとし、`plan → confirm → execute`を分離する。planはstrict Release検証、tile/physics走査、envelope、region/disk reservation、actor-bound token発行まででworldを変更しない。
2. confirmはtokenを一回だけ消費し、全effect envelopeのsnapshotとstrict read-backを完了してから、snapshot baseline＋canonical sourceでpost-apply viewを構築しcontainmentを証明する。失敗時のmutationは0件である。
3. executeはstrict sourceを再openし、canonical apply後にbounded settleとeffect envelope全体のexact verifyを行う。失敗して`RECOVERY_REQUIRED`となった場合は、取消不能な安全rollbackを逆順に実行する。terminal `APPLIED`またはexact-verified `ROLLED_BACK`以外を成功として返さない。
4. operational contextは既存version付きplacement Schema（envelope、reservation、snapshot、containment、verify、Undo、Recovery）だけでstrict read-backし、Releaseへ新artifactを追加しない。prepared／confirmed／appliedの各stageは必要fileをfsync＋atomic publishし、対応するsealed journal commit markerを最後に保存してからmain journalを進める。startupは全Release 2 journalをstrict読取し、非terminal状態をoperatorへ報告する。
5. Paper commandは`/lfc r2 ...`で明示分離する。既存`/lfc apply`／`undo`／`apply recover`のRelease 1意味を変更しない。R2にはplan／confirm／execute／status、operation-bound Undo、保守的Recoveryを提供する。R2 rootはoperator Player／CONSOLE／RCONだけに限定し、actor bindingとworld allow／deny policyを実行時にも検査する。command block、非operator、missing worldはworld mutation前にstable domain errorで拒否する。
6. Bukkit／WorldEdit world read/writeは`PaperPlacementWorldGatewayV2`だけがSchedulerへdispatchする。applyとrestoreはWorldEdit公開APIのbounded sliceで実行し、receiptはmain-thread実行とresource closeを証明する。artifact I/O、snapshot decode、containment、exact scanはPaper main threadで実行しない。
7. serviceは新規受付をcloseで停止し、所有するbounded apply／settle／rollback／Undo workerをcloseする。scheduler受理済みoperationの観測cancelを実operation取消とは扱わず、曖昧なrestart状態はRecoveryへ残す。
8. Undo reservationは同じplacement IDのapply leaseを同一effect envelopeで置換できるが、別placement IDのoverlapは常に拒否する。Undo context保存失敗時は元のapply reservationとterminal `APPLIED` journalを復元し、第三者が割り込める無予約windowを作らない。
9. production file store testはoperation contextの全write pointについて`BEFORE_WRITE`／`AFTER_PUBLISH`を注入し、restart後もpre-mutationはmutation 0、post-mutationはexact rollback／明示Recovery、Undo成功時はsnapshot baseline完全一致を要求する。

## Consequences

- Release 2 directory／ZIPからPaper applicationまでのfile-backed評価経路が存在し、write failure／restart／Undo／Recoveryをproduction storeと同じ保存実装で回帰できる。
- 本Decisionと2026-07-20 amendmentはsupport catalogを昇格しない。production supportの判断は証拠と専用promotion Taskへ分離し、500／1000寸法（後に`V2-11-04`／`V2-11-05`で実測）も本Decisionの昇格対象外のままとする（catalog寸法昇格は`V2-11-06`）。
- v1 Schema、generator `3.0.0-phase6`、Release format 1、既存command、placement／Undo／Recoveryは不変である。

## Alternatives

- 既存`/lfc apply`をRelease version自動判定へ変える案は、v1既定意味とoperator expectationを変えるため不採用。
- confirmとexecuteを1 commandへ統合する案は、snapshot-all／containmentの完了証拠をoperator確認から隠すため不採用。
- apply失敗時に部分worldを残してstatusだけ返す案は、安全rollbackを通常経路から外すため不採用。
