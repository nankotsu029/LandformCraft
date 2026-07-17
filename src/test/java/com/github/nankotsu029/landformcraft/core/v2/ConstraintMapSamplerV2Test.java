package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintMapSamplerV2Test {
    @Test
    void appliesRotationThenFlipThenCrop() {
        var sampler = new ConstraintMapSamplerV2(3, 2, mapping(
                GenerationRequestV2.QuarterTurn.DEGREES_90,
                true,
                false,
                new GenerationRequestV2.PixelCrop(0, 1, 2, 2)));

        int[] sampled = new int[4];
        int index = 0;
        for (int z = 0; z < 2; z++) {
            for (int x = 0; x < 2; x++) {
                sampled[index++] = sampler.sampleNearest(x, z, 2, 2, (rawX, rawZ) -> rawZ * 10 + rawX);
            }
        }

        assertArrayEquals(new int[]{1, 11, 2, 12}, sampled);
    }

    @Test
    void nearestUsesPixelCentersWithoutBlendingCategories() {
        var sampler = new ConstraintMapSamplerV2(2, 1, mapping(
                GenerationRequestV2.QuarterTurn.DEGREES_0,
                false,
                false,
                new GenerationRequestV2.PixelCrop(0, 0, 2, 1)));

        int[] result = new int[4];
        for (int x = 0; x < result.length; x++) {
            result[x] = sampler.sampleNearest(x, 0, 4, 1, (rawX, rawZ) -> rawX == 0 ? 7 : 29);
        }

        assertArrayEquals(new int[]{7, 7, 29, 29}, result);
    }

    @Test
    void bilinearSamplingUsesFixedPointAndPropagatesNoData() {
        var sampler = new ConstraintMapSamplerV2(2, 2, mapping(
                GenerationRequestV2.QuarterTurn.DEGREES_0,
                false,
                false,
                new GenerationRequestV2.PixelCrop(0, 0, 2, 2)));
        var center = sampler.sampleFixedBilinear(1, 1, 3, 3,
                (x, z) -> ConstraintMapSamplerV2.SemanticSample.value((z * 20L + x * 10L) * 1_000_000L));
        var missing = sampler.sampleFixedBilinear(1, 1, 3, 3,
                (x, z) -> x == 1 && z == 1
                        ? ConstraintMapSamplerV2.SemanticSample.missing()
                        : ConstraintMapSamplerV2.SemanticSample.value(0));

        assertEquals(15_000_000L, center.valueMillionths());
        assertTrue(missing.noData());
    }

    @Test
    void wholeAndTilesAreIdenticalAcrossLocaleAndTimezone() {
        var sampler = new ConstraintMapSamplerV2(4, 4, mapping(
                GenerationRequestV2.QuarterTurn.DEGREES_0,
                false,
                true,
                new GenerationRequestV2.PixelCrop(0, 0, 4, 4)));
        long[] whole = sample(sampler, 7, 7, 7);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            assertArrayEquals(whole, sample(sampler, 7, 7, 3));
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    private static long[] sample(ConstraintMapSamplerV2 sampler, int width, int length, int tileSize) {
        long[] result = new long[width * length];
        for (int originZ = 0; originZ < length; originZ += tileSize) {
            for (int originX = 0; originX < width; originX += tileSize) {
                int endZ = Math.min(length, originZ + tileSize);
                int endX = Math.min(width, originX + tileSize);
                for (int z = originZ; z < endZ; z++) {
                    for (int x = originX; x < endX; x++) {
                        result[z * width + x] = sampler.sampleFixedBilinear(x, z, width, length,
                                (rawX, rawZ) -> ConstraintMapSamplerV2.SemanticSample.value(
                                        (rawZ * 4L + rawX) * 1_000_000L)).valueMillionths();
                    }
                }
            }
        }
        return result;
    }

    private static GenerationRequestV2.CoordinateMapping mapping(
            GenerationRequestV2.QuarterTurn rotation,
            boolean flipX,
            boolean flipZ,
            GenerationRequestV2.PixelCrop crop
    ) {
        return new GenerationRequestV2.CoordinateMapping(
                GenerationRequestV2.CoordinateOrigin.NORTH_WEST,
                GenerationRequestV2.XAxis.EAST,
                GenerationRequestV2.ZAxis.SOUTH,
                GenerationRequestV2.PixelReference.PIXEL_CENTER,
                GenerationRequestV2.AspectMismatchPolicy.REJECT,
                rotation,
                flipX,
                flipZ,
                crop);
    }
}
