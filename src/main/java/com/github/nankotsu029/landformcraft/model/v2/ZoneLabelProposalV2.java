package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Objects;

/** Proposed categorical label from fixed-palette zone extraction (SOFT draft metadata). */
public record ZoneLabelProposalV2(int sample, String label, int red, int green, int blue) {
    public ZoneLabelProposalV2 {
        if (sample < 1 || sample > 254) {
            throw new IllegalArgumentException("proposal sample must be within 1..254");
        }
        label = V2Validation.slug(label, "label");
        if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
            throw new IllegalArgumentException("proposal RGB must be within 0..255");
        }
    }
}
