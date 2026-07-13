package com.github.nankotsu029.landformcraft.model;

public enum RecoveryClassification {
    SAFE_TO_ROLLBACK,
    SAFE_TO_ACCEPT,
    SAFE_TO_RESUME,
    MANUAL_INTERVENTION_REQUIRED,
    CORRUPTED
}
