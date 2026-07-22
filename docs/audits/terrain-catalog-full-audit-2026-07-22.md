# 全地形カタログ・公開経路監査（2026-07-22）

> Status: `CURRENT_INVENTORY` / `V2-15-04 COMPLETE` — inventory と実装計画を確定し、2026-07-22に`V2-14-03`／`V2-15`／`V2-16`／`V2-17`を登録した。`V2-15-01`〜`03`の監査／registry／ADRを経て、`V2-15-04`でcanonical target Schema／model／migrationを実装した。generator／Release／Paper capabilityは未接続で、次は`V2-15-05`である。

## 1. 結論

ユーザー提示の「既存70＋新規5」は現在の実装を表さない。production の事実は次のとおりである。

- `TerrainIntentV2.FeatureKind` と `terrain-intent-v2.schema.json` は一致して **60値**を公開している。
- sealed `FeatureSupportCatalogV2` は FeatureKind 60件に、internal／preset／macro 11件を加えた **71 entry**を持つ。
- production CLI／Paper の `generate`／`export` は `Release2ExportApplicationServiceV2` から `CoastalSurfaceExportPipelineV2` だけを呼ぶ。したがって、公開入力→生成→validation→preview→Release→CLI／Paper→Paper placement capability まで接続済みなのは **4種**である。
- offline standalone G/V/P/X が全列 `SUPPORTED` の既存kindは **16種**である。ただし4種以外はproduction export applicationへ未配線であり、本監査の「完全接続済み」には数えない。
- V2-9の18 kindとV2-10の14 kindは、plan-level generator／validator／previewを持つ。これらを「generator未実装」と数えるのは誤りである。欠けているのは主としてdedicated public dispatch、production Release export、CLI／Paperである。
- plan-level volume 7種のうち `UNDERGROUND_LAKE`／`SEA_CAVE`／`NATURAL_ARCH` は独立利用価値がありpublic候補、`WATERFALL_VOLUME`は `WATERFALL` checksum-bound overlayのままとする。
- target案は **canonical public independent FeatureKind 57種**、internal child／overlay 8種、composite preset 7種、named subtype／modifier disposition 33種である。既存Schema値の統廃合を伴うため、57種案の採用はADR amendmentと人間承認を要する。

## 2. 調査基線と証拠

作業開始時の`git status --short`はclean、HEADは`23cc424`だった。次をproduction evidenceとした。

| 面 | 根拠 |
|---|---|
| public enum／typed model | `model.v2.TerrainIntentV2.FeatureKind`（60値） |
| public Schema／codec | `schemas/terrain-intent-v2.schema.json`、`format.v2.LandformV2DataCodec` |
| execution module registry | `generator.v2.BuiltInLandformModuleCatalogV2`（専用binding 16、残りdiagnostic） |
| capability source | `model.v2.catalog.BuiltInFeatureSupportCatalogV2`、sealed example 71 entry |
| consistency CI | `core.v2.catalog.FeatureSupportCatalogConsistencyVerifierV2`、`FeatureSupportCatalogV2Test` |
| V2-9 plan実装 | `FoundationPhaseGateV2Test`、`v2-9-phase-gate.md` |
| V2-10 plan実装 | `DeferredTerrainPhaseGateV2Test`、`v2-10-phase-gate.md` |
| volume | `VolumePhaseGateV2Test`、`model.v2.volume`／`core.v2.volume`／`generator.v2.volume` |
| production export | `Release2ExportApplicationServiceV2` → `CoastalSurfaceExportPipelineV2` |
| CLI／Paper | `LandformCraftCli`、`V2WorkflowServiceV2`、`PaperV2WorkflowServiceV2`、`LandformCraftCommand` |
| Release | `ReleaseSurface/Hydrology/Environment/SparseVolume*PublisherV2`と各strict verifier |
| Paper capability | `BuiltInFeatureSupportCatalogV2`、`PlacementPhaseGateV2Test` |

### 2.1 現行profile集計

| Profile | 件数 | 実態 |
|---|---:|---|
| `SF16` | 16 | dedicated module、offline G/V/P/X `SUPPORTED`。Paper `SUPPORTED`はcoastal 4だけ |
| `FND9` | 18 | V2-9 plan-level G/V/P `SUPPORTED`、intent/standalone/export `PARTIAL` |
| `FND10` | 14 | V2-10 plan-level。deposition 2種のpreviewは`EXPERIMENTAL` |
| `VE4` | 4 | public diagnostic enum＋plan-level volume G/V/P/X |
| `VP3` | 3 | enumなしのplan-level standalone volume |
| `ENUM5` | 4 | enum／Schemaのみ、generatorなし |
| `CP4` | 4 | child-plan限定 |
| `PRESET` | 5 | plan-level composition、専用generatorなし |
| `MACRO9`／`OVL1`／`COMP1` | 各1 | macro constraint／volume overlay／component |

### 2.2 V2-15-01 現行HEAD再照合

`V2-14-03`完了後の2026-07-22に、production HEAD `23cc424`と保護対象の未コミットdocs差分を分けて再照合した。production source／Schema／exampleに作業ツリー差分はなく、`V2-14-03`のREADME修正、Task登録文書、本監査ファイルは既存変更として保持した。

| 主張 | 現行根拠 | 再照合結果 |
|---|---|---|
| public kind | `TerrainIntentV2.FeatureKind`、`terrain-intent-v2.schema.json`、`SchemaContractTest` | Java 60値＝Schema 60値、集合差0 |
| support catalog | `BuiltInFeatureSupportCatalogV2`、sealed `feature-support-catalog-v2.json`、`FeatureSupportCatalogV2Test` | 71 entry＝public 60＋synthetic 11。profileはSF16=16、FND9=18、FND10=14、VE4=4、VP3=3、ENUM5=4、CP4=4、PRESET=5、MACRO9／OVL1／COMP1=各1 |
| module binding | `BuiltInLandformModuleCatalogV2.bindings()` | dedicated binding 16、残りは明示diagnostic binding |
| production export | `Release2ExportApplicationServiceV2`→`CoastalSurfaceExportPipelineV2`、`CoastalFeaturePlanV2.isFoundationKind` | production pathはcoastal 4種だけ。Paper 5能力列の`SUPPORTED` entryも同じ4種 |
| plan-level実装 | `FoundationPhaseGateV2Test`、`DeferredTerrainPhaseGateV2Test`、`VolumePhaseGateV2Test` | V2-9／V2-10／volumeのplan-level成果は維持。production public exportへ配線済みとは数えない |
| 排他的4区分 | §4 | 4／45／8／48の件数と重複なしを再確認。区分2の45件中、現行Schema外のpublic候補3件を明示維持 |
| target案 | §6 | 現行60からalias／subtype／child候補14を除き、volume public候補3＋新規独立8を加えるため`60 - 14 + 11 = 57`。採用・Schema変更は未実施 |
| Task graph | Task Index、model assignment、roadmap | V2-15=47、V2-16=19、V2-17=7でID集合一致。`V2-15-01`〜`04`をcloseし、次は`V2-15-05` |

分類結果は、production接続済み／plan-levelまたは未接続／未定義新規／alias・subtype・child・preset・modifierを混同しない。新しい矛盾、false promotion、identifier変更、capability変更は検出しなかった。

## 3. 停止させた文書矛盾

次は単なるユーザー前提との差ではなく、repository内のproduction codeと対外説明の矛盾である。

1. `README.md`はV2-7について「抽出coreのみで入力封筒・draft保存・昇格は未実装」と記載するが、`V2-14-01`完了後のCLIには`extract`／`promote`／`request constraint-map`連携が実装され、E2E testも存在する。
2. `README.md`はreference image roleを旧v1風の6種（`HEIGHT_REFERENCE`／`ZONE_REFERENCE`含む）として列挙するが、`GenerationRequestV2.ReferenceImageRole`と`generation-request-v2.schema.json`は`MOOD_REFERENCE`／`TOP_DOWN_SKETCH`／`MATERIAL_REFERENCE`／`STRUCTURE_REFERENCE`／`OBLIQUE_TERRAIN_REFERENCE`／`MULTI_VIEW_REFERENCE`の**別の6種**である。HEIGHT／ZONEはv2 Requestに無く、決定論的height／zoneはconstraint map経路を使う。（登録時に「8種」と誤記していた箇所は、production 6種へamendした。）
3. 2026-07-18の`terrain-feature-gap-audit-2026-07-18.md`は当時の29 FeatureKind／Paper apply 0を記録する歴史資料であり、現在のinventory正本として読むと60 FeatureKind／Paper 4と矛盾する。文書自体は履歴として維持し、本監査をcurrent inventory linkへ昇格すべきである。

このため本監査Task自体では矛盾を勝手に直さず、docs-only `V2-14-03`を先行Taskとして正式登録した。`V2-14-03`は2026-07-22に完了し、以下のinventory根拠は`V2-15-01`で現行HEADへ再照合する。

## 4. 排他的な4区分

ここで「公開接続済み」は、Schema parseだけでなくproduction CLI／PaperからRelease strict read-backまで到達することを要求する。各名称は概念上の区分へ一度だけ置く。既存互換値が区分4でも、現行Schemaから即削除する意味ではない。

### 4.1 区分1 — 定義・generator・公開経路が接続済み（4）

`SANDY_BEACH`、`BREAKWATER_HARBOR`、`HARBOR_BASIN`、`ROCKY_CAPE`

4種だけが`surface-2_5d` production exportとPaper capabilityへ接続済みである。Paper上限はFAWE 2.15.2で1000×1000、WorldEdit 7.3.19単独で64×64。1024実測をこの監査からcatalogへ昇格しない。

### 4.2 区分2 — 定義またはgeneratorはあるが公開経路が未接続（45）

| Family | Names | 現在の主な欠落 |
|---|---|---|
| existing hydrology／environment | `LAKE`, `CANYON`, `WATERFALL`, `DELTA`, `TIDAL_CHANNEL_NETWORK`, `FJORD`, `CORAL_REEF` | production export dispatch／CLI。Paperは`EXPERIMENTAL` |
| existing volume | `CAVE_NETWORK`, `LUSH_CAVE`, `OVERHANG`, `SKY_ISLAND_GROUP` | public intent dispatch／production sparse-volume export |
| V2-9 surface | `PLAIN`, `HILL_RANGE`, `MOUNTAIN_RANGE`, `VALLEY`, `FLOODPLAIN`, `MARSH`, `RIVER` | dedicated module登録／Release capability／CLI |
| V2-9 coast/island | `ROCKY_COAST`, `SEA_CLIFF`, `SINGLE_ISLAND`, `ARCHIPELAGO`, `VOLCANIC_CONE` | 同上 |
| V2-9 marine | `OCEAN_BASIN`, `CONTINENTAL_SHELF`, `CONTINENTAL_SLOPE`, `SUBMARINE_CANYON` | 同上 |
| V2-9 connection | `CAVE_ENTRANCE`, `UNDERGROUND_RIVER` | public composition／Release export |
| V2-10 glacial | `VALLEY_GLACIER`, `ICE_CAP`, `ICE_SHEET`, `MORAINE_FIELD`, `OUTWASH_PLAIN` | public dispatch／Release。deposition preview index |
| V2-10 karst/marine/dry/volcanic | `SINKHOLE`, `KARST_SPRING`, `ABYSSAL_PLAIN`, `SEAMOUNT`, `ESCARPMENT`, `PLATEAU`, `LAVA_TUBE`, `SPRING` | public dispatch／Release／CLI |
| plan API only, public昇格候補 | `UNDERGROUND_LAKE`, `SEA_CAVE`, `NATURAL_ARCH` | FeatureKind／Schema／production dispatch |

### 4.3 区分3 — 定義がなく独立実装が必要（8）

`OCEAN_TRENCH`、`MID_OCEAN_RIDGE`、`DAM_RESERVOIR`、`ALLUVIAL_FAN`、`DUNE_FIELD`、`BADLANDS`、`TOWER_KARST`、`SALT_FLAT`

- `OCEAN_TRENCH`と`MID_OCEAN_RIDGE`はplate-scale continuityを持つ独立bathymetry ownershipで、既存`SUBMARINE_CANYON`／`SEAMOUNT`では代替できない。
- `DAM_RESERVOIR`はbarrier、spillway、reservoir levelの共同ownershipを持ち、単なる`LAKE` profileではない。
- singular `ALLUVIAL_FAN`はfan apex／sediment surface／drainage transitionを所有する。扇状地群はこのkindの複数配置であり別kindにしない。
- `DUNE_FIELD`、`BADLANDS`、`TOWER_KARST`、`SALT_FLAT`は現行modifier contractだけでは要求された形態不変条件を生成・検証できないため独立kind候補とする。
- `PEAT_BOG`は`MARSH`のhydrology ownershipを再利用でき、microtopography／material／drainage subtypeで表現できるためここへ含めない。

### 4.4 区分4 — alias／specialization／subtype／child／preset／modifier（48名）

| Role | Names | Canonical disposition |
|---|---|---|
| compatibility specialization | `MEANDERING_RIVER` | `RIVER.morphology=MEANDERING`。既存ID/seed/checksumはmigration中維持 |
| compatibility specialization | `ALPINE_MOUNTAIN_RANGE`, `GLACIAL_MOUNTAIN_RANGE` | `MOUNTAIN_RANGE.profile=ALPINE|GLACIAL` |
| compatibility specialization | `VOLCANIC_ARCHIPELAGO` | `ARCHIPELAGO.origin=VOLCANIC`＋cone/caldera child |
| compatibility specialization | `MANGROVE_WETLAND` | `MARSH.wetlandType=MANGROVE`＋environment profile |
| alias/profile | `BACKSHORE_PLAINS` | `PLAIN.context=BACKSHORE` |
| subtype／relation | `BEDROCK_RIVER`, `BRAIDED_RIVER`, `OXBOW_LAKE`, `KARST_CAVE_SYSTEM`, `SUBMARINE_VOLCANO`, `PEAT_BOG` | respectively `RIVER` subtype、`LAKE` cutoff relation、`CAVE_NETWORK` graph role、`SEAMOUNT` volcanic provenance、`MARSH` subtype |
| internal child／overlay | `LAGOON`, `REEF_PASS`, `VOLCANIC_CALDERA`, `LAVA_FLOW_FIELD`, `WATERFALL_VOLUME`, `RIVER_TERRACE`, `GLACIAL_CIRQUE_FIELD`, `FLOODED_CAVE` | parent-owned typed plan／overlay。standalone generatorを作らない |
| composite preset | `WATERFALL_CHAIN`, `ICE_FJORD`, `CENOTE`, `BARRIER_ISLAND`, `ATOLL`, `ESTUARY`, `FLOATING_REEF` | component generator再利用 |
| derived parameter/modifier（21） | メサ、ビュート、スロットキャニオン、ゴージ、ラビーン、U字谷、海食柱、海食アーチ、砂嘴、トンボロ、楯状火山、成層火山、火山島、三日月湖、扇状地群、氷河カール、エスカー、ドラムリン、海岸段丘、リアス式海岸、ワジ | `PLATEAU`／`CANYON`／`VALLEY`／coast／`VOLCANIC_CONE`／`LAKE`／`ALLUVIAL_FAN`／glacial deposition等のprofile、child、relation、presetで表現 |

上表のderived列は21名称を含む（依頼本文の列挙は21件）。したがって区分4のnamed concept総数は **48** である。

## 5. 重点的な重複・包含判断

| Pair／group | 判断 | 理由 |
|---|---|---|
| `RIVER` / `MEANDERING_RIVER` | subtype＋compatibility specialization | ownershipとgraphは共通。既存specialized checksumだけ維持 |
| `MOUNTAIN_RANGE` / alpine / glacial | profile subtype | ridge/peak/saddle coreは共通。snow/glacial materialはprofile |
| `ARCHIPELAGO` / volcanic | subtype＋child composition | island distributionを再利用しvolcanic provenanceを追加 |
| `ROCKY_COAST` / `ROCKY_CAPE` | 両方public、capeは独立specialization | spline coastとpolygon capeでownership・metric・boundsが異なり、既に独立evidenceがある |
| `WATERFALL` / volume | public parent＋internal overlay | volumeはfall checksumへbindし単独意味を持たない |
| cave / karst cave | public parent＋graph subtype | karstはsinkhole→underground river→spring graph role |
| marsh / mangrove / peat | public parent＋compatibility specialization/subtype | surface hydrology ownershipは共通、ecology/material/microtopographyを分離 |
| coral / floating reef | public parent＋preset | floating supportはcoral単体のownership外で、aerial hostとのcomposition |
| cone / caldera / lava field | public parent＋child plans | caldera/lavaはconeまたはvolcanic parent内のordinal付きplan |

## 6. target canonical inventory案

### 6.1 Canonical public independent FeatureKind（57）

```text
SANDY_BEACH
BREAKWATER_HARBOR
HARBOR_BASIN
ROCKY_CAPE
ROCKY_COAST
PLAIN
HILL_RANGE
MOUNTAIN_RANGE
VALLEY
RIVER
FLOODPLAIN
MARSH
FJORD
LAKE
DELTA
SINGLE_ISLAND
ARCHIPELAGO
VOLCANIC_CONE
OCEAN_BASIN
ABYSSAL_PLAIN
SEAMOUNT
CONTINENTAL_SHELF
CONTINENTAL_SLOPE
SUBMARINE_CANYON
CANYON
WATERFALL
TIDAL_CHANNEL_NETWORK
CORAL_REEF
CAVE_NETWORK
CAVE_ENTRANCE
UNDERGROUND_RIVER
SINKHOLE
KARST_SPRING
LUSH_CAVE
SEA_CLIFF
OVERHANG
SKY_ISLAND_GROUP
VALLEY_GLACIER
ICE_CAP
ICE_SHEET
MORAINE_FIELD
OUTWASH_PLAIN
ESCARPMENT
PLATEAU
LAVA_TUBE
SPRING
UNDERGROUND_LAKE
SEA_CAVE
NATURAL_ARCH
OCEAN_TRENCH
MID_OCEAN_RIDGE
DAM_RESERVOIR
ALLUVIAL_FAN
DUNE_FIELD
BADLANDS
TOWER_KARST
SALT_FLAT
```

現行60値のうち14値はtarget independent listから外れる。一方、plan-level volume 3と新規独立8を追加するため、`60 - 14 + 11 = 57`である。既存14値を削除・意味変更する作業はSchema/canonical tokenのbreaking changeなので、ADR承認なしに実施しない。

### 6.2 Internal child／overlay（8）

`LAGOON`、`REEF_PASS`、`VOLCANIC_CALDERA`、`LAVA_FLOW_FIELD`、`WATERFALL_VOLUME`、`RIVER_TERRACE`、`GLACIAL_CIRQUE_FIELD`、`FLOODED_CAVE`

### 6.3 Composite preset（7）

`WATERFALL_CHAIN`、`ICE_FJORD`、`CENOTE`、`BARRIER_ISLAND`、`ATOLL`、`ESTUARY`、`FLOATING_REEF`

### 6.4 Named subtype／modifier（33）

Compatibility／future subtype 12件（`MEANDERING_RIVER`、alpine/glacial mountain、volcanic archipelago、mangrove、backshore、bedrock/braided river、oxbow lake、submarine volcano、karst cave、peat bog）と、依頼の派生21件を指す。Schemaは独立kind列挙ではなく、親ごとのversion付きdiscriminator、parameter、relationへ置く。

## 7. preset contract監査

既存5 presetはtyped planとvalidatorを持つが、public preset input、共通bounds配分、component derived seed、preview legend、Release manifest recordが一つのcomposition contractへ統一されていない。全presetで次を共通化する。

- seed: `NamedSeedDeriverV2`で`preset/<preset-id>/<component-role>/<component-id>`から派生し、component順に依存させない。
- bounds: preset AABB内をcomponent roleごとの明示sub-boundsへ割当て、childが親を越えたらcompile error。
- order: dependency DAGのcanonical topological order。ID同票はUTF-8 byte order。
- failure: missing component、HARD relation矛盾、bounds交差、seed collision、budget超過、unknown versionをfail closed。
- preview: preset overlay、component ownership、bounds allocation、conflict layer、legendをstrict indexへ記録。
- manifest: preset ID/version、component kind/role/plan checksum、derived seed ID、bounds、order、validation checksumを記録し、専用world generator名は記録しない。

| Preset | Components／dependency | Order／bounds | 現在のvalidation | 不足 |
|---|---|---|---|---|
| `WATERFALL_CHAIN` | sealed `RIVER` graph → 2..8 `WATERFALL` nodes → plunge-pool child | upstream→downstream、river support radius≤64 | total ordering、downstream elevation、single downstream edge、pool 1:1、work budget | common seed/bounds/legend/manifest |
| `ICE_FJORD` | `FJORD` + `VALLEY_GLACIER` + cold/snow/ice profile | fjord host→glacier→environment、host checksum bound | valley glacier kind、cold climate、geometry/checksum binding | explicit sub-boundsとpreset preview |
| `CENOTE` | `SINKHOLE` + `CAVE_NETWORK` + `FLOODED_CAVE` hook | cave host→sinkhole carve→bounded fluid→material | host checksum、fluid body/hook bind、support/work budget | public preset input、seed、Release record |
| `BARRIER_ISLAND` | `SINGLE_ISLAND` + `LAGOON` child | island ridge→lagoon、shore-parallel relation | HARD adjacent/flanks/overlaps、centroid degeneracy、budget | integer-only azimuthへの移行、common preview/manifest |
| `ATOLL` | `CORAL_REEF` + `LAGOON` + `REEF_PASS` + optional islet | reef→lagoon→pass→islet | HARD enclosed/carves/connects/ownership、work budget | common seed/bounds/preview/manifest |
| `ESTUARY` | `RIVER` mouth + tidal graph + coast receiving body | global hydrology freeze→tidal coupling→sediment/material | 未実装 | V2-16 Taskで新規composition。DELTAを暗黙複製しない |
| `FLOATING_REEF` | aerial host (`SKY_ISLAND_GROUP`) + `CORAL_REEF` profile + support relation | host solid→reef surface/environment | 未実装 | coral generatorを複製せず、dry/wet environment矛盾を拒否 |

## 8. 実装状態matrix

| State | Count／names | 判断 |
|---|---|---|
| production fully connected | 4 | coastal 4、Paper `SUPPORTED` |
| offline standalone G/V/P/X supported | 16 | `SF16`; 4以外はproduction app未配線 |
| plan-level offline supported/partial | 42 kind | V2-9 18、V2-10 14、volume 7、ほかのplan/profile。能力列ごとに扱う |
| enum/Schema only | 4 | `BACKSHORE_PLAINS`, `BEDROCK_RIVER`, `GLACIAL_CIRQUE_FIELD`, `FLOODED_CAVE` |
| internal/preset/macro catalog entry | 11 | FeatureKind 60とは別にcatalogへ存在 |
| Paper `SUPPORTED` | 4 | coastal 4のみ |
| Paper `EXPERIMENTAL` | 21 catalog entry | hydrology/environment/sparse-volume prefix。実機prefix smoke前に昇格禁止 |
| Paper `UNSUPPORTED` | foundation/preset等 | Release capability未接続 |

## 9. Task dependency graph案

既存5 Track制を維持し、V2-15／V2-16はTrack Eの続編、V2-17はTrack Aのplacement capability follow-upとする。新Trackは作らない。

```text
V2-14-03 README current-state sync
  → V2-15-01 audit resume/close
  ├─ V2-15-02 current registry/CI ──┬─ V2-15-05 dispatch spine ── wiring leaves ── V2-15-47 gate
  │                                 └─ non-breaking docs sync
  └─ V2-15-03 identity ADR (HUMAN) → V2-15-04 target registry/schema migration ─┘

V2-15-47
  → V2-16-01 composition spine
  → preset/deferred/new feature leaves
  → V2-16-19 gate

V2-15-47 + V2-16-19
  → V2-17-01 measurement harness
  → prefix real-host tasks (BLOCKED_EXTERNAL without host)
  → evidence-bound promotion (HUMAN)
  → V2-17-07 gate
```

## 10. 新規Task本文案

全TaskはTask Execution Guideを継承する。通常Taskは対象test＋`./gradlew test`＋`./gradlew build`、Phase gateだけがfull clean scenario/tamper/runtime portfolioを実行する。feature leafの共通Acceptanceは、Schema/typed parse、semantic/bounds validation、named seed、generator registry、offline generation、feature invariant、preview index、Release strict directory/ZIP、CLI/Paper smoke、malformed/unknown rejection、whole/tile/thread/order/locale/timezone、1000角またはfeature上限budget、既存20 feature回帰である。Paper能力はV2-17まで昇格しない。

### 10.0 V2-14-03 README current-state consistency follow-up（Track B）

- **目的／前提:** 本監査が検出したV2-7/V2-14の対外説明矛盾を解消する。前提は`V2-14-01`／`02`完了。
- **Scope／成果物:** READMEの画像抽出状態をproduction CLIの`extract`／`promote`／request宣言E2Eと一致させ、reference roleをproductionの6種へ同期する。2026-07-18 Gap auditは履歴と明示し、本監査へのcurrent linkを追加する。
- **非Scope:** capability昇格、Schema／enum／checksum変更、画像処理実装。
- **必須test／Gate:** README内の旧主張を`rg`で検査し、既存`ImageExtractionWorkflowServiceV2Test`／`LandformCraftCliV2Test`／`SchemaContractTest`を実行する。production差分ゼロ、リンク切れゼロなら完了し、`V2-15-01`監査を正本照合から再開する。

### 10.1 V2-15 Canonical catalog and existing-generator public wiring（Track E続編、47 Task）

| Task | 依存 | Scope／成果物 | Gate／非Scope |
|---|---|---|---|
| `V2-15-01` | V2-14-03 | 本監査、4区分、57-kind案、Task graph | **監査成果物作成済み。V2-14-03後に正本再照合してclose** |
| `V2-15-02` | 01 | current-state machine registryとCI projection（enum/Schema/module/catalog/docs差分検出） | **完了（2026-07-22）**。read-only projectionとdocs fingerprintをCI固定。現行semantic/checksum不変 |
| `V2-15-03` | 01 | 14 existing Schema valueのalias/subtype/child移行[ADR 0036](../adr/0036-canonical-feature-identifier-disposition.md) | **完了（2026-07-22、人間承認済み）**。14値の処置と互換移行policyをAcceptedとして固定。enum／Schema／writer／generator／capabilityは本Taskで変更していない |
| `V2-15-04` | 02,03 | target registry、Schema projection、migration/deprecation、docs generator | compatibility fixtureと既存checksum不変または承認済みmigration |
| `V2-15-05` | 02 | registry-driven production generator/validator/preview/export dispatch spine | feature昇格なし、Release format名変更なし |
| `V2-15-06` | 05 | `hydrology-plan` production application pipeline | **完了（2026-07-22）**。shared artifactsのみ、個別feature昇格なし。production Application Service／pipelineとstrict graph bindingを固定 |
| `V2-15-07` | 05,06 | `environment-fields` production pipeline | **完了（2026-07-22）**。shared artifactsのみ、個別feature昇格なし。hydrology dependencyを同一Releaseでstrict verify |
| `V2-15-08` | 05,07 | `sparse-volume` production pipeline | ordered CSGとvolume tile read-back |
| `V2-15-09` | 05 | V2-9/10 foundation surface export adapter | new capability名は作らず既存artifact mappingをADRで確認 |
| `V2-15-10` | 03,06 | `RIVER`＋legacy meandering subtype wiring | `BRAIDED`はV2-16、既存meander checksum不変 |
| `V2-15-11` | 03,06 | `LAKE`＋oxbow cutoff subtype wiring | dam reservoirはV2-16 |
| `V2-15-12` | 06 | `CANYON` public CLI/Release wiring | submarine canyon非Scope |
| `V2-15-13` | 06,08 | `WATERFALL`＋`WATERFALL_VOLUME` overlay wiring | volumeをFeatureKind化しない |
| `V2-15-14` | 06 | `DELTA` wiring | estuary非Scope |
| `V2-15-15` | 06 | `TIDAL_CHANNEL_NETWORK` wiring | mangrove/estuary composition非Scope |
| `V2-15-16` | 06 | `FJORD` wiring | ICE_FJORDはV2-16 |
| `V2-15-17` | 03,07,09 | `MOUNTAIN_RANGE`＋alpine/glacial compatibility profiles | three independent generatorsを作らない |
| `V2-15-18` | 03,07,09 | `ARCHIPELAGO`＋volcanic compatibility profile | caldera/lava childを再利用 |
| `V2-15-19` | 03,07,09 | `MARSH`＋mangrove compatibility profile | peat bogはV2-16 |
| `V2-15-20` | 07 | `CORAL_REEF` wiring | lagoon/passはchild、floating reefはV2-16 |
| `V2-15-21` | 03,09 | `PLAIN`＋backshore alias | backshore独立generator禁止 |
| `V2-15-22` | 09 | `HILL_RANGE` wiring | mountain profile変更なし |
| `V2-15-23` | 09 | `VALLEY` wiring | U字谷はprofile |
| `V2-15-24` | 09 | `FLOODPLAIN` wiring | river relation必須 |
| `V2-15-25` | 09 | `ROCKY_COAST` wiring | cape checksum不変 |
| `V2-15-26` | 09 | `SEA_CLIFF` wiring | sea cave host handoffを検証 |
| `V2-15-27` | 09 | `SINGLE_ISLAND` wiring | barrier preset非Scope |
| `V2-15-28` | 09 | `VOLCANIC_CONE` wiring | caldera/lava child only |
| `V2-15-29` | 09 | basin/shelf/slope continuous marine trio | 同一transect kernelの明示variant例外 |
| `V2-15-30` | 29 | `SUBMARINE_CANYON` wiring | surface canyonとowner分離 |
| `V2-15-31` | 29 | `ABYSSAL_PLAIN`＋`SEAMOUNT` basin-contained variants | trench/ridge非Scope |
| `V2-15-32` | 08,09 | `CAVE_ENTRANCE` wiring | frozen cave checksum bind |
| `V2-15-33` | 08,09 | `UNDERGROUND_RIVER` wiring | flooded hookはinternal |
| `V2-15-34` | 07,09 | valley glacier/ice cap/ice sheet common-kernel wiring | 3 variant以外非Scope |
| `V2-15-35` | 34 | moraine/outwash wiring＋missing preview index | glacial parent bounds必須 |
| `V2-15-36` | 08,09 | sinkhole/karst spring graph wiring | karst caveはsubtype |
| `V2-15-37` | 09 | escarpment/plateau transition pair wiring | mesa/butte profile only |
| `V2-15-38` | 08,09 | `LAVA_TUBE` wiring | `CARVE_SOLID` ownershipのみ |
| `V2-15-39` | 06,09 | `SPRING` wiring | karst spring specialization不変 |
| `V2-15-40` | 08 | cave network/lush cave common graph wiring | cave graph internal component非公開 |
| `V2-15-41` | 08 | `OVERHANG` wiring | gravity containmentはPaper gateへ残す |
| `V2-15-42` | 08 | `SKY_ISLAND_GROUP` wiring | single islandと混同しない |
| `V2-15-43` | 03,08 | `UNDERGROUND_LAKE` public vertical slice | 新public kind 1件、cave/fluid bounds |
| `V2-15-44` | 03,08 | `SEA_CAVE` public vertical slice | rocky coast/cliff host |
| `V2-15-45` | 03,08 | `NATURAL_ARCH` public vertical slice | through-passage/support invariants |
| `V2-15-46` | 03,04 | child/overlay/compatibility catalog cleanup | generator削除やSchema破壊はADR範囲のみ |
| `V2-15-47` | 10..46 | Phase gate、既存20＋全leaf E2E、full baseline | 親Phaseを閉じる唯一のTask |

### 10.2 V2-16 Deferred/new terrain and composition（Track E続編、19 Task）

| Task | 依存 | Scope／成果物 | Gate／非Scope |
|---|---|---|---|
| `V2-16-01` | V2-15-47 | parent/child/preset composition engine、derived seed、bounds allocation、manifest/preview contract | dedicated preset generator禁止 |
| `V2-16-02` | 01 | `WATERFALL_CHAIN` public preset wiring | existing river/fall/pool reuse |
| `V2-16-03` | 01 | `ICE_FJORD` wiring | fjord/glacier/environment reuse |
| `V2-16-04` | 01 | `CENOTE` wiring | sinkhole/cave/flooded hook reuse |
| `V2-16-05` | 01 | `BARRIER_ISLAND` wiring | island/lagoon reuse |
| `V2-16-06` | 01 | `ATOLL` wiring | reef/lagoon/pass reuse |
| `V2-16-07` | 01 | `ESTUARY` composition | deltaを複製せずriver mouth＋tidal coupling |
| `V2-16-08` | 01 | `FLOATING_REEF` composition | coral＋aerial host、独立FeatureKindなし |
| `V2-16-09` | V2-15-10 | braided/bedrock river subtype、river terrace child | `RIVER` generator family内、new kindなし |
| `V2-16-10` | V2-15-11 | `DAM_RESERVOIR` standalone vertical slice | dam/barrier/spillway ownership |
| `V2-16-11` | V2-15-10 | `ALLUVIAL_FAN` standalone vertical slice | fan groupsはmulti-placement |
| `V2-16-12` | V2-15-29 | `OCEAN_TRENCH` vertical slice | global continuity、LARGEはHOLD policyに従う |
| `V2-16-13` | 12 | `MID_OCEAN_RIDGE`＋submarine volcano subtype | ridge 1 kind、volcanoはseamount provenance |
| `V2-16-14` | V2-15-21 | `DUNE_FIELD` | wind、spacing、height、stoss/lee、interdune、context |
| `V2-16-15` | V2-15-37 | `BADLANDS` | ridges/gullies/hoodoo/strata/drainage integration |
| `V2-16-16` | V2-15-36 | `TOWER_KARST` | towers/spacing/steepness/depressions/floor/cave hooks |
| `V2-16-17` | V2-15-37 | `SALT_FLAT` | flat floor/crust/water/cracks/rim/subtype |
| `V2-16-18` | V2-15-19 | `PEAT_BOG` subtype | hummock/hollow/pool/drainage/saturation/material、new kindなし |
| `V2-16-19` | 02..18 | Phase gate、full baseline、canonical count再確認 | 親Phaseを閉じる唯一のTask |

### 10.3 V2-17 Paper placement evidence and promotion（Track A follow-up、7 Task）

| Task | 依存 | Scope／成果物 | Gate／非Scope |
|---|---|---|---|
| `V2-17-01` | V2-15-47,V2-16-19 | feature×capability×runtime measurement harness、runbook、evidence schema | capability昇格なし |
| `V2-17-02` | 01 | hydrology-plan実機matrix、full verify/Undo/Recovery | hostなしは`BLOCKED_EXTERNAL` |
| `V2-17-03` | 01 | environment-fields実機matrix | 同上 |
| `V2-17-04` | 01 | sparse-volume実機matrix（fluid/gravity/containment含む） | 同上 |
| `V2-17-05` | 01 | foundation/new/preset exported stream実機matrix | 未定義capabilityなら先にRelease Taskへ戻す |
| `V2-17-06` | 02..05 | evidence範囲だけcatalog promotion | 人間承認必須、未測定kind/dimensionは昇格禁止 |
| `V2-17-07` | 06 | Paper Phase gate、Recovery drill、full runtime profile | 親Phaseを閉じる唯一のTask。host不足は`BLOCKED_EXTERNAL` |

## 11. Task Index／roadmap登録結果

2026-07-22に次を正本へ適用した。これはTask登録であり、README修正、Schema変更、generator実装、capability昇格ではない。

1. V2-14へfollow-up `V2-14-03`を追加し、Task Index概要へV2-15（47）、V2-16（19）、V2-17（7）を追加して合計を144→218へ更新する。
2. 親Phase close許可へ`V2-15-47`、`V2-16-19`、`V2-17-07`を追加する。
3. roadmapのactive nextを`V2-14-03`とし、完了後に`V2-15-01`を再照合してcloseする。その後のTrack E次Taskは`V2-15-02`、Track Aの将来依存Taskは`V2-17-01`（V2-15/16 gate待ち）とする。
4. model-assignmentへ`V2-14-03`を含む全74 Taskを追加する。既定はnormal wiring=Sonnet High/Antigravity、contract/Schema=Opus High、graph/math/memory=Opus XHighまたはCodex Terra、Release spine=Opus High＋Codex review、Phase gate=Codex Sol Max、実機=Cursor Pro＋人間。
5. READMEの2矛盾は専用docs-sync `V2-14-03`で先に解消し、本監査をcurrent inventory linkへ昇格する。

## 12. Decision required

### DR-1: 既存14 Schema valueのtarget disposition

- **採用:** canonical registryではalias/subtype/childへ分類し、1 release cycleのdeprecated read/migrateを経て新規writerから除外する。既存artifact readerは維持する。[ADR 0036](../adr/0036-canonical-feature-identifier-disposition.md)へ完全な処置表と互換移行条件を記録し、2026-07-22に人間承認されAcceptedとなった。
- **代替A:** 現行60値を永久にpublic spellingとして保持し、registry roleだけで重複を示す。互換性は強いがcanonical modelの重複が残る。
- **代替B:** 新Schema major versionで即時削除。最も単純だが既存TerrainIntent、seed namespace、checksumを破壊するため非推奨。
- **影響:** `terrain-intent-v2` token、parser、provider prompt、examples、catalog checksum、migration。ADR 0035 D4はこの変更を明示承認していない。
- **解消済み停止条件:** ADR 0036の人間承認により`V2-15-03`をcloseし、そのallowlist内で`V2-15-04`を完了した。dependent leafはTask Indexの依存順に従う。

### DR-2: foundation artifactのRelease capability mapping

- **推奨:** block streamの意味に応じ既存`surface-2_5d`／`hydrology-plan`／`environment-fields`／`sparse-volume`へ格納し、feature名ごとのcapabilityを作らない。
- **代替:** `foundation-plan`新capability。strict containerは明確だが新artifact format/ADR/全verifierが必要。
- **停止Task:** V2-15-09以降のfoundation export。plan/validator/previewとregistry CIは続行可能。

## 13. Verification

```text
./gradlew build -x test
BUILD SUCCESSFUL

./gradlew test --tests '*SchemaContractTest' \
  --tests '*FeatureSupportCatalogV2Test' \
  --tests '*FoundationPhaseGateV2Test' \
  --tests '*DeferredTerrainPhaseGateV2Test' \
  --tests '*V2CommandPathE2EV2Test' \
  --tests '*Release2ExportApplicationServiceV2Test'
BUILD SUCCESSFUL (30s)

Task登録後:

V2-15 Task Index／model-assignment unique ID = 47／47
V2-16 Task Index／model-assignment unique ID = 19／19
V2-17 Task Index／model-assignment unique ID = 7／7
V2-14-03を含む新規model-assignment行 = 74
git diff --check = PASS

./gradlew test
BUILD SUCCESSFUL（configuration cache、test FROM-CACHE）

./gradlew build
BUILD SUCCESSFUL（configuration cache、全Task UP-TO-DATE）
```

production source、Schema、example、capability、generated artifactの差分は0件である（登録時点）。README矛盾の修正とその対象test再実行は`V2-14-03`で完了した。

### 13.1 V2-15-01 再照合結果

```text
FeatureKind Java／Schema set comparison = MATCH（60／60）
sealed catalog = 71（featureKind 60、synthetic 11）
dedicated module bindings = 16
Paper SUPPORTED entries = 4（coastal 4）
target/current = 57／60
current values outside target proposal = 14
target proposal additions = 11（plan-level volume 3＋new independent 8）
V2-15／V2-16／V2-17 Task Index・model-assignment unique ID = 47／19／7、各集合一致

./gradlew build -x test --console=plain
BUILD SUCCESSFUL

./gradlew test --tests '*SchemaContractTest' \
  --tests '*DocsLinkConsistencyTest' \
  --tests '*FeatureSupportCatalogV2Test' \
  --tests '*FoundationPhaseGateV2Test' \
  --tests '*DeferredTerrainPhaseGateV2Test' \
  --tests '*VolumePhaseGateV2Test' \
  --tests '*V2CommandPathE2EV2Test' \
  --tests '*Release2ExportApplicationServiceV2Test' --console=plain
BUILD SUCCESSFUL（30s）

文書同期後:
./gradlew test --tests '*DocsLinkConsistencyTest' \
  --tests '*SchemaContractTest' \
  --tests '*FeatureSupportCatalogV2Test' --console=plain
BUILD SUCCESSFUL（2s）

./gradlew test --console=plain
BUILD SUCCESSFUL（configuration cache、test FROM-CACHE）

./gradlew build --console=plain
BUILD SUCCESSFUL（configuration cache、全Task UP-TO-DATE）

git diff --check = PASS
```

V2-15-01によるproduction source、test source、Schema、example、capability、generated artifactの変更は0件である。変更は本監査、Task Index、Execution Guide、implementation roadmap、進捗roadmapの整合に限定した。

### 13.2 V2-15-02 current-state registry／CI projection

`CurrentFeatureStateRegistryV2`は、Schema bytesをtest境界から受け取り、`TerrainIntentV2.FeatureKind`、module binding、sealed Feature Support Catalogを60件のread-only projectionへ正規化する。source欠落／未知、catalog欠落、存在しないmoduleへのbinding、module未宣言bindingをfail-closedで返す。分類はproduction-connected 4、offline-or-plan-level 48、enum-schema-only 4、child-plan-only 4であり、public exportやPaper `SUPPORTED`を追加しない。canonical projection SHA-256は[registry文書](../design-v2/current-feature-state-machine-registry.md)へ記録し、CIが一致を確認する。

```text
./gradlew test --tests 'com.github.nankotsu029.landformcraft.core.v2.catalog.CurrentFeatureStateRegistryV2Test' \
  --tests 'com.github.nankotsu029.landformcraft.format.SchemaContractTest' \
  --tests 'com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCatalogV2Test' \
  --tests 'com.github.nankotsu029.landformcraft.docs.DocsLinkConsistencyTest' --console=plain
BUILD SUCCESSFUL
```

production FeatureKind値、Schema、sealed catalog example／checksum、Release capability、generator／export／Paper routingはV2-15-02で変更していない。target projection migrationは後続`V2-15-04`で実装した。

### 13.3 V2-15-03 identifier disposition ADR（Accepted）

[ADR 0036](../adr/0036-canonical-feature-identifier-disposition.md)はDR-1の推奨案を具体化し、2026-07-22の明示的人間承認でAcceptedとなった。
現行14値を6 compatibility subtype／profile、2 subtype／relation、6 parent-owned child／overlayへ分類し、
各target owner、lossless migration条件、ambiguous relationのfail-closed拒否を固定した。read-time aliasは行わず、
legacy strict readとexplicit migrationを分離する。未移行artifactのchecksum／named seedは不変、migration後の
document checksum変更はversion付き旧新対応reportを条件に承認対象とし、意味が同じ既存generator経路のfinal
block checksumは維持する。

deprecation windowはtarget writer／migrator／reader／docsを含む最初のtagged releaseから開始し、少なくとも
1 release cycleと別のhuman close approvalを要求する。window後も14 tokenのlegacy readerは削除しない。
この承認により`V2-15-03`は完了した。Schema、enum、writer、provider、example、catalog、capability、
generator／export／Paper routingはV2-15-03で変更していない。`V2-15-04`はその後ADR allowlist内で完了した。

pre-acceptance review 1では、(1) V2-15-04が追加できるcanonical Schema、parent discriminator／typed payload、
6 child carrier、`legacySeedBinding`をexact allowlist化し、(2) `CANONICAL_V2` document discriminatorと
explicit `LEGACY_V2` reader引数を必須化してguess／Schema fallbackを禁止し、(3) migrated documentへsource
seed derivation tupleをcanonical generation semanticsとして保持し、(4) 公開lifecycleを単調・rollback不可、
implementation停止／切替をstate非変更の運用rollbackとして分離した。

```text
14-value ADR table count = 14
V2-15-04 parent typed-field allowlist rows = 7
parent-owned child token allowlist = 6
explicit projection／legacy seed semantic／monotonic lifecycle checks = PASS
human approval = PASS（2026-07-22、user message `adr0036を承認します。`）
git diff --check = PASS

./gradlew test --tests '*DocsLinkConsistencyTest' \
  --tests '*SchemaContractTest' --console=plain
BUILD SUCCESSFUL

./gradlew test --console=plain
BUILD SUCCESSFUL（configuration cache、test FROM-CACHE）

./gradlew build --console=plain
BUILD SUCCESSFUL（configuration cache、全Task UP-TO-DATE）
```

### 13.4 V2-15-04 canonical target projection／migration

ADR 0036のallowlistを`terrain-intent-v2-canonical.schema.json`、`CanonicalTerrainIntentV2`、
`CanonicalFeatureTargetRegistryV2`へ実装した。historic 60 sourceは互換registryに残し、canonical
authoringは46 top-level kindへ限定する。旧14値は7 parent specialization／aliasと6 nested child／overlayへ
exact mappingし、Schema driftはCIでfail closedにする。lifecycleは14値とも`CURRENT_PUBLIC`のままで、
operational stop／re-enableはstateを変更しない。

`TerrainIntentVersionDispatcher`はhistoric v2を読むとき`LEGACY_V2` selectorを必須にし、generic pathは
top-level `CANONICAL_V2`がないv2文書を拒否する。strict migratorはowner relationと全legacy seed tupleを
要求し、target＋version付きreportのexact file set、strict read-back、checksum／binding照合後だけatomic
publishする。移行済みtargetの再入力は`ALREADY_CANONICAL`で二重変換しない。

compatibility pairは既存meandering fixtureを変更せず、canonical targetを別exampleとして追加した。
14 mapping、missing／unsupported owner、unknown field／projection、seed checksum参加、bundle extra／collision、
idempotence、locale／timezone安定性を対象testで検証した。既存Schema、既存example bytes、catalog／Release
capability、generator／export／Paper routingは変更していない。

```text
./gradlew test --tests '*CanonicalFeatureTargetRegistryV2Test' \
  --tests '*CanonicalFeatureMigrationServiceV2Test' \
  --tests '*LandformV2DataCodecTest.exactVersionDispatcherKeepsV1AndV2Separate' \
  --tests '*SchemaContractTest' --console=plain
BUILD SUCCESSFUL

./gradlew test --console=plain
BUILD SUCCESSFUL（2m 27s）

./gradlew build --console=plain
BUILD SUCCESSFUL

git diff --check = PASS
```

## 14. 残存risk

- canonical target projection／migrationは実装済みだが、generator／validator／preview／export dispatch spineは`V2-15-05`まで未接続である。Schema実装をproduction feature supportと表現しない。
- existing five presetの一部compilerにfloating-point azimuth計算があり、共通composition determinism Taskでinteger-onlyへ置換またはchecksum非影響を証明する必要がある。
- V2-9/10の「plan-level export」とproduction Release exportを混同するとfalse promotionになる。
- Paper実機evidenceはcoastal 4以外にない。測定値を作らずV2-17を`BLOCKED_EXTERNAL`にできる設計を維持する。
