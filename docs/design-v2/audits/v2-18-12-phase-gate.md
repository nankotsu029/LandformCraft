# V2-18 Macro foundation / intent conformance integration audit and Phase gate

> Status: **PASS — 2026-07-23の人間承認で確定、V2-18親Phase CLOSED**。本文書は第一レビュー（2026-07-23、Conditional Approval）の指摘 — 欠番説明、vacuous pass防止、skipped test分類、保証範囲の分離、承認の原子性、finding集合・exact setのpin、sea stack会計 — を反映した**改訂第2版**であり、この改訂第2版に対して同日の人間承認（atomic decision）を得た。
>
> V2-18が確立する保証は2層であり、本gateは両方を実行可能な形で監査した。**(1) production fail-closed契約（runtime保証）**: HARD preflight（評価器なきHARD constraint／consumerなきHARD relation／未解決mapReference）、生成後のHARD EDGE conformance実測、surface foundation owner coverage 100%、およびkernel不変条件（`OWNERLESS_CELL`等）は、任意のrequestに対してproduction exportがartifact生成前・publish前に拒否する。**(2) conformance portfolio（release qualification保証、Phase gate）**: beach↔backshore連続性・breakwater armのlandfall・land-mass会計などの形状conformanceはproduction rejection ruleでは**なく**、production接続済みcoastal構成のportfolio fixtureをexport・再測定してPhase完了資格を判定するgateである。任意のユーザーrequestの形状不適合をproductionが必ず拒否するとは主張しない。capability・寸法は一切昇格しない。
>
> **`V2-15-47`／`V2-16-19` Acceptanceへのintent-conformance追記、V2-15 stage gate解除、V2-18親Phase閉鎖の3点は、model-assignmentが定めるレビュー体制（Opus 4.8＋人間承認）に従い、2026-07-23の人間承認（不可分のatomic decision）で同時に有効化された。V2-18親Phaseは`完了`、V2-15 stage gateはper-leaf義務体制へ移行した。**

## Decision（2026-07-23人間承認済み）

`V2-18-01`〜`V2-18-11`＋`V2-18-13`の12 leafはV2-18親gateを満たす。**`V2-18-12`は欠番でも未完了leafでもなく、本Phase gate Task自身である**（V2-12-07／V2-9-14等の先行Phase gateと同じ構成で、gate Taskはleaf一覧に含めない。V2-18の13 Task＝leaf 12＋gate 1）。未解決のcritical／high issueはなく、conformance portfolioは登録済み非適合ゼロで全case適合、full clean suiteはPASSである。2026-07-23の人間承認により次の3点が有効化された。

1. **conformance portfolioのgate化**（本監査で実装済み・実行可能）: `V2-18-11`で計測専用だったintent-conformance portfolioを、Phase gate資格へ昇格した。以後、portfolio caseは既知の形状非適合を「明示pin」として保持できない — 宣言arm集合とshore-connected arm集合の不一致、EDGE HARD不充足、beach↔backshore連続性の破れ、宣言armのlandfall不達はPhase gate失敗であり、欠陥は隠さず新しいTask IDへ分離する（`coastal-honored-400`東armが`V2-18-13`へ分離された前例に従う）。
2. **`V2-15-47`／`V2-16-19` Acceptanceへのintent-conformance追記**: 両Phase gateのAcceptanceへ「公開接続した各kindのconformance portfolio case登録＋portfolio全case適合（登録済み非適合ゼロ）の再検証」を追加する（[Task Index §15.2／§16.2](../task-index.md)へ追記済み、承認待ちを明記）。
3. **V2-15 stage gate解除判断**: 前提の`V2-18-09`（macro foundation production spine）は完了済みのため、production export／placement昇格系leafへの**一律保留を解除**し、各配線leafの**per-leaf義務**へ転換する — (a) 対象kindのcomposition profileをfield監査でPROVISIONAL→確定する（ADR 0038 D4。対応表の変更が必要ならADR amendment）、(b) 新規接続kindのintent-conformance portfolio caseを追加し、portfolio全case適合を維持する。承認までは現行stage gateが継続する。

本gateはcapabilityを昇格しない。Release 2 capability listは`[environment-fields, hydrology-plan, sparse-volume, surface-2_5d]`のまま、`paper_apply: SUPPORTED`は`surface-2_5d`の4 featureのみ、`PlacementDimensionLimitV2`は`measured()`（FAWE 2.15.2 evidenceの1000×1000／WorldEdit単独64×64）のままである。Beta hardening未完項目は放棄しない。

## 実行可能gate: `MacroFoundationPhaseGateV2Test`

`integration/v2/conformance/MacroFoundationPhaseGateV2Test`（5 method）が、phase固有の主張を1つの実行可能な単位として固定する。

### 1. Conformance portfolioのgate化（`V2-18-11`／`V2-18-13`）

`theConformancePortfolioGatesThePhaseWithNoRegisteredNonConformance`は、gate自身が両portfolio case（`coastal-honored-400`／`harbor-cove-64-honored`）をproduction export service（`Release2ExportApplicationServiceV2`）でexportし、その**atomic publish済みRelease成果物**（sealed intent／frozen blueprint／ACTUAL land-water sidecar。gate実行内で生成するもので、外部配布済みの意味ではない）だけを再測定して、caseごとに次を要求する。

- **登録済み非適合ゼロ:** `declaredArmIds == shoreConnectedArmIds`。V2-18-11では正当だった「既知非適合の明示pin」は、Phase gate下では失敗として扱う。
- **非空虚性（vacuous pass防止）:** sealed intentがproduction接続済みcoastal 4 kind＋`BACKSHORE_PLAINS`を全て実在宣言していること、HARD EDGE constraint集合が非空であること、宣言arm集合が非空で実測arm ID集合と完全一致すること、beach land band／nearshore band／hinterland polygon cellがそれぞれ非空であることをassertする。「存在するものが全て正しい」だけでなく「存在すべきものが実在する」ことをgate条件に含み、空集合では通らない。加えてmeasurement kernel自体が、`BREAKWATER_HARBOR`／`BACKSHORE_PLAINS`を欠くcaseを例外で失敗させる。
- **EDGE conformance＋intent→blueprint非脱落:** 公開blueprintの`v2.edge-classification` targetをproductionの`EdgeClassificationEvaluatorV2`（version 1 edge band）で再評価し、全targetがHARDかつ充足。さらに**sealed intentのHARD EDGE constraint ID集合＝実測target ID集合**をassertし、constraintがblueprint化の途中で脱落（または捏造）されても「全target適合」でPASSできないようにした。
- **beach↔backshore land連続性:** beachがeffective ownerであるFORESHORE／BACKSHORE cellが全てland・単一land component・mainland所属、NEARSHOREは全てwater、宣言`BACKSHORE_PLAINS` polygonは全cell landかつ同一mainland。
- **breakwater landfall:** 全宣言armがoff-structure mainlandへ8近傍接触し、宣言landfall cellがmainlandにある。
- **land-mass会計:** mainland以外のland componentは、blueprintが計画したrocky cape sea stack数を超えない（計画stackはshoreへ併合され得るため実測componentは計画以下になり得る。実測: 400 caseは計画5→独立3、64 caseは計画2→独立1）。かつmainland外のland cell総量が計画stack footprint上限（各stackの`(2r+1)²`の総和）以内。V2-18-13の欠陥class（2163 cellの孤立arm）はcomponent数・面積のどちらの上限も満たせない。owner-indexによる帰属attributionまでは実装していない（portfolio measurement型の拡張が必要なため。将来caseがこの上限で不足する場合の強化候補として記録する）。

production経路へ新しいgate・ruleは追加していない（portfolioのgate化はtest／acceptance層の昇格であり、export spineの拒否条件は`V2-18-03`／`04`／`10`が配線済みのものから不変）。したがって本節の連続性・landfall・land-mass会計は**release qualification保証**であり、runtime保証（次節）と混同しない。

#### Portfolio caseカバレッジ

| Case | サイズ | Foundation入力 | 宣言kind | HARD EDGE | Beach／backshore | Breakwater | Sea stack（計画→独立component） |
|---|---:|---|---|---|---|---|---|
| `coastal-honored-400` | 400×400 | HARD `LAND_WATER_MASK`＋`foundationBaseLevels` | coastal 4＋`BACKSHORE_PLAINS`（carrier=`PLAIN`） | north-is-land／south-is-sea（実測1.000000） | あり（連続性・mainland所属） | 2 arm（両方landfall接続） | 5→3 |
| `harbor-cove-64-honored` | 64×64 | HARD `LAND_WATER_MASK`＋`foundationBaseLevels` | coastal 4＋`BACKSHORE_PLAINS`（carrier=`PLAIN`） | north-is-land／south-is-sea（実測1.000000） | あり（同上） | 2 arm（両方landfall接続） | 2→1 |

production接続済み4 kind（`SANDY_BEACH`／`BREAKWATER_HARBOR`／`HARBOR_BASIN`／`ROCKY_CAPE`）は**両case**で全て宣言・生成・評価され、gateが非空虚性assertで実在を強制する。case数2はproduction接続面（coastal 4種＋macro foundation）に対する現時点の全量であり、V2-15配線leafがkindを接続するたびにper-leaf義務（後述）でcaseが追加される。

### 2. Fail-closed export spine契約の不変性（`V2-18-01`〜`04`、`10`）

`theFailClosedExportSpineContractsAreUnchanged`が次をpinする。

- `DiagnosticGateContractV2` = `diagnostic-gate-contract-v1`、production接続kindはcoastal 4種（`SANDY_BEACH`／`BREAKWATER_HARBOR`／`HARBOR_BASIN`／`ROCKY_CAPE`）のみ（`V2-18-01`）。
- `IntentContributionCoverageV2` = `intent-contribution-coverage-v1`（report-only、`V2-18-02`）。
- `HardPreflightGateV2`の3 stable rule id: `v2.preflight.hard-constraint-unevaluated`／`v2.preflight.hard-relation-unconsumed`／`v2.preflight.map-reference-unresolved`（`V2-18-03`）。
- `TargetDrivenValidatorV2.BUILT_IN_EVALUATED_CONSTRAINT_RULES` ∋ `v2.edge-classification`（EDGEはpreflightではなく生成後実測、`V2-18-04`）。
- `SurfaceFoundationOwnerGateV2` = `surface-foundation-owner-gate-v1`、rule `v2.export.foundation-owner-coverage-incomplete`、必要被覆1,000,000 millionths（=100%、override無し、`V2-18-10`）。

### 3. Legacy negative fixtureのartifact生成前拒否（`V2-18-03`）

`aNonHonorableLegacyIntentNeverReachesArtifactPublication`は、監査の原fixture `coastal-fishing-map`をfull production export serviceでexportし、`HardPreflightRejectedV2`で**artifactが1つも生成される前に**拒否されること、exports rootが空のままであることを固定する。例外型だけでなく**finding集合を完全一致でpin**する — `v2.preflight.hard-constraint-unevaluated` → subject `{beach-width}`、`v2.preflight.hard-relation-unconsumed` → subject `{backshore-adjoins-beach}`のrule→subject mapを`assertEquals`し、一方の検出が失われても、subjectが別要素へ漂流してもgateが失敗する。

### 4. ADR 0038 D4対応表との照合（`V2-18-08`／`09`）

`theCompositionProfileRegistryMatchesTheAcceptedAdr0038Table`が、`CompositionProfileRegistryV2`（`composition-profile-registry-v1`）を`Accepted`済みADR 0038 D4と照合する: 60 kind全登録、NORMATIVE 6（coastal 4＋`PLAIN`／`HILL_RANGE`）／PROVISIONAL 54、foundation-eligible 17、`ABYSSAL_PLAIN`のmodifier再分類、alias／subtypeのcanonical carrier継承（`BACKSHORE_PLAINS`→`PLAIN`）。PROVISIONAL kindの確定は各V2-15配線Taskのfield監査だけが行う — stage gate解除判断のper-leaf義務の実行可能な根拠である。

**期待値の更新規則（governance）:** V2-15配線leafがkindをNORMATIVE化すると本testは意図的に失敗する。その際は (1) 配線Task内でprofileのfield監査を行い、(2) 対応表の変更が必要ならADR 0038 amendmentを先に`Accepted`化し、(3) その後に本gateの期待値（NORMATIVE集合・件数）をregistry実装と同一commitで更新する。**test期待値だけを変更して通すことは禁止する** — 期待値変更は必ずregistry／ADRの正本変更と対で行う。

### 5. Capability false-promotionなし

`thePhaseGatePromotesNoCapabilityOrDimension`が、offline conformance PhaseであるV2-18が何も昇格していないことを、件数ではなく**正確な集合の完全一致**で固定する。

- `paper_apply: SUPPORTED`のentry ID集合 ＝ `{SANDY_BEACH, BREAKWATER_HARBOR, HARBOR_BASIN, ROCKY_CAPE}`（別entryとの入れ替わりを検出）。
- `PlacementDimensionLimitV2.measured()`（1000×1000受理／1001×1000拒否）。
- sealed catalog（`examples/v2/catalog/feature-support-catalog-v2.json`）のchecksumがbuilt-in sealedと一致。
- Release 2 capability list ＝ `[environment-fields, hydrology-plan, sparse-volume, surface-2_5d]`（順序込み完全一致）。
- capability prefix集合 ＝ `{[], [surface-2_5d], [hydrology-plan, surface-2_5d], [environment-fields, hydrology-plan, surface-2_5d], [environment-fields, hydrology-plan, sparse-volume, surface-2_5d]}`（`ReleaseCapabilityDependencyMatrixV2.validPrefixes()`との完全一致）。

## 全leaf再検証（full clean suite）

leaf corpora（`V2-18-01` diagnostic gate golden、`V2-18-02` coverage report、`V2-18-03` preflight negatives、`V2-18-04` EDGE evaluator、`V2-18-05` breakwater実測metric、`V2-18-06` constraint-map binding negatives＋determinism、`V2-18-07` conformance residual契約、`V2-18-09` macro foundation kernel不変条件、`V2-18-10` owner gate fail-closed negatives、`V2-18-11`／`13` portfolio＋locale／timezone／Release複製determinism）は、本gateのfull clean suiteで全件再実行した。

- 実行可能gate: `integration/v2/conformance/MacroFoundationPhaseGateV2Test`（5 method、6実行）。
- Full clean suite: `./gradlew clean test build` — **1217 tests、0 failures、0 errors、9 skipped（215 test class）、BUILD SUCCESSFUL**（2026-07-23）。
- v1回帰: D2b legacy read／verify／migrate境界とK1 immutable golden archiveの等価経路（`LegacyV1GoldenArchiveTest`ほか）はfull suiteで不変にPASS。
- 直前Release capability回帰: 4 capability verifierとplacement lifecycle portfolioはfull suiteで不変にPASS。

### 実行環境（再現性記録）

HEAD `572d187`＋本Task／`V2-18-13`の未commit差分（承認対象SHAはcommit時に確定し承認記録へ記載する）、OpenJDK 21.0.11（Ubuntu）、Gradle 9.6.1、Linux（Ubuntu 26.04系）、既定locale `ja_JP.UTF-8`／timezone `Asia/Tokyo`。locale／timezone determinismはtest内部で`tr-TR`／`Pacific/Chatham`へ切替えて検証しており、既定環境値へ依存しない。

### 9 skipped testの分類（全件、V2-18 Acceptance外）

| Test | 種別 | skip条件と根拠 |
|---|---|---|
| `V2614WorldEditSmokeFixtureExporterTest` | 実機fixture exporter | V2-6-14 export directory未設定時はassumptionでabort。実機WorldEdit smoke用のopt-in。evidenceは`V2-6-14`で取得済み |
| `V21104MeasurementFixtureExporterTest`／`V21105MeasurementFixtureExporterTest` | 実機fixture exporter | V2-11-04（500）／V2-11-05（1000）実機計測用。同上、evidenceは2026-07-20取得済み |
| `V21304MeasurementFixtureExporterTest`／`Medium1024OfflineBudgetMeasurementV2Test.heavyProbe…` | 実機／heavy probe | V2-13-03／V2-13-04の計測用opt-in。evidenceは各audit取得済み |
| `RegenerateHydrologyExampleChecksumsTest`／`PlacementSnapshotAllCompilerV2Test.rewriteSnapshotPlanExample`／`RegenerateFeatureSupportCatalogExampleTest` | 手動example再生成helper | `@Disabled`。実行するとtracked exampleを上書きするため既定無効（V2-12-11のtest-hygiene方針） |
| `RegenerateHonoredCoastalMaskExampleTest.rewriteHonored400Mask` | 手動fixture再生成helper（唯一のV2-18関連skip） | `@Disabled`。`V2-18-13`のmask再生成tool（実行時にtracked maskを上書き）。合成規則との一致は`V2-18-13`実行時にbyte一致で手動検証済みであり、fixtureの**適合性そのもの**はactiveなportfolio／本gateが公開Release実測で常時検証する |

**V2-18のAcceptanceを構成するtest（portfolio、owner coverage gate、preflight／binding negatives、EDGE evaluator、determinism、legacy回帰、capability false-promotion、本gate）はいずれもskipされていない。** 9件は全て (a) 実機・環境依存のopt-in exporter／probe（`BLOCKED_EXTERNAL`系Taskの実機evidenceは各Task完了時に取得済み、Beta blockerはrelease checklistで別途追跡）か (b) tracked fileを書き換える手動再生成helperであり、offline Acceptanceの証拠には含まれない。

## Checksum／compatibility境界

V2-18はterrain field／tile／blockの**semantic checksumの意味論**を変更していない。V2-18内の容器byte変化（coastal diagnostic容器、intent／blueprint canonicalChecksum、Release manifest byte — `V2-18-05`／`07`／`09`／`11`／`13`）はすべてADR 0038 D9の承認範囲内として各leafで記録済みであり、本gateは新たなchecksum変化を一切追加しない（gate testは計測のみ、tracked fileへ何も書かない）。Schema `$id`／capability名／artifact type・version／contract ID／error code等の凍結識別子の変更はない。Release format変更なし、新capability名なし。

## Findings

**V2-18 scope内の新規critical／high findingなし。** 全leaf再検証・portfolio再測定・ADR照合・false-promotion回帰で、新しい設計欠落・回帰は検出されなかった。`V2-18-11`が検出した唯一の形状非適合（`coastal-honored-400`東arm landfall）は`V2-18-13`が是正済みで、本gateの再測定でも両arm接続を確認した。mainland外のland componentは3個で、blueprint計画のrocky cape sea stack（5個。うち2個はshore併合）の数・footprint上限内に収まる（§1のland-mass会計。owner-index帰属attributionは未実装と明記済み）。

既知のBeta blocker（実機系release checklist項目）および実機依存項目は本Phaseの**非Scope**として継続する — 本gateはそれらを閉じも隠しもしない（[Release decision](#release-decision)）。

## Release decision

**リリースなし — Beta blockerは残存する。** 本gateはV2-18親Phaseの技術的完了を確立するが、[beta-release-checklist.md](../../beta-release-checklist.md)の未完項目（実機系）を閉じない。V2-17のPaper evidence Phaseは`V2-15-47`／`V2-16-19`完了後にのみ開始する。

## 人間承認記録（2026-07-23、atomic decision）

**2026-07-23、本文書（改訂第2版）に対する人間承認を得た。承認は次の3項目を不可分のatomic decisionとして承認したものであり、一部のみの有効化ではない**（3項目は相互依存する: Acceptance追記なしのstage gate解除はconformance義務なき昇格経路を開き、Phase閉鎖なしの追記は正本の出所を失う）。

1. `V2-15-47`／`V2-16-19` Acceptanceへのintent-conformance追記（[Task Index §15.2／§16.2](../task-index.md)の追記文言）— **発効済み**。
2. V2-15 stage gate解除（一律保留→per-leaf義務への転換）— **発効済み**。以後、production export／placement昇格系leafは、対象kindのcomposition profile確定（ADR 0038 D4のPROVISIONAL→確定、変更時はADR amendment先行）とintent-conformance portfolio case追加・全case適合維持を各leafのAcceptanceとして負う。
3. V2-18親Phase閉鎖（roadmap Track A行とTask Index §18の完了反映）— **発効済み**。

承認対象は次の成果物である: 本Phase gate文書、[Task Index](../task-index.md) §15.2／§16.2／§18、[roadmap](../../roadmap.md)、[model-assignment](../model-assignment.md) §9.1／§9.2、`MacroFoundationPhaseGateV2Test`。承認時点の作業treeはHEAD `572d187`＋本Task／`V2-18-13`の未commit差分であり、これらを収めるcommitが承認済み状態の正本SHAとなる（commit時にSHAを本節へ追記してよい）。

task-index §15.2／§16.2の追記文言・stage gate解除・親Phase閉鎖の各表記は、承認を受けて「人間承認待ち」から承認日付（2026-07-23）へ更新済みである。

## Verification commands

```text
./gradlew test --tests '*MacroFoundationPhaseGateV2Test'
./gradlew test --tests '*IntentConformancePortfolioV2Test' --tests '*ConformanceTargetSetV2Test' --tests '*RegenerateHonoredCoastalMaskExampleTest'
./gradlew clean test build
git diff --check
git status --short
```
