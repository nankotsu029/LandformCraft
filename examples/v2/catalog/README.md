# V2 Feature Support Catalog examples

Sealed V2-6-18 Feature Support Catalog (`feature-support-catalog-v1`).

- Schema: `schemas/feature-support-catalog-v2.schema.json`
- Built-in: `BuiltInFeatureSupportCatalogV2`
- Placement dimension hard limit: **64×64** (WE／FAWE smoke evidence). 500／1000 Paper apply are not `SUPPORTED`.
- Paper capability columns (`paper_apply`／`post_apply_validation`／`snapshot`／`rollback`／`restart_recovery`) were promoted by `V2-11-01` only for the smoke-evidenced `surface-2_5d` prefix: `SANDY_BEACH`, `BREAKWATER_HARBOR`, `HARBOR_BASIN`, `ROCKY_CAPE` at 64×64 on `paper-1.21.11+worldedit-7.3.19|fawe-2.15.2`. `hydrology-plan`／`environment-fields`／`sparse-volume` entries stay `EXPERIMENTAL`; entries without a Release capability (V2-9／V2-10 foundation) stay `UNSUPPORTED`.
- `lifecycleStatus` is display-only compatibility with module descriptors.

## Canonical feature projection example

`meandering-river.terrain-intent-v2-canonical.json` is the `CANONICAL_V2` target for
`../hydrology/meandering-river.terrain-intent-v2.json`, read explicitly as `LEGACY_V2`.
It demonstrates the approved `MEANDERING_RIVER` → `RIVER.morphology=MEANDERING`
mapping and embeds the complete legacy seed tuple in canonical checksum semantics.

This example is a migration/authoring contract only. It does not advertise generator,
Release capability, or Paper placement support.
