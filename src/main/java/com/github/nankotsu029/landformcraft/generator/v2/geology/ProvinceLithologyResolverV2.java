package com.github.nankotsu029.landformcraft.generator.v2.geology;

import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Pure compact-code resolver over the existing V2-4-01 province field; it owns no new sidecar. */
public final class ProvinceLithologyResolverV2 {
    private final Map<Integer, Integer> lithologyCodeByProvinceCode;

    public ProvinceLithologyResolverV2(GeologyPlanV2 geologyPlan, LithologyPlanV2 lithologyPlan) {
        Objects.requireNonNull(geologyPlan, "geologyPlan");
        Objects.requireNonNull(lithologyPlan, "lithologyPlan");
        lithologyPlan.requireGeologyPlan(geologyPlan);
        Map<Integer, Integer> mapping = new HashMap<>();
        for (LithologyPlanV2.ProvinceAssignment assignment : lithologyPlan.provinceAssignments()) {
            if (mapping.putIfAbsent(assignment.provinceCode(), assignment.lithologyCode()) != null) {
                throw new IllegalArgumentException("duplicate province lithology assignment");
            }
        }
        this.lithologyCodeByProvinceCode = Map.copyOf(mapping);
    }

    /** Returns U16 no-data for a no-data geology province and rejects all other unknown values. */
    public int lithologyCodeForProvinceRaw(int provinceRaw) {
        if (provinceRaw == GeologyPlanV2.NO_DATA_RAW) {
            return GeologyPlanV2.NO_DATA_RAW;
        }
        Integer code = lithologyCodeByProvinceCode.get(provinceRaw);
        if (code == null) {
            throw new IllegalArgumentException("unknown geology province code for lithology assignment: " + provinceRaw);
        }
        return code;
    }
}
