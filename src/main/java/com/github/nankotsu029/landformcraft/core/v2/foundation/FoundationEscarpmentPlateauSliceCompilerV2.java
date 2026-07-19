package com.github.nankotsu029.landformcraft.core.v2.foundation;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.escarpment.EscarpmentGeneratorV2;
import com.github.nankotsu029.landformcraft.generator.v2.foundation.plateau.PlateauGeneratorV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.DryLandModifierContractV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.EscarpmentPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationEscarpmentPlateauValidationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.FoundationPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.foundation.PlateauPlanV2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Orchestrates V2-10-06 escarpment/plateau vertical slice compile, validation, and preview export. */
public final class FoundationEscarpmentPlateauSliceCompilerV2 {
    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final EscarpmentPlanCompilerV2 escarpmentCompiler = new EscarpmentPlanCompilerV2();
    private final PlateauPlanCompilerV2 plateauCompiler = new PlateauPlanCompilerV2();

    public FoundationEscarpmentPlateauSliceV2 compile(
            TerrainIntentV2 intent,
            WorldBlueprintV2.Bounds bounds,
            long globalSeed
    ) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(Long.valueOf(globalSeed), "globalSeed");

        TerrainIntentV2.Feature escarpmentFeature = requireFeature(intent, TerrainIntentV2.FeatureKind.ESCARPMENT);
        TerrainIntentV2.Feature plateauFeature = requireFeature(intent, TerrainIntentV2.FeatureKind.PLATEAU);
        boolean transitionOk = resolveTransitionOk(intent, escarpmentFeature.id(), plateauFeature.id());

        EscarpmentPlanV2 escarpmentPlan = codec.sealEscarpmentPlan(escarpmentCompiler.compile(
                escarpmentFeature, intent, bounds, codec.geometryChecksum(escarpmentFeature.geometry())));
        PlateauPlanV2 plateauPlan = codec.sealPlateauPlan(plateauCompiler.compile(
                plateauFeature, intent, bounds, codec.geometryChecksum(plateauFeature.geometry())));
        DryLandModifierContractV2 dryLandContract = compileDryLandModifiers();

        EscarpmentGeneratorV2 escarpmentGenerator = new EscarpmentGeneratorV2(escarpmentPlan);
        PlateauGeneratorV2 plateauGenerator = new PlateauGeneratorV2(plateauPlan);
        EscarpmentGeneratorV2.EscarpmentMetrics escarpmentMetrics = escarpmentGenerator.evaluate();
        PlateauGeneratorV2.PlateauMetrics plateauMetrics = plateauGenerator.evaluate();

        FoundationEscarpmentPlateauValidationArtifactV2.Metrics metrics =
                new FoundationEscarpmentPlateauValidationArtifactV2.Metrics(
                        escarpmentMetrics.longScarpOwnerOk(),
                        escarpmentMetrics.capFloorTalusOk() && plateauMetrics.capOwnerOk(),
                        transitionOk,
                        escarpmentMetrics.materialHandoffOk() && plateauMetrics.materialHandoffOk(),
                        escarpmentMetrics.wholeTileOk() && plateauMetrics.wholeTileOk(),
                        escarpmentMetrics.budgetOk() && plateauMetrics.budgetOk(),
                        escarpmentMetrics.exportOk() && plateauMetrics.exportOk(),
                        dryLandModifiersSeparated(dryLandContract));
        if (!allMetrics(metrics)) {
            throw metricsFailure(metrics);
        }

        FoundationEscarpmentPlateauValidationArtifactV2 validation = codec.sealFoundationEscarpmentPlateauValidationArtifact(
                new FoundationEscarpmentPlateauValidationArtifactV2(
                        FoundationEscarpmentPlateauValidationArtifactV2.VERSION,
                        FoundationEscarpmentPlateauValidationArtifactV2.CONTRACT_VERSION,
                        escarpmentPlan.featureId(),
                        plateauPlan.featureId(),
                        metrics,
                        List.of(),
                        "0".repeat(64)));

        List<FoundationPreviewIndexV2.Layer> previewLayers = new ArrayList<>();
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "escarpment-face", EscarpmentPlanV2.FACE_MASK_FIELD_ID, escarpmentPlan.canonicalChecksum()));
        previewLayers.add(new FoundationPreviewIndexV2.Layer(
                "plateau-cap", PlateauPlanV2.CAP_MASK_FIELD_ID, plateauPlan.canonicalChecksum()));
        FoundationPreviewIndexV2 preview = codec.sealFoundationPreviewIndex(
                new FoundationPreviewIndexV2(
                        FoundationPreviewIndexV2.VERSION,
                        FoundationPreviewIndexV2.CONTRACT_VERSION,
                        previewLayers,
                        bounds.width(),
                        bounds.length(),
                        "0".repeat(64)));

        String exportChecksum = combinedExportChecksum(
                escarpmentGenerator.exportChecksum(), plateauGenerator.exportChecksum());
        return new FoundationEscarpmentPlateauSliceV2(
                escarpmentPlan, plateauPlan, dryLandContract, validation, preview, exportChecksum);
    }

    public DryLandModifierContractV2 compileDryLandModifiers() {
        return codec.sealDryLandModifierContract(DryLandModifierContractV2.decisionV21006());
    }

    private static TerrainIntentV2.Feature requireFeature(
            TerrainIntentV2 intent,
            TerrainIntentV2.FeatureKind kind
    ) {
        return intent.features().stream()
                .filter(feature -> feature.kind() == kind)
                .findFirst()
                .orElseThrow(() -> new FoundationSliceException("v2.foundation-slice-empty",
                        "escarpment/plateau slice requires a " + kind + " feature"));
    }

    private static boolean resolveTransitionOk(
            TerrainIntentV2 intent,
            String escarpmentId,
            String plateauId
    ) {
        List<TerrainIntentV2.Relation> relations = intent.relations().stream()
                .filter(relation -> relation.kind() == TerrainIntentV2.RelationKind.OVERLAPS)
                .filter(relation -> relation.strength() == TerrainIntentV2.Strength.HARD)
                .filter(relation -> references(relation.from(), escarpmentId, plateauId)
                        && references(relation.to(), escarpmentId, plateauId))
                .toList();
        if (relations.size() != 1) {
            throw new FoundationSliceException("v2.escarpment-plateau-missing-transition",
                    "escarpment/plateau slice requires exactly one HARD OVERLAPS relation");
        }
        TerrainIntentV2.Relation relation = relations.getFirst();
        return relation.transition() != null
                && relation.transition().profile() == TerrainIntentV2.TransitionProfile.PRIORITY_BLEND
                && relation.transition().bandBlocks() >= 2;
    }

    private static boolean references(String endpoint, String firstId, String secondId) {
        String featureRef = endpoint.startsWith("feature:") ? endpoint.substring("feature:".length()) : "";
        return featureRef.equals(firstId) || featureRef.equals(secondId);
    }

    private static boolean dryLandModifiersSeparated(DryLandModifierContractV2 contract) {
        return contract.modifiers().stream()
                .noneMatch(modifier -> DryLandModifierContractV2.isForbiddenFeatureKindName(modifier.kind()));
    }

    private static boolean allMetrics(FoundationEscarpmentPlateauValidationArtifactV2.Metrics metrics) {
        return metrics.longScarpOwnerOk()
                && metrics.capFloorTalusOk()
                && metrics.transitionOk()
                && metrics.materialHandoffOk()
                && metrics.wholeTileOk()
                && metrics.budgetOk()
                && metrics.exportOk()
                && metrics.dryLandModifiersSeparated();
    }

    private static FoundationSliceException metricsFailure(
            FoundationEscarpmentPlateauValidationArtifactV2.Metrics metrics
    ) {
        return new FoundationSliceException("v2.escarpment-plateau-metrics",
                "escarpment/plateau metrics failed: " + metrics);
    }

    private static String combinedExportChecksum(String escarpmentExport, String plateauExport) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(escarpmentExport.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '|');
            digest.update(plateauExport.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record FoundationEscarpmentPlateauSliceV2(
            EscarpmentPlanV2 escarpment,
            PlateauPlanV2 plateau,
            DryLandModifierContractV2 dryLandContract,
            FoundationEscarpmentPlateauValidationArtifactV2 validation,
            FoundationPreviewIndexV2 preview,
            String exportChecksum
    ) {
    }
}
