package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * COMPOSITE_PRESET for multiple WATERFALL nodes + plunge pools on a general river graph.
 * Not a FeatureKind and not a dedicated world generator (V2-9-13).
 */
public record WaterfallChainPlanV2(
        int planVersion,
        String chainId,
        String contractVersion,
        String riverFeatureId,
        String riverPlanChecksum,
        List<String> waterfallNodeIds,
        List<PlungePoolRef> plungePools,
        long totalDropMillionths,
        int waterfallCount,
        int supportRadiusXZ,
        long estimatedGraphWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "waterfall-chain-preset-contract-v1";
    public static final String MODULE_ID = "v2.foundation.waterfall-chain";
    public static final String MODULE_VERSION = "0.1.0-v2-9-13";
    public static final int MAXIMUM_WATERFALLS = 8;
    public static final long MAXIMUM_GRAPH_WORK_UNITS = 16_000_000L;

    public WaterfallChainPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("waterfall chain planVersion must be 1");
        }
        chainId = FoundationValidationV2.slug(chainId, "chainId");
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown waterfall chain contract version");
        }
        riverFeatureId = FoundationValidationV2.slug(riverFeatureId, "riverFeatureId");
        riverPlanChecksum = FoundationValidationV2.checksum(riverPlanChecksum, "riverPlanChecksum");
        waterfallNodeIds = FoundationValidationV2.immutable(waterfallNodeIds, "waterfallNodeIds", MAXIMUM_WATERFALLS)
                .stream()
                .map(id -> FoundationValidationV2.slug(id, "waterfallNodeId"))
                .toList();
        plungePools = FoundationValidationV2.sorted(plungePools, "plungePools", MAXIMUM_WATERFALLS,
                Comparator.comparing(PlungePoolRef::childId));
        if (waterfallNodeIds.size() < 2) {
            throw new IllegalArgumentException("waterfall chain requires at least two WATERFALL nodes");
        }
        if (waterfallCount != waterfallNodeIds.size() || plungePools.size() != waterfallNodeIds.size()) {
            throw new IllegalArgumentException("waterfall chain counts must match node and plunge-pool lists");
        }
        if (totalDropMillionths < TerrainIntentV2.FIXED_SCALE) {
            throw new IllegalArgumentException("waterfall chain total drop must be at least one block");
        }
        if (supportRadiusXZ < 2 || supportRadiusXZ > 64
                || estimatedGraphWorkUnits < 1 || estimatedGraphWorkUnits > MAXIMUM_GRAPH_WORK_UNITS) {
            throw new IllegalArgumentException("waterfall chain support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
        Objects.requireNonNull(waterfallNodeIds, "waterfallNodeIds");
        Objects.requireNonNull(plungePools, "plungePools");
    }

    public WaterfallChainPlanV2 withCanonicalChecksum(String checksum) {
        return new WaterfallChainPlanV2(
                planVersion, chainId, contractVersion, riverFeatureId, riverPlanChecksum,
                waterfallNodeIds, plungePools, totalDropMillionths, waterfallCount,
                supportRadiusXZ, estimatedGraphWorkUnits, geometryChecksum, checksum);
    }

    public record PlungePoolRef(String childId, String waterfallNodeId, int radiusBlocks, int depthBlocks) {
        public PlungePoolRef {
            childId = FoundationValidationV2.slug(childId, "childId");
            waterfallNodeId = FoundationValidationV2.slug(waterfallNodeId, "waterfallNodeId");
            if (radiusBlocks < 2 || radiusBlocks > 32 || depthBlocks < 1 || depthBlocks > 16) {
                throw new IllegalArgumentException("plunge pool ref dimensions are invalid");
            }
        }
    }
}
