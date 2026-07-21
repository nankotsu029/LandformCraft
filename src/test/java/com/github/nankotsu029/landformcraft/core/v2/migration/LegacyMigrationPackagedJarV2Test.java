package com.github.nankotsu029.landformcraft.core.v2.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR 0035 D2b Acceptance 6: the migration path must still work in a configuration that simulates
 * life after R4 (v1 design/generation orchestration deleted) and R7 (v1 schemas removed from the
 * active {@code schemas/} inventory and kept only as isolated legacy contract resources).
 *
 * <p>The simulation runs the real migration service under a class loader that hides exactly those
 * resources and classes. If the migration still reads a v1 intent and publishes a verified bundle,
 * V2-12-06 can remove them for real without breaking existing user assets.</p>
 */
class LegacyMigrationPackagedJarV2Test {
    /** v1 contract schemas R7 moves out of the active inventory. */
    private static final Set<String> HIDDEN_RESOURCES = Set.of(
            "schemas/terrain-intent.schema.json",
            "schemas/design-audit.schema.json",
            "schemas/image-input-evidence.schema.json",
            "schemas/export-manifest.schema.json",
            "schemas/world-blueprint.schema.json",
            "schemas/generation-request.schema.json",
            "schemas/generation-job.schema.json",
            "schemas/placement-journal.schema.json",
            "schemas/placement-safety-state.schema.json",
            "schemas/snapshot-cleanup-plan.schema.json",
            "schemas/required-assets.schema.json",
            "schemas/structure-placements.schema.json"
    );

    /** v1 design and generation orchestration R4 deletes. */
    private static final Set<String> HIDDEN_CLASSES = Set.of(
            "com.github.nankotsu029.landformcraft.core.TerrainDesignApplicationService",
            "com.github.nankotsu029.landformcraft.core.BlueprintCompiler",
            "com.github.nankotsu029.landformcraft.core.DesignResponseCache",
            "com.github.nankotsu029.landformcraft.core.TerrainDesignProviderFactory",
            "com.github.nankotsu029.landformcraft.core.FileGenerationJobRepository",
            "com.github.nankotsu029.landformcraft.core.GenerationApplicationService",
            "com.github.nankotsu029.landformcraft.format.DesignArtifactPublisher",
            "com.github.nankotsu029.landformcraft.format.ReleasePublisher"
    );

    @Test
    void legacyContractResourcesAreIsolatedFromTheActiveSchemaInventory() throws IOException {
        for (String resource : HIDDEN_RESOURCES) {
            String name = resource.substring("schemas/".length());
            Path active = Path.of("schemas", name);
            Path legacy = Path.of("src/main/resources/legacy/v1/contracts", name);
            assertTrue(Files.isRegularFile(legacy), "missing staged legacy contract: " + legacy);
            if (!name.equals("image-input-evidence.schema.json")
                    && !name.equals("required-assets.schema.json")) {
                assertFalse(Files.exists(active), "legacy contract remains active: " + active);
            }
            assertTrue(Files.readString(legacy, StandardCharsets.UTF_8).contains("\"$schema\""));
        }
    }

    @Test
    void migrationRunsWithTheActiveV1SchemasAndV1OrchestrationRemoved(@TempDir Path root) throws Exception {
        Path source = root.resolve("terrain-intent.json");
        Files.copy(Path.of("src/main/resources/legacy/v1/fixtures/mountain-stream/terrain-intent.json"), source);
        Path output = root.resolve("out");

        try (ReducedClassLoader loader = new ReducedClassLoader()) {
            // Sanity: the simulation really does remove them.
            assertNull(loader.getResource("schemas/terrain-intent.schema.json"));
            assertThrows(ClassNotFoundException.class, () ->
                    loader.loadClass("com.github.nankotsu029.landformcraft.core.BlueprintCompiler"));
            assertNotNull(loader.getResource("legacy/v1/contracts/terrain-intent.schema.json"));

            Object summary = runMigration(loader, source, output, "packaged-jar");
            String rendered = String.valueOf(summary);
            assertTrue(rendered.contains("PUBLISHED"), rendered);
            assertTrue(rendered.contains("zone:mountain-source"), rendered);
        }

        assertTrue(Files.isRegularFile(output.resolve("packaged-jar").resolve("migration-report-v2.json")));
        assertTrue(Files.isRegularFile(output.resolve("packaged-jar").resolve("checksums.sha256")));
    }

    /** Drives the migration reflectively so the whole call chain resolves inside {@code loader}. */
    private static Object runMigration(ClassLoader loader, Path source, Path output, String migrationId)
            throws Exception {
        Class<?> kinds = loader.loadClass(
                "com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationSourceKindV2");
        Object intentKind = kinds.getMethod("valueOf", String.class).invoke(null, "V1_TERRAIN_INTENT");
        Class<?> requestType = loader.loadClass(
                "com.github.nankotsu029.landformcraft.core.v2.migration.LegacyMigrationRequestV2");
        Object request = requestType.getConstructors()[0].newInstance(
                intentKind, source, java.util.Optional.of(output), migrationId, false, true);
        Class<?> executorsType = loader.loadClass(
                "com.github.nankotsu029.landformcraft.core.GenerationExecutors");
        Class<?> serviceType = loader.loadClass(
                "com.github.nankotsu029.landformcraft.core.v2.migration.LegacyMigrationApplicationServiceV2");
        Object executors = executorsType.getMethod("create", int.class, int.class, int.class)
                .invoke(null, 2, 2, 8);
        try {
            Object service = serviceType.getConstructor(executorsType).newInstance(executors);
            Object result = serviceType.getMethod("migrateNow", requestType).invoke(service, request);
            return serviceType.getMethod("summarize", result.getClass()).invoke(null, result);
        } finally {
            executorsType.getMethod("close").invoke(executors);
        }
    }

    private static void assertNull(Object value) {
        assertFalse(value != null, "expected the resource to be hidden but found " + value);
    }

    /**
     * Loads production classes from the same classpath the JAR is built from, but refuses the
     * resources and classes ADR 0035 removes. Delegation is inverted for the production namespace so
     * the hidden set is actually enforced instead of being satisfied by the parent loader.
     */
    private static final class ReducedClassLoader extends URLClassLoader {
        private ReducedClassLoader() {
            super(classpath(), ClassLoader.getPlatformClassLoader());
        }

        @Override
        public URL getResource(String name) {
            return HIDDEN_RESOURCES.contains(name) ? null : super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return HIDDEN_RESOURCES.contains(name)
                    ? java.util.Collections.enumeration(List.of())
                    : super.getResources(name);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return HIDDEN_RESOURCES.contains(name) ? null : super.getResourceAsStream(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (HIDDEN_CLASSES.contains(name)) {
                throw new ClassNotFoundException("removed by ADR 0035: " + name);
            }
            return super.loadClass(name, resolve);
        }

        private static URL[] classpath() {
            String[] entries = System.getProperty("java.class.path").split(java.io.File.pathSeparator);
            URL[] urls = new URL[entries.length];
            for (int index = 0; index < entries.length; index++) {
                try {
                    urls[index] = Path.of(entries[index]).toUri().toURL();
                } catch (java.net.MalformedURLException exception) {
                    throw new IllegalStateException("unusable classpath entry: " + entries[index], exception);
                }
            }
            return urls;
        }
    }
}
