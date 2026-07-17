---
name: landformcraft-verify-release-artifacts
description: artifact、Schema、semantic checksum、strict read-back、Release 1/2、directory/ZIP parity、missing/extra/duplicate、traversal/bomb/tampering、atomic publishの実装・監査で使う。単一feature生成や通常testには使わず、validator内容とPhase gate判定は専用Skillへ渡す。
---

# LandformCraft Artifact・Release検証

## 使用条件

- Schema、codec、field sidecar、preview index、tile metadata、Sponge、Release publisher／verifierを変更するときに使え。
- Release capabilityを専用Taskで追加するときに使え。
- directory／ZIP parity、tampering、atomic publish、strict read-backを監査するときに使え。

## 使用しない条件

- 単一feature generatorや通常のdocs修正だけには使うな。
- generator-independent validatorの設計は `landformcraft-build-validation-preview` を使え。
- Phase末尾の `SUPPORTED` 判定は `landformcraft-audit-phase-gate` を使え。

## 必ず最初に読む正本

1. `AGENTS.md` のRelease／artifact、安全性、compatibility規則
2. `README.md`、`docs/architecture.md`、`docs/roadmap.md`
3. `docs/artifact-format.md`、`docs/security.md`
4. V2なら `docs/design-v2/task-execution-guide.md`、`task-index.md` の対象Task節、`migration-plan.md`、`generation-pipeline-v2.md`
5. 関連ADR、特にformat／sidecar／Release capabilityのAccepted ADR
6. 対象Schema、examples、publisher、verifier、codec、tampering tests
7. 作業前の `git status --short`

## 入力

- artifact type、version、capability、container format。
- required artifact集合とsemantic binding。
- byte、entry、decode、expanded、resident、disk budget。

## 手順

1. version dispatchを特定し、v1とv2のSchema、catalog、publisher、verifierを混在させるな。
2. artifact type/version、path、byte length、artifact checksum、semantic checksum、source provenance、必須集合を定義せよ。
3. canonical order、canonical JSON、exclude-self checksum、stable XYZ／row-major semantic streamを明示せよ。
4. stagingへ全fileを書き、各fileとindexをstrict read-backしてからcontainer全体をself-verifyせよ。
5. directoryとZIPへ同じstrict policyを適用し、valid内容のsemantic parityを検査せよ。
6. missing、extra、duplicate、case collision、unknown type/version/capability、checksum／semantic binding改変を拒否せよ。
7. absolute、`..`、backslash、symlink、hardlink alias、ZIP traversal、directory entry、bomb、truncated／trailing dataを検査せよ。
8. entry、compressed／expanded byte、decode、resident、palette、diskをallocation／extract前に上限検査せよ。
9. 最後のcancel check後にatomic moveだけを行い、moveをcommit pointにせよ。非atomic fallbackや暗黙上書きを許すな。
10. 直前capability集合とRelease 1 verifierの回帰を実行せよ。
11. 対象test、`./gradlew test`、`./gradlew build`、`git diff --check`を実行せよ。

## 検査項目

- Schemaがstrictでfuture versionやunknown fieldを拒否するか。
- manifest/index外fileと、indexが参照するfileの欠損を双方拒否するか。
- IR checksumをfinal block semantic checksumの代用にしていないか。
- Sponge圧縮差ではなくcanonical block-state streamを検査するか。
- directoryとZIPが同じartifact集合、binding、budget policyを使うか。
- capability名だけを予約状態でmanifestへ出していないか。
- read-back前にREADY／canonical targetを公開していないか。
- raw filesystem path、secret、provider payloadをportable artifactへ保存していないか。

## 成功条件

- valid directory／ZIPが同じsemantic結果でstrict verifyされること。
- corruption corpusがmissing／extra／unknown／tampering／path／bombを拒否すること。
- cancel／failureで未公開targetを残さず、commit後late cancelを正しく扱うこと。
- v1 Release 1と直前Release 2 capability集合が回帰すること。

## 停止条件

- 必須artifact集合、semantic checksum、budget、version policyを一意に固定できなければcapabilityを有効化するな。
- v1 allowlistを緩める、shared mutable catalogを使う、future artifactをfallbackする必要があれば停止せよ。
- atomic publishまたはstrict read-backを省略しなければ成立しない場合は停止せよ。

## 出力／報告形式

- 対象format／capabilityと必須artifact表
- canonicalization／checksum／binding規則
- directory／ZIP positive結果
- corruption／tampering／budget／cancel結果
- 直前capabilityとRelease 1回帰
- 実行したtest／build／diff check

## 関連Skillとの境界

- v1の意味全体は `landformcraft-guard-v1-compatibility` で監査せよ。
- checksum順序とresource peakは `landformcraft-audit-determinism-resources` を併用せよ。
- validation report／preview内容は `landformcraft-build-validation-preview` に任せよ。
- Release 2をworldへ適用する安全性は `landformcraft-review-v2-placement-safety` に任せよ。
