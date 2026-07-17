# 局所3D地形

> Status: 設計提案。現行 `heightMap[x,z]` とcolumn materializerは変更していない。

## 1. 推奨方式

全域をdense voxel densityにせず、地表の大部分は2.5D heightfieldを維持し、cave、overhang、arch、sky island等のAABBだけへordered volume overlayを適用する。

```text
base heightfield / water / material fields
                 +
local analytic SDF / CSG feature plans
                 ↓
TerrainQuery / TerrainBlockResolver(x,y,z)
                 ↓
validation / preview / schematic stream / placement
```

1000×1000×512を1 byteだけで持っても約488 MiBであり、density、material、fluid、flagsをdense化すれば現行memory budgetを大幅に超える。局所AABBとtile-local cacheなら、必要な場所だけ3D表現できる。

## 2. 現行境界

現行 `BlockColumnMaterializer` は各X/Zへ1個のsurfaceと、その下の連続solidを仮定する。このため同じX/Zに複数solid intervalを持つ次の形は表現不能である。

- 地下空洞、地下湖、lava tube
- overhang、sea cave、滝裏
- natural arch
- 空中の独立solid、sky island

一方、Sponge schematic writerの「全block Listを作らず順次sampleする」方式は流用する。`heightAt(x,z)` 中心のsourceを一般の3D resolverへ置き換える。

## 3. Volume IR

```text
VolumeFeaturePlan
├── featureId / kind / AABB
├── operations[]
├── supportRadiusXZ / supportRadiusY
├── featureSeed / quantizationVersion
├── materialPolicy / fluidPolicy
├── interactionPolicy
└── validationTargetIds[]
```

operationは明示順序を持つ。

```text
ADD_SOLID   : air/fluidをsemantic solidへする
CARVE_SOLID : solid occupancyだけをvoidへする。fluidを暗黙追加しない
ADD_FLUID   : 既存voidへfluid body IDとsurface ruleを与える
REMOVE_FLUID: dry chamber等を保護する
PAINT       : geometryを変えずsemantic materialを上書きする
PLACE       : stalactite、root等のsparse descriptorを追加する
```

`ADD_SOLID → CARVE_SOLID` と逆順は結果が違うため、operation ordinal、feature priority、同priority tie-breakをBlueprint checksumに含める。水没cavityは `CARVE_SOLID → ADD_FLUID` と明記し、`CARVE_SOLID` がfluid状態まで所有しない。曖昧な重複はcompile errorにする。

## 4. Geometry primitive

SDFは「距離が負なら内部」のfixed-point関数として扱う。初期primitive:

- sphere / ellipsoid
- axis-aligned / oriented box
- capsule
- capped cone / tapered cone
- torus segment
- plane / half-space
- splineに沿うswept sphere／ellipse
- bounded deterministic noise warp
- smooth union / hard union / intersection / subtraction

arbitrary shader、expression、native library、AI生成codeは受理しない。すべてbuilt-in primitive IDと上限付きparameterへcompileする。

### 4.1 量子化

- control pointとradiusはrelease-local fixed-pointにする。
- SDF評価の丸め規則をversion化する。
- block判定の境界値とtie-breakを固定する。
- noiseはglobal X/Y/Z、feature ID、named seedでsampleする。
- JVM、thread、tile順が違っても同じblock stateを返すtestを持つ。

## 5. TerrainQuery

地表だけを読むAPIから、複数surfaceとfluidを扱うpure queryへ移行する。

```text
TerrainQuery
├── blockClassAt(x,y,z): SOLID | FLUID | AIR
├── semanticMaterialAt(x,y,z)
├── fluidBodyAt(x,y,z)
├── solidIntervals(x,z)
├── fluidIntervals(x,z)
├── topWalkableSurface(x,z, policy)
├── surfaceBelow(x,y,z)
├── ceilingAbove(x,y,z)
└── featureMembershipAt(x,y,z)
```

`TerrainBlockResolver` はこのsemantic queryとMinecraft compatibility paletteから最終block stateを返す。model/generatorはBukkit、Paper、WorldEdit型へ依存しない。

structure safety、snapshot bounds、validationは同じqueryを利用し、surfaceの定義を個別に再実装しない。

## 6. Feature別表現

### 6.1 Cave network

```text
tunnel graph
→ spline-swept capsulesをCARVE_SOLID
→ ellipsoid chamberをCARVE_SOLID
→ entrance corridorをsurfaceへ接続
→ floor/ceilingをPAINT
→ poolをADD_FLUID
→ decorationをPLACE
```

- graph nodeはchamber、junction、entrance、dead endを持つ。
- `SINGLE_CONNECTED_COMPONENT` 等のconnectivity contractを先にcompileする。
- declared entrance以外のsurface breakthroughを最小roof厚で防ぐ。
- lava tubeは円筒度、傾斜、branch数、basalt profileを変えた同じ基盤を使える。
- sea caveは海へのopening、潮位、背面roof、air pocketを明示する。

### 6.2 Lush cave

lush caveは別carve algorithmではなく、cave chamberに次を追加する複合featureである。

- moisture、drip、shade、groundwater condition
- clay/moss/wet-stone semantic material
- ceiling/root/vine/floor assemblage
- underground poolと乾いた歩行可能域

湿潤条件を満たさないchamberへ緑blockをランダム配置しない。

### 6.3 Overhang

host cliffの外側へsolid lobeを `ADD_SOLID` し、下面を `CARVE_SOLID` する。検査する値:

- hostへのsupport面積
- roofの最小厚
- projection長
- underside clearance
- seaward opening
- unsupported thin componentの有無

元heightfieldを無理に多価関数として扱わない。

### 6.4 Natural arch

ridge／cliffのsolid massを確保した後、swept tunnelを貫通carveする。arch span、crown厚、pier断面、通行clearance、connected solid componentを検証する。

### 6.5 Sky island

sky islandはabsolute Yを持つ独立 `ADD_SOLID` である。

- upper surfaceは局所heightfieldで作る。
- undersideはtapered cone／fractured SDFで作る。
- rim、土層、岩芯をsemantic materialで分ける。
- island間のgap、ground clearance、component数をhard validationする。
- waterfallがある場合はisland water body→fall node→落下流体のgraphを持つ。

### 6.6 Waterfallとunderground lake

waterfallの水柱はhydrologyのFallNodeから `ADD_FLUID` planへ変換する。plunge poolは地表carveと局所fluidを組み合わせる。地下湖はcavity、独立水面、shore、overflow reachを持ち、global sea levelへ自動接続しない。

## 7. Base fieldとのcomposition

base columnを最初のimplicit solidとして扱い、volume operationを適用する。

```text
if y <= baseSurfaceY(x,z): BASE_SOLID
else if y <= baseWaterSurfaceY(x,z): BASE_FLUID
else: AIR

then apply intersecting operations in canonical order
then resolve semantic material and sparse placement
```

volume featureがelevationやhydrologyを必要とする場合、Stage 9まで待つ。volumeがriver routeを変えるほど大きい場合は局所overlayではなくregional featureとして前段のplanへ昇格させる。

## 8. Spatial index、tile、cache

```text
global: VolumeFeaturePlan descriptor + deterministic spatial index
tile:   expanded 3D work region
cache:  16³または32³ chunkのbit/ID arrays
output: central tileだけcommit後、cacheを解放
```

- AABBをtile＋3D haloと交差判定し、無関係featureを評価しない。
- cache寸法、同時cache数、operator数、SDF sample数へhard limitを設ける。
- solid occupancyはbitset、material/fluid IDは必要最小幅を使う。
- whole-world volume arrayをReleaseの正本にしない。descriptorとchecksumが正本で、必要なら検証済みchunk sidecarをcacheとして持つ。
- tile単独生成と全体生成がblock単位で一致する。
- cancellationはY scan、chunk、operator loopで有界間隔に確認する。

stage haloは最大SDF support、noise warp、decoration radiusからcompileする。現行の水平16 block固定marginでは不足する。

## 9. Material、ecology、lighting相当条件

volume表面は向きで分類する。

```text
FLOOR / WALL / CEILING / UNDERSIDE / EXTERIOR_TOP / SUBMERGED
```

semantic material resolverは地質、depth、wetness、surface class、feature kindを読む。lush caveのceiling、coralのsubmerged face、sky island undersideを同じsurface素材として扱わない。

Minecraft lighting engineそのものをgenerator内でsimulationしないが、開口までの距離、天井厚、orientationから `LIGHT_EXPOSURE_CLASS` を決定的に近似し、生態条件に使う。

## 10. Exportとpalette

現行schematic streaming、tile分割、global座標、checksumを流用する。変更点:

- writer入力をcolumn materializerから3D `TerrainBlockResolver` へ切り替える。
- paletteはsemantic material profileからstable orderで構築する。
- palette IDが128以上でも正しい一般VarInt長を計算する。
- 1 tile分のblock streamを生成し、全block Listは作らない。
- volume plan、module version、palette mappingをRelease format 2へ保存する。

Sponge v3が表現できない機能を独自NBTへ埋めず、標準block stateへ解決してからexportする。

## 11. 配置安全性

3Dとfluidはtile境界外へ影響し得る。配置前に `mutation envelope` を算出する。

```text
tile AABB
+ volume/structure overhang
+ fluid update radius
+ gravity block fall range
+ WorldEdit side effect contract
= mutation/effect envelope
```

全envelopeのsnapshotを最初のapply前に完了する。tileごとの `snapshot → apply` 交互方式は、未snapshot隣接領域を流体が変更し得るためv2では使わない。apply中は外周fluid containmentを使い、完了後にsettled状態をbounded multi-tickでfull verifyする。失敗時は適用済みtileを逆順restoreする既存原則を維持する。

## 12. Validationとtest

### Baseline invariant

- bounds外operationなし
- AABBとoperation supportが一致
- illegal solid/fluid overlapなし
- minY/maxY外blockなし
- declared budget内
- tile seam一致

### Feature metric

- cave connectivity、roof厚、入口reachability、air volume
- lush chamberの湿潤／床／天井条件
- overhang support、roof厚、projection、clearance
- arch span、pier厚、connected component
- sky island component数、厚さ、ground clearance、gap
- waterfall column continuity、plunge pool接続

### Test層

- SDF primitive golden testと境界rounding test
- CSG operation順のtruth table
- randomized bounded property test
- whole/tile、thread、locale、JVM設定の決定性
- AABB／operator数／memory budget拒否
- cave graph corruptionによるnegative validator fixture
- palette 127/128/16383境界のSponge read-back
- Paper/WorldEdit/FAWEでfluid、gravity、rollbackのsmoke/performance test

## 13. 代替案

| 案 | 利点 | 判断 |
|---|---|---|
| heightmapへ例外flag追加 | 小変更 | 同一X/Zの複数intervalを表せず不採用 |
| 全域dense voxel | 単純なquery | 1000×1000×512のmemory／Releaseに不適 |
| octreeを唯一の正本 | sparse | 編集、deterministic raster、単純featureには複雑 |
| mesh→voxel変換 | 表現力 | topology、raster差、入力安全性が難しい |
| 2.5D＋局所analytic SDF/CSG | boundedで検証可能 | 推奨 |

将来octreeやmesh importを追加しても、最終的には同じbounded `VolumeFeaturePlan` と `TerrainBlockResolver` 契約へcompileする。

## 14. 実装Task順

3D coreと全featureを同じ変更へ入れない。[Task Index](task-index.md) の`V2-5-01`〜`V2-5-18`を正本とする。

```text
SDF primitive → ordered CSG → AABB index → 3D tile cache
→ TerrainQuery volume support
→ cave network → lush cave → underground lake → sea cave
→ overhang → natural arch → sky island
→ waterfall volume
→ post-volume environment/material
→ validator/preview → offline 3D schematic → Release capability
→ integration audit
```

SDF、CSG、index、cache、queryはそれぞれ独立Taskでgolden／budget／corruption testを閉じる。featureは原則1 Taskに1個とし、underground lakeとsea caveも分ける。`TerrainQuery`のvolume対応前にfeatureを実装せず、post-volume environmentをshape generatorへ埋め込まない。1000×1000×512 dense allocation禁止とXYZ seamは各該当Taskで検査し、`sparse-volume` capabilityと`SUPPORTED`昇格は`V2-5-18`まで保留する。Paper applyはV2-5 gate完了前に開始しない。
