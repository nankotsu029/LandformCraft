package com.github.nankotsu029.landformcraft.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Portable list of validated structure placements materialized into terrain tiles. */
public record StructurePlacementManifest(
        int schemaVersion,
        String generatorVersion,
        List<StructurePlan> structures
) {
    public StructurePlacementManifest {
        schemaVersion = ModelValidation.requireSchemaVersion(schemaVersion, "schemaVersion");
        generatorVersion = ModelValidation.requireNonBlank(generatorVersion, "generatorVersion", 128);
        structures = ModelValidation.immutableList(structures, "structures", 256);
        Set<String> placements = new HashSet<>();
        for (StructurePlan structure : structures) {
            String key = structure.anchorX() + ":" + structure.anchorY() + ":" + structure.anchorZ();
            if (!placements.add(key)) {
                throw new IllegalArgumentException("structure anchors must be unique");
            }
        }
    }
}
