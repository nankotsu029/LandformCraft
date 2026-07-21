package com.github.nankotsu029.landformcraft.format.v2;

import com.fasterxml.jackson.core.JsonParseException;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationRequestV2CodecTest {
    private static final String EXISTING_ROLE_REQUEST_CHECKSUM =
            "9de924318e568aef1a31c3f43f7ae2964409b41e5bdb0d21e662e91db2a85925";
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void separatesReferenceImagesFromTypedConstraintMapsAndRoundTrips(@TempDir Path directory) throws IOException {
        GenerationRequestV2 request = codec.readGenerationRequest(validRequest(), "request-v2");
        Path target = directory.resolve("request-v2.json");

        codec.writeGenerationRequest(target, request);
        GenerationRequestV2 roundTrip = codec.readGenerationRequest(target);

        assertEquals(request, roundTrip);
        assertEquals(codec.canonicalGenerationRequest(request), Files.readString(target));
        assertEquals(codec.generationRequestChecksum(request), codec.generationRequestChecksum(roundTrip));
        assertEquals(1, request.referenceImages().size());
        assertEquals(3, request.constraintMaps().size());
        assertTrue(request.constraintMaps().stream().noneMatch(source ->
                source.getClass().isAssignableFrom(GenerationRequestV2.ReferenceImageSource.class)));
        assertEquals("constraint-source:height", request.constraintMaps().getFirst().sourceId());
        assertTrue(request.constraintMaps().getFirst().encoding() instanceof GenerationRequestV2.HeightEncoding);
    }

    @Test
    void roundTripsObliqueAndMultiViewReferenceRoles(@TempDir Path directory) throws IOException {
        String source = validRequest().replace(
                "{ \"id\": \"mood\", \"file\": \"references/mood.png\", \"role\": \"MOOD_REFERENCE\" }",
                "{ \"id\": \"mood\", \"file\": \"references/mood.png\", \"role\": \"MOOD_REFERENCE\" },\n"
                        + "                    { \"id\": \"oblique\", \"file\": \"references/oblique.png\","
                        + " \"role\": \"OBLIQUE_TERRAIN_REFERENCE\" },\n"
                        + "                    { \"id\": \"multi-view\", \"file\": \"references/multi-view.png\","
                        + " \"role\": \"MULTI_VIEW_REFERENCE\" }");

        GenerationRequestV2 request = codec.readGenerationRequest(source, "oblique-multi-view");
        Path target = directory.resolve("request-v2.json");
        codec.writeGenerationRequest(target, request);
        GenerationRequestV2 roundTrip = codec.readGenerationRequest(target);

        assertEquals(request, roundTrip);
        assertEquals(codec.generationRequestChecksum(request), codec.generationRequestChecksum(roundTrip));
        assertEquals(3, request.referenceImages().size());
        assertTrue(request.referenceImages().stream().map(GenerationRequestV2.ReferenceImageSource::role)
                .anyMatch(role -> role == GenerationRequestV2.ReferenceImageRole.OBLIQUE_TERRAIN_REFERENCE));
        assertTrue(request.referenceImages().stream().map(GenerationRequestV2.ReferenceImageSource::role)
                .anyMatch(role -> role == GenerationRequestV2.ReferenceImageRole.MULTI_VIEW_REFERENCE));
    }

    @Test
    void existingRolesKeepAStableCanonicalRequestChecksum() throws IOException {
        // Additive enum values must not disturb the canonical checksum of a request that uses only
        // pre-existing roles (V2-14-02 checksum-impact audit invariant). This golden pins that checksum;
        // it must never change when new reference roles are appended.
        GenerationRequestV2 request = codec.readGenerationRequest(validRequest(), "existing-roles");
        assertEquals(EXISTING_ROLE_REQUEST_CHECKSUM, codec.generationRequestChecksum(request));
    }

    @Test
    void rejectsFutureUnknownDuplicateAndAmbiguousDecoderContracts() {
        String valid = validRequest();

        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replace("\"requestVersion\": 2", "\"requestVersion\": 3"), "future-version"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replaceFirst("\"encodingVersion\": 1", "\"encodingVersion\": 2"),
                "future-encoding-version"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replaceFirst("\\{", "{\"unknown\":true,"), "unknown-field"));
        assertThrows(JsonParseException.class, () -> codec.readGenerationRequest(
                valid.replaceFirst("\\{", "{\"requestVersion\":2,"), "duplicate-key"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replaceFirst("\"decoderKind\": \"CATEGORICAL_RASTER\"",
                        "\"decoderKind\": \"HEIGHT_RASTER\""), "decoder-mismatch"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replace("\"globalSeed\": 827413", "\"globalSeed\": 9223372036854775808"),
                "integer-overflow"));
    }

    @Test
    void rejectsUnsafePathsInvalidNoDataLabelsDimensionsAndAspect() {
        String valid = validRequest();

        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replace("maps/land-water.png", "../land-water.png"), "traversal"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replace("maps/land-water.png", "/tmp/land-water.png"), "absolute"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replace("maps/land-water.png", "maps/land water.png"), "non-portable-character"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replace("maps/land-water.png", "https://example.invalid/map.png"), "url"));
        assertThrows(IllegalArgumentException.class, () -> codec.readGenerationRequest(
                valid.replace("{ \"mode\": \"FORBIDDEN\" }",
                        "{ \"mode\": \"SENTINEL\", \"sample\": 1 }"), "no-data-label-collision"));
        assertThrows(IllegalArgumentException.class, () -> codec.readGenerationRequest(
                valid.replaceFirst("\"sample\": 1, \"label\": \"land\"",
                        "\"sample\": 0, \"label\": \"land\""), "duplicate-label-sample"));
        assertThrows(IllegalArgumentException.class, () -> codec.readGenerationRequest(
                valid.replaceFirst("\"expectedWidth\": 4", "\"expectedWidth\": 3"), "crop-dimensions"));
        assertThrows(IllegalArgumentException.class, () -> codec.readGenerationRequest(
                valid.replaceFirst("\"width\": 4, \"length\": 4", "\"width\": 4, \"length\": 3"),
                "aspect-mismatch"));
    }

    @Test
    void rejectsImplicitOrOutOfBoundsHeightMeaning() {
        String valid = validRequest();

        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replace("\"valueMeaning\": \"BLOCKS_ABOVE_REQUEST_MIN_Y\",", ""), "missing-meaning"));
        assertThrows(IllegalArgumentException.class, () -> codec.readGenerationRequest(
                valid.replace("\"valueScaleMillionths\": 1000", "\"valueScaleMillionths\": 1000000"),
                "height-outside-bounds"));
    }

    @Test
    void rejectsDeclaredPixelAndDecodedByteBudgetOverruns() {
        String valid = validRequest();

        assertThrows(IllegalArgumentException.class, () -> codec.readGenerationRequest(
                valid.replace("\"maximumPixels\": 16000000", "\"maximumPixels\": 47"),
                "pixel-budget"));
        assertThrows(IllegalArgumentException.class, () -> codec.readGenerationRequest(
                valid.replace("\"maximumDecodedBytes\": 33554432", "\"maximumDecodedBytes\": 79"),
                "decoded-byte-budget"));
    }

    @Test
    void rejectsBudgetsAboveTrustedCapsAndYOutsideI32MillionthsRange() {
        String valid = validRequest();

        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replace("\"maximumTotalSourceBytes\": 33554432",
                        "\"maximumTotalSourceBytes\": 33554433"), "source-cap"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replace("\"maximumDecodedBytes\": 33554432",
                        "\"maximumDecodedBytes\": 33554433"), "decoded-cap"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replace("\"maximumPixels\": 16000000",
                        "\"maximumPixels\": 16000001"), "pixel-cap"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replace("\"maximumArtifactBytes\": 67108864",
                        "\"maximumArtifactBytes\": 67108865"), "artifact-cap"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replace("\"maximumResidentBytes\": 100663296",
                        "\"maximumResidentBytes\": 100663297"), "resident-cap"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replace("\"minY\": 0", "\"minY\": -2148"), "minimum-y-cap"));
        assertThrows(StructuredDataValidationException.class, () -> codec.readGenerationRequest(
                valid.replace("\"maxY\": 100", "\"maxY\": 2148"), "maximum-y-cap"));

        assertThrows(IllegalArgumentException.class, () -> new GenerationRequestV2.ConstraintMapBudget(
                16, GenerationRequestV2.MAX_TOTAL_SOURCE_BYTES + 1L,
                GenerationRequestV2.MAX_DECODED_BYTES, GenerationRequestV2.MAX_CONSTRAINT_PIXELS,
                GenerationRequestV2.MAX_ARTIFACT_BYTES, GenerationRequestV2.MAX_RESIDENT_BYTES));
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationRequestV2.Bounds(1, 1, -2_148, -2_147, -2_147));
    }

    private static String validRequest() {
        return """
                {
                  "requestVersion": 2,
                  "requestId": "manual-island",
                  "bounds": { "width": 4, "length": 4, "minY": 0, "maxY": 100, "waterLevel": 50 },
                  "prompt": "Manual constraint map preview",
                  "referenceImages": [
                    { "id": "mood", "file": "references/mood.png", "role": "MOOD_REFERENCE" }
                  ],
                  "constraintMaps": [
                    {
                      "sourceId": "constraint-source:zones",
                      "file": "maps/zones-u16.png",
                      "expectedSha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                      "expectedWidth": 4,
                      "expectedLength": 4,
                      "decoderKind": "CATEGORICAL_RASTER",
                      "coordinateMapping": {
                        "origin": "NORTH_WEST", "xAxis": "EAST", "zAxis": "SOUTH",
                        "pixelReference": "PIXEL_CENTER", "aspectMismatchPolicy": "REJECT",
                        "rotation": "DEGREES_90", "flipX": true, "flipZ": false,
                        "crop": { "x": 0, "z": 0, "width": 4, "length": 4 }
                      },
                      "encoding": {
                        "kind": "CATEGORICAL", "encodingVersion": 1, "sampleType": "U16", "channel": "GRAY",
                        "labels": [
                          { "sample": 20, "label": "upland" },
                          { "sample": 10, "label": "shore" }
                        ],
                        "noData": { "mode": "SENTINEL", "sample": 0 }
                      }
                    },
                    {
                      "sourceId": "constraint-source:land-water",
                      "file": "maps/land-water.png",
                      "expectedSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "expectedWidth": 4,
                      "expectedLength": 4,
                      "decoderKind": "CATEGORICAL_RASTER",
                      "coordinateMapping": {
                        "origin": "NORTH_WEST", "xAxis": "EAST", "zAxis": "SOUTH",
                        "pixelReference": "PIXEL_CENTER", "aspectMismatchPolicy": "REJECT",
                        "rotation": "DEGREES_0", "flipX": false, "flipZ": true,
                        "crop": { "x": 0, "z": 0, "width": 4, "length": 4 }
                      },
                      "encoding": {
                        "kind": "CATEGORICAL", "encodingVersion": 1, "sampleType": "U8", "channel": "GRAY",
                        "labels": [
                          { "sample": 1, "label": "land" },
                          { "sample": 0, "label": "water" }
                        ],
                        "noData": { "mode": "FORBIDDEN" }
                      }
                    },
                    {
                      "sourceId": "constraint-source:height",
                      "file": "maps/height-u16.png",
                      "expectedSha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                      "expectedWidth": 4,
                      "expectedLength": 4,
                      "decoderKind": "HEIGHT_RASTER",
                      "coordinateMapping": {
                        "origin": "NORTH_WEST", "xAxis": "EAST", "zAxis": "SOUTH",
                        "pixelReference": "PIXEL_CENTER", "aspectMismatchPolicy": "REJECT",
                        "rotation": "DEGREES_0", "flipX": false, "flipZ": false,
                        "crop": { "x": 0, "z": 0, "width": 4, "length": 4 }
                      },
                      "encoding": {
                        "kind": "HEIGHT", "encodingVersion": 1, "sampleType": "U16", "channel": "GRAY",
                        "valueMeaning": "BLOCKS_ABOVE_REQUEST_MIN_Y",
                        "valueScaleMillionths": 1000,
                        "valueOffsetMillionths": 0,
                        "validSampleRange": { "minimum": 0, "maximum": 50000 },
                        "noData": { "mode": "SENTINEL", "sample": 65535 }
                      }
                    }
                  ],
                  "generation": { "globalSeed": 827413, "tileSize": 32 },
                  "constraintMapBudget": {
                    "maximumMapCount": 8,
                    "maximumTotalSourceBytes": 33554432,
                    "maximumDecodedBytes": 33554432,
                    "maximumPixels": 16000000,
                    "maximumArtifactBytes": 67108864,
                    "maximumResidentBytes": 100663296
                  }
                }
                """;
    }
}
