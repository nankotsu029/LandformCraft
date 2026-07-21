package com.github.nankotsu029.landformcraft.core.v2.migration;

import com.github.nankotsu029.landformcraft.format.v2.migration.LegacyMigrationBundleV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationReportV2;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of one migration (V2-12-04). A dry run carries the report only; a publish also carries the
 * bundle as it verified after the atomic publish.
 */
public record LegacyMigrationResultV2(
        LegacyMigrationReportV2 report,
        Optional<LegacyMigrationBundleV2> bundle
) {
    public LegacyMigrationResultV2 {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(bundle, "bundle");
    }
}
