package com.github.nankotsu029.landformcraft.paper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
            assertNotNull(zipFile.getEntry(
                    "com/github/nankotsu029/landformcraft/ai/openai/OpenAiTerrainDesignProvider.class"
            ));
            assertNotNull(zipFile.getEntry("schemas/design-audit.schema.json"));
            assertNotNull(zipFile.getEntry("schemas/generation-job.schema.json"));

            String pluginYaml;
            try (var input = zipFile.getInputStream(zipFile.getEntry("plugin.yml"))) {
                pluginYaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertTrue(pluginYaml.contains("main: com.github.nankotsu029.landformcraft.Landformcraft"));
            assertTrue(pluginYaml.contains("api-version: '1.21.11'"));
            assertTrue(pluginYaml.contains("landformcraft:"));
            assertTrue(pluginYaml.contains("FastAsyncWorldEdit"));
            assertTrue(pluginYaml.contains("landformcraft.apply.plan:"));
            assertTrue(pluginYaml.contains("landformcraft.apply.execute:"));
            assertTrue(pluginYaml.contains("landformcraft.undo.plan:"));
            assertTrue(pluginYaml.contains("landformcraft.undo.execute:"));
            assertTrue(pluginYaml.contains("landformcraft.recovery:"));

            boolean containsProvidedDependency = zipFile.stream()
                    .map(entry -> entry.getName())
                    .anyMatch(name -> PROVIDED_PREFIXES.stream().anyMatch(name::startsWith));
            assertTrue(!containsProvidedDependency, "Paper and WorldEdit must remain compileOnly");
        }
    }

    @Test
    void shadedJarCanReadJavaTimePlacementJournal() throws IOException, InterruptedException {
        Path pluginJar = Path.of(System.getProperty("landformcraft.pluginJar"));
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Process process = new ProcessBuilder(
                java.toString(), "-cp", pluginJar.toString(),
                "com.github.nankotsu029.landformcraft.cli.LandformCraftCli",
                "journal-verify", Path.of("examples/placement-journal.json").toAbsolutePath().toString()
        ).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(0, process.waitFor(), output);
        assertTrue(output.contains("state: PLANNED"), output);
    }

    @Test
    void defaultConfigContainsOnlyRuntimeWiredKeys() throws IOException {
        String config = Files.readString(Path.of("src/main/resources/config.yml"));

        assertTrue(config.contains("storage:"));
        assertTrue(config.contains("executors:"));
        assertTrue(config.contains("worldedit:"));
        assertTrue(!config.contains("limits:"));
        assertTrue(config.contains("providers:"));
        assertTrue(config.contains("api-key-env: OPENAI_API_KEY"));
        assertTrue(!config.contains("sk-"));
    }
}
