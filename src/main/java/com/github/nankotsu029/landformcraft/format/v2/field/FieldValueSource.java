package com.github.nankotsu029.landformcraft.format.v2.field;

/** Supplies one raw row-major field value at a time without requiring a full-grid allocation. */
@FunctionalInterface
public interface FieldValueSource {
    int rawValueAt(int x, int z);
}
