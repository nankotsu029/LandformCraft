package com.github.nankotsu029.landformcraft.generator.v2.coast;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.field.FieldWindow;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridReaderV1;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;

import java.io.IOException;
import java.util.Objects;

/** Strict bounded-window adapter from an LFC_GRID_V1 categorical field to HARD coast ownership. */
public final class CoastalHardMaskWindowV2 implements HardLandWaterSourceV2 {
    private final FieldWindow window;

    private CoastalHardMaskWindowV2(FieldWindow window) {
        this.window = window;
    }

    public static CoastalHardMaskWindowV2 read(
            LfcGridReaderV1 reader,
            CoastalRasterKernelV2 kernel,
            int coreOriginX,
            int coreOriginZ,
            int coreWidth,
            int coreLength,
            int haloXZ,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(token, "token");
        requireHardMaskDefinition(reader.descriptor().definition(), kernel.width(), kernel.length());
        CoastalRasterWindowV2.Bounds bounds = kernel.windowBounds(
                coreOriginX, coreOriginZ, coreWidth, coreLength, haloXZ);
        FieldWindow window = reader.readWindow(
                bounds.originX(), bounds.originZ(), bounds.width(), bounds.length(), token);
        for (int z = 0; z < window.length(); z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < window.width(); x++) {
                int raw = window.rawValueAt(x, z);
                if (window.isNoDataAt(x, z)) {
                    throw new CoastalRasterException(
                            "v2.coastal-hard-mask-no-data", "HARD LAND_WATER_MASK contains no-data");
                }
                if (raw != 0 && raw != 1) {
                    throw new CoastalRasterException(
                            "v2.coastal-hard-mask-label", "HARD LAND_WATER_MASK contains a value other than 0 or 1");
                }
            }
        }
        return new CoastalHardMaskWindowV2(window);
    }

    @Override
    public Classification classificationAt(int globalX, int globalZ) {
        int localX = globalX - window.originX();
        int localZ = globalZ - window.originZ();
        if (localX < 0 || localX >= window.width() || localZ < 0 || localZ >= window.length()) {
            throw new IndexOutOfBoundsException("coordinate outside loaded HARD LAND_WATER_MASK window");
        }
        return window.rawValueAt(localX, localZ) == 1 ? Classification.LAND : Classification.WATER;
    }

    private static void requireHardMaskDefinition(
            FieldArtifactDescriptorV2.Definition definition,
            int width,
            int length
    ) {
        boolean semantic = definition.semantic() == FieldArtifactDescriptorV2.FieldSemantic.LAND_WATER_MASK
                || definition.semantic() == FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER;
        if (!semantic
                || definition.valueType() != FieldArtifactDescriptorV2.FieldValueType.U8
                || definition.coordinateSpace() != FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ
                || definition.sampling() != FieldArtifactDescriptorV2.Sampling.NEAREST
                || definition.width() != width
                || definition.length() != length
                || definition.scaleMillionths() != FieldArtifactDescriptorV2.FIXED_SCALE
                || definition.offsetMillionths() != 0L) {
            throw new CoastalRasterException(
                    "v2.coastal-hard-mask-definition",
                    "HARD LAND_WATER_MASK definition does not match the coastal raster contract");
        }
    }
}
