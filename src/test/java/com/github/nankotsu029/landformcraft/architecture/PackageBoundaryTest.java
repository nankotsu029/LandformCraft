package com.github.nankotsu029.landformcraft.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PackageBoundaryTest {
    private static final Path ROOT = Path.of("src/main/java/com/github/nankotsu029/landformcraft");

    @Test
    void modelUsesOnlyTheJavaStandardLibrary() throws IOException {
        assertImports("model", imported -> imported.startsWith("java.")
                || imported.startsWith("com.github.nankotsu029.landformcraft.model."));
    }

    @Test
    void generatorAndCoreRespectAiAndRuntimeBoundaries() throws IOException {
        Predicate<String> portableRuntime = imported -> !imported.contains(".paper.")
                && !imported.startsWith("org.bukkit.")
                && !imported.startsWith("io.papermc.")
                && !imported.startsWith("com.sk89q.")
                && !imported.startsWith("com.fastasyncworldedit.");
        assertImports("generator", imported -> portableRuntime.test(imported) && !imported.contains(".ai."));
        assertImports("core", imported -> portableRuntime.test(imported)
                && (!imported.contains(".ai.") || imported.contains(".ai.spi.")));
    }

    @Test
    void portableAiAndFormatPackagesDoNotDependOnPaperRuntime() throws IOException {
        Predicate<String> allowed = imported -> !imported.contains(".paper.")
                && !imported.startsWith("org.bukkit.")
                && !imported.startsWith("io.papermc.");
        for (String packageName : List.of("ai", "format", "validation", "preview")) {
            assertImports(packageName, allowed);
        }
    }

    @Test
    void v2DiagnosticArtifactsAreNotWiredIntoV1ReleaseOrPlacement() throws IOException {
        for (String relative : List.of(
                "format/ReleasePublisher.java",
                "format/ReleaseVerifier.java",
                "core/PlacementApplicationService.java",
                "paper/PaperWorldEditPlacementGateway.java"
        )) {
            Path file = ROOT.resolve(relative);
            if (!Files.exists(file)) {
                continue;
            }
            String source = Files.readString(file);
            assertFalse(source.contains(".v2"), () -> file + " must not depend on v2 diagnostic code");
            assertFalse(source.contains("WorldBlueprintV2"), () -> file + " must remain a v1 boundary");
        }
    }

    private static void assertImports(String packageName, Predicate<String> allowed) throws IOException {
        Path root = ROOT.resolve(packageName);
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    String stripped = line.strip();
                    if (stripped.startsWith("import ")) {
                        String imported = stripped.substring("import ".length(), stripped.length() - 1);
                        assertTrue(allowed.test(imported), () -> file + " has forbidden import " + imported);
                    }
                }
            }
        }
    }
}
