package com.github.nankotsu029.landformcraft.core.v2.catalog;

import com.github.nankotsu029.landformcraft.format.v2.catalog.FeatureSupportCatalogCodecV2;
import com.github.nankotsu029.landformcraft.generator.v2.BuiltInLandformModuleCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.BuiltInFeatureSupportCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeaturePrimaryRoleV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportEntryV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportLevelV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.PlacementDimensionLimitV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.Release2MeasuredDimensionGateV2;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Taxonomy ↔ code consistency verifier for the V2-6-18 Feature Support Catalog.
 */
public final class FeatureSupportCatalogConsistencyVerifierV2 {
    private final FeatureSupportCatalogCodecV2 codec = new FeatureSupportCatalogCodecV2();

    public FeatureSupportCatalogV2 requireConsistentBuiltIn() {
        FeatureSupportCatalogV2 catalog = codec.builtInSealed();
        List<String> failures = verify(catalog, new BuiltInLandformModuleCatalogV2().modules());
        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "feature support catalog consistency failed:\n - "
                            + String.join("\n - ", failures));
        }
        return catalog;
    }

    public List<String> verify(
            FeatureSupportCatalogV2 catalog,
            List<ModuleDescriptorV2> modules
    ) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(modules, "modules");
        List<String> failures = new ArrayList<>();
        codec.verifyChecksum(catalog);

        // V2-11-06: the published limit is exactly the measured maximum (V2-11-04 500x500 and
        // V2-11-05 1000x1000 on FAWE 2.15.2). Measured sizes must be admitted and anything above
        // the largest measurement must stay rejected.
        if (catalog.placementDimensionLimit().maximumWidth()
                != PlacementDimensionLimitV2.MEASURED_MAXIMUM
                || catalog.placementDimensionLimit().maximumLength()
                != PlacementDimensionLimitV2.MEASURED_MAXIMUM) {
            failures.add("placementDimensionLimit must equal measured "
                    + PlacementDimensionLimitV2.MEASURED_MAXIMUM + "x"
                    + PlacementDimensionLimitV2.MEASURED_MAXIMUM);
        }
        if (catalog.rejectsUnmeasuredPaperPromotion(500, 500)
                || catalog.rejectsUnmeasuredPaperPromotion(1000, 1000)) {
            failures.add("catalog must admit the measured 500/1000 Paper dimensions (V2-11-06)");
        }
        if (!catalog.rejectsUnmeasuredPaperPromotion(
                PlacementDimensionLimitV2.MEASURED_MAXIMUM + 1,
                PlacementDimensionLimitV2.MEASURED_MAXIMUM)) {
            failures.add("catalog must reject dimensions above the measured maximum");
        }

        Set<TerrainIntentV2.FeatureKind> covered = EnumSet.noneOf(TerrainIntentV2.FeatureKind.class);
        for (FeatureSupportEntryV2 entry : catalog.entries()) {
            for (FeatureSupportCapabilityV2 capability : FeatureSupportCapabilityV2.values()) {
                Objects.requireNonNull(entry.support().level(capability));
            }
            // V2-11-01 policy: SUPPORTED Paper columns require the smoke-evidenced capability
            // prefix (surface-2_5d at 64x64) plus declared runtime and smoke evidence link.
            // Any non-UNSUPPORTED Paper claim requires a Release capability path at all.
            FeatureSupportLevelV2 paperApply =
                    entry.support().level(FeatureSupportCapabilityV2.PAPER_APPLY);
            if (paperApply == FeatureSupportLevelV2.SUPPORTED) {
                if (!BuiltInFeatureSupportCatalogV2.PAPER_SMOKE_EVIDENCED_CAPABILITIES
                        .contains(entry.requiredReleaseCapability())) {
                    failures.add("paper_apply SUPPORTED without smoke-evidenced capability prefix: "
                            + entry.entryId());
                }
                if (entry.requiredRuntime().isEmpty()) {
                    failures.add("paper_apply SUPPORTED without declared runtime: "
                            + entry.entryId());
                }
                if (!entry.evidenceRef().contains("smoke")) {
                    failures.add("paper_apply SUPPORTED without smoke evidence link: "
                            + entry.entryId());
                }
                // V2-11-06: a published limit above the smoke size additionally requires the
                // measurement audits on the promoted entries themselves.
                if (catalog.placementDimensionLimit().maximumWidth()
                        > PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM
                        && !entry.evidenceRef().contains(
                                BuiltInFeatureSupportCatalogV2.MEASURED_DIMENSION_EVIDENCE)) {
                    failures.add("paper_apply SUPPORTED above the smoke size without the"
                            + " V2-11-04/V2-11-05 measurement evidence link: " + entry.entryId());
                }
            }
            if (paperApply != FeatureSupportLevelV2.UNSUPPORTED
                    && paperApply != FeatureSupportLevelV2.NOT_APPLICABLE
                    && entry.requiredReleaseCapability().isEmpty()) {
                failures.add("Paper claim without Release capability path: " + entry.entryId());
            }
            if (entry.primaryRole() == FeaturePrimaryRoleV2.CHILD_PLAN_ONLY
                    && entry.support().standaloneUsage() != FeatureSupportLevelV2.UNSUPPORTED) {
                failures.add("child-plan-only standalone must be UNSUPPORTED: " + entry.entryId());
            }
            entry.featureKind().ifPresent(kind -> {
                if (!covered.add(kind)) {
                    failures.add("duplicate FeatureKind coverage: " + kind);
                }
            });
        }
        for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
            if (!covered.contains(kind)) {
                failures.add("FeatureKind missing from catalog: " + kind);
            }
        }

        Set<String> moduleIds = new HashSet<>();
        for (ModuleDescriptorV2 module : modules) {
            moduleIds.add(module.moduleId());
            if (module.lifecycleStatus() == ModuleDescriptorV2.LifecycleStatus.SUPPORTED) {
                for (TerrainIntentV2.FeatureKind kind : module.supportedFeatureKinds()) {
                    FeatureSupportEntryV2 entry = catalog.require(kind.name());
                    if (entry.support().offlineGenerate() != FeatureSupportLevelV2.SUPPORTED
                            || entry.support().validation() != FeatureSupportLevelV2.SUPPORTED
                            || entry.support().preview() != FeatureSupportLevelV2.SUPPORTED
                            || entry.support().export() != FeatureSupportLevelV2.SUPPORTED) {
                        failures.add(
                                "SUPPORTED module kind must have offline G/V/P/X SUPPORTED: "
                                        + kind);
                    }
                    if (entry.lifecycleStatus() != ModuleDescriptorV2.LifecycleStatus.SUPPORTED) {
                        failures.add("display lifecycle must be SUPPORTED for " + kind);
                    }
                }
            }
        }

        BuiltInLandformModuleCatalogV2 moduleCatalog = new BuiltInLandformModuleCatalogV2();
        for (TerrainIntentV2.FeatureKind kind : BuiltInFeatureSupportCatalogV2.dedicatedSurfaceKinds()) {
            ModuleDescriptorV2 bound = moduleCatalog.requireFor(kind);
            if (BuiltInLandformModuleCatalogV2.DIAGNOSTIC_MODULE_ID.equals(bound.moduleId())) {
                failures.add("SF16 kind must not bind to diagnostic module: " + kind);
            }
            FeatureSupportEntryV2 entry = catalog.require(kind.name());
            if (!"SF16".equals(entry.profileId())
                    || entry.support().standaloneUsage() != FeatureSupportLevelV2.SUPPORTED) {
                failures.add("SF16 standalone must be SUPPORTED: " + kind);
            }
            if (bound.lifecycleStatus() != ModuleDescriptorV2.LifecycleStatus.SUPPORTED) {
                failures.add("dedicated SF16 binding module must be SUPPORTED: " + kind);
            }
        }
        for (TerrainIntentV2.FeatureKind kind : BuiltInFeatureSupportCatalogV2.childPlanOnlyKinds()) {
            FeatureSupportEntryV2 entry = catalog.require(kind.name());
            if (entry.primaryRole() != FeaturePrimaryRoleV2.CHILD_PLAN_ONLY) {
                failures.add("CP4 role must be CHILD_PLAN_ONLY: " + kind);
            }
        }

        Release2MeasuredDimensionGateV2 gate = new Release2MeasuredDimensionGateV2(
                catalog.placementDimensionLimit().maximumWidth(),
                catalog.placementDimensionLimit().maximumLength());
        try {
            gate.requireAdmitted(
                    PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM,
                    PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM);
            gate.requireAdmitted(
                    PlacementDimensionLimitV2.MEASURED_MAXIMUM,
                    PlacementDimensionLimitV2.MEASURED_MAXIMUM);
        } catch (RuntimeException exception) {
            failures.add("dimension gate must admit measured sizes: " + exception.getMessage());
        }
        try {
            gate.requireAdmitted(
                    PlacementDimensionLimitV2.MEASURED_MAXIMUM + 1,
                    PlacementDimensionLimitV2.MEASURED_MAXIMUM);
            failures.add("dimension gate must reject above the measured maximum");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
        // The above-smoke evidence is FAWE-only: a runtime without it stays clamped to 64x64.
        if (Release2MeasuredDimensionGateV2.measuredCeilingFor(false)
                != PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM
                || Release2MeasuredDimensionGateV2.measuredCeilingFor(true)
                != PlacementDimensionLimitV2.MEASURED_MAXIMUM) {
            failures.add("runtime measured ceiling must match the catalog evidence split");
        }

        if (catalog.entries().size() > FeatureSupportCatalogV2.MAXIMUM_ENTRIES) {
            failures.add("catalog exceeds entry budget");
        }
        // Locale must not affect verification outcomes.
        Objects.requireNonNull(Locale.ROOT);
        Objects.requireNonNull(moduleIds);
        return List.copyOf(failures);
    }
}
