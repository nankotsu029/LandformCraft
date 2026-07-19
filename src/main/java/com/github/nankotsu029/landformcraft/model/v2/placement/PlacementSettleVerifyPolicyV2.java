package com.github.nankotsu029.landformcraft.model.v2.placement;

import java.util.Objects;

/**
 * Version-frozen settle／full-verify policy (V2-6-07). Changing any constant requires a contract
 * version bump. Sampling is never permitted: {@code rejectSampledVerify} is always true for v1.
 */
public record PlacementSettleVerifyPolicyV2(
        String policyVersion,
        int maximumSettleTicks,
        int quiescenceTicks,
        long settleTimeoutMillis,
        int maximumBlocksPerVerifySlice,
        int maximumQueuedVerifySlices,
        long maximumEffectEnvelopeBlocks,
        boolean rejectOutOfEnvelopeUpdates,
        boolean rejectSampledVerify,
        ResourceBudget budget
) {
    public static final String POLICY_VERSION = "release-2-placement-settle-verify-policy-v1";
    public static final int MAXIMUM_SETTLE_TICKS = 200;
    public static final int MAXIMUM_QUIESCENCE_TICKS = 32;
    public static final long MAXIMUM_SETTLE_TIMEOUT_MILLIS = 120_000L;
    public static final int MAXIMUM_VERIFY_SLICE_BLOCKS = 4_096;
    /** Enough slices for 500×500（and headroom toward 1000×1000）full-envelope exact verify. */
    public static final int MAXIMUM_QUEUED_VERIFY_SLICES = 2_048;

    public PlacementSettleVerifyPolicyV2 {
        policyVersion = nonBlank(policyVersion, "policyVersion", 64);
        if (!POLICY_VERSION.equals(policyVersion)) {
            throw new IllegalArgumentException("unknown placement settle/verify policy version");
        }
        if (maximumSettleTicks < 1 || maximumSettleTicks > MAXIMUM_SETTLE_TICKS
                || quiescenceTicks < 1 || quiescenceTicks > MAXIMUM_QUIESCENCE_TICKS
                || quiescenceTicks > maximumSettleTicks
                || settleTimeoutMillis < 1 || settleTimeoutMillis > MAXIMUM_SETTLE_TIMEOUT_MILLIS
                || maximumBlocksPerVerifySlice < 1
                || maximumBlocksPerVerifySlice > MAXIMUM_VERIFY_SLICE_BLOCKS
                || maximumQueuedVerifySlices < 1
                || maximumQueuedVerifySlices > MAXIMUM_QUEUED_VERIFY_SLICES
                || maximumEffectEnvelopeBlocks < 1) {
            throw new IllegalArgumentException("invalid placement settle/verify policy limits");
        }
        if (!rejectOutOfEnvelopeUpdates) {
            throw new IllegalArgumentException(
                    "settle/verify policy must reject updates outside the effect envelope");
        }
        if (!rejectSampledVerify) {
            throw new IllegalArgumentException(
                    "settle/verify policy must reject sampled verify; full envelope scan is required");
        }
        Objects.requireNonNull(budget, "budget");
    }

    public static PlacementSettleVerifyPolicyV2 standard() {
        return new PlacementSettleVerifyPolicyV2(
                POLICY_VERSION,
                40,
                2,
                30_000L,
                MAXIMUM_VERIFY_SLICE_BLOCKS,
                MAXIMUM_QUEUED_VERIFY_SLICES,
                50_000_000L,
                true,
                true,
                ResourceBudget.standard());
    }

    public record ResourceBudget(
            String budgetVersion,
            long maximumScannedBlocks,
            int maximumContinuityFindings,
            long maximumCanonicalBytes
    ) {
        public static final String VERSION = "release-2-placement-settle-verify-budget-v1";
        public static final long MAX_CANONICAL_BYTES = 256L * 1024L;

        public ResourceBudget {
            budgetVersion = nonBlank(budgetVersion, "budgetVersion", 64);
            if (!VERSION.equals(budgetVersion)) {
                throw new IllegalArgumentException("unknown placement settle/verify budget version");
            }
            if (maximumScannedBlocks < 1
                    || maximumContinuityFindings < 1
                    || maximumCanonicalBytes < 1
                    || maximumCanonicalBytes > MAX_CANONICAL_BYTES) {
                throw new IllegalArgumentException("invalid placement settle/verify budget");
            }
        }

        public static ResourceBudget standard() {
            return new ResourceBudget(VERSION, 50_000_000L, 4_096, MAX_CANONICAL_BYTES);
        }
    }

    private static String nonBlank(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and <= " + max);
        }
        return value;
    }
}
