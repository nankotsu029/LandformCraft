package com.github.nankotsu029.landformcraft.model.v2.job;

/**
 * Lifecycle of one v2 export job (V2-12-09).
 *
 * <p>{@link #PUBLISHED}, {@link #CANCELLED}, and {@link #FAILED} are terminal. A cancelled job never
 * publishes a Release: cancellation is observed before the publisher's atomic move, which is the
 * commit point (AGENTS.md §9).</p>
 */
public enum ExportJobStateV2 {
    QUEUED,
    RUNNING,
    PUBLISHED,
    CANCELLED,
    FAILED;

    public boolean terminal() {
        return this == PUBLISHED || this == CANCELLED || this == FAILED;
    }
}
