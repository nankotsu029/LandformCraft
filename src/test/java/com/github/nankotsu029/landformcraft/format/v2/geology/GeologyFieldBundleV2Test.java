package com.github.nankotsu029.landformcraft.format.v2.geology;

import com.github.nankotsu029.landformcraft.core.v2.GeologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.HydrologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.LithologyPlanCompilerV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridWriterV1;
import com.github.nankotsu029.landformcraft.generator.v2.geology.GeologyFieldSamplerV2;
import com.github.nankotsu029.landformcraft.generator.v2.geology.GeologyFoundationModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeologyFieldBundleV2Test {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    @Test
    void publishesCanonicalSidecarsAndStrictlyReadsCrossFieldValues(@TempDir Path root) throws Exception {
        GeologyPlanV2 plan = plan(96, 64, 827413L);
        GeologyFieldBundlePublisherV2.PublishedBundle bundle =
                new GeologyFieldBundlePublisherV2().publish(root.resolve("geology"), plan, () -> false);

        assertEquals(4, bundle.descriptors().size());
        assertTrue(bundle.artifactBytes() < plan.budget().estimatedArtifactBytes());
        LithologyPlanV2 lithologyPlan = new LithologyPlanCompilerV2().compile(plan);
        try (GeologyFieldSetReaderV2 reader = GeologyFieldSetReaderV2.open(
                bundle.root(), plan, bundle.descriptors(), () -> false)) {
            GeologyFieldSetReaderV2.Window window = reader.readWindow(31, 29, 3, 2, () -> false);
            assertEquals(1, window.province().rawValueAt(0, 0));
            assertEquals(1, window.formation().rawValueAt(2, 1));
            assertEquals(500, window.hardness().rawValueAt(1, 1));
            assertEquals(500, window.permeability().rawValueAt(1, 0));
            reader.verifyAll(32, () -> false);
            reader.verifyLithologyAssignments(lithologyPlan, 32, () -> false);
        }
        LithologyPlanV2 mismatchedSource = codec.sealLithologyPlan(new LithologyPlanV2(
                lithologyPlan.planVersion(), lithologyPlan.assignmentContractVersion(), "0".repeat(64),
                lithologyPlan.catalog(), lithologyPlan.provinceAssignments(), lithologyPlan.budget(),
                "0".repeat(64)));
        try (GeologyFieldSetReaderV2 reader = GeologyFieldSetReaderV2.open(
                bundle.root(), plan, bundle.descriptors(), () -> false)) {
            assertThrows(IOException.class,
                    () -> reader.verifyLithologyAssignments(mismatchedSource, 32, () -> false));
        }
    }

    @Test
    void emptyPlanUsesCompleteNoDataCells(@TempDir Path root) throws Exception {
        GeologyPlanV2 minimal = plan(32, 24, 1L);
        GeologyPlanV2 empty = codec.sealGeologyPlan(new GeologyPlanV2(
                minimal.planVersion(), minimal.fieldContractVersion(), minimal.moduleId(), minimal.moduleVersion(),
                minimal.stageId(), minimal.priorReplacement(), minimal.namedSeed(), minimal.seedNamespace(),
                minimal.width(), minimal.length(), List.of(), minimal.fields(), minimal.budget(), "0".repeat(64)));
        var bundle = new GeologyFieldBundlePublisherV2().publish(root.resolve("empty"), empty, () -> false);

        try (GeologyFieldSetReaderV2 reader = GeologyFieldSetReaderV2.open(
                bundle.root(), empty, bundle.descriptors(), () -> false)) {
            GeologyFieldSetReaderV2.Window window = reader.readWindow(0, 0, 32, 24, () -> false);
            assertEquals(GeologyPlanV2.NO_DATA_RAW, window.province().rawValueAt(0, 0));
            assertEquals(GeologyPlanV2.NO_DATA_RAW, window.permeability().rawValueAt(31, 23));
        }
    }

    @Test
    void wholeTileOrderThreadLocaleAndTimezoneProduceIdenticalFieldsAndArtifacts(@TempDir Path root)
            throws Exception {
        GeologyPlanV2 plan = plan(257, 259, 827413L);
        int[] whole = sample(plan, 257, 1, false);
        assertArrayEquals(whole, sample(plan, 128, 1, false));
        assertArrayEquals(whole, sample(plan, 128, 1, true));
        assertArrayEquals(whole, sample(plan, 128, 4, false));
        assertArrayEquals(whole, sample(plan, 128, 4, true));

        var first = new GeologyFieldBundlePublisherV2().publish(root.resolve("first"), plan, () -> false);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            var second = new GeologyFieldBundlePublisherV2().publish(root.resolve("second"), plan, () -> false);
            assertEquals(checksums(first.descriptors()), checksums(second.descriptors()));
            for (FieldArtifactDescriptorV2 descriptor : first.descriptors()) {
                assertEquals(-1L, Files.mismatch(
                        first.root().resolve(descriptor.relativePath()),
                        second.root().resolve(descriptor.relativePath())));
            }
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void thousandSquareAdmissionStaysBoundedWithoutDenseVoxelAllocation() {
        GeologyPlanV2 plan = plan(1_000, 1_000, 827413L);

        assertEquals(1_000_000L, plan.budget().globalCellCount());
        assertTrue(plan.budget().estimatedArtifactBytes() < 8_100_000L);
        assertTrue(plan.budget().maximumSingleArtifactBytes() < 2_010_000L);
        assertTrue(plan.budget().maximumWorkingBytes() < 1_100_000L);
        assertTrue(plan.budget().estimatedRetainedBytes() < 128L * 1024L);
        assertTrue(plan.budget().estimatedCpuWorkUnits() <= 4_000_000L);
    }

    @Test
    void rejectsUnknownProvinceChecksumFutureEncodingExtraFileAndCleansCancellation(@TempDir Path root)
            throws Exception {
        GeologyPlanV2 plan = plan(32, 24, 827413L);
        Path unknown = root.resolve("unknown");
        List<FieldArtifactDescriptorV2> unknownDescriptors = writeFieldSet(unknown, plan, 2);
        try (GeologyFieldSetReaderV2 reader = GeologyFieldSetReaderV2.open(
                unknown, plan, unknownDescriptors, () -> false)) {
            assertThrows(IOException.class, () -> reader.verifyAll(16, () -> false));
        }

        var corrupted = new GeologyFieldBundlePublisherV2().publish(
                root.resolve("corrupted"), plan, () -> false);
        Path provinceFile = corrupted.root().resolve(corrupted.descriptors().stream()
                .filter(value -> value.definition().semantic()
                        == FieldArtifactDescriptorV2.FieldSemantic.GEOLOGY_PROVINCE_ID)
                .findFirst().orElseThrow().relativePath());
        try (FileChannel channel = FileChannel.open(provinceFile, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[]{99}), channel.size() - 1L);
        }
        assertThrows(IOException.class, () -> GeologyFieldSetReaderV2.open(
                corrupted.root(), plan, corrupted.descriptors(), () -> false));

        var extra = new GeologyFieldBundlePublisherV2().publish(root.resolve("extra"), plan, () -> false);
        Files.writeString(extra.root().resolve("unexpected.txt"), "extra");
        assertThrows(IOException.class, () -> GeologyFieldSetReaderV2.open(
                extra.root(), plan, extra.descriptors(), () -> false));

        Path future = root.resolve("future");
        var futureBundle = new GeologyFieldBundlePublisherV2().publish(future, plan, () -> false);
        FieldArtifactDescriptorV2 formation = futureBundle.descriptors().stream()
                .filter(value -> value.definition().semantic()
                        == FieldArtifactDescriptorV2.FieldSemantic.GEOLOGY_FORMATION_ID)
                .findFirst().orElseThrow();
        Path futureFile = future.resolve(formation.relativePath());
        try (FileChannel channel = FileChannel.open(futureFile, StandardOpenOption.WRITE)) {
            ByteBuffer version = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN).putInt(2);
            version.flip();
            channel.write(version, 8L);
        }
        assertThrows(IOException.class, () -> GeologyFieldSetReaderV2.open(
                future, plan, futureBundle.descriptors(), () -> false));

        AtomicInteger checks = new AtomicInteger();
        Path cancelled = root.resolve("cancelled");
        assertThrows(CancellationException.class, () -> new GeologyFieldBundlePublisherV2().publish(
                cancelled, plan, () -> checks.incrementAndGet() > 8));
        assertFalse(Files.exists(cancelled));
        try (var children = Files.list(root)) {
            assertTrue(children.noneMatch(path -> path.getFileName().toString().startsWith(".geology-fields-")));
        }
    }

    private List<FieldArtifactDescriptorV2> writeFieldSet(
            Path root,
            GeologyPlanV2 plan,
            int provinceCode
    ) throws IOException {
        LfcGridWriterV1 writer = new LfcGridWriterV1();
        FieldArtifactDescriptorV2.Provenance provenance = provenance(plan);
        List<FieldArtifactDescriptorV2> result = new ArrayList<>();
        result.add(writer.write(root, "fields/province-id.lfgrid",
                definition(plan, GeologyPlanV2.FieldSemantic.PROVINCE_ID), provenance,
                (x, z) -> provinceCode, () -> false));
        result.add(writer.write(root, "fields/formation-id.lfgrid",
                definition(plan, GeologyPlanV2.FieldSemantic.FORMATION_ID), provenance,
                (x, z) -> 1, () -> false));
        result.add(writer.write(root, "fields/hardness.lfgrid",
                definition(plan, GeologyPlanV2.FieldSemantic.HARDNESS), provenance,
                (x, z) -> 500, () -> false));
        result.add(writer.write(root, "fields/permeability.lfgrid",
                definition(plan, GeologyPlanV2.FieldSemantic.PERMEABILITY), provenance,
                (x, z) -> 500, () -> false));
        return result.stream().sorted(Comparator.comparing(value -> value.definition().fieldId())).toList();
    }

    private static FieldArtifactDescriptorV2.Definition definition(
            GeologyPlanV2 plan,
            GeologyPlanV2.FieldSemantic semantic
    ) {
        String fieldId = switch (semantic) {
            case PROVINCE_ID -> GeologyFoundationModuleV2.PROVINCE_ID_FIELD;
            case FORMATION_ID -> GeologyFoundationModuleV2.FORMATION_ID_FIELD;
            case HARDNESS -> GeologyFoundationModuleV2.HARDNESS_FIELD;
            case PERMEABILITY -> GeologyFoundationModuleV2.PERMEABILITY_FIELD;
        };
        boolean identifier = semantic == GeologyPlanV2.FieldSemantic.PROVINCE_ID
                || semantic == GeologyPlanV2.FieldSemantic.FORMATION_ID;
        return new FieldArtifactDescriptorV2.Definition(
                fieldId,
                FieldArtifactDescriptorV2.FieldSemantic.valueOf("GEOLOGY_" + semantic.name()),
                FieldArtifactDescriptorV2.FieldValueType.U16,
                plan.width(), plan.length(),
                FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                FieldArtifactDescriptorV2.Sampling.NEAREST,
                identifier ? 1_000_000L : 1_000L,
                0L, true, GeologyPlanV2.NO_DATA_RAW);
    }

    private static FieldArtifactDescriptorV2.Provenance provenance(GeologyPlanV2 plan) {
        return new FieldArtifactDescriptorV2.Provenance(
                FieldArtifactDescriptorV2.SourceKind.DERIVED,
                "derived-source:geology-foundation",
                plan.canonicalChecksum(),
                "geology-field-rasterizer",
                "1",
                GeologyFoundationModuleV2.GENERATOR_VERSION);
    }

    private static List<String> checksums(List<FieldArtifactDescriptorV2> descriptors) {
        return descriptors.stream().map(value -> value.artifactChecksum() + ":" + value.semanticChecksum()).toList();
    }

    private static int[] sample(
            GeologyPlanV2 plan,
            int tileSize,
            int threads,
            boolean reverse
    ) throws Exception {
        GeologyFieldSamplerV2 sampler = new GeologyFieldSamplerV2(plan);
        int[] values = new int[Math.multiplyExact(
                Math.multiplyExact(plan.width(), plan.length()), GeologyPlanV2.MAX_FIELDS)];
        List<Tile> tiles = new ArrayList<>();
        for (int z = 0; z < plan.length(); z += tileSize) {
            for (int x = 0; x < plan.width(); x += tileSize) {
                tiles.add(new Tile(x, z, Math.min(tileSize, plan.width() - x),
                        Math.min(tileSize, plan.length() - z)));
            }
        }
        if (reverse) Collections.reverse(tiles);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Tile tile : tiles) {
                futures.add(executor.submit(() -> {
                    for (int localZ = 0; localZ < tile.length(); localZ++) {
                        for (int localX = 0; localX < tile.width(); localX++) {
                            int x = tile.x() + localX;
                            int z = tile.z() + localZ;
                            int offset = (z * plan.width() + x) * GeologyPlanV2.MAX_FIELDS;
                            for (GeologyPlanV2.FieldSemantic semantic : GeologyPlanV2.FieldSemantic.values()) {
                                values[offset + semantic.ordinal()] = sampler.rawValueAt(semantic, x, z);
                            }
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        return values;
    }

    @SuppressWarnings("unused")
    private static String checksum(int[] values) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
        for (int value : values) {
            buffer.clear();
            buffer.putInt(value).flip();
            digest.update(buffer);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private GeologyPlanV2 plan(int width, int length, long seed) {
        WorldBlueprintV2.Bounds bounds = new WorldBlueprintV2.Bounds(width, length, -64, 255, 50);
        return new GeologyPlanCompilerV2().compile(
                bounds, 128, seed, new HydrologyPlanCompilerV2().compile(bounds).fixedPriors());
    }

    private record Tile(int x, int z, int width, int length) {
    }
}
