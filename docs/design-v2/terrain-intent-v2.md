# TerrainIntent v2

> Status: V2-15-04でADR 0036の`CANONICAL_V2` authoring projectionを追加した。historic `schemas/terrain-intent-v2.schema.json`／`TerrainIntentV2`は`LEGACY_V2` compatibility readerとして凍結し、新規Schema／model／codecを並設する。V2-15-05は既存`LEGACY_V2` coastal production経路の選択をregistry-driven spineへ変更した。V2-15-06〜08は`hydrology-plan`／`environment-fields`／`sparse-volume` shared artifactのproduction Application Service／pipelineを並設し、exact capability dependencyを同一Releaseでstrict verifyするが、個別hydrology／environment／volume Feature leafとPaper能力は未接続である。次Taskは`V2-15-09`である。

## 1. 目的

TerrainIntent v2は、人間、AI、画像制約から得た「何を、どこに、どの形で、どう接続し、何を必ず守るか」を、generator実装から独立して記述する意味契約である。全block、実行可能コード、Minecraft block state、任意filesystem pathは含めない。

v1の全域集約値を増築するのではなく、次を第一級要素にする。

- stable IDを持つ地形 `feature`
- point、spline、polygon、mask、局所volume等の `geometry`
- feature間の接続、内包、上流下流等の `relation`
- 絶対条件と希望条件を分ける `HARD` / `SOFT` constraint
- 地質、気候、生態のsemantic preset／範囲
- 画像から正規化済みartifactだけを指すconstraint map reference
- feature種別ごとの型付きparameterとvalidation target

Intentは「生成方法」を決めない。どのmoduleを使い、fieldをどう合成するかは [WorldBlueprint v2](world-blueprint-v2.md) のcompile責務である。

## 2. Top-level契約

```text
TerrainIntentV2
├── intentVersion / intentId / theme
├── coordinateSystem
├── features[]
├── relations[]
├── constraints[]
├── environment
├── mapReferences[]
├── structures[]
└── provenance
```

次は**現行`schemas/terrain-intent-v2.schema.json`をそのまま通るstrict最小例**である。現行strict JSONではtop-level `provenance`を省略できない。

```json
{
  "intentVersion": 2,
  "intentId": "example",
  "theme": "Short human-readable purpose",
  "coordinateSystem": {
    "horizontal": "NORMALIZED_XZ",
    "origin": "NORTH_WEST",
    "xAxis": "EAST",
    "zAxis": "SOUTH",
    "vertical": "BLOCK_Y_OR_SURFACE_OFFSET"
  },
  "features": [],
  "relations": [],
  "constraints": [],
  "environment": {},
  "mapReferences": [],
  "structures": [],
  "provenance": {
    "source": "MANUAL",
    "sourceId": "strict-minimum",
    "confidence": 1,
    "confirmationState": "CONFIRMED"
  }
}
```

`intentVersion` はTerrainIntent意味契約のversionであり、generator versionやRelease format versionではない。`features`、`relations`、`constraints` はIDを含め、配列順を意味へ使わない。canonicalization時はIDで安定順序化する。

### 2.1 Feature projection discriminator（V2-15-04）

同じ`intentVersion: 2`に二つの明示projectionがある。historic文書は外部selector
`LEGACY_V2`を指定した専用readerだけで読み、`featureProjection`欠落からlegacyを推測しない。
新規authoring文書は`schemas/terrain-intent-v2-canonical.schema.json`とtop-level
`featureProjection: CANONICAL_V2`を必須とする。generic dispatcherはこのdiscriminatorがない
v2文書を拒否する。

canonical projectionは46 top-level kindに限定し、旧14値を7 parent discriminator行と6 typed
child tokenへ写像する。移行由来elementの`legacySeedBinding`は旧kind、derivation version、seed
namespace、module ID／version、generator versionを保持し、canonical JSONとsemantic checksumへ
参加する。詳細な機械projectionは[Canonical feature target registry](canonical-feature-target-registry.md)、
互換fixture対は[examples/v2/catalog](../../examples/v2/catalog/README.md)を参照する。

V2-15-05の`production-dispatch-registry-v1`はSchema projectionを推測しない。現行production
Application Serviceがstrict legacy readerで読んだIntentについて、current-state registry上のcoastal
4 kindだけを完全なhandler chainへdispatchする。canonical parent／childをgeneratorへ接続する作業は
対応するV2-15 leaf Taskで行い、本Taskからは昇格しない。

## 3. Geometry

水平座標は原則 `[x,z]` の0.0〜1.0で、boundsの北西を原点とする。1.0はboundsの外ではなく最終cellの外縁を表す。compilerがrelease-local fixed-pointへ量子化する。Yは用途に応じてabsolute block Y、地表からのoffset、水面からのoffsetを明記する。現行strict Schemaが受理するgeometryは`POINT`、`MULTI_POINT`、`SPLINE`、`MULTI_SPLINE`、`POLYGON`、`VOLUME_GUIDE`である。`MASK_REF`はbinding済みfieldをgeometryとして参照する将来のtarget designであり、現行Schemaへは渡せない。

| `type` | 用途 | 必須情報 |
|---|---|---|
| `POINT` | peak、火口、滝、入口 | `point` |
| `MULTI_POINT` | 島列、peak群、入口群 | stable `id` と `point` を持つ `points[]` |
| `SPLINE` | 海岸、川、尾根、谷、堤 | `points`、`interpolation` |
| `MULTI_SPLINE` | 分流、潮路、ridge群 | stable `id` と `points` を持つ `paths[]` |
| `POLYGON` | feature領域、hole付きreef | `rings[]`。先頭が外周、以降がhole |
| `MASK_REF`（target） | canonical raster制約 | `mapId`、`labels`／threshold |
| `VOLUME_GUIDE` | cave、overhang、sky island | `footprint` と `vertical` |

polygon ringは閉じた座標列とし、自己交差を許さない。splineは補間法、端点、必要なら幅profileを明示する。自由形式SVG pathや実行式は許さない。

point、path、endpoint等のsubgeometry IDもIntent内で一意にする。parameterは数値profileまたはrelationで結ばれたfeatureのsubgeometry IDを参照できるが、feature間の内包／接続自体はrelationだけを正本とする。

## 4. Feature

```text
FeatureIntent
├── id
├── kind
├── geometry
├── parameters
├── priority
└── provenance
```

- `id`、`kind`、`geometry`、`parameters` は必須である。
- `kind` は [地形feature分類](terrain-feature-taxonomy.md) のversion付きcatalogから選ぶ。
- `parameters` はkindごとのSchemaで検査し、unknown keyは黙って無視しない。
- 現行strict Schemaでは`priority`を必須とし、default相当値は明示的な`0`である。同じ場所へ複数のsoft featureが候補になる際のcompile用で、fieldの合成順そのものではない。
- 現行strict Schemaではfeatureごとの`provenance`も必須だが、生成結果へ影響しない。AI由来draftではsource、confidence、confirmation stateを記録し、freeze前に未確認項目を解消する。ambiguityや自由な説明文を追加するのは将来のtarget designであり、現行Schemaはunknown fieldとして拒否する。
- 現行strict Schemaでkind固有parameterが型付けされているのは、V2-2 coastal subsetと、V2-3の`MEANDERING_RIVER`、`LAKE`、`CANYON`、`WATERFALL`、`DELTA`である。それ以外のkindは`parameters: {}`だけを受理し、詳細parameterは対応TaskのSchema拡張まで受理しない。
- breakwater、dam、terrace等の地形規模人工物はfeatureである。灯台や小橋等のasset配置は `structures` とする。

## 5. Relation

relationはfeature間、またはfeatureとworld boundaryの位相／水系意味を表す。例は `CONNECTS_TO`、`DRAINS_TO`、`EMPTIES_INTO`、`WITHIN`、`FLANKS`、`ADJACENT_TO`、`ENCLOSED_BY`、`ENCLOSES`、`ON_PATH_OF`、`ORIGINATES_AT`、`REACHABLE_FROM`、`ENTRANCE_OF`、`CARVES_FLANK_OF`、`CARVES_THROUGH`、`SUPPORTED_BY`、`OVERLAPS`、`EXCLUDES`、`UPSTREAM_OF` である。

`V2-2-07`ではcoastal feature同士の`ADJACENT_TO`／`OVERLAPS`だけに、任意の`transition` objectを追加した。指定する場合は`transitionVersion=1`、`profile=PRIORITY_BLEND`、`bandBlocks=1..32`をすべて明示し、future version、0幅、他relation kindへの付与を拒否する。coastal同士の隣接を実行planへcompileする場合はpolicyを必須とし、欠落値やfeature kindから幅を推測しない。priorityはblendの数値入力としてBlueprintへfreezeされるが、HARD conflictをpriorityで解消しない。breakwater／basinの0-band connection seamは既存HARD `ENCLOSES` relationから専用profileへcompileし、Intent側にtransitionを重複記述しない。

```json
{
  "id": "river-to-delta",
  "kind": "DRAINS_TO",
  "from": "feature:main-river",
  "to": "feature:river-delta",
  "strength": "HARD"
}
```

endpoint grammarは `feature:<id>`、`boundary:<edge-id>`、world constraintだけが使う `world` に限定する。relation graphが循環してよい種類と、DAGでなければならない種類をcatalogで分け、未知参照、自己接続、不可能な型組合せはcompile前に拒否する。

| Relation kind | `from` → `to` の向き |
|---|---|
| `DRAINS_TO` / `EMPTIES_INTO` / `UPSTREAM_OF` | 上流feature → 下流feature／boundary |
| `WITHIN` / `ENCLOSED_BY` | 内側feature → container feature |
| `ENCLOSES` | container feature → 内側feature |
| `FLANKS` / `CARVES_FLANK_OF` | wall／carver feature → host feature |
| `ORIGINATES_AT` / `ON_PATH_OF` | 派生feature → origin／path feature |
| `CONNECTS_TO` | connector feature → 接続先feature |
| `REACHABLE_FROM` | 到達対象 → entrance/source feature |
| `ENTRANCE_OF` | entrance feature → volume feature |
| `CARVES_THROUGH` | pass／tunnel feature → host barrier feature |
| `SUPPORTED_BY` | supported feature → host feature |
| `ADJACENT_TO` / `OVERLAPS` / `EXCLUDES` | 対称。canonicalization時はendpoint ID順 |

`CONNECTS_TO_BOUNDARY` はrelationではなく、単一subjectとboundary parameterを持つconstraint kindへ統一する。catalogは各kindの許可endpoint type、対称性、cycle可否、HARD/SOFT可否をversion化する。

## 6. Hard / Soft constraint

constraintは測定可能でなければならない。

```text
Constraint
├── id / strength
├── kind / subject
├── metricまたはparameters
├── range / target / tolerance
├── weight（SOFTのみ）
└── failurePolicy
```

- `HARD`: compileまたはvalidationで満たせなければcandidateを失敗させる。
- `SOFT`: 正規化weightをscoreへ加え、実現値と残差をReleaseへ記録する。
- 「荒々しい」「広め」等は、AI／presetが具体的metric rangeへ変換する。
- hard同士が矛盾する場合、勝手にpriority解決せず診断付きで拒否する。
- hard geometryはnoiseで境界を動かさない。soft geometryは許容距離内で調整できる。

現行strict Schemaが受理するconstraint kindは`METRIC_RANGE`と`EDGE_CLASSIFICATION`だけである。`CONNECTS_TO_BOUNDARY`等、8.1〜8.11に現れる追加kindは対象featureを実装するPhaseでSchema／validatorと同時に追加するtarget designであり、未知kindへfallbackしない。

## 7. Environmentとmap reference

`environment` はMinecraft biome名ではなくsemanticなpresetを持つ。現行strict Schemaが受理するのは`geologyPreset`、`climatePreset`、`ecologyPreset`のsymbolだけである。次の`overrides`はV2-4で型を追加する**target-design例**であり、現行Schemaへ渡すとunknown fieldとして拒否される。

```json
{
  "geologyPreset": "BASALTIC_VOLCANIC",
  "climatePreset": "WARM_HUMID_MARITIME",
  "ecologyPreset": "MANGROVE_ESTUARY",
  "overrides": {
    "annualTemperatureC": { "min": 22, "max": 30 },
    "salinity01": { "min": 0.25, "max": 0.8 }
  }
}
```

`mapReferences` はfreeze済み `ConstraintMapBinding` の唯一の正本である。Requestのsource descriptorはraw source、encoding、座標変換だけを所有し、role、HARD/SOFT、weight、toleranceを重複保持しない。Blueprint compilerはRequest descriptorから直接制約を適用せず、必ずこのbindingを1回だけ読む。raw path、URL、base64 rasterをIntentへ埋め込まない。

```json
{
  "id": "coast-mask-binding",
  "sourceId": "constraint-source:coast-mask",
  "role": "LAND_WATER_MASK",
  "artifactId": "constraint:land-water:sha256-0000000000000000000000000000000000000000000000000000000000000000",
  "strength": "HARD",
  "sampling": "NEAREST",
  "toleranceBlocks": 0
}
```

詳細は [画像制約map](image-constraint-maps.md) を参照する。

## 8. JSON例（target design）

以下の8.1〜8.11は、各地形を最終的にどう表現するかを示す互いに独立した**target-design例**である。将来Phaseのparameter、constraint、environment overrideを含み、現行strict Schemaが要求する`priority`／`provenance`を読みやすさのため省略している箇所もあるため、現行codecへそのまま渡せるfixtureではない。

現在のstrict Schema／recordでparse、canonical normalize、round-tripできる機械検証済みfixtureは`examples/v2/diagnostic/azure-coast.terrain-intent-v2.json`と`examples/v2/diagnostic/scenarios/*.terrain-intent-v2.json`である。Azure CoastのSANDY_BEACH、HARBOR_BASIN、BREAKWATER_HARBORはそれぞれV2-2-03／04／05 parameterとplanを実行できる。breakwaterは`crestProfile=FLAT`、`foundationProfile=LINEAR_SIDE_SLOPE`、明示run/rise、2つの異なるopening endpoint、2..64 blockのclear widthを必須とし、暗黙profileを推測しない。`examples/v2/diagnostic/negative/sandy-beach-endpoint-corruption.terrain-intent-v2.json`はendpoint taper重複、`examples/v2/diagnostic/negative/harbor-basin-closed-entrance.terrain-intent-v2.json`はouter-ring opening不成立としてSchema通過後のcompileで拒否される。他の未実装featureは`parameters`を空objectにし、必須`priority`／`provenance`を明示する。一方、V2-1の3 role bindingのstrict形は`examples/v2/manual-constraint-island/terrain-intent-v2.json`が示す。そこにあるfixture checksumはSchema例であり、manual serviceが実sourceからfreezeしたsemantic checksumと一致しなければ公開を拒否する。

したがって、以下の数値は設計の具体性を示すもので、製品default、現行Schema受理、generator対応のいずれも確定しない。

### 8.1 Beach + breakwater + rocky cape

この例は既存 [Azure Coast request](../../src/main/resources/legacy/v1/fixtures/azure-coast/request.yml) の400×400、water Y=50、max Y=64を前提とする。幅や高さのhard rangeはそのbounds内で実現可能な値にしている。港口endpointの中心間は0.0875×400=35 blocksで、両armのcrest幅7 blocksを差し引く `CLEAR_EDGE_TO_EDGE` は28 blocksになる。

```json
{
  "intentVersion": 2,
  "intentId": "azure-coast-v2",
  "theme": "A broad sandy bay, a protected central harbor, and a rugged eastern cape",
  "coordinateSystem": {
    "horizontal": "NORMALIZED_XZ",
    "origin": "NORTH_WEST",
    "xAxis": "EAST",
    "zAxis": "SOUTH",
    "vertical": "BLOCK_Y_OR_SURFACE_OFFSET"
  },
  "features": [
    {
      "id": "main-beach",
      "kind": "SANDY_BEACH",
      "geometry": {
        "type": "SPLINE",
        "points": [[0.02, 0.42], [0.20, 0.35], [0.42, 0.41]],
        "interpolation": "CATMULL_ROM"
      },
      "parameters": {
        "widthBlocks": { "min": 20, "max": 55 },
        "shoreSlopeDegrees": { "min": 2, "max": 8 },
        "nearshoreDepthBlocks": { "atDistance": 40, "target": 6 },
        "foreshoreShare01": 0.6,
        "endpointTaperBlocks": 64,
        "landSide": "LEFT"
      }
    },
    {
      "id": "central-breakwater",
      "kind": "BREAKWATER_HARBOR",
      "geometry": {
        "type": "MULTI_SPLINE",
        "paths": [
          {
            "id": "west-arm",
            "startEndpointId": "west-landfall",
            "endEndpointId": "west-opening",
            "points": [[0.43, 0.39], [0.46, 0.48], [0.475, 0.53]]
          },
          {
            "id": "east-arm",
            "startEndpointId": "east-landfall",
            "endEndpointId": "east-opening",
            "points": [[0.61, 0.47], [0.59, 0.51], [0.5625, 0.53]]
          }
        ],
        "interpolation": "CATMULL_ROM"
      },
      "parameters": {
        "crestWidthBlocks": 7,
        "crestAboveWaterBlocks": 3,
        "outerDepthBlocks": 14,
        "crestProfile": "FLAT",
        "foundationProfile": "LINEAR_SIDE_SLOPE",
        "foundationSideSlopeRunPerRise": 1.5,
        "opening": {
          "betweenEndpointIds": ["west-opening", "east-opening"],
          "widthBlocks": 28,
          "measurement": "CLEAR_EDGE_TO_EDGE"
        },
        "innerSide": "NORTH"
      }
    },
    {
      "id": "harbor-basin",
      "kind": "HARBOR_BASIN",
      "geometry": {
        "type": "POLYGON",
        "rings": [[[0.43, 0.39], [0.61, 0.47], [0.5625, 0.53], [0.475, 0.53], [0.43, 0.39]]]
      },
      "parameters": {
        "waterDepthBlocks": { "min": 8, "max": 10 },
        "entranceEndpointIds": ["west-opening", "east-opening"],
        "entranceCorridorLengthBlocks": 24,
        "bottomProfile": "EDGE_TO_CENTER_LINEAR",
        "profileTransitionBlocks": 8
      }
    },
    {
      "id": "east-cape",
      "kind": "ROCKY_CAPE",
      "geometry": {
        "type": "POLYGON",
        "rings": [[[0.62, 0.28], [0.94, 0.22], [1.0, 0.56], [0.72, 0.60], [0.62, 0.28]]]
      },
      "parameters": {
        "cliffHeightBlocks": { "min": 6, "max": 10 },
        "localReliefAboveSeaBlocks": { "min": 12, "max": 14 },
        "cliffBandWidthBlocks": { "min": 6, "max": 10 },
        "seaStackCount": { "min": 3, "max": 5 },
        "seaStackRadiusBlocks": { "min": 2, "max": 3 },
        "seaStackOffshoreDistanceBlocks": { "min": 6, "max": 10 },
        "channelCount": { "min": 1, "max": 2 },
        "channelWidthBlocks": { "min": 2, "max": 4 },
        "channelLengthBlocks": { "min": 10, "max": 18 },
        "channelDepthBlocks": { "min": 2, "max": 4 },
        "rockExposure01": { "min": 0.68, "max": 0.92 },
        "seawardSide": "EAST",
        "capeMode": "TWO_POINT_FIVE_D_ONLY"
      }
    }
  ],
  "relations": [
    {
      "id": "breakwater-encloses-basin",
      "kind": "ENCLOSES",
      "from": "feature:central-breakwater",
      "to": "feature:harbor-basin",
      "strength": "HARD"
    },
    {
      "id": "harbor-adjoins-beach",
      "kind": "ADJACENT_TO",
      "from": "feature:central-breakwater",
      "to": "feature:main-beach",
      "strength": "HARD"
    },
    {
      "id": "cape-adjoins-harbor",
      "kind": "ADJACENT_TO",
      "from": "feature:east-cape",
      "to": "feature:central-breakwater",
      "strength": "SOFT"
    }
  ],
  "constraints": [
    {
      "id": "south-is-sea",
      "strength": "HARD",
      "kind": "EDGE_CLASSIFICATION",
      "subject": "world",
      "parameters": { "edge": "SOUTH", "classification": "SEA", "minimumShare01": 0.9 }
    },
    {
      "id": "beach-width",
      "strength": "HARD",
      "kind": "METRIC_RANGE",
      "subject": "feature:main-beach",
      "metric": "BEACH_WIDTH_BLOCKS_P50",
      "range": { "min": 20, "max": 55 },
      "tolerance": 2
    },
    {
      "id": "harbor-opening",
      "strength": "HARD",
      "kind": "METRIC_RANGE",
      "subject": "feature:central-breakwater",
      "metric": "HARBOR_OPENING_WIDTH_BLOCKS",
      "range": { "min": 26, "max": 30 },
      "tolerance": 1
    },
    {
      "id": "harbor-depth",
      "strength": "HARD",
      "kind": "METRIC_RANGE",
      "subject": "feature:harbor-basin",
      "metric": "NAVIGABLE_WATER_DEPTH_BLOCKS_P50",
      "range": { "min": 8, "max": 10 },
      "tolerance": 1
    },
    {
      "id": "irregular-cape",
      "strength": "SOFT",
      "kind": "METRIC_RANGE",
      "subject": "feature:east-cape",
      "metric": "COASTLINE_TURNING_PER_100_BLOCKS",
      "range": { "min": 1.8, "max": 4.2 },
      "weight": 0.7
    }
  ],
  "environment": {
    "geologyPreset": "MIXED_SEDIMENT_AND_ANDESITE",
    "climatePreset": "TEMPERATE_MARITIME",
    "ecologyPreset": "SPARSE_COASTAL"
  },
  "mapReferences": [],
  "structures": []
}
```

### 8.2 Fjord

```json
{
  "intentVersion": 2,
  "intentId": "deep-northern-fjord",
  "theme": "A long glacial fjord connected to the northern sea",
  "coordinateSystem": {
    "horizontal": "NORMALIZED_XZ",
    "origin": "NORTH_WEST",
    "xAxis": "EAST",
    "zAxis": "SOUTH",
    "vertical": "BLOCK_Y_OR_SURFACE_OFFSET"
  },
  "features": [
    {
      "id": "main-fjord",
      "kind": "FJORD",
      "geometry": {
        "type": "SPLINE",
        "points": [[0.48, 0.0], [0.43, 0.22], [0.55, 0.48], [0.50, 0.82]],
        "interpolation": "CATMULL_ROM"
      },
      "parameters": {
        "surfaceWidthBlocks": { "min": 34, "max": 78 },
        "channelDepthBlocks": { "min": 18, "max": 34 },
        "crossSection": "GLACIAL_U",
        "headBasinRadiusBlocks": 52
      }
    },
    {
      "id": "fjord-walls",
      "kind": "GLACIAL_MOUNTAIN_RANGE",
      "geometry": {
        "type": "POLYGON",
        "rings": [[[0.10, 0.02], [0.90, 0.02], [0.86, 0.92], [0.16, 0.92], [0.10, 0.02]]]
      },
      "parameters": {
        "wallReliefBlocks": { "min": 72, "max": 150 },
        "cirqueCount": { "min": 2, "max": 6 },
        "snowlineY": 170
      }
    }
  ],
  "relations": [
    {
      "id": "walls-flank-fjord",
      "kind": "FLANKS",
      "from": "feature:fjord-walls",
      "to": "feature:main-fjord",
      "strength": "HARD"
    }
  ],
  "constraints": [
    {
      "id": "fjord-reaches-sea",
      "strength": "HARD",
      "kind": "CONNECTS_TO_BOUNDARY",
      "subject": "feature:main-fjord",
      "parameters": { "edge": "NORTH", "classification": "SEA" }
    },
    {
      "id": "fjord-slenderness",
      "strength": "HARD",
      "kind": "METRIC_RANGE",
      "subject": "feature:main-fjord",
      "metric": "CENTERLINE_LENGTH_TO_MEAN_WIDTH",
      "range": { "min": 5.0, "max": 14.0 },
      "tolerance": 0.2
    },
    {
      "id": "high-sidewalls",
      "strength": "SOFT",
      "kind": "METRIC_RANGE",
      "subject": "feature:main-fjord",
      "metric": "SIDEWALL_RELIEF_BLOCKS_P50",
      "range": { "min": 80, "max": 140 },
      "weight": 0.9
    }
  ],
  "environment": {
    "geologyPreset": "GLACIATED_HARD_ROCK",
    "climatePreset": "COLD_MARITIME",
    "ecologyPreset": "SUBALPINE_FJORD"
  },
  "mapReferences": [],
  "structures": []
}
```

### 8.3 Delta

```json
{
  "intentVersion": 2,
  "intentId": "fan-delta",
  "theme": "A low-gradient river splitting into a marshy distributary fan",
  "coordinateSystem": {
    "horizontal": "NORMALIZED_XZ",
    "origin": "NORTH_WEST",
    "xAxis": "EAST",
    "zAxis": "SOUTH",
    "vertical": "BLOCK_Y_OR_SURFACE_OFFSET"
  },
  "features": [
    {
      "id": "main-river",
      "kind": "MEANDERING_RIVER",
      "geometry": {
        "type": "SPLINE",
        "points": [[0.50, 0.02], [0.44, 0.30], [0.54, 0.54], [0.50, 0.68]],
        "interpolation": "CATMULL_ROM"
      },
      "parameters": {
        "bankfullWidthBlocks": { "min": 14, "max": 24 },
        "dischargeClass": "LARGE",
        "minimumBedSlope01": 0.0005,
        "variant": "MEANDERING_RIVER"
      }
    },
    {
      "id": "river-delta",
      "kind": "DELTA",
      "geometry": {
        "type": "POLYGON",
        "rings": [[[0.22, 0.58], [0.78, 0.58], [0.94, 1.0], [0.06, 1.0], [0.22, 0.58]]]
      },
      "parameters": {
        "distributaryCount": { "min": 4, "max": 8 },
        "fanOpeningDegrees": { "min": 55, "max": 115 },
        "fanReliefBlocks": { "min": 2, "max": 14 },
        "sandbarCount": { "min": 5, "max": 9 },
        "shallowSeaDepthBlocks": { "min": 2, "max": 6 },
        "fanProfile": "APEX_TO_SEA_LINEAR"
      }
    }
  ],
  "relations": [
    {
      "id": "river-drains-to-delta",
      "kind": "DRAINS_TO",
      "from": "feature:main-river",
      "to": "feature:river-delta",
      "strength": "HARD"
    },
    {
      "id": "delta-empties-south",
      "kind": "EMPTIES_INTO",
      "from": "feature:river-delta",
      "to": "boundary:SOUTH",
      "strength": "HARD"
    }
  ],
  "constraints": [
    {
      "id": "south-receiving-sea",
      "strength": "HARD",
      "kind": "EDGE_CLASSIFICATION",
      "subject": "world",
      "parameters": { "edge": "SOUTH", "classification": "SEA", "minimumShare01": 0.8 }
    },
    {
      "id": "delta-branches",
      "strength": "HARD",
      "kind": "METRIC_RANGE",
      "subject": "feature:river-delta",
      "metric": "ACTIVE_DISTRIBUTARY_COUNT",
      "range": { "min": 4, "max": 8 },
      "tolerance": 0
    },
    {
      "id": "delta-low-relief",
      "strength": "HARD",
      "kind": "METRIC_RANGE",
      "subject": "feature:river-delta",
      "metric": "ELEVATION_RANGE_BLOCKS",
      "range": { "min": 2, "max": 14 },
      "tolerance": 1
    }
  ],
  "environment": {
    "geologyPreset": "ALLUVIAL_SEDIMENT",
    "climatePreset": "WARM_HUMID",
    "ecologyPreset": "DELTA_MARSH"
  },
  "mapReferences": [],
  "structures": []
}
```

### 8.4 Volcanic archipelago

```json
{
  "intentVersion": 2,
  "intentId": "volcanic-arc",
  "theme": "A curved chain of volcanic islands with one active central cone",
  "coordinateSystem": {
    "horizontal": "NORMALIZED_XZ",
    "origin": "NORTH_WEST",
    "xAxis": "EAST",
    "zAxis": "SOUTH",
    "vertical": "BLOCK_Y_OR_SURFACE_OFFSET"
  },
  "features": [
    {
      "id": "island-arc",
      "kind": "VOLCANIC_ARCHIPELAGO",
      "geometry": {
        "type": "MULTI_POINT",
        "points": [
          { "id": "west-islet", "point": [0.16, 0.68] },
          { "id": "west-island", "point": [0.31, 0.51] },
          { "id": "main-island", "point": [0.50, 0.42] },
          { "id": "east-island", "point": [0.69, 0.49] },
          { "id": "east-islet", "point": [0.84, 0.66] }
        ]
      },
      "parameters": {
        "islands": [
          { "pointId": "west-islet", "radiusBlocks": 36, "summitHeightBlocksAboveSea": 42 },
          { "pointId": "west-island", "radiusBlocks": 52, "summitHeightBlocksAboveSea": 58 },
          { "pointId": "main-island", "radiusBlocks": 88, "summitHeightBlocksAboveSea": 116 },
          { "pointId": "east-island", "radiusBlocks": 50, "summitHeightBlocksAboveSea": 54 },
          { "pointId": "east-islet", "radiusBlocks": 34, "summitHeightBlocksAboveSea": 38 }
        ],
        "submarineSaddleDepthBlocks": { "min": 10, "max": 28 }
      }
    },
    {
      "id": "central-caldera",
      "kind": "VOLCANIC_CALDERA",
      "geometry": { "type": "POINT", "point": [0.50, 0.42] },
      "parameters": {
        "rimRadiusBlocks": 32,
        "rimReliefBlocks": 26,
        "craterFloorDepthBlocks": 18,
        "breachDirection": "SOUTH_EAST"
      }
    },
    {
      "id": "young-lava-flow",
      "kind": "LAVA_FLOW_FIELD",
      "geometry": {
        "type": "SPLINE",
        "points": [[0.50, 0.42], [0.55, 0.50], [0.59, 0.65]],
        "interpolation": "CATMULL_ROM"
      },
      "parameters": { "widthBlocks": { "min": 12, "max": 28 }, "surfaceRoughness01": 0.82 }
    }
  ],
  "relations": [
    {
      "id": "caldera-on-main-island",
      "kind": "WITHIN",
      "from": "feature:central-caldera",
      "to": "feature:island-arc",
      "strength": "HARD"
    },
    {
      "id": "lava-from-caldera",
      "kind": "ORIGINATES_AT",
      "from": "feature:young-lava-flow",
      "to": "feature:central-caldera",
      "strength": "HARD"
    }
  ],
  "constraints": [
    {
      "id": "islands-separated",
      "strength": "HARD",
      "kind": "METRIC_RANGE",
      "subject": "feature:island-arc",
      "metric": "PAIRWISE_DRY_LAND_GAP_BLOCKS_MIN",
      "range": { "min": 12, "max": 100 },
      "tolerance": 0
    },
    {
      "id": "central-dominance",
      "strength": "SOFT",
      "kind": "METRIC_RANGE",
      "subject": "feature:island-arc",
      "metric": "LARGEST_TO_SECOND_ISLAND_AREA_RATIO",
      "range": { "min": 1.5, "max": 3.0 },
      "weight": 0.8
    }
  ],
  "environment": {
    "geologyPreset": "BASALTIC_VOLCANIC",
    "climatePreset": "WARM_MARITIME",
    "ecologyPreset": "VOLCANIC_SUCCESSION"
  },
  "mapReferences": [],
  "structures": []
}
```

### 8.5 Canyon + waterfall

```json
{
  "intentVersion": 2,
  "intentId": "canyon-falls",
  "theme": "A river-cut canyon with a single major waterfall and plunge pool",
  "coordinateSystem": {
    "horizontal": "NORMALIZED_XZ",
    "origin": "NORTH_WEST",
    "xAxis": "EAST",
    "zAxis": "SOUTH",
    "vertical": "BLOCK_Y_OR_SURFACE_OFFSET"
  },
  "features": [
    {
      "id": "main-canyon",
      "kind": "CANYON",
      "geometry": {
        "type": "SPLINE",
        "points": [[0.18, 0.05], [0.34, 0.34], [0.56, 0.58], [0.80, 0.95]],
        "interpolation": "CATMULL_ROM"
      },
      "parameters": {
        "floorWidthBlocks": { "min": 14, "max": 30 },
        "rimWidthBlocks": { "min": 70, "max": 130 },
        "depthBlocks": { "min": 42, "max": 92 },
        "crossSection": "TERRACED_V"
      }
    },
    {
      "id": "canyon-river",
      "kind": "BEDROCK_RIVER",
      "geometry": {
        "type": "SPLINE",
        "points": [[0.18, 0.05], [0.34, 0.34], [0.56, 0.58], [0.80, 0.95]],
        "interpolation": "CATMULL_ROM"
      },
      "parameters": { "bankfullWidthBlocks": 10, "bedMaterial": "COARSE_BEDROCK_GRAVEL" }
    },
    {
      "id": "main-fall",
      "kind": "WATERFALL",
      "geometry": { "type": "POINT", "point": [0.48, 0.49] },
      "parameters": {
        "dropBlocks": { "min": 24, "max": 42 },
        "lipWidthBlocks": 9,
        "plungePoolRadiusBlocks": 16,
        "behindFallClearanceBlocks": 4
      }
    }
  ],
  "relations": [
    {
      "id": "river-in-canyon",
      "kind": "WITHIN",
      "from": "feature:canyon-river",
      "to": "feature:main-canyon",
      "strength": "HARD"
    },
    {
      "id": "fall-on-river",
      "kind": "ON_PATH_OF",
      "from": "feature:main-fall",
      "to": "feature:canyon-river",
      "strength": "HARD"
    }
  ],
  "constraints": [
    {
      "id": "downstream-continuity",
      "strength": "HARD",
      "kind": "MONOTONIC_PROFILE",
      "subject": "feature:canyon-river",
      "parameters": { "metric": "BED_ELEVATION", "direction": "DOWNSTREAM", "allowFalls": true }
    },
    {
      "id": "fall-drop",
      "strength": "HARD",
      "kind": "METRIC_RANGE",
      "subject": "feature:main-fall",
      "metric": "WATERFALL_DROP_BLOCKS",
      "range": { "min": 24, "max": 42 },
      "tolerance": 1
    }
  ],
  "environment": {
    "geologyPreset": "STRATIFIED_SEDIMENTARY_CANYON",
    "climatePreset": "SEASONAL_SEMI_ARID",
    "ecologyPreset": "RIPARIAN_CANYON"
  },
  "mapReferences": [],
  "structures": []
}
```

### 8.6 Mangrove wetland

```json
{
  "intentVersion": 2,
  "intentId": "mangrove-estuary",
  "theme": "A warm tidal wetland crossed by branching mangrove channels",
  "coordinateSystem": {
    "horizontal": "NORMALIZED_XZ",
    "origin": "NORTH_WEST",
    "xAxis": "EAST",
    "zAxis": "SOUTH",
    "vertical": "BLOCK_Y_OR_SURFACE_OFFSET"
  },
  "features": [
    {
      "id": "mangrove-wetland",
      "kind": "MANGROVE_WETLAND",
      "geometry": {
        "type": "POLYGON",
        "rings": [[[0.05, 0.10], [0.95, 0.10], [0.96, 1.0], [0.08, 1.0], [0.05, 0.10]]]
      },
      "parameters": {
        "waterloggedShare01": { "min": 0.30, "max": 0.58 },
        "microReliefBlocks": { "min": 1, "max": 4 },
        "canopyCover01": { "min": 0.45, "max": 0.76 },
        "rootClearanceBlocks": { "min": 1, "max": 3 }
      }
    },
    {
      "id": "tidal-channels",
      "kind": "TIDAL_CHANNEL_NETWORK",
      "geometry": {
        "type": "MULTI_SPLINE",
        "paths": [
          { "id": "main-tidal-channel", "points": [[0.48, 1.0], [0.51, 0.68], [0.44, 0.40], [0.36, 0.20]] },
          { "id": "east-branch", "points": [[0.50, 0.70], [0.70, 0.52], [0.88, 0.34]] },
          { "id": "west-branch", "points": [[0.45, 0.55], [0.26, 0.47], [0.12, 0.31]] }
        ],
        "interpolation": "POLYLINE"
      },
      "parameters": { "widthBlocks": { "min": 5, "max": 16 }, "tidalRangeBlocks": 2 }
    }
  ],
  "relations": [
    {
      "id": "channels-within-wetland",
      "kind": "WITHIN",
      "from": "feature:tidal-channels",
      "to": "feature:mangrove-wetland",
      "strength": "HARD"
    }
  ],
  "constraints": [
    {
      "id": "tidal-sea-connection",
      "strength": "HARD",
      "kind": "CONNECTS_TO_BOUNDARY",
      "subject": "feature:tidal-channels",
      "parameters": { "edge": "SOUTH", "classification": "SEA" }
    },
    {
      "id": "mangrove-salinity",
      "strength": "HARD",
      "kind": "FIELD_RANGE",
      "subject": "feature:mangrove-wetland",
      "metric": "SALINITY_01_P50",
      "range": { "min": 0.2, "max": 0.8 },
      "tolerance": 0.05
    }
  ],
  "environment": {
    "geologyPreset": "TIDAL_MUD_AND_SILT",
    "climatePreset": "WARM_HUMID_MARITIME",
    "ecologyPreset": "MANGROVE_ESTUARY"
  },
  "mapReferences": [],
  "structures": []
}
```

### 8.7 Snowy mountains

```json
{
  "intentVersion": 2,
  "intentId": "snowy-alpine-range",
  "theme": "A sharp alpine ridge with cirques, scree, and an elevation-dependent snowline",
  "coordinateSystem": {
    "horizontal": "NORMALIZED_XZ",
    "origin": "NORTH_WEST",
    "xAxis": "EAST",
    "zAxis": "SOUTH",
    "vertical": "BLOCK_Y_OR_SURFACE_OFFSET"
  },
  "features": [
    {
      "id": "alpine-ridge",
      "kind": "ALPINE_MOUNTAIN_RANGE",
      "geometry": {
        "type": "SPLINE",
        "points": [[0.08, 0.70], [0.30, 0.48], [0.53, 0.38], [0.78, 0.20], [0.94, 0.12]],
        "interpolation": "CATMULL_ROM"
      },
      "parameters": {
        "peakCount": { "min": 4, "max": 8 },
        "summitY": { "min": 205, "max": 268 },
        "ridgeSharpness01": { "min": 0.68, "max": 0.90 },
        "screeSlopeDegrees": { "min": 30, "max": 39 }
      }
    },
    {
      "id": "north-cirques",
      "kind": "GLACIAL_CIRQUE_FIELD",
      "geometry": {
        "type": "MULTI_POINT",
        "points": [
          { "id": "west-cirque", "point": [0.28, 0.40] },
          { "id": "central-cirque", "point": [0.50, 0.31] },
          { "id": "east-cirque", "point": [0.72, 0.16] }
        ]
      },
      "parameters": { "radiusBlocks": { "min": 30, "max": 58 }, "aspect": "NORTH" }
    }
  ],
  "relations": [
    {
      "id": "cirques-cut-ridge",
      "kind": "CARVES_FLANK_OF",
      "from": "feature:north-cirques",
      "to": "feature:alpine-ridge",
      "strength": "HARD"
    }
  ],
  "constraints": [
    {
      "id": "snowline",
      "strength": "HARD",
      "kind": "FIELD_TRANSITION",
      "subject": "feature:alpine-ridge",
      "parameters": { "field": "SNOW_COVER_01", "transitionY": 178, "transitionWidthBlocks": 12 }
    },
    {
      "id": "connected-ridgeline",
      "strength": "SOFT",
      "kind": "METRIC_RANGE",
      "subject": "feature:alpine-ridge",
      "metric": "RIDGE_CONTINUITY_01",
      "range": { "min": 0.85, "max": 1.0 },
      "weight": 0.9
    }
  ],
  "environment": {
    "geologyPreset": "ALPINE_GRANITIC",
    "climatePreset": "COLD_ALPINE",
    "ecologyPreset": "ALPINE_TREELINE"
  },
  "mapReferences": [],
  "structures": []
}
```

### 8.8 Coral reef

```json
{
  "intentVersion": 2,
  "intentId": "coral-atoll",
  "theme": "A shallow ring reef enclosing a navigable lagoon",
  "coordinateSystem": {
    "horizontal": "NORMALIZED_XZ",
    "origin": "NORTH_WEST",
    "xAxis": "EAST",
    "zAxis": "SOUTH",
    "vertical": "BLOCK_Y_OR_SURFACE_OFFSET"
  },
  "features": [
    {
      "id": "ring-reef",
      "kind": "CORAL_REEF",
      "geometry": {
        "type": "POLYGON",
        "rings": [
          [[0.12, 0.18], [0.82, 0.10], [0.94, 0.78], [0.24, 0.92], [0.12, 0.18]],
          [[0.30, 0.34], [0.70, 0.26], [0.76, 0.66], [0.38, 0.72], [0.30, 0.34]]
        ]
      },
      "parameters": {
        "reefCrestDepthBlocks": { "min": 1, "max": 4 },
        "reefWidthBlocks": { "min": 18, "max": 46 },
        "outerSlopeDegrees": { "min": 18, "max": 42 },
        "coralCover01": { "min": 0.45, "max": 0.78 }
      }
    },
    {
      "id": "inner-lagoon",
      "kind": "LAGOON",
      "geometry": {
        "type": "POLYGON",
        "rings": [[[0.30, 0.34], [0.70, 0.26], [0.76, 0.66], [0.38, 0.72], [0.30, 0.34]]]
      },
      "parameters": { "depthBlocks": { "min": 5, "max": 14 }, "sandPatchShare01": 0.35 }
    },
    {
      "id": "north-east-reef-pass",
      "kind": "REEF_PASS",
      "geometry": {
        "type": "SPLINE",
        "points": [[0.72, 0.115], [0.70, 0.19], [0.67, 0.27]],
        "interpolation": "POLYLINE"
      },
      "parameters": {
        "widthBlocks": { "min": 10, "max": 14 },
        "waterDepthBlocks": { "min": 4, "max": 7 }
      }
    }
  ],
  "relations": [
    {
      "id": "lagoon-enclosed",
      "kind": "ENCLOSED_BY",
      "from": "feature:inner-lagoon",
      "to": "feature:ring-reef",
      "strength": "HARD"
    },
    {
      "id": "pass-cuts-reef",
      "kind": "CARVES_THROUGH",
      "from": "feature:north-east-reef-pass",
      "to": "feature:ring-reef",
      "strength": "HARD"
    },
    {
      "id": "pass-connects-lagoon",
      "kind": "CONNECTS_TO",
      "from": "feature:north-east-reef-pass",
      "to": "feature:inner-lagoon",
      "strength": "HARD"
    }
  ],
  "constraints": [
    {
      "id": "lagoon-marine-pass",
      "strength": "HARD",
      "kind": "METRIC_RANGE",
      "subject": "feature:inner-lagoon",
      "metric": "NAVIGABLE_MARINE_PASS_COUNT",
      "range": { "min": 1, "max": 2 },
      "tolerance": 0
    },
    {
      "id": "reef-shallow",
      "strength": "HARD",
      "kind": "FIELD_RANGE",
      "subject": "feature:ring-reef",
      "metric": "WATER_DEPTH_BLOCKS_P90",
      "range": { "min": 1, "max": 8 },
      "tolerance": 1
    },
    {
      "id": "warm-water",
      "strength": "HARD",
      "kind": "FIELD_RANGE",
      "subject": "feature:ring-reef",
      "metric": "TEMPERATURE_C_P50",
      "range": { "min": 23, "max": 31 },
      "tolerance": 1
    }
  ],
  "environment": {
    "geologyPreset": "CARBONATE_REEF_PLATFORM",
    "climatePreset": "WARM_TROPICAL_MARITIME",
    "ecologyPreset": "SHALLOW_CORAL_REEF"
  },
  "mapReferences": [],
  "structures": []
}
```

### 8.9 Cave + lush cave

```json
{
  "intentVersion": 2,
  "intentId": "lush-cave-system",
  "theme": "A connected cave network with a humid central lush chamber",
  "coordinateSystem": {
    "horizontal": "NORMALIZED_XZ",
    "origin": "NORTH_WEST",
    "xAxis": "EAST",
    "zAxis": "SOUTH",
    "vertical": "BLOCK_Y_OR_SURFACE_OFFSET"
  },
  "features": [
    {
      "id": "cave-network",
      "kind": "CAVE_NETWORK",
      "geometry": {
        "type": "VOLUME_GUIDE",
        "footprint": {
          "type": "POLYGON",
          "rings": [[[0.18, 0.16], [0.82, 0.18], [0.88, 0.80], [0.14, 0.82], [0.18, 0.16]]]
        },
        "vertical": { "mode": "SURFACE_OFFSET", "min": -58, "max": -8 }
      },
      "parameters": {
        "tunnelRadiusBlocks": { "min": 3, "max": 8 },
        "chamberCount": { "min": 3, "max": 7 },
        "connectivity": "SINGLE_CONNECTED_COMPONENT"
      }
    },
    {
      "id": "west-entrance",
      "kind": "CAVE_ENTRANCE",
      "geometry": { "type": "POINT", "point": [0.20, 0.42] },
      "parameters": {
        "surfaceOffsetBlocks": -2,
        "minimumOpeningBlocks": 4,
        "approachLengthBlocks": 8,
        "roofClearanceBlocks": 3,
        "targetEntranceNodeId": "n.entrance"
      }
    },
    {
      "id": "east-entrance",
      "kind": "CAVE_ENTRANCE",
      "geometry": { "type": "POINT", "point": [0.79, 0.58] },
      "parameters": {
        "surfaceOffsetBlocks": -4,
        "minimumOpeningBlocks": 4,
        "approachLengthBlocks": 8,
        "roofClearanceBlocks": 3,
        "targetEntranceNodeId": "n.exit"
      }
    },
    {
      "id": "lush-chamber",
      "kind": "LUSH_CAVE",
      "geometry": {
        "type": "VOLUME_GUIDE",
        "footprint": {
          "type": "POLYGON",
          "rings": [[[0.38, 0.36], [0.64, 0.34], [0.67, 0.62], [0.36, 0.64], [0.38, 0.36]]]
        },
        "vertical": { "mode": "SURFACE_OFFSET", "min": -44, "max": -18 }
      },
      "parameters": {
        "chamberRadiusBlocks": { "min": 16, "max": 30 },
        "ceilingClearanceBlocks": { "min": 12, "max": 28 },
        "moisture01": { "min": 0.75, "max": 1.0 },
        "undergroundPoolShare01": { "min": 0.08, "max": 0.24 }
      }
    }
  ],
  "relations": [
    {
      "id": "west-entrance-of-network",
      "kind": "ENTRANCE_OF",
      "from": "feature:west-entrance",
      "to": "feature:cave-network",
      "strength": "HARD"
    },
    {
      "id": "east-entrance-of-network",
      "kind": "ENTRANCE_OF",
      "from": "feature:east-entrance",
      "to": "feature:cave-network",
      "strength": "HARD"
    },
    {
      "id": "lush-within-network",
      "kind": "WITHIN",
      "from": "feature:lush-chamber",
      "to": "feature:cave-network",
      "strength": "HARD"
    },
    {
      "id": "lush-connected-to-entrance",
      "kind": "REACHABLE_FROM",
      "from": "feature:lush-chamber",
      "to": "feature:west-entrance",
      "strength": "HARD"
    }
  ],
  "constraints": [
    {
      "id": "no-surface-breakthrough",
      "strength": "HARD",
      "kind": "MINIMUM_CLEARANCE",
      "subject": "feature:cave-network",
      "parameters": { "from": "CAVE_CEILING", "to": "SURFACE", "blocks": 5, "exceptDeclaredEntrances": true }
    },
    {
      "id": "network-connected",
      "strength": "HARD",
      "kind": "METRIC_RANGE",
      "subject": "feature:cave-network",
      "metric": "REACHABLE_VOLUME_SHARE_01",
      "range": { "min": 0.98, "max": 1.0 },
      "tolerance": 0.01
    }
  ],
  "environment": {
    "geologyPreset": "FRACTURED_LIMESTONE_LIKE",
    "climatePreset": "TEMPERATE_HUMID",
    "ecologyPreset": "LUSH_SUBTERRANEAN"
  },
  "mapReferences": [],
  "structures": []
}
```

### 8.10 Overhang

```json
{
  "intentVersion": 2,
  "intentId": "sea-cliff-overhang",
  "theme": "A supported coastal overhang above a wave-cut recess",
  "coordinateSystem": {
    "horizontal": "NORMALIZED_XZ",
    "origin": "NORTH_WEST",
    "xAxis": "EAST",
    "zAxis": "SOUTH",
    "vertical": "BLOCK_Y_OR_SURFACE_OFFSET"
  },
  "features": [
    {
      "id": "host-cliff",
      "kind": "SEA_CLIFF",
      "geometry": {
        "type": "SPLINE",
        "points": [[0.18, 0.52], [0.45, 0.46], [0.78, 0.55]],
        "interpolation": "CATMULL_ROM"
      },
      "parameters": { "heightBlocks": { "min": 42, "max": 76 }, "faceDirection": "SOUTH" }
    },
    {
      "id": "main-overhang",
      "kind": "OVERHANG",
      "geometry": {
        "type": "VOLUME_GUIDE",
        "footprint": {
          "type": "POLYGON",
          "rings": [[[0.34, 0.44], [0.65, 0.45], [0.68, 0.60], [0.31, 0.59], [0.34, 0.44]]]
        },
        "vertical": { "mode": "SURFACE_OFFSET", "min": -24, "max": 8 }
      },
      "parameters": {
        "projectionBlocks": { "min": 10, "max": 24 },
        "roofThicknessBlocks": { "min": 7, "max": 16 },
        "undersideClearanceBlocks": { "min": 12, "max": 28 },
        "supportFraction01": { "min": 0.30, "max": 0.55 }
      }
    }
  ],
  "relations": [
    {
      "id": "overhang-supported-by-cliff",
      "kind": "SUPPORTED_BY",
      "from": "feature:main-overhang",
      "to": "feature:host-cliff",
      "strength": "HARD"
    }
  ],
  "constraints": [
    {
      "id": "roof-thickness",
      "strength": "HARD",
      "kind": "METRIC_RANGE",
      "subject": "feature:main-overhang",
      "metric": "MINIMUM_SOLID_ROOF_THICKNESS_BLOCKS",
      "range": { "min": 7, "max": 64 },
      "tolerance": 0
    },
    {
      "id": "open-seaward-side",
      "strength": "HARD",
      "kind": "VISIBILITY_CORRIDOR",
      "subject": "feature:main-overhang",
      "parameters": { "direction": "SOUTH", "minimumOpenShare01": 0.8 }
    }
  ],
  "environment": {
    "geologyPreset": "HARD_COASTAL_ROCK",
    "climatePreset": "TEMPERATE_MARITIME",
    "ecologyPreset": "WIND_EXPOSED_CLIFF"
  },
  "mapReferences": [],
  "structures": []
}
```

### 8.11 Sky islands

```json
{
  "intentVersion": 2,
  "intentId": "sky-island-cluster",
  "theme": "A navigable cluster of floating islands with tapered rocky undersides",
  "coordinateSystem": {
    "horizontal": "NORMALIZED_XZ",
    "origin": "NORTH_WEST",
    "xAxis": "EAST",
    "zAxis": "SOUTH",
    "vertical": "BLOCK_Y_OR_SURFACE_OFFSET"
  },
  "features": [
    {
      "id": "sky-islands",
      "kind": "SKY_ISLAND_GROUP",
      "geometry": {
        "type": "VOLUME_GUIDE",
        "footprint": {
          "type": "POLYGON",
          "rings": [[[0.12, 0.12], [0.88, 0.10], [0.92, 0.84], [0.10, 0.86], [0.12, 0.12]]]
        },
        "vertical": { "mode": "ABSOLUTE_Y", "min": 145, "max": 235 }
      },
      "parameters": {
        "centers": [
          { "xz": [0.28, 0.34], "surfaceY": 190, "radiusBlocks": 34 },
          { "xz": [0.53, 0.46], "surfaceY": 218, "radiusBlocks": 52 },
          { "xz": [0.74, 0.30], "surfaceY": 178, "radiusBlocks": 28 },
          { "xz": [0.61, 0.70], "surfaceY": 166, "radiusBlocks": 22 }
        ],
        "undersideShape": "TAPERED_FRACTURED_CONE",
        "minimumThicknessBlocks": 10,
        "edgeFalloffBlocks": { "min": 8, "max": 20 }
      }
    }
  ],
  "relations": [],
  "constraints": [
    {
      "id": "island-count",
      "strength": "HARD",
      "kind": "METRIC_RANGE",
      "subject": "feature:sky-islands",
      "metric": "CONNECTED_SOLID_COMPONENT_COUNT",
      "range": { "min": 4, "max": 4 },
      "tolerance": 0
    },
    {
      "id": "ground-clearance",
      "strength": "HARD",
      "kind": "METRIC_RANGE",
      "subject": "feature:sky-islands",
      "metric": "MINIMUM_GROUND_CLEARANCE_BLOCKS",
      "range": { "min": 48, "max": 180 },
      "tolerance": 0
    },
    {
      "id": "traversable-spacing",
      "strength": "SOFT",
      "kind": "METRIC_RANGE",
      "subject": "feature:sky-islands",
      "metric": "NEAREST_SURFACE_GAP_BLOCKS_P90",
      "range": { "min": 12, "max": 44 },
      "weight": 0.7
    }
  ],
  "environment": {
    "geologyPreset": "FRACTURED_GRANITIC",
    "climatePreset": "COOL_HIGH_ALTITUDE",
    "ecologyPreset": "WIND_EXPOSED_SKY_MEADOW"
  },
  "mapReferences": [],
  "structures": []
}
```

## 9. 通常画像から地形を作る場合

通常写真は投影、遠近、遮蔽、未知縮尺があるため、1枚からhard height guideを直接生成しない。

```text
通常画像
→ 既存の安全な画像decode／metadata除去
→ AIによるfeature候補、semantic region、curve、相対depthの抽出
→ confidence付きTerrainIntent draftとsoft constraint map
→ previewと不確実領域をユーザー確認
→ canonical Intent／map artifactをfreeze
→ 以後はAI非依存でBlueprint compile
```

俯瞰図、標高図、label mapのように座標対応が明示された入力だけをhard map候補にする。元写真のpixelやAI hidden stateを再現性の入力にせず、確認済みcanonical artifactとchecksumを正本にする。

## 10. Compileと検証規則

`TerrainIntentValidatorV2` と `BlueprintCompilerV2` は少なくとも次を行う。

1. ID、kind、parameter、range、座標、ring、map referenceを構造検証する。
2. relationの型、未知参照、hard conflict、featureの最小実現寸法を検査する。
3. normalized geometryをblock座標へ量子化し、誤差を記録する。
4. softな自然語を残さずtyped metricへ確定する。
5. featureをbuilt-in moduleへ割り当て、未対応kindは明示的に失敗させる。
6. hard/soft constraintをversion付きValidationTargetへcompileする。
7. resource budget、halo、volume、palette、preview数を事前評価する。

AI応答はuntrusted inputである。重複key、unknown enum、過大配列、深すぎるJSON、巨大polygon、NaN／Infinity、範囲外座標、任意式、任意class名を拒否する。

## 11. v1との関係

### 流用する

- immutable record／enum、JDK-only model、Schemaとrecordの二重検証
- AI ProviderはIntentだけを返し、block listやコードを返さない境界
- seed、bounds、tile、structure安全制約の考え方
- unknown fieldを拒否するstrict parsingと入力上限

### 置換する

- `topology`、`seaSides`、全域relief、水系個数、coarse zoneを、feature geometryとcompiled fieldへ置換する。
- `preferredArea` と `areaShare` を、polygon／mask、metric target、transitionへ置換する。
- 大規模breakwater等をsmall structure countではなくregional featureとして扱う。

### 段階的に廃止する

- v1をv2として暗黙解釈する経路
- 自然語 `theme` だけに依存して細部を生成する経路
- `bayCount` 等、保存されるがgeneratorが消費しないdead intent
- 全artifactのSchema versionを1整数で同期させる前提

v1 Schemaとgeneratorは既存Release再現用に凍結する。v1→v2 upgradeは新しいIntentを生成し、失われていた形状情報をwarningとして提示する。元artifactを上書きしない。
