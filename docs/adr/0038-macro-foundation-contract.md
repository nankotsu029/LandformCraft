# 0038: 全surface XZ cellへeffective foundation ownerを要求するmacro foundation契約

- Status: Accepted
- Date: 2026-07-22（同日第一レビュー Changes Requested を反映した改訂第2版。同日、改訂第2版に対する人間承認により`Accepted`）
- Decision scope: V2-18-08（人間承認必須 — 2026-07-22承認済み）

> このADRはScopeとして「契約とcomposition profileの確定」だけを持ち、production実装（macro foundation spine、coastal再接続、fail-closed昇格）は行わない。実装は`V2-18-09`（spine＋coastal統合）／`V2-18-10`（owner coverage gate fail-closed）が本ADRの承認後に行う。本ADRが`Accepted`になるまで`V2-18-09`は開始しない（[task-index §18](../design-v2/task-index.md)、[model-assignment §9.1](../design-v2/model-assignment.md)）。
>
> 改訂第2版は第一レビュー（2026-07-22、Changes Requested）の4 blocking指摘 — (1) 単一role enumの多段寄与表現不能、(2) macro base×producerの候補owner解決未定義、(3) modifierのowner merge化、(4) 明示polygon入力のSchema home不在 — をコードベース照合のうえ全面反映した。反映内容は各Decisionに記す。レビュー指摘のうち`GLACIAL_CIRQUE_FIELD`のchild未確定説のみ、`CanonicalFeatureTargetRegistryV2`の承認済みdisposition（`PARENT_CHILD` `MOUNTAIN_RANGE.profile=GLACIAL.children.GLACIAL_CIRQUE_FIELD`）で反証されるため採用しない。

## Context

[macro foundation／intent conformance監査（2026-07-22、HEAD `5920afc`）](../audits/macro-foundation-conformance-audit-2026-07-22.md)は、400×400 coastal intent（`coastal-fishing-map`）で北側本土が消失しfeatureが孤立する事象の根本原因を実測で確定した。中核の欠陥は次である。

1. **基礎地形ownerの不在。** production surface経路（`CoastalSurfaceExportPipelineV2`＋`SurfaceBaselineV2`）はfeature非所有cell（実測73.0%）を一律`SurfaceBaselineV2`（map全域で単一のland/water分類＋単一Y）で埋め、大域land-water構図の正本を持たない。設計文書Stage 2（macro layout）に対応するproduction実装が無い。
2. **設計資産はofflineに存在するがproduction spineへ未接続。** 正しい「ownerless cell拒否」契約は`SurfaceFoundationMergeCompilerV2`（`OWNERLESS_CELL`／`OWNER_TIE`／`UNDECLARED_OVERLAP`を拒否）と`SurfaceFoundationPlanV2`（V2-9-01）／`MacroLandWaterTopologyPlanV2`（V2-9-12）としてplan-levelに実装済みだが、export spineが呼ばない。
3. **役割軸の不足。** `FeaturePrimaryRoleV2`（catalog意味＝`MACRO_CONSTRAINT`／`STANDALONE_FEATURE`／`CHILD_PLAN_ONLY`等）は「どう著述・利用するか」を表すが、「合成時に基礎地形を**生成する**のか、既存基礎地形を**変形する**のか、volume／fluidへ寄与するのか」を区別しない。owner coverage gate（`V2-18-10`）はこの区別を必要とするが、`FeaturePrimaryRoleV2`単独では表現できない。さらにtaxonomyが示すとおり、単一のFeatureが複数の合成段へ寄与する（`OVERHANG`＝solid add＋recess carve、`SINKHOLE`＝surface collapse＋cave connection、`CAVE_ENTRANCE`＝surface-volume connector、`UNDERGROUND_RIVER`＝carve→`ADD_FLUID`）ため、**単一値のrole enumでは合成寄与を表現できない**。

`SurfaceFoundationMergeCompilerV2`は`OwnerDescriptor.surfaceClass`（`SurfaceClassCode` 16種）を持つflatなowner集合を統合するが、(a) 基礎tierとmodifier tierを分離しておらず、(b) interaction未宣言のoverlapを一律拒否するため、domain全域を覆うbackground候補と個別producerの共存を現契約のまま表現できない。本ADRはこの2点の契約拡張を確定する。

## Decision

### D1. Production foundation契約（candidate／background／effective ownerと全cell被覆）

surface domain内の各XZ cellについて、次を契約とする。

1. **candidate owner（0..N）。** 各cellには複数のfoundation candidateが存在してよい。candidateになれるのは、D3のcomposition profileで`foundationEligible: true`かつ`FOUNDATION` stageへ寄与するowner、および次項のmacro baseだけである。
2. **macro base＝BACKGROUND candidate。** D2の明示入力（`LAND_WATER_MASK`等）から生成されるmacro baseは、surface domain全域を覆う**background候補**である。backgroundは他のcandidateと衝突しない特別なtier下位に位置し、`UNDECLARED_OVERLAP`拒否の対象外とする（現行merge kernelのflat owner集合への契約**拡張**。D5-2）。
3. **置換規則（replacement）。** foundation producerは自身の明示footprint内でbackgroundをeffective ownerとして**置換**する。置換は宣言的（footprint＋D5の置換契約）であり、暗黙の優先度比較やlast-write-winsで決めない。producer同士（同tier）のoverlapは、明示interaction（`SurfaceFoundationPlanV2.Interaction`）が無い限り従来どおり`UNDECLARED_OVERLAP`で拒否する。
4. **effective owner（ちょうど1）。** merge後の各cellは**ちょうど1つのeffective foundation owner**を持つ。effective ownerはそのcellのland-water medium（`LAND`／`WATER`）とbase elevationを確定する正本である。同tie（`OWNER_TIE`）は拒否する。
5. **ownerless拒否。** backgroundすら存在しないcell（＝明示foundation入力がdomainを被覆しない）は`OWNERLESS_CELL`として契約違反であり、`SurfaceBaselineV2`等の暗黙fallbackで補完しない。

用語は candidate owner／background owner／effective owner／replacement／transition（interaction band内のblend）／undeclared overlap を上記の意味で使い分け、`V2-18-09`実装はこの区別を型に反映する。

fail-closed化は必ずreport-only段階を先行させる（[audit §4](../audits/macro-foundation-conformance-audit-2026-07-22.md)）。`V2-18-02`のowner coverage診断（report-only）は既に存在する。本ADRは契約を定義し、`V2-18-09`がfoundation stageを配線して被覆を100%にできる状態を作り、`V2-18-10`だけがcoverage gateをfail-closedへ変える（gateの分担はD7）。

### D2. Foundation入力契約とresolution policy（内部境界推測の禁止）

macro foundationのland-water＋provisional base elevationは、次の**明示入力からのみ**生成する。

1. **正規入力（`V2-18-09`のnormative経路）: `LAND_WATER_MASK` raster。** Schema上の保存場所は既存の`TerrainIntentV2.mapReferences`（`ConstraintMapBinding`、`ConstraintMapRole.LAND_WATER_MASK`、HARDはzero tolerance強制済み）であり、解決は`V2-18-06`の再利用binding（`ConstraintMapFieldBindingV2`→`BoundConstraintFieldV2`、secure resolve→digest→decode→canonical field登録）で行う。optionalな`ZONE_LABEL_MAP`（同`mapReferences`）はmacro region labelの入力である。
2. **base elevationの明示source。** provisional elevationは (a) `ConstraintMapRole.HEIGHT_GUIDE` mapの解決値、または (b) requestが宣言するmedium別base level（LAND／WATERごとの固定値）のみから得る。どちらも無い場合はfoundation入力不完全としてownerless扱い（D1-5）であり、推測しない。
3. **明示LAND／OCEAN polygon regionは本ADR時点でSchema homeが存在しない。** `TerrainIntentV2.PolygonGeometry`はgeometry型のみでmedium／elevation source／優先度／provenanceを持てず、公開intentにmacro region collectionは無い。よってpolygon入力の**導入は本ADRでは行わず**、導入する場合の契約要件だけを固定する: region型は最低限 `id`／`medium: LAND|WATER`／`geometry`／`baseElevationSource`／`priority`／`provenance` を持ち、hole ringのmedium・domain外周の扱い・mask併用時の正本（maskを正本とし矛盾は拒否）を宣言しなければならない。この型の追加は独立したSchema変更であり、必要になった時点で新Task＋本ADR amendmentとする。`V2-18-09`はmask経路のみで完了できる（[task-index §18](../design-v2/task-index.md) `V2-18-09` Scopeの「または」）。
4. **`MacroLandWaterTopologyPlanV2`の役割限定。** 同plan（V2-9-12）は解決済みland-water／zone rasterからregionの接続・包含・最小neck/channel幅を**表現・検証・freeze**するものであり（`MacroLandWaterTopologyPlanCompilerV2`は`ManualTopologyInputV2`のmask rasterをcomponent labelingするだけで境界を生成しない）、`BAY`／`STRAIT`等のlabelだけから境界を**新規生成しない**。D5ではこの意味でのみtopology正本として接続する。

次を**禁止**する。

- `EDGE_CLASSIFICATION`制約（外周band比率）だけからの内部海岸線・内部land-water境界の推測。`EDGE_CLASSIFICATION`はvalidation target（`V2-18-04`のevaluator）であって生成入力ではない。
- feature footprint（coastal plan等）だけからの大域land-water構図の逆算。
- 欠測・no-data・未解決mapへのfallback受理。未解決HARD mapは`V2-18-03`の`HardPreflightGateV2`が既にfail-closed拒否する。

### D3. Composition profileの新設（要否＝**要**。単一role enumは不採用）

`FeaturePrimaryRoleV2`と**分離した**versioned契約`CompositionProfileV2`を新設する（本ADRは契約のみ確定、型の実装は`V2-18-09`）。第一レビューの指摘どおり、単一値のrole enum（初版の`CompositionRoleV2` 5値案）はtaxonomyが明記する多段寄与（`OVERHANG`＝add＋carve、`SINKHOLE`＝surface＋volume等）を表現できないため**不採用**とし、次の3成分で表す。

```text
CompositionProfileV2
- foundationEligible: boolean         … D1のfoundation candidate（基礎tier owner候補）になれるか
- stages: Set<CompositionStageV2>     … compiled planが寄与する合成段（0..N）
- parentPolicy: ParentPolicyV2        … 親依存性（合成段とは独立の軸）
```

```text
CompositionStageV2（合成段。実行順は基礎→surface→volume→fluid、volume内はADD_SOLID→CARVE_SOLID→ADD_FLUID＝AGENTS.md §8の既存順序）
- FOUNDATION            … land-water medium＋base elevationの確定（D1のcandidate）
- SURFACE_MODIFICATION  … 既存基礎の2.5D変形・堆積・切込み・transition
- VOLUME_OPERATION      … bounded sparse AABB内のADD_SOLID／CARVE_SOLID
- FLUID_OPERATION       … checksum-boundなbounded ADD_FLUID

ParentPolicyV2
- STANDALONE            … 親なしで意味を持つ（host planへのchecksum bindは可）
- PARENT_REQUIRED       … 特定親のownership内でのみ意味を持つ（child plan）
- PARENT_BOUND_OVERLAY  … 親planのchecksumへboundしたoverlay hook
```

**lookup規則:** composition profileの参照は[ADR 0036](0036-canonical-feature-identifier-disposition.md)のcanonicalization**後**のtarget carrierに対して行う。legacy alias／subtype／child disposition（`CanonicalFeatureTargetRegistryV2.approvedDispositions`）を持つkindはcarrierのprofileを継承する（例: `BACKSHORE_PLAINS`→`PLAIN.context=BACKSHORE`→`PLAIN`のprofile）。これによりaliasの前後でprofileが変わる不整合を構造的に排除する。

**要否の判断:** composition profileは**必要**である。owner coverage gate（`V2-18-10`）は「foundation-eligibleなownerが全cellを被覆するか」を判定する必要があるが、`FeaturePrimaryRoleV2`はこれを表現できない（`STANDALONE_FEATURE`にfoundation producerもsurface modifierも混在する）。profileはcatalog roleへ畳み込まず独立軸として登録し、macro foundation stage（`V2-18-09`）とcoverage gate（`V2-18-10`）だけが消費する。新capability／artifact type／Release formatは追加しない。

### D4. 60 kind対応表（Normative／Provisional 2段階）

`TerrainIntentV2.FeatureKind`の全60値へcomposition profileを割り当てる。第一レビューの指摘を受け、本表を**2段階**とする。

- **NORMATIVE（6 kind）:** 現在production接続済み、またはADR 0037 adapterでexport接続済みのkind。本ADRの承認で確定する。
- **PROVISIONAL（54 kind）:** 未接続kindの暫定分類。**各kindのproduction配線Task（V2-15系）が、generatorが実際に提供・要求するfieldを監査したうえで確定**し、変更が必要なら本ADRをamendする。V2-15 stage gateの「composition role登録」はこの確定を指す。

stages列は複数寄与を`+`で列挙する。alias／subtype／childはD3のlookup規則によりcarrier継承（表では「→carrier」と記す）。

| # | FeatureKind | foundationEligible | stages | parentPolicy | 区分 | 根拠（taxonomy／registry） |
|---:|---|:---:|---|---|---|---|
| 1 | `SANDY_BEACH` | no | SURFACE_MODIFICATION | STANDALONE | **NORMATIVE** | 海岸帯変形（§3.1、監査対象4種） |
| 2 | `BREAKWATER_HARBOR` | no | SURFACE_MODIFICATION | STANDALONE | **NORMATIVE** | 海岸帯変形（§3.1） |
| 3 | `HARBOR_BASIN` | no | SURFACE_MODIFICATION | STANDALONE | **NORMATIVE** | 海岸帯変形（§3.1） |
| 4 | `ROCKY_CAPE` | no | SURFACE_MODIFICATION | STANDALONE | **NORMATIVE** | 海岸帯変形（§3.1、局所3Dはvolume Taskへ分離済み） |
| 5 | `PLAIN` | **yes** | FOUNDATION | STANDALONE | **NORMATIVE** | 面的terrestrial base（§3.5、ADR 0037 plain-hill adapter接続済み） |
| 6 | `HILL_RANGE` | **yes** | FOUNDATION | STANDALONE | **NORMATIVE** | 面的base（§3.2、ADR 0037接続済み） |
| 7 | `BACKSHORE_PLAINS` | →carrier | →`PLAIN`（FOUNDATION） | →carrier | PROVISIONAL | `PARENT_ALIAS` `PLAIN.context=BACKSHORE`（registry確定）。D3 lookup規則でcarrier継承 |
| 8 | `MOUNTAIN_RANGE` | **yes** | FOUNDATION | STANDALONE | PROVISIONAL | 面的base surface（§3.2） |
| 9 | `ALPINE_MOUNTAIN_RANGE` | →carrier | →`MOUNTAIN_RANGE` | →carrier | PROVISIONAL | `PARENT_SUBTYPE` `MOUNTAIN_RANGE.profile=ALPINE` |
| 10 | `GLACIAL_MOUNTAIN_RANGE` | →carrier | →`MOUNTAIN_RANGE` | →carrier | PROVISIONAL | `PARENT_SUBTYPE` `MOUNTAIN_RANGE.profile=GLACIAL` |
| 11 | `VALLEY` | **yes** | FOUNDATION | STANDALONE | PROVISIONAL | 面的V/U断面base（§3.3、`CANYON`と区別） |
| 12 | `PLATEAU` | **yes** | FOUNDATION | STANDALONE | PROVISIONAL | 面的cap elevation base（§3.2） |
| 13 | `SINGLE_ISLAND` | **yes** | FOUNDATION | STANDALONE | PROVISIONAL | water base上へland mass確定（§3.6） |
| 14 | `ARCHIPELAGO` | **yes** | FOUNDATION | STANDALONE | PROVISIONAL | land mass分布確定（§3.6） |
| 15 | `VOLCANIC_ARCHIPELAGO` | →carrier | →`ARCHIPELAGO` | →carrier | PROVISIONAL | `PARENT_SUBTYPE` `ARCHIPELAGO.origin=VOLCANIC` |
| 16 | `VOLCANIC_CONE` | **yes** | FOUNDATION | STANDALONE | PROVISIONAL | cone land mass確定（§3.2／§3.6） |
| 17 | `OCEAN_BASIN` | **yes** | FOUNDATION | STANDALONE | PROVISIONAL | marine base＋macro topology host（§3.1a） |
| 18 | `CONTINENTAL_SHELF` | **yes** | FOUNDATION | STANDALONE | PROVISIONAL | coast-to-shelf depth base（§3.1a）。**再監査事項:** coast/basin文脈依存の有無を配線時に確定 |
| 19 | `CONTINENTAL_SLOPE` | **yes** | FOUNDATION | STANDALONE | PROVISIONAL | shelf-to-basin depth base（§3.1a）。同上の再監査事項 |
| 20 | `ICE_CAP` | **yes**（未決注記） | FOUNDATION | STANDALONE | PROVISIONAL | 面的ice base（§3.9）。**未決事項:** ice表面elevationとbedrock/underlying mediumの分離モデルを配線時に確定（D1のmedium定義へiceを含めるかは本ADRで確定しない） |
| 21 | `ICE_SHEET` | **yes**（未決注記） | FOUNDATION | STANDALONE | PROVISIONAL | 同上 |
| 22 | `ABYSSAL_PLAIN` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | **初版から再分類。** HARD `WITHIN`→`OCEAN_BASIN`前提（§3.1a）でproducer定義（他owner前提なし）と矛盾するため、basin floorのmodifierとする |
| 23 | `SEAMOUNT` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | basin floor上の局所relief（§3.1a、`WITHIN`前提） |
| 24 | `ROCKY_COAST` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | 海岸帯変形（§3.1） |
| 25 | `SEA_CLIFF` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | 海岸cliff変形（§3.1） |
| 26 | `FJORD` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | land baseへのmarine channel切込み（§3.3） |
| 27 | `RIVER` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | 河道incision＋hydrology graph（§3.4、global solve→freeze） |
| 28 | `MEANDERING_RIVER` | →carrier | →`RIVER` | →carrier | PROVISIONAL | `PARENT_SUBTYPE` `RIVER.morphology=MEANDERING` |
| 29 | `BEDROCK_RIVER` | →carrier | →`RIVER` | →carrier | PROVISIONAL | `PARENT_SUBTYPE` `RIVER.channelSubtype=BEDROCK` |
| 30 | `LAKE` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | basin＋独立water level（§3.4、2.5D land-water field内で表現） |
| 31 | `OXBOW_LAKE` | →carrier | →`LAKE` | →carrier | PROVISIONAL | `PARENT_SUBTYPE` `LAKE.origin=RIVER_CUTOFF` |
| 32 | `DELTA` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | river＋sea前提のfan堆積（§3.4） |
| 33 | `WATERFALL` | no | SURFACE_MODIFICATION+VOLUME_OPERATION+FLUID_OPERATION | STANDALONE | PROVISIONAL | 2.5D fall node＋checksum-bound `WATERFALL_VOLUME` overlay＋plunge fluid（§3.4／§3.7）。**多段寄与の代表例** |
| 34 | `SPRING` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | groundwater source outflow（§3.4） |
| 35 | `KARST_SPRING` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | karst drainage surface出力（§3.9） |
| 36 | `TIDAL_CHANNEL_NETWORK` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | tidal graph、海岸base前提（§3.4） |
| 37 | `FLOODPLAIN` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | 河川随伴の低起伏surface（§3.5） |
| 38 | `MARSH` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | wetland surface変形（§3.5） |
| 39 | `MANGROVE_WETLAND` | →carrier | →`MARSH` | →carrier | PROVISIONAL | `PARENT_SUBTYPE` `MARSH.wetlandType=MANGROVE` |
| 40 | `CANYON` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | narrow incision＋terrace（§3.3） |
| 41 | `SUBMARINE_CANYON` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | shelf/slope baseへのbathymetric carve（§3.1a） |
| 42 | `ESCARPMENT` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | plateau⇄低地のscarp transition（§3.2） |
| 43 | `MORAINE_FIELD` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | glacial parent geometryへの堆積（§3.9） |
| 44 | `OUTWASH_PLAIN` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | glacial堆積（§3.9） |
| 45 | `VALLEY_GLACIER` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | 既存valleyを満たすice堆積（§3.9、面的baseの`ICE_CAP`と区別） |
| 46 | `CORAL_REEF` | no | SURFACE_MODIFICATION | STANDALONE | PROVISIONAL | marine base上のreef crest／slope（§3.6。environment/materialは合成段の対象外） |
| 47 | `LAGOON` | no | SURFACE_MODIFICATION | PARENT_REQUIRED | PROVISIONAL | `PARENT_CHILD` `CORAL_REEF.children.LAGOON`（registry確定） |
| 48 | `REEF_PASS` | no | SURFACE_MODIFICATION | PARENT_REQUIRED | PROVISIONAL | `PARENT_CHILD` `CORAL_REEF.children.REEF_PASS` |
| 49 | `VOLCANIC_CALDERA` | no | SURFACE_MODIFICATION | PARENT_REQUIRED | PROVISIONAL | `PARENT_CHILD` `VOLCANIC_OWNER.children` |
| 50 | `LAVA_FLOW_FIELD` | no | SURFACE_MODIFICATION+VOLUME_OPERATION | PARENT_REQUIRED | PROVISIONAL | `PARENT_CHILD`。2.5D levee/lobe＋lava tube hook（§3.2） |
| 51 | `GLACIAL_CIRQUE_FIELD` | no | SURFACE_MODIFICATION | PARENT_REQUIRED | PROVISIONAL | `PARENT_CHILD` `MOUNTAIN_RANGE.profile=GLACIAL.children.GLACIAL_CIRQUE_FIELD`（registry確定。レビュー指摘5cはこのコード事実で不採用） |
| 52 | `CAVE_NETWORK` | no | VOLUME_OPERATION | STANDALONE | PROVISIONAL | tunnel/chamber carve（§3.7） |
| 53 | `CAVE_ENTRANCE` | no | SURFACE_MODIFICATION+VOLUME_OPERATION | STANDALONE | PROVISIONAL | surface-volume connector（§3.7。host planへのHARD checksum bindはSTANDALONEのまま根拠列に記録） |
| 54 | `SINKHOLE` | no | SURFACE_MODIFICATION+VOLUME_OPERATION | STANDALONE | PROVISIONAL | surface collapse＋cave connection（§3.7、host cave checksum bind） |
| 55 | `LUSH_CAVE` | no | VOLUME_OPERATION | STANDALONE | PROVISIONAL | chamber carve（§3.7。wet surface/ecology hookは合成段の対象外） |
| 56 | `OVERHANG` | no | VOLUME_OPERATION | STANDALONE | PROVISIONAL | solid add＋recess carve（§3.7。addもcarveも`VOLUME_OPERATION`内でADD_SOLID→CARVE_SOLID順） |
| 57 | `SKY_ISLAND_GROUP` | no | VOLUME_OPERATION | STANDALONE | PROVISIONAL | independent solid add＋underside carve（§3.7） |
| 58 | `LAVA_TUBE` | no | VOLUME_OPERATION | STANDALONE | PROVISIONAL | swept tunnel carve（§3.7） |
| 59 | `UNDERGROUND_RIVER` | no | VOLUME_OPERATION+FLUID_OPERATION | STANDALONE | PROVISIONAL | frozen cave＋lake checksum bind、carve→単一`ADD_FLUID`（§3.7） |
| 60 | `FLOODED_CAVE` | no | FLUID_OPERATION | PARENT_BOUND_OVERLAY | PROVISIONAL | `PARENT_OVERLAY` `CAVE_OWNER.children.FLOODED_CAVE`（registry確定）、fluid-region hook |

集計: NORMATIVE 6（foundation-eligible 2＝`PLAIN`／`HILL_RANGE`、modifier 4＝coastal 4種）、PROVISIONAL 54。foundation-eligible合計17（直接13＋carrier継承4: `BACKSHORE_PLAINS`／`ALPINE_MOUNTAIN_RANGE`／`GLACIAL_MOUNTAIN_RANGE`／`VOLCANIC_ARCHIPELAGO`）。

境界事例の判断規則（人間レビュー用）:

- **foundation-eligible判定:** 面的footprint全体にland-water medium＋base elevationを**他ownerの前提なしに**確定できるkindだけをeligibleとする。HARD `WITHIN`等で親featureを前提とするkind（`ABYSSAL_PLAIN`／`SEAMOUNT`）、既存baseへの局所incision／堆積／transition（`RIVER`／`CANYON`／`ESCARPMENT`／海岸系）はeligibleにしない。
- **coastal 4種のmodifier化:** `SANDY_BEACH`／`BREAKWATER_HARBOR`／`HARBOR_BASIN`／`ROCKY_CAPE`は現在唯一のproduction接続kindだが、macro foundationのland-water境界を前提とする局所変形であるため`SURFACE_MODIFICATION`とする（`V2-18-09`非Scope「coastal 4種をfoundation上のmodifierとして再接続」と一致）。
- **glacial ice:** `ICE_CAP`／`ICE_SHEET`は暫定eligible（面的被覆を提供する唯一の候補）だが、ice表面／bedrock elevation／underlying mediumの分離は未決事項として配線時監査へ委ねる。`VALLEY_GLACIER`は既存valleyを満たす堆積でmodifier。

### D5. 昇格差分（foundation resolverとmodifier compositorの分離）

現状のoffline資産をproduction spineへ昇格するための差分を次に確定する（実装は`V2-18-09`のScope）。

1. **macro foundation production stageの新設（`core.v2.export`）。** feature合成の**前段**で、D2の正規入力からsurface domain全域のland-water medium＋provisional base-elevation rasterを生成し、background candidate（D1-2）として供給する。`MacroLandWaterTopologyPlanV2`はD2-4の限定された意味（表現・検証・freeze）でこの段のtopology正本として接続する。
2. **foundation resolver（基礎tier）。** `SurfaceFoundationMergeCompilerV2`の契約を次の2点で**拡張**する: (a) background candidateの導入 — backgroundはproducer footprint外のcellを所有し、producer footprint内では置換される。background×producerの共存は`UNDECLARED_OVERLAP`の対象外（置換契約が宣言そのもの）。(b) `OWNERLESS_CELL`／`OWNER_TIE`／`UNDECLARED_OVERLAP`拒否は**基礎tierにのみ**適用する。producer同士のoverlapは従来どおり明示`Interaction`（transition band 0..32）を要求する。integer-only決定性、whole==tiled一致は不変。
3. **modifier compositor（modifier tierの分離）。** surface modifierは**foundation ownerではない**。各cellに0..N個のmodifier寄与が共存でき（例: `PLAIN` foundation＋`RIVER` incision＋`FLOODPLAIN` shaping＋`DELTA`堆積は合法な複合）、被覆要求・単一owner要求を課さない。合成はtaxonomy §5のinteraction分類（Required／Cooperative／Overlay／Exclusive／Ordered／Transition）に従う順序付き・協調合成とし、modifier契約は適用順序・読み取りfield・書き込みfield・競合policy・transition policy・dependency relationを宣言する。既存`CoastalTransitionCompositorV2`はこのmodifier compositorのcoastal具現であり、`V2-18-09`はcoastal 4種をfoundation上のmodifierとして同compositorへ再接続する。foundation resolverとmodifier compositorは**別の型**として実装し、単一のmerge kernelへ両tierを畳み込まない。
4. **`SurfaceBaselineV2` fallbackの置換とdeprecation。** feature非所有cellの`SurfaceBaselineV2`充填を、background candidate所有へ置換する。CLI引数のdeprecation方針はD8。
5. **composition profile登録。** `CompositionProfileV2`をD3のlookup規則（canonical carrier基準）でper-kind登録し、基礎tier／modifier tierの振り分け正本とする。V2-15 stage gateの「composition role登録」はこの登録＋D4のPROVISIONAL→確定監査を指す。

### D6. Resource budget

macro foundation stageは新しいhard-codeを追加せず、既存のscale契約（[ADR 0016](0016-scale-classes-and-execution-planning.md)）と既存budget上限に従う。

- **scale上限:** `MacroLandWaterTopologyPlanV2`はMEDIUM ceiling（`ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING`）、最大raster cell数`MEDIUM_MAXIMUM_CELLS`、最大graph work units 64,000,000、regions≤64、adjacencies≤256で既に固定済み。LARGE（1024超）は[AGENTS.md §8](../../AGENTS.md)どおり全域full解像度常駐を禁止し、macro foundationでも`SUPPORTED`と表現しない。
- **surface foundation budget:** `SurfaceFoundationPlanV2.ResourceBudget`（`surface-foundation-budget-v1`）が owners≤64、interactions≤256、fields=5、estimatedCpuWorkUnits≤64,000,000、maximumWorkingBytes≤128 MiB、estimatedArtifactBytes≤256 MiB、maximumCanonicalBytes≤64 KiB、supportRadiusXZ 0..32を既に強制する。background candidateはowner数1として計上する。5 field（`surface-class`／`elevation`／`residual`／`owner-index`／`transition-weight`、各U16）を正本とする。
- **実行形態:** [AGENTS.md §8](../../AGENTS.md)に従い、coarse大域solve（land-water topology／base elevation）をtile化前にfreezeし、streaming tileで実行する。dense voxel配列を正本または常駐配列にしない。base elevation／land-waterは2.5D fieldとしてversion付きsidecar＋strict indexへ保存し、Blueprint JSONへ全field値を埋め込まない。
- 新しい寸法・tile・予算のhard-codeを追加せず、既存admission（queue／memory／disk／decode）を再利用する。

### D7. 検証の分担（coverage単独をgate根拠にしない）

owner-index被覆100%だけでは正しいfoundationを保証しない（全cell LAND・無効elevation・field間不整合でも100%になり得る）ため、検証を次の3層へ分担する。分担は[task-index §18](../design-v2/task-index.md)の各Task Scopeと整合させ、本ADRが他TaskのScopeを勝手に拡張しない。

1. **kernel不変条件（`V2-18-09`実装時から常時fail-closed）:** foundation resolver／field書き出しの内部契約として、medium値の有効性（LAND/WATER以外の拒否）、base elevationの有効域、owner-index／medium／elevation field間の整合、`OWNER_TIE`＝0、undeclared foundation overlap＝0を生成時に拒否する。これは新設コード経路の契約そのもの（`HardPreflightGateV2`と同型）であり、既存traffic を止めるgateではないためreport-only先行原則に反しない。
2. **owner coverage gate（`V2-18-10`）:** task-index定義どおり「surface foundation owner被覆100%のexport必須化」だけを扱う。coverage計測は`V2-18-02`の既存metricを用いる。
3. **conformance／precondition検証（`V2-18-09`のmodifier再接続＋`V2-18-11` portfolio）:** coastal modifierのprecondition（beachがLAND/WATER境界bandへ接続、harbor basinがWATER側、breakwater landfallがLANDへ接続、capeが海岸境界と交差）と形状conformance（EDGE実測、beach↔backshore land連続性、breakwater両armのland接続）は、`V2-18-11`が常設回帰化する（task-indexに既にScope化済み）。desired／actual residualは`V2-18-07`の`ConformanceResidualEvaluatorV2`を用いる。

### D8. `SurfaceBaselineV2` CLI引数のdeprecation方針

`V2-18-09`での扱いを次に固定する。即時削除・即時無効化はしない（実質的な公開contract破壊を避ける）。

1. **受理継続:** 引数は受理し続ける。明示foundation入力（D2）が存在するrequestでは引数を**無視**し、NON_GATINGの警告（stable rule ID `v2.cli.surface-baseline-deprecated`、`DiagnosticGateContractV2`の既存分類に従う）をCLI summaryへ表示する。manifestへは書き込まない。
2. **legacy request（foundation入力なし）:** `V2-18-10`のcoverage gate有効化までは従来どおりbaseline経路で動作する（「どの時点でも動作するproduction経路を1つ保つ」）。`V2-18-10`以降、foundation入力なしのrequestはowner coverage不足としてfail-closed拒否され、baseline引数はそれを回避できない（override不可）。
3. **削除条件:** 引数の削除は`V2-18-12` Phase gate（人間承認）以降の独立Task＋本ADR amendmentを要する。既存fixture（foundation入力なし）の移行は`V2-18-09`／`V2-18-10`の各Taskが自Taskのfixture更新として行う（`V2-18-03`のhonored fixture移行と同じ方式）。

### D9. 凍結と可変域

- **不変（semantic checksum）:** 本ADRはproduction出力を変えない。まだ再配線されていないkindのterrain field／tile／block **semantic checksum**は不変とする。
- **可変（container byte）:** `V2-18-09`実装時、coastal単体planのchecksum変更は本ADR承認範囲（[task-index §18](../design-v2/task-index.md) `V2-18-09` gate、model-assignment「coastal checksum変更はADR承認範囲＝人間確認」）。intent／blueprint canonicalChecksum／manifest容器byteは可変。
- **v1境界:** v1 Schema、generator `3.0.0-phase6`、Release format 1、v1 placement／Undo、既存golden／checksumは不変。本ADRはv2 spineのみを対象とし、[ADR 0035](0035-v1-retirement-governance.md)のlegacy read／verify／migrate境界を変更しない。
- **停止条件:** 本契約の実装が新capability名、新artifact type／version、Release format変更、または公開contract破壊を要求する場合は停止し、別ADR／Task IDを登録する。本ADRの範囲では、既存`surface-2_5d` capability・既存`core.v2.export` spine・既存binding・既存merge kernel（契約拡張）・既存coastal compositorの再利用で足り、新capability／formatは不要と判断する。D2-3のpolygon region型追加だけは独立Schema Taskへ分離済みである。

## Consequences

- surface production経路は「merge後の各cellにちょうど1つのeffective foundation owner」という単一の明確な不変条件を持ち、candidate／background／effective の3概念でmacro baseとproducerの共存が矛盾なく定義される。監査で確定した73%のbaseline fallbackは、`V2-18-09`のmacro foundation stage配線後にbackground candidate所有へ置換される。
- `CompositionProfileV2`（foundationEligible＋stage集合＋parentPolicy、canonical carrier lookup）により、owner coverage gate（`V2-18-10`）とV2-15 stage gateが「foundation-eligibleなowner被覆」を機械判定でき、`OVERHANG`／`SINKHOLE`／`WATERFALL`等の多段寄与も表現できる。
- foundation resolver（exactly-one）とmodifier compositor（0..N interaction合成）の分離により、`PLAIN`＋`RIVER`＋`FLOODPLAIN`＋`DELTA`のような正常な複合が単一ownerモデルで拒否される設計事故を防ぐ。
- coastal 4種はmodifierへ再分類され、`V2-18-09`でfoundationの上に再接続されることで、`CoastalValidationInputV2(blueprint, fields, fields)`のdesired＝actual自己参照（監査item 3のproduction側）が解消可能になる（`V2-18-07`が契約と型を用意済み）。
- D4のPROVISIONAL 54 kindは配線Taskのfield監査を経て確定するため、未検証分類が「設計正本」として凍結される事故を防ぐ。確定時に変更が必要なら本ADRをamendする。
- 本ADRは実装を伴わないため、terrain semantic checksum／Release checksum／全既存testは不変である。docs（roadmap／task-index／model-assignment）と本ADRのみを同期する。
- 承認前は`Proposed`であり、`V2-18-09`は開始できない。

## Alternatives considered

### 単一値の`CompositionRoleV2` enum（本ADR初版の設計）

各FeatureKindへ`FOUNDATION_PRODUCER`／`SURFACE_MODIFIER`／`VOLUME_CARVER`／`FLUID_OVERLAY`／`CHILD_COMPONENT`の1値を割り当てる案。第一レビューが指摘したとおり、taxonomyが明記する多段寄与（`OVERHANG`＝solid add＋recess carve、`SINKHOLE`＝surface＋volume、`WATERFALL`＝surface＋volume＋fluid、`UNDERGROUND_RIVER`＝carve＋fluid）を表現できず、親依存性（child/overlay）と合成段を同一軸へ混在させる。`VOLUME_CARVER`という名称もADD_SOLIDを含む実態と乖離する。D3のprofile（eligible＋stage集合＋parentPolicy）へ置換して不採用。

### `FeaturePrimaryRoleV2`へ第9値（例:`FOUNDATION`）を追加する

catalog roleへproducer/modifier区別を畳み込むと、著述・利用可否の意味と合成時役割が再びconflateする（監査が指摘した本質問題の再発）。既存catalog checksumとconsistency verifierへも波及する。独立軸の方が影響が閉じるため不採用。

### macro foundationを新capability／artifact type（例:`foundation-plan`）として公開する

新format／verifier／catalog／dependency matrixが必要になり、[task-index §18](../design-v2/task-index.md) 共通停止条件に触れる。[ADR 0037](0037-foundation-surface-to-surface-2_5d-mapping.md)が既にfoundationを既存`surface-2_5d`へ写像する方針を`Accepted`にしており、macro foundation stageも同じspine内に閉じるべきである。不採用。

### coverage gateを本ADR／`V2-18-09`でfail-closedにする

foundation producer配線と同時にgateをfail-closedにすると、producer未整備のkindを含むrequestや、明示foundation入力を持たない既存requestが即座に全停止する。[audit §4](../audits/macro-foundation-conformance-audit-2026-07-22.md)は「report-only先行、どの時点でも動作するproduction経路を1つ保つ」を要求する。fail-closed昇格は`V2-18-10`単独へ分離するため不採用（D7・D8）。

### `EDGE_CLASSIFICATION`から内部land-water境界を推測して被覆を埋める

外周band比率だけから内部海岸線を生成すると、HARD情報の推測補完となり[AGENTS.md §6](../../AGENTS.md)（欠落した位置・形状・関係をhard constraintとして補完しない）と`V2-18-09`非Scopeに反する。D2で明示禁止とする。

### 明示LAND／OCEAN polygon region型を本ADRで即時導入する

`TerrainIntentV2`へのregion collection追加は独立したSchema変更であり、V2-18-09の1 Task予算（mask経路で完了可能）を超える。契約要件（medium／geometry／baseElevationSource／priority／provenance必須、mask正本）だけをD2-3で固定し、導入は新Task＋amendmentへ分離する。
