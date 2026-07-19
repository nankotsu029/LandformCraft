package com.github.nankotsu029.landformcraft.generator.v2.material;

import com.github.nankotsu029.landformcraft.generator.v2.geology.strata.StrataExposureResolverV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Pure per-cell resolver applying the frozen V2-4-07 rule order (base substrate, then wetness,
 * then surface-only snow) over host lithology, hydrology wetness, and snow cover. It never
 * allocates a dense width×length material array and owns no new sidecar.
 */
public final class MaterialProfileResolverV2 {
    private final StrataExposureResolverV2 strataResolver;
    private final MaterialProfilePlanV2 plan;

    public MaterialProfileResolverV2(
            GeologyPlanV2 geologyPlan,
            LithologyPlanV2 lithologyPlan,
            StrataPlanV2 strataPlan,
            WaterConditionPlanV2 waterConditionPlan,
            SnowPlanV2 snowPlan,
            MaterialProfilePlanV2 materialPlan
    ) {
        Objects.requireNonNull(materialPlan, "materialPlan");
        materialPlan.requireGeologyPlan(geologyPlan, lithologyPlan, strataPlan);
        materialPlan.requireWaterConditionPlan(waterConditionPlan);
        materialPlan.requireSnowPlan(snowPlan);
        this.strataResolver = new StrataExposureResolverV2(geologyPlan, lithologyPlan, strataPlan);
        this.plan = materialPlan;
    }

    public int classCodeAt(
            MaterialProfilePlanV2.SurfaceAspect aspect,
            int globalX,
            int globalZ,
            CellInputs inputs
    ) {
        Objects.requireNonNull(aspect, "aspect");
        CellInputs validated = requireInputs(inputs);
        if (validated.provinceRaw() == GeologyPlanV2.NO_DATA_RAW) {
            return GeologyPlanV2.NO_DATA_RAW;
        }
        LithologyPlanV2.ErosionResponse erosion =
                strataResolver.erosionResponse(validated.provinceRaw(), globalX, globalZ);
        MaterialProfilePlanV2.SubstrateCategory substrate = substrateFor(erosion);

        MaterialProfilePlanV2.SemanticMaterialClass resolved = plan.catalog().baseEntry(substrate).kind();
        if (validated.wetnessRaw() >= plan.kernel().wetnessThresholdRaw()) {
            resolved = plan.catalog().wetEntry(substrate).kind();
        }
        if (aspect == MaterialProfilePlanV2.SurfaceAspect.SURFACE
                && validated.snowCoverRaw() >= plan.kernel().snowThresholdRaw()) {
            resolved = plan.catalog().snowEntry(substrate).kind();
        }
        return plan.catalog().requireByKind(resolved).classCode();
    }

    public int[] sampleWindow(
            MaterialProfilePlanV2.SurfaceAspect aspect,
            int startX,
            int startZ,
            int width,
            int length,
            CellInputSource inputSource
    ) {
        if (width < 1 || length < 1
                || width > plan.budget().maximumWindowSize()
                || length > plan.budget().maximumWindowSize()
                || startX < 0 || startZ < 0) {
            throw new IllegalArgumentException("material-profile window is outside declared bounds");
        }
        long requiredBytes = Math.multiplyExact(Math.multiplyExact((long) width, length), Integer.BYTES);
        if (requiredBytes > plan.budget().maximumWorkingBytes()) {
            throw new IllegalArgumentException("material-profile window exceeds working-memory budget");
        }
        Objects.requireNonNull(inputSource, "inputSource");
        int[] result = new int[Math.multiplyExact(width, length)];
        for (int localZ = 0; localZ < length; localZ++) {
            for (int localX = 0; localX < width; localX++) {
                int globalX = startX + localX;
                int globalZ = startZ + localZ;
                result[localZ * width + localX] = classCodeAt(aspect, globalX, globalZ, inputSource.at(globalX, globalZ));
            }
        }
        return result;
    }

    public String checksum(
            MaterialProfilePlanV2.SurfaceAspect aspect,
            int width,
            int length,
            CellInputSource inputSource
    ) {
        Objects.requireNonNull(aspect, "aspect");
        Objects.requireNonNull(inputSource, "inputSource");
        MessageDigest digest = sha256();
        digest.update("LFC_MATERIAL_PROFILE_FIELD_V1\n".getBytes(StandardCharsets.UTF_8));
        digest.update(aspect.name().getBytes(StandardCharsets.UTF_8));
        ByteBuffer cell = ByteBuffer.allocate(Integer.BYTES);
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                cell.clear();
                cell.putInt(classCodeAt(aspect, x, z, inputSource.at(x, z)));
                digest.update(cell.array());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MaterialProfilePlanV2.SubstrateCategory substrateFor(LithologyPlanV2.ErosionResponse erosion) {
        return switch (erosion) {
            case RESISTANT, MODERATE -> MaterialProfilePlanV2.SubstrateCategory.ROCK;
            case ERODIBLE, HIGHLY_ERODIBLE -> MaterialProfilePlanV2.SubstrateCategory.SEDIMENT;
        };
    }

    private static CellInputs requireInputs(CellInputs inputs) {
        Objects.requireNonNull(inputs, "material profile inputs");
        if (inputs.provinceRaw() != GeologyPlanV2.NO_DATA_RAW
                && (inputs.provinceRaw() < 1 || inputs.provinceRaw() >= GeologyPlanV2.NO_DATA_RAW)) {
            throw new IllegalArgumentException("material profile province input is outside declared hard range");
        }
        if (inputs.wetnessRaw() < 0 || inputs.wetnessRaw() > 1_000
                || inputs.snowCoverRaw() < 0 || inputs.snowCoverRaw() > 1_000) {
            throw new IllegalArgumentException("material profile inputs are outside declared hard ranges");
        }
        return inputs;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public record CellInputs(int provinceRaw, int wetnessRaw, int snowCoverRaw) {
    }

    @FunctionalInterface
    public interface CellInputSource {
        CellInputs at(int globalX, int globalZ);
    }
}
