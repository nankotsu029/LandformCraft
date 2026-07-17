# Hydrology v2

> Status: V2-3 Phase gate完了。`V2-4-01`〜`V2-4-03`のgeology／lithology／strata別planに加え、`V2-4-04`はcoarse climate runoff priorへの明示version transitionを`ClimatePlanV2`へ追加した。Hydrology plan、fixed prior、既存routing artifactのversion／値／checksumは変更していない。Paper経路と現行v1 river/lake実装も未変更である。次は`V2-4-05`である。

## 1. 目的と不変条件

川、湖、delta、fjord、滝、湿地を一貫して生成するには、水を単一 `waterLevel` 以下へ埋める方式から、全域の有向水系計画へ移行する必要がある。

Hydrology v2は次を保証する。

- riverはsourceからmouthまたはterminal basinへ到達する。
- downstreamの通常reachで河床／水面が逆勾配にならない。
- confluenceで上流流量と下流流量が整合する。
- lakeは独立した水面、rim、spillway、inlet/outletを持つ。
- deltaの分流は閉路のないdistributary DAGとして海へ到達する。
- fjordは海と接続したmarine channelで、側壁と中心深度を持つ。
- waterfallはgraph上のfall nodeであり、上流／下流reachと連続する。
- global planはtile順序とthread数に依存しない。
- hard mapやhard routeを局所noiseで上書きしない。

## 2. 現行方式からの変更

現行generatorはriver数に応じた平行meander bandと、seed由来の円形lakeをcarveし、全水域へrequestの1個のwater levelを使う。この実装、global X/Z sample、tile seam testはv1再現用に凍結する。

v2は次のIRを正本にする。

```text
HydrologyPlan
├── basins[]
├── nodes[]
├── reaches[]
├── lakes[]
├── deltas[]
├── marineChannels[]
├── fallNodes[]
├── fields
│   ├── waterBodyId / flowDirection / accumulation
│   ├── bedElevation / waterSurface / depth
│   └── groundwater / wetness / salinity
└── reconciliationReport
```

## 3. Graph model

### 3.1 Basin

```text
DrainageBasin
├── basinId
├── boundary / outletNodeId
├── sourceNodeIds[]
├── areaBlocks2 / runoffClass
└── terminalType: SEA | LAKE | CLOSED
```

closed basinは明示Intentがある場合だけ許可し、lake levelとoverflow policyを要求する。

### 3.2 Node

node kindは少なくとも次を持つ。

```text
SOURCE
CONFLUENCE
BIFURCATION
LAKE_INLET
LAKE_OUTLET
WATERFALL_LIP
WATERFALL_BASE
MOUTH
SPRING
TIDAL_BOUNDARY
```

各nodeはrelease-local fixed-point X/Z、bed Y、水面Y、incoming/outgoing reach IDを持つ。位置が同costのときはstable node IDでtie-breakする。

### 3.3 Reach

```text
RiverReach
├── reachId / fromNodeId / toNodeId
├── centerline / arcLengthTable
├── bedElevationProfile
├── waterSurfaceProfile
├── widthProfile / depthProfile
├── dischargeProfile / streamOrder
├── bank / floodplain / substrate profiles
└── rasterSupportRadius
```

profileはblockごとの巨大Listではなく、固定点sampleと補間規則で表す。tileは空間indexから交差reachだけをrasterizeする。

## 4. Solve pipeline

```text
provisional elevation
+ hard land-water / river / ridge constraints
+ geology hardness/permeability
+ climate runoff prior
→ depression analysis / basin identification
→ source and outlet selection
→ least-cost route / flow accumulation
→ reach graph simplification
→ bed and water-surface profile solve
→ lake / delta / fjord / waterfall specialization
→ regional terrain shaping
→ bounded reconciliation
→ final water / wetness / salinity fields
```

### 4.1 Provisional surface

Hydrology前の標高は完成地形ではない。hard coastline、ridge、excluded region、feature corridorを保持したcoarse surfaceである。地質hardnessはroute costへ影響するが、最終侵食結果は後段で確定する。

### 4.2 Basinとrouting

- priority-flood相当のdepression解析でsinkを識別する。
- 海境界、hard mouth、lake outletをoutlet候補にする。
- flow directionはD8に固定せず、将来vector／multi-flowへ移行できるfield descriptorにする。
- hard river splineはcenterline corridorとして通過を強制する。
- soft flow guideはrouting costを下げるが、上り勾配やexcluded regionを破らない。
- 同cost pathはglobal cell ID、方向ordinal、node IDの固定順で選ぶ。

### 4.3 Reconciliation

regional generatorがcanyon、bank、delta fan等を形成した後、次だけを固定回数で補正する。

- bedのdownstream monotonicity
- lake rimとspillway
- reach接続部の幅／水面
- delta mouthの海接続
- tidal channelのmarine connection
- fjord center channelのmarine connectivity
- waterfall lip/baseとplunge pool

無制限の「地形→水系→地形」反復は決定性と停止性を損なうため禁止する。最大反復回数、走査順、許容誤差をgenerator versionへ含める。

`V2-3-12`では最大反復を3、走査順を`REACH_BED → LAKE_SPILL → DELTA_MOUTH → TIDAL_CONNECTION → FJORD_CONNECTION → WATERFALL_LIP_BASE`、同kind内をfeature ID／constraint ID順に固定した。補正可能なtargetは右辺だけを宣言上限内で変更し、marine connectionは推測補完せずverify-onlyとする。3 pass後も残る違反、hard lock、補正上限、接続欠損はresidualとstable failure reasonを持つartifactへfreezeする。

## 5. River

river width/depthは単純な乱数でなく、catchment、runoff、勾配、stream orderからbounded profileへcompileする。自然らしさは物理simulationの完全再現ではなく、次の関係を保つことで得る。

- 下流ほど通常は流量が増える。
- 合流後の幅／深さは上流より不自然に小さくならない。
- meander波長と振幅は河幅、勾配、valley corridorに応じる。
- canyon内はmeanderを制限し、floodplainでは許容する。
- river bed materialは流速と地質からsemantic profileを選ぶ。
- bank、terrace、bar、oxbowはreachの派生planとしてstable IDを持つ。

braided riverは1本の太いnoise bandではなく、bifurcation/confluenceを持つ短命channel DAGとbar regionとして表す。

## 6. Lake

```text
LakePlan
├── lakeId / basinGeometry
├── waterSurfaceY
├── floorDepthProfile
├── rimMinimumY
├── inletNodeIds[] / outletNodeId
├── spillwayGeometry
└── shoreMaterial / wetlandTransition
```

lake作成手順:

1. basinとrimを識別する。
2. bounds、水量／意図、最大深度からwater surfaceを決める。
3. lowest legal spillをoutletへする。
4. floorを浅瀬、深部、inlet sedimentへ形成する。
5. inlet/outlet reachの水面を連続させる。
6. closed lakeなら蒸発／terminal policyをplanに明記する。

低地をglobal sea levelまで埋めることはlake生成とみなさない。

## 7. Delta

```text
DeltaPlan
├── apexNodeId / receivingSeaBoundary
├── fanGeometry / fanSlope
├── distributaryGraph
├── sedimentZones
├── bars / levees / wetlands
└── tidalInfluence
```

- main reachはapexで2本以上へ分かれる。
- 各distributaryは海またはtidal channelへ到達する。
- branchはgraph上で流量配分を持ち、総和を保存する。
- fanは低勾配だが完全な平面にしない。
- channel移動の履歴はabandoned channel、oxbow、wetlandのsoft planとして表現できる。
- sandbar、silt、clay、salinity gradientはgeology/ecologyへ渡す。

分流数、fan opening、低地率、海への到達率をfeature validatorが測定する。

`V2-3-07`の実装範囲では、DELTAは単一POLYGON fan、1本のHARD incoming `DRAINS_TO` river、1つのHARD `EMPTIES_INTO` sea boundaryを必須とする。apexから各mouthへ向かうstar-shaped DAGをstable branch IDとinteger discharge shareでfreezeし、fan／channel／branch index／surface／sandbar／shallow-sea depth／discharge shareをglobal X/Zでsampleする。全active mouthは宣言海境界上に置き、流量share総和を`1_000_000`に固定する。tidal bidirectionality、wetland/mangrove、sediment geology/materialは後続Taskの責務であり、暗黙推測しない。

## 8. Fjord

fjordは「細い海mask」ではなく、氷河谷とmarine channelの複合planである。

```text
MarineChannelPlan
├── mouth / head / centerline
├── surfaceWidthProfile
├── thalwegDepthProfile
├── U-shaped cross-section profile
├── sill locations
├── sidewall relief profile
└── tributary valley relations
```

- mouthはsea boundaryへhard接続する。
- center channelは連続したmarine waterを持つ。
- 横断面は中央深部、比較的平たい谷底、急な側壁を持つ。
- head、sill、tributary hanging valleyを専用parameterで扱う。
- cliffやsnow mountainはsidewallを共有するが、水深の所有者はhydrology moduleとする。

fjord validatorはlength/width比、中心水路接続、sidewall relief、横断面類似度、最低水深を測る。

## 9. Waterfall

waterfallはheightmapの段差だけでなく、graph nodeと局所volumeをまたぐ。

```text
FallNode
├── lipNode / baseNode
├── lipY / baseY / drop
├── flowWidth / discharge
├── plungePoolPlan
├── sprayWetnessRegion
└── waterColumnVolumeFeatureId
```

上流reachはlipへ、下流reachはplunge pool outletから継続する。落下水柱、滝裏空間は [局所3D地形](volumetric-terrain.md) の `ADD_FLUID`／`CARVE_SOLID` operationへcompileする。Paper配置時の流体更新範囲はmutation envelopeへ含める。

## 10. Wetland、groundwater、tide

wetlandは「海面以下の泥」ではない。次からwetnessとhydroperiodを計算する。

- groundwater depth
- river/lake/tidal channelへの距離
- local micro-relief
- permeabilityとsoil
- salinity／tidal range
- climate water balance

mangroveは海と接続したtidal channel、brackish salinity、低relief、root clearanceを同時に要求する。tidal flowを時間simulationする必要はないが、high/low water bandと接続性をstatic planへ保存する。

### 10.1 Dam / reservoir

damはsmall structure placementではなく、水面、流路、地形を変更するregional featureである。

```text
DamReservoirPlan
├── barrier geometry / crest / foundation
├── upstream reservoir basin / waterSurfaceY
├── inlet reaches
├── controlled outlet / spillway reach
├── downstream continuity
└── overtopping / containment validation targets
```

reservoirはlakeと同じ独立水面モデルを使うが、rimの一部を人工barrierが所有する。spillwayとoutletが下流reachへ接続しないplan、foundationが薄いplan、外周へ制御不能な流体更新を起こすplanはhard failureにする。初期v2の必須featureではなく、river/lake/waterfall契約が安定した後のcatalog拡張とする。

## 11. Tile、性能、決定性

- basin、graph、lake level、delta/fjord planはglobalに1回solveする。
- full-resolution flow working fieldはstage後にcompact graph／必要fieldへ落として解放する。
- reachはR-tree相当の決定的spatial indexでtile交差を検索する。
- rasterizationはglobal座標とarc lengthを使い、tile-local起点を使わない。
- stage haloはbank、meander、filter半径から算出し、現行固定16を流用しない。
- graph node/reach数、curve point数、source数、basin数へhard upper boundを設ける。
- global solveと各tile loopでcancel／interruptを有界間隔で確認する。
- fixed-point profileをcanonical artifactとし、platform float差をchecksum正本にしない。

## 12. Validationとpreview

共通metric:

- source→mouth reachability
- orphan node／cycle／逆向きreach
- downstream bed/water monotonicity
- confluence discharge conservation
- lake rim／spill consistency
- water bodyとsolidの不正重複
- tile seamでのcenterline、bed、surface、width一致

preview layer:

- basin ID
- flow direction / accumulation
- reach graphとnode kind
- bed elevation / water surface / depth
- lake rim / spillway
- delta distributary / sediment
- fjord thalweg / cross-section sample
- waterfall lip / base / influence envelope
- hydrology constraint residual

## 13. 代替案

| 案 | 利点 | 採用しない理由／用途 |
|---|---|---|
| 現行noise riverの増築 | 小さい変更 | 合流、湖、delta、fjordの整合を表せない |
| Minecraft標準world generatorへ委譲 | 成熟した地形 | 正規化Blueprintと厳密な局所制約、Release再現が難しい |
| 完全な流体／侵食simulation | 高い物理性 | 1000角、決定性、時間、validationのbudgetに不釣合い |
| 全域graph＋決定的rasterization | 意味と性能の均衡 | 推奨。形成則を近似するが検証可能 |

v2の目標は科学simulationそのものではなく、地形タイプ固有の不変条件を、再現可能かつ測定可能に満たすことである。

## 14. 実装Task順

Hydrology全体を1回で実装しない。詳細なAcceptanceと停止条件は [Task Index](task-index.md) の`V2-3-01`〜`V2-3-15`を正本とする。

```text
V2-3-01 IR / fixed priors
→ V2-3-02 basin / routing solve
→ V2-3-03 river / meander
→ V2-3-04 lake / rim / spillway
→ V2-3-05 canyon + river
→ V2-3-06 waterfall graph / 2.5D
→ V2-3-07 delta
→ V2-3-08 tidal channel
→ V2-3-09 fjord
→ V2-3-10 mountain skeleton
→ V2-3-11 volcanic archipelago skeleton
→ V2-3-12 bounded reconciliation
→ V2-3-13 validator / preview
→ V2-3-14 Release capability
→ V2-3-15 integration audit
```

`V2-3-01`はgraph契約、`V2-3-02`はglobal solverだけを扱う。regional generatorはfeatureごとに分け、`V2-3-12`より前に無制限reconciliationを導入しない。falling water columnは`V2-5-13`、salinity／hydroperiodは`V2-4-05`であり、V2-3のwaterfallやtidal Taskへ混ぜない。`hydrology-plan` capabilityとfeatureの`SUPPORTED`昇格は統合監査完了まで保留する。

### 14.1 V2-3-01実装結果

`HydrologyPlanV2`は`planVersion=1`／`hydrology-graph-v1`をstrictに読み、basin、node、reach、water body、fallをID順へcanonicalizeする。reach endpoint、nodeのincoming／outgoing index、basin source／outlet、water body binding、fall lip／base、source→outlet reachability、cycleをdomain構築時に検査する。通常のBlueprint compileではsolver前のempty graphを保存し、minimal river／waterfall graphはSchema／codec／negative fixtureで契約を固定する。

V2-4前提を使わないため、prior version 1はuniform hardness／permeabilityを各`500000` millionths、constant runoffを`1000000` millionthsとし、`UNIFORM_GEOLOGY_PRIOR`／`CONSTANT_RUNOFF_PRIOR`とcanonical prior checksumを不可分にする。未知prior、値またはchecksum改変はfallbackせず拒否する。

`v2.hydrology.ir`は`hydrology.water-body-id`、`flow-direction`、`flow-accumulation`、`bed-elevation`、`water-surface`、`water-depth`の6 descriptorだけを`SINGLE_OWNER`で所有する。V2-3-01時点ではpayloadやrouting結果を生成しなかった。budget version 1はglobal cell数からbasin最大256、node最大4096、reach最大8192等を有界算出し、整数`Math.*Exact`でCPU／resident見積をBlueprint admissionへ合算する。V2-3-02ではrouting working setをこの同じadmissionへ含め、1000×1000でもCPU上限6500万work unit、resident見積40 MiB未満に収める。dense voxelや全block Listは作らない。

### 14.2 V2-3-02実装結果

`DeterministicHydrologyRoutingSolverV2`はrelease-local global X/Zのprovisional surfaceをinteger millionthsへsnapshotし、呼出側が明示した`BOUNDARY`／`HARD` outletだけをseedにする。outlet候補はglobal Z、X、ID順、priority heapは`(filled elevation, global cell ID)`順、近傍はversion固定D8順で処理する。cellごとにdownstream directionを一度だけ確定し、priority-popの逆順でflow accumulationをstable reductionする。outletや海側をsurfaceから推測せず、blocked hard outlet、outletのないroutable component、範囲／overflow、事前budget超過はcanonical failureにする。

結果はstable `basin-000001`形式のsummary、`HYDROLOGY_FLOW_DIRECTION` U8（terminal=0、D8=1..8、no-data=255）、`HYDROLOGY_FLOW_ACCUMULATION` I32（no-data=0）の2 field、source HydrologyPlan／surface／fixed-prior checksum、graph／field／routing checksumへfreezeする。fieldは既存`LFC_GRID_V1`のsemantic code 10／11を使い、indexと2 sidecarだけのbundleとしてstagingへstreamする。readerはindex 512 KiB、exact file／directory set、symlink、artifact／semantic checksum、direction code、global boundary、downstream accumulationのstrict増加、全terminalとbasin areaの一致をrow単位のbounded windowで検査する。最後のcancel観測後にbundle directoryをatomic moveし、commit後のlate cancelで削除しない。

bowl、flat、multiple outlet、boundary fixtureでは全routable cellが宣言outletへ到達する。候補順、tile 32／64／128、tile正逆順、worker 1／3／4、locale、timezoneでsurface／graph／両field／routing checksumが一致する。1000×1000ではpeak working set 40 MiB未満、retained result 6 MiB未満である。river centerline／channel shaping、lake／delta／fjord、bounded reconciliation、validator／preview、Release `hydrology-plan` capabilityは後続Taskのままであり、この基盤は`EXPERIMENTAL`である。

### 14.3 V2-3-03実装結果

`MEANDERING_RIVER` Intentに`bankfullWidthBlocks`、`dischargeClass`、`minimumBedSlope01`、`variant`（`RIVER`｜`MEANDERING_RIVER`）を追加し、同一source→mouth reach contractの上で直線／蛇行を切り替える。`MeanderingRiverPlanCompilerV2`はSPLINEをblock millionthsへflattenし、床勾配を下流単調にfreeze、蛇行はcorridor内のperpendicular offsetだけを適用してgraph意味を変えない。`MeanderingRiverGeneratorV2`はchannel／bank／floodplain／meander-corridor／local-width／dischargeとbed／water／depth／water-body samplesをglobal X/Zでrasterし、HARD route conflict、逆勾配、孤立reach、幅不足、halo／window budgetを拒否する。`RiverConfluenceValidatorV2`はHARD `UPSTREAM_OF`／`DRAINS_TO`の合流流量整合を検査する。featureは`EXPERIMENTAL`で、full validator bundle、lake／canyon／waterfall／delta、Release capabilityは含まない。

### 14.4 V2-3-04実装結果

独立`LAKE` Intentに`targetDepthBlocks`、`shoreWidthBlocks`、`terminalPolicy`（`OPEN_SPILL`｜`CLOSED`）、`spillSelection`（`DECLARED_EDGE`｜`LOWEST_RIM_SADDLE`）、spillway寸法、`floorProfile`（`EDGE_TO_CENTER_LINEAR`）を追加した。`LakePlanCompilerV2`はPOLYGON basinをblock millionthsへfreezeし、単一`waterSurfaceY`、rim、宣言edgeまたは一意lowest-rim spill、outward spill corridor、HARD river inlet relationを確定する。flat rimのambiguous spill、逆流corridor、invalid inlet、vertical／fill budget超過は拒否する。`LakeGeneratorV2`はbasin／rim／spillway／depth／floor／surfaceとhydrology bed／water samplesをglobal X/Zでrasterし、HARD land conflictとwindow budgetを拒否する。dam／reservoir構造は導入していない。featureは`EXPERIMENTAL`で、canyon／waterfall／delta、full validator、Release capabilityは後続である。

### 14.5 V2-3-05実装結果

`CANYON` Intentに`floorWidthBlocks`、`rimWidthBlocks`、`depthBlocks`、`crossSection`（`V`｜`U`｜`TERRACED_V`｜`TERRACED_U`）、terrace寸法を追加した。`CanyonPlanCompilerV2`はHARD `WITHIN`でちょうど1本の`MEANDERING_RIVER`へ結び、river-owned centerline／bedを共有し、missing WITHIN、crossing、disconnected spline、thin wall、vertical budget、床幅不足を拒否する。`CanyonGeneratorV2`はfloor／rim／terrace／canyon maskとwall／surface heightをglobal X/Zでrasterし、river containmentとbed monotonicity metrics、owner conflict／window budgetを検査する。地質fieldは使わず固定geology priorで形状を成立させ、strata material／volume ledgeは導入していない。featureは`EXPERIMENTAL`である。

### 14.6 V2-3-06実装結果

`WATERFALL` Intentに`dropBlocks`、`lipWidthBlocks`、`plungePoolRadiusBlocks`、`behindFallClearanceBlocks`を追加した。`WaterfallPlanCompilerV2`はHARD `ON_PATH_OF`でちょうど1本の`MEANDERING_RIVER`へ結び、POINTをriver centerlineへ投影し、upstream／downstream reach splitとlip／base床不連続をfreezeする。off-path、ambiguous binding、`behindFallClearanceBlocks>0`（zero roof clearance／V2-5 volume deferred）、vertical bounds超過を拒否する。`WaterfallGeneratorV2`はlip／base／plunge-pool maskとelevation／floorをglobal X/Zでrasterし、drop／discontinuity／reach monotonicity metrics、owner conflict／halo-window budgetを検査する。falling water column／behind-fall cavity／volume fluidは導入していない。featureは`EXPERIMENTAL`である。

### 14.7 V2-3-07実装結果

`DELTA` Intentに`distributaryCount`、`fanOpeningDegrees`、`fanReliefBlocks`、`sandbarCount`、`shallowSeaDepthBlocks`、`APEX_TO_SEA_LINEAR` profileを追加した。`DeltaPlanCompilerV2`はHARD `DRAINS_TO`でちょうど1本のcompiled river mouthをapexへ、HARD `EMPTIES_INTO`と一致する明示HARD SEA edgeをreceiving boundaryへ結ぶ。fan ringをblock millionthsへwinding／始点非依存にcanonicalizeし、境界span、stable branch／mouth ID、整数流量share、低起伏surface、sandbar、support／CPU budgetを`DeltaPlanV2`へfreezeする。

`DeltaGeneratorV2`はfan mask、channel mask、branch index、fan surface、sandbar mask、shallow-sea depth、discharge shareの7 fieldをglobal X/Zでsampleし、bounded windowとrow-major checksumを提供する。`DeltaGraphValidatorV2`とmetrics hookはbranch count、fan relief、全mouthのmarine reachability、flow conservationを検査し、dead branch、loop、landlocked mouth、flow破損、HARD outlet conflictを拒否する。whole／tile、tile順、thread数、ring/candidate順、locale、timezoneでfield checksumが一致し、memory／CPU admissionとrow単位cancelを確認した。tidal channel network、mangrove／wetland、sediment geology/material、full validation/preview bundle、Release capabilityは含まず、featureは`EXPERIMENTAL`である。

### 14.8 V2-3-08実装結果

`TIDAL_CHANNEL_NETWORK` Intentに`widthBlocks`、`tidalRangeBlocks`、`edgeKind=BIDIRECTIONAL`を追加した。`TidalChannelPlanCompilerV2`はMULTI_SPLINE端点をjoin tolerance内でclusterし、HARD `EMPTIES_INTO`と一致する明示HARD SEA edgeをreceiving boundaryへ結ぶ。各pathをundirected bidirectional edgeとしてfreezeし、optional HARD `WITHIN` `MANGROVE_WETLAND`をwetland child-plan hookへ保存する（mangrove shaping自体はV2-4）。closed channel、非宣言境界上の端点（ambiguous direction）、孤立component、unknown／非bidirectional edge、hard no-data、graph／channel budgetを拒否する。

`TidalChannelGeneratorV2`はchannel mask、branch index、depth corridor、marine connectionの4 fieldをglobal X/Zでsampleし、bounded windowとrow-major checksumを提供する。`TidalGraphValidatorV2`とmetrics hookはmarine connectivity、branch count、depth corridorを検査する。whole／tile、tile順、thread数、path順、locale、timezoneでfield checksumが一致し、memory／CPU admissionとrow単位cancelを確認した。salinity／hydroperiod、mangrove shaping、coral pass、full validation/preview bundle、Release capabilityは含まず、featureは`EXPERIMENTAL`である。

### 14.9 V2-3-09実装結果

`FJORD` Intentに`surfaceWidthBlocks`、`channelDepthBlocks`、`profile=GLACIAL_U`、`headBasinRadiusBlocks`を追加した。`FjordPlanCompilerV2`はHARD `EMPTIES_INTO`＋明示HARD SEA、optional HARD `FLANKS` wall hookをfreezeし、landlocked／too-wide／broken wallを拒否する。`FjordGeneratorV2`はdeep channel／U profile／sidewallの5 fieldを所有する。ice／snow／volume sea caveは含まず、featureは`EXPERIMENTAL`である。

### 14.10 V2-3-10実装結果

`ALPINE_MOUNTAIN_RANGE`／`GLACIAL_MOUNTAIN_RANGE`共有の`MountainParameters`と`MountainPlanV2`を追加した。ridge splineまたはpolygon major-axis、peak／saddle／spur、provisional surfaceを含む6 fieldを`MountainGeneratorV2`が所有する。snowline／material／cirque ecologyは含まず、featureは`EXPERIMENTAL`である。

### 14.11 V2-3-11実装結果

`VOLCANIC_ARCHIPELAGO` Intentに`islands[]`（pointId／radius／summit）、`submarineSaddleDepthBlocks`を追加し、optional `VOLCANIC_CALDERA`（WITHIN）と`LAVA_FLOW_FIELD`（ORIGINATES_AT）をplan hookへfreezeする。`VolcanicPlanCompilerV2`はdry-land gap≥12、central dominance≥1.5×、in-bounds island disks、orphan／unknown childを検査する。`VolcanicGeneratorV2`はisland mask／index、summit relief、submarine saddle、radial drainage、provisional surfaceの6 fieldをglobal X/Zでsampleし、component count／dry gap／dominance／marine separation metrics、window／raster budgetとcancelを検査する。catalog bindingは`v2.landform.volcanic` version `0.1.0-v2-3-11`である。basalt／tuff／ash material、lava tube、full volcanic ecology、full validation/preview、Release／Paperは含まず、featureは`EXPERIMENTAL`である。

### 14.12 V2-3-12実装結果

`HydrologyReconciliationPlanV2` version 1は、regional planからreach bed、open-lake surface／spill、delta branch mouth、tidal／fjord marine connection、waterfall lip／baseのscalar variableとhard targetをcompileし、source HydrologyPlan checksum、`hydrology-reconcile-fixed-v1`、`kind-feature-constraint-v1`、固定3 pass、work／working-set／artifact budgetをBlueprintへfreezeする。`v2.hydrology.reconcile`は全regional hydrology stageの後に置くfield非所有の`EXPERIMENTAL` built-in moduleである。

`BoundedHydrologyReconcilerV2`はinteger millionthsだけを使い、plan constructorが固定したkind／feature／constraint順で必ず3 pass走査する。reach、lake spill、waterfall baseは右辺だけを最大16 blockまで補正し、delta／tidal／fjord connectionはverify-onlyである。結果はfinal scalar state、constraintごとのactual delta／residual／correction count、`NONE`／`HARD_CONFLICT`／`ADJUSTMENT_LIMIT`／`UNRECOVERABLE_CONNECTION`／`NON_CONVERGENCE`、resource usage、state／result／canonical checksumを持つstrict artifactとなる。recoverable／unrecoverable、hard lock、補正上限、3 pass non-convergence、cancel、Schema／checksum改変を検査し、feature／candidate入力順、thread数、locale、timezoneでresidualとchecksumが一致する。full field validator／preview、`hydrology-plan` Release capability、Paper配置、V2-4 fieldは含めていない。

### 14.13 V2-3-13実装結果

`HydrologyValidatorV2`（`hydrology-validator-v1`）は`HydrologyFieldSamplerV2`とoptional `HydrologyReconciliationArtifactV2`だけを読み、generator-local `evaluate()`を呼ばない。river channel continuity／source-mouth reachability、bed reverse gradient、flow-direction cycle、open-lake spillway、delta mouth connection、tidal marine cells、fjord mouth、waterfall drop／masks、mountain ridge／peak、volcanic island components、reconcile unsatisfied residualをHARD metric／issueへ変換する。compilerは対応する`ValidationTargetV2`と`v2.hydrology.validation-preview`（stage `validate.hydrology`）をBlueprintへfreezeする。

`HydrologyDiagnosticPreviewRendererV2`は次の12 PNGを固定順で1枚ずつrenderし、`hydrology-preview-index-v2.schema.json`／checksum／directory entry／PNG dimensionのstrict read-back後にatomic publishする: basin-id、flow-direction、flow-accumulation、reach-graph、bed-elevation、water-surface、water-body、lake-rim-spill、delta-distributary、fjord-thalweg、waterfall-envelope、constraint-residual。validation artifactは`hydrology-validation-artifact-v2.schema.json`でboundする。isolated reach／reverse gradient／cycle／leaking lake／dead delta／broken fjord／fall mismatch等のcorruption、tile／locale決定性、cancel cleanupを確認した。

### 14.14 V2-3-14実装結果

`ReleaseHydrologyPublisherV2`／`HydrologyReleaseCapabilityVerifierV2`は`requiredCapabilities=["hydrology-plan","surface-2_5d"]`だけを許し、surface exact setに加えてplan／routing／field／reconciliation／validation／12 previewをstrict indexする。plan semantic checksum、routing graph／field binding、directory／ZIP parity、surface-only回帰、`hydrology-plan`単独拒否、graph checksum／future capability／version tampering、cancel cleanupを確認した。ADR 0014を追加した。featureの`SUPPORTED`昇格は`V2-3-15`へ残す。

### 14.15 V2-3-15実装結果

`HydrologyPhaseGateV2Test`は9 scenarioのstrict example parseとcanonical Blueprint compileを、scenario正逆順、1／4 thread、Turkish locale、Chatham timezoneで比較する。既存feature test群のwhole／tile／seam／candidate、positive／corruption、1000角budget、cancel／non-convergenceと、Release directory／ZIP tampering、v1／Release 1／V2-2回帰を同じworktreeで再実行した。結果は [Phase gate audit](audits/v2-3-phase-gate.md) に固定した。

full completionしたofflineのRIVER／MEANDERING_RIVER、LAKE、CANYON、DELTA、TIDAL_CHANNEL_NETWORK、FJORDと、IR／reconciliation／validation-preview共通moduleを`SUPPORTED`へ昇格した。falling water column／behind-fall cavityを欠くWATERFALL、snow／environmentを欠くALPINE／GLACIAL mountain、material／ecologyを欠くVOLCANIC_ARCHIPELAGOは`EXPERIMENTAL`を維持する。公開CLI／Paper、Release 1、world mutationへは接続していない。

### 14.16 V2-4-04 climate prior transition

`ClimatePlanV2.HydrologyRunoffHandoff`はsource `HydrologyPlan` canonical checksum、`CONSTANT_RUNOFF_PRIOR` checksum／`1000000` millionths、source generator `hydrology-priority-flood-v1`を厳密に参照し、coarse precipitation／runoff prior checksumとtarget generator `hydrology-priority-flood-climate-prior-v1`を明示する。これはV2-3 artifactのin-place upgradeではなく、別planにfreezeした`EXPLICIT_VERSION_TRANSITION`である。source plan／prior／generator mismatchは拒否し、同じpriorとsurface／outletから作るgraphはfinal climate入力に依存しない。

`WaterConditionPlanV2`（`V2-4-05`）はHydrology plan checksumとclimate final moistureへ結合し、bounded water distance、groundwater proxy、tidal influence、salinity、hydroperiod、wetness、wetness residualをinteger-onlyで導出する。marine connectivityが無い場合はsalinity／tidal influenceを0にし、implicit ocean fallbackとunbounded diffusionを拒否する。mangrove shapingとecology placementは後続Taskへ残す。
