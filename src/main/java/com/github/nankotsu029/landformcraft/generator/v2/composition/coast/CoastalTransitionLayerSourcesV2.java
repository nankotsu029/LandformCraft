package com.github.nankotsu029.landformcraft.generator.v2.composition.coast;

import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.beach.SandyBeachGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.breakwater.BreakwaterHarborGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.cape.RockyCapeGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.harbor.HarborBasinGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalTransitionPlanV2;

import java.util.Objects;

/** Typed adapters from the V2-2-03..06 generators to the transition compositor input contract. */
public final class CoastalTransitionLayerSourcesV2 {
    private static final int FIXED_SCALE = 1_000_000;

    private CoastalTransitionLayerSourcesV2() { }

    public static CoastalTransitionCompositorV2.LayerBinding beach(
            CoastalTransitionPlanV2.Contributor contributor,
            SandyBeachGeneratorV2 generator,
            HardLandWaterSourceV2 hardSource
    ) {
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(hardSource, "hardSource");
        return binding(contributor, (x, z) -> {
            SandyBeachGeneratorV2.BeachSample sample = generator.sampleAt(x, z, hardSource);
            if (sample.band() == SandyBeachGeneratorV2.BeachBand.OUTSIDE) return outside();
            int signed = sample.coastalSample().signedDistanceMillionths();
            int distance;
            if (signed >= 0) {
                distance = Math.max(0, Math.min(signed, sample.localWidthMillionths() - signed));
            } else {
                distance = Math.max(0, Math.min(-signed,
                        generator.plan().nearshoreDistanceBlocks() * FIXED_SCALE + signed));
            }
            return new CoastalTransitionCompositorV2.LayerSample(
                    true,
                    sample.coastalSample().actualLandWater(),
                    sample.surfaceHeightMillionths(),
                    distance,
                    sample.coastalSample().hardConstrained());
        });
    }

    public static CoastalTransitionCompositorV2.LayerBinding harbor(
            CoastalTransitionPlanV2.Contributor contributor,
            HarborBasinGeneratorV2 generator,
            HardLandWaterSourceV2 hardSource
    ) {
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(hardSource, "hardSource");
        return binding(contributor, (x, z) -> {
            HarborBasinGeneratorV2.HarborSample sample = generator.sampleAt(x, z, hardSource);
            if (sample.region() == HarborBasinGeneratorV2.HarborRegion.OUTSIDE) return outside();
            long minimumDepth = (long) generator.plan().minimumDepthBlocks() * FIXED_SCALE;
            long depthRange = (long) generator.plan().maximumDepthBlocks()
                    - generator.plan().minimumDepthBlocks();
            long boundaryDistance = sample.region() == HarborBasinGeneratorV2.HarborRegion.ENTRANCE_CORRIDOR
                    || depthRange == 0 ? FIXED_SCALE
                    : Math.floorDiv(Math.multiplyExact(
                            (long) sample.depthMillionths() - minimumDepth,
                            generator.plan().profileTransitionBlocks()), depthRange);
            return new CoastalTransitionCompositorV2.LayerSample(
                    true, 0, sample.bottomHeightMillionths(),
                    Math.toIntExact(Math.max(1L, boundaryDistance)), sample.hardConstrained());
        });
    }

    public static CoastalTransitionCompositorV2.LayerBinding breakwater(
            CoastalTransitionPlanV2.Contributor contributor,
            BreakwaterHarborGeneratorV2 generator
    ) {
        Objects.requireNonNull(generator, "generator");
        return binding(contributor, (x, z) -> {
            BreakwaterHarborGeneratorV2.BreakwaterSample sample = generator.sampleAt(x, z);
            if (sample.region() == BreakwaterHarborGeneratorV2.BreakwaterRegion.OUTSIDE) return outside();
            long boundaryDistance;
            if (sample.region() == BreakwaterHarborGeneratorV2.BreakwaterRegion.CREST) {
                boundaryDistance = (long) generator.plan().crestWidthBlocks() * FIXED_SCALE / 2L;
            } else {
                int depth = sample.region() == BreakwaterHarborGeneratorV2.BreakwaterRegion.INNER_FOUNDATION
                        ? generator.plan().innerDepthBlocks() : generator.plan().outerDepthBlocks();
                long availableRun = Math.multiplyExact(
                        (long) depth, generator.plan().foundationSideSlopeRunPerRiseMillionths());
                long verticalDrop = Math.max(0L,
                        (long) generator.plan().waterLevel() * FIXED_SCALE - sample.topHeightMillionths());
                long consumedRun = Math.floorDiv(Math.multiplyExact(
                        verticalDrop, generator.plan().foundationSideSlopeRunPerRiseMillionths()), FIXED_SCALE);
                boundaryDistance = Math.max(1L, availableRun - consumedRun);
            }
            return new CoastalTransitionCompositorV2.LayerSample(
                    true, 1, sample.topHeightMillionths(), Math.toIntExact(boundaryDistance), false);
        });
    }

    public static CoastalTransitionCompositorV2.LayerBinding cape(
            CoastalTransitionPlanV2.Contributor contributor,
            RockyCapeGeneratorV2 generator,
            HardLandWaterSourceV2 hardSource
    ) {
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(hardSource, "hardSource");
        return binding(contributor, (x, z) -> {
            RockyCapeGeneratorV2.CapeSample sample = generator.sampleAt(x, z, hardSource);
            if (sample.region() == RockyCapeGeneratorV2.CapeRegion.OUTSIDE) return outside();
            int landWater = sample.region() == RockyCapeGeneratorV2.CapeRegion.CHANNEL ? 0 : 1;
            long boundaryDistance = FIXED_SCALE;
            if (sample.region() == RockyCapeGeneratorV2.CapeRegion.CLIFF
                    || sample.region() == RockyCapeGeneratorV2.CapeRegion.INTERIOR) {
                long base = (long) (generator.plan().waterLevel() + generator.plan().cliffHeightBlocks())
                        * FIXED_SCALE;
                int reliefRange = generator.plan().localReliefAboveSeaBlocks()
                        - generator.plan().cliffHeightBlocks();
                boundaryDistance = reliefRange == 0
                        ? (long) generator.plan().cliffBandWidthBlocks() * FIXED_SCALE
                        : Math.floorDiv(Math.multiplyExact(
                                (long) sample.surfaceHeightMillionths() - base,
                                generator.plan().cliffBandWidthBlocks()), reliefRange);
            }
            return new CoastalTransitionCompositorV2.LayerSample(
                    true, landWater, sample.surfaceHeightMillionths(),
                    Math.toIntExact(Math.max(1L, boundaryDistance)),
                    sample.hardConstraintMatched());
        });
    }

    private static CoastalTransitionCompositorV2.LayerBinding binding(
            CoastalTransitionPlanV2.Contributor contributor,
            CoastalTransitionCompositorV2.LayerSource source
    ) {
        return new CoastalTransitionCompositorV2.LayerBinding(contributor, source);
    }

    private static CoastalTransitionCompositorV2.LayerSample outside() {
        return CoastalTransitionCompositorV2.LayerSample.OUTSIDE;
    }
}
