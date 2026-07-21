package com.github.nankotsu029.landformcraft.model.v2.migration;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The record of one v1 → v2 migration (V2-12-04, ADR 0035 D9).
 *
 * <p>The report is the honest half of the tool. Everything the v1 contract carried that has no exact
 * v2 equivalent is listed in {@link #unmappedElements()} with the reason it was not carried over,
 * because deriving it would mean inventing position, shape, relation or geology that v1 never
 * stated. A report never authorises continuing: a lossy migration still has to be acknowledged
 * explicitly by the operator.</p>
 */
public record LegacyMigrationReportV2(
        int reportVersion,
        String migrationId,
        LegacyMigrationSourceKindV2 sourceKind,
        int sourceSchemaVersion,
        String sourceDigest,
        String sourceCanonicalChecksum,
        String targetRequestId,
        String targetJobId,
        int targetIntentVersion,
        String targetIntentChecksum,
        List<String> mappedFields,
        List<UnmappedElement> unmappedElements,
        LegacyMigrationStatusV2 status
) {
    public static final int VERSION = 1;

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern UUID_FORM =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    public LegacyMigrationReportV2 {
        if (reportVersion != VERSION) {
            throw new IllegalArgumentException("reportVersion must be " + VERSION);
        }
        migrationId = requireSlug(migrationId, "migrationId");
        Objects.requireNonNull(sourceKind, "sourceKind");
        if (sourceSchemaVersion < 1) {
            throw new IllegalArgumentException("sourceSchemaVersion must be positive");
        }
        sourceDigest = requireChecksum(sourceDigest, "sourceDigest");
        sourceCanonicalChecksum = requireChecksumOrEmpty(sourceCanonicalChecksum, "sourceCanonicalChecksum");
        targetRequestId = requireSlug(targetRequestId, "targetRequestId");
        Objects.requireNonNull(targetJobId, "targetJobId");
        if (!UUID_FORM.matcher(targetJobId).matches()) {
            throw new IllegalArgumentException("targetJobId must be a lowercase UUID");
        }
        if (targetIntentVersion != 2) {
            throw new IllegalArgumentException("targetIntentVersion must be 2");
        }
        targetIntentChecksum = requireChecksum(targetIntentChecksum, "targetIntentChecksum");
        mappedFields = sortedDistinct(mappedFields, "mappedFields", 64);
        unmappedElements = List.copyOf(Objects.requireNonNull(unmappedElements, "unmappedElements")
                .stream()
                .sorted(Comparator.comparing(UnmappedElement::elementId))
                .toList());
        if (unmappedElements.size() > 512) {
            throw new IllegalArgumentException("unmappedElements exceeds 512");
        }
        for (int index = 1; index < unmappedElements.size(); index++) {
            if (unmappedElements.get(index - 1).elementId().equals(unmappedElements.get(index).elementId())) {
                throw new IllegalArgumentException("duplicate unmapped element id: "
                        + unmappedElements.get(index).elementId());
            }
        }
        Objects.requireNonNull(status, "status");
    }

    /** True when the v1 asset carried content that v2 cannot express without inventing geometry. */
    public boolean lossy() {
        return !unmappedElements.isEmpty();
    }

    /**
     * One v1 element that was deliberately not carried into the v2 intent.
     *
     * @param elementId  stable identifier, for example {@code zone:mountain-source}
     * @param sourceField the v1 contract field it came from
     * @param reason     why v2 cannot express it without inference
     */
    public record UnmappedElement(String elementId, String sourceField, String reason) {
        public UnmappedElement {
            elementId = requireBounded(elementId, "elementId", 128);
            sourceField = requireBounded(sourceField, "sourceField", 128);
            reason = requireBounded(reason, "reason", 512);
        }
    }

    private static List<String> sortedDistinct(List<String> values, String field, int maximum) {
        Objects.requireNonNull(values, field);
        List<String> sorted = values.stream().map(value -> requireBounded(value, field, 128)).sorted().distinct()
                .toList();
        if (sorted.size() != values.size() || sorted.size() > maximum) {
            throw new IllegalArgumentException(field + " must be unique and at most " + maximum);
        }
        return sorted;
    }

    private static String requireBounded(String value, String field, int maximumLength) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + maximumLength);
        }
        return value;
    }

    private static String requireSlug(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!SLUG.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase slug");
        }
        return value;
    }

    private static String requireChecksum(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256");
        }
        return value;
    }

    private static String requireChecksumOrEmpty(String value, String field) {
        Objects.requireNonNull(value, field);
        return value.isEmpty() ? value : requireChecksum(value, field);
    }
}
