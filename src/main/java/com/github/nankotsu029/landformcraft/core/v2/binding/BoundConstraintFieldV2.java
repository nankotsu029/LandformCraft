package com.github.nankotsu029.landformcraft.core.v2.binding;

import com.github.nankotsu029.landformcraft.core.v2.CanonicalConstraintRasterV2;
import com.github.nankotsu029.landformcraft.core.v2.ConstraintMapSamplerV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Objects;

/**
 * V2-18-06 canonical, XZ-addressable view of one securely resolved and decoded constraint map.
 *
 * <p>A bound field is the reusable product of {@link ConstraintMapFieldBindingV2}: the release-request
 * coordinate space (global X/Z) already carries the map's normalized registration, so a consumer reads
 * {@link #valueAt(int, int)} without knowing the source's pixel geometry, crop, rotation, or flip. The
 * canonical value follows the role — for {@code LAND_WATER_MASK} it is {@code 1} (land) / {@code 0}
 * (water) with a role-specific no-data sentinel, exactly the encoding
 * {@link CanonicalConstraintRasterV2} already produces for the manual path. The permanent consumer is
 * the {@code V2-18-09} macro foundation stage; this class is deliberately not coastal-specific.</p>
 *
 * <p>The field is immutable and integer-only, so repeated reads and reads from other threads return the
 * same value regardless of module registration order, locale, timezone, or seed.</p>
 */
public final class BoundConstraintFieldV2 {
    private final GenerationRequestV2.ConstraintMapSource source;
    private final TerrainIntentV2.ConstraintMapBinding binding;
    private final CanonicalConstraintRasterV2 raster;
    private final int width;
    private final int length;
    private final String sourceChecksum;

    BoundConstraintFieldV2(
            GenerationRequestV2.ConstraintMapSource source,
            TerrainIntentV2.ConstraintMapBinding binding,
            CanonicalConstraintRasterV2 raster,
            int width,
            int length,
            String sourceChecksum
    ) {
        this.source = Objects.requireNonNull(source, "source");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.raster = Objects.requireNonNull(raster, "raster");
        if (width < 1 || length < 1) {
            throw new IllegalArgumentException("bound constraint field dimensions must be positive");
        }
        this.width = width;
        this.length = length;
        this.sourceChecksum = Objects.requireNonNull(sourceChecksum, "sourceChecksum");
    }

    /** Semantic role the binding declared (e.g. {@code LAND_WATER_MASK}). */
    public TerrainIntentV2.ConstraintMapRole role() {
        return binding.role();
    }

    /** Declared source id of the constraint map this field was bound from. */
    public String sourceId() {
        return source.sourceId();
    }

    /** SHA-256 of the exact verified source bytes, so a consumer can bind provenance to the input map. */
    public String sourceChecksum() {
        return sourceChecksum;
    }

    /** Release-local field width in global X cells. */
    public int width() {
        return width;
    }

    /** Release-local field length in global Z cells. */
    public int length() {
        return length;
    }

    /** Stable canonicalization contract id of the normalized XZ registration. */
    public String canonicalizationVersion() {
        return ConstraintMapSamplerV2.CANONICALIZATION_VERSION;
    }

    /**
     * Canonical value at the global cell. For {@code LAND_WATER_MASK} the value is {@code 1} (land),
     * {@code 0} (water), or the role no-data sentinel where the source declared no-data; for other roles
     * it is the same canonical encoding the field sidecars use. Reads outside the field are rejected.
     */
    public int valueAt(int globalX, int globalZ) {
        if (globalX < 0 || globalX >= width || globalZ < 0 || globalZ >= length) {
            throw new IndexOutOfBoundsException("coordinate outside the bound constraint field");
        }
        return raster.desiredRawAt(globalX, globalZ);
    }
}
