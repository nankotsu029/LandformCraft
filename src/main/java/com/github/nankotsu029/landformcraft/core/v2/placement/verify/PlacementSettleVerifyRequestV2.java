package com.github.nankotsu029.landformcraft.core.v2.placement.verify;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementCanonicalBlockSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentEvidenceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSettleVerifyPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementVerifyEvidenceV2;

import java.io.IOException;
import java.util.Objects;

/** Immutable settle／full-verify request after a successful V2-6-06 apply transaction. */
public record PlacementSettleVerifyRequestV2(
        PlacementPlanV2 placementPlan,
        PlacementEnvelopePlanV2 envelopePlan,
        PlacementSnapshotPlanV2 snapshotPlan,
        PlacementContainmentEvidenceV2 containmentEvidence,
        PlacementJournalV2 applyCompleteJournal,
        PlacementCanonicalBlockSourceV2 blockSource,
        PlacementExpectedBlockResolverV2.SnapshotBaselineV2 snapshotBaseline,
        PlacementSettleVerifyPolicyV2 policy,
        CancellationToken cancellation,
        VerifiedCommitV2 verifiedCommit
) {
    public PlacementSettleVerifyRequestV2(
            PlacementPlanV2 placementPlan,
            PlacementEnvelopePlanV2 envelopePlan,
            PlacementSnapshotPlanV2 snapshotPlan,
            PlacementContainmentEvidenceV2 containmentEvidence,
            PlacementJournalV2 applyCompleteJournal,
            PlacementCanonicalBlockSourceV2 blockSource,
            PlacementExpectedBlockResolverV2.SnapshotBaselineV2 snapshotBaseline,
            PlacementSettleVerifyPolicyV2 policy,
            CancellationToken cancellation
    ) {
        this(placementPlan, envelopePlan, snapshotPlan, containmentEvidence, applyCompleteJournal,
                blockSource, snapshotBaseline, policy, cancellation, VerifiedCommitV2.none());
    }

    public PlacementSettleVerifyRequestV2 {
        Objects.requireNonNull(placementPlan, "placementPlan");
        Objects.requireNonNull(envelopePlan, "envelopePlan");
        Objects.requireNonNull(snapshotPlan, "snapshotPlan");
        Objects.requireNonNull(containmentEvidence, "containmentEvidence");
        Objects.requireNonNull(applyCompleteJournal, "applyCompleteJournal");
        Objects.requireNonNull(blockSource, "blockSource");
        Objects.requireNonNull(snapshotBaseline, "snapshotBaseline");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(verifiedCommit, "verifiedCommit");
    }

    /** Persists all Undo-required evidence before the terminal APPLIED journal is published. */
    @FunctionalInterface
    public interface VerifiedCommitV2 {
        void commit(
                PlacementJournalV2 applyCompleteJournal,
                PlacementJournalV2 appliedJournal,
                PlacementVerifyEvidenceV2 verifyEvidence
        ) throws IOException;

        static VerifiedCommitV2 none() {
            return (applyCompleteJournal, appliedJournal, verifyEvidence) -> { };
        }
    }
}
