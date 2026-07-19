package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.deposition.DepositionPolygonMathV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.OutwashPlainPlanV2;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Compiles V2-10-02 outwash-plain plans bound to a glacial-ice parent by geometry checksum. */
public final class OutwashPlainPlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);
    private static final Set<TerrainIntentV2.RelationKind> PARENT_RELATIONS = EnumSet.of(
            TerrainIntentV2.RelationKind.DRAINS_TO,
            TerrainIntentV2.RelationKind.EMPTIES_INTO,
            TerrainIntentV2.RelationKind.ADJACENT_TO,
            TerrainIntentV2.RelationKind.UPSTREAM_OF);
    private static final Set<TerrainIntentV2.FeatureKind> GLACIAL_KINDS = EnumSet.of(
            TerrainIntentV2.FeatureKind.VALLEY_GLACIER,
            TerrainIntentV2.FeatureKind.ICE_CAP,
            TerrainIntentV2.FeatureKind.ICE_SHEET);

    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public MoraineFieldPlanCompilerV2.GlacialParentBinding resolveGlacialParent(
            TerrainIntentV2.Feature outwash,
            TerrainIntentV2 intent
    ) {
        Objects.requireNonNull(outwash, "outwash");
        Objects.requireNonNull(intent, "intent");
        String endpoint = "feature:" + outwash.id();
        List<TerrainIntentV2.Relation> parents = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> PARENT_RELATIONS.contains(relation.kind()))
                .filter(relation -> connectsOutwashToGlacial(intent, endpoint, relation))
                .toList();
        if (parents.isEmpty()) {
            throw failure("v2.outwash-missing-glacial-parent",
                    "outwash requires exactly one HARD glacial parent relation");
        }
        if (parents.size() > 1) {
            throw failure("v2.outwash-ambiguous-glacial-parent",
                    "outwash has multiple HARD glacial parent relations");
        }
        TerrainIntentV2.Relation parent = parents.getFirst();
        String parentId = glacialParentId(intent, endpoint, parent);
        TerrainIntentV2.Feature parentFeature = featureOf(intent, parentId);
        return new MoraineFieldPlanCompilerV2.GlacialParentBinding(
                parent.id(),
                parentId,
                codec.geometryChecksum(parentFeature.geometry()));
    }

    public OutwashPlainPlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        if (feature.kind() != TerrainIntentV2.FeatureKind.OUTWASH_PLAIN) {
            throw failure("v2.outwash-kind", "feature kind is not OUTWASH_PLAIN");
        }
        if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)) {
            throw failure("v2.outwash-geometry", "outwash requires POLYGON geometry");
        }
        if (!(feature.parameters() instanceof TerrainIntentV2.OutwashPlainParameters params)) {
            throw failure("v2.outwash-params", "outwash parameters missing");
        }

        MoraineFieldPlanCompilerV2.GlacialParentBinding parent = resolveGlacialParent(feature, intent);
        List<OutwashPlainPlanV2.Ring> rings = toRings(polygon, bounds);
        long interiorCells = estimateInteriorCells(rings, bounds);
        if (interiorCells < 1L) {
            throw failure("v2.outwash-degenerate", "outwash interior is empty or degenerate");
        }

        int sedimentThickness = midpoint(params.sedimentThicknessBlocks());
        int channelSpacing = midpoint(params.channelSpacingBlocks());
        long work = Math.multiplyExact(interiorCells, Math.max(1L, sedimentThickness));
        if (work > OutwashPlainPlanV2.MAXIMUM_RASTER_WORK_UNITS) {
            throw failure("v2.outwash-budget", "outwash raster work units exceed budget");
        }
        int support = Math.min(64, Math.max(4, channelSpacing / 2));

        String meltwaterId = "";
        String meltwaterChecksum = "";
        Optional<TerrainIntentV2.Relation> meltwater = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.DRAINS_TO
                        || relation.kind() == TerrainIntentV2.RelationKind.EMPTIES_INTO)
                .filter(relation -> ("feature:" + feature.id()).equals(relation.from()))
                .findFirst();
        if (meltwater.isPresent()) {
            meltwaterId = meltwater.get().to().substring("feature:".length());
            meltwaterChecksum = codec.geometryChecksum(featureOf(intent, meltwaterId).geometry());
        } else if (!params.meltwaterHandoffFeatureIdHint().isBlank()) {
            throw failure("v2.outwash-meltwater-missing",
                    "meltwater handoff hint requires HARD DRAINS_TO/EMPTIES_INTO");
        }

        return new OutwashPlainPlanV2(
                OutwashPlainPlanV2.VERSION,
                feature.id(),
                parent.glacialParentFeatureId(),
                parent.glacialParentCanonicalChecksum(),
                parent.glacialParentRelationId(),
                meltwaterId,
                meltwaterChecksum,
                sedimentThickness,
                channelSpacing,
                params.flowAzimuthDegrees(),
                rings,
                bounds.width(),
                bounds.length(),
                bounds.minY(),
                bounds.maxY(),
                OutwashPlainPlanV2.SEDIMENT_OWNERSHIP_FIELD_ID,
                OutwashPlainPlanV2.FLOW_DIRECTION_FIELD_ID,
                OutwashPlainPlanV2.MELTWATER_MASK_FIELD_ID,
                support,
                work,
                geometryChecksum,
                ZERO);
    }

    private static boolean connectsOutwashToGlacial(
            TerrainIntentV2 intent,
            String outwashEndpoint,
            TerrainIntentV2.Relation relation
    ) {
        if (outwashEndpoint.equals(relation.from()) && isGlacialEndpoint(intent, relation.to())) {
            return true;
        }
        if (outwashEndpoint.equals(relation.to()) && isGlacialEndpoint(intent, relation.from())) {
            return relation.kind() == TerrainIntentV2.RelationKind.DRAINS_TO
                    || relation.kind() == TerrainIntentV2.RelationKind.EMPTIES_INTO
                    || relation.kind() == TerrainIntentV2.RelationKind.ADJACENT_TO
                    || relation.kind() == TerrainIntentV2.RelationKind.UPSTREAM_OF;
        }
        return false;
    }

    private static String glacialParentId(
            TerrainIntentV2 intent,
            String outwashEndpoint,
            TerrainIntentV2.Relation relation
    ) {
        if (outwashEndpoint.equals(relation.from())) {
            return relation.to().substring("feature:".length());
        }
        return relation.from().substring("feature:".length());
    }

    private static List<OutwashPlainPlanV2.Ring> toRings(
            TerrainIntentV2.PolygonGeometry polygon,
            WorldBlueprintV2.Bounds bounds
    ) {
        List<OutwashPlainPlanV2.Ring> rings = new ArrayList<>(polygon.rings().size());
        for (List<TerrainIntentV2.Point2> ring : polygon.rings()) {
            List<OutwashPlainPlanV2.Vertex> vertices = new ArrayList<>(ring.size());
            for (TerrainIntentV2.Point2 point : ring) {
                vertices.add(new OutwashPlainPlanV2.Vertex(
                        scaleCoordinate(point.xMillionths(), bounds.width()),
                        scaleCoordinate(point.zMillionths(), bounds.length())));
            }
            rings.add(new OutwashPlainPlanV2.Ring(List.copyOf(vertices)));
        }
        return List.copyOf(rings);
    }

    private static long estimateInteriorCells(
            List<OutwashPlainPlanV2.Ring> rings,
            WorldBlueprintV2.Bounds bounds
    ) {
        long minX = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long minZ = Long.MAX_VALUE;
        long maxZ = Long.MIN_VALUE;
        for (OutwashPlainPlanV2.Ring ring : rings) {
            for (OutwashPlainPlanV2.Vertex vertex : ring.vertices()) {
                minX = Math.min(minX, vertex.xMillionths());
                maxX = Math.max(maxX, vertex.xMillionths());
                minZ = Math.min(minZ, vertex.zMillionths());
                maxZ = Math.max(maxZ, vertex.zMillionths());
            }
        }
        int originX = Math.toIntExact(Math.max(0L, Math.floorDiv(minX, TerrainIntentV2.FIXED_SCALE) - 1));
        int originZ = Math.toIntExact(Math.max(0L, Math.floorDiv(minZ, TerrainIntentV2.FIXED_SCALE) - 1));
        int endX = Math.toIntExact(Math.min(bounds.width() - 1L,
                Math.floorDiv(maxX, TerrainIntentV2.FIXED_SCALE) + 1));
        int endZ = Math.toIntExact(Math.min(bounds.length() - 1L,
                Math.floorDiv(maxZ, TerrainIntentV2.FIXED_SCALE) + 1));
        long cells = 0L;
        for (int z = originZ; z <= endZ; z++) {
            for (int x = originX; x <= endX; x++) {
                long cx = Math.addExact(Math.multiplyExact((long) x, TerrainIntentV2.FIXED_SCALE),
                        TerrainIntentV2.FIXED_SCALE / 2L);
                long cz = Math.addExact(Math.multiplyExact((long) z, TerrainIntentV2.FIXED_SCALE),
                        TerrainIntentV2.FIXED_SCALE / 2L);
                if (DepositionPolygonMathV2.containsOutwashRings(rings, cx, cz)) {
                    cells = Math.addExact(cells, 1L);
                }
            }
        }
        return cells;
    }

    private static boolean isGlacialEndpoint(TerrainIntentV2 intent, String endpoint) {
        if (!endpoint.startsWith("feature:")) {
            return false;
        }
        return GLACIAL_KINDS.contains(featureOf(intent, endpoint.substring("feature:".length())).kind());
    }

    private static TerrainIntentV2.Feature featureOf(TerrainIntentV2 intent, String featureId) {
        return intent.features().stream()
                .filter(feature -> feature.id().equals(featureId))
                .findFirst()
                .orElseThrow(() -> failure("v2.outwash-missing-feature",
                        "referenced feature missing: " + featureId));
    }

    private static long scaleCoordinate(long millionths, int blocks) {
        long scaled = Math.multiplyExact(millionths, (long) blocks - 1L);
        if (millionths >= TerrainIntentV2.FIXED_SCALE) {
            return Math.addExact(scaled, TerrainIntentV2.FIXED_SCALE - 1L);
        }
        return scaled;
    }

    private static int midpoint(TerrainIntentV2.IntRange range) {
        return range.minimum() + (range.maximum() - range.minimum()) / 2;
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }
}
