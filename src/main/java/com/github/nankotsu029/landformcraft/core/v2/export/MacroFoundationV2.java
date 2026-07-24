package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.v2.binding.BoundConstraintFieldV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationFailureCodeV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.generator.v2.detail.CoherentDetailKernelV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * <p>V2-19-07 wired the first producer kind ({@code PLAIN}); before it the candidate list was always
 * empty and every cell resolved to the background. Never infers land-water geometry: the medium comes
 * only from the declared mask or a declared producer, the provisional elevation only from the
 * explicit sources ADR 0038 D2-2 allows — a {@code HEIGHT_GUIDE} map where it specifies a value, a
 * producer inside its own declared footprint, and the request's declared per-medium base levels
 * everywhere else (EDGE constraints and feature footprints are forbidden inference sources).</p>
 *
 * <p><b>Producer contract (V2-19-07).</b> A producer replaces the background inside its declared
 * footprint and is then the cell's single source of medium and base elevation. Three contradictions
 * fail closed rather than resolving silently, each with a stable rule id: a producer medium against
 * the HARD mask that is the land-water authority (ADR 0038 D2-3), a producer elevation outside the
 * request's own vertical extent, and a producer elevation against a HARD {@code HEIGHT_GUIDE} beyond
 * the binding's tolerance (two declared HARD sources for one height, AGENTS.md §7). Producer-producer
 * overlap without a declared interaction stays {@code UNDECLARED_OVERLAP}. Surface modifiers compose
 * <em>over</em> the resolved foundation and keep owning the height of the cells they claim (ADR 0038
 * D5-3), so a modifier over a producer footprint is a declared interaction, not a conflict.</p>
 *
 * <p><b>Elevation priority (V2-19-06).</b> Before this Task the background elevation was two
 * constants, so a mask-driven macro foundation was flat land plus flat water however much relief the
 * author declared. The guide now takes precedence per cell and the per-medium base level is the
 * fallback for the cells it does not specify — never the reverse, and never a blend. A guide value
 * outside the request's vertical bounds is out of contract and rejected rather than clamped, and a
 * guide cell the guide itself marks no-data falls back to the base level rather than to zero.</p>
 */
final class MacroFoundationV2 {
    static final int SCALE = 1_000_000;
    /** Owner index of the background candidate in the {@code foundation.owner-index} namespace. */
    static final int BACKGROUND_OWNER_INDEX = 1;
    /** "This cell has no desired height", matching the canonical HEIGHT_GUIDE no-data sentinel. */
    static final int NO_HEIGHT = Integer.MIN_VALUE;
    /** A HARD guide and a surface modifier both claiming one cell's height (AGENTS.md §7). */
    static final String RULE_HEIGHT_GUIDE_MODIFIER_CONFLICT = "v2.foundation.height-guide-modifier-conflict";
    /** A guide value outside the request's vertical extent. */
    static final String RULE_HEIGHT_GUIDE_OUT_OF_CONTRACT = "v2.foundation.height-guide-out-of-contract";
    /** A foundation producer claiming a medium the HARD land-water mask contradicts (ADR 0038 D2-3). */
    static final String RULE_PRODUCER_MASK_MEDIUM_CONFLICT = "v2.foundation.producer-mask-medium-conflict";
    /** A producer elevation outside the request's vertical extent — rejected, never clamped. */
    static final String RULE_PRODUCER_ELEVATION_OUT_OF_CONTRACT =
            "v2.foundation.producer-elevation-out-of-contract";
    /** A HARD guide and a foundation producer both claiming one cell's height (AGENTS.md §7). */
    static final String RULE_HEIGHT_GUIDE_PRODUCER_CONFLICT = "v2.foundation.height-guide-producer-conflict";
    /** Coherent detail pushed a background cell out of the extent or across the water level (V2-19-12). */
    static final String RULE_DETAIL_OUT_OF_CONTRACT = "v2.foundation.detail-out-of-contract";

    /**
     * The resolved {@code HEIGHT_GUIDE} background elevation source (ADR 0038 D2-2 alternative (a)).
     *
     * <p>{@code minimumMillionths} / {@code maximumMillionths} are the request's own vertical extent:
     * a guide is an input to generation, so a value the Release could not represent fails closed
     * instead of being silently clamped the way the diagnostic {@code actual} projection rounds it.
     * {@code toleranceMillionths} is the declared binding tolerance and is only consulted where a
     * surface modifier claims a cell a HARD guide also specifies.</p>
     */
    record HeightGuideV2(
            BoundConstraintFieldV2 field,
            TerrainIntentV2.Strength strength,
            long toleranceMillionths,
            long minimumMillionths,
            long maximumMillionths
    ) {
        HeightGuideV2 {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(strength, "strength");
            if (field.role() != TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE) {
                throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                        "macro foundation elevation guide requires a HEIGHT_GUIDE field, got " + field.role());
            }
            if (toleranceMillionths < 0 || minimumMillionths > maximumMillionths) {
                throw new IllegalArgumentException("invalid height guide contract bounds");
            }
        }
    }

    /**
     * A foundation producer candidate layer (ADR 0038 D1-1). {@code ownerId} and {@code kind} are the
     * declared feature this layer was compiled from, so a fail-closed rejection names the input the
     * operator wrote rather than an anonymous owner index.
     */
    record ProducerLayer(
            int ownerIndex,
            String ownerId,
            TerrainIntentV2.FeatureKind kind,
            FootprintPredicate footprint,
            IntBinaryOperator mediumAt,
            IntBinaryOperator elevationMillionthsAt
    ) {
        ProducerLayer {
            if (ownerIndex <= BACKGROUND_OWNER_INDEX) {
                throw new IllegalArgumentException("producer ownerIndex must be above the background index");
            }
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(footprint, "footprint");
            Objects.requireNonNull(mediumAt, "mediumAt");
            Objects.requireNonNull(elevationMillionthsAt, "elevationMillionthsAt");
        }
    }

    /**
     * The request's own vertical extent in block-millionths. A producer is an input to generation just
     * as a guide is, so a base elevation the Release could not represent fails closed here instead of
     * being clamped into range downstream.
     */
    record VerticalExtentV2(long minimumMillionths, long maximumMillionths) {
        VerticalExtentV2 {
            if (minimumMillionths > maximumMillionths) {
                throw new IllegalArgumentException("invalid foundation vertical extent");
            }
        }

        static VerticalExtentV2 of(GenerationRequestV2.Bounds bounds) {
            Objects.requireNonNull(bounds, "bounds");
            return new VerticalExtentV2(
                    Math.multiplyExact((long) bounds.minY(), SCALE),
                    Math.multiplyExact((long) bounds.maxY(), SCALE));
        }
    }

    @FunctionalInterface
    interface FootprintPredicate {
        boolean contains(int globalX, int globalZ);
    }

    /**
     * Resolved coherent detail (V2-19-12, ADR 0041) for the background owner's base-level cells, one
     * kernel per medium. It only ever adds an offset to the per-medium base level; the medium itself
     * always comes from the mask (ADR 0041 凍結4).
     */
    record BackgroundDetailV2(CoherentDetailKernelV2 land, CoherentDetailKernelV2 water) {
        BackgroundDetailV2 {
            Objects.requireNonNull(land, "land");
            Objects.requireNonNull(water, "water");
        }

        int millionthsAt(int medium, int globalX, int globalZ) {
            return medium == 1
                    ? land.valueMillionthsAt(globalX, globalZ)
                    : water.valueMillionthsAt(globalX, globalZ);
        }
    }

    private final BoundConstraintFieldV2 mask;
    private final HeightGuideV2 heightGuide;
    private final VerticalExtentV2 verticalExtent;
    private final List<ProducerLayer> producers;
    private final int landElevationMillionths;
    private final int waterElevationMillionths;
    private final BackgroundDetailV2 detail;
    private final int waterLevelMillionths;

    MacroFoundationV2(
            BoundConstraintFieldV2 mask,
            GenerationRequestV2.FoundationBaseLevels baseLevels,
            VerticalExtentV2 verticalExtent,
            List<ProducerLayer> producers
    ) {
        this(mask, Optional.empty(), baseLevels, verticalExtent, producers);
    }

    MacroFoundationV2(
            BoundConstraintFieldV2 mask,
            Optional<HeightGuideV2> heightGuide,
            GenerationRequestV2.FoundationBaseLevels baseLevels,
            VerticalExtentV2 verticalExtent,
            List<ProducerLayer> producers
    ) {
        this(mask, heightGuide, baseLevels, verticalExtent, producers, Optional.empty(), 0);
    }

    MacroFoundationV2(
            BoundConstraintFieldV2 mask,
            Optional<HeightGuideV2> heightGuide,
            GenerationRequestV2.FoundationBaseLevels baseLevels,
            VerticalExtentV2 verticalExtent,
            List<ProducerLayer> producers,
            Optional<BackgroundDetailV2> detail,
            int waterLevelMillionths
    ) {
        this.mask = Objects.requireNonNull(mask, "mask");
        this.heightGuide = Objects.requireNonNull(heightGuide, "heightGuide").orElse(null);
        Objects.requireNonNull(baseLevels, "baseLevels");
        this.verticalExtent = Objects.requireNonNull(verticalExtent, "verticalExtent");
        this.producers = List.copyOf(Objects.requireNonNull(producers, "producers"));
        this.detail = Objects.requireNonNull(detail, "detail").orElse(null);
        this.waterLevelMillionths = waterLevelMillionths;
        Set<Integer> ownerIndexes = new LinkedHashSet<>();
        for (ProducerLayer producer : this.producers) {
            if (!ownerIndexes.add(producer.ownerIndex())) {
                throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                        "two foundation producers share owner index " + producer.ownerIndex());
            }
        }
        if (mask.role() != TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                    "macro foundation requires a LAND_WATER_MASK field, got " + mask.role());
        }
        if (this.heightGuide != null
                && (this.heightGuide.field().width() != mask.width()
                || this.heightGuide.field().length() != mask.length())) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.DIMENSIONS_INVALID,
                    "the height guide and the land-water mask cover different domains");
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

    /** The bound land-water mask, so a consumer can record the binding and source it honored. */
    BoundConstraintFieldV2 maskField() {
        return mask;
    }

    /** The bound elevation guide, empty when the request declares none (V2-19-06). */
    Optional<HeightGuideV2> heightGuide() {
        return Optional.ofNullable(heightGuide);
    }

    /**
     * Effective foundation owner index at one cell — exactly one after resolution (ADR 0038 D1-4).
     * Producer footprints replace the background declaratively; two producers overlapping without a
     * declared interaction, and cells no candidate covers, fail closed.
     */
    int effectiveOwnerIndexAt(int globalX, int globalZ) {
        ProducerLayer effective = effectiveProducerAt(globalX, globalZ);
        if (effective != null) {
            requireProducerHonorsDeclaredInput(effective, globalX, globalZ);
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

    /** The producer owning a cell, or {@code null} where the background owns it. */
    private ProducerLayer effectiveProducerAt(int globalX, int globalZ) {
        ProducerLayer effective = null;
        for (ProducerLayer producer : producers) {
            if (!producer.footprint().contains(globalX, globalZ)) {
                continue;
            }
            if (effective != null) {
                throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.UNDECLARED_OVERLAP,
                        "overlapping foundation producers have no merge contract at "
                                + globalX + ',' + globalZ + " (" + effective.ownerId() + ", "
                                + producer.ownerId() + ")");
            }
            effective = producer;
        }
        return effective;
    }

    /**
     * Fail-closed producer checks, evaluated on every cell a producer owns (V2-19-07). A producer is a
     * declared input, so each contradiction with another declared input is rejected here rather than
     * resolved by precedence: the HARD mask stays the land-water authority (ADR 0038 D2-3), the
     * request's vertical extent bounds the base elevation, and a HARD elevation guide over the same
     * cell is a second HARD source that must agree within the binding's tolerance (AGENTS.md §7).
     */
    private void requireProducerHonorsDeclaredInput(ProducerLayer producer, int globalX, int globalZ) {
        int medium = requireValidMedium(
                producer.mediumAt().applyAsInt(globalX, globalZ), globalX, globalZ);
        int declared = mask.valueAt(globalX, globalZ);
        if ((declared == 0 || declared == 1) && declared != medium) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                    RULE_PRODUCER_MASK_MEDIUM_CONFLICT + ": foundation producer " + producer.ownerId()
                            + " claims a medium the HARD land-water mask contradicts at "
                            + globalX + ',' + globalZ);
        }
        int elevation = producer.elevationMillionthsAt().applyAsInt(globalX, globalZ);
        if (elevation < verticalExtent.minimumMillionths()
                || elevation > verticalExtent.maximumMillionths()) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                    RULE_PRODUCER_ELEVATION_OUT_OF_CONTRACT + ": foundation producer "
                            + producer.ownerId() + " places " + globalX + ',' + globalZ
                            + " outside the request's vertical extent");
        }
        if (heightGuide == null || heightGuide.strength() != TerrainIntentV2.Strength.HARD) {
            return;
        }
        int desired = desiredHeightMillionthsAt(globalX, globalZ);
        if (desired != NO_HEIGHT
                && Math.abs((long) elevation - desired) > heightGuide.toleranceMillionths()) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                    RULE_HEIGHT_GUIDE_PRODUCER_CONFLICT + ": foundation producer " + producer.ownerId()
                            + " and the HARD height guide declare different heights at "
                            + globalX + ',' + globalZ);
        }
    }

    /**
     * Effective provisional base elevation (block-millionths) at one cell. A producer owns the
     * elevation of every cell in its footprint; on the background owner the declared
     * {@code HEIGHT_GUIDE} wins wherever it specifies a value and the per-medium base level fills the
     * rest (ADR 0038 D2-2, priority fixed by V2-19-06). A SOFT guide over a producer cell yields to the
     * producer — the cell's effective owner — and the difference is recorded as the residual; a HARD
     * one is a contradiction and was already rejected by the owner resolution above.
     */
    int elevationMillionthsAt(int globalX, int globalZ) {
        int owner = effectiveOwnerIndexAt(globalX, globalZ);
        if (owner != BACKGROUND_OWNER_INDEX) {
            return producerByIndex(owner).elevationMillionthsAt().applyAsInt(globalX, globalZ);
        }
        int guided = desiredHeightMillionthsAt(globalX, globalZ);
        if (guided != NO_HEIGHT) {
            return guided;
        }
        int medium = mediumAt(globalX, globalZ);
        int base = medium == 1 ? landElevationMillionths : waterElevationMillionths;
        if (detail == null) {
            return base;
        }
        // V2-19-12: coherent detail replaces the flat per-medium base level here — the one cell class
        // ADR 0041 D2 allows. Guide cells returned above, producer cells returned before this method's
        // base branch, and modifier cells never reach the background path, so no other declared height
        // is touched. |detail| ≤ amplitude and the declaration-time checks keep base ± amplitude in
        // contract; the per-cell check is defence in depth (ADR 0041 D5).
        int elevation = Math.addExact(base, detail.millionthsAt(medium, globalX, globalZ));
        requireDetailWithinContract(elevation, medium, globalX, globalZ);
        return elevation;
    }

    /**
     * Defence-in-depth for a detailed background cell (V2-19-12, ADR 0041 D5). The declaration-time
     * checks already bound {@code base ± amplitude}, so a violation here means the kernel or the datum
     * disagreed with the request; it fails closed rather than clamping.
     */
    private void requireDetailWithinContract(int elevation, int medium, int globalX, int globalZ) {
        if (elevation < verticalExtent.minimumMillionths()
                || elevation > verticalExtent.maximumMillionths()) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                    RULE_DETAIL_OUT_OF_CONTRACT + ": detailed background elevation at " + globalX + ','
                            + globalZ + " leaves the request's vertical extent");
        }
        if (medium == 1 && elevation < waterLevelMillionths) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                    RULE_DETAIL_OUT_OF_CONTRACT + ": detail sank a land surface below the water level at "
                            + globalX + ',' + globalZ);
        }
        if (medium == 0 && elevation > waterLevelMillionths - SCALE) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                    RULE_DETAIL_OUT_OF_CONTRACT + ": detail raised a sea bed into the water surface at "
                            + globalX + ',' + globalZ);
        }
    }

    /**
     * The guide's desired elevation at one cell in block-millionths, or {@link #NO_HEIGHT} when no
     * guide is declared or the guide marks the cell no-data. A value outside the request's vertical
     * extent is rejected here — the single place the guide is read — so neither generation nor the
     * published desired sidecar can carry an unrepresentable height.
     */
    int desiredHeightMillionthsAt(int globalX, int globalZ) {
        if (heightGuide == null) {
            return NO_HEIGHT;
        }
        int value = heightGuide.field().valueAt(globalX, globalZ);
        if (value == NO_HEIGHT) {
            return NO_HEIGHT;
        }
        if (value < heightGuide.minimumMillionths() || value > heightGuide.maximumMillionths()) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                    RULE_HEIGHT_GUIDE_OUT_OF_CONTRACT + ": height guide value at " + globalX + ','
                            + globalZ + " lies outside the request's vertical extent");
        }
        return value;
    }

    /**
     * Fail-closed check for a cell a surface modifier claims (V2-19-06). A HARD guide and a modifier
     * are two declared sources for the same cell's height; if they disagree beyond the binding's
     * declared tolerance the export is rejected rather than one of them silently losing (AGENTS.md
     * §7). A SOFT guide yields to the modifier and the difference is reported as the residual.
     */
    void requireModifierHonorsHeightGuide(int globalX, int globalZ, int composedMillionths) {
        if (heightGuide == null || heightGuide.strength() != TerrainIntentV2.Strength.HARD) {
            return;
        }
        int desired = desiredHeightMillionthsAt(globalX, globalZ);
        if (desired == NO_HEIGHT) {
            return;
        }
        if (Math.abs((long) composedMillionths - desired) > heightGuide.toleranceMillionths()) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                    RULE_HEIGHT_GUIDE_MODIFIER_CONFLICT + ": a surface modifier and the HARD height "
                            + "guide declare different heights at " + globalX + ',' + globalZ);
        }
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
