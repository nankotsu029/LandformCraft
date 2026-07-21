package com.github.nankotsu029.landformcraft.format.v2.migration;

import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignArtifactVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignVerificationV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationReportV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationStatusV2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Strict read-back verifier for a published migration bundle (V2-12-04, ADR 0035 D9-7 and D9-10).
 *
 * <p>The bundle is only ever accepted as a whole: the checksum manifest must cover exactly the files
 * on disk, the embedded design package must pass the Release 2 design strict verifier, and the
 * report must agree with the design package it claims to describe. A bundle that is missing a file,
 * carries an extra one, or whose report points at a different intent is rejected rather than
 * partially accepted.</p>
 */
public final class LegacyMigrationBundleVerifierV2 {
    public static final String REPORT_FILE = "migration-report-v2.json";
    public static final String CHECKSUMS_FILE = "checksums.sha256";
    public static final String DESIGNS_DIRECTORY = "designs";

    private static final Pattern CHECKSUM_LINE = Pattern.compile("^([0-9a-f]{64})  ([^\\r\\n]+)$");
    private static final Pattern RELATIVE_PATH = Pattern.compile("^[a-z0-9][a-z0-9._/-]{0,255}$");
    private static final int MAXIMUM_FILES = 64;

    private final LegacyMigrationReportCodecV2 reportCodec = new LegacyMigrationReportCodecV2();
    private final DesignArtifactVerifierV2 designVerifier = new DesignArtifactVerifierV2();

    public LegacyMigrationBundleV2 verify(Path bundleRoot) throws IOException {
        Path root = bundleRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            throw new IOException("migration bundle must be a non-symbolic directory: " + bundleRoot);
        }
        Map<String, Path> files = collectFiles(root);
        Path checksums = files.get(CHECKSUMS_FILE);
        if (checksums == null) {
            throw new IOException("migration bundle is missing " + CHECKSUMS_FILE);
        }
        Map<String, String> expected = readChecksums(checksums);
        Set<String> covered = new HashSet<>(files.keySet());
        covered.remove(CHECKSUMS_FILE);
        if (!expected.keySet().equals(covered)) {
            throw new IOException(CHECKSUMS_FILE + " does not exactly cover the migration bundle files");
        }
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (!Sha256.file(files.get(entry.getKey())).equals(entry.getValue())) {
                throw new IOException("migration bundle checksum mismatch: " + entry.getKey());
            }
        }

        LegacyMigrationReportV2 report = reportCodec.read(requireFile(files, REPORT_FILE));
        if (report.status() != LegacyMigrationStatusV2.PUBLISHED) {
            throw new IOException("a published migration bundle must carry a PUBLISHED report");
        }
        String designRelative = DESIGNS_DIRECTORY + "/" + report.targetRequestId() + "/" + report.targetJobId();
        Path designPackage = root.resolve(designRelative);
        DesignVerificationV2 design = designVerifier.verify(designPackage);
        if (!design.audit().intentChecksum().equals(report.targetIntentChecksum())
                || !design.audit().requestId().equals(report.targetRequestId())
                || !design.audit().jobId().toString().equals(report.targetJobId())
                || design.intent().intentVersion() != report.targetIntentVersion()) {
            throw new IOException("migration report does not describe the design package it ships");
        }
        Set<String> designFiles = expected.keySet().stream()
                .filter(name -> name.startsWith(DESIGNS_DIRECTORY + "/"))
                .collect(java.util.stream.Collectors.toSet());
        for (String name : designFiles) {
            if (!name.startsWith(designRelative + "/")) {
                throw new IOException("migration bundle carries a design package it does not describe: " + name);
            }
        }
        return new LegacyMigrationBundleV2(root, report, designPackage, design, expected.size());
    }

    /** Writes the bundle-level checksum manifest over every file below {@code root}. */
    public void writeChecksums(Path root) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        Map<String, Path> files = collectFiles(normalized);
        if (files.containsKey(CHECKSUMS_FILE)) {
            throw new IOException("migration bundle checksums already exist");
        }
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, Path> entry : new TreeMap<>(files).entrySet()) {
            body.append(Sha256.file(entry.getValue())).append("  ").append(entry.getKey()).append('\n');
        }
        Files.writeString(normalized.resolve(CHECKSUMS_FILE), body.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static Map<String, Path> collectFiles(Path root) throws IOException {
        Map<String, Path> files = new TreeMap<>();
        Set<String> folded = new HashSet<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("symbolic links are not allowed in a migration bundle");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!attributes.isRegularFile() || Files.isSymbolicLink(file)) {
                    throw new IOException("migration bundle contains a non-regular file");
                }
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (!RELATIVE_PATH.matcher(relative).matches()) {
                    throw new IOException("unsafe migration bundle path: " + relative);
                }
                if (files.putIfAbsent(relative, file) != null || !folded.add(relative.toLowerCase(Locale.ROOT))) {
                    throw new IOException("migration bundle contains duplicate or case-colliding files");
                }
                if (files.size() > MAXIMUM_FILES) {
                    throw new IOException("migration bundle contains too many files");
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private static Map<String, String> readChecksums(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || lines.size() > MAXIMUM_FILES) {
            throw new IOException("invalid migration bundle checksum entry count");
        }
        Map<String, String> values = new TreeMap<>();
        String previous = null;
        for (String line : lines) {
            var matcher = CHECKSUM_LINE.matcher(line);
            if (!matcher.matches()) {
                throw new IOException("invalid migration bundle checksum line");
            }
            String relative = matcher.group(2);
            if (CHECKSUMS_FILE.equals(relative) || !RELATIVE_PATH.matcher(relative).matches()
                    || values.putIfAbsent(relative, matcher.group(1)) != null
                    || previous != null && previous.compareTo(relative) >= 0) {
                throw new IOException("migration bundle checksum paths must be safe, unique and sorted");
            }
            previous = relative;
        }
        return Map.copyOf(values);
    }

    private static Path requireFile(Map<String, Path> files, String name) throws IOException {
        Path path = files.get(name);
        if (path == null) {
            throw new IOException("migration bundle is missing " + name);
        }
        return path;
    }
}
