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
`standalone_usage`も`PARTIAL`のまま（coastal 4種必須の緩和は`V2-19-09`）である。

<!-- public-dispatch-reachability-v1:start -->

- Contract: `public-dispatch-reachability-v1`
- Axes: Feature Support Catalog support columns, public dispatch reachability, and block materialization are independent; none implies another, and an intentional no-op route is never Feature-promotion evidence
- Reachability: production-connected=4, offline-production=3, contract-only=1, not-publicly-dispatchable=52
- Production-connected (materialized surface owners): `BREAKWATER_HARBOR`, `HARBOR_BASIN`, `ROCKY_CAPE`, `SANDY_BEACH`
- Offline-production, block-effect measured by the portfolio: `MEANDERING_RIVER`, `PLAIN`, `RIVER`
- Offline-production, plan-only until portfolio block-effect evidence: (none)
- Contract-only diagnostic inputs: `BACKSHORE_PLAINS`
- Canonical projection SHA-256: `7d46e315299d585efb47dbd0c51d5ca761b00a1e79f9c16fb901ec63459fedcc`

<!-- public-dispatch-reachability-v1:end -->
