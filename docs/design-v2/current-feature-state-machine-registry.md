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
- Module bindings: dedicated=17, diagnostic=43
- States: production-connected=4, offline-or-plan-level=48, enum-schema-only=4, child-plan-only=4
- Production-connected: `BREAKWATER_HARBOR`, `HARBOR_BASIN`, `ROCKY_CAPE`, `SANDY_BEACH`
- Enum/Schema only: `BACKSHORE_PLAINS`, `BEDROCK_RIVER`, `FLOODED_CAVE`, `GLACIAL_CIRQUE_FIELD`
- Child-plan-only: `LAGOON`, `LAVA_FLOW_FIELD`, `REEF_PASS`, `VOLCANIC_CALDERA`
- Canonical projection SHA-256: `8b3cf144445c34e71a3dfd8f87364ca4378d2bc2bfb3f8ef0785b31fdf5ae00c`

<!-- current-feature-state-registry-v1:end -->

V2-15-10 (ADR 0039 Candidate A) moves `RIVER` from the diagnostic module binding onto the dedicated
`HydrologyRiverModuleV2` (shared with `MEANDERING_RIVER`), so dedicated bindings move from 16 to 17
and diagnostic bindings move from 44 to 43. `RIVER`'s current state stays `OFFLINE_OR_PLAN_LEVEL`
(unchanged set membership; only the module-binding count changes), and `PRODUCTION_CONNECTED`
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
