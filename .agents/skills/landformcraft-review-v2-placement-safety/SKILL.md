---
name: landformcraft-review-v2-placement-safety
description: V2-6、Release 2 placement、effect envelope、reservation、snapshot-all、apply/settle/full verify、rollback、Undo、Recovery、Paper main-thread境界の実装・監査で使う。v1配置の小修正や通常test、offline Release生成には使わない。
---

# LandformCraft V2配置安全workflow

## 使用条件

- V2-6のRelease 2 placement Taskで使え。
- mutation/effect envelope、region/disk reservation、bound confirmation、snapshot-all、containment、apply、verify、rollback、Undo、Recoveryを変更するときに使え。
- WorldEdit／FAWE smokeまたは実world measurement Taskでは外部証拠条件も適用せよ。

## 使用しない条件

- v1 placementの小修正には主Skillとして使うな。v1互換Skillを使え。
- offline generator、schematic、Release 2 containerだけの作業には使うな。
- V2-5 gate完了前にRelease 2 world mutationを開始するために使うな。
- 通常testやPaper以外の変更で暗黙起動するな。

## 必ず最初に読む正本

1. `AGENTS.md` のthread、配置、安全性、v1互換規則
2. `README.md`、`docs/architecture.md`、`docs/roadmap.md`
3. `docs/design-v2/task-execution-guide.md` と `task-index.md` の対象V2-6 Task節
4. `docs/design-v2/implementation-roadmap.md`、`migration-plan.md`、`volumetric-terrain.md` の配置節
5. `docs/security.md`、`docs/operations.md`、`docs/artifact-format.md`、配置関連ADR
6. Release 2 verifier、placement model/core/Paper/worldedit、journal Schema、tests
7. 作業前の `git status --short`

## 入力

- 対象V2-6 Task IDとRelease 2 capability集合。
- target world、bounds、anchor、tile、mutation内容。
- fluid／gravity／neighbor-sensitive contentとruntime profile。
- support予定寸法と外部環境。

## 手順

1. roadmapでV2-5 gate完了と対象V2-6 Taskの前提を確認せよ。未完なら停止せよ。
2. Release 2をworld mutation前にstrict verifyし、placement eligibilityを確定せよ。
3. final resolverからper-tile mutation AABBとunion effect envelopeを保守的に算出し、provenance/checksumへbindingせよ。
4. fluid、gravity、neighbor update、support radiusを分類し、effect envelope外へ出る可能性をpreflightでhard rejectせよ。
5. 全regionとdiskをatomicに予約し、confirmをRelease、target、envelope、reservation、operation、actor、expiryへ結合せよ。
6. 最初のapply前に全effect envelopeをcanonical順でsnapshotし、全snapshotをstrict read-backせよ。
7. applyをcanonical順かつbounded scheduler sliceで行い、gatewayの受理、完了、closeを追跡せよ。
8. bounded multi-tick settle後、effect envelope全体をexpected canonical block streamとexact比較せよ。sample verifyで代用するな。
9. failure時はsnapshot済みenvelopeを逆順復元し、settle/full verifyせよ。曖昧なら `RECOVERY_REQUIRED` にせよ。
10. Undoはoperation-bound confirmとworld drift preflightを要求し、Recoveryは曖昧状態を成功へ自動分類するな。
11. Bukkit/Paper world read/writeだけをSchedulerへdispatchし、HTTP、image、artifact I/O、CPU生成をmain threadで行うな。
12. 受理済みoperationを観測Futureのtimeout／cancelだけで取消済みにするな。late completionをjournalと照合せよ。
13. fault injection、restart、disk不足、overlap、replay、cancel、shutdown、v1 placement回帰をtestせよ。
14. 対象test、`./gradlew test`、`./gradlew build`、`git diff --check`を実行せよ。

## 検査項目

- 順序が `validate → preview/export → envelope/estimate → reserve → bound confirm → snapshot-all → apply → settle → full verify` か。
- snapshotがmutationだけでなくeffect envelope全体を覆うか。
- fluid／gravity containmentを事後rollbackへ委ねていないか。
- tile checkpointをsettle後exact verifyと誤認していないか。
- reservation失敗、snapshot失敗、apply失敗で全資源とstateが安全に整合するか。
- journal、snapshot、Releaseの欠損／tamperingを拒否するか。
- v1 transaction、journal、Undo、Recoveryの意味を変更していないか。
- supported寸法がexact buildのWorldEdit／FAWE smokeと実world測定で裏付けられているか。

## 成功条件

- world mutation前の全preflight、reservation、confirm、snapshot-allが強制されること。
- apply後のsettle/full verify、全failure点のrollbackまたは明示Recoveryが確認できること。
- main-thread境界、bounded scheduler、late completion、restartがtestされること。
- v1配置回帰が通り、support宣言が実測済み範囲に限られること。

## 停止条件

- V2-5 gate、offline self-verify、3D read-back、corruption validationが未完なら開始するな。
- effect envelopeを安全に上限化できないcontentは拒否し、配置対応を宣言するな。
- snapshot-all前apply、sample verify、snapshot外rollback、private WorldEdit／FAWE APIが必要なら停止せよ。
- 実機環境がなければ対象smoke／measurement Taskを `BLOCKED_EXTERNAL` とし、mockで閉じるな。

## 出力／報告形式

- 対象Taskと前提gate
- Release／target／envelope／reservation／confirm binding
- snapshot-all、apply、settle/full verify、rollback sequence
- fault injection／restart／Undo／Recovery結果
- main-thread／queue／disk／dimension evidence
- v1回帰とexternal blocker
- 実行したtest／build／diff check

## 関連Skillとの境界

- 対象TaskのScope管理は `landformcraft-execute-v2-task` に任せよ。
- Release 2 eligibilityとtamperingは `landformcraft-verify-release-artifacts` に任せよ。
- scheduler／memory／cancel matrixは `landformcraft-audit-determinism-resources` を併用せよ。
- v1 placement／Undo不変は `landformcraft-guard-v1-compatibility` に任せよ。
- V2-6末尾の最終判定は `landformcraft-audit-phase-gate` に任せよ。
