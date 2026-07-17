package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Portable metadata index for a V2-1 constraint-field bundle; it never embeds grid values. */
public record ConstraintFieldIndexV2(
        int indexVersion,
        String requestId,
        String sourceRequestChecksum,
        String sourceIntentChecksum,
        String canonicalChecksum,
        List<AppliedBinding> bindings,
        List<FieldArtifactDescriptorV2> fields
) {
    public static final int VERSION = 1;
    /** Internal construction marker. It is never accepted when an index is parsed or published. */
    public static final String PENDING_CANONICAL_CHECKSUM = "0".repeat(64);
    private static final Pattern SOURCE_ID = Pattern.compile(
            "constraint-source:[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern ARTIFACT_ID = Pattern.compile(
            "constraint:[a-z0-9][a-z0-9._-]{0,63}:sha256-[0-9a-f]{64}");

    public ConstraintFieldIndexV2 {
        if (indexVersion != VERSION) {
            throw new IllegalArgumentException("indexVersion must be exactly 1");
        }
        requestId = V2Validation.slug(requestId, "requestId");
        sourceRequestChecksum = V2Validation.checksum(sourceRequestChecksum, "sourceRequestChecksum");
        sourceIntentChecksum = V2Validation.checksum(sourceIntentChecksum, "sourceIntentChecksum");
        canonicalChecksum = V2Validation.checksum(canonicalChecksum, "canonicalChecksum");
        bindings = V2Validation.sorted(bindings, "bindings", 3, Comparator.comparing(AppliedBinding::bindingId));
        fields = V2Validation.sorted(fields, "fields", 7,
                Comparator.comparing(field -> field.definition().fieldId()));
        if (bindings.isEmpty() || fields.isEmpty()) {
            throw new IllegalArgumentException("constraint field index must contain bindings and fields");
        }
        validateReferences(bindings, fields);
    }

    /**
     * Compatibility constructor for bundle builders. The format codec seals this pending value
     * before canonical serialization and rejects it on every read boundary.
     */
    public ConstraintFieldIndexV2(
            int indexVersion,
            String requestId,
            String sourceRequestChecksum,
            String sourceIntentChecksum,
            List<AppliedBinding> bindings,
            List<FieldArtifactDescriptorV2> fields
    ) {
        this(indexVersion, requestId, sourceRequestChecksum, sourceIntentChecksum,
                PENDING_CANONICAL_CHECKSUM, bindings, fields);
    }

    public boolean hasPendingCanonicalChecksum() {
        return PENDING_CANONICAL_CHECKSUM.equals(canonicalChecksum);
    }

    public ConstraintFieldIndexV2 withCanonicalChecksum(String checksum) {
        return new ConstraintFieldIndexV2(
                indexVersion, requestId, sourceRequestChecksum, sourceIntentChecksum,
                checksum, bindings, fields);
    }

    private static void validateReferences(
            List<AppliedBinding> bindings,
            List<FieldArtifactDescriptorV2> fields
    ) {
        Map<String, FieldArtifactDescriptorV2> byId = new HashMap<>();
        Set<String> paths = new HashSet<>();
        Integer commonWidth = null;
        Integer commonLength = null;
        FieldArtifactDescriptorV2.CoordinateSpace commonCoordinateSpace = null;
        for (FieldArtifactDescriptorV2 field : fields) {
            if (byId.putIfAbsent(field.definition().fieldId(), field) != null) {
                throw new IllegalArgumentException("duplicate field ID in constraint index");
            }
            if (!paths.add(field.relativePath())) {
                throw new IllegalArgumentException("duplicate field path in constraint index");
            }
            var definition = field.definition();
            if (commonWidth == null) {
                commonWidth = definition.width();
                commonLength = definition.length();
                commonCoordinateSpace = definition.coordinateSpace();
            } else if (commonWidth != definition.width()
                    || commonLength != definition.length()
                    || commonCoordinateSpace != definition.coordinateSpace()) {
                throw new IllegalArgumentException(
                        "all constraint fields must use one dimension and coordinate space");
            }
        }
        Set<String> bindingIds = new HashSet<>();
        Set<String> sourceIds = new HashSet<>();
        Set<TerrainIntentV2.ConstraintMapRole> roles = new HashSet<>();
        Map<String, String> fieldOwners = new HashMap<>();
        for (AppliedBinding binding : bindings) {
            if (!bindingIds.add(binding.bindingId())) {
                throw new IllegalArgumentException("duplicate binding ID in constraint index");
            }
            if (!sourceIds.add(binding.sourceId())) {
                throw new IllegalArgumentException("constraint source may only be bound once");
            }
            if (!roles.add(binding.role())) {
                throw new IllegalArgumentException("V2-1 constraint role may only be bound once");
            }
            for (String fieldId : binding.fieldIds()) {
                if (!byId.containsKey(fieldId)) {
                    throw new IllegalArgumentException("binding references unknown field: " + fieldId);
                }
                String previousOwner = fieldOwners.putIfAbsent(fieldId, binding.bindingId());
                if (previousOwner != null) {
                    throw new IllegalArgumentException("constraint field is shared by multiple bindings: " + fieldId);
                }
            }
            FieldArtifactDescriptorV2 canonical = byId.get(binding.canonicalFieldId());
            if (canonical == null) {
                throw new IllegalArgumentException("binding canonicalFieldId is unknown");
            }
            String checksum = binding.canonicalArtifactId().substring(
                    binding.canonicalArtifactId().lastIndexOf("sha256-") + "sha256-".length());
            if (!checksum.equals(canonical.semanticChecksum())) {
                throw new IllegalArgumentException("binding artifact ID does not match field semantic checksum");
            }
            validateBindingShape(binding, byId);
        }
        if (!fieldOwners.keySet().equals(byId.keySet())) {
            throw new IllegalArgumentException("constraint index contains an orphan field");
        }
    }

    private static void validateBindingShape(
            AppliedBinding binding,
            Map<String, FieldArtifactDescriptorV2> byId
    ) {
        Map<FieldArtifactDescriptorV2.FieldSemantic, FieldArtifactDescriptorV2> bySemantic =
                new EnumMap<>(FieldArtifactDescriptorV2.FieldSemantic.class);
        FieldArtifactDescriptorV2.Provenance provenance = null;
        for (String fieldId : binding.fieldIds()) {
            FieldArtifactDescriptorV2 field = byId.get(fieldId);
            if (bySemantic.putIfAbsent(field.definition().semantic(), field) != null) {
                throw new IllegalArgumentException("binding contains duplicate field semantics");
            }
            if (field.provenance().sourceKind() != FieldArtifactDescriptorV2.SourceKind.CONSTRAINT_MAP
                    || !field.provenance().sourceId().equals(binding.sourceId())) {
                throw new IllegalArgumentException("binding source does not match field provenance");
            }
            if (provenance == null) {
                provenance = field.provenance();
            } else if (!provenance.equals(field.provenance())) {
                throw new IllegalArgumentException("binding fields must have identical provenance");
            }
        }

        String expectedArtifactPrefix;
        FieldArtifactDescriptorV2.FieldSemantic canonicalSemantic;
        switch (binding.role()) {
            case LAND_WATER_MASK -> {
                expectedArtifactPrefix = "constraint:land-water:sha256-";
                canonicalSemantic = FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER;
                requireExactSemantics(bySemantic, Set.of(
                        FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER,
                        FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_LAND_WATER));
                requireDefinition(bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER),
                        FieldArtifactDescriptorV2.FieldValueType.U8,
                        FieldArtifactDescriptorV2.Sampling.NEAREST, 1_000_000L, 0L);
                requireDefinition(bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER),
                        FieldArtifactDescriptorV2.FieldValueType.U8,
                        FieldArtifactDescriptorV2.Sampling.NEAREST, 1_000_000L, 0L);
                requireDefinition(bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_LAND_WATER),
                        FieldArtifactDescriptorV2.FieldValueType.I32,
                        FieldArtifactDescriptorV2.Sampling.NEAREST, 1L, 0L);
                requireCanonicalNoData(
                        bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER), 255);
                requireCanonicalNoData(
                        bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER), 255);
                requireNoDataAlignment(
                        bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER),
                        bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER),
                        bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_LAND_WATER));
            }
            case HEIGHT_GUIDE -> {
                expectedArtifactPrefix = "constraint:height-guide:sha256-";
                canonicalSemantic = FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT;
                requireExactSemantics(bySemantic, Set.of(
                        FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT,
                        FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_HEIGHT,
                        FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT));
                FieldArtifactDescriptorV2.Sampling expectedSampling = switch (binding.sampling()) {
                    case NEAREST -> FieldArtifactDescriptorV2.Sampling.NEAREST;
                    case BILINEAR_FIXED -> FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED;
                };
                requireDefinition(bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT),
                        FieldArtifactDescriptorV2.FieldValueType.I32, expectedSampling, 1L, 0L);
                requireDefinition(bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_HEIGHT),
                        FieldArtifactDescriptorV2.FieldValueType.I32, expectedSampling, 1L, 0L);
                requireDefinition(bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT),
                        FieldArtifactDescriptorV2.FieldValueType.I32, expectedSampling, 1L, 0L);
                requireCanonicalNoData(
                        bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT), Integer.MIN_VALUE);
                requireCanonicalNoData(
                        bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_HEIGHT), Integer.MIN_VALUE);
                requireNoDataAlignment(
                        bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT),
                        bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_HEIGHT),
                        bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT));
            }
            case ZONE_LABEL_MAP -> {
                expectedArtifactPrefix = "constraint:zone-label-map:sha256-";
                canonicalSemantic = FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP;
                requireExactSemantics(bySemantic, Set.of(
                        FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP));
                requireDefinition(bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP),
                        FieldArtifactDescriptorV2.FieldValueType.U16,
                        FieldArtifactDescriptorV2.Sampling.NEAREST, 1_000_000L, 0L);
                requireCanonicalNoData(
                        bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP), 65_535);
            }
            default -> throw new IllegalArgumentException("unsupported constraint role");
        }
        if (!binding.canonicalArtifactId().startsWith(expectedArtifactPrefix)) {
            throw new IllegalArgumentException("canonical artifact ID prefix does not match binding role");
        }
        FieldArtifactDescriptorV2 canonical = byId.get(binding.canonicalFieldId());
        if (canonical.definition().semantic() != canonicalSemantic) {
            throw new IllegalArgumentException("canonical field semantic does not match binding role");
        }
    }

    private static void requireExactSemantics(
            Map<FieldArtifactDescriptorV2.FieldSemantic, FieldArtifactDescriptorV2> actual,
            Set<FieldArtifactDescriptorV2.FieldSemantic> expected
    ) {
        if (!actual.keySet().equals(expected)) {
            throw new IllegalArgumentException("binding field set does not match its role");
        }
    }

    private static void requireDefinition(
            FieldArtifactDescriptorV2 field,
            FieldArtifactDescriptorV2.FieldValueType valueType,
            FieldArtifactDescriptorV2.Sampling sampling,
            long scaleMillionths,
            long offsetMillionths
    ) {
        var definition = field.definition();
        if (definition.valueType() != valueType
                || definition.sampling() != sampling
                || definition.scaleMillionths() != scaleMillionths
                || definition.offsetMillionths() != offsetMillionths
                || definition.coordinateSpace()
                != FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ) {
            throw new IllegalArgumentException("field definition does not match its canonical role");
        }
    }

    private static void requireNoDataAlignment(
            FieldArtifactDescriptorV2 desired,
            FieldArtifactDescriptorV2 actual,
            FieldArtifactDescriptorV2 residual
    ) {
        var desiredDefinition = desired.definition();
        var actualDefinition = actual.definition();
        var residualDefinition = residual.definition();
        if (desiredDefinition.hasNoData() != actualDefinition.hasNoData()
                || desiredDefinition.noDataRaw() != actualDefinition.noDataRaw()
                || desiredDefinition.hasNoData() != residualDefinition.hasNoData()
                || (residualDefinition.hasNoData()
                && residualDefinition.noDataRaw() != Integer.MIN_VALUE)) {
            throw new IllegalArgumentException("desired/actual/residual no-data definitions are inconsistent");
        }
    }

    private static void requireCanonicalNoData(FieldArtifactDescriptorV2 field, int sentinel) {
        var definition = field.definition();
        if (definition.hasNoData() && definition.noDataRaw() != sentinel) {
            throw new IllegalArgumentException("field uses a non-canonical no-data sentinel");
        }
    }

    public record AppliedBinding(
            String bindingId,
            String sourceId,
            TerrainIntentV2.ConstraintMapRole role,
            TerrainIntentV2.Strength strength,
            TerrainIntentV2.Sampling sampling,
            int toleranceBlocks,
            int weightMillionths,
            String canonicalArtifactId,
            String canonicalFieldId,
            List<String> fieldIds,
            List<LabelEntry> labels
    ) {
        public AppliedBinding(
                String bindingId,
                String sourceId,
                TerrainIntentV2.ConstraintMapRole role,
                TerrainIntentV2.Strength strength,
                TerrainIntentV2.Sampling sampling,
                int toleranceBlocks,
                int weightMillionths,
                String canonicalArtifactId,
                String canonicalFieldId,
                List<String> fieldIds
        ) {
            this(bindingId, sourceId, role, strength, sampling, toleranceBlocks, weightMillionths,
                    canonicalArtifactId, canonicalFieldId, fieldIds, List.of());
        }

        public AppliedBinding {
            bindingId = V2Validation.slug(bindingId, "bindingId");
            sourceId = V2Validation.nonBlank(sourceId, "sourceId", 96);
            if (!SOURCE_ID.matcher(sourceId).matches()) {
                throw new IllegalArgumentException("sourceId must use constraint-source:<slug>");
            }
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(strength, "strength");
            Objects.requireNonNull(sampling, "sampling");
            if (toleranceBlocks < 0 || toleranceBlocks > 1_000) {
                throw new IllegalArgumentException("toleranceBlocks outside 0..1000");
            }
            if (strength == TerrainIntentV2.Strength.HARD && weightMillionths != 0
                    || strength == TerrainIntentV2.Strength.SOFT
                    && (weightMillionths < 1 || weightMillionths > TerrainIntentV2.FIXED_SCALE)) {
                throw new IllegalArgumentException("invalid binding weight");
            }
            canonicalArtifactId = V2Validation.nonBlank(
                    canonicalArtifactId, "canonicalArtifactId", 192);
            if (!ARTIFACT_ID.matcher(canonicalArtifactId).matches()) {
                throw new IllegalArgumentException("invalid canonical constraint artifact ID");
            }
            canonicalFieldId = V2Validation.qualifiedId(canonicalFieldId, "canonicalFieldId");
            fieldIds = V2Validation.sorted(fieldIds, "fieldIds", 8, Comparator.naturalOrder()).stream()
                    .map(value -> V2Validation.qualifiedId(value, "fieldId"))
                    .toList();
            if (fieldIds.isEmpty() || !fieldIds.contains(canonicalFieldId)) {
                throw new IllegalArgumentException("fieldIds must include canonicalFieldId");
            }
            if (new HashSet<>(fieldIds).size() != fieldIds.size()) {
                throw new IllegalArgumentException("fieldIds must be unique");
            }
            if (role != TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE
                    && sampling != TerrainIntentV2.Sampling.NEAREST) {
                throw new IllegalArgumentException("categorical bindings require nearest sampling");
            }
            labels = V2Validation.sorted(labels, "labels", 4_096,
                    Comparator.comparingInt(LabelEntry::sourceSample));
            if (role == TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE && !labels.isEmpty()
                    || role != TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE && labels.isEmpty()) {
                throw new IllegalArgumentException("label table presence does not match constraint role");
            }
            Set<Integer> samples = new HashSet<>();
            Set<Integer> values = new HashSet<>();
            Set<String> names = new HashSet<>();
            for (LabelEntry label : labels) {
                if (!samples.add(label.sourceSample())
                        || !values.add(label.canonicalValue())
                        || !names.add(label.label())) {
                    throw new IllegalArgumentException("label table values must be unique");
                }
            }
            if (role == TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK) {
                if (labels.size() != 2
                        || labels.stream().noneMatch(label -> label.label().equals("water")
                        && label.canonicalValue() == 0)
                        || labels.stream().noneMatch(label -> label.label().equals("land")
                        && label.canonicalValue() == 1)) {
                    throw new IllegalArgumentException(
                            "land-water labels must be exactly water=0 and land=1");
                }
            } else if (role == TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP) {
                Set<Integer> expected = new HashSet<>();
                for (int value = 1; value <= labels.size(); value++) expected.add(value);
                if (!values.equals(expected)) {
                    throw new IllegalArgumentException("zone canonical label IDs must be contiguous 1..N");
                }
            }
        }
    }

    /** Exact source-sample to canonical field ID dictionary frozen with categorical artifacts. */
    public record LabelEntry(int sourceSample, int canonicalValue, String label) {
        public LabelEntry {
            if (sourceSample < 0 || sourceSample > 65_535
                    || canonicalValue < 0 || canonicalValue > 65_534) {
                throw new IllegalArgumentException("label table numeric value outside U16 range");
            }
            label = V2Validation.slug(label, "label");
        }
    }
}
