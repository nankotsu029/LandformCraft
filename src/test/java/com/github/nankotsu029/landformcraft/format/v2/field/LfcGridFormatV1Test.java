package com.github.nankotsu029.landformcraft.format.v2.field;

import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
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

class LfcGridFormatV1Test {
    private static final String SOURCE_CHECKSUM = "a".repeat(64);

    @Test
    void roundTripsU8U16AndI32WithNearestBilinearAndNoData(@TempDir Path root) throws Exception {
        LfcGridWriterV1 writer = new LfcGridWriterV1();
        var mask = definition(
                "constraint.land-water", FieldArtifactDescriptorV2.FieldSemantic.LAND_WATER_MASK,
                FieldArtifactDescriptorV2.FieldValueType.U8, 5, 4,
                FieldArtifactDescriptorV2.Sampling.NEAREST, 1_000_000L, 0L, true, 255
        );
        FieldArtifactDescriptorV2 maskDescriptor = writer.write(
                root, "constraints/land-water.lfgrid", mask, provenance(),
                (x, z) -> (x + z) & 1, () -> false
        );
        assertEquals(FieldArtifactDescriptorV2.ENCODING_VERSION, maskDescriptor.encodingVersion());
        assertEquals(64, maskDescriptor.artifactChecksum().length());
        assertEquals(64, maskDescriptor.semanticChecksum().length());
        try (LfcGridReaderV1 reader = LfcGridReaderV1.open(root, maskDescriptor)) {
            assertArrayEquals(new int[]{0, 1, 0, 1, 0, 1},
                    reader.readWindow(1, 1, 3, 2).toRawArray());
            assertEquals(1_000_000L,
                    reader.sampleNearest(1_600_000L, 1_200_000L).valueMillionths());
        }

        var height = definition(
                "constraint.height", FieldArtifactDescriptorV2.FieldSemantic.HEIGHT_GUIDE,
                FieldArtifactDescriptorV2.FieldValueType.U16, 4, 4,
                FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, 1_000_000L, 0L, true, 65_535
        );
        FieldArtifactDescriptorV2 heightDescriptor = writer.write(
                root, "constraints/height.lfgrid", height, provenance(),
                (x, z) -> z * 10 + x, () -> false
        );
        try (LfcGridReaderV1 reader = LfcGridReaderV1.open(root, heightDescriptor)) {
            assertEquals(5_500_000L,
                    reader.sampleBilinearFixed(500_000L, 500_000L).valueMillionths());
            assertEquals(reader.sampleBilinearFixed(500_000L, 500_000L),
                    reader.sample(500_000L, 500_000L));
        }

        FieldArtifactDescriptorV2 noDataDescriptor = writer.write(
                root, "constraints/height-no-data.lfgrid", height, provenance(),
                (x, z) -> x == 1 && z == 1 ? 65_535 : z * 10 + x, () -> false
        );
        try (LfcGridReaderV1 reader = LfcGridReaderV1.open(root, noDataDescriptor)) {
            assertTrue(reader.sampleBilinearFixed(500_000L, 500_000L).noData());
        }

        var residual = definition(
                "constraint.height-residual", FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT,
                FieldArtifactDescriptorV2.FieldValueType.I32, 3, 2,
                FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, 1L, -7L, false, 0
        );
        FieldArtifactDescriptorV2 residualDescriptor = writer.write(
                root, "constraints/height-residual.lfgrid", residual, provenance(),
                (x, z) -> (z * 3 + x - 3) * 1_000_000, () -> false
        );
        try (LfcGridReaderV1 reader = LfcGridReaderV1.open(root, residualDescriptor)) {
            assertArrayEquals(
                    new int[]{-3_000_000, -2_000_000, -1_000_000, 0, 1_000_000, 2_000_000},
                    reader.readWindow(0, 0, 3, 2).toRawArray()
            );
            assertEquals(-7L, reader.readWindow(0, 1, 1, 1).sampleAt(0, 0).valueMillionths());
        }
    }

    @Test
    void wholeAndTileWindowsMatchAcrossOrderThreadsLocaleTimezoneAndSeams(@TempDir Path root)
            throws Exception {
        int width = 257;
        int length = 259;
        var definition = definition(
                "derived.residual", FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT,
                FieldArtifactDescriptorV2.FieldValueType.I32, width, length,
                FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, 1L, 0L, false, 0
        );
        FieldValueSource source = (x, z) -> z * width + x - 25_000;
        LfcGridWriterV1 writer = new LfcGridWriterV1();
        FieldArtifactDescriptorV2 descriptor = writer.write(
                root.resolve("first"), "blueprint/fields/residual.lfgrid",
                definition, derivedProvenance(), source, () -> false
        );

        try (LfcGridReaderV1 reader = LfcGridReaderV1.open(root.resolve("first"), descriptor)) {
            int[] whole = reader.readWindow(0, 0, width, length).toRawArray();
            List<WindowCoordinate> natural = windows(width, length, 128);
            List<WindowCoordinate> reverse = new ArrayList<>(natural);
            Collections.reverse(reverse);
            assertArrayEquals(whole, readTiles(reader, natural, 1, width, length));
            assertArrayEquals(whole, readTiles(reader, reverse, 1, width, length));
            assertArrayEquals(whole, readTiles(reader, natural, 4, width, length));
            assertArrayEquals(whole, readTiles(reader, reverse, 4, width, length));
            assertEquals(source.rawValueAt(127, 128), reader.readWindow(127, 128, 2, 2).rawValueAt(0, 0));
            assertEquals(source.rawValueAt(128, 128), reader.readWindow(127, 128, 2, 2).rawValueAt(1, 0));
        }

        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            FieldArtifactDescriptorV2 changedDefaults = writer.write(
                    root.resolve("second"), "blueprint/fields/residual.lfgrid",
                    definition, derivedProvenance(), source, () -> false
            );
            assertEquals(descriptor.artifactChecksum(), changedDefaults.artifactChecksum());
            assertEquals(descriptor.semanticChecksum(), changedDefaults.semanticChecksum());
            assertEquals(-1L, Files.mismatch(
                    root.resolve("first/blueprint/fields/residual.lfgrid"),
                    root.resolve("second/blueprint/fields/residual.lfgrid")
            ));
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    void thousandSquareUsesStreamingWriterAndBoundedWindows(@TempDir Path root) throws Exception {
        var definition = definition(
                "derived.large-height", FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_HEIGHT,
                FieldArtifactDescriptorV2.FieldValueType.I32, 1_000, 1_000,
                FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, 1L, 0L, false, 0
        );
        LfcGridWriterV1 writer = new LfcGridWriterV1();
        assertTrue(writer.estimateArtifactBytes(definition, derivedProvenance()) < 4_010_000L);
        assertTrue(writer.estimatePeakWorkingBytes(definition, derivedProvenance()) < 70_000L);
        FieldArtifactDescriptorV2 descriptor = writer.write(
                root, "blueprint/fields/large-height.lfgrid", definition, derivedProvenance(),
                (x, z) -> z * 1_000 + x, () -> false
        );
        assertTrue(Files.size(root.resolve(descriptor.relativePath())) < 4_010_000L);
        assertTrue(LfcGridReaderV1.estimateWindowWorkingBytes(
                128, 128, FieldArtifactDescriptorV2.FieldValueType.I32) < 70_000L);
        try (LfcGridReaderV1 reader = LfcGridReaderV1.open(root, descriptor)) {
            FieldWindow window = reader.readWindow(872, 872, 128, 128);
            assertEquals(872_872, window.rawValueAt(0, 0));
            assertEquals(999_999, window.rawValueAt(127, 127));
        }
    }

    @Test
    void rejectsCorruptionFutureVersionLengthBudgetAndCleansCancelledStaging(@TempDir Path root)
            throws Exception {
        var definition = definition(
                "constraint.height", FieldArtifactDescriptorV2.FieldSemantic.HEIGHT_GUIDE,
                FieldArtifactDescriptorV2.FieldValueType.U16, 16, 16,
                FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED, 1_000_000L, 0L, false, 0
        );
        LfcGridWriterV1 writer = new LfcGridWriterV1();
        FieldArtifactDescriptorV2 corrupted = writer.write(
                root.resolve("corrupt"), "constraints/height.lfgrid", definition, provenance(),
                (x, z) -> z * 16 + x, () -> false
        );
        Path corruptFile = root.resolve("corrupt").resolve(corrupted.relativePath());
        try (FileChannel channel = FileChannel.open(corruptFile, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[]{99}), channel.size() - 1L);
        }
        assertThrows(IOException.class,
                () -> LfcGridReaderV1.open(root.resolve("corrupt"), corrupted));

        FieldArtifactDescriptorV2 future = writer.write(
                root.resolve("future"), "constraints/height.lfgrid", definition, provenance(),
                (x, z) -> z * 16 + x, () -> false
        );
        Path futureFile = root.resolve("future").resolve(future.relativePath());
        try (FileChannel channel = FileChannel.open(futureFile, StandardOpenOption.WRITE)) {
            ByteBuffer version = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN).putInt(2);
            version.flip();
            channel.write(version, 8L);
        }
        FieldArtifactDescriptorV2 futureChecksum = withArtifactChecksum(future, Sha256.file(futureFile));
        assertThrows(IOException.class,
                () -> LfcGridReaderV1.open(root.resolve("future"), futureChecksum));

        FieldArtifactDescriptorV2 truncated = writer.write(
                root.resolve("truncated"), "constraints/height.lfgrid", definition, provenance(),
                (x, z) -> z * 16 + x, () -> false
        );
        Path truncatedFile = root.resolve("truncated").resolve(truncated.relativePath());
        try (FileChannel channel = FileChannel.open(truncatedFile, StandardOpenOption.WRITE)) {
            channel.truncate(channel.size() - 1L);
        }
        FieldArtifactDescriptorV2 truncatedChecksum = withArtifactChecksum(
                truncated, Sha256.file(truncatedFile));
        assertThrows(IOException.class,
                () -> LfcGridReaderV1.open(root.resolve("truncated"), truncatedChecksum));

        Path budgetRoot = root.resolve("budget");
        assertThrows(IOException.class, () -> writer.write(
                budgetRoot, "constraints/height.lfgrid", definition, provenance(),
                (x, z) -> 0, () -> false,
                new LfcGridWriterV1.WriteLimits(200L, 128L * 1024L)
        ));
        assertFalse(Files.exists(budgetRoot.resolve("constraints/height.lfgrid")));

        Path cancelledRoot = root.resolve("cancelled");
        AtomicInteger checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> writer.write(
                cancelledRoot, "constraints/height.lfgrid", definition, provenance(),
                (x, z) -> z * 16 + x, () -> checks.incrementAndGet() > 3
        ));
        Path target = cancelledRoot.resolve("constraints/height.lfgrid");
        assertFalse(Files.exists(target));
        try (var files = Files.list(target.getParent())) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".lfc-grid-")));
        }

        assertThrows(IllegalArgumentException.class, () -> writer.write(
                root.resolve("path"), "../escape.lfgrid", definition, provenance(),
                (x, z) -> 0, () -> false
        ));
    }

    private static int[] readTiles(
            LfcGridReaderV1 reader,
            List<WindowCoordinate> coordinates,
            int threads,
            int width,
            int length
    ) throws Exception {
        int[] result = new int[Math.multiplyExact(width, length)];
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (WindowCoordinate coordinate : coordinates) {
                futures.add(executor.submit(() -> {
                    FieldWindow window = reader.readWindow(
                            coordinate.x(), coordinate.z(), coordinate.width(), coordinate.length()
                    );
                    for (int localZ = 0; localZ < coordinate.length(); localZ++) {
                        for (int localX = 0; localX < coordinate.width(); localX++) {
                            int globalX = coordinate.x() + localX;
                            int globalZ = coordinate.z() + localZ;
                            result[globalZ * width + globalX] = window.rawValueAt(localX, localZ);
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
        return result;
    }

    private static List<WindowCoordinate> windows(int width, int length, int tileSize) {
        List<WindowCoordinate> result = new ArrayList<>();
        for (int z = 0; z < length; z += tileSize) {
            for (int x = 0; x < width; x += tileSize) {
                result.add(new WindowCoordinate(
                        x, z, Math.min(tileSize, width - x), Math.min(tileSize, length - z)
                ));
            }
        }
        return List.copyOf(result);
    }

    private static FieldArtifactDescriptorV2.Definition definition(
            String id,
            FieldArtifactDescriptorV2.FieldSemantic semantic,
            FieldArtifactDescriptorV2.FieldValueType valueType,
            int width,
            int length,
            FieldArtifactDescriptorV2.Sampling sampling,
            long scale,
            long offset,
            boolean hasNoData,
            int noData
    ) {
        return new FieldArtifactDescriptorV2.Definition(
                id, semantic, valueType, width, length,
                FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                sampling, scale, offset, hasNoData, noData
        );
    }

    private static FieldArtifactDescriptorV2.Provenance provenance() {
        return new FieldArtifactDescriptorV2.Provenance(
                FieldArtifactDescriptorV2.SourceKind.CONSTRAINT_MAP,
                "constraint-source:test-map",
                SOURCE_CHECKSUM,
                "png.numeric",
                "1",
                "orientation-v1"
        );
    }

    private static FieldArtifactDescriptorV2.Provenance derivedProvenance() {
        return new FieldArtifactDescriptorV2.Provenance(
                FieldArtifactDescriptorV2.SourceKind.DERIVED,
                "derived-source:test-field",
                SOURCE_CHECKSUM,
                "field.kernel",
                "1",
                "identity-v1"
        );
    }

    private static FieldArtifactDescriptorV2 withArtifactChecksum(
            FieldArtifactDescriptorV2 descriptor,
            String artifactChecksum
    ) {
        return new FieldArtifactDescriptorV2(
                descriptor.relativePath(), descriptor.definition(), descriptor.encodingVersion(),
                artifactChecksum, descriptor.semanticChecksum(), descriptor.provenance()
        );
    }

    private record WindowCoordinate(int x, int z, int width, int length) {
    }
}
