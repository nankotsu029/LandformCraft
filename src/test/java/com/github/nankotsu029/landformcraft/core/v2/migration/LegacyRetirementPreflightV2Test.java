package com.github.nankotsu029.landformcraft.core.v2.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyRetirementPreflightV2Test {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-21T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path temporary;

    @Test
    void emptyStatePassesWithoutArchive() throws IOException {
        var preflight = new LegacyRetirementPreflightV2(CLOCK);

        var result = preflight.inspect(temporary.resolve("data"));

        assertEquals("PASS", result.status());
        assertEquals(0, result.unresolvedCount());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void discoveredStateFailsClosedUntilByteExactNeutralArchiveExists() throws IOException {
        Path data = temporary.resolve("data");
        Files.createDirectories(data.resolve("jobs"));
        Files.createDirectories(data.resolve("placements"));
        Files.writeString(data.resolve("jobs/job.json"), "{\"stage\":\"READY\"}\n");
        Files.writeString(data.resolve("placements/placement.json"), "{\"state\":\"APPLIED\"}\n");
        Files.writeString(data.resolve("placement-safety-state.json"), "{}\n");
        var preflight = new LegacyRetirementPreflightV2(CLOCK);

        var blocked = preflight.inspect(data);
        assertEquals("BLOCKED", blocked.status());
        assertEquals(3, blocked.unresolvedCount());

        var archived = preflight.archive(data, temporary.resolve("archives"), "v2-12-06-test");
        assertEquals("PASS", archived.status());
        assertEquals("NEUTRAL_ARCHIVE", archived.resolution());
        assertEquals(0, archived.unresolvedCount());
        assertEquals(3, archived.entryCount());
        assertTrue(Files.isRegularFile(temporary.resolve(
                "archives/v2-12-06-test/payload/jobs/job.json")));
        assertTrue(Files.isRegularFile(temporary.resolve(
                "archives/v2-12-06-test/inventory.json")));
        assertFalse(Files.exists(temporary.resolve(
                "archives/v2-12-06-test/INCOMPLETE")));
        assertEquals("{\"stage\":\"READY\"}\n", Files.readString(data.resolve("jobs/job.json")));
    }

    @Test
    void archiveNeverOverwritesAndInvalidIdIsRejected() throws IOException {
        Path data = temporary.resolve("data");
        Files.createDirectories(data.resolve("exports"));
        Files.writeString(data.resolve("exports/release.json"), "release");
        Path archives = temporary.resolve("archives");
        Files.createDirectories(archives.resolve("existing"));
        var preflight = new LegacyRetirementPreflightV2(CLOCK);

        assertThrows(IOException.class, () -> preflight.archive(data, archives, "existing"));
        assertThrows(IllegalArgumentException.class,
                () -> preflight.archive(data, archives, "../escape"));
    }

    @Test
    void symbolicLinkStateIsRejected() throws IOException {
        Path data = temporary.resolve("data");
        Files.createDirectories(data.resolve("jobs"));
        Path outside = temporary.resolve("outside.json");
        Files.writeString(outside, "outside");
        try {
            Files.createSymbolicLink(data.resolve("jobs/link.json"), outside);
        } catch (UnsupportedOperationException exception) {
            return;
        }

        assertThrows(IOException.class, () -> new LegacyRetirementPreflightV2(CLOCK).inspect(data));
    }
}
