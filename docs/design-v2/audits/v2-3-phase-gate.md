# V2-3 Hydrology Phase gate audit

> Status: PASS — `V2-3-15` completed. This is an offline generation/validation/Release gate; Release 2 CLI/Paper dispatch and world mutation remain disabled.

## Decision

`V2-3-01` through `V2-3-14` satisfy the V2-3 parent gate. The offline lifecycle is `SUPPORTED` for RIVER／MEANDERING_RIVER, LAKE, CANYON, DELTA, TIDAL_CHANNEL_NETWORK, FJORD, and the `hydrology-plan` Release 2 capability. Hydrology IR, fixed-pass reconciliation, and field-only validation/preview are supported infrastructure for that offline path.

WATERFALL remains `EXPERIMENTAL` until the V2-5 falling-water/behind-fall volume gate. ALPINE／GLACIAL mountain remains `EXPERIMENTAL` until V2-4 environment completion. VOLCANIC_ARCHIPELAGO remains `EXPERIMENTAL` until V2-4 material/ecology completion. This audit does not support mangrove shaping, snow, volcanic material, Paper apply, or real-world placement.

## Integrated scenario portfolio

- Executable gate: `HydrologyPhaseGateV2Test`
- Strict examples: meandering river, open-spill lake, canyon/river, waterfall 2.5D, delta, tidal channel, fjord, alpine ridge, volcanic archipelago
- Bounds: scenario-specific `97..257` X/Z for canonical integration; maximum-dimension admission and routing measurements use `1000 × 1000`
- Comparison: forward/reverse scenario order, one/four compiler threads, default/Turkish locale, default/Chatham timezone
- Result: every scenario produces the same canonical Blueprint checksum for the same input; complete and deferred kinds have the exact lifecycle declared above

The gate scan also corrected two source-tree fixture defects without relaxing Schema: the lake example used obsolete draft field names, and the volcanic example contained two concatenated JSON objects. Both now strict-parse as one current TerrainIntent v2 object.

## Task evidence matrix

| Task | Evidence | Result |
|---|---|---|
| V2-3-01 IR/prior | `HydrologyPlanCompilerV2Test`: graph round-trip, endpoint/cycle/prior/checksum/budget rejection, canonical order, locale/timezone, 1000-square admission | PASS |
| V2-3-02 routing | `DeterministicHydrologyRoutingSolverV2Test`: bowl/flat/multiple outlet/boundary, reachability, candidate/tile/thread order, sidecar read-back, cancel/tampering | PASS |
| V2-3-03 river | `MeanderingRiverGeneratorV2Test`: source-mouth, monotonic bed, confluence hooks, whole/tile/seam/thread/candidate and corruption | PASS |
| V2-3-04 lake | `LakeGeneratorV2Test`: level/rim/spill/inlet, ambiguity/reverse/hard conflict, whole/tile/seam/thread and budget | PASS |
| V2-3-05 canyon | `CanyonGeneratorV2Test`: shared centerline/bed ownership, cross-section, thin-wall/owner corruption, whole/tile/thread/candidate | PASS |
| V2-3-06 waterfall | `WaterfallGeneratorV2Test`: graph split, lip/base/plunge, off-path/deferred-volume/owner corruption, whole/tile/thread | PASS, lifecycle deferred |
| V2-3-07 delta | `DeltaGeneratorV2Test`: DAG, marine mouths, flow conservation, dead/loop/landlocked corruption, whole/tile/thread/candidate/cancel | PASS |
| V2-3-08 tidal | `TidalChannelGeneratorV2Test`: bidirectional marine graph, isolated/unknown/no-data corruption, whole/tile/thread/path/cancel | PASS |
| V2-3-09 fjord | `FjordGeneratorV2Test`: marine connection/U profile/sidewall, landlocked/width/wall corruption, whole/tile/thread/cancel | PASS |
| V2-3-10 mountain | `MountainGeneratorV2Test`: ridge/peak/saddle/spur, self-cross/coast corruption, whole/tile/thread/order/budget | PASS, lifecycle deferred |
| V2-3-11 volcanic | `VolcanicGeneratorV2Test`: components/gap/dominance/marine separation, child/bounds corruption, whole/tile/thread/point/cancel | PASS, lifecycle deferred |
| V2-3-12 reconciliation | `BoundedHydrologyReconcilerV2Test`: fixed three passes, recoverable/unrecoverable/hard/non-convergence, order/thread/locale/timezone/cancel/checksum | PASS |
| V2-3-13 validation/preview | `HydrologyValidatorV2Test` and `HydrologyDiagnosticPreviewRendererV2Test`: independent field observation, all feature corruption rule IDs, strict 12-layer index, 1000-square bounded render/cancel cleanup | PASS |
| V2-3-14 Release capability | `ReleaseHydrologyPublisherVerifierV2Test`: surface dependency, exact artifact set, semantic binding, directory/ZIP parity, missing/extra/future/version/graph tampering, cancel cleanup | PASS |
| V2-3-15 integration | `HydrologyPhaseGateV2Test`, full suite, v1 compatibility and Release 1/V2-2 regression | PASS |

## Determinism and resource gate

All canonical numeric paths use integer/fixed-point values and stable IDs. The feature tests compare whole sampling with forward/reverse tiled windows and one/multiple workers; relevant planners also reverse candidate, path, point, or descriptor input. Validator metrics and preview checksums are independent of generator-private `evaluate()` state.

The maximum routing fixture admits `1,000,000` cells with peak working set below 40 MiB and retained result below 6 MiB. Regional generators allocate bounded core-plus-halo windows and reject oversized full-world windows; no `1000 × 1000 × 512` dense voxel or all-block list exists. Preview rendering holds one bounded image at a time. Reconciliation work is fixed to three passes and is admitted as iteration × working-set × artifact bytes. Cancellation is observed before atomic commit, while committed bundles are not deleted by a late cancel.

## Release, security, and compatibility gate

`hydrology-plan` is accepted only with `surface-2_5d`. Its plan, routing index/sidecars, reconciliation plan/artifact, validation artifact, preview index, and 12 PNGs form an exact versioned set. Directory and ZIP verification apply the same path, entry, byte, checksum, semantic-binding, and capability policy. Missing, extra, future, dependency, graph-checksum, and content tampering fail closed.

The complete suite retains TerrainIntent/WorldBlueprint v1 strict Schema behavior, generator `3.0.0-phase6` terrain and full block-stream goldens, Release format 1 allowlist/directory/ZIP verification, placement/rollback/Undo/Recovery behavior, and the V2-2 `surface-2_5d` capability. V2 types remain outside the v1 publisher and placement paths.

## Verification commands

```text
./gradlew test --tests '*HydrologyPhaseGateV2Test'
./gradlew test --tests '*Hydrology*' --tests '*MeanderingRiver*' --tests '*LakeGeneratorV2Test' --tests '*CanyonGeneratorV2Test' --tests '*WaterfallGeneratorV2Test' --tests '*DeltaGeneratorV2Test' --tests '*TidalChannelGeneratorV2Test' --tests '*FjordGeneratorV2Test' --tests '*MountainGeneratorV2Test' --tests '*VolcanicGeneratorV2Test'
./gradlew test
./gradlew build
git diff --check
```

## Remaining boundaries

- V2-4 begins at `V2-4-01`; no geology/climate/ecology implementation is included here.
- Falling water/behind-fall volume remains V2-5 scope.
- Release 2 remains offline-only until the V2-5 gate and V2-6 placement safety tasks complete.
- Current-build FAWE standalone smoke, 500×500 real-world apply/Undo measurement, and the Beta release checklist remain open in the separate Beta hardening track.
