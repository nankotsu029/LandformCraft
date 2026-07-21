package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedHeightGuideDraftArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedHeightGuideDraftArtifactPublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedHeightGuideDraftV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskDraftArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskDraftArtifactPublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskDraftV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedZoneLabelDraftArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedZoneLabelDraftArtifactPublisherV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedZoneLabelDraftV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageExtractionInputLimitsV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageMaskExtractionLimitsV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.SecureImageExtractionEnvelopeV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedHeightGuideDraftArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedHeightGuidePromotionRecordV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedMaskDraftArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedMaskPromotionRecordV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedZoneLabelDraftArtifactV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedZoneLabelPromotionRecordV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Surface-independent backend that makes the V2-7 deterministic extraction path reachable from the
 * official command surface (V2-14-01).
 *
 * <p>Extraction runs the untrusted image through {@link SecureImageExtractionEnvelopeV2} into one of
 * the three V2-7 draft cores and publishes the sealed draft bundle. Promotion re-loads a published
 * draft strictly and hands it to the matching V2-7 promotion service, which re-enters the V2-1 strict
 * decoder before atomic publish. Nothing here promotes implicitly: every promotion carries the
 * operator's explicit confidence threshold and unknown/height/zone handling, exactly as the V2-7
 * services require.</p>
 *
 * <p>The service holds no world, executor, or AI dependency and never guesses a coordinate mapping,
 * value meaning, or label alphabet. Callers pass already-resolved absolute paths; sandboxing the
 * operator's image path is the envelope's job and sandboxing the request slug is the store's job.</p>
 */
public final class ImageExtractionWorkflowServiceV2 {
    /** Synthetic anchor name; the envelope only uses its parent directory as the request root. */
    private static final String REQUEST_ROOT_ANCHOR = "_extract-request-anchor";

    private final SecureImageExtractionEnvelopeV2 envelope = new SecureImageExtractionEnvelopeV2();
    private final ExtractedMaskDraftArtifactPublisherV2 maskPublisher =
            new ExtractedMaskDraftArtifactPublisherV2();
    private final ExtractedHeightGuideDraftArtifactPublisherV2 heightPublisher =
            new ExtractedHeightGuideDraftArtifactPublisherV2();
    private final ExtractedZoneLabelDraftArtifactPublisherV2 zonePublisher =
            new ExtractedZoneLabelDraftArtifactPublisherV2();
    private final ExtractedMaskDraftArtifactCodecV2 maskDraftCodec =
            new ExtractedMaskDraftArtifactCodecV2();
    private final ExtractedHeightGuideDraftArtifactCodecV2 heightDraftCodec =
            new ExtractedHeightGuideDraftArtifactCodecV2();
    private final ExtractedZoneLabelDraftArtifactCodecV2 zoneDraftCodec =
            new ExtractedZoneLabelDraftArtifactCodecV2();
    private final ExtractedMaskPromotionServiceV2 maskPromotion =
            new ExtractedMaskPromotionServiceV2();
    private final ExtractedHeightGuidePromotionServiceV2 heightPromotion =
            new ExtractedHeightGuidePromotionServiceV2();
    private final ExtractedZoneLabelPromotionServiceV2 zonePromotion =
            new ExtractedZoneLabelPromotionServiceV2();

    private final ImageExtractionInputLimitsV2 inputLimits = ImageExtractionInputLimitsV2.defaults();
    private final ImageMaskExtractionLimitsV2 extractLimits = ImageMaskExtractionLimitsV2.defaults();

    // --- extraction: untrusted image → sealed draft bundle -------------------------------------

    /** Extracts a land/water mask draft and publishes it as a sealed bundle in {@code draftDirectory}. */
    public ExtractedMaskDraftArtifactV2 extractLandWater(
            Path imageFile, Path draftDirectory, CancellationToken cancellationToken) throws IOException {
        ResolvedImage image = resolveImage(imageFile);
        ExtractedMaskDraftV2 draft = envelope.loadAndExtractLandWater(
                image.anchor(), image.relativePath(), inputLimits, extractLimits, asSupplier(cancellationToken));
        return maskPublisher.publish(draftDirectory, draft, image.relativePath(), cancellationToken);
    }

    /** Extracts a height-guide draft and publishes it as a sealed bundle in {@code draftDirectory}. */
    public ExtractedHeightGuideDraftArtifactV2 extractHeightGuide(
            Path imageFile, Path draftDirectory, CancellationToken cancellationToken) throws IOException {
        ResolvedImage image = resolveImage(imageFile);
        ExtractedHeightGuideDraftV2 draft = envelope.loadAndExtractHeightGuide(
                image.anchor(), image.relativePath(), inputLimits, extractLimits, asSupplier(cancellationToken));
        return heightPublisher.publish(draftDirectory, draft, image.relativePath(), cancellationToken);
    }

    /** Extracts a zone-label draft and publishes it as a sealed bundle in {@code draftDirectory}. */
    public ExtractedZoneLabelDraftArtifactV2 extractZoneLabel(
            Path imageFile, Path draftDirectory, CancellationToken cancellationToken) throws IOException {
        ResolvedImage image = resolveImage(imageFile);
        ExtractedZoneLabelDraftV2 draft = envelope.loadAndExtractZoneLabel(
                image.anchor(), image.relativePath(), inputLimits, extractLimits, asSupplier(cancellationToken));
        return zonePublisher.publish(draftDirectory, draft, image.relativePath(), cancellationToken);
    }

    // --- promotion: sealed draft bundle → V2-1 constraint map ----------------------------------

    /** Loads a published land/water draft and promotes it to a categorical constraint map. */
    public ExtractedMaskPromotionRecordV2 promoteLandWater(
            Path draftDirectory,
            Path targetDirectory,
            ExtractedMaskPromotionOptionsV2 options,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(options, "options");
        ExtractedMaskDraftV2 draft = maskDraftCodec.loadDraft(
                draftDirectory.resolve(ExtractedMaskDraftArtifactCodecV2.INDEX_FILE_NAME),
                draftDirectory, cancellationToken);
        return maskPromotion.promote(targetDirectory, draft, options, cancellationToken);
    }

    /** Loads a published height-guide draft and promotes it to a HEIGHT_RASTER constraint map. */
    public ExtractedHeightGuidePromotionRecordV2 promoteHeightGuide(
            Path draftDirectory,
            Path targetDirectory,
            ExtractedHeightGuidePromotionOptionsV2 options,
            GenerationRequestV2.Bounds bounds,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(bounds, "bounds");
        ExtractedHeightGuideDraftV2 draft = heightDraftCodec.loadDraft(
                draftDirectory.resolve(ExtractedHeightGuideDraftArtifactCodecV2.INDEX_FILE_NAME),
                draftDirectory, cancellationToken);
        return heightPromotion.promote(targetDirectory, draft, options, bounds, cancellationToken);
    }

    /** Loads a published zone-label draft and promotes it to a ZONE_LABEL_MAP constraint map. */
    public ExtractedZoneLabelPromotionRecordV2 promoteZoneLabel(
            Path draftDirectory,
            Path targetDirectory,
            ExtractedZoneLabelPromotionOptionsV2 options,
            GenerationRequestV2.Bounds bounds,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(bounds, "bounds");
        ExtractedZoneLabelDraftV2 draft = zoneDraftCodec.loadDraft(
                draftDirectory.resolve(ExtractedZoneLabelDraftArtifactCodecV2.INDEX_FILE_NAME),
                draftDirectory, cancellationToken);
        return zonePromotion.promote(targetDirectory, draft, options, bounds, cancellationToken);
    }

    /**
     * Splits an operator-supplied image path into the request root (its parent directory) and a
     * portable relative name, so the envelope's path/link/portability checks apply unchanged. The
     * anchor file is never opened — the envelope resolves images relative to {@code anchor.getParent()}.
     */
    private static ResolvedImage resolveImage(Path imageFile) {
        Objects.requireNonNull(imageFile, "imageFile");
        Path absolute = imageFile.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("extraction image path must have a parent directory");
        }
        Path fileName = absolute.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("extraction image path must name a file");
        }
        return new ResolvedImage(parent.resolve(REQUEST_ROOT_ANCHOR), fileName.toString());
    }

    private static java.util.function.BooleanSupplier asSupplier(CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        return cancellationToken::isCancellationRequested;
    }

    private record ResolvedImage(Path anchor, String relativePath) {
    }
}
