package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.WaterfallChainPlanV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoundationRiverGraphRolesSliceCompilerV2Test {
    private static final Path MEANDERING =
            Path.of("examples/v2/hydrology/meandering-river.terrain-intent-v2.json");
    private static final Path DELTA =
            Path.of("examples/v2/hydrology/delta-distributary-fan.terrain-intent-v2.json");
    private static final Path WATERFALL =
            Path.of("examples/v2/hydrology/waterfall-2_5d-skeleton.terrain-intent-v2.json");
    private static final String GEOMETRY = "a".repeat(64);

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final FoundationRiverGraphRolesSliceCompilerV2 sliceCompiler =
            new FoundationRiverGraphRolesSliceCompilerV2();
    private final RiverGraphRolesPlanCompilerV2 graphCompiler = new RiverGraphRolesPlanCompilerV2();

    @Test
    void positiveGraphRolesAndWaterfallChainCompileDeterministically() throws Exception {
        var input = positiveInput();
        var slice = sliceCompiler.compile(input, "cascade-chain");

        assertTrue(slice.validation().metrics().wholeTileOk());
        assertTrue(slice.validation().metrics().flowConservationOk());
        assertTrue(slice.validation().metrics().elevationContinuityOk());
        assertTrue(slice.validation().metrics().modifierOrderOk());
        assertTrue(slice.validation().metrics().childOwnershipOk());
        assertTrue(slice.validation().metrics().waterfallChainOk());
        assertEquals(2, slice.waterfallChain().waterfallCount());
        assertEquals(slice.river().canonicalChecksum(), slice.waterfallChain().riverPlanChecksum());
        assertTrue(slice.river().nodes().stream()
                .anyMatch(node -> node.kind() == RiverPlanV2.NodeKind.SOURCE
                        && node.role() == RiverPlanV2.NodeRole.HEADWATER));
        assertTrue(slice.river().reaches().stream()
                .anyMatch(reach -> reach.reachClass() == RiverPlanV2.ReachClass.TRIBUTARY));
        assertTrue(slice.river().reaches().stream()
                .anyMatch(reach -> reach.reachClass() == RiverPlanV2.ReachClass.DISTRIBUTARY));
        assertTrue(slice.river().reaches().stream()
                .anyMatch(reach -> reach.reachClass() == RiverPlanV2.ReachClass.STREAM));
        assertTrue(slice.river().reaches().stream()
                .anyMatch(reach -> reach.modifiers().contains(RiverPlanV2.ReachModifier.RAPIDS)));
        assertTrue(slice.river().children().stream()
                .anyMatch(child -> child.kind() == RiverPlanV2.ChildKind.SANDBAR));
        assertTrue(slice.river().children().stream()
                .anyMatch(child -> child.kind() == RiverPlanV2.ChildKind.RIVER_ISLAND));
        assertEquals(2, slice.river().children().stream()
                .filter(child -> child.kind() == RiverPlanV2.ChildKind.PLUNGE_POOL)
                .count());

        Path riverFile = Path.of("examples/v2/foundation/river-graph-roles-plan-v2.json");
        Path chainFile = Path.of("examples/v2/foundation/waterfall-chain-plan-v2.json");
        Path validationFile = Path.of(
                "examples/v2/foundation/foundation-river-graph-roles-validation-artifact-v2.json");
        codec.writeRiverPlan(riverFile, slice.river());
        codec.writeWaterfallChainPlan(chainFile, slice.waterfallChain());
        codec.writeFoundationRiverGraphRolesValidationArtifact(validationFile, slice.validation());
        assertEquals(slice.river(), codec.readRiverPlan(riverFile));
        assertEquals(slice.waterfallChain(), codec.readWaterfallChainPlan(chainFile));
        assertEquals(slice.validation(),
                codec.readFoundationRiverGraphRolesValidationArtifact(validationFile));

        var again = sliceCompiler.compile(input, "cascade-chain");
        assertEquals(slice.river().canonicalChecksum(), again.river().canonicalChecksum());
        assertEquals(slice.waterfallChain().canonicalChecksum(), again.waterfallChain().canonicalChecksum());
    }

    @Test
    void sealedPlansStrictRoundTrip(@TempDir Path temp) throws Exception {
        var slice = sliceCompiler.compile(positiveInput(), "cascade-chain");
        Path river = temp.resolve("river.json");
        Path chain = temp.resolve("chain.json");
        Path validation = temp.resolve("validation.json");
        codec.writeRiverPlan(river, slice.river());
        codec.writeWaterfallChainPlan(chain, slice.waterfallChain());
        codec.writeFoundationRiverGraphRolesValidationArtifact(validation, slice.validation());
        assertEquals(slice.river(), codec.readRiverPlan(river));
        assertEquals(slice.waterfallChain(), codec.readWaterfallChainPlan(chain));
        assertEquals(slice.validation(), codec.readFoundationRiverGraphRolesValidationArtifact(validation));
    }

    @Test
    void rejectsFlowConservationFailure() {
        var input = positiveInput();
        List<RiverGraphRolesPlanCompilerV2.ReachSpec> broken = input.reaches().stream()
                .map(reach -> "dist-a".equals(reach.reachId())
                        ? reach("dist-a", reach.fromNodeId(), reach.toNodeId(),
                        reach.reachClass(), reach.modifiers(), 100_000)
                        : reach)
                .toList();
        var bad = copyInput(input, input.nodes(), broken, input.children());
        FoundationSliceException ex = assertThrows(FoundationSliceException.class,
                () -> graphCompiler.compile(bad));
        assertEquals(RiverGraphRolesPlanCompilerV2.RULE_FLOW, ex.ruleId());
    }

    @Test
    void rejectsElevationReversal() {
        var input = positiveInput();
        long s = 1_000_000L;
        List<RiverGraphRolesPlanCompilerV2.NodeSpec> broken = input.nodes().stream()
                .map(node -> "mouth".equals(node.nodeId())
                        ? node("mouth", node.kind(), node.role(),
                        node.xMillionths(), node.zMillionths(), 90 * s)
                        : node)
                .toList();
        var bad = copyInput(input, broken, input.reaches(), input.children());
        FoundationSliceException ex = assertThrows(FoundationSliceException.class,
                () -> graphCompiler.compile(bad));
        assertEquals(RiverGraphRolesPlanCompilerV2.RULE_ELEVATION, ex.ruleId());
    }

    @Test
    void rejectsOrphanPlungePoolOwner() {
        var input = positiveInput();
        List<RiverGraphRolesPlanCompilerV2.ChildSpec> broken = input.children().stream()
                .map(child -> "plunge-wf1".equals(child.childId())
                        ? child("plunge-wf1", child.kind(), "", "missing-node",
                        child.xMillionths(), child.zMillionths(),
                        child.radiusBlocks(), child.ownershipPriority())
                        : child)
                .toList();
        var bad = copyInput(input, input.nodes(), input.reaches(), broken);
        FoundationSliceException ex = assertThrows(FoundationSliceException.class,
                () -> graphCompiler.compile(bad));
        assertEquals(RiverGraphRolesPlanCompilerV2.RULE_CHILD, ex.ruleId());
    }

    @Test
    void rejectsWaterfallChainWithSingleFall() {
        RiverPlanV2 river = codec.sealRiverPlan(graphCompiler.compile(singleWaterfallInput()));
        FoundationSliceException ex = assertThrows(FoundationSliceException.class,
                () -> new WaterfallChainPlanCompilerV2().compile(
                        river, river.canonicalChecksum(), "too-short"));
        assertEquals(WaterfallChainPlanCompilerV2.RULE_COUNT, ex.ruleId());
    }

    @Test
    void legacyHydrologyFixturesRemainByteIdentical() throws Exception {
        assertUnchanged(MEANDERING);
        assertUnchanged(DELTA);
        assertUnchanged(WATERFALL);
    }

    @Test
    void generalRiverSliceStillCompilesWithoutRolesRequired() throws Exception {
        TerrainIntentV2 intent = codec.readTerrainIntent(
                Path.of("examples/v2/foundation/general-river-slice.terrain-intent-v2.json"));
        FoundationRiverSliceCompilerV2.FoundationRiverSliceV2 slice =
                new FoundationRiverSliceCompilerV2().compile(
                        intent, new WorldBlueprintV2.Bounds(64, 48, 0, 256, 62), 101L);
        assertEquals(1, slice.river().reaches().size());
        assertTrue(slice.river().children().isEmpty());
        assertEquals(RiverPlanV2.NodeRole.HEADWATER, slice.river().nodes().stream()
                .filter(n -> n.kind() == RiverPlanV2.NodeKind.SOURCE)
                .findFirst()
                .orElseThrow()
                .role());
        Path riverFile = Path.of("examples/v2/foundation/river-plan-v2.json");
        codec.writeRiverPlan(riverFile, slice.river());
        assertEquals(slice.river(), codec.readRiverPlan(riverFile));
    }

    @Test
    void noWaterfallChainFeatureKindIntroduced() {
        assertFalse(java.util.Arrays.stream(TerrainIntentV2.FeatureKind.values())
                .anyMatch(kind -> kind.name().contains("WATERFALL_CHAIN")
                        || kind.name().equals("HEADWATER")
                        || kind.name().equals("TRIBUTARY")
                        || kind.name().equals("DISTRIBUTARY")
                        || kind.name().equals("RAPIDS")
                        || kind.name().equals("SANDBAR")
                        || kind.name().equals("RIVER_ISLAND")
                        || kind.name().equals("PLUNGE_POOL")
                        || kind.name().equals("STREAM")));
        assertEquals("waterfall-chain-preset-contract-v1", WaterfallChainPlanV2.CONTRACT_VERSION);
    }

    private static void assertUnchanged(Path path) throws Exception {
        byte[] before = Files.readAllBytes(path);
        new LandformV2DataCodec().readTerrainIntent(path);
        byte[] after = Files.readAllBytes(path);
        assertEquals(new String(before, StandardCharsets.UTF_8), new String(after, StandardCharsets.UTF_8));
    }

    private static RiverGraphRolesPlanCompilerV2.RiverGraphRolesInputV2 positiveInput() {
        long s = 1_000_000L;
        return new RiverGraphRolesPlanCompilerV2.RiverGraphRolesInputV2(
                "role-river",
                TerrainIntentV2.DischargeClass.MEDIUM,
                List.of(
                        node("source-main", RiverPlanV2.NodeKind.SOURCE, RiverPlanV2.NodeRole.HEADWATER,
                                32 * s, 4 * s, 80 * s),
                        node("source-trib", RiverPlanV2.NodeKind.SOURCE, RiverPlanV2.NodeRole.HEADWATER,
                                8 * s, 20 * s, 79 * s),
                        node("confluence", RiverPlanV2.NodeKind.CONFLUENCE, RiverPlanV2.NodeRole.NONE,
                                32 * s, 20 * s, 78 * s),
                        node("wf1", RiverPlanV2.NodeKind.WATERFALL, RiverPlanV2.NodeRole.NONE,
                                32 * s, 26 * s, 76 * s),
                        node("wf2", RiverPlanV2.NodeKind.WATERFALL, RiverPlanV2.NodeRole.NONE,
                                32 * s, 32 * s, 70 * s),
                        node("bifurcation", RiverPlanV2.NodeKind.BIFURCATION, RiverPlanV2.NodeRole.NONE,
                                32 * s, 38 * s, 64 * s),
                        node("mouth", RiverPlanV2.NodeKind.MOUTH, RiverPlanV2.NodeRole.NONE,
                                32 * s, 44 * s, 60 * s)
                ),
                List.of(
                        reach("main-upper", "source-main", "confluence",
                                RiverPlanV2.ReachClass.MAIN_STEM, List.of(), 600_000),
                        reach("trib-stream", "source-trib", "confluence",
                                RiverPlanV2.ReachClass.TRIBUTARY, List.of(), 400_000),
                        reach("main-to-wf1", "confluence", "wf1",
                                RiverPlanV2.ReachClass.MAIN_STEM, List.of(), 1_000_000),
                        reach("fall-1", "wf1", "wf2",
                                RiverPlanV2.ReachClass.STREAM,
                                List.of(RiverPlanV2.ReachModifier.RAPIDS), 1_000_000),
                        reach("fall-2", "wf2", "bifurcation",
                                RiverPlanV2.ReachClass.MAIN_STEM, List.of(), 1_000_000),
                        reach("dist-a", "bifurcation", "mouth",
                                RiverPlanV2.ReachClass.DISTRIBUTARY, List.of(), 400_000),
                        reach("dist-b", "bifurcation", "mouth",
                                RiverPlanV2.ReachClass.DISTRIBUTARY, List.of(), 600_000)
                ),
                List.of(
                        child("plunge-wf1", RiverPlanV2.ChildKind.PLUNGE_POOL, "", "wf1",
                                32 * s, 27 * s, 4, 10),
                        child("plunge-wf2", RiverPlanV2.ChildKind.PLUNGE_POOL, "", "wf2",
                                32 * s, 33 * s, 4, 10),
                        child("sandbar-a", RiverPlanV2.ChildKind.SANDBAR, "dist-a", "",
                                30 * s, 41 * s, 3, 8),
                        child("island-b", RiverPlanV2.ChildKind.RIVER_ISLAND, "dist-b", "",
                                34 * s, 41 * s, 5, 8)
                ),
                10, 20, 1_000L, 0, 256, 62, 64, 48, 20, GEOMETRY);
    }

    private static RiverGraphRolesPlanCompilerV2.RiverGraphRolesInputV2 singleWaterfallInput() {
        long s = 1_000_000L;
        return new RiverGraphRolesPlanCompilerV2.RiverGraphRolesInputV2(
                "single-fall",
                TerrainIntentV2.DischargeClass.SMALL,
                List.of(
                        node("source-a", RiverPlanV2.NodeKind.SOURCE, RiverPlanV2.NodeRole.HEADWATER,
                                16 * s, 4 * s, 70 * s),
                        node("wf-only", RiverPlanV2.NodeKind.WATERFALL, RiverPlanV2.NodeRole.NONE,
                                16 * s, 20 * s, 66 * s),
                        node("mouth-a", RiverPlanV2.NodeKind.MOUTH, RiverPlanV2.NodeRole.NONE,
                                16 * s, 40 * s, 60 * s)
                ),
                List.of(
                        reach("r1", "source-a", "wf-only",
                                RiverPlanV2.ReachClass.MAIN_STEM, List.of(), 1_000_000),
                        reach("r2", "wf-only", "mouth-a",
                                RiverPlanV2.ReachClass.MAIN_STEM, List.of(), 1_000_000)
                ),
                List.of(
                        child("plunge-only", RiverPlanV2.ChildKind.PLUNGE_POOL, "", "wf-only",
                                16 * s, 21 * s, 3, 5)
                ),
                6, 12, 1_000L, 0, 256, 62, 32, 48, 12, GEOMETRY);
    }

    private static RiverGraphRolesPlanCompilerV2.RiverGraphRolesInputV2 copyInput(
            RiverGraphRolesPlanCompilerV2.RiverGraphRolesInputV2 base,
            List<RiverGraphRolesPlanCompilerV2.NodeSpec> nodes,
            List<RiverGraphRolesPlanCompilerV2.ReachSpec> reaches,
            List<RiverGraphRolesPlanCompilerV2.ChildSpec> children
    ) {
        return new RiverGraphRolesPlanCompilerV2.RiverGraphRolesInputV2(
                base.featureId(), base.dischargeClass(), nodes, reaches, children,
                base.bankfullWidthBlocks(), base.floodplainHandoffWidthBlocks(),
                base.minimumBedSlopeMillionths(), base.minY(), base.maxY(), base.waterLevel(),
                base.width(), base.length(), base.supportRadiusXZ(), base.geometryChecksum());
    }

    private static RiverGraphRolesPlanCompilerV2.NodeSpec node(
            String id, RiverPlanV2.NodeKind kind, RiverPlanV2.NodeRole role,
            long x, long z, long bed
    ) {
        return new RiverGraphRolesPlanCompilerV2.NodeSpec(id, kind, role, x, z, bed);
    }

    private static RiverGraphRolesPlanCompilerV2.ReachSpec reach(
            String id, String from, String to, RiverPlanV2.ReachClass reachClass,
            List<RiverPlanV2.ReachModifier> modifiers, int share
    ) {
        return new RiverGraphRolesPlanCompilerV2.ReachSpec(id, from, to, reachClass, modifiers, share);
    }

    private static RiverGraphRolesPlanCompilerV2.ChildSpec child(
            String id, RiverPlanV2.ChildKind kind, String reachOwner, String nodeOwner,
            long x, long z, int radius, int priority
    ) {
        return new RiverGraphRolesPlanCompilerV2.ChildSpec(
                id, kind, reachOwner, nodeOwner, x, z, radius, priority);
    }
}
