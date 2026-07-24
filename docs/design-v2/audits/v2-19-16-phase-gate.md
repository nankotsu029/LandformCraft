# V2-19 Input integrity / block materialization integration audit and Phase gate

> Status: **PASS — 2026-07-24の人間承認で確定、V2-19親Phase CLOSED**。本gateの技術的検証（実行可能gate、全leaf再検証、full clean suite）は2026-07-24に完了し、model-assignmentが定めるレビュー体制（Opus 4.8＋人間承認）に従い、同日の人間承認（不可分のatomic decision）で`V2-19-16`は完了、V2-19親Phaseは閉じた。
>
> V2-19が確立する保証は2層であり、本gateは両方を実行可能な形で監査した。**(1) production fail-closed契約（runtime保証）**: V2-18スパイン（HARD preflight、生成後EDGE実測、owner被覆100%、kernel不変条件）は不変のまま、V2-19はその上へ river実体化interaction（route freeze乖離／marine contact／leak envelope／owner conflict／垂直・予算境界の各拒否）、role別constraint map cardinality、producer tierのkernel不変条件、閉じたmaterial語彙（構築時allowlist強制）、detail／reconcileの宣言時fail-closedを追加した。**(2) semantic materialization gate＋conformance portfolio（release qualification保証、Phase gate）**: `V2-19-01`のmaterialization gateは「公開配線leafはfinal canonical block streamからの非空効果・形状conformanceを証明しない限り閉じられない」を発効済みで、本gateはその発効を再実測し、ADR 0040のcontributor-subset caseを含むportfolio全10 caseをPhase gate資格へ昇格した。capability・寸法は一切昇格しない。

## Decision（2026-07-24人間承認済み）

`V2-19-01`〜`V2-19-15`の15 leafはV2-19親gateを満たす。**`V2-19-16`は本Phase gate Task自身である**（V2-18-12／V2-12-07等の先行Phase gateと同じ構成で、gate Taskはleaf一覧に含めない。V2-19の16 Task＝leaf 15＋gate 1）。未解決のcritical／high issueはなく、conformance portfolioは登録済み非適合ゼロで全10 case適合、materialization gateは全公開routeで発効済み、full clean suiteはPASSである。2026-07-24の人間承認により次の3点が不可分のatomic decisionとして有効化された。

1. **conformance portfolioのPhase gate範囲拡大**（本監査で実装済み・実行可能、**発効済み**）: `V2-18-12`が4-contributor構成8 caseへ限定していたPhase gate資格を、ADR 0040 D1のcontributor-subset case（`harbor-cove-64-honored-beach`＝サイズ1、`harbor-cove-64-honored-coastless`＝サイズ0）を含む**portfolio全case**へ拡大する（`V2-18-12` gateコードが明記した引き継ぎの実行）。以後、subset caseも既知の形状非適合を「明示pin」として保持できない — 非適合はPhase gate失敗であり、欠陥は新しいTask IDへ分離する。gateの拡大であり、`V2-18-12`の既存assertionは1つも弱めない。
2. **`V2-19-16`の完了** — **発効済み**: 全leaf再検証、materialization gate発効確認、V2-15再開状態確認、full clean suiteの4条件充足をもって本TaskはCOMPLETEである。
3. **V2-19親Phase閉鎖**（roadmap Track A行とTask Index §19の完了反映）— **発効済み**: Track Aの残りはV2-17（`V2-15-47`／`V2-16-19`完了待ち）のみである。

本gateはcapabilityを昇格しない。Release 2 capability listは`[environment-fields, hydrology-plan, sparse-volume, surface-2_5d]`のまま、`paper_apply: SUPPORTED`は`surface-2_5d`のcoastal 4 featureのみ、`RIVER`／`MEANDERING_RIVER`のPaper 5列は`EXPERIMENTAL`、`PLAIN`のPaper 5列は`UNSUPPORTED`・`standalone_usage`は`PARTIAL`（ADR 0040 D7）、`PlacementDimensionLimitV2`は`measured()`（FAWE 2.15.2 evidenceの1000×1000／WorldEdit単独64×64）のままである。Beta hardening未完項目は放棄しない。

## 実行可能gate: `InputIntegrityPhaseGateV2Test`

`integration/v2/conformance/InputIntegrityPhaseGateV2Test`（6 method、15実行）が、phase固有の主張を1つの実行可能な単位として固定する。gateは全10 portfolio caseを自らproduction export service（`Release2ExportApplicationServiceV2`／`Release2HydrologyExportApplicationServiceV2`、ADR 0039 Candidate Aのroute別）でexportし、**atomic publish済みRelease成果物だけ**を再測定する。

### 1. Portfolio全caseのgate化（subset昇格、`V2-18-12`引き継ぎの実行）

`theGateCoversTheWholePortfolioIncludingContributorSubsets`（drift guard: 全10 case・4-contributor 8 case・subset 2 caseの構成を固定）と`theConformancePortfolioGatesThePhaseWithNoRegisteredNonConformance`（全10 case parameterized）が、caseごとに次を要求する。

- **登録済み非適合ゼロ:** `declaredArmIds == shoreConnectedArmIds`。
- **ADR 0040下の非空虚性:** caseの宣言contributor集合＝sealed intentの実宣言集合（coastal 4＋`BACKSHORE_PLAINS`との交差で完全一致）。黙ったcontributor脱落も混入もgate失敗。
- **測定の有無＝宣言の有無:** beach測定は`SANDY_BEACH`宣言時のみ実在、backshore測定は`BACKSHORE_PLAINS`宣言時のみ実在、arm測定ID集合＝宣言arm集合。欠測をゼロ値で代替しない。
- **EDGE conformance＋intent→blueprint非脱落:** サイズ0（coastless）を含む全caseが非空のHARD EDGE契約を宣言し、sealed intentのHARD EDGE constraint ID集合＝実測target ID集合、全targetがHARDかつ充足。macro foundation単独でも宣言構図を充足することがcoastless caseで実測される。
- **beach↔backshore連続性**（beach宣言時）: 単一mainland上のland band、nearshore全water、hinterland同一mainland。
- **breakwater landfall**（arm宣言時）: 全armがoff-structure mainlandへ接触し宣言landfall cellがmainland。
- **land-mass会計:** `ROCKY_CAPE`宣言caseは計画sea stack数・footprint上限内（V2-18-12規則）。非宣言caseもland／mainland非空（HARD maskをmacro foundationが単独で充足した証拠）。

### 2. Materialization gateの発効（`V2-19-01`／`05`／`07`）

`theSemanticMaterializationGateIsInEffectForEveryPubliclyRoutedKind`が、phaseの中核主張「公開routeにplan-only kindは残っていない」を固定する。

- `public-dispatch-reachability-v1`の3軸表示: PRODUCTION_CONNECTED＝coastal 4、OFFLINE_PRODUCTION＝`{RIVER, MEANDERING_RIVER, PLAIN}`、CONTRACT_ONLY＝`{BACKSHORE_PLAINS}`の完全一致。
- **offline route全kindがMATERIALIZED、PLAN_ONLY集合は空**。監査が確定した欠陥class（validation JSONを公開しblockを1個も変えないroute）の残存インスタンスはゼロ。
- MATERIALIZEDはラベルでないことの再実測: gate自身のexportした公開Releaseからbaseline比block effectを再計測し、`RIVER`／`MEANDERING_RIVER`は`{SOLID_SHAPE, FLUID}`、`PLAIN`は`{SOLID_SHAPE, MATERIAL}`の宣言effect classと完全一致で`requireMaterialized`通過。両river kindのeffect同一性も再確認。
- **gate自体の効力:** plan column（`hydrology/validation.json`全metric PASS）が実在してもidentity stream（自己diff、changedCells 0）は必ず拒否される — 定数healthy sampler／identity sliceの共通形はFeature昇格に使えない。

### 3. Fail-closed export spine契約（V2-18スパイン不変＋V2-19追加分）

`theFailClosedExportSpineContractsIncludeTheV219Additions`が次をpinする。

- V2-18スパイン不変: `diagnostic-gate-contract-v1`（production接続kind＝coastal 4）、preflight 3 rule、`v2.edge-classification`評価、`surface-foundation-owner-gate-v1`（rule／被覆1,000,000 millionths）。
- V2-19追加分: `production-dispatch-registry-v2`（ADR 0039 offline route class）、`design-support-lint-v1`（advisory固定）、**全pipelineのcompanion要求空**（per-pipeline＋union。ADR 0040 D5 — 監査の「beach単体はexportできない」前提条件の消滅であり移設でないこと）、`surface-material-profile-v1`／`surface-material-binding-v1`／`environment-surface-material-v1`（閉じた語彙）、`coherent-detail-fixed-v1`、`mask-feature-reconcile-v1`。
- package-privateな契約定数（`river-bed-materialization-v1`と6 river rule、`coastal-environment-field-stack-v1`、role別cardinality実装）は同一packageのleaf corpus（`RiverBedMaterializationV2`系／`CoastalEnvironmentFieldStackV2`系／`MacroFoundationStageV2Test`）がfull suiteでpinする。

### 4. V2-15再開状態（Track E leaf surface）

`theReopenedTrackELeafSurfaceIsExactlyTheRegistryProjection`が、`V2-15-11`（`LAKE`）が再開時に見る面を固定する。

- design-time support lint surface（`V2-19-08`）はdispatch registryの投影そのもの: registry checksum・reachability projection checksumの一致、reachable集合（production-connected 4／offline-production 3／contract-only 1）、companion要求空。
- `LAKE`／`OXBOW_LAKE`は`NOT_PUBLICLY_DISPATCHABLE`のまま正直に表示される（leaf完了前の能力先取りなし）。
- `CompositionProfileRegistryV2`はADR 0038 D4の承認形: NORMATIVE 7（coastal 4＋`PLAIN`／`HILL_RANGE`＋ADR 0039 confidence-only amendmentの`RIVER`）／PROVISIONAL 53、`LAKE`はPROVISIONAL — per-leaf義務（field監査による確定＋portfolio case追加）の実行可能な根拠。期待値の更新はregistry／ADR正本変更と同一commitでのみ行う（V2-18-12 governance規則の継続）。

### 5. Capability false-promotionなし

`thePhaseGatePromotesNoCapabilityOrDimension`が、正確な集合の完全一致で固定する: `paper_apply: SUPPORTED`＝coastal 4のexact set、`RIVER`／`MEANDERING_RIVER`のPaper 5列＝`EXPERIMENTAL`、`PLAIN`のPaper 5列＝`UNSUPPORTED`＋`standalone_usage: PARTIAL`、dimension limit＝`measured()`（1000受理／1001拒否）、sealed catalog checksum＝built-in sealed一致、Release 2 capability list＝4件順序込み、capability prefix集合＝5集合完全一致。

## 全leaf再検証（full clean suite）

leaf corpora（`V2-19-01` materialization gate契約＋no-op拒否、`V2-19-02` Gradle input drift guard、`V2-19-03` reference image準備negatives＋実CLI／Paper design E2E、`V2-19-04` constraint-source／binding authoring E2E、`V2-19-05` river bed実体化negatives＋block conformance、`V2-19-06` height guide consumer＋RasterResidual、`V2-19-07` producer tier kernel不変条件、`V2-19-08` support lint dry-run、`V2-19-09` subset runtime、`V2-19-10` material profile／environment stack、`V2-19-12` detail kernel（振幅bound・coherence上限・cell-hash対照）、`V2-19-14` reconcile pre-pass（全順序・不動点・負方向）、portfolio determinism（locale／timezone／再export））は、本gateのfull clean suiteで全件再実行した。

- 実行可能gate: `integration/v2/conformance/InputIntegrityPhaseGateV2Test`（6 method、15実行、全PASS）。
- Full clean suite: `./gradlew clean test build` — **239 test class、1461 tests、0 failures、0 errors、12 skipped、BUILD SUCCESSFUL（9m 20s）**（2026-07-24）。
- `V2-19-02`再現check: `scripts/ci/v2-19-02-inventory-input-check.sh` — **8/8一致でPASSED**（clean／orphan-example／broken-docs-link／restoredの4状態×`--build-cache`／`--no-build-cache`両方。2026-07-24再実行）。
- v1回帰: D2b legacy read／verify／migrate境界とK1 immutable golden archiveの等価経路はfull suiteで不変にPASS。
- 直前Release capability回帰: 4 capability verifierとplacement lifecycle portfolioはfull suiteで不変にPASS。

### 実行環境（再現性記録）

HEAD `a2d397e`＋`V2-19-08`〜`15`および本Taskの未commit差分（承認対象SHAはcommit時に確定し承認記録へ記載する）、OpenJDK 21.0.11（Homebrew）、Gradle 9.6.1、macOS 26.5.2（Darwin 25.5.0）、既定timezone JST。locale／timezone determinismはtest内部で`tr-TR`／`Pacific/Chatham`へ切替えて検証しており、既定環境値へ依存しない。

### 12 skipped testの分類（全件、V2-19 Acceptance外）

| Test | 種別 | skip条件と根拠 |
|---|---|---|
| `V2614WorldEditSmokeFixtureExporterTest` | 実機fixture exporter | V2-6-14実機WorldEdit smoke用のopt-in。export directory未設定時はassumptionでabort。evidenceは`V2-6-14`で取得済み |
| `V21104MeasurementFixtureExporterTest`／`V21105MeasurementFixtureExporterTest` | 実機fixture exporter | V2-11-04（500）／V2-11-05（1000）実機計測用。同上、evidenceは2026-07-20取得済み |
| `V21304MeasurementFixtureExporterTest`／`Medium1024OfflineBudgetMeasurementV2Test.heavyProbe…` | 実機／heavy probe | V2-13-03／V2-13-04の計測用opt-in。evidenceは各audit取得済み |
| `RegenerateHydrologyExampleChecksumsTest`／`PlacementSnapshotAllCompilerV2Test.rewriteSnapshotPlanExample`／`RegenerateFeatureSupportCatalogExampleTest` | 手動example再生成helper | `@Disabled`。実行するとtracked exampleを上書きするため既定無効（V2-12-11のtest-hygiene方針） |
| `RegenerateHonoredCoastalMaskExampleTest.rewriteHonored400Mask`／`RegenerateBeachOnlyCoastalMaskExampleTest.rewriteBeachOnlyMask` | 手動fixture再生成helper | `@Disabled`。`V2-18-13`／`V2-19-09`のmask再生成tool（実行時にtracked maskを上書き）。合成規則との一致は各Task実行時にbyte一致で検証済みで、fixtureの**適合性そのもの**はactiveなportfolio／本gateが公開Release実測で常時検証する |
| `HeightGuideExampleFixtureV2Test.rewriteExampleHeightGuide`／`ReferenceImageExampleFixtureV2Test.rewriteExampleReferenceImages` | 手動fixture再生成helper | `@Disabled`相当のopt-in（V2-19-06／V2-19-03のfixture再生成tool）。committed fixtureの正当性は**同クラスのactive method**（guide decode一致2件、image pixel／digest一致2件）がfull suiteで常時検証する |

**V2-19のAcceptanceを構成するtest（materialization gate、portfolio、subset、river／producer／material／detail／reconcile corpus、design E2E、input tracking drift guard、本gate）はいずれもskipされていない。** skip全12件は (a) 実機・環境依存のopt-in exporter／probe（`BLOCKED_EXTERNAL`系Taskの実機evidenceは各Task完了時に取得済み、Beta blockerはrelease checklistで別途追跡）か (b) tracked fileを書き換える手動再生成helperであり、offline Acceptanceの証拠には含まれない。

## Checksum／compatibility境界

V2-19はterrain field／tile／blockの**semantic checksumの意味論**を変更していない。V2-19内のchecksum変化は各leafが宣言・記録済みの範囲に限る — `V2-19-05`（river Releaseのtile／block semantic checksum、意図どおり）、`V2-19-10`（`environment-fields`のtile／block semantic checksum、ADR 0038 D9範囲）、容器byte変化（`V2-19-07`のplain module登録等）。**opt-in宣言（`foundationDetail`／`maskFeatureReconcile`／height guide／producer）絶対なしのrequestのterrain semantic checksumは不変**で、絶対pin `20318e6c…`（4-contributor tile）は本suiteでも通過した。本gateは新たなchecksum変化を一切追加しない（gate testは計測のみ、tracked fileへ何も書かない）。Schema `$id`／capability名／artifact type・version／contract ID／error code等の凍結識別子の変更はない。Release format変更なし、新capability名なし。v1 goldenは不変。

## Findings

**V2-19 scope内の新規critical／high findingなし。** 全leaf再検証・portfolio再測定・reachability照合・false-promotion回帰で、新しい設計欠落・回帰は検出されなかった。LOW（docs、本gate内で是正済み）: `V2-19-12`／`V2-19-14`のTask行が表セル内のinline code（`\|value\|≤A`、`\|dx\|+\|dz\|`、`\|d\|`）へ未エスケープの`|`を含み、[Task Index](../task-index.md) §19.2の2行と[roadmap](../../roadmap.md) Track A行のMarkdown表構造を壊していた。内容不変の`\|`エスケープで是正し、全編集docsの表列数検査を通した。2026-07-23横断監査の5欠陥はいずれも解消済みである — (1) plan-only配線は`V2-19-01`／`05`で遮断・実体化、(2) BROKEN_PUBLICは`V2-19-03`で修理、(3) 公開authoring不在は`V2-19-04`で解消、(4) HEIGHT_GUIDE非接続と2定数flatは`V2-19-06`で解消、(5) Gradle input穴は`V2-19-02`で閉鎖（本gateで8/8再現確認）。

既知のBeta blocker（実機系release checklist項目）および実機依存項目は本Phaseの**非Scope**として継続する — 本gateはそれらを閉じも隠しもしない。

## Release decision

**リリースなし — Beta blockerは残存する。** 本gateはV2-19親Phaseの技術的完了を確立するが、[beta-release-checklist.md](../../beta-release-checklist.md)の未完項目（実機系）を閉じない。V2-17のPaper evidence Phaseは`V2-15-47`／`V2-16-19`完了後にのみ開始する。

## 人間承認記録（2026-07-24、atomic decision）

**2026-07-24、本文書に対する人間承認を得た。承認は上記Decisionの3項目（portfolio gate範囲拡大、`V2-19-16`完了、V2-19親Phase閉鎖）を不可分のatomic decisionとして承認したものであり、一部のみの有効化ではない。**承認対象は次の成果物である: 本Phase gate文書、`InputIntegrityPhaseGateV2Test`、[Task Index](../task-index.md) §19、[roadmap](../../roadmap.md)、[model-assignment](../model-assignment.md) §9.3。承認時点の作業treeはHEAD `a2d397e`＋V2-19-08〜16の未commit差分であり、これらを収めるcommitが承認済み状態の正本SHAとなる（commit時にSHAを本節へ追記してよい）。

## Verification commands

```text
./gradlew test --tests '*InputIntegrityPhaseGateV2Test'
./gradlew test --tests '*IntentConformancePortfolioV2Test' --tests '*FeatureMaterializationGateV2Test' --tests '*CoastalContributorSubsetV2Test'
bash scripts/ci/v2-19-02-inventory-input-check.sh
./gradlew clean test build
git diff --check
git status --short
```
