package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.placement.PlacementPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.envelope.PlacementEnvelopeCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.FilePlacementSafetyStoreV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementDiskSpaceProbeV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.reservation.PlacementReservationConfirmCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.safety.PlacementContainmentPreflightV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.safety.PlacementContainmentWorldViewV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementSnapshotAllCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.snapshot.PlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseCoreArtifactsV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseCorePublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseCoreVerificationV2;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseCoreVerifierV2;
import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentEvidenceV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementContainmentPolicyV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPhysicsClassV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementReservationPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementSnapshotPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleClassV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Builds a fully sealed V2-6-01..05 prerequisite chain for V2-6-06／V2-6-07 tests. */
public final class PlacementApplyTestFixtureV2 {
    public static final CancellationToken NEVER = () -> false;
    public static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-18T12:00:00Z"), ZoneOffset.UTC);

    public final Path releasesRoot;
    public final Path safetyFile;
    public final FilePlacementSafetyStoreV2 safetyStore;
    public final PlacementSnapshotAllCompilerV2 snapshotCompiler;
    public final PlacementPlanV2 plan;
    public final PlacementEnvelopePlanV2 envelope;
    public final PlacementReservationPlanV2 reservation;
    public final PlacementJournalV2 preSnapshotJournal;
    public final PlacementSnapshotPlanV2 snapshot;
    public final PlacementJournalV2 journal;
    public final PlacementContainmentEvidenceV2 evidence;
    public final String manifestChecksum;
    public final Map<String, WorldAabbV2> mutationRegions;
    public final Map<String, List<PlacementDesiredBlockV2>> desiredBlocks;

    private PlacementApplyTestFixtureV2(
            Path releasesRoot,
            Path safetyFile,
            FilePlacementSafetyStoreV2 safetyStore,
            PlacementSnapshotAllCompilerV2 snapshotCompiler,
            PlacementPlanV2 plan,
            PlacementEnvelopePlanV2 envelope,
            PlacementReservationPlanV2 reservation,
            PlacementJournalV2 preSnapshotJournal,
            PlacementSnapshotPlanV2 snapshot,
            PlacementJournalV2 journal,
            PlacementContainmentEvidenceV2 evidence,
            String manifestChecksum,
            Map<String, WorldAabbV2> mutationRegions,
            Map<String, List<PlacementDesiredBlockV2>> desiredBlocks
    ) {
        this.releasesRoot = releasesRoot;
        this.safetyFile = safetyFile;
        this.safetyStore = safetyStore;
        this.snapshotCompiler = snapshotCompiler;
        this.plan = plan;
        this.envelope = envelope;
        this.reservation = reservation;
        this.preSnapshotJournal = preSnapshotJournal;
        this.snapshot = snapshot;
        this.journal = journal;
        this.evidence = evidence;
        this.manifestChecksum = manifestChecksum;
        this.mutationRegions = mutationRegions;
        this.desiredBlocks = desiredBlocks;
    }

    public static PlacementApplyTestFixtureV2 create(Path root, boolean twoTiles) throws Exception {
        Files.createDirectories(root);
        Path releasesRoot = root.resolve("releases");
        String releaseId = twoTiles ? "apply-two-tiles" : "apply-one-tile";
        ReleaseCoreArtifactsV2 release = new ReleaseCorePublisherV2().publish(
                releasesRoot, new ReleaseManifestV2(releaseId), false, NEVER);
        ReleaseCoreVerificationV2 releaseVerification = new ReleaseCoreVerifierV2()
                .verifyDirectory(release.releaseDirectory());
        String manifestChecksum = releaseVerification.manifest().canonicalChecksum();

        UUID placementId = twoTiles
                ? UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2")
                : UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
        UUID operationId = twoTiles
                ? UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2")
                : UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        UUID worldId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        int width = twoTiles ? 256 : 4;
        PlacementPlanV2 compiledPlan = new PlacementPlanCompilerV2().compile(
                new PlacementPlanCompilerV2.PlacementCompileRequestV2(
                        placementId,
                        operationId,
                        releaseId,
                        PlacementPlanV2.PlacementActorV2.console(),
                        new PlacementPlanV2.PlacementTargetV2(
                                worldId,
                                "world",
                                PlacementPlanV2.AnchorKind.MINIMUM_CORNER,
                                0, 64, 0,
                                0, 64, 0,
                                width - 1, 80, 3),
                        new PlacementPlanV2.ReleaseBindingV2(
                                PlacementPlanV2.ReleaseBindingV2.VERSION,
                                2,
                                releaseId,
                                manifestChecksum,
                                PlacementPlanV2.ReleaseBindingV2.CONTRACT_VERSION),
                        List.of(),
                        TilePlanV2.of(
                                width,
                                4,
                                ScaleProfileV2.defaults(ScaleClassV2.SMALL))))
                .plan();

        LinkedHashMap<String, WorldAabbV2> regions = new LinkedHashMap<>();
        LinkedHashMap<String, List<PlacementDesiredBlockV2>> desired = new LinkedHashMap<>();
        List<PlacementEnvelopeCompilerV2.TileMutationContentV2> contents = new ArrayList<>();
        List<PlacementPhysicsClassV2> physics = List.of(
                PlacementPhysicsClassV2.SOLID,
                PlacementPhysicsClassV2.AIR,
                PlacementPhysicsClassV2.FLUID);
        if (twoTiles) {
            addTile(
                    compiledPlan,
                    regions,
                    desired,
                    contents,
                    0,
                    new WorldAabbV2(126, 64, 0, 127, 64, 0),
                    physics,
                    List.of("minecraft:stone", "minecraft:water[level=0]"),
                    List.of(0, 0));
            addTile(
                    compiledPlan,
                    regions,
                    desired,
                    contents,
                    1,
                    new WorldAabbV2(128, 64, 0, 129, 64, 0),
                    physics,
                    List.of("minecraft:water[level=0]", "minecraft:stone"),
                    List.of(0, 0));
        } else {
            addTile(
                    compiledPlan,
                    regions,
                    desired,
                    contents,
                    0,
                    new WorldAabbV2(0, 64, 0, 3, 64, 1),
                    physics,
                    List.of(
                            "minecraft:stone",
                            "minecraft:air",
                            "minecraft:deepslate",
                            "minecraft:water[level=0]",
                            "minecraft:stone",
                            "minecraft:cave_air",
                            "minecraft:lava[level=0]",
                            "minecraft:stone"),
                    List.of(0, 0, 10, 10, 0, 10, 10, 10));
        }

        PlacementEnvelopeCompilerV2.CompiledEnvelopeV2 compiledEnvelope =
                new PlacementEnvelopeCompilerV2().compile(
                        new PlacementEnvelopeCompilerV2.EnvelopeCompileRequestV2(
                                compiledPlan,
                                new WorldAabbV2(-512, -64, -512, 512, 320, 512),
                                PlacementEnvelopePlanV2.PhysicsPolicy.standard(),
                                new PlacementEnvelopePlanV2.ResourceBudget(
                                        PlacementEnvelopePlanV2.ResourceBudget.VERSION,
                                        64,
                                        50_000_000L,
                                        1_000_000_000L,
                                        8_192L,
                                        PlacementEnvelopePlanV2.MAX_CANONICAL_BYTES),
                                contents));

        Path safetyFile = root.resolve("placement-safety-v2.json");
        FilePlacementSafetyStoreV2 safetyStore = new FilePlacementSafetyStoreV2(
                safetyFile,
                root.resolve("snapshots"),
                CLOCK,
                fixedProbe());
        PlacementReservationConfirmCompilerV2 reservationCompiler =
                new PlacementReservationConfirmCompilerV2(safetyStore, CLOCK);
        PlacementReservationConfirmCompilerV2.PreparedReservationV2 prepared =
                reservationCompiler.prepare(
                        new PlacementReservationConfirmCompilerV2.ReservationConfirmRequestV2(
                                compiledEnvelope.boundPlacementPlan(),
                                compiledEnvelope.envelopePlan(),
                                reservationBudget(),
                                null,
                                "99999999-8888-7777-6666-555555555555"));
        reservationCompiler.verifyAndConsume(
                prepared.plan(),
                compiledEnvelope.envelopePlan(),
                prepared.reservationPlan(),
                prepared.plan().actor(),
                prepared.plaintextToken());

        PlacementSnapshotAllCompilerV2 snapshotCompiler =
                new PlacementSnapshotAllCompilerV2(safetyStore, CLOCK, fixedProbe());
        PlacementSnapshotAllCompilerV2.PreparedSnapshotAllV2 snap = snapshotCompiler.snapshotAll(
                new PlacementSnapshotAllCompilerV2.SnapshotAllRequestV2(
                        prepared.plan(),
                        compiledEnvelope.envelopePlan(),
                        prepared.reservationPlan(),
                        prepared.journal(),
                        new StoneSnapshotGateway(),
                        snapshotBudget(),
                        NEVER,
                        ignored -> { }));
        PlacementContainmentEvidenceV2 evidence = new PlacementContainmentPreflightV2(CLOCK).preflight(
                new PlacementContainmentPreflightV2.ContainmentRequestV2(
                        prepared.plan(),
                        compiledEnvelope.envelopePlan(),
                        snap.snapshotPlan(),
                        prepared.reservationPlan(),
                        snap.snapshotCompleteJournal(),
                        PlacementContainmentPolicyV2.standard(),
                        (x, y, z) -> "minecraft:stone"));

        return new PlacementApplyTestFixtureV2(
                releasesRoot,
                safetyFile,
                safetyStore,
                snapshotCompiler,
                prepared.plan(),
                compiledEnvelope.envelopePlan(),
                prepared.reservationPlan(),
                prepared.journal(),
                snap.snapshotPlan(),
                snap.snapshotCompleteJournal(),
                evidence,
                manifestChecksum,
                Collections.unmodifiableMap(new LinkedHashMap<>(regions)),
                Collections.unmodifiableMap(new LinkedHashMap<>(desired)));
    }

    public StrictPlacementApplyPrerequisiteVerifierV2 strictVerifier() {
        return new StrictPlacementApplyPrerequisiteVerifierV2(
                releasesRoot, safetyStore, snapshotCompiler);
    }

    public ImmutableSource source(boolean reverseRegistration) {
        LinkedHashMap<String, List<PlacementDesiredBlockV2>> ordered = new LinkedHashMap<>();
        List<String> tileIds = new ArrayList<>(desiredBlocks.keySet());
        if (reverseRegistration) {
            Collections.reverse(tileIds);
        }
        for (String tileId : tileIds) {
            ordered.put(tileId, desiredBlocks.get(tileId));
        }
        List<Integer> ordinals = desiredBlocks.values().stream()
                .flatMap(List::stream)
                .map(PlacementDesiredBlockV2::overlayOrdinal)
                .distinct()
                .sorted()
                .toList();
        return new ImmutableSource(
                new PlacementCanonicalBlockSourceV2.SourceBindingV2(
                        PlacementCanonicalBlockSourceV2.SOURCE_CONTRACT_VERSION,
                        manifestChecksum,
                        plan.requiredCapabilities(),
                        ordinals,
                        twoTileFingerprint(desiredBlocks.size())),
                ordered,
                mutationRegions);
    }

    public PlacementApplyRequestV2 request(
            PlacementCanonicalBlockSourceV2 source,
            CancellationToken cancellation
    ) {
        return new PlacementApplyRequestV2(
                plan,
                envelope,
                reservation,
                snapshot,
                evidence,
                journal,
                source,
                cancellation);
    }

    private static void addTile(
            PlacementPlanV2 plan,
            Map<String, WorldAabbV2> regions,
            Map<String, List<PlacementDesiredBlockV2>> desired,
            List<PlacementEnvelopeCompilerV2.TileMutationContentV2> contents,
            int tileIndex,
            WorldAabbV2 region,
            List<PlacementPhysicsClassV2> physics,
            List<String> states,
            List<Integer> ordinals
    ) {
        PlacementPlanV2.TileRefV2 tile = plan.tileOrder().tiles().get(tileIndex);
        contents.add(new PlacementEnvelopeCompilerV2.TileMutationContentV2(
                tile.tileId(), tile.tileIndex(), region, physics));
        regions.put(tile.tileId(), region);
        if (states.size() != region.volumeBlocks() || states.size() != ordinals.size()) {
            throw new IllegalArgumentException("fixture state coverage mismatch");
        }
        List<PlacementDesiredBlockV2> blocks = new ArrayList<>();
        int index = 0;
        for (int y = region.minY(); y <= region.maxY(); y++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int x = region.minX(); x <= region.maxX(); x++) {
                    String state = states.get(index);
                    blocks.add(new PlacementDesiredBlockV2(
                            x,
                            y,
                            z,
                            state,
                            PlacementDesiredBlockV2.classify(state),
                            ordinals.get(index),
                            tileIndex));
                    index++;
                }
            }
        }
        desired.put(tile.tileId(), List.copyOf(blocks));
    }

    private static String twoTileFingerprint(int tiles) {
        return tiles == 2 ? "2".repeat(64) : "1".repeat(64);
    }

    private static PlacementDiskSpaceProbeV2 fixedProbe() {
        return new PlacementDiskSpaceProbeV2() {
            @Override
            public long usableBytes(Path root) {
                return 10L * 1024L * 1024L * 1024L;
            }

            @Override
            public String fileStoreKey(Path root) {
                return "test|apply-v2";
            }
        };
    }

    private static PlacementReservationPlanV2.ResourceBudget reservationBudget() {
        return new PlacementReservationPlanV2.ResourceBudget(
                PlacementReservationPlanV2.ResourceBudget.VERSION,
                64,
                PlacementReservationPlanV2.MAXIMUM_ENTRIES,
                1_000_000_000L,
                8_192L,
                PlacementReservationPlanV2.MAX_CANONICAL_BYTES);
    }

    private static PlacementSnapshotPlanV2.ResourceBudget snapshotBudget() {
        return new PlacementSnapshotPlanV2.ResourceBudget(
                PlacementSnapshotPlanV2.ResourceBudget.VERSION,
                64,
                1_000_000_000L,
                4_096,
                8_192L,
                PlacementSnapshotPlanV2.MAX_CANONICAL_BYTES);
    }

    public static final class ImmutableSource implements PlacementCanonicalBlockSourceV2 {
        private final SourceBindingV2 binding;
        private final Map<String, List<PlacementDesiredBlockV2>> blocks;
        private final Map<String, WorldAabbV2> regions;
        private final List<String> registrationOrder;
        final AtomicInteger openCount = new AtomicInteger();
        final AtomicInteger closeCount = new AtomicInteger();

        private ImmutableSource(
                SourceBindingV2 binding,
                Map<String, List<PlacementDesiredBlockV2>> blocks,
                Map<String, WorldAabbV2> regions
        ) {
            this.binding = binding;
            this.blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
            this.regions = regions;
            this.registrationOrder = List.copyOf(blocks.keySet());
        }

        @Override
        public SourceBindingV2 binding() {
            return binding;
        }

        @Override
        public BlockCursorV2 openTile(
                PlacementPlanV2 plan,
                PlacementPlanV2.TileRefV2 tile,
                WorldAabbV2 mutationRegion
        ) throws IOException {
            List<PlacementDesiredBlockV2> values = blocks.get(tile.tileId());
            if (values == null || !Objects.equals(regions.get(tile.tileId()), mutationRegion)) {
                throw new IOException("unknown fixture tile or region");
            }
            openCount.incrementAndGet();
            return new BlockCursorV2() {
                private int index;
                private boolean closed;

                @Override
                public PlacementDesiredBlockV2 next() throws IOException {
                    if (closed) {
                        throw new IOException("fixture cursor already closed");
                    }
                    return index < values.size() ? values.get(index++) : null;
                }

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        closeCount.incrementAndGet();
                    }
                }
            };
        }

        List<String> registrationOrder() {
            return registrationOrder;
        }
    }

    private static final class StoneSnapshotGateway implements PlacementWorldGatewayV2 {
        @Override
        public void streamRegionBlockStates(
                UUID worldId,
                WorldAabbV2 region,
                PlacementBlockStateConsumerV2 consumer
        ) throws IOException {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    for (int x = region.minX(); x <= region.maxX(); x++) {
                        consumer.accept(x, y, z, "minecraft:stone");
                    }
                }
            }
        }
    }
}
