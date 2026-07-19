package com.github.nankotsu029.landformcraft.model.v2.placement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sealed settle／full-verify evidence (V2-6-07). Produced only when the entire effect envelope
 * matches the canonical expected block-state stream after bounded settle. Never a substitute for
 * rollback；verify failure must leave the journal in {@code RECOVERY_REQUIRED}.
 */
public record PlacementVerifyEvidenceV2(
        int evidenceVersion,
        String verifyContractVersion,
        PlacementSettleVerifyPolicyV2 policy,
        UUID placementId,
        UUID operationId,
        UUID worldId,
        PlacementPlanBinding placementPlanBinding,
        EnvelopeBinding envelopeBinding,
        SnapshotBinding snapshotBinding,
        JournalBinding journalBinding,
        WorldAabbV2 unionEffectEnvelope,
        String expectedStreamChecksum,
        String observedStreamChecksum,
        List<ContinuityMetricV2> continuityMetrics,
        SettleStats settleStats,
        ScanStats scanStats,
        Verdict verdict,
        String createdAt,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String VERIFY_CONTRACT_VERSION = "release-2-placement-settle-verify-v1";
    public static final long MAX_CANONICAL_BYTES =
            PlacementSettleVerifyPolicyV2.ResourceBudget.MAX_CANONICAL_BYTES;
    public static final int MAXIMUM_CONTINUITY_METRICS = 4_096;

    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ISO_INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");

    public PlacementVerifyEvidenceV2 {
        if (evidenceVersion != VERSION) {
            throw new IllegalArgumentException("placement verify evidenceVersion must be 1");
        }
        verifyContractVersion = nonBlank(verifyContractVersion, "verifyContractVersion", 64);
        if (!VERIFY_CONTRACT_VERSION.equals(verifyContractVersion)) {
            throw new IllegalArgumentException("unknown placement settle/verify contract version");
        }
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(placementId, "placementId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(placementPlanBinding, "placementPlanBinding");
        Objects.requireNonNull(envelopeBinding, "envelopeBinding");
        Objects.requireNonNull(snapshotBinding, "snapshotBinding");
        Objects.requireNonNull(journalBinding, "journalBinding");
        Objects.requireNonNull(unionEffectEnvelope, "unionEffectEnvelope");
        expectedStreamChecksum = checksum(expectedStreamChecksum, "expectedStreamChecksum");
        observedStreamChecksum = checksum(observedStreamChecksum, "observedStreamChecksum");
        if (!expectedStreamChecksum.equals(observedStreamChecksum)) {
            throw new IllegalArgumentException(
                    "only exact-match verify evidence may be sealed; expected/observed checksum mismatch");
        }
        Objects.requireNonNull(continuityMetrics, "continuityMetrics");
        if (continuityMetrics.size() > MAXIMUM_CONTINUITY_METRICS
                || continuityMetrics.size() > policy.budget().maximumContinuityFindings()) {
            throw new IllegalArgumentException("continuity metrics exceed budget");
        }
        continuityMetrics = normalizeMetrics(continuityMetrics);
        Objects.requireNonNull(settleStats, "settleStats");
        Objects.requireNonNull(scanStats, "scanStats");
        Objects.requireNonNull(verdict, "verdict");
        if (verdict != Verdict.VERIFIED) {
            throw new IllegalArgumentException("only VERIFIED evidence may be sealed");
        }
        createdAt = instant(createdAt, "createdAt");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        if (scanStats.scannedBlocks() > policy.budget().maximumScannedBlocks()
                || scanStats.scannedBlocks() > policy.maximumEffectEnvelopeBlocks()) {
            throw new IllegalArgumentException("verify scan stats exceed policy budget");
        }
        if (settleStats.settleTicks() > policy.maximumSettleTicks()
                || settleStats.updatesOutsideEnvelope() != 0) {
            throw new IllegalArgumentException("settle stats violate policy invariants");
        }
    }

    public PlacementVerifyEvidenceV2 withCanonicalChecksum(String checksum) {
        return new PlacementVerifyEvidenceV2(
                evidenceVersion,
                verifyContractVersion,
                policy,
                placementId,
                operationId,
                worldId,
                placementPlanBinding,
                envelopeBinding,
                snapshotBinding,
                journalBinding,
                unionEffectEnvelope,
                expectedStreamChecksum,
                observedStreamChecksum,
                continuityMetrics,
                settleStats,
                scanStats,
                verdict,
                createdAt,
                checksum);
    }

    public void requireBindings(
            PlacementPlanV2 plan,
            PlacementEnvelopePlanV2 envelope,
            PlacementSnapshotPlanV2 snapshot,
            PlacementJournalV2 applyCompleteJournal
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(applyCompleteJournal, "applyCompleteJournal");
        if (!placementId.equals(plan.placementId())
                || !operationId.equals(plan.operationId())
                || !worldId.equals(plan.target().worldId())) {
            throw new IllegalArgumentException("verify evidence identity mismatch");
        }
        if (!placementPlanBinding.sourcePlacementPlanChecksum().equals(plan.canonicalChecksum())) {
            throw new IllegalArgumentException("verify placement plan binding mismatch");
        }
        if (!envelopeBinding.sourceEnvelopePlanChecksum().equals(envelope.canonicalChecksum())
                || !envelopeBinding.sourceMutationEnvelopeChecksum()
                .equals(envelope.mutationEnvelopeChecksum())) {
            throw new IllegalArgumentException("verify envelope binding mismatch");
        }
        if (!snapshotBinding.sourceSnapshotPlanChecksum().equals(snapshot.canonicalChecksum())) {
            throw new IllegalArgumentException("verify snapshot binding mismatch");
        }
        if (!journalBinding.sourceJournalChecksum().equals(applyCompleteJournal.journalChecksum())) {
            throw new IllegalArgumentException("verify journal binding mismatch");
        }
        if (!unionEffectEnvelope.equals(envelope.unionEffectEnvelope())) {
            throw new IllegalArgumentException("verify effect envelope mismatch");
        }
    }

    public enum Verdict {
        VERIFIED
    }

    public enum ContinuityRuleV2 {
        SURFACE_FOUNDATION,
        MARINE_UNDERWATER_COLUMN,
        SURFACE_VOLUME_ENTRANCE,
        UNDERGROUND_FLUID,
        OVERLAY_CONTINUITY
    }

    public record ContinuityMetricV2(
            ContinuityRuleV2 rule,
            long examinedEdges,
            long continuousEdges,
            String detail,
            String evidenceHash
    ) {
        public ContinuityMetricV2 {
            Objects.requireNonNull(rule, "rule");
            if (examinedEdges < 0 || continuousEdges < 0 || continuousEdges > examinedEdges) {
                throw new IllegalArgumentException("invalid continuity metric counts");
            }
            detail = nonBlank(detail, "detail", 256);
            evidenceHash = checksum(evidenceHash, "evidenceHash");
        }
    }

    public record SettleStats(
            int settleTicks,
            int quiescentTicksObserved,
            int updatesInsideEnvelope,
            int updatesOutsideEnvelope,
            long settleElapsedMillis
    ) {
        public SettleStats {
            if (settleTicks < 0 || quiescentTicksObserved < 0
                    || updatesInsideEnvelope < 0 || updatesOutsideEnvelope < 0
                    || settleElapsedMillis < 0) {
                throw new IllegalArgumentException("settle stats must be non-negative");
            }
        }
    }

    public record ScanStats(
            long scannedBlocks,
            int verifySlices,
            int mismatchCount,
            int tileSeamSamples
    ) {
        public ScanStats {
            if (scannedBlocks < 0 || verifySlices < 0 || mismatchCount < 0 || tileSeamSamples < 0) {
                throw new IllegalArgumentException("scan stats must be non-negative");
            }
            if (mismatchCount != 0) {
                throw new IllegalArgumentException("VERIFIED evidence requires zero mismatches");
            }
        }
    }

    public record PlacementPlanBinding(
            int bindingVersion,
            String sourcePlacementPlanChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION =
                "release-2-placement-settle-verify-plan-binding-v1";

        public PlacementPlanBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown settle/verify placement plan binding");
            }
            sourcePlacementPlanChecksum = checksum(sourcePlacementPlanChecksum, "sourcePlacementPlanChecksum");
        }
    }

    public record EnvelopeBinding(
            int bindingVersion,
            String sourceEnvelopePlanChecksum,
            String sourceMutationEnvelopeChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION =
                "release-2-placement-settle-verify-envelope-binding-v1";

        public EnvelopeBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown settle/verify envelope binding");
            }
            sourceEnvelopePlanChecksum = checksum(sourceEnvelopePlanChecksum, "sourceEnvelopePlanChecksum");
            sourceMutationEnvelopeChecksum =
                    checksum(sourceMutationEnvelopeChecksum, "sourceMutationEnvelopeChecksum");
        }
    }

    public record SnapshotBinding(
            int bindingVersion,
            String sourceSnapshotPlanChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION =
                "release-2-placement-settle-verify-snapshot-binding-v1";

        public SnapshotBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown settle/verify snapshot binding");
            }
            sourceSnapshotPlanChecksum = checksum(sourceSnapshotPlanChecksum, "sourceSnapshotPlanChecksum");
        }
    }

    public record JournalBinding(
            int bindingVersion,
            String sourceJournalChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION =
                "release-2-placement-settle-verify-journal-binding-v1";

        public JournalBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown settle/verify journal binding");
            }
            sourceJournalChecksum = checksum(sourceJournalChecksum, "sourceJournalChecksum");
        }
    }

    private static List<ContinuityMetricV2> normalizeMetrics(List<ContinuityMetricV2> metrics) {
        List<ContinuityMetricV2> sorted = new ArrayList<>(metrics.size());
        Set<ContinuityRuleV2> rules = new HashSet<>();
        for (ContinuityMetricV2 metric : metrics) {
            Objects.requireNonNull(metric, "continuityMetrics");
            if (!rules.add(metric.rule())) {
                throw new IllegalArgumentException("duplicate continuity metric rule");
            }
            sorted.add(metric);
        }
        sorted.sort(Comparator.comparing(metric -> metric.rule().name()));
        return List.copyOf(sorted);
    }

    private static String checksum(String value, String field) {
        value = nonBlank(value, field, 64);
        if (!CHECKSUM.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase sha-256 hex digest");
        }
        return value;
    }

    private static String instant(String value, String field) {
        value = nonBlank(value, field, 64);
        if (!ISO_INSTANT.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be an ISO-8601 UTC instant");
        }
        return value;
    }

    private static String nonBlank(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and <= " + max);
        }
        return value;
    }
}
