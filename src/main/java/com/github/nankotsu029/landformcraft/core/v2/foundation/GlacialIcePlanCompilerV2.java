package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.GlacialIcePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfAabbV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Compiles V2-10-01 glacial-ice plans for VALLEY_GLACIER / ICE_CAP / ICE_SHEET. */
public final class GlacialIcePlanCompilerV2 {
    private static final String ZERO = "0".repeat(64);
    private static final long FIXED = TerrainIntentV2.FIXED_SCALE;

    private final LandformV2DataCodec codec = new LandformV2DataCodec();

    public HostBinding resolveHostBinding(TerrainIntentV2.Feature ice, TerrainIntentV2 intent) {
        Objects.requireNonNull(ice, "ice");
        Objects.requireNonNull(intent, "intent");
        GlacialIcePlanV2.IceKind iceKind = GlacialIcePlanV2.iceKindOf(ice.kind());
        String endpoint = "feature:" + ice.id();

        List<TerrainIntentV2.Relation> beds = intent.relations().stream()
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.SUPPORTED_BY
                        || relation.kind() == TerrainIntentV2.RelationKind.WITHIN)
                .filter(relation -> endpoint.equals(relation.from()))
                .filter(relation -> isAllowedBedHost(intent, relation.to(), iceKind))
                .toList();
        if (beds.isEmpty()) {
            throw failure("v2.glacial-ice-missing-bed",
                    "glacial ice requires exactly one HARD SUPPORTED_BY/WITHIN bed host");
        }
        if (beds.size() > 1) {
            throw failure("v2.glacial-ice-ambiguous-bed",
                    "glacial ice has multiple HARD bed hosts");
        }
        TerrainIntentV2.Relation bed = beds.getFirst();
        String bedHostId = bed.to().substring("feature:".length());
        TerrainIntentV2.FeatureKind hostKind = featureKind(intent, bedHostId);
        GlacialIcePlanV2.BedHostKind bedHostKind = switch (hostKind) {
            case VALLEY -> GlacialIcePlanV2.BedHostKind.VALLEY;
            case MOUNTAIN_RANGE -> GlacialIcePlanV2.BedHostKind.MOUNTAIN_RANGE;
            case PLAIN -> GlacialIcePlanV2.BedHostKind.PLAIN;
            default -> throw failure("v2.glacial-ice-unsupported-bed",
                    "bed host kind is unsupported for glacial ice");
        };
        return new HostBinding(bed.id(), bedHostId, bedHostKind, iceKind);
    }

    public GlacialIcePlanV2 compile(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            String geometryChecksum,
            String bedHostGeometryChecksum
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(geometryChecksum, "geometryChecksum");
        Objects.requireNonNull(bedHostGeometryChecksum, "bedHostGeometryChecksum");

        GlacialIcePlanV2.IceKind iceKind = GlacialIcePlanV2.iceKindOf(feature.kind());
        if (!(feature.parameters() instanceof TerrainIntentV2.GlacialIceParameters params)) {
            throw failure("v2.glacial-ice-params", "glacial ice parameters missing");
        }
        requireColdClimate(intent, params.climatePreset());

        HostBinding host = resolveHostBinding(feature, intent);
        FlowAxis axis = flowAxis(feature, iceKind, params.flowAzimuthDegrees(), bounds);
        int thickness = mid(params.thicknessBlocks());
        int halfWidth = mid(params.halfWidthBlocks());

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
            throw failure("v2.glacial-ice-meltwater-missing",
                    "meltwater handoff hint requires HARD DRAINS_TO/EMPTIES_INTO");
        }

        long half = Math.multiplyExact((long) halfWidth, FIXED);
        long thick = Math.multiplyExact((long) thickness, FIXED);
        long minX = Math.min(axis.headX(), axis.terminusX()) - half;
        long maxX = Math.max(axis.headX(), axis.terminusX()) + half;
        long minZ = Math.min(axis.headZ(), axis.terminusZ()) - half;
        long maxZ = Math.max(axis.headZ(), axis.terminusZ()) + half;
        long bedY = Math.multiplyExact((long) bounds.minY(), FIXED);
        VolumeSdfAabbV2 aabb = new VolumeSdfAabbV2(minX, bedY, minZ, maxX, bedY + thick, maxZ);
        if (aabb.extentXBlocks() > 512 || aabb.extentZBlocks() > 512 || aabb.extentYBlocks() > 128) {
            throw failure("v2.glacial-ice-budget", "glacial ice AABB exceeds sparse budget");
        }

        String climateBinding = climateBindingChecksum(params.climatePreset(), geometryChecksum);
        String primitiveId = "prim.ice." + feature.id();
        String opId = "op.add.ice." + feature.id();
        String sdfChecksum = digest("sdf|" + feature.id() + "|" + geometryChecksum + "|" + thickness);
        String csgChecksum = digest("csg|" + feature.id() + "|" + geometryChecksum + "|" + halfWidth);

        long work = Math.multiplyExact(
                Math.multiplyExact(Math.max(1L, aabb.extentXBlocks()), Math.max(1L, aabb.extentZBlocks())),
                Math.max(1L, aabb.extentYBlocks()));
        if (work > GlacialIcePlanV2.MAXIMUM_WORK_UNITS) {
            throw failure("v2.glacial-ice-budget", "glacial ice work units exceed budget");
        }

        return new GlacialIcePlanV2(
                GlacialIcePlanV2.VERSION,
                feature.id(),
                iceKind,
                host.bedHostFeatureId(),
                host.bedHostKind(),
                host.bedHostRelationId(),
                bedHostGeometryChecksum,
                params.climatePreset(),
                climateBinding,
                GlacialIcePlanV2.DEFAULT_SNOW_PROFILE_ID,
                params.flowAzimuthDegrees(),
                thickness,
                halfWidth,
                axis.headX(),
                axis.headZ(),
                axis.terminusX(),
                axis.terminusZ(),
                meltwaterId,
                meltwaterChecksum,
                aabb,
                List.of(new GlacialIcePlanV2.OrderedVolumeOp(
                        opId, 0, GlacialIcePlanV2.OrderedVolumeOp.ADD_SOLID, primitiveId)),
                new GlacialIcePlanV2.ArtifactBinding(
                        1, sdfChecksum, GlacialIcePlanV2.ArtifactBinding.SDF_CONTRACT),
                new GlacialIcePlanV2.ArtifactBinding(
                        1, csgChecksum, GlacialIcePlanV2.ArtifactBinding.CSG_CONTRACT),
                GlacialIcePlanV2.SURFACE_OWNERSHIP_FIELD_ID,
                GlacialIcePlanV2.VOLUME_OWNERSHIP_FIELD_ID,
                GlacialIcePlanV2.THICKNESS_FIELD_ID,
                GlacialIcePlanV2.FLOW_DIRECTION_FIELD_ID,
                GlacialIcePlanV2.BED_CONTACT_FIELD_ID,
                GlacialIcePlanV2.MELTWATER_MASK_FIELD_ID,
                Math.min(64, Math.max(4, halfWidth / 2)),
                work,
                geometryChecksum,
                ZERO);
    }

    public void requireColdClimate(TerrainIntentV2 intent, String parameterPreset) {
        String env = intent.environment().climatePreset();
        if (!GlacialIcePlanV2.COLD_CLIMATE_PRESETS.contains(parameterPreset)) {
            throw failure("v2.glacial-ice-warm-climate", "parameter climatePreset is not cold");
        }
        if (env != null && !env.isBlank() && !GlacialIcePlanV2.COLD_CLIMATE_PRESETS.contains(env)) {
            throw failure("v2.glacial-ice-warm-climate",
                    "environment climatePreset is warm/unsupported for glacial ice");
        }
        if (env != null && !env.isBlank() && !env.equals(parameterPreset)) {
            throw failure("v2.glacial-ice-climate-mismatch",
                    "parameter and environment climatePreset must match");
        }
    }

    private FlowAxis flowAxis(
            TerrainIntentV2.Feature feature,
            GlacialIcePlanV2.IceKind iceKind,
            int azimuthDegrees,
            WorldBlueprintV2.Bounds bounds
    ) {
        long width = Math.multiplyExact((long) bounds.width(), FIXED);
        long length = Math.multiplyExact((long) bounds.length(), FIXED);
        return switch (iceKind) {
            case VALLEY_GLACIER -> {
                if (!(feature.geometry() instanceof TerrainIntentV2.SplineGeometry spline)
                        || spline.points().size() < 2) {
                    throw failure("v2.glacial-ice-geometry", "VALLEY_GLACIER requires a SPLINE");
                }
                TerrainIntentV2.Point2 head = spline.points().getFirst();
                TerrainIntentV2.Point2 terminus = spline.points().getLast();
                yield new FlowAxis(
                        scale(head.xMillionths(), width),
                        scale(head.zMillionths(), length),
                        scale(terminus.xMillionths(), width),
                        scale(terminus.zMillionths(), length));
            }
            case ICE_CAP -> {
                if (!(feature.geometry() instanceof TerrainIntentV2.PointGeometry point)) {
                    throw failure("v2.glacial-ice-geometry", "ICE_CAP requires a POINT");
                }
                long cx = scale(point.point().xMillionths(), width);
                long cz = scale(point.point().zMillionths(), length);
                long dx = Math.round(Math.sin(Math.toRadians(azimuthDegrees)) * 8.0 * FIXED);
                long dz = Math.round(Math.cos(Math.toRadians(azimuthDegrees)) * 8.0 * FIXED);
                yield new FlowAxis(cx, cz, Math.addExact(cx, dx), Math.addExact(cz, dz));
            }
            case ICE_SHEET -> {
                if (!(feature.geometry() instanceof TerrainIntentV2.PolygonGeometry polygon)
                        || polygon.rings().isEmpty() || polygon.rings().getFirst().size() < 3) {
                    throw failure("v2.glacial-ice-geometry", "ICE_SHEET requires a POLYGON");
                }
                List<TerrainIntentV2.Point2> ring = polygon.rings().getFirst();
                long minX = Long.MAX_VALUE;
                long maxX = Long.MIN_VALUE;
                long minZ = Long.MAX_VALUE;
                long maxZ = Long.MIN_VALUE;
                for (TerrainIntentV2.Point2 p : ring) {
                    long x = scale(p.xMillionths(), width);
                    long z = scale(p.zMillionths(), length);
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                }
                long cx = (minX + maxX) / 2L;
                long cz = (minZ + maxZ) / 2L;
                long dx = Math.round(Math.sin(Math.toRadians(azimuthDegrees))
                        * Math.max(4.0, (maxX - minX) / (2.0 * FIXED)) * FIXED);
                long dz = Math.round(Math.cos(Math.toRadians(azimuthDegrees))
                        * Math.max(4.0, (maxZ - minZ) / (2.0 * FIXED)) * FIXED);
                yield new FlowAxis(cx, cz, Math.addExact(cx, dx), Math.addExact(cz, dz));
            }
        };
    }

    private static boolean isAllowedBedHost(
            TerrainIntentV2 intent, String to, GlacialIcePlanV2.IceKind iceKind
    ) {
        if (!to.startsWith("feature:")) {
            return false;
        }
        TerrainIntentV2.FeatureKind kind = featureKind(intent, to.substring("feature:".length()));
        return switch (iceKind) {
            case VALLEY_GLACIER -> kind == TerrainIntentV2.FeatureKind.VALLEY;
            case ICE_CAP -> kind == TerrainIntentV2.FeatureKind.MOUNTAIN_RANGE
                    || kind == TerrainIntentV2.FeatureKind.PLAIN;
            case ICE_SHEET -> kind == TerrainIntentV2.FeatureKind.PLAIN;
        };
    }

    private static TerrainIntentV2.FeatureKind featureKind(TerrainIntentV2 intent, String featureId) {
        return featureOf(intent, featureId).kind();
    }

    private static TerrainIntentV2.Feature featureOf(TerrainIntentV2 intent, String featureId) {
        return intent.features().stream()
                .filter(feature -> feature.id().equals(featureId))
                .findFirst()
                .orElseThrow(() -> failure("v2.glacial-ice-missing-feature",
                        "referenced feature missing: " + featureId));
    }

    private static long scale(long normalizedMillionths, long extentMillionths) {
        return Math.multiplyExact(normalizedMillionths, extentMillionths) / FIXED;
    }

    private static int mid(TerrainIntentV2.IntRange range) {
        return Math.addExact(range.minimum(), range.maximum()) / 2;
    }

    public static String climateBindingChecksum(String climatePreset, String geometryChecksum) {
        return digest("climate-bind-v1|" + climatePreset + "|" + geometryChecksum);
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static FoundationSliceException failure(String ruleId, String message) {
        return new FoundationSliceException(ruleId, message);
    }

    public record HostBinding(
            String bedHostRelationId,
            String bedHostFeatureId,
            GlacialIcePlanV2.BedHostKind bedHostKind,
            GlacialIcePlanV2.IceKind iceKind
    ) {
    }

    private record FlowAxis(long headX, long headZ, long terminusX, long terminusZ) {
    }
}
