package com.github.nankotsu029.landformcraft.format.v2.migration;

import com.github.nankotsu029.landformcraft.format.v2.design.DesignVerificationV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationReportV2;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One verified migration bundle: the report plus the Release 2 design package it produced
 * (V2-12-04).
 */
public record LegacyMigrationBundleV2(
        Path root,
        LegacyMigrationReportV2 report,
        Path designPackage,
        DesignVerificationV2 design,
        int verifiedFiles
) {
    public LegacyMigrationBundleV2 {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(designPackage, "designPackage");
        Objects.requireNonNull(design, "design");
        if (verifiedFiles < 1) {
            throw new IllegalArgumentException("verifiedFiles must be positive");
        }
    }
}
