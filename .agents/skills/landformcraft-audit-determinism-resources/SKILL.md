---
name: landformcraft-audit-determinism-resources
description: determinism、whole/tile、tile順、thread数、module順、locale/timezone、checksum、memory/CPU/artifact budget、cancel cleanupの実装・監査で使う。通常の単体testやRelease tampering専用作業には使わず、artifact securityはRelease Skillへ渡す。
---

# LandformCraft 決定性・Resource監査

## 使用条件

- generator、field、graph、SDF、validator、preview、tile export、並列stageの結果決定性を変更するときに使え。
- memory、CPU、queue、artifact、disk、decode、cancel／interruptのbudgetを追加・監査するときに使え。
- Task IndexのD/M条件をAcceptance証拠へ落とすときに使え。

## 使用しない条件

- 通常の小さなunit test追加だけには使うな。
- ZIP traversal、missing／extra artifactなどsecurity中心なら `landformcraft-verify-release-artifacts` を使え。
- Phase全体の完了判定には `landformcraft-audit-phase-gate` を使え。

## 必ず最初に読む正本

1. `AGENTS.md` のthread、数値、tile、resource不変条件
2. `README.md`、`docs/architecture.md`、`docs/roadmap.md`
3. V2 Taskなら `docs/design-v2/task-execution-guide.md` と `task-index.md` の対象Task節
4. 対象に応じて `generation-pipeline-v2.md`、`world-blueprint-v2.md`、`hydrology.md`、`volumetric-terrain.md`、`validation-and-preview.md`
5. 対象Schema、production code、tests、fixtures
6. 作業前の `git status --short`

## 入力

- 対象stage、kernel、artifact、validatorまたはpipeline。
- 対象TaskのD/M条件と宣言budget。
- 比較する実行形態、runtime profile、最大寸法。

## 手順

1. canonical入力、結果、checksum、順序、rounding、seed、merge rule、commit pointを列挙せよ。
2. 数値がbranch/checksumへ影響する経路を追い、integer/fixed-point、overflow、rounding、stable reduction、`StrictMath`またはversion固定lookupを確認せよ。
3. whole、tile、seam、tile正逆順、thread数、module／candidate／point登録順の比較matrixを作れ。
4. locale、timezone、default charset、対応runtime profileを変え、field、metric、artifact、最終block checksumを比較せよ。
5. `retained + concurrency × task peak + cache + decode + queue + validation + preview + schematic buffer` を同一単位で積算せよ。
6. allocation前admission、stage並列上限、bounded queue、oversize拒否、peak instrumentationを検査せよ。
7. 1000×1000の全block Listまたは1000×1000×512 dense voxel保持がないことをcodeとtestで確認せよ。
8. cancel token、Future cancel、interrupt、job state、temporary cleanup、atomic commit前後の挙動をfault injectionで検査せよ。
9. 対象testと最大寸法budget test、`./gradlew test`、`./gradlew build`、`git diff --check`を実行せよ。

## 検査項目

- global X/Zまたはglobal X/Y/Zでsampleし、tile-local seed/noiseへ依存していないか。
- stage別halo／3D support radiusがBlueprintとbudgetへ宣言されているか。
- module登録順、thread完了順、HashMap iteration、object hash、enum ordinalへ結果が依存していないか。
- non-commutative mergeを暗黙last-write-winsで処理していないか。
- stable XYZ、row-major、canonical ID順などの順序をversion化しているか。
- worker数増加時の合計peakとqueue投入数を過小評価していないか。
- cancel前failureでcanonical targetやpartial bundleを残さず、commit後late cancelで公開物を削除していないか。

## 成功条件

- 宣言した実行matrixでfield、metric、semantic checksum、block streamが一致すること。
- 最大寸法と最大並列数で事前admissionと測定peakがbudget内であること。
- saturation、cancel、failure、cleanupがboundedかつ再現可能であること。

## 停止条件

- platform依存float、無制限収束、unbounded queue、dense world、暗黙mergeが正本に必要なら停止せよ。
- resource上限または最大並列数を安全に定義できなければ実装済み／supportedと報告するな。
- runtime差をgoldenで固定できない場合は対象runtimeをunsupportedとして停止せよ。

## 出力／報告形式

- canonical入力と比較対象matrix
- checksum／metric／block stream比較結果
- budget式、宣言値、測定peak、余裕
- cancel／failure injectionとcleanup結果
- 非決定要因またはunbounded resourceの指摘
- 実行したtest／build／diff check

## 関連Skillとの境界

- Task Scope管理は `landformcraft-execute-v2-task` に任せよ。
- v1 goldenの意味監査は `landformcraft-guard-v1-compatibility` に任せよ。
- artifact path、ZIP、tampering、strict read-backは `landformcraft-verify-release-artifacts` に任せよ。
- validator metricの独立性とpreview内容は `landformcraft-build-validation-preview` に任せよ。
