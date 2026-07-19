package com.github.nankotsu029.landformcraft.core.v2.placement.safety;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.minecraft.PlacementBlockPhysicsCatalogV2;
import com.github.nankotsu029.landformcraft.format.v2.minecraft.PlacementBlockPhysicsKindV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentEvidenceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPhysicsClassV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Apply-time fluid／gravity／neighbor containment preflight (V2-6-05). Proves that physics-sensitive
 * planned content cannot affect blocks outside the sealed union effect envelope. Never applies,
 * settles, or rolls back world state — missing proof is a hard reject, not a deferred detection.
 */
public final class PlacementContainmentPreflightV2 {
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final Clock clock;

    public PlacementContainmentPreflightV2() {
        this(Clock.systemUTC());
    }

    public PlacementContainmentPreflightV2(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PlacementContainmentEvidenceV2 preflight(ContainmentRequestV2 request) {
        Objects.requireNonNull(request, "request");
        PlacementPlanV2 plan = request.placementPlan();
        PlacementEnvelopePlanV2 envelope = request.envelopePlan();
        PlacementSnapshotPlanV2 snapshot = request.snapshotPlan();
        PlacementJournalV2 journal = request.journal();
        PlacementContainmentPolicyV2 policy = request.policy();
        PlacementContainmentWorldViewV2 world = request.worldView();

        if (journal.state() != PlacementJournalStateV2.SNAPSHOT_COMPLETE) {
            throw new PlacementContainmentExceptionV2(
                    PlacementContainmentFailureCodeV2.JOURNAL_STATE_INVALID,
                    "containment requires SNAPSHOT_COMPLETE journal; was " + journal.state());
        }
        if (!PlacementContainmentPolicyV2.POLICY_VERSION.equals(policy.policyVersion())) {
            throw new PlacementContainmentExceptionV2(
                    PlacementContainmentFailureCodeV2.UNKNOWN_POLICY_VERSION,
                    "unknown containment policy version");
        }
        if (!PlacementBlockPhysicsCatalogV2.CATALOG_VERSION.equals(policy.catalogVersion())) {
            throw new PlacementContainmentExceptionV2(
                    PlacementContainmentFailureCodeV2.UNKNOWN_CATALOG_VERSION,
                    "unknown block physics catalog version");
        }
        try {
            policy.requireMatchesEnvelope(envelope.physicsPolicy());
        } catch (IllegalArgumentException mismatch) {
            throw new PlacementContainmentExceptionV2(
                    PlacementContainmentFailureCodeV2.POLICY_MISMATCH, mismatch.getMessage());
        }
        try {
            snapshot.requireBindings(plan, envelope, request.reservationPlan());
        } catch (IllegalArgumentException mismatch) {
            throw new PlacementContainmentExceptionV2(
                    PlacementContainmentFailureCodeV2.BINDING_MISMATCH, mismatch.getMessage());
        }
        if (!journal.plan().placementId().equals(plan.placementId())
                || !journal.plan().operationId().equals(plan.operationId())
                || !journal.planChecksum().equals(plan.canonicalChecksum())) {
            throw new PlacementContainmentExceptionV2(
                    PlacementContainmentFailureCodeV2.BINDING_MISMATCH,
                    "journal identity does not match placement plan");
        }

        WorldAabbV2 effect = envelope.unionEffectEnvelope();
        if (effect.volumeBlocks() > policy.budget().maximumScannedBlocks()) {
            throw new PlacementContainmentExceptionV2(
                    PlacementContainmentFailureCodeV2.SCAN_BUDGET_EXCEEDED,
                    "effect envelope volume exceeds containment scan budget");
        }

        ClassifierCache cache = new ClassifierCache(policy.budget().maximumCacheEntries());
        ScanAccumulator scan = new ScanAccumulator();
        EnumSet<PlacementPhysicsClassV2> observed = EnumSet.noneOf(PlacementPhysicsClassV2.class);
        List<long[]> fluidSeeds = new ArrayList<>();
        List<long[]> gravityBlocks = new ArrayList<>();
        List<long[]> neighborBlocks = new ArrayList<>();
        List<PlacementContainmentEvidenceV2.FindingV2> findings = new ArrayList<>();

        // Canonical order: X fastest, then Z, then Y — matches PlacementWorldGatewayV2.
        for (int y = effect.minY(); y <= effect.maxY(); y++) {
            for (int z = effect.minZ(); z <= effect.maxZ(); z++) {
                for (int x = effect.minX(); x <= effect.maxX(); x++) {
                    scan.scannedBlocks++;
                    if (scan.scannedBlocks > policy.budget().maximumScannedBlocks()) {
                        throw new PlacementContainmentExceptionV2(
                                PlacementContainmentFailureCodeV2.SCAN_BUDGET_EXCEEDED,
                                "scanned block count exceeds containment budget");
                    }
                    String state = read(world, x, y, z);
                    PlacementBlockPhysicsKindV2 kind = classify(cache, scan, state);
                    PlacementPhysicsClassV2 envelopeClass = toEnvelopeClass(kind);
                    observed.add(envelopeClass);
                    boolean inMutation = insideAnyMutation(envelope, x, y, z);
                    // Fluid closure seeds from mutation only so existing open water outside the
                    // planned paste does not vacuous-fail SOLID placements. Flood-fill still walks
                    // AIR+FLUID through the whole effect envelope and rejects exterior escapes.
                    if (inMutation && kind == PlacementBlockPhysicsKindV2.FLUID) {
                        fluidSeeds.add(pack(x, y, z));
                    }
                    if (inMutation && kind == PlacementBlockPhysicsKindV2.GRAVITY) {
                        gravityBlocks.add(pack(x, y, z));
                    }
                    if (inMutation && kind == PlacementBlockPhysicsKindV2.NEIGHBOR) {
                        neighborBlocks.add(pack(x, y, z));
                    }
                }
            }
        }

        requireDeclaredPhysics(envelope, observed);
        findings.add(finding(
                "classification-pass",
                PlacementContainmentEvidenceV2.FindingRuleV2.CLASSIFICATION,
                PlacementPhysicsClassV2.SOLID,
                "classified " + scan.scannedBlocks + " blocks with catalog "
                        + PlacementBlockPhysicsCatalogV2.CATALOG_VERSION,
                hash("classification|" + scan.scannedBlocks + "|" + cache.entries())));
        findings.add(finding(
                "physics-declaration-pass",
                PlacementContainmentEvidenceV2.FindingRuleV2.PHYSICS_CLASS_DECLARATION,
                PlacementPhysicsClassV2.SOLID,
                "observed physics classes are covered by envelope declarations",
                hash("declaration|" + observed)));

        int fluidComponents = verifyFluidClosure(
                world, cache, scan, policy, effect, fluidSeeds, findings);
        verifyGravitySupport(world, cache, scan, policy, effect, gravityBlocks, findings);
        verifyNeighborRadius(policy, effect, neighborBlocks, findings);
        findings.add(finding(
                "boundary-seal-pass",
                PlacementContainmentEvidenceV2.FindingRuleV2.BOUNDARY_SEAL,
                PlacementPhysicsClassV2.FLUID,
                "no fluid-connected escape to exterior air within effect envelope",
                hash("boundary|" + fluidComponents + "|" + fluidSeeds.size())));

        PlacementContainmentEvidenceV2 draft = new PlacementContainmentEvidenceV2(
                PlacementContainmentEvidenceV2.VERSION,
                PlacementContainmentEvidenceV2.CONTAINMENT_CONTRACT_VERSION,
                policy,
                plan.placementId(),
                plan.operationId(),
                plan.target().worldId(),
                new PlacementContainmentEvidenceV2.PlacementPlanBinding(
                        PlacementContainmentEvidenceV2.PlacementPlanBinding.VERSION,
                        plan.canonicalChecksum(),
                        PlacementContainmentEvidenceV2.PlacementPlanBinding.CONTRACT_VERSION),
                new PlacementContainmentEvidenceV2.EnvelopeBinding(
                        PlacementContainmentEvidenceV2.EnvelopeBinding.VERSION,
                        envelope.canonicalChecksum(),
                        envelope.mutationEnvelopeChecksum(),
                        PlacementContainmentEvidenceV2.EnvelopeBinding.CONTRACT_VERSION),
                new PlacementContainmentEvidenceV2.SnapshotBinding(
                        PlacementContainmentEvidenceV2.SnapshotBinding.VERSION,
                        snapshot.canonicalChecksum(),
                        PlacementContainmentEvidenceV2.SnapshotBinding.CONTRACT_VERSION),
                effect,
                findings,
                new PlacementContainmentEvidenceV2.ScanStats(
                        scan.scannedBlocks,
                        cache.entries(),
                        scan.cacheHits,
                        scan.bfsNodes,
                        fluidComponents,
                        gravityBlocks.size(),
                        neighborBlocks.size()),
                PlacementContainmentEvidenceV2.Verdict.CONTAINED,
                clock.instant().toString(),
                PlacementPlanV2.UNBOUND_CHECKSUM);
        PlacementContainmentEvidenceV2 sealed = codec.sealPlacementContainmentEvidence(draft);
        try {
            sealed.requireBindings(plan, envelope, snapshot);
        } catch (IllegalArgumentException mismatch) {
            throw new PlacementContainmentExceptionV2(
                    PlacementContainmentFailureCodeV2.BINDING_MISMATCH, mismatch.getMessage());
        }
        long canonicalBytes = codec.canonicalPlacementContainmentEvidence(sealed)
                .getBytes(StandardCharsets.UTF_8).length;
        if (canonicalBytes > policy.budget().maximumCanonicalBytes()) {
            throw new PlacementContainmentExceptionV2(
                    PlacementContainmentFailureCodeV2.CANONICAL_BUDGET_EXCEEDED,
                    "containment evidence exceeds canonical byte budget");
        }
        return sealed;
    }

    private int verifyFluidClosure(
            PlacementContainmentWorldViewV2 world,
            ClassifierCache cache,
            ScanAccumulator scan,
            PlacementContainmentPolicyV2 policy,
            WorldAabbV2 effect,
            List<long[]> fluidSeeds,
            List<PlacementContainmentEvidenceV2.FindingV2> findings
    ) {
        if (fluidSeeds.isEmpty()) {
            findings.add(finding(
                    "fluid-closure-vacuous",
                    PlacementContainmentEvidenceV2.FindingRuleV2.FLUID_CLOSURE,
                    PlacementPhysicsClassV2.FLUID,
                    "no fluid cells in effect envelope",
                    hash("fluid|empty")));
            return 0;
        }
        Set<Long> visited = new java.util.HashSet<>();
        int components = 0;
        for (long[] seed : fluidSeeds) {
            long key = key(seed[0], seed[1], seed[2]);
            if (!visited.add(key)) {
                continue;
            }
            components++;
            ArrayDeque<long[]> queue = new ArrayDeque<>();
            queue.add(seed);
            scan.bfsNodes++;
            while (!queue.isEmpty()) {
                if (scan.bfsNodes > policy.budget().maximumBfsNodes()) {
                    throw new PlacementContainmentExceptionV2(
                            PlacementContainmentFailureCodeV2.BFS_BUDGET_EXCEEDED,
                            "fluid closure BFS exceeded node budget");
                }
                long[] cell = queue.removeFirst();
                int x = (int) cell[0];
                int y = (int) cell[1];
                int z = (int) cell[2];
                for (int dir = 0; dir < 6; dir++) {
                    int nx = x + DX[dir];
                    int ny = y + DY[dir];
                    int nz = z + DZ[dir];
                    if (!effect.contains(nx, ny, nz)) {
                        // Escape hatch: fluid-connected passable cell touches exterior.
                        throw new PlacementContainmentExceptionV2(
                                PlacementContainmentFailureCodeV2.UNCONTAINED_FLUID,
                                "fluid can escape effect envelope at (" + x + "," + y + "," + z + ")");
                    }
                    PlacementBlockPhysicsKindV2 neighborKind =
                            classify(cache, scan, read(world, nx, ny, nz));
                    if (neighborKind == PlacementBlockPhysicsKindV2.AIR
                            || neighborKind == PlacementBlockPhysicsKindV2.FLUID) {
                        long nKey = key(nx, ny, nz);
                        if (visited.add(nKey)) {
                            queue.add(pack(nx, ny, nz));
                            scan.bfsNodes++;
                        }
                    }
                }
            }
        }
        findings.add(finding(
                "fluid-closure-pass",
                PlacementContainmentEvidenceV2.FindingRuleV2.FLUID_CLOSURE,
                PlacementPhysicsClassV2.FLUID,
                "sealed " + components + " fluid component(s) inside effect envelope",
                hash("fluid|" + components + "|" + fluidSeeds.size())));
        return components;
    }

    private void verifyGravitySupport(
            PlacementContainmentWorldViewV2 world,
            ClassifierCache cache,
            ScanAccumulator scan,
            PlacementContainmentPolicyV2 policy,
            WorldAabbV2 effect,
            List<long[]> gravityBlocks,
            List<PlacementContainmentEvidenceV2.FindingV2> findings
    ) {
        if (gravityBlocks.isEmpty()) {
            findings.add(finding(
                    "gravity-support-vacuous",
                    PlacementContainmentEvidenceV2.FindingRuleV2.GRAVITY_SUPPORT,
                    PlacementPhysicsClassV2.GRAVITY,
                    "no gravity cells in mutation envelopes",
                    hash("gravity|empty")));
            return;
        }
        for (long[] cell : gravityBlocks) {
            int x = (int) cell[0];
            int y = (int) cell[1];
            int z = (int) cell[2];
            boolean supported = false;
            for (int fall = 1; fall <= policy.gravityFallBlocks(); fall++) {
                int ny = Math.subtractExact(y, fall);
                if (!effect.contains(x, ny, z)) {
                    throw new PlacementContainmentExceptionV2(
                            PlacementContainmentFailureCodeV2.UNCONTAINED_GRAVITY,
                            "gravity fall path leaves effect envelope at ("
                                    + x + "," + ny + "," + z + ")");
                }
                PlacementBlockPhysicsKindV2 kind = classify(cache, scan, read(world, x, ny, z));
                if (kind == PlacementBlockPhysicsKindV2.SOLID) {
                    supported = true;
                    break;
                }
                if (kind == PlacementBlockPhysicsKindV2.AIR
                        || kind == PlacementBlockPhysicsKindV2.FLUID
                        || kind == PlacementBlockPhysicsKindV2.GRAVITY) {
                    continue;
                }
                throw new PlacementContainmentExceptionV2(
                        PlacementContainmentFailureCodeV2.UNCONTAINED_GRAVITY,
                        "gravity fall path hits non-support kind " + kind + " at ("
                                + x + "," + ny + "," + z + ")");
            }
            if (!supported) {
                int beyond = Math.subtractExact(y, policy.gravityFallBlocks() + 1);
                if (!effect.contains(x, beyond, z)) {
                    throw new PlacementContainmentExceptionV2(
                            PlacementContainmentFailureCodeV2.UNCONTAINED_GRAVITY,
                            "gravity fall would leave effect envelope below ("
                                    + x + "," + y + "," + z + ")");
                }
                throw new PlacementContainmentExceptionV2(
                        PlacementContainmentFailureCodeV2.NO_GRAVITY_SUPPORT,
                        "no solid support within gravityFallBlocks for ("
                                + x + "," + y + "," + z + ")");
            }
        }
        findings.add(finding(
                "gravity-support-pass",
                PlacementContainmentEvidenceV2.FindingRuleV2.GRAVITY_SUPPORT,
                PlacementPhysicsClassV2.GRAVITY,
                "supported " + gravityBlocks.size() + " gravity block(s) inside effect envelope",
                hash("gravity|" + gravityBlocks.size())));
    }

    private void verifyNeighborRadius(
            PlacementContainmentPolicyV2 policy,
            WorldAabbV2 effect,
            List<long[]> neighborBlocks,
            List<PlacementContainmentEvidenceV2.FindingV2> findings
    ) {
        if (neighborBlocks.isEmpty()) {
            findings.add(finding(
                    "neighbor-radius-vacuous",
                    PlacementContainmentEvidenceV2.FindingRuleV2.NEIGHBOR_RADIUS,
                    PlacementPhysicsClassV2.NEIGHBOR,
                    "no neighbor-sensitive cells in mutation envelopes",
                    hash("neighbor|empty")));
            return;
        }
        int radius = policy.neighborUpdateRadius();
        for (long[] cell : neighborBlocks) {
            int x = (int) cell[0];
            int y = (int) cell[1];
            int z = (int) cell[2];
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int nx = Math.addExact(x, dx);
                        int ny = Math.addExact(y, dy);
                        int nz = Math.addExact(z, dz);
                        if (!effect.contains(nx, ny, nz)) {
                            throw new PlacementContainmentExceptionV2(
                                    PlacementContainmentFailureCodeV2.UNCONTAINED_NEIGHBOR,
                                    "neighbor update radius leaves effect envelope around ("
                                            + x + "," + y + "," + z + ")");
                        }
                    }
                }
            }
        }
        findings.add(finding(
                "neighbor-radius-pass",
                PlacementContainmentEvidenceV2.FindingRuleV2.NEIGHBOR_RADIUS,
                PlacementPhysicsClassV2.NEIGHBOR,
                "neighbor radius contained for " + neighborBlocks.size() + " block(s)",
                hash("neighbor|" + neighborBlocks.size() + "|" + radius)));
    }

    private static void requireDeclaredPhysics(
            PlacementEnvelopePlanV2 envelope,
            Set<PlacementPhysicsClassV2> observed
    ) {
        EnumSet<PlacementPhysicsClassV2> declared = EnumSet.noneOf(PlacementPhysicsClassV2.class);
        for (PlacementEnvelopePlanV2.TileEnvelopeV2 tile : envelope.tiles()) {
            declared.addAll(tile.physicsClassSet());
        }
        for (PlacementPhysicsClassV2 physicsClass : observed) {
            if (physicsClass == PlacementPhysicsClassV2.SOLID || physicsClass == PlacementPhysicsClassV2.AIR) {
                continue;
            }
            if (!declared.contains(physicsClass)) {
                throw new PlacementContainmentExceptionV2(
                        PlacementContainmentFailureCodeV2.PHYSICS_CLASS_UNDERSTATED,
                        "effect envelope understates physics class " + physicsClass);
            }
        }
    }

    private static boolean insideAnyMutation(PlacementEnvelopePlanV2 envelope, int x, int y, int z) {
        for (PlacementEnvelopePlanV2.TileEnvelopeV2 tile : envelope.tiles()) {
            if (tile.mutationAabb().contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static String read(PlacementContainmentWorldViewV2 world, int x, int y, int z) {
        try {
            String state = world.blockStateAt(x, y, z);
            if (state == null || state.isBlank()) {
                throw new PlacementContainmentExceptionV2(
                        PlacementContainmentFailureCodeV2.ENVELOPE_GAP,
                        "world view gap at (" + x + "," + y + "," + z + ")");
            }
            return state;
        } catch (PlacementContainmentExceptionV2 known) {
            throw known;
        } catch (RuntimeException exception) {
            throw new PlacementContainmentExceptionV2(
                    PlacementContainmentFailureCodeV2.WORLD_VIEW_FAILURE,
                    "world view failed at (" + x + "," + y + "," + z + ")",
                    exception);
        }
    }

    private static PlacementBlockPhysicsKindV2 classify(
            ClassifierCache cache,
            ScanAccumulator scan,
            String blockState
    ) {
        String identifier = PlacementBlockPhysicsCatalogV2.identifierOf(blockState);
        PlacementBlockPhysicsKindV2 cached = cache.get(identifier);
        if (cached != null) {
            scan.cacheHits++;
            return cached;
        }
        var found = PlacementBlockPhysicsCatalogV2.find(blockState);
        if (found.isEmpty()) {
            throw new PlacementContainmentExceptionV2(
                    PlacementContainmentFailureCodeV2.UNKNOWN_BLOCK_STATE,
                    "unknown block state for containment: " + blockState);
        }
        PlacementBlockPhysicsKindV2 kind = found.get();
        if (kind == PlacementBlockPhysicsKindV2.UNSUPPORTED) {
            throw new PlacementContainmentExceptionV2(
                    PlacementContainmentFailureCodeV2.UNSUPPORTED_BLOCK_STATE,
                    "unsupported block state for containment: " + blockState);
        }
        cache.put(identifier, kind);
        return kind;
    }

    private static PlacementPhysicsClassV2 toEnvelopeClass(PlacementBlockPhysicsKindV2 kind) {
        try {
            return PlacementBlockPhysicsCatalogV2.toEnvelopeClass(kind);
        } catch (IllegalArgumentException exception) {
            throw new PlacementContainmentExceptionV2(
                    PlacementContainmentFailureCodeV2.UNSUPPORTED_BLOCK_STATE,
                    exception.getMessage());
        }
    }

    private static PlacementContainmentEvidenceV2.FindingV2 finding(
            String id,
            PlacementContainmentEvidenceV2.FindingRuleV2 rule,
            PlacementPhysicsClassV2 physicsClass,
            String detail,
            String evidenceHash
    ) {
        return new PlacementContainmentEvidenceV2.FindingV2(id, rule, physicsClass, detail, evidenceHash);
    }

    private static long[] pack(int x, int y, int z) {
        return new long[] {x, y, z};
    }

    private static long key(long x, long y, long z) {
        return (x & 0x1FFFFFL) | ((y & 0x1FFFFFL) << 21) | ((z & 0x1FFFFFL) << 42);
    }

    private static String hash(String material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record ContainmentRequestV2(
            PlacementPlanV2 placementPlan,
            PlacementEnvelopePlanV2 envelopePlan,
            PlacementSnapshotPlanV2 snapshotPlan,
            com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2
                    reservationPlan,
            PlacementJournalV2 journal,
            PlacementContainmentPolicyV2 policy,
            PlacementContainmentWorldViewV2 worldView
    ) {
        public ContainmentRequestV2 {
            Objects.requireNonNull(placementPlan, "placementPlan");
            Objects.requireNonNull(envelopePlan, "envelopePlan");
            Objects.requireNonNull(snapshotPlan, "snapshotPlan");
            Objects.requireNonNull(reservationPlan, "reservationPlan");
            Objects.requireNonNull(journal, "journal");
            Objects.requireNonNull(policy, "policy");
            Objects.requireNonNull(worldView, "worldView");
        }
    }

    private static final class ScanAccumulator {
        private long scannedBlocks;
        private int cacheHits;
        private int bfsNodes;
    }

    private static final class ClassifierCache {
        private final int maximumEntries;
        private final Map<String, PlacementBlockPhysicsKindV2> entries = new HashMap<>();

        private ClassifierCache(int maximumEntries) {
            this.maximumEntries = maximumEntries;
        }

        private PlacementBlockPhysicsKindV2 get(String identifier) {
            return entries.get(identifier);
        }

        private void put(String identifier, PlacementBlockPhysicsKindV2 kind) {
            if (entries.size() >= maximumEntries && !entries.containsKey(identifier)) {
                throw new PlacementContainmentExceptionV2(
                        PlacementContainmentFailureCodeV2.CACHE_BUDGET_EXCEEDED,
                        "classification cache exceeded " + maximumEntries + " entries");
            }
            entries.put(identifier, kind);
        }

        private int entries() {
            return entries.size();
        }
    }
}
