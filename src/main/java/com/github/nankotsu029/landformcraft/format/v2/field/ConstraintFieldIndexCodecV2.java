package com.github.nankotsu029.landformcraft.format.v2.field;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict canonical JSON codec and artifact verifier for the V2-1 constraint-field index. */
public final class ConstraintFieldIndexCodecV2 {
    public static final String SCHEMA = "constraint-field-index-v2.schema.json";

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public ConstraintFieldIndexV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return parse(readTree(path), path.toString());
    }

    public ConstraintFieldIndexV2 read(String input, String documentName) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(documentName, "documentName");
        return parse(readTree(input, documentName), documentName);
    }

    public ConstraintFieldIndexV2 readAndVerify(Path path, Path artifactRoot) throws IOException {
        return readAndVerify(path, artifactRoot, () -> false);
    }

    public ConstraintFieldIndexV2 readAndVerify(
            Path path,
            Path artifactRoot,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        ConstraintFieldIndexV2 index = read(path);
        verifyArtifacts(artifactRoot, index, cancellationToken);
        cancellationToken.throwIfCancellationRequested();
        return index;
    }

    public ConstraintFieldIndexV2 readAndVerify(
            Path path,
            Path artifactRoot,
            String expectedRequestChecksum,
            String expectedIntentChecksum
    ) throws IOException {
        return readAndVerify(path, artifactRoot, expectedRequestChecksum, expectedIntentChecksum,
                () -> false);
    }

    public ConstraintFieldIndexV2 readAndVerify(
            Path path,
            Path artifactRoot,
            String expectedRequestChecksum,
            String expectedIntentChecksum,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        ConstraintFieldIndexV2 index = read(path);
        verifyArtifacts(
                artifactRoot, index, expectedRequestChecksum, expectedIntentChecksum,
                cancellationToken);
        cancellationToken.throwIfCancellationRequested();
        return index;
    }

    public void verifyArtifacts(Path artifactRoot, ConstraintFieldIndexV2 index) throws IOException {
        verifyArtifacts(artifactRoot, index, () -> false);
    }

    public void verifyArtifacts(
            Path artifactRoot,
            ConstraintFieldIndexV2 index,
            CancellationToken cancellationToken
    ) throws IOException {
        verifyArtifactsInternal(artifactRoot, index, null, null, cancellationToken);
    }

    public void verifyArtifacts(
            Path artifactRoot,
            ConstraintFieldIndexV2 index,
            String expectedRequestChecksum,
            String expectedIntentChecksum
    ) throws IOException {
        verifyArtifacts(
                artifactRoot, index, expectedRequestChecksum, expectedIntentChecksum,
                () -> false);
    }

    public void verifyArtifacts(
            Path artifactRoot,
            ConstraintFieldIndexV2 index,
            String expectedRequestChecksum,
            String expectedIntentChecksum,
            CancellationToken cancellationToken
    ) throws IOException {
        verifyArtifactsInternal(
                artifactRoot, index,
                Objects.requireNonNull(expectedRequestChecksum, "expectedRequestChecksum"),
                Objects.requireNonNull(expectedIntentChecksum, "expectedIntentChecksum"),
                cancellationToken);
    }

    private void verifyArtifactsInternal(
            Path artifactRoot,
            ConstraintFieldIndexV2 index,
            String expectedRequestChecksum,
            String expectedIntentChecksum,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(artifactRoot, "artifactRoot");
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        ConstraintFieldIndexV2 sealed = sealOrVerify(index);
        if (expectedRequestChecksum != null
                && (!sealed.sourceRequestChecksum().equals(expectedRequestChecksum)
                || !sealed.sourceIntentChecksum().equals(expectedIntentChecksum))) {
            throw new IOException("constraint field index source checksum mismatch");
        }
        cancellationToken.throwIfCancellationRequested();
        for (var field : sealed.fields()) {
            cancellationToken.throwIfCancellationRequested();
            try (LfcGridReaderV1 reader = LfcGridReaderV1.open(
                    artifactRoot, field, LfcGridReaderV1.ReadLimits.defaults(), cancellationToken)) {
                if (!reader.descriptor().equals(field)) {
                    throw new IOException("constraint field descriptor changed during verification");
                }
            }
        }
        verifyCategoricalDesiredValues(artifactRoot, sealed, cancellationToken);
        cancellationToken.throwIfCancellationRequested();
    }

    public String canonical(ConstraintFieldIndexV2 index) {
        Objects.requireNonNull(index, "index");
        return CanonicalJsonV2.string(mapper.valueToTree(sealUnchecked(index)));
    }

    /** Returns the exclude-self checksum stored in {@code canonicalChecksum}. */
    public String checksum(ConstraintFieldIndexV2 index) {
        Objects.requireNonNull(index, "index");
        return sealUnchecked(index).canonicalChecksum();
    }

    public void write(Path path, ConstraintFieldIndexV2 index) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(index, "index");
        ConstraintFieldIndexV2 sealed = sealOrVerify(index);
        JsonNode tree = mapper.valueToTree(sealed);
        validator.validate(SCHEMA, path.toString(), tree);
        Path target = path.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "index path must have a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".constraint-field-index-", ".tmp");
        boolean published = false;
        try {
            Files.write(
                    temporary,
                    CanonicalJsonV2.bytes(tree),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            // Read back the staged file through the complete schema and model boundary.
            if (!read(temporary).equals(sealed)) {
                throw new IOException("constraint field index changed during staged read-back");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for constraint field index", exception);
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private ConstraintFieldIndexV2 parse(JsonNode tree, String documentName) throws IOException {
        validator.validate(SCHEMA, documentName, tree);
        ConstraintFieldIndexV2 index = mapper.treeToValue(tree, ConstraintFieldIndexV2.class);
        if (index.hasPendingCanonicalChecksum()) {
            throw new IOException("constraint field index is not canonically sealed: " + documentName);
        }
        String expected = canonicalChecksum(index);
        if (!expected.equals(index.canonicalChecksum())) {
            throw new IOException("constraint field index canonical checksum mismatch: " + documentName);
        }
        return index;
    }

    private ConstraintFieldIndexV2 sealOrVerify(ConstraintFieldIndexV2 index) throws IOException {
        String expected = canonicalChecksum(index);
        if (index.hasPendingCanonicalChecksum()) {
            return index.withCanonicalChecksum(expected);
        }
        if (!expected.equals(index.canonicalChecksum())) {
            throw new IOException("constraint field index canonical checksum mismatch");
        }
        return index;
    }

    private ConstraintFieldIndexV2 sealUnchecked(ConstraintFieldIndexV2 index) {
        String expected = canonicalChecksum(index);
        if (index.hasPendingCanonicalChecksum()) {
            return index.withCanonicalChecksum(expected);
        }
        if (!expected.equals(index.canonicalChecksum())) {
            throw new IllegalArgumentException("constraint field index canonical checksum mismatch");
        }
        return index;
    }

    private String canonicalChecksum(ConstraintFieldIndexV2 index) {
        ObjectNode tree = mapper.valueToTree(index);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    private void verifyCategoricalDesiredValues(
            Path artifactRoot,
            ConstraintFieldIndexV2 index,
            CancellationToken cancellationToken
    ) throws IOException {
        Map<String, FieldArtifactDescriptorV2> byId = new HashMap<>();
        for (var field : index.fields()) byId.put(field.definition().fieldId(), field);
        for (ConstraintFieldIndexV2.AppliedBinding binding : index.bindings()) {
            if (binding.role()
                    == TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE) {
                continue;
            }
            cancellationToken.throwIfCancellationRequested();
            var desired = byId.get(binding.canonicalFieldId());
            Set<Integer> dictionary = new HashSet<>();
            for (ConstraintFieldIndexV2.LabelEntry label : binding.labels()) {
                dictionary.add(label.canonicalValue());
            }
            try (LfcGridReaderV1 reader = LfcGridReaderV1.open(
                    artifactRoot, desired, LfcGridReaderV1.ReadLimits.defaults(), cancellationToken)) {
                int width = desired.definition().width();
                int rowsPerWindow = Math.max(1, (64 * 1024) / width);
                for (int originZ = 0; originZ < desired.definition().length(); originZ += rowsPerWindow) {
                    cancellationToken.throwIfCancellationRequested();
                    int rows = Math.min(rowsPerWindow, desired.definition().length() - originZ);
                    FieldWindow window = reader.readWindow(
                            0, originZ, width, rows, cancellationToken);
                    for (int raw : window.toRawArray()) {
                        if (desired.definition().isNoData(raw)) {
                            if (binding.strength()
                                    == TerrainIntentV2.Strength.HARD) {
                                throw new IOException("hard categorical constraint contains no-data");
                            }
                        } else if (!dictionary.contains(raw)) {
                            throw new IOException(
                                    "categorical desired field contains a value outside its label dictionary");
                        }
                    }
                }
            }
        }
    }

    private JsonNode readTree(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(LandformDataCodec.MAX_DOCUMENT_BYTES + 1L));
            if (bytes.length > LandformDataCodec.MAX_DOCUMENT_BYTES) {
                throw new IOException("constraint field index exceeds document limit: " + path);
            }
            JsonNode result = mapper.readTree(bytes);
            if (result == null) {
                throw new IOException("constraint field index is empty: " + path);
            }
            return result;
        }
    }

    private JsonNode readTree(String input, String documentName) throws IOException {
        if (input.getBytes(StandardCharsets.UTF_8).length > LandformDataCodec.MAX_DOCUMENT_BYTES) {
            throw new IOException("constraint field index exceeds document limit: " + documentName);
        }
        JsonNode result = mapper.readTree(input);
        if (result == null) {
            throw new IOException("constraint field index is empty: " + documentName);
        }
        return result;
    }
}
