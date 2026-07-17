package com.github.nankotsu029.landformcraft.format.v2;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;

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
        return new VersionedTerrainIntent.V2(v2.readTerrainIntent(input, documentName));
    }
}
