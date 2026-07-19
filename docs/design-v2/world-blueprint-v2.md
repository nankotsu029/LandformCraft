# WorldBlueprint v2

> Status: V2-0〜V2-4のPhase gateを完了した。V2-4-01〜05の`geologyPlan`／`lithologyPlan`／`strataPlan`／`climatePlan`／`waterConditionPlan`はWorldBlueprintV2へ配線され、snow／semantic material／Minecraft palette／ecology／feature materialはchecksum結合したstandalone plan／compilerとしてoffline `environment-fields` Releaseへ収容される。V2-4対象featureとcapabilityはoffline `SUPPORTED`だが、standalone planのWorldBlueprint公開dispatch／Paper配置は未接続である。`V2-5-01`〜`V2-5-14`のvolume SDF／CSG／index／cache／TerrainQuery／局所volume feature／waterfall volume／post-volume local environmentは実装済みで、volume validators／preview以降とRelease未接続である。いずれもv1から分離され、現行 [WorldBlueprint v1](../../src/main/java/com/github/nankotsu029/landformcraft/model/WorldBlueprint.java) は変更していない。Track Aの次は`V2-5-15`である。

## 1. 目的

`TerrainIntent v2` は人間とAIが「何を作るか」を表す。`WorldBlueprint v2` は、それをProviderから完全に切り離し、generatorが「どのfieldを、どのmoduleが、どの順序で、どのbudget内で作るか」を実行できるcompile済みIRへ変換する。

v1のように元Intentを包むだけではなく、少なくとも次を確定させる。

- 座標、bounds、tile、stage別halo
- normalized geometryから解決したblock座標のcurve／polygon／volume bounds
- feature planと依存DAG
- land-water、elevation、zone等のfield descriptorとartifact
- drainage graph、lake、delta、fjord、waterfall plan
- geology、climate、ecology、material plan
- sparse volumetric plan
- module set、version、field ownership、merge rule
- named seed、canonical order、checksum
- hard/soft validation target
- memory、CPU、artifact、preview、palette budget

Blueprint以降はAI応答、raw画像、外部filesystem pathを参照しない。

V2-4-01の`geologyPlan`はV2-3 fixed priorのchecksum、named seed、uniformまたはempty province、4個のtyped field binding、bounded resource budgetをfreezeする。`generate.geology-foundation`は`compile.features`に依存し、既存Hydrology IR stageはこのversion付きstageへ依存するが、Hydrology plan自体のversion・fixed prior・checksumは変更しない。field payloadはBlueprintへ埋め込まず`LFC_GRID_V1` sidecarへ置く。

V2-4-02の`lithologyPlan`は`geologyPlan`のchecksumとprovince／formation／scalar descriptorへ厳密に結合する。catalog、assignment、resource budget、catalog／plan checksumをBlueprintにfreezeするが、V2-4-01の4 field contract、sidecar semantic code、Hydrology planは変更しない。Minecraft paletteとstrataは保持しない。

V2-4-03の`strataPlan`は`lithologyPlan`／`geologyPlan` checksumへ結合し、province profile、layer order／thickness、bounded fold/tilt、surface-exposed derived hardness／permeability、およびV2-3 FixedPriors／reconciliation algorithmからの明示 hydrology geology-input handoffをfreezeする。dense 3D strata payloadとHydrology planのin-place変更は行わない。

V2-4-04の`climatePlan`は32-cell coarse precipitation／runoff prior、final temperature／moisture、4 fieldのphase／single owner、named seed、CPU／retained／window budgetをfreezeする。`generate.climate-prior`はgeologyの後、`compile.hydrology-ir`の前に置き、`generate.climate-final`はhydrology reconciliation後に置く。Hydrology handoffはsource plan／fixed prior／generator versionとreplacement prior checksumを厳密に結合し、V2-3 artifactをin-placeで再解釈しない。payloadはdescriptor-onlyで、sidecarとRelease capabilityは後続Taskへ残す。

## 2. 契約の分離

Schema versionを全artifactで同じ値にするv1の前提はやめ、compatibility matrixで結ぶ。

| 契約 | versionの意味 |
|---|---|
| Generation Request | 入力、bounds、reference image、constraint map source/encodingの契約 |
| TerrainIntent | feature、geometry、constraint、canonical map bindingの意味契約 |
| WorldBlueprint | compile済みIRとsidecar参照の契約 |
| Generator | algorithm、module set、seed、量子化規則 |
| Release format | package構成、manifest、preview index、tile成果物 |
| Minecraft compatibility | block state、Sponge DataVersion、WorldEdit互換 |

`request schema v2` と `intent schema v2` が必ず同じ整数である必要はない。`BlueprintCompilerV2` が対応表を持ち、未対応の組合せを推測せず拒否する。

現行の公開CLI／Paper／Release 1はv1契約だけを使用する。V2-0のBlueprint契約とV2-1のfield bundleはoffline境界であり、`generator 3.0.0-phase6`やRelease format 1のartifactへ混在させない。

## 3. Blueprintの論理構造

```text
WorldBlueprintV2
├── identity
│   ├── blueprintVersion / requestId
│   ├── sourceRequestChecksum / sourceIntentChecksum
│   └── compilerVersion / generatorVersion
├── space
│   ├── bounds / coordinateSystem
│   └── tilePolicy / stageSupport
├── determinism
│   ├── globalSeed
│   ├── seedNamespaces[]
│   └── canonicalizationVersion
├── modules[] / stageGraph[]
├── fields[]
├── featurePlans[]
├── coastalFeaturePlans[]
├── sandyBeachPlans[]
├── meanderingRiverPlans[] / lakePlans[] / canyonPlans[]
├── waterfallPlans[] / deltaPlans[]
├── hydrologyPlan
├── geologyPlan / climatePlan / ecologyPlan
├── volumePlan
├── structurePlanInputs
├── validationTargets[]
├── budgets
└── resources[]
```

## 4. Field model

### 4.1 Field descriptor

Blueprint JSONへ1000×1000の数値配列を直接埋め込まない。各fieldはdescriptorとcontent-addressed artifactで表す。

```json
{
  "fieldId": "macro.land-water",
  "semantic": "DESIRED_LAND_WATER",
  "valueType": "U8",
  "width": 256,
  "length": 256,
  "space": "RELEASE_LOCAL_XZ",
  "sampling": "NEAREST",
  "storage": "SIDECAR",
  "artifact": {
    "relativePath": "fields/macro.land-water.lfgrid",
    "definition": {
      "fieldId": "macro.land-water",
      "semantic": "DESIRED_LAND_WATER",
      "valueType": "U8",
      "width": 256,
      "length": 256,
      "coordinateSpace": "RELEASE_LOCAL_XZ",
      "sampling": "NEAREST",
      "scaleMillionths": 1000000,
      "offsetMillionths": 0,
      "hasNoData": true,
      "noDataRaw": 255
    },
    "encodingVersion": "LFC_GRID_V1",
    "artifactChecksum": "0000000000000000000000000000000000000000000000000000000000000000",
    "semanticChecksum": "0000000000000000000000000000000000000000000000000000000000000000",
    "provenance": {
      "sourceKind": "CONSTRAINT_MAP",
      "sourceId": "constraint-source:coast-mask",
      "sourceChecksum": "0000000000000000000000000000000000000000000000000000000000000000",
      "decoderId": "numeric-png",
      "decoderVersion": "1",
      "transformId": "constraint-pixel-center-fixed-v1"
    }
  }
}
```

`LFC_GRID_V1` はV2-1で導入済みのcompact sidecar形式である。format ADRでheader、endianness、上限を固定し、次を満たす。上のland-water例ではraw `0/1`を意味値`0.0/1.0`として解釈するため`scaleMillionths`は`1_000_000`である。

- 寸法とpayload長をalloc前に検証できる
- row-major順と数値endiannessが一意
- V2-1で必要な `U8/U16/I32` のcompact型を使える。型追加はencoding version互換性を検討して行う
- NaN、locale、platform float表現を正本にしない
- header内semantic checksumと、外部descriptorのfile全体artifact checksumを検証する
- tile／window単位で読み、stage終了時に解放できる
- version 1は非圧縮row-majorで、chunk、chunk checksum、row paddingを持たない

V2-1のfield provenanceにある`transformId`は変換algorithmのversionを識別する。rotation／flip／crop等の具体値はsource Requestにあり、strict indexの`sourceRequestChecksum`がそのcanonical Request全体をbindingする。descriptor単体から変換parameterを推測してはならない。

### 4.2 必須field群

すべてを常時full resolutionで保持しない。macro resolution、vector plan、tile-local derived fieldを使い分ける。

| Field | 役割 | 推奨表現 |
|---|---|---|
| land-water | 陸海hard boundary | bit/U8 mask |
| elevation guide | 暫定／最終地表高 | minY相対I16 |
| zone assignment | region／feature所属 | U16 ID |
| distance fields | coast、river、ridgeへの距離 | I16/Q16、必要stageのみ |
| slope/aspect | 派生地形 | tile-local I16/U8 |
| water surface | cellごとの水面Y | I16＋no-data |
| water body | 海、川、湖、湿地のID | U16/I32 |
| flow direction/accumulation | 水系solve | U8＋I32、solve後に必要部分だけ保存 |
| lithology/strata | 岩相と地層 | U16 ID＋vector descriptor |
| temperature/moisture | 気候 | fixed-point I16 |
| salinity/wetness/exposure | 生態条件 | U8/U16 |
| habitat/material profile | 生態／block resolver入力 | U16 ID |
| constraint residual | desiredとactualの差 | typed sparse/tile field |

## 5. Geometry registry

Intentのnormalized geometryは、compilerがboundsに対して量子化し、Blueprint内のstable geometry IDへ解決する。

| Geometry | Blueprint表現 |
|---|---|
| point / multipoint | release-local fixed-point X/Z、必要ならY |
| spline | stable control point、補間法、幅profile、arc-length table |
| polygon | windingを正規化したring、hole、bounds、rasterization rule |
| mask | field artifact参照、threshold／label集合 |
| volume | AABB、SDF/CSG operator tree、support radius |

compilerは自己交差polygon、重複ID、範囲外point、0長spline、未解決map、曖昧なring方向を拒否する。自動修復する場合は入力を上書きせず、正規化結果とwarningをBlueprintへ保存する。

`V2-2-01`の実装は4 coastal kindだけを`CoastalFeaturePlanV2.BlockGeometry`へ解決する。`SPLINE`／`MULTI_SPLINE`はstable pathとinterpolation、`POLYGON`はring indexを保持し、各pointをrelease-local block millionthsへ `normalizedMillionths × (dimension - 1)` で変換する。`geometryVersion=1`、source geometry checksum、Blueprint boundsをstrict検証し、spline control-line自己交差を拒否する。一般geometry registryやarc-length table、rasterization ruleは後続Taskである。

## 6. FeaturePlan

各featureはIntentの自由な意味記述ではなく、特定moduleが実行できるplanへcompileされる。

```text
FeaturePlan
├── featureId / kind
├── moduleId / moduleVersion
├── resolvedGeometryIds[]
├── typedParameterSet
├── relationRefs[]
├── required/provided FieldKey
├── writeRegion / supportRadius
├── seedNamespace
├── validationTargetIds[]
└── provenance
```

同じfieldへ複数featureが書く場合、暗黙のlast-write-winsは禁止する。Blueprintが `MIN/MAX/ADD_CLAMP/MASK_UNION/PRIORITY_BLEND/ORDERED_CSG` のいずれかとpriorityを明記する。

`V2-2-01`では一般`FeaturePlan`に加え、4 coastal kindだけが同じfeature IDの`CoastalFeaturePlanV2`を必須とする。planはgeometry role、明示coast side、signed-distanceの符号／最大距離、nearshore profile、support radiusを持つ。`V2-2-02`でmodule出力を`coastal.actual-land-water`、`coastal.coast-side`、`coastal.signed-distance`、`coastal.normal-x`、`coastal.normal-z`、`coastal.nearshore-profile`へ拡張した。

`V2-2-03`では各`SANDY_BEACH` featureと同じIDの`SandyBeachPlanV2`を必須にし、`coastal.beach.local-width`、`coastal.beach.surface-height`、`coastal.beach.band`、`coastal.beach.semantic-sand`を追加した。planは`planVersion=1`、`ENDPOINT_TAPER`、幅／foreshore／slope／nearshore／vertical bounds／field binding／supportをstrictにfreezeする。他kindへbeach planが付く、SANDY featureのplanが欠ける、field ownerが異なる場合はBlueprint構築時に拒否する。Blueprint JSONへraster payloadは埋め込まず、kernelがglobal X/Zから直接またはbounded windowとして導出する。

`V2-2-04`では各`HARBOR_BASIN` featureと同じIDの`HarborBasinPlanV2`を必須にし、`coastal.harbor-basin.region`、`coastal.harbor-basin.water`、`coastal.harbor-basin.water-depth`、`coastal.harbor-basin.bottom-height`を追加した。planは`planVersion=1`、`EDGE_TO_CENTER_LINEAR`、水深範囲、profile transition、2つのstable entrance endpoint、opening edge／outward unit、入口回廊長、vertical bounds／field binding／supportをfreezeする。4 fieldはdescriptor-onlyで、payloadをBlueprintへ埋め込まない。plan欠損、kind／field owner不一致、future plan versionはstrictに拒否する。

`V2-2-05`では各`BREAKWATER_HARBOR` featureと同じIDの`BreakwaterHarborPlanV2`を必須にし、`coastal.breakwater.region`、`coastal.breakwater.arm-index`、`coastal.breakwater.top-height`、`coastal.breakwater.bottom-height`を追加した。planは`planVersion=1`、ID順の2 armと長さ／endpoint所有、`FLAT` crest、`LINEAR_SIDE_SLOPE`とrun/rise、inner／outer depth、要求／実clear opening、HARD enclosure relation／basin ID、vertical bounds／field binding／supportをfreezeする。4 fieldはdescriptor-onlyで、payloadやblock listをBlueprintへ埋め込まない。breakwater plan欠損、basin／relation／field owner不一致、future plan versionはstrictに拒否する。

`V2-2-06`では各`ROCKY_CAPE` featureと同じIDの`RockyCapePlanV2`を必須にし、`coastal.cape.region`、`coastal.cape.surface-height`、`coastal.cape.rock-exposure`、`coastal.cape.descriptor-index`を追加した。planは`planVersion=1`、`TWO_POINT_FIVE_D_ONLY`、cardinal seaward side、cliff／relief／露岩、stable channel／sea-stack descriptor、vertical bounds／field binding／support／turning countをfreezeする。descriptor合計は16以下で、1000角のfield payloadや3D配列をBlueprintへ埋め込まない。plan欠損、kind／field owner／descriptor座標不一致、future plan versionはstrictに拒否し、`LOCAL_VOLUME_REQUIRED`はplanを作らずV2-5への明示診断とする。

`V2-2-07`ではcoastal featureが1つ以上あるBlueprintに1個の`CoastalTransitionPlanV2`を必須にする。planはcompositor module/version、`PRIORITY_BLEND`、HARD／ambiguity policy、feature kind／priority／stable owner index、relation由来pair interaction、入力16 field、出力5 field、最大bandと一致するsupportをfreezeする。出力は`coastal.composed.land-water`、`surface-height`、`owner-index`、`blend-weight`、`conflict`で、専用moduleが`SINGLE_OWNER`として所有する。複数featureの寄与規則はplan内のmerge contractが正本であり、複数module ownerやlast-write-winsにはしない。contributorとFeaturePlanのpriority／kind不一致、共有relation欠落、field／module/version／halo不一致、重複pairをBlueprint構築時に拒否する。

`V2-2-08`ではcompilerがcoastal planからHARD `ValidationTargetV2`を生成する。beach width p50、harbor depth p50、breakwater clear opening、cape rock exposure、transition conflictがtarget ID、rule/version、expected range、tolerance、required field、diagnostic layerをBlueprint checksumへ含める。`v2.coast.validation-preview`はfieldを所有せず、`validate.coastal` stageでfinal samplerを読むbuilt-in capability descriptorである。実測値やPNG byte列はBlueprintへ埋めず、次段のvalidation／preview artifactが保持する。

`V2-3-13`ではcompilerがhydrology／landform planからHARD `ValidationTargetV2`を生成する。river reachability／bed、lake spill、delta mouth、tidal／fjord marine、waterfall drop、mountain ridge、volcanic components、reconciliation residualがtarget集合へ入り、`v2.hydrology.validation-preview`は`validate.hydrology` stageでfield samplerとoptional reconciliation artifactを読む。実測値と12 PNGはBlueprint外のvalidation／preview artifactが保持する。

## 7. Module manifestとstage graph

初期v2は第三者classを動的に読むplugin systemにしない。単一Gradle project内のbuilt-in catalogを使い、data-driven presetはallowlist済みmodule IDだけを参照する。

```text
ModuleDescriptor
├── moduleId / moduleVersion
├── supportedFeatureKinds[]
├── stage
├── reads[] / writes[]
├── mergeOperators[]
├── supportRadiusXZ / supportRadiusY
├── globalOrTiled
├── validationRuleIds[]
└── previewLayerIds[]
```

compilerは次を拒否する。

- module ID/version重複、未知module
- fieldの複数ownerと未宣言merge
- 未供給fieldのread
- stage dependency cycle
- budgetを超えるsupport radius
- feature kindとmoduleの不一致

stage順と同stage内のmodule順はBlueprintへ保存し、通常は `stage ordinal → moduleId → featureId` の辞書順とする。実行threadの完了順をchecksumへ反映しない。

## 8. Named seed

module追加で既存featureの乱数列がずれないよう、shared mutable RNGやlist index由来seedを使わない。

```text
featureSeed = SHA-256-to-long(
  globalSeed,
  generatorVersion,
  moduleId,
  moduleVersion,
  featureId,
  stageId,
  purpose
)
```

各入力は型tag＋長さ付きUTF-8またはbig-endian整数として連結し、digest先頭8 byteをsigned big-endian longとして読む等、曖昧さのないderivation versionを定める。geometry正規化、graph routingの同cost tie-break、sparse placement、palette順はstable IDとglobal座標で決める。module set、module version、merge順、fixed-point量子化versionをBlueprint checksumへ含める。

## 9. HydrologyPlan

詳細は [hydrology.md](hydrology.md) を参照する。Blueprintには少なくとも次をfreezeする。

- drainage basinとoutlet
- node/reach IDを持つ有向graph
- source、confluence、bifurcation、lake inlet/outlet、fall、mouth
- centerline、bed/water surface、width/depth/discharge profile
- lake basin、level、rim、spillway
- delta distributary、fan、sediment zone
- fjord marine channel、U字断面、wall profile
- waterfall lip、drop、plunge poolとvolume連携

flow accumulationやbasin fillingをtileごとに再計算してはいけない。global solveをchecksum付きでfreezeし、tile generatorは空間indexから交差reachをrasterizeする。

`V2-3-01`では、このtarget contractのうちversion付きgraph envelopeを実装した。`HydrologyPlanV2`はbasin／node／reach／water body／fall、固定prior、6 field binding、graph/work budget、plan checksumを持つ。通常compilerが保存するgraphはemptyであり、source／outletやrouteを推測しない。minimal graphではendpoint、incoming／outgoing index、basin／water body／fall binding、cycle、reachabilityをstrictに検査する。fieldはすべて`v2.hydrology.ir`のdescriptor-only `SINGLE_OWNER`で、routing値のpayload、V2-4 geology/climate field、Release capabilityは含まない。

`V2-3-02`ではBlueprint自体へ1000角payloadを埋めず、HydrologyPlan checksumとfixed-prior checksumにbindingした別の`HydrologyRoutingArtifactV2`をfreezeする。artifactはsource provisional-surface checksum、明示outlet、stable basin summary、D8 direction U8、flow accumulation I32、resource usage、graph／field／routing checksumを持つ。2 fieldは`LFC_GRID_V1` sidecarとしてbounded windowで読み、index外file、未知code、out-of-bounds direction、downstream accumulation非増加、terminal／basin coverage不一致を拒否する。通常Blueprintのempty graphは引き続き勝手にsource／reachへ補完されず、river feature graphへの反映は`V2-3-03`以降で明示version化する。

`V2-3-03`〜`V2-3-06`はfeatureごとのversion付きexecution planを`meanderingRiverPlans[]`、`lakePlans[]`、`canyonPlans[]`、`waterfallPlans[]`へ追加する。各planは対応FeaturePlan、relation、bounds、field ownership、source geometry checksumと一致しなければならず、payloadはdescriptor-onlyのままである。

`V2-3-07`は各`DELTA` featureと同じIDの`DeltaPlanV2`を`deltaPlans[]`へ必須追加する。planはtrunk river feature／reach／geometry checksum、HARD `DRAINS_TO`／`EMPTIES_INTO` relation、receiving SEA boundary、canonical fan ring、apex、stable distributary branch／mouthとinteger discharge share、sandbar、parameter選択値、vertical／world bounds、7 field ID、support／CPU budgetをfreezeする。branch graphはapexから全mouthへ到達し、mouthは宣言境界上、share総和は`1_000_000`でなければならない。DELTA plan欠損、kind／trunk／relation／bounds／field owner不一致、future plan version、landlocked mouth、budget超過はBlueprint構築前後でstrictに拒否する。tidal edge、wetland、sediment material、raster payloadは含めない。

`V2-3-08`は各`TIDAL_CHANNEL_NETWORK` featureと同じIDの`TidalChannelPlanV2`を`tidalChannelPlans[]`へ必須追加する。planはHARD `EMPTIES_INTO`、receiving SEA boundary、`BIDIRECTIONAL` edge kind、stable node／edge graph、optional mangrove `WetlandChildPlanHook`、width／tidal range選択値、vertical／world bounds、4 field ID、support／CPU budgetをfreezeする。全nodeは宣言海境界上のmarine seedからundirected到達できなければならない。TIDAL plan欠損、kind／outlet／bounds／field owner不一致、closed／ambiguous／isolated graph、future edge kind、budget超過はBlueprint構築前後でstrictに拒否する。salinity／hydroperiod、raster payloadは含めない。

`V2-4-09`は各`MANGROVE_WETLAND` featureと同じIDの`MangroveWetlandPlanV2`を`mangroveWetlandPlans[]`へ必須追加する。planはclosed wetland polygon、microRelief／waterloggedShare選択値、optional `TidalNetworkPlanHook`（HARD WITHIN tidal→wetlandの逆参照）、5 field ID（wetland-mask／surface-height／open-water-gap／substrate-class／micro-relief）、support／CPU budgetをfreezeする。tidal hookがある場合は対応`TidalChannelPlanV2`が存在し、Stage 6 `MANGROVE_TIDAL_LINK`がmarine connectionをVERIFY_ONLYで結合する。tree/root／ecology payloadは含めない。

`V2-4-10`は各`CORAL_REEF` featureと同じIDの`CoralReefPlanV2`を`coralReefPlans[]`へ必須追加する。planはreef ring（outer polygon＋lagoon hole）、crest depth／reef width／outer slope選択値、required `LagoonPlanHook`（HARD ENCLOSED_BY lagoon→reef）、`ReefPassPlanHook` list（HARD CARVES_THROUGH＋CONNECTS_TO、centerline arc length）、lagoon depth選択値、5 field ID（reef-mask／crest-depth／lagoon-depth／pass-corridor／marine-connection）、support／CPU budgetをfreezeする。pass hookがある場合はStage 6 `REEF_LAGOON_PASS`がmarine connectionをVERIFY_ONLYで結合する。`LAGOON`／`REEF_PASS`はreef relation経由でcompileし、独立typed plan arrayは持たない。coral object／ecology payloadは含めない。

`V2-4-11`はstandaloneの`EcologyPlanV2`（`ecology-placement-contract-v1`）を追加する。habitat U16、閉じたassemblage catalog、density／spacing lattice、mangrove／coral／alpine descriptorsを提供するが、このTaskでは`WorldBlueprintV2.ecologyPlan`配線とPaper placementは行わない（material profileと同様のstandalone契約）。

`V2-4-12`はstandaloneの`FeatureMaterialProfilePlanV2`（`feature-material-profile-contract-v1`）を追加する。volcanic basalt／tuff／ashとcanyon strata／talus／sediment overlayを提供するが、shape plan arrayとMinecraft paletteは変更せず、Blueprint配線は行わない。

`V2-3-09`〜`V2-3-11`は`fjordPlans[]`、`mountainPlans[]`、`volcanicPlans[]`へ各featureのversion付き2.5D skeleton、field binding、bounds／support／budgetをfreezeする。material／ecology／volume payloadは含めず、対応FeaturePlanとのID／kind／relation／field整合をconstructorで検査する。

`V2-3-12`は必須`hydrologyReconciliationPlan`へsource HydrologyPlan checksum、module／stage／algorithm／scan-order version、固定3 pass、regional scalar variable、hard target、work／working-set／artifact budget、canonical checksumをfreezeする。moduleは既存hydrology／regional fieldを読むがfieldを所有せず、`reconcile.hydrology` stageは対象regional stageへ明示依存する。variable／constraintのfeature kind不一致、未知参照、unused variable、source checksum、module version、budget不一致をBlueprint構築時に拒否する。実測final valueとresidualはBlueprintへ埋めず、別の`HydrologyReconciliationArtifactV2`へ保存する。

## 10. Geology、climate、ecology

詳細は [geology-climate-ecology.md](geology-climate-ecology.md) を参照する。BlueprintではMinecraft block stateでなくsemantic profileを保持する。

```text
GeologyPlan: lithology、strata、hardness、permeability、erosion response
ClimatePlan: temperature、moisture、precipitation、wind、salinity influence
EcologyPlan: habitat、assemblage、density、placement rule（V2-4-11でstandalone `EcologyPlanV2`として実装。Blueprint配線は後続）
MaterialPalettePlan: surface/substrate/bedrock/wet/underwater variants
```

Minecraft 1.21.11 block stateへの解決はformat/worldedit側のversion固定adapterで行う。

## 11. VolumePlan

詳細は [volumetric-terrain.md](volumetric-terrain.md) を参照する。全world density配列ではなく、局所AABBと解析的operatorを保存する。

```text
VolumePlan
└── VolumeFeaturePlan[]
    ├── AABB / featureSeed
    ├── ordered ADD_SOLID / CARVE_SOLID / ADD_FLUID / REMOVE_FLUID / PAINT / PLACE operations
    ├── SDF primitives / swept spline / noise warp
    ├── support radius / material policy
    └── validation targets
```

`V2-5-01`ではSDF primitive集合だけを`volume-sdf-primitive-plan-v2`として独立契約化した。`V2-5-02`でordered CSG operationsを`volume-csg-plan-v2`へ追加した。`V2-5-03`でAABB index descriptorを`volume-aabb-index-plan-v2`へ追加した。`V2-5-04`で3D tile-cache contractを`volume-tile-cache-plan-v2`へ追加した。`V2-5-05`で`VolumeTerrainQueryV2`がbase heightfield＋ordered CSGをcommon `TerrainQuery`へ合成する。`V2-5-06`で`cave-network-plan-v2`が`CAVE_NETWORK` graph→carveを`EXPERIMENTAL`で提供する。`V2-5-07`で`lush-cave-plan-v2`が`LUSH_CAVE` WITHIN／REACHABLE_FROM／wet surfaces／ecology hooksを`EXPERIMENTAL`で提供する。`V2-5-08`で`underground-lake-plan-v2`が`UNDERGROUND_LAKE` basin carve＋単一`ADD_FLUID`を`EXPERIMENTAL`で提供する。`V2-5-09`で`sea-cave-plan-v2`が`SEA_CAVE` cliff opening＋静的marine fluidを`EXPERIMENTAL`で提供する。`V2-5-10`で`overhang-plan-v2`が`OVERHANG` host support＋ADD_SOLID／CARVE recessを`EXPERIMENTAL`で提供する。`V2-5-11`で`natural-arch-plan-v2`が`NATURAL_ARCH` two-pier＋through carveを`EXPERIMENTAL`で提供する。`V2-5-13`で`waterfall-volume-plan-v2`がfall geometry checksum-bound column／behind／plunge静的fluidを`EXPERIMENTAL`で提供する。`V2-5-14`で`volume-local-environment-plan-v2`がpost-volume surface／material／sparse placementを`EXPERIMENTAL`で提供する。volume validators／preview以降は`V2-5-15`以降で拡張する。

tile生成時だけ交差AABBを16³または32³のbounded cacheへ評価し、commit後に解放する。operator順は意味を変えるためartifactの一部である。

## 12. ValidationTarget

Hard/soft constraintを曖昧な文章のまま残さず、測定可能なtargetへcompileする。

```text
ValidationTarget
├── targetId / sourceConstraintId
├── featureId / ruleId / ruleVersion
├── hardness / weight
├── metric / expected range / tolerance
├── spatial scope
├── required fields
└── failure severity / diagnostic layer
```

たとえば `beach width 20〜55 blocks`、`river reaches mouth`、`fjord sidewall 35以上`、`cave roof 4以上` を同じ仕組みで扱う。詳細は [validation-and-preview.md](validation-and-preview.md) を参照する。

## 13. Budget

Blueprint compile時に次の上限を確定し、generator投入前に拒否できるようにする。

- feature、relation、constraint、geometry point数
- field artifactの合計byte数と同時resident byte数
- graph node/edge、curve control point、cave tunnel総延長
- volume feature、operator、AABB総体積、active chunk数
- stage別halo、tile cache、1 voxelが参照する最大feature数
- validation scan、issue数、preview枚数／pixel／byte
- palette state、schematic展開、Release／snapshot disk

現行の1000×1000、vertical span 512、bounded CPU executor、admission制限を維持する。全面 `1000×1000×512` densityは1 byteでも約488 MiBのため禁止する。

## 14. Artifact配置

Release format 2の想定例を示す。正確なfile名はformat ADRで固定する。

```text
blueprint/
├── world-blueprint.json
├── module-manifest.json
├── stage-graph.json
├── geometries.json
├── features.json
├── validation-targets.json
└── fields/*.lfgrid
plans/
├── hydrology.json
├── geology.json
├── climate.json
├── ecology.json
└── volumes.json
constraints/
└── canonical map artifacts
```

すべてのresource参照はRelease rootからの安全な相対path、型、寸法、semantic checksum、artifact SHA-256を持つ。外部path、symlink、未列挙fileを許可しない。

## 15. Checkpointと再開

stage出力は次のkeyで再利用可否を決める。

```text
input stage checksums
+ blueprint checksum
+ module ID/version set
+ generator/canonicalization version
+ seed namespaces
+ output artifact checksums
```

一部だけ一致する古いcacheを推測再利用しない。global hydrology等の安全なcheckpointが一致した場合だけtile stageを再開する。cancelまたは失敗したtemporary artifactはREADYとして公開しない。

## 16. v1との関係

| 項目 | v1 | v2方針 |
|---|---|---|
| Blueprint | request/Intent/seed wrapper | compile済みIR＋sidecar |
| logical map | continental/relief 2枚 | typed field catalog |
| seed | candidate seed中心 | module/feature/stage named seed |
| margin | 固定16 | stage/module別support |
| water | global level＋depth | water body、surface、graph、fluid volume |
| material | 6 enum | semantic profile＋versioned resolver |
| 3D | なし | bounded sparse volume |
| validation | 固定rule | compiled target＋registry |

v1 Blueprintをin-placeでv2へ書き換えない。元requestとIntentから明示的に再compileし、情報不足をwarningとして新artifactへ記録する。既存v1 Releaseは旧Blueprintとchecksumのまま維持する。
