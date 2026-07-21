package com.github.nankotsu029.landformcraft.core.v2.migration;

import com.github.nankotsu029.landformcraft.model.CardinalDirection;
import com.github.nankotsu029.landformcraft.model.StructureIntent;
import com.github.nankotsu029.landformcraft.model.TerrainIntent;
import com.github.nankotsu029.landformcraft.model.TerrainZone;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.migration.LegacyMigrationReportV2.UnmappedElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Maps a strictly read v1 intent onto the v2 intent contract (V2-12-04).
 *
 * <h2>Why so little is carried over</h2>
 *
 * <p>A v2 {@code Feature} is defined by normalized geometry — a point, spline or polygon in the
 * request's coordinate system. A v1 intent has none: a {@code zone} states only a type, a coarse
 * compass hint ({@code preferredArea}) and an area share, and the relief, coastline and water
 * intents are whole-map scalars. Turning either into a v2 feature would mean choosing positions and
 * shapes v1 never stated, which is exactly the inference the v2 contract forbids (AGENTS.md §6,
 * task-index V2-12-04 scope).</p>
 *
 * <p>So this mapper carries over only what v1 stated exactly, and returns everything else as an
 * {@link UnmappedElement} for the migration report. The result is a valid, verifiable v2 intent that
 * makes no claim v1 did not make; the operator then adds geometry through the normal v2 design
 * stage. {@code ProvenanceSource.UPGRADED_V1} with {@code ConfirmationState.UNCONFIRMED} records
 * exactly that: the upgrade itself is exact, the resulting design is not yet confirmed.</p>
 */
public final class LegacyV1IntentMapperV2 {
    /** v1 fields this mapper carries into the v2 intent without interpretation. */
    private static final List<String> MAPPED_FIELDS = List.of("schemaVersion", "theme");

    /** One mapping outcome: the v2 intent plus everything v1 stated that v2 cannot express. */
    public record MappingV2(TerrainIntentV2 intent, List<UnmappedElement> unmapped, List<String> mappedFields) {
        public MappingV2 {
            Objects.requireNonNull(intent, "intent");
            unmapped = List.copyOf(Objects.requireNonNull(unmapped, "unmapped"));
            mappedFields = List.copyOf(Objects.requireNonNull(mappedFields, "mappedFields"));
        }
    }

    public MappingV2 map(String intentId, TerrainIntent source) {
        Objects.requireNonNull(intentId, "intentId");
        Objects.requireNonNull(source, "source");

        TerrainIntentV2 intent = new TerrainIntentV2(
                TerrainIntentV2.VERSION,
                intentId,
                source.theme(),
                new TerrainIntentV2.CoordinateSystem(
                        TerrainIntentV2.HorizontalCoordinates.NORMALIZED_XZ,
                        TerrainIntentV2.CoordinateOrigin.NORTH_WEST,
                        TerrainIntentV2.XAxis.EAST,
                        TerrainIntentV2.ZAxis.SOUTH,
                        TerrainIntentV2.VerticalCoordinates.BLOCK_Y_OR_SURFACE_OFFSET),
                List.of(),
                List.of(),
                List.of(),
                new TerrainIntentV2.EnvironmentDescriptor("", "", ""),
                List.of(),
                List.of(),
                new TerrainIntentV2.Provenance(
                        TerrainIntentV2.ProvenanceSource.UPGRADED_V1,
                        intentId,
                        TerrainIntentV2.FIXED_SCALE,
                        TerrainIntentV2.ConfirmationState.UNCONFIRMED));
        return new MappingV2(intent, unmapped(source), MAPPED_FIELDS);
    }

    private static List<UnmappedElement> unmapped(TerrainIntent source) {
        String noGeometry = "v1 states no position or shape for this, and v2 features require "
                + "normalized geometry; deriving one would invent terrain v1 never described";
        List<UnmappedElement> unmapped = new ArrayList<>();
        unmapped.add(new UnmappedElement("intent:topology", "topology",
                "v2 has no whole-map topology enum; topology follows from the features and their "
                        + "relations, which v1 does not carry (" + source.topology() + ")"));
        if (!source.seaSides().isEmpty()) {
            unmapped.add(new UnmappedElement("intent:sea-sides", "seaSides",
                    "v2 classifies map edges through per-feature edge constraints, which need feature "
                            + "geometry v1 does not carry (" + sortedSides(source.seaSides()) + ")"));
        }
        unmapped.add(new UnmappedElement("intent:land-ratio", "landRatio",
                "whole-map land share is not a v2 constraint subject; v2 constraints bind to a "
                        + "feature (" + source.landRatio() + ")"));
        unmapped.add(new UnmappedElement("intent:relief", "relief",
                "whole-map relief scalars have no v2 constraint subject; " + noGeometry));
        unmapped.add(new UnmappedElement("intent:coastline", "coastline",
                "coastline irregularity, bay count and cape count describe features v1 never placed; "
                        + noGeometry));
        unmapped.add(new UnmappedElement("intent:water", "water",
                "river, lake, sea depth and shelf counts describe features v1 never placed; "
                        + noGeometry));
        for (TerrainZone zone : source.zones()) {
            unmapped.add(new UnmappedElement("zone:" + zone.id(), "zones[]",
                    "v1 zone of type " + zone.type() + " with preferredArea " + zone.preferredArea()
                            + " and areaShare " + zone.areaShare() + "; " + noGeometry));
        }
        int index = 0;
        for (StructureIntent structure : source.structures()) {
            unmapped.add(new UnmappedElement("structure:" + index++ + ":" + structure.type(), "structures[]",
                    "a v2 structure request must name the feature it sits on; this v1 structure names "
                            + "zone '" + structure.preferredZone() + "', which has no v2 feature"));
        }
        return List.copyOf(unmapped);
    }

    private static String sortedSides(java.util.Set<CardinalDirection> sides) {
        return sides.stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(","));
    }
}
