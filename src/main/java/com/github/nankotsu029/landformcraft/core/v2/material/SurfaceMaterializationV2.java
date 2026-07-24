package com.github.nankotsu029.landformcraft.core.v2.material;

import java.util.Objects;
import java.util.Optional;

/**
 * V2-19-10 binding between the surface role profile and the optional per-cell environment material
 * ({@code surface-material-binding-v1}).
 *
 * <p>The block a resolver writes is decided in two explicit, ordered steps, mirroring the frozen
 * V2-4-07 rule table ({@code BASE_SUBSTRATE_FROM_LITHOLOGY}, then {@code WETNESS_OVERRIDE}, then
 * {@code SNOW_OVERRIDE}):</p>
 *
 * <ol>
 *   <li>the surface role gives the <em>base assignment</em> from {@link SurfaceMaterialProfileV2} —
 *       this is where a beach is sand and a cape is rock, which lithology alone cannot say;</li>
 *   <li>where an environment material source is bound, its <em>conditional overrides</em> replace
 *       the exposed natural surface of the column ({@link SurfaceMaterialProfileV2.LayerV2#NATURAL_SURFACE}
 *       only). Structural cells (bedrock／air／water), built structures (breakwater crest and core)
 *       and every subsurface layer keep the profile's state.</li>
 * </ol>
 *
 * <p>Because the override only ever replaces one solid block state with another solid block state,
 * a bound environment material can change the {@code MATERIAL} effect class of the final canonical
 * block stream and nothing else: it cannot add or remove solid mass and cannot create or destroy a
 * fluid. {@link #builtIn()} binds nothing and is byte-identical to the pre-V2-19-10 resolver.</p>
 */
public record SurfaceMaterializationV2(
        SurfaceMaterialProfileV2 profile,
        Optional<NaturalSurfaceMaterialV2> naturalSurface
) {
    public static final String CONTRACT_VERSION = "surface-material-binding-v1";

    public SurfaceMaterializationV2 {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(naturalSurface, "naturalSurface");
    }

    /** The frozen built-in profile with no environment material bound. */
    public static SurfaceMaterializationV2 builtIn() {
        return new SurfaceMaterializationV2(SurfaceMaterialProfileV2.builtIn(), Optional.empty());
    }

    public static SurfaceMaterializationV2 withNaturalSurface(
            SurfaceMaterialProfileV2 profile,
            NaturalSurfaceMaterialV2 naturalSurface
    ) {
        return new SurfaceMaterializationV2(profile, Optional.of(naturalSurface));
    }

    /**
     * The block state of one cell in one role. Release-local coordinates; pure and independent of
     * tile order, so a tile seam cannot make two neighbouring columns disagree.
     */
    public String blockStateAt(SurfaceMaterialProfileV2.RoleV2 role, int globalX, int globalZ) {
        Objects.requireNonNull(role, "role");
        if (role.layer() == SurfaceMaterialProfileV2.LayerV2.NATURAL_SURFACE
                && naturalSurface.isPresent()) {
            String override = naturalSurface.orElseThrow().blockStateAt(globalX, globalZ);
            if (override != null) {
                return override;
            }
        }
        return profile.blockState(role);
    }

    /**
     * Per-cell exposed-surface material of a bound environment stack. {@code null} means the
     * environment declares no override for the cell and the profile's base assignment stands — an
     * absent override is never substituted with an invented block state.
     */
    @FunctionalInterface
    public interface NaturalSurfaceMaterialV2 {
        String blockStateAt(int globalX, int globalZ);
    }
}
