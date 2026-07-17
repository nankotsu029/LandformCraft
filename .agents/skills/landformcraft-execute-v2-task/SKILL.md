---
name: landformcraft-execute-v2-task
description: V2-x-NN、次のTask、Acceptance gateの実装で使う。roadmapとTask Indexから現在または指定Taskを1件だけ実装し、test・Schema・examples・docsを同期する。v1の小修正、単なる調査、Phase末尾監査には使わず、互換監査とPhase gate監査は専用Skillへ渡す。
---

# LandformCraft V2 Task実行

## 使用条件

- 現在の次、または明示された `V2-x-NN` TaskをAcceptance gateまで実装せよ。
- V2-2〜V2-6の個別Taskに適用せよ。
- Task内に互換、決定性、artifact、validation、配置の副workflowがあれば対応Skillも併用せよ。

## 使用しない条件

- v1だけの小修正、一般的なdocs修正、調査やレビューだけには使うな。
- Phase末尾の統合監査Taskには `landformcraft-audit-phase-gate` を使え。
- 複数Task、Phase全体、未登録の大規模subsystemを一度に実装するために使うな。

## 必ず最初に読む正本

1. `AGENTS.md`
2. `README.md`
3. `docs/architecture.md`
4. `docs/roadmap.md`
5. `docs/design-v2/task-execution-guide.md`
6. `docs/design-v2/task-index.md` の対象Task節
7. 対象Taskが挙げる設計文書、ADR、Schema、examples、production code、tests
8. 作業前の `git status --short`

進捗、次Task、Task ID一覧、完了状態をこのSkillから推測するな。毎回正本から読み直せ。

## 入力

- 任意の対象Task ID。省略時はroadmapが示す次の未完了Task。
- ユーザーが指定した追加制約。
- 現在の作業ツリーと既存変更。

## 手順

1. roadmapの現在地とTask Indexの状態・前提を照合せよ。矛盾があれば実装せず報告せよ。
2. 対象Taskから目的、前提、Scope、非Scope、主変更、必須test、D/M/S、Gate、docs、停止条件を作業メモへ抽出せよ。
3. 関連設計のStatusと実装・Schema・testの現状を照合し、設計提案を実装済みと扱うな。
4. 変更面をcontract、実装、negative/corruption test、determinism/resource、docs同期に分けよ。
5. 対象TaskのScopeだけを実装せよ。後続Taskの型、capability、配置経路を先取りするな。
6. pure Java logicへ単体testを追加し、Taskが要求するnegative、corruption、cancel、budget、order差を検証せよ。
7. 外部仕様を変えた場合だけ、対応Schema、examples、ADR、docsを同じ差分で同期せよ。
8. 対象test、`./gradlew test`、`./gradlew build`、`git diff --check`、`git status --short`を実行せよ。
9. Acceptance証拠をroadmapへ簡潔に同期せよ。詳細はtestまたは対象docsへ置け。
10. Acceptanceを満たした時点で終了し、後続Taskへ進むな。

## 検査項目

- v1 Schema、generator、Release 1、placement、Undoの意味とchecksumを変えていないか。
- model/generator/core/Paper/WorldEditのpackage境界とthread境界を守っているか。
- HARD conflict、unknown version/capability、budget超過をfallbackしていないか。
- global座標、stage別halo、stable seed/order、bounded allocation、cancel cleanupをTask条件どおり検査したか。
- feature lifecycleまたはRelease capabilityを、専用gate前に `SUPPORTED`／有効へしていないか。
- production docs、Schema、examples、roadmapの表現が実装状態と一致するか。
- 既存の未コミット変更を保持し、無関係なreformatや削除をしていないか。

## 成功条件

- 対象Taskの全Acceptanceが自動testまたは要求された外部証拠で確認できること。
- compile、対象test、全test、build、diff checkが成功すること。
- docsとroadmapが実態に一致し、親Phaseと後続Taskの状態を誤って進めていないこと。

## 停止条件

- 前提Taskまたは必要Phase gateが未完なら停止せよ。
- v1互換破壊、hard constraintの推測、resource上限不明、security弱体化、clean build不能なら停止せよ。
- 2個目の独立generator、独立artifact format、大規模subsystem、generatorとPaper mutationの同時実装が必要ならAcceptanceを弱めず、新Taskを提案せよ。
- 実機依存証拠が得られなければ `BLOCKED_EXTERNAL` とし、mockで完了扱いにするな。

## 出力／報告形式

- 対象Taskと照合した正本
- 実装したScopeと維持した非Scope
- 変更ファイルと設計上の理由
- Acceptanceごとの証拠
- 実行したtest／build／diff check
- docs／Schema／examples／roadmap同期
- v1互換性とproduction安全性
- 残課題、停止理由、追加Task案

## 関連Skillとの境界

- v1回帰matrixは `landformcraft-guard-v1-compatibility` に任せよ。
- whole/tile/thread/resource/cancelの深い監査は `landformcraft-audit-determinism-resources` に任せよ。
- Release／ZIP／strict read-backは `landformcraft-verify-release-artifacts` に任せよ。
- validator／preview専用Taskは `landformcraft-build-validation-preview` を併用せよ。
- Phase末尾Taskは `landformcraft-audit-phase-gate` を主Skillにせよ。
- V2-6のworld mutation安全性は `landformcraft-review-v2-placement-safety` を併用せよ。
