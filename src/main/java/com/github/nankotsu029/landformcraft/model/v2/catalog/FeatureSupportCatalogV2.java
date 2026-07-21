package com.github.nankotsu029.landformcraft.model.v2.catalog;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Versioned Feature Support Catalog (V2-6-18). Authoritative for role / 13-capability matrix,
 * placement dimension hard limit, presets, and deferred/unsupported diagnostics.
 */
public record FeatureSupportCatalogV2(
        int catalogVersion,
        String contractVersion,
        PlacementDimensionLimitV2 placementDimensionLimit,
        List<FeatureSupportEntryV2> entries,
        List<String> availablePresets,
        List<String> unsupportedDiagnostics,
        List<String> deferredDiagnostics,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "feature-support-catalog-v1";
    public static final int MAXIMUM_ENTRIES = 128;
    public static final int MAXIMUM_DIAGNOSTICS = 64;
    public static final int MAXIMUM_PRESETS = 32;

    public FeatureSupportCatalogV2 {
        if (catalogVersion != VERSION) {
            throw new IllegalArgumentException(
                    "unknown feature support catalog version: " + catalogVersion);
        }
        contractVersion = CatalogValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown feature support catalog contract version");
        }
        Objects.requireNonNull(placementDimensionLimit, "placementDimensionLimit");
        if (placementDimensionLimit.maximumWidth() > PlacementDimensionLimitV2.MEASURED_MAXIMUM
                || placementDimensionLimit.maximumLength() > PlacementDimensionLimitV2.MEASURED_MAXIMUM) {
            throw new IllegalArgumentException(
                    "placementDimensionLimit exceeds measured ceiling "
                            + PlacementDimensionLimitV2.MEASURED_MAXIMUM
                            + " (V2-11-04/05 measured 500x500 and 1000x1000 on FAWE 2.15.2;"
                            + " larger sizes are unmeasured and cannot be catalogued)");
        }
        entries = CatalogValidationV2.sorted(
                entries, "entries", MAXIMUM_ENTRIES, Comparator.comparing(FeatureSupportEntryV2::entryId));
        availablePresets = CatalogValidationV2.sorted(
                availablePresets, "availablePresets", MAXIMUM_PRESETS, Comparator.naturalOrder());
        unsupportedDiagnostics = CatalogValidationV2.immutable(
                unsupportedDiagnostics, "unsupportedDiagnostics", MAXIMUM_DIAGNOSTICS);
        deferredDiagnostics = CatalogValidationV2.immutable(
                deferredDiagnostics, "deferredDiagnostics", MAXIMUM_DIAGNOSTICS);
        canonicalChecksum = CatalogValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
        validateUniqueEntryIds(entries);
    }

    public FeatureSupportCatalogV2 withCanonicalChecksum(String checksum) {
        return new FeatureSupportCatalogV2(
                catalogVersion, contractVersion, placementDimensionLimit, entries,
                availablePresets, unsupportedDiagnostics, deferredDiagnostics, checksum);
    }

    public Optional<FeatureSupportEntryV2> find(String entryId) {
        Objects.requireNonNull(entryId, "entryId");
        return entries.stream().filter(entry -> entry.entryId().equals(entryId)).findFirst();
    }

    public FeatureSupportEntryV2 require(String entryId) {
        return find(entryId).orElseThrow(() -> new IllegalArgumentException(
                "unknown feature support catalog entry: " + entryId));
    }

    public boolean rejectsUnmeasuredPaperPromotion(int width, int length) {
        return !placementDimensionLimit.admits(width, length);
    }

    private static void validateUniqueEntryIds(List<FeatureSupportEntryV2> entries) {
        Set<String> seen = new HashSet<>();
        for (FeatureSupportEntryV2 entry : entries) {
            if (!seen.add(entry.entryId())) {
                throw new IllegalArgumentException("duplicate catalog entryId: " + entry.entryId());
            }
            if (entry.hasFeatureKind() && !entry.entryId().equals(entry.featureKindName())) {
                throw new IllegalArgumentException(
                        "FeatureKind entryId must equal featureKindName: " + entry.entryId());
            }
        }
        if (seen.size() > MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException("catalog exceeds parse budget");
        }
        // Keep Locale unused-path free for determinism comments in docs.
        Objects.requireNonNull(Locale.ROOT);
    }
}
