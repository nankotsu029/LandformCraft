package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * V2-19-08: runtime preconditions a production pipeline enforces <em>after</em> dispatch selection.
 *
 * <p>{@link ProductionDispatchRegistryV2#select} only answers "does every declared kind have a
 * route". A pipeline runtime may additionally require companion kinds, in which case an intent that
 * declares a routed subset selects a pipeline and is still rejected during generation. Reporting only
 * the routing answer would tell a designer such an intent is reachable when it never is.</p>
 *
 * <p>This class is the single public statement of those preconditions. It is read-only and adds no
 * gate.</p>
 *
 * <p><b>V2-19-09 (ADR 0040 D5):</b> the one entry this table ever carried — the {@code surface-2_5d}
 * coastal runtime's "all four V2-2 contributors" requirement — is gone. ADR 0040 D1 relaxed the
 * contributor set to any subset of the four (including none), because a coverage requirement on the
 * modifier tier contradicts ADR 0038 D5-3 and duplicates the V2-18-10 foundation owner gate. Every
 * pipeline therefore requires no companion kind today. The table, its API, and the
 * {@code v2.design.route-companion-missing} rule stay in place unchanged for a future pipeline that
 * does carry such a requirement; only the values are now empty.</p>
 */
public final class ProductionRoutePreconditionsV2 {
    /**
     * Pipeline id → kinds the pipeline's runtime requires in full whenever it runs. Every current
     * pipeline builds the coastal runtime (the hydrology, environment, and sparse-volume pipelines
     * delegate to the coastal one), and since ADR 0040 that runtime requires no contributor at all.
     */
    private static final Map<String, Set<TerrainIntentV2.FeatureKind>> REQUIRED_COMPANIONS;
    static {
        Map<String, Set<TerrainIntentV2.FeatureKind>> required = new TreeMap<>();
        Set<TerrainIntentV2.FeatureKind> none = Set.of();
        required.put(CoastalSurfaceExportPipelineV2.PIPELINE_ID, none);
        required.put(HydrologyPlanExportPipelineV2.PIPELINE_ID, none);
        required.put(EnvironmentFieldsExportPipelineV2.PIPELINE_ID, none);
        required.put(SparseVolumeExportPipelineV2.PIPELINE_ID, none);
        REQUIRED_COMPANIONS = Map.copyOf(required);
    }

    private ProductionRoutePreconditionsV2() {
    }

    /** Kinds every run of {@code pipelineId} requires, or an empty set for an unknown pipeline. */
    public static Set<TerrainIntentV2.FeatureKind> requiredCompanionKinds(String pipelineId) {
        Objects.requireNonNull(pipelineId, "pipelineId");
        Set<TerrainIntentV2.FeatureKind> required = REQUIRED_COMPANIONS.get(pipelineId);
        return required == null ? Set.of() : sorted(required);
    }

    /** Union of every pipeline's companion requirement, for operator-facing display. */
    public static Set<TerrainIntentV2.FeatureKind> requiredCompanionKinds() {
        Set<TerrainIntentV2.FeatureKind> union = new TreeSet<>(Comparator.comparing(Enum::name));
        REQUIRED_COMPANIONS.values().forEach(union::addAll);
        return union;
    }

    private static Set<TerrainIntentV2.FeatureKind> sorted(Set<TerrainIntentV2.FeatureKind> kinds) {
        Set<TerrainIntentV2.FeatureKind> ordered = new TreeSet<>(Comparator.comparing(Enum::name));
        ordered.addAll(kinds);
        return ordered;
    }
}
