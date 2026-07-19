package com.github.nankotsu029.landformcraft.core.v2.placement;

import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementCanonicalBlockSourceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementDesiredBlockV2;
import com.github.nankotsu029.landformcraft.model.v2.release.ReleaseCapabilityDependencyMatrixV2;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Common placement input contract for Release 2 capability prefixes (V2-6-12).
 * Future foundation/bathymetry outputs must map into the existing feature-neutral
 * {@link PlacementCanonicalBlockSourceV2} overlay ordinal stream; they must not introduce
 * Feature-specific placement types.
 */
public final class ReleasePlacementInputContractV2 {
    public static final String CONTRACT_VERSION = "release-2-placement-input-contract-v1";

    /** Overlay ordinals reserved for surface solid / air carve / fluid layers. */
    public static final int OVERLAY_SURFACE_SOLID = 0;
    public static final int OVERLAY_SURFACE_AIR = 1;
    public static final int OVERLAY_SURFACE_FLUID = 2;
    /** Sparse-volume overlays reuse the same ordinal space above surface. */
    public static final int OVERLAY_VOLUME_SOLID = 3;
    public static final int OVERLAY_VOLUME_AIR = 4;
    public static final int OVERLAY_VOLUME_FLUID = 5;

    private static final Set<String> FOUNDATION_HOST_KINDS = Set.of(
            "PLAIN", "HILL_RANGE", "MOUNTAIN_RANGE", "VALLEY", "PLATEAU", "ESCARPMENT",
            "FLOODPLAIN", "MARSH", "RIVER", "SINGLE_ISLAND", "ARCHIPELAGO", "ATOLL",
            "BARRIER_ISLAND", "ROCKY_COAST", "SEA_CLIFF", "CONTINENTAL_SHELF",
            "CONTINENTAL_SLOPE", "OCEAN_BASIN", "ABYSSAL_PLAIN", "SEAMOUNT",
            "SUBMARINE_CANYON", "MORAINE_FIELD", "OUTWASH_PLAIN");

    private ReleasePlacementInputContractV2() {
    }

    public static List<Integer> overlayOrdinalsFor(List<String> requiredCapabilities) {
        ReleaseCapabilityDependencyMatrixV2.requireValidPrefix(requiredCapabilities);
        List<String> prefix = ReleaseCapabilityDependencyMatrixV2.normalize(requiredCapabilities);
        if (prefix.isEmpty() || prefix.equals(ReleaseCapabilityDependencyMatrixV2.SURFACE_ONLY)
                || prefix.equals(ReleaseCapabilityDependencyMatrixV2.HYDROLOGY_WITH_SURFACE)
                || prefix.equals(ReleaseCapabilityDependencyMatrixV2.ENVIRONMENT_WITH_HYDROLOGY_AND_SURFACE)) {
            return List.of(OVERLAY_SURFACE_SOLID, OVERLAY_SURFACE_AIR, OVERLAY_SURFACE_FLUID);
        }
        if (prefix.equals(ReleaseCapabilityDependencyMatrixV2.SPARSE_VOLUME_WITH_ENVIRONMENT)) {
            return List.of(
                    OVERLAY_SURFACE_SOLID, OVERLAY_SURFACE_AIR, OVERLAY_SURFACE_FLUID,
                    OVERLAY_VOLUME_SOLID, OVERLAY_VOLUME_AIR, OVERLAY_VOLUME_FLUID);
        }
        throw new IllegalArgumentException("unsupported capability prefix for placement overlays");
    }

    /**
     * Proves a foundation/bathymetry host kind can be placed only through the shared overlay
     * ordinal stream (no Feature-specific placement type).
     */
    public static PlacementCanonicalBlockSourceV2.SourceBindingV2 foundationBathymetryBinding(
            String releaseManifestChecksum,
            List<String> requiredCapabilities,
            String hostFeatureKind,
            String immutableFingerprint
    ) {
        Objects.requireNonNull(hostFeatureKind, "hostFeatureKind");
        String kind = hostFeatureKind.trim().toUpperCase(Locale.ROOT);
        if (!FOUNDATION_HOST_KINDS.contains(kind)) {
            throw new IllegalArgumentException(
                    "foundation/bathymetry host kind is not admitted to the shared placement stream: "
                            + hostFeatureKind);
        }
        List<Integer> overlays = overlayOrdinalsFor(requiredCapabilities);
        for (Integer ordinal : overlays) {
            if (ordinal > PlacementDesiredBlockV2.MAXIMUM_OVERLAY_ORDINAL) {
                throw new IllegalArgumentException("overlay ordinal exceeds shared placement budget");
            }
        }
        return new PlacementCanonicalBlockSourceV2.SourceBindingV2(
                PlacementCanonicalBlockSourceV2.SOURCE_CONTRACT_VERSION,
                releaseManifestChecksum,
                ReleaseCapabilityDependencyMatrixV2.normalize(requiredCapabilities),
                overlays,
                immutableFingerprint
        );
    }

    public static boolean admitsFoundationHostKind(String hostFeatureKind) {
        if (hostFeatureKind == null || hostFeatureKind.isBlank()) {
            return false;
        }
        return FOUNDATION_HOST_KINDS.contains(hostFeatureKind.trim().toUpperCase(Locale.ROOT));
    }
}
