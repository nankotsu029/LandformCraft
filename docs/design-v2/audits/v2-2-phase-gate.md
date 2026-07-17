# V2-2 Coastal 2.5D Phase gate audit

> Status: PASS — `V2-2-12` completed. This is an offline generation/export gate; Release 2 Paper apply remains disabled.

## Decision

`V2-2-01` through `V2-2-11` are integrated by the Azure Coast phase fixture and satisfy the V2-2 parent gate. The built-in `SANDY_BEACH`, `HARBOR_BASIN`, `BREAKWATER_HARBOR`, and strictly 2.5D `ROCKY_CAPE` lifecycle is `SUPPORTED` for the offline `surface-2_5d` capability.

This decision does not support `BACKSHORE_PLAINS`, geology/climate/ecology material profiles, Hydrology v2, local volume, provider/CLI/Paper generation dispatch, or world mutation. The integration fixture uses the bounded V2-2 coastal export palette; V2-4 remains responsible for the semantic Minecraft palette adapter.

## Canonical fixture

- Request contract: [`examples/v2/diagnostic/azure-coast.request-v2.json`](../../../examples/v2/diagnostic/azure-coast.request-v2.json)
- Intent contract: [`examples/v2/diagnostic/azure-coast.terrain-intent-v2.json`](../../../examples/v2/diagnostic/azure-coast.terrain-intent-v2.json)
- Executable evidence: `AzureCoastPhaseGateV2Test`
- Bounds: `400 × 400 × Y 32..72`, water level `50`, tile size `128`, seed `827413`

The fixture compiles all four coastal plans and the explicit transition DAG, freezes a checksum-bound HARD land/water desired/actual/residual field set, runs the final field-only validator, emits the fixed 11-layer preview bundle, resolves a bounded canonical block stream, writes all 16 Sponge v3 tiles, and publishes/verifies directory and ZIP Release 2 artifacts. The constraint-map numeric decoder and filesystem boundary remain the V2-1 responsibility and are not merged into the coastal generator.

## Acceptance evidence

| Gate | Evidence | Result |
|---|---|---|
| Four feature contract/generator | Azure compile plus existing beach, basin, breakwater, cape positive/boundary/corruption tests | PASS |
| Hard land/water | every frozen desired sidecar cell equals the actual final surface; all active coastal cells report hard protection | PASS |
| Transition | reverse module bindings produce the same versioned whole-field checksums; conflict/corruption portfolio remains enabled | PASS |
| Validation/preview | hard validation passes with beach width, harbor depth/opening, cape exposure/complexity metrics; 11 strict PNG layers | PASS |
| Whole/tile block stream | direct and tile-dispatched canonical stream checksums match over the maximum 256-square whole window | PASS |
| Tile order/thread/defaults | forward single-thread and reverse four-thread export match under Turkish locale and Chatham timezone | PASS |
| Sponge/WorldEdit | all tiles receive strict Sponge inspection; beach, harbor/breakwater, and cape representative tiles pass WorldEdit 7.3.19 offline read-back | PASS |
| Release 2 | full X/Z coverage, directory/ZIP manifest parity, semantic checksum chain, missing/extra/future/tamper portfolio | PASS |
| Cleanup/security | cancelled publish leaves no canonical/staging release; reduced disk/artifact budget and preview tamper are rejected | PASS |
| Compatibility | complete test suite retains v1 goldens, generator `3.0.0-phase6`, Release 1, placement, rollback, and Undo behavior | PASS |

General VarInt `127`, `128`, and `16383` encoding boundaries remain covered by `OfflineTileSchematicWriterV2Test`; the Azure palette itself intentionally stays within the compile-time coastal allowlist.

## Resource admission

The 1000-square admission test uses one 128-square core plus the declared per-stage halo and never allocates a `1000 × 1000` raster or a dense 3D world:

| Retained component | Upper bound |
|---|---:|
| Common coastal raster, `256 × 256` | 1,703,936 bytes |
| Beach/basin/breakwater/cape windows, four `256 × 256` windows | 4,456,448 bytes |
| Transition window, `192 × 192` | 737,536 bytes |
| Simultaneous generation upper bound | 6,897,920 bytes |
| One 1000-square ARGB preview plus allowance | 5,048,576 bytes |
| One `128 × 128 × 41` tile plus palette upper bound | 1,354,752 bytes |

The largest admitted value is below the fixed 96 MiB V2 request resident ceiling. A 1000-square release has exactly 64 horizontal tiles, which is the existing `surface-2_5d` source limit. These are admission bounds, not real-world Paper placement measurements.

## Verification commands

The gate is closed only with all of the following successful in the same worktree:

```text
./gradlew test --tests '*AzureCoastPhaseGateV2Test'
./gradlew test
./gradlew build
git diff --check
```

## Remaining boundaries

- V2-3 hydrology starts at `V2-3-01`; no hydrology implementation is included here.
- Release 2 remains offline-only until the V2-5 gate and V2-6 placement safety tasks complete.
- Current-build FAWE standalone smoke, 500×500 real-world apply/Undo measurement, and the Beta release checklist remain open in the Beta hardening track.

