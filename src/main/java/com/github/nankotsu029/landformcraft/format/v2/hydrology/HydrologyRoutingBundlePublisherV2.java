package com.github.nankotsu029.landformcraft.format.v2.hydrology;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridWriterV1;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingResultV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.hydrology.HydrologyRoutingArtifactV2;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Stages, strictly reads back, and atomically publishes one routing bundle. */
public final class HydrologyRoutingBundlePublisherV2 {
    private final LfcGridWriterV1 fieldWriter = new LfcGridWriterV1();
    private final HydrologyRoutingArtifactCodecV2 codec = new HydrologyRoutingArtifactCodecV2();

    public HydrologyRoutingArtifactV2 publish(
            Path targetDirectory,
            HydrologyRoutingResultV2 result,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(token, "token");
        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "routing bundle target requires a parent");
        Files.createDirectories(parent);
        if (Files.exists(target)) throw new IOException("hydrology routing bundle target already exists");
        token.throwIfCancellationRequested();
        Path staging = Files.createTempDirectory(parent, ".hydrology-routing-");
        boolean published = false;
        try {
            FieldArtifactDescriptorV2.Provenance provenance = new FieldArtifactDescriptorV2.Provenance(
                    FieldArtifactDescriptorV2.SourceKind.DERIVED,
                    HydrologyRoutingArtifactV2.PROVENANCE_SOURCE_ID,
                    result.sourceSurfaceChecksum(),
                    HydrologyRoutingArtifactV2.PROVENANCE_DECODER_ID,
                    "1",
                    HydrologyRoutingArtifactV2.PROVENANCE_TRANSFORM_ID);
            FieldArtifactDescriptorV2 direction = fieldWriter.write(
                    staging,
                    HydrologyRoutingArtifactV2.FLOW_DIRECTION_PATH,
                    definition(result, HydrologyRoutingArtifactV2.FLOW_DIRECTION_FIELD_ID,
                            FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_DIRECTION,
                            FieldArtifactDescriptorV2.FieldValueType.U8, 255),
                    provenance,
                    result::flowDirectionCodeAt,
                    token);
            FieldArtifactDescriptorV2 accumulation = fieldWriter.write(
                    staging,
                    HydrologyRoutingArtifactV2.FLOW_ACCUMULATION_PATH,
                    definition(result, HydrologyRoutingArtifactV2.FLOW_ACCUMULATION_FIELD_ID,
                            FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_ACCUMULATION,
                            FieldArtifactDescriptorV2.FieldValueType.I32, 0),
                    provenance,
                    result::flowAccumulationAt,
                    token);
            if (!direction.semanticChecksum().equals(result.flowDirectionSemanticChecksum())
                    || !accumulation.semanticChecksum().equals(result.flowAccumulationSemanticChecksum())) {
                throw new IOException("published routing field checksum differs from the solved result");
            }
            List<FieldArtifactDescriptorV2> fields = List.of(direction, accumulation);
            long fieldBytes = Math.addExact(
                    Files.size(staging.resolve(direction.relativePath())),
                    Files.size(staging.resolve(accumulation.relativePath())));
            var metrics = result.metrics();
            var budget = metrics.budget();
            HydrologyRoutingArtifactV2.ResourceUsage resources = new HydrologyRoutingArtifactV2.ResourceUsage(
                    budget.budgetVersion(), metrics.globalCellCount(), metrics.routableCellCount(),
                    metrics.cpuWorkUnits(), budget.maximumCpuWorkUnits(), metrics.peakWorkingBytes(),
                    budget.maximumWorkingBytes(), metrics.retainedResultBytes(),
                    budget.maximumRetainedResultBytes(), fieldBytes, budget.maximumFieldArtifactBytes(),
                    metrics.maximumHeapCells());
            String routingChecksum = HydrologyRoutingArtifactV2.computeRoutingChecksum(
                    result.graphChecksum(), fields);
            if (!routingChecksum.equals(result.routingChecksum())) {
                throw new IOException("routing artifact checksum differs from the solved result");
            }
            HydrologyRoutingArtifactV2 draft = new HydrologyRoutingArtifactV2(
                    HydrologyRoutingArtifactV2.VERSION,
                    HydrologyRoutingArtifactV2.SOLVER_VERSION,
                    HydrologyRoutingArtifactV2.DIRECTION_ENCODING_VERSION,
                    result.width(), result.length(), result.sourceHydrologyPlanChecksum(),
                    result.sourceSurfaceChecksum(), result.fixedPriorChecksum(), result.outlets(), result.basins(),
                    fields, resources, result.graphChecksum(), routingChecksum, "0".repeat(64));
            HydrologyRoutingArtifactV2 artifact = codec.seal(draft);
            codec.write(staging.resolve(HydrologyRoutingArtifactCodecV2.INDEX_FILE), artifact);
            HydrologyRoutingArtifactV2 verified = codec.readAndVerify(
                    staging.resolve(HydrologyRoutingArtifactCodecV2.INDEX_FILE), staging, token);
            if (!verified.equals(artifact)) {
                throw new IOException("routing artifact changed during strict read-back");
            }
            token.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for hydrology routing publication", exception);
            }
            published = true;
            return artifact;
        } finally {
            if (!published) deleteTree(staging);
        }
    }

    private static FieldArtifactDescriptorV2.Definition definition(
            HydrologyRoutingResultV2 result,
            String fieldId,
            FieldArtifactDescriptorV2.FieldSemantic semantic,
            FieldArtifactDescriptorV2.FieldValueType valueType,
            int noData
    ) {
        return new FieldArtifactDescriptorV2.Definition(
                fieldId, semantic, valueType, result.width(), result.length(),
                FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                FieldArtifactDescriptorV2.Sampling.NEAREST,
                1L, 0L, true, noData);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
