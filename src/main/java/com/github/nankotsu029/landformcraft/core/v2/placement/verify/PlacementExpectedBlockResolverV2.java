package com.github.nankotsu029.landformcraft.core.v2.placement.verify;

import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyPassV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementCanonicalBlockSourceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementDesiredBlockV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementEnvelopePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the canonical expected block-state stream for the union effect envelope: snapshot
 * baseline outside mutation AABBs, overlaid by the final solid→air carve→fluid resolved source
 * inside mutation AABBs. Never samples; every envelope coordinate has exactly one expected state.
 */
public final class PlacementExpectedBlockResolverV2 {
    private final PlacementPlanV2 plan;
    private final PlacementEnvelopePlanV2 envelope;
    private final PlacementCanonicalBlockSourceV2 blockSource;
    private final SnapshotBaselineV2 baseline;
    private final Map<Coord, String> mutationOverrides;
    private final Map<Coord, Integer> mutationOverlayOrdinals;

    public PlacementExpectedBlockResolverV2(
            PlacementPlanV2 plan,
            PlacementEnvelopePlanV2 envelope,
            PlacementCanonicalBlockSourceV2 blockSource,
            SnapshotBaselineV2 baseline
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.envelope = Objects.requireNonNull(envelope, "envelope");
        this.blockSource = Objects.requireNonNull(blockSource, "blockSource");
        this.baseline = Objects.requireNonNull(baseline, "baseline");
        MutationMaps maps = resolveMutations();
        this.mutationOverrides = Map.copyOf(maps.overrides());
        this.mutationOverlayOrdinals = Map.copyOf(maps.ordinals());
    }

    public String expectedAt(int x, int y, int z) {
        WorldAabbV2 union = envelope.unionEffectEnvelope();
        if (!union.contains(x, y, z)) {
            throw new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.SOURCE_INVALID,
                    "expected resolver queried outside the union effect envelope",
                    true);
        }
        Coord key = new Coord(x, y, z);
        String overlay = mutationOverrides.get(key);
        if (overlay != null) {
            return overlay;
        }
        return baseline.blockState(x, y, z);
    }

    public String streamChecksum() {
        MessageDigest digest = sha256();
        WorldAabbV2 union = envelope.unionEffectEnvelope();
        for (int y = union.minY(); y <= union.maxY(); y++) {
            for (int z = union.minZ(); z <= union.maxZ(); z++) {
                for (int x = union.minX(); x <= union.maxX(); x++) {
                    digest.update(expectedAt(x, y, z).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public Integer overlayOrdinalAt(int x, int y, int z) {
        return mutationOverlayOrdinals.get(new Coord(x, y, z));
    }

    public long volumeBlocks() {
        return envelope.unionEffectEnvelope().volumeBlocks();
    }

    @FunctionalInterface
    public interface SnapshotBaselineV2 {
        String blockState(int x, int y, int z);
    }

    private MutationMaps resolveMutations() {
        Map<Coord, String> overrides = new HashMap<>();
        Map<Coord, Integer> ordinals = new HashMap<>();
        List<PlacementEnvelopePlanV2.TileEnvelopeV2> tiles = envelope.tiles();
        for (int index = 0; index < tiles.size(); index++) {
            PlacementEnvelopePlanV2.TileEnvelopeV2 tileEnvelope = tiles.get(index);
            PlacementPlanV2.TileRefV2 tile = plan.tileOrder().tiles().get(index);
            if (tile.tileIndex() != tileEnvelope.tileIndex()
                    || !tile.tileId().equals(tileEnvelope.tileId())) {
                throw new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.BINDING_MISMATCH,
                        "expected resolver tile order differs from the envelope plan",
                        true);
            }
            Map<Coord, PlacementDesiredBlockV2> byCoord = new HashMap<>();
            try (PlacementCanonicalBlockSourceV2.BlockCursorV2 cursor = blockSource.openTile(
                    plan, tile, tileEnvelope.mutationAabb())) {
                PlacementDesiredBlockV2 block;
                while ((block = cursor.next()) != null) {
                    Coord key = new Coord(block.x(), block.y(), block.z());
                    byCoord.merge(key, block, PlacementExpectedBlockResolverV2::preferLaterPass);
                }
            } catch (IOException | RuntimeException exception) {
                throw new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.SOURCE_INVALID,
                        "canonical block source failed while building expected mutations",
                        true,
                        exception);
            }
            if (byCoord.size() != tileEnvelope.mutationAabb().volumeBlocks()) {
                throw new PlacementVerifyExceptionV2(
                        PlacementVerifyFailureCodeV2.SOURCE_INVALID,
                        "canonical block source did not cover the mutation AABB exactly",
                        true);
            }
            for (Map.Entry<Coord, PlacementDesiredBlockV2> entry : byCoord.entrySet()) {
                overrides.put(entry.getKey(), entry.getValue().blockState());
                ordinals.put(entry.getKey(), entry.getValue().overlayOrdinal());
            }
        }
        return new MutationMaps(overrides, ordinals);
    }

    private static PlacementDesiredBlockV2 preferLaterPass(
            PlacementDesiredBlockV2 left,
            PlacementDesiredBlockV2 right
    ) {
        int cmp = Integer.compare(passRank(left.pass()), passRank(right.pass()));
        if (cmp == 0) {
            cmp = Integer.compare(left.overlayOrdinal(), right.overlayOrdinal());
        }
        if (cmp == 0) {
            throw new PlacementVerifyExceptionV2(
                    PlacementVerifyFailureCodeV2.SOURCE_INVALID,
                    "canonical source emitted duplicate blocks for the same pass/ordinal",
                    true);
        }
        return cmp < 0 ? right : left;
    }

    private static int passRank(PlacementApplyPassV2 pass) {
        return switch (pass) {
            case SOLID -> 10;
            case AIR_CARVE -> 20;
            case FLUID -> 30;
        };
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record Coord(int x, int y, int z) {
    }

    private record MutationMaps(Map<Coord, String> overrides, Map<Coord, Integer> ordinals) {
    }
}
