package com.github.nankotsu029.landformcraft.model.v2.job;

/** Which of the two production export forms a v2 job runs (V2-12-09). */
public enum ExportJobKindV2 {
    /** Publishes the strict Release directory only. */
    GENERATE,
    /** Publishes the Release directory and its ZIP, then verifies placement eligibility. */
    EXPORT
}
