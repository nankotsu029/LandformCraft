package com.github.nankotsu029.landformcraft.core.v2.migration;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignArtifactPublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.migration.LegacyMigrationBundleV2;
import com.github.nankotsu029.landformcraft.format.v2.migration.LegacyMigrationBundleVerifierV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationReportV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationSourceKindV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationStatusV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-12-04: explicit v1 → v2 migration, and the ADR 0035 D9 quality conditions that gate the v1
 * removal in V2-12-06.
 */
class LegacyMigrationApplicationServiceV2Test {
    private static final Path V1_INTENT = Path.of(
            "src/main/resources/legacy/v1/fixtures/mountain-stream/terrain-intent.json");

    @Test
    void dryRunReportsEveryUnmappableV1ElementAndPublishesNothing(@TempDir Path root) throws Exception {
        Path output = root.resolve("out");
        Files.createDirectories(output);

        LegacyMigrationResultV2 result = migrate(new LegacyMigrationRequestV2(
                LegacyMigrationSourceKindV2.V1_TERRAIN_INTENT, V1_INTENT, Optional.of(output),
                "mountain-stream", true, true));

        LegacyMigrationReportV2 report = result.report();
        assertEquals(LegacyMigrationStatusV2.DRY_RUN, report.status());
        assertTrue(result.bundle().isEmpty());
        assertTrue(report.lossy());
        assertEquals(List.of("schemaVersion", "theme"), report.mappedFields());
        // topology, landRatio, relief, coastline, water, seaSides + 6 zones + 6 structures.
        assertEquals(18, report.unmappedElements().size(), report.unmappedElements().toString());
        assertTrue(report.unmappedElements().stream()
                .anyMatch(element -> element.elementId().equals("zone:mountain-source")));
        assertTrue(report.unmappedElements().stream()
                .anyMatch(element -> element.elementId().equals("intent:coastline")));
        assertEquals(List.of(), listFiles(output), "a dry run must not write into the output root");
    }

    @Test
    void strictMigrationRefusesToDropWhatV2CannotExpress(@TempDir Path root) {
        IOException failure = assertThrows(IOException.class, () -> migrate(new LegacyMigrationRequestV2(
                LegacyMigrationSourceKindV2.V1_TERRAIN_INTENT, V1_INTENT, Optional.of(root.resolve("out")),
                "strict-run", false, false)));

        assertTrue(failure.getMessage().contains("v2 cannot"), failure.getMessage());
        assertFalse(Files.exists(root.resolve("out").resolve("strict-run")));
    }

    @Test
    void acceptedLossyMigrationPublishesAVerifiableRelease2DesignPackage(@TempDir Path root) throws Exception {
        Path output = root.resolve("out");

        LegacyMigrationResultV2 result = migrate(apply(V1_INTENT, output, "mountain-stream"));

        LegacyMigrationBundleV2 bundle = result.bundle().orElseThrow();
        assertEquals(LegacyMigrationStatusV2.PUBLISHED, result.report().status());
        assertEquals(output.resolve("mountain-stream").toAbsolutePath().normalize(), bundle.root());

        // The bundle re-verifies from disk through the Release 2 design strict verifier (D9-10).
        LegacyMigrationBundleV2 reread = new LegacyMigrationBundleVerifierV2().verify(bundle.root());
        assertEquals(bundle.report(), reread.report());
        assertEquals(DesignPathKindV2.IMPORT, reread.design().audit().pathKind());
        assertEquals(TerrainIntentV2.ProvenanceSource.UPGRADED_V1,
                reread.design().intent().provenance().source());
        assertEquals(TerrainIntentV2.ConfirmationState.UNCONFIRMED,
                reread.design().intent().provenance().confirmationState());

        // Nothing was invented: the migrated intent carries the theme and no derived geometry.
        TerrainIntentV2 intent = reread.design().intent();
        assertEquals(List.of(), intent.features());
        assertEquals(List.of(), intent.relations());
        assertEquals(List.of(), intent.constraints());
        assertEquals(List.of(), intent.structures());
        assertTrue(intent.theme().startsWith("森林に覆われた"), intent.theme());

        assertEquals(List.of(
                        "checksums.sha256",
                        "designs/" + reread.report().targetRequestId() + "/"
                                + reread.report().targetJobId() + "/audit-v2.json",
                        "designs/" + reread.report().targetRequestId() + "/"
                                + reread.report().targetJobId() + "/checksums.sha256",
                        "designs/" + reread.report().targetRequestId() + "/"
                                + reread.report().targetJobId() + "/terrain-intent-v2.json",
                        "migration-report-v2.json"),
                listFiles(bundle.root()));
    }

    @Test
    void migratingTheExampleSourceReproducesTheSealedExampleArtifacts(@TempDir Path root) throws Exception {
        LegacyMigrationResultV2 result = migrate(apply(V1_INTENT, root.resolve("out"), "example-migration"));
        LegacyMigrationBundleV2 bundle = result.bundle().orElseThrow();

        assertEquals(
                Files.readString(Path.of("examples/v2/migration/migration-report-v2.json"),
                        StandardCharsets.UTF_8),
                Files.readString(bundle.root().resolve(LegacyMigrationBundleVerifierV2.REPORT_FILE),
                        StandardCharsets.UTF_8),
                "the sealed migration report example has drifted from what the tool produces");
        assertEquals(
                Files.readString(Path.of("examples/v2/migration/mountain-stream.terrain-intent-v2.json"),
                        StandardCharsets.UTF_8),
                Files.readString(bundle.designPackage().resolve(DesignArtifactPublisherV2.INTENT_FILE),
                        StandardCharsets.UTF_8),
                "the sealed migrated intent example has drifted from what the tool produces");
    }

    @Test
    void theSameSourceAlwaysProducesTheSameBundle(@TempDir Path root) throws Exception {
        LegacyMigrationResultV2 first = migrate(apply(V1_INTENT, root.resolve("first"), "repeat"));
        LegacyMigrationResultV2 second = migrate(apply(V1_INTENT, root.resolve("second"), "repeat"));

        assertEquals(first.report(), second.report());
        assertEquals(checksums(first.bundle().orElseThrow().root()),
                checksums(second.bundle().orElseThrow().root()));
    }

    @Test
    void migrationNeverWritesToOrOverwritesAnything(@TempDir Path root) throws Exception {
        Path source = root.resolve("source").resolve("terrain-intent.json");
        Files.createDirectories(source.getParent());
        Files.copy(V1_INTENT, source);
        String before = Sha256.file(source);

        migrate(apply(source, root.resolve("out"), "once"));

        assertEquals(before, Sha256.file(source), "the v1 source must not be modified");
        assertEquals(List.of("terrain-intent.json"), listFiles(source.getParent()));
        IOException overwrite = assertThrows(IOException.class,
                () -> migrate(apply(source, root.resolve("out"), "once")));
        assertTrue(overwrite.getMessage().contains("never overwritten"), overwrite.getMessage());
    }

    @Test
    void unknownVersionsUndefinedFieldsAndCorruptionFailClosed(@TempDir Path root) throws Exception {
        String original = Files.readString(V1_INTENT, StandardCharsets.UTF_8);

        Path futureVersion = write(root, "future.json", original.replace("\"schemaVersion\": 1",
                "\"schemaVersion\": 2"));
        Path unknownField = write(root, "unknown.json", original.replaceFirst("\\{",
                "{\n  \"tectonics\": \"none\","));
        Path corrupted = write(root, "corrupted.json", original.substring(0, original.length() / 2));

        for (Path source : List.of(futureVersion, unknownField, corrupted)) {
            assertThrows(Exception.class,
                    () -> migrate(apply(source, root.resolve("out-" + source.getFileName()), "rejected")),
                    "expected a fail-closed rejection for " + source.getFileName());
            assertFalse(Files.exists(root.resolve("out-" + source.getFileName()).resolve("rejected")));
        }
    }

    @Test
    void unsafeAndMislabelledSourcesAreRejected(@TempDir Path root) throws Exception {
        Path missing = root.resolve("absent.json");
        assertThrows(IOException.class, () -> migrate(apply(missing, root.resolve("a"), "missing")));

        // A v1 intent file is not a design package, and the declared kind is never second-guessed.
        assertThrows(IOException.class, () -> migrate(new LegacyMigrationRequestV2(
                LegacyMigrationSourceKindV2.V1_DESIGN_PACKAGE, V1_INTENT, Optional.of(root.resolve("b")),
                "mislabelled", false, true)));
    }

    @Test
    void v1DesignPackagesMigrateAndKeepTheirRequestIdentity(@TempDir Path root) throws Exception {
        // The canonical v1 layout is <designs-root>/<requestId>/<jobId>; the example ships the job
        // directory alone, so lay it out the way the strict v1 verifier requires.
        Path design = root.resolve("designs").resolve("azure-coast-001")
                .resolve("38834796-183d-45ff-a567-6ee80cb9b243");
        Files.createDirectories(design);
        Path example = Path.of("src/main/resources/legacy/v1/fixtures/azure-coast/results/"
                + "38834796-183d-45ff-a567-6ee80cb9b243");
        try (var stream = Files.list(example)) {
            for (Path file : stream.toList()) {
                byte[] archived = Files.readAllBytes(file);
                int length = archived.length > 0 && archived[archived.length - 1] == '\n'
                        ? archived.length - 1 : archived.length;
                Files.write(design.resolve(file.getFileName().toString()),
                        java.util.Arrays.copyOf(archived, length));
            }
        }

        LegacyMigrationResultV2 result = migrate(new LegacyMigrationRequestV2(
                LegacyMigrationSourceKindV2.V1_DESIGN_PACKAGE, design, Optional.of(root.resolve("out")),
                "azure-coast", false, true));

        LegacyMigrationReportV2 report = result.report();
        assertEquals(LegacyMigrationSourceKindV2.V1_DESIGN_PACKAGE, report.sourceKind());
        assertEquals("azure-coast-001", report.targetRequestId());
        assertEquals(Sha256.file(design.resolve("terrain-intent.json")), report.sourceDigest());
        assertEquals("", report.sourceCanonicalChecksum(),
                "a v1 design package defines no canonical whole-package checksum");
        assertTrue(Files.isRegularFile(result.bundle().orElseThrow().designPackage()
                .resolve(DesignArtifactPublisherV2.INTENT_FILE)));
    }

    @Test
    void release1MigratesItsIntentAndReportsTheBlockArtifactsAsNonConvertible(@TempDir Path root)
            throws Exception {
        Path release = release1Fixture(root.resolve("fixture"));

        LegacyMigrationResultV2 result = migrate(new LegacyMigrationRequestV2(
                LegacyMigrationSourceKindV2.V1_RELEASE, release, Optional.of(root.resolve("out")),
                "release-1", false, true));

        LegacyMigrationReportV2 report = result.report();
        assertEquals(1, report.sourceSchemaVersion(), "Release format 1");
        assertEquals(Sha256.file(release.resolve("checksums.sha256")), report.sourceCanonicalChecksum());
        assertNotEquals("", report.sourceCanonicalChecksum());
        for (String element : List.of("release:tiles", "release:structures", "release:assets",
                "release:previews", "release:world-blueprint")) {
            assertTrue(report.unmappedElements().stream()
                            .anyMatch(entry -> entry.elementId().equals(element)),
                    "missing non-convertible element " + element);
        }
        assertTrue(report.unmappedElements().stream()
                        .anyMatch(entry -> entry.reason().contains("lfc v2 export")),
                "the report must say how the v2 block artifacts are produced instead");
    }

    @Test
    void aTamperedPublishedBundleIsRejectedOnReRead(@TempDir Path root) throws Exception {
        LegacyMigrationResultV2 result = migrate(apply(V1_INTENT, root.resolve("out"), "tampered"));
        Path bundle = result.bundle().orElseThrow().root();

        Files.writeString(bundle.resolve(LegacyMigrationBundleVerifierV2.REPORT_FILE), "{}",
                StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> new LegacyMigrationBundleVerifierV2().verify(bundle));
    }

    private static LegacyMigrationRequestV2 apply(Path source, Path output, String migrationId) {
        return new LegacyMigrationRequestV2(LegacyMigrationSourceKindV2.V1_TERRAIN_INTENT, source,
                Optional.of(output), migrationId, false, true);
    }

    private static LegacyMigrationResultV2 migrate(LegacyMigrationRequestV2 request) throws IOException {
        try (GenerationExecutors executors = GenerationExecutors.create(2, 2, 8)) {
            return new LegacyMigrationApplicationServiceV2(executors).migrateNow(request);
        }
    }

    private static Path write(Path root, String name, String body) throws IOException {
        Path path = root.resolve(name);
        Files.createDirectories(root);
        Files.writeString(path, body, StandardCharsets.UTF_8);
        return path;
    }

    private static List<String> listFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    private static Map<String, String> checksums(Path root) throws IOException {
        Map<String, String> values = new TreeMap<>();
        for (String relative : listFiles(root)) {
            values.put(relative, Sha256.file(root.resolve(relative)));
        }
        return values;
    }

    /** Byte-exact immutable Release format 1 archive used by the migration-only reader. */
    private static Path release1Fixture(Path directory) throws IOException {
        List<String> files = List.of(
                "README.txt",
                "assets/required-assets.json",
                "assets/schematics/fence-v1.schem",
                "assets/schematics/path-v1.schem",
                "assets/schematics/stone-ruin-v1.schem",
                "checksums.sha256",
                "manifest.json",
                "previews/features.png",
                "previews/height.png",
                "previews/materials.png",
                "previews/overview.png",
                "previews/slope.png",
                "previews/structures.png",
                "previews/validation.png",
                "previews/water.png",
                "request.yml",
                "schematics/tile-00-00.schem",
                "schematics/tile-00-01.schem",
                "schematics/tile-00-02.schem",
                "schematics/tile-01-00.schem",
                "schematics/tile-01-01.schem",
                "schematics/tile-01-02.schem",
                "schematics/tile-02-00.schem",
                "schematics/tile-02-01.schem",
                "schematics/tile-02-02.schem",
                "structures.json",
                "terrain-intent.json",
                "validation.json",
                "world-blueprint.json");
        for (String relative : files) {
            String resource = "legacy/v1/fixtures/release1-phase6/" + relative + ".b64";
            try (InputStream input = LegacyMigrationApplicationServiceV2Test.class
                    .getClassLoader().getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IOException("missing immutable Release 1 fixture: " + resource);
                }
                Path target = directory.resolve(relative);
                Files.createDirectories(target.getParent());
                Files.write(target, Base64.getMimeDecoder().decode(input.readAllBytes()));
            }
        }
        // This archived release predates the final frozen placement boolean. Replace only that
        // structured artifact with the immutable final v1 fixture and rebind its file checksum.
        Path structures = directory.resolve("structures.json");
        Files.copy(Path.of("src/main/resources/legacy/v1/fixtures/structure-placements.json"),
                structures, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Path checksums = directory.resolve("checksums.sha256");
        Files.writeString(checksums, Files.readString(checksums).replaceFirst(
                "(?m)^[0-9a-f]{64}  structures\\.json$",
                Sha256.file(structures) + "  structures.json"), StandardCharsets.UTF_8);
        return directory;
    }
}
