package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.model.ConfirmationAction;

import java.util.Objects;

public record PreparedRecovery(
        RecoveryReport report,
        ConfirmationAction action,
        String confirmationToken
) {
    public PreparedRecovery {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(confirmationToken, "confirmationToken");
    }
}
