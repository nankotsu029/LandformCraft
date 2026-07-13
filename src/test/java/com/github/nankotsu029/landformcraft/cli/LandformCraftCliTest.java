package com.github.nankotsu029.landformcraft.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandformCraftCliTest {
    @Test
    void helpListsPhaseOneGenerateCommand() {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();

        int exitCode = LandformCraftCli.run(
                new String[]{"--help"},
                new PrintStream(outputBytes, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );

        assertEquals(0, exitCode);
        assertTrue(outputBytes.toString(StandardCharsets.UTF_8).contains("generate <request.yml>"));
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
}
