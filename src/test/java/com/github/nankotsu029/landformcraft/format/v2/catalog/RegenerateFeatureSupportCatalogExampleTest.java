package com.github.nankotsu029.landformcraft.format.v2.catalog;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/** Dev-only helper for refreshing the sealed Feature Support Catalog example. */
class RegenerateFeatureSupportCatalogExampleTest {
    @Test
    @Disabled("manual fixture regeneration helper")
    void rewriteSealedCatalogExample() throws Exception {
        FeatureSupportCatalogCodecV2 codec = new FeatureSupportCatalogCodecV2();
        codec.write(Path.of("examples/v2/catalog/feature-support-catalog-v2.json"),
                codec.builtInSealed());
    }
}
