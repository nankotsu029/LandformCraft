package com.github.nankotsu029.landformcraft.format.v2.field;

import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;

import java.util.Objects;

/** Bounded immutable raw-value window read from an LFC_GRID_V1 sidecar. */
public final class FieldWindow {
    private final int originX;
    private final int originZ;
    private final int width;
    private final int length;
    private final int[] rawValues;
    private final FieldArtifactDescriptorV2.Definition definition;

    FieldWindow(
            int originX,
            int originZ,
            int width,
            int length,
            int[] rawValues,
            FieldArtifactDescriptorV2.Definition definition
    ) {
        if (originX < 0 || originZ < 0 || width < 1 || length < 1
                || (long) width * length != rawValues.length) {
            throw new IllegalArgumentException("invalid field window");
        }
        this.originX = originX;
        this.originZ = originZ;
        this.width = width;
        this.length = length;
        this.rawValues = rawValues;
        this.definition = Objects.requireNonNull(definition, "definition");
    }

    public int originX() {
        return originX;
    }

    public int originZ() {
        return originZ;
    }

    public int width() {
        return width;
    }

    public int length() {
        return length;
    }

    public int rawValueAt(int x, int z) {
        if (x < 0 || x >= width || z < 0 || z >= length) {
            throw new IndexOutOfBoundsException("coordinate outside field window");
        }
        return rawValues[z * width + x];
    }

    public boolean isNoDataAt(int x, int z) {
        return definition.isNoData(rawValueAt(x, z));
    }

    public FieldSample sampleAt(int x, int z) {
        int raw = rawValueAt(x, z);
        return definition.isNoData(raw)
                ? FieldSample.missing()
                : FieldSample.value(definition.semanticValueMillionths(raw));
    }

    public int[] toRawArray() {
        return rawValues.clone();
    }
}
