package com.github.nankotsu029.landformcraft.generator.v2.detail;

import com.github.nankotsu029.landformcraft.generator.v2.NamedSeedDeriverV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;

/**
 * Coherent multi-scale detail kernel (V2-19-12, ADR 0041 D1, contract {@code coherent-detail-fixed-v1}).
 *
 * <p>Produces a spatially <em>continuous</em>, deterministic, integer-only detail value for one medium
 * of the macro foundation background. It is deliberately not the cell-hash micro relief of
 * {@code PlainGeneratorV2} (ADR 0041 forbids reusing that): each octave lays value noise on a grid of
 * spacing {@code W >> k} and interpolates it with an integer smoothstep, so adjacent cells differ by a
 * bounded amount rather than jumping across the whole amplitude. That bound is published as
 * {@link #maximumAdjacentStepMillionths()} and lets a test tell a coherent field from a cell-hash one
 * mechanically.</p>
 *
 * <p>The kernel reads no other cell's field value — it evaluates only its own grid points, which are a
 * pure function of the global coordinate — so it needs no halo and no support radius (ADR 0041 D4), and
 * whole-generation equals tiled generation by construction. The amplitude is a hard bound: octave
 * amplitudes sum to at most {@code A}, so {@code |value| ≤ A} on every cell (ADR 0041 D1).</p>
 */
public final class CoherentDetailKernelV2 {
    public static final String VERSION = "coherent-detail-fixed-v1";
    static final int SCALE = 1_000_000;
    private static final String MODULE_ID = "landform-foundation-detail";
    /** A per-octave rounding slack (millionths) over the exact {@code 3·a_k / W_k} slope bound. */
    private static final long ROUNDING_SLACK_MILLIONTHS = 64L;
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long SECOND_GAMMA = 0xC2B2AE3D27D4EB4FL;

    private final long[] octaveSeeds;
    private final int[] octaveAmplitudeMillionths;
    private final int[] octaveGridSpacing;
    private final int amplitudeMillionths;
    private final long maximumAdjacentStepMillionths;

    private CoherentDetailKernelV2(
            long[] octaveSeeds,
            int[] octaveAmplitudeMillionths,
            int[] octaveGridSpacing,
            int amplitudeMillionths,
            long maximumAdjacentStepMillionths
    ) {
        this.octaveSeeds = octaveSeeds;
        this.octaveAmplitudeMillionths = octaveAmplitudeMillionths;
        this.octaveGridSpacing = octaveGridSpacing;
        this.amplitudeMillionths = amplitudeMillionths;
        this.maximumAdjacentStepMillionths = maximumAdjacentStepMillionths;
    }

    /**
     * Builds the kernel for one medium of one request. {@code amplitudeBlocks} may be zero (that medium
     * carries no detail), in which case {@link #valueMillionthsAt} returns 0 everywhere. The wavelength
     * and octave count are already range-checked by {@link GenerationRequestV2.FoundationDetail}; they
     * are re-derived here from the same declared numbers so the kernel is self-contained.
     *
     * @param mediumLabel a stable seed namespace component ("land" / "water") so the two mediums'
     *        relief are uncorrelated; the author cannot choose it (ADR 0041 D4)
     */
    public static CoherentDetailKernelV2 forMedium(
            long globalSeed,
            String mediumLabel,
            int amplitudeBlocks,
            int wavelengthBlocks,
            int octaves
    ) {
        if (amplitudeBlocks < 0 || amplitudeBlocks > GenerationRequestV2.FoundationDetail.MAX_AMPLITUDE_BLOCKS) {
            throw new IllegalArgumentException("amplitude blocks out of contract");
        }
        int amplitude = Math.multiplyExact(amplitudeBlocks, SCALE);
        long[] seeds = new long[octaves];
        int[] amplitudes = new int[octaves];
        int[] spacing = new int[octaves];
        long denominator = (1L << octaves) - 1L;
        long step = 0L;
        for (int k = 0; k < octaves; k++) {
            seeds[k] = NamedSeedDeriverV2.derive(
                    globalSeed, MODULE_ID, VERSION, mediumLabel, "octave-" + k, VERSION);
            // a_k = floor(A · 2^(octaves-1-k) / (2^octaves - 1)); the weights sum to 2^octaves-1, so
            // Σ a_k ≤ A and the amplitude is a hard upper bound on |value| (ADR 0041 D1).
            amplitudes[k] = Math.toIntExact(
                    (long) amplitude * (1L << (octaves - 1 - k)) / denominator);
            spacing[k] = wavelengthBlocks >> k;
            if (spacing[k] < GenerationRequestV2.FoundationDetail.MIN_GRID_SPACING_BLOCKS) {
                throw new IllegalArgumentException("octave grid spacing below the contract minimum");
            }
            // Smoothstep's peak slope is 1.5; the value span across a cell is ≤ 2·SCALE, scaled by
            // a_k/SCALE, so the per-block change is ≤ 3·a_k / W_k. A small slack absorbs integer rounding.
            step += ceilDiv(3L * amplitudes[k], spacing[k]) + ROUNDING_SLACK_MILLIONTHS;
        }
        return new CoherentDetailKernelV2(seeds, amplitudes, spacing, amplitude, step);
    }

    /** The detail offset (block-millionths) at one global cell. {@code |value| ≤ amplitude}. */
    public int valueMillionthsAt(int globalX, int globalZ) {
        long sum = 0L;
        for (int k = 0; k < octaveSeeds.length; k++) {
            sum = Math.addExact(sum, octaveContribution(k, globalX, globalZ));
        }
        // The construction bounds |sum| ≤ Σ a_k ≤ amplitude; clamp defensively so a downstream check
        // never sees an out-of-band value from an unexpected rounding path.
        if (sum > amplitudeMillionths) {
            sum = amplitudeMillionths;
        } else if (sum < -amplitudeMillionths) {
            sum = -amplitudeMillionths;
        }
        return (int) sum;
    }

    /** {@code |value|}'s contractual ceiling in block-millionths (ADR 0041 D1). */
    public int amplitudeMillionths() {
        return amplitudeMillionths;
    }

    /**
     * The published upper bound on {@code |value(x±1,z) − value(x,z)|} and {@code |value(x,z±1) −
     * value(x,z)|} in millionths. A cell-hash field of the same amplitude exceeds this (its neighbours
     * are independent draws over the full range), which is how a test proves this kernel is coherent
     * and not a repackaged independent hash (ADR 0041 D1/D8-3).
     */
    public long maximumAdjacentStepMillionths() {
        return maximumAdjacentStepMillionths;
    }

    private long octaveContribution(int k, int globalX, int globalZ) {
        int w = octaveGridSpacing[k];
        int lx = Math.floorDiv(globalX, w);
        int lz = Math.floorDiv(globalZ, w);
        long fx = (long) Math.floorMod(globalX, w) * SCALE / w;
        long fz = (long) Math.floorMod(globalZ, w) * SCALE / w;
        long sx = smoothstep(fx);
        long sz = smoothstep(fz);
        long v00 = gridValue(k, lx, lz);
        long v10 = gridValue(k, lx + 1, lz);
        long v01 = gridValue(k, lx, lz + 1);
        long v11 = gridValue(k, lx + 1, lz + 1);
        long ix0 = v00 + (v10 - v00) * sx / SCALE;
        long ix1 = v01 + (v11 - v01) * sx / SCALE;
        long value = ix0 + (ix1 - ix0) * sz / SCALE;
        return (long) octaveAmplitudeMillionths[k] * value / SCALE;
    }

    /** Deterministic grid value in {@code [-SCALE, SCALE]}, a pure function of octave seed and lattice. */
    private long gridValue(int k, int latticeX, int latticeZ) {
        long h = octaveSeeds[k];
        h = mix64(h ^ (latticeX * GOLDEN_GAMMA));
        h = mix64(h ^ (latticeZ * SECOND_GAMMA));
        return Long.remainderUnsigned(h, 2L * SCALE + 1L) - SCALE;
    }

    /** Integer smoothstep {@code 3t² − 2t³} scaled to millionths, {@code u} in {@code [0, SCALE)}. */
    private static long smoothstep(long u) {
        long u2 = u * u;
        return u2 * (3L * SCALE - 2L * u) / (1_000_000L * 1_000_000L);
    }

    private static long ceilDiv(long numerator, long denominator) {
        return (numerator + denominator - 1L) / denominator;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
