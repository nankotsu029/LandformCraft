package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Objects;
import java.util.Set;

/**
 * COMPOSITE_PRESET for flooded karst sinkhole ({@code SINKHOLE + CAVE_NETWORK + FLOODED_CAVE}).
 * Not a FeatureKind and not a dedicated world generator (V2-10-03).
 */
public record CenotePlanV2(
        int planVersion,
        String presetId,
        String contractVersion,
        String sinkholeFeatureId,
        String sinkholePlanChecksum,
        String caveNetworkFeatureId,
        String caveNetworkCanonicalChecksum,
        String fluidBodyId,
        String floodedHookFeatureId,
        String materialHandoffId,
        int supportRadiusXZ,
        long estimatedWorkUnits,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "cenote-preset-contract-v1";
    public static final String MODULE_ID = "v2.foundation.cenote";
    public static final String MODULE_VERSION = "0.1.0-v2-10-03";
    public static final long MAXIMUM_WORK_UNITS = 16_000_000L;

    public CenotePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("cenote planVersion must be 1");
        }
        presetId = FoundationValidationV2.slug(presetId, "presetId");
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown cenote contract version");
        }
        sinkholeFeatureId = FoundationValidationV2.slug(sinkholeFeatureId, "sinkholeFeatureId");
        sinkholePlanChecksum = FoundationValidationV2.checksum(sinkholePlanChecksum, "sinkholePlanChecksum");
        caveNetworkFeatureId = FoundationValidationV2.slug(caveNetworkFeatureId, "caveNetworkFeatureId");
        caveNetworkCanonicalChecksum = FoundationValidationV2.checksum(
                caveNetworkCanonicalChecksum, "caveNetworkCanonicalChecksum");
        fluidBodyId = FoundationValidationV2.slug(fluidBodyId, "fluidBodyId");
        floodedHookFeatureId = FoundationValidationV2.optionalSlug(
                floodedHookFeatureId, "floodedHookFeatureId");
        materialHandoffId = FoundationValidationV2.qualified(materialHandoffId, "materialHandoffId");
        if (!SinkholePlanV2.MATERIAL_HANDOFF_ID.equals(materialHandoffId)) {
            throw new IllegalArgumentException("cenote materialHandoffId must be karst-limestone-handoff");
        }
        if (supportRadiusXZ < 2 || supportRadiusXZ > 64
                || estimatedWorkUnits < 1L || estimatedWorkUnits > MAXIMUM_WORK_UNITS) {
            throw new IllegalArgumentException("cenote support or work budget is invalid");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
        Objects.requireNonNull(Set.of(sinkholeFeatureId, caveNetworkFeatureId), "composition ids");
    }

    public CenotePlanV2 withCanonicalChecksum(String checksum) {
        return new CenotePlanV2(
                planVersion, presetId, contractVersion, sinkholeFeatureId, sinkholePlanChecksum,
                caveNetworkFeatureId, caveNetworkCanonicalChecksum, fluidBodyId, floodedHookFeatureId,
                materialHandoffId, supportRadiusXZ, estimatedWorkUnits, geometryChecksum, checksum);
    }
}
