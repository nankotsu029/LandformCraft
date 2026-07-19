package com.github.nankotsu029.landformcraft.core.v2.operations;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.nankotsu029.landformcraft.format.v2.CanonicalJsonV2;
import com.github.nankotsu029.landformcraft.model.v2.operations.OperationalAuditEventV2;
import com.github.nankotsu029.landformcraft.validation.StructuredDataValidator;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Bounded append-only operational audit JSONL writer with in-memory correlation ring (V2-6-13).
 */
public final class OperationalAuditLogV2 {
    public static final String SCHEMA = "operational-audit-event-v2.schema.json";
    public static final String DEFAULT_FILE_NAME = "operations-audit-v2.jsonl";
    public static final int MAXIMUM_RING_EVENTS = 256;
    public static final long MAXIMUM_FILE_BYTES = 8L * 1024L * 1024L;
    public static final int MAXIMUM_LINE_BYTES = 4_096;

    private final Path auditFile;
    private final ObjectMapper mapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final StructuredDataValidator validator = new StructuredDataValidator();
    private final Deque<OperationalAuditEventV2> ring = new ArrayDeque<>();
    private final Object lock = new Object();

    public OperationalAuditLogV2(Path dataRoot) {
        Objects.requireNonNull(dataRoot, "dataRoot");
        this.auditFile = dataRoot.toAbsolutePath().normalize().resolve(DEFAULT_FILE_NAME);
    }

    public Path auditFile() {
        return auditFile;
    }

    public void append(OperationalAuditEventV2 event) throws IOException {
        Objects.requireNonNull(event, "event");
        ObjectNode tree = mapper.valueToTree(event);
        validator.validate(SCHEMA, "operational-audit-event", tree);
        byte[] line = (CanonicalJsonV2.string(tree) + "\n").getBytes(StandardCharsets.UTF_8);
        if (line.length > MAXIMUM_LINE_BYTES) {
            throw new IOException("operational audit line exceeds byte budget");
        }
        synchronized (lock) {
            Path parent = Objects.requireNonNull(auditFile.getParent(), "audit parent");
            Files.createDirectories(parent);
            if (Files.isSymbolicLink(auditFile) || Files.isSymbolicLink(parent)) {
                throw new IOException("operational audit path must not be a symbolic link");
            }
            long existing = Files.exists(auditFile, LinkOption.NOFOLLOW_LINKS) ? Files.size(auditFile) : 0L;
            if (existing + line.length > MAXIMUM_FILE_BYTES) {
                throw new IOException("operational audit file exceeds byte budget");
            }
            try (FileChannel channel = FileChannel.open(
                    auditFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND)) {
                channel.write(java.nio.ByteBuffer.wrap(line));
                channel.force(true);
            }
            ring.addLast(event);
            while (ring.size() > MAXIMUM_RING_EVENTS) {
                ring.removeFirst();
            }
        }
    }

    public Optional<OperationalAuditEventV2> findByCorrelationId(UUID correlationId) {
        Objects.requireNonNull(correlationId, "correlationId");
        synchronized (lock) {
            for (OperationalAuditEventV2 event : ring) {
                if (event.correlationId().equals(correlationId)) {
                    return Optional.of(event);
                }
            }
        }
        return Optional.empty();
    }

    public List<OperationalAuditEventV2> recentEvents() {
        synchronized (lock) {
            return List.copyOf(new ArrayList<>(ring));
        }
    }
}
