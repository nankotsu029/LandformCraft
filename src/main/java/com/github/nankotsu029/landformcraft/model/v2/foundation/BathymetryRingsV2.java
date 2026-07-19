package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.List;

/** Shared closed-ring geometry for V2-9-08 bathymetry foundation plans. */
public final class BathymetryRingsV2 {
    private BathymetryRingsV2() {
    }

    public record Vertex(long xMillionths, long zMillionths) {
        public Vertex {
            if (xMillionths < 0 || zMillionths < 0) {
                throw new IllegalArgumentException("bathymetry vertex is invalid");
            }
        }
    }

    public record Ring(List<Vertex> vertices) {
        public Ring {
            vertices = FoundationValidationV2.immutable(vertices, "vertices", 2_048);
        }
    }
}
