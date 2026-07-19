package com.github.nankotsu029.landformcraft.generator.v2.landform.reef;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;

/** Built-in supported offline coral reef bathymetry/environment module. */
public final class LandformCoralReefModuleV2 {
    public static final String MODULE_ID = "v2.landform.reef";
    public static final String MODULE_VERSION = "0.1.0-v2-4-10";
    public static final String STAGE_ID = "generate.landform-reef";
    public static final String REEF_MASK_FIELD_ID = "landform.reef.reef-mask";
    public static final String CREST_DEPTH_FIELD_ID = "landform.reef.crest-depth";
    public static final String LAGOON_DEPTH_FIELD_ID = "landform.reef.lagoon-depth";
    public static final String PASS_CORRIDOR_FIELD_ID = "landform.reef.pass-corridor";
    public static final String MARINE_CONNECTION_FIELD_ID = "landform.reef.marine-connection";
    public static final int MAXIMUM_SUPPORT_RADIUS_XZ = 64;

    private static final List<String> PROVIDED = List.of(
            REEF_MASK_FIELD_ID,
            CREST_DEPTH_FIELD_ID,
            LAGOON_DEPTH_FIELD_ID,
            PASS_CORRIDOR_FIELD_ID,
            MARINE_CONNECTION_FIELD_ID);

    private final ModuleDescriptorV2 descriptor = new ModuleDescriptorV2(
            MODULE_ID,
            MODULE_VERSION,
            ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
            List.of(TerrainIntentV2.FeatureKind.CORAL_REEF),
            List.of(),
            PROVIDED,
            PROVIDED.stream()
                    .map(field -> new ModuleDescriptorV2.FieldWrite(
                            field, ModuleDescriptorV2.MergeOperator.SINGLE_OWNER))
                    .toList(),
            STAGE_ID,
            MAXIMUM_SUPPORT_RADIUS_XZ,
            0,
            ModuleDescriptorV2.ResourceClass.REGIONAL_MEDIUM,
            List.of("feature.reef.validator"),
            List.of());

    public ModuleDescriptorV2 descriptor() {
        return descriptor;
    }
}
