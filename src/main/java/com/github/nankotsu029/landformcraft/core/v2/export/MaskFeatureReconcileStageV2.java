package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.binding.BoundConstraintFieldV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationFailureCodeV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionCompositorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mask ⇔ feature reconcile pre-pass (V2-19-14, ADR 0043).
 *
 * <p>The 2026-07-23 cross-cutting audit (§4.2-4) measured that the declared HARD
 * {@code LAND_WATER_MASK} has to agree with the generated coastal raster cell for cell, so the only
 * working procedure was to <em>regenerate the mask</em> whenever geometry or seed changed
 * ({@code V2-18-13}, {@code V2-19-09}). A mask fixed first — hand-drawn, or extracted from an image —
 * was therefore unusable if the authored geometry sat a few blocks off, even when its shape was
 * right: the compositor rejects the run with {@code v2.coastal-transition-hard-conflict}.</p>
 *
 * <p>This pre-pass aligns the other direction. It chooses <b>one</b> rigid integer-block translation
 * for the whole declared feature set and applies it to every geometry control point before the
 * Blueprint is compiled. What it never does (ADR 0043 凍結1〜4):</p>
 *
 * <ul>
 *   <li>touch the mask — no value, digest, binding or no-data cell is changed or inferred;</li>
 *   <li>relax a gate — every fail-closed rule downstream keeps its verdict and its threshold;</li>
 *   <li>deform — no rotation, scale, per-vertex snap or per-feature offset, so declared relations
 *       ({@code ENCLOSES}, {@code ADJACENT_TO}, {@code OVERLAPS}) keep their exact relative geometry;</li>
 *   <li>move geometry that already agrees — the total order below makes {@code (0,0)} win every tie,
 *       so a run whose declared geometry has no mask disagreement is byte-identical.</li>
 * </ul>
 *
 * <p>The objective is an <em>estimator</em>: it counts cells where the coastal composition (sampled
 * with no HARD source) disagrees with the mask, which is not literally the compositor's per-cell HARD
 * predicate where contributors overlap. That is deliberate — the pre-pass solves registration, and
 * the existing HARD gates remain the sole authority on whether the result is acceptable (ADR 0043
 * D2/D4). A run with no in-tolerance alignment is rejected exactly as it was before this Task, by the
 * same rule; this class adds no rejection of its own.</p>
 */
final class MaskFeatureReconcileStageV2 {
    /** Normalized coordinate scale of {@link TerrainIntentV2.Point2} (0..10^6). */
    private static final long NORMALIZED_SCALE = TerrainIntentV2.FIXED_SCALE;

    /** Result of one pre-pass: the intent to generate from, plus what was done to get there. */
    record ReconciledIntentV2(TerrainIntentV2 intent, MaskFeatureReconcileV2 report) {
        ReconciledIntentV2 {
            Objects.requireNonNull(intent, "intent");
            Objects.requireNonNull(report, "report");
        }
    }

    /**
     * Aligns {@code intent} with {@code mask}. {@code runtime} must be the coastal runtime compiled
     * from that same intent, so the objective measures the declared geometry.
     */
    ReconciledIntentV2 reconcile(
            GenerationRequestV2.MaskFeatureReconcile declaration,
            TerrainIntentV2 intent,
            CoastalGeneratorRuntimeV2 runtime,
            BoundConstraintFieldV2 mask,
            int width,
            int length,
            CancellationToken token
    ) {
        Objects.requireNonNull(declaration, "declaration");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(token, "token");
        if (mask.width() != width || mask.length() != length) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.DIMENSIONS_INVALID,
                    "the land-water mask and the export bounds cover different domains");
        }
        int tolerance = declaration.toleranceBlocks();

        // One pass over the domain: the coastal composition of the declared geometry, sampled without
        // a HARD source so the raster describes what the features themselves claim.
        byte[] active = new byte[Math.multiplyExact(width, length)];
        int evaluated = 0;
        for (int z = 0; z < length; z++) {
            token.throwIfCancellationRequested();
            for (int x = 0; x < width; x++) {
                CoastalTransitionCompositorV2.CompositionSample sample =
                        runtime.composeAt(x, z, HardLandWaterSourceV2.NONE);
                if (sample.active()) {
                    active[z * width + x] = (byte) (sample.landWater() == 1 ? 1 : 2);
                    evaluated++;
                }
            }
        }

        CandidateV2 best = null;
        int declaredDisagreement = -1;
        long rejected = 0L;
        // Fixed iteration order (dz then dx ascending) and an explicit total order, so the choice is
        // independent of thread count, locale and collection iteration order.
        for (int dz = -tolerance; dz <= tolerance; dz++) {
            token.throwIfCancellationRequested();
            for (int dx = -tolerance; dx <= tolerance; dx++) {
                if (!translatable(intent, dx, dz, width, length)) {
                    rejected++;
                    continue;
                }
                int disagreement = disagreement(active, mask, dx, dz, width, length);
                if (disagreement < 0) {
                    rejected++;
                    continue;
                }
                if (dx == 0 && dz == 0) {
                    declaredDisagreement = disagreement;
                }
                CandidateV2 candidate = new CandidateV2(dx, dz, disagreement);
                if (best == null || candidate.betterThan(best)) {
                    best = candidate;
                }
            }
        }
        if (best == null || declaredDisagreement < 0) {
            // (0,0) is the declared intent itself and is always translatable inside its own domain, so
            // this is unreachable; failing closed beats silently continuing with an unknown offset.
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                    "the mask reconcile pre-pass found no valid candidate offset");
        }
        TerrainIntentV2 reconciled = best.dx() == 0 && best.dz() == 0
                ? intent
                : translate(intent, best.dx(), best.dz(), width, length);
        return new ReconciledIntentV2(reconciled, new MaskFeatureReconcileV2(
                tolerance, best.dx(), best.dz(), evaluated,
                declaredDisagreement, best.disagreement(),
                declaration.candidateCount(), rejected,
                best.dx() == 0 && best.dz() == 0 ? 0 : intent.features().size()));
    }

    /**
     * Cells where the coastal composition, moved by {@code (dx, dz)}, contradicts the mask, or
     * {@code -1} when the offset would push an active cell outside the mask domain. Mask no-data cells
     * are skipped: the pre-pass never treats an unspecified cell as agreement or as conflict.
     */
    private static int disagreement(
            byte[] active,
            BoundConstraintFieldV2 mask,
            int dx,
            int dz,
            int width,
            int length
    ) {
        int disagreement = 0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                byte claim = active[z * width + x];
                if (claim == 0) {
                    continue;
                }
                int sampleX = x + dx;
                int sampleZ = z + dz;
                if (sampleX < 0 || sampleX >= width || sampleZ < 0 || sampleZ >= length) {
                    return -1;
                }
                int declared = mask.valueAt(sampleX, sampleZ);
                if (declared != 0 && declared != 1) {
                    continue;
                }
                if (declared != (claim == 1 ? 1 : 0)) {
                    disagreement++;
                }
            }
        }
        return disagreement;
    }

    /** Whether every declared control point stays inside the normalized range after the translation. */
    private static boolean translatable(TerrainIntentV2 intent, int dx, int dz, int width, int length) {
        long deltaX = normalizedDelta(dx, width);
        long deltaZ = normalizedDelta(dz, length);
        for (TerrainIntentV2.Feature feature : intent.features()) {
            for (TerrainIntentV2.Point2 point : controlPoints(feature.geometry())) {
                long x = point.xMillionths() + deltaX;
                long z = point.zMillionths() + deltaZ;
                if (x < 0 || x > NORMALIZED_SCALE || z < 0 || z > NORMALIZED_SCALE) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Normalized delta of a whole-block translation. Block space is
     * {@code blockMillionths = normalized × (extent − 1)} (the existing coastal geometry rule), and the
     * rounding is symmetric about zero — {@code delta(+d) + delta(−d) == 0} exactly — so translating a
     * geometry and translating it back restores the original control points bit for bit.
     */
    static long normalizedDelta(int blocks, int extent) {
        if (blocks == 0) {
            return 0L;
        }
        if (extent < 2) {
            throw new IllegalArgumentException("a translation needs an extent of at least two cells");
        }
        long denominator = extent - 1L;
        long magnitude = (Math.abs((long) blocks) * 2L * NORMALIZED_SCALE + denominator)
                / (2L * denominator);
        return blocks > 0 ? magnitude : -magnitude;
    }

    /**
     * The reconciled intent: every declared feature's geometry moved by the same offset, everything
     * else identical. Coordinates live only in {@code Feature.geometry} — {@code Constraint} is
     * {@code METRIC_RANGE} / {@code EDGE_CLASSIFICATION} and {@code StructureRequest} carries ids and
     * counts — so a rigid translation of the geometry is the complete correction (ADR 0043 D1).
     */
    private static TerrainIntentV2 translate(
            TerrainIntentV2 intent,
            int dx,
            int dz,
            int width,
            int length
    ) {
        long deltaX = normalizedDelta(dx, width);
        long deltaZ = normalizedDelta(dz, length);
        List<TerrainIntentV2.Feature> features = new ArrayList<>(intent.features().size());
        for (TerrainIntentV2.Feature feature : intent.features()) {
            features.add(new TerrainIntentV2.Feature(
                    feature.id(), feature.kind(), translate(feature.geometry(), deltaX, deltaZ),
                    feature.parameters(), feature.priority(), feature.provenance()));
        }
        return new TerrainIntentV2(
                intent.intentVersion(), intent.intentId(), intent.theme(), intent.coordinateSystem(),
                features, intent.relations(), intent.constraints(), intent.environment(),
                intent.mapReferences(), intent.structures(), intent.provenance());
    }

    private static TerrainIntentV2.Geometry translate(
            TerrainIntentV2.Geometry geometry,
            long deltaX,
            long deltaZ
    ) {
        return switch (geometry) {
            case TerrainIntentV2.PointGeometry point ->
                    new TerrainIntentV2.PointGeometry(translate(point.point(), deltaX, deltaZ));
            case TerrainIntentV2.MultiPointGeometry multi -> new TerrainIntentV2.MultiPointGeometry(
                    multi.points().stream()
                            .map(named -> new TerrainIntentV2.NamedPoint(
                                    named.id(), translate(named.point(), deltaX, deltaZ)))
                            .toList());
            case TerrainIntentV2.SplineGeometry spline -> new TerrainIntentV2.SplineGeometry(
                    translate(spline.points(), deltaX, deltaZ), spline.interpolation());
            case TerrainIntentV2.MultiSplineGeometry multi -> new TerrainIntentV2.MultiSplineGeometry(
                    multi.paths().stream()
                            .map(path -> new TerrainIntentV2.NamedPath(
                                    path.id(), path.startEndpointId(), path.endEndpointId(),
                                    translate(path.points(), deltaX, deltaZ)))
                            .toList(),
                    multi.interpolation());
            case TerrainIntentV2.PolygonGeometry polygon -> new TerrainIntentV2.PolygonGeometry(
                    polygon.rings().stream()
                            .map(ring -> translate(ring, deltaX, deltaZ))
                            .toList());
            case TerrainIntentV2.VolumeGuideGeometry volume -> new TerrainIntentV2.VolumeGuideGeometry(
                    new TerrainIntentV2.PolygonGeometry(volume.footprint().rings().stream()
                            .map(ring -> translate(ring, deltaX, deltaZ))
                            .toList()),
                    volume.vertical());
        };
    }

    private static List<TerrainIntentV2.Point2> translate(
            List<TerrainIntentV2.Point2> points,
            long deltaX,
            long deltaZ
    ) {
        return points.stream().map(point -> translate(point, deltaX, deltaZ)).toList();
    }

    private static TerrainIntentV2.Point2 translate(
            TerrainIntentV2.Point2 point,
            long deltaX,
            long deltaZ
    ) {
        return new TerrainIntentV2.Point2(
                Math.toIntExact(point.xMillionths() + deltaX),
                Math.toIntExact(point.zMillionths() + deltaZ));
    }

    private static List<TerrainIntentV2.Point2> controlPoints(TerrainIntentV2.Geometry geometry) {
        return switch (geometry) {
            case TerrainIntentV2.PointGeometry point -> List.of(point.point());
            case TerrainIntentV2.MultiPointGeometry multi ->
                    multi.points().stream().map(TerrainIntentV2.NamedPoint::point).toList();
            case TerrainIntentV2.SplineGeometry spline -> spline.points();
            case TerrainIntentV2.MultiSplineGeometry multi ->
                    multi.paths().stream().flatMap(path -> path.points().stream()).toList();
            case TerrainIntentV2.PolygonGeometry polygon ->
                    polygon.rings().stream().flatMap(List::stream).toList();
            case TerrainIntentV2.VolumeGuideGeometry volume ->
                    volume.footprint().rings().stream().flatMap(List::stream).toList();
        };
    }

    /**
     * One evaluated offset. The total order is
     * {@code (disagreement, |dx|+|dz|, dz, dx)} ascending: fewest disagreements first, then the
     * smallest movement — which is what makes an already-agreeing geometry stay exactly where the
     * author put it — and finally two fixed coordinate keys so no two candidates ever compare equal.
     */
    private record CandidateV2(int dx, int dz, int disagreement) {
        boolean betterThan(CandidateV2 other) {
            if (disagreement != other.disagreement()) {
                return disagreement < other.disagreement();
            }
            int movement = Math.abs(dx) + Math.abs(dz);
            int otherMovement = Math.abs(other.dx()) + Math.abs(other.dz());
            if (movement != otherMovement) {
                return movement < otherMovement;
            }
            if (dz != other.dz()) {
                return dz < other.dz();
            }
            return dx < other.dx();
        }
    }
}
