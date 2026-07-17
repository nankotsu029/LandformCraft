package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Data-only descriptor for a compile-time built-in module. */
public record ModuleDescriptorV2(
        String moduleId,
        String moduleVersion,
        LifecycleStatus lifecycleStatus,
        List<TerrainIntentV2.FeatureKind> supportedFeatureKinds,
        List<String> requiredFields,
        List<String> providedFields,
        List<FieldWrite> fieldWrites,
        String stageId,
        int requiredHaloXZ,
        int requiredHaloY,
        ResourceClass resourceClass,
        List<String> validatorCapabilities,
        List<String> previewCapabilities
) {
    public ModuleDescriptorV2 {
        moduleId = V2Validation.qualifiedId(moduleId, "moduleId");
        moduleVersion = V2Validation.nonBlank(moduleVersion, "moduleVersion", 64);
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus");
        supportedFeatureKinds = V2Validation.sorted(supportedFeatureKinds, "supportedFeatureKinds", 128,
                Comparator.comparing(Enum::name));
        requiredFields = qualifiedIds(requiredFields, "requiredFields");
        providedFields = qualifiedIds(providedFields, "providedFields");
        fieldWrites = V2Validation.sorted(fieldWrites, "fieldWrites", 64, Comparator.comparing(FieldWrite::fieldId));
        stageId = V2Validation.qualifiedId(stageId, "stageId");
        if (requiredHaloXZ < 0 || requiredHaloXZ > 1_000 || requiredHaloY < 0 || requiredHaloY > 512) {
            throw new IllegalArgumentException("module halo outside bounds");
        }
        Objects.requireNonNull(resourceClass, "resourceClass");
        validatorCapabilities = qualifiedIds(validatorCapabilities, "validatorCapabilities");
        previewCapabilities = qualifiedIds(previewCapabilities, "previewCapabilities");
        if (!providedFields.equals(fieldWrites.stream().map(FieldWrite::fieldId).sorted().toList())) {
            throw new IllegalArgumentException("fieldWrites must exactly cover providedFields");
        }
    }

    public enum LifecycleStatus { EXPERIMENTAL, SUPPORTED, DEPRECATED }
    public enum MergeOperator { SINGLE_OWNER, MIN, MAX, ADD_CLAMP, MASK_UNION, PRIORITY_BLEND, ORDERED_CSG }
    public enum ResourceClass { DIAGNOSTIC_LOW, REGIONAL_MEDIUM, VOLUMETRIC_HIGH }

    public record FieldWrite(String fieldId, MergeOperator mergeOperator) {
        public FieldWrite {
            fieldId = V2Validation.qualifiedId(fieldId, "fieldId");
            Objects.requireNonNull(mergeOperator, "mergeOperator");
        }
    }

    private static List<String> qualifiedIds(List<String> values, String field) {
        return V2Validation.sorted(values, field, 128, Comparator.naturalOrder()).stream()
                .map(value -> V2Validation.qualifiedId(value, field + " entry")).toList();
    }
}
