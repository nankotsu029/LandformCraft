package com.github.nankotsu029.landformcraft.validation.v2.conformance;

import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.ValidationTargetV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The heterogeneous set of intent-conformance targets a Blueprint carries (V2-18-07).
 *
 * <p>This is the contract the V2-18 macro-foundation work needs and the pre-V2-18 code lacked: a single
 * place that models desired-raster, aggregate-metric, topology, and geometric targets as distinct kinds
 * ({@link ConformanceTargetV2}), each with the provenance of its desired reference. It is a pure
 * projection of a compiled Blueprint's validation targets plus its declared constraint-map bindings —
 * it holds no generated field values and gates nothing on its own. {@link ConformanceResidualEvaluatorV2}
 * turns it, plus measurements, into a faithful residual per target.</p>
 *
 * <p><b>Desired rasters are optional (V2-18-07 non-scope):</b> a request need not declare a
 * {@code LAND_WATER_MASK}, so {@link #desiredRasterTargets()} may be empty. When one is declared, its
 * provenance origin is {@link ConformanceProvenanceV2.Origin#INPUT_MASK} only if the sealed binding's
 * digest matches the digest the request declared for that source — the honest binding the V2-18-07
 * {@code withLandWaterBinding} fix now writes. A binding that still points at a self-derived digest
 * (a source id with no declared match, or a digest that differs) is recorded as
 * {@link ConformanceProvenanceV2.Origin#SELF_DERIVED} and yields an unconsumed raster residual rather
 * than a vacuous zero.</p>
 */
public record ConformanceTargetSetV2(
        String contractVersion,
        List<ConformanceTargetV2> targets
) {
    public static final String CONTRACT_VERSION = "conformance-target-set-v1";
    /** Canonical land/water contract field the desired raster and actual field are both sampled through. */
    public static final String LAND_WATER_FIELD_ID = BuiltInLandformModuleCatalogV2.CONTRACT_FIELD_ID;
    /**
     * Canonical surface-height contract field of a {@code HEIGHT_GUIDE} desired raster (V2-19-06).
     *
     * <p>Values are block-millionths, matching the published {@code DESIRED_HEIGHT} /
     * {@code ACTUAL_HEIGHT} sidecars a caller samples this id through.</p>
     */
    public static final String HEIGHT_GUIDE_FIELD_ID = "intent.height-guide";

    public ConformanceTargetSetV2 {
        contractVersion = Objects.requireNonNull(contractVersion, "contractVersion");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
    }

    /**
     * Builds the target set from a compiled Blueprint, its source intent's map bindings, and the request's
     * declared constraint-map sources (used only to classify each desired raster's provenance origin).
     */
    public static ConformanceTargetSetV2 from(
            WorldBlueprintV2 blueprint,
            TerrainIntentV2 intent,
            GenerationRequestV2 request
    ) {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(request, "request");
        Map<String, String> declaredDigestBySourceId = request.constraintMaps().stream().collect(
                Collectors.toMap(
                        GenerationRequestV2.ConstraintMapSource::sourceId,
                        GenerationRequestV2.ConstraintMapSource::expectedSha256,
                        (first, second) -> first));
        return from(blueprint.validationTargets(), intent.mapReferences(), declaredDigestBySourceId);
    }

    /**
     * Core factory over the raw contract lists, so the classification is unit-testable without assembling
     * a full Blueprint. Targets keep Blueprint order; desired rasters follow, in binding order.
     * {@code declaredDigestBySourceId} maps a declared constraint-map source id to its expected SHA-256;
     * a desired raster whose binding digest matches its declared source is {@code INPUT_MASK}, else
     * {@code SELF_DERIVED}.
     */
    public static ConformanceTargetSetV2 from(
            List<ValidationTargetV2> validationTargets,
            List<TerrainIntentV2.ConstraintMapBinding> mapReferences,
            Map<String, String> declaredDigestBySourceId
    ) {
        Objects.requireNonNull(validationTargets, "validationTargets");
        Objects.requireNonNull(mapReferences, "mapReferences");
        Objects.requireNonNull(declaredDigestBySourceId, "declaredDigestBySourceId");

        List<ConformanceTargetV2> targets = new ArrayList<>();
        for (ValidationTargetV2 target : validationTargets) {
            targets.add(ConformanceTargetClassifierV2.classify(target));
        }
        for (TerrainIntentV2.ConstraintMapBinding binding : mapReferences) {
            // V2-19-06 added the HEIGHT_GUIDE raster, whose generation-side consumer is the macro
            // foundation's background elevation source. ZONE_LABEL_MAP still has no consumer and no
            // per-cell desired field, and is intentionally not fabricated here.
            if (binding.role() == TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP) {
                continue;
            }
            targets.add(desiredRasterTarget(binding, declaredDigestBySourceId));
        }
        return new ConformanceTargetSetV2(CONTRACT_VERSION, List.copyOf(targets));
    }

    private static ConformanceTargetV2.DesiredRaster desiredRasterTarget(
            TerrainIntentV2.ConstraintMapBinding binding,
            Map<String, String> declaredDigestBySourceId
    ) {
        String boundDigest = artifactDigest(binding);
        String declaredDigest = declaredDigestBySourceId.get(binding.sourceId());
        ConformanceProvenanceV2.Origin origin =
                declaredDigest != null && declaredDigest.equals(boundDigest)
                        ? ConformanceProvenanceV2.Origin.INPUT_MASK
                        : ConformanceProvenanceV2.Origin.SELF_DERIVED;
        ConformanceProvenanceV2 provenance = new ConformanceProvenanceV2(
                binding.role(), binding.sourceId(), boundDigest, origin);
        return new ConformanceTargetV2.DesiredRaster(
                "conformance-desired-raster-" + binding.id(), fieldIdOf(binding.role()),
                binding.strength(), provenance);
    }

    private static String fieldIdOf(TerrainIntentV2.ConstraintMapRole role) {
        return switch (role) {
            case LAND_WATER_MASK -> LAND_WATER_FIELD_ID;
            case HEIGHT_GUIDE -> HEIGHT_GUIDE_FIELD_ID;
            case ZONE_LABEL_MAP -> throw new IllegalArgumentException(
                    "zone label maps carry no desired raster target");
        };
    }

    private static String artifactDigest(TerrainIntentV2.ConstraintMapBinding binding) {
        String prefix = switch (binding.role()) {
            case LAND_WATER_MASK -> "constraint:land-water:sha256-";
            case HEIGHT_GUIDE -> "constraint:height-guide:sha256-";
            case ZONE_LABEL_MAP -> "constraint:zone-label-map:sha256-";
        };
        String artifactId = binding.artifactId();
        return artifactId.startsWith(prefix) ? artifactId.substring(prefix.length()) : artifactId;
    }

    public List<ConformanceTargetV2.DesiredRaster> desiredRasterTargets() {
        return targetsOfType(ConformanceTargetV2.DesiredRaster.class);
    }

    public List<ConformanceTargetV2.AggregateMetric> aggregateMetricTargets() {
        return targetsOfType(ConformanceTargetV2.AggregateMetric.class);
    }

    public List<ConformanceTargetV2.Topology> topologyTargets() {
        return targetsOfType(ConformanceTargetV2.Topology.class);
    }

    public List<ConformanceTargetV2.Geometric> geometricTargets() {
        return targetsOfType(ConformanceTargetV2.Geometric.class);
    }

    /** The provenance bindings of the set, one per desired raster, in the set's order. */
    public List<ConformanceProvenanceV2> provenanceBindings() {
        return desiredRasterTargets().stream().map(ConformanceTargetV2.DesiredRaster::provenance).toList();
    }

    private <T extends ConformanceTargetV2> List<T> targetsOfType(Class<T> type) {
        return targets.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
