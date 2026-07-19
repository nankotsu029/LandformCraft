package com.github.nankotsu029.landformcraft.model.v2.placement;

/**
 * Conservative Release 2 recovery classification (V2-6-10). Distinct from v1
 * {@code RecoveryClassification}. Ambiguous evidence is never classified as success; only
 * {@link #SAFE_TO_ROLLBACK} and {@link #SAFE_TO_ACCEPT} permit a confirmation-bound world action.
 */
public enum PlacementRecoveryClassificationV2 {
    /** Journal proves no world mutation has occurred; leases may be released without world action. */
    NO_WORLD_MUTATION,
    /** Published snapshot evidence strict-verifies and the durable lease is owned; restore is safe. */
    SAFE_TO_ROLLBACK,
    /**
     * Additionally the current world exactly matches the expected applied stream over the full
     * union effect envelope; accept-as-applied and rollback are both safe.
     */
    SAFE_TO_ACCEPT,
    /** Journal is already terminal (APPLIED／ROLLED_BACK／UNDONE); no recovery action is needed. */
    ALREADY_TERMINAL,
    /** Evidence is missing, tampered, or mismatched; no automatic action is offered. */
    MANUAL_INTERVENTION_REQUIRED
}
