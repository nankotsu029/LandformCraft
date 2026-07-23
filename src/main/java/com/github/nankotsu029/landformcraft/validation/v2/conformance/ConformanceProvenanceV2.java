package com.github.nankotsu029.landformcraft.validation.v2.conformance;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Objects;

/**
 * Provenance binding of one conformance source (V2-18-07): where the <em>desired</em> reference for a
 * target comes from, and by which digest it is identified.
 *
 * <p>The macro-foundation audit's central provenance defect was that the sealed surface Blueprint bound
 * its {@code LAND_WATER_MASK} artifact reference to the digest of the field the pipeline had just
 * <em>generated</em> — a self-reference that made "desired" indistinguishable from "actual" and hid the
 * fact that no input mask is ever read. This record makes the distinction explicit and reviewable:</p>
 *
 * <ul>
 *   <li>{@link Origin#INPUT_MASK} — the desired reference is an external input artifact (the declared
 *       constraint-map source) identified by its expected SHA-256. This is the honest provenance the
 *       V2-18-07 {@code withLandWaterBinding} fix now records for the surface export path.</li>
 *   <li>{@link Origin#SELF_DERIVED} — no external desired reference exists; the "desired" is derived from
 *       the generation itself. A conformance check against a self-derived reference is vacuous and is
 *       reported as an unconsumed target rather than as a passing residual.</li>
 * </ul>
 */
public record ConformanceProvenanceV2(
        TerrainIntentV2.ConstraintMapRole role,
        String sourceId,
        String artifactDigest,
        Origin origin
) {
    /** Whether the desired reference is an external input or self-derived from the generated output. */
    public enum Origin {
        INPUT_MASK,
        SELF_DERIVED
    }

    public ConformanceProvenanceV2 {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(artifactDigest, "artifactDigest");
        Objects.requireNonNull(origin, "origin");
        if (sourceId.isBlank()) {
            throw new IllegalArgumentException("provenance sourceId must not be blank");
        }
        if (artifactDigest.isBlank()) {
            throw new IllegalArgumentException("provenance artifactDigest must not be blank");
        }
    }

    /** True when the desired reference is an external input the actual field can be checked against. */
    public boolean resolvable() {
        return origin == Origin.INPUT_MASK;
    }
}
