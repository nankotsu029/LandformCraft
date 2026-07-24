package com.github.nankotsu029.landformcraft.integration.v2.conformance;

import com.github.nankotsu029.landformcraft.generator.v2.landform.canyon.CanyonGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.CanyonPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * V2-15-12 block-column of the intent-conformance portfolio for {@code CANYON}.
 *
 * <p>Everything here is measured from the <em>final canonical block stream</em> of a published
 * Release — the tiles an operator places — against the corridor declared by the published Blueprint.
 * The plan artifacts ({@code hydrology/validation.json}) are deliberately not read: they are the
 * portfolio's separate plan-only column, and V2-19-01 forbids one standing in for the other.</p>
 *
 * <p>A canyon owns no fluid (its shared river alone owns the channel water), so unlike
 * {@link RiverBlockConformanceV2} / {@link LakeBlockConformanceV2} there is no water continuity or
 * leak envelope to measure. Two properties are measured instead:</p>
 * <ol>
 *   <li><b>carved surface</b> — every corridor column is solid exactly at the generator-declared
 *       surface and open to the sky above it, never re-lined below;</li>
 *   <li><b>shared floor containment</b> — the corridor's floor band actually reaches down to the
 *       shared river's own bed at the reach's channel columns, proving the two materializations agree
 *       on the one elevation they share.</li>
 * </ol>
 */
final class CanyonBlockConformanceV2 {
    private CanyonBlockConformanceV2() {
    }

    private static final int SCALE = 1_000_000;

    /** Pure measurement record: the same Release and Blueprint always yield the same values. */
    record MeasurementsV2(
            String featureId,
            int corridorCells,
            int corridorCellsAtDeclaredSurface,
            int corridorCellsOpenAbove,
            int floorCellsAtSharedRiverBed
    ) {
    }

    /** Measures the first declared corridor of a published hydrology Release. */
    static MeasurementsV2 measure(Path releaseDirectory, WorldBlueprintV2 blueprint) throws IOException {
        List<CanyonPlanV2> plans = blueprint.canyonPlans();
        if (plans.isEmpty()) {
            throw new IOException("published Release declares no canyon corridor to measure");
        }
        CanyonPlanV2 plan = plans.getFirst();
        CanyonGeneratorV2 generator = new CanyonGeneratorV2(plan);
        FeatureMaterializationV2.FinalBlockStreamV2 blocks =
                FeatureMaterializationV2.readFinalBlockStream(releaseDirectory);
        int width = blocks.width();
        int length = blocks.length();

        int corridorCells = 0;
        int atSurface = 0;
        int openAbove = 0;
        int floorAtBed = 0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                CanyonGeneratorV2.CanyonSample sample = generator.sampleAt(x, z, index -> false);
                if (sample.canyonMask() != 1) {
                    continue;
                }
                corridorCells++;
                int surface = Math.floorDiv(sample.surfaceHeightMillionths(), SCALE);
                int bed = Math.floorDiv(sample.bedElevationMillionths(), SCALE);
                boolean surfaceSolid = blocks.isSolid(x, surface, z);
                boolean carvedAbove = true;
                for (int y = surface + 1; y <= blocks.maxY(); y++) {
                    if (!blocks.isAir(x, y, z) && !blocks.isFluid(x, y, z)) {
                        carvedAbove = false;
                        break;
                    }
                }
                if (carvedAbove) {
                    openAbove++;
                }
                if (surfaceSolid && carvedAbove) {
                    atSurface++;
                }
                if (sample.floorMask() == 1 && surface == bed) {
                    floorAtBed++;
                }
            }
        }
        return new MeasurementsV2(plan.featureId(), corridorCells, atSurface, openAbove, floorAtBed);
    }
}
