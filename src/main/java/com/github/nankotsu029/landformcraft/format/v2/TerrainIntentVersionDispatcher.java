package com.github.nankotsu029.landformcraft.format.v2;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.model.v2.CanonicalTerrainIntentV2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Exact v1/v2 discriminator. Unknown, future, ambiguous, and missing versions are rejected. */
public final class TerrainIntentVersionDispatcher {
    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private final LandformDataCodec v1 = new LandformDataCodec();
    private final LandformV2DataCodec v2 = new LandformV2DataCodec();
    private final CanonicalTerrainIntentCodecV2 canonicalV2 = new CanonicalTerrainIntentCodecV2();

    public VersionedTerrainIntent read(Path path) throws IOException {
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(Math.toIntExact(LandformDataCodec.MAX_DOCUMENT_BYTES + 1));
        }
        if (bytes.length > LandformDataCodec.MAX_DOCUMENT_BYTES) throw new IOException("document exceeds limit: " + path);
        return read(new String(bytes, StandardCharsets.UTF_8), path.toString());
    }

    public VersionedTerrainIntent read(String input, String documentName) throws IOException {
        JsonNode root = mapper.readTree(input);
        if (root == null || !root.isObject()) throw new IOException("terrain intent must be an object: " + documentName);
        boolean hasV1 = root.has("schemaVersion");
        boolean hasV2 = root.has("intentVersion");
        if (hasV1 == hasV2) throw new IOException("terrain intent has an ambiguous or missing version: " + documentName);
        if (hasV1) {
            if (!root.path("schemaVersion").isInt() || root.path("schemaVersion").intValue() != 1) throw new IOException("unsupported v1 terrain intent version");
            return new VersionedTerrainIntent.V1(v1.readTerrainIntent(input, documentName));
        }
        if (!root.path("intentVersion").isInt() || root.path("intentVersion").intValue() != 2) throw new IOException("unsupported v2 terrain intent version");
        if (!root.has("featureProjection")) {
            throw new IOException("historic v2 terrain intent requires explicit LEGACY_V2 reader selection");
        }
        if (!"CANONICAL_V2".equals(root.path("featureProjection").textValue())) {
            throw new IOException("unsupported or mismatched v2 feature projection");
        }
        return new VersionedTerrainIntent.CanonicalV2(canonicalV2.read(input, documentName));
    }

    public VersionedTerrainIntent.V2 readLegacy(
            Path path,
            CanonicalTerrainIntentV2.FeatureProjection projection
    ) throws IOException {
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(Math.toIntExact(LandformDataCodec.MAX_DOCUMENT_BYTES + 1));
        }
        if (bytes.length > LandformDataCodec.MAX_DOCUMENT_BYTES) {
            throw new IOException("document exceeds limit: " + path);
        }
        return readLegacy(new String(bytes, StandardCharsets.UTF_8), path.toString(), projection);
    }

    public VersionedTerrainIntent.V2 readLegacy(
            String input,
            String documentName,
            CanonicalTerrainIntentV2.FeatureProjection projection
    ) throws IOException {
        if (projection != CanonicalTerrainIntentV2.FeatureProjection.LEGACY_V2) {
            throw new IOException("legacy reader requires explicit LEGACY_V2 projection");
        }
        JsonNode root = mapper.readTree(input);
        if (root == null || !root.isObject()
                || !root.path("intentVersion").isInt() || root.path("intentVersion").intValue() != 2) {
            throw new IOException("legacy reader requires a TerrainIntent v2 document");
        }
        if (root.has("featureProjection")) {
            throw new IOException("LEGACY_V2 document must not contain a featureProjection field");
        }
        return new VersionedTerrainIntent.V2(v2.readTerrainIntent(input, documentName));
    }
}
