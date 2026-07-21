package com.github.nankotsu029.landformcraft.core.v2.migration;

import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationReportV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationSourceKindV2;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One strictly read v1 asset, reduced to what a v2 artifact can be built from (V2-12-04).
 *
 * @param kind                the declared source kind
 * @param schemaVersion       {@code schemaVersion} declared by the v1 contract
 * @param digest              SHA-256 byte digest of the v1 {@code terrain-intent.json} document
 * @param canonicalChecksum   canonical checksum defined by the v1 format, or {@code ""} when the
 *                            format defines none
 * @param requestId           request identifier carried by the source, or a deterministic one
 *                            derived from {@code digest} when the source carries none
 * @param intent              the v1 semantic intent
 * @param sealedAt            timestamp the source sealed itself with, used so the migration stays
 *                            deterministic instead of stamping wall-clock time
 * @param carrierUnmapped     elements that belong to the carrier artifact rather than the intent
 *                            (Release 1 tiles, assets, previews) and cannot be converted
 */
public record LegacyV1SourceV2(
        LegacyMigrationSourceKindV2 kind,
        int schemaVersion,
        String digest,
        String canonicalChecksum,
        String requestId,
        TerrainIntent intent,
        Instant sealedAt,
        List<LegacyMigrationReportV2.UnmappedElement> carrierUnmapped
) {
    public LegacyV1SourceV2 {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(canonicalChecksum, "canonicalChecksum");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(sealedAt, "sealedAt");
        carrierUnmapped = List.copyOf(Objects.requireNonNull(carrierUnmapped, "carrierUnmapped"));
    }
}
