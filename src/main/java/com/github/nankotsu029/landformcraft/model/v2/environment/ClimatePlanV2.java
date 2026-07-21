package com.github.nankotsu029.landformcraft.model.v2.environment;

import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;

import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned V2-4 climate-prior/final-field contract. The V2-3 hydrology plan stays immutable;
 * {@link HydrologyRunoffHandoff} declares the checksum-bound transition to the new prior.
 */
public record ClimatePlanV2(
        int planVersion,
        String fieldContractVersion,
        BaseClimatePreset baseClimatePreset,
        String priorModuleId,
        String priorModuleVersion,
        String priorStageId,
        String finalModuleId,
        String finalModuleVersion,
        String finalStageId,
        long namedSeed,
        String seedNamespace,
        int width,
        int length,
        int minY,
        int maxY,
        int referenceElevationY,
        CoarsePrior coarsePrior,
        FinalKernel finalKernel,
        HydrologyRunoffHandoff hydrologyHandoff,
        List<FieldBinding> fields,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String FIELD_CONTRACT_VERSION = "climate-field-contract-v1";
    public static final String SEED_NAMESPACE = "terrain.v2.climate";
    public static final int FIXED_SCALE = 1_000_000;
    public static final int RAW_SCALE_MILLIONTHS = 1_000;
    public static final int MAX_FIELDS = 4;
    public static final int MAX_COARSE_CELL_SIZE = 128;
    public static final long MAX_CANONICAL_BYTES = 128L * 1024L;

    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public ClimatePlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("climate planVersion must be 1");
        }
        fieldContractVersion = nonBlank(fieldContractVersion, "fieldContractVersion", 64);
        if (!FIELD_CONTRACT_VERSION.equals(fieldContractVersion)) {
            throw new IllegalArgumentException("unknown climate field contract version");
        }
        Objects.requireNonNull(baseClimatePreset, "baseClimatePreset");
        priorModuleId = qualified(priorModuleId, "priorModuleId");
        priorModuleVersion = nonBlank(priorModuleVersion, "priorModuleVersion", 64);
        priorStageId = qualified(priorStageId, "priorStageId");
        finalModuleId = qualified(finalModuleId, "finalModuleId");
        finalModuleVersion = nonBlank(finalModuleVersion, "finalModuleVersion", 64);
        finalStageId = qualified(finalStageId, "finalStageId");
        if (priorModuleId.equals(finalModuleId) || priorStageId.equals(finalStageId)) {
            throw new IllegalArgumentException("climate prior and final module/stage IDs must differ");
        }
        seedNamespace = qualified(seedNamespace, "seedNamespace");
        if (!SEED_NAMESPACE.equals(seedNamespace)) {
            throw new IllegalArgumentException("unknown climate seed namespace");
        }
        if (width < 1 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING || length < 1 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING
                || minY >= maxY || (long) maxY - minY + 1L > 512L
                || referenceElevationY < minY || referenceElevationY > maxY) {
            throw new IllegalArgumentException("climate dimensions or vertical reference are invalid");
        }
        Objects.requireNonNull(coarsePrior, "coarsePrior");
        Objects.requireNonNull(finalKernel, "finalKernel");
        Objects.requireNonNull(hydrologyHandoff, "hydrologyHandoff");
        fields = immutable(fields, "fields", MAX_FIELDS).stream()
                .sorted(Comparator.comparing(FieldBinding::fieldId)).toList();
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateContract(width, length, minY, maxY, baseClimatePreset, priorModuleId, finalModuleId,
                coarsePrior, finalKernel, hydrologyHandoff, fields, budget);
    }

    public ClimatePlanV2 withCanonicalChecksum(String checksum) {
        return new ClimatePlanV2(
                planVersion, fieldContractVersion, baseClimatePreset,
                priorModuleId, priorModuleVersion, priorStageId,
                finalModuleId, finalModuleVersion, finalStageId,
                namedSeed, seedNamespace, width, length, minY, maxY, referenceElevationY,
                coarsePrior, finalKernel, hydrologyHandoff, fields, budget, checksum);
    }

    /** Fails closed unless this plan names the exact immutable V2-3 source plan and priors. */
    public void requireHydrologyPlan(HydrologyPlanV2 hydrologyPlan) {
        Objects.requireNonNull(hydrologyPlan, "hydrologyPlan");
        if (!hydrologyHandoff.sourceHydrologyPlanChecksum().equals(hydrologyPlan.canonicalChecksum())
                || !hydrologyHandoff.sourceHydrologyPriorChecksum()
                .equals(hydrologyPlan.fixedPriors().priorChecksum())
                || hydrologyPlan.fixedPriors().runoffPrior()
                != HydrologyPlanV2.RunoffPriorKind.CONSTANT_RUNOFF_PRIOR
                || hydrologyHandoff.sourceConstantRunoffMillionths()
                != hydrologyPlan.fixedPriors().constantRunoffMillionths()
                || !hydrologyHandoff.replacementClimatePriorChecksum().equals(coarsePrior.priorChecksum())) {
            throw new IllegalArgumentException("climate hydrology prior transition binding mismatch");
        }
    }

    public enum BaseClimatePreset {
        COLD_ALPINE(430, 650, 720, 580, 360, 6),
        COLD_MARITIME(520, 720, 780, 680, 300, 5),
        COOL_HIGH_ALTITUDE(480, 610, 690, 560, 320, 6),
        SEASONAL_SEMI_ARID(420, 520, 460, 720, 260, 4),
        TEMPERATE_HUMID(720, 820, 760, 690, 230, 4),
        TEMPERATE_MARITIME(680, 780, 740, 700, 220, 4),
        WARM_HUMID(780, 900, 820, 790, 170, 3),
        WARM_HUMID_MARITIME(800, 920, 840, 820, 160, 3),
        WARM_MARITIME(650, 760, 720, 830, 150, 3),
        WARM_TROPICAL_MARITIME(820, 940, 860, 900, 120, 2);

        private final int northPrecipitationRaw;
        private final int southPrecipitationRaw;
        private final int runoffCoefficientRaw;
        private final int baseTemperatureRaw;
        private final int latitudeCoolingRaw;
        private final int lapseRawPerBlock;

        BaseClimatePreset(
                int northPrecipitationRaw,
                int southPrecipitationRaw,
                int runoffCoefficientRaw,
                int baseTemperatureRaw,
                int latitudeCoolingRaw,
                int lapseRawPerBlock
        ) {
            this.northPrecipitationRaw = northPrecipitationRaw;
            this.southPrecipitationRaw = southPrecipitationRaw;
            this.runoffCoefficientRaw = runoffCoefficientRaw;
            this.baseTemperatureRaw = baseTemperatureRaw;
            this.latitudeCoolingRaw = latitudeCoolingRaw;
            this.lapseRawPerBlock = lapseRawPerBlock;
        }

        public int northPrecipitationRaw() { return northPrecipitationRaw; }
        public int southPrecipitationRaw() { return southPrecipitationRaw; }
        public int runoffCoefficientRaw() { return runoffCoefficientRaw; }
        public int baseTemperatureRaw() { return baseTemperatureRaw; }
        public int latitudeCoolingRaw() { return latitudeCoolingRaw; }
        public int lapseRawPerBlock() { return lapseRawPerBlock; }
    }

    public enum FieldSemantic {
        PRIOR_PRECIPITATION,
        PRIOR_RUNOFF,
        FINAL_TEMPERATURE,
        FINAL_MOISTURE
    }
    public enum FieldValueType { U16, I16 }
    public enum FieldPhase { PRIOR, FINAL }
    public enum Ownership { SINGLE_OWNER }
    public enum Sampling { BILINEAR_FIXED }
    public enum SourcePriorKind { CONSTANT_RUNOFF_PRIOR }
    public enum TransitionMode { EXPLICIT_VERSION_TRANSITION }

    /** Coarse-grid parameters are descriptors; values are evaluated by the versioned integer kernel. */
    public record CoarsePrior(
            String kernelVersion,
            int coarseCellSize,
            int coarseWidth,
            int coarseLength,
            int northPrecipitationRaw,
            int southPrecipitationRaw,
            int runoffCoefficientRaw,
            String priorChecksum
    ) {
        public static final String KERNEL_VERSION = "climate-coarse-prior-v1";

        public CoarsePrior {
            if (!KERNEL_VERSION.equals(kernelVersion)
                    || coarseCellSize < 8 || coarseCellSize > MAX_COARSE_CELL_SIZE
                    || coarseWidth < 1 || coarseWidth > 126
                    || coarseLength < 1 || coarseLength > 126
                    || northPrecipitationRaw < 0 || northPrecipitationRaw > 1_000
                    || southPrecipitationRaw < 0 || southPrecipitationRaw > 1_000
                    || runoffCoefficientRaw < 0 || runoffCoefficientRaw > 1_000) {
                throw new IllegalArgumentException("climate coarse prior is outside trusted bounds");
            }
            priorChecksum = checksum(priorChecksum, "priorChecksum");
            if (!expectedChecksum(
                    kernelVersion, coarseCellSize, coarseWidth, coarseLength,
                    northPrecipitationRaw, southPrecipitationRaw, runoffCoefficientRaw)
                    .equals(priorChecksum)) {
                throw new IllegalArgumentException("climate coarse prior checksum mismatch");
            }
        }

        public static CoarsePrior create(
                int coarseCellSize,
                int coarseWidth,
                int coarseLength,
                BaseClimatePreset preset
        ) {
            String expected = expectedChecksum(
                    KERNEL_VERSION, coarseCellSize, coarseWidth, coarseLength,
                    preset.northPrecipitationRaw(), preset.southPrecipitationRaw(),
                    preset.runoffCoefficientRaw());
            return new CoarsePrior(
                    KERNEL_VERSION, coarseCellSize, coarseWidth, coarseLength,
                    preset.northPrecipitationRaw(), preset.southPrecipitationRaw(),
                    preset.runoffCoefficientRaw(), expected);
        }

        private static String expectedChecksum(
                String kernelVersion,
                int coarseCellSize,
                int coarseWidth,
                int coarseLength,
                int northPrecipitationRaw,
                int southPrecipitationRaw,
                int runoffCoefficientRaw
        ) {
            return sha256(String.join("\n",
                    "kernelVersion=" + kernelVersion,
                    "coarseCellSize=" + coarseCellSize,
                    "coarseWidth=" + coarseWidth,
                    "coarseLength=" + coarseLength,
                    "northPrecipitationRaw=" + northPrecipitationRaw,
                    "southPrecipitationRaw=" + southPrecipitationRaw,
                    "runoffCoefficientRaw=" + runoffCoefficientRaw));
        }
    }

    public record FinalKernel(
            String kernelVersion,
            int baseTemperatureRaw,
            int latitudeCoolingRaw,
            int lapseRawPerBlock,
            int exposureCoolingRaw,
            int exposureDryingRaw,
            int flowMoistureGainRaw,
            int minimumTemperatureRaw,
            int maximumTemperatureRaw,
            int minimumMoistureRaw,
            int maximumMoistureRaw
    ) {
        public static final String KERNEL_VERSION = "climate-final-fixed-v1";

        public FinalKernel {
            if (!KERNEL_VERSION.equals(kernelVersion)
                    || baseTemperatureRaw < -1_000 || baseTemperatureRaw > 1_000
                    || latitudeCoolingRaw < 0 || latitudeCoolingRaw > 1_000
                    || lapseRawPerBlock < 0 || lapseRawPerBlock > 32
                    || exposureCoolingRaw < 0 || exposureCoolingRaw > 1_000
                    || exposureDryingRaw < 0 || exposureDryingRaw > 1_000
                    || flowMoistureGainRaw < 0 || flowMoistureGainRaw > 1_000
                    || minimumTemperatureRaw != -1_000 || maximumTemperatureRaw != 1_000
                    || minimumMoistureRaw != 0 || maximumMoistureRaw != 1_000) {
                throw new IllegalArgumentException("unknown or invalid climate final kernel");
            }
        }

        public static FinalKernel forPreset(BaseClimatePreset preset) {
            return new FinalKernel(
                    KERNEL_VERSION, preset.baseTemperatureRaw(), preset.latitudeCoolingRaw(),
                    preset.lapseRawPerBlock(), 100, 240, 160,
                    -1_000, 1_000, 0, 1_000);
        }
    }

    /**
     * Explicitly binds the immutable V2-3 constant prior to a new climate-prior generator version.
     * It does not alter or reinterpret the source HydrologyPlan.
     */
    public record HydrologyRunoffHandoff(
            int bindingVersion,
            SourcePriorKind sourcePriorKind,
            String sourceHydrologyPlanChecksum,
            String sourceHydrologyPriorChecksum,
            int sourceConstantRunoffMillionths,
            String replacementClimatePriorChecksum,
            String sourceGeneratorVersion,
            String targetGeneratorVersion,
            String transitionContractVersion,
            TransitionMode transitionMode
    ) {
        public static final int VERSION = 1;
        public static final String SOURCE_GENERATOR_VERSION = "hydrology-priority-flood-v1";
        public static final String TARGET_GENERATOR_VERSION = "hydrology-priority-flood-climate-prior-v1";
        public static final String TRANSITION_CONTRACT_VERSION = "hydrology-climate-prior-binding-v1";

        public HydrologyRunoffHandoff {
            if (bindingVersion != VERSION
                    || sourcePriorKind != SourcePriorKind.CONSTANT_RUNOFF_PRIOR
                    || sourceConstantRunoffMillionths
                    != HydrologyPlanV2.FixedPriors.CONSTANT_RUNOFF_MILLIONTHS
                    || !SOURCE_GENERATOR_VERSION.equals(sourceGeneratorVersion)
                    || !TARGET_GENERATOR_VERSION.equals(targetGeneratorVersion)
                    || !TRANSITION_CONTRACT_VERSION.equals(transitionContractVersion)
                    || transitionMode != TransitionMode.EXPLICIT_VERSION_TRANSITION) {
                throw new IllegalArgumentException("unknown climate hydrology prior transition");
            }
            sourceHydrologyPlanChecksum = checksum(sourceHydrologyPlanChecksum, "sourceHydrologyPlanChecksum");
            sourceHydrologyPriorChecksum = checksum(sourceHydrologyPriorChecksum, "sourceHydrologyPriorChecksum");
            replacementClimatePriorChecksum = checksum(
                    replacementClimatePriorChecksum, "replacementClimatePriorChecksum");
            if (!HydrologyPlanV2.FixedPriors.CHECKSUM.equals(sourceHydrologyPriorChecksum)) {
                throw new IllegalArgumentException("climate source hydrology prior checksum mismatch");
            }
        }
    }

    public record FieldBinding(
            String fieldId,
            FieldSemantic semantic,
            FieldValueType valueType,
            FieldPhase phase,
            String ownerModuleId,
            Ownership ownership,
            Sampling sampling,
            int scaleMillionths
    ) {
        public FieldBinding {
            fieldId = qualified(fieldId, "fieldId");
            Objects.requireNonNull(semantic, "semantic");
            Objects.requireNonNull(valueType, "valueType");
            Objects.requireNonNull(phase, "phase");
            ownerModuleId = qualified(ownerModuleId, "ownerModuleId");
            if (ownership != Ownership.SINGLE_OWNER || sampling != Sampling.BILINEAR_FIXED
                    || scaleMillionths != RAW_SCALE_MILLIONTHS) {
                throw new IllegalArgumentException("climate field ownership/sampling/scale mismatch");
            }
            FieldPhase expectedPhase = semantic.name().startsWith("PRIOR_") ? FieldPhase.PRIOR : FieldPhase.FINAL;
            FieldValueType expectedType = semantic == FieldSemantic.FINAL_TEMPERATURE
                    ? FieldValueType.I16 : FieldValueType.U16;
            if (phase != expectedPhase || valueType != expectedType) {
                throw new IllegalArgumentException("climate field phase/value type mismatch");
            }
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            int maximumFields,
            long globalCellCount,
            long coarseCellCount,
            long estimatedCpuWorkUnits,
            long estimatedRetainedBytes,
            int maximumWindowSize,
            long maximumWorkingBytes,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "climate-field-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)
                    || maximumFields != MAX_FIELDS
                    || globalCellCount < 1 || globalCellCount > ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS
                    || coarseCellCount < 1 || coarseCellCount > 16_384L
                    || estimatedCpuWorkUnits < globalCellCount || estimatedCpuWorkUnits > 16_000_000L
                    || estimatedRetainedBytes < 1 || estimatedRetainedBytes > 4L * 1024L * 1024L
                    || maximumWindowSize < 1 || maximumWindowSize > 256
                    || maximumWorkingBytes < 1 || maximumWorkingBytes > 8L * 1024L * 1024L
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES) {
                throw new IllegalArgumentException("climate resource budget is outside trusted bounds");
            }
        }
    }

    private static void validateContract(
            int width,
            int length,
            int minY,
            int maxY,
            BaseClimatePreset preset,
            String priorModuleId,
            String finalModuleId,
            CoarsePrior coarsePrior,
            FinalKernel finalKernel,
            HydrologyRunoffHandoff handoff,
            List<FieldBinding> fields,
            ResourceBudget budget
    ) {
        int expectedCoarseWidth = divideCeiling(width, coarsePrior.coarseCellSize()) + 1;
        int expectedCoarseLength = divideCeiling(length, coarsePrior.coarseCellSize()) + 1;
        if (coarsePrior.coarseWidth() != expectedCoarseWidth
                || coarsePrior.coarseLength() != expectedCoarseLength
                || coarsePrior.northPrecipitationRaw() != preset.northPrecipitationRaw()
                || coarsePrior.southPrecipitationRaw() != preset.southPrecipitationRaw()
                || coarsePrior.runoffCoefficientRaw() != preset.runoffCoefficientRaw()
                || finalKernel.baseTemperatureRaw() != preset.baseTemperatureRaw()
                || finalKernel.latitudeCoolingRaw() != preset.latitudeCoolingRaw()
                || finalKernel.lapseRawPerBlock() != preset.lapseRawPerBlock()
                || !handoff.replacementClimatePriorChecksum().equals(coarsePrior.priorChecksum())) {
            throw new IllegalArgumentException("climate preset/prior/final contract mismatch");
        }
        Set<String> ids = new HashSet<>();
        EnumSet<FieldSemantic> semantics = EnumSet.noneOf(FieldSemantic.class);
        for (FieldBinding field : fields) {
            String expectedOwner = field.phase() == FieldPhase.PRIOR ? priorModuleId : finalModuleId;
            if (!ids.add(field.fieldId()) || !semantics.add(field.semantic())
                    || !field.ownerModuleId().equals(expectedOwner)) {
                throw new IllegalArgumentException("conflicting climate field binding");
            }
        }
        if (!semantics.equals(EnumSet.allOf(FieldSemantic.class))) {
            throw new IllegalArgumentException("climate field contract is incomplete");
        }
        long cells = Math.multiplyExact((long) width, length);
        long coarseCells = Math.multiplyExact((long) coarsePrior.coarseWidth(), coarsePrior.coarseLength());
        int windowWidth = Math.min(width, budget.maximumWindowSize());
        int windowLength = Math.min(length, budget.maximumWindowSize());
        long working = Math.multiplyExact(Math.multiplyExact((long) windowWidth, windowLength),
                MAX_FIELDS * (long) Integer.BYTES);
        long retained = Math.addExact(Math.multiplyExact(coarseCells, 2L * Integer.BYTES), 64L * 1024L);
        long cpu = Math.addExact(Math.multiplyExact(cells, MAX_FIELDS), Math.multiplyExact(coarseCells, 2L));
        long verticalSpan = Math.addExact(Math.subtractExact((long) maxY, minY), 1L);
        if (verticalSpan > 512L
                || budget.globalCellCount() != cells
                || budget.coarseCellCount() != coarseCells
                || budget.estimatedCpuWorkUnits() < cpu
                || budget.estimatedRetainedBytes() < retained
                || budget.maximumWorkingBytes() < working
                || fields.size() != budget.maximumFields()) {
            throw new IllegalArgumentException("climate plan exceeds its declared resource budget");
        }
    }

    private static int divideCeiling(int value, int divisor) {
        return Math.floorDiv(Math.addExact(value, divisor - 1), divisor);
    }

    private static <T> List<T> immutable(List<T> values, String name, int maximum) {
        Objects.requireNonNull(values, name);
        if (values.size() > maximum || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " is invalid or exceeds " + maximum);
        }
        return List.copyOf(values);
    }

    private static String qualified(String value, String name) {
        value = nonBlank(value, name, 128);
        if (!QUALIFIED.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase qualified ID");
        }
        return value;
    }

    private static String checksum(String value, String name) {
        value = nonBlank(value, name, 64);
        if (!CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256");
        }
        return value;
    }

    private static String nonBlank(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must be non-blank and bounded");
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
