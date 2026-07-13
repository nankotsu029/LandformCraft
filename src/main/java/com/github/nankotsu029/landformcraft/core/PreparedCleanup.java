package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.model.SnapshotCleanupPlan;

import java.util.Objects;

public record PreparedCleanup(SnapshotCleanupPlan plan, String confirmationToken) {
    public PreparedCleanup {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(confirmationToken, "confirmationToken");
    }
}
