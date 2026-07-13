package com.github.nankotsu029.landformcraft.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageBoundaryTest {
    private static final Path ROOT = Path.of("src/main/java/com/github/nankotsu029/landformcraft");

    @Test
    void modelUsesOnlyTheJavaStandardLibrary() throws IOException {
        assertImports("model", imported -> imported.startsWith("java."));
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
