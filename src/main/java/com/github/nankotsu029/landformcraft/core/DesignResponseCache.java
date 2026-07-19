package com.github.nankotsu029.landformcraft.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.ai.spi.PreparedReferenceImage;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignResult;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.model.ProviderUsage;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Durable cache of successful provider design responses.
 *
 * <p>Keyed by the provider cache identity (provider id plus configured model), the SHA-256 of
 * the request document bytes, and the prepared reference-image checksums. A later design job
 * with byte-identical inputs reuses the stored canonical TerrainIntent without calling — or
 * re-billing — the provider. Entries are written atomically, verified strictly on read
 * (version, key binding, intent checksum, exact field set), and any mismatch or corruption is
 * treated as a cache miss, never as a failure. The cache stores only the canonical intent and
 * response metadata; no request prompt, image bytes, or credentials are persisted.</p>
 */
public final class DesignResponseCache {
    static final int VERSION = 1;
    private static final Set<String> FIELDS = Set.of(
            "version", "key", "providerId", "modelId", "promptVersion", "responseId",
            "attempts", "createdAt", "inputTokens", "outputTokens", "totalTokens",
            "intentChecksum", "intent");

    private final LandformDataCodec codec;
    private final ObjectMapper mapper = new ObjectMapper();

    public DesignResponseCache(LandformDataCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /** Deterministic cache key over provider identity, request bytes, and image checksums. */
    public String key(
            String cacheIdentity,
            byte[] requestDocumentBytes,
            List<PreparedReferenceImage> images
    ) {
        Objects.requireNonNull(cacheIdentity, "cacheIdentity");
        Objects.requireNonNull(requestDocumentBytes, "requestDocumentBytes");
        Objects.requireNonNull(images, "images");
        MessageDigest digest = sha256();
        digest.update(("design-response-cache-v" + VERSION + "\n").getBytes(StandardCharsets.UTF_8));
        digest.update((cacheIdentity + "\n").getBytes(StandardCharsets.UTF_8));
        digest.update(sha256Hex(requestDocumentBytes).getBytes(StandardCharsets.UTF_8));
        for (PreparedReferenceImage image : images) {
            digest.update(("\n" + image.index() + ":" + image.role() + ":" + image.checksum())
                    .getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Strict load; every mismatch or corruption is a miss. */
    public Optional<TerrainDesignResult> load(Path cacheRoot, String key) {
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        Path file = cacheRoot.resolve(requireKey(key) + ".json");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonNode node = mapper.readTree(Files.readAllBytes(file));
            if (!node.isObject()) {
                return Optional.empty();
            }
            for (var names = node.fieldNames(); names.hasNext(); ) {
                if (!FIELDS.contains(names.next())) {
                    return Optional.empty();
                }
            }
            for (String field : FIELDS) {
                if (!node.has(field)) {
                    return Optional.empty();
                }
            }
            if (node.get("version").asInt() != VERSION || !key.equals(node.get("key").asText())) {
                return Optional.empty();
            }
            String intentJson = node.get("intent").asText();
            if (!node.get("intentChecksum").asText()
                    .equals(sha256Hex(intentJson.getBytes(StandardCharsets.UTF_8)))) {
                return Optional.empty();
            }
            TerrainIntent intent = codec.readTerrainIntent(intentJson, "cached provider TerrainIntent");
            return Optional.of(new TerrainDesignResult(
                    intent,
                    node.get("providerId").asText(),
                    node.get("modelId").asText(),
                    node.get("promptVersion").asText(),
                    node.get("responseId").asText(),
                    new ProviderUsage(
                            node.get("inputTokens").asLong(),
                            node.get("outputTokens").asLong(),
                            node.get("totalTokens").asLong()),
                    node.get("attempts").asInt(),
                    Instant.parse(node.get("createdAt").asText())));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    /** Atomic best-effort store of a successful, already-validated provider result. */
    public void store(Path cacheRoot, String key, TerrainDesignResult result) {
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        Objects.requireNonNull(result, "result");
        requireKey(key);
        try {
            String intentJson = codec.writeJsonString(result.intent());
            ObjectNode node = mapper.createObjectNode();
            node.put("version", VERSION);
            node.put("key", key);
            node.put("providerId", result.providerId());
            node.put("modelId", result.modelId());
            node.put("promptVersion", result.promptVersion());
            node.put("responseId", result.responseId());
            node.put("attempts", result.attempts());
            node.put("createdAt", result.createdAt().toString());
            node.put("inputTokens", result.usage().inputTokens());
            node.put("outputTokens", result.usage().outputTokens());
            node.put("totalTokens", result.usage().totalTokens());
            node.put("intentChecksum", sha256Hex(intentJson.getBytes(StandardCharsets.UTF_8)));
            node.put("intent", intentJson);
            Files.createDirectories(cacheRoot);
            Path temp = Files.createTempFile(cacheRoot, key, ".tmp");
            try {
                Files.write(temp, mapper.writeValueAsBytes(node));
                Files.move(temp, cacheRoot.resolve(key + ".json"),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException | RuntimeException ignored) {
            // The cache is an optimization: a failed store must never fail the design job.
        }
    }

    private static String requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (!key.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("cache key must be a lowercase SHA-256");
        }
        return key;
    }

    private static String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
