package com.github.nankotsu029.landformcraft.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V2-12-06 CLI retirement boundary. */
class LandformCraftCliTest {
    @Test
    void helpListsOnlyTheV2AndMaintainedSurfaces() {
        Result result = run("--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("request validate|info"));
        assertTrue(result.output().contains("journal-verify <placement-journal-v2.json>"));
        assertTrue(result.output().contains("migrate inspect"));
        assertFalse(result.output().contains("--v1"));
        assertFalse(result.output().contains("非推奨のv1経路"));
    }

    @Test
    void removedCommandRootsFailClosed() {
        assertEquals(2, run("unknown").exitCode());
        assertEquals(2, run("--v1", "unknown").exitCode());
        assertEquals(2, run("r2", "status", "id").exitCode());
        assertTrue(run("--v1", "unknown").error().contains("V2_UNKNOWN_VERB"));
    }

    @Test
    void verifiesTheV2PlacementJournal() {
        Result result = run("v2", "journal-verify",
                "examples/v2/placement/placement-journal-v2.json");

        assertEquals(0, result.exitCode(), result.error());
        assertTrue(result.output().contains("state: PLANNED"));
    }

    @Test
    void corruptedV2PlacementJournalFailsClosed(@TempDir Path directory) throws Exception {
        Path invalid = directory.resolve("invalid.json");
        Files.writeString(invalid, "{\"journalVersion\":1}");

        Result result = run("v2", "journal-verify", invalid.toString());

        assertEquals(1, result.exitCode());
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code;
        try (PrintStream output = new PrintStream(out, true, StandardCharsets.UTF_8);
             PrintStream error = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            code = LandformCraftCli.run(args, output, error);
        }
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int exitCode, String output, String error) { }
}
