package com.github.nankotsu029.landformcraft.model.v2;

/**
 * Frozen proposal-priority kinds for V2-7 multi-source reconciliation
 * ({@code image-fidelity-multisource-reconcile-v1}). Lower {@link #rank()} wins.
 * Mirrors {@code docs/design-v2/image-constraint-maps.md} §7; image drafts beat prompt soft.
 */
public enum ImageFidelitySourceKindV2 {
    MANUAL_HARD(1, ImageFidelityStrengthV2.HARD),
    HARD_CANONICAL_MAP(2, ImageFidelityStrengthV2.HARD),
    MANUAL_SOFT(3, ImageFidelityStrengthV2.SOFT),
    SOFT_CANONICAL_MAP(4, ImageFidelityStrengthV2.SOFT),
    IMAGE_DRAFT(5, ImageFidelityStrengthV2.SOFT),
    PROMPT_SOFT(6, ImageFidelityStrengthV2.SOFT),
    PRESET_DEFAULT(7, ImageFidelityStrengthV2.SOFT);

    private final int rank;
    private final ImageFidelityStrengthV2 defaultStrength;

    ImageFidelitySourceKindV2(int rank, ImageFidelityStrengthV2 defaultStrength) {
        this.rank = rank;
        this.defaultStrength = defaultStrength;
    }

    public int rank() {
        return rank;
    }

    public ImageFidelityStrengthV2 defaultStrength() {
        return defaultStrength;
    }
}
