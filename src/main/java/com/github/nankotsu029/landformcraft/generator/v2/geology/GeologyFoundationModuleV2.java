package com.github.nankotsu029.landformcraft.generator.v2.geology;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;

import java.util.List;

/** Stage-3 typed field ownership contract for V2-4-01. */
public final class GeologyFoundationModuleV2 {
    public static final String MODULE_ID = "v2.environment.geology-foundation";
    public static final String MODULE_VERSION = "0.1.0-v2-4-01";
    public static final String GENERATOR_VERSION = "geology-uniform-prior-v1";
    public static final String STAGE_ID = "generate.geology-foundation";
    public static final String SEED_NAMESPACE = "terrain.v2.geology";

    public static final String PROVINCE_ID_FIELD = "geology.province-id";
    public static final String FORMATION_ID_FIELD = "geology.formation-id";
    public static final String HARDNESS_FIELD = "geology.hardness";
    public static final String PERMEABILITY_FIELD = "geology.permeability";

    private static final List<String> FIELDS = List.of(
            FORMATION_ID_FIELD,
            HARDNESS_FIELD,
            PERMEABILITY_FIELD,
            PROVINCE_ID_FIELD);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
            List.of(),
            List.of(),
            FIELDS,
            FIELDS.stream().map(field -> new ModuleDescriptorV2.FieldWrite(
                    field, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER)).toList(),
            STAGE_ID,
            0,
            0,
            ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of(),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }

    public List<GeologyPlanV2.FieldBinding> fieldBindings() {
        return List.of(
                binding(FORMATION_ID_FIELD, GeologyPlanV2.FieldSemantic.FORMATION_ID),
                binding(HARDNESS_FIELD, GeologyPlanV2.FieldSemantic.HARDNESS),
                binding(PERMEABILITY_FIELD, GeologyPlanV2.FieldSemantic.PERMEABILITY),
                binding(PROVINCE_ID_FIELD, GeologyPlanV2.FieldSemantic.PROVINCE_ID));
    }

    private static GeologyPlanV2.FieldBinding binding(
            String fieldId,
            GeologyPlanV2.FieldSemantic semantic
    ) {
        return new GeologyPlanV2.FieldBinding(
                fieldId,
                semantic,
                GeologyPlanV2.FieldValueType.U16,
                MODULE_ID,
                GeologyPlanV2.Ownership.SINGLE_OWNER,
                com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2.ENCODING_VERSION);
    }
}
