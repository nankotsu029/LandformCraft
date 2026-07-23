package com.github.nankotsu029.landformcraft.validation.v2.target;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.ValidationTargetV2;

import java.util.Locale;

/**
 * V2-18-04 evaluator for {@code v2.edge-classification} targets (compiled from HARD/SOFT
 * {@code EDGE_CLASSIFICATION} constraints). It measures the land/water share of one world edge and
 * checks it against the constraint's minimum share.
 *
 * <p><b>Edge-band contract (evaluator version 1).</b> The measurement region for an edge is the strip
 * of the outermost {@code max(1, perpendicularExtent / 32)} rows (NORTH/SOUTH) or columns (EAST/WEST)
 * spanning the full parallel extent. The depth scales with the map so a fixed absolute band never
 * dominates a small map nor vanishes on a large one, and it is a pure function of the release-local
 * dimensions, so the measured region is deterministic. The V2-18 audit confirmed the north/south
 * land-share conclusion is band-width independent (0.000 vs 1.000 at both a 1-row and a 60-row band),
 * so this fixed contract does not bias the result. Changing the region requires bumping
 * {@link #EVALUATOR_VERSION}.</p>
 *
 * <p>The land/water value is read from the target's required field ({@code intent.land-water-mask}),
 * where {@code 1} is land and {@code 0} is water. A cell that reports neither (e.g. a no-data
 * sentinel) matches no classification and therefore lowers the share — it is never guessed.</p>
 */
public final class EdgeClassificationEvaluatorV2 implements ValidationTargetEvaluatorV2 {
    public static final String RULE_ID = "v2.edge-classification";
    public static final int EVALUATOR_VERSION = 1;
    /** Edge-band depth = max(1, perpendicularExtent / EDGE_BAND_DENOMINATOR). Part of version 1. */
    static final int EDGE_BAND_DENOMINATOR = 32;
    private static final int LAND_VALUE = 1;
    private static final int WATER_VALUE = 0;

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public int evaluatorVersion() {
        return EVALUATOR_VERSION;
    }

    @Override
    public TargetEvaluationV2 evaluate(ValidationTargetV2 target, ValidationFieldSamplerV2 fields) {
        TerrainIntentV2.Edge edge = parseEdge(target.metric());
        int matchValue = parseClassificationValue(target.metric());
        String fieldId = requiredField(target);
        int width = fields.width();
        int length = fields.length();

        int[] band = edgeBand(edge, width, length);
        long total = 0;
        long matches = 0;
        for (int z = band[2]; z < band[3]; z++) {
            for (int x = band[0]; x < band[1]; x++) {
                total++;
                if (fields.valueAt(fieldId, x, z) == matchValue) {
                    matches++;
                }
            }
        }
        long measured = total == 0 ? 0L : matches * TerrainIntentV2.FIXED_SCALE / total;
        long minimum = target.expected().minimumMillionths();
        // Range is [minimum, FIXED_SCALE]; the measured share can never exceed FIXED_SCALE, so the
        // only failure mode is falling below the minimum, applying the target's tolerance.
        boolean satisfied = measured + target.toleranceMillionths() >= minimum;
        String detail = "edge " + edge.name().toLowerCase(Locale.ROOT)
                + " " + (matchValue == LAND_VALUE ? "land" : "sea")
                + " share " + measured + "/1000000 over " + total + " band cells"
                + " (minimum " + minimum + ", tolerance " + target.toleranceMillionths() + ")";
        return new TargetEvaluationV2(
                target.targetId(), target.sourceConstraintId(), RULE_ID, EVALUATOR_VERSION,
                target.hardness(), measured, minimum, satisfied, detail);
    }

    /** Returns {@code [xStart, xEnd, zStart, zEnd)} for the outermost band of the named edge. */
    private static int[] edgeBand(TerrainIntentV2.Edge edge, int width, int length) {
        return switch (edge) {
            case NORTH -> new int[] {0, width, 0, bandDepth(length)};
            case SOUTH -> new int[] {0, width, length - bandDepth(length), length};
            case WEST -> new int[] {0, bandDepth(width), 0, length};
            case EAST -> new int[] {width - bandDepth(width), width, 0, length};
        };
    }

    private static int bandDepth(int perpendicularExtent) {
        return Math.max(1, perpendicularExtent / EDGE_BAND_DENOMINATOR);
    }

    private static TerrainIntentV2.Edge parseEdge(String metric) {
        return TerrainIntentV2.Edge.valueOf(metricPart(metric, 1).toUpperCase(Locale.ROOT));
    }

    private static int parseClassificationValue(String metric) {
        TerrainIntentV2.EdgeClassification classification =
                TerrainIntentV2.EdgeClassification.valueOf(metricPart(metric, 2).toUpperCase(Locale.ROOT));
        return classification == TerrainIntentV2.EdgeClassification.LAND ? LAND_VALUE : WATER_VALUE;
    }

    private static String metricPart(String metric, int index) {
        String[] parts = metric.split("\\.");
        if (parts.length != 3 || !"edge".equals(parts[0])) {
            throw new IllegalArgumentException("edge-classification metric must be edge.<edge>.<classification>: " + metric);
        }
        return parts[index];
    }

    private static String requiredField(ValidationTargetV2 target) {
        if (target.requiredFields().size() != 1) {
            throw new IllegalArgumentException(
                    "edge-classification target must declare exactly one required field: " + target.targetId());
        }
        return target.requiredFields().getFirst();
    }
}
