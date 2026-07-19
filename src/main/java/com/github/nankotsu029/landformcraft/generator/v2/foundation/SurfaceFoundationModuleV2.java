package com.github.nankotsu029.landformcraft.generator.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;

import java.util.List;

/** Built-in module identity for the V2-9-01 surface foundation contract. */
public final class SurfaceFoundationModuleV2 {
    public static final String MODULE_ID = SurfaceFoundationPlanV2.MODULE_ID;
    public static final String MODULE_VERSION = SurfaceFoundationPlanV2.MODULE_VERSION;
    public static final String STAGE_ID = SurfaceFoundationPlanV2.STAGE_ID;
    public static final String SEED_NAMESPACE = SurfaceFoundationPlanV2.SEED_NAMESPACE;
    public static final String GENERATOR_VERSION = SurfaceFoundationPlanV2.GENERATOR_VERSION;

    private SurfaceFoundationModuleV2() {
    }

    public static List<SurfaceFoundationPlanV2.FieldBinding> requiredFieldBindings() {
        return List.of(
                binding(SurfaceFoundationPlanV2.SURFACE_CLASS_FIELD_ID,
                        SurfaceFoundationPlanV2.FieldSemantic.SURFACE_CLASS),
                binding(SurfaceFoundationPlanV2.ELEVATION_FIELD_ID,
                        SurfaceFoundationPlanV2.FieldSemantic.ELEVATION),
                binding(SurfaceFoundationPlanV2.RESIDUAL_FIELD_ID,
                        SurfaceFoundationPlanV2.FieldSemantic.RESIDUAL),
                binding(SurfaceFoundationPlanV2.OWNER_INDEX_FIELD_ID,
                        SurfaceFoundationPlanV2.FieldSemantic.OWNER_INDEX),
                binding(SurfaceFoundationPlanV2.TRANSITION_WEIGHT_FIELD_ID,
                        SurfaceFoundationPlanV2.FieldSemantic.TRANSITION_WEIGHT));
    }

    private static SurfaceFoundationPlanV2.FieldBinding binding(
            String fieldId,
            SurfaceFoundationPlanV2.FieldSemantic semantic
    ) {
        return new SurfaceFoundationPlanV2.FieldBinding(
                fieldId,
                semantic,
                SurfaceFoundationPlanV2.FieldValueType.U16,
                MODULE_ID,
                SurfaceFoundationPlanV2.Ownership.SINGLE_OWNER,
                FieldArtifactDescriptorV2.ENCODING_VERSION);
    }
}
