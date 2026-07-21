package com.github.nankotsu029.landformcraft.model.v2.migration;

/** Whether a migration report describes a rehearsal or a published bundle (V2-12-04, ADR 0035 D9-2). */
public enum LegacyMigrationStatusV2 {
    /** Nothing was written outside the tool's own temporary directory. */
    DRY_RUN,
    /** The bundle was published atomically and re-verified after publish. */
    PUBLISHED
}
