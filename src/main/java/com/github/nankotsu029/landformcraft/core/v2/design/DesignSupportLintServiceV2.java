package com.github.nankotsu029.landformcraft.core.v2.design;

import com.github.nankotsu029.landformcraft.core.v2.catalog.PublicDispatchReachabilityV2;
import com.github.nankotsu029.landformcraft.core.v2.export.ProductionDispatchRegistryV2;
import com.github.nankotsu029.landformcraft.core.v2.export.ProductionRoutePreconditionsV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignSupportLintV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignSupportSurfaceV2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * V2-19-08 design-time support lint. Report-only in both directions:
 *
 * <ol>
 *   <li>before the provider call it projects the reachable kind and capability set from the
 *       production dispatch registry, so a provider is told what it may design instead of learning
 *       at export time;</li>
 *   <li>after the provider returns an intent it dry-runs dispatch — selecting nothing, generating
 *       nothing — and records what would not be reachable.</li>
 * </ol>
 *
 * <p>The reachable set is projected from {@link ProductionDispatchRegistryV2}, not from the Feature
 * Support Catalog: the audit's §2.2 finding is precisely that {@code export == SUPPORTED} is a
 * different question from public dispatch reachability. Historic Schema is not narrowed; every one
 * of the 60 compatibility kinds still parses, and an intent naming an unreachable kind is still
 * published with a {@code NON_GATING} finding attached.</p>
 */
public final class DesignSupportLintServiceV2 {
    private final ProductionDispatchRegistryV2 registry;
    private final PublicDispatchReachabilityV2 reachability;

    public DesignSupportLintServiceV2() {
        this(ProductionDispatchRegistryV2.builtIn());
    }

    public DesignSupportLintServiceV2(ProductionDispatchRegistryV2 registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.reachability = PublicDispatchReachabilityV2.of(registry);
    }

    /** The reachable surface presented to a provider for {@code capabilities}. */
    public DesignSupportSurfaceV2 surface(Set<DesignCapabilityV2> capabilities) {
        Objects.requireNonNull(capabilities, "capabilities");
        return new DesignSupportSurfaceV2(
                DesignSupportSurfaceV2.CONTRACT_VERSION,
                ProductionDispatchRegistryV2.CONTRACT_VERSION,
                registry.registryChecksum(),
                reachability.canonicalChecksum(),
                names(reachability.kindsWith(PublicDispatchReachabilityV2.ReachabilityV2.PRODUCTION_CONNECTED)),
                names(reachability.kindsWith(PublicDispatchReachabilityV2.ReachabilityV2.OFFLINE_PRODUCTION)),
                names(reachability.kindsWith(PublicDispatchReachabilityV2.ReachabilityV2.CONTRACT_ONLY)),
                names(ProductionRoutePreconditionsV2.requiredCompanionKinds()),
                capabilities.stream().map(Enum::name).sorted().toList());
    }

    /**
     * Dry-runs the designed intent against every registered capability set. Nothing is generated and
     * no artifact is touched: {@link ProductionDispatchRegistryV2#select} is a pure lookup, and its
     * rejection is caught rather than propagated because this lint never fails a design.
     */
    public DesignSupportLintV2 lint(DesignSupportSurfaceV2 surface, TerrainIntentV2 intent) {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(intent, "intent");

        Set<TerrainIntentV2.FeatureKind> declared = new TreeSet<>(Comparator.comparing(Enum::name));
        intent.features().forEach(feature -> declared.add(feature.kind()));

        List<String> selectablePipelines = new ArrayList<>();
        Set<TerrainIntentV2.FeatureKind> missingCompanions = new TreeSet<>(Comparator.comparing(Enum::name));
        for (List<String> capabilities : registry.pipelineCapabilitySets()) {
            String pipelineId = registry.pipelineIdFor(capabilities);
            if (!selects(intent, capabilities)) {
                continue;
            }
            Set<TerrainIntentV2.FeatureKind> required =
                    ProductionRoutePreconditionsV2.requiredCompanionKinds(pipelineId);
            Set<TerrainIntentV2.FeatureKind> missing = EnumSet.noneOf(TerrainIntentV2.FeatureKind.class);
            required.stream().filter(kind -> !declared.contains(kind)).forEach(missing::add);
            if (missing.isEmpty()) {
                selectablePipelines.add(pipelineId);
            } else {
                missingCompanions.addAll(missing);
            }
        }
        selectablePipelines.sort(Comparator.naturalOrder());

        List<DesignSupportLintV2.FindingV2> findings = new ArrayList<>();
        Set<TerrainIntentV2.FeatureKind> unreachable = new TreeSet<>(Comparator.comparing(Enum::name));
        Set<TerrainIntentV2.FeatureKind> contractOnly = new TreeSet<>(Comparator.comparing(Enum::name));
        Set<TerrainIntentV2.FeatureKind> planOnly = new TreeSet<>(Comparator.comparing(Enum::name));
        for (TerrainIntentV2.FeatureKind kind : declared) {
            PublicDispatchReachabilityV2.EntryV2 entry = reachability.entry(kind);
            switch (entry.reachability()) {
                case NOT_PUBLICLY_DISPATCHABLE -> unreachable.add(kind);
                case CONTRACT_ONLY -> contractOnly.add(kind);
                case PRODUCTION_CONNECTED, OFFLINE_PRODUCTION -> {
                    if (entry.blockMaterialization()
                            == PublicDispatchReachabilityV2.BlockMaterializationV2.PLAN_ONLY) {
                        planOnly.add(kind);
                    }
                }
            }
        }
        addFinding(findings, DesignSupportLintV2.RULE_KIND_NOT_DISPATCHABLE, unreachable,
                "declared kind has no production dispatch route; export would reject it before "
                        + "any artifact is written");
        addFinding(findings, DesignSupportLintV2.RULE_KIND_CONTRACT_ONLY, contractOnly,
                "declared kind is accepted only as a contract-only diagnostic companion and selects "
                        + "no pipeline by itself");
        addFinding(findings, DesignSupportLintV2.RULE_KIND_PLAN_ONLY, planOnly,
                "declared kind is routed but its route changes no block in the final canonical "
                        + "block stream");
        addFinding(findings, DesignSupportLintV2.RULE_COMPANION_MISSING, missingCompanions,
                "a pipeline runtime requires these companion kinds in full; the intent declares a "
                        + "routed subset only");
        DesignSupportLintV2.DispatchDryRunV2 dryRun = selectablePipelines.isEmpty()
                ? DesignSupportLintV2.DispatchDryRunV2.NOT_SELECTABLE
                : DesignSupportLintV2.DispatchDryRunV2.SELECTABLE;
        if (dryRun == DesignSupportLintV2.DispatchDryRunV2.NOT_SELECTABLE) {
            findings.add(new DesignSupportLintV2.FindingV2(
                    DesignSupportLintV2.RULE_DISPATCH_UNSELECTABLE,
                    DesignSupportLintV2.GateClassV2.NON_GATING,
                    List.of(),
                    "no registered capability set would accept this intent; the design is published "
                            + "but cannot be exported as designed"));
        }
        findings.sort(Comparator
                .comparing(DesignSupportLintV2.FindingV2::ruleId)
                .thenComparing(finding -> String.join(",", finding.featureKinds())));

        return new DesignSupportLintV2(
                surface,
                dryRun,
                List.copyOf(new LinkedHashSet<>(selectablePipelines)),
                names(declared),
                findings);
    }

    private boolean selects(TerrainIntentV2 intent, List<String> capabilities) {
        try {
            registry.select(intent, capabilities);
            return true;
        } catch (IllegalArgumentException rejected) {
            // A rejection is the dry-run's answer, not a failure: the lint never fails a design.
            return false;
        }
    }

    private static void addFinding(
            List<DesignSupportLintV2.FindingV2> findings,
            String ruleId,
            Set<TerrainIntentV2.FeatureKind> kinds,
            String detail
    ) {
        if (kinds.isEmpty()) {
            return;
        }
        findings.add(new DesignSupportLintV2.FindingV2(
                ruleId, DesignSupportLintV2.GateClassV2.NON_GATING, names(kinds), detail));
    }

    private static List<String> names(Set<TerrainIntentV2.FeatureKind> kinds) {
        return kinds.stream().map(Enum::name).sorted().toList();
    }
}
