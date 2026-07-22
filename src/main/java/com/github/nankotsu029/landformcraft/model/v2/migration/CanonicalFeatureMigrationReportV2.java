package com.github.nankotsu029.landformcraft.model.v2.migration;

import com.github.nankotsu029.landformcraft.model.v2.CanonicalTerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Machine-readable source/target checksum binding for ADR 0036 explicit migration. */
public record CanonicalFeatureMigrationReportV2(
        String migrationContractVersion,
        String sourceArtifactIdentity,
        CanonicalTerrainIntentV2.FeatureProjection sourceProjection,
        CanonicalTerrainIntentV2.FeatureProjection targetProjection,
        String sourceCanonicalChecksum,
        String targetCanonicalChecksum,
        Status status,
        List<FeatureMigration> features,
        List<String> diagnostics
) {
    public static final String CONTRACT_VERSION = "canonical-feature-migration-report-v1";

    public CanonicalFeatureMigrationReportV2 {
        if (!CONTRACT_VERSION.equals(migrationContractVersion)) {
            throw new IllegalArgumentException("unknown canonical feature migration report version");
        }
        sourceArtifactIdentity = requireText(sourceArtifactIdentity, "sourceArtifactIdentity", 256);
        Objects.requireNonNull(sourceProjection, "sourceProjection");
        if (targetProjection != CanonicalTerrainIntentV2.FeatureProjection.CANONICAL_V2) {
            throw new IllegalArgumentException("migration report target projection must be CANONICAL_V2");
        }
        sourceCanonicalChecksum = checksum(sourceCanonicalChecksum, "sourceCanonicalChecksum");
        targetCanonicalChecksum = checksum(targetCanonicalChecksum, "targetCanonicalChecksum");
        Objects.requireNonNull(status, "status");
        if (status == Status.MIGRATED
                && sourceProjection != CanonicalTerrainIntentV2.FeatureProjection.LEGACY_V2) {
            throw new IllegalArgumentException("MIGRATED report source projection must be LEGACY_V2");
        }
        if (status == Status.ALREADY_CANONICAL
                && sourceProjection != CanonicalTerrainIntentV2.FeatureProjection.CANONICAL_V2) {
            throw new IllegalArgumentException("ALREADY_CANONICAL report source projection must be CANONICAL_V2");
        }
        Objects.requireNonNull(features, "features");
        Set<String> ids = new HashSet<>();
        features = List.copyOf(features.stream().peek(feature -> {
            if (!ids.add(feature.sourceFeatureId())) {
                throw new IllegalArgumentException("duplicate migration feature id: " + feature.sourceFeatureId());
            }
        }).sorted(Comparator.comparing(FeatureMigration::sourceFeatureId)).toList());
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics.stream()
                .map(value -> requireText(value, "diagnostic", 512)).sorted().toList());
        if (status == Status.ALREADY_CANONICAL && !sourceCanonicalChecksum.equals(targetCanonicalChecksum)) {
            throw new IllegalArgumentException("ALREADY_CANONICAL report checksums must match");
        }
    }

    public enum Status { MIGRATED, ALREADY_CANONICAL }

    public record FeatureMigration(
            String sourceFeatureId,
            TerrainIntentV2.FeatureKind sourceKind,
            String targetOwnerId,
            String targetDisposition,
            CanonicalTerrainIntentV2.LegacySeedBinding legacySeedBinding,
            String diagnostic
    ) {
        public FeatureMigration {
            sourceFeatureId = slug(sourceFeatureId, "sourceFeatureId");
            Objects.requireNonNull(sourceKind, "sourceKind");
            if (!CanonicalTerrainIntentV2.LEGACY_SOURCE_KINDS.contains(sourceKind)) {
                throw new IllegalArgumentException("report sourceKind is outside approved 14-token set");
            }
            targetOwnerId = slug(targetOwnerId, "targetOwnerId");
            targetDisposition = requireText(targetDisposition, "targetDisposition", 128);
            Objects.requireNonNull(legacySeedBinding, "legacySeedBinding");
            if (legacySeedBinding.sourceKind() != sourceKind) {
                throw new IllegalArgumentException("report seed sourceKind mismatch");
            }
            diagnostic = requireText(diagnostic, "diagnostic", 256);
        }
    }

    private static String checksum(String value, String field) {
        value = requireText(value, field, 64);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must use 64-character SHA-256 hex format");
        }
        return value;
    }

    private static String slug(String value, String field) {
        value = requireText(value, field, 64);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException(field + " must be a canonical slug");
        }
        return value;
    }

    private static String requireText(String value, String field, int maximum) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + maximum);
        }
        return value;
    }
}
