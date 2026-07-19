package com.github.nankotsu029.landformcraft.model.v2.volume;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned V2-5-01 VolumePlan SDF primitive contract. It freezes analytic signed-distance
 * primitives, quantization, and resource ceilings without ordered CSG, spatial index, voxel
 * cache, or feature generators.
 */
public record VolumeSdfPrimitivePlanV2(
        int planVersion,
        String primitiveContractVersion,
        Quantization quantization,
        Kernel kernel,
        List<VolumeSdfPrimitiveV2> primitives,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PRIMITIVE_CONTRACT_VERSION = "volume-sdf-primitive-contract-v1";
    public static final long MAX_CANONICAL_BYTES = 64L * 1024L;
    public static final int MAXIMUM_PRIMITIVES = 32;
    public static final int MAXIMUM_SWEPT_CONTROL_POINTS = 64;
    public static final int MAXIMUM_AABB_EXTENT_BLOCKS = 512;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public VolumeSdfPrimitivePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("volume-sdf-primitive planVersion must be 1");
        }
        primitiveContractVersion = nonBlank(primitiveContractVersion, "primitiveContractVersion", 64);
        if (!PRIMITIVE_CONTRACT_VERSION.equals(primitiveContractVersion)) {
            throw new IllegalArgumentException("unknown volume-sdf-primitive contract version");
        }
        Objects.requireNonNull(quantization, "quantization");
        Objects.requireNonNull(kernel, "kernel");
        primitives = List.copyOf(Objects.requireNonNull(primitives, "primitives"));
        if (primitives.isEmpty() || primitives.size() > MAXIMUM_PRIMITIVES) {
            throw new IllegalArgumentException("volume-sdf-primitive count out of range");
        }
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validatePrimitiveIds(primitives);
        validateBudget(budget, primitives);
        for (VolumeSdfPrimitiveV2 primitive : primitives) {
            VolumeSdfAabbV2 bounds = primitive.conservativeBounds();
            if (bounds.extentXBlocks() > MAXIMUM_AABB_EXTENT_BLOCKS
                    || bounds.extentYBlocks() > MAXIMUM_AABB_EXTENT_BLOCKS
                    || bounds.extentZBlocks() > MAXIMUM_AABB_EXTENT_BLOCKS) {
                throw new IllegalArgumentException("volume-sdf-primitive AABB exceeds trusted extent");
            }
        }
    }

    public VolumeSdfPrimitivePlanV2 withCanonicalChecksum(String checksum) {
        return new VolumeSdfPrimitivePlanV2(
                planVersion, primitiveContractVersion, quantization, kernel, primitives, budget, checksum);
    }

    public List<VolumeSdfPrimitiveV2> primitivesInCanonicalOrder() {
        return primitives.stream()
                .sorted(Comparator.comparing(VolumeSdfPrimitiveV2::primitiveId))
                .toList();
    }

    public record Quantization(
            String quantizationVersion,
            int fixedScale,
            int geometryScale
    ) {
        public static final String VERSION = "volume-sdf-q-v1";
        public static final int FIXED_SCALE = 1_000_000;
        public static final int GEOMETRY_SCALE = 4_096;

        public Quantization {
            quantizationVersion = nonBlank(quantizationVersion, "quantizationVersion", 64);
            if (!VERSION.equals(quantizationVersion)
                    || fixedScale != FIXED_SCALE
                    || geometryScale != GEOMETRY_SCALE) {
                throw new IllegalArgumentException("unknown volume-sdf quantization");
            }
        }

        public static Quantization standard() {
            return new Quantization(VERSION, FIXED_SCALE, GEOMETRY_SCALE);
        }
    }

    public record Kernel(
            String kernelVersion,
            int maximumSweptControlPoints,
            int maximumSampleOperationsPerPrimitive
    ) {
        public static final String VERSION = "volume-sdf-fixed-v1";
        public static final int MAXIMUM_SAMPLE_OPS = 256;

        public Kernel {
            kernelVersion = nonBlank(kernelVersion, "kernelVersion", 64);
            if (!VERSION.equals(kernelVersion)) {
                throw new IllegalArgumentException("unknown volume-sdf kernel version");
            }
            if (maximumSweptControlPoints < 2
                    || maximumSweptControlPoints > MAXIMUM_SWEPT_CONTROL_POINTS) {
                throw new IllegalArgumentException("swept control-point budget out of range");
            }
            if (maximumSampleOperationsPerPrimitive < 1
                    || maximumSampleOperationsPerPrimitive > MAXIMUM_SAMPLE_OPS) {
                throw new IllegalArgumentException("sample-operation budget out of range");
            }
        }

        public static Kernel standard() {
            return new Kernel(VERSION, MAXIMUM_SWEPT_CONTROL_POINTS, MAXIMUM_SAMPLE_OPS);
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            int maximumPrimitives,
            int maximumSweptControlPoints,
            long estimatedCanonicalBytes,
            long maximumCanonicalBytes,
            long maximumWorkingBytes
    ) {
        public static final String VERSION = "volume-sdf-primitive-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown volume-sdf-primitive budget version");
            }
            if (maximumPrimitives < 1 || maximumPrimitives > MAXIMUM_PRIMITIVES
                    || maximumSweptControlPoints < 2
                    || maximumSweptControlPoints > MAXIMUM_SWEPT_CONTROL_POINTS
                    || estimatedCanonicalBytes < 1
                    || maximumCanonicalBytes < 1
                    || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || maximumWorkingBytes < 1) {
                throw new IllegalArgumentException("volume-sdf-primitive budget out of range");
            }
        }
    }

    private static void validatePrimitiveIds(List<VolumeSdfPrimitiveV2> primitives) {
        Set<String> ids = new HashSet<>();
        for (VolumeSdfPrimitiveV2 primitive : primitives) {
            if (!ids.add(primitive.primitiveId())) {
                throw new IllegalArgumentException("duplicate volume-sdf-primitive id");
            }
        }
    }

    private static void validateBudget(ResourceBudget budget, List<VolumeSdfPrimitiveV2> primitives) {
        if (primitives.size() > budget.maximumPrimitives()) {
            throw new IllegalArgumentException("volume-sdf-primitive count exceeds budget");
        }
        int maxControlPoints = 0;
        for (VolumeSdfPrimitiveV2 primitive : primitives) {
            if (primitive instanceof VolumeSdfPrimitiveV2.SweptSpline swept) {
                maxControlPoints = Math.max(maxControlPoints, swept.controlPoints().size());
            }
        }
        if (maxControlPoints > budget.maximumSweptControlPoints()) {
            throw new IllegalArgumentException("swept control-point count exceeds budget");
        }
        if (budget.estimatedCanonicalBytes() > budget.maximumCanonicalBytes()) {
            throw new IllegalArgumentException("volume-sdf-primitive canonical budget exceeded");
        }
    }

    static String qualified(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!QUALIFIED.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a qualified id");
        }
        return value;
    }

    static String checksum(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a sha-256 hex digest");
        }
        return value;
    }

    static String nonBlank(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " length out of range");
        }
        return value;
    }
}
