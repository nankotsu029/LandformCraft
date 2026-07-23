package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.v2.binding.BoundConstraintFieldV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationFailureCodeV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

/**
 * Resolved macro foundation of one surface export run (V2-18-09, ADR 0038 D1/D5).
 *
 * <p>Implements the foundation-tier owner model the ADR fixes: <em>candidate</em> owners
 * (0..N foundation producers plus the mask-derived <em>background</em>), and exactly one
 * <em>effective</em> owner per cell after resolution. The background owner covers the whole
 * surface domain from the explicit {@code LAND_WATER_MASK} input; a producer replaces it inside
 * its declared footprint. Producer-producer overlap without a declared interaction and cells no
 * candidate covers (mask no-data with no producer) are rejected fail-closed — the kernel
 * invariants of ADR 0038 D7-1, enforced at generation time from the first wiring, never as an
 * optional gate. Surface modifiers (the coastal four) are <em>not</em> foundation owners and never
 * appear here; they compose over this foundation through the modifier compositor.</p>
 *
 * <p>No producer kind is production-wired into the coastal spine yet, so today's resolution always
 * yields the background owner; the candidate list exists so V2-15 wiring Tasks can add producers
 * without changing this contract. Never infers land-water geometry: the medium comes only from the
 * declared mask, the provisional elevation only from the request's declared per-medium base levels
 * (ADR 0038 D2 — EDGE constraints and feature footprints are forbidden inference sources).</p>
 */
final class MacroFoundationV2 {
    static final int SCALE = 1_000_000;
    /** Owner index of the background candidate in the {@code foundation.owner-index} namespace. */
    static final int BACKGROUND_OWNER_INDEX = 1;

    /** A foundation producer candidate layer (ADR 0038 D1-1). None are wired yet. */
    record ProducerLayer(
            int ownerIndex,
            FootprintPredicate footprint,
            IntBinaryOperator mediumAt,
            IntBinaryOperator elevationMillionthsAt
    ) {
        ProducerLayer {
            if (ownerIndex <= BACKGROUND_OWNER_INDEX) {
                throw new IllegalArgumentException("producer ownerIndex must be above the background index");
            }
            Objects.requireNonNull(footprint, "footprint");
            Objects.requireNonNull(mediumAt, "mediumAt");
            Objects.requireNonNull(elevationMillionthsAt, "elevationMillionthsAt");
        }
    }

    @FunctionalInterface
    interface FootprintPredicate {
        boolean contains(int globalX, int globalZ);
    }

    private final BoundConstraintFieldV2 mask;
    private final List<ProducerLayer> producers;
    private final int landElevationMillionths;
    private final int waterElevationMillionths;

    MacroFoundationV2(
            BoundConstraintFieldV2 mask,
            GenerationRequestV2.FoundationBaseLevels baseLevels,
            List<ProducerLayer> producers
    ) {
        this.mask = Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(baseLevels, "baseLevels");
        this.producers = List.copyOf(Objects.requireNonNull(producers, "producers"));
        if (mask.role() != TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                    "macro foundation requires a LAND_WATER_MASK field, got " + mask.role());
        }
        this.landElevationMillionths = Math.multiplyExact(baseLevels.landSurfaceY(), SCALE);
        this.waterElevationMillionths = Math.multiplyExact(baseLevels.waterBedY(), SCALE);
    }

    int width() {
        return mask.width();
    }

    int length() {
        return mask.length();
    }

    /** SHA-256 of the exact mask bytes the foundation was resolved from (input provenance). */
    String maskSourceChecksum() {
        return mask.sourceChecksum();
    }

    /**
     * Effective foundation owner index at one cell — exactly one after resolution (ADR 0038 D1-4).
     * Producer footprints replace the background declaratively; two producers overlapping without a
     * declared interaction, and cells no candidate covers, fail closed.
     */
    int effectiveOwnerIndexAt(int globalX, int globalZ) {
        ProducerLayer effective = null;
        for (ProducerLayer producer : producers) {
            if (!producer.footprint().contains(globalX, globalZ)) {
                continue;
            }
            if (effective != null) {
                throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.UNDECLARED_OVERLAP,
                        "overlapping foundation producers have no merge contract at "
                                + globalX + ',' + globalZ);
            }
            effective = producer;
        }
        if (effective != null) {
            return effective.ownerIndex();
        }
        if (isMaskNoData(globalX, globalZ)) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.OWNERLESS_CELL,
                    "no foundation candidate covers cell " + globalX + ',' + globalZ
                            + " (mask no-data and no producer footprint); implicit baseline fallback is forbidden");
        }
        return BACKGROUND_OWNER_INDEX;
    }

    /** Effective land-water medium (1 = LAND, 0 = WATER) at one cell. Never inferred. */
    int mediumAt(int globalX, int globalZ) {
        int owner = effectiveOwnerIndexAt(globalX, globalZ);
        if (owner != BACKGROUND_OWNER_INDEX) {
            return requireValidMedium(producerByIndex(owner).mediumAt().applyAsInt(globalX, globalZ),
                    globalX, globalZ);
        }
        return requireValidMedium(mask.valueAt(globalX, globalZ), globalX, globalZ);
    }

    /** Effective provisional base elevation (block-millionths) at one cell. */
    int elevationMillionthsAt(int globalX, int globalZ) {
        int owner = effectiveOwnerIndexAt(globalX, globalZ);
        if (owner != BACKGROUND_OWNER_INDEX) {
            return producerByIndex(owner).elevationMillionthsAt().applyAsInt(globalX, globalZ);
        }
        return mediumAt(globalX, globalZ) == 1 ? landElevationMillionths : waterElevationMillionths;
    }

    /**
     * The mask as the coastal HARD land-water source: a specified cell always wins in the modifier
     * compositor, a no-data cell leaves the modifiers' own classification in place. Reads outside
     * the field are unspecified so halo sampling never fabricates a constraint.
     */
    HardLandWaterSourceV2 hardLandWaterSource() {
        return (globalX, globalZ) -> {
            if (globalX < 0 || globalX >= mask.width() || globalZ < 0 || globalZ >= mask.length()) {
                return HardLandWaterSourceV2.Classification.UNSPECIFIED;
            }
            int value = mask.valueAt(globalX, globalZ);
            if (value == 1) {
                return HardLandWaterSourceV2.Classification.LAND;
            }
            if (value == 0) {
                return HardLandWaterSourceV2.Classification.WATER;
            }
            return HardLandWaterSourceV2.Classification.UNSPECIFIED;
        };
    }

    /** Desired land-water raw value straight from the input mask (1 / 0), or the role no-data sentinel. */
    int desiredLandWaterAt(int globalX, int globalZ) {
        return mask.valueAt(globalX, globalZ);
    }

    private boolean isMaskNoData(int globalX, int globalZ) {
        int value = mask.valueAt(globalX, globalZ);
        return value != 0 && value != 1;
    }

    private int requireValidMedium(int value, int globalX, int globalZ) {
        if (value != 0 && value != 1) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                    "foundation medium must be LAND(1) or WATER(0) at " + globalX + ',' + globalZ);
        }
        return value;
    }

    private ProducerLayer producerByIndex(int ownerIndex) {
        return producers.stream()
                .filter(producer -> producer.ownerIndex() == ownerIndex)
                .findFirst()
                .orElseThrow();
    }
}
