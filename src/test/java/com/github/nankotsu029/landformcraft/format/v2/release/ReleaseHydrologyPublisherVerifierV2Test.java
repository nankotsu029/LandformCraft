package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticCompileRequestV2;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.field.ConstraintFieldIndexCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.field.FieldValueSource;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridWriterV1;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyReconciliationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.hydrology.HydrologyRoutingBundlePublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.tile.OfflineTileSchematicWriterV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.CoastalValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.validation.HydrologyValidationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainBlockResolver;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.DeterministicHydrologyRoutingSolverV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingRequestV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingResultV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.ProvisionalSurfaceV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.reconcile.BoundedHydrologyReconcilerV2;
import com.github.nankotsu029.landformcraft.model.GenerationBounds;
import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.HydrologyValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTileArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.OfflineTilePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyReconciliationStateV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;
import com.github.nankotsu029.landformcraft.preview.v2.CoastalDiagnosticFieldsV2;
import com.github.nankotsu029.landformcraft.preview.v2.CoastalDiagnosticPreviewRendererV2;
import com.github.nankotsu029.landformcraft.preview.v2.HydrologyDiagnosticFieldsV2;
import com.github.nankotsu029.landformcraft.preview.v2.HydrologyDiagnosticPreviewRendererV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseHydrologyPublisherVerifierV2Test {
    private final ReleaseManifestCodecV2 manifestCodec = new ReleaseManifestCodecV2();

    @Test
    void publishesCompleteHydrologyCapabilityAndStrictlyVerifiesDirectoryZipAndOrder(@TempDir Path root)
            throws Exception {
        Fixture fixture = fixture(root.resolve("source"));
        ReleaseHydrologyArtifactsV2 first = new ReleaseHydrologyPublisherV2().publish(
                root.resolve("first"), "hydrology-fixture", fixture.source(), true, () -> false);
        ReleaseHydrologyArtifactsV2 second = new ReleaseHydrologyPublisherV2().publish(
                root.resolve("second"), "hydrology-fixture", fixture.source(), true, () -> false);

        ReleaseCoreVerificationV2 directory = new ReleaseHydrologyVerifierV2().verify(first.releaseDirectory());
        ReleaseCoreVerificationV2 zip = new ReleaseHydrologyVerifierV2().verify(first.zip().orElseThrow());
        assertEquals(ReleaseArtifactCatalogV2.HYDROLOGY_WITH_SURFACE, directory.manifest().requiredCapabilities());
        assertEquals(directory.manifest(), zip.manifest());
        assertTrue(directory.manifest().artifacts().size() >= 40);
        assertEquals(directory.manifest().artifacts().stream().map(ReleaseArtifactDescriptorV2::path).sorted().toList(),
                directory.manifest().artifacts().stream().map(ReleaseArtifactDescriptorV2::path).toList());
        assertArrayEquals(Files.readAllBytes(first.releaseDirectory().resolve("manifest.json")),
                Files.readAllBytes(second.releaseDirectory().resolve("manifest.json")));
        assertArrayEquals(Files.readAllBytes(first.zip().orElseThrow()), Files.readAllBytes(second.zip().orElseThrow()));
    }

    @Test
    void surfaceOnlyReleaseStillStrictlyVerifiesWithoutHydrology(@TempDir Path root) throws Exception {
        Fixture fixture = fixture(root.resolve("source"));
        ReleaseSurfaceArtifactsV2 surface = new ReleaseSurfacePublisherV2().publish(
                root.resolve("surface"), "surface-only", fixture.source().surface(), true, () -> false);
        ReleaseCoreVerificationV2 directory = new ReleaseSurfaceVerifierV2().verify(surface.releaseDirectory());
        assertEquals(List.of(ReleaseArtifactCatalogV2.SURFACE_TWO_POINT_FIVE_D),
                directory.manifest().requiredCapabilities());
        assertTrue(directory.manifest().artifacts().stream().noneMatch(descriptor ->
                descriptor.artifactType().startsWith("hydrology-")));
    }

    @Test
    void rejectsMissingExtraVersionGraphChecksumAndCapabilityDependencyTampering(@TempDir Path root)
            throws Exception {
        Fixture fixture = fixture(root.resolve("source"));

        ReleaseHydrologyArtifactsV2 missing = new ReleaseHydrologyPublisherV2().publish(
                root.resolve("missing"), "hydrology-missing", fixture.source(), false, () -> false);
        Files.delete(missing.releaseDirectory().resolve("hydrology/plan.json"));
        assertThrows(IOException.class, () -> new ReleaseHydrologyVerifierV2().verify(missing.releaseDirectory()));

        ReleaseHydrologyArtifactsV2 extra = new ReleaseHydrologyPublisherV2().publish(
                root.resolve("extra"), "hydrology-extra", fixture.source(), false, () -> false);
        Files.writeString(extra.releaseDirectory().resolve("hydrology/unexpected.bin"), "extra");
        assertThrows(IOException.class, () -> new ReleaseHydrologyVerifierV2().verify(extra.releaseDirectory()));

        ReleaseHydrologyArtifactsV2 future = new ReleaseHydrologyPublisherV2().publish(
                root.resolve("future"), "hydrology-future", fixture.source(), false, () -> false);
        ReleaseManifestV2 existing = manifestCodec.read(future.releaseDirectory().resolve("manifest.json"));
        manifestCodec.write(future.releaseDirectory().resolve("manifest.json"), new ReleaseManifestV2(
                existing.releaseFormatVersion(), existing.manifestVersion(), existing.releaseId(),
                List.of("future-capability"), existing.artifacts(), ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
        assertThrows(IOException.class, () -> new ReleaseHydrologyVerifierV2().verify(future.releaseDirectory()));

        ReleaseHydrologyArtifactsV2 dependency = new ReleaseHydrologyPublisherV2().publish(
                root.resolve("dependency"), "hydrology-dependency", fixture.source(), false, () -> false);
        ReleaseManifestV2 dependencyManifest = manifestCodec.read(
                dependency.releaseDirectory().resolve("manifest.json"));
        manifestCodec.write(dependency.releaseDirectory().resolve("manifest.json"), new ReleaseManifestV2(
                dependencyManifest.releaseFormatVersion(), dependencyManifest.manifestVersion(),
                dependencyManifest.releaseId(), List.of(ReleaseArtifactCatalogV2.HYDROLOGY_PLAN),
                dependencyManifest.artifacts(), ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
        assertThrows(IOException.class, () -> new ReleaseHydrologyVerifierV2().verify(dependency.releaseDirectory()));

        ReleaseHydrologyArtifactsV2 version = new ReleaseHydrologyPublisherV2().publish(
                root.resolve("version"), "hydrology-version", fixture.source(), false, () -> false);
        ReleaseManifestV2 versionManifest = manifestCodec.read(version.releaseDirectory().resolve("manifest.json"));
        List<ReleaseArtifactDescriptorV2> futureDescriptors = versionManifest.artifacts().stream().map(descriptor ->
                descriptor.artifactType().equals(HydrologyReleaseCapabilityVerifierV2.PLAN_TYPE)
                        ? new ReleaseArtifactDescriptorV2(descriptor.artifactId(), descriptor.artifactType(), 2,
                        descriptor.path(), descriptor.byteLength(), descriptor.artifactChecksum(),
                        descriptor.semanticChecksum())
                        : descriptor).toList();
        manifestCodec.write(version.releaseDirectory().resolve("manifest.json"), new ReleaseManifestV2(
                versionManifest.releaseFormatVersion(), versionManifest.manifestVersion(), versionManifest.releaseId(),
                versionManifest.requiredCapabilities(), futureDescriptors, ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
        assertThrows(IOException.class, () -> new ReleaseHydrologyVerifierV2().verify(version.releaseDirectory()));

        ReleaseHydrologyArtifactsV2 graph = new ReleaseHydrologyPublisherV2().publish(
                root.resolve("graph"), "hydrology-graph", fixture.source(), false, () -> false);
        Path routingIndex = graph.releaseDirectory().resolve("hydrology/routing/index.json");
        String routingJson = Files.readString(routingIndex);
        String corrupted = routingJson.replaceFirst(
                "\"graphChecksum\"\\s*:\\s*\"[0-9a-f]{64}\"",
                "\"graphChecksum\":\"" + "f".repeat(64) + "\"");
        assertFalse(corrupted.equals(routingJson));
        Files.writeString(routingIndex, corrupted);
        rewriteDescriptor(graph.releaseDirectory(), "hydrology/routing/index.json", Sha256.file(routingIndex));
        assertThrows(IOException.class, () -> new ReleaseHydrologyVerifierV2().verify(graph.releaseDirectory()));

        Path cancelRoot = root.resolve("cancel");
        assertThrows(CancellationException.class, () -> new ReleaseHydrologyPublisherV2().publish(
                cancelRoot, "hydrology-cancel", fixture.source(), true, () -> true));
        assertFalse(Files.exists(cancelRoot.resolve("hydrology-cancel")));
        if (Files.exists(cancelRoot)) {
            try (var files = Files.list(cancelRoot)) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".release-v2-hydrology-")));
            }
        }
    }

    private void rewriteDescriptor(Path release, String path, String artifactChecksum) throws Exception {
        ReleaseManifestV2 manifest = manifestCodec.read(release.resolve("manifest.json"));
        List<ReleaseArtifactDescriptorV2> descriptors = manifest.artifacts().stream().map(descriptor -> {
            if (!descriptor.path().equals(path)) return descriptor;
            try {
                byte[] bytes = Files.readAllBytes(release.resolve(path));
                return new ReleaseArtifactDescriptorV2(descriptor.artifactId(), descriptor.artifactType(),
                        descriptor.artifactVersion(), descriptor.path(), bytes.length, artifactChecksum,
                        descriptor.semanticChecksum());
            } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }).toList();
        manifestCodec.write(release.resolve("manifest.json"), new ReleaseManifestV2(
                manifest.releaseFormatVersion(), manifest.manifestVersion(), manifest.releaseId(),
                manifest.requiredCapabilities(), descriptors, ReleaseManifestV2.PENDING_CANONICAL_CHECKSUM));
    }

    private static Fixture fixture(Path root) throws Exception {
        Files.createDirectories(root);
        LandformV2DataCodec data = new LandformV2DataCodec();
        var request = data.readGenerationRequest(Path.of("examples/v2/manual-constraint-island/request-v2.json"));
        Path requestPath = root.resolve("request.json");
        data.writeGenerationRequest(requestPath, request);
        String requestChecksum = data.generationRequestChecksum(request);

        Path fieldRoot = root.resolve("constraints");
        List<FieldArtifactDescriptorV2> fields = writeFields(fieldRoot);
        String sourceIntent = Files.readString(Path.of("examples/v2/manual-constraint-island/terrain-intent-v2.json"));
        String landChecksum = field(fields, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER).semanticChecksum();
        String heightChecksum = field(fields, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT).semanticChecksum();
        String zoneChecksum = field(fields, FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP).semanticChecksum();
        TerrainIntentV2 intent = data.readTerrainIntent(sourceIntent
                .replace("a".repeat(64), landChecksum)
                .replace("b".repeat(64), heightChecksum)
                .replace("c".repeat(64), zoneChecksum), "hydrology-fixture-intent");
        Path intentPath = root.resolve("intent.json");
        data.writeTerrainIntent(intentPath, intent);
        String intentChecksum = data.terrainIntentChecksum(intent);

        ConstraintFieldIndexV2 index = new ConstraintFieldIndexV2(1, request.requestId(), requestChecksum, intentChecksum,
                bindings(fields), fields);
        Path indexPath = fieldRoot.resolve("index.json");
        new ConstraintFieldIndexCodecV2().write(indexPath, index);

        WorldBlueprintV2 blueprint = new DiagnosticBlueprintCompilerV2().compile(new DiagnosticCompileRequestV2(
                request.requestId(), new GenerationBounds(4, 4, 0, 100, 50), 32, request.generation().globalSeed(),
                requestChecksum, DiagnosticCompileRequestV2.defaultBudget()), intent);
        Path blueprintPath = root.resolve("blueprint.json");
        data.writeWorldBlueprint(blueprintPath, blueprint);

        Path validationPath = root.resolve("coastal-validation.json");
        new CoastalValidationArtifactCodecV2().write(validationPath, new CoastalValidationArtifactV2(
                blueprint.canonicalChecksum(), "coastal-validator-v1",
                new CoastalValidationArtifactV2.CoastalValidationReport(List.of(), List.of())));

        Path previewRoot = root.resolve("previews");
        var preview = new CoastalDiagnosticPreviewRendererV2().render(
                previewRoot, blueprint.canonicalChecksum(), coastalFields(), () -> false);
        assertEquals(11, preview.layers().size());

        OfflineTilePlanV2 plan = new OfflineTilePlanV2(1, "tile-00-00", 0, 0, 0, 0, 4, 4, 0, 100);
        Path tileRoot = root.resolve("tiles");
        Path schematic = tileRoot.resolve(plan.defaultSchematicFileName());
        OfflineTileArtifactV2 tile = new OfflineTileSchematicWriterV2().write(
                schematic, plan, blueprint.canonicalChecksum(), resolver(), () -> false);
        Path metadata = tileRoot.resolve("tile-00-00.json");
        new OfflineTileArtifactCodecV2().write(metadata, tile);

        SurfaceReleaseSourceV2 surface = new SurfaceReleaseSourceV2(
                requestPath, intentPath, blueprintPath, indexPath, fieldRoot,
                validationPath, previewRoot.resolve("index.json"), previewRoot,
                List.of(new SurfaceReleaseSourceV2.TileSource(tile.tileId(), metadata, schematic)));

        Path hydrologyPlanPath = root.resolve("hydrology-plan.json");
        data.writeHydrologyPlan(hydrologyPlanPath, blueprint.hydrologyPlan());

        HydrologyRoutingResultV2 routingResult = new DeterministicHydrologyRoutingSolverV2().solve(
                HydrologyRoutingRequestV2.create(
                        4, 4, blueprint.hydrologyPlan(),
                        ProvisionalSurfaceV2.routable((x, z) -> 50_000_000 + x + z),
                        List.of(new HydrologyRoutingArtifactV2.Outlet(
                                "west", 0, 0, HydrologyRoutingArtifactV2.OutletKind.BOUNDARY))),
                HydrologyRoutingRequestV2.ExecutionProfile.canonical(), () -> false);
        Path routingRoot = root.resolve("routing");
        new HydrologyRoutingBundlePublisherV2().publish(routingRoot, routingResult, () -> false);

        Path reconciliationPlanPath = root.resolve("reconciliation-plan.json");
        data.writeHydrologyReconciliationPlan(reconciliationPlanPath, blueprint.hydrologyReconciliationPlan());
        HydrologyReconciliationArtifactV2 reconciliationArtifact = new BoundedHydrologyReconcilerV2().reconcile(
                blueprint.canonicalChecksum(),
                blueprint.hydrologyReconciliationPlan(),
                new HydrologyReconciliationStateV2(blueprint.hydrologyReconciliationPlan().canonicalChecksum(), List.of()),
                () -> false);
        Path reconciliationArtifactPath = root.resolve("reconciliation-artifact.json");
        new HydrologyReconciliationArtifactCodecV2().write(reconciliationArtifactPath, reconciliationArtifact);

        Path hydrologyValidationPath = root.resolve("hydrology-validation.json");
        new HydrologyValidationArtifactCodecV2().write(hydrologyValidationPath, new HydrologyValidationArtifactV2(
                blueprint.canonicalChecksum(), HydrologyValidationArtifactV2.VALIDATOR_VERSION,
                new HydrologyValidationArtifactV2.HydrologyValidationReport(List.of(), List.of())));

        Path hydrologyPreviewRoot = root.resolve("hydrology-previews");
        new HydrologyDiagnosticPreviewRendererV2().render(
                hydrologyPreviewRoot, blueprint.canonicalChecksum(), hydrologyFields(), () -> false);

        return new Fixture(new HydrologyReleaseSourceV2(
                surface, hydrologyPlanPath, routingRoot.resolve("index.json"), routingRoot,
                reconciliationPlanPath, reconciliationArtifactPath, hydrologyValidationPath,
                hydrologyPreviewRoot.resolve("index.json"), hydrologyPreviewRoot));
    }

    private static List<FieldArtifactDescriptorV2> writeFields(Path root) throws Exception {
        LfcGridWriterV1 writer = new LfcGridWriterV1();
        List<FieldArtifactDescriptorV2> fields = new ArrayList<>();
        fields.add(write(writer, root, "fields/land-desired.lfgrid", "constraint.land.desired",
                FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER, FieldArtifactDescriptorV2.FieldValueType.U8,
                FieldArtifactDescriptorV2.Sampling.NEAREST, "land-water", false, (x, z) -> (x + z) & 1));
        fields.add(write(writer, root, "fields/land-actual.lfgrid", "constraint.land.actual",
                FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER, FieldArtifactDescriptorV2.FieldValueType.U8,
                FieldArtifactDescriptorV2.Sampling.NEAREST, "land-water", false, (x, z) -> (x + z) & 1));
        fields.add(write(writer, root, "fields/land-residual.lfgrid", "constraint.land.residual",
                FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_LAND_WATER, FieldArtifactDescriptorV2.FieldValueType.I32,
                FieldArtifactDescriptorV2.Sampling.NEAREST, "land-water", false, (x, z) -> 0));
        fields.add(write(writer, root, "fields/height-desired.lfgrid", "constraint.height.desired",
                FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT, FieldArtifactDescriptorV2.FieldValueType.I32,
                FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, "height", false, (x, z) -> 50_000_000));
        fields.add(write(writer, root, "fields/height-actual.lfgrid", "constraint.height.actual",
                FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_HEIGHT, FieldArtifactDescriptorV2.FieldValueType.I32,
                FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, "height", false, (x, z) -> 50_000_000));
        fields.add(write(writer, root, "fields/height-residual.lfgrid", "constraint.height.residual",
                FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT, FieldArtifactDescriptorV2.FieldValueType.I32,
                FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, "height", false, (x, z) -> 0));
        fields.add(write(writer, root, "fields/zone-desired.lfgrid", "constraint.zone.desired",
                FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP, FieldArtifactDescriptorV2.FieldValueType.U16,
                FieldArtifactDescriptorV2.Sampling.NEAREST, "zones", false, (x, z) -> x < 2 ? 1 : 2));
        return List.copyOf(fields);
    }

    private static FieldArtifactDescriptorV2 write(
            LfcGridWriterV1 writer, Path root, String path, String id,
            FieldArtifactDescriptorV2.FieldSemantic semantic, FieldArtifactDescriptorV2.FieldValueType type,
            FieldArtifactDescriptorV2.Sampling sampling, String source, boolean noData, FieldValueSource values
    ) throws Exception {
        int sentinel = noData ? type.maximumRaw() : 0;
        long scale = type == FieldArtifactDescriptorV2.FieldValueType.I32 ? 1L : 1_000_000L;
        FieldArtifactDescriptorV2.Definition definition = new FieldArtifactDescriptorV2.Definition(
                id, semantic, type, 4, 4, FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                sampling, scale, 0L, noData, sentinel);
        String checksum = switch (source) { case "land-water" -> "a"; case "height" -> "b"; default -> "c"; };
        return writer.write(root, path, definition, new FieldArtifactDescriptorV2.Provenance(
                FieldArtifactDescriptorV2.SourceKind.CONSTRAINT_MAP, "constraint-source:" + source,
                checksum.repeat(64), "numeric-png", "1", "pixel-center-v1"), values, () -> false);
    }

    private static List<ConstraintFieldIndexV2.AppliedBinding> bindings(List<FieldArtifactDescriptorV2> fields) {
        FieldArtifactDescriptorV2 land = field(fields, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER);
        FieldArtifactDescriptorV2 height = field(fields, FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT);
        FieldArtifactDescriptorV2 zone = field(fields, FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP);
        return List.of(
                new ConstraintFieldIndexV2.AppliedBinding("land-water-binding", "constraint-source:land-water",
                        TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK, TerrainIntentV2.Strength.HARD,
                        TerrainIntentV2.Sampling.NEAREST, 0, 0, "constraint:land-water:sha256-" + land.semanticChecksum(),
                        land.definition().fieldId(), ids(fields, "constraint.land"), List.of(
                        new ConstraintFieldIndexV2.LabelEntry(0, 0, "water"), new ConstraintFieldIndexV2.LabelEntry(1, 1, "land"))),
                new ConstraintFieldIndexV2.AppliedBinding("height-binding", "constraint-source:height",
                        TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE, TerrainIntentV2.Strength.SOFT,
                        TerrainIntentV2.Sampling.BILINEAR_FIXED, 2, 800_000,
                        "constraint:height-guide:sha256-" + height.semanticChecksum(), height.definition().fieldId(),
                        ids(fields, "constraint.height"), List.of()),
                new ConstraintFieldIndexV2.AppliedBinding("zone-binding", "constraint-source:zones",
                        TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP, TerrainIntentV2.Strength.HARD,
                        TerrainIntentV2.Sampling.NEAREST, 0, 0, "constraint:zone-label-map:sha256-" + zone.semanticChecksum(),
                        zone.definition().fieldId(), ids(fields, "constraint.zone"), List.of(
                        new ConstraintFieldIndexV2.LabelEntry(10, 1, "shore"), new ConstraintFieldIndexV2.LabelEntry(20, 2, "upland"))));
    }

    private static List<String> ids(List<FieldArtifactDescriptorV2> fields, String prefix) {
        return fields.stream().map(value -> value.definition().fieldId()).filter(value -> value.startsWith(prefix)).toList();
    }

    private static FieldArtifactDescriptorV2 field(
            List<FieldArtifactDescriptorV2> fields, FieldArtifactDescriptorV2.FieldSemantic semantic
    ) {
        return fields.stream().filter(value -> value.definition().semantic() == semantic).findFirst().orElseThrow();
    }

    private static CoastalDiagnosticFieldsV2 coastalFields() {
        return new CoastalDiagnosticFieldsV2(4, 4, 0, 100_000_000,
                (x, z) -> 0, (x, z) -> 0, (x, z) -> 0, (x, z) -> 0,
                (x, z) -> (x + z) & 1, (x, z) -> (x + z) & 1, (x, z) -> 0,
                (x, z) -> 50_000_000, (x, z) -> 50_000_000, (x, z) -> 0, (x, z) -> 0);
    }

    private static HydrologyDiagnosticFieldsV2 hydrologyFields() {
        return new HydrologyDiagnosticFieldsV2(
                4, 4, 0, 100_000_000,
                (x, z) -> x % 2,
                (x, z) -> z % 3,
                (x, z) -> (x + z) % 4,
                (x, z) -> x == z ? 1 : 0,
                (x, z) -> (x + z) * 1_000_000,
                (x, z) -> (x + z + 1) * 1_000_000,
                (x, z) -> x % 3,
                (x, z) -> x < 2 ? 1 : 0,
                (x, z) -> (x + z) % 5,
                (x, z) -> (x + z) * 500_000,
                (x, z) -> z % 2,
                (x, z) -> 0);
    }

    private static TerrainBlockResolver resolver() {
        return (x, y, z) -> y == 0 ? "minecraft:bedrock" : y < 50 ? "minecraft:stone"
                : y == 50 ? "minecraft:water" : "minecraft:air";
    }

    private record Fixture(HydrologyReleaseSourceV2 source) { }
}
