package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.ConstraintCompilationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.binding.BoundConstraintFieldV2;
import com.github.nankotsu029.landformcraft.core.v2.binding.ConstraintMapFieldBindingV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.FoundationSliceException;
import com.github.nankotsu029.landformcraft.core.v2.foundation.PlainPlanCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.foundation.SurfaceFoundationFailureCodeV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapDecodeLimits;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapInputException;
import com.github.nankotsu029.landformcraft.generator.v2.detail.CoherentDetailKernelV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.plain.PlainGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlainPlanV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Macro foundation production stage of the surface export spine (V2-18-09, ADR 0038 D5-1).
 *
 * <p>Runs before feature composition and turns the explicit foundation input into the
 * {@link MacroFoundationV2} background candidate: the HARD {@code LAND_WATER_MASK} reference and the
 * optional {@code HEIGHT_GUIDE} reference are resolved through the permanent V2-18-06 binding (secure
 * resolve → digest → decode → canonical XZ registration), and the provisional elevation comes from
 * the guide where it specifies a value and from the request's declared per-medium base levels
 * everywhere else. Nothing is inferred: a request without a complete explicit foundation input — a
 * HARD mask binding <em>and</em> declared base levels — resolves to empty, and the pipeline keeps the
 * legacy surface-baseline path for it (ADR 0038 D8-2), so a working production path always exists
 * while V2-18-10 owns the later fail-closed coverage promotion.</p>
 *
 * <p><b>Role cardinality (V2-19-06).</b> The surface path used to demand exactly one constraint map,
 * which made the second explicit elevation source ADR 0038 D2-2 allows impossible to declare. The
 * requirement is now per role — exactly one HARD {@code LAND_WATER_MASK}, at most one
 * {@code HEIGHT_GUIDE} — and a role with no consumer on this path ({@code ZONE_LABEL_MAP}) is
 * rejected rather than accepted and ignored.</p>
 *
 * <p><b>Producer tier (V2-19-07).</b> Every declared feature of a wired producer kind
 * ({@link #WIRED_PRODUCER_KINDS}) is compiled here into one {@link MacroFoundationV2.ProducerLayer}
 * that replaces the background inside its own footprint (ADR 0038 D1-3). {@code PLAIN} is the first:
 * its plan comes from the same {@link PlainPlanCompilerV2} the V2-9 slice uses and its raster from the
 * existing {@link PlainGeneratorV2}, so this Task adds a wiring tier rather than a second generator.
 * Layers are built in feature-id order, so the owner indexes do not depend on the order features
 * happen to appear in the intent. A kind that is not wired here is not a producer: it composes as a
 * surface modifier or is rejected earlier by dispatch, and no kind may reach the block stream through
 * an unwired plan.</p>
 *
 * <p><b>Elevation datum.</b> A {@code PLAIN} declares {@code baseElevationAboveDatumBlocks}, and this
 * stage fixes the datum to the request's water level — one explicit rule, applied to every value. The
 * offline ADR 0037 adapter instead guesses per value (in-range means absolute Y, out-of-range means
 * water-relative) and clamps what does not fit; the production tier rejects an out-of-extent elevation
 * instead ({@code v2.foundation.producer-elevation-out-of-contract}).</p>
 *
 * <p><b>Budget.</b> The stage adds no new limit of its own: each map is admitted by the same
 * {@link ConstraintMapDecodeLimits#defaults()} envelope as before (per-source and total source bytes,
 * dimension, aspect, pixel and decoded-sample ceilings), and the request's own
 * {@code constraintMapBudget} already bounds the declared set — count, pixels and decoded bytes —
 * when the request is constructed. The foundation therefore holds at most two decoded rasters, whose
 * combined residency is bounded by twice the existing decoded-sample ceiling.</p>
 */
final class MacroFoundationStageV2 {
    /**
     * FeatureKinds this stage can build a foundation producer for (ADR 0038 D4 foundation-eligible
     * kinds, wired one leaf at a time). {@code V2-15-22} / {@code V2-15-17} / {@code V2-15-23} extend
     * it for {@code HILL_RANGE} / {@code MOUNTAIN_RANGE} / {@code VALLEY} under the same Acceptance,
     * and a drift guard keeps it aligned with the publicly dispatchable foundation kinds.
     */
    static final Set<TerrainIntentV2.FeatureKind> WIRED_PRODUCER_KINDS =
            Collections.unmodifiableSet(EnumSet.of(TerrainIntentV2.FeatureKind.PLAIN));

    /** Land in the canonical land-water medium encoding; a {@code PLAIN} is terrestrial by contract. */
    private static final int LAND_MEDIUM = 1;

    private final ConstraintMapFieldBindingV2 binding = new ConstraintMapFieldBindingV2();
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final PlainPlanCompilerV2 plainCompiler = new PlainPlanCompilerV2();

    /**
     * Resolves the macro foundation when the request carries the complete explicit foundation
     * input. Decodes map bytes, so callers must already be off the Paper main thread.
     */
    Optional<MacroFoundationV2> resolve(
            GenerationRequestV2 request,
            Path requestSource,
            TerrainIntentV2 intent,
            CancellationToken token
    ) throws IOException {
        return bindInputs(request, requestSource, intent, token)
                .map(inputs -> resolve(inputs, request, intent));
    }

    /**
     * Decodes the declared foundation maps once (V2-19-14). The bound rasters depend only on the
     * intent's {@code mapReferences}, which the reconcile pre-pass never touches, so the pipeline can
     * read the HARD mask <em>before</em> the pre-pass and still build the foundation from the
     * reconciled intent afterwards without decoding anything twice.
     *
     * <p>Empty means "no explicit foundation input": the request declares no per-medium base levels or
     * the intent carries no HARD {@code LAND_WATER_MASK} binding, and the legacy surface-baseline path
     * applies (ADR 0038 D8-2).</p>
     */
    Optional<FoundationInputsV2> bindInputs(
            GenerationRequestV2 request,
            Path requestSource,
            TerrainIntentV2 intent,
            CancellationToken token
    ) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(requestSource, "requestSource");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(token, "token");
        Optional<TerrainIntentV2.ConstraintMapBinding> maskBinding = hardLandWaterBinding(intent);
        if (maskBinding.isEmpty() || request.foundationBaseLevels().isEmpty()) {
            return Optional.empty();
        }
        FoundationInputRolesV2 roles = FoundationInputRolesV2.of(intent);
        token.throwIfCancellationRequested();
        BoundConstraintFieldV2 mask = bind(request, requestSource, roles.mask(), token);
        Optional<MacroFoundationV2.HeightGuideV2> guide = Optional.empty();
        if (roles.heightGuide().isPresent()) {
            token.throwIfCancellationRequested();
            TerrainIntentV2.ConstraintMapBinding guideBinding = roles.heightGuide().orElseThrow();
            guide = Optional.of(new MacroFoundationV2.HeightGuideV2(
                    bind(request, requestSource, guideBinding, token),
                    guideBinding.strength(),
                    Math.multiplyExact((long) guideBinding.toleranceBlocks(), MacroFoundationV2.SCALE),
                    Math.multiplyExact((long) request.bounds().minY(), MacroFoundationV2.SCALE),
                    Math.multiplyExact((long) request.bounds().maxY(), MacroFoundationV2.SCALE)));
        }
        return Optional.of(new FoundationInputsV2(mask, guide));
    }

    /**
     * Builds the foundation from already bound inputs and the intent whose producer features it must
     * honor. Pure: no I/O, no decoding — the producer layers are the only part that depends on the
     * intent's features, which is why V2-19-14 can run the reconcile pre-pass between the two halves.
     */
    MacroFoundationV2 resolve(
            FoundationInputsV2 inputs,
            GenerationRequestV2 request,
            TerrainIntentV2 intent
    ) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(intent, "intent");
        return new MacroFoundationV2(
                inputs.mask(), inputs.heightGuide(), request.foundationBaseLevels().orElseThrow(),
                MacroFoundationV2.VerticalExtentV2.of(request.bounds()),
                producerLayers(request, intent),
                backgroundDetail(request),
                Math.multiplyExact(request.bounds().waterLevel(), MacroFoundationV2.SCALE));
    }

    /** The decoded foundation maps of one run: the HARD land-water mask and the optional guide. */
    record FoundationInputsV2(
            BoundConstraintFieldV2 mask,
            Optional<MacroFoundationV2.HeightGuideV2> heightGuide
    ) {
        FoundationInputsV2 {
            Objects.requireNonNull(mask, "mask");
            Objects.requireNonNull(heightGuide, "heightGuide");
        }
    }

    /**
     * Resolves the optional coherent detail (V2-19-12, ADR 0041) into one kernel per medium. Absent
     * detail leaves the background elevation as the flat per-medium base level, so the whole path is
     * byte-identical to a pre-V2-19-12 request.
     */
    private static Optional<MacroFoundationV2.BackgroundDetailV2> backgroundDetail(
            GenerationRequestV2 request
    ) {
        if (request.foundationDetail().isEmpty()) {
            return Optional.empty();
        }
        GenerationRequestV2.FoundationDetail declared = request.foundationDetail().get();
        long seed = request.generation().globalSeed();
        return Optional.of(new MacroFoundationV2.BackgroundDetailV2(
                CoherentDetailKernelV2.forMedium(seed, "land", declared.landAmplitudeBlocks(),
                        declared.wavelengthBlocks(), declared.octaves()),
                CoherentDetailKernelV2.forMedium(seed, "water", declared.waterAmplitudeBlocks(),
                        declared.wavelengthBlocks(), declared.octaves())));
    }

    /**
     * Compiles one producer layer per declared feature of a wired producer kind, in feature-id order
     * (V2-19-07). A plan compile rejection is a contract violation of the declared intent, so it is
     * surfaced with the compiler's own rule id instead of escaping as a generator runtime exception.
     */
    private List<MacroFoundationV2.ProducerLayer> producerLayers(
            GenerationRequestV2 request,
            TerrainIntentV2 intent
    ) {
        List<TerrainIntentV2.Feature> declared = intent.features().stream()
                .filter(feature -> WIRED_PRODUCER_KINDS.contains(feature.kind()))
                .sorted(Comparator.comparing(TerrainIntentV2.Feature::id))
                .toList();
        if (declared.isEmpty()) {
            return List.of();
        }
        GenerationRequestV2.Bounds bounds = request.bounds();
        WorldBlueprintV2.Bounds planBounds = new WorldBlueprintV2.Bounds(
                bounds.width(), bounds.length(), bounds.minY(), bounds.maxY(), bounds.waterLevel());
        List<MacroFoundationV2.ProducerLayer> layers = new ArrayList<>(declared.size());
        int ownerIndex = MacroFoundationV2.BACKGROUND_OWNER_INDEX;
        for (TerrainIntentV2.Feature feature : declared) {
            ownerIndex++;
            layers.add(switch (feature.kind()) {
                case PLAIN -> plainProducer(feature, intent, planBounds, ownerIndex);
                default -> throw new SurfaceFoundationExceptionV2(
                        SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                        "no foundation producer is wired for kind " + feature.kind());
            });
        }
        return List.copyOf(layers);
    }

    /**
     * One {@code PLAIN} feature as a foundation producer: land everywhere inside the compiled polygon,
     * base elevation from {@link PlainGeneratorV2} measured above the request's water level. The
     * generator is only ever sampled inside the footprint the same generator defines.
     */
    private MacroFoundationV2.ProducerLayer plainProducer(
            TerrainIntentV2.Feature feature,
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            int ownerIndex
    ) {
        PlainPlanV2 plan;
        try {
            plan = codec.sealPlainPlan(plainCompiler.compile(
                    feature, intent, bounds, codec.geometryChecksum(feature.geometry())));
        } catch (FoundationSliceException exception) {
            throw new SurfaceFoundationExceptionV2(SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                    "foundation producer " + feature.id() + " failed to compile ["
                            + exception.ruleId() + "]: " + exception.getMessage(), exception);
        }
        PlainGeneratorV2 generator = new PlainGeneratorV2(plan);
        int datumY = bounds.waterLevel();
        return new MacroFoundationV2.ProducerLayer(
                ownerIndex,
                feature.id(),
                feature.kind(),
                (globalX, globalZ) -> generator.sampleAt(globalX, globalZ).active(),
                (globalX, globalZ) -> LAND_MEDIUM,
                (globalX, globalZ) -> {
                    PlainGeneratorV2.PlainSample sample = generator.sampleAt(globalX, globalZ);
                    if (!sample.active()) {
                        throw new SurfaceFoundationExceptionV2(
                                SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION,
                                "foundation producer " + feature.id() + " was sampled outside its "
                                        + "footprint at " + globalX + ',' + globalZ);
                    }
                    return Math.toIntExact(Math.multiplyExact(
                            (long) Math.addExact(datumY, sample.elevationBlocks()),
                            MacroFoundationV2.SCALE));
                });
    }

    private BoundConstraintFieldV2 bind(
            GenerationRequestV2 request,
            Path requestSource,
            TerrainIntentV2.ConstraintMapBinding reference,
            CancellationToken token
    ) throws IOException {
        try {
            return binding.bind(requestSource, request, reference,
                    ConstraintMapDecodeLimits.defaults(), token);
        } catch (ConstraintCompilationExceptionV2 exception) {
            throw new IOException("macro foundation " + role(reference) + " binding failed ["
                    + exception.code() + "]: " + exception.getMessage(), exception);
        } catch (ConstraintMapInputException exception) {
            throw new IOException("macro foundation " + role(reference) + " resolution failed ["
                    + exception.code() + "]: " + exception.getMessage(), exception);
        }
    }

    private static String role(TerrainIntentV2.ConstraintMapBinding reference) {
        return reference.role().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private static Optional<TerrainIntentV2.ConstraintMapBinding> hardLandWaterBinding(
            TerrainIntentV2 intent
    ) {
        return intent.mapReferences().stream()
                .filter(binding -> binding.role() == TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK)
                .filter(binding -> binding.strength() == TerrainIntentV2.Strength.HARD)
                .findFirst();
    }

    /**
     * The foundation input bindings of one intent, classified by role and validated for cardinality
     * (V2-19-06). Shared by the stage and by the export pipeline, which has to write one applied
     * binding per honored map into the constraint field index.
     */
    record FoundationInputRolesV2(
            TerrainIntentV2.ConstraintMapBinding mask,
            Optional<TerrainIntentV2.ConstraintMapBinding> heightGuide
    ) {
        FoundationInputRolesV2 {
            Objects.requireNonNull(mask, "mask");
            Objects.requireNonNull(heightGuide, "heightGuide");
        }

        static FoundationInputRolesV2 of(TerrainIntentV2 intent) {
            List<TerrainIntentV2.ConstraintMapBinding> masks = new ArrayList<>();
            List<TerrainIntentV2.ConstraintMapBinding> guides = new ArrayList<>();
            for (TerrainIntentV2.ConstraintMapBinding binding : intent.mapReferences()) {
                switch (binding.role()) {
                    case LAND_WATER_MASK -> masks.add(binding);
                    case HEIGHT_GUIDE -> guides.add(binding);
                    case ZONE_LABEL_MAP -> throw reject(
                            "surface-2_5d export has no consumer for a ZONE_LABEL_MAP binding ("
                                    + binding.id() + ")");
                }
            }
            if (masks.size() != 1) {
                throw reject("surface-2_5d export requires exactly one LAND_WATER_MASK map binding, got "
                        + masks.size());
            }
            if (guides.size() > 1) {
                throw reject("surface-2_5d export accepts at most one HEIGHT_GUIDE map binding, got "
                        + guides.size());
            }
            if (masks.getFirst().strength() != TerrainIntentV2.Strength.HARD) {
                throw reject("the macro foundation land-water mask binding must be HARD");
            }
            return new FoundationInputRolesV2(masks.getFirst(),
                    guides.isEmpty() ? Optional.empty() : Optional.of(guides.getFirst()));
        }

        /** Every declared binding, mask first, in the order the constraint field index consumes them. */
        List<TerrainIntentV2.ConstraintMapBinding> all() {
            List<TerrainIntentV2.ConstraintMapBinding> bindings = new ArrayList<>(2);
            bindings.add(mask);
            heightGuide.ifPresent(bindings::add);
            return List.copyOf(bindings);
        }

        private static SurfaceFoundationExceptionV2 reject(String message) {
            return new SurfaceFoundationExceptionV2(
                    SurfaceFoundationFailureCodeV2.CONTRACT_VIOLATION, message);
        }
    }
}
