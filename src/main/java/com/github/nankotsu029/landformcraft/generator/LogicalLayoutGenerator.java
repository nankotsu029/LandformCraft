package com.github.nankotsu029.landformcraft.generator;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.model.CardinalDirection;
import com.github.nankotsu029.landformcraft.model.PreferredArea;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.model.TerrainZone;
import com.github.nankotsu029.landformcraft.model.TerrainZoneType;
import com.github.nankotsu029.landformcraft.model.WorldBlueprint;

import java.util.Objects;

/** Produces the 64x64 or 128x128 global layout before full-resolution sampling. */
public final class LogicalLayoutGenerator {
    public LogicalTerrainLayout generate(WorldBlueprint blueprint, CancellationToken token) {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(token, "token");
        int resolution = blueprint.logicalResolution();
        double[] continental = new double[resolution * resolution];
        double[] relief = new double[continental.length];
        TerrainIntent intent = blueprint.intent();

        for (int z = 0; z < resolution; z++) {
            token.throwIfCancellationRequested();
            double normalizedZ = z / (double) (resolution - 1);
            for (int x = 0; x < resolution; x++) {
                double normalizedX = x / (double) (resolution - 1);
                double lowNoise = DeterministicNoise.fractal(
                        blueprint.seed(), normalizedX * 5.0, normalizedZ * 5.0, 4
                );
                int index = z * resolution + x;
                continental[index] = continentalSignal(intent, normalizedX, normalizedZ, lowNoise);
                double ridged = 1.0 - Math.abs(DeterministicNoise.fractal(
                        blueprint.seed() ^ 0x51EADL, normalizedX * 3.0, normalizedZ * 3.0, 4
                ));
                double zoneModifier = reliefZoneModifier(intent, normalizedX, normalizedZ);
                relief[index] = clamp(
                        intent.relief().average() + lowNoise * 0.20 + (ridged - 0.5) * 0.24 + zoneModifier,
                        intent.relief().minimum(),
                        intent.relief().maximum()
                );
            }
        }
        return new LogicalTerrainLayout(resolution, continental, relief);
    }

    static double zoneScore(TerrainZone zone, double normalizedX, double normalizedZ) {
        return zone.areaShare() * zoneInfluence(zone.preferredArea(), normalizedX, normalizedZ);
    }

    private static double reliefZoneModifier(TerrainIntent intent, double normalizedX, double normalizedZ) {
        double result = 0.0;
        for (TerrainZone zone : intent.zones()) {
            double influence = zone.areaShare() * zoneInfluence(zone.preferredArea(), normalizedX, normalizedZ);
            result += influence * switch (zone.type()) {
                case MOUNTAINS -> 1.0;
                case CLIFFS -> 0.55;
                case ROCKY_COAST -> 0.30;
                case VALLEY -> -0.90;
                case WETLAND -> -0.80;
                case SANDY_BEACH -> -1.0;
                case PLAINS, FOREST -> 0.0;
            };
        }
        return result;
    }

    private static double zoneInfluence(PreferredArea area, double x, double z) {
        if (area == PreferredArea.ANY) {
            return 0.55;
        }
        double centerX = switch (area) {
            case NORTH, CENTER, SOUTH -> 0.5;
            case NORTH_EAST, EAST, SOUTH_EAST -> 0.82;
            case NORTH_WEST, WEST, SOUTH_WEST -> 0.18;
            case ANY -> 0.5;
        };
        double centerZ = switch (area) {
            case WEST, CENTER, EAST -> 0.5;
            case NORTH_WEST, NORTH, NORTH_EAST -> 0.18;
            case SOUTH_WEST, SOUTH, SOUTH_EAST -> 0.82;
            case ANY -> 0.5;
        };
        double squaredDistance = Math.pow(x - centerX, 2.0) + Math.pow(z - centerZ, 2.0);
        return Math.exp(-squaredDistance / 0.10);
    }

    private static double continentalSignal(TerrainIntent intent, double nx, double nz, double detail) {
        return switch (intent.topology()) {
            case INLAND, RIVER_VALLEY, LAKE_DISTRICT -> 1.0;
            case ISLAND -> 1.0 - Math.hypot(nx - 0.5, nz - 0.5) * 1.42 + detail * 0.14;
            case ARCHIPELAGO -> Math.max(
                    0.72 - Math.hypot(nx - 0.33, nz - 0.38) * 2.1,
                    0.68 - Math.hypot(nx - 0.69, nz - 0.62) * 2.0
            ) + detail * 0.22;
            case COAST, COAST_WITH_RIVER -> coastalSignal(intent, nx, nz) + detail * 0.2
                    * intent.coastline().irregularity();
        };
    }

    private static double coastalSignal(TerrainIntent intent, double nx, double nz) {
        double signal = 1.0;
        for (CardinalDirection side : intent.seaSides()) {
            signal = Math.min(signal, switch (side) {
                case NORTH -> nz;
                case EAST -> 1.0 - nx;
                case SOUTH -> 1.0 - nz;
                case WEST -> nx;
            });
        }
        return signal;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
