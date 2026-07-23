package com.github.nankotsu029.landformcraft.integration.v2;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.catalog.FeatureSupportCatalogConsistencyVerifierV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2CommandRouteV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2CommandRouterV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2CommandVerbV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2WorkflowServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportResultV2;
import com.github.nankotsu029.landformcraft.core.v2.migration.LegacyMigrationApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.migration.LegacyMigrationRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.migration.LegacyMigrationResultV2;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.catalog.FeatureSupportCatalogCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.job.ExportJobCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.migration.LegacyMigrationReportCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseArtifactCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportLevelV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.PlacementDimensionLimitV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationSourceKindV2;
import com.github.nankotsu029.landformcraft.model.v2.release.ReleaseCapabilityDependencyMatrixV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-12-07 Migration integration audit and Phase gate evidence.
 *
 * <p>Pins the one invariant V2-12 exists to establish: the <em>v2 production-only</em>
 * configuration defined in {@code ADR 0035} Consequences. v2 is the only production
 * writer/generator/placement/command path, while v1 is isolated to the D2b migration-only read
 * boundary and the D3 maintained assets — this is deliberately <em>not</em> a "zero v1 code"
 * configuration.</p>
 *
 * <p>This gate re-confirms the phase-distinctive facts as one executable claim: the retirement
 * boundary, the v2-only command surface, the production offline export path, the absence of any
 * capability false-promotion from a command/migration-only phase, and the legacy migration
 * regression. The world placement/Undo/Recovery lifecycle, capability tamper corpus, fault
 * injection, and cross-runtime determinism are exercised by their own tests
 * ({@code V2CommandPathE2EV2Test}, {@code Release2PlacementApplicationServiceV2Test},
 * {@code PlacementPhaseGateV2Test}, and the capability verifier suites), which the full clean
 * suite re-runs under this gate.</p>
 */
class MigrationPhaseGateV2Test {

    private static final Path MAIN = Path.of("src/main/java/com/github/nankotsu029/landformcraft");
    private static final Path REQUEST = Path.of("examples/v2/diagnostic/harbor-cove-64-honored.request-v2.json");
    private static final Path INTENT = Path.of("examples/v2/diagnostic/harbor-cove-64-honored.terrain-intent-v2.json");

    /** ADR 0035 D2a production writers/orchestration/provider SPI removed by V2-12-06. */
    private static final List<String> RETIRED_PRODUCTION_TYPES = List.of(
            "generator/TerrainGenerator.java",
            "generator/LogicalLayoutGenerator.java",
            "generator/StructurePlanner.java",
            "generator/v2/V1TerrainQueryAdapter.java",
            "format/ReleasePublisher.java",
            "format/ReleaseArtifacts.java",
            "format/DesignArtifactPublisher.java",
            "core/PlacementApplicationService.java",
            "core/SnapshotCleanupService.java",
            "core/GenerationApplicationService.java",
            "core/TerrainDesignApplicationService.java",
            "ai/spi/TerrainDesignProvider.java");

    /** ADR 0035 D2b/D3 legacy readers/verifiers and maintained assets that must remain. */
    private static final List<String> MAINTAINED_LEGACY_TYPES = List.of(
            "format/ReleaseVerifier.java",
            "format/ReleaseVerification.java",
            "format/DesignArtifactVerifier.java",
            "format/DesignArtifacts.java",
            "core/CustomAssetService.java");

    /** ADR 0035 R7 v1 schemas that left the active inventory but stay packaged as legacy contracts. */
    private static final List<String> RETIRED_ACTIVE_SCHEMAS = List.of(
            "terrain-intent.schema.json",
            "world-blueprint.schema.json",
            "generation-request.schema.json",
            "generation-job.schema.json",
            "design-audit.schema.json",
            "export-manifest.schema.json",
            "placement-journal.schema.json",
            "placement-safety-state.schema.json",
            "snapshot-cleanup-plan.schema.json",
            "structure-placements.schema.json");

    @Test
    void v2ProductionOnlyConfigurationIsEstablishedAndV1IsIsolated() throws Exception {
        // D2a: every production v1 writer, orchestration service, and provider SPI is gone.
        for (String retired : RETIRED_PRODUCTION_TYPES) {
            assertFalse(Files.exists(MAIN.resolve(retired)),
                    "retired v1 production type remains: " + retired);
        }

        // D2b/D3: the migration-only readers/verifiers and maintained assets remain. A
        // production-only configuration is not a zero-v1 configuration.
        for (String kept : MAINTAINED_LEGACY_TYPES) {
            assertTrue(Files.isRegularFile(MAIN.resolve(kept)),
                    "maintained legacy compatibility asset missing: " + kept);
        }
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/resources/legacy/v1/golden/rocky-coast/golden-checksums.json")),
                "K1 immutable v1 golden archive missing");
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/resources/legacy/v1/fixtures/release1-phase6/manifest.json.b64")),
                "packaged Release 1 migration fixture missing");

        // R7: the ten v1 schemas left the active inventory and exist only as immutable packaged
        // legacy contract resources — the "active inventory does not show R7-moved legacy
        // resources" gate requirement.
        for (String schema : RETIRED_ACTIVE_SCHEMAS) {
            assertFalse(Files.exists(Path.of("schemas", schema)),
                    "v1 schema still in the active inventory: " + schema);
            assertTrue(Files.isRegularFile(
                            Path.of("src/main/resources/legacy/v1/contracts", schema)),
                    "packaged legacy contract missing: " + schema);
        }

        // R6/D4: the r2 permission alias and the CLI --v1 opt-in are removed.
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String cli = Files.readString(MAIN.resolve("cli/LandformCraftCli.java"));
        assertFalse(plugin.contains("landformcraft.r2."), "retired r2 permission alias remains");
        assertFalse(cli.contains("--v1"), "retired CLI --v1 switch remains");

        // The shared command router knows only the v2 root; the evaluation-era r2 root is gone.
        assertEquals("v2", V2CommandVerbV2.ROOT);
        assertFalse(V2CommandRouterV2.route(new String[] {"r2", "export"},
                V2CommandVerbV2.Surface.CLI).accepted());
    }

    @Test
    void everyProductionVerbRoutesThroughTheV2PathWithACanonicalPermission() {
        // The operator-facing production verbs (ADR 0035 D10) all resolve through the v2 router and
        // carry a landformcraft.v2.* permission node. No r2/v1 alias survives to reach them.
        record Case(String[] tokens, V2CommandVerbV2.Surface surface, V2CommandVerbV2 verb) { }
        List<Case> cases = List.of(
                new Case(new String[] {"v2", "request", "validate", "r.json"},
                        V2CommandVerbV2.Surface.CLI, V2CommandVerbV2.REQUEST_VALIDATE),
                new Case(new String[] {"v2", "design", "fixture", "r.json", "i.json"},
                        V2CommandVerbV2.Surface.CLI, V2CommandVerbV2.DESIGN),
                new Case(new String[] {"v2", "generate", "r.json", "i.json", "out", "id",
                        "water", "54", "46"}, V2CommandVerbV2.Surface.CLI, V2CommandVerbV2.GENERATE),
                new Case(new String[] {"v2", "export", "r.json", "i.json", "out", "id",
                        "water", "54", "46"}, V2CommandVerbV2.Surface.CLI, V2CommandVerbV2.EXPORT),
                new Case(new String[] {"v2", "preview", "release"},
                        V2CommandVerbV2.Surface.CLI, V2CommandVerbV2.PREVIEW),
                new Case(new String[] {"v2", "place", "plan", "release", "world", "0", "64", "0"},
                        V2CommandVerbV2.Surface.PAPER, V2CommandVerbV2.PLACE_PLAN),
                new Case(new String[] {"v2", "status", "id"},
                        V2CommandVerbV2.Surface.PAPER, V2CommandVerbV2.STATUS),
                new Case(new String[] {"v2", "undo", "plan", "id"},
                        V2CommandVerbV2.Surface.PAPER, V2CommandVerbV2.UNDO_PLAN));
        for (Case testCase : cases) {
            V2CommandRouteV2 route = V2CommandRouterV2.route(testCase.tokens(), testCase.surface());
            assertTrue(route.accepted(), () -> route.message());
            assertEquals(testCase.verb(), route.requireVerb(), testCase.verb().name());
            assertTrue(route.requireVerb().permission().startsWith("landformcraft.v2."),
                    testCase.verb() + " permission: " + route.requireVerb().permission());
        }
    }

    @Test
    void productionV2ExportPathProducesAPlacementEligibleReleaseEndToEnd(@TempDir Path root)
            throws Exception {
        // The representative V2-12 scenario: the production offline path the CLI and Paper both call
        // (V2-12-02) runs request → generate/export → strict preview verify → placement eligibility.
        GenerationExecutors executors = GenerationExecutors.create(4, 2, 16);
        try {
            V2WorkflowServiceV2 workflow = new V2WorkflowServiceV2(executors, null);

            Map<String, Object> request = workflow.inspectRequest(REQUEST);
            assertEquals("harbor-cove-64-honored", request.get("requestId"));

            // createZip=false is the `v2 generate` form: the strict Release directory without a ZIP.
            Release2ExportResultV2 generated = workflow.export(REQUEST, INTENT, root.resolve("work"),
                    root.resolve("exports"), "v2-12-07-gate",
                    V2WorkflowServiceV2.baseline("water", "54", "46"), false);
            assertEquals(List.of("surface-2_5d"), generated.requiredCapabilities());
            assertTrue(generated.eligibility().eligible(), "production export must be placement-eligible");

            Map<String, Object> previews = workflow.inspectPreviews(generated.releaseDirectory());
            assertEquals(generated.manifestChecksum(), previews.get("manifestChecksum"));
        } finally {
            executors.shutdown(Duration.ofSeconds(30));
            executors.close();
        }
    }

    @Test
    void releaseCapabilitySetAndCatalogHaveNoFalsePromotionFromV2Twelve() throws Exception {
        // V2-12 changed only the command surface and migration path; it must not promote any
        // capability. The sealed catalog stays byte-stable and free of Paper false-promotion.
        FeatureSupportCatalogV2 catalog =
                new FeatureSupportCatalogConsistencyVerifierV2().requireConsistentBuiltIn();
        assertEquals(4, catalog.entries().stream()
                .filter(entry -> entry.support().level(FeatureSupportCapabilityV2.PAPER_APPLY)
                        == FeatureSupportLevelV2.SUPPORTED)
                .count(), "only the four surface-2_5d features may be paper_apply SUPPORTED");

        assertEquals(PlacementDimensionLimitV2.measured(), catalog.placementDimensionLimit());
        assertTrue(catalog.placementDimensionLimit().admits(1_000, 1_000));
        assertTrue(catalog.rejectsUnmeasuredPaperPromotion(1_001, 1_000));

        FeatureSupportCatalogCodecV2 catalogCodec = new FeatureSupportCatalogCodecV2();
        FeatureSupportCatalogV2 sealed =
                catalogCodec.read(Path.of("examples/v2/catalog/feature-support-catalog-v2.json"));
        catalogCodec.verifyChecksum(sealed);
        assertEquals(catalogCodec.builtInSealed().canonicalChecksum(), sealed.canonicalChecksum());

        // The Release 2 capability list is unchanged by V2-12.
        assertEquals(List.of(
                        ReleaseArtifactCatalogV2.ENVIRONMENT_FIELDS,
                        ReleaseArtifactCatalogV2.HYDROLOGY_PLAN,
                        ReleaseArtifactCatalogV2.SPARSE_VOLUME,
                        ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                ReleaseArtifactCatalogV2.SPARSE_VOLUME_WITH_ENVIRONMENT);
        assertEquals(5, ReleaseCapabilityDependencyMatrixV2.validPrefixes().size());
    }

    @Test
    void legacyV1MigrationRegressionIsDeterministicThroughPackagedReaders(@TempDir Path root)
            throws Exception {
        // The D2b migration-only boundary still reads a v1 golden through the packaged legacy
        // readers and converts it to a verified v2 bundle, deterministically.
        Path golden = Path.of("src/main/resources/legacy/v1/golden/rocky-coast/terrain-intent.json");
        try (GenerationExecutors executors = GenerationExecutors.create(2, 2, 8)) {
            LegacyMigrationApplicationServiceV2 service =
                    new LegacyMigrationApplicationServiceV2(executors);
            LegacyMigrationResultV2 first = service.migrateNow(new LegacyMigrationRequestV2(
                    LegacyMigrationSourceKindV2.V1_TERRAIN_INTENT, golden,
                    Optional.of(root.resolve("a")), "v2-12-07-gate", false, true));
            LegacyMigrationResultV2 second = service.migrateNow(new LegacyMigrationRequestV2(
                    LegacyMigrationSourceKindV2.V1_TERRAIN_INTENT, golden,
                    Optional.of(root.resolve("b")), "v2-12-07-gate", false, true));

            assertEquals("PUBLISHED", first.report().status().name());
            assertEquals("PUBLISHED", second.report().status().name());
            Path reportA = first.bundle().orElseThrow().root().resolve("migration-report-v2.json");
            Path reportB = second.bundle().orElseThrow().root().resolve("migration-report-v2.json");
            assertTrue(Files.isRegularFile(reportA));
            // The report is path-independent, so the same source yields identical bytes across runs.
            assertEquals(Sha256.file(reportA), Sha256.file(reportB));
        }
    }

    @Test
    void gatePortfolioIsStableAcrossOrderThreadsLocaleAndTimezone() throws Exception {
        Map<String, String> baseline = read(List.copyOf(PORTFOLIO.keySet()), 1);
        List<String> reversed = new ArrayList<>(PORTFOLIO.keySet());
        Collections.reverse(reversed);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertEquals(baseline, read(reversed, 4));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private static final Map<String, ExampleReader> PORTFOLIO = portfolio();

    private static Map<String, ExampleReader> portfolio() {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        Map<String, ExampleReader> readers = new LinkedHashMap<>();
        readers.put("generation-request", () ->
                codec.generationRequestChecksum(codec.readGenerationRequest(REQUEST)));
        readers.put("terrain-intent", () ->
                codec.terrainIntentChecksum(codec.readTerrainIntent(INTENT)));
        readers.put("export-job", () ->
                new ExportJobCodecV2().read(Path.of("examples/v2/job/export-job-v2.json")).toString());
        readers.put("migration-report", () ->
                new LegacyMigrationReportCodecV2()
                        .read(Path.of("examples/v2/migration/migration-report-v2.json")).toString());
        readers.put("feature-support-catalog", () ->
                new FeatureSupportCatalogCodecV2()
                        .read(Path.of("examples/v2/catalog/feature-support-catalog-v2.json"))
                        .canonicalChecksum());
        return Collections.unmodifiableMap(readers);
    }

    private static Map<String, String> read(List<String> names, int threads) throws Exception {
        List<Callable<Map.Entry<String, String>>> tasks = names.stream()
                .<Callable<Map.Entry<String, String>>>map(name -> () ->
                        Map.entry(name, PORTFOLIO.get(name).canonicalForm()))
                .toList();
        Map<String, String> result = new LinkedHashMap<>();
        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (var future : executor.invokeAll(tasks)) {
                Map.Entry<String, String> entry = future.get();
                result.put(entry.getKey(), entry.getValue());
            }
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String name : PORTFOLIO.keySet()) {
            ordered.put(name, result.get(name));
        }
        return ordered;
    }

    @FunctionalInterface
    private interface ExampleReader {
        String canonicalForm() throws Exception;
    }
}
