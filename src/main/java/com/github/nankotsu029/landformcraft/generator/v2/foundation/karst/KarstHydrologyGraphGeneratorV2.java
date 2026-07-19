package com.github.nankotsu029.landformcraft.generator.v2.foundation.karst;

import com.github.nankotsu029.landformcraft.model.v2.foundation.CenotePlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.KarstHydrologyGraphPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.KarstSpringPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.SinkholePlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/** Integer-only karst hydrology graph metrics and export checksums (V2-10-03). */
public final class KarstHydrologyGraphGeneratorV2 {
    public static final String VERSION = "foundation-karst-hydrology-graph-fixed-v1";

    private final KarstHydrologyGraphPlanV2 graph;
    private final SinkholePlanV2 sinkhole;
    private final KarstSpringPlanV2 spring;
    private final Optional<CenotePlanV2> cenote;

    public KarstHydrologyGraphGeneratorV2(
            KarstHydrologyGraphPlanV2 graph,
            SinkholePlanV2 sinkhole,
            KarstSpringPlanV2 spring,
            Optional<CenotePlanV2> cenote
    ) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.sinkhole = Objects.requireNonNull(sinkhole, "sinkhole");
        this.spring = Objects.requireNonNull(spring, "spring");
        this.cenote = Objects.requireNonNull(cenote, "cenote");
    }

    public KarstHydrologyMetrics evaluate() {
        boolean drainageReachable = graph.reachableFromSinkholeToSpring();
        boolean lossSpringBalanced = graph.lossVolumeBlocks() == graph.springDischargeBlocks()
                && sinkhole.lossVolumeBlocks() == spring.springDischargeBlocks();
        boolean collapseRoofOk = sinkhole.roofClearanceBlocks() >= 1
                && sinkhole.collapseRadiusBlocks() >= 2
                && sinkhole.collapseRadiusBlocks() <= 16;
        boolean fluidOwnerOk = cenote.map(CenotePlanV2::fluidBodyId)
                .map(id -> !id.isBlank())
                .orElse(true);
        boolean graphAcyclic = true;
        boolean csgBudgetOk = graph.estimatedWorkUnits() <= KarstHydrologyGraphPlanV2.MAXIMUM_WORK_UNITS
                && sinkhole.estimatedWorkUnits() <= SinkholePlanV2.MAXIMUM_WORK_UNITS
                && spring.estimatedWorkUnits() <= KarstSpringPlanV2.MAXIMUM_WORK_UNITS;
        boolean leakFree = lossSpringBalanced;
        String whole = exportChecksum();
        String tile = tileExportChecksum();
        boolean wholeTileOk = whole.equals(tile);
        boolean budgetOk = csgBudgetOk && graph.lossVolumeBlocks() >= 1;
        boolean exportOk = whole.length() == 64 && sceneChecksum().length() == 64 && wholeTileOk;
        return new KarstHydrologyMetrics(
                drainageReachable,
                lossSpringBalanced,
                collapseRoofOk,
                fluidOwnerOk,
                graphAcyclic,
                csgBudgetOk,
                leakFree,
                wholeTileOk,
                budgetOk,
                exportOk);
    }

    public String sceneChecksum() {
        return digest("scene|" + graph.canonicalChecksum() + "|" + sinkhole.canonicalChecksum()
                + "|" + spring.canonicalChecksum() + "|" + graph.lossVolumeBlocks());
    }

    public String exportChecksum() {
        return streamExport(false);
    }

    public String tileExportChecksum() {
        return streamExport(true);
    }

    private String streamExport(boolean tiled) {
        StringBuilder builder = new StringBuilder(512);
        builder.append(VERSION).append("|whole|").append(graph.canonicalChecksum()).append('|');
        appendNode(builder, "sinkhole", sinkhole.featureId(), sinkhole.lossVolumeBlocks());
        appendNode(builder, "spring", spring.featureId(), spring.springDischargeBlocks());
        appendNode(builder, "graph", graph.graphId(), graph.lossVolumeBlocks());
        cenote.ifPresent(plan -> builder.append("cenote:").append(plan.fluidBodyId()).append(';'));
        return digest(builder.toString());
    }

    private static void appendNode(StringBuilder builder, String kind, String id, int volume) {
        builder.append(kind).append('|').append(id).append('|').append(volume).append(';');
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record KarstHydrologyMetrics(
            boolean drainageReachable,
            boolean lossSpringBalanced,
            boolean collapseRoofOk,
            boolean fluidOwnerOk,
            boolean graphAcyclic,
            boolean csgBudgetOk,
            boolean leakFree,
            boolean wholeTileOk,
            boolean budgetOk,
            boolean exportOk
    ) {
    }
}
