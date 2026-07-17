# 地形Feature Taxonomy

> Status: V2-0〜V2-3のfeature contractとoffline Phase gateを完了し、`V2-4-01`〜`V2-4-04`でfeature非依存のgeology／lithology／strata／climate coreを`EXPERIMENTAL`として追加した。feature lifecycle、現行v1 enum、Paper配置は変更していない。次は`V2-4-05`である。

## 1. 目的

地形名を1個の巨大enumへ追加し続けると、意味、geometry、parameter、generator、validatorの対応が崩れる。v2ではversion付きcatalogを正本にし、各feature kindが実行可能な契約を持つ。

```text
FeatureKindDescriptor
├── kindId / catalogVersion
├── category / dimensionality
├── allowedGeometryTypes[]
├── parameterSchemaId
├── allowedRelationKinds[]
├── compilerModuleId / generatorModuleId
├── requiredFields[] / providedFields[]
├── validatorRuleIds[] / previewLayerIds[]
├── defaultSupportRadius / resourceClass
└── lifecycleStatus
```

`lifecycleStatus` は `EXPERIMENTAL`、`SUPPORTED`、`DEPRECATED` を持つ。未知kindを近いkindへ黙って変換しない。

## 2. 4つの概念を混ぜない

| 概念 | 例 | 責務 |
|---|---|---|
| Landform feature | beach、fjord、volcano | 形、水、地質を変える領域／経路 |
| Environmental profile | alpine、mangrove、coral | 温度、湿度、habitat、material条件 |
| Volume operation | cave carve、island solid add | 局所3Dのsolid／fluid変更 |
| Structure | lighthouse、small bridge | catalog assetの安全な配置 |

`MANGROVE_WETLAND` や `CORAL_REEF` のように複数要素を一体で成立させるkindは、regional landform featureがenvironmental profileを要求する複合featureとする。breakwaterは水深と海岸を変えるためstructureではなくfeatureである。

## 3. Category catalog

### 3.1 Coast / Marine

| Kind | 次元 | 主なgeometry | 専用生成要素 |
|---|---:|---|---|
| `SANDY_BEACH` | 2.5D | spline/polygon | 幅profile、shore slope、砂帯、遠浅 |
| `GRAVEL_BEACH` | 2.5D | spline/polygon | gravel ridge、粒径帯、急なshore |
| `ROCKY_COAST` | 2.5D+局所3D | spline/polygon | 岩棚、水路、露岩 |
| `ROCKY_CAPE` | 2.5D+局所3D | polygon | cape relief、sea stack、channel |
| `SEA_CLIFF` | 2.5D+局所3D | spline | cliff face、talus、wave-cut notch |
| `BREAKWATER_HARBOR` | 2.5D | multi-spline | arm、crest、opening、接続 |
| `HARBOR_BASIN` | 2.5D | polygon | navigable depth、entrance、inner/outer side |
| `TIDAL_POOL_COAST` | 2.5D | spline/polygon | 岩棚、basin、spill elevation |
| `LAGOON` | 2.5D | polygon | enclosing rim、depth、inlet |
| `TIDAL_FLAT` | 2.5D | polygon | 微勾配、潮路、泥／砂帯 |
| `BLACK_SAND_BEACH` | 2.5D | spline | volcanic sediment profile |
| `SEA_STACK_FIELD` | 2.5D+3D | polygon/multipoint | stack分布、基部侵食 |

`SANDY_BEACH`、`BREAKWATER_HARBOR`、`HARBOR_BASIN`、`ROCKY_CAPE`だけが`v2.coast.foundation`へbindingされる。block座標geometry、coast-side、signed-distance／normal／nearshore field ownershipを持ち、COASTLINE planは共通integer rasterでsampleできる。各feature固有generatorの出力は、明示transition relationとpriorityをfreezeした`v2.coast.transition`が合成する。`v2.coast.validation-preview`はfinal field samplerだけを読み、Blueprint target、corruption validator、4 overlay＋desired/actual/residual＋constraint errorのstrict preview indexを提供する。HARD cellはexactに保護し、breakwater／basinは明示0-band seam、他の隣接／overlapは1..32 blockのpriority blendとする。未契約overlapや曖昧な同票は拒否し、module登録順で解決しない。`capeMode=LOCAL_VOLUME_REQUIRED`は3Dへfallbackせず`v2.cape-volume-required`で診断し、overhang／sea cave／full 3D stackはV2-5へ残す。4 kindはRelease capabilityと`V2-2-12`統合監査までを完了したため、offline生成／検証／export経路で`SUPPORTED`である。

### 3.2 Mountain / Tectonic / Volcanic

| Kind | 次元 | 主なgeometry | 専用生成要素 |
|---|---:|---|---|
| `HILL_RANGE` | 2.5D | spline/polygon | smooth ridge、saddle |
| `MOUNTAIN_RANGE` | 2.5D | spline | ridge、peak、spur |
| `ALPINE_MOUNTAIN_RANGE` | 2.5D | spline | sharp ridge、scree、snow band |
| `GLACIAL_MOUNTAIN_RANGE` | 2.5D | spline/polygon | U谷、cirque、arête |
| `VOLCANIC_CONE` | 2.5D | point/polygon | cone、crater、radial drainage |
| `VOLCANIC_CALDERA` | 2.5D | point/polygon | rim、floor、breach |
| `LAVA_FLOW_FIELD` | 2.5D+3D | spline/polygon | levee、lobe、lava tube hook |
| `PLATEAU` / `MESA` / `BUTTE` | 2.5D | polygon/point | cap、escarpment、talus |
| `KARST_PEAK_FIELD` | 2.5D+3D | polygon | tower distribution、sinkhole |
| `GLACIAL_CIRQUE_FIELD` | 2.5D | multipoint | bowl、headwall、threshold |

### 3.3 Valley / Canyon

| Kind | 次元 | 主なgeometry | 専用生成要素 |
|---|---:|---|---|
| `VALLEY` | 2.5D | spline | V/U断面、floor、shoulder |
| `CANYON` | 2.5D | spline | rim/floor幅、terrace、strata exposure |
| `GORGE` | 2.5D+3D | spline | narrow wall、岩棚、滝 |
| `RAVINE` | 2.5D | spline | small incised channel |
| `RIFT_VALLEY` | 2.5D | spline/polygon | opposing scarps、flat floor |
| `FJORD` | 2.5D | spline | marine channel、U断面、head basin |

`FJORD` は海岸と谷の両方の性質を持つが、主な形成／validationがglacial valley断面とmarine channelなのでValley catalogへ置く。UIは複数tagで検索できる。

### 3.4 Hydrology

| Kind | 主なIR | 専用生成要素 |
|---|---|---|
| `RIVER` / `MEANDERING_RIVER` | reach graph | source、合流、bank、floodplain |
| `BRAIDED_RIVER` | reach DAG | split/merge、bar、active channel |
| `BEDROCK_RIVER` | reach graph | bedrock channel、step/pool |
| `LAKE` | basin plan | independent level、rim、inlet/outlet |
| `OXBOW_LAKE` | reach relation | cutoff、stagnant water、wetland |
| `DELTA` | distributary DAG | fan、分流、sandbar、sediment |
| `WATERFALL` | fall node | lip、drop、water column、plunge pool |
| `SPRING` | source node | groundwater source、outflow |
| `DAM_RESERVOIR` | barrier＋lake plan | dam crest、reservoir level、spillway、outlet |
| `TIDAL_CHANNEL_NETWORK` | bidirectional/tidal graph | branching channel、tidal range |

`DELTA`の現在のcatalog bindingは`v2.hydrology.delta` version `0.1.0-v2-3-07`である。単一POLYGON fan、HARD river `DRAINS_TO`、HARD SEA boundary `EMPTIES_INTO`を要求し、flow-conserving distributary、低起伏fan、sandbar／shallow receiving seaのdescriptor fieldだけを所有する。独立validator／previewとstrict Releaseを含むV2-3 gate完了によりoffline lifecycleは`SUPPORTED`である。mangrove、sediment geology/material、Paperは未対応である。

`TIDAL_CHANNEL_NETWORK`の現在のcatalog bindingは`v2.hydrology.tidal` version `0.1.0-v2-3-08`である。MULTI_SPLINE、`BIDIRECTIONAL` edge kind、HARD SEA boundary `EMPTIES_INTO`を要求し、optional HARD `WITHIN` mangrove child-plan hookとchannel／branch／depth／marine-connection descriptor fieldだけを所有する。standalone networkのoffline lifecycleはV2-3 gate完了により`SUPPORTED`である。mangrove shaping、salinity／hydroperiod、coral pass、Paperは未対応で、複合mangrove scenarioはV2-4まで未完成である。

`VOLCANIC_ARCHIPELAGO`の現在のcatalog bindingは`v2.landform.volcanic` version `0.1.0-v2-3-11`である。MULTI_POINT island mass、dry-land gap、central dominance、submarine saddle、radial drainage、optional HARD `WITHIN` caldera／`ORIGINATES_AT` lava child-plan hookと6個のdescriptor fieldだけを所有する。lifecycleは`EXPERIMENTAL`で、basalt／tuff／ash material、lava tube、full volcanic ecology、preview、Release／Paperは未対応である。

### 3.5 Wetland / Plain / Dry land

| Category | Kinds |
|---|---|
| Plain | `PLAIN`, `FLOODPLAIN`, `SAVANNA_PLAIN` |
| Wetland | `MARSH`, `BOG`, `FEN`, `MANGROVE_WETLAND`, `SALT_MARSH` |
| Dune | `COASTAL_DUNE_FIELD`, `INLAND_DUNE_FIELD` |
| Arid | `DESERT_FLAT`, `BADLANDS`, `DRY_CANYON`, `SALT_FLAT` |

plainは「何もないdefault」ではなく、微地形、地下水位、河川との関係を持つfeatureである。duneは風向き、crest line、stoss/lee slopeをparameterに持つ。

### 3.6 Island / Reef

| Kind | 専用生成要素 |
|---|---|
| `SINGLE_ISLAND` | core、coast band、drainage |
| `BARRIER_ISLAND` | shore-parallel ridge、lagoon |
| `ARCHIPELAGO` | size distribution、spacing、submarine saddle |
| `VOLCANIC_ARCHIPELAGO` | volcanic arc、cone/caldera relation |
| `ATOLL` | reef ring、lagoon、islets、pass |
| `CORAL_REEF` | reef crest、outer slope、coral habitat |
| `REEF_PASS` | reefを横切るmarine connection、幅、navigable depth |

### 3.7 Volumetric / Subterranean / Aerial

| Kind | 主operation |
|---|---|
| `CAVE_NETWORK` | tunnel/chamber carve |
| `CAVE_ENTRANCE` | surface-to-tunnel connection carve |
| `LUSH_CAVE` | chamber carve＋ecology paint/place |
| `SEA_CAVE` | carve＋marine opening＋fluid |
| `LAVA_TUBE` | swept tunnel carve |
| `OVERHANG` | solid add＋recess carve |
| `NATURAL_ARCH` | solid add＋through carve |
| `SINKHOLE` | surface carve＋cave connection |
| `UNDERGROUND_LAKE` | basin carve＋fluid add |
| `SKY_ISLAND` / `SKY_ISLAND_GROUP` | independent solid add＋underside carve |

詳細なoperatorは [局所3D地形](volumetric-terrain.md) で定義する。

### 3.8 Special environment / theme

すべてを新しい地形generatorにしない。次は既存landformへenvironment／material／volumeを組み合わせるpresetとして扱うのが基本である。

| Theme | 合成例 |
|---|---|
| Cherry grove | hill/mountain＋temperate climate＋cherry assemblage |
| Glacier / ice fjord | glacial mountain/fjord＋cold climate＋ice/snow profile |
| Geothermal terrace | spring＋terraced deposition＋thermal material/ecology |
| Bamboo/rainforest basin | valley/wetland＋warm humid ecology |
| Crystal/luminous cave | cave network＋special material/ecology profile |
| Mushroom-like biome | plain/forest/cave＋fungal assemblage preset |
| Dead volcanic wasteland | volcano/lava field＋arid climate＋early succession |
| Shattered cliffs / floating reef | cliff/sky island/coral featureの明示的複合 |

独自の形状不変条件が必要になった時だけ新kindへ昇格させる。色や植生themeだけでgenerator kindを増やさない。

## 4. 複合featureとcomposition

複合地形を1個の巨大generatorへ閉じ込めず、親featureが必要な子planをcompileする。

```text
VOLCANIC_ARCHIPELAGO
├── island mass plans
├── volcanic cone / caldera plans
├── radial drainage plans
├── lava / ash geology profiles
└── coast transition plans
```

```text
MANGROVE_WETLAND
├── low-relief wetland surface
├── tidal channel network
├── salinity / groundwater fields
├── mud/silt substrate
└── mangrove habitat / root placement
```

子planはstable derived IDを持ち、validatorとpreviewから追跡できる。親featureを複数moduleが無秩序に変更するのではなく、compile済みstage graphとfield mergeで合成する。

## 5. Feature間interaction

compile時にinteraction policyを明示する。

| Interaction | 例 | 方針 |
|---|---|---|
| Required | river → delta | relationがなければcompile error |
| Cooperative | canyon + river | shared centerline、hydrologyが床高を所有 |
| Overlay | snow on mountain | environment fieldで後段適用 |
| Exclusive | lake vs independent solid island | hard overlapならerror |
| Ordered | cliff add then sea-cave carve | `ORDERED_CSG` をBlueprintに保存 |
| Transition | beach ↔ rocky cape | ownership境界＋blend band |

同じcellのelevationを2つのregional featureが所有する場合は、priorityだけで黙って決めない。専用interaction compiler、明示merge、または入力修正のいずれかを要求する。

## 6. Generator / Validator / Previewの拡張単位

新しいkindをsupportedへ上げるには、最低限次が同じchange setに必要である。

1. kind descriptorとparameter Schema
2. Intent→FeaturePlan compiler
3. declared field I/Oを持つgenerator
4. baseline invariantに加えたfeature validator
5. feature geometry、actual field、residualのpreview layer
6. deterministic fixture、negative fixture、budget test
7. presetとユーザー向けdocs

generatorだけ実装し、validatorやpreviewがない状態は `EXPERIMENTAL` とする。

## 7. Preset

presetはfeatureの別名ではなく、複数feature、environment、soft constraint、default parameterをまとめるdataである。

```text
rocky-coast
azure-coast
fjord-basin
volcanic-archipelago
mangrove-delta
snowy-mountain-lake
lush-karst-caves
canyon-waterfall
floating-islands
coral-atoll
```

初期はrepository内の署名／checksum対象JSON/YAMLをbuilt-in catalogから読む。presetから任意class、script、外部URL、block commandを参照させない。展開後のcanonical TerrainIntent v2をReleaseへ保存し、preset更新が過去Releaseを変えないようにする。

## 8. 実装優先順

1. `SANDY_BEACH`、`BREAKWATER_HARBOR`、`HARBOR_BASIN`、`ROCKY_CAPE` でfeature基盤を縦に通す。
2. `MOUNTAIN_RANGE`、`RIVER`、`LAKE`、`CANYON`、`WATERFALL` でregional/hydrologyを固める。
3. `DELTA`、`FJORD`、`VOLCANIC_ARCHIPELAGO` でgraphと複合featureを検証する。
4. `MANGROVE_WETLAND`、`ALPINE_MOUNTAIN_RANGE`、`CORAL_REEF` でenvironment連携を固める。
5. `CAVE_NETWORK`、`LUSH_CAVE`、`OVERHANG`、`NATURAL_ARCH`、`SKY_ISLAND_GROUP` で局所3Dを導入する。

網羅性はkind数ではなく、「契約・生成・検証・診断・性能上限が揃ったsupported kind数」で測る。

## 9. Taskとlifecycleの対応

実装順の詳細は [Task Index](task-index.md) を正本とする。feature generator Taskではdescriptor／Schema／generator／単体fixtureを閉じ、原則`EXPERIMENTAL`のままにする。同Phaseのvalidator／preview Task、Release capability Task、統合監査Taskが揃った後だけ`SUPPORTED`へ変更する。

| Feature group | 主な実装Task | lifecycleを確定するTask |
|---|---|---|
| Beach／harbor／cape | `V2-2-03`〜`V2-2-07` | `V2-2-12` |
| River／lake／canyon／waterfall／delta／tidal／fjord | `V2-3-03`〜`V2-3-09` | `V2-3-15` |
| Mountain／volcanic skeleton | `V2-3-10`〜`V2-3-11` | `V2-3-15`。material完成は`V2-4-15` |
| Mangrove／coral／snow／environmental completion | `V2-4-06`、`V2-4-09`〜`V2-4-12` | `V2-4-15` |
| Cave／lush／underground lake／sea cave／overhang／arch／sky island | `V2-5-06`〜`V2-5-14` | `V2-5-18` |

複数Phaseにまたがるscenarioは、骨格Taskが終わってもfull completionではない。例としてwaterfallはV2-3のgraph／2.5Dと`V2-5-13`のvolume、volcanic archipelagoはV2-3の骨格とV2-4のmaterialを両方必要とする。親Phaseの完了や別featureの成功を根拠に、未完のkindを一括して`SUPPORTED`へ上げない。
