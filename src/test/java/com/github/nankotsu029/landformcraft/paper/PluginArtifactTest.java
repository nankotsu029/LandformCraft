package com.github.nankotsu029.landformcraft.paper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginArtifactTest {
    private static final Set<String> PROVIDED_PREFIXES = Set.of(
            "org/bukkit/",
            "io/papermc/",
            "com/sk89q/",
            "com/fastasyncworldedit/"
    );

    @Test
    void distributableJarContainsThePluginButNotProvidedDependencies() throws IOException {
        Path pluginJar = Path.of(System.getProperty("landformcraft.pluginJar"));
        assertTrue(Files.isRegularFile(pluginJar), "shadow JAR must exist");

        try (ZipFile zipFile = new ZipFile(pluginJar.toFile())) {
            assertNotNull(zipFile.getEntry("plugin.yml"));
            assertNotNull(zipFile.getEntry("com/github/nankotsu029/landformcraft/Landformcraft.class"));
            assertNotNull(zipFile.getEntry(
                    "com/github/nankotsu029/landformcraft/core/GenerationExecutors.class"
            ));

            String pluginYaml;
            try (var input = zipFile.getInputStream(zipFile.getEntry("plugin.yml"))) {
                pluginYaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertTrue(pluginYaml.contains("main: com.github.nankotsu029.landformcraft.Landformcraft"));
            assertTrue(pluginYaml.contains("api-version: '1.21.11'"));

            boolean containsProvidedDependency = zipFile.stream()
                    .map(entry -> entry.getName())
                    .anyMatch(name -> PROVIDED_PREFIXES.stream().anyMatch(name::startsWith));
            assertTrue(!containsProvidedDependency, "Paper and WorldEdit must remain compileOnly");
        }
    }
}
