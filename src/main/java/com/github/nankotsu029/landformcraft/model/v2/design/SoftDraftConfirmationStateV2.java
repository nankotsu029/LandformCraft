package com.github.nankotsu029.landformcraft.model.v2.design;

/** Soft-draft confirmation. CONFIRMED_SOFT never implies HARD constraint promotion. */
public enum SoftDraftConfirmationStateV2 {
    UNCONFIRMED,
    CONFIRMED_SOFT,
    REJECTED
}
