# 2026-07-18 地形Feature Gap監査

> Status: **履歴資料**（2026-07-18時点のinventory。当時は29 FeatureKind／Paper apply 0）。現行の全地形inventoryと公開経路計画は [terrain-catalog-full-audit-2026-07-22.md](terrain-catalog-full-audit-2026-07-22.md) を正本とする。調査対象は branch `fix/all`、HEAD `5d2df6c`、2026-07-18の未コミット作業ツリー。進捗の正本は [roadmap](../roadmap.md)、Task Scopeの正本は [Task Index](../design-v2/task-index.md) である。

## 1. 調査方法と数え方

- v1は `TerrainZoneType`、v2 public intentは `TerrainIntentV2.FeatureKind`、実行配線は `BuiltInLandformModuleCatalogV2.bindings()`、局所3Dは各 `*PlanCompilerV2.LIFECYCLE` と `VolumePhaseGateV2Test` を別々に数えた。
- enumに存在するだけ、generator classが存在するだけ、親Feature内でchild planとして読めるだけ、offline Releaseへ出せるだけ、の各状態を実装済みstandalone kindへ合算していない。
- 「taxonomyのみ」は、監査開始時のtaxonomy §3に明示された73地形名のうち、FeatureKind、専用generator、plan-level volume generatorのいずれもないものとした。profile、preset、componentはこの73件へ含めない。
- `WATERFALL`はsurface FeatureKindとして1件、`WATERFALL_VOLUME`は同Featureへ結合するoverlayとして別能力1件とした。post-volume local environmentはcomponentであり地形kind数へ含めない。

## 2. 実装事実

| 集計 | 件数 | 根拠 |
|---|---:|---|
| v1 Paper zone type | 8 | `model/TerrainZoneType.java` |
| v2 public `FeatureKind` | 29 | `model/v2/TerrainIntentV2.java` |
| 専用moduleへbinding済みstandalone surface kind | 16 | `generator/v2/BuiltInLandformModuleCatalogV2.java`、V2-2〜V2-4 gate tests |
| public enumはあるがdiagnostic-only | 13 | catalog default binding。内訳はchild-plan限定4、plan-level volumeと対応4、generatorなし5 |
| plan-level standalone volume | 7 | cave、lush cave、underground lake、sea cave、overhang、natural arch、sky-island group |
| volume overlay | 1 | `WaterfallVolumePlanCompilerV2` |
| post-volume component | 1 | `VolumeLocalEnvironmentPlanCompilerV2` |
| child-plan限定または親内限定 | 4 | lagoon、reef pass、volcanic caldera、lava flow field |
| taxonomyに名前だけありenumもgeneratorもない地形 | 41 | 下記Gap Matrixの`DOC` profile |
| v2 Paper apply可能なkind | 0 | V2-6は`V2-6-05`まで。applyは`V2-6-06`以降 |

専用surface kind 16件は `SANDY_BEACH`、`BREAKWATER_HARBOR`、`HARBOR_BASIN`、`ROCKY_CAPE`、`MEANDERING_RIVER`、`LAKE`、`CANYON`、`WATERFALL`、`DELTA`、`TIDAL_CHANNEL_NETWORK`、`FJORD`、`ALPINE_MOUNTAIN_RANGE`、`GLACIAL_MOUNTAIN_RANGE`、`VOLCANIC_ARCHIPELAGO`、`MANGROVE_WETLAND`、`CORAL_REEF` である。一般`RIVER`は`RiverVariant`として内部表現されるがpublic FeatureKindではなく、`BACKSHORE_PLAINS`はenumにあるが専用moduleへbindingされない。

## 3. Capability profile

状態は `S=SUPPORTED`、`P=PARTIAL`、`E=EXPERIMENTAL`、`U=UNSUPPORTED`、`N=NOT_APPLICABLE` とする。以下のprofileはGap Matrixの要求列を展開したものなので、各Name行はprofileとの結合で全能力を持つ。

| Profile | FeatureKind exists | Intent compiler | Generator | Validation | Preview | Export | Standalone usage | Child-plan usage | Volume-overlay usage | Paper apply | Snapshot | Rollback | Restart recovery | Tests |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `SF16` | yes | S | S | S | S | S | S | N | N | U | U | U | U | feature unit＋V2-2/3/4/5 gate |
| `CP4` | yes | P（親compilerのみ） | P（親generator内） | P（親metric） | P（親layer） | P（親artifact内） | U | P | N | U | U | U | U | coral/volcanic unit＋V2-4 gate |
| `VE4` | yes | E（diagnostic-only） | S（plan-level） | S | S | S | P（public Intentなし） | feature依存 | N | U | U | U | U | volume unit＋V2-5 gate |
| `VP3` | no | U（plan direct） | S | S | S | S | P（public Intentなし） | feature依存 | N | U | U | U | U | volume unit＋V2-5 gate |
| `OVL1` | no | N（surface fallへbinding） | S | S | S | S | N | N | S | U | U | U | U | waterfall volume＋V2-5 gate |
| `COMP1` | no | N | S | S | S | S | N | N | N | U | U | U | U | local environment＋V2-5 gate |
| `ENUM5` | yes | E（diagnostic-only） | U | U | U | U | U | U | U | U | U | U | U | schema/diagnostic round-tripのみ |
| `DOC` | no | U | U | U | U | U | U | U | U | U | U | U | U | なし |

`SF16`の`Paper apply`以降がすべて`U`なのは、snapshot基盤単体の完成をFeatureの配置能力へ昇格させないためである。V2-6 gateまで個別Featureの`paper_apply: SUPPORTED`を禁止する。

## 4. Gap Matrix

### 4.1 実装またはenumが存在する地形

| Name | Current classification | Recommended classification | Profile | Current task reference | Required action | Priority | Dependencies |
|---|---|---|---|---|---|---|---|
| `SANDY_BEACH` | standalone surface | `STANDALONE_FEATURE` | SF16 | V2-2-03/12 | capability別表示へ移行 | current | V2-6-18/19 |
| `BREAKWATER_HARBOR` | standalone surface | `STANDALONE_FEATURE` | SF16 | V2-2-05/12 | 同上 | current | V2-6-18/19 |
| `HARBOR_BASIN` | standalone surface | `STANDALONE_FEATURE` | SF16 | V2-2-04/12 | 同上 | current | V2-6-18/19 |
| `ROCKY_CAPE` | standalone surface | `STANDALONE_FEATURE` | SF16 | V2-2-06/12 | `ROCKY_COAST`親との互換transitionを後続定義 | P0 | V2-9-06 |
| `MEANDERING_RIVER` | specialized river | `STANDALONE_FEATURE`（general `RIVER` subtype） | SF16 | V2-3-03/15 | ID/seed/checksumを維持し一般graphへ段階接続 | P0 | V2-9-04/12 |
| `LAKE` | standalone surface | `STANDALONE_FEATURE` | SF16 | V2-3-04/15 | profile/provenanceで派生湖を表現 | P1 | V2-10-05 |
| `CANYON` | standalone surface | `STANDALONE_FEATURE` | SF16 | V2-3-05/15 | submarine canyonとownershipを分離 | current | V2-9-09 |
| `WATERFALL` | surface kind＋volume completion | `STANDALONE_FEATURE` | SF16 | V2-3-06, V2-5-13/18 | overlay配置順をV2-6へ統合 | current | V2-6-06/07 |
| `DELTA` | standalone surface | `STANDALONE_FEATURE` | SF16 | V2-3-07/15 | sediment childとestuary拡張をgraphへ接続 | P1 | V2-9-13, V2-10-05 |
| `TIDAL_CHANNEL_NETWORK` | standalone graph feature | `STANDALONE_FEATURE` | SF16 | V2-3-08/15 | macro coast topologyとの境界を明示 | P1 | V2-9-12 |
| `FJORD` | standalone surface | `STANDALONE_FEATURE` | SF16 | V2-3-09/15 | `VALLEY`/glacial presetへ互換接続 | P0 | V2-9-03, V2-10-01 |
| `ALPINE_MOUNTAIN_RANGE` | specialized mountain | `STANDALONE_FEATURE` | SF16 | V2-3-10, V2-4-15 | general range component contractを非破壊で再利用 | P0 | V2-9-03 |
| `GLACIAL_MOUNTAIN_RANGE` | specialized mountain | `STANDALONE_FEATURE` | SF16 | V2-3-10, V2-4-15 | 同上 | P0 | V2-9-03 |
| `VOLCANIC_ARCHIPELAGO` | composite regional kind | `STANDALONE_FEATURE`＋child plans | SF16 | V2-3-11, V2-4-15 | general island/cone contractへ段階接続 | P0 | V2-9-07 |
| `MANGROVE_WETLAND` | composite regional kind | `STANDALONE_FEATURE`＋environment profile | SF16 | V2-4-09/15 | `MARSH`基盤とecologyを分離して互換維持 | P0 | V2-9-05 |
| `CORAL_REEF` | composite regional kind | `STANDALONE_FEATURE`＋child plans/profile | SF16 | V2-4-10/15 | child限定状態をcatalogへ明示 | current | V2-6-18 |
| `LAGOON` | enum、reef child hook | `CHILD_PLAN_ONLY` | CP4 | V2-4-10/15 | standaloneへ昇格しない | current | V2-6-18 |
| `REEF_PASS` | enum、reef child hook | `CHILD_PLAN_ONLY` | CP4 | V2-4-10/15 | graph edge/child contractとして固定 | current | V2-6-18 |
| `VOLCANIC_CALDERA` | enum、volcanic child hook | `CHILD_PLAN_ONLY` | CP4 | V2-3-11, V2-4-15 | standalone contractは別Taskまで禁止 | current | V2-6-18 |
| `LAVA_FLOW_FIELD` | enum、volcanic child hook | `CHILD_PLAN_ONLY`＋`MODIFIER` | CP4 | V2-3-11, V2-4-15 | lava tube/lobeを別概念に維持 | current | V2-6-18, V2-10-07 |
| `CAVE_NETWORK` | enum diagnostic＋plan generator | `STANDALONE_FEATURE`（plan-level） | VE4 | V2-5-06/18 | public Intentとsurface entranceはfollow-up | P0 | V2-9-10 |
| `LUSH_CAVE` | enum diagnostic＋plan generator | `STANDALONE_FEATURE`＋environment profile | VE4 | V2-5-07/18 | public Intentは別version境界で判断 | P1 | V2-9-10 |
| `OVERHANG` | enum diagnostic＋plan generator | `STANDALONE_FEATURE`（plan-level） | VE4 | V2-5-10/18 | Paper gravity containmentを共通配置で検証 | current | V2-6-05〜10 |
| `SKY_ISLAND_GROUP` | enum diagnostic＋plan generator | `STANDALONE_FEATURE`（plan-level） | VE4 | V2-5-12/18 | single islandと混同せずPaper共通配置へ | current | V2-6-06〜10 |
| `UNDERGROUND_LAKE` | plan generatorのみ | `STANDALONE_FEATURE`（plan-level） | VP3 | V2-5-08/18 | cave/river fluid graph接続をfollow-up | P0 | V2-9-11 |
| `SEA_CAVE` | plan generatorのみ | `STANDALONE_FEATURE`（plan-level） | VP3 | V2-5-09/18 | rocky coast/sea cliff host contractを追加 | P0 | V2-9-06/09 |
| `NATURAL_ARCH` | plan generatorのみ | `STANDALONE_FEATURE`（plan-level） | VP3 | V2-5-11/18 | public Intentは別version境界で判断 | P1 | V2-9 gate後 |
| `WATERFALL_VOLUME` | plan overlay | `VOLUME_OVERLAY` | OVL1 | V2-5-13/18 | child/overlay placement順をV2-6で固定 | current | V2-6-06/07/10 |
| post-volume local environment | plan component | `COMPONENT` | COMP1 | V2-5-14/18 | FeatureKindへ追加しない | current | V2-6-12/18 |
| `BACKSHORE_PLAINS` | enum diagnostic only | `STANDALONE_FEATURE`候補 | ENUM5 | V2-0 diagnostic | `PLAIN`とのalias/migrationを設計してから判断 | P0 | V2-9-02 |
| `BEDROCK_RIVER` | enum diagnostic only | river profile/reach class候補 | ENUM5 | V2-0 diagnostic | 一般river graphで評価、直ちにkind化しない | P1 | V2-9-13 |
| `GLACIAL_CIRQUE_FIELD` | enum diagnostic only | child field/modifier候補 | ENUM5 | V2-0 diagnostic | glacial foundationで再分類 | P2 | V2-10-01 |
| `CAVE_ENTRANCE` | enum diagnostic only | `STANDALONE_FEATURE`（connector） | ENUM5 | V2-0 diagnostic | surface-volume vertical slice | P0 | V2-9-09 |
| `SEA_CLIFF` | enum diagnostic only | `STANDALONE_FEATURE` | ENUM5 | V2-0 diagnostic | rocky coast/volume host vertical slice | P0 | V2-9-06 |

### 4.2 Taxonomyのみ、または新たに分類すべき概念

全行のcapability profileは`DOC`である。`Current classification`がtaxonomy未定義の場合も明示した。

| Name | Current classification | Recommended classification | Current task reference | Required action | Priority | Dependencies |
|---|---|---|---|---|---|---|
| `PLAIN` | taxonomy only、v1 zoneあり | `STANDALONE_FEATURE` | none | v2 foundation vertical slice | P0 | V2-9-01/02 |
| `HILL_RANGE` | taxonomy only | `STANDALONE_FEATURE` | none | plain/range transition付きvertical slice | P0 | V2-9-02 |
| `MOUNTAIN_RANGE` | taxonomy only | `STANDALONE_FEATURE` | none | reusable ridge component contract | P0 | V2-9-03 |
| `VALLEY` | taxonomy only、v1 zoneあり | `STANDALONE_FEATURE` | none | river/fjord/volume connector | P0 | V2-9-03 |
| `RIVER` | variantのみ | `STANDALONE_FEATURE` | V2-3-03 | general graph contract、legacy meander保持 | P0 | V2-9-04 |
| `FLOODPLAIN` | generated field only | `STANDALONE_FEATURE`候補 | V2-3-03 | ownership可能なsliceとして評価 | P0 | V2-9-05 |
| `MARSH` | taxonomy only、v1 wetlandあり | `STANDALONE_FEATURE` | none | groundwater/hydroperiod foundation | P0 | V2-9-05 |
| `ROCKY_COAST` | taxonomy only、v1 zoneあり | `STANDALONE_FEATURE` | none | rocky cape/sea cave親contract | P0 | V2-9-06 |
| `SINGLE_ISLAND` | taxonomy only | `STANDALONE_FEATURE` | none | island ownership/shore transition | P0 | V2-9-07 |
| `ARCHIPELAGO` | taxonomy only | `STANDALONE_FEATURE` | none | non-volcanic group contract | P0 | V2-9-07 |
| `VOLCANIC_CONE` | taxonomy only | `STANDALONE_FEATURE` | none | volcanic child/common core | P0 | V2-9-07 |
| `UNDERGROUND_RIVER` | taxonomy未定義 | `STANDALONE_FEATURE`（volume/fluid） | none | cave/lake connector vertical slice | P0 | V2-9-11 |
| `OCEAN_BASIN` | taxonomy未定義 | `STANDALONE_FEATURE`＋macro host | none | bathymetry depth-field core | P0 | V2-9-08 |
| `CONTINENTAL_SHELF` | taxonomy未定義 | `STANDALONE_FEATURE` | none | coast/basin transition | P0 | V2-9-08 |
| `CONTINENTAL_SLOPE` | taxonomy未定義 | `STANDALONE_FEATURE` | none | shelf/basin transition | P0 | V2-9-08 |
| `SUBMARINE_CANYON` | taxonomy未定義 | `STANDALONE_FEATURE` | none | shelf/slope carve vertical slice | P1 | V2-9-09 |
| `BAY` / `COVE` / `HEADLAND` / `PENINSULA` | taxonomy未定義 | `MACRO_CONSTRAINT` | none | land-water topology primitive | P0 | V2-9-12 |
| `ISTHMUS` / `STRAIT` / `ENCLOSED_BASIN` / `COASTAL_EMBAYMENT` | taxonomy未定義 | `MACRO_CONSTRAINT` | none | connectivity/width/containment contract | P0 | V2-9-12 |
| `STREAM` | taxonomy未定義 | reach class | none | river graph enum/catalog、FeatureKind禁止 | P1 | V2-9-13 |
| `HEADWATER` / `CONFLUENCE` | taxonomy未定義 | graph node roles | none | source/junction contract | P1 | V2-9-13 |
| `TRIBUTARY` / `DISTRIBUTARY` | taxonomy未定義 | graph edge relations | none | merge/split contract | P1 | V2-9-13 |
| `RAPIDS` | taxonomy未定義 | `MODIFIER` | none | reach modifier | P1 | V2-9-13 |
| `PLUNGE_POOL` | existing waterfall detail | `CHILD_PLAN_ONLY` | V2-3-06/V2-5-13 | child plan、FeatureKind禁止 | current | V2-6-18 |
| `SANDBAR` / `RIVER_ISLAND` | existing descriptor/taxonomy gap | `CHILD_PLAN_ONLY` | V2-3-07 | sediment/channel child contract | P1 | V2-9-13 |
| `RIDGE` / `PEAK` / `SADDLE` / `SPUR` | generated mountain fields | `COMPONENT` | V2-3-10 | common range component catalog | P0 | V2-9-03 |
| `MOUNTAIN_PASS` / `FOOTHILLS` / `TALUS_SLOPE` / `SCREE` | taxonomy detail | `COMPONENT`/`MODIFIER` | none | parent-owned transition/material components | P1 | V2-9-03 |
| `GRAVEL_BEACH` / `BLACK_SAND_BEACH` / `TIDAL_POOL_COAST` / `TIDAL_FLAT` | taxonomy only | standalone/profile candidates | none | coast foundation後に個別評価 | P2 | V2-9-06 gate |
| `SEA_STACK_FIELD` / `WAVE_CUT_PLATFORM` | taxonomy only/detail | child/volume component | none | rocky coast/sea cliff child contract | P2 | V2-9-06 gate |
| `PLATEAU` / `ESCARPMENT` | V2-10-06 EXPERIMENTAL slice | cap/scarp ownership、HARD OVERLAPS transition | `EscarpmentPlanV2`／`PlateauPlanV2` | catalog未登録 | P2 | 完了 |
| `MESA` / `BUTTE` | V2-10-06 profile only | `PlateauProfile` on `PLATEAU` | none | FeatureKind化なし | P2 | 完了 |
| `GORGE` / `RAVINE` / `RIFT_VALLEY` | taxonomy only | valley/river profiles候補 | none | general valley後に評価 | P2 | V2-9-03 gate |
| `SPRING` | V2-10-05 first slice | graph source node（≠`KARST_SPRING`） | none yet | implement on `V2-10-10` | P1 | V2-10-10 |
| `OXBOW_LAKE` | V2-10-05 first slice | reach cutoff + lake basin | none yet | implement on `V2-10-11` | P1 | V2-10-11 |
| `BRAIDED_RIVER` / `DAM_RESERVOIR` | V2-10-05 deferred | multi-channel DAG / barrier+basin | none | high-risk mix禁止、later Tasks | P2 | post-V2-10-11 |
| `ESTUARY` / `ALLUVIAL_FAN` / `RIVER_TERRACE` | V2-10-05 deferred | composition / sediment fan / reach profile | none | ownership成立後の個別Task | P2 | post-V2-10-11 |
| `POND` / `CRATER_LAKE` / `GLACIAL_LAKE` / `KETTLE_LAKE` / `EPHEMERAL_LAKE` | taxonomy/preset gap | `LAKE` profile/provenance/preset | none | FeatureKind禁止、LAKE contract拡張 | P2 | V2-10-05 non-scope |
| `BARRIER_ISLAND` / `ATOLL` | COMPOSITE_PRESET（V2-10-08 closed） | `BarrierIslandPlanV2`／`AtollPlanV2` composition | none | catalog候補5種分類、`FLOATING_REEF` deferred | P2 | 完了 |
| `ABYSSAL_PLAIN` / `SEAMOUNT` | V2-10-04 EXPERIMENTAL slice | basin `WITHIN` bind、depth/relief/slope metrics | `AbyssalPlainPlanV2`／`SeamountPlanV2` | catalog未登録 | P1 | 完了 |
| `OCEAN_TRENCH` / `MID_OCEAN_RIDGE` / `SUBMARINE_VOLCANO` | catalog評価のみ（V2-10-04） | plate-scale／ownership deferred | none | FeatureKind化・generatorなし | P2 | V2-10-04 catalog eval |
| `FLOODED_CAVE` / `LAVA_TUBE` | taxonomy gap/only → `LAVA_TUBE` slice | standalone volume candidates | `LavaTubePlanV2` EXPERIMENTAL | fluid/volcanic volume slices | P1 | V2-9-11, V2-10-07 done |
| `VERTICAL_SHAFT` / `CAVE_CHAMBER` / `SUMP` | taxonomy未定義 | graph node/component | none | cave graph内部、FeatureKind禁止 | P1 | V2-9-10 |
| `VALLEY_GLACIER` / `ICE_CAP` / `ICE_SHEET` | taxonomy未定義 | standalone volume/surface candidates | none | glacial foundation | P2 | V2-10-01 |
| `MORAINE_FIELD` / `OUTWASH_PLAIN` / `PERMAFROST_PLAIN` | taxonomy未定義 | standalone/component candidates | none | deposition/environmentを分離 | P2 | V2-10-01/02 |
| `SINKHOLE` / `KARST_CAVE_SYSTEM` / `KARST_SPRING` / `CENOTE` | taxonomy partial | karst graph feature/node candidates | none | underground drainage graph中心 | P2 | V2-10-03 |
| `DOLINE_FIELD` / `POLJE` / `LIMESTONE_PAVEMENT` / `KARST_PEAK_FIELD` | taxonomy partial | later karst candidates | none | initial karst waveへ含めない | P3 | V2-10 gate後 |
| `COASTAL_DUNE_FIELD` / `INLAND_DUNE_FIELD` / `DESERT_FLAT` / `BADLANDS` / `DRY_CANYON` / `SALT_FLAT` | V2-10-06 modifier contract | `DryLandModifierContractV2` only | none | FeatureKind／generatorなし | P2 | 完了 |
| `BOG` / `FEN` / `SALT_MARSH` / `SAVANNA_PLAIN` | taxonomy only | profileまたはstandalone候補 | none | MARSH/PLAIN contract後に判定 | P2 | V2-9-05 gate |
| `FOREST` / `CHERRY_GROVE` / `MUSHROOM_BIOME` / `GRASSLAND` / `SWAMP` / `SAVANNA` | taxonomy/theme | `ENVIRONMENT_PROFILE` | V2-4 ecology | FeatureKindへ追加しない | P1 | profile task only |
| `LUMINOUS_CAVE` / `CRYSTAL_CAVE` / `SNOW_MOUNTAINS` | taxonomy/theme | `ENVIRONMENT_PROFILE`/preset | V2-4/5 | FeatureKindへ追加しない | P1 | profile task only |
| `CORAL_COAST` / `MANGROVE_COAST` / `VOLCANIC_COAST` / `ICE_FJORD` | taxonomy/theme | `COMPOSITE_PRESET` | none | signed/checksummed presetのみ | P2 | foundation kinds |
| `DUNE_BACKED_BEACH` / `OASIS` / `WATERFALL_CHAIN` / `SNOWY_MOUNTAIN_LAKE` / `FLOATING_REEF` | taxonomy/theme | `COMPOSITE_PRESET` | none | 専用generator禁止 | P2 | foundation kinds |

## 5. 矛盾と再利用資産

### 矛盾

1. taxonomy冒頭はV2-5 plan-level volumeをoffline `SUPPORTED`とする一方、§3.7は各Task完了時の`EXPERIMENTAL`表記を残していた。
2. `lifecycleStatus` 1列がoffline generator、public Intent、standalone/child、Paper applyを同時に表しており、`CAVE_NETWORK`のplan-level `SUPPORTED`とpublic enum diagnostic-only `EXPERIMENTAL`を表現できない。
3. taxonomyは`VOLCANIC_CALDERA`／`LAVA_FLOW_FIELD`を未対応と記載するが、親`VOLCANIC_ARCHIPELAGO` compilerはtyped child hookをcompileしgenerator testも持つ。standalone未対応とchild-plan限定実装が混在していた。
4. `CORAL_REEF`も`LAGOON`と`REEF_PASS`を必須/任意childとしてcompileするが、enum lifecycleだけでは限定利用を示せない。
5. V2-6 snapshot基盤は完了しているがapply未実装であり、Featureごとのsnapshot/rollback/recoveryをsupportedと主張できない。

### 再利用資産

- `NamedSeedDeriverV2`、module/stage/field ownership、fixed-order reconciliation、`TerrainQuery`/`TerrainBlockResolver`。
- V2-3のglobal Hydrology graphとV2-5のordered sparse volume CSG/AABB/query。
- V2-2〜V2-5のvalidator、diagnostic preview、strict Release 2 capability、whole/tile/thread/locale/timezone test portfolio。
- V2-6のplacement plan、effect envelope、reservation/confirmation、snapshot-all、containment preflight。これらをFeature別adapterへ分岐させず共通surface/solid/air/fluid streamへ適用する。

## 6. 調査時検証

- `./gradlew build -x test`: SUCCESS（変更前、11 tasks up-to-date）。
- docs validation（Markdown相対リンク、Task ID重複、V2-9/10依存cycle、29 Java/Schema FeatureKind一致、support必須13項目、Paper誤昇格、V2-6優先依存）: SUCCESS。117 Task heading、29 FeatureKind、13 support keyを確認した。
- `./gradlew test --tests ...SchemaContractTest --tests ...DiagnosticBlueprintCompilerV2Test --tests ...AzureCoastPhaseGateV2Test --tests ...HydrologyPhaseGateV2Test --tests ...EnvironmentPhaseGateV2Test --tests ...VolumePhaseGateV2Test`: SUCCESS（27秒）。
- `./gradlew test`: SUCCESS（2分34秒）。
- `./gradlew build`: SUCCESS（13 tasks up-to-date）。
- `git diff --check`: SUCCESS。
