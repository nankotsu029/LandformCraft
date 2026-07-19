# V2-4 Environment Phase gate audit

> Status: PASS — `V2-4-15` completed. This is an offline generation／validation／Release gate; public CLI／Paper dispatch, world mutation, cave-local environment, and real-world measurement remain disabled.

## Decision

`V2-4-01` through `V2-4-14` satisfy the V2-4 parent gate. The offline lifecycle is `SUPPORTED` for the geology／climate／regional-water／snow infrastructure, ALPINE_MOUNTAIN_RANGE, GLACIAL_MOUNTAIN_RANGE, MANGROVE_WETLAND, CORAL_REEF, VOLCANIC_ARCHIPELAGO, and the Release 2 `environment-fields` capability. CANYON remains `SUPPORTED` from V2-3, with its ordered strata／feature-material path now included in the supported environment portfolio.

WATERFALL remains `EXPERIMENTAL` until its V2-5 falling-water and behind-fall volume work is complete. GLACIAL_CIRQUE_FIELD, LAGOON, REEF_PASS, VOLCANIC_CALDERA, and LAVA_FLOW_FIELD remain `EXPERIMENTAL` child-plan hooks. Sparse ecology and feature-material plans are sealed offline components of `environment-fields`; they are not connected to `WorldBlueprintV2` public dispatch, Paper placement, entity placement, biome mutation, or cave-local environment.

## Integrated scenario portfolio

- Executable gate: `EnvironmentPhaseGateV2Test`
- Strict examples: snowy mountains, mangrove wetland, coral reef, volcanic archipelago, canyon plus waterfall
- Bounds: `257 × 257` for canonical portfolio comparison; `1000 × 1000` for maximum-dimension environment admission
- Composition: canonical Blueprint plus sealed snow, semantic material, Minecraft palette, sparse ecology, and feature-material plans
- Comparison: forward／reverse scenario order, one／four compiler threads, reversed module／stage input, default／Turkish locale, default／Chatham timezone
- Result: every plan checksum is stable for the same input, supported and deferred kinds have the exact lifecycle above, and the capability dependency order is exactly `environment-fields → hydrology-plan → surface-2_5d`

## Task evidence matrix

| Task | Evidence | Result |
|---|---|---|
| V2-4-01 geology | `GeologyPlanCompilerV2Test`, `GeologyFieldBundleV2Test`: typed fields, strict sidecar read-back, checksum／ownership／future-version rejection, seed／locale／timezone and admission | PASS |
| V2-4-02 lithology | `LithologyPlanCompilerV2Test`: closed catalog, province binding, strict round-trip, candidate／thread／locale／timezone stability, unknown compact-code rejection | PASS |
| V2-4-03 strata | `StrataPlanCompilerV2Test`: ordered layers／derived scalars, exposure, whole／tile／seam／thread stability, thin／inverted／unknown／budget rejection | PASS |
| V2-4-04 climate | `ClimatePlanCompilerV2Test`: separate prior／final fields, explicit Hydrology version transition, whole／tile／thread／locale／timezone stability, 1000-square bounded windows | PASS |
| V2-4-05 water condition | `WaterConditionPlanCompilerV2Test`: seven regional fields, river／lake／tide gradients, no-data／disconnect／diffusion rejection, whole／tile／thread stability, 1000-square budget | PASS |
| V2-4-06 snow | `SnowPlanV2Test`, `SnowFieldSamplerV2Test`: climate binding, potential／cover rules, strict version／checksum handling, deterministic bounded sampling | PASS |
| V2-4-07 semantic material | `MaterialProfilePlanCompilerV2Test`, `MaterialProfileResolverV2Test`: fixed rule order, upstream checksum binding, rock／sediment wet／snow resolution, whole／tile／thread stability | PASS |
| V2-4-08 Minecraft palette | `MinecraftPalettePlanCompilerV2Test`, `MinecraftPaletteResolverV2Test`, `MinecraftPalettePlanV2Test`: closed 1.21.11 mapping, VarInt boundary, strict Sponge read-back, checksum and unknown-state rejection | PASS |
| V2-4-09 mangrove | `MangroveGeneratorV2Test`, `MangroveBlueprintWiringTest`: tidal-link ownership, channel protection, whole／tile／seam／thread／path stability, dry／hard-conflict／future-input rejection | PASS |
| V2-4-10 coral | `CoralReefGeneratorV2Test`, `CoralBlueprintWiringTest`: lagoon／pass marine connection, whole／tile／seam／thread／path stability, sealed-lagoon／relation／future-input rejection | PASS |
| V2-4-11 ecology | `EcologyPlanV2Test`, `EcologyPlanCompilerV2Test`, `EcologyPlacementResolverV2Test`: closed habitats／assemblages, support／spacing, whole／tile／seam／thread／candidate stability, sparse-window budget rejection | PASS |
| V2-4-12 feature material | `FeatureMaterialProfilePlanV2Test`, compiler／resolver tests: volcanic basalt／tuff／ash, canyon strata／talus／floor sediment, explicit conflict order, shape checksum regression | PASS |
| V2-4-13 validation／preview | `EnvironmentValidatorV2Test`, `EnvironmentDiagnosticPreviewRendererV2Test`: independent corruption detection, strict validation round-trip, fixed 10-layer index, symlink／extra／checksum／budget rejection, cancel cleanup | PASS |
| V2-4-14 Release capability | `ReleaseEnvironmentPublisherVerifierV2Test`: exact environment artifact set, directory／ZIP parity, surface／hydrology regression, missing／extra／dependency／future／version／palette tampering, cancel cleanup | PASS |
| V2-4-15 integration | `EnvironmentPhaseGateV2Test`, `HydrologyPhaseGateV2Test`, full suite, v1／Release 1／V2-2／V2-3 compatibility regression | PASS |

## Determinism and resource gate

The integrated portfolio compares Blueprint, snow, semantic-material, Minecraft-palette, ecology, and feature-material canonical checksums. All numeric generation paths remain integer／fixed-point and use global X/Z sampling, stable identifiers, fixed rule order, and canonical module／stage ordering. Component tests retain whole versus tiled, seam, tile order, worker count, candidate／path order, locale, timezone, and cancel coverage.

The `1000 × 1000` fixture admits exactly `1,000,000` cells. The maximum declared retained-plus-working stage peak is bounded to 32 MiB; ecology and feature-material working windows are each at most 4 MiB, ecology placement descriptors are capped at 65,536 per window, and the palette retained budget is at most 16 MiB. No dense `1000 × 1000 × 512` voxel array or full block list is introduced. Preview and Release publishers observe cancellation before atomic commit and clean staging on pre-commit cancellation.

## Release, security, and compatibility gate

`environment-fields` is accepted only with `hydrology-plan` and `surface-2_5d`. Geology, lithology, strata, climate, water condition, snow, semantic material, palette, ecology, feature material, validation, preview index, and ten PNGs form an exact versioned set. Directory and ZIP readers use the same path, entry-count, byte, checksum, semantic-binding, and dependency policy and reject unknown, missing, extra, future, dependency, version, and palette tampering.

The complete suite retains TerrainIntent／WorldBlueprint v1 strict Schema behavior, generator `3.0.0-phase6` terrain and block-stream goldens, Release format 1 allowlist and directory／ZIP verification, v1 placement／rollback／Undo／Recovery, V2-2 `surface-2_5d`, and V2-3 `hydrology-plan`. V2 environment types remain outside the v1 publisher and Paper placement paths.

## Verification commands

```text
./gradlew test --tests '*EnvironmentPhaseGateV2Test'
./gradlew test --tests '*EnvironmentPhaseGateV2Test' --tests '*Geology*' --tests '*Lithology*' --tests '*Strata*' --tests '*Climate*' --tests '*WaterCondition*' --tests '*Snow*' --tests '*Mountain*' --tests '*Mangrove*' --tests '*Coral*' --tests '*Volcanic*' --tests '*Canyon*' --tests '*MaterialProfile*' --tests '*MinecraftPalette*' --tests '*Ecology*' --tests '*EnvironmentValidator*' --tests '*EnvironmentDiagnostic*' --tests '*ReleaseEnvironment*' --tests '*HydrologyPhaseGateV2Test' --tests '*DiagnosticBlueprintCompilerV2Test'
./gradlew clean test
./gradlew build
git diff --check
```

## Remaining boundaries

- Track A continues at `V2-5-01`; no V2-5 implementation is included in this gate.
- Falling water, behind-fall cavity, caves, lush cave environment, and all other sparse local volume remain V2-5 scope.
- Public CLI／Paper dispatch, Release 2 placement, world mutation, Undo／Recovery integration, and real-world measurement remain V2-6 scope.
- Current-build FAWE standalone smoke, 500×500 real-world apply／Undo measurement, and the Beta release checklist remain open in the separate Beta hardening work.
