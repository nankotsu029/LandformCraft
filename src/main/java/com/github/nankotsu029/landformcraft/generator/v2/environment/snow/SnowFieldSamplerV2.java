package com.github.nankotsu029.landformcraft.generator.v2.environment.snow;

import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class SnowFieldSamplerV2 {
    private final SnowPlanV2 plan;

    public SnowFieldSamplerV2(SnowPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public int rawValueAt(
            SnowPlanV2.FieldSemantic semantic,
            int globalX,
            int globalZ,
            CellInputs inputs
    ) {
        requireCoordinate(globalX, globalZ);
        CellInputs validated = requireInputs(inputs);
        return switch (semantic) {
            case SNOW_POTENTIAL -> snowPotential(validated);
            case SNOW_COVER -> snowCover(validated);
        };
    }

    public int[] sampleWindow(
            SnowPlanV2.FieldSemantic semantic,
            int startX,
            int startZ,
            int width,
            int length,
            CellInputSource inputSource
    ) {
        if (width < 1 || length < 1
                || width > plan.budget().maximumWindowSize()
                || length > plan.budget().maximumWindowSize()
                || startX < 0 || startZ < 0
                || Math.addExact(startX, width) > plan.width()
                || Math.addExact(startZ, length) > plan.length()) {
            throw new IllegalArgumentException("snow window is outside declared bounds");
        }
        long requiredBytes = Math.multiplyExact(Math.multiplyExact((long) width, length), Integer.BYTES);
        if (requiredBytes > plan.budget().maximumWorkingBytes()) {
            throw new IllegalArgumentException("snow window exceeds working-memory budget");
        }
        Objects.requireNonNull(inputSource, "inputSource");
        int[] result = new int[Math.multiplyExact(width, length)];
        for (int localZ = 0; localZ < length; localZ++) {
            for (int localX = 0; localX < width; localX++) {
                int globalX = startX + localX;
                int globalZ = startZ + localZ;
                result[localZ * width + localX] = rawValueAt(
                        semantic, globalX, globalZ, inputSource.at(globalX, globalZ));
            }
        }
        return result;
    }

    public String checksum(SnowPlanV2.FieldSemantic semantic, CellInputSource inputSource) {
        MessageDigest digest = sha256();
        digest.update("LFC_SNOW_FIELD_V1\n".getBytes(StandardCharsets.UTF_8));
        digest.update(semantic.name().getBytes(StandardCharsets.UTF_8));
        ByteBuffer cell = ByteBuffer.allocate(Integer.BYTES);
        Objects.requireNonNull(inputSource, "inputSource");
        for (int z = 0; z < plan.length(); z++) {
            for (int x = 0; x < plan.width(); x++) {
                cell.clear();
                cell.putInt(rawValueAt(semantic, x, z, inputSource.at(x, z)));
                digest.update(cell.array());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private int snowPotential(CellInputs inputs) {
        SnowPlanV2.Kernel kernel = plan.kernel();
        int basePotential = Math.max(0, Math.subtractExact(kernel.snowlineTemperatureRaw(), inputs.temperatureRaw()));
        if (basePotential == 0) return 0;
        
        long potential = roundedDivide(Math.multiplyExact((long) basePotential, 10L), 1L);
        long moistureBonus = roundedDivide(Math.multiplyExact((long) inputs.moistureRaw(), 200L), 1_000L);
        potential = Math.addExact(potential, moistureBonus);
        
        long windPenalty = roundedDivide(Math.multiplyExact((long) inputs.windExposureRaw(), 300L), 1_000L);
        potential = Math.max(0L, Math.subtractExact(potential, windPenalty));
        
        long sunPenalty = roundedDivide(Math.multiplyExact((long) inputs.sunExposureRaw(), 200L), 1_000L);
        potential = Math.max(0L, Math.subtractExact(potential, sunPenalty));

        return clamp(Math.toIntExact(potential), kernel.minimumRaw(), kernel.maximumRaw());
    }

    private int snowCover(CellInputs inputs) {
        SnowPlanV2.Kernel kernel = plan.kernel();
        int potential = snowPotential(inputs);
        if (potential == 0) return 0;
        
        long cover = potential;
        if (inputs.slopeRaw() > kernel.steepSlopeThresholdRaw()) {
            long excessSlope = Math.subtractExact((long) inputs.slopeRaw(), kernel.steepSlopeThresholdRaw());
            long slopePenalty = roundedDivide(Math.multiplyExact(excessSlope, kernel.steepSlopePenaltyRaw()), 1_000L);
            cover = Math.max(0L, Math.subtractExact(cover, slopePenalty));
        }
        
        return clamp(Math.toIntExact(cover), kernel.minimumRaw(), kernel.maximumRaw());
    }

    private void requireCoordinate(int globalX, int globalZ) {
        if (globalX < 0 || globalX >= plan.width() || globalZ < 0 || globalZ >= plan.length()) {
            throw new IllegalArgumentException("snow coordinate outside release-local bounds");
        }
    }

    private CellInputs requireInputs(CellInputs inputs) {
        Objects.requireNonNull(inputs, "snow inputs");
        if (inputs.elevationY() < plan.minY() || inputs.elevationY() > plan.maxY()
                || inputs.temperatureRaw() < -1_000 || inputs.temperatureRaw() > 1_000
                || inputs.moistureRaw() < 0 || inputs.moistureRaw() > 1_000
                || inputs.slopeRaw() < 0 || inputs.slopeRaw() > 1_000
                || inputs.windExposureRaw() < 0 || inputs.windExposureRaw() > 1_000
                || inputs.sunExposureRaw() < 0 || inputs.sunExposureRaw() > 1_000) {
            throw new IllegalArgumentException("snow inputs are outside declared hard ranges");
        }
        return inputs;
    }

    private static int roundedDivide(long numerator, long denominator) {
        if (denominator <= 0) throw new IllegalArgumentException("denominator must be positive");
        long absolute = Math.abs(numerator);
        long rounded = Math.floorDiv(Math.addExact(absolute, denominator / 2L), denominator);
        return Math.toIntExact(numerator < 0 ? -rounded : rounded);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public record CellInputs(
            int elevationY,
            int temperatureRaw,
            int moistureRaw,
            int slopeRaw,
            int windExposureRaw,
            int sunExposureRaw
    ) {
    }

    @FunctionalInterface
    public interface CellInputSource {
        CellInputs at(int globalX, int globalZ);
    }
}
