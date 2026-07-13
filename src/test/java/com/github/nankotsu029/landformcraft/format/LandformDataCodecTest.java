package com.github.nankotsu029.landformcraft.format;

import com.fasterxml.jackson.core.JsonParseException;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.ExportManifest;
import com.github.nankotsu029.landformcraft.model.ManifestAnchor;
import com.github.nankotsu029.landformcraft.model.ManifestTile;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LandformDataCodecTest {
    private final LandformDataCodec codec = new LandformDataCodec();

    @Test
    void validatesExamplesAndRoundTripsIntent(@TempDir Path directory) throws IOException {
        GenerationRequest request = codec.readGenerationRequest(Path.of("examples/rocky-coast/request.yml"));
        TerrainIntent intent = codec.readTerrainIntent(Path.of("examples/rocky-coast/terrain-intent.json"));
        Path copy = directory.resolve("intent.json");

        codec.writeJson(copy, intent);

        assertEquals("rocky-coast-001", request.requestId());
        assertEquals(intent, codec.readTerrainIntent(copy));
    }

    @Test
    void rejectsUnknownRequestField(@TempDir Path directory) throws IOException {
        String valid = Files.readString(Path.of("examples/rocky-coast/request.yml"));
        Path invalid = directory.resolve("invalid.yml");
        Files.writeString(invalid, valid + "unknown-field: true\n");

        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(invalid));
    }

    @Test
    void rejectsDuplicateJsonKeys(@TempDir Path directory) throws IOException {
        String valid = Files.readString(Path.of("examples/rocky-coast/terrain-intent.json"));
        Path invalid = directory.resolve("duplicate.json");
        Files.writeString(invalid, valid.replaceFirst("\\{", "{\\n  \\\"schemaVersion\\\": 1,"));

        assertThrows(JsonParseException.class, () -> codec.readTerrainIntent(invalid));
    }

    @Test
    void rejectsUnsupportedSchemaVersion(@TempDir Path directory) throws IOException {
        String valid = Files.readString(Path.of("examples/rocky-coast/terrain-intent.json"));
        Path incompatible = directory.resolve("future-version.json");
        Files.writeString(incompatible, valid.replaceFirst("\\\"schemaVersion\\\": 1", "\\\"schemaVersion\\\": 2"));

        assertThrows(StructuredDataValidationException.class, () -> codec.readTerrainIntent(incompatible));
    }

    @Test
    void validatesAndRoundTripsManifest(@TempDir Path directory) throws IOException {
        String checksum = "0".repeat(64);
        ExportManifest manifest = new ExportManifest(
                1,
                "1.0.0-phase1",
                "1.21.11",
                "manifest-test",
                64,
                64,
                -32,
                160,
                128,
                1,
                1,
                ManifestAnchor.MINIMUM_CORNER,
                827413L,
                List.of(new ManifestTile(
                        "tile-00-00", 0, 0, 0, -32, 0, 64, 64, -32, 160,
                        "schematics/tile-00-00.schem", checksum
                ))
        );
        Path path = directory.resolve("manifest.json");

        codec.writeExportManifest(path, manifest);

        assertEquals(manifest, codec.readExportManifest(path));
    }
}
