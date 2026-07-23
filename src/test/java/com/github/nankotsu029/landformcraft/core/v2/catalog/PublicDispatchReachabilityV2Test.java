package com.github.nankotsu029.landformcraft.core.v2.catalog;

import com.github.nankotsu029.landformcraft.format.v2.catalog.FeatureSupportCatalogCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportCatalogV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportEntryV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.FeatureSupportLevelV2;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-19-01: the Feature Support Catalog support columns and public dispatch reachability are
 * displayed as separate axes, and neither implies block materialization.
 */
class PublicDispatchReachabilityV2Test {
    private static final Path DOCUMENT = Path.of("docs/design-v2/current-feature-state-machine-registry.md");
    private static final String START = "<!-- public-dispatch-reachability-v1:start -->";
    private static final String END = "<!-- public-dispatch-reachability-v1:end -->";

    @Test
    void everyCompatibilityKindHasExactlyOneReachabilityEntry() {
        PublicDispatchReachabilityV2 projection = PublicDispatchReachabilityV2.builtIn();

        assertEquals(TerrainIntentV2.FeatureKind.values().length, projection.entries().size());
        assertEquals(60, projection.entries().size());
        for (TerrainIntentV2.FeatureKind kind : TerrainIntentV2.FeatureKind.values()) {
            assertEquals(kind, projection.entry(kind).kind());
        }
    }

    @Test
    void theCoastalFourAreProductionConnectedAndMaterialized() {
        PublicDispatchReachabilityV2 projection = PublicDispatchReachabilityV2.builtIn();

        assertEquals(EnumSet.of(
                        TerrainIntentV2.FeatureKind.SANDY_BEACH,
                        TerrainIntentV2.FeatureKind.BREAKWATER_HARBOR,
                        TerrainIntentV2.FeatureKind.HARBOR_BASIN,
                        TerrainIntentV2.FeatureKind.ROCKY_CAPE),
                projection.kindsWith(PublicDispatchReachabilityV2.ReachabilityV2.PRODUCTION_CONNECTED));
        for (TerrainIntentV2.FeatureKind kind
                : projection.kindsWith(PublicDispatchReachabilityV2.ReachabilityV2.PRODUCTION_CONNECTED)) {
            assertEquals(PublicDispatchReachabilityV2.BlockMaterializationV2.MATERIALIZED,
                    projection.entry(kind).blockMaterialization(), kind::name);
            assertEquals("v2.production.surface-2_5d.coastal", projection.entry(kind).pipelineId());
        }
    }

    @Test
    void theOfflineRoutesAreReachableAndMaterializedAfterV21905AndV21907() {
        // The V2-15-10 OFFLINE_PRODUCTION routes were displayed PLAN_ONLY because they changed no
        // block (the cross-cutting audit's finding). V2-19-05 carved and filled the bed and measured
        // the effect per kind from a published Release, which is the only thing that flips this axis;
        // V2-19-07 added the PLAIN macro foundation producer the same way, on the coastal pipeline.
        PublicDispatchReachabilityV2 projection = PublicDispatchReachabilityV2.builtIn();

        assertEquals(EnumSet.of(
                        TerrainIntentV2.FeatureKind.RIVER,
                        TerrainIntentV2.FeatureKind.MEANDERING_RIVER,
                        TerrainIntentV2.FeatureKind.PLAIN),
                projection.kindsWith(PublicDispatchReachabilityV2.ReachabilityV2.OFFLINE_PRODUCTION));
        for (TerrainIntentV2.FeatureKind kind
                : projection.kindsWith(PublicDispatchReachabilityV2.ReachabilityV2.OFFLINE_PRODUCTION)) {
            assertEquals(PublicDispatchReachabilityV2.BlockMaterializationV2.MATERIALIZED,
                    projection.entry(kind).blockMaterialization(), kind::name);
        }
        assertEquals("v2.production.hydrology-plan.shared",
                projection.entry(TerrainIntentV2.FeatureKind.RIVER).pipelineId());
        assertEquals("v2.production.hydrology-plan.shared",
                projection.entry(TerrainIntentV2.FeatureKind.MEANDERING_RIVER).pipelineId());
        // The producer runs in the surface pipeline's foundation tier, not the hydrology overlay.
        assertEquals("v2.production.surface-2_5d.coastal",
                projection.entry(TerrainIntentV2.FeatureKind.PLAIN).pipelineId());
        assertTrue(projection.offlineKindsWith(
                PublicDispatchReachabilityV2.BlockMaterializationV2.PLAN_ONLY).isEmpty());
    }

    @Test
    void supportColumnsAndReachabilityAreIndependentAxes() {
        PublicDispatchReachabilityV2 projection = PublicDispatchReachabilityV2.builtIn();
        FeatureSupportCatalogV2 catalog = new FeatureSupportCatalogCodecV2().builtInSealed();

        // Direction 1: a strong support column does not make a kind publicly dispatchable — some
        // kind with offline_generate == SUPPORTED (the V2-9/V2-10 foundation work) has no route.
        // HILL_RANGE is the V2-9 sibling of the PLAIN producer V2-19-07 wired: same plan-level
        // support, no dispatch route until its own V2-15-22 wiring leaf runs.
        Set<TerrainIntentV2.FeatureKind> offlineGenerateSupported =
                kindsWithLevel(catalog, FeatureSupportCapabilityV2.OFFLINE_GENERATE);
        assertTrue(offlineGenerateSupported.contains(TerrainIntentV2.FeatureKind.HILL_RANGE));
        assertEquals(PublicDispatchReachabilityV2.ReachabilityV2.NOT_PUBLICLY_DISPATCHABLE,
                projection.entry(TerrainIntentV2.FeatureKind.HILL_RANGE).reachability());

        // Direction 2: reachability does not certify support — BACKSHORE_PLAINS reaches dispatch as
        // a contract-only diagnostic input while its catalog export column is UNSUPPORTED.
        assertEquals(PublicDispatchReachabilityV2.ReachabilityV2.CONTRACT_ONLY,
                projection.entry(TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS).reachability());
        assertEquals(FeatureSupportLevelV2.UNSUPPORTED,
                levelOf(catalog, TerrainIntentV2.FeatureKind.BACKSHORE_PLAINS,
                        FeatureSupportCapabilityV2.EXPORT));

        // Direction 3: block materialization does not certify support or Paper reachability either —
        // RIVER is MATERIALIZED since V2-19-05 while its paper_apply column stays UNSUPPORTED, which
        // only V2-17's real-runtime evidence may change.
        assertEquals(FeatureSupportLevelV2.SUPPORTED,
                levelOf(catalog, TerrainIntentV2.FeatureKind.RIVER, FeatureSupportCapabilityV2.EXPORT));
        assertEquals(PublicDispatchReachabilityV2.BlockMaterializationV2.MATERIALIZED,
                projection.entry(TerrainIntentV2.FeatureKind.RIVER).blockMaterialization());
        assertEquals(FeatureSupportLevelV2.EXPERIMENTAL,
                levelOf(catalog, TerrainIntentV2.FeatureKind.RIVER, FeatureSupportCapabilityV2.PAPER_APPLY));
    }

    @Test
    void unreachableKindsCarryNoPipelineAndNoMaterializationClaim() {
        PublicDispatchReachabilityV2 projection = PublicDispatchReachabilityV2.builtIn();

        Set<TerrainIntentV2.FeatureKind> unreachable =
                projection.kindsWith(PublicDispatchReachabilityV2.ReachabilityV2.NOT_PUBLICLY_DISPATCHABLE);
        assertFalse(unreachable.isEmpty());
        for (TerrainIntentV2.FeatureKind kind : unreachable) {
            assertEquals("", projection.entry(kind).pipelineId(), kind::name);
            assertEquals(PublicDispatchReachabilityV2.BlockMaterializationV2.NOT_APPLICABLE,
                    projection.entry(kind).blockMaterialization(), kind::name);
        }
    }

    @Test
    void theProjectionIsDeterministic() {
        PublicDispatchReachabilityV2 first = PublicDispatchReachabilityV2.builtIn();
        PublicDispatchReachabilityV2 second = PublicDispatchReachabilityV2.builtIn();

        assertEquals(first.canonicalLines(), second.canonicalLines());
        assertEquals(first.canonicalChecksum(), second.canonicalChecksum());
        assertEquals(PublicDispatchReachabilityV2.CONTRACT_VERSION, first.canonicalLines().getFirst());
    }

    @Test
    void documentationProjectionIsKeptInSyncByCi() throws Exception {
        String text = Files.readString(DOCUMENT, StandardCharsets.UTF_8);
        int start = text.indexOf(START);
        int end = text.indexOf(END);
        assertTrue(start >= 0 && end > start, "public dispatch reachability markers must exist");
        String documented = text.substring(start + START.length(), end).strip();
        assertEquals(PublicDispatchReachabilityV2.builtIn().documentationProjection(), documented);
    }

    private static Set<TerrainIntentV2.FeatureKind> kindsWithLevel(
            FeatureSupportCatalogV2 catalog,
            FeatureSupportCapabilityV2 capability
    ) {
        Set<TerrainIntentV2.FeatureKind> result = EnumSet.noneOf(TerrainIntentV2.FeatureKind.class);
        for (FeatureSupportEntryV2 entry : catalog.entries()) {
            if (entry.hasFeatureKind()
                    && entry.support().level(capability) == FeatureSupportLevelV2.SUPPORTED) {
                result.add(TerrainIntentV2.FeatureKind.valueOf(entry.featureKindName()));
            }
        }
        return result;
    }

    private static FeatureSupportLevelV2 levelOf(
            FeatureSupportCatalogV2 catalog,
            TerrainIntentV2.FeatureKind kind,
            FeatureSupportCapabilityV2 capability
    ) {
        return catalog.entries().stream()
                .filter(entry -> entry.hasFeatureKind() && entry.featureKindName().equals(kind.name()))
                .findFirst()
                .orElseThrow()
                .support()
                .level(capability);
    }
}
