package com.github.nankotsu029.landformcraft.core.v2.command;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-12-08 v2 request authoring store. Closes coverage-audit finding F1: v1 could create and edit a
 * request, v2 could only read one.
 */
class V2RequestStoreV2Test {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void createPublishesASchemaValidRequestWithConservativeDefaults(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);

        GenerationRequestV2 created = store.create("harbor-cove");

        assertEquals("harbor-cove", created.requestId());
        assertEquals(GenerationRequestV2.VERSION, created.requestVersion());
        assertTrue(created.referenceImages().isEmpty());
        assertTrue(created.constraintMaps().isEmpty());
        // Published through the codec, so it is already strict-schema valid and readable back.
        assertEquals(created, codec.readGenerationRequest(store.pathOf("harbor-cove")));
    }

    @Test
    void createRefusesToOverwriteAnExistingRequest(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);
        store.create("harbor-cove");

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> store.create("harbor-cove"));

        assertTrue(failure.getMessage().contains("already exists"), failure.getMessage());
    }

    @Test
    void boundsReplacesTheExtentAndClampsTheWaterLevel(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);
        store.create("harbor-cove");

        GenerationRequestV2 updated = store.bounds("harbor-cove", 64, 64, 40, 80, 200);

        assertEquals(64, updated.bounds().width());
        assertEquals(64, updated.bounds().length());
        assertEquals(40, updated.bounds().minY());
        assertEquals(80, updated.bounds().maxY());
        // 200 is above maxY; clamping keeps the request editable in any order instead of failing.
        assertEquals(80, updated.bounds().waterLevel());
        assertEquals(updated, codec.readGenerationRequest(store.pathOf("harbor-cove")));
    }

    @Test
    void selectionBoundsKeepTheStoredWaterLevel(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);
        store.create("harbor-cove");
        store.bounds("harbor-cove", 128, 128, 0, 120, 54);

        GenerationRequestV2 updated = store.boundsKeepingWaterLevel("harbor-cove", 32, 48, 0, 100);

        assertEquals(32, updated.bounds().width());
        assertEquals(48, updated.bounds().length());
        assertEquals(54, updated.bounds().waterLevel());
    }

    @Test
    void selectionBoundsClampTheStoredWaterLevelIntoTheNewRange(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);
        store.create("harbor-cove");
        store.bounds("harbor-cove", 128, 128, 0, 120, 100);

        GenerationRequestV2 updated = store.boundsKeepingWaterLevel("harbor-cove", 32, 32, 0, 60);

        assertEquals(60, updated.bounds().waterLevel());
    }

    @Test
    void boundsOutsideTheContractAreRejectedAndNothingIsWritten(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);
        GenerationRequestV2 created = store.create("harbor-cove");

        assertThrows(IllegalArgumentException.class,
                () -> store.bounds("harbor-cove", 2_000, 64, 0, 100, 50));

        assertEquals(created, codec.readGenerationRequest(store.pathOf("harbor-cove")));
    }

    @Test
    void constraintMapDeclaresTheCanonicalCategoricalLandWaterSource(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);
        store.create("harbor-cove");
        store.bounds("harbor-cove", 64, 64, 32, 72, 50);

        GenerationRequestV2 updated = store.constraintMap("harbor-cove", "coast-mask",
                "maps/harbor-cove-64-land-water-u8.png", "d".repeat(64), 64, 64);

        assertEquals(1, updated.constraintMaps().size());
        GenerationRequestV2.ConstraintMapSource source = updated.constraintMaps().getFirst();
        assertEquals("constraint-source:coast-mask", source.sourceId());
        assertEquals(GenerationRequestV2.DecoderKind.CATEGORICAL_RASTER, source.decoderKind());
        assertEquals(new GenerationRequestV2.PixelCrop(0, 0, 64, 64), source.coordinateMapping().crop());
        GenerationRequestV2.CategoricalEncoding encoding =
                (GenerationRequestV2.CategoricalEncoding) source.encoding();
        assertEquals(List.of(
                        new GenerationRequestV2.LabelMapping(0, "water"),
                        new GenerationRequestV2.LabelMapping(1, "land")),
                encoding.labels());
        assertEquals(GenerationRequestV2.SampleType.U8, encoding.sampleType());
        assertEquals(updated, codec.readGenerationRequest(store.pathOf("harbor-cove")));
    }

    @Test
    void constraintMapReplacesThepreviousDeclaration(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);
        store.create("harbor-cove");
        store.constraintMap("harbor-cove", "first", "maps/a.png", "a".repeat(64), 128, 128);

        GenerationRequestV2 updated = store.constraintMap(
                "harbor-cove", "second", "maps/b.png", "b".repeat(64), 128, 128);

        // The export requires exactly one source, so setting one replaces rather than appends.
        assertEquals(1, updated.constraintMaps().size());
        assertEquals("constraint-source:second", updated.constraintMaps().getFirst().sourceId());
    }

    @Test
    void constraintMapRejectsUnsafePathsBadDigestsAndAspectMismatches(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);
        store.create("harbor-cove");
        store.bounds("harbor-cove", 64, 64, 32, 72, 50);

        assertThrows(IllegalArgumentException.class, () -> store.constraintMap(
                "harbor-cove", "coast-mask", "../outside.png", "d".repeat(64), 64, 64));
        assertThrows(IllegalArgumentException.class, () -> store.constraintMap(
                "harbor-cove", "coast-mask", "maps/coast.png", "not-a-digest", 64, 64));
        // 64x128 cannot map onto 64x64 bounds.
        assertThrows(IllegalArgumentException.class, () -> store.constraintMap(
                "harbor-cove", "coast-mask", "maps/coast.png", "d".repeat(64), 64, 128));

        assertTrue(codec.readGenerationRequest(store.pathOf("harbor-cove")).constraintMaps().isEmpty());
    }

    @Test
    void promptIsStoredAndSecretLookingTextIsRefused(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);
        store.create("harbor-cove");

        GenerationRequestV2 updated = store.prompt("harbor-cove", "A sheltered cove with a stone breakwater.");
        assertEquals("A sheltered cove with a stone breakwater.", updated.prompt());

        for (String secret : List.of(
                "Authorization: Bearer abc",
                "api_key=0123456789",
                "APIKEY=zzz",
                "use sk-abcdefghijklmnop0123 for the run")) {
            IllegalArgumentException failure =
                    assertThrows(IllegalArgumentException.class, () -> store.prompt("harbor-cove", secret));
            assertTrue(failure.getMessage().contains("resembles a secret"), failure.getMessage());
        }
        // The refused prompts never reached disk.
        assertEquals("A sheltered cove with a stone breakwater.",
                codec.readGenerationRequest(store.pathOf("harbor-cove")).prompt());
    }

    @Test
    void listIsDeterministicAndIgnoresUnrelatedFiles(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);
        store.create("zulu-bay");
        store.create("alpha-cove");
        store.create("mike-point");
        Files.writeString(root.resolve("notes.txt"), "not a request", StandardCharsets.UTF_8);

        assertEquals(List.of("alpha-cove", "mike-point", "zulu-bay"), store.list());
        assertEquals(store.list(), store.list());
    }

    @Test
    void listOnAMissingRootIsEmptyRatherThanAFailure(@TempDir Path root) throws IOException {
        assertEquals(List.of(), new V2RequestStoreV2(root.resolve("absent")).list());
    }

    @Test
    void authoringIsDeterministic(@TempDir Path first, @TempDir Path second) throws IOException {
        V2RequestStoreV2 left = new V2RequestStoreV2(first);
        V2RequestStoreV2 right = new V2RequestStoreV2(second);

        left.create("harbor-cove");
        left.bounds("harbor-cove", 64, 64, 40, 80, 54);
        GenerationRequestV2 leftValue = left.prompt("harbor-cove", "A sheltered cove.");

        right.create("harbor-cove");
        right.bounds("harbor-cove", 64, 64, 40, 80, 54);
        GenerationRequestV2 rightValue = right.prompt("harbor-cove", "A sheltered cove.");

        assertEquals(codec.generationRequestChecksum(leftValue),
                codec.generationRequestChecksum(rightValue));
        assertArrayEqualsBytes(left.pathOf("harbor-cove"), right.pathOf("harbor-cove"));
    }

    @Test
    void adifferentPromptChangesTheChecksum(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);
        GenerationRequestV2 created = store.create("harbor-cove");
        GenerationRequestV2 updated = store.prompt("harbor-cove", "A sheltered cove.");

        assertNotEquals(codec.generationRequestChecksum(created),
                codec.generationRequestChecksum(updated));
    }

    @Test
    void requestIdsThatAreNotPortableSlugsAreRejected(@TempDir Path root) {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);

        for (String unsafe : List.of("../escape", "Harbor-Cove", "harbor cove", "", "a/b", "harbor\\cove")) {
            IllegalArgumentException failure =
                    assertThrows(IllegalArgumentException.class, () -> store.create(unsafe));
            assertTrue(failure.getMessage().contains("portable slug"), failure.getMessage());
        }
    }

    @Test
    void traversalIsRejectedBeforeAnyFileIsTouched(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root.resolve("requests"));
        Files.createDirectories(root.resolve("requests"));

        assertThrows(IllegalArgumentException.class, () -> store.create("../outside"));
        assertThrows(IllegalArgumentException.class, () -> store.pathOf("../outside"));

        try (var paths = Files.list(root)) {
            assertEquals(List.of("requests"),
                    paths.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void aSymbolicLinkInThePlaceOfARequestIsRefused(@TempDir Path root) throws IOException {
        Path requests = Files.createDirectories(root.resolve("requests"));
        Path outside = Files.writeString(root.resolve("outside.json"), "{}", StandardCharsets.UTF_8);
        try {
            Files.createSymbolicLink(requests.resolve("linked" + V2RequestStoreV2.SUFFIX), outside);
        } catch (UnsupportedOperationException | IOException unsupported) {
            return; // The filesystem does not support symbolic links; the guard is still compiled in.
        }
        V2RequestStoreV2 store = new V2RequestStoreV2(requests);

        assertThrows(IllegalArgumentException.class, () -> store.create("linked"));
        assertEquals("{}", Files.readString(outside, StandardCharsets.UTF_8));
    }

    @Test
    void aRequestFiledUnderTheWrongNameIsReportedRatherThanAccepted(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);
        store.create("harbor-cove");
        Files.move(store.pathOf("harbor-cove"), root.resolve("other-cove" + V2RequestStoreV2.SUFFIX));

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> store.read("other-cove"));

        assertTrue(failure.getMessage().contains("filed as"), failure.getMessage());
    }

    @Test
    void editingAMissingRequestFails(@TempDir Path root) {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> store.prompt("absent", "text")).getMessage().contains("does not exist"));
    }

    @Test
    void aCorruptedStoredRequestIsRejectedFailClosed(@TempDir Path root) throws IOException {
        V2RequestStoreV2 store = new V2RequestStoreV2(root);
        store.create("harbor-cove");
        Files.writeString(store.pathOf("harbor-cove"), "{\"requestVersion\":2}", StandardCharsets.UTF_8);

        assertThrows(StructuredDataValidationException.class, () -> store.read("harbor-cove"));
    }

    private static void assertArrayEqualsBytes(Path left, Path right) throws IOException {
        assertEquals(Files.readString(left, StandardCharsets.UTF_8),
                Files.readString(right, StandardCharsets.UTF_8));
    }
}
