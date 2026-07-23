package com.github.nankotsu029.landformcraft.model.v2.catalog;

import com.github.nankotsu029.landformcraft.model.v2.ModuleDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Built-in Feature Support Catalog data for V2-6-18, promoted by V2-11-01 and V2-11-06.
 * Evidence sources: Gap audit profiles, V2-2〜V2-5 offline gates, V2-9/V2-10 phase gates,
 * WE/FAWE smoke evidence (surface-2_5d at 64x64), V2-6 phase gate audit, and the V2-11-04
 * (500x500) / V2-11-05 (1000x1000) FAWE measurements. Paper capability columns are SUPPORTED
 * only for the smoke-evidenced surface-2_5d prefix; the other Release capability prefixes stay
 * EXPERIMENTAL until per-prefix real-machine smoke, and entries without a Release capability
 * stay UNSUPPORTED. V2-11-06 widens the published dimension ceiling only, never the feature set.
 */
public final class BuiltInFeatureSupportCatalogV2 {
    public static final String DECISION_ID = "v2-6-18-final-supported-catalog";
    public static final String PROMOTION_DECISION_ID = "v2-11-01-paper-capability-promotion";
    public static final String MEASURED_DIMENSION_DECISION_ID =
            "v2-11-06-measured-dimension-promotion";
    public static final String REQUIRED_RUNTIME =
            "paper-1.21.11+worldedit-7.3.19|fawe-2.15.2";

    /**
     * Runtime that carries the above-smoke dimension evidence. V2-11-04/05 ran FAWE 2.15.2 only,
     * so a WorldEdit-only server keeps the 64x64 production ceiling (V2-11-06).
     */
    public static final String MEASURED_DIMENSION_RUNTIME = "fawe-2.15.2";

    /** Capability prefixes with real-machine placement smoke evidence (V2-6-14/15, 64x64). */
    public static final Set<String> PAPER_SMOKE_EVIDENCED_CAPABILITIES = Set.of("surface-2_5d");

    /** Evidence links for the V2-11-06 dimension promotion. */
    public static final String MEASURED_DIMENSION_EVIDENCE =
            "docs/design-v2/audits/v2-11-04-fawe-500-measurement.md"
                    + " + docs/design-v2/audits/v2-11-05-fawe-1000-measurement.md";

    private BuiltInFeatureSupportCatalogV2() {
    }

    /** Unsealed catalog (checksum placeholder). Seal via FeatureSupportCatalogCodecV2. */
    public static FeatureSupportCatalogV2 unsealed() {
        return new FeatureSupportCatalogV2(
                FeatureSupportCatalogV2.VERSION,
                FeatureSupportCatalogV2.CONTRACT_VERSION,
                PlacementDimensionLimitV2.measured(),
                entries(),
                List.of("ATOLL", "BARRIER_ISLAND", "CENOTE", "ICE_FJORD", "WATERFALL_CHAIN"),
                List.of(
                        "paper_apply/post_apply/snapshot/rollback/restart_recovery SUPPORTED only for the smoke-evidenced surface-2_5d prefix, up to the published 1000x1000 measured limit (V2-11-01 features, V2-11-06 dimensions)",
                        "hydrology-plan/environment-fields/sparse-volume Paper columns are EXPERIMENTAL until per-prefix real-machine smoke (new Task ID required for SUPPORTED)",
                        "V2-9/V2-10 foundation features share the canonical placement stream (V2-6 evidence inheritance) but have no Release capability; their Paper columns stay UNSUPPORTED until export connection",
                        "dimensions above 64x64 are measured on fawe-2.15.2 only (V2-11-04 500x500, V2-11-05 1000x1000); a WorldEdit-only runtime keeps the 64x64 production ceiling",
                        "dimensions above 1000x1000 remain unmeasured and are rejected before any world mutation (LARGE/V2-8 streaming is not claimed)",
                        "lifecycleStatus is display-only and must not decide capability support"),
                List.of(
                        "OCEAN_TRENCH", "MID_OCEAN_RIDGE", "SUBMARINE_VOLCANO",
                        "BRAIDED_RIVER", "DAM_RESERVOIR", "ESTUARY", "ALLUVIAL_FAN", "RIVER_TERRACE",
                        "FLOATING_REEF", "KARST_CAVE_SYSTEM"),
                "0".repeat(64));
    }

    public static Set<TerrainIntentV2.FeatureKind> dedicatedSurfaceKinds() {
        return EnumSet.of(
                TerrainIntentV2.FeatureKind.SANDY_BEACH,
                TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                TerrainIntentV2.FeatureKind.ROCKY_CAPE,
                TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                TerrainIntentV2.FeatureKind.RIVER,
                TerrainIntentV2.FeatureKind.LAKE,
                TerrainIntentV2.FeatureKind.CANYON,
                TerrainIntentV2.FeatureKind.WATERFALL,
                TerrainIntentV2.FeatureKind.DELTA,
                TerrainIntentV2.FeatureKind.TIDAL_CHANNEL_NETWORK,
                TerrainIntentV2.FeatureKind.FJORD,
                TerrainIntentV2.FeatureKind.ALPINE_MOUNTAIN_RANGE,
                TerrainIntentV2.FeatureKind.GLACIAL_MOUNTAIN_RANGE,
                TerrainIntentV2.FeatureKind.VOLCANIC_ARCHIPELAGO,
                TerrainIntentV2.FeatureKind.MANGROVE_WETLAND,
                TerrainIntentV2.FeatureKind.CORAL_REEF);
    }

    public static Set<TerrainIntentV2.FeatureKind> childPlanOnlyKinds() {
        return EnumSet.of(
                TerrainIntentV2.FeatureKind.LAGOON,
                TerrainIntentV2.FeatureKind.REEF_PASS,
                TerrainIntentV2.FeatureKind.VOLCANIC_CALDERA,
                TerrainIntentV2.FeatureKind.LAVA_FLOW_FIELD);
    }

    private static List<FeatureSupportEntryV2> entries() {
        List<FeatureSupportEntryV2> result = new ArrayList<>();
        result.add(entry(
                "SANDY_BEACH",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.SUPPORTED),
                "SANDY_BEACH",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "surface-2_5d",
                REQUIRED_RUNTIME,
                "v2-6-14/15 smoke evidence + docs/design-v2/audits/v2-6-phase-gate.md + "
                        + MEASURED_DIMENSION_EVIDENCE,
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns SUPPORTED within the measured 1000x1000 limit (features V2-11-01, dimensions V2-11-06); above 64x64 the evidence is fawe-2.15.2 only")));
        result.add(entry(
                "BREAKWATER_HARBOR",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.SUPPORTED),
                "BREAKWATER_HARBOR",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "surface-2_5d",
                REQUIRED_RUNTIME,
                "v2-6-14/15 smoke evidence + docs/design-v2/audits/v2-6-phase-gate.md + "
                        + MEASURED_DIMENSION_EVIDENCE,
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns SUPPORTED within the measured 1000x1000 limit (features V2-11-01, dimensions V2-11-06); above 64x64 the evidence is fawe-2.15.2 only")));
        result.add(entry(
                "HARBOR_BASIN",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.SUPPORTED),
                "HARBOR_BASIN",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "surface-2_5d",
                REQUIRED_RUNTIME,
                "v2-6-14/15 smoke evidence + docs/design-v2/audits/v2-6-phase-gate.md + "
                        + MEASURED_DIMENSION_EVIDENCE,
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns SUPPORTED within the measured 1000x1000 limit (features V2-11-01, dimensions V2-11-06); above 64x64 the evidence is fawe-2.15.2 only")));
        result.add(entry(
                "ROCKY_CAPE",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.SUPPORTED),
                "ROCKY_CAPE",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "surface-2_5d",
                REQUIRED_RUNTIME,
                "v2-6-14/15 smoke evidence + docs/design-v2/audits/v2-6-phase-gate.md + "
                        + MEASURED_DIMENSION_EVIDENCE,
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns SUPPORTED within the measured 1000x1000 limit (features V2-11-01, dimensions V2-11-06); above 64x64 the evidence is fawe-2.15.2 only")));
        result.add(entry(
                "MEANDERING_RIVER",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "MEANDERING_RIVER",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "hydrology-plan",
                "",
                "docs/design-v2/audits + V2-2/3/4 phase gates",
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "LAKE",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "LAKE",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "hydrology-plan",
                "",
                "docs/design-v2/audits + V2-2/3/4 phase gates",
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "CANYON",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "CANYON",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "hydrology-plan",
                "",
                "docs/design-v2/audits + V2-2/3/4 phase gates",
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "WATERFALL",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "WATERFALL",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "hydrology-plan",
                "",
                "docs/design-v2/audits + V2-2/3/4 phase gates",
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "DELTA",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "DELTA",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "hydrology-plan",
                "",
                "docs/design-v2/audits + V2-2/3/4 phase gates",
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "TIDAL_CHANNEL_NETWORK",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "TIDAL_CHANNEL_NETWORK",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "hydrology-plan",
                "",
                "docs/design-v2/audits + V2-2/3/4 phase gates",
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "FJORD",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "FJORD",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "hydrology-plan",
                "",
                "docs/design-v2/audits + V2-2/3/4 phase gates",
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "ALPINE_MOUNTAIN_RANGE",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "ALPINE_MOUNTAIN_RANGE",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "environment-fields",
                "",
                "docs/design-v2/audits + V2-2/3/4 phase gates",
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "GLACIAL_MOUNTAIN_RANGE",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "GLACIAL_MOUNTAIN_RANGE",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "environment-fields",
                "",
                "docs/design-v2/audits + V2-2/3/4 phase gates",
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "VOLCANIC_ARCHIPELAGO",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "VOLCANIC_ARCHIPELAGO",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "environment-fields",
                "",
                "docs/design-v2/audits + V2-2/3/4 phase gates",
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "MANGROVE_WETLAND",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "MANGROVE_WETLAND",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "environment-fields",
                "",
                "docs/design-v2/audits + V2-2/3/4 phase gates",
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "CORAL_REEF",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "CORAL_REEF",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "environment-fields",
                "",
                "docs/design-v2/audits + V2-2/3/4 phase gates",
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "LAGOON",
                "CP4",
                FeaturePrimaryRoleV2.CHILD_PLAN_ONLY,
                List.of(FeaturePrimaryRoleV2.CHILD_PLAN_ONLY),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE),
                "LAGOON",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/terrain-feature-taxonomy.md",
                List.of(
                        "CHILD_PLAN_ONLY; standalone_usage must remain UNSUPPORTED")));
        result.add(entry(
                "REEF_PASS",
                "CP4",
                FeaturePrimaryRoleV2.CHILD_PLAN_ONLY,
                List.of(FeaturePrimaryRoleV2.CHILD_PLAN_ONLY),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE),
                "REEF_PASS",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/terrain-feature-taxonomy.md",
                List.of(
                        "CHILD_PLAN_ONLY; standalone_usage must remain UNSUPPORTED")));
        result.add(entry(
                "VOLCANIC_CALDERA",
                "CP4",
                FeaturePrimaryRoleV2.CHILD_PLAN_ONLY,
                List.of(FeaturePrimaryRoleV2.CHILD_PLAN_ONLY),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE),
                "VOLCANIC_CALDERA",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/terrain-feature-taxonomy.md",
                List.of(
                        "CHILD_PLAN_ONLY; standalone_usage must remain UNSUPPORTED")));
        result.add(entry(
                "LAVA_FLOW_FIELD",
                "CP4",
                FeaturePrimaryRoleV2.CHILD_PLAN_ONLY,
                List.of(FeaturePrimaryRoleV2.CHILD_PLAN_ONLY),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE),
                "LAVA_FLOW_FIELD",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/terrain-feature-taxonomy.md",
                List.of(
                        "CHILD_PLAN_ONLY; standalone_usage must remain UNSUPPORTED")));
        result.add(entry(
                "CAVE_NETWORK",
                "VE4",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.EXPERIMENTAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "CAVE_NETWORK",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "sparse-volume",
                "",
                "docs/design-v2/audits/v2-5-phase-gate.md",
                List.of(
                        "Plan-level volume generator; public Intent stays EXPERIMENTAL/diagnostic",
                        "May host child connectors",
                        "Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "LUSH_CAVE",
                "VE4",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.EXPERIMENTAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "LUSH_CAVE",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "sparse-volume",
                "",
                "docs/design-v2/audits/v2-5-phase-gate.md",
                List.of(
                        "Plan-level volume generator; public Intent stays EXPERIMENTAL/diagnostic",
                        "Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "OVERHANG",
                "VE4",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.EXPERIMENTAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "OVERHANG",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "sparse-volume",
                "",
                "docs/design-v2/audits/v2-5-phase-gate.md",
                List.of(
                        "Plan-level volume generator; public Intent stays EXPERIMENTAL/diagnostic",
                        "Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "SKY_ISLAND_GROUP",
                "VE4",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.EXPERIMENTAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "SKY_ISLAND_GROUP",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "sparse-volume",
                "",
                "docs/design-v2/audits/v2-5-phase-gate.md",
                List.of(
                        "Plan-level volume generator; public Intent stays EXPERIMENTAL/diagnostic",
                        "Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "UNDERGROUND_LAKE",
                "VP3",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "sparse-volume",
                "",
                "docs/design-v2/audits/v2-5-phase-gate.md",
                List.of(
                        "No public FeatureKind; plan-level API only",
                        "Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "SEA_CAVE",
                "VP3",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "sparse-volume",
                "",
                "docs/design-v2/audits/v2-5-phase-gate.md",
                List.of(
                        "No public FeatureKind; plan-level API only",
                        "Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "NATURAL_ARCH",
                "VP3",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "sparse-volume",
                "",
                "docs/design-v2/audits/v2-5-phase-gate.md",
                List.of(
                        "No public FeatureKind; plan-level API only",
                        "Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "WATERFALL_VOLUME",
                "OVL1",
                FeaturePrimaryRoleV2.VOLUME_OVERLAY,
                List.of(FeaturePrimaryRoleV2.VOLUME_OVERLAY),
                withPaper(caps(FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.SUPPORTED), FeatureSupportLevelV2.EXPERIMENTAL),
                "",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "sparse-volume",
                "",
                "docs/design-v2/audits/v2-5-phase-gate.md",
                List.of(
                        "VOLUME_OVERLAY bound to surface WATERFALL; not a FeatureKind",
                        "Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "VOLUME_LOCAL_ENVIRONMENT",
                "COMP1",
                FeaturePrimaryRoleV2.COMPONENT,
                List.of(FeaturePrimaryRoleV2.COMPONENT),
                withPaper(caps(FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "sparse-volume",
                "",
                "docs/design-v2/audits/v2-5-phase-gate.md",
                List.of(
                        "COMPONENT; must not become a FeatureKind",
                        "Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)")));
        result.add(entry(
                "PLAIN",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "PLAIN",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "HILL_RANGE",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "HILL_RANGE",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "MOUNTAIN_RANGE",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "MOUNTAIN_RANGE",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "VALLEY",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "VALLEY",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "RIVER",
                "SF16",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                withPaper(caps(FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE), FeatureSupportLevelV2.EXPERIMENTAL),
                "RIVER",
                ModuleDescriptorV2.LifecycleStatus.SUPPORTED,
                "hydrology-plan",
                "",
                "docs/design-v2/audits + V2-15-10 offline hydrology-plan wiring (ADR 0039 Candidate A)",
                List.of(
                        "Dedicated module binding; offline SUPPORTED; Paper columns EXPERIMENTAL: /lfc v2 accepts this capability prefix, per-prefix real-machine smoke pending (V2-11-01)",
                        "V2-15-10 / ADR 0039 Candidate A: compiles via MeanderingRiverSubtypeBridgeV2 into the shared MeanderingRiverPlanCompilerV2 shape; MEANDERING_RIVER's own math/checksum contract is unchanged")));
        result.add(entry(
                "FLOODPLAIN",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "FLOODPLAIN",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "MARSH",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "MARSH",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "ROCKY_COAST",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.PARTIAL),
                "ROCKY_COAST",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL",
                        "Sea-cave/overhang host handoff")));
        result.add(entry(
                "SEA_CLIFF",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.PARTIAL),
                "SEA_CLIFF",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL",
                        "Sea-cave/overhang host handoff")));
        result.add(entry(
                "SINGLE_ISLAND",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "SINGLE_ISLAND",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "ARCHIPELAGO",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "ARCHIPELAGO",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "VOLCANIC_CONE",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "VOLCANIC_CONE",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "OCEAN_BASIN",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "OCEAN_BASIN",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "CONTINENTAL_SHELF",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "CONTINENTAL_SHELF",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "CONTINENTAL_SLOPE",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "CONTINENTAL_SLOPE",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "SUBMARINE_CANYON",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "SUBMARINE_CANYON",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "CAVE_ENTRANCE",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL),
                "CAVE_ENTRANCE",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "UNDERGROUND_RIVER",
                "FND9",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL),
                "UNDERGROUND_RIVER",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "V2-9 plan-level offline G/V/P SUPPORTED; intent/standalone/export PARTIAL")));
        result.add(entry(
                "MACRO_LAND_WATER_TOPOLOGY",
                "MACRO9",
                FeaturePrimaryRoleV2.MACRO_CONSTRAINT,
                List.of(FeaturePrimaryRoleV2.MACRO_CONSTRAINT),
                caps(FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "MACRO_CONSTRAINT; no FeatureKind")));
        result.add(entry(
                "VALLEY_GLACIER",
                "FND10",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.PARTIAL),
                "VALLEY_GLACIER",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "V2-10 plan-level offline; intent/standalone/export PARTIAL")));
        result.add(entry(
                "ICE_CAP",
                "FND10",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.PARTIAL),
                "ICE_CAP",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "V2-10 plan-level offline; intent/standalone/export PARTIAL")));
        result.add(entry(
                "ICE_SHEET",
                "FND10",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.PARTIAL),
                "ICE_SHEET",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "V2-10 plan-level offline; intent/standalone/export PARTIAL")));
        result.add(entry(
                "MORAINE_FIELD",
                "FND10",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.EXPERIMENTAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE),
                "MORAINE_FIELD",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "V2-10 plan-level offline; intent/standalone/export PARTIAL",
                        "Preview EXPERIMENTAL — no sealed preview index")));
        result.add(entry(
                "OUTWASH_PLAIN",
                "FND10",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.EXPERIMENTAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE),
                "OUTWASH_PLAIN",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "V2-10 plan-level offline; intent/standalone/export PARTIAL",
                        "Preview EXPERIMENTAL — no sealed preview index")));
        result.add(entry(
                "SINKHOLE",
                "FND10",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE),
                "SINKHOLE",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "V2-10 plan-level offline; intent/standalone/export PARTIAL")));
        result.add(entry(
                "KARST_SPRING",
                "FND10",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE),
                "KARST_SPRING",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "V2-10 plan-level offline; intent/standalone/export PARTIAL")));
        result.add(entry(
                "ABYSSAL_PLAIN",
                "FND10",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE),
                "ABYSSAL_PLAIN",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "V2-10 plan-level offline; intent/standalone/export PARTIAL")));
        result.add(entry(
                "SEAMOUNT",
                "FND10",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE),
                "SEAMOUNT",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "V2-10 plan-level offline; intent/standalone/export PARTIAL")));
        result.add(entry(
                "ESCARPMENT",
                "FND10",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "ESCARPMENT",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "V2-10 plan-level offline; intent/standalone/export PARTIAL")));
        result.add(entry(
                "PLATEAU",
                "FND10",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "PLATEAU",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "V2-10 plan-level offline; intent/standalone/export PARTIAL")));
        result.add(entry(
                "LAVA_TUBE",
                "FND10",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL),
                "LAVA_TUBE",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "V2-10 plan-level offline; intent/standalone/export PARTIAL")));
        result.add(entry(
                "SPRING",
                "FND10",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE),
                "SPRING",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "V2-10 plan-level offline; intent/standalone/export PARTIAL")));
        result.add(entry(
                "OXBOW_LAKE",
                "FND10",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE),
                "OXBOW_LAKE",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "V2-10 plan-level offline; intent/standalone/export PARTIAL")));
        result.add(entry(
                "BACKSHORE_PLAINS",
                "ENUM5",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.EXPERIMENTAL,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED),
                "BACKSHORE_PLAINS",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/audits/terrain-feature-gap-audit-2026-07-18.md",
                List.of(
                        "Diagnostic enum only; no dedicated generator")));
        result.add(entry(
                "BEDROCK_RIVER",
                "ENUM5",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.EXPERIMENTAL,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED),
                "BEDROCK_RIVER",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/audits/terrain-feature-gap-audit-2026-07-18.md",
                List.of(
                        "Diagnostic enum only; no dedicated generator")));
        result.add(entry(
                "GLACIAL_CIRQUE_FIELD",
                "ENUM5",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.EXPERIMENTAL,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED),
                "GLACIAL_CIRQUE_FIELD",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/audits/terrain-feature-gap-audit-2026-07-18.md",
                List.of(
                        "Diagnostic enum only; no dedicated generator")));
        result.add(entry(
                "FLOODED_CAVE",
                "ENUM5",
                FeaturePrimaryRoleV2.STANDALONE_FEATURE,
                List.of(FeaturePrimaryRoleV2.STANDALONE_FEATURE),
                caps(FeatureSupportLevelV2.EXPERIMENTAL,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED,FeatureSupportLevelV2.UNSUPPORTED),
                "FLOODED_CAVE",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/audits/terrain-feature-gap-audit-2026-07-18.md",
                List.of(
                        "Diagnostic enum only; no dedicated generator")));
        result.add(entry(
                "WATERFALL_CHAIN",
                "PRESET",
                FeaturePrimaryRoleV2.COMPOSITE_PRESET,
                List.of(FeaturePrimaryRoleV2.COMPOSITE_PRESET),
                caps(FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-9-phase-gate.md",
                List.of(
                        "COMPOSITE_PRESET; no FeatureKind")));
        result.add(entry(
                "ICE_FJORD",
                "PRESET",
                FeaturePrimaryRoleV2.COMPOSITE_PRESET,
                List.of(FeaturePrimaryRoleV2.COMPOSITE_PRESET),
                caps(FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.EXPERIMENTAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "COMPOSITE_PRESET; no FeatureKind")));
        result.add(entry(
                "CENOTE",
                "PRESET",
                FeaturePrimaryRoleV2.COMPOSITE_PRESET,
                List.of(FeaturePrimaryRoleV2.COMPOSITE_PRESET),
                caps(FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.EXPERIMENTAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "COMPOSITE_PRESET; no FeatureKind")));
        result.add(entry(
                "BARRIER_ISLAND",
                "PRESET",
                FeaturePrimaryRoleV2.COMPOSITE_PRESET,
                List.of(FeaturePrimaryRoleV2.COMPOSITE_PRESET),
                caps(FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.EXPERIMENTAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "COMPOSITE_PRESET; no FeatureKind")));
        result.add(entry(
                "ATOLL",
                "PRESET",
                FeaturePrimaryRoleV2.COMPOSITE_PRESET,
                List.of(FeaturePrimaryRoleV2.COMPOSITE_PRESET),
                caps(FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.SUPPORTED,FeatureSupportLevelV2.EXPERIMENTAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.PARTIAL,FeatureSupportLevelV2.NOT_APPLICABLE,FeatureSupportLevelV2.NOT_APPLICABLE),
                "",
                ModuleDescriptorV2.LifecycleStatus.EXPERIMENTAL,
                "",
                "",
                "docs/design-v2/audits/v2-10-phase-gate.md",
                List.of(
                        "COMPOSITE_PRESET; no FeatureKind")));
        return List.copyOf(result);
    }

    private static FeatureSupportEntryV2 entry(
            String entryId,
            String profileId,
            FeaturePrimaryRoleV2 primaryRole,
            List<FeaturePrimaryRoleV2> allowedUsages,
            FeatureSupportCapabilitiesV2 support,
            String featureKindName,
            ModuleDescriptorV2.LifecycleStatus lifecycleStatus,
            String requiredReleaseCapability,
            String requiredRuntime,
            String evidenceRef,
            List<String> notes
    ) {
        return new FeatureSupportEntryV2(
                entryId, profileId, primaryRole, allowedUsages, support, featureKindName,
                lifecycleStatus, requiredReleaseCapability, requiredRuntime, evidenceRef, notes);
    }

    private static FeatureSupportCapabilitiesV2 caps(
            FeatureSupportLevelV2 intent,
            FeatureSupportLevelV2 generate,
            FeatureSupportLevelV2 validation,
            FeatureSupportLevelV2 preview,
            FeatureSupportLevelV2 export,
            FeatureSupportLevelV2 standalone,
            FeatureSupportLevelV2 child,
            FeatureSupportLevelV2 overlay
    ) {
        return new FeatureSupportCapabilitiesV2(
                intent, generate, validation, preview, export, standalone, child, overlay,
                FeatureSupportLevelV2.UNSUPPORTED, FeatureSupportLevelV2.UNSUPPORTED,
                FeatureSupportLevelV2.UNSUPPORTED, FeatureSupportLevelV2.UNSUPPORTED,
                FeatureSupportLevelV2.UNSUPPORTED);
    }

    /** V2-11-01: sets all five Paper columns to the given evidence-bound level. */
    private static FeatureSupportCapabilitiesV2 withPaper(
            FeatureSupportCapabilitiesV2 base,
            FeatureSupportLevelV2 paper
    ) {
        return new FeatureSupportCapabilitiesV2(
                base.intentCompile(), base.offlineGenerate(), base.validation(), base.preview(),
                base.export(), base.standaloneUsage(), base.childPlanUsage(),
                base.volumeOverlayUsage(), paper, paper, paper, paper, paper);
    }
}
