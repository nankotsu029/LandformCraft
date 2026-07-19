# 地質・気候・生態・素材

> Status: `V2-4-01`〜`V2-4-15`を完了し、typed geology／lithology／strata、climate／water／snow、semantic material／Minecraft palette、mangrove／coral regional shaping、sparse ecology placement、volcanic／canyon feature material overlay、独立environment validator／10-layer diagnostic preview、Release 2 `environment-fields` capability（ADR 0019）をoffline `SUPPORTED`とした。`V2-5-07`で`LUSH_CAVE` wet surface／ecology hooksを、`V2-5-14`でpost-volume local environment／material／sparse moss-root placement（`volume-local-environment-v1`）を`EXPERIMENTAL`追加した。entity／Paper、WorldBlueprint公開dispatch、現行6値 `SurfaceMaterial`とv1 block paletteは変更していない。Track Aの次は`V2-5-15`である。

## 1. 目的

素材を最後にzoneとnoiseで選ぶ方式から、地形形成とblock解決の間に意味場を置く。

```text
geology → erosion / hydrology response
final elevation + hydrology + climate → soil / wetness / salinity
terrain + environment → habitat / ecology
all semantic fields → material profile
Minecraft compatibility adapter → block state
```

目標は地球科学simulationの完全再現ではなく、火山、canyon、雪山、mangrove、coral、lush cave等が固有の成立条件と一貫した素材を持つことである。

## 2. 循環依存の解消

水系は地質と降水を必要とし、気候は最終標高と水域を必要とする。無制限反復を避け、pipelineを次へ固定する。

1. macro layout
2. geology foundation
3. coarse climate prior（runoffに必要な温度／降水だけ）
4. global hydrology
5. regional shapingとbounded reconciliation
6. final regional climate、surface wetness、soil、salinity
7. volume geometry／fluid確定後の局所wetness、shade、surface class
8. ecology、semantic material、micro placement

この順序と最大補正回数をgenerator versionへ含める。上記手順6の「final」はregional fieldに対する呼称であり、caveやsky islandの局所環境はvolume評価後に派生させる。

## 3. GeologyPlan

### 3.0 V2-4-01 geology field core

`GeologyPlanV2(planVersion=1, fieldContractVersion=geology-field-contract-v1)`は、V2-3のimmutableな`UNIFORM_GEOLOGY_PRIOR`をchecksum付き`PriorReplacement`で参照し、Hydrology planをin-place変更せずtyped fieldへ置換する。Stage 3のbuilt-in `v2.environment.geology-foundation`だけが次のfieldを`SINGLE_OWNER`で所有する。

| Field | Semantic | Encoding | V2-4-01の値 |
|---|---|---|---|
| `geology.province-id` | `PROVINCE_ID` | U16 / scale 1,000,000 | uniform prior province code、empty planはno-data |
| `geology.formation-id` | `FORMATION_ID` | U16 / scale 1,000,000 | opaque formation code。semantic catalogは別planで結合 |
| `geology.hardness` | `HARDNESS` | U16 / scale 1,000 | V2-3 priorの500,000 millionths |
| `geology.permeability` | `PERMEABILITY` | U16 / scale 1,000 | V2-3 priorの500,000 millionths |

payloadはBlueprint JSONへ埋め込まず、4個の`LFC_GRID_V1`をstagingへstream writeし、artifact／semantic checksumとcross-field codeをbounded readerで全域検証してからdirectoryをatomic publishする。最大window幅、4 windowの`int[]`、row buffer、writer strict read-back、retained descriptor、CPU、単体／合計artifact byteをplan budgetへfreezeする。1000×1000でもdense voxelを作らず、tile順、thread数、locale、timezoneに依存しない。

V2-4-01のformation ID自体はopaqueのままであり、V2-4-02のsemantic lithology catalogは別の`LithologyPlanV2`からchecksumで結合する。strata、Minecraft block state、feature連携は表さない。V2-4-01単体ではmodule lifecycleを`EXPERIMENTAL`のままにし、Release 2 capabilityも追加しなかった。V2-4-15統合監査後はoffline environment infrastructureとして`SUPPORTED`である。

```text
GeologyPlan
├── provinces[]
├── lithologyField
├── strataProfiles[]
├── hardness / permeability / erodibility fields
├── fault / fracture guides
├── volcanic / sediment source plans
└── exposure rules
```

### 3.1 Semantic lithology

Minecraft block名を直接地質種としない。

`V2-4-02`は`planVersion=1`、`assignmentContractVersion=lithology-province-assignment-v1`の`LithologyPlanV2`を追加する。catalogは`landformcraft.builtin-lithology`／`builtin-lithology-catalog-v1`に固定し、下表の9種とその8-bit compact code、hardness、permeability、erosion responseをcanonical checksumでfreezeする。外部preset、任意class、未登録ID、future versionは受理しない。

各assignmentはsource `GeologyPlanV2` checksum、province ID/code、formation ID/code、hardness、permeabilityを明示し、catalog entryと完全一致しなければ拒否する。既存の4 `LFC_GRID_V1` fieldにはsemantic codeを追加せず、bounded readerがprovince raw codeをassignment経由で検査する。catalog canonical bytesは32 KiB、plan canonical bytesは64 KiB以下であり、block palette、strata、climate、feature material responseは後続Taskの責務である。

| Semantic lithology | 性質例 | block adapter候補 |
|---|---|---|
| `HARD_INTRUSIVE` | hard、低透水、粗粒 | stone/granite系 |
| `ANDESITIC_VOLCANIC` | hard、暗色、節理 | andesite/deepslate系 |
| `BASALTIC_FLOW` | hard、層状／柱状 | basalt/blackstone系 |
| `TUFF_ASH` | 軟らかい、侵食性 | tuff系 |
| `CARBONATE_LIKE` | 溶食しやすい | calcite/stone系 |
| `SANDSTONE_STRATA` | 層理、差別侵食 | sandstone系 |
| `ALLUVIAL_GRAVEL` | 河床、透水 | gravel/coarse dirt系 |
| `SILT_CLAY` | 低地、保水 | clay/mud系 |
| `REEF_CARBONATE` | 浅海生物起源 | calcite/coral substrate系 |

adapter候補はcompatibility profileで確定し、generator modelへMinecraft型を入れない。

### 3.2 Strata

```text
StrataProfile
├── profileId
├── layers[]: lithologyId / thickness / variation
├── orientation / dip
├── erosionResistance
└── exposurePaletteProfile
```

`V2-4-03`は`planVersion=1`、`profileContractVersion=strata-profile-contract-v1`の`StrataPlanV2`を追加する。各provinceへ1 profileを割り当て、`BOTTOM_TO_TOP`のordered layer、thickness blocks、cardinal dip（0〜45°）とoptional fold（amplitude 0〜32／wavelength 16〜256）だけをbounded subsetとしてfreezeする。surface exposureとderived hardness／permeabilityは`StrataExposureResolverV2`がprofile descriptorからinteger-onlyで再計算し、dense W×L×depth layer mapや追加sidecarを正本にしない。

`HydrologyGeologyInputHandoff`はV2-3の`UNIFORM_GEOLOGY_PRIOR` checksumと`hydrology-reconcile-fixed-v1`を明示参照したまま、入力modeを`SURFACE_EXPOSED_STRATA_SCALARS`、transitionを`EXPLICIT_VERSION_TRANSITION`へ宣言する。HydrologyPlan／FixedPriors／reconciliation algorithmの意味はin-place変更せず、将来のerosion／hydrology costが読むべきversion境界だけを固定する。zero thickness、inverted layerIndex、unknown lithology、非cardinal azimuth、layer×tile budget不足は拒否する。Minecraft block、canyon/volcano固有material、full geologic simulationは後続Taskの責務である。

canyon、cliff、cave wallではsurface normalとdepthから露出層を解決する。全cellへ全layer IDを保存せず、province＋profile＋fixed-point変位fieldを使う。

### 3.3 Formationとの関係

- hard rockはcanyon wall、sea cliff、ridgeを保持しやすい。
- permeable層はspring、groundwater、karst route costへ影響する。
- alluvial sedimentはriver、delta、floodplainへ堆積する。
- basalt/tuff/ashはvolcano、lava flow、black sand beachへ連続する。
- reef carbonateはcoral reefの浅海platformと連動する。

地質が地形を完全決定するのではなく、feature hard geometryを守りつつcost、erosion、materialへ影響させる。

## 4. ClimatePlan

### 4.0 V2-4-04 climate field core

`ClimatePlanV2(planVersion=1, fieldContractVersion=climate-field-contract-v1)`は、気候をhydrology前のcoarse priorとreconciliation後のfinal fieldへ分離する。`generate.climate-prior`は32-cell coarse gridを固定小数補間し、`climate.prior.precipitation`と`climate.prior.runoff`を所有する。`generate.climate-final`はその2 field、最終標高／exposure、V2-3 `hydrology.flow-accumulation`を読み、`climate.final.temperature`と`climate.final.moisture`だけを所有する。全kernelはinteger-onlyかつglobal X/Z samplingで、wall clock、locale、timezone、tile順、thread完了順を入力にしない。

`HydrologyRunoffHandoff`はsource `HydrologyPlan` canonical checksum、`CONSTANT_RUNOFF_PRIOR` checksum／値、source generator `hydrology-priority-flood-v1`を厳密に参照し、target `hydrology-priority-flood-climate-prior-v1`とreplacement coarse-prior checksumを`EXPLICIT_VERSION_TRANSITION`としてfreezeする。既存V2-3 plan、routing artifact、fixed prior checksumは書き換えず、final field差によって同一priorのhydrology graphを変えない。preset欠落、unknown preset、future contract、source plan／prior／generator version mismatchはcanonical Blueprint公開前に拒否する。

4 fieldはdescriptor-onlyであり、dense full-resolution arrayをBlueprint JSONへ埋め込まない。1000×1000 planは33×33のcoarse grid、bounded tile window、CPU／retained／working／canonical byte budgetを宣言する。V2-4-04単体ではsidecar payloadとRelease 2 capabilityを追加せず、field module lifecycleを`EXPERIMENTAL`のままとした。V2-4-15統合監査後はoffline environment infrastructureとして`SUPPORTED`である。

```text
ClimatePlan
├── baseClimatePreset
├── temperatureField
├── precipitation / moisture fields
├── windDirection / exposure field
├── maritimeInfluence / salinity field
├── snowPotential / freeze field
└── quantization / interpolation rules
```

### 4.1 Temperature

base latitude相当preset、標高減率、海からの距離、局所shadeをfixed-pointで合成する。wall clock、実際の天気、server biome乱数を入力にしない。

### 4.2 Moisture

降水prior、flow accumulation、groundwater depth、水域距離、斜面向き、wind exposureから求める。単一wetness noiseだけで湿地を決めない。

### 4.3 Snowline

snowは `maxY` 近傍の一律surface materialではなく、temperature、elevation、exposure、slope、shadeから `SNOW_COVER_01` を作る。hard snowlineはtransition Yと幅、soft snowlineはtarget分布として検証する。急崖、風上ridge、温かい水際には補正を入れる。

### 4.4 Salinityとtide

海／tidal channelからの距離、freshwater discharge、elevation、tidal connectivityで0〜1のsalinity influenceを作る。mangrove、salt marsh、coral reefのhabitat条件に使う。

## 5. Soilとsurface condition

最終地形、水系、地質から次を導出する。

- soil depth
- drainage class
- groundwater depth
- wetness / inundation frequency class
- slope / aspect / curvature
- substrate grain class
- erosion/deposition class
- shade / light exposure class

これらはpreviewとvalidatorから読めるnamed fieldにし、material resolver内の隠れたif文だけにしない。

## 6. EcologyPlan

```text
EcologyPlan
├── habitatRegions[]
├── assemblageDescriptors[]
├── density / spacing fields
├── exclusion / succession rules
└── placementBudgets
```

`assemblage` はblockの羅列ではなく、条件付きの意味的集合である。

```text
AssemblageDescriptor
├── assemblageId
├── required habitat ranges
├── canopy / understory / ground / aquatic layers
├── density / minimum spacing / cluster scale
├── allowed surface classes
└── semantic placement types
```

placementはglobal X/Y/Zとnamed seedで決定し、tile境界で密度やspacingが変わらない。全候補Listを作らず、cell hash、blue-noise相当の決定的判定、空間windowで生成する。

### 6.0 V2-4-11 sparse ecology placement

`EcologyPlanV2(planVersion=1, placementContractVersion=ecology-placement-contract-v1)`は`model.v2.ecology`に追加した。climate final temperature、water-condition wetness／salinity／hydroperiod、snow coverへchecksum結合し、閉じたcatalog（`landformcraft.builtin-ecology-assemblage`）にmangrove canopy／root・coral colony・alpine shrub／meadowの5 assemblageを固定する。`ecologyPreset`は閉じたenumだけを受理し、external script／未知presetを拒否する。`generator.v2.ecology.EcologyPlacementResolverV2`はhabitat U16（`environment.ecology.habitat`）をcell入力から導出し、spacing lattice winner＋density millionthsでsparse placementを決定する。dense object gridは割り当てず、bounded windowのdescriptor数にbudgetを掛ける。cave ecology、entity、block entity、Paper placement、WorldBlueprint配線は対象外である。

## 7. 代表環境

### 7.1 Mangrove wetland

必要条件:

- warm climate
- tidal／estuary connectivity
- low reliefと高groundwater
- brackish salinity
- mud/silt substrate
- root用の浅い水と空間

出力はmangrove habitat、channel margin、root placement、mud/wet material、open-water gapである。単にswamp zoneへtreeを置かない。

### 7.2 Snow mountain

- alpine temperature lapse
- ridge wind exposure
- slope-dependent snow retention
- treeline、alpine meadow、bare rock、screeのelevation band
- cirque／north-facing hollowのsnow persistence

### 7.3 Coral reef

- warm、shallow、marine、適度なlight exposure
- substrate stability
- salinity、depth、outer slope／lagoon position
- coral cover、sand patch、seagrass、dead reefのmosaic

coralは水面下の任意blockへ配置せず、surface normal、depth、habitat、spacingを検査する。

### 7.4 Lush cave

- cavity floor／wall／ceiling classification
- high moisture、drip path、groundwater
- surface breakthroughを除く低light exposure
- pool、clay、moss、rooted ceilingとの関係

### 7.5 Volcanic field

- basalt、tuff、ash、lava lobeのprovince
- age／successionに応じたbare rock、sparse pioneer、mature patch
- waterとlava flowの境界でblack sand／gravel transition

## 8. Semantic material

### 8.0 V2-4-07 semantic material profile

`MaterialProfilePlanV2(planVersion=1, profileContractVersion=material-profile-contract-v1)`は`model.v2.material`に追加した。`geologyBinding`（geology／lithology／strata checksum）、`waterConditionBinding`（`environment.water.wetness`）、`snowBinding`（`environment.snow.cover`）で上流planを直接checksum結合し、in-placeの再解釈を行わない。version固定の閉じたcatalog（`landformcraft.builtin-material-profile`）は`HOST_ROCK_EXPOSED`／`HOST_ROCK_WET`／`SEDIMENT_EXPOSED`／`SEDIMENT_WET`／`SNOW_COVERED_ROCK`／`SNOW_COVERED_SEDIMENT`の6 semantic classをcompact codeで固定する。3段のfixed-order resolution rule（`BASE_SUBSTRATE_FROM_LITHOLOGY`→`WETNESS_OVERRIDE`→`SNOW_OVERRIDE`）はimplicitなlast-write-winsを許さず、各ruleがexplicitな`RuleMergeOperator`（`BASE_ASSIGNMENT`／`CONDITIONAL_OVERRIDE`）と`applicableAspects`（`SURFACE`／`CEILING`／`FLOOR`）を持つ。`generator.v2.material.MaterialProfileResolverV2`は`StrataExposureResolverV2`のhost lithology／erosion responseからrock／sediment substrateを導出し、wetness／snow cover raw値としきい値（kernel、`material-profile-fixed-v1`）を順に適用してclass codeをinteger-onlyで解決する。dense width×length配列は保持せず、`checksum`メソッドでwhole／tile／thread不変を検証できる。volcanic/canyon固有ruleとvolume-local materialは対象外である。Minecraft block解決は`V2-4-08`のpalette adapterへ分離する。

### 8.1 V2-4-08 Minecraft palette adapter

`MinecraftPalettePlanV2(planVersion=1, paletteContractVersion=minecraft-palette-contract-v1)`は`model.v2.minecraft`に追加した（ADR 0018）。sealed `MaterialProfilePlanV2` checksumとcatalog IDへ直接bindingし、Minecraft `1.21.11`／DataVersion `4671`／`minecraft-palette-resolver-v1`をcompatibility tupleとして凍結する。閉じたcatalog（`landformcraft.builtin-minecraft-palette`）は6 semantic class × SURFACE／CEILING／FLOORの18 mappingだけを持ち、未知ID／future resolver／NBT付きstate／last-write-winsを拒否する。`format.v2.minecraft.MinecraftPaletteResolverV2`はcompact codeをcanonical block-state文字列へ解決し、`EnvironmentBlockStateCatalogV2`（coastal allowlistのstrict超集合）がoffline Sponge writer／inspectorの共有gateになる。palette ID 127／128 VarInt境界、Sponge strict read-back、WorldEdit 7.3.19 offline checksum一致をAcceptanceで固定した。v1 `MinecraftBlockPalette`、Paper apply、biome書換えは変更・接続しない。

### 8.2 V2-4-12 volcanic／canyon feature material overlay

`FeatureMaterialProfilePlanV2(planVersion=1, profileContractVersion=feature-material-profile-contract-v1)`は`model.v2.material.feature`に追加した。sealed base `MaterialProfilePlanV2`とgeology／lithology／strata checksumへ結合し、optionalなvolcanic／canyon `geometryChecksum` bindingを持つ。閉じたcatalog（`landformcraft.builtin-feature-material-profile`）は`VOLCANIC_BASALT_EXPOSED`／`VOLCANIC_TUFF_EXPOSED`／`VOLCANIC_ASH_EXPOSED`／`CANYON_STRATA_EXPOSED`／`CANYON_TALUS`／`CANYON_FLOOR_SEDIMENT`の6 class（compact code 7–12）を固定する。resolutionは`BASE_FROM_MATERIAL_PROFILE`→`VOLCANIC_ZONE_OVERRIDE`→`CANYON_ZONE_OVERRIDE`→`FEATURE_WETNESS_HINT`の固定順、conflictは`CANYON_WINS_OVER_VOLCANIC`を明示する。`generator.v2.material.feature.FeatureMaterialProfileResolverV2`は`VolcanicSample`／`CanyonSample`相当のcell入力と`StrataExposureResolverV2`だけを読み、shape field／generator versionを変更しない。unknown lithologyとsilent fallbackを拒否する。lava tube、cave、Minecraft palette拡張、WorldBlueprint配線は対象外である。

現行6値surface enumを次の役割へ分解する。

```text
MaterialProfile
├── surface cover
├── soil/substrate layers
├── host lithology
├── wet / submerged variant
├── floor / wall / ceiling / underside variant
├── transition rules
└── Minecraft palette binding ID
```

例:

```text
COASTAL_ANDESITE_WET
DELTA_SILT_MUD
ALPINE_GRANITE_SNOW_PATCH
VOLCANIC_BASALT_TUFF
REEF_CARBONATE_LIVE_CORAL
LUSH_CAVE_WET_CLAY_MOSS
```

semantic profileをWorldBlueprint／Releaseへ保存し、Minecraft 1.21.11 block stateはversion付きadapterで解決する。これにより将来のblock互換更新が地形意味を変えにくい。

### 8.3 V2-4-13 environment validators and previews

`EnvironmentValidatorV2`は公開`EnvironmentFieldSamplerV2`だけを測定し、generator-private配列を読まない。wrong snowline、mangrove salinity、reef depth、root support、material exposureをHARD issueへ変換し、habitat／volcanic／canyon／strataのsummary metricを固定する。`EnvironmentDiagnosticPreviewRendererV2`は10固定PNGを1枚ずつrenderし、strict preview index／checksum／directory entry read-back後にatomic publishする。Release 2 `environment-fields` capability（ADR 0019）へ収容済みであり、`V2-4-15`統合監査で対象featureとcapabilityをoffline `SUPPORTED`へ昇格した。


## 9. Material resolution order

同じcell／faceへの候補は次の明示順で解決する。

1. safety／excluded material rule
2. host lithologyとstrata
3. hydrology substrate／submerged state
4. regional feature profile
5. soil／snow／wet cover
6. ecology placement
7. micro detail
8. structure override

priorityだけのlast-write-winsは禁止し、各段のmerge operatorをBlueprintへ保存する。

## 10. Field表現と性能

- macro climateはcoarse grid＋固定補間でよい。
- lithology、habitat、material profileはU16 ID fieldを基本とする。
- temperature/moisture/salinityはI16/U16 fixed-pointにする。
- slope、aspect、wetness等のderived fieldはtile＋haloで再計算可能なら保持しない。
- strataはprofile descriptorを正本にし、dense 3D layer mapを持たない。
- ecology placementはsparse descriptorとしてstreamする。
- field数、profile数、assemblage数、placement密度へhard budgetを設ける。

## 11. 決定性とvalidation

### 共通

- field値範囲、no-data、artifact寸法、seam一致
- climate gradientの不連続、未知profile、unresolved paletteなし
- materialがsolid/fluid/surface classと整合
- placementがbounds、密度、spacing、support条件を満たす

### Feature metric

- snowline Y、snow cover対elevation、treeline、bare-rock share
- mangrove salinity、inundation、canopy、channel距離、root support
- coral depth、temperature、salinity、cover、dry exposure率
- lush cave moisture、reachable floor、ceiling support、pool share（`V2-5-07`でwet FLOOR／WALL／CEILING eligibilityとecology hooksを`EXPERIMENTAL`実装。`V2-5-14`でpost-volume sparse moss／root／pool placementを`EXPERIMENTAL`追加）
- volcanoのbasalt/tuff/ash分布とlava continuity
- canyon strata visibilityとtalus band

### Test

- preset contract／unknown profile拒否
- field fixed-point golden fixture
- whole/tile、thread、locale、timezone決定性
- boundary／transitionのproperty test
- block adapter palette snapshotとSponge read-back
- placement density／spacingのmetamorphic test
- corrupted field/profile/materialのnegative validator fixture

## 12. Preview

- lithology / strata exposure
- hardness / permeability / erodibility
- temperature / moisture / wind exposure
- groundwater / wetness / salinity
- soil depth / substrate
- habitat / assemblage / placement density
- snow cover / treeline
- semantic material profile
- unresolved／conflicting material residual

previewの色はstable legendを持ち、Minecraft block色そのものとsemantic fieldを混同しない。

## 13. 流用・置換

### 流用する

- pure modelとgenerator package境界
- global座標noise、seed、tile＋marginの原則
- `BlockPalette`／Sponge writerという最終adapter境界
- bounded executor、cancel、memory estimateの事前拒否

### 置換する

- `SurfaceMaterial` 6値をsemantic profile＋adapterへ置き換える。
- `materialAt` の単一switchをfield-driven resolverへ置き換える。
- `VEGETATION` bitをhabitat＋sparse placementへ置き換える。
- `maxY` 近傍snowをclimate-based snow fieldへ置き換える。

### 段階的に廃止する

- zone名から直接block materialを決める規則
- Minecraft block名を地質の正本にするpreset
- 全環境を同一resolutionのdense fieldで永久保持する方式

v1 material pathはgenerator `3.0.0-phase6` の再現用に残し、v2へ混在させない。

## 14. 実装Task順

環境subsystem全体を同時に導入せず、[Task Index](task-index.md) の`V2-4-01`〜`V2-4-15`を順に実行した。全Taskと [V2-4 Phase gate audit](audits/v2-4-phase-gate.md) は完了し、Track Aの次Taskは`V2-5-01`である。

```text
geology field core
→ lithology catalog
→ strata / hardness / permeability
→ climate prior / final core
→ wetness / salinity / hydroperiod
→ snow / snowline
→ semantic material profile
→ Minecraft palette adapter
→ mangrove regional shaping
→ coral reef bathymetry
→ ecology placement
→ volcanic / canyon material integration
→ validator / preview
→ Release capability
→ integration audit
```

この順序は `geology → hydrology prior binding → final climate → regional water condition → ecology/material` の一方向依存を保つ。mangroveとcoralの地域形状はそれぞれ`V2-4-09`／`V2-4-10`で実装し、ecology placementは`V2-4-11`まで開始しなかった。semantic materialとMinecraft block adapterを`V2-4-07`／`V2-4-08`に分離し、modelへblock stateを持ち込まない。`environment-fields` capabilityと対象scenarioは`V2-4-15`統合監査を通過したためoffline `SUPPORTED`である。
