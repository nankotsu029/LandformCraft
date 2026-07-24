package com.github.nankotsu029.landformcraft.integration.v2.conformance;

import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.TreeSet;

/**
 * V2-19-12 (ADR 0041) block-column conformance for the coherent detail kernel, measured from the two
 * <em>published</em> Releases — the detail case and the otherwise identical baseline — plus their
 * frozen blueprints. Nothing here reads in-process generator state.
 *
 * <p>Detail's claim is narrow (ADR 0041 D2): it replaces the flat per-medium base level on the
 * background cells the macro foundation owns and nothing else. So the measurements separate three
 * things a regression could hide behind an aggregate — the background surface actually varies
 * (non-flat), it varies <em>coherently</em> (bounded adjacent steps, not the whole-range jumps a
 * cell-hash field would give), and no cell a coastal modifier owns changed by a single block.</p>
 */
final class FoundationDetailBlockConformanceV2 {
    private FoundationDetailBlockConformanceV2() {
    }

    /**
     * @param backgroundLandColumns background land columns compared
     * @param backgroundWaterColumns background water columns compared
     * @param distinctLandSurfaceY distinct surface heights across background land columns (a flat
     *        regression collapses this to 1)
     * @param distinctWaterBedY distinct sea-bed heights across background water columns
     * @param maxAdjacentBackgroundStepBlocks the largest 4-neighbour surface-Y step between two
     *        background columns of the same medium (the coherence witness)
     * @param backgroundLandBelowWater background land columns whose surface fell below the water level
     *        (must be zero: ADR 0041 D5 forbids a dry pit)
     * @param backgroundWaterAtOrAboveWater background water columns whose sea bed reached the water
     *        surface (must be zero: no vanished water column)
     * @param modifierColumns coastal-modifier columns compared
     * @param changedModifierColumns modifier columns whose surface Y differs from the baseline (must
     *        be zero: ADR 0041 D2 leaves modifier cells untouched)
     */
    record MeasurementsV2(
            int backgroundLandColumns,
            int backgroundWaterColumns,
            int distinctLandSurfaceY,
            int distinctWaterBedY,
            int maxAdjacentBackgroundStepBlocks,
            int backgroundLandBelowWater,
            int backgroundWaterAtOrAboveWater,
            int modifierColumns,
            int changedModifierColumns
    ) {
    }

    /**
     * Measures the detail Release against the baseline Release. Both are strictly verified when their
     * block streams are read. Pure: the same pair of directories always yields the same record.
     */
    static MeasurementsV2 measure(Path baselineRelease, Path detailRelease) throws IOException {
        WorldBlueprintV2 blueprint = IntentConformancePortfolioV2.blueprintOf(detailRelease);
        int width = blueprint.space().bounds().width();
        int length = blueprint.space().bounds().length();
        int waterLevel = blueprint.space().bounds().waterLevel();
        GenerationRequestV2 request = new com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec()
                .readGenerationRequest(detailRelease.resolve("source/generation-request.json"));
        Objects.requireNonNull(request.foundationDetail().orElseThrow(
                () -> new IOException("the detail case declares no foundation detail")), "detail");

        FeatureMaterializationV2.FinalBlockStreamV2 baseline =
                FeatureMaterializationV2.readFinalBlockStream(baselineRelease);
        FeatureMaterializationV2.FinalBlockStreamV2 detail =
                FeatureMaterializationV2.readFinalBlockStream(detailRelease);
        boolean[] background = IntentConformancePortfolioV2.backgroundCells(blueprint, width, length);
        int[] land = IntentConformancePortfolioV2.readActualLandWater(detailRelease);

        int[] surface = new int[Math.multiplyExact(width, length)];
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                surface[z * width + x] = PlainBlockConformanceV2.surfaceY(detail, x, z);
            }
        }

        TreeSet<Integer> landHeights = new TreeSet<>();
        TreeSet<Integer> waterHeights = new TreeSet<>();
        int backgroundLand = 0;
        int backgroundWater = 0;
        int landBelowWater = 0;
        int waterAtOrAboveWater = 0;
        int modifierColumns = 0;
        int changedModifierColumns = 0;
        int maxStep = 0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                int surfaceY = surface[index];
                if (!background[index]) {
                    modifierColumns++;
                    if (surfaceY != PlainBlockConformanceV2.surfaceY(baseline, x, z)) {
                        changedModifierColumns++;
                    }
                    continue;
                }
                boolean isLand = land[index] == IntentConformancePortfolioV2.LAND;
                if (isLand) {
                    backgroundLand++;
                    landHeights.add(surfaceY);
                    if (surfaceY < waterLevel) {
                        landBelowWater++;
                    }
                } else {
                    backgroundWater++;
                    waterHeights.add(surfaceY);
                    if (surfaceY >= waterLevel) {
                        waterAtOrAboveWater++;
                    }
                }
                // 4-neighbour coherence, only between two background columns of the same medium: a
                // modifier or a medium change is a legitimate step and says nothing about the kernel.
                for (int[] delta : new int[][] {{1, 0}, {0, 1}}) {
                    int nx = x + delta[0];
                    int nz = z + delta[1];
                    if (nx >= width || nz >= length) {
                        continue;
                    }
                    int nIndex = nz * width + nx;
                    if (!background[nIndex]
                            || (land[nIndex] == IntentConformancePortfolioV2.LAND) != isLand) {
                        continue;
                    }
                    maxStep = Math.max(maxStep, Math.abs(surfaceY - surface[nIndex]));
                }
            }
        }
        return new MeasurementsV2(
                backgroundLand, backgroundWater, landHeights.size(), waterHeights.size(),
                maxStep, landBelowWater, waterAtOrAboveWater, modifierColumns, changedModifierColumns);
    }
}
