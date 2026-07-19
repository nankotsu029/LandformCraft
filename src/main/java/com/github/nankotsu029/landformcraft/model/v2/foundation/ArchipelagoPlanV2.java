package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Frozen V2-9-07 execution plan for an EXPERIMENTAL archipelago foundation profile. */
public record ArchipelagoPlanV2(
        int planVersion,
        String featureId,
        List<IslandMass> islands,
        List<Saddle> saddles,
        int dominantIslandIndex,
        int minDryLandGapBlocks,
        int selectedSubmarineSaddleDepthBlocks,
        int minY,
        int maxY,
        int waterLevel,
        int width,
        int length,
        String massMaskFieldId,
        String saddleFieldId,
        String dominanceFieldId,
        String gapFieldId,
        String solidOwnershipFieldId,
        int supportRadiusXZ,
        long estimatedRasterWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String MODULE_ID = "v2.foundation.archipelago";
    public static final String MODULE_VERSION = "0.1.0-v2-9-07";
    public static final String CONTRACT = "archipelago-plan-contract-v1";
    public static final long MAXIMUM_RASTER_WORK_UNITS = 64_000_000L;
    public static final int MAXIMUM_ISLANDS = 64;
    public static final String MASS_MASK_FIELD_ID = "foundation.archipelago.mass-mask";
    public static final String SADDLE_FIELD_ID = "foundation.archipelago.saddle";
    public static final String DOMINANCE_FIELD_ID = "foundation.archipelago.dominance";
    public static final String GAP_FIELD_ID = "foundation.archipelago.gap";
    public static final String SOLID_OWNERSHIP_FIELD_ID = "foundation.archipelago.solid-ownership";

    public ArchipelagoPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("archipelago planVersion must be 1");
        }
        featureId = FoundationValidationV2.slug(featureId, "featureId");
        islands = FoundationValidationV2.sorted(islands, "islands", MAXIMUM_ISLANDS,
                Comparator.comparing(IslandMass::pointId));
        saddles = FoundationValidationV2.sorted(saddles, "saddles", MAXIMUM_ISLANDS * MAXIMUM_ISLANDS,
                Comparator.comparing(Saddle::fromPointId).thenComparing(Saddle::toPointId));
        if (islands.size() < 2) {
            throw new IllegalArgumentException("archipelago needs at least two islands");
        }
        Set<String> ids = new HashSet<>();
        for (IslandMass island : islands) {
            if (!ids.add(island.pointId())) {
                throw new IllegalArgumentException("duplicate archipelago island pointId");
            }
        }
        if (dominantIslandIndex < 0 || dominantIslandIndex >= islands.size()) {
            throw new IllegalArgumentException("dominantIslandIndex is out of range");
        }
        if (minDryLandGapBlocks < 4 || minDryLandGapBlocks > 64
                || selectedSubmarineSaddleDepthBlocks < 4 || selectedSubmarineSaddleDepthBlocks > 64) {
            throw new IllegalArgumentException("archipelago gap/saddle dimensions are invalid");
        }
        if (width < 2 || width > 1_000 || length < 2 || length > 1_000 || minY >= maxY
                || waterLevel < minY || waterLevel > maxY) {
            throw new IllegalArgumentException("archipelago bounds are invalid");
        }
        long maxX = Math.multiplyExact((long) width - 1L, TerrainIntentV2.FIXED_SCALE);
        long maxZ = Math.multiplyExact((long) length - 1L, TerrainIntentV2.FIXED_SCALE);
        int maxRadius = 0;
        for (IslandMass island : islands) {
            if (island.centerXMillionths() < 0 || island.centerXMillionths() > maxX
                    || island.centerZMillionths() < 0 || island.centerZMillionths() > maxZ) {
                throw new IllegalArgumentException("archipelago island center is out of bounds");
            }
            maxRadius = Math.max(maxRadius, island.radiusBlocks());
        }
        for (Saddle saddle : saddles) {
            if (!ids.contains(saddle.fromPointId()) || !ids.contains(saddle.toPointId())) {
                throw new IllegalArgumentException("archipelago saddle references unknown island");
            }
            if (saddle.fromPointId().compareTo(saddle.toPointId()) >= 0) {
                throw new IllegalArgumentException("archipelago saddle endpoints must be ordered");
            }
        }
        massMaskFieldId = FoundationValidationV2.qualified(massMaskFieldId, "massMaskFieldId");
        saddleFieldId = FoundationValidationV2.qualified(saddleFieldId, "saddleFieldId");
        dominanceFieldId = FoundationValidationV2.qualified(dominanceFieldId, "dominanceFieldId");
        gapFieldId = FoundationValidationV2.qualified(gapFieldId, "gapFieldId");
        solidOwnershipFieldId = FoundationValidationV2.qualified(solidOwnershipFieldId, "solidOwnershipFieldId");
        if (supportRadiusXZ < maxRadius || supportRadiusXZ > 64
                || estimatedRasterWorkUnits < 1 || estimatedRasterWorkUnits > MAXIMUM_RASTER_WORK_UNITS) {
            throw new IllegalArgumentException("archipelago support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
    }

    public ArchipelagoPlanV2 withCanonicalChecksum(String checksum) {
        return new ArchipelagoPlanV2(
                planVersion, featureId, islands, saddles, dominantIslandIndex, minDryLandGapBlocks,
                selectedSubmarineSaddleDepthBlocks, minY, maxY, waterLevel, width, length,
                massMaskFieldId, saddleFieldId, dominanceFieldId, gapFieldId, solidOwnershipFieldId,
                supportRadiusXZ, estimatedRasterWorkUnits, geometryChecksum, checksum);
    }

    public record IslandMass(
            String pointId,
            long centerXMillionths,
            long centerZMillionths,
            int radiusBlocks,
            int summitHeightBlocksAboveSea
    ) {
        public IslandMass {
            pointId = FoundationValidationV2.slug(pointId, "pointId");
            if (radiusBlocks < 8 || radiusBlocks > 256
                    || summitHeightBlocksAboveSea < 8 || summitHeightBlocksAboveSea > 256
                    || centerXMillionths < 0 || centerZMillionths < 0) {
                throw new IllegalArgumentException("archipelago island mass is invalid");
            }
        }
    }

    public record Saddle(String fromPointId, String toPointId, int depthBlocks) {
        public Saddle {
            fromPointId = FoundationValidationV2.slug(fromPointId, "fromPointId");
            toPointId = FoundationValidationV2.slug(toPointId, "toPointId");
            if (depthBlocks < 4 || depthBlocks > 64) {
                throw new IllegalArgumentException("archipelago saddle depth is invalid");
            }
        }
    }
}
