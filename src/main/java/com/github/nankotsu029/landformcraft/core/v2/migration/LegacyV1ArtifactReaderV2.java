package com.github.nankotsu029.landformcraft.core.v2.migration;

import com.github.nankotsu029.landformcraft.format.DesignArtifactVerifier;
import com.github.nankotsu029.landformcraft.format.DesignVerification;
import com.github.nankotsu029.landformcraft.format.LandformDataCodec;
import com.github.nankotsu029.landformcraft.format.ReleaseVerification;
import com.github.nankotsu029.landformcraft.format.ReleaseVerifier;
import com.github.nankotsu029.landformcraft.format.FileTreeOperations;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationReportV2.UnmappedElement;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationSourceKindV2;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Migration-only strict reader for existing v1 assets (V2-12-04, ADR 0035 D2b).
 *
 * <p>Every read goes through the v1 format's own strict verifier, so unknown versions, fields the v1
 * contract does not define, and corrupted or tampered artifacts fail closed here rather than
 * reaching the mapper. Nothing in this class writes to the source.</p>
 */
public final class LegacyV1ArtifactReaderV2 {
    private static final int MAXIMUM_ZIP_ENTRIES = 2_100;
    private static final long MAXIMUM_ZIP_EXPANDED_BYTES = 2L * 1024L * 1024L * 1024L;

    private final LandformDataCodec codec = new LandformDataCodec();
    private final DesignArtifactVerifier designVerifier = new DesignArtifactVerifier();
    private final ReleaseVerifier releaseVerifier = new ReleaseVerifier();

    /**
     * Reads one v1 asset of the declared kind.
     *
     * @param scratchRoot directory the reader may use to expand a Release 1 ZIP; never the source
     */
    public LegacyV1SourceV2 read(LegacyMigrationSourceKindV2 kind, Path source, Path scratchRoot)
            throws IOException {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(scratchRoot, "scratchRoot");
        Path normalized = source.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)) {
            throw new IOException("migration source must not be a symbolic link: " + source);
        }
        return switch (kind) {
            case V1_TERRAIN_INTENT -> readIntent(normalized);
            case V1_DESIGN_PACKAGE -> readDesignPackage(normalized);
            case V1_RELEASE -> readRelease(normalized, scratchRoot);
        };
    }

    private LegacyV1SourceV2 readIntent(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("v1 terrain intent must be a regular file: " + path);
        }
        TerrainIntent intent = codec.readTerrainIntent(path);
        String digest = Sha256.file(path);
        return new LegacyV1SourceV2(
                LegacyMigrationSourceKindV2.V1_TERRAIN_INTENT,
                intent.schemaVersion(),
                digest,
                "",
                derivedRequestId(digest),
                intent,
                Instant.EPOCH,
                List.of());
    }

    private LegacyV1SourceV2 readDesignPackage(Path directory) throws IOException {
        DesignVerification verification = designVerifier.verify(directory);
        return new LegacyV1SourceV2(
                LegacyMigrationSourceKindV2.V1_DESIGN_PACKAGE,
                verification.intent().schemaVersion(),
                verification.audit().intentChecksum(),
                "",
                verification.audit().requestId(),
                verification.intent(),
                verification.audit().completedAt(),
                List.of());
    }

    private LegacyV1SourceV2 readRelease(Path path, Path scratchRoot) throws IOException {
        ReleaseVerification verification = releaseVerifier.verify(path);
        Path directory = path;
        Path expanded = null;
        try {
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(scratchRoot);
                expanded = Files.createTempDirectory(scratchRoot, "release-1-");
                extractZip(path, expanded);
                // Re-run the full strict verifier over the expanded tree so the migration never reads
                // an entry the directory verifier would have rejected.
                releaseVerifier.verifyDirectory(expanded);
                directory = expanded;
            }
            TerrainIntent intent = codec.readTerrainIntent(directory.resolve("terrain-intent.json"));
            String digest = Sha256.file(directory.resolve("terrain-intent.json"));
            String canonical = Sha256.file(directory.resolve("checksums.sha256"));
            return new LegacyV1SourceV2(
                    LegacyMigrationSourceKindV2.V1_RELEASE,
                    verification.manifest().formatVersion(),
                    digest,
                    canonical,
                    verification.manifest().requestId(),
                    intent,
                    Instant.EPOCH,
                    releaseCarrierUnmapped(verification));
        } finally {
            if (expanded != null) {
                FileTreeOperations.deleteTree(expanded);
            }
        }
    }

    /**
     * The generated halves of a Release 1 that migration cannot convert. A Release 2 container is
     * produced by a compiled v2 Blueprint with module, stage and field descriptors; a Release 1
     * carries none of them, so re-deriving one would mean inventing the generation plan.
     */
    private static List<UnmappedElement> releaseCarrierUnmapped(ReleaseVerification verification) {
        String rerun = "Release 1 block artifacts have no v2 Blueprint field or module descriptors; "
                + "re-generate them with 'lfc v2 export' from the migrated intent";
        List<UnmappedElement> unmapped = new ArrayList<>();
        unmapped.add(new UnmappedElement("release:world-blueprint", "world-blueprint.json",
                "the v1 blueprint has no v2 module, stage, field or ownership descriptors"));
        unmapped.add(new UnmappedElement("release:tiles", "manifest.tiles[]",
                verification.verifiedTiles() + " v1 tile schematics; " + rerun));
        unmapped.add(new UnmappedElement("release:structures", "structures.json",
                "v1 structure placements are anchored to v1 generator output; " + rerun));
        unmapped.add(new UnmappedElement("release:assets", "assets/required-assets.json",
                "v1 required structure assets belong to the v1 generator catalog; " + rerun));
        unmapped.add(new UnmappedElement("release:previews", "previews/",
                "v2 seals its own diagnostic preview index; " + rerun));
        return List.copyOf(unmapped);
    }

    /** Deterministic request identifier for sources that carry none, such as a bare v1 intent. */
    private static String derivedRequestId(String digest) {
        return "v1-migration-" + digest.substring(0, 12);
    }

    private static void extractZip(Path zip, Path target) throws IOException {
        Set<String> names = new HashSet<>();
        Set<String> folded = new HashSet<>();
        int entries = 0;
        long expanded = 0;
        try (InputStream file = Files.newInputStream(zip);
             ZipInputStream input = new ZipInputStream(new BufferedInputStream(file), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (++entries > MAXIMUM_ZIP_ENTRIES) {
                    throw new IOException("Release 1 ZIP contains too many entries");
                }
                String name = entry.getName();
                if (name.isBlank() || name.startsWith("/") || name.contains("\\") || name.contains("//")
                        || name.contains("../") || name.equals("..") || name.matches("^[A-Za-z]:.*")) {
                    throw new IOException("unsafe Release 1 ZIP entry: " + name);
                }
                if (!names.add(name) || !folded.add(name.toLowerCase(Locale.ROOT))) {
                    throw new IOException("Release 1 ZIP contains duplicate or case-colliding entries");
                }
                Path destination = target.resolve(name).normalize();
                if (!destination.startsWith(target)) {
                    throw new IOException("Release 1 ZIP entry escapes the extraction root");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                    input.closeEntry();
                    continue;
                }
                Files.createDirectories(destination.getParent());
                try (var output = Files.newOutputStream(destination)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            expanded += read;
                            if (expanded > MAXIMUM_ZIP_EXPANDED_BYTES) {
                                throw new IOException("Release 1 ZIP expands beyond the allowed size");
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                }
                input.closeEntry();
            }
        }
    }
}
