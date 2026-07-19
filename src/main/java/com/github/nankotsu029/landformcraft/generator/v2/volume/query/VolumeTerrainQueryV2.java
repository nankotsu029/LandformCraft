package com.github.nankotsu029.landformcraft.generator.v2.volume.query;

import com.github.nankotsu029.landformcraft.generator.v2.TerrainQuery;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgOccupancyV2;
import com.github.nankotsu029.landformcraft.generator.v2.volume.csg.VolumeCsgSampleV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeCsgPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.volume.VolumeSdfPrimitivePlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * V2-5-05 volume-aware {@link TerrainQuery}. Composes a base heightfield query with ordered CSG.
 * Without a CSG plan the query is a pure pass-through of the base (base-only compatibility).
 * Feature generators and Paper placement remain out of scope.
 */
public final class VolumeTerrainQueryV2 implements TerrainQuery {
    public static final int MAXIMUM_SOLID_INTERVALS_PER_COLUMN = 64;
    public static final int MAXIMUM_FLUID_INTERVALS_PER_COLUMN = 64;

    private final TerrainQuery base;
    private final VolumeTerrainCompositionKernelV2 composition;
    private final int maximumSolidIntervals;
    private final int maximumFluidIntervals;

    public VolumeTerrainQueryV2(TerrainQuery base) {
        this(base, null, MAXIMUM_SOLID_INTERVALS_PER_COLUMN, MAXIMUM_FLUID_INTERVALS_PER_COLUMN);
    }

    public VolumeTerrainQueryV2(
            TerrainQuery base,
            VolumeCsgPlanV2 csgPlan,
            VolumeSdfPrimitivePlanV2 primitivePlan
    ) {
        this(
                base,
                new VolumeTerrainCompositionKernelV2(csgPlan, primitivePlan),
                MAXIMUM_SOLID_INTERVALS_PER_COLUMN,
                MAXIMUM_FLUID_INTERVALS_PER_COLUMN);
    }

    public VolumeTerrainQueryV2(
            TerrainQuery base,
            VolumeTerrainCompositionKernelV2 composition,
            int maximumSolidIntervals,
            int maximumFluidIntervals
    ) {
        this.base = Objects.requireNonNull(base, "base");
        this.composition = composition;
        if (maximumSolidIntervals < 1 || maximumSolidIntervals > MAXIMUM_SOLID_INTERVALS_PER_COLUMN
                || maximumFluidIntervals < 1
                || maximumFluidIntervals > MAXIMUM_FLUID_INTERVALS_PER_COLUMN) {
            throw new VolumeTerrainQueryExceptionV2(
                    VolumeTerrainQueryFailureCodeV2.BUDGET_EXCEEDED,
                    "volume query interval budget out of range");
        }
        this.maximumSolidIntervals = maximumSolidIntervals;
        this.maximumFluidIntervals = maximumFluidIntervals;
    }

    public TerrainQuery base() {
        return base;
    }

    public boolean volumeEnabled() {
        return composition != null;
    }

    @Override
    public String queryKernelVersion() {
        return composition == null ? QUERY_KERNEL_COLUMN_V1 : QUERY_KERNEL_VOLUME_V1;
    }

    @Override
    public QueryBounds bounds() {
        return base.bounds();
    }

    @Override
    public BlockClass blockClassAt(int x, int y, int z) {
        return sample(x, y, z).blockClass();
    }

    @Override
    public SemanticMaterial semanticMaterialAt(int x, int y, int z) {
        return sample(x, y, z).material();
    }

    @Override
    public FluidBody fluidBodyAt(int x, int y, int z) {
        return sample(x, y, z).fluidBody();
    }

    @Override
    public List<VerticalInterval> solidIntervals(int x, int z) {
        return columnIntervals(x, z).solid();
    }

    @Override
    public List<VerticalInterval> fluidIntervals(int x, int z) {
        return columnIntervals(x, z).fluid();
    }

    @Override
    public OptionalInt topWalkableSurface(int x, int z, WalkableSurfacePolicy policy) {
        Objects.requireNonNull(policy, "policy");
        requireColumn(x, z);
        List<VerticalInterval> solids = solidIntervals(x, z);
        if (solids.isEmpty()) {
            return OptionalInt.empty();
        }
        if (policy == WalkableSurfacePolicy.DRY_ONLY && !fluidIntervals(x, z).isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(solids.get(solids.size() - 1).maxY());
    }

    @Override
    public OptionalInt surfaceBelow(int x, int y, int z) {
        requireCoordinate(x, y, z);
        List<VerticalInterval> solids = solidIntervals(x, z);
        for (VerticalInterval solid : solids) {
            if (y >= solid.minY() && y < solid.maxY()) {
                return OptionalInt.empty();
            }
        }
        int best = Integer.MIN_VALUE;
        boolean found = false;
        for (VerticalInterval solid : solids) {
            if (solid.maxY() <= y) {
                best = Math.max(best, solid.maxY());
                found = true;
            }
        }
        return found ? OptionalInt.of(best) : OptionalInt.empty();
    }

    @Override
    public OptionalInt ceilingAbove(int x, int y, int z) {
        requireCoordinate(x, y, z);
        int best = Integer.MAX_VALUE;
        boolean found = false;
        for (VerticalInterval solid : solidIntervals(x, z)) {
            if (solid.minY() > y) {
                best = Math.min(best, solid.minY());
                found = true;
            }
        }
        return found ? OptionalInt.of(best) : OptionalInt.empty();
    }

    @Override
    public int featureMembershipAt(int x, int y, int z) {
        return base.featureMembershipAt(x, y, z);
    }

    /**
     * Deterministic occupancy fingerprint for a block AABB. Used by whole/tile/thread tests.
     */
    public String regionOccupancyChecksum(
            int minX,
            int minY,
            int minZ,
            int maxXInclusive,
            int maxYInclusive,
            int maxZInclusive
    ) {
        if (minX > maxXInclusive || minY > maxYInclusive || minZ > maxZInclusive) {
            throw new VolumeTerrainQueryExceptionV2(
                    VolumeTerrainQueryFailureCodeV2.INVALID_INTERVAL, "region AABB empty");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(queryKernelVersion().getBytes(StandardCharsets.UTF_8));
            for (int z = minZ; z <= maxZInclusive; z++) {
                for (int y = minY; y <= maxYInclusive; y++) {
                    for (int x = minX; x <= maxXInclusive; x++) {
                        VolumeTerrainCompositionKernelV2.ComposedSample sample = sample(x, y, z);
                        digest.update((byte) sample.blockClass().ordinal());
                        digest.update((byte) sample.material().ordinal());
                        digest.update((byte) sample.fluidBody().ordinal());
                        digest.update(sample.sample().fluidBodyId().getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                    }
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private VolumeTerrainCompositionKernelV2.ComposedSample sample(int x, int y, int z) {
        requireCoordinate(x, y, z);
        if (composition == null) {
            TerrainQuery.BlockClass blockClass = base.blockClassAt(x, y, z);
            VolumeCsgSampleV2 csg = switch (blockClass) {
                case AIR -> VolumeCsgSampleV2.air();
                case SOLID -> VolumeCsgSampleV2.solid();
                case FLUID -> VolumeCsgSampleV2.fluid("fluid.base-water");
            };
            return new VolumeTerrainCompositionKernelV2.ComposedSample(
                    csg, base.semanticMaterialAt(x, y, z));
        }
        return composition.compose(base, x, y, z);
    }

    private ColumnIntervals columnIntervals(int x, int z) {
        requireColumn(x, z);
        QueryBounds bounds = base.bounds();
        int minY = bounds.minY();
        int maxY = bounds.maxY();
        long span = (long) maxY - (long) minY + 1L;
        if (span < 1L || span > Integer.MAX_VALUE) {
            throw new VolumeTerrainQueryExceptionV2(
                    VolumeTerrainQueryFailureCodeV2.Y_OVERFLOW, "vertical span overflow");
        }
        List<VerticalInterval> solids = new ArrayList<>();
        List<VerticalInterval> fluids = new ArrayList<>();
        int y = minY;
        while (y <= maxY) {
            VolumeTerrainCompositionKernelV2.ComposedSample start = sample(x, y, z);
            VolumeCsgOccupancyV2 kind = start.sample().occupancy();
            if (kind == VolumeCsgOccupancyV2.AIR) {
                y++;
                continue;
            }
            String fluidId = start.sample().fluidBodyId();
            int startY = y;
            y++;
            while (y <= maxY) {
                VolumeTerrainCompositionKernelV2.ComposedSample next = sample(x, y, z);
                if (next.sample().occupancy() != kind
                        || !Objects.equals(next.sample().fluidBodyId(), fluidId)) {
                    break;
                }
                y++;
            }
            VerticalInterval interval = new VerticalInterval(startY, y - 1);
            if (kind == VolumeCsgOccupancyV2.SOLID) {
                solids.add(interval);
                if (solids.size() > maximumSolidIntervals) {
                    throw new VolumeTerrainQueryExceptionV2(
                            VolumeTerrainQueryFailureCodeV2.BUDGET_EXCEEDED,
                            "solid interval budget exceeded");
                }
            } else {
                fluids.add(interval);
                if (fluids.size() > maximumFluidIntervals) {
                    throw new VolumeTerrainQueryExceptionV2(
                            VolumeTerrainQueryFailureCodeV2.BUDGET_EXCEEDED,
                            "fluid interval budget exceeded");
                }
            }
        }
        rejectSolidFluidOverlap(solids, fluids);
        return new ColumnIntervals(List.copyOf(solids), List.copyOf(fluids));
    }

    /** Visible for corruption tests: solid and fluid must never claim the same Y. */
    static void rejectSolidFluidOverlap(
            List<VerticalInterval> solids,
            List<VerticalInterval> fluids
    ) {
        for (VerticalInterval solid : solids) {
            for (VerticalInterval fluid : fluids) {
                if (solid.maxY() >= fluid.minY() && fluid.maxY() >= solid.minY()) {
                    throw new VolumeTerrainQueryExceptionV2(
                            VolumeTerrainQueryFailureCodeV2.OWNER_CONFLICT,
                            "solid/fluid interval overlap");
                }
            }
        }
    }

    private void requireColumn(int x, int z) {
        if (!base.bounds().containsColumn(x, z)) {
            throw new VolumeTerrainQueryExceptionV2(
                    VolumeTerrainQueryFailureCodeV2.OUT_OF_BOUNDS,
                    "column outside volume query bounds");
        }
    }

    private void requireCoordinate(int x, int y, int z) {
        if (!base.bounds().contains(x, y, z)) {
            throw new VolumeTerrainQueryExceptionV2(
                    VolumeTerrainQueryFailureCodeV2.OUT_OF_BOUNDS,
                    "block outside volume query bounds");
        }
    }

    private static String hex(byte[] digest) {
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    private record ColumnIntervals(List<VerticalInterval> solid, List<VerticalInterval> fluid) {
    }
}
