package com.github.nankotsu029.landformcraft.format.v2.hydrology;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.format.v2.field.FieldWindow;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridReaderV1;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyFlowDirectionV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict canonical codec and bounded semantic reader for V2-3-02 routing bundles. */
public final class HydrologyRoutingArtifactCodecV2 {
    public static final String SCHEMA = "hydrology-routing-artifact-v2.schema.json";
    public static final String INDEX_FILE = "index.json";
    private static final long MAXIMUM_INDEX_BYTES = 512L * 1024L;

    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();

    public HydrologyRoutingArtifactV2 read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return parse(readTree(path), path.toString());
    }

    public HydrologyRoutingArtifactV2 read(String input, String documentName) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(documentName, "documentName");
        if (input.length() > MAXIMUM_INDEX_BYTES) {
            throw new IOException("hydrology routing index exceeds its byte budget");
        }
        return parse(mapper.readTree(input), documentName);
    }

    public HydrologyRoutingArtifactV2 readAndVerify(
            Path indexPath,
            Path bundleRoot,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(indexPath, "indexPath");
        Objects.requireNonNull(token, "token");
        token.throwIfCancellationRequested();
        Path expectedIndex = Objects.requireNonNull(bundleRoot, "bundleRoot")
                .toAbsolutePath().normalize().resolve(INDEX_FILE);
        if (!indexPath.toAbsolutePath().normalize().equals(expectedIndex)) {
            throw new IOException("hydrology routing index must be the canonical bundle index");
        }
        HydrologyRoutingArtifactV2 artifact = read(indexPath);
        verifyBundle(bundleRoot, artifact, token);
        token.throwIfCancellationRequested();
        return artifact;
    }

    public String canonical(HydrologyRoutingArtifactV2 artifact) {
        return CanonicalJsonV2.string(mapper.valueToTree(seal(artifact)));
    }

    public String checksum(HydrologyRoutingArtifactV2 artifact) {
        ObjectNode tree = mapper.valueToTree(artifact);
        tree.remove("canonicalChecksum");
        return CanonicalJsonV2.checksum(tree);
    }

    public HydrologyRoutingArtifactV2 seal(HydrologyRoutingArtifactV2 artifact) {
        return artifact.withCanonicalChecksum(checksum(artifact));
    }

    public void write(Path path, HydrologyRoutingArtifactV2 artifact) throws IOException {
        HydrologyRoutingArtifactV2 sealed = seal(artifact);
        verifyCanonicalChecksum(sealed);
        JsonNode tree = mapper.valueToTree(sealed);
        validator.validate(SCHEMA, path.toString(), tree);
        Files.write(path, CanonicalJsonV2.bytes(tree), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private void verifyBundle(
            Path bundleRoot,
            HydrologyRoutingArtifactV2 artifact,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(bundleRoot, "bundleRoot");
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(token, "token");
        verifyCanonicalChecksum(artifact);
        verifyExactFiles(bundleRoot, artifact);

        FieldArtifactDescriptorV2 directionDescriptor = field(
                artifact, FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_DIRECTION);
        FieldArtifactDescriptorV2 accumulationDescriptor = field(
                artifact, FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_ACCUMULATION);
        long fieldBytes = Math.addExact(
                Files.size(bundleRoot.resolve(directionDescriptor.relativePath())),
                Files.size(bundleRoot.resolve(accumulationDescriptor.relativePath())));
        if (fieldBytes != artifact.resources().fieldArtifactBytes()) {
            throw new IOException("hydrology routing field byte count does not match its index");
        }

        try (LfcGridReaderV1 directions = LfcGridReaderV1.open(bundleRoot, directionDescriptor,
                new LfcGridReaderV1.ReadLimits(8L * 1024L * 1024L, 128L * 1024L), token);
             LfcGridReaderV1 accumulation = LfcGridReaderV1.open(bundleRoot, accumulationDescriptor,
                     new LfcGridReaderV1.ReadLimits(8L * 1024L * 1024L, 128L * 1024L), token)) {
            verifyRoutingRows(artifact, directions, accumulation, token);
        }
    }

    private HydrologyRoutingArtifactV2 parse(JsonNode node, String documentName) throws IOException {
        validator.validate(SCHEMA, documentName, node);
        HydrologyRoutingArtifactV2 artifact = mapper.treeToValue(node, HydrologyRoutingArtifactV2.class);
        verifyCanonicalChecksum(artifact);
        return artifact;
    }

    private void verifyCanonicalChecksum(HydrologyRoutingArtifactV2 artifact) throws IOException {
        String actual = checksum(artifact);
        if (!actual.equals(artifact.canonicalChecksum())) {
            throw new IOException("hydrology routing canonical checksum mismatch");
        }
    }

    private static void verifyExactFiles(
            Path bundleRoot,
            HydrologyRoutingArtifactV2 artifact
    ) throws IOException {
        Path normalizedRoot = bundleRoot.toAbsolutePath().normalize();
        Set<String> expected = new HashSet<>();
        expected.add(INDEX_FILE);
        artifact.fields().forEach(field -> expected.add(field.relativePath()));
        Set<String> expectedDirectories = new HashSet<>();
        for (String expectedFile : expected) {
            Path parent = Path.of(expectedFile).getParent();
            while (parent != null) {
                expectedDirectories.add(parent.toString().replace('\\', '/'));
                parent = parent.getParent();
            }
        }
        Set<String> actual = new HashSet<>();
        try (var files = Files.walk(normalizedRoot)) {
            Iterator<Path> iterator = files.iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                if (file.equals(normalizedRoot)) continue;
                if (Files.isSymbolicLink(file)) {
                    throw new IOException("hydrology routing bundle must not contain symbolic links");
                }
                String relative = normalizedRoot.relativize(file.toAbsolutePath().normalize())
                        .toString().replace('\\', '/');
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                    if (!expectedDirectories.contains(relative)) {
                        throw new IOException("hydrology routing bundle contains an unindexed directory");
                    }
                    continue;
                }
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("hydrology routing bundle contains a non-regular file");
                }
                if (!expected.contains(relative) || !actual.add(relative)) {
                    throw new IOException("hydrology routing bundle contains an unindexed file");
                }
            }
        }
        if (!actual.equals(expected)) {
            throw new IOException("hydrology routing bundle file set is incomplete or contains extra files");
        }
    }

    private static void verifyRoutingRows(
            HydrologyRoutingArtifactV2 artifact,
            LfcGridReaderV1 directions,
            LfcGridReaderV1 accumulation,
            CancellationToken token
    ) throws IOException {
        int width = artifact.width();
        int length = artifact.length();
        Map<Integer, HydrologyRoutingArtifactV2.BasinSummary> basinByOutletCell = new HashMap<>();
        for (HydrologyRoutingArtifactV2.BasinSummary basin : artifact.basins()) {
            basinByOutletCell.put(basin.outletCellId(), basin);
        }
        Set<Integer> observedOutlets = new HashSet<>();
        long routableCells = 0L;
        long terminalAccumulation = 0L;

        int[] previousAccumulation = null;
        int[] currentDirection = row(directions, 0, width, token);
        int[] currentAccumulation = row(accumulation, 0, width, token);
        int[] nextAccumulation = length > 1 ? row(accumulation, 1, width, token) : null;
        for (int z = 0; z < length; z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                int code = currentDirection[x];
                int value = currentAccumulation[x];
                int cellId = z * width + x;
                if (code == HydrologyFlowDirectionV2.NO_DATA.code()) {
                    if (value != 0) throw new IOException("no-data routing cell has non-zero accumulation");
                    continue;
                }
                routableCells++;
                if (value < 1) throw new IOException("routable cell has invalid flow accumulation");
                HydrologyFlowDirectionV2 flow;
                try {
                    flow = HydrologyFlowDirectionV2.fromCode(code);
                } catch (IllegalArgumentException exception) {
                    throw new IOException("routing field contains an unknown direction code", exception);
                }
                if (flow == HydrologyFlowDirectionV2.TERMINAL) {
                    HydrologyRoutingArtifactV2.BasinSummary basin = basinByOutletCell.get(cellId);
                    if (basin == null || basin.outletAccumulation() != value || !observedOutlets.add(cellId)) {
                        throw new IOException("terminal routing cell is not the declared basin outlet");
                    }
                    terminalAccumulation = Math.addExact(terminalAccumulation, value);
                    continue;
                }
                int downstreamX = x + flow.deltaX();
                int downstreamZ = z + flow.deltaZ();
                if (downstreamX < 0 || downstreamX >= width || downstreamZ < 0 || downstreamZ >= length) {
                    throw new IOException("routing direction exits the global surface");
                }
                int downstream = switch (Integer.compare(downstreamZ, z)) {
                    case -1 -> previousAccumulation == null ? 0 : previousAccumulation[downstreamX];
                    case 0 -> currentAccumulation[downstreamX];
                    case 1 -> nextAccumulation == null ? 0 : nextAccumulation[downstreamX];
                    default -> throw new IOException("routing direction is not D8 adjacent");
                };
                if (downstream <= value) {
                    throw new IOException("routing accumulation does not strictly increase downstream");
                }
            }
            previousAccumulation = currentAccumulation;
            currentAccumulation = nextAccumulation;
            if (z + 1 < length) {
                currentDirection = row(directions, z + 1, width, token);
            }
            nextAccumulation = z + 2 < length ? row(accumulation, z + 2, width, token) : null;
        }
        if (routableCells != artifact.resources().routableCellCount()
                || terminalAccumulation != routableCells
                || observedOutlets.size() != artifact.outlets().size()) {
            throw new IOException("routing forest does not cover every routable cell exactly once");
        }
    }

    private static int[] row(
            LfcGridReaderV1 reader,
            int z,
            int width,
            CancellationToken token
    ) throws IOException {
        FieldWindow window = reader.readWindow(0, z, width, 1, token);
        return window.toRawArray();
    }

    private static FieldArtifactDescriptorV2 field(
            HydrologyRoutingArtifactV2 artifact,
            FieldArtifactDescriptorV2.FieldSemantic semantic
    ) {
        return artifact.fields().stream()
                .filter(field -> field.definition().semantic() == semantic)
                .findFirst().orElseThrow();
    }

    private JsonNode readTree(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("hydrology routing index must be a regular file");
        }
        if (Files.size(path) > MAXIMUM_INDEX_BYTES) {
            throw new IOException("hydrology routing index exceeds its byte budget");
        }
        try (InputStream stream = Files.newInputStream(path, StandardOpenOption.READ)) {
            byte[] bytes = stream.readNBytes(Math.toIntExact(MAXIMUM_INDEX_BYTES) + 1);
            if (bytes.length > MAXIMUM_INDEX_BYTES) {
                throw new IOException("hydrology routing index exceeds its byte budget");
            }
            return mapper.readTree(bytes);
        }
    }
}
