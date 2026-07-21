package com.github.nankotsu029.landformcraft.compatibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.migration.LegacyMigrationApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.migration.LegacyMigrationRequestV2;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationSourceKindV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Immutable evidence replacing the executable v1 generator harness (ADR 0035 D3-K1). */
class LegacyV1GoldenArchiveTest {
    private static final String ROOT = "legacy/v1/golden/rocky-coast/";

    @Test
    void archivedCorpusIsImmutableAndStrictReadable(@TempDir Path directory) throws Exception {
        Path intent = copyResource(ROOT + "terrain-intent.json", directory.resolve("terrain-intent.json"));
        Path checksums = copyResource(ROOT + "golden-checksums.json", directory.resolve("checksums.json"));
        var archive = new ObjectMapper().readTree(checksums.toFile());

        assertEquals("3.0.0-phase6", archive.path("generatorVersion").asText());
        assertEquals(archive.path("sourceSha256").asText(), Sha256.file(intent));
        assertEquals("9d482b33bcf721c9f8b065ef5050bb926cfe3b251ee857c5bb42dba2c72f1708",
                archive.path("canonicalIntentSha256").asText());
        assertEquals(1, new LandformDataCodec().readTerrainIntent(intent).schemaVersion());
    }

    @Test
    void archivedScenarioMigratesThroughTheV2EquivalencePath(@TempDir Path directory) throws Exception {
        Path intent = copyResource(ROOT + "terrain-intent.json", directory.resolve("terrain-intent.json"));
        try (GenerationExecutors executors = GenerationExecutors.create(2, 2, 8)) {
            var result = new LegacyMigrationApplicationServiceV2(executors).migrateNow(
                    new LegacyMigrationRequestV2(LegacyMigrationSourceKindV2.V1_TERRAIN_INTENT,
                            intent, Optional.of(directory.resolve("out")), "golden-equivalence", false, true));
            assertEquals("PUBLISHED", result.report().status().name());
            assertTrue(result.bundle().isPresent());
            assertTrue(Files.isRegularFile(
                    result.bundle().orElseThrow().root().resolve("migration-report-v2.json")));
        }
    }

    private static Path copyResource(String name, Path target) throws Exception {
        try (InputStream input = LegacyV1GoldenArchiveTest.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new IllegalStateException("missing immutable v1 resource: " + name);
            Files.copy(input, target);
        }
        return target;
    }
}
