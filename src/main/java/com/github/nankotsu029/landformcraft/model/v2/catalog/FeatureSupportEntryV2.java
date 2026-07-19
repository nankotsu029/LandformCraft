package com.github.nankotsu029.landformcraft.model.v2.catalog;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One Feature Support Catalog entry. {@code lifecycleStatus} is display-only compatibility
 * with module descriptors and must not be used as the capability decision.
 */
public record FeatureSupportEntryV2(
        String entryId,
        String profileId,
        FeaturePrimaryRoleV2 primaryRole,
        List<FeaturePrimaryRoleV2> allowedUsages,
        FeatureSupportCapabilitiesV2 support,
        String featureKindName,
        ModuleDescriptorV2.LifecycleStatus lifecycleStatus,
        String requiredReleaseCapability,
        String requiredRuntime,
        String evidenceRef,
        List<String> notes
) {
    public FeatureSupportEntryV2 {
        entryId = CatalogValidationV2.entryId(entryId, "entryId");
        profileId = CatalogValidationV2.profileId(profileId, "profileId");
        Objects.requireNonNull(primaryRole, "primaryRole");
        allowedUsages = CatalogValidationV2.sorted(
                allowedUsages, "allowedUsages", 8, Comparator.comparing(Enum::name));
        if (!allowedUsages.contains(primaryRole)) {
            throw new IllegalArgumentException("allowedUsages must include primaryRole for " + entryId);
        }
        Objects.requireNonNull(support, "support");
        featureKindName = CatalogValidationV2.optionalNonBlank(featureKindName, "featureKindName", 64);
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus");
        requiredReleaseCapability = CatalogValidationV2.optionalNonBlank(
                requiredReleaseCapability, "requiredReleaseCapability", 64);
        requiredRuntime = CatalogValidationV2.optionalNonBlank(requiredRuntime, "requiredRuntime", 128);
        evidenceRef = CatalogValidationV2.nonBlank(evidenceRef, "evidenceRef", 256);
        notes = CatalogValidationV2.immutable(notes, "notes", 16);
        validateRoleUsageConsistency(entryId, primaryRole, support);
        validatePaperColumnStructure(entryId, support, requiredReleaseCapability, requiredRuntime);
    }

    public Optional<TerrainIntentV2.FeatureKind> featureKind() {
        if (featureKindName.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TerrainIntentV2.FeatureKind.valueOf(featureKindName));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("catalog entry references unknown FeatureKind: " + featureKindName);
        }
    }

    public boolean hasFeatureKind() {
        return !featureKindName.isEmpty();
    }

    public FeatureSupportEntryV2 withSupport(FeatureSupportCapabilitiesV2 replacement) {
        return new FeatureSupportEntryV2(
                entryId, profileId, primaryRole, allowedUsages, replacement, featureKindName,
                lifecycleStatus, requiredReleaseCapability, requiredRuntime, evidenceRef, notes);
    }

    private static void validateRoleUsageConsistency(
            String entryId,
            FeaturePrimaryRoleV2 primaryRole,
            FeatureSupportCapabilitiesV2 support
    ) {
        if (primaryRole == FeaturePrimaryRoleV2.CHILD_PLAN_ONLY
                && support.standaloneUsage() == FeatureSupportLevelV2.SUPPORTED) {
            throw new IllegalArgumentException(
                    "child-plan-only entry cannot claim standalone_usage SUPPORTED: " + entryId);
        }
        if (primaryRole == FeaturePrimaryRoleV2.VOLUME_OVERLAY
                && support.standaloneUsage() == FeatureSupportLevelV2.SUPPORTED) {
            throw new IllegalArgumentException(
                    "volume-overlay entry cannot claim standalone_usage SUPPORTED: " + entryId);
        }
        if (primaryRole == FeaturePrimaryRoleV2.COMPOSITE_PRESET
                && support.intentCompile() == FeatureSupportLevelV2.SUPPORTED) {
            throw new IllegalArgumentException(
                    "composite preset cannot claim intent_compile SUPPORTED: " + entryId);
        }
    }

    /**
     * V2-11-01 structural rules for the five Paper columns. Policy (which capability prefixes
     * carry real-machine smoke evidence) lives in the consistency verifier; this record only
     * enforces reachability and monotonicity so a Paper claim can never exist without a Release
     * container path, declared runtime, and a paper_apply claim at least as strong.
     */
    private static void validatePaperColumnStructure(
            String entryId,
            FeatureSupportCapabilitiesV2 support,
            String requiredReleaseCapability,
            String requiredRuntime
    ) {
        List<FeatureSupportLevelV2> paperColumns = List.of(
                support.paperApply(), support.postApplyValidation(), support.snapshot(),
                support.rollback(), support.restartRecovery());
        boolean anyClaimed = paperColumns.stream().anyMatch(level ->
                level != FeatureSupportLevelV2.UNSUPPORTED
                        && level != FeatureSupportLevelV2.NOT_APPLICABLE);
        if (anyClaimed && requiredReleaseCapability.isEmpty()) {
            throw new IllegalArgumentException(
                    "Paper capability columns require a Release capability path: " + entryId
                            + " (" + support.paperApply().name().toLowerCase(Locale.ROOT) + ")");
        }
        boolean anySupported = paperColumns.stream()
                .anyMatch(level -> level == FeatureSupportLevelV2.SUPPORTED);
        if (anySupported) {
            if (requiredRuntime.isEmpty()) {
                throw new IllegalArgumentException(
                        "SUPPORTED Paper capability requires a declared runtime: " + entryId);
            }
            if (support.paperApply() != FeatureSupportLevelV2.SUPPORTED) {
                throw new IllegalArgumentException(
                        "post-apply columns cannot exceed paper_apply: " + entryId);
            }
        }
    }
}
