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
 * Sealed containment preflight evidence (V2-6-05). Produced only when every fluid／gravity／neighbor
 * effect is proven to stay inside the union effect envelope. Never a substitute for settle or
 * rollback; apply orchestration (V2-6-06) must require this artifact before any world mutation.
 */
public record PlacementContainmentEvidenceV2(
        int evidenceVersion,
        String containmentContractVersion,
        PlacementContainmentPolicyV2 policy,
        UUID placementId,
        UUID operationId,
        UUID worldId,
        PlacementPlanBinding placementPlanBinding,
        EnvelopeBinding envelopeBinding,
        SnapshotBinding snapshotBinding,
        WorldAabbV2 unionEffectEnvelope,
        List<FindingV2> findings,
        ScanStats scanStats,
        Verdict verdict,
        String createdAt,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTAINMENT_CONTRACT_VERSION = "release-2-placement-containment-v1";
    public static final long MAX_CANONICAL_BYTES =
            PlacementContainmentPolicyV2.ResourceBudget.MAX_CANONICAL_BYTES;
    public static final int MAXIMUM_FINDINGS = 4_096;

    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ISO_INSTANT = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");

    public PlacementContainmentEvidenceV2 {
        if (evidenceVersion != VERSION) {
            throw new IllegalArgumentException("placement containment evidenceVersion must be 1");
        }
        containmentContractVersion = nonBlank(containmentContractVersion, "containmentContractVersion", 64);
        if (!CONTAINMENT_CONTRACT_VERSION.equals(containmentContractVersion)) {
            throw new IllegalArgumentException("unknown placement containment contract version");
        }
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(placementId, "placementId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(placementPlanBinding, "placementPlanBinding");
        Objects.requireNonNull(envelopeBinding, "envelopeBinding");
        Objects.requireNonNull(snapshotBinding, "snapshotBinding");
        Objects.requireNonNull(unionEffectEnvelope, "unionEffectEnvelope");
        Objects.requireNonNull(findings, "findings");
        if (findings.size() > MAXIMUM_FINDINGS) {
            throw new IllegalArgumentException("containment findings exceed maximum");
        }
        findings = normalizeFindings(findings);
        Objects.requireNonNull(scanStats, "scanStats");
        Objects.requireNonNull(verdict, "verdict");
        if (verdict != Verdict.CONTAINED) {
            throw new IllegalArgumentException("only CONTAINED evidence may be sealed");
        }
        createdAt = instant(createdAt, "createdAt");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        if (scanStats.scannedBlocks() > policy.budget().maximumScannedBlocks()
                || scanStats.cacheEntries() > policy.budget().maximumCacheEntries()
                || scanStats.bfsNodes() > policy.budget().maximumBfsNodes()) {
            throw new IllegalArgumentException("containment scan stats exceed policy budget");
        }
    }

    public PlacementContainmentEvidenceV2 withCanonicalChecksum(String checksum) {
        return new PlacementContainmentEvidenceV2(
                evidenceVersion,
                containmentContractVersion,
                policy,
                placementId,
                operationId,
                worldId,
                placementPlanBinding,
                envelopeBinding,
                snapshotBinding,
                unionEffectEnvelope,
                findings,
                scanStats,
                verdict,
                createdAt,
                checksum);
    }

    public void requireBindings(
            PlacementPlanV2 plan,
            PlacementEnvelopePlanV2 envelope,
            PlacementSnapshotPlanV2 snapshot
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!placementId.equals(plan.placementId())
                || !operationId.equals(plan.operationId())
                || !worldId.equals(plan.target().worldId())) {
            throw new IllegalArgumentException("containment evidence identity mismatch");
        }
        if (!placementPlanBinding.sourcePlacementPlanChecksum().equals(plan.canonicalChecksum())) {
            throw new IllegalArgumentException("containment placement plan binding mismatch");
        }
        if (!envelopeBinding.sourceEnvelopePlanChecksum().equals(envelope.canonicalChecksum())
                || !envelopeBinding.sourceMutationEnvelopeChecksum()
                .equals(envelope.mutationEnvelopeChecksum())) {
            throw new IllegalArgumentException("containment envelope binding mismatch");
        }
        if (!snapshotBinding.sourceSnapshotPlanChecksum().equals(snapshot.canonicalChecksum())) {
            throw new IllegalArgumentException("containment snapshot binding mismatch");
        }
        if (!unionEffectEnvelope.equals(envelope.unionEffectEnvelope())) {
            throw new IllegalArgumentException("containment effect envelope mismatch");
        }
        policy.requireMatchesEnvelope(envelope.physicsPolicy());
    }

    public enum Verdict {
        CONTAINED
    }

    public enum FindingRuleV2 {
        CLASSIFICATION,
        FLUID_CLOSURE,
        GRAVITY_SUPPORT,
        NEIGHBOR_RADIUS,
        BOUNDARY_SEAL,
        PHYSICS_CLASS_DECLARATION
    }

    public record FindingV2(
            String findingId,
            FindingRuleV2 rule,
            PlacementPhysicsClassV2 physicsClass,
            String detail,
            String evidenceHash
    ) {
        public FindingV2 {
            findingId = nonBlank(findingId, "findingId", 64);
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(physicsClass, "physicsClass");
            detail = nonBlank(detail, "detail", 256);
            evidenceHash = checksum(evidenceHash, "evidenceHash");
        }
    }

    public record ScanStats(
            long scannedBlocks,
            int cacheEntries,
            int cacheHits,
            int bfsNodes,
            int fluidComponents,
            int gravityBlocks,
            int neighborBlocks
    ) {
        public ScanStats {
            if (scannedBlocks < 0 || cacheEntries < 0 || cacheHits < 0 || bfsNodes < 0
                    || fluidComponents < 0 || gravityBlocks < 0 || neighborBlocks < 0) {
                throw new IllegalArgumentException("scan stats must be non-negative");
            }
        }
    }

    public record PlacementPlanBinding(
            int bindingVersion,
            String sourcePlacementPlanChecksum,
            String bindingContractVersion
    ) {
        public static final int VERSION = 1;
        public static final String CONTRACT_VERSION = "release-2-placement-containment-plan-binding-v1";

        public PlacementPlanBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown containment placement plan binding");
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
                "release-2-placement-containment-envelope-binding-v1";

        public EnvelopeBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown containment envelope binding");
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
                "release-2-placement-containment-snapshot-binding-v1";

        public SnapshotBinding {
            if (bindingVersion != VERSION || !CONTRACT_VERSION.equals(bindingContractVersion)) {
                throw new IllegalArgumentException("unknown containment snapshot binding");
            }
            sourceSnapshotPlanChecksum = checksum(sourceSnapshotPlanChecksum, "sourceSnapshotPlanChecksum");
        }
    }

    private static List<FindingV2> normalizeFindings(List<FindingV2> findings) {
        List<FindingV2> sorted = new ArrayList<>(findings.size());
        Set<String> ids = new HashSet<>();
        for (FindingV2 finding : findings) {
            Objects.requireNonNull(finding, "findings");
            if (!ids.add(finding.findingId())) {
                throw new IllegalArgumentException("duplicate containment finding id");
            }
            sorted.add(finding);
        }
        sorted.sort(Comparator.comparing(FindingV2::findingId));
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
