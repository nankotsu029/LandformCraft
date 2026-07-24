# Current Feature State Machine Registry v2

V2-15-02 establishes a read-only projection of the current public `FeatureKind` state. It is not
a target taxonomy, migration plan, Release artifact, or dispatch mechanism. In particular, it
does not adopt the 57-kind proposal and it does not change Schema values, catalog capability,
checksums, generator routing, or Paper support.

The CI test projects `TerrainIntentV2.FeatureKind`, the `terrain-intent-v2` Schema enum, the
compile-time module bindings, and the sealed Feature Support Catalog into the same 60-key set.
Missing, unknown, duplicate, or module-declared-binding mismatches fail closed. The text between
the following markers is a checked projection: changing any of those sources requires an explicit
documentation update in the same change.

<!-- current-feature-state-registry-v1:start -->

- Contract: `current-feature-state-registry-v1`
- Sources: enum=60, Schema=60, module bindings=60, catalog FeatureKind entries=60
- Module bindings: dedicated=18, diagnostic=42
- States: production-connected=4, offline-or-plan-level=48, enum-schema-only=4, child-plan-only=4
- Production-connected: `BREAKWATER_HARBOR`, `HARBOR_BASIN`, `ROCKY_CAPE`, `SANDY_BEACH`
- Enum/Schema only: `BACKSHORE_PLAINS`, `BEDROCK_RIVER`, `FLOODED_CAVE`, `GLACIAL_CIRQUE_FIELD`
- Child-plan-only: `LAGOON`, `LAVA_FLOW_FIELD`, `REEF_PASS`, `VOLCANIC_CALDERA`
- Canonical projection SHA-256: `e2c1ac37275756b4769b2e4158ebefd563ae7d4ada22a4811587b18e9ac85ad0`

<!-- current-feature-state-registry-v1:end -->

V2-15-10 (ADR 0039 Candidate A) moves `RIVER` from the diagnostic module binding onto the dedicated
`HydrologyRiverModuleV2` (shared with `MEANDERING_RIVER`), so dedicated bindings move from 16 to 17
and diagnostic bindings move from 44 to 43. V2-19-07 registers the V2-9-02 `LandformPlainModuleV2`
in the built-in catalog and binds `PLAIN` to it (dedicated 17 → 18, diagnostic 43 → 42) so the first
macro foundation producer can take the same ADR 0039 Candidate A offline production route. Both
kinds' current state stays `OFFLINE_OR_PLAN_LEVEL`
(unchanged set membership; only the module-binding counts change), and `PRODUCTION_CONNECTED`
remains the existing coastal four only. `OFFLINE_OR_PLAN_LEVEL` does not
mean public export, CLI, Paper application, or `SUPPORTED` promotion; those connections remain
separate V2-15 leaf Tasks and V2-17 real-host evidence gates. `ENUM_SCHEMA_ONLY` and
`CHILD_PLAN_ONLY` remain current-state classifications, not deletion or migration authority.

The authoritative Task scope is [Task Index](task-index.md); the current inventory rationale is
the [2026-07-22 terrain catalog audit](../audits/terrain-catalog-full-audit-2026-07-22.md).
The separate [canonical target registry](canonical-feature-target-registry.md) projects these
compatibility sources into the ADR 0036 `CANONICAL_V2` authoring contract; it does not rewrite
this frozen current-state inventory.

V2-15-05 consumes this projection through the separate immutable
`production-dispatch-registry-v1`. That application registry requires an exact current module
binding and a complete generator／validator／preview／export handler chain before it can select a
production route. It does not mutate this source registry: the production-connected set remains
the coastal four, and `BACKSHORE_PLAINS` remains a diagnostic, contract-only compatibility input.

## Public dispatch reachability（V2-19-01、支援列とは別軸の表示）

2026-07-23横断監査は、Feature Support Catalogのsupport列・公開dispatch到達性・block実体化の
3軸が表示上混同され、「catalogがexport SUPPORTEDでdispatch routeもあるのにblockを1個も変えない」
V2-15-10型の状態が不可視だったことを確定した。以下は`PublicDispatchReachabilityV2`
（`public-dispatch-reachability-v1`、表示専用・gate無し）の検査済み投影であり、support列
（sealed catalogが所有）とは**別表示**である。`OFFLINE_PRODUCTION` routeの
block materializationがPLAN_ONLYからMATERIALIZEDへ変わるのは、intent-conformance portfolioの
final canonical block stream実測（非空効果）が同一変更内に揃った場合だけである。意図的no-op route
（capability spine smoke）はFeature昇格の証拠に使えない。

最初の適用は`V2-19-05`（RIVER block materialization）で、`RIVER`／`MEANDERING_RIVER`を
PLAN_ONLYからMATERIALIZEDへ移した。両kindは1つのplan shape（V2-15-10のbridge）を共有するが、
表示はkindごとの実測を要求するため、portfolio caseも`harbor-cove-64-honored-river`（RIVER）と
`harbor-cove-64-honored-meander`（MEANDERING_RIVER）の2件を持つ。block materializationは
support列でもPaper到達性でもない: 両kindの`paper_apply`は`EXPERIMENTAL`のままで、昇格はV2-17の
実機証跡だけが行う。

2件目の適用は`V2-19-07`（foundation producer tier＋PLAIN vertical slice）で、`PLAIN`を
`OFFLINE_PRODUCTION`＋MATERIALIZEDとして追加した。`PLAIN`はcoastal modifierではなく
`surface-2_5d` coastal pipelineの**foundation tier**（ADR 0038 D1）で実行されるため、pipeline idは
`v2.production.surface-2_5d.coastal`である。実測は`harbor-cove-64-honored-plain`と
baseline `harbor-cove-64-honored`のfinal canonical block stream差分（changed 1238＝`SOLID_SHAPE` 713／
`MATERIAL` 525／`FLUID` 0）で、`paper_apply`は`UNSUPPORTED`のまま（V2-17専管）、
`standalone_usage`も`PARTIAL`のままである。`V2-19-09`（[ADR 0040](../adr/0040-coastal-contributor-set-cardinality.md) D1）が
coastal 4種必須を撤廃したため`PLAIN`単体intentは実際にexportできる（fixture
`harbor-cove-64-honored-coastless`）が、`SUPPORTED`昇格には単体構成でのblock materialization証拠が
別途必要であり、ADR 0040 D7が後続leafへ委ねている。本registryのprojection（reachability・
materialization分類・binding件数）は`V2-19-09`で不変である。

3件目の適用は`V2-15-11`（`LAKE`基本 public wiring）で、`LAKE`を`OFFLINE_PRODUCTION`＋
MATERIALIZEDとして追加した。`LAKE`は既に`HydrologyLakeModuleV2`（V2-3-04起源）へdedicated
bindingが確定していたためmodule binding件数は不変で、`hydrology-plan`共有pipeline（RIVER／
MEANDERING_RIVERと同一pipeline id）へ配線した。`LakeBedMaterializationV2`（`lake-bed-materialization-v1`）
がCLOSED terminal policyの基本だけをCARVE_SOLID→ADD_FLUIDで実体化する（OPEN_SPILLの
spillway出口はまだblock効果へ配線されておらず、`v2.lake.spill-not-wired`でfail closed拒否する —
RIVERのmarine mouth「まだ配線されていない」と同じ姿勢）。実測は`harbor-cove-64-honored-lake`と
baseline `harbor-cove-64-honored`のfinal canonical block stream差分で、宣言effect class
`{SOLID_SHAPE, FLUID}`と完全一致する。oxbow cutoff subtype（`OXBOW_LAKE`）は既存の
`OxbowLakePlanCompilerV2`／`OxbowLakeGeneratorV2`（V2-10起源）が関係graph束縛（HARD
`SUPPORTED_BY`host＋HARD `ORIGINATES_AT`parent river）を要求する構造的に別の生成経路であり、
LAKEの基本ring/spill生成経路と同一kernelを共有しない。1 Taskで2個目の独立generatorを抱えない
という実行規約（task-execution-guide.md §4）に従い本Taskの対象外とし、後続leaf`V2-15-48`へ
分離した（詳細は[Task Index](task-index.md) §15.2 `V2-15-11`／`V2-15-48`行）。

4件目の適用は`V2-15-12`（`CANYON` public wiring）で、`CANYON`を`OFFLINE_PRODUCTION`＋
MATERIALIZEDとして追加した。`CANYON`は既に`LandformCanyonModuleV2`（V2-3-05起源）へdedicated
bindingが確定していたためmodule binding件数は不変で、`hydrology-plan`共有pipeline（RIVER／
LAKEと同一pipeline id）へ配線した。CANYONは独立した地形ではなく既存の`MEANDERING_RIVER` reach
へHARD `WITHIN`で束縛され共有bedを持つ横断面のみで、fluidを一切所有しない
（`CanyonBedMaterializationV2`、`canyon-bed-materialization-v1`、CARVE_SOLIDのみ。共有channel
列は常にRIVERのCARVE_SOLID→ADD_FLUIDが優先するcomposition順序）。実測は
`harbor-cove-64-honored-canyon`とbaseline `harbor-cove-64-honored`のfinal canonical block
stream差分（宣言effect class`{SOLID_SHAPE, FLUID}`、FLUIDは共有RIVERの寄与）に加え、
`CanyonBlockConformanceV2`がfinal tile streamだけからcanyon自身の彫り込みがdry（fluid非所有）
であることを構造的に確認する。本leafの着手中、HARD relationの評価に使われていた
`HardPreflightGateV2`／`DiagnosticGateContractV2`の「production-connected」判定が
`PRODUCTION_CONNECTED`（coastal 4、Paper完全接続）だけを見ており、ADR 0039の
`OFFLINE_PRODUCTION`route（RIVER／LAKE／CANYON自身を含む）を宛先とするHARD relationを
誤って`v2.preflight.hard-relation-unconsumed`で拒否する潜在的gapが判明したため、
`HardPreflightGateV2`が`PublicDispatchReachabilityV2`も参照するよう是正した
（`DiagnosticGateContractV2.isProductionConnected`自体の意味・戻り値は不変。他箇所での
利用・pinned testへの影響なし）。generator field math・v1 golden・coastal既存Release
checksum・Release format不変、full `./gradlew test`／`./gradlew build` PASS。

5件目の適用は`V2-15-13`（`WATERFALL` public wiring）で、`WATERFALL`を`OFFLINE_PRODUCTION`＋
MATERIALIZEDとして追加した。`WATERFALL`は既に`HydrologyWaterfallModuleV2`（V2-3-06起源）へ
dedicated bindingが確定していたためmodule binding件数は不変で、`hydrology-plan`共有pipeline
（RIVER／LAKE／CANYONと同一pipeline id）へ配線した。WATERFALLは独立した地形ではなく既存の
`MEANDERING_RIVER` reachへHARD `ON_PATH_OF`で束縛される落差であり、本leafが実体化するのは
**2.5D surfaceが所有する範囲＝落ち口下流のplunge basin**だけである
（`WaterfallBasinMaterializationV2`、`waterfall-basin-materialization-v1`、
CARVE_SOLID→ADD_FLUID）。basinのfootprintはhost riverのfrozen channel cellを構成上すべて除外し
（`HOST_CHANNEL`）、lip crestの下流半平面へclipされる（`CREST_HALF_PLANE`）ため、host channelの
bed／water決定と衝突しない。実測は`harbor-cove-64-honored-waterfall`とbaseline
`harbor-cove-64-honored`のfinal canonical block stream差分（宣言effect class
`{SOLID_SHAPE, FLUID}`、host reachの寄与を含む）に加え、`WaterfallBlockConformanceV2`が
final tile streamだけからbasin深さ・host reachとの水continuity・**実測落差＝宣言drop**・
containment envelopeを構造的に確認する。

**本leafのScopeはsurface側だけである。** `WATERFALL_VOLUME`（falling column `ADD_FLUID`＋
behind-fall `CARVE_SOLID`、`WaterfallVolumePlanV2`／`waterfall-volume-contract-v1`）は
`sparse-volume` capability prefixを要し、そのprefixは`environment-fields`を含む。
`EnvironmentFieldsExportPipelineV2`は`V2-19-10`の判断で`meanderingRiverPlans`を持つBlueprintを
fail-closedで拒否する（environment field stackがriver proximity／freshwater discharge入力を
実測できず、coast-onlyのwater conditionを宣言済みriverの傍らで黙って報告しないため）。
WATERFALLはhost riverなしに宣言できないので、volume overlayの配線はこのenvironment側の
実測化を前提とする。両者はそれぞれ独立したsubsystemであり、task-execution-guide.md §4に従い
本leafでは抱えず、後続leaf`V2-15-49`（environment stackのhydrology proximity実測化）と
`V2-15-50`（`WATERFALL_VOLUME` sparse-volume overlay配線）へ分離した
（詳細は[Task Index](task-index.md) §15.2 `V2-15-13`／`V2-15-49`／`V2-15-50`行）。
`WATERFALL_VOLUME`はFeatureKindではなくVOLUME_OVERLAYのままであり、本leafでも
FeatureKind化していない。generator field math・v1 golden・coastal既存Release checksum・
Release format不変。

<!-- public-dispatch-reachability-v1:start -->

- Contract: `public-dispatch-reachability-v1`
- Axes: Feature Support Catalog support columns, public dispatch reachability, and block materialization are independent; none implies another, and an intentional no-op route is never Feature-promotion evidence
- Reachability: production-connected=4, offline-production=6, contract-only=1, not-publicly-dispatchable=49
- Production-connected (materialized surface owners): `BREAKWATER_HARBOR`, `HARBOR_BASIN`, `ROCKY_CAPE`, `SANDY_BEACH`
- Offline-production, block-effect measured by the portfolio: `CANYON`, `LAKE`, `MEANDERING_RIVER`, `PLAIN`, `RIVER`, `WATERFALL`
- Offline-production, plan-only until portfolio block-effect evidence: (none)
- Contract-only diagnostic inputs: `BACKSHORE_PLAINS`
- Canonical projection SHA-256: `1c11c1207d666d7a9f91a74f23149b987252fc03eee67dda67d037d723ca058d`

<!-- public-dispatch-reachability-v1:end -->
