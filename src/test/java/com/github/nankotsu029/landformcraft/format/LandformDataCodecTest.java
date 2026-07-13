package com.github.nankotsu029.landformcraft.format;

import com.fasterxml.jackson.core.JsonParseException;
import com.github.nankotsu029.landformcraft.model.GenerationRequest;
import com.github.nankotsu029.landformcraft.model.ExportManifest;
import com.github.nankotsu029.landformcraft.model.ManifestAnchor;
import com.github.nankotsu029.landformcraft.model.ManifestAirPolicy;
import com.github.nankotsu029.landformcraft.model.ManifestTile;
import com.github.nankotsu029.landformcraft.model.ManifestTileStatus;
import com.github.nankotsu029.landformcraft.model.PlacementJournal;
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
                        "schematics/tile-00-00.schem", checksum, checksum, 64L * 64L * 193L,
                        ManifestAirPolicy.INCLUDED, ManifestTileStatus.READY
                ))
        );
        Path path = directory.resolve("manifest.json");

        codec.writeExportManifest(path, manifest);

        assertEquals(manifest, codec.readExportManifest(path));
        assertEquals("release-example", codec.readExportManifest(Path.of("examples/release-manifest.json")).requestId());
    }

    @Test
    void validatesAndRoundTripsPlacementJournal(@TempDir Path directory) throws IOException {
        PlacementJournal journal = codec.readPlacementJournal(Path.of("examples/placement-journal.json"));
        Path copy = directory.resolve("placement.json");

        codec.writePlacementJournal(copy, journal);

        assertEquals(journal, codec.readPlacementJournal(copy));
    }

    @Test
    void rejectsUnknownPlacementJournalField(@TempDir Path directory) throws IOException {
        String valid = Files.readString(Path.of("examples/placement-journal.json"));
        Path invalid = directory.resolve("invalid-placement.json");
        Files.writeString(invalid, valid.replaceFirst("\\{", "{\n  \"unexpected\": true,"));

        assertThrows(StructuredDataValidationException.class, () -> codec.readPlacementJournal(invalid));
    }

    @Test
    void safelyReadsLegacyPhaseThreeJournalWithoutWeakeningStrictValidation(@TempDir Path directory)
            throws IOException {
        String legacy = Files.readString(Path.of("examples/placement-journal.json"))
                .replaceFirst("(?s)\\s*\"actor\"\\s*:\\s*\\{.*?\\},", "")
                .replaceFirst("(?s)\\s*\"confirmationActor\"\\s*:\\s*\\{.*?\\},", "")
                .replaceFirst("(?m)^\\s*\"confirmationCreatedAt\".*\\R", "")
                .replaceFirst("(?m)^\\s*\"reservedBytes\".*\\R", "")
                .replaceFirst("(?m)^\\s*\"snapshotBytesUsed\".*\\R", "");
        Path path = directory.resolve("legacy-placement.json");
        Files.writeString(path, legacy);

        assertThrows(StructuredDataValidationException.class, () -> codec.readPlacementJournal(path));
        PlacementJournal migrated = codec.readPlacementJournalCompatible(path);
        assertEquals("SYSTEM:LEGACY", migrated.plan().actor().canonical());
        assertEquals(migrated.plan().actor(), migrated.confirmationActor());
        assertEquals(0L, migrated.reservedBytes());
        assertEquals(0L, migrated.snapshotBytesUsed());
    }

    @Test
    void validatesAndRoundTripsPhaseFourAuditAndJob(@TempDir Path directory) throws IOException {
        var audit = codec.readDesignAudit(Path.of("examples/design-audit.json"));
        var job = codec.readGenerationJob(Path.of("examples/generation-job.json"));
        Path auditCopy = directory.resolve("audit.json");
        Path jobCopy = directory.resolve("job.json");

        codec.writeDesignAudit(auditCopy, audit);
        codec.writeGenerationJob(jobCopy, job);

        assertEquals(audit, codec.readDesignAudit(auditCopy));
        assertEquals(job, codec.readGenerationJob(jobCopy));
    }

    @Test
    void validatesAndRoundTripsImageEvidence(@TempDir Path directory) throws IOException {
        var evidence = codec.readImageInputEvidence(Path.of("examples/image-input-evidence.json"));
        Path copy = directory.resolve("image-evidence.json");

        codec.writeImageInputEvidence(copy, evidence);

        assertEquals(evidence, codec.readImageInputEvidence(copy));
        assertEquals("image-example", evidence.requestId());
        assertEquals(1, evidence.images().size());
    }

    @Test
    void rejectsReferenceImageWithoutRoleAndDangerousImagePath(@TempDir Path directory) throws IOException {
        String valid = Files.readString(Path.of("examples/rocky-coast/request.yml"));
        Path roleless = directory.resolve("roleless.yml");
        Files.writeString(roleless, valid.replace(
                "images: []", "images:\n  - file: images/map.png"
        ));
        Path dangerous = directory.resolve("dangerous.yml");
        Files.writeString(dangerous, valid.replace(
                "images: []", "images:\n  - file: ../map.png\n    role: TOP_DOWN_SKETCH"
        ));

        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(roleless));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(dangerous));
    }

    @Test
    void rejectsUnknownImageRoleAndMoreThanSixteenImages(@TempDir Path directory) throws IOException {
        String valid = Files.readString(Path.of("examples/rocky-coast/request.yml"));
        Path unknownRole = directory.resolve("unknown-role.yml");
        Files.writeString(unknownRole, valid.replace(
                "images: []", "images:\n  - file: images/map.png\n    role: UNTRUSTED_ROLE"
        ));
        StringBuilder images = new StringBuilder("images:\n");
        for (int index = 0; index < 17; index++) {
            images.append("  - file: images/map-").append(index)
                    .append(".png\n    role: MOOD_REFERENCE\n");
        }
        Path excessive = directory.resolve("excessive-images.yml");
        Files.writeString(excessive, valid.replace("images: []", images.toString().stripTrailing()));

        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(unknownRole));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(excessive));
    }
}
