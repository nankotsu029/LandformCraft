package com.github.nankotsu029.landformcraft.core.v2.design;

import com.github.nankotsu029.landformcraft.ai.spi.v2.DesignExceptionV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.DesignFailureCodeV2;
import com.github.nankotsu029.landformcraft.model.v2.design.SoftDraftConfirmationStateV2;
import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskDraftV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageLandWaterExtractorV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageMaskExtractionLimitsV2;
import com.github.nankotsu029.landformcraft.model.v2.design.ImageDraftEvidenceV2;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Soft reference-image draft boundary. Never writes constraint maps or HARD geometry; promotion
 * to HARD maps is explicitly forbidden.
 */
public final class ReferenceImageSoftDraftServiceV2 {

    public record ExtractionResult(ExtractedMaskDraftV2 draft, ImageDraftEvidenceV2 evidence) {
        public ExtractionResult {
            Objects.requireNonNull(draft, "draft");
            Objects.requireNonNull(evidence, "evidence");
        }
    }

    public ExtractionResult extract(
            int width,
            int length,
            int[] argbPixels,
            String sourceChecksum,
            ImageMaskExtractionLimitsV2 limits,
            CancellationToken cancellationToken,
            String sourceRelativePath
    ) {
        BooleanSupplier cancelled = cancellationToken == null
                ? () -> false
                : cancellationToken::isCancellationRequested;
        ExtractedMaskDraftV2 draft = ImageLandWaterExtractorV2.extract(
                width, length, argbPixels, sourceChecksum, limits, cancelled);
        ImageDraftEvidenceV2 evidence = new ImageDraftEvidenceV2(
                ImageDraftEvidenceV2.VERSION,
                draft.algorithmVersion(),
                draft.sourceChecksum(),
                draft.semanticChecksum(),
                SoftDraftConfirmationStateV2.UNCONFIRMED,
                draft.width(),
                draft.length(),
                draft.waterCells(),
                draft.landCells(),
                draft.unknownCells(),
                sourceRelativePath
        );
        return new ExtractionResult(draft, evidence);
    }

    public ImageDraftEvidenceV2 confirmSoft(ImageDraftEvidenceV2 evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.confirmationState() == SoftDraftConfirmationStateV2.REJECTED) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.DRAFT_NOT_CONFIRMED,
                    "rejected soft draft cannot be confirmed");
        }
        return evidence.withConfirmationState(SoftDraftConfirmationStateV2.CONFIRMED_SOFT);
    }

    public ImageDraftEvidenceV2 reject(ImageDraftEvidenceV2 evidence) {
        Objects.requireNonNull(evidence, "evidence");
        return evidence.withConfirmationState(SoftDraftConfirmationStateV2.REJECTED);
    }

    /**
     * Guard used by callers that must never treat a soft draft as a HARD constraint source.
     * Soft confirmation states are allowed; there is no HARD state on this evidence type.
     */
    public void assertSoftOnly(ImageDraftEvidenceV2 evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.confirmationState() == SoftDraftConfirmationStateV2.REJECTED) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.DRAFT_NOT_CONFIRMED,
                    "rejected soft draft cannot enter the design package as an active constraint");
        }
    }

    /** Explicit refusal API for any attempt to promote a soft draft into HARD mapReferences. */
    public void forbidHardPromotion(ImageDraftEvidenceV2 evidence) {
        Objects.requireNonNull(evidence, "evidence");
        throw new DesignExceptionV2(
                DesignFailureCodeV2.HARD_PROMOTION_FORBIDDEN,
                "image draft evidence remains soft-only and cannot become a HARD constraint map");
    }
}
