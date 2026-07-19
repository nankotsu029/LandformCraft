# Generation Pipeline v2

> Status: 多段地形pipeline全体は段階実装中。V2-0〜V2-4のPhase gateを完了し、geology／lithology／strata、coarse climate prior／final fields、regional water／snow、semantic material／Minecraft palette、mangrove／coral、sparse ecology、volcanic／canyon feature material、独立environment validator／preview、Release 2 `environment-fields`をoffline `SUPPORTED`へ統合した。V2-5ではSDF／CSG／index／cache／TerrainQuery／局所volume feature／waterfall volume／post-volume local environment／volume validators／5-layer preview／offline 3D read-back／Release 2 `sparse-volume`を追加し、`V2-5-18`のPhase gate（[Volume監査](audits/v2-5-phase-gate.md)）でoffline `SUPPORTED`へ昇格した。現行 `TerrainGenerator`、Release 1、公開CLI／Paper配置の挙動は変更していない。Track Aの次は`V2-6-01`である。

## 1. 設計方針

v2の地形は、1個の汎用noise generatorではなく、複数の意味場とfeature planを明示的なpass DAGで合成する。

```text
地形 = elevation + land/water + hydrology + geology + climate
     + ecology + material + local volume + sparse feature + structure
```

各passは入力field、出力field、merge rule、seed namespace、必要halo、resource budget、validator、previewを宣言する。暗黙の共有配列、module登録順、thread完了順へ結果を依存させない。

### 1.1 実装済みcoastal compile foundation

`V2-2-01`では`SANDY_BEACH`、`BREAKWATER_HARBOR`、`HARBOR_BASIN`、`ROCKY_CAPE`を、`v2.coast.foundation` moduleの`CoastalFeaturePlanV2`へcompileする。normalized point `p` はrelease-local block millionthsの `p × (dimension - 1)` へ整数演算だけで解決する。beachはspline進行方向に対する`landSide=LEFT|RIGHT`をIntentで必須とし、符号をedge constraintから推測しない。

moduleは`generate.coastal-raster` stage、XZ halo 64、feature named seed、次の共通／beach fieldに加え、harbor／breakwater／capeの各4 descriptor-only fieldを宣言する。現在の22 fieldのownerは同一built-in moduleだけで、mergeは`SINGLE_OWNER`である。

| Field ID | Semantic | Value type | V2-2-02の状態 |
|---|---|---|---|
| `coastal.actual-land-water` | `ACTUAL_LAND_WATER` | `U8` | coast側分類またはHARD maskのexact値 |
| `coastal.coast-side` | `COAST_SIDE` | `U8` | land=1／water=0 |
| `coastal.signed-distance` | `COAST_SIGNED_DISTANCE` | `I32` | land-positive block millionths |
| `coastal.normal-x` | `COAST_NORMAL_X` | `I32` | land向きunit normal millionths |
| `coastal.normal-z` | `COAST_NORMAL_Z` | `I32` | land向きunit normal millionths |
| `coastal.nearshore-profile` | `NEARSHORE_PROFILE` | `I32` | water側depth millionths、非対象はno-data |
| `coastal.beach.local-width` | `BEACH_LOCAL_WIDTH` | `I32` | endpoint taper済み幅millionths、非対象はno-data |
| `coastal.beach.surface-height` | `BEACH_SURFACE_HEIGHT` | `I32` | absolute Y millionths、非対象はno-data |
| `coastal.beach.band` | `BEACH_BAND` | `U8` | outside／foreshore／backshore／nearshore |
| `coastal.beach.semantic-sand` | `BEACH_SEMANTIC_SAND` | `U8` | land側砂帯=1、それ以外=0 |

`planVersion=1`と`geometryVersion=1`をstrictに読み、spline control-line自己交差、範囲外point、unsupported coastal relation、field／point／halo budget超過をcanonical Blueprint公開前に拒否する。

### 1.2 V2-9-01 surface foundation contract（EXPERIMENTAL）

`V2-9-01`はPLAIN／hill／mountain／valley／river／wetlandが共有する2.5D field・ownership・transition・seed契約を`SurfaceFoundationPlanV2`（`surface-foundation-field-contract-v1`）へ固定する。fieldは`foundation.surface-class`／`elevation`／`residual`／`owner-index`／`transition-weight`の5つで、mergeは`PRIORITY_BLEND`、ambiguityは`REJECT`、transition bandは0..32である。compileは`ScaleAdmissionV2`（`V2-8-01`）を通し、個別形状generator・kind `SUPPORTED`・WorldBlueprint埋込・Paper・LARGE enableは含まない。

`V2-9-02`は`PLAIN`（polygon＋microrelief＋groundwater handoff）と`HILL_RANGE`（spline ridge／saddle＋plain transition）のEXPERIMENTAL vertical sliceを追加する。`FoundationPlainHillSliceCompilerV2`が両plan・foundation merge・validation／preview index・sealed exportを閉じる。`BACKSHORE_PLAINS`はlegacy diagnosticのまま残し、alias写像のみを提供する。catalogは`EXPERIMENTAL`で、`SUPPORTED`昇格とWorldBlueprint埋込はV2-9-14以降とする。

`V2-9-03`は`MOUNTAIN_RANGE`（ridge／peak／saddle／spur／pass／foothill derived components）と`VALLEY`（V／U cross-section＋floor／shoulder＋fjord／river connection role）のEXPERIMENTAL vertical sliceを追加する。`FoundationMountainValleySliceCompilerV2`が両plan・foundation merge・validation／preview・sealed exportを閉じる。ALPINE／GLACIAL／FJORD specialized outputは変更せず、`SUPPORTED`昇格とWorldBlueprint埋込はV2-9-14以降とする。

`V2-9-04`はpublic `RIVER`（`MEANDERING_RIVER`とは別FeatureKind）のEXPERIMENTAL vertical sliceを追加する。`RiverPlanV2`はsource→mouth reach graphとbank／bed／discharge／floodplain handoffを持ち、`FoundationRiverSliceCompilerV2`がvalidation／preview／sealed exportを閉じる。legacy meander pathは不変である。

`V2-9-05`は`FLOODPLAIN`／`MARSH`のEXPERIMENTAL hydrologic surface sliceを追加する。独立field ownership（`foundation.floodplain.*`／`foundation.marsh.*`）でriver adjacency・groundwater／hydroperiod・wetness・open-water fluid／solid ownershipを閉じ、`FoundationFloodplainMarshSliceCompilerV2`がvalidation／preview／sealed exportを行う。surface mergeはfloodplain+marsh、`MANGROVE_WETLAND`は未変更である。

`V2-9-06`は`ROCKY_COAST`／`SEA_CLIFF`のEXPERIMENTAL coast／cliff foundation sliceを追加する。`RockyCoastPlanV2`／`SeaCliffPlanV2`と`FoundationRockyCoastCliffSliceCompilerV2`がrock shelf／exposure／channel／talus handoff、cliff face／talus／notch、coast transition、および`hostSupportAabb`経由のsea-cave／overhang host handoffを閉じる。surface mergeはcoast+cliff、`ROCKY_CAPE`と既存volume compilerは未変更である。

`V2-9-07`は`SINGLE_ISLAND`／`ARCHIPELAGO`／`VOLCANIC_CONE`のEXPERIMENTAL island／cone foundation sliceを追加する。各planとslice compilerがisland mass／shore／drainage／apron、group spacing／dominance／saddle、cone／crater／radial drainageを閉じる。`VolcanicIslandConeAdapterV2`はlegacy volcanic paramsからsuggested foundation paramsだけを返す。既存`VOLCANIC_ARCHIPELAGO` checksum／hook／moduleは未変更である。

`V2-9-08`は`OCEAN_BASIN`／`CONTINENTAL_SHELF`／`CONTINENTAL_SLOPE`のEXPERIMENTAL bathymetry foundation sliceを追加する。depth／slope／coast-distance／ownership／fluid-column-hintを2.5D fieldとしてsampleし、`FoundationBathymetryTransectCompilerV2`がcoast→basin monotone断面とwhole／tile depth checksum、streaming underwater column exportを閉じる。dense 3D water配列は使わない。catalog未登録・`SUPPORTED`ではない。

`V2-9-09`は`SUBMARINE_CANYON`のEXPERIMENTAL foundation vertical sliceを追加する。shelf head／slope crossing／basin outletのHARD relationに束縛されたSPLINE centerlineを、host bathymetry depth＋additional carveで2.5D corridor carveし、whole／tile checksumとstreaming underwater column exportを閉じる。surface `CANYON`とは別kindであり、catalog未登録・`SUPPORTED`ではない。

`V2-9-10`は`CAVE_ENTRANCE`のEXPERIMENTAL surface-volume connectorを追加する。POINT opening＋approach capsuleを、HARD `ENTRANCE_OF`でfrozen `CaveNetworkPlanV2`（featureId＋canonicalChecksum）へ、HARD `SUPPORTED_BY`で`MOUNTAIN_RANGE`／`VALLEY`へ束縛し、ordered `CARVE_SOLID`とroof／flood／owner／reachability validation、seamless query／export checksumを閉じる。in-network ENTRANCE nodeとは別概念であり、catalog未登録・`SUPPORTED`ではない。

`V2-9-11`は`UNDERGROUND_RIVER`のEXPERIMENTAL flooded-volume connectionを追加する。cave graph上のsource→outlet reachをBFSで解決し、channel `CARVE_SOLID`の後に単一owner `ADD_FLUID`（host underground lakeの`fluidBodyId`）を置く。`FLOODED_CAVE`はIntent hook kind＋plan-level `FloodedCaveFluidRegionHook`として静的fluid regionを表す。dynamic fluid simulationは行わず、whole／tile／scene export checksumでcave→river→lake sceneを検証する。catalog未登録・`SUPPORTED`ではない。

`V2-2-02`の`CoastalRasterKernelV2`はblock millionthsをQ12へround-half-away-from-zeroで変換し、polylineまたは16分割のinteger Catmull-Rom/Bezierでflattenする。cell中心の最近傍segmentからsigned distanceとnormalを整数演算で求め、最大距離でclampする。whole samplingもtile samplingもrelease-local global X/Zを使い、checksumはkernel version／field／寸法とrow-major raw valueをSHA-256へ送る。

windowはcore最大256×256、halo最大64、retained上限8 MiBで、1000角を全域dense配列にしない。`CoastalHardMaskWindowV2`は既存のstrict `LFC_GRID_V1` bounded readerからU8／NEAREST／0=water／1=landだけを受理し、no-data／未知値／definition不一致を拒否する。指定されたHARD cellはactual land-waterへexact copyし、coast noiseやnearshoreで上書きしない。

`V2-2-03`の`SandyBeachPlanV2`は幅min/max、endpoint taper、foreshore share、shore slope rangeと選択値、integer rise、nearshore、vertical bounds、field ID、support radiusをfreezeする。`SandyBeachGeneratorV2`は共通signed distanceを読み、endpoint距離に応じた幅、foreshore／backshore、linear nearshore bed、semantic sandを整数演算だけでsampleする。tangentは`beach-tangent-linear-v1` lookup＋linear interpolation、field checksumはglobal row-majorである。common kernelがsupport値で距離を飽和するため、land帯外縁を半開区間とし、nearshore終端の外側には1 blockの観測余白を必須にして遠方への誤拡張を防ぐ。このためnearshore距離は1..63、target depthは1..64である。beach windowもcore／halo制約を再利用し、4 fieldで8 MiB以下に制限する。これはdescriptor-only実行fieldであり、canonical sidecar化、Minecraft material解決、独立validator／previewは後続Taskである。

`V2-2-04`の`HarborBasinPlanV2`はHARDなbreakwater `ENCLOSES` relationと同一の2 named endpointをouter polygonの1 edgeへ解決し、opening幅／outward unit、入口回廊長、水深範囲、`EDGE_TO_CENTER_LINEAR` transition、vertical bounds、4 field ID、supportをfreezeする。`HarborBasinGeneratorV2`はpolygon interiorからholeを除外し、outward corridorをunionし、ringへの整数距離からwater depth／bottom heightをglobal X/Zでsampleする。HARD land cellと水域が衝突した場合は上書きせず拒否する。windowはcore最大256、halo最大64、4 fieldで8 MiB以下、checksumはversion付きglobal row-majorである。river／lake graph、breakwater arm、material、独立validator／previewは含まない。

`V2-2-05`の`BreakwaterHarborPlanV2`は、2本のnamed armをID順に固定し、それぞれのlandfall／opening endpoint、fixed-grid arm長、`FLAT` crest、`LINEAR_SIDE_SLOPE` foundationと明示run/rise、inner／outer depth、clear edge-to-edge opening、HARD `ENCLOSES` relationと対応basin IDをfreezeする。compile時にarm交差／self-intersection、endpoint所有、basin opening座標、実開口幅±0.5 block、vertical bounds、64 block supportを検査する。`BreakwaterHarborGeneratorV2`はopening端の外向き半平面をclipして開口を閉じず、region／stable arm index／top／bottomの4 fieldをinteger-onlyでglobal X/Z sampleする。windowはcore最大256、halo最大64、4 fieldで8 MiB以下、単体metricはarm長／開口幅／crest・inner・outer cell／最大4,000,000 solid blockをrow-major streaming測定する。material、fluid simulation、他featureとのtransition、独立validator／previewは含まない。

`V2-2-06`の`RockyCapePlanV2`は、単一polygon、明示cardinal `seawardSide`、`TWO_POINT_FIVE_D_ONLY` mode、cliff／local relief／cliff band／露岩率、最大4 channelと12 sea stackの有界descriptorをfreezeする。descriptorはglobal block millionthsのmouth／inland endまたはcenter、幅／長さ／深度または半径／沖距離／高さ、stable indexを持ち、合計16以下、support 64以下である。`RockyCapeGeneratorV2`はregion／surface height／rock exposure／descriptor indexの4 fieldをinteger-onlyでglobal X/Z sampleし、HARD water上のlandまたはHARD land上のchannelを拒否する。windowはcore最大256、halo最大64、4 fieldで8 MiB以下、単体metricはrelief、rock ratio、coast turning、channel／stack cellをrow-major streaming測定する。薄いland bridge、孤立stack、未知parameter、3D要求をcanonical plan公開前に拒否する。overhang／sea cave／full 3D stack、geology、独立validator／previewは含まない。

`V2-2-07`は`compose.coastal-transitions`を`generate.coastal-raster`の後段へ追加する。`ADJACENT_TO`／`OVERLAPS` relationは`transitionVersion=1`、`profile=PRIORITY_BLEND`、1..32 blockのbandを明示し、欠落／future versionを拒否する。compilerはfeature priorityとcanonical owner index、pair interaction、`PROTECT_EXACT`、`REJECT`、breakwater-over-basinの0-band connection seamを`CoastalTransitionPlanV2`へfreezeする。専用compositorは入力をfeature ID順へ正規化し、整数weightとstable reductionだけで`coastal.composed.land-water`、`surface-height`、`owner-index`、`blend-weight`、`conflict`を生成する。HARD classificationは反対classのsoft sampleから分離してexactに保ち、HARD-HARD、未契約overlap、同票categorical conflictはstable rule IDで拒否する。windowはhalo最大32、retained 2,000,000 byte以下で、1000角dense payloadを保持しない。

`V2-2-08`は`validate.coastal` stageに`v2.coast.validation-preview`を加える。`CoastalValidatorV2`はfinal field samplerをglobal row-majorで一度ずつ走査し、固定histogramとdescriptor集合だけを保持する。これによりgenerator-local metricを再利用せず、beach width、harbor depth／entrance、breakwater opening、cape exposure／complexity、transition conflict、desired/actual residualを測定する。`CoastalDiagnosticPreviewRendererV2`は11枚の固定PNGを一枚ずつallocation／write／checksumして解放し、`index.json`とdirectoryのstrict read-backが成功したbundleだけをatomic moveする。これはRelease format 2ではなく、次Taskのtile exportにもPaper配置にも接続しない。

`V2-2-09`はvalidation後のfinal `TerrainBlockResolver`を`OfflineTilePlanV2`のglobal座標で二走査する。第1走査はblock-state別countとcanonical semantic checksumだけを保持し、辞書順paletteとData byte長を確定する。第2走査はX fastest→Z→Y順でgeneral VarIntを直接GZip NBTへ書き、同じsemantic checksumを再計算してresolverの非決定的変化を拒否する。staging `.schem`はbounded inspectorがversion、DataVersion、dimension、offset、palette、VarInt entry、unknown state、block entity／biome不在、semantic checksumをread-backし、最後のcancel観測後にatomic publishする。WorldEdit 7.3.19 adapterは同じimmutable bytesをoffline readし、canonical checksumを再計算する。Release 2 indexとPaper applyにはまだ接続しない。

`V2-2-10`は生成stageを追加せず、生成済みartifactを収容するRelease 2 coreだけを`format.v2.release`へ隔離した。`manifest.json`はcanonical checksumを持ち、non-manifest fileを`artifacts[]`へ完全列挙し、`requiredCapabilities[]`は有効なpayload契約を明示する。`V2-2-11`は`surface-2_5d`だけを有効化し、request／intent／Blueprint、field、validation、preview、tileのcomplete setとsemantic checksum chainをdirectory／ZIP双方でstrict verifyする。publisherはstaging read-back後にatomic publishし、verifierはunknown capability／type／version、index外file、path／symlink／ZIP traversal／case collision、checksum、entry／展開／resident／disk budgetを拒否する。Paper applyには接続しない。

`V2-3-01`はStage 1のcompile contractだけを拡張した。`compile.hydrology-ir` stageと`v2.hydrology.ir` built-in moduleが6 descriptor fieldを所有し、version 1 graph envelope、checksum付きuniform geology／constant runoff prior、node／edge／field／CPU／resident budgetをBlueprintへsealする。通常結果はempty graphであり、Stage 4のrouting、field raster、reconciliation、Release `hydrology-plan` capabilityを先取りしない。1000角のbudget式とcanonical順序はinteger-onlyで、module登録順、locale、timezoneに依存しない。

`V2-3-02`はStage 4の最初のglobal passだけを実装した。freeze済みempty HydrologyPlan、integer provisional surface、明示outletを入力に、tile化前のpriority floodを1回実行する。priorityはfilled elevationとglobal cell ID、outletとbasin IDはglobal Z／X／ID順、accumulationはpriority-pop逆順で固定する。surface snapshotだけはbounded executorでtile順／worker数を変えられるが、disjoint cellへ書くpure処理であり、global solve結果は実行形態に依存しない。結果はD8 direction／accumulationの2個の`LFC_GRID_V1`とstrict indexへfreezeし、river／lake等のregional featureを作らない。

`V2-4-01`は`generate.geology-foundation`を`compile.features`の後、`compile.hydrology-ir`の前へversion付きで追加する。`GeologyPlanV2`はV2-3 fixed prior checksumを明示参照し、province／opaque formation／hardness／permeabilityの4個のU16 fieldを単一built-in moduleへ帰属させる。emptyまたはuniform minimal planをglobal X/Zでsampleし、4個の`LFC_GRID_V1`をbounded windowでstrict read-back後にatomic publishする。Hydrology planのversion・prior値・checksumは変更せず、lithology、strata、material、feature response、Release capabilityを先取りしない。

`V2-4-02`は新しいfield stageやsidecarを追加せず、`GeologyPlanV2`のcompile後に`LithologyPlanV2`をfreezeする。compile-time built-in catalogだけからprovince descriptorと完全一致するentryを選び、source geology checksumとassignment contractへ結合する。既存province fieldはbounded windowで読んでassignment適用を検査する。block state、strata、climate、feature response、Release capabilityを先取りしない。

`V2-4-03`は`LithologyPlanV2`の後に`StrataPlanV2`をfreezeする。provinceごとのordered strata profile、bounded fold/tilt、surface-exposed derived hardness／permeabilityをdescriptor＋pure resolverで表し、dense 3D strata配列を作らない。`HydrologyGeologyInputHandoff`はV2-3 FixedPriors／reconciliation algorithm checksumを明示参照したversion transitionを宣言し、Hydrology planの意味は変更しない。climate、material、Release capabilityを先取りしない。

`V2-4-04`は`generate.climate-prior`をgeology後／Hydrology IR前、`generate.climate-final`をhydrology reconciliation後へ追加する。prior moduleは32-cell coarse precipitation／runoff、final moduleは標高減率・緯度相当・exposure・flow accumulationからtemperature／moistureをinteger-onlyで計算する。`ClimatePlanV2`は4 fieldのphase／owner、named seed、resource budgetと、V2-3 `HydrologyPlan`／fixed prior／source generatorからtarget generatorへのchecksum付き明示transitionをfreezeする。V2-3 artifactは書き換えず、snow、sidecar、Release capabilityを先取りしない。

`V2-4-05`は`generate.water-condition`をclimate-finalとhydrology reconciliationの後へ追加する。`WaterConditionPlanV2`はHydrology／Climate moisture checksumへ結合し、bounded distance／groundwater／tidal influence／salinity／hydroperiod／wetness／residualの7 fieldをinteger-onlyでsampleする。implicit oceanとunbounded diffusionは拒否し、mangrove／coral／ecology／snow／Release capabilityは後続Taskへ残す。

## 2. End-to-end flow

```text
GenerationRequest v2
  → file / image / constraint security validation
  → AI Providerまたはmanual TerrainIntent v2
  → canonical Intent validation
  → constraint map canonicalization
  → WorldBlueprint v2 compile
  → macro layout
  → geology foundation / climate priors
  → global hydrology solve
  → regional feature shaping
  → bounded hydrology reconciliation
  → meso erosion / deposition / transition
  → final climate / wetness / soil
  → sparse local volume add/carve/fluid
  → ecology / semantic material / micro detail
  → small structure planning
  → feature-specific validation and scoring
  → diagnostic previews
  → tile block stream / Sponge v3
  → Release format 2 strict verify
  → reserve / bound confirm / snapshot-all / apply / verify / rollback
```

## 3. Stage定義

### Stage 0: 入力境界

- request、Intent、constraint descriptorを完全Schemaとrecord不変条件で検査する。
- reference imageは既存と同様にProvider向けにsanitizeする。
- constraint mapはAIを介さず、型ごとのdecoderでcanonical fieldへ変換する。
- raw path、metadata、unknown label、過大raster、重複ID、hard conflictを拒否する。

出力はcanonical request/Intent bytes、画像evidence、constraint provenanceである。

### Stage 1: Blueprint compile

- geometryをnormalized X/Zからrelease-local fixed-pointへ解決する。
- feature relationをDAGへし、moduleとtyped parameterへcompileする。
- field ownership、merge operator、seed namespace、stage順を固定する。
- hard/soft constraintを測定可能なvalidation targetへ変換する。
- peak memory、halo、graph、volume、palette、preview budgetを事前検査する。

この段階ではMinecraft blockを作らない。

### Stage 2: Macro layout

coarseな世界の骨格を作る。

- land-water mask
- provisional elevation
- macro zone / ecoregion assignment
- coast、ridge、valley、island chainのguide
- protected／excluded region

hard maskをnoiseで上書きしない。soft guideは重み付きcostやblendとして使い、残差を保存する。

### Stage 3: Geology foundationとclimate priors

地質は侵食と水系の前提なので、lithology、strata、hardness、permeabilityを先に決める。hydrologyが必要とする降水／融雪等はcoarseなclimate priorとしてここで固定する。

V2-4-04の最終temperature／moistureは地形と水系確定後に再計算する。V2-4-05のwetness／salinity／hydroperiodは同じ境界で接続し、snowlineとhabitatは後続Taskで同じ一方向 `geology → climate prior → hydrology → final climate/water-condition/ecology` を保つ。

### Stage 4: Global hydrology solve

tileに分割する前に次を確定する。

- basin、outlet、flow direction／accumulation
- source→mouth reach graph
- lake level、rim、spillway、inlet/outlet
- delta distributaryとsediment fan
- fjord marine channel
- waterfall lip/drop/plunge pool

priority floodやroutingの同costはglobal cell IDでtie-breakする。tileごとにriver pathを作らない。

実装済みの`V2-3-02`はこのうちbasin、明示outlet、flow direction／accumulationだけを提供する。`V2-3-03`〜`V2-3-10`はriver／lake／canyon／waterfall／delta／tidal／fjord／mountainの各骨格を追加した。`V2-3-11`はstable MULTI_POINT island mass、dry-land gapとcentral dominance、submarine saddle、radial drainage-compatible provisional surface、optional HARD caldera／lava child-plan hookを追加した。volcanic geology／materialはV2-4へ残し、routing artifact、salinity、snowlineを推測生成しない。resource超過はfallbackせず失敗する。

### Stage 5: Regional feature shaping

feature kindごとの専用generatorがmacro fieldとhydrology planへ地形操作を出す。

- coast: beach、rocky cape、cliff、lagoon、breakwater
- mountain: ridge、peak、cirque、volcano、mesa
- valley: valley、canyon、gorge、fjord wall
- water: river corridor、lake basin、delta、wetland
- dry land: dune、badlands、salt flat
- island: island chain、atoll、volcanic archipelago

moduleは任意にglobal arrayを変更せず、宣言済みfield deltaとmerge operatorを返す。

### Stage 6: Bounded reconciliation

regional shapingで暫定標高が変わるため、水系をversion固定の3 passで再検査・補正する。

- reach bedの単調性
- lake spillとrim
- delta outletと海接続
- tidal channelのmarine connection
- fjord center channel
- waterfall lip/base

`V2-3-12`は`reconcile.hydrology` stageと`v2.hydrology.reconcile` moduleを追加した。全regional planからfull-resolution fieldを複製せずscalar targetを`HydrologyReconciliationPlanV2`へfreezeし、`kind → feature ID → constraint ID`順でinteger-onlyに3 pass走査する。補正は宣言された右辺と最大adjustmentだけに限定し、marine connectionを推測生成しない。hard targetを満たせなければ、残差、failure reason、state／result checksumを持つ`HydrologyReconciliationArtifactV2`をcanonical failureとして返す。

workは`variableCount + constraintCount × (3 + final scan)`、working setとartifact byteはcardinality係数付きでcompile前にadmissionする。cancelは各pass／constraintとartifact構築直前に観測し、無制限queue、tile-local再solve、収束待ちは行わない。full validator／previewは`V2-3-13`で実装し、Release収容は`V2-3-14`へ残す。

### Stage 7: Mesoscale shaping

大域構造を壊さない範囲で中規模形状を加える。

- river meander、bank、terrace、sand bar
- dune crest、talus、scree、sea stack
- erosion/deposition、tidal pool、plunge pool
- feature間のtransition band

局所kernelはstage固有haloを宣言する。流域全体が必要な処理をhaloで近似しない。

### Stage 8: Final regional environmental fields

確定した標高、水系、地質から地表／水域のregional fieldを作る。

- temperature、moisture、precipitation、wind exposure
- wetness、groundwater、salinity、shade
- soil depth、habitat、snow cover potential

詳細は [geology-climate-ecology.md](geology-climate-ecology.md) を参照する。

この時点では未評価のcave ceiling、地下湖、sky island undersideに対する局所wetness／shadeを「final」とみなさない。それらはStage 9のvolume確定後、Stage 10でregional fieldから派生させる。

### Stage 9: Sparse local volume

feature AABBと解析的SDF/CSGを使い、必要箇所だけ3D化する。

- cave / lush cave / sea cave / lava tube
- overhang / natural arch
- sky island underside
- waterfall water column / underground lake

base heightfieldを残し、tileと交差するvolumeだけをbounded chunkへ評価する。詳細は [volumetric-terrain.md](volumetric-terrain.md) を参照する。

`V2-5-01`〜`V2-5-14`でSDF／CSG／AABB index／tile cache／TerrainQuery volume／局所volume feature／waterfall volume／post-volume local environmentを追加した。volume validators／previewは後続Taskである。

### Stage 10: Ecology、material、micro detail

最終surface／ceiling／floor queryに従ってsemantic materialとsparse placementを解決する。

- cave、地下湖、overhang等のvolume-local moisture／shade／surface class
- strata exposure、wet rock、sand/gravel/clay、volcanic layer
- snowline、mangrove、coral、lush cave assemblage
- tree、shrub、root、coral、boulder等のbounded descriptor
- small crack、pebble、moss等のmicro detail

地形の後からランダムに素材を貼るのではなく、geology、slope、水、気候、生態条件を読む。

### Stage 11: Structure planning

既存built-in asset catalogと安全探索の考え方を再利用する。ただし直接 `heightMap` を読むのではなく、final `TerrainQuery` を使う。

```text
topWalkableSurface(x,z, context)
solidIntervals(x,z)
fluidIntervals(x,z)
ceilingAbove(x,y,z)
surfaceBelow(x,y,z)
```

breakwaterのように地形と水系を変える大規模人工物はsmall structureではなくregional feature moduleである。

### Stage 12: Validation、preview、export

baseline invariant、cross-feature rule、feature-specific metric、hard/soft constraintを検査する。hard violationを持つcandidateはReleaseへ進めない。

previewはregistryから1枚ずつ生成し、index manifestへ宣言する。exportは最終 `TerrainBlockResolver(x,y,z)` からtileごとにstreamする。

## 4. Stage I/O表

| Stage | 主なread | 主なwrite | 実行scope |
|---|---|---|---|
| Macro | constraint、geometry | land-water、provisional elevation、zone | global coarse |
| Geology prior | zone、feature | lithology、strata、hardness | global/tiled |
| Hydrology | elevation、geology、climate prior | graph、water body、surface | global |
| Regional | macro、graph、feature plan | elevation delta、feature mask | global plan＋tiled raster |
| Reconcile | shaped elevation、graph | final hydrology | global |
| Meso | elevation、hydrology、geology | local shape/deposition | tiled＋halo |
| Climate | final terrain、水、geology | temperature、moisture、wetness | global coarse＋tiled |
| Volume | feature、geology、水 | volume operator/cache | AABB＋3D halo |
| Ecology/material | final query、environment | material profile、placement | tiled |
| Structure | final query | structure plans | global search |
| Validation/preview | final fields/IR | issues、metrics、PNG | bounded scan |
| Export | resolver、tile、structure | `.schem` | streaming tile |

## 5. Built-in module catalog

初期実装は外部JAR、script、ServiceLoaderで任意コードを実行しない。moduleはcompile-time catalogへ登録し、BlueprintにID/versionを固定する。

```java
interface LandformModule {
    ModuleDescriptor descriptor();
    FeatureCompiler compiler();
    FeatureGenerator generator();
    List<FeatureValidationRule> validators();
    List<PreviewLayerProvider> previewLayers();
}
```

これは概念interfaceであり、実装時に責務を別interfaceへ分けてもよい。重要なのは、generatorがvalidator自身を信用してbaseline invariantを省略しないことである。

### Module規約

- `moduleId` と `moduleVersion` はstable。
- supported feature kindとparameter Schemaを持つ。
- read/write FieldKeyとmerge ruleを宣言する。
- global/tiled、halo XZ/Y、peak working bytesを宣言する。
- loop内でCancellationTokenを有界間隔で確認する。
- Paper、Bukkit、WorldEdit、AI Provider型へ依存しない。
- filesystemやnetworkへ直接アクセスしない。
- unordered collection、default locale、wall clockを生成判断に使わない。

## 6. Field ownershipと合成

複数moduleが同じfieldへ書く場合、compilerが明示的なcompositorを挟む。

| Operator | 用途例 |
|---|---|
| `MIN` | canyon/river carve |
| `MAX` | ridge/volcano/sky island base |
| `ADD_CLAMP` | bounded relief detail |
| `MASK_UNION` | feature presence |
| `PRIORITY_BLEND` | zone transition、material cover |
| `ORDERED_CSG` | volume add/carve |

同priority、同cell、非可換operationの衝突はstable ID順で黙って決めず、Blueprint compile時に拒否するか、契約に明記したtie-breakを使う。

## 7. Tile、halo、global処理

現行のglobal X/Z sampleと「margin付き計算後に中央だけ採用」は維持する。ただし固定16 blockを全stageへ流用しない。

```text
stageHalo = max(交差moduleのsupportRadiusXZ)
volumeHaloY = max(交差volume operatorのsupportRadiusY)
```

- river basin、flow accumulation、feature dependencyはglobal precomputeする。
- curve／graphはglobalにfreezeし、tileでは空間indexから交差segmentだけ読む。
- erosion、distance transform、smoothingはexpanded tileで計算する。
- volumeは3D haloを含むchunkで評価し、中央chunkだけcommitする。
- haloがbudgetを超えるfeatureは、global artifact化するかcompileを失敗させる。
- stage別halo内訳をBlueprintとdiagnosticへ残す。

## 8. Memory lifecycle

1000×1000の全fieldを同時保持しない。

- fieldはbyte/short/fixed-pointを基本とする。
- global graphはprimitive collectionまたはcompact arrayで保持する。
- working fieldはstage終了後にchecksumを確定して解放する。
- full-resolution derived fieldは必要ならchunk artifactへ退避する。
- volumeはdescriptorを正本とし、tile-local chunkだけvoxel化する。
- previewは現行同様1枚ずつ生成・解放する。
- exportは全block配列を作らずresolverからstreamする。

memory admissionは概ね `global retained + stage concurrency × per-task peak + tile/cache/decode/queue overhead + validation/PNG/schematic buffer` で見積もる。executorのparallelismと同時stage数をこのbudgetへ結び、見積上限内でも無制限にtaskを並行投入しない。

## 9. 決定性

再現性の入力は次とする。

```text
canonical request/Intent/constraint bytes
+ WorldBlueprint checksum
+ global seed / named seed derivation version
+ module ID/version set
+ stage graph / merge order / quantization / deterministic-kernel version
+ semantic material palette checksum
+ Minecraft compatibility version
```

checksumへ影響する分岐値はinteger／fixed-pointを正本にする。float/doubleが必要なkernelは、rounding、overflow、NaN禁止、`StrictMath`またはversion固定lookup、stable reduction順をcontract化する。parallel reductionの完了順やunordered collection順を使わず、overflowはchecked failureまたは明示したsaturating ruleに統一する。

必要なtest:

- thread数1と複数、tile順序の正逆、module登録順の正逆で同一。
- locale、timezone、default charsetに非依存。
- wholeと全tile単独生成が全field／最終blockで一致。
- module追加が無関係なfeature seedを変えない。
- cancelされたstageがcanonical artifactを公開しない。
- 対応するJava 21 runtime／CPU profile間でgolden field、metric、最終block checksumが一致する。

## 10. Checkpointとfailure

各stageは開始前に入力checksumを、完了後に出力checksumをatomic job stateへ保存する。global artifactとtile artifactの再開単位を分ける。

```text
QUEUED
→ VALIDATING_INPUTS
→ COMPILING_BLUEPRINT
→ GENERATING_MACRO
→ SOLVING_HYDROLOGY
→ GENERATING_REGIONAL
→ RESOLVING_REGIONAL_ENVIRONMENT
→ GENERATING_VOLUMES
→ RESOLVING_LOCAL_ENVIRONMENT_AND_MATERIAL
→ VALIDATING_RESULT
→ RENDERING_PREVIEWS
→ EXPORTING_TILES
→ PACKAGING
→ READY
```

これは将来のstage案であり、現行 `GenerationStage` enumをこの文書だけで変更するものではない。

CPU workは所有者とshutdown lifecycleが明確なbounded executor、artifact I/Oはadmission制限付きVirtual Threadを維持する。CPU queueと投入数にも上限を持たせ、飽和はjob層へfailed futureとして返す。Futureのcancel、実taskのinterrupt、CancellationToken、job stateを連動させる。Paper main threadで `join()`／`get()`しない。

## 11. Release format 2

現行format 1の固定allowlistを緩めず、別reader/writerでformat 2を導入する。

format 2には次を追加する。

- module manifest、stage graph、feature/hydrology/volume plans
- canonical constraint mapとfield sidecar
- preview indexとlayer metadata
- structure、volume、fluidを解決後のstable XYZ順canonical block-state streamを対象にしたtile content checksum
- physics stability／placement envelope metadata

V2-2でformat 2 containerを導入する時点で、`manifest.json.artifacts[]` をstrict artifact indexとし、core必須artifact、条件付きcapability catalog、artifactごとのSchema versionを固定する。例は `surface-2_5d`、`hydrology-plan`、`environment-fields`、`sparse-volume` である。manifestは `requiredCapabilities[]` を列挙し、readerは未知capability、未知artifact type/version、index外file、capabilityに必要な欠損fileを拒否する。後続Phaseが予約済みcapabilityを実装する場合もartifact Schema versionを検査し、予約外の意味変更はRelease format versionを上げる。

Blueprint、field、plan、`.schem` はそれぞれartifact checksumを持つが、最終tile内容の意味checksumをIR checksumで代用しない。`tileContentChecksum` はstructure、volume、fluidを含めresolverが返すcanonical block-state列だけから計算し、Sponge encodingや圧縮差から独立させる。

既存v1 artifactは書き換えないread-only対象として固定8 previewと旧allowlistで検証する。互換期間中のv1 generation/publish pathは意味とchecksumを凍結したまま維持できるが、v2 readerがv1の意味を推測しない。

## 12. 配置pipelineへの影響

流体、接続state、重力blockがtile境界を越えて未snapshot領域を変えないよう、v2は次を要求する。

```text
Release verify
→ mutation/effect envelopeを算出
→ target / bounds / physics containment / disk estimateをvalidate
→ 全regionとdiskを予約
→ Release / target / envelope / reservationにbindingしたconfirmを発行・検証
→ 全mutation/effect envelopeをsnapshotしてcheckpoint
→ solids/airをapply
→ fluids/neighbor-sensitive contentをapply
→ per-tile write acceptance / checkpoint
→ final settled full verify
→ success
```

per-tile write acceptanceはWorldEdit operationの完了、close、書込対象、即時block countを確認するcheckpointであり、physics後のexact一致を意味しない。最終settle後にeffect envelope全体をcanonical resolverとfull exact verifyする。

失敗時はsnapshot済み範囲を逆順復元する。effect envelope外へ副作用が及ぶ可能性をapply前に排除できないwater/lava、unsupported gravity block、自然変化でexact verifyを壊すstateはhard validationで拒否する。snapshot外変更を事後検出してrollbackできるとはみなさない。

full verifyをsampleへ弱めない。大規模world accessはPaper Scheduler上でbounded workへ分割し、WorldEdit/FAWEの完了とcloseを追跡する必要がある。

## 13. 代替案

| 案 | 評価 |
|---|---|
| v1の巨大switchへtype追加 | 初期差分は小さいが、field ownership、依存、3D、feature validatorを解けない |
| 全面dense 3D | 表現力は高いが最大約5.12億voxelでmemory条件に反する |
| tileごとにriver/caveを独立生成 | global接続とseamを保証できない |
| 完全物理erosion／fluid simulation | 高costでhard constraintと60秒budgetを制御しにくい。将来のoptional offline pass向け |
| raster入力だけを正本にする | 形状忠実度は高いが自然言語と意味validationを弱める |
| 外部runtime module | supply-chain、任意コード、version、決定性のriskが高い |

推奨する「typed field＋global plan＋built-in module＋局所volume」は、既存の安全境界を残しながら表現力を増やせる中間案である。

## 14. Stageを実装するTask境界

実装順の正本は [Task Index](task-index.md) である。Stage全体を1 Taskへ詰めず、次の順でcontract、kernel、feature、diagnostic、artifact、統合gateを閉じる。

| Pipeline領域 | Task chain | 境界 |
|---|---|---|
| Coastal Stage 1/2/5/7/12 | `V2-2-01`〜`V2-2-12` | 共通field/geometry、4 feature、transition、validator/preview、schematic、Releaseを分離 |
| Hydrology Stage 3/4/5/6/12 | `V2-3-01`〜`V2-3-15` | IR/prior、global solve、feature、reconciliation、diagnostic、capabilityを分離 |
| Environment Stage 3/5/8/10/12 | `V2-4-01`〜`V2-4-15` | geology、climate、regional shaping、material、ecology、diagnosticを分離 |
| Volume Stage 9/10/12 | `V2-5-01`〜`V2-5-18` | math/index/cache/queryを先に固定し、volume featureを1個ずつ追加 |
| Placement | `V2-6-01`〜`V2-6-19` | offline pipeline完了後、contractから実測までを分離 |

Task内で後続Stageを仮実装して先のcapabilityを有効化しない。各Phase末尾の統合Taskだけが、Stage間I/O、field ownership、memory lifecycle、determinism、strict Releaseをまとめて再検証し、親Phase gateを閉じる。
