package com.github.nankotsu029.landformcraft.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record TerrainIntent(
        int schemaVersion,
        String theme,
        Topology topology,
        Set<CardinalDirection> seaSides,
        double landRatio,
        ReliefIntent relief,
        CoastlineIntent coastline,
        WaterIntent water,
        List<TerrainZone> zones,
        List<StructureIntent> structures
) {
    public static final int MAX_THEME_LENGTH = 1_000;
    public static final int MAX_ZONES = 64;
    public static final int MAX_STRUCTURE_INTENTS = 64;
    public static final int MAX_TOTAL_STRUCTURES = 256;

    public TerrainIntent {
        schemaVersion = ModelValidation.requireSchemaVersion(schemaVersion, "schemaVersion");
        theme = ModelValidation.requireNonBlank(theme, "theme", MAX_THEME_LENGTH);
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(seaSides, "seaSides");
        seaSides = seaSides.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(seaSides));
        landRatio = ModelValidation.requireUnitInterval(landRatio, "landRatio");
        Objects.requireNonNull(relief, "relief");
        Objects.requireNonNull(coastline, "coastline");
        Objects.requireNonNull(water, "water");
        zones = ModelValidation.immutableList(zones, "zones", MAX_ZONES);
        structures = ModelValidation.immutableList(structures, "structures", MAX_STRUCTURE_INTENTS);

        Set<String> zoneIds = zones.stream()
                .map(TerrainZone::id)
                .collect(Collectors.toUnmodifiableSet());
        if (zoneIds.size() != zones.size()) {
            throw new IllegalArgumentException("zone ids must be unique");
        }
        for (StructureIntent structure : structures) {
            if (!zoneIds.contains(structure.preferredZone())) {
                throw new IllegalArgumentException("structure references unknown zone: " + structure.preferredZone());
            }
        }
        int totalStructures = structures.stream().mapToInt(StructureIntent::count).sum();
        if (totalStructures > MAX_TOTAL_STRUCTURES) {
            throw new IllegalArgumentException("total structure count must not exceed " + MAX_TOTAL_STRUCTURES);
        }
        double totalAreaShare = zones.stream().mapToDouble(TerrainZone::areaShare).sum();
        if (totalAreaShare > 1.0 + 1.0e-9) {
            throw new IllegalArgumentException("zone area shares must not total more than 1.0");
        }
        validateTopology(topology, seaSides, water);
    }

    private static void validateTopology(Topology topology, Set<CardinalDirection> seaSides, WaterIntent water) {
        if (topology == Topology.INLAND && !seaSides.isEmpty()) {
            throw new IllegalArgumentException("INLAND topology must not declare sea sides");
        }
        if ((topology == Topology.COAST || topology == Topology.COAST_WITH_RIVER) && seaSides.isEmpty()) {
            throw new IllegalArgumentException("coastal topology must declare at least one sea side");
        }
        if ((topology == Topology.COAST_WITH_RIVER || topology == Topology.RIVER_VALLEY)
                && water.riverCount() == 0) {
            throw new IllegalArgumentException(topology + " topology requires at least one river");
        }
        if (topology == Topology.LAKE_DISTRICT && water.lakeCount() == 0) {
            throw new IllegalArgumentException("LAKE_DISTRICT topology requires at least one lake");
        }
    }
}
