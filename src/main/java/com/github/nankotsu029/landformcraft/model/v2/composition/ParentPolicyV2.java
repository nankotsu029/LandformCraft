package com.github.nankotsu029.landformcraft.model.v2.composition;

/** ADR 0038 D3 parent dependency axis, independent of the composition stages. */
public enum ParentPolicyV2 {
    /** Meaningful without a parent (a checksum bind to a host plan is still allowed). */
    STANDALONE,
    /** Only meaningful inside a specific parent's ownership (child plan). */
    PARENT_REQUIRED,
    /** An overlay hook bound to the parent plan's checksum. */
    PARENT_BOUND_OVERLAY
}
