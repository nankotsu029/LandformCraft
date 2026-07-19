# V2-10 Deferred terrain families Phase gate audit

> Status: PASS — `V2-10-09` completed (2026-07-18). This is an offline plan-level gate over the completed V2-10 slices only; public intent dispatch stays on the diagnostic module, no Release 2 capability is added, and Paper apply／world mutation remain V2-6 scope.

## Decision

`V2-10-01`〜`V2-10-08`, `V2-10-10`, and `V2-10-11` satisfy the V2-10 parent gate. The following offline plan-level capabilities are fixed by this gate, per capability and with evidence, without changing any WorldBlueprint checksum, module catalog registration, or Release capability set:

- **offline_generate / validation: `SUPPORTED`（plan-level）** for the deferred-family vertical slices: `VALLEY_GLACIER`／`ICE_CAP`／`ICE_SHEET`（共通`GlacialIcePlanV2`）, `MORAINE_FIELD`／`OUTWASH_PLAIN`, `SINKHOLE`／`KARST_SPRING`（`KarstHydrologyGraphPlanV2` typed graph）, `ABYSSAL_PLAIN`／`SEAMOUNT`, `ESCARPMENT`／`PLATEAU`, `LAVA_TUBE`, surface `SPRING`, `OXBOW_LAKE`. Each slice closed its typed contract, integer-only generator, independent validator artifact, positive/boundary/corruption fixtures, whole/tile/thread/locale/timezone determinism, and budget admission in its own task.
- **preview: `SUPPORTED`（plan-level）** for the slices that sealed a `FoundationPreviewIndexV2` in their compiler: glacial ice, karst hydrology, additional marine, escarpment/plateau, lava tube, spring, oxbow lake. **`MORAINE_FIELD`／`OUTWASH_PLAIN` preview stays `EXPERIMENTAL`** — the V2-10-02 deposition slice sealed validation and raster export evidence but no preview index, so preview is deliberately not promoted for these two kinds.
- **intent_compile / standalone_usage: `PARTIAL`.** Typed Intent parameters and strict Schemas exist and compile, but only through the diagnostic module binding; the 12 dedicated V2-10 modules (glacial ice, moraine field, outwash plain, sinkhole, karst spring, abyssal plain, seamount, escarpment, plateau, lava tube, spring, oxbow lake) stay `EXPERIMENTAL` and unregistered in `BuiltInLandformModuleCatalogV2`, so `DiagnosticBlueprintCompilerV2`／WorldBlueprint checksums are unchanged. Usage is plan-level API only (no CLI／Paper command surface).
- **export: `PARTIAL`.** Sealed canonical plan JSON, validation artifacts, and streaming whole/tile export checksums are frozen, but V2-10 defines no Release 2 capability; deferred-family artifacts have no Release container path. Promoting `export` to `SUPPORTED` requires a future Release capability task with strict directory/ZIP read-back.
- **paper_apply / post_apply_validation / snapshot / rollback / restart_recovery: `UNSUPPORTED`.** Deferred-family features resolve through the same canonical X→Z→Y block-state stream contract as V2-2〜V2-9 output, so V2-6 placement evidence carries over as common-stream compatibility once `V2-6-06`〜`V2-6-10` and the `V2-6-19` gate complete. No per-feature Paper adapter exists or is planned.

No profile, preset, component, graph role, or deferred candidate is promoted: `ICE_FJORD`／`CENOTE`／`BARRIER_ISLAND`／`ATOLL` remain `COMPOSITE_PRESET`s（plan-level composition only）, `PERMAFROST_PLAIN`／`MESA`／`BUTTE` remain profiles, `KARST_CAVE_SYSTEM` remains a graph node role on the sealed `CAVE_NETWORK`, and `OCEAN_TRENCH`／`MID_OCEAN_RIDGE`／`SUBMARINE_VOLCANO`／`RIVER_TERRACE`／`ALLUVIAL_FAN`／`ESTUARY`／`BRAIDED_RIVER`／`DAM_RESERVOIR`／`FLOATING_REEF` and the dry-land modifier candidates stay deferred without FeatureKinds or generators. The gate test asserts none of these names exists in the FeatureKind enum.

## Capability matrix

`S/P/E/U/N` = `SUPPORTED`／`PARTIAL`／`EXPERIMENTAL`／`UNSUPPORTED`／`NOT_APPLICABLE`. All rows: plan-level offline path; `Paper以降` covers paper apply, post-apply validation, snapshot, rollback, restart recovery.

| Group | Kinds／contracts | intent | generate | validate | preview | export | standalone | child | overlay | Paper以降 |
|---|---|---|---|---|---|---|---|---|---|---|
| Glacial ice | `VALLEY_GLACIER` `ICE_CAP` `ICE_SHEET` | P | S | S | S | P | P | N | P（bounded sparse `ADD_SOLID`） | U |
| Glacial deposition | `MORAINE_FIELD` `OUTWASH_PLAIN` | P | S | S | E | P | P | P（glacial parent bind） | N | U |
| Karst graph | `SINKHOLE` `KARST_SPRING`（`KarstHydrologyGraphPlanV2`） | P | S | S | S | P | P | P（host `CAVE_NETWORK` bind） | N | U |
| Additional marine | `ABYSSAL_PLAIN` `SEAMOUNT` | P | S | S | S | P | P | P（HARD `WITHIN`→`OCEAN_BASIN`） | N | U |
| Escarpment／dry land | `ESCARPMENT` `PLATEAU` | P | S | S | S | P | P | N | N | U |
| Volcanic volume | `LAVA_TUBE` | P | S | S | S | P | P | P（HARD `WITHIN`→`VOLCANIC_CONE`） | P（`CARVE_SOLID` only） | U |
| River／lake follow-up | `SPRING` `OXBOW_LAKE` | P | S | S | S | P | P | P（river／host bind） | N | U |
| Presets | `ICE_FJORD` `CENOTE` `BARRIER_ISLAND` `ATOLL`（`COMPOSITE_PRESET`、FeatureKindなし） | N | S | S | E | P | P | N | N | U |
| Profiles／contracts | `PERMAFROST_PLAIN` profile（`PermafrostPlainProfileV2`）、`DryLandModifierContractV2`、`AdvancedRiverLakeSplitContractV2`、`AdvancedIslandReefCatalogContractV2` | N | N | S（sealed strict JSON） | N | P | N | N | N | U |

`intent = P` はすべてdiagnostic module binding経由のみ（専用module catalog未登録）。`export = P` はsealed canonical plan JSON＋streaming tile checksum経路のみ（Release 2 capability未定義）。preset行の`generate/validate = S`はchild contract再利用のplan-level composition評価であり、専用generatorは存在しない。

## Integrated portfolio

- Executable gate: `DeferredTerrainPhaseGateV2Test`
- False-promotion audit: all 14 V2-10 FeatureKinds dispatch to the diagnostic module (`EXPERIMENTAL`, no validator/preview catalog capability); all 12 dedicated V2-10 modules stay `EXPERIMENTAL` and catalog-unregistered; 17 profile/preset/role/candidate names are asserted absent from the FeatureKind enum; the Release 2 capability list is unchanged as `[environment-fields, hydrology-plan, sparse-volume, surface-2_5d]`
- Example portfolio: the 21 strict V2-10 sealed examples (glacial ice, ice fjord, moraine field, outwash plain, permafrost plain profile, karst hydrology graph, sinkhole, karst spring, cenote, abyssal plain, seamount, advanced river/lake split contract, escarpment, plateau, dry-land modifier contract, lava tube, barrier island, atoll, advanced island/reef catalog contract, spring, oxbow lake) read and re-verify their sealed canonical checksums identically across forward／reverse read order, one／four threads, Turkish locale, and Chatham timezone
- Representative scenario: the plateau-escarpment slice recompiles to identical sealed and export checksums, its HARD `OVERLAPS` transition metrics hold, and both generators produce identical whole／tile export checksums
- Protected hosts: sealed `plain`／`ocean-basin`／`volcanic-cone`／`single-island`／`river`／`river-graph-roles` plans, the advanced river/lake split contract, and the open-spill `LAKE` fixture re-verify their recorded checksums unchanged
- Resource: a 1000-square admits under the `V2-8-01` ScaleProfile admission contract and an oversized dimension against a SMALL profile is rejected before allocation (`SCALE_CLASS_EXCEEDED`), keeping LARGE gated behind V2-8

## Task evidence matrix

| Task | Evidence | Result |
|---|---|---|
| V2-10-01 glacial ice | `FoundationGlacialIceSliceCompilerV2Test`: cold-climate binding, bed contact, flow/terminus, bounded sparse ice, meltwater handoff, `ICE_FJORD` preset, warm/unsupported/leaking corruption | PASS |
| V2-10-02 glacial deposition | `FoundationGlacialDepositionSliceCompilerV2Test`: glacial parent geometry bind, sediment owner, integer raster whole/tile export, `PermafrostPlainProfileV2` profile-kind separation | PASS |
| V2-10-03 karst graph | `FoundationKarstHydrologySliceCompilerV2Test`: typed graph freeze, loss/spring static balance, host `CAVE_NETWORK` checksum bind, `CenotePlanV2` preset, orphan/cycle/leak corruption | PASS |
| V2-10-04 additional marine | `FoundationAdditionalMarineSliceCompilerV2Test`: basin containment, depth/relief/slope, out-of-basin rejection, host basin checksum unchanged, advanced-three catalog評価のみ | PASS |
| V2-10-05 river/lake split | `AdvancedRiverLakeSplitContractV2` sealed contract round-trip; seven candidates classified, no FeatureKind introduced | PASS |
| V2-10-06 escarpment/plateau | `FoundationEscarpmentPlateauSliceCompilerV2Test`: long scarp owner, cap/floor/talus, HARD `OVERLAPS` transition, `DryLandModifierContractV2`, plain regression checksum | PASS |
| V2-10-07 lava tube | `FoundationLavaTubeSliceCompilerV2Test`: host/provenance relations, tube continuity, roof/fluid conflict, `CARVE_SOLID` only, volcanic-cone checksum regression | PASS |
| V2-10-08 island/reef | `FoundationAdvancedIslandReefSliceCompilerV2Test`: barrier/atoll composition on existing child contracts, catalog contract seal, single-island/volcanic checksum regression | PASS |
| V2-10-10 surface SPRING | `FoundationSpringSliceCompilerV2Test`: source ownership, `RiverPlanV2` SOURCE bind, outflow continuity, karst-spring/river checksum regression | PASS |
| V2-10-11 OXBOW_LAKE | `FoundationOxbowLakeSliceCompilerV2Test`: cutoff basin ownership, `CLOSED` stagnant level, wetland handoff, open-spill lake SHA-256 regression | PASS |
| V2-10-09 integration | `DeferredTerrainPhaseGateV2Test`＋full clean suite（`./gradlew test build`） | PASS |

## Determinism and resource gate

All deferred-family math is integer/fixed-point over global X/Z (Y for volume slices); slice tests retain whole versus tiled, seam, tile order, worker count, locale, timezone, and corruption coverage, and the gate test re-verifies the 21-example portfolio under reversed order, four threads, Turkish locale, and Chatham timezone. Glacial ice and lava tube stay bounded sparse volumes (`ADD_SOLID`／`CARVE_SOLID` within declared AABBs); no dense ice or lava voxel array is introduced. Dimension checks reject anything beyond the admitted ScaleProfile before allocation; LARGE remains Track C scope.

## Release, security, and compatibility gate

V2-10 adds no Release 2 capability and does not touch `format.v2.release` semantics: the canonical capability list `[environment-fields, hydrology-plan, sparse-volume, surface-2_5d]` and all four existing capability verifiers are regression-unchanged in the full suite. Deferred-family plans remain sealed strict-JSON artifacts with checksum binding to their frozen hosts (cave network, volcanic cone, ocean basin, river plans, open-spill lake), and no input path/ZIP/image trust boundary changed.

The complete suite retains TerrainIntent／WorldBlueprint v1 strict Schema behavior, generator `3.0.0-phase6` goldens, Release format 1 verification, v1 placement/rollback/Undo/Recovery, V2-2〜V2-6 portfolios, and the V2-9 foundation gate (`FoundationPhaseGateV2Test`) at their current implementation state (`V2-6-19` is not a prerequisite of this gate). Frozen example checksums are unchanged in place.

## Verification commands

```text
./gradlew test --tests '*DeferredTerrainPhaseGateV2Test'
./gradlew test
./gradlew build
git diff --check
```

## Remaining boundaries

- Deferred candidates stay unimplemented: `OCEAN_TRENCH`／`MID_OCEAN_RIDGE`／`SUBMARINE_VOLCANO`, `RIVER_TERRACE`／`ALLUVIAL_FAN`／`ESTUARY`／`BRAIDED_RIVER`／`DAM_RESERVOIR`, `FLOATING_REEF`, and the dry-land modifier generators require new V2-10 Task IDs before any implementation claim.
- `MORAINE_FIELD`／`OUTWASH_PLAIN` preview stays `EXPERIMENTAL` until a slice task seals a preview index for the deposition fields.
- Deferred-family `export` stays `PARTIAL` until a dedicated Release 2 capability task defines the artifact set with strict directory/ZIP read-back.
- Paper apply and all post-apply capabilities stay `UNSUPPORTED` until Track A closes `V2-6-06`〜`V2-6-10` and `V2-6-19`; the common canonical stream compatibility recorded here is the inheritance path, not an enablement.
- Dedicated V2-10 modules remain catalog-unregistered; promoting intent dispatch requires a future task that revisits WorldBlueprint checksums explicitly.
