package com.github.nankotsu029.landformcraft.model.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Versioned V2-9-01 surface foundation contract shared by plain/hill/mountain/valley/river/wetland
 * slices. Describes 2.5D fields, ownership, transition bands, and seed namespace only. No feature
 * kind is promoted to SUPPORTED by this plan alone.
 */
public record SurfaceFoundationPlanV2(
        int planVersion,
        String fieldContractVersion,
        String moduleId,
        String moduleVersion,
        String stageId,
        long namedSeed,
        String seedNamespace,
        int width,
        int length,
        String scaleClassId,
        int tileSizeBlocks,
        int haloBlocks,
        ModuleDescriptorV2.MergeOperator mergeOperator,
        AmbiguityPolicy ambiguityPolicy,
        List<OwnerDescriptor> owners,
        List<Interaction> interactions,
        List<FieldBinding> fields,
        int supportRadiusXZ,
        ResourceBudget budget,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String FIELD_CONTRACT_VERSION = "surface-foundation-field-contract-v1";
    public static final String MODULE_ID = "v2.foundation.surface";
    public static final String MODULE_VERSION = "0.1.0-v2-9-01";
    public static final String STAGE_ID = "compile.surface-foundation";
    public static final String SEED_NAMESPACE = "terrain.v2.foundation";
    public static final String GENERATOR_VERSION = "surface-foundation-merge-v1";
    public static final int MAX_OWNERS = 64;
    public static final int MAX_INTERACTIONS = 256;
    public static final int MAX_FIELDS = 5;
    public static final int MAX_SUPPORT_RADIUS_XZ = 32;
    public static final long MAX_HEADER_BYTES_PER_FIELD = 2_048L;
    public static final long STRICT_READ_BACK_BUFFER_BYTES = 64L * 1024L;
    public static final long ESTIMATED_RETAINED_BYTES = 64L * 1024L;
    public static final long MAX_CANONICAL_BYTES = 64L * 1024L;

    public static final String SURFACE_CLASS_FIELD_ID = "foundation.surface-class";
    public static final String ELEVATION_FIELD_ID = "foundation.elevation";
    public static final String RESIDUAL_FIELD_ID = "foundation.residual";
    public static final String OWNER_INDEX_FIELD_ID = "foundation.owner-index";
    public static final String TRANSITION_WEIGHT_FIELD_ID = "foundation.transition-weight";

    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern QUALIFIED = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

    public SurfaceFoundationPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("surface foundation planVersion must be 1");
        }
        fieldContractVersion = nonBlank(fieldContractVersion, "fieldContractVersion", 64);
        if (!FIELD_CONTRACT_VERSION.equals(fieldContractVersion)) {
            throw new IllegalArgumentException("unknown surface foundation field contract version");
        }
        if (!MODULE_ID.equals(moduleId) || !MODULE_VERSION.equals(moduleVersion) || !STAGE_ID.equals(stageId)) {
            throw new IllegalArgumentException("surface foundation module identity is not the fixed built-in");
        }
        if (!SEED_NAMESPACE.equals(seedNamespace)) {
            throw new IllegalArgumentException("surface foundation seedNamespace must be terrain.v2.foundation");
        }
        if (width < 1 || length < 1
                || width > ScaleClassV2.MAXIMUM_HORIZONTAL_BLOCKS
                || length > ScaleClassV2.MAXIMUM_HORIZONTAL_BLOCKS) {
            throw new IllegalArgumentException("surface foundation dimensions outside 1.."
                    + ScaleClassV2.MAXIMUM_HORIZONTAL_BLOCKS);
        }
        ScaleClassV2 scaleClass = ScaleClassV2.forDimensions(width, length);
        scaleClassId = nonBlank(scaleClassId, "scaleClassId", 32).toLowerCase(Locale.ROOT);
        if (!scaleClass.id().equals(scaleClassId)) {
            throw new IllegalArgumentException("surface foundation scaleClassId must match dimensions");
        }
        if (tileSizeBlocks < 32 || tileSizeBlocks > 512 || tileSizeBlocks % 32 != 0) {
            throw new IllegalArgumentException("surface foundation tileSizeBlocks must be 32..512 multiple of 32");
        }
        if (haloBlocks < 0 || haloBlocks > tileSizeBlocks / 2) {
            throw new IllegalArgumentException("surface foundation haloBlocks outside 0..tileSize/2");
        }
        if (mergeOperator != ModuleDescriptorV2.MergeOperator.PRIORITY_BLEND) {
            throw new IllegalArgumentException("surface foundation mergeOperator must be PRIORITY_BLEND");
        }
        Objects.requireNonNull(ambiguityPolicy, "ambiguityPolicy");
        if (ambiguityPolicy != AmbiguityPolicy.REJECT) {
            throw new IllegalArgumentException("surface foundation ambiguityPolicy must be REJECT");
        }
        owners = immutable(owners, "owners", MAX_OWNERS).stream()
                .sorted(Comparator.comparing(OwnerDescriptor::ownerId))
                .toList();
        interactions = immutable(interactions, "interactions", MAX_INTERACTIONS).stream()
                .sorted(Comparator.comparing(Interaction::firstOwnerId)
                        .thenComparing(Interaction::secondOwnerId)
                        .thenComparing(Interaction::relationId))
                .toList();
        fields = immutable(fields, "fields", MAX_FIELDS).stream()
                .sorted(Comparator.comparing(FieldBinding::fieldId))
                .toList();
        if (supportRadiusXZ < 0 || supportRadiusXZ > MAX_SUPPORT_RADIUS_XZ) {
            throw new IllegalArgumentException("surface foundation supportRadiusXZ outside 0..32");
        }
        Objects.requireNonNull(budget, "budget");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validateContract(owners, interactions, fields, supportRadiusXZ, width, length, haloBlocks, budget);
    }

    public SurfaceFoundationPlanV2 withCanonicalChecksum(String checksum) {
        return new SurfaceFoundationPlanV2(
                planVersion, fieldContractVersion, moduleId, moduleVersion, stageId,
                namedSeed, seedNamespace, width, length, scaleClassId, tileSizeBlocks, haloBlocks,
                mergeOperator, ambiguityPolicy, owners, interactions, fields, supportRadiusXZ,
                budget, checksum);
    }

    public enum AmbiguityPolicy { REJECT }

    public enum FieldSemantic {
        SURFACE_CLASS,
        ELEVATION,
        RESIDUAL,
        OWNER_INDEX,
        TRANSITION_WEIGHT
    }

    public enum FieldValueType { U16 }

    public enum Ownership { SINGLE_OWNER }

    public enum SurfaceClassCode {
        UNSPECIFIED(0),
        PLAIN(1),
        HILL(2),
        MOUNTAIN(3),
        VALLEY(4),
        RIVER(5),
        WETLAND(6),
        COAST(7),
        CLIFF(8),
        ISLAND(9),
        CONE(10),
        OCEAN(11),
        SHELF(12),
        SLOPE(13),
        SUBMARINE_CANYON(14),
        ENTRANCE(15);

        private final int code;

        SurfaceClassCode(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    public record OwnerDescriptor(
            String ownerId,
            int ownerIndex,
            int priority,
            int parentOrdinal,
            SurfaceClassCode surfaceClass,
            long derivedSeed
    ) {
        public OwnerDescriptor {
            ownerId = slug(ownerId, "ownerId");
            if (ownerIndex < 1 || ownerIndex > 65_535) {
                throw new IllegalArgumentException("ownerIndex outside 1..65535");
            }
            if (priority < -100 || priority > 100) {
                throw new IllegalArgumentException("owner priority outside -100..100");
            }
            if (parentOrdinal < 0 || parentOrdinal > 1_000) {
                throw new IllegalArgumentException("parentOrdinal outside 0..1000");
            }
            Objects.requireNonNull(surfaceClass, "surfaceClass");
            if (surfaceClass == SurfaceClassCode.UNSPECIFIED) {
                throw new IllegalArgumentException("owner surfaceClass must not be UNSPECIFIED");
            }
        }
    }

    public record Interaction(
            String relationId,
            String firstOwnerId,
            String secondOwnerId,
            int bandBlocks
    ) {
        public Interaction {
            relationId = slug(relationId, "relationId");
            firstOwnerId = slug(firstOwnerId, "firstOwnerId");
            secondOwnerId = slug(secondOwnerId, "secondOwnerId");
            if (firstOwnerId.compareTo(secondOwnerId) >= 0) {
                throw new IllegalArgumentException("interaction owner IDs must be canonical and distinct");
            }
            if (bandBlocks < 0 || bandBlocks > MAX_SUPPORT_RADIUS_XZ) {
                throw new IllegalArgumentException("interaction bandBlocks outside 0..32");
            }
        }
    }

    public record FieldBinding(
            String fieldId,
            FieldSemantic semantic,
            FieldValueType valueType,
            String ownerModuleId,
            Ownership ownership,
            String encodingVersion
    ) {
        public FieldBinding {
            fieldId = qualified(fieldId, "fieldId");
            Objects.requireNonNull(semantic, "semantic");
            if (valueType != FieldValueType.U16 || ownership != Ownership.SINGLE_OWNER) {
                throw new IllegalArgumentException("surface foundation field type/ownership mismatch");
            }
            ownerModuleId = qualified(ownerModuleId, "ownerModuleId");
            if (!MODULE_ID.equals(ownerModuleId)) {
                throw new IllegalArgumentException("surface foundation field owner must be the foundation module");
            }
            if (!FieldArtifactDescriptorV2.ENCODING_VERSION.equals(encodingVersion)) {
                throw new IllegalArgumentException("unknown surface foundation field encoding version");
            }
        }
    }

    public record ResourceBudget(
            String budgetVersion,
            int maximumOwners,
            int maximumInteractions,
            int maximumFields,
            long globalCellCount,
            long estimatedCpuWorkUnits,
            long estimatedRetainedBytes,
            int maximumWindowSize,
            long maximumWorkingBytes,
            long estimatedArtifactBytes,
            long maximumSingleArtifactBytes,
            long maximumCanonicalBytes,
            int supportRadiusXZ,
            int haloBlocks
    ) {
        public static final String VERSION = "surface-foundation-budget-v1";

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown surface foundation budget version");
            }
            if (maximumOwners < 0 || maximumOwners > MAX_OWNERS
                    || maximumInteractions < 0 || maximumInteractions > MAX_INTERACTIONS
                    || maximumFields != MAX_FIELDS
                    || globalCellCount < 1
                    || globalCellCount > (long) ScaleClassV2.MAXIMUM_HORIZONTAL_BLOCKS
                    * ScaleClassV2.MAXIMUM_HORIZONTAL_BLOCKS
                    || estimatedCpuWorkUnits < globalCellCount
                    || estimatedCpuWorkUnits > 64_000_000L
                    || estimatedRetainedBytes < 1 || estimatedRetainedBytes > 256L * 1024L * 1024L
                    || maximumWindowSize < 1 || maximumWindowSize > 512
                    || maximumWorkingBytes < 1 || maximumWorkingBytes > 128L * 1024L * 1024L
                    || estimatedArtifactBytes < 1 || estimatedArtifactBytes > 256L * 1024L * 1024L
                    || maximumSingleArtifactBytes < 1
                    || maximumSingleArtifactBytes > 64L * 1024L * 1024L
                    || maximumSingleArtifactBytes > estimatedArtifactBytes
                    || maximumCanonicalBytes < 1 || maximumCanonicalBytes > MAX_CANONICAL_BYTES
                    || supportRadiusXZ < 0 || supportRadiusXZ > MAX_SUPPORT_RADIUS_XZ
                    || haloBlocks < 0 || haloBlocks > 256) {
                throw new IllegalArgumentException("surface foundation resource budget is outside trusted bounds");
            }
        }
    }

    private static void validateContract(
            List<OwnerDescriptor> owners,
            List<Interaction> interactions,
            List<FieldBinding> fields,
            int supportRadiusXZ,
            int width,
            int length,
            int haloBlocks,
            ResourceBudget budget
    ) {
        Set<String> ownerIds = new HashSet<>();
        Set<Integer> ownerIndexes = new HashSet<>();
        Set<Long> derivedSeeds = new HashSet<>();
        for (OwnerDescriptor owner : owners) {
            if (!ownerIds.add(owner.ownerId()) || !ownerIndexes.add(owner.ownerIndex())) {
                throw new IllegalArgumentException("duplicate surface foundation owner id or index");
            }
            if (!derivedSeeds.add(owner.derivedSeed())) {
                throw new IllegalArgumentException("surface foundation derived seed collision");
            }
        }
        Set<String> pairs = new HashSet<>();
        int maximumBand = 0;
        for (Interaction interaction : interactions) {
            if (!ownerIds.contains(interaction.firstOwnerId())
                    || !ownerIds.contains(interaction.secondOwnerId())) {
                throw new IllegalArgumentException("interaction references unknown owner");
            }
            String pair = interaction.firstOwnerId() + '\n' + interaction.secondOwnerId();
            if (!pairs.add(pair)) {
                throw new IllegalArgumentException("duplicate surface foundation interaction pair");
            }
            maximumBand = Math.max(maximumBand, interaction.bandBlocks());
        }
        if (supportRadiusXZ != maximumBand) {
            throw new IllegalArgumentException("supportRadiusXZ must equal maximum interaction band");
        }
        if (budget.supportRadiusXZ() != supportRadiusXZ || budget.haloBlocks() != haloBlocks) {
            throw new IllegalArgumentException("budget support/halo must match plan values");
        }
        if (haloBlocks < supportRadiusXZ) {
            throw new IllegalArgumentException("haloBlocks must cover supportRadiusXZ");
        }

        Set<String> fieldIds = new HashSet<>();
        EnumSet<FieldSemantic> semantics = EnumSet.noneOf(FieldSemantic.class);
        for (FieldBinding field : fields) {
            if (!fieldIds.add(field.fieldId()) || !semantics.add(field.semantic())) {
                throw new IllegalArgumentException("conflicting surface foundation field binding");
            }
        }
        if (!semantics.equals(EnumSet.allOf(FieldSemantic.class))) {
            throw new IllegalArgumentException("surface foundation field contract is incomplete");
        }
        requireField(fields, SURFACE_CLASS_FIELD_ID, FieldSemantic.SURFACE_CLASS);
        requireField(fields, ELEVATION_FIELD_ID, FieldSemantic.ELEVATION);
        requireField(fields, RESIDUAL_FIELD_ID, FieldSemantic.RESIDUAL);
        requireField(fields, OWNER_INDEX_FIELD_ID, FieldSemantic.OWNER_INDEX);
        requireField(fields, TRANSITION_WEIGHT_FIELD_ID, FieldSemantic.TRANSITION_WEIGHT);

        long cells = Math.multiplyExact((long) width, length);
        int windowWidth = Math.min(width, budget.maximumWindowSize());
        int windowLength = Math.min(length, budget.maximumWindowSize());
        long windowCells = Math.multiplyExact((long) windowWidth, windowLength);
        long readerWorkingBytes = Math.addExact(
                Math.multiplyExact(windowCells, MAX_FIELDS * (long) Integer.BYTES),
                Math.multiplyExact(windowWidth, MAX_FIELDS * (long) Short.BYTES));
        long writerWorkingBytes = Math.addExact(STRICT_READ_BACK_BUFFER_BYTES, MAX_HEADER_BYTES_PER_FIELD);
        long requiredWorkingBytes = Math.max(readerWorkingBytes, writerWorkingBytes);
        long requiredSingleArtifactBytes = Math.addExact(
                Math.multiplyExact(cells, Short.BYTES), MAX_HEADER_BYTES_PER_FIELD);
        long requiredArtifactBytes = Math.multiplyExact(requiredSingleArtifactBytes, MAX_FIELDS);
        if (owners.size() > budget.maximumOwners()
                || interactions.size() > budget.maximumInteractions()
                || fields.size() != budget.maximumFields()
                || budget.globalCellCount() != cells
                || budget.estimatedCpuWorkUnits() < Math.multiplyExact(cells, MAX_FIELDS)
                || budget.estimatedRetainedBytes() < ESTIMATED_RETAINED_BYTES
                || budget.maximumWorkingBytes() < requiredWorkingBytes
                || budget.estimatedArtifactBytes() < requiredArtifactBytes
                || budget.maximumSingleArtifactBytes() < requiredSingleArtifactBytes) {
            throw new IllegalArgumentException("surface foundation plan exceeds its declared resource budget");
        }
    }

    private static void requireField(List<FieldBinding> fields, String fieldId, FieldSemantic semantic) {
        boolean present = fields.stream().anyMatch(field ->
                field.fieldId().equals(fieldId) && field.semantic() == semantic);
        if (!present) {
            throw new IllegalArgumentException("missing required surface foundation field: " + fieldId);
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
        if (!SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase slug");
        }
        return value;
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
}
