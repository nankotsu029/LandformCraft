package com.github.nankotsu029.landformcraft.model.v2.environment;

import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Ordered strata profiles bound to V2-4-02 lithology, with derived surface hardness/permeability
 * and an explicit hydrology geology-input version transition. Dense 3D layer maps are out of scope.
 */
public record StrataPlanV2(
        int planVersion,
        String profileContractVersion,
        String sourceGeologyPlanChecksum,
        String sourceLithologyPlanChecksum,
        List<Profile> profiles,
        HydrologyGeologyInputHandoff hydrologyHandoff,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String PROFILE_CONTRACT_VERSION = "strata-profile-contract-v1";
    public static final int MAX_PROFILES = GeologyPlanV2.MAX_PROVINCES;
    public static final int MAX_LAYERS_PER_PROFILE = 16;
    public static final int MAX_TOTAL_LAYERS = 1_024;
    public static final int MAX_STACK_THICKNESS_BLOCKS = 512;
    public static final int MIN_LAYER_THICKNESS_BLOCKS = 1;
    public static final int MAX_DIP_DEGREES = 45;
    public static final int MAX_FOLD_AMPLITUDE_BLOCKS = 32;
    public static final int MIN_FOLD_WAVELENGTH_BLOCKS = 16;
    public static final int MAX_FOLD_WAVELENGTH_BLOCKS = 256;
    public static final int MAX_TILT_SLOPE_MILLIONTHS = 1_000_000;
    public static final int DEFAULT_STACK_THICKNESS_BLOCKS = 64;

    public StrataPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("strata planVersion must be 1");
        }
        profileContractVersion = nonBlank(profileContractVersion, "profileContractVersion", 64);
        if (!PROFILE_CONTRACT_VERSION.equals(profileContractVersion)) {
            throw new IllegalArgumentException("unknown strata profile contract version");
        }
        sourceGeologyPlanChecksum = checksum(sourceGeologyPlanChecksum, "sourceGeologyPlanChecksum");
        sourceLithologyPlanChecksum = checksum(sourceLithologyPlanChecksum, "sourceLithologyPlanChecksum");
        profiles = immutable(profiles, "profiles", MAX_PROFILES).stream()
                .sorted(Comparator.comparingInt(Profile::provinceCode).thenComparing(Profile::profileId))
                .toList();
        Objects.requireNonNull(hydrologyHandoff, "hydrologyHandoff");
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateProfiles(profiles, budget);
        if (!hydrologyHandoff.sourceGeologyPlanChecksum().equals(sourceGeologyPlanChecksum)
                || !hydrologyHandoff.sourceLithologyPlanChecksum().equals(sourceLithologyPlanChecksum)) {
            throw new IllegalArgumentException("strata hydrology handoff checksum binding mismatch");
        }
    }

    public StrataPlanV2 withCanonicalChecksum(String checksum) {
        return new StrataPlanV2(
                planVersion, profileContractVersion, sourceGeologyPlanChecksum, sourceLithologyPlanChecksum,
                profiles, hydrologyHandoff, budget, checksum);
    }

    /** Fails closed unless profiles exactly cover the bound lithology/geology province set. */
    public void requireLithologyPlan(GeologyPlanV2 geologyPlan, LithologyPlanV2 lithologyPlan) {
        Objects.requireNonNull(geologyPlan, "geologyPlan");
        Objects.requireNonNull(lithologyPlan, "lithologyPlan");
        lithologyPlan.requireGeologyPlan(geologyPlan);
        if (!sourceGeologyPlanChecksum.equals(geologyPlan.canonicalChecksum())
                || !sourceLithologyPlanChecksum.equals(lithologyPlan.canonicalChecksum())) {
            throw new IllegalArgumentException("strata plan source geology/lithology checksum mismatch");
        }
        if (profiles.size() != lithologyPlan.provinceAssignments().size()) {
            throw new IllegalArgumentException("strata province profile set is incomplete");
        }
        for (LithologyPlanV2.ProvinceAssignment assignment : lithologyPlan.provinceAssignments()) {
            Profile profile = profiles.stream()
                    .filter(value -> value.provinceCode() == assignment.provinceCode())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "missing strata profile for province " + assignment.provinceId()));
            if (!profile.provinceId().equals(assignment.provinceId())
                    || !profile.formationId().equals(assignment.formationId())
                    || profile.formationCode() != assignment.formationCode()) {
                throw new IllegalArgumentException("strata profile disagrees with lithology province assignment");
            }
            for (Layer layer : profile.layers()) {
                LithologyPlanV2.Entry entry = lithologyPlan.catalog().requireByCode(layer.lithologyCode());
                if (!entry.lithologyId().equals(layer.lithologyId())) {
                    throw new IllegalArgumentException("strata layer lithology ID/code mismatch");
                }
            }
        }
        if (!hydrologyHandoff.sourceHydrologyPriorChecksum().equals(HydrologyPlanV2.FixedPriors.CHECKSUM)
                || !HydrologyReconciliationPlanV2.ALGORITHM_VERSION.equals(
                hydrologyHandoff.sourceReconciliationAlgorithmVersion())
                || hydrologyHandoff.sourcePriorKind() != SourcePriorKind.UNIFORM_GEOLOGY_PRIOR
                || hydrologyHandoff.inputMode() != InputMode.SURFACE_EXPOSED_STRATA_SCALARS
                || hydrologyHandoff.transitionMode() != TransitionMode.EXPLICIT_VERSION_TRANSITION) {
            throw new IllegalArgumentException("strata hydrology handoff does not declare the V2-3 prior transition");
        }
    }

    public record Profile(
            String profileId,
            String provinceId,
            int provinceCode,
            String formationId,
            int formationCode,
            LayerOrder layerOrder,
            List<Layer> layers,
            FoldTilt orientation
    ) {
        public Profile {
            profileId = slug(profileId, "profileId");
            provinceId = slug(provinceId, "provinceId");
            formationId = qualified(formationId, "formationId");
            if (provinceCode < 1 || provinceCode >= GeologyPlanV2.NO_DATA_RAW
                    || formationCode < 1 || formationCode >= GeologyPlanV2.NO_DATA_RAW) {
                throw new IllegalArgumentException("strata province/formation code is invalid");
            }
            if (layerOrder != LayerOrder.BOTTOM_TO_TOP) {
                throw new IllegalArgumentException("unknown strata layer order");
            }
            layers = immutable(layers, "layers", MAX_LAYERS_PER_PROFILE);
            Objects.requireNonNull(orientation, "orientation");
            if (layers.isEmpty()) {
                throw new IllegalArgumentException("strata profile requires at least one layer");
            }
            int totalThickness = 0;
            for (int index = 0; index < layers.size(); index++) {
                Layer layer = layers.get(index);
                if (layer.layerIndex() != index) {
                    throw new IllegalArgumentException("strata layerIndex must be contiguous from 0 at the bottom");
                }
                totalThickness = Math.addExact(totalThickness, layer.thicknessBlocks());
            }
            if (totalThickness < MIN_LAYER_THICKNESS_BLOCKS || totalThickness > MAX_STACK_THICKNESS_BLOCKS) {
                throw new IllegalArgumentException("strata stack thickness is outside trusted bounds");
            }
        }

        public int totalThicknessBlocks() {
            int total = 0;
            for (Layer layer : layers) {
                total = Math.addExact(total, layer.thicknessBlocks());
            }
            return total;
        }
    }

    public enum LayerOrder { BOTTOM_TO_TOP }

    public record Layer(
            int layerIndex,
            String lithologyId,
            int lithologyCode,
            int thicknessBlocks
    ) {
        public Layer {
            lithologyId = qualified(lithologyId, "lithologyId");
            if (layerIndex < 0 || layerIndex >= MAX_LAYERS_PER_PROFILE
                    || lithologyCode < 1 || lithologyCode > 255
                    || thicknessBlocks < MIN_LAYER_THICKNESS_BLOCKS
                    || thicknessBlocks > MAX_STACK_THICKNESS_BLOCKS) {
                throw new IllegalArgumentException("strata layer is invalid or zero/thin beyond budget");
            }
        }
    }

    /**
     * Bounded fold/tilt subset. Slope millionths are frozen integer rise/run values derived from dip/azimuth.
     * Platform floating kernels are not the source of truth.
     */
    public record FoldTilt(
            int dipDegrees,
            int dipAzimuthDegrees,
            int tiltDxMillionths,
            int tiltDzMillionths,
            int foldAmplitudeBlocks,
            int foldWavelengthBlocks
    ) {
        public FoldTilt {
            if (dipDegrees < 0 || dipDegrees > MAX_DIP_DEGREES
                    || dipAzimuthDegrees < 0 || dipAzimuthDegrees > 359
                    || Math.abs(tiltDxMillionths) > MAX_TILT_SLOPE_MILLIONTHS
                    || Math.abs(tiltDzMillionths) > MAX_TILT_SLOPE_MILLIONTHS
                    || foldAmplitudeBlocks < 0 || foldAmplitudeBlocks > MAX_FOLD_AMPLITUDE_BLOCKS
                    || (foldWavelengthBlocks != 0 && (foldWavelengthBlocks < MIN_FOLD_WAVELENGTH_BLOCKS
                    || foldWavelengthBlocks > MAX_FOLD_WAVELENGTH_BLOCKS))
                    || (foldAmplitudeBlocks == 0) != (foldWavelengthBlocks == 0)) {
                throw new IllegalArgumentException("strata fold/tilt is outside the bounded subset");
            }
            int[] expected = expectedTiltMillionths(dipDegrees, dipAzimuthDegrees);
            if (tiltDxMillionths != expected[0] || tiltDzMillionths != expected[1]) {
                throw new IllegalArgumentException("strata tilt slopes do not match dip/azimuth contract");
            }
        }

        public static FoldTilt flat() {
            return new FoldTilt(0, 0, 0, 0, 0, 0);
        }

        public static FoldTilt cardinalDip(int dipDegrees, int dipAzimuthDegrees) {
            int[] slopes = expectedTiltMillionths(dipDegrees, dipAzimuthDegrees);
            return new FoldTilt(dipDegrees, dipAzimuthDegrees, slopes[0], slopes[1], 0, 0);
        }

        /** Integer-only tan(dip)*direction using a fixed degree table. */
        public static int[] expectedTiltMillionths(int dipDegrees, int dipAzimuthDegrees) {
            if (dipDegrees < 0 || dipDegrees > MAX_DIP_DEGREES) {
                throw new IllegalArgumentException("strata dip degrees outside bounded subset");
            }
            int rise = TAN_DIP_MILLIONTHS[dipDegrees];
            return switch (dipAzimuthDegrees) {
                case 0 -> new int[] {0, rise};
                case 90 -> new int[] {rise, 0};
                case 180 -> new int[] {0, -rise};
                case 270 -> new int[] {-rise, 0};
                default -> throw new IllegalArgumentException(
                        "strata dip azimuth must be a cardinal degree in the bounded subset");
            };
        }
    }

    /**
     * Declares that future erosion/hydrology geology inputs read surface-exposed strata scalars under a
     * new binding version, without silently reinterpreting V2-3 FixedPriors or reconciliation artifacts.
     */
    public record HydrologyGeologyInputHandoff(
            int bindingVersion,
            SourcePriorKind sourcePriorKind,
            String sourceHydrologyPriorChecksum,
            String sourceReconciliationAlgorithmVersion,
            String sourceGeologyPlanChecksum,
            String sourceLithologyPlanChecksum,
            InputMode inputMode,
            TransitionMode transitionMode
    ) {
        public static final int VERSION = 1;

        public HydrologyGeologyInputHandoff {
            if (bindingVersion != VERSION
                    || sourcePriorKind != SourcePriorKind.UNIFORM_GEOLOGY_PRIOR
                    || inputMode != InputMode.SURFACE_EXPOSED_STRATA_SCALARS
                    || transitionMode != TransitionMode.EXPLICIT_VERSION_TRANSITION) {
                throw new IllegalArgumentException("unknown strata hydrology geology-input handoff contract");
            }
            sourceHydrologyPriorChecksum = checksum(sourceHydrologyPriorChecksum, "sourceHydrologyPriorChecksum");
            sourceReconciliationAlgorithmVersion = nonBlank(
                    sourceReconciliationAlgorithmVersion, "sourceReconciliationAlgorithmVersion", 64);
            if (!HydrologyPlanV2.FixedPriors.CHECKSUM.equals(sourceHydrologyPriorChecksum)
                    || !HydrologyReconciliationPlanV2.ALGORITHM_VERSION.equals(
                    sourceReconciliationAlgorithmVersion)) {
                throw new IllegalArgumentException("strata handoff must reference immutable V2-3 hydrology versions");
            }
            sourceGeologyPlanChecksum = checksum(sourceGeologyPlanChecksum, "sourceGeologyPlanChecksum");
            sourceLithologyPlanChecksum = checksum(sourceLithologyPlanChecksum, "sourceLithologyPlanChecksum");
        }
    }

    public enum SourcePriorKind { UNIFORM_GEOLOGY_PRIOR }

    public enum InputMode { SURFACE_EXPOSED_STRATA_SCALARS }

    public enum TransitionMode { EXPLICIT_VERSION_TRANSITION }

    public record ResourceBudget(
            String budgetVersion,
            int maximumProfiles,
            int maximumLayersPerProfile,
            int maximumTotalLayers,
            int maximumStackThicknessBlocks,
            long globalCellCount,
            long estimatedCpuWorkUnits,
            long estimatedRetainedBytes,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "strata-profile-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)
                    || maximumProfiles < 0 || maximumProfiles > MAX_PROFILES
                    || maximumLayersPerProfile != MAX_LAYERS_PER_PROFILE
                    || maximumTotalLayers < 1 || maximumTotalLayers > MAX_TOTAL_LAYERS
                    || maximumStackThicknessBlocks != MAX_STACK_THICKNESS_BLOCKS
                    || globalCellCount < 1 || globalCellCount > ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS
                    || estimatedCpuWorkUnits < 1L || estimatedCpuWorkUnits > 16_000_000L
                    || estimatedRetainedBytes < 1L || estimatedRetainedBytes > 4L * 1024L * 1024L
                    || maximumCanonicalBytes < 1_024L || maximumCanonicalBytes > 128L * 1024L) {
                throw new IllegalArgumentException("strata resource budget is outside trusted bounds");
            }
        }
    }

    private static final int[] TAN_DIP_MILLIONTHS = buildTanDipTable();

    private static int[] buildTanDipTable() {
        // Fixed-point tan(d°) * 1e6 for d in 0..45. Values are frozen integers, not runtime Math.tan.
        int[] table = new int[MAX_DIP_DEGREES + 1];
        table[0] = 0;
        table[1] = 17_455;
        table[2] = 34_921;
        table[3] = 52_408;
        table[4] = 69_927;
        table[5] = 87_489;
        table[6] = 105_104;
        table[7] = 122_785;
        table[8] = 140_541;
        table[9] = 158_384;
        table[10] = 176_327;
        table[11] = 194_380;
        table[12] = 212_557;
        table[13] = 230_868;
        table[14] = 249_328;
        table[15] = 267_949;
        table[16] = 286_745;
        table[17] = 305_731;
        table[18] = 324_920;
        table[19] = 344_328;
        table[20] = 363_970;
        table[21] = 383_864;
        table[22] = 404_026;
        table[23] = 424_475;
        table[24] = 445_229;
        table[25] = 466_308;
        table[26] = 487_733;
        table[27] = 509_525;
        table[28] = 531_709;
        table[29] = 554_309;
        table[30] = 577_350;
        table[31] = 600_861;
        table[32] = 624_869;
        table[33] = 649_408;
        table[34] = 674_509;
        table[35] = 700_208;
        table[36] = 726_543;
        table[37] = 753_554;
        table[38] = 781_286;
        table[39] = 809_784;
        table[40] = 839_100;
        table[41] = 869_287;
        table[42] = 900_404;
        table[43] = 932_515;
        table[44] = 965_689;
        table[45] = 1_000_000;
        return table;
    }

    private static void validateProfiles(List<Profile> profiles, ResourceBudget budget) {
        if (profiles.size() > budget.maximumProfiles()) {
            throw new IllegalArgumentException("strata profiles exceed their budget");
        }
        Set<String> profileIds = new HashSet<>();
        Set<String> provinceIds = new HashSet<>();
        Set<Integer> provinceCodes = new HashSet<>();
        int totalLayers = 0;
        for (Profile profile : profiles) {
            if (!profileIds.add(profile.profileId())
                    || !provinceIds.add(profile.provinceId())
                    || !provinceCodes.add(profile.provinceCode())) {
                throw new IllegalArgumentException("duplicate strata profile or province");
            }
            if (profile.layers().size() > budget.maximumLayersPerProfile()) {
                throw new IllegalArgumentException("strata layers per profile exceed budget");
            }
            totalLayers = Math.addExact(totalLayers, profile.layers().size());
            if (profile.totalThicknessBlocks() > budget.maximumStackThicknessBlocks()) {
                throw new IllegalArgumentException("strata stack thickness exceeds budget");
            }
        }
        if (totalLayers > budget.maximumTotalLayers()) {
            throw new IllegalArgumentException("strata total layers exceed budget");
        }
        long layerTileWork = Math.multiplyExact((long) Math.max(1, totalLayers), budget.globalCellCount());
        if (budget.estimatedCpuWorkUnits() < layerTileWork) {
            throw new IllegalArgumentException("strata layer×tile CPU budget is insufficient");
        }
    }

    private static <T> List<T> immutable(List<T> values, String name, int maximum) {
        Objects.requireNonNull(values, name);
        if (values.size() > maximum || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " is invalid or exceeds " + maximum);
        }
        return List.copyOf(values);
    }

    private static String slug(String value, String name) {
        value = nonBlank(value, name, 64);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException(name + " is not a slug");
        }
        return value;
    }

    private static String qualified(String value, String name) {
        value = nonBlank(value, name, 128);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException(name + " is not a qualified ID");
        }
        return value;
    }

    private static String checksum(String value, String name) {
        value = nonBlank(value, name, 64);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " is not a SHA-256 checksum");
        }
        return value;
    }

    private static String nonBlank(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is blank or too long");
        }
        return value;
    }
}
