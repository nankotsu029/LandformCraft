package com.github.nankotsu029.landformcraft.generator.v2.geology;

import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;

import java.util.Objects;

/** Pure global-X/Z sampler for the empty or uniform-prior V2-4-01 geology foundation. */
public final class GeologyFieldSamplerV2 {
    private final GeologyPlanV2 plan;
    private final GeologyPlanV2.ProvinceDescriptor uniformProvince;

    public GeologyFieldSamplerV2(GeologyPlanV2 plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        if (plan.provinces().size() > 1) {
            throw new IllegalArgumentException(
                    "V2-4-01 sampler accepts only empty or one uniform geology province");
        }
        this.uniformProvince = plan.provinces().isEmpty() ? null : plan.provinces().getFirst();
    }

    public int rawValueAt(GeologyPlanV2.FieldSemantic semantic, int globalX, int globalZ) {
        Objects.requireNonNull(semantic, "semantic");
        if (globalX < 0 || globalX >= plan.width() || globalZ < 0 || globalZ >= plan.length()) {
            throw new IndexOutOfBoundsException("geology sample lies outside plan bounds");
        }
        if (uniformProvince == null) {
            return GeologyPlanV2.NO_DATA_RAW;
        }
        return switch (semantic) {
            case PROVINCE_ID -> uniformProvince.provinceCode();
            case FORMATION_ID -> uniformProvince.formationCode();
            case HARDNESS -> uniformProvince.hardnessRaw();
            case PERMEABILITY -> uniformProvince.permeabilityRaw();
        };
    }
}
