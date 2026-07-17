package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseCorePublisherVerifierV2Test {
    private final ReleaseManifestCodecV2 manifestCodec = new ReleaseManifestCodecV2();

    @Test
    void publishesAndStrictlyVerifiesDeterministicCoreDirectoryAndZip(@TempDir Path directory) throws Exception {
        ReleaseManifestV2 manifest = new ReleaseManifestV2("release-core-fixture");
        ReleaseCoreArtifactsV2 first = new ReleaseCorePublisherV2().publish(
                directory.resolve("first"), manifest, true, () -> false);
        ReleaseCoreArtifactsV2 second = new ReleaseCorePublisherV2().publish(
                directory.resolve("second"), manifest, true, () -> false);

        ReleaseCoreVerifierV2 verifier = new ReleaseCoreVerifierV2();
        ReleaseCoreVerificationV2 directoryResult = verifier.verify(first.releaseDirectory());
        ReleaseCoreVerificationV2 zipResult = verifier.verify(first.zip().orElseThrow());
        assertEquals(manifestCodec.seal(manifest), directoryResult.manifest());
        assertEquals(directoryResult.manifest(), zipResult.manifest());
        assertEquals(1, directoryResult.verifiedFiles());
        try (var files = Files.list(first.releaseDirectory())) {
            assertEquals(List.of("manifest.json"), files.map(path -> path.getFileName().toString()).sorted().toList());
        }
        assertArrayEquals(
                Files.readAllBytes(first.releaseDirectory().resolve("manifest.json")),
                Files.readAllBytes(second.releaseDirectory().resolve("manifest.json")));
        assertArrayEquals(Files.readAllBytes(first.zip().orElseThrow()), Files.readAllBytes(second.zip().orElseThrow()));
    }

    @Test
    void rejectsUnknownTypeVersionAndCapability(@TempDir Path directory) throws Exception {
        Path unknownType = directory.resolve("unknown-type");
        writeIndexedArtifactRelease(unknownType, "unknown-artifact", 1, List.of());
        assertThrows(IOException.class, () -> new ReleaseCoreVerifierV2().verifyDirectory(unknownType));

        Path unknownVersion = directory.resolve("unknown-version");
        writeIndexedArtifactRelease(unknownVersion, "generation-request-v2", 2, List.of());
        assertThrows(IOException.class, () -> new ReleaseCoreVerifierV2().verifyDirectory(unknownVersion));

        Path unknownCapability = directory.resolve("unknown-capability");
        Files.createDirectories(unknownCapability);
        manifestCodec.write(unknownCapability.resolve("manifest.json"), new ReleaseManifestV2(
                2, 1, "unknown-capability", List.of("future-capability"), List.of(),
                ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
        assertThrows(IOException.class, () -> new ReleaseCoreVerifierV2().verifyDirectory(unknownCapability));
    }

    @Test
    void canonicalizesCapabilityAndArtifactIndexOrderBeforeChecksum(@TempDir Path directory) throws Exception {
        ReleaseArtifactDescriptorV2 later = descriptor("later", "unknown-artifact", 1,
                "z/payload.bin", new byte[]{1});
        ReleaseArtifactDescriptorV2 earlier = descriptor("earlier", "unknown-artifact", 1,
                "a/payload.bin", new byte[]{2});
        ReleaseManifestV2 first = manifestCodec.seal(new ReleaseManifestV2(
                2, 1, "canonical-order", List.of("z-capability", "a-capability"), List.of(later, earlier),
                ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
        ReleaseManifestV2 second = manifestCodec.seal(new ReleaseManifestV2(
                2, 1, "canonical-order", List.of("a-capability", "z-capability"), List.of(earlier, later),
                ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
        assertEquals(first, second);
        assertEquals(List.of("a-capability", "z-capability"), first.requiredCapabilities());
        assertEquals(List.of("a/payload.bin", "z/payload.bin"),
                first.artifacts().stream().map(ReleaseArtifactDescriptorV2::path).toList());
        assertThrows(IOException.class, () -> new ReleaseCorePublisherV2().publish(
                directory, first, false, () -> false));
    }

    @Test
    void rejectsMissingExtraAndDuplicateArtifactPaths(@TempDir Path directory) throws Exception {
        Path missing = directory.resolve("missing");
        Files.createDirectories(missing);
        ReleaseArtifactDescriptorV2 descriptor = descriptor("missing-artifact", "unknown-artifact", 1,
                "payload.bin", "payload".getBytes(StandardCharsets.UTF_8));
        manifestCodec.write(missing.resolve("manifest.json"), new ReleaseManifestV2(
                2, 1, "missing", List.of(), List.of(descriptor), ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
        assertThrows(IOException.class, () -> new ReleaseCoreVerifierV2().verifyDirectory(missing));

        Path extra = directory.resolve("extra");
        Files.createDirectories(extra);
        manifestCodec.write(extra.resolve("manifest.json"), new ReleaseManifestV2("extra"));
        Files.writeString(extra.resolve("extra.txt"), "unexpected");
        assertThrows(IOException.class, () -> new ReleaseCoreVerifierV2().verifyDirectory(extra));

        ReleaseArtifactDescriptorV2 first = descriptor("first", "unknown-artifact", 1,
                "duplicate.bin", new byte[]{1});
        ReleaseArtifactDescriptorV2 second = descriptor("second", "unknown-artifact", 1,
                "duplicate.bin", new byte[]{2});
        assertThrows(IllegalArgumentException.class, () -> new ReleaseManifestV2(
                2, 1, "duplicates", List.of(), List.of(first, second),
                ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
    }

    @Test
    void rejectsChecksumTamperingSymlinksAndUnsafeZipEntries(@TempDir Path directory) throws Exception {
        ReleaseCoreArtifactsV2 artifacts = new ReleaseCorePublisherV2().publish(
                directory.resolve("published"), new ReleaseManifestV2("tamper"), true, () -> false);
        Path manifest = artifacts.releaseDirectory().resolve("manifest.json");
        Files.writeString(manifest, Files.readString(manifest).replace("tamper", "tamper-x"));
        assertThrows(IOException.class, () -> new ReleaseCoreVerifierV2().verifyDirectory(artifacts.releaseDirectory()));

        Path symlinkRoot = directory.resolve("symlink");
        Files.createDirectories(symlinkRoot);
        Path external = directory.resolve("external-manifest.json");
        manifestCodec.write(external, new ReleaseManifestV2("external"));
        Files.createSymbolicLink(symlinkRoot.resolve("manifest.json"), external);
        assertThrows(IOException.class, () -> new ReleaseCoreVerifierV2().verifyDirectory(symlinkRoot));

        Path traversal = directory.resolve("traversal.zip");
        writeZip(traversal, Map.of("../escaped.json", "not a manifest"));
        assertThrows(IOException.class, () -> new ReleaseCoreVerifierV2().verify(traversal));
        assertFalse(Files.exists(directory.resolve("escaped.json")));

        Path collision = directory.resolve("collision.zip");
        writeZip(collision, Map.of("manifest.json", "one", "Manifest.json", "two"));
        assertThrows(IOException.class, () -> new ReleaseCoreVerifierV2().verify(collision));
    }

    @Test
    void enforcesEntryExpansionResidentAndDiskBudgetsAndCleansCancelledPublish(@TempDir Path directory)
            throws Exception {
        ReleaseV2Limits tiny = new ReleaseV2Limits(1, 64, 64, 512, 64, 1024);
        Path expanded = directory.resolve("expanded.zip");
        writeZip(expanded, Map.of("manifest.json", "x".repeat(65)));
        assertThrows(IOException.class, () -> new ReleaseCoreVerifierV2(tiny).verify(expanded));
        assertThrows(IllegalArgumentException.class,
                () -> new ReleaseV2Limits(1, 64, 64, 512, 64, 1024 * 1024 + 1));

        Path tooMany = directory.resolve("too-many.zip");
        writeZip(tooMany, Map.of("manifest.json", "x", "extra.txt", "y"));
        assertThrows(IOException.class, () -> new ReleaseCoreVerifierV2(tiny).verify(tooMany));

        ReleaseCorePublisherV2 constrainedPublisher = new ReleaseCorePublisherV2(tiny);
        assertThrows(IOException.class, () -> constrainedPublisher.publish(
                directory.resolve("disk-budget"), new ReleaseManifestV2("disk-budget"), false, () -> false));

        Path cancelRoot = directory.resolve("cancel");
        assertThrows(CancellationException.class, () -> new ReleaseCorePublisherV2().publish(
                cancelRoot, new ReleaseManifestV2("cancelled"), true, () -> true));
        assertFalse(Files.exists(cancelRoot.resolve("cancelled")));
        if (Files.exists(cancelRoot)) {
            try (var files = Files.list(cancelRoot)) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".release-v2-")));
            }
        }

        AtomicInteger checks = new AtomicInteger();
        Path lateCancelRoot = directory.resolve("late-cancel");
        assertThrows(CancellationException.class, () -> new ReleaseCorePublisherV2().publish(
                lateCancelRoot, new ReleaseManifestV2("late-cancelled"), false,
                () -> checks.incrementAndGet() > 6));
        assertFalse(Files.exists(lateCancelRoot.resolve("late-cancelled")));
        if (Files.exists(lateCancelRoot)) {
            try (var files = Files.list(lateCancelRoot)) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".release-v2-")));
            }
        }
    }

    private void writeIndexedArtifactRelease(
            Path root,
            String type,
            int version,
            List<String> capabilities
    ) throws Exception {
        Files.createDirectories(root);
        byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        Files.write(root.resolve("payload.bin"), payload);
        ReleaseArtifactDescriptorV2 descriptor = descriptor("payload", type, version, "payload.bin", payload);
        manifestCodec.write(root.resolve("manifest.json"), new ReleaseManifestV2(
                2, 1, root.getFileName().toString(), capabilities, List.of(descriptor),
                ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
    }

    private static ReleaseArtifactDescriptorV2 descriptor(
            String id,
            String type,
            int version,
            String path,
            byte[] payload
    ) {
        String checksum = Sha256.bytes(payload);
        return new ReleaseArtifactDescriptorV2(id, type, version, path, payload.length, checksum, checksum);
    }

    private static void writeZip(Path path, Map<String, String> entries) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }
}
