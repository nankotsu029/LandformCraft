# Volume fixtures (V2-5)

- `volume-sdf-primitive-plan-v2.json` — V2-5-01 sealed SDF primitive contract (`volume-sdf-fixed-v1`).
- `volume-csg-plan-v2.json` — V2-5-02 ordered CSG plan (`volume-csg-ordered-v1`) bound to the SDF example checksum.
- `volume-aabb-index-plan-v2.json` — V2-5-03 AABB index descriptor (`volume-aabb-index-v1`) bound to the CSG example checksum.
- `volume-tile-cache-plan-v2.json` — V2-5-04 3D tile-cache contract (`volume-tile-cache-v1`) bound to the CSG example checksum.
- `cave-network-plan-v2.json` — V2-5-06 `CAVE_NETWORK` plan (`cave-network-v1`) with SDF/CSG bindings.
- `lush-cave-plan-v2.json` — V2-5-07 `LUSH_CAVE` plan (`lush-cave-v1`) with WITHIN/REACHABLE_FROM host bindings and wet-surface/ecology hooks.
- `underground-lake-plan-v2.json` — V2-5-08 `UNDERGROUND_LAKE` plan (`underground-lake-v1`) with basin CARVE→ADD_FLUID and cave host bindings.
- `sea-cave-plan-v2.json` — V2-5-09 `SEA_CAVE` plan (`sea-cave-v1`) with cliff CARVES_FLANK_OF, marine EMPTIES_INTO, and static ADD_FLUID.
- `overhang-plan-v2.json` — V2-5-10 `OVERHANG` plan (`overhang-v1`) with host SUPPORTS_FROM, ADD_SOLID lobe, and CARVE_SOLID recess.
- `natural-arch-plan-v2.json` — V2-5-11 `NATURAL_ARCH` plan (`natural-arch-v1`) with two-pier ADD_SOLID mass and through CARVE_SOLID opening.
- `sky-island-group-plan-v2.json` — V2-5-12 `SKY_ISLAND_GROUP` plan (`sky-island-group-v1`) with ordered multipoint ADD_SOLID lobes and underside CARVE_SOLID.
- `waterfall-volume-plan-v2.json` — V2-5-13 waterfall volume plan (`waterfall-volume-v1`) bound to fall geometry checksum with column/behind/plunge static ADD_FLUID.
- `volume-validation-artifact-v2.json` — V2-5-15 sealed healthy volume validation evidence (`volume-validator-v1`).
- `offline-volume-tile-artifact-v2.json` — V2-5-16 metadata for a 3D volume tile (cave air cavity, floating solid, fluid pool) exported to Sponge v3 and offline read-back; same `offline-tile-artifact-v2.schema.json` as the V2-2 coastal tile, with a 16×16×16 release-local window.

V2-5-05 TerrainQuery volume composition (`terrain-query-volume-v1`) is a pure Java query API and has no additional JSON fixture. Release `sparse-volume` remains out of scope until `V2-5-17`.
