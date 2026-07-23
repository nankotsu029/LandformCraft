package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.v2.DiagnosticGateContractV2;
import com.github.nankotsu029.landformcraft.generator.v2.composition.coast.CoastalTransitionModuleV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SurfaceFoundationPlanV2;
import com.github.nankotsu029.landformcraft.validation.v2.coast.CoastalFieldSamplerV2;
import com.github.nankotsu029.landformcraft.validation.v2.target.TargetDrivenValidatorV2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * V2-18-02 report-only intent contribution / coverage diagnostic.
 *
 * <p>The V2-18 macro foundation audit found that map-level HARD input ({@code LAND_WATER_MASK},
 * {@code EDGE_CLASSIFICATION}, and HARD relations targeting contract-only kinds) is compiled but
 * never consumed by any validator or export gate, and that the true feature-non-owned cell coverage
 * (audit: 73.0%) can only be established from a single run's active-contributor / owner-index data,
 * not from a two-baseline diff. This type makes both facts directly inspectable without changing any
 * generation, validation, or gate behaviour: it only reports.</p>
 *
 * <p><b>Manifest / diagnostic container byte impact (explicit per the V2-18-02 Acceptance gate):
 * none.</b> This report is never written into the sealed {@code WorldBlueprintV2}, its diagnostic
 * issues, or any published Release manifest/artifact. It exists only as an in-memory sibling of
 * {@link ProductionExportPipelineV2.GeneratedSurface}, surfaced to operators solely through the CLI
 * JSON summary. Field, tile, and block semantic checksums — and every existing sealed byte stream —
 * are therefore provably unaffected by this Task.</p>
 *
 * <p>{@code activeContributorCoverage} counts cells an active coastal contributor claims (the
 * composed owner-index field); {@code surfaceFoundationOwnerCoverage} counts cells with an effective
 * foundation owner (the {@code foundation.owner-index} namespace). Since V2-18-09 wired the macro
 * foundation stage the two diverge exactly as designed: on the foundation path the background owner
 * covers the whole domain (100%) while modifiers still claim only their footprints, and on the
 * legacy baseline path the foundation owner coverage is honestly 0 — a baseline fill is not an
 * owner, which is the audited defect this phase removes.</p>
 */
public record IntentContributionCoverageV2(
        String contractVersion,
        int totalCells,
        int activeContributorCells,
        int activeContributorCoverageMillionths,
        int surfaceFoundationOwnerCells,
        int surfaceFoundationOwnerCoverageMillionths,
        List<TerrainIntentV2.FeatureKind> contractOnlyKinds,
        List<UnconsumedHardConstraint> unconsumedHardConstraints,
        List<UnconsumedHardRelation> unconsumedHardRelations,
        List<UnconsumedHardMapReference> unconsumedHardMapReferences
) {
    public static final String CONTRACT_VERSION = "intent-contribution-coverage-v1";
    private static final int FIXED_SCALE = 1_000_000;

    public IntentContributionCoverageV2 {
        contractVersion = Objects.requireNonNull(contractVersion, "contractVersion");
        if (totalCells < 0 || activeContributorCells < 0 || activeContributorCells > totalCells
                || surfaceFoundationOwnerCells < 0 || surfaceFoundationOwnerCells > totalCells) {
            throw new IllegalArgumentException("intent contribution coverage cell counts are invalid");
        }
        contractOnlyKinds = List.copyOf(Objects.requireNonNull(contractOnlyKinds, "contractOnlyKinds"));
        unconsumedHardConstraints = List.copyOf(
                Objects.requireNonNull(unconsumedHardConstraints, "unconsumedHardConstraints"));
        unconsumedHardRelations = List.copyOf(
                Objects.requireNonNull(unconsumedHardRelations, "unconsumedHardRelations"));
        unconsumedHardMapReferences = List.copyOf(
                Objects.requireNonNull(unconsumedHardMapReferences, "unconsumedHardMapReferences"));
    }

    /** Backward-compatible overload: no constraint-map role is consumed by the run. */
    public static IntentContributionCoverageV2 compute(
            TerrainIntentV2 intent,
            CoastalFieldSamplerV2 fields,
            List<TerrainIntentV2.FeatureKind> pipelineContractOnlyKinds,
            DiagnosticGateContractV2 gateContract
    ) {
        return compute(intent, fields, pipelineContractOnlyKinds, gateContract, Set.of());
    }

    /**
     * Computes the report from one already-generated coastal run. {@code fields} must be the same
     * sampler the pipeline just produced for {@code intent}, so the owner-index scan reflects a
     * single run rather than a two-baseline diff. {@code consumedMapRoles} lists the constraint-map
     * roles the run actually resolved into generation (V2-18-09: the macro foundation stage consumes
     * {@code LAND_WATER_MASK}), so honored references stop being reported as unconsumed.
     */
    public static IntentContributionCoverageV2 compute(
            TerrainIntentV2 intent,
            CoastalFieldSamplerV2 fields,
            List<TerrainIntentV2.FeatureKind> pipelineContractOnlyKinds,
            DiagnosticGateContractV2 gateContract,
            Set<TerrainIntentV2.ConstraintMapRole> consumedMapRoles
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(pipelineContractOnlyKinds, "pipelineContractOnlyKinds");
        Objects.requireNonNull(gateContract, "gateContract");
        Objects.requireNonNull(consumedMapRoles, "consumedMapRoles");

        int width = fields.width();
        int length = fields.length();
        int totalCells = Math.multiplyExact(width, length);
        int ownedCells = 0;
        int foundationOwnedCells = 0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                if (fields.valueAt(CoastalTransitionModuleV2.OWNER_INDEX_FIELD_ID, x, z) != 0) {
                    ownedCells++;
                }
                // V2-18-09: the foundation owner metric now reads the foundation owner-index
                // namespace. On the legacy baseline path no cell has a foundation owner (that is
                // the audited defect), so the two metrics diverge exactly as V2-18-02 anticipated:
                // active contributors are surface modifiers, not foundation owners (ADR 0038 D3).
                if (fields.valueAt(SurfaceFoundationPlanV2.OWNER_INDEX_FIELD_ID, x, z) > 0) {
                    foundationOwnedCells++;
                }
            }
        }
        int coverageMillionths = totalCells == 0 ? 0
                : Math.toIntExact(Math.floorDiv((long) ownedCells * FIXED_SCALE, totalCells));
        int foundationCoverageMillionths = totalCells == 0 ? 0
                : Math.toIntExact(Math.floorDiv((long) foundationOwnedCells * FIXED_SCALE, totalCells));

        Map<String, TerrainIntentV2.FeatureKind> featureKinds = new HashMap<>();
        for (TerrainIntentV2.Feature feature : intent.features()) {
            featureKinds.put(feature.id(), feature.kind());
        }

        Set<TerrainIntentV2.FeatureKind> contractOnlyPresent = intent.features().stream()
                .map(TerrainIntentV2.Feature::kind)
                .filter(pipelineContractOnlyKinds::contains)
                .collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Enum::name))));

        List<UnconsumedHardConstraint> unconsumedConstraints = new ArrayList<>();
        for (TerrainIntentV2.Constraint constraint : intent.constraints()) {
            if (constraint.strength() != TerrainIntentV2.Strength.HARD) {
                continue;
            }
            // Every HARD MetricRangeConstraint / EdgeClassificationConstraint compiles to a
            // ValidationTargetV2 (rule v2.metric-range / v2.edge-classification, see
            // DiagnosticBlueprintCompilerV2#compileTargets). V2-18-04's target-driven framework now
            // evaluates v2.edge-classification, so only rules without an evaluator remain unconsumed.
            String ruleId = constraint instanceof TerrainIntentV2.MetricRangeConstraint
                    ? "v2.metric-range" : "v2.edge-classification";
            if (TargetDrivenValidatorV2.BUILT_IN_EVALUATED_CONSTRAINT_RULES.contains(ruleId)) {
                continue;
            }
            unconsumedConstraints.add(new UnconsumedHardConstraint(constraint.id(), ruleId, constraint.subject()));
        }

        List<UnconsumedHardRelation> unconsumedRelations = new ArrayList<>();
        for (TerrainIntentV2.Relation relation : intent.relations()) {
            if (relation.strength() != TerrainIntentV2.Strength.HARD) {
                continue;
            }
            TerrainIntentV2.FeatureKind unconnected = unconnectedEndpointKind(relation, featureKinds, gateContract);
            if (unconnected != null) {
                unconsumedRelations.add(new UnconsumedHardRelation(
                        relation.id(), relation.kind(), relation.from(), relation.to(), unconnected));
            }
        }

        List<UnconsumedHardMapReference> unconsumedMapReferences = new ArrayList<>();
        for (TerrainIntentV2.ConstraintMapBinding binding : intent.mapReferences()) {
            if (binding.strength() != TerrainIntentV2.Strength.HARD) {
                continue;
            }
            // V2-18-09 wired the macro foundation stage as the permanent LAND_WATER_MASK consumer
            // (audit item 2a): a role the run resolved into generation is honored, everything else
            // remains reported until its consumer exists.
            if (consumedMapRoles.contains(binding.role())) {
                continue;
            }
            unconsumedMapReferences.add(new UnconsumedHardMapReference(binding.id(), binding.role()));
        }

        return new IntentContributionCoverageV2(
                CONTRACT_VERSION,
                totalCells,
                ownedCells,
                coverageMillionths,
                foundationOwnedCells,
                foundationCoverageMillionths,
                List.copyOf(contractOnlyPresent),
                List.copyOf(unconsumedConstraints),
                List.copyOf(unconsumedRelations),
                List.copyOf(unconsumedMapReferences));
    }

    /** Flattened, JSON-serializable view for the CLI summary (never written to a sealed artifact). */
    public Map<String, Object> toSummaryMap() {
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("contractVersion", contractVersion);
        summary.put("totalCells", totalCells);
        summary.put("activeContributorCells", activeContributorCells);
        summary.put("activeContributorCoverageMillionths", activeContributorCoverageMillionths);
        summary.put("surfaceFoundationOwnerCells", surfaceFoundationOwnerCells);
        summary.put("surfaceFoundationOwnerCoverageMillionths", surfaceFoundationOwnerCoverageMillionths);
        summary.put("contractOnlyKinds", contractOnlyKinds.stream().map(Enum::name).toList());
        summary.put("unconsumedHardConstraints", unconsumedHardConstraints.stream()
                .map(entry -> Map.of(
                        "constraintId", entry.constraintId(),
                        "ruleId", entry.ruleId(),
                        "subject", entry.subject()))
                .toList());
        summary.put("unconsumedHardRelations", unconsumedHardRelations.stream()
                .map(entry -> Map.of(
                        "relationId", entry.relationId(),
                        "kind", entry.kind().name(),
                        "from", entry.from(),
                        "to", entry.to(),
                        "unconnectedFeatureKind", entry.unconnectedFeatureKind().name()))
                .toList());
        summary.put("unconsumedHardMapReferences", unconsumedHardMapReferences.stream()
                .map(entry -> Map.of(
                        "mapReferenceId", entry.mapReferenceId(),
                        "role", entry.role().name()))
                .toList());
        return java.util.Collections.unmodifiableMap(summary);
    }

    private static TerrainIntentV2.FeatureKind unconnectedEndpointKind(
            TerrainIntentV2.Relation relation,
            Map<String, TerrainIntentV2.FeatureKind> featureKinds,
            DiagnosticGateContractV2 gateContract
    ) {
        TerrainIntentV2.FeatureKind from = endpointKind(relation.from(), featureKinds);
        if (from != null && !gateContract.isProductionConnected(from)) {
            return from;
        }
        TerrainIntentV2.FeatureKind to = endpointKind(relation.to(), featureKinds);
        if (to != null && !gateContract.isProductionConnected(to)) {
            return to;
        }
        return null;
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

    /** A HARD user constraint that no validator consumes yet (audit item 2b, V2-18-04 adds a consumer). */
    public record UnconsumedHardConstraint(String constraintId, String ruleId, String subject) {
        public UnconsumedHardConstraint {
            constraintId = Objects.requireNonNull(constraintId, "constraintId");
            ruleId = Objects.requireNonNull(ruleId, "ruleId");
            subject = Objects.requireNonNull(subject, "subject");
        }
    }

    /** A HARD relation whose endpoint feature kind is not production-connected. */
    public record UnconsumedHardRelation(
            String relationId,
            TerrainIntentV2.RelationKind kind,
            String from,
            String to,
            TerrainIntentV2.FeatureKind unconnectedFeatureKind
    ) {
        public UnconsumedHardRelation {
            relationId = Objects.requireNonNull(relationId, "relationId");
            kind = Objects.requireNonNull(kind, "kind");
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
            unconnectedFeatureKind = Objects.requireNonNull(unconnectedFeatureKind, "unconnectedFeatureKind");
        }
    }

    /** A HARD map reference the current production pipeline never resolves (audit item 2a). */
    public record UnconsumedHardMapReference(String mapReferenceId, TerrainIntentV2.ConstraintMapRole role) {
        public UnconsumedHardMapReference {
            mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
            role = Objects.requireNonNull(role, "role");
        }
    }
}
