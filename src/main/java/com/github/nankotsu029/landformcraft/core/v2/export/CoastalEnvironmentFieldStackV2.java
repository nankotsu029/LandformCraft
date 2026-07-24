package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.v2.material.EnvironmentSurfaceMaterialV2;
import com.github.nankotsu029.landformcraft.generator.v2.climate.ClimateFieldSamplerV2;
import com.github.nankotsu029.landformcraft.generator.v2.ecology.EcologyPlacementResolverV2;
import com.github.nankotsu029.landformcraft.generator.v2.environment.snow.SnowFieldSamplerV2;
import com.github.nankotsu029.landformcraft.generator.v2.environment.water.WaterConditionFieldSamplerV2;
import com.github.nankotsu029.landformcraft.generator.v2.geology.GeologyFieldSamplerV2;
import com.github.nankotsu029.landformcraft.generator.v2.geology.strata.StrataExposureResolverV2;
import com.github.nankotsu029.landformcraft.generator.v2.hydrology.core.HydrologyRoutingResultV2;
import com.github.nankotsu029.landformcraft.generator.v2.material.MaterialProfileResolverV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.ecology.EcologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.ClimatePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.GeologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.LithologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.SnowPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.StrataPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.environment.WaterConditionPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.material.MaterialProfilePlanV2;
import com.github.nankotsu029.landformcraft.validation.v2.environment.EnvironmentCellSnapshotV2;
import com.github.nankotsu029.landformcraft.validation.v2.environment.EnvironmentFieldSamplerV2;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * V2-19-10 shared environment field stack of the coastal production surface
 * ({@code coastal-environment-field-stack-v1}).
 *
 * <p>The 2026-07-23 cross-cutting audit (§2.8) recorded that the shared {@code environment-fields}
 * pipeline validated a <em>constant</em> {@code healthy} snapshot: every cell returned the same
 * literal, so "environment validation passed" said nothing about the terrain. This stack replaces
 * that literal. Every value is sampled from the sealed plans of the very Blueprint being published,
 * through the existing V2-4 samplers and resolvers, over the surface the coastal stage actually
 * generated:</p>
 *
 * <ul>
 *   <li>surface elevation and land／water come from {@link CoastalSurfaceFieldsV2};</li>
 *   <li>the marine distance is a bounded multi-source BFS from the water cells that reach the domain
 *       boundary, clamped to the water-condition kernel's declared support;</li>
 *   <li>flow accumulation comes from the frozen hydrology routing result of the same run;</li>
 *   <li>temperature／moisture, wetness／salinity／hydroperiod, snow cover, the semantic material class
 *       and the habitat class come from {@link ClimateFieldSamplerV2},
 *       {@link WaterConditionFieldSamplerV2}, {@link SnowFieldSamplerV2},
 *       {@link MaterialProfileResolverV2} and {@link EcologyPlacementResolverV2}.</li>
 * </ul>
 *
 * <p>Two sampler inputs have no field of their own in the shared pipeline and are stated here as an
 * explicit, bounded derivation rather than invented per call: the <b>topographic exposure</b> proxy
 * (height above the request water level, saturating at 40 blocks) feeds climate exposure and both
 * snow exposure inputs — separate wind and insolation models are not implemented and are not
 * fabricated — and the <b>slope</b> proxy is the largest four-neighbour surface-height step,
 * saturating at 8 blocks. Feature-scoped inputs that no declared plan provides (river／lake／tidal
 * proximity, tidal range, freshwater discharge, wetland／reef／island／canyon masks) stay at their
 * "no such declared Feature" value; they are not guessed from the coast.</p>
 *
 * <p>Values are computed per call from fixed-size state plus one bounded distance field, so no dense
 * per-field grid is retained. The stack is pure: the same Blueprint, fields and routing result always
 * yield the same snapshot, independently of tile order, thread count, locale and timezone.</p>
 */
final class CoastalEnvironmentFieldStackV2 implements EnvironmentFieldSamplerV2 {
    static final String CONTRACT_VERSION = "coastal-environment-field-stack-v1";

    /** Blocks above the request water level at which the topographic exposure proxy saturates. */
    private static final int EXPOSURE_SATURATION_BLOCKS = 40;
    /** Neighbour surface-height step in blocks at which the slope proxy saturates. */
    private static final int SLOPE_SATURATION_BLOCKS = 8;
    private static final int RAW_MAXIMUM = 1_000;

    private final int width;
    private final int length;
    private final int minY;
    private final int maxY;
    private final int waterLevel;
    private final long cells;

    private final CoastalSurfaceFieldsV2 fields;
    private final HydrologyRoutingResultV2 routing;
    private final WaterConditionPlanV2 waterPlan;
    private final MaterialProfilePlanV2 materialPlan;
    private final ClimateFieldSamplerV2 climate;
    private final WaterConditionFieldSamplerV2 water;
    private final SnowFieldSamplerV2 snow;
    private final MaterialProfileResolverV2 material;
    private final EcologyPlacementResolverV2 ecology;
    private final StrataExposureResolverV2 strata;
    private final int provinceRaw;
    private final int permeabilityRaw;
    /** Unclamped BFS distance in blocks to the nearest boundary-connected water cell. */
    private final int[] marineDistance;

    private CoastalEnvironmentFieldStackV2(Builder builder) {
        this.width = builder.width;
        this.length = builder.length;
        this.minY = builder.minY;
        this.maxY = builder.maxY;
        this.waterLevel = builder.waterLevel;
        this.cells = Math.multiplyExact((long) builder.width, builder.length);
        this.fields = builder.fields;
        this.routing = builder.routing;
        this.waterPlan = builder.waterPlan;
        this.materialPlan = builder.materialPlan;
        this.climate = builder.climate;
        this.water = builder.water;
        this.snow = builder.snow;
        this.material = builder.material;
        this.ecology = builder.ecology;
        this.strata = builder.strata;
        this.provinceRaw = builder.provinceRaw;
        this.permeabilityRaw = builder.permeabilityRaw;
        this.marineDistance = builder.marineDistance;
    }

    /** One int per cell for the bounded marine distance field; nothing else is retained densely. */
    static long estimatedResidentBytes(int width, int length) {
        return Math.addExact(
                Math.multiplyExact(Math.multiplyExact((long) width, length), (long) Integer.BYTES),
                EnvironmentSurfaceMaterialV2.estimatedResidentBytes(width, length));
    }

    static CoastalEnvironmentFieldStackV2 create(
            GenerationRequestV2.Bounds bounds,
            CoastalSurfaceFieldsV2 fields,
            HydrologyRoutingResultV2 routing,
            GeologyPlanV2 geologyPlan,
            LithologyPlanV2 lithologyPlan,
            StrataPlanV2 strataPlan,
            ClimatePlanV2 climatePlan,
            WaterConditionPlanV2 waterPlan,
            SnowPlanV2 snowPlan,
            MaterialProfilePlanV2 materialPlan,
            EcologyPlanV2 ecologyPlan
    ) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(routing, "routing");
        Builder builder = new Builder();
        builder.width = bounds.width();
        builder.length = bounds.length();
        builder.minY = bounds.minY();
        builder.maxY = bounds.maxY();
        builder.waterLevel = bounds.waterLevel();
        builder.fields = fields;
        builder.routing = routing;
        builder.waterPlan = waterPlan;
        builder.materialPlan = materialPlan;
        builder.climate = new ClimateFieldSamplerV2(climatePlan);
        builder.water = new WaterConditionFieldSamplerV2(waterPlan);
        builder.snow = new SnowFieldSamplerV2(snowPlan);
        builder.material = new MaterialProfileResolverV2(
                geologyPlan, lithologyPlan, strataPlan, waterPlan, snowPlan, materialPlan);
        builder.ecology = new EcologyPlacementResolverV2(
                climatePlan, waterPlan, snowPlan, ecologyPlan);
        builder.strata = new StrataExposureResolverV2(geologyPlan, lithologyPlan, strataPlan);
        GeologyFieldSamplerV2 geology = new GeologyFieldSamplerV2(geologyPlan);
        builder.provinceRaw = geology.rawValueAt(GeologyPlanV2.FieldSemantic.PROVINCE_ID, 0, 0);
        if (builder.provinceRaw == GeologyPlanV2.NO_DATA_RAW) {
            throw new IllegalArgumentException(
                    "environment field stack requires a resolved geology province; the Blueprint declares none");
        }
        builder.permeabilityRaw = geology.rawValueAt(GeologyPlanV2.FieldSemantic.PERMEABILITY, 0, 0);
        builder.marineDistance = marineDistanceField(fields, builder.width, builder.length);
        return new CoastalEnvironmentFieldStackV2(builder);
    }

    @Override
    public EnvironmentCellSnapshotV2 at(int globalX, int globalZ) {
        requireInBounds(globalX, globalZ);
        int index = globalZ * width + globalX;
        int surfaceY = clamp(fields.surfaceYAt(index), minY, maxY);
        int landWater = fields.landWaterAt(index);
        int exposureRaw = exposureRaw(surfaceY);
        int slopeRaw = slopeRaw(globalX, globalZ);

        ClimateFieldSamplerV2.FinalInputs climateInputs = new ClimateFieldSamplerV2.FinalInputs(
                surfaceY,
                Math.toIntExact((long) exposureRaw * (ClimatePlanV2.FIXED_SCALE / RAW_MAXIMUM)),
                flowAccumulationMillionths(globalX, globalZ));
        int temperatureRaw = climate.rawValueAt(
                ClimatePlanV2.FieldSemantic.FINAL_TEMPERATURE, globalX, globalZ, climateInputs);
        int moistureRaw = climate.rawValueAt(
                ClimatePlanV2.FieldSemantic.FINAL_MOISTURE, globalX, globalZ, climateInputs);

        int distance = marineDistance[index];
        boolean marineConnected = distance <= waterPlan.kernel().maximumDistanceBlocks();
        int seaDistanceBlocks = Math.min(distance, waterPlan.kernel().maximumDistanceBlocks());
        WaterConditionFieldSamplerV2.CellInputs waterInputs = WaterConditionFieldSamplerV2.CellInputs.of(
                surfaceY,
                moistureRaw,
                waterPlan.kernel().maximumDistanceBlocks(),
                waterPlan.kernel().maximumDistanceBlocks(),
                waterPlan.kernel().maximumDistanceBlocks(),
                seaDistanceBlocks,
                marineConnected,
                0,
                0,
                permeabilityRaw);
        int wetnessRaw = water.rawValueAt(
                WaterConditionPlanV2.FieldSemantic.WETNESS, globalX, globalZ, waterInputs);
        int salinityRaw = water.rawValueAt(
                WaterConditionPlanV2.FieldSemantic.SALINITY, globalX, globalZ, waterInputs);
        int hydroperiodRaw = water.rawValueAt(
                WaterConditionPlanV2.FieldSemantic.HYDROPERIOD, globalX, globalZ, waterInputs);

        int snowCoverRaw = snow.rawValueAt(
                SnowPlanV2.FieldSemantic.SNOW_COVER, globalX, globalZ,
                new SnowFieldSamplerV2.CellInputs(
                        surfaceY, temperatureRaw, moistureRaw, slopeRaw, exposureRaw, exposureRaw));

        int materialClassCode = material.classCodeAt(
                MaterialProfilePlanV2.SurfaceAspect.SURFACE, globalX, globalZ,
                new MaterialProfileResolverV2.CellInputs(provinceRaw, wetnessRaw, snowCoverRaw));
        int openWaterGap = landWater == 0 ? 1 : 0;
        int substrateWet = wetnessRaw >= materialPlan.kernel().wetnessThresholdRaw() ? 1 : 0;
        int habitatCode = ecology.habitatCodeAt(globalX, globalZ,
                new EcologyPlacementResolverV2.CellInputs(
                        temperatureRaw, wetnessRaw, salinityRaw, hydroperiodRaw, snowCoverRaw,
                        0, openWaterGap, substrateWet, 0, 0, 0, surfaceY));

        return new EnvironmentCellSnapshotV2(
                temperatureRaw,
                moistureRaw,
                wetnessRaw,
                salinityRaw,
                hydroperiodRaw,
                snowCoverRaw,
                habitatCode,
                materialClassCode,
                // No volcanic or canyon plan can reach the shared pipeline, so there is no
                // feature-scoped material class, island mask, canyon mask or wall height to report.
                0,
                strata.exposedLithologyCode(provinceRaw, globalX, globalZ),
                0,
                openWaterGap,
                substrateWet,
                0,
                0,
                0,
                0,
                0);
    }

    /** The per-cell semantic material class the environment surface material binds to a block. */
    EnvironmentSurfaceMaterialV2.MaterialClassSourceV2 materialClasses() {
        return (x, z) -> at(x, z).materialClassCode();
    }

    private int exposureRaw(int surfaceY) {
        int above = Math.max(0, surfaceY - waterLevel);
        return clamp(Math.toIntExact(
                Math.min((long) RAW_MAXIMUM,
                        (long) above * RAW_MAXIMUM / EXPOSURE_SATURATION_BLOCKS)), 0, RAW_MAXIMUM);
    }

    private int slopeRaw(int globalX, int globalZ) {
        int here = fields.surfaceYAt(globalZ * width + globalX);
        int step = 0;
        if (globalX > 0) {
            step = Math.max(step, Math.abs(here - fields.surfaceYAt(globalZ * width + globalX - 1)));
        }
        if (globalX + 1 < width) {
            step = Math.max(step, Math.abs(here - fields.surfaceYAt(globalZ * width + globalX + 1)));
        }
        if (globalZ > 0) {
            step = Math.max(step, Math.abs(here - fields.surfaceYAt((globalZ - 1) * width + globalX)));
        }
        if (globalZ + 1 < length) {
            step = Math.max(step, Math.abs(here - fields.surfaceYAt((globalZ + 1) * width + globalX)));
        }
        return clamp(Math.toIntExact(
                Math.min((long) RAW_MAXIMUM,
                        (long) step * RAW_MAXIMUM / SLOPE_SATURATION_BLOCKS)), 0, RAW_MAXIMUM);
    }

    private int flowAccumulationMillionths(int globalX, int globalZ) {
        long accumulation = Math.max(0, routing.flowAccumulationAt(globalX, globalZ));
        return Math.toIntExact(Math.min(ClimatePlanV2.FIXED_SCALE,
                Math.multiplyExact(accumulation, ClimatePlanV2.FIXED_SCALE) / cells));
    }

    /**
     * Bounded multi-source BFS from every water cell that belongs to a water component touching the
     * domain boundary. An enclosed pond is not marine and does not seed the field; a domain with no
     * boundary water leaves every distance unreachable rather than inventing a coastline.
     */
    private static int[] marineDistanceField(CoastalSurfaceFieldsV2 fields, int width, int length) {
        int cells = Math.multiplyExact(width, length);
        boolean[] marine = new boolean[cells];
        Deque<Integer> queue = new ArrayDeque<>();
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                boolean boundary = x == 0 || z == 0 || x == width - 1 || z == length - 1;
                int index = z * width + x;
                if (boundary && fields.landWaterAt(index) == 0 && !marine[index]) {
                    marine[index] = true;
                    queue.add(index);
                }
            }
        }
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            forEachNeighbour(index, width, length, neighbour -> {
                if (!marine[neighbour] && fields.landWaterAt(neighbour) == 0) {
                    marine[neighbour] = true;
                    queue.add(neighbour);
                }
            });
        }

        int[] distance = new int[cells];
        Arrays.fill(distance, Integer.MAX_VALUE);
        Deque<Integer> frontier = new ArrayDeque<>();
        for (int index = 0; index < cells; index++) {
            if (marine[index]) {
                distance[index] = 0;
                frontier.add(index);
            }
        }
        while (!frontier.isEmpty()) {
            int index = frontier.removeFirst();
            int next = distance[index] + 1;
            forEachNeighbour(index, width, length, neighbour -> {
                if (distance[neighbour] > next) {
                    distance[neighbour] = next;
                    frontier.add(neighbour);
                }
            });
        }
        return distance;
    }

    private static void forEachNeighbour(int index, int width, int length, IntConsumer action) {
        int x = index % width;
        int z = index / width;
        if (x > 0) action.accept(index - 1);
        if (x + 1 < width) action.accept(index + 1);
        if (z > 0) action.accept(index - width);
        if (z + 1 < length) action.accept(index + width);
    }

    private void requireInBounds(int globalX, int globalZ) {
        if (globalX < 0 || globalX >= width || globalZ < 0 || globalZ >= length) {
            throw new IllegalArgumentException("environment sample is outside release-local bounds");
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Builder {
        private int width;
        private int length;
        private int minY;
        private int maxY;
        private int waterLevel;
        private CoastalSurfaceFieldsV2 fields;
        private HydrologyRoutingResultV2 routing;
        private WaterConditionPlanV2 waterPlan;
        private MaterialProfilePlanV2 materialPlan;
        private ClimateFieldSamplerV2 climate;
        private WaterConditionFieldSamplerV2 water;
        private SnowFieldSamplerV2 snow;
        private MaterialProfileResolverV2 material;
        private EcologyPlacementResolverV2 ecology;
        private StrataExposureResolverV2 strata;
        private int provinceRaw;
        private int permeabilityRaw;
        private int[] marineDistance;
    }
}
