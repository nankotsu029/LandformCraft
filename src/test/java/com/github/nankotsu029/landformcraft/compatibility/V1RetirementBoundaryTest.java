package com.github.nankotsu029.landformcraft.compatibility;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static ADR 0035 D2/D3 boundary: production v1 writers are gone; legacy readers remain. */
class V1RetirementBoundaryTest {
    private static final Path MAIN = Path.of("src/main/java/com/github/nankotsu029/landformcraft");
    private static final List<String> RETIRED_TYPES = List.of(
            "generator/TerrainGenerator.java",
            "generator/LogicalLayoutGenerator.java",
            "generator/StructurePlanner.java",
            "generator/v2/V1TerrainQueryAdapter.java",
            "format/ReleasePublisher.java",
            "format/ReleaseArtifacts.java",
            "format/DesignArtifactPublisher.java",
            "core/PlacementApplicationService.java",
            "core/SnapshotCleanupService.java",
            "core/GenerationApplicationService.java",
            "core/TerrainDesignApplicationService.java",
            "ai/spi/TerrainDesignProvider.java",
            "paper/PaperTerrainDesignProviderFactory.java"
    );
    private static final List<String> RETIRED_ACTIVE_SCHEMAS = List.of(
            "terrain-intent.schema.json",
            "world-blueprint.schema.json",
            "generation-request.schema.json",
            "generation-job.schema.json",
            "design-audit.schema.json",
            "export-manifest.schema.json",
            "placement-journal.schema.json",
            "placement-safety-state.schema.json",
            "snapshot-cleanup-plan.schema.json",
            "structure-placements.schema.json"
    );

    @Test
    void d2aWritersAndActiveContractsAreRemoved() throws Exception {
        for (String type : RETIRED_TYPES) {
            assertFalse(Files.exists(MAIN.resolve(type)), "retired production type remains: " + type);
        }
        for (String schema : RETIRED_ACTIVE_SCHEMAS) {
            assertFalse(Files.exists(Path.of("schemas", schema)), "v1 schema remains active: " + schema);
            assertTrue(Files.isRegularFile(Path.of("src/main/resources/legacy/v1/contracts", schema)),
                    "legacy contract missing: " + schema);
        }

        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String cli = Files.readString(MAIN.resolve("cli/LandformCraftCli.java"));
        assertFalse(plugin.contains("landformcraft.r2."), "retired permission alias remains");
        assertFalse(cli.contains("--v1"), "retired CLI v1 switch remains");
    }

    @Test
    void d2bAndD3CompatibilityAssetsRemain() {
        assertTrue(Files.isRegularFile(MAIN.resolve("format/ReleaseVerifier.java")));
        assertTrue(Files.isRegularFile(MAIN.resolve("format/ReleaseVerification.java")));
        assertTrue(Files.isRegularFile(MAIN.resolve("format/DesignArtifactVerifier.java")));
        assertTrue(Files.isRegularFile(MAIN.resolve("format/DesignArtifacts.java")));
        assertTrue(Files.isRegularFile(MAIN.resolve("core/CustomAssetService.java")));
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/resources/legacy/v1/golden/rocky-coast/golden-checksums.json")));
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/resources/legacy/v1/fixtures/release1-phase6/manifest.json.b64")));
    }
}
