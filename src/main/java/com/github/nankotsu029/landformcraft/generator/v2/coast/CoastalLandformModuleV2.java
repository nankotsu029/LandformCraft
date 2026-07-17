package com.github.nankotsu029.landformcraft.generator.v2.coast;

import com.github.nankotsu029.landformcraft.model.v2.CoastalFeaturePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

/** Compile-time built-in coastal module boundary. It does not rasterize or materialize terrain. */
public interface CoastalLandformModuleV2 {
    ModuleDescriptorV2 descriptor();

    CoastalFeaturePlanV2 compileFoundation(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            int width,
            int length,
            String geometryChecksum
    );
}
