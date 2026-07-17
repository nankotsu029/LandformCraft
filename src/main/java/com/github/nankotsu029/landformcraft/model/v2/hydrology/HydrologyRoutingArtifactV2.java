package com.github.nankotsu029.landformcraft.model.v2.hydrology;

import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict index for one frozen V2-3-02 global basin/routing result. */
public record HydrologyRoutingArtifactV2(
        int artifactVersion,
        String solverVersion,
        String directionEncodingVersion,
        int width,
        int length,
        String sourceHydrologyPlanChecksum,
        String sourceSurfaceChecksum,
        String fixedPriorChecksum,
        List<Outlet> outlets,
        List<BasinSummary> basins,
        List<FieldArtifactDescriptorV2> fields,
        ResourceUsage resources,
        String graphChecksum,
        String routingChecksum,
        String canonicalChecksum
) {
    public static final int VERSION = 1;
    public static final String SOLVER_VERSION = "hydrology-priority-flood-v1";
    public static final String DIRECTION_ENCODING_VERSION = "hydrology-d8-terminal-v1";
    public static final String BUDGET_VERSION = "hydrology-routing-budget-v1";
    public static final String FLOW_DIRECTION_FIELD_ID = "hydrology.flow-direction";
    public static final String FLOW_ACCUMULATION_FIELD_ID = "hydrology.flow-accumulation";
    public static final String FLOW_DIRECTION_PATH = "fields/flow-direction.lfgrid";
    public static final String FLOW_ACCUMULATION_PATH = "fields/flow-accumulation.lfgrid";
    public static final String PROVENANCE_SOURCE_ID = "derived-source:hydrology-routing";
    public static final String PROVENANCE_DECODER_ID = "hydrology-priority-flood";
    public static final String PROVENANCE_TRANSFORM_ID = "hydrology-global-routing-v1";

    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");
    private static final byte[] GRAPH_DOMAIN = "HYDROLOGY_ROUTING_GRAPH_V1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ROUTING_DOMAIN = "HYDROLOGY_ROUTING_RESULT_V1".getBytes(StandardCharsets.US_ASCII);

    public HydrologyRoutingArtifactV2 {
        if (artifactVersion != VERSION) {
            throw new IllegalArgumentException("hydrology routing artifactVersion must be 1");
        }
        solverVersion = exact(solverVersion, SOLVER_VERSION, "solverVersion");
        directionEncodingVersion = exact(
                directionEncodingVersion, DIRECTION_ENCODING_VERSION, "directionEncodingVersion");
        if (width < 1 || width > 1_000 || length < 1 || length > 1_000) {
            throw new IllegalArgumentException("hydrology routing dimensions must be within 1..1000");
        }
        sourceHydrologyPlanChecksum = checksum(sourceHydrologyPlanChecksum, "sourceHydrologyPlanChecksum");
        sourceSurfaceChecksum = checksum(sourceSurfaceChecksum, "sourceSurfaceChecksum");
        fixedPriorChecksum = checksum(fixedPriorChecksum, "fixedPriorChecksum");
        outlets = sorted(outlets, "outlets", 256, Comparator
                .comparingInt(Outlet::z)
                .thenComparingInt(Outlet::x)
                .thenComparing(Outlet::outletId));
        basins = sorted(basins, "basins", 256, Comparator.comparingInt(BasinSummary::numericId));
        fields = sorted(fields, "fields", 2,
                Comparator.comparing(field -> field.definition().fieldId()));
        Objects.requireNonNull(resources, "resources");
        graphChecksum = checksum(graphChecksum, "graphChecksum");
        routingChecksum = checksum(routingChecksum, "routingChecksum");
        canonicalChecksum = checksum(canonicalChecksum, "canonicalChecksum");
        validate(outlets, basins, fields, resources, width, length, sourceSurfaceChecksum);
        String expectedGraph = computeGraphChecksum(
                width, length, sourceHydrologyPlanChecksum, sourceSurfaceChecksum,
                fixedPriorChecksum, outlets, basins);
        if (!expectedGraph.equals(graphChecksum)) {
            throw new IllegalArgumentException("hydrology routing graph checksum mismatch");
        }
        String expectedRouting = computeRoutingChecksum(graphChecksum, fields);
        if (!expectedRouting.equals(routingChecksum)) {
            throw new IllegalArgumentException("hydrology routing result checksum mismatch");
        }
    }

    public HydrologyRoutingArtifactV2 withCanonicalChecksum(String checksum) {
        return new HydrologyRoutingArtifactV2(
                artifactVersion, solverVersion, directionEncodingVersion, width, length,
                sourceHydrologyPlanChecksum, sourceSurfaceChecksum, fixedPriorChecksum,
                outlets, basins, fields, resources, graphChecksum, routingChecksum, checksum);
    }

    public static String basinId(int numericId) {
        if (numericId < 1 || numericId > 256) {
            throw new IllegalArgumentException("basin numeric ID is outside 1..256");
        }
        return "basin-" + String.format(Locale.ROOT, "%06d", numericId);
    }

    public static String computeGraphChecksum(
            int width,
            int length,
            String sourceHydrologyPlanChecksum,
            String sourceSurfaceChecksum,
            String fixedPriorChecksum,
            List<Outlet> outlets,
            List<BasinSummary> basins
    ) {
        MessageDigest digest = sha256();
        digest.update(GRAPH_DOMAIN);
        updateString(digest, SOLVER_VERSION);
        updateString(digest, DIRECTION_ENCODING_VERSION);
        updateInt(digest, width);
        updateInt(digest, length);
        updateString(digest, sourceHydrologyPlanChecksum);
        updateString(digest, sourceSurfaceChecksum);
        updateString(digest, fixedPriorChecksum);
        List<Outlet> orderedOutlets = outlets.stream().sorted(Comparator
                .comparingInt(Outlet::z)
                .thenComparingInt(Outlet::x)
                .thenComparing(Outlet::outletId)).toList();
        updateInt(digest, orderedOutlets.size());
        for (Outlet outlet : orderedOutlets) {
            updateString(digest, outlet.outletId());
            updateInt(digest, outlet.x());
            updateInt(digest, outlet.z());
            updateString(digest, outlet.kind().name());
        }
        List<BasinSummary> orderedBasins = basins.stream()
                .sorted(Comparator.comparingInt(BasinSummary::numericId)).toList();
        updateInt(digest, orderedBasins.size());
        for (BasinSummary basin : orderedBasins) {
            updateString(digest, basin.basinId());
            updateInt(digest, basin.numericId());
            updateString(digest, basin.outletId());
            updateInt(digest, basin.outletCellId());
            updateInt(digest, basin.outletElevationMillionths());
            updateLong(digest, basin.areaCells());
            updateLong(digest, basin.outletAccumulation());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String computeRoutingChecksum(
            String graphChecksum,
            List<FieldArtifactDescriptorV2> fields
    ) {
        MessageDigest digest = sha256();
        digest.update(ROUTING_DOMAIN);
        updateString(digest, graphChecksum);
        List<FieldArtifactDescriptorV2> ordered = fields.stream()
                .sorted(Comparator.comparing(field -> field.definition().fieldId())).toList();
        updateInt(digest, ordered.size());
        for (FieldArtifactDescriptorV2 field : ordered) {
            updateString(digest, field.definition().fieldId());
            updateString(digest, field.definition().semantic().name());
            updateString(digest, field.semanticChecksum());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String computeRoutingChecksum(
            String graphChecksum,
            String flowDirectionSemanticChecksum,
            String flowAccumulationSemanticChecksum
    ) {
        MessageDigest digest = sha256();
        digest.update(ROUTING_DOMAIN);
        updateString(digest, graphChecksum);
        updateInt(digest, 2);
        updateString(digest, FLOW_ACCUMULATION_FIELD_ID);
        updateString(digest, FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_ACCUMULATION.name());
        updateString(digest, flowAccumulationSemanticChecksum);
        updateString(digest, FLOW_DIRECTION_FIELD_ID);
        updateString(digest, FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_DIRECTION.name());
        updateString(digest, flowDirectionSemanticChecksum);
        return HexFormat.of().formatHex(digest.digest());
    }

    public enum OutletKind { BOUNDARY, HARD }

    public record Outlet(String outletId, int x, int z, OutletKind kind) {
        public Outlet {
            outletId = slug(outletId, "outletId");
            if (x < 0 || x >= 1_000 || z < 0 || z >= 1_000) {
                throw new IllegalArgumentException("outlet coordinate is outside trusted bounds");
            }
            Objects.requireNonNull(kind, "kind");
        }

        public int cellId(int width) {
            return Math.addExact(Math.multiplyExact(z, width), x);
        }
    }

    public record BasinSummary(
            String basinId,
            int numericId,
            String outletId,
            int outletCellId,
            int outletElevationMillionths,
            long areaCells,
            long outletAccumulation
    ) {
        public BasinSummary {
            basinId = slug(basinId, "basinId");
            if (numericId < 1 || numericId > 256) {
                throw new IllegalArgumentException("basin numericId is outside 1..256");
            }
            outletId = slug(outletId, "outletId");
            if (outletCellId < 0 || outletCellId >= 1_000_000) {
                throw new IllegalArgumentException("basin outletCellId is outside trusted bounds");
            }
            if (outletElevationMillionths < -512_000_000 || outletElevationMillionths > 1_024_000_000) {
                throw new IllegalArgumentException("basin outlet elevation is outside trusted bounds");
            }
            if (areaCells < 1 || areaCells > 1_000_000L || outletAccumulation != areaCells) {
                throw new IllegalArgumentException("basin area and outlet accumulation are inconsistent");
            }
        }
    }

    public record ResourceUsage(
            String budgetVersion,
            long globalCellCount,
            long routableCellCount,
            long cpuWorkUnits,
            long maximumCpuWorkUnits,
            long peakWorkingBytes,
            long maximumWorkingBytes,
            long retainedResultBytes,
            long maximumRetainedResultBytes,
            long fieldArtifactBytes,
            long maximumFieldArtifactBytes,
            int maximumHeapCells
    ) {
        public ResourceUsage {
            budgetVersion = exact(budgetVersion, BUDGET_VERSION, "budgetVersion");
            if (globalCellCount < 1 || globalCellCount > 1_000_000L
                    || routableCellCount < 1 || routableCellCount > globalCellCount
                    || cpuWorkUnits < 1 || cpuWorkUnits > maximumCpuWorkUnits
                    || maximumCpuWorkUnits < 1 || maximumCpuWorkUnits > 100_000_000L
                    || peakWorkingBytes < 1 || peakWorkingBytes > maximumWorkingBytes
                    || maximumWorkingBytes < 1 || maximumWorkingBytes > 512L * 1024L * 1024L
                    || retainedResultBytes < 1 || retainedResultBytes > maximumRetainedResultBytes
                    || maximumRetainedResultBytes < 1
                    || maximumRetainedResultBytes > 64L * 1024L * 1024L
                    || fieldArtifactBytes < 1 || fieldArtifactBytes > maximumFieldArtifactBytes
                    || maximumFieldArtifactBytes < 1 || maximumFieldArtifactBytes > 64L * 1024L * 1024L
                    || maximumHeapCells < 1 || maximumHeapCells > globalCellCount) {
                throw new IllegalArgumentException("hydrology routing resource usage exceeds its trusted budget");
            }
        }
    }

    private static void validate(
            List<Outlet> outlets,
            List<BasinSummary> basins,
            List<FieldArtifactDescriptorV2> fields,
            ResourceUsage resources,
            int width,
            int length,
            String sourceSurfaceChecksum
    ) {
        if (outlets.isEmpty() || basins.size() != outlets.size() || fields.size() != 2) {
            throw new IllegalArgumentException("routing artifact requires outlets, matching basins, and two fields");
        }
        Set<String> outletIds = new HashSet<>();
        Set<Integer> outletCells = new HashSet<>();
        for (Outlet outlet : outlets) {
            if (outlet.x() >= width || outlet.z() >= length) {
                throw new IllegalArgumentException("outlet lies outside routing dimensions");
            }
            int cellId = outlet.cellId(width);
            if (!outletIds.add(outlet.outletId()) || !outletCells.add(cellId)) {
                throw new IllegalArgumentException("duplicate routing outlet ID or cell");
            }
            if (outlet.kind() == OutletKind.BOUNDARY
                    && outlet.x() != 0 && outlet.z() != 0
                    && outlet.x() != width - 1 && outlet.z() != length - 1) {
                throw new IllegalArgumentException("BOUNDARY outlet must lie on the global boundary");
            }
        }
        long totalArea = 0L;
        for (int index = 0; index < basins.size(); index++) {
            BasinSummary basin = basins.get(index);
            int expectedNumericId = index + 1;
            Outlet outlet = outlets.get(index);
            if (basin.numericId() != expectedNumericId
                    || !basin.basinId().equals(basinId(expectedNumericId))
                    || !basin.outletId().equals(outlet.outletId())
                    || basin.outletCellId() != outlet.cellId(width)) {
                throw new IllegalArgumentException("basin summary does not match canonical outlet order");
            }
            totalArea = Math.addExact(totalArea, basin.areaCells());
        }
        if (totalArea != resources.routableCellCount()
                || resources.globalCellCount() != Math.multiplyExact((long) width, length)) {
            throw new IllegalArgumentException("basin areas do not cover the routable surface");
        }

        Map<FieldArtifactDescriptorV2.FieldSemantic, FieldArtifactDescriptorV2> bySemantic = new HashMap<>();
        Set<String> paths = new HashSet<>();
        for (FieldArtifactDescriptorV2 field : fields) {
            if (bySemantic.putIfAbsent(field.definition().semantic(), field) != null
                    || !paths.add(field.relativePath())) {
                throw new IllegalArgumentException("duplicate hydrology routing field semantic or path");
            }
            var definition = field.definition();
            var provenance = field.provenance();
            if (definition.width() != width || definition.length() != length
                    || definition.coordinateSpace() != FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ
                    || definition.sampling() != FieldArtifactDescriptorV2.Sampling.NEAREST
                    || provenance.sourceKind() != FieldArtifactDescriptorV2.SourceKind.DERIVED
                    || !provenance.sourceId().equals(PROVENANCE_SOURCE_ID)
                    || !provenance.sourceChecksum().equals(sourceSurfaceChecksum)
                    || !provenance.decoderId().equals(PROVENANCE_DECODER_ID)
                    || !provenance.decoderVersion().equals("1")
                    || !provenance.transformId().equals(PROVENANCE_TRANSFORM_ID)) {
                throw new IllegalArgumentException("hydrology routing field descriptor is not canonical");
            }
        }
        requireField(bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_DIRECTION),
                FLOW_DIRECTION_FIELD_ID, FLOW_DIRECTION_PATH,
                FieldArtifactDescriptorV2.FieldValueType.U8, 255);
        requireField(bySemantic.get(FieldArtifactDescriptorV2.FieldSemantic.HYDROLOGY_FLOW_ACCUMULATION),
                FLOW_ACCUMULATION_FIELD_ID, FLOW_ACCUMULATION_PATH,
                FieldArtifactDescriptorV2.FieldValueType.I32, 0);
    }

    private static void requireField(
            FieldArtifactDescriptorV2 field,
            String fieldId,
            String path,
            FieldArtifactDescriptorV2.FieldValueType valueType,
            int noData
    ) {
        if (field == null
                || !field.definition().fieldId().equals(fieldId)
                || !field.relativePath().equals(path)
                || field.definition().valueType() != valueType
                || field.definition().scaleMillionths() != 1L
                || field.definition().offsetMillionths() != 0L
                || !field.definition().hasNoData()
                || field.definition().noDataRaw() != noData) {
            throw new IllegalArgumentException("hydrology routing field definition is not canonical");
        }
    }

    private static String exact(String value, String expected, String field) {
        if (!expected.equals(value)) throw new IllegalArgumentException("unknown hydrology routing " + field);
        return value;
    }

    private static String slug(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!SLUG.matcher(value).matches()) throw new IllegalArgumentException(field + " must be a slug");
        return value;
    }

    private static String checksum(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!CHECKSUM.matcher(value).matches()) throw new IllegalArgumentException(field + " must be SHA-256");
        return value;
    }

    private static <T> List<T> sorted(List<T> values, String field, int maximum, Comparator<T> comparator) {
        Objects.requireNonNull(values, field);
        if (values.size() > maximum || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " exceeds its trusted limit");
        }
        return values.stream().sorted(comparator).toList();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateLong(MessageDigest digest, long value) {
        updateInt(digest, (int) (value >>> 32));
        updateInt(digest, (int) value);
    }
}
