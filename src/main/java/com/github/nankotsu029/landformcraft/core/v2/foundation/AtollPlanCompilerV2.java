package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.AtollPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Compiles the {@code ATOLL} COMPOSITE_PRESET from CORAL_REEF + LAGOON + REEF_PASS child features.
 * Does not invent a FeatureKind or dedicated world generator.
 */
public final class AtollPlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);

    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public AtollPlanV2 compile(TerrainIntentV2 intent, WorldBlueprintV2.Bounds bounds) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");

        TerrainIntentV2.Feature reef = requireFeature(intent, TerrainIntentV2.FeatureKind.CORAL_REEF, "v2.atoll-missing-reef");
        TerrainIntentV2.Feature lagoon = requireFeature(intent, TerrainIntentV2.FeatureKind.LAGOON, "v2.atoll-missing-lagoon");
        TerrainIntentV2.Feature pass = requireFeature(intent, TerrainIntentV2.FeatureKind.REEF_PASS, "v2.atoll-missing-pass");

        validateLagoonEnclosedByReef(intent, lagoon.id(), reef.id());
        validatePassConnectivity(intent, pass.id(), reef.id(), lagoon.id());

        Optional<TerrainIntentV2.Feature> islet = intent.features().stream()
                .filter(feature -> feature.kind() == TerrainIntentV2.FeatureKind.SINGLE_ISLAND)
                .findFirst();
        String isletFeatureId = "";
        String isletGeometryChecksum = AtollPlanV2.EMPTY_CHECKSUM;
        if (islet.isPresent()) {
            validateIsletOwnership(intent, islet.get().id(), lagoon.id());
            isletFeatureId = islet.get().id();
            isletGeometryChecksum = codec.geometryChecksum(islet.get().geometry());
        }

        String reefGeometryChecksum = codec.geometryChecksum(reef.geometry());
        String lagoonGeometryChecksum = codec.geometryChecksum(lagoon.geometry());
        String passGeometryChecksum = codec.geometryChecksum(pass.geometry());
        int support = Math.min(64, Math.max(8, midpoint(((TerrainIntentV2.CoralReefParameters) reef.parameters())
                .reefWidthBlocks())));
        long work = Math.multiplyExact((long) bounds.width(), Math.multiplyExact(bounds.length(), 5L));
        if (work > AtollPlanV2.MAXIMUM_WORK_UNITS) {
            throw new FoundationSliceException("v2.atoll-budget", "atoll work budget exceeded");
        }
        String geometryChecksum = bindingChecksum(
                reefGeometryChecksum, lagoonGeometryChecksum, passGeometryChecksum, isletGeometryChecksum);

        return new AtollPlanV2(
                AtollPlanV2.VERSION,
                "atoll." + reef.id(),
                AtollPlanV2.CONTRACT_VERSION,
                reef.id(),
                reefGeometryChecksum,
                lagoon.id(),
                lagoonGeometryChecksum,
                pass.id(),
                passGeometryChecksum,
                isletFeatureId,
                isletGeometryChecksum,
                support,
                work,
                geometryChecksum,
                ZERO);
    }

    private static TerrainIntentV2.Feature requireFeature(
            TerrainIntentV2 intent,
            TerrainIntentV2.FeatureKind kind,
            String ruleId
    ) {
        return intent.features().stream()
                .filter(feature -> feature.kind() == kind)
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException(ruleId, "ATOLL requires a " + kind + " feature"));
    }

    static void validateLagoonEnclosedByReef(TerrainIntentV2 intent, String lagoonId, String reefId) {
        List<TerrainIntentV2.Relation> enclosed = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.ENCLOSED_BY
                        && relation.from().equals("feature:" + lagoonId)
                        && relation.to().equals("feature:" + reefId))
                .toList();
        if (enclosed.isEmpty()) {
            throw new FoundationSliceException("v2.atoll-missing-relation",
                    "ATOLL requires HARD ENCLOSED_BY lagoon to reef");
        }
        if (enclosed.size() != 1 || enclosed.getFirst().strength() != TerrainIntentV2.Strength.HARD) {
            throw new FoundationSliceException("v2.atoll-missing-relation",
                    "ATOLL allows exactly one HARD ENCLOSED_BY lagoon to reef");
        }
    }

    static void validatePassConnectivity(
            TerrainIntentV2 intent,
            String passId,
            String reefId,
            String lagoonId
    ) {
        List<TerrainIntentV2.Relation> carves = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.CARVES_THROUGH
                        && relation.from().equals("feature:" + passId)
                        && relation.to().equals("feature:" + reefId))
                .toList();
        List<TerrainIntentV2.Relation> connects = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.CONNECTS_TO
                        && relation.from().equals("feature:" + passId)
                        && relation.to().equals("feature:" + lagoonId))
                .toList();
        if (carves.isEmpty() || connects.isEmpty()) {
            throw new FoundationSliceException("v2.atoll-disconnected",
                    "ATOLL requires reef pass CARVES_THROUGH reef and CONNECTS_TO lagoon");
        }
        if (carves.size() != 1 || connects.size() != 1
                || carves.getFirst().strength() != TerrainIntentV2.Strength.HARD
                || connects.getFirst().strength() != TerrainIntentV2.Strength.HARD) {
            throw new FoundationSliceException("v2.atoll-missing-relation",
                    "ATOLL requires exactly one HARD CARVES_THROUGH and one HARD CONNECTS_TO for reef pass");
        }
    }

    private static void validateIsletOwnership(TerrainIntentV2 intent, String isletId, String lagoonId) {
        boolean owned = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .anyMatch(relation -> (relation.kind() == TerrainIntentV2.RelationKind.WITHIN
                        || relation.kind() == TerrainIntentV2.RelationKind.ENCLOSED_BY)
                        && relation.from().equals("feature:" + isletId)
                        && relation.to().equals("feature:" + lagoonId));
        if (!owned) {
            throw new FoundationSliceException("v2.atoll-islet-ownership",
                    "optional islet requires HARD WITHIN or ENCLOSED_BY lagoon");
        }
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    static String bindingChecksum(
            String reefGeometry,
            String lagoonGeometry,
            String passGeometry,
            String isletGeometry
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(reefGeometry.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(lagoonGeometry.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(passGeometry.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(isletGeometry.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
