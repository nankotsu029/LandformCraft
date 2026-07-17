package com.github.nankotsu029.landformcraft.format.v2.geology;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridWriterV1;
import com.github.nankotsu029.landformcraft.generator.v2.geology.GeologyFieldSamplerV2;
import com.github.nankotsu029.landformcraft.generator.v2.geology.GeologyFoundationModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Writes all four geology sidecars to staging, strictly reads them back, then atomically publishes. */
public final class GeologyFieldBundlePublisherV2 {
    private static final List<FieldSpec> FIELDS = List.of(
            new FieldSpec(GeologyPlanV2.FieldSemantic.FORMATION_ID,
                    "fields/formation-id.lfgrid", GeologyFoundationModuleV2.FORMATION_ID_FIELD),
            new FieldSpec(GeologyPlanV2.FieldSemantic.HARDNESS,
                    "fields/hardness.lfgrid", GeologyFoundationModuleV2.HARDNESS_FIELD),
            new FieldSpec(GeologyPlanV2.FieldSemantic.PERMEABILITY,
                    "fields/permeability.lfgrid", GeologyFoundationModuleV2.PERMEABILITY_FIELD),
            new FieldSpec(GeologyPlanV2.FieldSemantic.PROVINCE_ID,
                    "fields/province-id.lfgrid", GeologyFoundationModuleV2.PROVINCE_ID_FIELD));

    public PublishedBundle publish(
            Path target,
            GeologyPlanV2 plan,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(token, "token");
        Path absolute = target.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(absolute.getParent(), "target must have a parent");
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                || Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("geology target or parent is invalid");
        }
        token.throwIfCancellationRequested();
        Path staging = Files.createTempDirectory(parent, ".geology-fields-");
        boolean published = false;
        try {
            GeologyFieldSamplerV2 sampler = new GeologyFieldSamplerV2(plan);
            FieldArtifactDescriptorV2.Provenance provenance = new FieldArtifactDescriptorV2.Provenance(
                    FieldArtifactDescriptorV2.SourceKind.DERIVED,
                    "derived-source:geology-foundation",
                    plan.canonicalChecksum(),
                    "geology-field-rasterizer",
                    "1",
                    GeologyFoundationModuleV2.GENERATOR_VERSION);
            LfcGridWriterV1 writer = new LfcGridWriterV1();
            List<FieldArtifactDescriptorV2> descriptors = new ArrayList<>();
            for (FieldSpec field : FIELDS) {
                token.throwIfCancellationRequested();
                FieldArtifactDescriptorV2.Definition definition = definition(plan, field);
                descriptors.add(writer.write(
                        staging,
                        field.relativePath(),
                        definition,
                        provenance,
                        (x, z) -> sampler.rawValueAt(field.semantic(), x, z),
                        token,
                        new LfcGridWriterV1.WriteLimits(
                                plan.budget().maximumSingleArtifactBytes(),
                                plan.budget().maximumWorkingBytes())));
            }
            descriptors = descriptors.stream()
                    .sorted(Comparator.comparing(value -> value.definition().fieldId())).toList();
            long artifactBytes = 0L;
            for (FieldArtifactDescriptorV2 descriptor : descriptors) {
                artifactBytes = Math.addExact(
                        artifactBytes, Files.size(staging.resolve(descriptor.relativePath())));
            }
            if (artifactBytes > plan.budget().estimatedArtifactBytes()) {
                throw new IOException("geology field set exceeds declared artifact budget");
            }
            try (GeologyFieldSetReaderV2 reader = GeologyFieldSetReaderV2.open(
                    staging, plan, descriptors, token)) {
                reader.verifyAll(plan.budget().maximumWindowSize(), token);
            }
            token.throwIfCancellationRequested();
            try {
                Files.move(staging, absolute, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for geology field publication", exception);
            }
            published = true;
            return new PublishedBundle(absolute, descriptors, artifactBytes);
        } finally {
            if (!published) {
                deleteTree(staging);
            }
        }
    }

    private static FieldArtifactDescriptorV2.Definition definition(
            GeologyPlanV2 plan,
            FieldSpec field
    ) {
        boolean identifier = field.semantic() == GeologyPlanV2.FieldSemantic.PROVINCE_ID
                || field.semantic() == GeologyPlanV2.FieldSemantic.FORMATION_ID;
        return new FieldArtifactDescriptorV2.Definition(
                field.fieldId(),
                FieldArtifactDescriptorV2.FieldSemantic.valueOf("GEOLOGY_" + field.semantic().name()),
                FieldArtifactDescriptorV2.FieldValueType.U16,
                plan.width(),
                plan.length(),
                FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                FieldArtifactDescriptorV2.Sampling.NEAREST,
                identifier ? 1_000_000L : 1_000L,
                0L,
                true,
                GeologyPlanV2.NO_DATA_RAW);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record FieldSpec(
            GeologyPlanV2.FieldSemantic semantic,
            String relativePath,
            String fieldId
    ) {
    }

    public record PublishedBundle(
            Path root,
            List<FieldArtifactDescriptorV2> descriptors,
            long artifactBytes
    ) {
        public PublishedBundle {
            root = root.toAbsolutePath().normalize();
            descriptors = List.copyOf(descriptors);
            if (descriptors.size() != GeologyPlanV2.MAX_FIELDS || artifactBytes < 1L) {
                throw new IllegalArgumentException("invalid published geology field bundle");
            }
        }
    }
}
