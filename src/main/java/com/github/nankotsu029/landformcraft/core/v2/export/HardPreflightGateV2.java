package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticGateContractV2;
import com.github.nankotsu029.landformcraft.core.v2.catalog.PublicDispatchReachabilityV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapDecodeLimits;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapInputException;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapPngHeaderV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapSourceSpec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.LoadedConstraintMapSource;
import com.github.nankotsu029.landformcraft.format.v2.constraint.SecureConstraintMapSourceLoader;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.TargetDrivenValidatorV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * V2-18-03 HARD preflight gate for the production export spine.
 *
 * <p>The V2-18 macro foundation audit found that map-level HARD input is compiled but never honored:
 * HARD {@code METRIC_RANGE} / {@code EDGE_CLASSIFICATION} constraints compile to validation targets no
 * evaluator consumes, HARD relations that point at contract-only or otherwise not-production-connected
 * kinds are recorded but never enforced, and HARD {@code LAND_WATER_MASK} references are never
 * resolved (there is no I/O call site reading their bytes on the export spine). V2-18-02 made that
 * state <em>observable</em> (report-only). This gate makes it <em>fail closed</em>: it rejects, before
 * any artifact is generated, an intent that declares a HARD requirement the current engine cannot
 * honor, so a HARD requirement can no longer be silently ignored.</p>
 *
 * <p>Enforcement is unconditional — there is deliberately no override flag. A HARD requirement is
 * either honored by an evaluator / consumer / resolvable source, or it is rejected. SOFT declarations
 * of the same shape are advisory only and are surfaced as warnings, never rejections.</p>
 *
 * <p>The gate performs the minimal map resolution the audit named — existence, digest, dimensions — by
 * reusing the {@link SecureConstraintMapSourceLoader} envelope (secure resolve, PNG signature, byte
 * digest) plus an IHDR dimension check. It deliberately does <em>not</em> decode pixels, register a
 * canonical field, or become the reusable constraint-map binding: that is {@code V2-18-06}. It never
 * writes an artifact and never mutates the intent, so terrain field / tile / block semantic checksums
 * are unaffected.</p>
 */
public final class HardPreflightGateV2 {
    /** A HARD constraint compiles to a validation target rule no evaluator consumes yet. */
    public static final String RULE_HARD_CONSTRAINT_UNEVALUATED = "v2.preflight.hard-constraint-unevaluated";
    /**
     * A HARD relation points at an endpoint kind no production route consumes (contract-only, or not
     * publicly dispatchable at all — V2-15-12 excludes any {@code OFFLINE_PRODUCTION}-routed kind).
     */
    public static final String RULE_HARD_RELATION_UNCONSUMED = "v2.preflight.hard-relation-unconsumed";
    /** A HARD map reference cannot be resolved: missing file, digest mismatch, or dimension mismatch. */
    public static final String RULE_MAP_REFERENCE_UNRESOLVED = "v2.preflight.map-reference-unresolved";

    private static final String METRIC_RANGE_RULE = "v2.metric-range";
    private static final String EDGE_CLASSIFICATION_RULE = "v2.edge-classification";

    /**
     * Compiled constraint rules that some validator already evaluates. Sourced from the target-driven
     * validation framework (V2-18-04), which now evaluates {@code v2.edge-classification}; a HARD EDGE
     * constraint therefore stops being rejected here pre-generation because the requirement became
     * honorable — the framework instead measures it after generation and gates a HARD violation there.
     * {@code v2.metric-range} still has no evaluator, so HARD METRIC_RANGE constraints stay rejected.
     */
    private static final Set<String> EVALUATED_CONSTRAINT_RULES =
            TargetDrivenValidatorV2.BUILT_IN_EVALUATED_CONSTRAINT_RULES;

    private final DiagnosticGateContractV2 gateContract;
    private final PublicDispatchReachabilityV2 reachability;
    private final SecureConstraintMapSourceLoader mapLoader;
    private final ConstraintMapDecodeLimits mapLimits;

    public HardPreflightGateV2() {
        this(DiagnosticGateContractV2.builtIn());
    }

    public HardPreflightGateV2(DiagnosticGateContractV2 gateContract) {
        this(gateContract, PublicDispatchReachabilityV2.builtIn(),
                new SecureConstraintMapSourceLoader(), ConstraintMapDecodeLimits.defaults());
    }

    HardPreflightGateV2(
            DiagnosticGateContractV2 gateContract,
            PublicDispatchReachabilityV2 reachability,
            SecureConstraintMapSourceLoader mapLoader,
            ConstraintMapDecodeLimits mapLimits
    ) {
        this.gateContract = Objects.requireNonNull(gateContract, "gateContract");
        this.reachability = Objects.requireNonNull(reachability, "reachability");
        this.mapLoader = Objects.requireNonNull(mapLoader, "mapLoader");
        this.mapLimits = Objects.requireNonNull(mapLimits, "mapLimits");
    }

    /**
     * Rejects the export before generation when the intent declares an un-honorable HARD requirement.
     * The exception is an {@link IOException} carrying every rejection so callers surface the stable
     * rule ids rather than a single opaque failure.
     */
    public void requireHonorable(
            GenerationRequestV2 request,
            Path requestPath,
            TerrainIntentV2 intent,
            CancellationToken token
    ) throws IOException {
        HardPreflightResultV2 result = evaluate(request, requestPath, intent, token);
        if (!result.rejections().isEmpty()) {
            throw new HardPreflightRejectedV2(result);
        }
    }

    /**
     * Classifies every HARD (rejection) and SOFT (warning) requirement the current engine cannot
     * honor. Reads constraint-map bytes to resolve HARD map references; heavy work must therefore run
     * off the Paper main thread, which the export application services already guarantee.
     */
    public HardPreflightResultV2 evaluate(
            GenerationRequestV2 request,
            Path requestPath,
            TerrainIntentV2 intent,
            CancellationToken token
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(requestPath, "requestPath");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(token, "token");
        token.throwIfCancellationRequested();

        List<HardPreflightResultV2.Finding> rejections = new ArrayList<>();
        List<HardPreflightResultV2.Finding> warnings = new ArrayList<>();

        classifyConstraints(intent, rejections, warnings);
        classifyRelations(intent, rejections, warnings);
        classifyMapReferences(request, requestPath, intent, token, rejections, warnings);

        return new HardPreflightResultV2(rejections, warnings);
    }

    private static void classifyConstraints(
            TerrainIntentV2 intent,
            List<HardPreflightResultV2.Finding> rejections,
            List<HardPreflightResultV2.Finding> warnings
    ) {
        for (TerrainIntentV2.Constraint constraint : intent.constraints()) {
            String rule = compiledConstraintRule(constraint);
            if (rule == null || EVALUATED_CONSTRAINT_RULES.contains(rule)) {
                continue;
            }
            HardPreflightResultV2.Finding finding = new HardPreflightResultV2.Finding(
                    RULE_HARD_CONSTRAINT_UNEVALUATED,
                    HardPreflightResultV2.Category.HARD_CONSTRAINT_UNEVALUATED,
                    constraint.id(),
                    "constraint compiles to rule " + rule + " but no evaluator consumes it yet");
            bucket(constraint.strength(), finding, rejections, warnings);
        }
    }

    private void classifyRelations(
            TerrainIntentV2 intent,
            List<HardPreflightResultV2.Finding> rejections,
            List<HardPreflightResultV2.Finding> warnings
    ) {
        Map<String, TerrainIntentV2.FeatureKind> featureKinds = new HashMap<>();
        for (TerrainIntentV2.Feature feature : intent.features()) {
            featureKinds.put(feature.id(), feature.kind());
        }
        for (TerrainIntentV2.Relation relation : intent.relations()) {
            TerrainIntentV2.FeatureKind unconnected = unconnectedEndpointKind(relation, featureKinds);
            if (unconnected == null) {
                continue;
            }
            HardPreflightResultV2.Finding finding = new HardPreflightResultV2.Finding(
                    RULE_HARD_RELATION_UNCONSUMED,
                    HardPreflightResultV2.Category.HARD_RELATION_UNCONSUMED,
                    relation.id(),
                    "relation endpoint kind " + unconnected.name()
                            + " is not production-connected, so no consumer enforces the relation");
            bucket(relation.strength(), finding, rejections, warnings);
        }
    }

    private void classifyMapReferences(
            GenerationRequestV2 request,
            Path requestPath,
            TerrainIntentV2 intent,
            CancellationToken token,
            List<HardPreflightResultV2.Finding> rejections,
            List<HardPreflightResultV2.Finding> warnings
    ) {
        Map<String, GenerationRequestV2.ConstraintMapSource> sources = new HashMap<>();
        for (GenerationRequestV2.ConstraintMapSource source : request.constraintMaps()) {
            sources.put(source.sourceId(), source);
        }
        for (TerrainIntentV2.ConstraintMapBinding binding : intent.mapReferences()) {
            String detail = resolveMapReference(sources.get(binding.sourceId()), requestPath, token);
            if (detail == null) {
                continue;
            }
            HardPreflightResultV2.Finding finding = new HardPreflightResultV2.Finding(
                    RULE_MAP_REFERENCE_UNRESOLVED,
                    HardPreflightResultV2.Category.MAP_REFERENCE_UNRESOLVED,
                    binding.id(),
                    detail);
            bucket(binding.strength(), finding, rejections, warnings);
        }
    }

    /** Returns null when the reference resolves; otherwise a stable, redaction-safe reason. */
    private String resolveMapReference(
            GenerationRequestV2.ConstraintMapSource source,
            Path requestPath,
            CancellationToken token
    ) {
        if (source == null) {
            return "no declared constraint-map source matches the binding sourceId";
        }
        ConstraintMapSourceSpec spec;
        try {
            spec = new ConstraintMapSourceSpec(
                    source.sourceId(), source.file(), source.expectedSha256(),
                    source.expectedWidth(), source.expectedLength());
        } catch (ConstraintMapInputException exception) {
            return exception.code() + ": " + exception.getMessage();
        }
        LoadedConstraintMapSource loaded;
        try {
            loaded = mapLoader.load(requestPath, List.of(spec), mapLimits, token::isCancellationRequested)
                    .getFirst();
        } catch (ConstraintMapInputException exception) {
            return exception.code() + ": " + exception.getMessage();
        }
        return ConstraintMapPngHeaderV2.dimensionMismatch(
                loaded.contentCopy(), source.expectedWidth(), source.expectedLength());
    }

    private static String compiledConstraintRule(TerrainIntentV2.Constraint constraint) {
        if (constraint instanceof TerrainIntentV2.MetricRangeConstraint) {
            return METRIC_RANGE_RULE;
        }
        if (constraint instanceof TerrainIntentV2.EdgeClassificationConstraint) {
            return EDGE_CLASSIFICATION_RULE;
        }
        return null;
    }

    /**
     * The relation is unconsumed when a resolvable endpoint feature's kind has no production
     * consumer at all: neither {@code PRODUCTION_CONNECTED} (the strict, Paper-complete
     * {@link DiagnosticGateContractV2#isProductionConnected} sense the V2-18-02 report also uses) nor
     * an ADR 0039 {@code OFFLINE_PRODUCTION} dispatch route (V2-15-10 onward). A kind on the latter
     * route is genuinely compiled by production code — {@code CanyonPlanCompilerV2}'s HARD
     * {@code WITHIN} resolution is exactly such a consumer for a {@code MEANDERING_RIVER} endpoint —
     * so treating only the narrower, Paper-complete axis as "consumed" would reject an honorable
     * relation the export spine actually enforces.
     */
    private TerrainIntentV2.FeatureKind unconnectedEndpointKind(
            TerrainIntentV2.Relation relation,
            Map<String, TerrainIntentV2.FeatureKind> featureKinds
    ) {
        TerrainIntentV2.FeatureKind from = endpointKind(relation.from(), featureKinds);
        if (from != null && !hasProductionConsumer(from)) {
            return from;
        }
        TerrainIntentV2.FeatureKind to = endpointKind(relation.to(), featureKinds);
        if (to != null && !hasProductionConsumer(to)) {
            return to;
        }
        return null;
    }

    private boolean hasProductionConsumer(TerrainIntentV2.FeatureKind kind) {
        if (gateContract.isProductionConnected(kind)) {
            return true;
        }
        return reachability.entry(kind).reachability()
                == PublicDispatchReachabilityV2.ReachabilityV2.OFFLINE_PRODUCTION;
    }

    private static TerrainIntentV2.FeatureKind endpointKind(
            String endpoint,
            Map<String, TerrainIntentV2.FeatureKind> featureKinds
    ) {
        if (!endpoint.startsWith("feature:")) {
            return null;
        }
        return featureKinds.get(endpoint.substring("feature:".length()));
    }

    private static void bucket(
            TerrainIntentV2.Strength strength,
            HardPreflightResultV2.Finding finding,
            List<HardPreflightResultV2.Finding> rejections,
            List<HardPreflightResultV2.Finding> warnings
    ) {
        if (strength == TerrainIntentV2.Strength.HARD) {
            rejections.add(finding);
        } else {
            warnings.add(finding);
        }
    }
}
