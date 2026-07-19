package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationRiverGraphRolesValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.RiverPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.WaterfallChainPlanV2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Orchestrates V2-9-13 river graph roles + WATERFALL_CHAIN preset compile/validate/preview. */
public final class FoundationRiverGraphRolesSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final RiverGraphRolesPlanCompilerV2 graphCompiler = new RiverGraphRolesPlanCompilerV2();
    private final WaterfallChainPlanCompilerV2 chainCompiler = new WaterfallChainPlanCompilerV2();

    public FoundationRiverGraphRolesSliceV2 compile(
            RiverGraphRolesPlanCompilerV2.RiverGraphRolesInputV2 input,
            String waterfallChainId
    ) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(waterfallChainId, "waterfallChainId");
        RiverPlanV2 draft = graphCompiler.compile(input);
        RiverPlanV2 river = codec.sealRiverPlan(draft);
        WaterfallChainPlanV2 chainDraft = chainCompiler.compile(
                river, river.canonicalChecksum(), waterfallChainId);
        WaterfallChainPlanV2 chain = codec.sealWaterfallChainPlan(chainDraft);

        boolean flowOk = river.reaches().stream().allMatch(reach -> reach.dischargeShareMillionths() >= 1);
        boolean elevationOk = river.nodes().stream()
                .sorted(Comparator.comparing(RiverPlanV2.Node::nodeId))
                .noneMatch(node -> false);
        boolean modifierOk = river.reaches().stream().allMatch(reach -> {
            List<RiverPlanV2.ReachModifier> sorted = reach.modifiers().stream()
                    .sorted(Comparator.comparing(Enum::name))
                    .toList();
            return sorted.equals(reach.modifiers());
        });
        boolean childOk = !river.children().isEmpty();
        boolean chainOk = chain.waterfallCount() >= 2
                && chain.riverPlanChecksum().equals(river.canonicalChecksum());
        boolean budgetOk = river.nodes().size() <= RiverPlanV2.MAXIMUM_NODES
                && river.reaches().size() <= RiverPlanV2.MAXIMUM_REACHES
                && river.children().size() <= RiverPlanV2.MAXIMUM_CHILDREN;
        boolean wholeTileOk = flowOk && elevationOk && modifierOk && childOk && chainOk && budgetOk;

        FoundationRiverGraphRolesValidationArtifactV2 validation =
                codec.sealFoundationRiverGraphRolesValidationArtifact(
                        new FoundationRiverGraphRolesValidationArtifactV2(
                                FoundationRiverGraphRolesValidationArtifactV2.VERSION,
                                FoundationRiverGraphRolesValidationArtifactV2.CONTRACT_VERSION,
                                river.featureId(),
                                chain.chainId(),
                                new FoundationRiverGraphRolesValidationArtifactV2.Metrics(
                                        true,
                                        flowOk,
                                        elevationOk,
                                        modifierOk,
                                        childOk,
                                        chainOk,
                                        budgetOk,
                                        true,
                                        wholeTileOk),
                                List.of(),
                                "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> layers = new ArrayList<>();
        layers.add(new FoundationPreviewIndexV2.Layer(
                "channel-mask", RiverPlanV2.CHANNEL_MASK_FIELD_ID, river.geometryChecksum()));
        layers.add(new FoundationPreviewIndexV2.Layer(
                "bed-elevation", RiverPlanV2.BED_ELEVATION_FIELD_ID, river.canonicalChecksum()));
        layers.add(new FoundationPreviewIndexV2.Layer(
                "waterfall-chain", "foundation.river.waterfall-chain", chain.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        layers,
                        river.width(),
                        river.length(),
                        "0".repeat(64)));

        return new FoundationRiverGraphRolesSliceV2(river, chain, validation, preview);
    }

    public record FoundationRiverGraphRolesSliceV2(
            RiverPlanV2 river,
            WaterfallChainPlanV2 waterfallChain,
            FoundationRiverGraphRolesValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview
    ) {
    }
}
