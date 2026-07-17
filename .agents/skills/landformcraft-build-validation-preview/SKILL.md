---
name: landformcraft-build-validation-preview
description: validator、ValidationTarget、MetricResult、positive/negative/corruption fixture、stable reduction、diagnostic preview/index、bounded renderingの実装・監査で使う。generator単体やRelease containerだけの作業には使わず、artifact packagingはRelease Skillへ渡す。
---

# LandformCraft Validation・Preview workflow

## 使用条件

- feature／field／graph／volume validator、cross-feature rule、corruption fixtureを実装するときに使え。
- metric、issue、ValidationTarget、preview registry/index、diagnostic layerを追加するときに使え。
- feature lifecycleのvalidator／preview証拠を整備するときに使え。

## 使用しない条件

- generatorだけの実装や通常のunit test追加だけには使うな。
- Release directory／ZIPへの収容だけなら `landformcraft-verify-release-artifacts` を使え。
- Phase gateでの最終 `SUPPORTED` 昇格判断には `landformcraft-audit-phase-gate` を使え。

## 必ず最初に読む正本

1. `AGENTS.md` のvalidation、preview、resource、feature lifecycle規則
2. `README.md`、`docs/architecture.md`、`docs/roadmap.md`
3. `docs/design-v2/validation-and-preview.md`
4. V2なら `docs/design-v2/task-execution-guide.md` と `task-index.md` の対象Task節
5. 対象featureの設計文書、TerrainIntent／WorldBlueprint docs、Schema、examples
6. generator／query／artifactの公開境界、既存validator、renderer、tests
7. 作業前の `git status --short`

## 入力

- 対象feature、field、graph、volumeまたはartifact。
- hard／soft／invariant条件、metric、tolerance、sampling scope。
- 必須preview layerと最大寸法／byte／count budget。

## 手順

1. Contract、Blueprint、baseline、field、graph、feature、cross-feature、artifact、placementのどの層を検証するか分類せよ。
2. rule ID/version、subject、scope、metric、unit、aggregation、target、tolerance、hardness、diagnostic layerを定義せよ。
3. validatorをfreeze済みBlueprint、public `TerrainQuery`／resolver、sealed field／artifactから実装せよ。
4. generator-private array、task-local `evaluate()`、同じ内部判定結果を自己承認に使うな。
5. 正常fixtureと、対象ruleだけを意図的に壊すnegative／corruption fixtureを作れ。
6. missing metricを成功や0点へfallbackせず、required targetの未計測を失敗にせよ。
7. traversal、percentile、component、cross-section、reductionの順序とroundingをversion固定せよ。
8. desired／actual／residual、feature overlay、error layerをstable paletteと座標で可視化せよ。
9. previewを1枚ずつrender、write、checksum、解放し、indexと全fileをstrict read-backしてatomic publishせよ。
10. duplicate／unknown／missing／extra layer、path、checksum、dimension、pixel／byte／count budget、cancel cleanupをtestせよ。
11. whole/tile、thread、locale、timezoneでmetricとpreview checksumを比較せよ。
12. 対象test、`./gradlew test`、`./gradlew build`、`git diff --check`を実行せよ。

## 検査項目

- baseline invariantをmodule validator成功で省略していないか。
- hard失敗をsoft scoreで相殺していないか。
- message文字列ではなくrule ID、metric、evidence checksumを機械判定の正本にしているか。
- corruption fixtureがgeneratorと同じbugを共有しない独立観測面を使うか。
- previewがblock量子化後のgeometryとfieldを表示するか。
- full-resolution ARGB copy、全issue、全volumeを無制限保持していないか。
- v1固定8 previewとRelease 1 allowlistを変えていないか。
- validator／rendererをPaper main threadで実行していないか。

## 成功条件

- positive fixtureがhard validationを通ること。
- corruption fixtureが期待rule IDとscopeで失敗すること。
- metric reductionとpreview checksumが実行matrixで安定すること。
- preview index、全layer、budget、cancel cleanupがstrict read-backされること。

## 停止条件

- generator-private stateなしで検証できなければ、公開query／artifact境界をTask Scope内で定義できるか確認し、できなければ停止せよ。
- unbounded scan/render、platform依存metric、曖昧なhard/soft変換が必要なら停止せよ。
- validator／previewだけでfeatureを `SUPPORTED` にするな。

## 出力／報告形式

- validation層、rule／metric／hardness表
- positive／negative／corruption fixture結果
- generatorからの独立性の証拠
- preview layer/indexとbounded rendering結果
- determinism／budget／cancel結果
- 実行したtest／build／diff check

## 関連Skillとの境界

- Task Scopeとdocs同期は `landformcraft-execute-v2-task` に任せよ。
- reduction順とmemory matrixは `landformcraft-audit-determinism-resources` を併用せよ。
- preview bundleやvalidation artifactのRelease収容は `landformcraft-verify-release-artifacts` に任せよ。
- lifecycleの最終昇格は `landformcraft-audit-phase-gate` に任せよ。
