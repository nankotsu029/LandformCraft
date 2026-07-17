package com.github.nankotsu029.landformcraft.generator.v2.climate;

import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Integer-only global-X/Z sampler for V2-4-04 coarse prior and final climate descriptors. */
public final class ClimateFieldSamplerV2 {
    private static final long FIXED = ClimatePlanV2.FIXED_SCALE;
    private final ClimatePlanV2 plan;

    public ClimateFieldSamplerV2(ClimatePlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public int rawValueAt(
            ClimatePlanV2.FieldSemantic semantic,
            int globalX,
            int globalZ,
            FinalInputs inputs
    ) {
        requireCoordinate(globalX, globalZ);
        return switch (semantic) {
            case PRIOR_PRECIPITATION -> priorPrecipitationRaw(globalX, globalZ);
            case PRIOR_RUNOFF -> roundedDivide(
                    (long) priorPrecipitationRaw(globalX, globalZ)
                            * plan.coarsePrior().runoffCoefficientRaw(), 1_000L);
            case FINAL_TEMPERATURE -> finalTemperatureRaw(globalX, globalZ, requireInputs(inputs));
            case FINAL_MOISTURE -> finalMoistureRaw(globalX, globalZ, requireInputs(inputs));
        };
    }

    public int[] sampleWindow(
            ClimatePlanV2.FieldSemantic semantic,
            int startX,
            int startZ,
            int width,
            int length,
            FinalInputSource inputSource
    ) {
        if (width < 1 || length < 1
                || width > plan.budget().maximumWindowSize()
                || length > plan.budget().maximumWindowSize()
                || startX < 0 || startZ < 0
                || Math.addExact(startX, width) > plan.width()
                || Math.addExact(startZ, length) > plan.length()) {
            throw new IllegalArgumentException("climate window is outside declared bounds");
        }
        long requiredBytes = Math.multiplyExact(Math.multiplyExact((long) width, length), Integer.BYTES);
        if (requiredBytes > plan.budget().maximumWorkingBytes()) {
            throw new IllegalArgumentException("climate window exceeds working-memory budget");
        }
        int[] result = new int[Math.multiplyExact(width, length)];
        for (int localZ = 0; localZ < length; localZ++) {
            for (int localX = 0; localX < width; localX++) {
                int globalX = startX + localX;
                int globalZ = startZ + localZ;
                FinalInputs inputs = semantic.name().startsWith("FINAL_")
                        ? Objects.requireNonNull(inputSource, "inputSource").at(globalX, globalZ)
                        : null;
                result[localZ * width + localX] = rawValueAt(semantic, globalX, globalZ, inputs);
            }
        }
        return result;
    }

    public String checksum(ClimatePlanV2.FieldSemantic semantic, FinalInputSource inputSource) {
        MessageDigest digest = sha256();
        digest.update("LFC_CLIMATE_FIELD_V1\n".getBytes(StandardCharsets.UTF_8));
        digest.update(semantic.name().getBytes(StandardCharsets.UTF_8));
        ByteBuffer cell = ByteBuffer.allocate(Integer.BYTES);
        for (int z = 0; z < plan.length(); z++) {
            for (int x = 0; x < plan.width(); x++) {
                FinalInputs inputs = semantic.name().startsWith("FINAL_")
                        ? Objects.requireNonNull(inputSource, "inputSource").at(x, z)
                        : null;
                cell.clear();
                cell.putInt(rawValueAt(semantic, x, z, inputs));
                digest.update(cell.array());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private int priorPrecipitationRaw(int globalX, int globalZ) {
        ClimatePlanV2.CoarsePrior prior = plan.coarsePrior();
        long coarseXMillionths = scaledCoordinate(globalX, plan.width(), prior.coarseWidth());
        long coarseZMillionths = scaledCoordinate(globalZ, plan.length(), prior.coarseLength());
        int x0 = Math.toIntExact(Math.floorDiv(coarseXMillionths, FIXED));
        int z0 = Math.toIntExact(Math.floorDiv(coarseZMillionths, FIXED));
        int x1 = Math.min(x0 + 1, prior.coarseWidth() - 1);
        int z1 = Math.min(z0 + 1, prior.coarseLength() - 1);
        long fx = Math.floorMod(coarseXMillionths, FIXED);
        long fz = Math.floorMod(coarseZMillionths, FIXED);
        int northWest = coarsePrecipitationAt(x0, z0);
        int northEast = coarsePrecipitationAt(x1, z0);
        int southWest = coarsePrecipitationAt(x0, z1);
        int southEast = coarsePrecipitationAt(x1, z1);
        int north = interpolate(northWest, northEast, fx);
        int south = interpolate(southWest, southEast, fx);
        return interpolate(north, south, fz);
    }

    private int coarsePrecipitationAt(int coarseX, int coarseZ) {
        ClimatePlanV2.CoarsePrior prior = plan.coarsePrior();
        if (coarseX < 0 || coarseX >= prior.coarseWidth()
                || coarseZ < 0 || coarseZ >= prior.coarseLength()) {
            throw new IllegalArgumentException("climate coarse coordinate outside bounds");
        }
        long position = prior.coarseLength() == 1
                ? 0L
                : roundedDivide((long) coarseZ * FIXED, prior.coarseLength() - 1L);
        return interpolate(prior.northPrecipitationRaw(), prior.southPrecipitationRaw(), position);
    }

    private int finalTemperatureRaw(int globalX, int globalZ, FinalInputs inputs) {
        ClimatePlanV2.FinalKernel kernel = plan.finalKernel();
        long latitude = normalizedLatitudeDistance(globalZ);
        int elevationDelta = Math.subtractExact(inputs.elevationY(), plan.referenceElevationY());
        long temperature = kernel.baseTemperatureRaw();
        temperature = Math.subtractExact(temperature,
                roundedDivide((long) kernel.latitudeCoolingRaw() * latitude, FIXED));
        temperature = Math.subtractExact(temperature,
                Math.multiplyExact((long) elevationDelta, kernel.lapseRawPerBlock()));
        temperature = Math.subtractExact(temperature,
                roundedDivide((long) kernel.exposureCoolingRaw()
                        * Math.max(0, inputs.exposureMillionths()), FIXED));
        return clamp(Math.toIntExact(temperature),
                kernel.minimumTemperatureRaw(), kernel.maximumTemperatureRaw());
    }

    private int finalMoistureRaw(int globalX, int globalZ, FinalInputs inputs) {
        ClimatePlanV2.FinalKernel kernel = plan.finalKernel();
        int precipitation = priorPrecipitationRaw(globalX, globalZ);
        int runoff = roundedDivide((long) precipitation * plan.coarsePrior().runoffCoefficientRaw(), 1_000L);
        long moisture = roundedDivide((long) precipitation + runoff, 2L);
        moisture = Math.subtractExact(moisture,
                roundedDivide((long) kernel.exposureDryingRaw()
                        * Math.max(0, inputs.exposureMillionths()), FIXED));
        moisture = Math.addExact(moisture,
                roundedDivide((long) kernel.flowMoistureGainRaw()
                        * inputs.flowAccumulationMillionths(), FIXED));
        return clamp(Math.toIntExact(moisture), kernel.minimumMoistureRaw(), kernel.maximumMoistureRaw());
    }

    private long normalizedLatitudeDistance(int globalZ) {
        if (plan.length() == 1) return 0L;
        long normalized = roundedDivide((long) globalZ * FIXED, plan.length() - 1L);
        return Math.abs(Math.subtractExact(Math.multiplyExact(normalized, 2L), FIXED));
    }

    private static long scaledCoordinate(int coordinate, int fullSize, int coarseSize) {
        if (fullSize == 1 || coarseSize == 1) return 0L;
        return roundedDivide(
                Math.multiplyExact(Math.multiplyExact((long) coordinate, coarseSize - 1L), FIXED),
                fullSize - 1L);
    }

    private void requireCoordinate(int globalX, int globalZ) {
        if (globalX < 0 || globalX >= plan.width() || globalZ < 0 || globalZ >= plan.length()) {
            throw new IllegalArgumentException("climate coordinate outside release-local bounds");
        }
    }

    private FinalInputs requireInputs(FinalInputs inputs) {
        Objects.requireNonNull(inputs, "final climate inputs");
        if (inputs.elevationY() < plan.minY() || inputs.elevationY() > plan.maxY()
                || inputs.exposureMillionths() < -ClimatePlanV2.FIXED_SCALE
                || inputs.exposureMillionths() > ClimatePlanV2.FIXED_SCALE
                || inputs.flowAccumulationMillionths() < 0
                || inputs.flowAccumulationMillionths() > ClimatePlanV2.FIXED_SCALE) {
            throw new IllegalArgumentException("final climate inputs are outside declared ranges");
        }
        return inputs;
    }

    private static int interpolate(int first, int second, long fractionMillionths) {
        long delta = Math.subtractExact((long) second, first);
        return Math.toIntExact(Math.addExact(first, roundedDivide(
                Math.multiplyExact(delta, fractionMillionths), FIXED)));
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

    public record FinalInputs(int elevationY, int exposureMillionths, int flowAccumulationMillionths) {
    }

    @FunctionalInterface
    public interface FinalInputSource {
        FinalInputs at(int globalX, int globalZ);
    }
}
