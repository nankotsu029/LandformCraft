package com.github.nankotsu029.landformcraft.integration.v2.conformance;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * V2-19-07 block-column conformance for the macro foundation producer tier, measured from the
 * <em>published</em> Release alone: the final canonical block stream, the ACTUAL land-water sidecar,
 * the sealed request and the sealed intent. Nothing here reads in-process generator state, so the
 * measurements describe the artifact an operator would place.
 *
 * <p>The producer's claim is that it <em>replaces the background it owns</em> (ADR 0038 D1-3): the
 * cells it owns stand at the elevation the intent declares, every other background cell keeps the
 * request's per-medium base level, and no cell a surface modifier owns is touched. Each of the three
 * is measured separately so a regression cannot hide in an aggregate.</p>
 */
final class PlainBlockConformanceV2 {
    private PlainBlockConformanceV2() {
    }

    /**
     * @param raisedCells background land columns standing above the declared land base level — the
     *        producer's measured footprint in the published block stream
     * @param raisedCellsInsideDeclaredExtent how many of those lie inside the declared polygon's own
     *        bounding box; a producer that leaks outside its declaration shows up as a shortfall
     * @param raisedCellsWithinDeclaredBand how many stand inside
     *        {@code waterLevel + baseElevation + [microMin, microMax]}
     * @param raisedCellsSolidToSurface how many are solid from the bedrock floor up to their surface
     * @param raisedCellsDryColumn how many carry no fluid block at all (a PLAIN is terrestrial)
     * @param backgroundLandCellsAtBaseLevel unraised background land columns standing exactly at the
     *        declared {@code landSurfaceY}
     * @param backgroundWaterCellsAtBedLevel background water columns standing exactly at the declared
     *        {@code waterBedY}
     * @param raisedCellsBySurfaceY histogram of the producer's surface heights, so a collapse to one
     *        constant height is visible rather than averaged away
     */
    record MeasurementsV2(
            String featureId,
            int waterLevel,
            int declaredMinimumSurfaceY,
            int declaredMaximumSurfaceY,
            int backgroundLandCells,
            int backgroundWaterCells,
            int raisedCells,
            int raisedCellsInsideDeclaredExtent,
            int raisedCellsWithinDeclaredBand,
            int raisedCellsSolidToSurface,
            int raisedCellsDryColumn,
            int backgroundLandCellsAtBaseLevel,
            int backgroundWaterCellsAtBedLevel,
            Map<Integer, Integer> raisedCellsBySurfaceY
    ) {
        MeasurementsV2 {
            Objects.requireNonNull(featureId, "featureId");
            raisedCellsBySurfaceY = Map.copyOf(raisedCellsBySurfaceY);
        }
    }

    /** Measures one published surface Release carrying exactly one declared {@code PLAIN} feature. */
    static MeasurementsV2 measure(Path release) throws IOException {
        LandformV2DataCodec codec = new LandformV2DataCodec();
        WorldBlueprintV2 blueprint = IntentConformancePortfolioV2.blueprintOf(release);
        TerrainIntentV2 intent = IntentConformancePortfolioV2.intentOf(release);
        GenerationRequestV2 request =
                codec.readGenerationRequest(release.resolve("source/generation-request.json"));
        GenerationRequestV2.FoundationBaseLevels baseLevels = request.foundationBaseLevels()
                .orElseThrow(() -> new IOException("the case declares no foundation base levels"));

        int width = blueprint.space().bounds().width();
        int length = blueprint.space().bounds().length();
        int waterLevel = blueprint.space().bounds().waterLevel();
        TerrainIntentV2.Feature feature = intent.features().stream()
                .filter(candidate -> candidate.kind() == TerrainIntentV2.FeatureKind.PLAIN)
                .reduce((first, second) -> {
                    throw new IllegalStateException("the case declares more than one PLAIN feature");
                })
                .orElseThrow(() -> new IllegalStateException("the case declares no PLAIN feature"));
        TerrainIntentV2.PlainParameters parameters =
                (TerrainIntentV2.PlainParameters) feature.parameters();
        // The stage's declared datum: base elevation is measured above the request's water level, and
        // the micro relief band widens it. Both numbers come from the sealed intent, never from the
        // generator that produced the blocks.
        int baseElevation = midpoint(parameters.baseElevationAboveDatumBlocks());
        int minimumSurfaceY = waterLevel + baseElevation + parameters.microReliefBlocks().minimum();
        int maximumSurfaceY = waterLevel + baseElevation + parameters.microReliefBlocks().maximum();

        boolean[] background = IntentConformancePortfolioV2.backgroundCells(blueprint, width, length);
        int[] land = IntentConformancePortfolioV2.readPublishedField(
                release, FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER);
        FeatureMaterializationV2.FinalBlockStreamV2 stream =
                FeatureMaterializationV2.readFinalBlockStream(release);
        int[] extent = boundingBox(feature, width, length);

        int backgroundLandCells = 0;
        int backgroundWaterCells = 0;
        int raised = 0;
        int raisedInsideExtent = 0;
        int raisedWithinBand = 0;
        int raisedSolid = 0;
        int raisedDry = 0;
        int landAtBaseLevel = 0;
        int waterAtBedLevel = 0;
        Map<Integer, Integer> byHeight = new TreeMap<>();
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                if (!background[index]) {
                    continue;
                }
                int surface = surfaceY(stream, x, z);
                if (land[index] != IntentConformancePortfolioV2.LAND) {
                    backgroundWaterCells++;
                    if (surface == baseLevels.waterBedY()) {
                        waterAtBedLevel++;
                    }
                    continue;
                }
                backgroundLandCells++;
                if (surface == baseLevels.landSurfaceY()) {
                    landAtBaseLevel++;
                    continue;
                }
                raised++;
                byHeight.merge(surface, 1, Integer::sum);
                if (x >= extent[0] && x <= extent[2] && z >= extent[1] && z <= extent[3]) {
                    raisedInsideExtent++;
                }
                if (surface >= minimumSurfaceY && surface <= maximumSurfaceY) {
                    raisedWithinBand++;
                }
                if (isSolidToSurface(stream, x, z, surface)) {
                    raisedSolid++;
                }
                if (isDryColumn(stream, x, z)) {
                    raisedDry++;
                }
            }
        }
        return new MeasurementsV2(feature.id(), waterLevel, minimumSurfaceY, maximumSurfaceY,
                backgroundLandCells, backgroundWaterCells, raised, raisedInsideExtent,
                raisedWithinBand, raisedSolid, raisedDry, landAtBaseLevel, waterAtBedLevel, byHeight);
    }

    /** Topmost solid block of one published column, or the bedrock floor when none stands above it. */
    static int surfaceY(FeatureMaterializationV2.FinalBlockStreamV2 stream, int x, int z) {
        for (int y = stream.maxY(); y > stream.minY(); y--) {
            if (stream.isSolid(x, y, z)) {
                return y;
            }
        }
        return stream.minY();
    }

    private static boolean isSolidToSurface(
            FeatureMaterializationV2.FinalBlockStreamV2 stream, int x, int z, int surface) {
        for (int y = stream.minY(); y <= surface; y++) {
            if (!stream.isSolid(x, y, z)) {
                return false;
            }
        }
        return surface < stream.maxY() && stream.isAir(x, surface + 1, z);
    }

    private static boolean isDryColumn(
            FeatureMaterializationV2.FinalBlockStreamV2 stream, int x, int z) {
        for (int y = stream.minY(); y <= stream.maxY(); y++) {
            if (stream.isFluid(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    /** The declared polygon's bounding box in release-local cells: {minX, minZ, maxX, maxZ}. */
    private static int[] boundingBox(TerrainIntentV2.Feature feature, int width, int length) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (TerrainIntentV2.Point2 point
                : ((TerrainIntentV2.PolygonGeometry) feature.geometry()).rings().getFirst()) {
            int x = Math.toIntExact(point.xMillionths() * (width - 1L) / TerrainIntentV2.FIXED_SCALE);
            int z = Math.toIntExact(point.zMillionths() * (length - 1L) / TerrainIntentV2.FIXED_SCALE);
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
        }
        return new int[] {minX, minZ, maxX, maxZ};
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }
}
