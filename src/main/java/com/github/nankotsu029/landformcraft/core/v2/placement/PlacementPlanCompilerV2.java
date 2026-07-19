package com.github.nankotsu029.landformcraft.core.v2.placement;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalStateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementTileStateV2;
import com.github.nankotsu029.landformcraft.model.v2.scale.TilePlanV2;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Compiles a sealed Release 2 placement plan and an initial {@link PlacementJournalStateV2#PLANNED}
 * journal. Does not verify Release contents, calculate envelopes, reserve disk/regions, snapshot,
 * or apply blocks.
 */
public final class PlacementPlanCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public CompiledPlacementV2 compile(PlacementCompileRequestV2 request) {
        Objects.requireNonNull(request, "request");
        PlacementPlanV2.TileOrderV2 tileOrder = tileOrder(request.tilePlan());
        PlacementPlanV2.ResourceBudget budget = new PlacementPlanV2.ResourceBudget(
                PlacementPlanV2.ResourceBudget.VERSION,
                Math.min(PlacementPlanV2.MAXIMUM_TILES, Math.max(tileOrder.tiles().size(), 1)),
                PlacementPlanV2.MAXIMUM_JOURNAL_ENTRIES,
                estimateCanonicalBytes(tileOrder.tiles().size()),
                PlacementPlanV2.MAX_CANONICAL_BYTES,
                32L * 1024L);
        PlacementPlanV2 draft = new PlacementPlanV2(
                PlacementPlanV2.VERSION,
                PlacementPlanV2.PLACEMENT_CONTRACT_VERSION,
                request.placementId(),
                request.operationId(),
                request.requestId(),
                request.actor(),
                request.target(),
                request.releaseBinding(),
                request.requiredCapabilities(),
                tileOrder,
                PlacementPlanV2.EnvelopeReferencesV2.unbound(),
                PlacementPlanV2.ReservationConfirmationBindingV2.unbound(request.actor()),
                budget,
                PlacementPlanV2.UNBOUND_CHECKSUM);
        PlacementPlanV2 sealedPlan = codec.sealPlacementPlan(draft);

        List<PlacementJournalV2.PlacementTileEntryV2> tiles = new ArrayList<>(tileOrder.tiles().size());
        for (PlacementPlanV2.TileRefV2 tile : tileOrder.tiles()) {
            tiles.add(new PlacementJournalV2.PlacementTileEntryV2(
                    tile.tileId(),
                    tile.tileIndex(),
                    PlacementTileStateV2.PENDING,
                    "",
                    ""));
        }
        PlacementJournalV2 journalDraft = new PlacementJournalV2(
                PlacementJournalV2.VERSION,
                PlacementJournalV2.JOURNAL_CONTRACT_VERSION,
                sealedPlan,
                sealedPlan.canonicalChecksum(),
                PlacementJournalStateV2.PLANNED,
                tiles,
                0L,
                0L,
                Instant.EPOCH.toString(),
                "planned",
                PlacementPlanV2.UNBOUND_CHECKSUM);
        PlacementJournalV2 sealedJournal = codec.sealPlacementJournal(journalDraft);
        return new CompiledPlacementV2(sealedPlan, sealedJournal);
    }

    private static PlacementPlanV2.TileOrderV2 tileOrder(TilePlanV2 tilePlan) {
        Objects.requireNonNull(tilePlan, "tilePlan");
        if (tilePlan.tileCount() > PlacementPlanV2.MAXIMUM_TILES) {
            throw new IllegalArgumentException("placement tile count exceeds maximum");
        }
        List<PlacementPlanV2.TileRefV2> tiles = new ArrayList<>(tilePlan.tileCount());
        for (int index = 0; index < tilePlan.tileCount(); index++) {
            TilePlanV2.TileV2 tile = tilePlan.tileByIndex(index);
            tiles.add(new PlacementPlanV2.TileRefV2(
                    tile.tileId(),
                    tile.index(),
                    tile.coreMinX(),
                    tile.coreMinZ(),
                    tile.coreWidth(),
                    tile.coreLength()));
        }
        return new PlacementPlanV2.TileOrderV2(PlacementPlanV2.TileOrderV2.CONTRACT_VERSION, tiles);
    }

    private static long estimateCanonicalBytes(int tileCount) {
        return Math.min(
                PlacementPlanV2.MAX_CANONICAL_BYTES,
                Math.addExact(4_096L, Math.multiplyExact(tileCount, 192L)));
    }

    public record CompiledPlacementV2(PlacementPlanV2 plan, PlacementJournalV2 journal) {
        public CompiledPlacementV2 {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(journal, "journal");
        }
    }

    public record PlacementCompileRequestV2(
            UUID placementId,
            UUID operationId,
            String requestId,
            PlacementPlanV2.PlacementActorV2 actor,
            PlacementPlanV2.PlacementTargetV2 target,
            PlacementPlanV2.ReleaseBindingV2 releaseBinding,
            List<String> requiredCapabilities,
            TilePlanV2 tilePlan
    ) {
        public PlacementCompileRequestV2 {
            Objects.requireNonNull(placementId, "placementId");
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(releaseBinding, "releaseBinding");
            Objects.requireNonNull(requiredCapabilities, "requiredCapabilities");
            Objects.requireNonNull(tilePlan, "tilePlan");
        }
    }
}
