# V2-5 Sparse volume Phase gate audit

> Status: PASS — `V2-5-18` completed (2026-07-18). This is an offline generation／validation／export／Release gate; Paper apply, running-server WorldEdit／FAWE smoke, and real-world measurement remain disabled and belong to V2-6 and Beta hardening.

## Decision

`V2-5-01` through `V2-5-17` satisfy the V2-5 parent gate. The offline lifecycle is `SUPPORTED` for the sparse-volume infrastructure (fixed-point SDF primitives, ordered CSG, AABB index, bounded 3D tile cache, volume-aware `TerrainQuery`), the volume features CAVE_NETWORK, LUSH_CAVE, UNDERGROUND_LAKE, SEA_CAVE, OVERHANG, NATURAL_ARCH, SKY_ISLAND_GROUP, the WATERFALL volume integration, the post-volume local environment, the volume validator／5-layer diagnostic preview, the offline 3D volume tile export, and the Release 2 `sparse-volume` capability.

WATERFALL, deferred as `EXPERIMENTAL` since `V2-3-15` because its falling-column and behind-fall volume were missing, is now `SUPPORTED`: `HydrologyWaterfallModuleV2` (2.5D lip／base／plunge pool) plus the `WaterfallVolumePlanV2` bound to the fall geometry checksum complete the offline path. The module's `feature.waterfall.volume-deferred` preview tag is retained as a historical marker; the fall volume is owned by the separate sparse-volume plan, not by the 2.5D module.

Volume features are `SUPPORTED` as an offline plan／artifact／Release path. The VOLUME_GUIDE intent kinds (CAVE_NETWORK, LUSH_CAVE, OVERHANG, SKY_ISLAND_GROUP) remain bound to the diagnostic module and are not promoted into public intent dispatch, CLI／Paper commands, or world mutation. GLACIAL_CIRQUE_FIELD, LAGOON, REEF_PASS, VOLCANIC_CALDERA, and LAVA_FLOW_FIELD remain `EXPERIMENTAL` child-plan hooks.

## Integrated portfolio

- Executable gate: `VolumePhaseGateV2Test` (with `HydrologyPhaseGateV2Test` and `EnvironmentPhaseGateV2Test` for the promoted WATERFALL lifecycle)
- Lifecycle: all nine volume plan compilers report `SUPPORTED`; the waterfall module and kind are `SUPPORTED`; VOLUME_GUIDE kinds stay diagnostic-only
- Example portfolio: the thirteen strict volume plan examples (SDF, CSG, AABB index, tile cache, seven feature plans, waterfall volume, local environment) read and re-verify their sealed canonical checksums identically across forward／reverse read order, one／four threads, Turkish locale, and Chatham timezone
- Scene composition: the shared cave／floating-solid／fluid scene composes identical canonical block streams for the whole window, an exclusive-owner tile dispatch, and window-restricted volume queries over the same global CSG plan (3D whole／tile／XYZ seam invariance)
- Capability order: `sparse-volume` is accepted only as `[environment-fields, hydrology-plan, sparse-volume, surface-2_5d]`

## Task evidence matrix

| Task | Evidence | Result |
|---|---|---|
| V2-5-01 SDF primitives | `VolumeSdfKernelV2Test`: fixed-point distances, conservative AABB, primitive-order／locale／timezone／thread stability, zero-radius／future-kernel／overflow rejection | PASS |
| V2-5-02 ordered CSG | `VolumeCsgEvaluatorV2Test`: explicit ordinal order, add→carve vs carve→add, CARVE owns no fluid, ambiguous-ordinal／cycle／depth／budget rejection | PASS |
| V2-5-03 AABB index | `VolumeAabbIndexV2Test`: brute-force oracle parity, halo query, input-order／thread invariance, overflow／checksum rejection | PASS |
| V2-5-04 3D tile cache | `VolumeTileCacheV2Test`: LRU／intervals, `retained + concurrency×peak + cache` admission, dense allocation detector, cancel-safe fill, cache-on／off checksum parity | PASS |
| V2-5-05 volume query | `VolumeTerrainQueryV2Test`: base＋CSG composition, intervals／surface／ceiling, XYZ seam／whole／tile／thread stability, `V1TerrainAdapterTest`／`V1CompatibilityGoldenTest` regression | PASS |
| V2-5-06 cave network | `CaveNetworkGeneratorV2Test`: connectivity, entrance reachability, thin-roof／breakthrough rejection | PASS |
| V2-5-07 lush cave | `LushCaveGeneratorV2Test`: WITHIN／REACHABLE_FROM, wet classification, orphan／too-dry rejection | PASS |
| V2-5-08 underground lake | `UndergroundLakeGeneratorV2Test`: basin carve→single ADD_FLUID, rim／containment, leak／double-fluid rejection | PASS |
| V2-5-09 sea cave | `SeaCaveGeneratorV2Test`: marine opening, static fluid continuity, landlocked／leaking-inland rejection | PASS |
| V2-5-10 overhang | `OverhangGeneratorV2Test`: support／roof／projection／clearance, floating-slab rejection | PASS |
| V2-5-11 natural arch | `NaturalArchGeneratorV2Test`: two piers, crown, span, one-pier／closed-opening rejection | PASS |
| V2-5-12 sky island group | `SkyIslandGroupGeneratorV2Test`: clearance／gap／classes, merged／grounded rejection, component-order stability | PASS |
| V2-5-13 waterfall volume | `WaterfallVolumeGeneratorV2Test`: fall-geometry checksum binding, lip→column→pool continuity, behind clearance, mismatch rejection | PASS |
| V2-5-14 local environment | `VolumeLocalEnvironmentResolverV2Test`: surface／wetness／drip classes, moss-on-dry-ceiling／unsupported-root rejection | PASS |
| V2-5-15 validator／preview | `VolumeValidatorV2Test`, `VolumeDiagnosticPreviewRendererV2Test`: descriptor-only corruption detection, strict 5-layer index, atomic publish／cancel cleanup | PASS |
| V2-5-16 offline read-back | `OfflineVolumeTileReadBackV2Test`, `WorldEditOfflineVolumeTileReaderV2Test`: cave／floating-solid／fluid export, strict inspector＋WorldEdit 7.3.19 read-back, 127/128 VarInt boundary in 3D, truncated／corrupt rejection | PASS |
| V2-5-17 Release capability | `ReleaseSparseVolumePublisherVerifierV2Test`: exact artifact set, checksum bindings, directory／ZIP parity, missing／extra／version／tile tampering, dependency downgrade, cancel cleanup, prior capability regression | PASS |
| V2-5-18 integration | `VolumePhaseGateV2Test`, updated `HydrologyPhaseGateV2Test`／`EnvironmentPhaseGateV2Test`／`DiagnosticBlueprintCompilerV2Test`, full clean suite | PASS |

## Determinism and resource gate

All volume math is integer／fixed-point over global coordinates; CSG operations execute in explicit ordinal order with stable operator IDs and no last-write-wins. Component tests retain whole versus tiled, XYZ seam, tile order, operator order, worker count, locale, timezone, cancel, and corruption coverage; the gate test re-verifies the example portfolio and scene composition under reversed order, four threads, Turkish locale, and Chatham timezone.

`VolumeDenseAllocationDetectorV2` rejects `1000×1000×512` (and larger) dense world arrays. The sealed tile-cache example admits `retained + concurrency×peak + cache` under 1 MiB, independent of the XZ extent, and a 1000-square region necessarily exceeds the retained chunk cache, forcing bounded streaming. No dense voxel world array or full block list is introduced anywhere in the volume path.

## Release, security, and compatibility gate

`sparse-volume` is accepted only with `environment-fields`, `hydrology-plan`, and `surface-2_5d`. The `volume/` artifact set (SDF plan, CSG plan, AABB index plan, volume validation, volume tile metadata＋Sponge v3 schematics under dedicated `volume-offline-tile-artifact-v2`／`volume-sponge-schematic-v3` types) is exact and versioned; CSG→SDF and AABB→CSG checksum bindings, validation `sourcePlanChecksum`, and tile `sourceBlueprintChecksum` are strictly verified for both directory and ZIP with the shared path／entry／byte／checksum policy. Unknown, missing, extra, future, version, binding, and tile tampering are rejected; earlier capability sets still verify unchanged.

The complete suite retains TerrainIntent／WorldBlueprint v1 strict Schema behavior, generator `3.0.0-phase6` goldens, Release format 1 verification, v1 placement／rollback／Undo／Recovery, V2-2 `surface-2_5d`, V2-3 `hydrology-plan`, and V2-4 `environment-fields`. Volume types remain outside the v1 publisher and all Paper placement paths.

## Verification commands

```text
./gradlew test --tests '*VolumePhaseGateV2Test' --tests '*HydrologyPhaseGateV2Test' --tests '*EnvironmentPhaseGateV2Test'
./gradlew clean test
./gradlew build
git diff --check
```

## Remaining boundaries

- Track A continues at `V2-6-01`; no placement, envelope, reservation, snapshot, or apply work is included in this gate.
- Paper apply for Release 2, world mutation, Undo／Recovery integration, and 500×500／1000×1000 real-world measurement remain V2-6 scope.
- Current-build FAWE standalone smoke and the Beta release checklist remain open in the separate Beta hardening work.
- LARGE (>1024) generation remains Track C scope; volume features are bounded to the existing dimension limits.
