package com.github.nankotsu029.landformcraft.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandformCraftCliTest {
    @Test
    void helpGroupsCommandsByPurposeAndListsSupportedOperations() {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();

        int exitCode = LandformCraftCli.run(
                new String[]{"--help"},
                new PrintStream(outputBytes, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );

        assertEquals(0, exitCode);
        String help = outputBytes.toString(StandardCharsets.UTF_8);
        assertTrue(help.contains("共通オプション:"));
        assertTrue(help.contains("設計・生成:"));
        assertTrue(help.contains("Release・検証:"));
        assertTrue(help.contains("管理:"));
        assertTrue(help.contains("generate <request.yml>"));
        assertTrue(help.contains("export <request.yml>"));
        assertTrue(help.contains("verify <release-directory-or-zip>"));
        assertTrue(help.contains("journal-verify <placement-journal.json>"));
        assertTrue(help.contains("design <import|fixture>"));
        assertTrue(help.contains("design <openai|anthropic>"));
        assertTrue(help.contains("design-verify <design-directory>"));
    }

    @Test
    void returnsUsageErrorForUnknownCommand() {
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();

        int exitCode = LandformCraftCli.run(
                new String[]{"unknown"},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(errorBytes, true, StandardCharsets.UTF_8)
        );

        assertEquals(2, exitCode);
        assertTrue(errorBytes.toString(StandardCharsets.UTF_8).contains("Unknown command"));
    }

    @Test
    void verifiesPlacementJournalExample() {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();

        int exitCode = LandformCraftCli.run(
                new String[]{"journal-verify", "examples/placement-journal.json"},
                new PrintStream(outputBytes, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );

        assertEquals(0, exitCode);
        assertTrue(outputBytes.toString(StandardCharsets.UTF_8).contains("state: PLANNED"));
    }

    @Test
    void reportsInvalidPlacementJournalWithoutThrowing(@org.junit.jupiter.api.io.TempDir Path directory)
            throws java.io.IOException {
        Path invalid = directory.resolve("invalid.json");
        Files.writeString(invalid, Files.readString(Path.of("examples/placement-journal.json"))
                .replace("\"state\": \"PLANNED\"", "\"state\": \"BROKEN\""));
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();

        int exitCode = LandformCraftCli.run(
                new String[]{"journal-verify", invalid.toString()},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(errorBytes, true, StandardCharsets.UTF_8)
        );

        assertEquals(1, exitCode);
        assertTrue(errorBytes.toString(StandardCharsets.UTF_8).contains("Journal verification failed"));
    }

    @Test
    void importsAndVerifiesDesignArtifact(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path designs = directory.resolve("designs");
        Path jobs = directory.resolve("jobs");
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();

        int designExit = LandformCraftCli.run(
                new String[]{
                        "design", "import", "examples/rocky-coast/request.yml",
                        "examples/rocky-coast/terrain-intent.json", designs.toString(), jobs.toString()
                },
                new PrintStream(outputBytes, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );
        Path artifact;
        try (var requestDirectories = Files.list(designs.resolve("rocky-coast-001"))) {
            artifact = requestDirectories.filter(Files::isDirectory).findFirst().orElseThrow();
        }

        int verifyExit = LandformCraftCli.run(
                new String[]{"design-verify", artifact.toString()},
                new PrintStream(outputBytes, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );

        assertEquals(0, designExit);
        assertEquals(0, verifyExit);
        assertTrue(outputBytes.toString(StandardCharsets.UTF_8).contains("Published verified design"));
        assertTrue(outputBytes.toString(StandardCharsets.UTF_8).contains("Verified design"));
    }
}
