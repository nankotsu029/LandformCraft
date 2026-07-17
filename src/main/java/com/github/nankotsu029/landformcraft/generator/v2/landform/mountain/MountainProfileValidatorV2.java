package com.github.nankotsu029.landformcraft.generator.v2.landform.mountain;

import com.github.nankotsu029.landformcraft.model.v2.MountainPlanV2;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Detects frozen mountain ridge-id and station corruption before canonical raster publish. */
public final class MountainProfileValidatorV2 {
    private MountainProfileValidatorV2() {
    }

    public static void requireValid(MountainPlanV2 plan) {
        Objects.requireNonNull(plan, "plan");
        Set<String> ids = new HashSet<>();
        for (MountainPlanV2.NamedStation peak : plan.peaks()) {
            if (!ids.add(peak.stationId())) {
                throw new MountainGenerationException("v2.mountain-duplicate-ridge-id", "duplicate mountain peak id");
            }
        }
        for (MountainPlanV2.NamedStation saddle : plan.saddles()) {
            if (!ids.add(saddle.stationId())) {
                throw new MountainGenerationException("v2.mountain-duplicate-ridge-id", "duplicate mountain saddle id");
            }
        }
        for (MountainPlanV2.SpurSegment spur : plan.spurs()) {
            if (!ids.add(spur.spurId())) {
                throw new MountainGenerationException("v2.mountain-duplicate-ridge-id", "duplicate mountain spur id");
            }
        }
        long previous = -1L;
        for (MountainPlanV2.NamedStation peak : plan.peaks()) {
            if (peak.arcLengthMillionths() <= previous) {
                throw new MountainGenerationException("v2.mountain-peak-order", "mountain peak order is corrupted");
            }
            previous = peak.arcLengthMillionths();
        }
        if (plan.ridge().size() < 2) {
            throw new MountainGenerationException("v2.mountain-geometry", "mountain ridge is corrupted");
        }
    }
}
