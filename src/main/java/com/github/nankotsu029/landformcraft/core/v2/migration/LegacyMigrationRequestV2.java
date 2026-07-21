package com.github.nankotsu029.landformcraft.core.v2.migration;

import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationSourceKindV2;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Trusted application-layer inputs for one v1 → v2 migration (V2-12-04).
 *
 * <p>Nothing is inferred: the operator names the source kind, the source, where the bundle is
 * published, and whether a lossy migration is acceptable. A dry run needs no output root because it
 * writes nothing outside the tool's own temporary directory.</p>
 *
 * @param acceptLossy when {@code false} (the default) a source carrying anything v2 cannot express
 *                    is rejected instead of silently migrated with the loss recorded only in the
 *                    report (ADR 0035 D9-8: a report never authorises continuing)
 */
public record LegacyMigrationRequestV2(
        LegacyMigrationSourceKindV2 sourceKind,
        Path source,
        Optional<Path> outputRoot,
        String migrationId,
        boolean dryRun,
        boolean acceptLossy
) {
    private static final Pattern MIGRATION_ID = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,63}$");

    public LegacyMigrationRequestV2 {
        Objects.requireNonNull(sourceKind, "sourceKind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(outputRoot, "outputRoot");
        if (migrationId == null || !MIGRATION_ID.matcher(migrationId).matches()) {
            throw new IllegalArgumentException("migrationId must be a lowercase portable slug");
        }
        if (!dryRun && outputRoot.isEmpty()) {
            throw new IllegalArgumentException("a publishing migration requires an output root");
        }
    }

    /** A rehearsal that reads and maps the source but publishes nothing. */
    public static LegacyMigrationRequestV2 dryRun(LegacyMigrationSourceKindV2 kind, Path source) {
        return new LegacyMigrationRequestV2(kind, source, Optional.empty(), "dry-run", true, true);
    }
}
