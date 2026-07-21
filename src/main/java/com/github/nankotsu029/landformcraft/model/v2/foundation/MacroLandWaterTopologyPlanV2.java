package com.github.nankotsu029.landformcraft.model.v2.foundation;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;

/**
 * Frozen V2-9-12 macro land-water topology contract. Encodes bay/cove/headland/peninsula/isthmus/
 * strait/enclosed-basin/coastal-embayment as {@code MACRO_CONSTRAINT} regions, not FeatureKinds.
 */
public record MacroLandWaterTopologyPlanV2(
        int planVersion,
        String topologyId,
        String contractVersion,
        int width,
        int length,
        String landWaterMaskFieldId,
        String zoneLabelFieldId,
        String regionIndexFieldId,
        List<Region> regions,
        List<Adjacency> adjacencies,
        List<Containment> containments,
        List<ZoneBinding> zoneBindings,
        int minimumIsthmusWidthBlocks,
        int minimumStraitWidthBlocks,
        int supportRadiusXZ,
        long estimatedGraphWorkUnits,
        long estimatedRasterCells,
        String geometryChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String CONTRACT_VERSION = "macro-land-water-topology-contract-v1";
    public static final String MODULE_ID = "v2.foundation.macro-land-water-topology";
    public static final String MODULE_VERSION = "0.1.0-v2-9-12";
    public static final String LAND_WATER_MASK_FIELD_ID = "foundation.topology.land-water-mask";
    public static final String ZONE_LABEL_FIELD_ID = "foundation.topology.zone-label";
    public static final String REGION_INDEX_FIELD_ID = "foundation.topology.region-index";
    public static final int MAXIMUM_REGIONS = 64;
    public static final int MAXIMUM_ADJACENCIES = 256;
    public static final int MAXIMUM_CONTAINMENTS = 64;
    public static final int MAXIMUM_ZONE_BINDINGS = 64;
    public static final int MAXIMUM_DIMENSION = ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING;
    public static final long MAXIMUM_GRAPH_WORK_UNITS = 64_000_000L;
    public static final long MAXIMUM_RASTER_CELLS = ScaleDimensionPolicyV2.MEDIUM_MAXIMUM_CELLS;

    public enum Medium { LAND, WATER }

    public enum MacroRegionKind {
        BAY(Medium.WATER),
        COVE(Medium.WATER),
        HEADLAND(Medium.LAND),
        PENINSULA(Medium.LAND),
        ISTHMUS(Medium.LAND),
        STRAIT(Medium.WATER),
        ENCLOSED_BASIN(Medium.WATER),
        COASTAL_EMBAYMENT(Medium.WATER),
        UNLABELED_LAND(Medium.LAND),
        UNLABELED_WATER(Medium.WATER);

        private final Medium medium;

        MacroRegionKind(Medium medium) {
            this.medium = medium;
        }

        public Medium medium() {
            return medium;
        }
    }

    public MacroLandWaterTopologyPlanV2 {
        if (planVersion != VERSION) {
            throw new IllegalArgumentException("macro land-water topology planVersion must be 1");
        }
        topologyId = FoundationValidationV2.slug(topologyId, "topologyId");
        contractVersion = FoundationValidationV2.nonBlank(contractVersion, "contractVersion", 64);
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException("unknown macro land-water topology contract version");
        }
        if (width < 2 || width > MAXIMUM_DIMENSION || length < 2 || length > MAXIMUM_DIMENSION) {
            throw new IllegalArgumentException("macro land-water topology dimensions outside 2.." + MAXIMUM_DIMENSION);
        }
        landWaterMaskFieldId = FoundationValidationV2.qualified(landWaterMaskFieldId, "landWaterMaskFieldId");
        zoneLabelFieldId = zoneLabelFieldId == null || zoneLabelFieldId.isBlank()
                ? ""
                : FoundationValidationV2.qualified(zoneLabelFieldId, "zoneLabelFieldId");
        regionIndexFieldId = FoundationValidationV2.qualified(regionIndexFieldId, "regionIndexFieldId");
        if (!LAND_WATER_MASK_FIELD_ID.equals(landWaterMaskFieldId)
                || !REGION_INDEX_FIELD_ID.equals(regionIndexFieldId)) {
            throw new IllegalArgumentException("macro land-water topology field IDs are fixed");
        }
        if (!zoneLabelFieldId.isEmpty() && !ZONE_LABEL_FIELD_ID.equals(zoneLabelFieldId)) {
            throw new IllegalArgumentException("macro land-water topology zone field ID is fixed");
        }
        regions = FoundationValidationV2.sorted(regions, "regions", MAXIMUM_REGIONS,
                Comparator.comparing(Region::regionId));
        adjacencies = FoundationValidationV2.sorted(adjacencies, "adjacencies", MAXIMUM_ADJACENCIES,
                Comparator.comparing(Adjacency::edgeId));
        containments = FoundationValidationV2.sorted(containments, "containments", MAXIMUM_CONTAINMENTS,
                Comparator.comparing(Containment::parentRegionId)
                        .thenComparing(Containment::childRegionId));
        zoneBindings = FoundationValidationV2.sorted(zoneBindings, "zoneBindings", MAXIMUM_ZONE_BINDINGS,
                Comparator.comparingInt(ZoneBinding::label)
                        .thenComparing(ZoneBinding::regionId));
        if (minimumIsthmusWidthBlocks < 1 || minimumIsthmusWidthBlocks > 64
                || minimumStraitWidthBlocks < 1 || minimumStraitWidthBlocks > 64) {
            throw new IllegalArgumentException("macro land-water topology min-width outside 1..64");
        }
        if (supportRadiusXZ < 0 || supportRadiusXZ > 32) {
            throw new IllegalArgumentException("macro land-water topology supportRadiusXZ outside 0..32");
        }
        if (estimatedGraphWorkUnits < 1 || estimatedGraphWorkUnits > MAXIMUM_GRAPH_WORK_UNITS
                || estimatedRasterCells < 1 || estimatedRasterCells > MAXIMUM_RASTER_CELLS) {
            throw new IllegalArgumentException("macro land-water topology budget is invalid");
        }
        long expectedCells = (long) width * (long) length;
        if (estimatedRasterCells != expectedCells) {
            throw new IllegalArgumentException("macro land-water topology raster cell count mismatch");
        }
        geometryChecksum = FoundationValidationV2.checksum(geometryChecksum, "geometryChecksum");
        canonicalChecksum = FoundationValidationV2.checksum(canonicalChecksum, "canonicalChecksum");
        validateGraph(regions, adjacencies, containments, zoneBindings);
    }

    public MacroLandWaterTopologyPlanV2 withCanonicalChecksum(String checksum) {
        return new MacroLandWaterTopologyPlanV2(
                planVersion, topologyId, contractVersion, width, length,
                landWaterMaskFieldId, zoneLabelFieldId, regionIndexFieldId,
                regions, adjacencies, containments, zoneBindings,
                minimumIsthmusWidthBlocks, minimumStraitWidthBlocks, supportRadiusXZ,
                estimatedGraphWorkUnits, estimatedRasterCells, geometryChecksum, checksum);
    }

    private static void validateGraph(
            List<Region> regions,
            List<Adjacency> adjacencies,
            List<Containment> containments,
            List<ZoneBinding> zoneBindings
    ) {
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("macro land-water topology requires regions");
        }
        Map<String, Region> byId = new HashMap<>();
        for (Region region : regions) {
            if (byId.put(region.regionId(), region) != null) {
                throw new IllegalArgumentException("duplicate macro topology region id");
            }
            if (region.kind().medium() != region.medium()) {
                throw new IllegalArgumentException("macro topology region kind/medium mismatch");
            }
        }
        Set<String> edgeIds = new HashSet<>();
        for (Adjacency adjacency : adjacencies) {
            if (!edgeIds.add(adjacency.edgeId())) {
                throw new IllegalArgumentException("duplicate macro topology adjacency id");
            }
            Region first = byId.get(adjacency.firstRegionId());
            Region second = byId.get(adjacency.secondRegionId());
            if (first == null || second == null) {
                throw new IllegalArgumentException("macro topology adjacency references unknown region");
            }
            if (adjacency.firstRegionId().compareTo(adjacency.secondRegionId()) >= 0) {
                throw new IllegalArgumentException("macro topology adjacency endpoints must be ordered");
            }
            if (first.medium() == second.medium()) {
                throw new IllegalArgumentException("macro topology adjacency must cross land/water");
            }
        }
        Set<String> nesting = new HashSet<>();
        for (Containment containment : containments) {
            String key = containment.parentRegionId() + ">" + containment.childRegionId();
            if (!nesting.add(key)) {
                throw new IllegalArgumentException("duplicate macro topology containment");
            }
            Region parent = byId.get(containment.parentRegionId());
            Region child = byId.get(containment.childRegionId());
            if (parent == null || child == null) {
                throw new IllegalArgumentException("macro topology containment references unknown region");
            }
            if (parent.medium() != Medium.LAND || child.medium() != Medium.WATER) {
                throw new IllegalArgumentException("macro topology containment must be land⊃water");
            }
            if (containment.parentRegionId().equals(containment.childRegionId())) {
                throw new IllegalArgumentException("macro topology self-containment is forbidden");
            }
        }
        Set<Integer> labels = new HashSet<>();
        for (ZoneBinding binding : zoneBindings) {
            if (!labels.add(binding.label())) {
                throw new IllegalArgumentException("duplicate macro topology zone label");
            }
            Region region = byId.get(binding.regionId());
            if (region == null) {
                throw new IllegalArgumentException("macro topology zone binding references unknown region");
            }
            if (binding.kind() != region.kind()) {
                throw new IllegalArgumentException("macro topology zone binding kind mismatch");
            }
            if (binding.kind() == MacroRegionKind.UNLABELED_LAND
                    || binding.kind() == MacroRegionKind.UNLABELED_WATER) {
                throw new IllegalArgumentException("macro topology zone binding cannot target unlabeled kind");
            }
        }
    }

    public record Region(
            String regionId,
            MacroRegionKind kind,
            Medium medium,
            int cellCount,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            int centroidX,
            int centroidZ,
            int minNeckWidthBlocks
    ) {
        public Region {
            regionId = FoundationValidationV2.slug(regionId, "regionId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(medium, "medium");
            if (cellCount < 1 || minX > maxX || minZ > maxZ
                    || minX < 0 || minZ < 0 || centroidX < minX || centroidX > maxX
                    || centroidZ < minZ || centroidZ > maxZ
                    || minNeckWidthBlocks < 0 || minNeckWidthBlocks > 1_000) {
                throw new IllegalArgumentException("macro topology region bounds are invalid");
            }
        }
    }

    public record Adjacency(
            String edgeId,
            String firstRegionId,
            String secondRegionId,
            int sharedEdgeLengthBlocks,
            int minContactWidthBlocks
    ) {
        public Adjacency {
            edgeId = FoundationValidationV2.slug(edgeId, "edgeId");
            firstRegionId = FoundationValidationV2.slug(firstRegionId, "firstRegionId");
            secondRegionId = FoundationValidationV2.slug(secondRegionId, "secondRegionId");
            if (sharedEdgeLengthBlocks < 1 || sharedEdgeLengthBlocks > 1_000_000
                    || minContactWidthBlocks < 1 || minContactWidthBlocks > 1_000) {
                throw new IllegalArgumentException("macro topology adjacency metrics are invalid");
            }
        }
    }

    public record Containment(String parentRegionId, String childRegionId) {
        public Containment {
            parentRegionId = FoundationValidationV2.slug(parentRegionId, "parentRegionId");
            childRegionId = FoundationValidationV2.slug(childRegionId, "childRegionId");
        }
    }

    public record ZoneBinding(int label, MacroRegionKind kind, String regionId) {
        public ZoneBinding {
            if (label < 1 || label > 65_534) {
                throw new IllegalArgumentException("macro topology zone label outside 1..65534");
            }
            Objects.requireNonNull(kind, "kind");
            regionId = FoundationValidationV2.slug(regionId, "regionId");
        }
    }
}
