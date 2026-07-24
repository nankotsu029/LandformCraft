package com.github.nankotsu029.landformcraft.generator.v2.detail;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.TimeZone;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-19-12 (ADR 0041 D1/D8-3) kernel-level conformance for {@link CoherentDetailKernelV2}. The tight
 * coherence bound and its contrast with a cell-hash field of the same amplitude live here, at full
 * millionth precision, so the block-level portfolio case does not have to reconstruct sub-block
 * heights from a Release.
 */
class CoherentDetailKernelV2Test {
    private static final long SEED = 827_413L;
    private static final int SPAN = 96;

    @Test
    void theAmplitudeIsAHardBoundOnEveryCell() {
        CoherentDetailKernelV2 kernel = CoherentDetailKernelV2.forMedium(SEED, "land", 8, 64, 4);
        int amplitude = kernel.amplitudeMillionths();
        assertEquals(8_000_000, amplitude);
        for (int z = -SPAN; z <= SPAN; z++) {
            for (int x = -SPAN; x <= SPAN; x++) {
                int value = kernel.valueMillionthsAt(x, z);
                assertTrue(Math.abs(value) <= amplitude,
                        "value " + value + " exceeded amplitude " + amplitude + " at " + x + ',' + z);
            }
        }
    }

    @Test
    void adjacentCellsStayWithinThePublishedCoherenceBound() {
        // The defining property: a coherent field's 4-neighbour step is bounded by O(amplitude /
        // wavelength), far below the amplitude, so the surface is continuous rather than speckled.
        CoherentDetailKernelV2 kernel = CoherentDetailKernelV2.forMedium(SEED, "land", 8, 64, 4);
        long bound = kernel.maximumAdjacentStepMillionths();
        long observed = maxAdjacentStep(kernel);
        assertTrue(observed <= bound,
                "observed step " + observed + " exceeded the published bound " + bound);
        // The bound is genuinely tight — well under the amplitude, not a vacuous ceiling.
        assertTrue(bound < kernel.amplitudeMillionths(),
                "the coherence bound " + bound + " is not below the amplitude "
                        + kernel.amplitudeMillionths());
    }

    @Test
    void aCellHashFieldOfTheSameAmplitudeBreaksTheCoherenceBound() {
        // ADR 0041 forbids reusing the cell-hash micro relief: this proves the kernel is not that. An
        // independent per-cell hash over the same amplitude routinely jumps far past the coherent bound.
        CoherentDetailKernelV2 kernel = CoherentDetailKernelV2.forMedium(SEED, "land", 8, 64, 4);
        long bound = kernel.maximumAdjacentStepMillionths();
        long cellHashStep = maxAdjacentCellHashStep(8_000_000);
        assertTrue(cellHashStep > bound,
                "a cell-hash field did not exceed the coherence bound; the contrast is meaningless");
        // Not merely above — dramatically above, on the order of the amplitude.
        assertTrue(cellHashStep > 4L * bound, "cell-hash step " + cellHashStep + " vs bound " + bound);
    }

    @Test
    void zeroAmplitudeProducesNoDetail() {
        CoherentDetailKernelV2 kernel = CoherentDetailKernelV2.forMedium(SEED, "water", 0, 32, 3);
        assertEquals(0, kernel.amplitudeMillionths());
        for (int z = 0; z < 32; z++) {
            for (int x = 0; x < 32; x++) {
                assertEquals(0, kernel.valueMillionthsAt(x, z));
            }
        }
    }

    @Test
    void theTwoMediumsAreUncorrelated() {
        // Distinct seed namespaces (ADR 0041 D4): land and water relief must not be the same field.
        CoherentDetailKernelV2 land = CoherentDetailKernelV2.forMedium(SEED, "land", 6, 32, 3);
        CoherentDetailKernelV2 water = CoherentDetailKernelV2.forMedium(SEED, "water", 6, 32, 3);
        int differences = 0;
        for (int z = 0; z < 32; z++) {
            for (int x = 0; x < 32; x++) {
                if (land.valueMillionthsAt(x, z) != water.valueMillionthsAt(x, z)) {
                    differences++;
                }
            }
        }
        assertTrue(differences > 512, "the two mediums produced near-identical relief: " + differences);
    }

    @Test
    void theValueIsIndependentOfLocaleAndTimezone() {
        CoherentDetailKernelV2 kernel = CoherentDetailKernelV2.forMedium(SEED, "land", 5, 32, 3);
        int[] before = sampleGrid(kernel);
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            CoherentDetailKernelV2 underHostileDefaults =
                    CoherentDetailKernelV2.forMedium(SEED, "land", 5, 32, 3);
            assertArrayEqualsGrid(before, sampleGrid(underHostileDefaults));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private static long maxAdjacentStep(CoherentDetailKernelV2 kernel) {
        long max = 0;
        for (int z = -SPAN; z <= SPAN; z++) {
            for (int x = -SPAN; x < SPAN; x++) {
                max = Math.max(max, Math.abs(
                        (long) kernel.valueMillionthsAt(x + 1, z) - kernel.valueMillionthsAt(x, z)));
            }
        }
        for (int z = -SPAN; z < SPAN; z++) {
            for (int x = -SPAN; x <= SPAN; x++) {
                max = Math.max(max, Math.abs(
                        (long) kernel.valueMillionthsAt(x, z + 1) - kernel.valueMillionthsAt(x, z)));
            }
        }
        return max;
    }

    /** An independent per-cell hash of the given amplitude — the exact thing ADR 0041 forbids reusing. */
    private static long maxAdjacentCellHashStep(int amplitudeMillionths) {
        long max = 0;
        for (int z = -SPAN; z <= SPAN; z++) {
            for (int x = -SPAN; x < SPAN; x++) {
                max = Math.max(max, Math.abs(cellHash(x + 1, z, amplitudeMillionths)
                        - cellHash(x, z, amplitudeMillionths)));
            }
        }
        return max;
    }

    private static long cellHash(int x, int z, int amplitudeMillionths) {
        long h = SEED;
        h = mix(h ^ (x * 0x9E3779B97F4A7C15L));
        h = mix(h ^ (z * 0xC2B2AE3D27D4EB4FL));
        long span = 2L * amplitudeMillionths + 1L;
        return Long.remainderUnsigned(h, span) - amplitudeMillionths;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static int[] sampleGrid(CoherentDetailKernelV2 kernel) {
        int[] grid = new int[64 * 64];
        for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 64; x++) {
                grid[z * 64 + x] = kernel.valueMillionthsAt(x, z);
            }
        }
        return grid;
    }

    private static void assertArrayEqualsGrid(int[] expected, int[] actual) {
        TreeSet<Integer> mismatches = new TreeSet<>();
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                mismatches.add(i);
            }
        }
        assertTrue(mismatches.isEmpty(), "kernel value changed with locale/timezone at " + mismatches);
    }
}
