# Surface foundation examples (V2-9-01 / … / V2-10-01 / V2-10-02)

`surface-foundation-plan-v2.json` is the empty/minimal sealed plan for the shared 2.5D
surface foundation contract (`surface-foundation-field-contract-v1`).

- Five field bindings: surface-class, elevation, residual, owner-index, transition-weight
- Empty owners/interactions (valid minimal plan)
- Scale class / tile / halo admitted through `ScaleProfileV2` / `ScaleAdmissionV2`
- No feature kind is `SUPPORTED` by this contract alone

V2-9-02 adds EXPERIMENTAL `PLAIN` and `HILL_RANGE` vertical slices:

- `plain-hill-slice.terrain-intent-v2.json` — combined plain polygon + hill spline with
  `OVERLAPS` transition band
- `plain-plan-v2.json` / `hill-range-plan-v2.json` — sealed plan artifacts (written by tests)

V2-9-03 adds EXPERIMENTAL `MOUNTAIN_RANGE` and `VALLEY` vertical slices:

- `mountain-valley-slice.terrain-intent-v2.json` — range spline + U-profile valley with
  `OVERLAPS` transition band
- `mountain-range-plan-v2.json` / `valley-plan-v2.json` — sealed plan artifacts (written by tests)
- Component IDs (`peak-*`, `saddle-*`, `spur-*`, `pass-*`, `foothill-*`) are derived COMPONENT
  roles, not FeatureKinds
- Specialized `ALPINE_MOUNTAIN_RANGE` / `GLACIAL_MOUNTAIN_RANGE` / `FJORD` seeds are unchanged

V2-9-04 adds EXPERIMENTAL public `RIVER` (distinct from `MEANDERING_RIVER`):

- `general-river-slice.terrain-intent-v2.json` — source→mouth reach graph vertical slice
- `river-plan-v2.json` — sealed general river plan (written by tests)
- `MeanderingRiverSubtypeBridgeV2` suggests `RiverParameters` from legacy meander params without
  changing meander serialization
- `HydrologyRiverModuleV2` / `MEANDERING_RIVER` remain `SUPPORTED` and catalog-bound

V2-9-05 adds EXPERIMENTAL `FLOODPLAIN` and `MARSH` hydrologic surfaces:

- `floodplain-river-marsh-slice.terrain-intent-v2.json` — river + floodplain + marsh with
  adjacency／overlap relations
- `floodplain-plan-v2.json` / `marsh-plan-v2.json` — sealed plans (written by tests)
- Independent ownership (`foundation.floodplain.*` / `foundation.marsh.*`) distinct from
  river-owned `foundation.river.floodplain-mask`
- Surface merge is floodplain+marsh; river graph compiles separately
- `MANGROVE_WETLAND` module／seeds remain unchanged

V2-9-06 adds EXPERIMENTAL `ROCKY_COAST` and `SEA_CLIFF` coast／cliff foundation:

- `rocky-coast-cliff-slice.terrain-intent-v2.json` — rocky coast + sea cliff + optional beach
  transition relation（surface merge owners are coast+cliff only）
- `rocky-coast-plan-v2.json` / `sea-cliff-plan-v2.json` — sealed plans (written by tests)
- Cliff `hostSupportAabb` handoff compiles sea-cave／overhang without rewriting those compilers
- Modules stay `EXPERIMENTAL` and catalog-unregistered; `ROCKY_CAPE` checksums unchanged

V2-9-07 adds EXPERIMENTAL `SINGLE_ISLAND`／`ARCHIPELAGO`／`VOLCANIC_CONE` island／cone foundation:

- `single-island-slice.terrain-intent-v2.json` — POINT island mass／shore／drainage／apron
- `archipelago-slice.terrain-intent-v2.json` — MULTI_POINT group with dry-land gap／saddles
- `volcanic-cone-slice.terrain-intent-v2.json` — POINT cone／crater／radial drainage
- `*-plan-v2.json` sealed plans are written by tests
- `VolcanicIslandConeAdapterV2` suggests foundation params from legacy volcanic without
  changing volcanic checksums／hooks／module version
- Modules stay `EXPERIMENTAL` and catalog-unregistered（diagnostic binding）

V2-9-08 adds EXPERIMENTAL `OCEAN_BASIN`／`CONTINENTAL_SHELF`／`CONTINENTAL_SLOPE` bathymetry:

- `ocean-basin-slice.terrain-intent-v2.json`／`continental-shelf-slice.terrain-intent-v2.json`／
  `continental-slope-slice.terrain-intent-v2.json` — individual 2.5D depth／ownership slices
- `coast-to-basin-transect.terrain-intent-v2.json` — west shelf → mid slope → east basin with
  optional `ROCKY_COAST` adjacency；`ADJACENT_TO`／`OVERLAPS`／`FLANKS` chain required
- Sealed `ocean-basin-plan-v2.json`／`continental-shelf-plan-v2.json`／`continental-slope-plan-v2.json`
  are written by tests
- Depth／slope／coast-distance／ownership／fluid-column-hint fields；underwater column export streams
  per `(x,z)` without dense 3D water arrays
- Modules stay `EXPERIMENTAL` and catalog-unregistered（diagnostic binding）；not `SUPPORTED`

V2-9-09 adds EXPERIMENTAL `SUBMARINE_CANYON` bathymetric carve（distinct from surface `CANYON`）:

- `submarine-canyon-positive.terrain-intent-v2.json` — shelf→slope→basin SPLINE with
  `ORIGINATES_AT`／`CARVES_THROUGH`／`DRAINS_TO` host relations
- `submarine-canyon-out-of-host.terrain-intent-v2.json` — spline outside host polygons
- Blocked missing-relation coverage is asserted in tests by mutating the positive fixture
- `submarine-canyon-plan-v2.json` — sealed plan artifact (written by tests)
- Reuses `CanyonCrossSection` math patterns only；`LandformCanyonModuleV2`／`CanyonPlanV2` unchanged
- Modules stay `EXPERIMENTAL` and catalog-unregistered（diagnostic binding）；not `SUPPORTED`

V2-9-10 adds EXPERIMENTAL `CAVE_ENTRANCE` surface-volume connector:

- `cave-entrance-positive.terrain-intent-v2.json` — mountain host＋`CAVE_NETWORK` stub＋POINT entrance
  with HARD `ENTRANCE_OF`／`SUPPORTED_BY`
- `cave-entrance-orphan.terrain-intent-v2.json` — missing `ENTRANCE_OF`
- `cave-entrance-plan-v2.json` — sealed plan artifact (written by tests)
- Host cave is a frozen `CaveNetworkPlanV2` bound by featureId＋canonicalChecksum（do not mutate
  `examples/v2/volume/cave-network-plan-v2.json`）
- Modules stay `EXPERIMENTAL` and catalog-unregistered（diagnostic binding）；not `SUPPORTED`

V2-9-11 adds EXPERIMENTAL `UNDERGROUND_RIVER`／`FLOODED_CAVE` flooded-volume connection:

- `underground-river-positive.terrain-intent-v2.json` — cave stub＋`FLOODED_CAVE` hook＋SPLINE river
  with HARD `WITHIN`
- `underground-river-plan-v2.json` — sealed plan artifact (written by tests)
- Hosts are frozen `CaveNetworkPlanV2`＋`UndergroundLakePlanV2` bound by featureId＋canonicalChecksum
  （do not mutate `examples/v2/volume/cave-network-plan-v2.json` or `underground-lake-plan-v2.json`）
- Ordered CSG: channel `CARVE_SOLID` then single `ADD_FLUID` using the lake `fluidBodyId`
- Modules stay `EXPERIMENTAL` and catalog-unregistered（diagnostic binding）；not `SUPPORTED`

V2-9-12 adds macro land-water topology as `MACRO_CONSTRAINT`（no FeatureKind）:

- Manual `LAND_WATER` mask＋`ZONE_LABEL` map compile to `macro-land-water-topology-plan-v2.json`
- Region kinds: bay／cove／headland／peninsula／isthmus／strait／enclosed-basin／coastal-embayment
- Rejects disconnected strait、collapsed isthmus、nested basin、ambiguous boundary、budgets
- Whole／tile／thread checksum equality freezes the plan for later coast／bathymetry handoff
- Coverage lives in `MacroLandWaterTopologySliceCompilerV2Test`

V2-9-13 extends the general `RiverPlanV2` IR with graph roles（no new FeatureKinds）:

- `river-graph-roles-plan-v2.json` — headwater／stream／tributary／confluence／distributary／rapids
  plus sandbar／river-island／plunge-pool child ownership on the same general river IR
- `waterfall-chain-plan-v2.json` — `WATERFALL_CHAIN` COMPOSITE_PRESET（multiple WATERFALL nodes＋
  plunge pools；not a dedicated world generator）
- `foundation-river-graph-roles-validation-artifact-v2.json` — flow／elevation／modifier／child／
  chain metrics
- Coverage lives in `FoundationRiverGraphRolesSliceCompilerV2Test`
- Existing `meandering-river`／`delta-distributary-fan`／`waterfall-2_5d-skeleton` fixtures are
  unchanged；simple `river-plan-v2.json` still compiles from `general-river-slice`

V2-10-01 adds EXPERIMENTAL glacial ice foundation（common contract）:

- `valley-glacier-positive.terrain-intent-v2.json` — `VALLEY_GLACIER`＋valley bed＋meltwater river
- `ice-cap-positive.terrain-intent-v2.json` — `ICE_CAP`＋mountain bed
- `ice-sheet-positive.terrain-intent-v2.json` — `ICE_SHEET`＋plain bed
- `ice-fjord-composition.terrain-intent-v2.json` — `ICE_FJORD` COMPOSITE_PRESET inputs（`FJORD`＋
  `VALLEY_GLACIER`＋cold climate；no `ICE_FJORD` FeatureKind）
- `glacial-ice-plan-v2.json`／`ice-fjord-plan-v2.json` — sealed plans (written by tests)
- Shared `GlacialIcePlanV2` owns flow／thickness／bed contact／meltwater／cold climate＋snow profile and
  bounded sparse `ADD_SOLID`（no dense ice voxel store）
- Modules stay `EXPERIMENTAL` and catalog-unregistered（diagnostic binding）；not `SUPPORTED`
- Specialized `ALPINE`／`GLACIAL_MOUNTAIN_RANGE`／`FJORD` hydrology seeds remain unchanged

V2-10-02 adds EXPERIMENTAL glacial deposition foundation:

- `moraine-field-positive.terrain-intent-v2.json` — `MORAINE_FIELD` polygon＋`VALLEY_GLACIER` parent stub
  （glacial parent bind by geometry checksum）
- `outwash-plain-positive.terrain-intent-v2.json` — `OUTWASH_PLAIN` polygon downstream of valley glacier with
  optional meltwater river handoff
- `permafrost-plain-profile.terrain-intent-v2.json` — `PLAIN` under cold climate only（no
  `PERMAFROST_PLAIN` FeatureKind）
- `moraine-field-plan-v2.json`／`outwash-plain-plan-v2.json`／`permafrost-plain-profile-v2.json` — sealed
  plans (written by tests)
- Glacial parent binding uses `codec.geometryChecksum(parent.geometry())` as
  `glacialParentCanonicalChecksum`（V2-10-01 sealed ice plan checksums are not rewritten in place）
- Modules stay `EXPERIMENTAL` and catalog-unregistered（diagnostic binding）；not `SUPPORTED`

V2-10-03 adds EXPERIMENTAL karst hydrology graph foundation:

- `karst-hydrology-positive.terrain-intent-v2.json` — `SINKHOLE`＋optional `UNDERGROUND_RIVER`＋`KARST_SPRING`
  on `PLAIN` with sealed host `cave.fixture-network`（`examples/v2/volume/cave-network-plan-v2.json`、checksum
  read-only） and optional `FLOODED_CAVE` cenote hook
- `sinkhole-plan-v2.json`／`karst-spring-plan-v2.json`／`karst-hydrology-graph-plan-v2.json`／
  `cenote-plan-v2.json` — sealed plans (written by tests)
- `KarstHydrologyGraphPlanV2` freezes typed nodes（`KARST_CAVE_SYSTEM` is a graph role on `CAVE_NETWORK`, not a
  FeatureKind）；`CenotePlanV2` is a COMPOSITE_PRESET（no `CENOTE` FeatureKind）
- Static loss/spring volume balance only（no unbounded groundwater solve）
- Modules stay `EXPERIMENTAL` and catalog-unregistered（diagnostic binding）；not `SUPPORTED`

V2-10-04 adds EXPERIMENTAL additional-marine foundation（`ABYSSAL_PLAIN`／`SEAMOUNT`）:

- `abyssal-plain-positive.terrain-intent-v2.json` — interior abyssal polygon HARD `WITHIN` shared `deep-basin` host
- `seamount-positive.terrain-intent-v2.json` — POINT seamount HARD `WITHIN` same basin host
- `abyssal-plain-out-of-basin.terrain-intent-v2.json`／`seamount-out-of-basin.terrain-intent-v2.json` — negative containment fixtures
- `abyssal-plain-plan-v2.json`／`seamount-plan-v2.json` — sealed plans (written by tests)
- Basin host bind uses `codec.geometryChecksum(basin.geometry())` as `basinGeometryChecksum`（V2-9-08 sealed
  `ocean-basin-plan-v2.json` checksum is not rewritten in place）
- `OCEAN_TRENCH`／`MID_OCEAN_RIDGE`／`SUBMARINE_VOLCANO` remain catalog-evaluation-only（no FeatureKinds）
- Modules stay `EXPERIMENTAL` and catalog-unregistered（diagnostic binding）；not `SUPPORTED`

V2-10-05 is a **contract/split only** Task（no FeatureKinds, no generators）:

- `advanced-river-lake-split-contract-v2.json` — sealed `AdvancedRiverLakeSplitContractV2` decision
- First implementation slices fixed to `SPRING` → Task `V2-10-10` and `OXBOW_LAKE` → Task `V2-10-11`
- Deferred: `RIVER_TERRACE`（child/profile）、`ALLUVIAL_FAN`、`ESTUARY`、`BRAIDED_RIVER`、`DAM_RESERVOIR`
- Pond/crater/glacial lake kindization remains forbidden

V2-10-06 adds EXPERIMENTAL `ESCARPMENT`／`PLATEAU` foundation slices:

- `plateau-escarpment-slice.terrain-intent-v2.json` — PLATEAU polygon + ESCARPMENT spline + HARD `OVERLAPS` `PRIORITY_BLEND` band 8
- `escarpment-plan-v2.json`／`plateau-plan-v2.json`／`dry-land-modifier-contract-v2.json` — sealed plans (written by tests)
- `MESA`／`BUTTE` remain `PlateauProfile` values only（no FeatureKinds）
- Dune/badlands/salt-flat modifiers are contract-only via `DryLandModifierContractV2`（no FeatureKinds, no generators）
- Modules stay `EXPERIMENTAL` and catalog-unregistered（diagnostic binding）；not `SUPPORTED`

V2-10-07 adds EXPERIMENTAL `LAVA_TUBE` foundation slice:

- `lava-tube-positive.terrain-intent-v2.json` — `VOLCANIC_CONE` host + `VOLCANIC_CALDERA` provenance + SPLINE tube + HARD `WITHIN`/`ORIGINATES_AT`
- `lava-tube-orphan.terrain-intent-v2.json` — missing HARD `WITHIN` cone
- `lava-tube-plan-v2.json` — sealed plan artifact (written by tests)
- Tube roof/support/entrance are plan fields only（no internal FeatureKinds）
- Ops are `CARVE_SOLID` swept spline only（no dynamic lava, no `ADD_FLUID` ownership）
- Modules stay `EXPERIMENTAL` and catalog-unregistered（diagnostic binding）；not `SUPPORTED`

V2-10-08 adds `BARRIER_ISLAND`／`ATOLL` COMPOSITE_PRESET composition slices:

- `barrier-island-composition.terrain-intent-v2.json` — `SINGLE_ISLAND` POINT + shore-parallel `LAGOON` + HARD `FLANKS`
- `atoll-composition.terrain-intent-v2.json` — `CORAL_REEF` + `LAGOON` + `REEF_PASS` + coral-style HARD relations（diagnostic coral-reef fixture unchanged）
- `barrier-island-plan-v2.json`／`atoll-plan-v2.json`／`advanced-island-reef-catalog-contract-v2.json` — sealed artifacts (written by tests)
- `BARRIER_ISLAND`／`ATOLL`／`FLOATING_REEF` remain non-FeatureKinds; catalog classifies `CORAL_COAST`／`MANGROVE_COAST`／`VOLCANIC_COAST`／`DUNE_BACKED_BEACH` as preset-only or deferred
- No dedicated modules registered; sealed `single-island-plan-v2.json` checksum unchanged

V2-10-10 adds EXPERIMENTAL surface `SPRING` foundation slice:

- `spring-positive.terrain-intent-v2.json` — `PLAIN` host + general `RIVER` + POINT spring + HARD `SUPPORTED_BY`／`EMPTIES_INTO`（alluvial/temperate-humid/river-corridor environment）
- `spring-orphan.terrain-intent-v2.json` — missing HARD `EMPTIES_INTO` river
- `spring-plan-v2.json` — sealed plan artifact (written by tests)
- Distinct from `KARST_SPRING`／`KarstSpringPlanV2`; binds `RiverPlanV2` SOURCE+HEADWATER and `HydrologyPlanV2.NodeKind.SPRING` id
- Modules stay `EXPERIMENTAL` and catalog-unregistered（diagnostic binding）；not `SUPPORTED`
- Sealed `karst-spring-plan-v2.json`／`river-plan-v2.json`／`river-graph-roles-plan-v2.json`／`advanced-river-lake-split-contract-v2.json` checksums unchanged

V2-10-11 adds EXPERIMENTAL `OXBOW_LAKE` reach-cutoff basin slice:

- `oxbow-lake-positive.terrain-intent-v2.json` — `FLOODPLAIN` host + general `RIVER` + POLYGON oxbow + HARD `SUPPORTED_BY`／`ORIGINATES_AT`（alluvial/temperate-humid/river-corridor environment）
- `oxbow-lake-orphan.terrain-intent-v2.json` — missing HARD `ORIGINATES_AT` parent river
- `oxbow-lake-plan-v2.json` — sealed plan artifact (written by tests)
- FIXED `CLOSED` stagnant basin; distinct from open-spill `LAKE`／`LakePlanV2` and deferred `DAM_RESERVOIR`
- Modules stay `EXPERIMENTAL` and catalog-unregistered（diagnostic binding）；not `SUPPORTED`
- Sealed `open-spill-lake.terrain-intent-v2.json` SHA-256／`river-plan-v2.json`／`river-graph-roles-plan-v2.json`／`advanced-river-lake-split-contract-v2.json` checksums unchanged

`BACKSHORE_PLAINS` remains a legacy diagnostic kind. Use `BackshorePlainsAliasV2.suggestedPlainParameters`
to derive `PlainParameters` without changing existing serialization (see azure-coast fixture).

Synthetic two-owner whole/tile merge coverage lives in
`SurfaceFoundationPlanCompilerV2Test`, `FoundationPlainHillSliceCompilerV2Test`, and
`FoundationMountainValleySliceCompilerV2Test`, not as static fixtures.
