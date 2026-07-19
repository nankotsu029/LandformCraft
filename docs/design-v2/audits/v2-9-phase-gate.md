# V2-9 Terrain foundation Phase gate audit

> Status: PASS — `V2-9-14` completed (2026-07-18). This is an offline plan-level foundation gate; public intent dispatch stays on the diagnostic module, no Release 2 capability is added, and Paper apply／world mutation remain V2-6 scope.

## Decision

`V2-9-01` through `V2-9-13` satisfy the V2-9 parent gate. The following offline plan-level capabilities are fixed by this gate, per capability and with evidence, without changing any WorldBlueprint checksum, module catalog registration, or Release capability set:

- **offline_generate / validation / preview: `SUPPORTED`（plan-level）** for the surface foundation contract (`SurfaceFoundationPlanV2`, ScaleProfile admission, owner/transition merge) and the V2-9 vertical slices: `PLAIN`／`HILL_RANGE`, `MOUNTAIN_RANGE`／`VALLEY`, general `RIVER`（graph roles／`WATERFALL_CHAIN` preset含む）, `FLOODPLAIN`／`MARSH`, `ROCKY_COAST`／`SEA_CLIFF`, `SINGLE_ISLAND`／`ARCHIPELAGO`／`VOLCANIC_CONE`, `OCEAN_BASIN`／`CONTINENTAL_SHELF`／`CONTINENTAL_SLOPE`／`SUBMARINE_CANYON`, `CAVE_ENTRANCE`／`UNDERGROUND_RIVER`（＋`FLOODED_CAVE` hook）, macro land-water topology（`MACRO_CONSTRAINT`）. Each slice closed contract, integer-only generator, independent validator, diagnostic preview index, positive/boundary/corruption fixtures, whole/tile/seam/thread/locale/timezone determinism, and budget admission in its own task.
- **intent_compile / standalone_usage: `PARTIAL`.** Typed Intent parameters and strict Schemas exist and compile, but only through the diagnostic module binding; the 18 dedicated foundation modules stay `EXPERIMENTAL` and unregistered in `BuiltInLandformModuleCatalogV2` so that `DiagnosticBlueprintCompilerV2`／WorldBlueprint checksums are unchanged. Usage is plan-level API only (no CLI／Paper command surface).
- **export: `PARTIAL`.** Sealed canonical plan JSON, validation artifacts, preview indexes, and streaming whole/tile（bathymetryはstreaming underwater column）export checksums are frozen, but V2-9 defines no Release 2 capability; foundation artifacts have no Release container path. Promoting `export` to `SUPPORTED` requires a future Release capability task with strict directory/ZIP read-back.
- **paper_apply / post_apply_validation / snapshot / rollback / restart_recovery: `UNSUPPORTED`.** Foundation features resolve through the same canonical X→Z→Y block-state stream contract as V2-2〜V2-5 output (`TerrainQuery`／`TerrainBlockResolver`), so V2-6 placement evidence (envelope, snapshot-all, containment, apply/settle/rollback/Undo/Recovery) will carry over as common-stream compatibility once `V2-6-06`〜`V2-6-10` and the `V2-6-19` gate complete. No per-feature Paper adapter exists or is planned.

No feature kind is promoted into public intent dispatch. `BACKSHORE_PLAINS` keeps its ID (alias mapping only), `MEANDERING_RIVER`／`MANGROVE_WETLAND`／`VOLCANIC_ARCHIPELAGO`／surface `CANYON`／`ROCKY_CAPE` keep their existing SUPPORTED paths and checksums unchanged. LARGE (>1024) remains not generatable and is not represented as supported; V2-9 binds only the `V2-8-01` ScaleProfile admission contract.

## Capability matrix

`S/P/E/U/N` = `SUPPORTED`／`PARTIAL`／`EXPERIMENTAL`／`UNSUPPORTED`／`NOT_APPLICABLE`. All rows: plan-level offline path; `Paper以降` covers paper apply, post-apply validation, snapshot, rollback, restart recovery.

| Group | Kinds／contracts | intent | generate | validate | preview | export | standalone | child | overlay | Paper以降 |
|---|---|---|---|---|---|---|---|---|---|---|
| Surface | `PLAIN` `HILL_RANGE` `MOUNTAIN_RANGE` `VALLEY` | P | S | S | S | P | P | N | N | U |
| Hydrologic surface | `FLOODPLAIN` `MARSH` | P | S | S | S | P | P | N | N | U |
| River graph | `RIVER`（roles／modifiers／children）＋`WATERFALL_CHAIN` preset | P | S | S | S | P | P | P（sandbar／river-island／plunge-pool child） | N | U |
| Coast | `ROCKY_COAST` `SEA_CLIFF` | P | S | S | S | P | P | N | P（sea-cave／overhang host handoff） | U |
| Island／cone | `SINGLE_ISLAND` `ARCHIPELAGO` `VOLCANIC_CONE` | P | S | S | S | P | P | N | N | U |
| Marine bathymetry | `OCEAN_BASIN` `CONTINENTAL_SHELF` `CONTINENTAL_SLOPE` `SUBMARINE_CANYON` | P | S | S | S | P | P | N | N | U |
| Surface-volume connection | `CAVE_ENTRANCE` `UNDERGROUND_RIVER`（＋`FLOODED_CAVE` hook） | P | S | S | S | P | P | P（hook） | P（frozen volume checksum bind） | U |
| Macro constraint | land-water topology（FeatureKindなし） | N | S | S | S | P | P | N | N | U |

`intent = P` はすべてdiagnostic module binding経由のみ（専用module catalog未登録）。`export = P` はsealed canonical JSON＋streaming tile checksum経路のみ（Release 2 capability未定義）。

## Integrated portfolio

- Executable gate: `FoundationPhaseGateV2Test`
- False-promotion audit: all 19 V2-9 FeatureKinds dispatch to the diagnostic module (`EXPERIMENTAL`, no validator/preview catalog capability); all 18 dedicated foundation modules stay `EXPERIMENTAL`; the Release 2 capability list is unchanged as `[environment-fields, hydrology-plan, sparse-volume, surface-2_5d]`
- Example portfolio: the 22 strict foundation plan examples (surface foundation, plain, hill range, mountain range, valley, river, river graph roles, waterfall chain, floodplain, marsh, rocky coast, sea cliff, single island, archipelago, volcanic cone, ocean basin, continental shelf, continental slope, submarine canyon, cave entrance, underground river, macro land-water topology) read and re-verify their sealed canonical checksums identically across forward／reverse read order, one／four threads, Turkish locale, and Chatham timezone
- Representative scenario: the plain-hill slice recompiles to identical sealed checksums and merges identical whole／tile field checksums (SMALL profile)
- 1000-square: a two-owner foundation merge at 1000×1000 passes ScaleProfile admission and produces identical whole／row-major-tile field checksums; an oversized dimension against a SMALL profile is rejected up front (`SCALE_ADMISSION_REJECTED`), keeping LARGE gated behind V2-8

## Task evidence matrix

| Task | Evidence | Result |
|---|---|---|
| V2-9-01 surface foundation contract | `SurfaceFoundationPlanCompilerV2Test`: ScaleProfile admission, named seed namespace, 5 field bindings, two-owner whole/tile merge parity, ownerless/tie/undeclared-overlap/band/seed-collision rejection | PASS |
| V2-9-02 PLAIN／HILL_RANGE | `FoundationPlainHillSliceCompilerV2Test`: microrelief/groundwater handoff, ridge/saddle budget, transition corruption, whole/tile checksum, BACKSHORE_PLAINS alias | PASS |
| V2-9-03 MOUNTAIN_RANGE／VALLEY | `FoundationMountainValleySliceCompilerV2Test`: derived components, V/U cross-section, peak/pass budget, floor owner conflict, valley connection rejection | PASS |
| V2-9-04 general RIVER | `FoundationRiverSliceCompilerV2Test`: source→mouth reach graph, bank/bed/discharge/floodplain handoff, orphan/self-loop rejection, MEANDERING_RIVER bridge unchanged | PASS |
| V2-9-05 FLOODPLAIN／MARSH | `FoundationFloodplainMarshSliceCompilerV2Test`: river adjacency, groundwater/hydroperiod/wetness, dry-marsh/filled-channel/disconnect rejection | PASS |
| V2-9-06 ROCKY_COAST／SEA_CLIFF | `FoundationRockyCoastCliffSliceCompilerV2Test`: rock shelf/cliff face/talus/notch, coast transition, `hostSupportAabb` handoff, ROCKY_CAPE checksum unchanged | PASS |
| V2-9-07 island／cone | `FoundationIslandConeSliceCompilerV2Test`: island mass/shore/drainage/apron, group spacing/dominance/saddle, cone/crater, VOLCANIC_ARCHIPELAGO unchanged | PASS |
| V2-9-08 marine bathymetry | `FoundationBathymetrySliceCompilerV2Test`: depth/slope/coast-distance/fluid-column-hint fields, coast→basin transect, streaming underwater column export, no dense 3D water array | PASS |
| V2-9-09 SUBMARINE_CANYON | `FoundationSubmarineCanyonSliceCompilerV2Test`: shelf/slope/basin host relations, centerline carve, out-of-host rejection, surface CANYON unchanged | PASS |
| V2-9-10 CAVE_ENTRANCE | `FoundationCaveEntranceSliceCompilerV2Test`: HARD ENTRANCE_OF＋SUPPORTED_BY, frozen `CaveNetworkPlanV2` checksum bind, roof/flood/owner/reachability, seamless query/export | PASS |
| V2-9-11 UNDERGROUND_RIVER | `FoundationUndergroundRiverSliceCompilerV2Test`: HARD WITHIN, single fluid owner, carve→`ADD_FLUID` ordering, leak/air-pocket rejection, frozen host checksums unchanged | PASS |
| V2-9-12 macro topology | `MacroLandWaterTopologySliceCompilerV2Test`: mask/zone→topology, adjacency/containment/min-width, disconnected-strait/collapsed-isthmus/nested-basin rejection, freeze checksum | PASS |
| V2-9-13 river graph roles | `FoundationRiverGraphRolesSliceCompilerV2Test`: roles/classes/modifiers/children on general IR, `WATERFALL_CHAIN` preset, flow conservation, legacy fixtures unchanged | PASS |
| V2-9-14 integration | `FoundationPhaseGateV2Test`＋full clean suite（`./gradlew clean test build`） | PASS |

## Determinism and resource gate

All foundation math is integer/fixed-point over global X/Z (Y for volume connections); owner merge uses declared priority with explicit interaction bands, and ownerless cells, priority ties, and undeclared overlaps are compile/sample errors — no last-write-wins. Component tests retain whole versus tiled, seam, tile order, candidate/module order, worker count, locale, timezone, cancel, and corruption coverage; the gate test re-verifies the example portfolio under reversed order, four threads, Turkish locale, and Chatham timezone, and the 1000-square merge under ScaleProfile admission.

Bathymetry and flooded-volume slices export by streaming columns; no dense 3D array is introduced. Support radii are bounded by the declared interaction bands (≤32) and halo admission against tile size; dimension checks reject anything beyond the admitted ScaleProfile before any allocation.

## Release, security, and compatibility gate

V2-9 adds no Release 2 capability and does not touch `format.v2.release` semantics: the canonical capability list `[environment-fields, hydrology-plan, sparse-volume, surface-2_5d]` and all four existing capability verifiers are regression-unchanged in the full suite (directory/ZIP parity, tampering, dependency, cancel-cleanup portfolios). Foundation plans remain sealed strict-JSON artifacts with checksum binding to their frozen hosts (cave network, underground lake, V2-3 hydrology), and no input path/ZIP/image trust boundary changed.

The complete suite retains TerrainIntent／WorldBlueprint v1 strict Schema behavior, generator `3.0.0-phase6` goldens, Release format 1 verification, v1 placement/rollback/Undo/Recovery, V2-2〜V2-5 capability verifiers, and the V2-6-01〜05 placement/envelope/reservation/snapshot/containment portfolios at their current implementation state (`V2-6-19` is not a prerequisite of this gate). Frozen example checksums (cave network, underground lake, hydrology fixtures) are unchanged in place.

## Verification commands

```text
./gradlew test --tests '*FoundationPhaseGateV2Test'
./gradlew clean test build
git diff --check
```

## Remaining boundaries

- Track E starts at `V2-10-01`; no V2-10 family (glacial, karst, advanced marine/river, dry land, lava tube, advanced island/reef) is included in this gate.
- Foundation `export` stays `PARTIAL` until a dedicated Release 2 capability task defines the artifact set with strict directory/ZIP read-back.
- Paper apply and all post-apply capabilities stay `UNSUPPORTED` until Track A closes `V2-6-06`〜`V2-6-10` and `V2-6-19`; the common canonical stream compatibility recorded here is the inheritance path, not an enablement.
- LARGE (>1024〜3072) remains Track C scope; V2-9 binds the admission contract only and 3000×3000 generation is still not possible.
- Dedicated foundation modules remain catalog-unregistered; promoting intent dispatch requires a future task that revisits WorldBlueprint checksums explicitly.
