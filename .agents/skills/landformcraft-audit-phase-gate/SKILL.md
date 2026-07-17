---
name: landformcraft-audit-phase-gate
description: 明示されたPhase末尾のintegration／gate audit Taskだけで使う。全Task証拠、SUPPORTED昇格、capability、determinism/resource/security、v1回帰、clean buildを監査する。単一feature、通常docs、Phase途中Taskでは使わず、実装作業はTask実行Skillへ戻す。
---

# LandformCraft Phase gate監査

## 使用条件

- ユーザーがPhase末尾のintegration／gate audit TaskまたはこのSkillを明示した場合だけ使え。
- Task Indexで対象Taskが親Phaseを閉じる唯一のTaskであることを確認せよ。
- Phase全体のevidence portfolioとlifecycle昇格を監査せよ。

## 使用しない条件

- 単一feature実装、通常のdocs修正、一般的なtest、Phase途中Taskには使うな。
- Phase末尾以外で `SUPPORTED` を付けるために使うな。
- 大規模bug修正や新機能を監査Taskへ抱え込むために使うな。

## 必ず最初に読む正本

1. `AGENTS.md`
2. `README.md`、`docs/architecture.md`、`docs/roadmap.md`
3. `docs/design-v2/task-execution-guide.md`
4. `docs/design-v2/task-index.md` の対象Phase全Taskとgate Task節
5. `docs/design-v2/implementation-roadmap.md`
6. 対象Phaseの全関連設計文書、ADR、Schema、examples、implementation、tests、既存audit
7. 作業前の `git status --short`

Task ID、完了件数、現在PhaseをこのSkillへ固定するな。毎回roadmapとTask Indexから確定せよ。

## 入力

- 明示されたPhase gate Task ID。
- 同PhaseのTask一覧とAcceptance証拠。
- feature／capability lifecycle、external evidence、既知risk。

## 手順

1. 対象がTask Index上のPhase末尾gate Taskか確認せよ。違えばこのSkillを停止し、Task実行Skillへ切り替えよ。
2. roadmapとTask Indexの前提、完了状態、再open／追加Task、未完external Taskを照合せよ。
3. 同Phaseの各TaskについてScope、Acceptance、D/M/S、docs更新、test証拠をevidence matrixへまとめよ。
4. positive、negative、corruption、tampering、whole/tile/thread/module/locale/timezone、最大寸法budget、cancel cleanupを再実行または証拠照合せよ。
5. featureごとにcontract、generator、validator、preview、positive/corruption fixture、resource budget、Release strict capabilityが揃うか確認せよ。
6. capabilityのrequired artifact集合、directory／ZIP、直前capability回帰、future／missing／extra／tampering拒否を確認せよ。
7. v1 Schema、generator、Release 1、placement、Undo goldenとclean buildを確認せよ。
8. 実機依存Taskはexact build／version／environmentの証拠を確認し、欠ければ `BLOCKED_EXTERNAL` のままにせよ。
9. 小規模gate defectだけが対象Task内で安全に直せるか判定せよ。大規模修正は原因Taskをreopenするか新Taskを提案せよ。
10. 全gateが通った場合だけ、Task Indexが許すfeature／capability／Phase状態とroadmapを更新せよ。
11. `./gradlew test`、`./gradlew build`、`git diff --check`、`git status --short`を実行せよ。

## 検査項目

- 未完、再open、追加Task、critical/high riskを見落としていないか。
- 設計例やunit testだけをend-to-end証拠と誤認していないか。
- individual feature Taskの成果を統合監査前に `SUPPORTED` としていないか。
- generatorとvalidatorが同じprivate stateを共有していないか。
- capabilityがstrict verifierとtampering portfolioを持つか。
- maximum dimension、memory、artifact、CPU、diskのgateが測定またはbounded testで閉じているか。
- v1回帰とBeta hardeningの別未完項目を勝手に閉じていないか。

## 成功条件

- Phaseの全必須TaskとAcceptance証拠が揃うこと。
- lifecycle、capability、determinism、resource、security、compatibility、clean buildの全gateが通ること。
- roadmap、implementation roadmap、README、auditが実態に一致すること。

## 停止条件

- 前提未完、証拠欠損、未解決risk、budget未計測、external環境不足ならPhaseを閉じるな。
- 大規模修正、独立subsystem、format再設計が必要なら原因Taskをreopenするか新Taskを提案せよ。
- Acceptanceを弱める、未完を完了と書く、mockで実機gateを代用する必要があれば停止せよ。

## 出力／報告形式

- Phase／gate Taskと前提照合
- Task別evidence matrix
- feature lifecycle／capability判定
- determinism／resource／security／v1回帰結果
- external evidenceと `BLOCKED_EXTERNAL` 項目
- Phaseを閉じる／閉じない判断と根拠
- reopen／追加Task案
- 実行したtest／build／diff check

## 関連Skillとの境界

- defect修正は `landformcraft-execute-v2-task` のScopeへ戻せ。
- v1、決定性、Release、validation、placementの各portfolioは対応する専用Skillを併用せよ。
- このSkillは証拠を統合してgateを判定し、新feature workflowを代替しない。
