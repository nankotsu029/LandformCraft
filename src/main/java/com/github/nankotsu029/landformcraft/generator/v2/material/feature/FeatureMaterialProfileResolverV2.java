package com.github.nankotsu029.landformcraft.generator.v2.material.feature;

import com.github.nankotsu029.landformcraft.generator.v2.geology.strata.StrataExposureResolverV2;
import com.github.nankotsu029.landformcraft.model.v2.CanyonPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.VolcanicPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.feature.FeatureMaterialProfilePlanV2;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure per-cell overlay resolving volcanic basalt/tuff/ash and canyon strata/talus/sediment
 * without allocating dense feature grids or mutating shape field checksums.
 */
public final class FeatureMaterialProfileResolverV2 {
    public static final int NO_FEATURE_OVERRIDE = 0;

    private final FeatureMaterialProfilePlanV2 plan;
    private final StrataExposureResolverV2 strataResolver;

    public FeatureMaterialProfileResolverV2(
            MaterialProfilePlanV2 materialProfilePlan,
            GeologyPlanV2 geologyPlan,
            LithologyPlanV2 lithologyPlan,
            StrataPlanV2 strataPlan,
            FeatureMaterialProfilePlanV2 featurePlan
    ) {
        Objects.requireNonNull(featurePlan, "featurePlan");
        featurePlan.requireMaterialProfilePlan(materialProfilePlan);
        featurePlan.requireGeologyPlan(geologyPlan, lithologyPlan, strataPlan);
        this.plan = featurePlan;
        this.strataResolver = new StrataExposureResolverV2(geologyPlan, lithologyPlan, strataPlan);
    }

    public FeatureMaterialProfilePlanV2 plan() {
        return plan;
    }

    public void bindVolcanicPlan(VolcanicPlanV2 volcanicPlan) {
        plan.requireVolcanicPlan(volcanicPlan);
    }

    public void bindCanyonPlan(CanyonPlanV2 canyonPlan) {
        plan.requireCanyonPlan(canyonPlan);
    }

    /**
     * Resolves the feature overlay class code (7–12) or {@link #NO_FEATURE_OVERRIDE} when the
     * base material profile remains authoritative for the cell.
     */
    public int featureClassCodeAt(int globalX, int globalZ, CellInputs inputs) {
        CellInputs validated = requireInputs(inputs);
        Optional<FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass> volcanic =
                volcanicZone(validated);
        Optional<FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass> canyon =
                canyonZone(globalX, globalZ, validated);

        if (volcanic.isPresent() && canyon.isPresent()) {
            return mergeConflict(volcanic.get(), canyon.get()).compactCode();
        }
        if (canyon.isPresent()) {
            return plan.catalog().requireByKind(canyon.get()).classCode();
        }
        if (volcanic.isPresent()) {
            return plan.catalog().requireByKind(volcanic.get()).classCode();
        }
        return NO_FEATURE_OVERRIDE;
    }

    /**
     * Final semantic class for the cell: feature overlay when present, otherwise the provided
     * base material compact code (1–6). Does not re-run the base material resolver.
     */
    public int resolvedClassCodeAt(int globalX, int globalZ, CellInputs inputs, int baseMaterialClassCode) {
        if (baseMaterialClassCode < 1 || baseMaterialClassCode > 6) {
            throw new IllegalArgumentException("base material class code must be in 1..6");
        }
        int feature = featureClassCodeAt(globalX, globalZ, inputs);
        return feature == NO_FEATURE_OVERRIDE ? baseMaterialClassCode : feature;
    }

    public int[] sampleFeatureWindow(
            int startX,
            int startZ,
            int width,
            int length,
            CellInputSource inputSource
    ) {
        validateWindow(startX, startZ, width, length);
        Objects.requireNonNull(inputSource, "inputSource");
        int[] result = new int[Math.multiplyExact(width, length)];
        for (int localZ = 0; localZ < length; localZ++) {
            for (int localX = 0; localX < width; localX++) {
                int globalX = startX + localX;
                int globalZ = startZ + localZ;
                result[localZ * width + localX] = featureClassCodeAt(
                        globalX, globalZ, inputSource.at(globalX, globalZ));
            }
        }
        return result;
    }

    public String featureChecksum(int width, int length, CellInputSource inputSource) {
        Objects.requireNonNull(inputSource, "inputSource");
        if (width < 1 || length < 1
                || width > plan.budget().maximumWindowSize()
                || length > plan.budget().maximumWindowSize()) {
            throw new IllegalArgumentException("feature-material checksum window is invalid");
        }
        MessageDigest digest = sha256();
        digest.update("LFC_FEATURE_MATERIAL_FIELD_V1\n".getBytes(StandardCharsets.UTF_8));
        ByteBuffer cell = ByteBuffer.allocate(Integer.BYTES);
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                cell.clear();
                cell.putInt(featureClassCodeAt(x, z, inputSource.at(x, z)));
                digest.update(cell.array());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private Optional<FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass> volcanicZone(
            CellInputs inputs
    ) {
        FeatureMaterialProfilePlanV2.Kernel kernel = plan.kernel();
        if (inputs.submarineSaddleMask() == 1) {
            return Optional.of(FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_ASH_EXPOSED);
        }
        if (inputs.islandMask() != 1) {
            return Optional.empty();
        }
        long shoreCeiling = Math.multiplyExact(
                (long) inputs.waterLevelBlocks() + kernel.volcanicShoreBandBlocks(), 1_000_000L);
        if (inputs.provisionalSurfaceMillionths() <= shoreCeiling) {
            return Optional.of(FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_ASH_EXPOSED);
        }
        int relief = inputs.summitReliefMillionths();
        if (relief >= kernel.volcanicBasaltMinReliefMillionths()) {
            return Optional.of(FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_BASALT_EXPOSED);
        }
        if (relief >= kernel.volcanicTuffMinReliefMillionths()) {
            return Optional.of(FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_TUFF_EXPOSED);
        }
        if (relief <= kernel.volcanicAshMaxReliefMillionths()) {
            return Optional.of(FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_ASH_EXPOSED);
        }
        return Optional.of(FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.VOLCANIC_TUFF_EXPOSED);
    }

    private Optional<FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass> canyonZone(
            int globalX,
            int globalZ,
            CellInputs inputs
    ) {
        if (inputs.canyonMask() != 1) {
            return Optional.empty();
        }
        if (inputs.floorMask() == 1) {
            return Optional.of(FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.CANYON_FLOOR_SEDIMENT);
        }
        if (inputs.wallHeightMillionths() <= 0) {
            return Optional.empty();
        }
        if (inputs.wallHeightMillionths() <= plan.kernel().canyonTalusMaxWallHeightMillionths()) {
            return Optional.of(FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.CANYON_TALUS);
        }
        int lithologyCode = strataResolver.exposedLithologyCode(inputs.provinceRaw(), globalX, globalZ);
        requireKnownLithology(lithologyCode);
        return Optional.of(FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass.CANYON_STRATA_EXPOSED);
    }

    private static void requireKnownLithology(int lithologyCode) {
        for (LithologyPlanV2.SemanticLithology kind : LithologyPlanV2.SemanticLithology.values()) {
            if (kind.compactCode() == lithologyCode) {
                return;
            }
        }
        throw new IllegalArgumentException("unknown lithology compact code: " + lithologyCode);
    }

    private FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass mergeConflict(
            FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass volcanic,
            FeatureMaterialProfilePlanV2.FeatureSemanticMaterialClass canyon
    ) {
        for (FeatureMaterialProfilePlanV2.ConflictRule rule : plan.conflictRules()) {
            if (rule.ruleId() == FeatureMaterialProfilePlanV2.ConflictRuleId.CANYON_WINS_OVER_VOLCANIC
                    && rule.mergeOperator() == FeatureMaterialProfilePlanV2.ConflictMergeOperator.PRIORITY_OVERRIDE
                    && rule.winner() == FeatureMaterialProfilePlanV2.FeatureKind.CANYON
                    && rule.loser() == FeatureMaterialProfilePlanV2.FeatureKind.VOLCANIC) {
                return canyon;
            }
            if (rule.ruleId() == FeatureMaterialProfilePlanV2.ConflictRuleId.REJECT_UNKNOWN_FEATURE_CLAIM
                    && rule.mergeOperator() == FeatureMaterialProfilePlanV2.ConflictMergeOperator.REJECT) {
                // Reachable only for malformed dual claims outside the priority rule; keep fail-closed.
                throw new IllegalArgumentException("feature-material rejects unresolved dual feature claim");
            }
        }
        throw new IllegalArgumentException("feature-material conflict rules failed to resolve");
    }

    private void validateWindow(int startX, int startZ, int width, int length) {
        if (width < 1 || length < 1
                || width > plan.budget().maximumWindowSize()
                || length > plan.budget().maximumWindowSize()
                || startX < 0 || startZ < 0) {
            throw new IllegalArgumentException("feature-material window is outside declared bounds");
        }
        long requiredBytes = Math.multiplyExact(Math.multiplyExact((long) width, length), Integer.BYTES);
        if (requiredBytes > plan.budget().maximumWorkingBytes()) {
            throw new IllegalArgumentException("feature-material window exceeds working-memory budget");
        }
    }

    private static CellInputs requireInputs(CellInputs inputs) {
        Objects.requireNonNull(inputs, "inputs");
        if (inputs.islandMask() < 0 || inputs.islandMask() > 1
                || inputs.submarineSaddleMask() < 0 || inputs.submarineSaddleMask() > 1
                || inputs.summitReliefMillionths() < 0
                || inputs.provisionalSurfaceMillionths() < Integer.MIN_VALUE / 2
                || inputs.canyonMask() < 0 || inputs.canyonMask() > 1
                || inputs.floorMask() < 0 || inputs.floorMask() > 1
                || inputs.wallHeightMillionths() < 0
                || inputs.wetnessRaw() < 0 || inputs.wetnessRaw() > 1_000) {
            throw new IllegalArgumentException("feature-material cell inputs are out of range");
        }
        return inputs;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record CellInputs(
            int islandMask,
            int summitReliefMillionths,
            int submarineSaddleMask,
            int provisionalSurfaceMillionths,
            int waterLevelBlocks,
            int canyonMask,
            int floorMask,
            int wallHeightMillionths,
            int provinceRaw,
            int wetnessRaw
    ) {
        public static CellInputs volcanicOnly(
                int islandMask,
                int summitReliefMillionths,
                int submarineSaddleMask,
                int provisionalSurfaceMillionths,
                int waterLevelBlocks
        ) {
            return new CellInputs(
                    islandMask, summitReliefMillionths, submarineSaddleMask,
                    provisionalSurfaceMillionths, waterLevelBlocks,
                    0, 0, 0, GeologyPlanV2.NO_DATA_RAW, 0);
        }

        public static CellInputs canyonOnly(
                int canyonMask,
                int floorMask,
                int wallHeightMillionths,
                int provinceRaw,
                int wetnessRaw
        ) {
            return new CellInputs(
                    0, 0, 0, 0, 0,
                    canyonMask, floorMask, wallHeightMillionths, provinceRaw, wetnessRaw);
        }
    }

    @FunctionalInterface
    public interface CellInputSource {
        CellInputs at(int globalX, int globalZ);
    }
}
