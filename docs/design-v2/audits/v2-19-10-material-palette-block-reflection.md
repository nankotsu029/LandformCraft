# V2-19-10 evidence: material／paletteのblock反映

> Status: 完了（2026-07-24）。進捗の正本は [docs/roadmap.md](../../roadmap.md)、Scopeの正本は [task-index §19](../task-index.md)。ADR新規起草なし（既存 [ADR 0038](../../adr/0038-macro-foundation-contract.md) D9の可変域内、[ADR 0040](../../adr/0040-coastal-contributor-set-cardinality.md) の絶対pinは不変）。

## 1. 何が壊れていたか

[2026-07-23横断監査](../../audits/cross-cutting-audit-2026-07-23.md) が実測で確定した2件（提案T-M1）。

**(a) §2.4 — hard-coded 11 block stateとpalette未接続。**

```java
// CoastalSurfaceFieldsV2.resolver（V2-19-10以前）
if (y == minY) return "minecraft:bedrock";
...
return y == surface ? "minecraft:grass_block"
        : y >= surface - 2 ? "minecraft:dirt" : "minecraft:stone";
```

surface columnが取り得るblockは resolver 本体へ直接書かれた11個のliteralに固定され、**どの層もこの語彙を宣言していなかった**。実測tileの`paletteSize: 11`と一致する。同一Releaseが`environment/material-profile-plan.json`と`environment/minecraft-palette-plan.json`を公開していても、その2枚はblockへ一切届かない。`FoundationSurfaceFieldsV2`（ADR 0037 adapter）も独自に7個のliteralを持っていた。

**(b) §2.8 — environment validationの定数healthy sampler。**

```java
// EnvironmentFieldsExportPipelineV2（V2-19-10以前）
EnvironmentCellSnapshotV2 healthy = new EnvironmentCellSnapshotV2(
        400, 500, 500, 400, 500, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0);
return (x, z) -> healthy;
```

全cellが同一literalを返すため、`environment/validation.json`の全metricがPASSでも地形について何も言っていない。

## 2. 実装

### 2.1 profile-driven allowlist（`surface-material-profile-v1`）

`SurfaceMaterialProfileV2`（`core.v2.material`）がsurface columnのblock語彙を**閉じたrole catalog**として宣言する。13 role・11 distinct block stateで、内訳はcolumn tier（`LayerV2`）付きである。

| Layer | Role | built-in block state |
|---|---|---|
| `STRUCTURAL` | `BEDROCK_FLOOR` / `OPEN_AIR` / `OPEN_WATER` | bedrock / air / water |
| `NATURAL_SURFACE` | `VEGETATED_SURFACE` / `BEACH_SURFACE` / `ROCK_SURFACE` / `SEABED_SURFACE` | grass_block / sand / cobblestone / gravel |
| `NATURAL_SUBSURFACE` | `SUBSOIL` / `BEACH_SUBSURFACE` / `SEABED_SUBSURFACE` / `DEEP_SUBSTRATE` | dirt / sandstone / stone / stone |
| `BUILT_STRUCTURE` | `STRUCTURE_CREST` / `STRUCTURE_CORE` | stone_bricks / cobblestone |

- 全roleがちょうど1回mapされていなければ構築時に拒否する。
- 各block stateは`EnvironmentBlockStateCatalogV2.requireKnown`を通る。**任意block state・非canonical識別子・NBT payloadは構築時に拒否**され、外部script／式は一切評価しない。
- `canonicalChecksum()`はordered role tableのSHA-256（artifactへは書かない。証跡用の識別子）。
- `builtIn()`はV2-2の凍結表であり、`CoastalSurfaceFieldsV2`／`FoundationSurfaceFieldsV2`のroleへの写像は旧literal分岐と1対1である（下記§3のchecksum不変が実証）。

resolverは「cell → role」を決めるだけになり、block stateの決定は`SurfaceMaterializationV2`が持つ。

### 2.2 material／palette → block（`surface-material-binding-v1`／`environment-surface-material-v1`）

blockは2段で決まる。これは凍結済みV2-4-07 rule table（`BASE_SUBSTRATE_FROM_LITHOLOGY` → `WETNESS_OVERRIDE` → `SNOW_OVERRIDE`）の写しである。

1. **base assignment**: surface roleがprofileの状態を与える。beach＝sand、cape＝rock という形状由来の意味はここにしかなく、per-cell lithologyでは表現できない。
2. **conditional override**: environment materialがbindされているとき、解決済みsemantic material classが**wet variantまたはsnow variant**のcellだけ、`MinecraftPalettePlanV2`の`SURFACE` aspect mappingで置換する。exposed variantはoverrideを宣言せず、roleのbase assignmentが残る。

対象は`NATURAL_SURFACE` roleのみである。structural（bedrock/air/water）、built structure（breakwater crest/core）、subsurfaceは常にprofileの状態を保つ。結果として**environment materialはsolid質量を増減できずfluidも作れない** — 変化するのはblock effect classの`MATERIAL`だけである（§3で実測）。

`EnvironmentSurfaceMaterialV2`はrelease-local domain全体の override を **tile書き出し前に一度だけ**凍結する（1 cellあたり1 byteのindex、distinct stateは最大4）。したがってtile境界やtile順で材質が食い違うことはない。palette planは`requireMaterialProfilePlan`でmaterial profileへ束縛され、class codeがsealed catalog外（geology no-data sentinel 65535を含む）なら拒否する。

**なぜbase assignmentまでpaletteへ委ねなかったか**: `SemanticMaterialClass`は6値（rock/sediment × exposed/wet ＋ snow 2種）であり、`SEDIMENT_EXPOSED/SURFACE`＝sand、`HOST_ROCK_EXPOSED/SURFACE`＝stone である。base assignmentを丸ごとpaletteに委ねると、共有pipelineのuniform geology prior（§4）がROCKを返す限り**beachがstoneになる**。形状が決めた意味を上書きするのは非適合であり、rule tableもbase assignmentとconditional overrideを別rule idで分けている。

### 2.3 定数samplerの廃止（`coastal-environment-field-stack-v1`）

`CoastalEnvironmentFieldStackV2`（`core.v2.export`）が、**公開しようとしているまさにそのBlueprintのsealed plan**から既存V2-4 sampler／resolver経由で全値を測る。

| snapshot項目 | 出所 |
|---|---|
| `temperatureRaw` / `moistureRaw` | `ClimateFieldSamplerV2`（`FINAL_TEMPERATURE`／`FINAL_MOISTURE`） |
| `wetnessRaw` / `salinityRaw` / `hydroperiodRaw` | `WaterConditionFieldSamplerV2` |
| `snowCoverRaw` | `SnowFieldSamplerV2` |
| `materialClassCode` | `MaterialProfileResolverV2`（`SURFACE` aspect） |
| `lithologyCode` | `StrataExposureResolverV2.exposedLithologyCode` |
| `habitatCode` | `EcologyPlacementResolverV2` |
| elevation / land-water / slope | `CoastalSurfaceFieldsV2`（生成済みsurface） |
| sea distance / marine connectivity | 境界connected water componentからのbounded多始点BFS（water-condition kernelのsupportでclamp） |
| flow accumulation | 同一runのfrozen hydrology routing result |

宣言されたfieldを持たない2入力は、**呼び出しごとの発明ではなく明示のbounded derivation**として契約に書いた。

- **topographic exposure proxy**: request water level上の高さ（40 blockで飽和）。climate exposureとsnowのwind／sun exposureの両方に使う。個別の風・日射modelは実装しておらず、捏造もしない。
- **slope proxy**: 4近傍surface heightの最大段差（8 blockで飽和）。

宣言planが無いfeature scoped入力（river／lake／tidal proximity、tidal range、freshwater discharge、wetland／reef／island／canyon mask、feature material class、wall height）は「該当featureの宣言なし」の値のままで、海岸から推測しない。これを正直に保つため`requireSharedEnvironmentOnly`は`meanderingRiverPlans`も拒否対象へ加えた（この pipeline の`executableKinds`にRIVER系は無く、dispatchからは到達不能。defence in depth）。

dense保持は marine distance の`int[]`＋override の`byte[]`だけで、他は呼び出しごとに算出する（`estimatedResidentBytes`で宣言）。thread／tile順／locale／timezoneに依存しない。

### 2.4 pipeline分割

`HydrologyPlanExportPipelineV2`をV2-19-05と同型に`prepareHydrology` / `completeHydrology`へ分けた。environment pipelineは prepare → sealed plan群のcompile → field stack → material override → `completeHydrology(materialization)` の順で走る。**final canonical block streamの生成点はtile書き出しの1箇所のまま**である。

## 3. 実測

`harbor-cove-64-honored`（64×64）を`surface-2_5d` baselineと`environment-fields`の2回exportし、`FeatureMaterializationV2`（V2-19-01のgate計測コードそのもの）で公開Release同士をcell単位diffした。

```
BlockEffectV2[comparedCells=167936, changedCells=3191,
              solidShapeChanges=0, fluidChanges=0, materialChanges=3191]
```

露出surface（各columnの最上位solid）の分布:

| | baseline（`surface-2_5d`） | environment bound |
|---|---|---|
| `minecraft:gravel` | 2251 | 0 |
| `minecraft:sand` | 221 | 0 |
| `minecraft:cobblestone` | 228 | 3419 |
| `minecraft:grass_block` | 1062 | 343 |
| `minecraft:stone_bricks` | 334 | 334 |

- 変化は`MATERIAL`のみ（solid shape 0／fluid 0）。
- baselineの分布はV2-2凍結表そのままで、V2-19-10で動いていない。
- environment側は3状態に分かれる。**定数samplerなら1状態しか残らない**ため、これがfield stackの空間変化のblock側証拠である。海岸・海底はwet（`HOST_ROCK_WET`→cobblestone）、内陸のdry cellはroleのbase assignment（grass_block）を保ち、built structureのcrest（stone_bricks）は一度も奪われていない。
- 決定性: locale `tr-TR`／timezone `Pacific/Chatham`での再exportとのblock diffは0 cell。

証跡testは`EnvironmentMaterialBlockConformanceV2Test`（`integration.v2.conformance`）。**plan-only metric（`environment/validation.json`）は代替evidenceとして使っていない**（V2-19-01の別欄化の原則）。本Taskは公開配線leafではなくFeature昇格も行わないため、intent-conformance portfolioへのcase追加はしていない。

## 4. checksum影響

| 対象 | 影響 |
|---|---|
| `surface-2_5d` / `hydrology-plan` の terrain field／tile／block semantic checksum | **不変**。`SurfaceMaterializationV2.builtIn()`がV2-2表と1対1。既存の絶対pin `20318e6c…`（`MacroFoundationProducerV2Test`／`CoastalContributorSubsetV2Test`）はそのまま通る |
| ADR 0037 foundation adapter | **不変**（同じrole catalogへ移行、built-in binding） |
| `environment-fields` の tile／block semantic checksum | **変化**（本Taskの目的そのもの。3191 cellのMATERIAL差分） |
| `environment/validation.json`・environment previewsのbyte | **変化**（定数snapshotから実測snapshotへ） |
| Release format／capability／新artifact type | **なし** |
| sealed catalog、`production-dispatch-registry-v1`、`public-dispatch-reachability-v1` | **不変**（capability昇格・dispatch route追加なし） |
| v1 golden | **不変** |

ADR amendmentは不要である。ADR 0038 D9は「まだ再配線されていないkindのsemantic checksumは不変」を求めており、本Taskはkindを再配線せず、変化するのは`environment-fields` capabilityが自身のplanをblockへ反映した結果に限られる。

## 5. 残存する制約（実装より先に能力を書かない）

- **共有pipelineのgeology priorはuniform 1 provinceである。** そのため`erosionResponse`は全域ROCK側に落ち、wet cellは`HOST_ROCK_WET`（cobblestone）になる。実測でmudが1 cellも出ないのはこの理由で、material profile側の欠陥ではない。実province fieldはV2-4-01のuniform priorを置き換える別Taskの範囲である。
- **植生はblockへ届いていない。** `EcologyPlanV2`のassemblage／placementはsnapshotのhabitat classまでで、`VEGETATED_SURFACE`のgrass_blockは依然としてroleのbase assignmentである。ecology placement→blockは本TaskのScope外である。
- **snow overrideは本fixtureでは発火しない。** `TEMPERATE_MARITIME`の実測温度はsnowline（150 raw）を大きく上回り、snow coverは全cell 0である。snow分岐の写像は`EnvironmentSurfaceMaterialV2Test`がsealed planに対して直接固定している。
- **`environment-fields`はCLI／Paperから到達できない。** production Application Serviceは存在するが公開commandが呼んでおらず（監査§2.8）、公開到達性の整理は`V2-19-11`のdocs同期と後続の配線Taskが扱う。capability昇格は本Taskでは行っていない。
- **ZONE_LABEL→material zoneは未接続。** `V2-19-04`のbinding経由で行う設計は変えておらず、`HEIGHT_GUIDE`／`LAND_WATER_MASK`のようなgenerator consumerはまだ無い。

## 6. 実行したtest

```bash
./gradlew test build
```

`BUILD SUCCESSFUL`（235 test class／1395 test／12 skip／0 failure／0 error）。`git diff --check`もclean。CLIと外部挙動は変更していないため`./gradlew run --args="--help"`は不要である。

対象testは`SurfaceMaterialProfileV2Test`、`EnvironmentSurfaceMaterialV2Test`、`EnvironmentMaterialBlockConformanceV2Test`、既存の`Release2EnvironmentExportApplicationServiceV2Test`／`MacroFoundationProducerV2Test`／`CoastalContributorSubsetV2Test`／`MacroFoundationPhaseGateV2Test`／`IntentConformancePortfolioV2Test`である。
