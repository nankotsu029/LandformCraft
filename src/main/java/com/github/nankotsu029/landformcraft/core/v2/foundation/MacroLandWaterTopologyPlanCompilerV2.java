package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.model.v2.foundation.MacroLandWaterTopologyPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MacroLandWaterTopologyPlanV2.Adjacency;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MacroLandWaterTopologyPlanV2.Containment;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MacroLandWaterTopologyPlanV2.MacroRegionKind;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MacroLandWaterTopologyPlanV2.Medium;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MacroLandWaterTopologyPlanV2.Region;
import com.github.nankotsu029.landformcraft.model.v2.foundation.MacroLandWaterTopologyPlanV2.ZoneBinding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Compiles a frozen macro land-water topology from a manual LAND_WATER mask and optional ZONE_LABEL
 * map. Deterministic over tile assembly order and thread count; does not add FeatureKinds.
 */
public final class MacroLandWaterTopologyPlanCompilerV2 {
    public static final String RULE_DISCONNECTED_STRAIT = "v2.topology-disconnected-strait";
    public static final String RULE_COLLAPSED_ISTHMUS = "v2.topology-collapsed-isthmus";
    public static final String RULE_NESTED_BASIN = "v2.topology-nested-basin";
    public static final String RULE_AMBIGUOUS_BOUNDARY = "v2.topology-ambiguous-boundary";
    public static final String RULE_GRAPH_BUDGET = "v2.topology-graph-budget";
    public static final String RULE_RASTER_BUDGET = "v2.topology-raster-budget";
    public static final String RULE_DECODE_BUDGET = "v2.topology-decode-budget";

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    public MacroLandWaterTopologyPlanV2 compile(ManualTopologyInputV2 input) {
        Objects.requireNonNull(input, "input");
        validateInputBudgets(input);

        int width = input.width();
        int length = input.length();
        byte[] mask = input.landWaterMask();
        int[] zones = input.zoneLabels();
        long cells = (long) width * length;

        int[] componentOf = new int[mask.length];
        List<ComponentSeed> seeds = labelComponents(width, length, mask, zones, componentOf);
        if (seeds.size() > MacroLandWaterTopologyPlanV2.MAXIMUM_REGIONS) {
            throw fail(RULE_GRAPH_BUDGET, "region count exceeds budget");
        }

        Map<Integer, MacroRegionKind> labelKinds = Map.copyOf(input.labelKinds());
        List<RegionDraft> drafts = new ArrayList<>();
        Map<Integer, Integer> labelToComponent = new HashMap<>();
        for (int component = 0; component < seeds.size(); component++) {
            drafts.add(measureComponent(
                    width, length, mask, zones, componentOf, component, seeds.get(component),
                    labelKinds, labelToComponent));
        }

        List<Adjacency> adjacencies = buildAdjacencies(width, length, componentOf, drafts);
        Map<Integer, Set<Integer>> sameMediumNeighbors =
                buildSameMediumNeighbors(width, length, componentOf, drafts);
        List<Containment> containments = buildContainments(width, length, componentOf, drafts);
        applySemanticValidation(drafts, sameMediumNeighbors, containments, input);

        List<Region> regions = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            RegionDraft draft = drafts.get(index);
            regions.add(new Region(
                    regionId(index),
                    draft.kind(),
                    draft.medium(),
                    draft.cellCount(),
                    draft.minX(),
                    draft.minZ(),
                    draft.maxX(),
                    draft.maxZ(),
                    draft.centroidX(),
                    draft.centroidZ(),
                    draft.minNeckWidthBlocks()));
        }

        List<ZoneBinding> zoneBindings = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : new TreeMap<>(labelToComponent).entrySet()) {
            int label = entry.getKey();
            int component = entry.getValue();
            MacroRegionKind kind = labelKinds.get(label);
            zoneBindings.add(new ZoneBinding(label, kind, regionId(component)));
        }

        long workUnits = cells + (long) regions.size() * 16L + (long) adjacencies.size() * 8L;
        if (workUnits > MacroLandWaterTopologyPlanV2.MAXIMUM_GRAPH_WORK_UNITS) {
            throw fail(RULE_GRAPH_BUDGET, "graph work units exceed budget");
        }

        String geometryChecksum = geometryChecksum(mask, zones);
        return new MacroLandWaterTopologyPlanV2(
                MacroLandWaterTopologyPlanV2.VERSION,
                input.topologyId(),
                MacroLandWaterTopologyPlanV2.CONTRACT_VERSION,
                width,
                length,
                MacroLandWaterTopologyPlanV2.LAND_WATER_MASK_FIELD_ID,
                zones == null ? "" : MacroLandWaterTopologyPlanV2.ZONE_LABEL_FIELD_ID,
                MacroLandWaterTopologyPlanV2.REGION_INDEX_FIELD_ID,
                regions,
                adjacencies,
                containments,
                zoneBindings,
                input.minimumIsthmusWidthBlocks(),
                input.minimumStraitWidthBlocks(),
                input.supportRadiusXZ(),
                workUnits,
                cells,
                geometryChecksum,
                "0".repeat(64));
    }

    private static void validateInputBudgets(ManualTopologyInputV2 input) {
        int width = input.width();
        int length = input.length();
        if (width < 2 || length < 2
                || width > MacroLandWaterTopologyPlanV2.MAXIMUM_DIMENSION
                || length > MacroLandWaterTopologyPlanV2.MAXIMUM_DIMENSION) {
            throw fail(RULE_RASTER_BUDGET, "mask dimensions outside 2.." + MacroLandWaterTopologyPlanV2.MAXIMUM_DIMENSION);
        }
        long cells = (long) width * length;
        if (cells > MacroLandWaterTopologyPlanV2.MAXIMUM_RASTER_CELLS) {
            throw fail(RULE_RASTER_BUDGET, "raster cell count exceeds budget");
        }
        byte[] mask = input.landWaterMask();
        if (mask == null || mask.length != cells) {
            throw fail(RULE_DECODE_BUDGET, "land-water mask length mismatch");
        }
        int[] zones = input.zoneLabels();
        if (zones != null && zones.length != cells) {
            throw fail(RULE_DECODE_BUDGET, "zone label length mismatch");
        }
        long decodeBytes = cells + (zones == null ? 0L : cells * 4L);
        if (decodeBytes > 8L * 1024L * 1024L) {
            throw fail(RULE_DECODE_BUDGET, "decode budget exceeded");
        }
        for (byte value : mask) {
            if (value != 0 && value != 1) {
                throw fail(RULE_AMBIGUOUS_BOUNDARY, "land-water mask values must be 0 or 1");
            }
        }
        if (zones != null) {
            for (int label : zones) {
                if (label < 0 || label > 65_534) {
                    throw fail(RULE_AMBIGUOUS_BOUNDARY, "zone label outside 0..65534");
                }
            }
        }
        for (Map.Entry<Integer, MacroRegionKind> entry : input.labelKinds().entrySet()) {
            if (entry.getKey() < 1 || entry.getKey() > 65_534) {
                throw fail(RULE_AMBIGUOUS_BOUNDARY, "labelKinds key outside 1..65534");
            }
            if (entry.getValue() == MacroRegionKind.UNLABELED_LAND
                    || entry.getValue() == MacroRegionKind.UNLABELED_WATER) {
                throw fail(RULE_AMBIGUOUS_BOUNDARY, "labelKinds cannot map to unlabeled kinds");
            }
        }
        if (input.minimumIsthmusWidthBlocks() < 1 || input.minimumIsthmusWidthBlocks() > 64
                || input.minimumStraitWidthBlocks() < 1 || input.minimumStraitWidthBlocks() > 64) {
            throw fail(RULE_COLLAPSED_ISTHMUS, "min-width policy outside 1..64");
        }
    }

    /**
     * Connected components are 4-connected cells sharing both medium and zone label. Zone labels
     * therefore split straits/isthmuses from open land/water without needing FeatureKinds.
     */
    private static List<ComponentSeed> labelComponents(
            int width,
            int length,
            byte[] mask,
            int[] zones,
            int[] componentOf
    ) {
        java.util.Arrays.fill(componentOf, -1);
        List<ComponentSeed> seeds = new ArrayList<>();
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                if (componentOf[index] >= 0) {
                    continue;
                }
                byte medium = mask[index];
                int zone = zones == null ? 0 : zones[index];
                int component = seeds.size();
                seeds.add(new ComponentSeed(x, z, medium == 0 ? Medium.LAND : Medium.WATER, zone));
                ArrayDeque<Integer> queue = new ArrayDeque<>();
                queue.add(index);
                componentOf[index] = component;
                while (!queue.isEmpty()) {
                    int current = queue.removeFirst();
                    int cx = current % width;
                    int cz = current / width;
                    for (int dir = 0; dir < 4; dir++) {
                        int nx = cx + DX[dir];
                        int nz = cz + DZ[dir];
                        if (nx < 0 || nz < 0 || nx >= width || nz >= length) {
                            continue;
                        }
                        int neighbor = nz * width + nx;
                        int neighborZone = zones == null ? 0 : zones[neighbor];
                        if (componentOf[neighbor] >= 0
                                || mask[neighbor] != medium
                                || neighborZone != zone) {
                            continue;
                        }
                        componentOf[neighbor] = component;
                        queue.addLast(neighbor);
                    }
                }
            }
        }
        return seeds;
    }

    private static RegionDraft measureComponent(
            int width,
            int length,
            byte[] mask,
            int[] zones,
            int[] componentOf,
            int component,
            ComponentSeed seed,
            Map<Integer, MacroRegionKind> labelKinds,
            Map<Integer, Integer> labelToComponent
    ) {
        int cellCount = 0;
        int minX = width;
        int minZ = length;
        int maxX = -1;
        int maxZ = -1;
        long sumX = 0;
        long sumZ = 0;
        int minNeck = Integer.MAX_VALUE;
        boolean touchesBorder = false;
        Integer soleLabel = seed.zoneLabel() == 0 ? null : seed.zoneLabel();

        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                if (componentOf[index] != component) {
                    continue;
                }
                cellCount++;
                minX = Math.min(minX, x);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxZ = Math.max(maxZ, z);
                sumX += x;
                sumZ += z;
                if (x == 0 || z == 0 || x == width - 1 || z == length - 1) {
                    touchesBorder = true;
                }
                int widthAtCell = localWidth(width, length, mask, zones, x, z, mask[index],
                        zones == null ? 0 : zones[index]);
                minNeck = Math.min(minNeck, widthAtCell);
            }
        }
        if (cellCount < 1) {
            throw fail(RULE_GRAPH_BUDGET, "empty component");
        }
        MacroRegionKind kind;
        if (soleLabel != null) {
            MacroRegionKind mapped = labelKinds.get(soleLabel);
            if (mapped == null) {
                throw fail(RULE_AMBIGUOUS_BOUNDARY, "zone label " + soleLabel + " has no kind");
            }
            if (mapped.medium() != seed.medium()) {
                throw fail(RULE_AMBIGUOUS_BOUNDARY,
                        "zone label " + soleLabel + " medium conflicts with mask");
            }
            Integer prior = labelToComponent.putIfAbsent(soleLabel, component);
            if (prior != null && prior != component) {
                throw fail(RULE_AMBIGUOUS_BOUNDARY,
                        "zone label " + soleLabel + " spans multiple regions");
            }
            kind = mapped;
        } else {
            kind = seed.medium() == Medium.LAND
                    ? MacroRegionKind.UNLABELED_LAND
                    : MacroRegionKind.UNLABELED_WATER;
        }
        return new RegionDraft(
                kind,
                seed.medium(),
                cellCount,
                minX,
                minZ,
                maxX,
                maxZ,
                (int) (sumX / cellCount),
                (int) (sumZ / cellCount),
                minNeck == Integer.MAX_VALUE ? 1 : minNeck,
                touchesBorder,
                soleLabel);
    }

    private static int localWidth(
            int width,
            int length,
            byte[] mask,
            int[] zones,
            int x,
            int z,
            byte medium,
            int zone
    ) {
        int left = 0;
        for (int cx = x; cx >= 0; cx--) {
            int index = z * width + cx;
            if (mask[index] != medium || zoneAt(zones, index) != zone) {
                break;
            }
            left++;
        }
        int right = 0;
        for (int cx = x + 1; cx < width; cx++) {
            int index = z * width + cx;
            if (mask[index] != medium || zoneAt(zones, index) != zone) {
                break;
            }
            right++;
        }
        int horizontal = left + right;
        int up = 0;
        for (int cz = z; cz >= 0; cz--) {
            int index = cz * width + x;
            if (mask[index] != medium || zoneAt(zones, index) != zone) {
                break;
            }
            up++;
        }
        int down = 0;
        for (int cz = z + 1; cz < length; cz++) {
            int index = cz * width + x;
            if (mask[index] != medium || zoneAt(zones, index) != zone) {
                break;
            }
            down++;
        }
        int vertical = up + down;
        return Math.min(horizontal, vertical);
    }

    private static int zoneAt(int[] zones, int index) {
        return zones == null ? 0 : zones[index];
    }

    private static List<Adjacency> buildAdjacencies(
            int width,
            int length,
            int[] componentOf,
            List<RegionDraft> drafts
    ) {
        Map<PairKey, int[]> metrics = new LinkedHashMap<>();
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                int component = componentOf[index];
                if (x + 1 < width) {
                    int other = componentOf[index + 1];
                    if (other != component) {
                        accumulate(metrics, PairKey.of(component, other), drafts);
                    }
                }
                if (z + 1 < length) {
                    int other = componentOf[index + width];
                    if (other != component) {
                        accumulate(metrics, PairKey.of(component, other), drafts);
                    }
                }
            }
        }
        List<Adjacency> adjacencies = new ArrayList<>();
        List<Map.Entry<PairKey, int[]>> ordered = new ArrayList<>(metrics.entrySet());
        ordered.sort(Comparator
                .comparing((Map.Entry<PairKey, int[]> entry) -> regionId(entry.getKey().a()))
                .thenComparing(entry -> regionId(entry.getKey().b())));
        int edgeOrdinal = 0;
        for (Map.Entry<PairKey, int[]> entry : ordered) {
            PairKey key = entry.getKey();
            RegionDraft first = drafts.get(key.a());
            RegionDraft second = drafts.get(key.b());
            if (first.medium() == second.medium()) {
                continue;
            }
            String firstId = regionId(key.a());
            String secondId = regionId(key.b());
            String left = firstId.compareTo(secondId) < 0 ? firstId : secondId;
            String right = firstId.compareTo(secondId) < 0 ? secondId : firstId;
            int[] values = entry.getValue();
            adjacencies.add(new Adjacency(
                    "edge." + edgeOrdinal++,
                    left,
                    right,
                    values[0],
                    Math.max(1, values[1])));
        }
        if (adjacencies.size() > MacroLandWaterTopologyPlanV2.MAXIMUM_ADJACENCIES) {
            throw fail(RULE_GRAPH_BUDGET, "adjacency count exceeds budget");
        }
        return adjacencies;
    }

    private static void accumulate(Map<PairKey, int[]> metrics, PairKey key, List<RegionDraft> drafts) {
        int[] values = metrics.computeIfAbsent(key, ignored -> new int[] {0, Integer.MAX_VALUE});
        values[0]++;
        int neck = Math.min(drafts.get(key.a()).minNeckWidthBlocks(), drafts.get(key.b()).minNeckWidthBlocks());
        values[1] = Math.min(values[1], neck);
    }

    private static List<Containment> buildContainments(
            int width,
            int length,
            int[] componentOf,
            List<RegionDraft> drafts
    ) {
        List<Containment> containments = new ArrayList<>();
        for (int child = 0; child < drafts.size(); child++) {
            RegionDraft draft = drafts.get(child);
            if (draft.medium() != Medium.WATER || draft.touchesBorder()) {
                continue;
            }
            Set<Integer> landNeighbors = new HashSet<>();
            boolean waterNeighbor = false;
            for (int z = draft.minZ(); z <= draft.maxZ(); z++) {
                for (int x = draft.minX(); x <= draft.maxX(); x++) {
                    int index = z * width + x;
                    if (componentOf[index] != child) {
                        continue;
                    }
                    for (int dir = 0; dir < 4; dir++) {
                        int nx = x + DX[dir];
                        int nz = z + DZ[dir];
                        if (nx < 0 || nz < 0 || nx >= width || nz >= length) {
                            continue;
                        }
                        int neighbor = componentOf[nz * width + nx];
                        if (neighbor == child) {
                            continue;
                        }
                        if (drafts.get(neighbor).medium() == Medium.WATER) {
                            waterNeighbor = true;
                        } else {
                            landNeighbors.add(neighbor);
                        }
                    }
                }
            }
            if (waterNeighbor || landNeighbors.size() != 1) {
                continue;
            }
            int parent = landNeighbors.iterator().next();
            containments.add(new Containment(regionId(parent), regionId(child)));
        }
        if (containments.size() > MacroLandWaterTopologyPlanV2.MAXIMUM_CONTAINMENTS) {
            throw fail(RULE_GRAPH_BUDGET, "containment count exceeds budget");
        }
        return containments;
    }

    private static Map<Integer, Set<Integer>> buildSameMediumNeighbors(
            int width,
            int length,
            int[] componentOf,
            List<RegionDraft> drafts
    ) {
        Map<Integer, Set<Integer>> neighbors = new HashMap<>();
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                int component = componentOf[index];
                if (x + 1 < width) {
                    int other = componentOf[index + 1];
                    if (other != component && drafts.get(component).medium() == drafts.get(other).medium()) {
                        neighbors.computeIfAbsent(component, ignored -> new HashSet<>()).add(other);
                        neighbors.computeIfAbsent(other, ignored -> new HashSet<>()).add(component);
                    }
                }
                if (z + 1 < length) {
                    int other = componentOf[index + width];
                    if (other != component && drafts.get(component).medium() == drafts.get(other).medium()) {
                        neighbors.computeIfAbsent(component, ignored -> new HashSet<>()).add(other);
                        neighbors.computeIfAbsent(other, ignored -> new HashSet<>()).add(component);
                    }
                }
            }
        }
        return neighbors;
    }

    private static void applySemanticValidation(
            List<RegionDraft> drafts,
            Map<Integer, Set<Integer>> sameMediumNeighbors,
            List<Containment> containments,
            ManualTopologyInputV2 input
    ) {
        Map<String, Integer> idToIndex = new HashMap<>();
        for (int index = 0; index < drafts.size(); index++) {
            idToIndex.put(regionId(index), index);
        }
        Set<String> containedChildren = new HashSet<>();
        for (Containment containment : containments) {
            containedChildren.add(containment.childRegionId());
        }

        for (int index = 0; index < drafts.size(); index++) {
            RegionDraft draft = drafts.get(index);
            String id = regionId(index);
            if (draft.kind() == MacroRegionKind.ISTHMUS) {
                if (draft.minNeckWidthBlocks() < input.minimumIsthmusWidthBlocks()) {
                    throw fail(RULE_COLLAPSED_ISTHMUS,
                            "isthmus " + id + " neck width " + draft.minNeckWidthBlocks()
                                    + " below minimum " + input.minimumIsthmusWidthBlocks());
                }
                Set<Integer> landPeers = sameMediumNeighbors.getOrDefault(index, Set.of());
                if (landPeers.size() < 2) {
                    throw fail(RULE_COLLAPSED_ISTHMUS,
                            "isthmus " + id + " does not bridge two land regions");
                }
            }
            if (draft.kind() == MacroRegionKind.STRAIT) {
                if (draft.minNeckWidthBlocks() < input.minimumStraitWidthBlocks()) {
                    throw fail(RULE_DISCONNECTED_STRAIT,
                            "strait " + id + " width " + draft.minNeckWidthBlocks()
                                    + " below minimum " + input.minimumStraitWidthBlocks());
                }
                Set<Integer> waterPeers = sameMediumNeighbors.getOrDefault(index, Set.of());
                if (waterPeers.size() < 2) {
                    throw fail(RULE_DISCONNECTED_STRAIT,
                            "strait " + id + " does not connect two water bodies");
                }
            }
            if (draft.kind() == MacroRegionKind.ENCLOSED_BASIN) {
                if (!containedChildren.contains(id)) {
                    throw fail(RULE_NESTED_BASIN,
                            "enclosed basin " + id + " is not land-contained");
                }
            }
        }

        Map<String, String> childToParent = new HashMap<>();
        for (Containment containment : containments) {
            if (childToParent.put(containment.childRegionId(), containment.parentRegionId()) != null) {
                throw fail(RULE_NESTED_BASIN, "water region has multiple land parents");
            }
        }
        for (int index = 0; index < drafts.size(); index++) {
            if (drafts.get(index).kind() != MacroRegionKind.ENCLOSED_BASIN) {
                continue;
            }
            for (int peer : sameMediumNeighbors.getOrDefault(index, Set.of())) {
                if (drafts.get(peer).kind() == MacroRegionKind.ENCLOSED_BASIN) {
                    throw fail(RULE_NESTED_BASIN,
                            "nested enclosed basins " + regionId(index) + " / " + regionId(peer));
                }
            }
        }
        for (Containment containment : containments) {
            RegionDraft parentDraft = drafts.get(idToIndex.get(containment.parentRegionId()));
            if (parentDraft.kind() == MacroRegionKind.ENCLOSED_BASIN) {
                throw fail(RULE_NESTED_BASIN, "land parent cannot be enclosed basin");
            }
        }
    }

    private static String regionId(int component) {
        return "region." + component;
    }

    private static String geometryChecksum(byte[] mask, int[] zones) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("macro-land-water-geometry-v1".getBytes(StandardCharsets.UTF_8));
            digest.update(mask);
            if (zones != null) {
                for (int label : zones) {
                    digest.update((byte) (label >>> 24));
                    digest.update((byte) (label >>> 16));
                    digest.update((byte) (label >>> 8));
                    digest.update((byte) label);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static FoundationSliceException fail(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }

    public record ManualTopologyInputV2(
            String topologyId,
            int width,
            int length,
            byte[] landWaterMask,
            int[] zoneLabels,
            Map<Integer, MacroRegionKind> labelKinds,
            int minimumIsthmusWidthBlocks,
            int minimumStraitWidthBlocks,
            int supportRadiusXZ
    ) {
        public ManualTopologyInputV2 {
            topologyId = Objects.requireNonNull(topologyId, "topologyId");
            landWaterMask = Objects.requireNonNull(landWaterMask, "landWaterMask").clone();
            zoneLabels = zoneLabels == null ? null : zoneLabels.clone();
            labelKinds = Map.copyOf(Objects.requireNonNull(labelKinds, "labelKinds"));
        }
    }

    private record PairKey(int a, int b) {
        static PairKey of(int left, int right) {
            return left < right ? new PairKey(left, right) : new PairKey(right, left);
        }
    }

    private record ComponentSeed(int seedX, int seedZ, Medium medium, int zoneLabel) {
    }

    private record RegionDraft(
            MacroRegionKind kind,
            Medium medium,
            int cellCount,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            int centroidX,
            int centroidZ,
            int minNeckWidthBlocks,
            boolean touchesBorder,
            Integer zoneLabel
    ) {
    }
}
