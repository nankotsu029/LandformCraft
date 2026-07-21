package com.github.nankotsu029.landformcraft.integration.v2;

import com.github.nankotsu029.landformcraft.core.v2.MultiSourceProposalLayerV2;
import com.github.nankotsu029.landformcraft.core.v2.MultiSourceReconciliationOptionsV2;
import com.github.nankotsu029.landformcraft.core.v2.MultiSourceReconciliationServiceV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedHeightGuideDraftArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedHeightGuidePromotionRecordCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskDraftArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskPromotionRecordCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedZoneLabelDraftArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedZoneLabelPromotionRecordCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageHeightGuideExtractorV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageLandWaterExtractorV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageZoneLabelExtractorV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.MultiSourceReconciliationArtifactCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.ImageFidelityReconcileRoleV2;
import com.github.nankotsu029.landformcraft.model.v2.ImageFidelitySourceKindV2;
import com.github.nankotsu029.landformcraft.model.v2.MultiSourceReconciliationArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.MultiSourceReconciliationStatusV2;
import com.github.nankotsu029.landformcraft.preview.v2.MultiSourceReconciliationPreviewIndexCodecV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-7 Image fidelity Phase gate. Confirms extract→draft→promote→multi-source portfolio
 * contracts and records extract as a SUPPORTED candidate only (still EXPERIMENTAL / unwired).
 */
class ImageFidelityPhaseGateV2Test {
    @Test
    void phaseGateContractsExamplesAndAudit(@TempDir Path root) throws Exception {
        assertEquals("image-land-water-extract-v1", ImageLandWaterExtractorV2.ALGORITHM_VERSION);
        assertEquals("image-height-guide-extract-v1", ImageHeightGuideExtractorV2.ALGORITHM_VERSION);
        assertEquals("image-zone-label-extract-v1", ImageZoneLabelExtractorV2.ALGORITHM_VERSION);
        assertEquals(
                MultiSourceReconciliationArtifactV2.ALGORITHM_VERSION,
                "image-fidelity-multisource-reconcile-v1");

        new ExtractedMaskDraftArtifactCodecV2()
                .read(Path.of("examples/v2/extract/extracted-mask-draft-v2.json"));
        new ExtractedMaskPromotionRecordCodecV2()
                .read(Path.of("examples/v2/extract/extracted-mask-promotion-v2.json"));
        new ExtractedHeightGuideDraftArtifactCodecV2()
                .read(Path.of("examples/v2/extract/extracted-height-guide-draft-v2.json"));
        new ExtractedHeightGuidePromotionRecordCodecV2()
                .read(Path.of("examples/v2/extract/extracted-height-guide-promotion-v2.json"));
        new ExtractedZoneLabelDraftArtifactCodecV2()
                .read(Path.of("examples/v2/extract/extracted-zone-label-draft-v2.json"));
        new ExtractedZoneLabelPromotionRecordCodecV2()
                .read(Path.of("examples/v2/extract/extracted-zone-label-promotion-v2.json"));
        new MultiSourceReconciliationArtifactCodecV2()
                .read(Path.of("examples/v2/extract/multi-source-reconciliation-v2.json"));
        new MultiSourceReconciliationPreviewIndexCodecV2()
                .read(Path.of("examples/v2/extract/multi-source-reconciliation-preview-index-v2.json"));

        byte[] image = new byte[]{0, 0, (byte) 255, 1};
        byte[] prompt = new byte[]{1, 0, 1, 1};
        var options = new MultiSourceReconciliationOptionsV2(
                ImageFidelityReconcileRoleV2.LAND_WATER_MASK,
                MultiSourceReconciliationOptionsV2.DEFAULT_NODATA,
                List.of(
                        new MultiSourceProposalLayerV2(
                                "image.gate",
                                ImageFidelitySourceKindV2.IMAGE_DRAFT,
                                ImageFidelitySourceKindV2.IMAGE_DRAFT.defaultStrength(),
                                255,
                                image),
                        new MultiSourceProposalLayerV2(
                                "prompt.gate",
                                ImageFidelitySourceKindV2.PROMPT_SOFT,
                                ImageFidelitySourceKindV2.PROMPT_SOFT.defaultStrength(),
                                255,
                                prompt)));
        MultiSourceReconciliationServiceV2 service = new MultiSourceReconciliationServiceV2();
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            String expected = service.reconcile(2, 2, options, () -> false).semanticChecksum();
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));
            var second = service.reconcile(2, 2, options, () -> false);
            assertEquals(expected, second.semanticChecksum());
            assertEquals(MultiSourceReconciliationStatusV2.RESOLVED, second.status());
            assertEquals(0, Byte.toUnsignedInt(second.result()[0])); // image beats prompt
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }

        Path published = root.resolve("gate-artifact");
        MultiSourceReconciliationArtifactV2 artifact = service.reconcileAndPublish(
                published, 2, 2, options, () -> false);
        assertEquals(MultiSourceReconciliationStatusV2.RESOLVED, artifact.status());
        assertTrue(artifact.absoluteDiffSum() >= 0);

        Path audit = Path.of("docs/design-v2/audits/v2-7-phase-gate.md");
        assertTrue(Files.isRegularFile(audit));
        String text = Files.readString(audit);
        assertTrue(text.contains("Status: PASS"));
        assertTrue(text.contains("SUPPORTED candidate"));
        assertTrue(text.contains("EXPERIMENTAL"));
        assertTrue(text.contains("未接続") || text.contains("unwired") || text.contains("CLI"));
        assertFalse(text.contains("Release capability を SUPPORTED に昇格"));
        assertFalse(text.contains("CLI／Paper／Request へ接続済み"));
    }
}
