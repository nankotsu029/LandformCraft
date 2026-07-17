package com.github.nankotsu029.landformcraft.format.v2.constraint;

import com.github.nankotsu029.landformcraft.format.Sha256;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Reads PNG constraint-map sources under the same filesystem envelope as reference images.
 * It performs no pixel or semantic decoding.
 */
public final class SecureConstraintMapSourceLoader {
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };
    private static final int READ_BUFFER_BYTES = 64 * 1024;

    private final SourceReadObserver observer;

    public SecureConstraintMapSourceLoader() {
        this((phase, source) -> { });
    }

    SecureConstraintMapSourceLoader(SourceReadObserver observer) {
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    public List<LoadedConstraintMapSource> load(
            Path requestPath,
            List<ConstraintMapSourceSpec> specifications,
            ConstraintMapDecodeLimits limits,
            BooleanSupplier cancellationRequested
    ) {
        Objects.requireNonNull(requestPath, "requestPath");
        Objects.requireNonNull(specifications, "specifications");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        if (specifications.size() > limits.maximumSources()) {
            throw failure(ConstraintMapFailureCode.DECODE_BUDGET_EXCEEDED,
                    "constraint-map source count exceeds the budget");
        }

        Path root = Objects.requireNonNull(
                requestPath.toAbsolutePath().normalize().getParent(), "request path must have a parent");
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw failure(ConstraintMapFailureCode.UNSAFE_PATH,
                    "constraint-map request root must be a non-symbolic directory");
        }

        List<PreflightSource> preflight = new ArrayList<>();
        Set<String> sourceIds = new HashSet<>();
        Set<String> relativePaths = new HashSet<>();
        Set<Object> fileKeys = new HashSet<>();
        List<Path> pathsWithoutFileKeys = new ArrayList<>();
        long totalBytes = 0L;
        long maximumSourceBytes = 0L;
        for (ConstraintMapSourceSpec specification : specifications) {
            ensureNotCancelled(cancellationRequested);
            Objects.requireNonNull(specification, "constraint-map source specification");
            if (!sourceIds.add(specification.sourceId())) {
                throw failure(ConstraintMapFailureCode.INVALID_DESCRIPTOR,
                        "duplicate constraint-map source ID");
            }
            if (!relativePaths.add(specification.relativePath())) {
                throw failure(ConstraintMapFailureCode.UNSAFE_PATH,
                        "duplicate constraint-map source path");
            }
            verifyPngExtension(specification.relativePath());
            Path source = resolveSafe(root, specification.relativePath());
            BasicFileAttributes before = attributes(source, specification.sourceId());
            if (!before.isRegularFile() || Files.isSymbolicLink(source)) {
                throw failure(ConstraintMapFailureCode.UNSAFE_PATH,
                        "constraint-map source must be a regular file");
            }
            checkFileIdentity(source, before.fileKey(), fileKeys, pathsWithoutFileKeys);
            if (before.size() < 1 || before.size() > limits.maximumSourceBytes()) {
                throw failure(ConstraintMapFailureCode.FILE_TOO_LARGE,
                        "constraint-map source byte size is outside the limit");
            }
            totalBytes = addExact(totalBytes, before.size(), ConstraintMapFailureCode.TOTAL_BYTES_EXCEEDED,
                    "constraint-map total source bytes overflow");
            if (totalBytes > limits.maximumTotalSourceBytes()) {
                throw failure(ConstraintMapFailureCode.TOTAL_BYTES_EXCEEDED,
                        "constraint-map total source bytes exceed the limit");
            }
            maximumSourceBytes = Math.max(maximumSourceBytes, before.size());
            preflight.add(new PreflightSource(specification, source, before));
        }

        if (!preflight.isEmpty()) {
            long loadingPeak = addExact(totalBytes, maximumSourceBytes,
                    ConstraintMapFailureCode.DECODE_BUDGET_EXCEEDED,
                    "constraint-map source loading working-set estimate overflow");
            loadingPeak = addExact(loadingPeak, READ_BUFFER_BYTES,
                    ConstraintMapFailureCode.DECODE_BUDGET_EXCEEDED,
                    "constraint-map source loading working-set estimate overflow");
            if (loadingPeak > limits.maximumWorkingBytes()) {
                throw failure(ConstraintMapFailureCode.DECODE_BUDGET_EXCEEDED,
                        "constraint-map source loading working set exceeds the limit");
            }
        }

        // No source bytes are read until every source has passed path, identity, stat, and aggregate admission.
        List<LoadedConstraintMapSource> result = new ArrayList<>(preflight.size());
        for (PreflightSource prepared : preflight) {
            ensureNotCancelled(cancellationRequested);
            ConstraintMapSourceSpec specification = prepared.specification();
            byte[] bytes = readLimited(
                    prepared.source(), prepared.attributes(), limits, cancellationRequested);
            verifyPngSignatureAndExtension(specification.relativePath(), bytes);
            String checksum = Sha256.bytes(bytes);
            if (!checksum.equals(specification.expectedSha256())) {
                throw failure(ConstraintMapFailureCode.CHECKSUM_MISMATCH,
                        "constraint-map source checksum mismatch");
            }
            ensureNotCancelled(cancellationRequested);
            result.add(new LoadedConstraintMapSource(
                    specification.sourceId(), specification.relativePath(), "image/png", checksum, bytes));
        }
        return List.copyOf(result);
    }

    private byte[] readLimited(
            Path source,
            BasicFileAttributes before,
            ConstraintMapDecodeLimits limits,
            BooleanSupplier cancellationRequested
    ) {
        try (SeekableByteChannel channel = Files.newByteChannel(
                source, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
             ByteArrayOutputStream output = new ByteArrayOutputStream(Math.toIntExact(before.size()))) {
            observer.onPhase(ReadPhase.AFTER_OPEN_BEFORE_READ, source);
            ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER_BYTES);
            long readTotal = 0L;
            while (true) {
                ensureNotCancelled(cancellationRequested);
                int read = channel.read(buffer);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                readTotal = addExact(readTotal, read, ConstraintMapFailureCode.FILE_TOO_LARGE,
                        "constraint-map source byte count overflow");
                if (readTotal > limits.maximumSourceBytes()) {
                    throw failure(ConstraintMapFailureCode.FILE_TOO_LARGE,
                            "constraint-map source exceeds the byte limit");
                }
                output.write(buffer.array(), 0, read);
                buffer.clear();
            }
            observer.onPhase(ReadPhase.AFTER_READ_BEFORE_RESTAT, source);
            ensureNotCancelled(cancellationRequested);
            BasicFileAttributes after = attributes(source, "source");
            if (readTotal != before.size()
                    || !after.isRegularFile()
                    || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !Objects.equals(before.fileKey(), after.fileKey())) {
                throw failure(ConstraintMapFailureCode.SOURCE_CHANGED,
                        "constraint-map source changed while being read");
            }
            return output.toByteArray();
        } catch (ConstraintMapInputException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ConstraintMapInputException(
                    ConstraintMapFailureCode.MISSING_FILE, "failed to read constraint-map source", exception);
        }
    }

    private static Path resolveSafe(Path root, String relative) {
        Path candidate;
        try {
            candidate = root.resolve(relative).normalize();
        } catch (RuntimeException exception) {
            throw new ConstraintMapInputException(
                    ConstraintMapFailureCode.UNSAFE_PATH, "constraint-map path is invalid", exception);
        }
        if (!candidate.startsWith(root)) {
            throw failure(ConstraintMapFailureCode.UNSAFE_PATH,
                    "constraint-map path escapes its request root");
        }
        Path current = root;
        for (Path segment : Path.of(relative)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw failure(ConstraintMapFailureCode.UNSAFE_PATH,
                        "symbolic links are not allowed for constraint maps");
            }
        }
        return candidate;
    }

    private static BasicFileAttributes attributes(Path source, String sourceId) {
        try {
            return Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new ConstraintMapInputException(
                    ConstraintMapFailureCode.MISSING_FILE,
                    "constraint-map source is missing: " + sourceId, exception);
        }
    }

    private static void checkFileIdentity(
            Path source,
            Object fileKey,
            Set<Object> fileKeys,
            List<Path> pathsWithoutFileKeys
    ) {
        if (fileKey != null) {
            if (!fileKeys.add(fileKey)) {
                throw failure(ConstraintMapFailureCode.HARD_LINK_ALIAS,
                        "multiple constraint-map paths reference the same file");
            }
            return;
        }
        for (Path previous : pathsWithoutFileKeys) {
            try {
                if (Files.isSameFile(previous, source)) {
                    throw failure(ConstraintMapFailureCode.HARD_LINK_ALIAS,
                            "multiple constraint-map paths reference the same file");
                }
            } catch (IOException exception) {
                throw new ConstraintMapInputException(
                        ConstraintMapFailureCode.UNSAFE_PATH,
                        "constraint-map file identity could not be verified", exception);
            }
        }
        pathsWithoutFileKeys.add(source);
    }

    private static void verifyPngSignatureAndExtension(String relativePath, byte[] bytes) {
        verifyPngExtension(relativePath);
        if (bytes.length < PNG_SIGNATURE.length) {
            throw failure(ConstraintMapFailureCode.INVALID_MAGIC,
                    "constraint-map source is not a PNG");
        }
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (bytes[index] != PNG_SIGNATURE[index]) {
                throw failure(ConstraintMapFailureCode.INVALID_MAGIC,
                        "constraint-map source is not a PNG");
            }
        }
    }

    private static void verifyPngExtension(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".png")) {
            throw failure(ConstraintMapFailureCode.UNSUPPORTED_FORMAT,
                    "constraint maps must use the .png extension");
        }
    }

    private static long addExact(long first, long second, ConstraintMapFailureCode code, String message) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException exception) {
            throw new ConstraintMapInputException(code, message, exception);
        }
    }

    private static void ensureNotCancelled(BooleanSupplier cancellationRequested) {
        if (cancellationRequested.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("constraint-map source loading was cancelled");
        }
    }

    private static ConstraintMapInputException failure(ConstraintMapFailureCode code, String message) {
        return new ConstraintMapInputException(code, message);
    }

    enum ReadPhase {
        AFTER_OPEN_BEFORE_READ,
        AFTER_READ_BEFORE_RESTAT
    }

    @FunctionalInterface
    interface SourceReadObserver {
        void onPhase(ReadPhase phase, Path source) throws IOException;
    }

    private record PreflightSource(
            ConstraintMapSourceSpec specification,
            Path source,
            BasicFileAttributes attributes
    ) {
    }
}
