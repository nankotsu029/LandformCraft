package com.github.nankotsu029.landformcraft.core.v2.design;

import com.github.nankotsu029.landformcraft.ai.spi.v2.DesignExceptionV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.DesignFailureCodeV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.PreparedReferenceImageV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.TerrainDesignRequestV2;
import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageExtractionInputExceptionV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ImageExtractionInputLimitsV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.SanitizedArgbImageV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.SecureImageExtractionEnvelopeV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.nio.file.Path;

/**
 * Turns the reference images a v2 request declares into provider-ready handles (V2-19-03).
 *
 * <p>Before this existed the design orchestrator handed providers an empty image list, which
 * {@link TerrainDesignRequestV2} rejects whenever the request declares any image — the public
 * design path failed for every image-carrying request. Preparation now runs for every design path
 * so import and fixture runs validate the same declared inputs the HTTP paths submit.</p>
 *
 * <p>Untrusted bytes go through the shared {@link SecureImageExtractionEnvelopeV2}: request-relative
 * resolve, symlink and hard-link alias rejection, magic/extension agreement, single frame, source
 * byte, dimension, aspect, pixel and decode working-set budgets, and EXIF orientation applied to the
 * raster. Provider payload bytes are then re-encoded as PNG <em>from the sanitized raster only</em>,
 * so EXIF, XMP, colour profiles, comments and any other ancillary metadata in the source file cannot
 * reach a provider or a log; the filesystem path never leaves this service either. Handles are built
 * in the request's canonical order, one per declared image.</p>
 */
public final class ReferenceImagePreparationServiceV2 {
    /** Aggregate ceiling for the bytes actually submitted, mirrored from the provider request. */
    public static final long MAX_PREPARED_TOTAL_BYTES = TerrainDesignRequestV2.MAX_TOTAL_IMAGE_BYTES;
    /** Per-image ceiling accepted by {@link PreparedReferenceImageV2}. */
    public static final int MAX_PREPARED_IMAGE_BYTES = 16 * 1024 * 1024;
    /** Media type of every prepared handle: the raster is always re-encoded, never passed through. */
    public static final String PREPARED_MEDIA_TYPE = "image/png";

    private final SecureImageExtractionEnvelopeV2 envelope;
    private final ImageExtractionInputLimitsV2 limits;

    public ReferenceImagePreparationServiceV2() {
        this(new SecureImageExtractionEnvelopeV2(), ImageExtractionInputLimitsV2.defaults());
    }

    public ReferenceImagePreparationServiceV2(
            SecureImageExtractionEnvelopeV2 envelope,
            ImageExtractionInputLimitsV2 limits
    ) {
        this.envelope = Objects.requireNonNull(envelope, "envelope");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /**
     * Prepares every declared reference image, in request order, resolved beside {@code requestPath}.
     * Returns an empty list when the request declares none, which keeps image-free requests on the
     * exact byte-for-byte path they had before this service existed.
     *
     * @throws DesignExceptionV2 with a stable failure code when an image is missing, unsafe,
     *         mis-declared, over budget, or not a supported encoding
     * @throws CancellationException when the caller cancels; nothing is published or written
     */
    public List<PreparedReferenceImageV2> prepare(
            Path requestPath,
            GenerationRequestV2 request,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(requestPath, "requestPath");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        List<GenerationRequestV2.ReferenceImageSource> declared = request.referenceImages();
        if (declared.isEmpty()) {
            return List.of();
        }
        cancellationToken.throwIfCancellationRequested();

        List<SanitizedArgbImageV2> sanitized = load(requestPath, declared, cancellationToken);
        List<PreparedReferenceImageV2> prepared = new ArrayList<>(declared.size());
        long totalBytes = 0L;
        for (int index = 0; index < declared.size(); index++) {
            cancellationToken.throwIfCancellationRequested();
            GenerationRequestV2.ReferenceImageSource source = declared.get(index);
            SanitizedArgbImageV2 image = sanitized.get(index);
            requireDeclaredDigest(source, image);
            byte[] content = encodePng(image, source.file());
            if (content.length > MAX_PREPARED_IMAGE_BYTES) {
                throw failure(DesignFailureCodeV2.BUDGET_EXCEEDED,
                        "prepared reference image exceeds the per-image submission limit: " + source.file());
            }
            totalBytes += content.length;
            if (totalBytes > MAX_PREPARED_TOTAL_BYTES) {
                throw failure(DesignFailureCodeV2.BUDGET_EXCEEDED,
                        "prepared reference images exceed the total submission limit");
            }
            prepared.add(new PreparedReferenceImageV2(
                    index,
                    source.file(),
                    source.role(),
                    PREPARED_MEDIA_TYPE,
                    image.width(),
                    image.length(),
                    Sha256.bytes(content),
                    content));
        }
        cancellationToken.throwIfCancellationRequested();
        return List.copyOf(prepared);
    }

    private List<SanitizedArgbImageV2> load(
            Path requestPath,
            List<GenerationRequestV2.ReferenceImageSource> declared,
            CancellationToken cancellationToken
    ) {
        List<String> relativePaths = declared.stream()
                .map(GenerationRequestV2.ReferenceImageSource::file)
                .toList();
        try {
            return envelope.load(
                    requestPath, relativePaths, limits, cancellationToken::isCancellationRequested);
        } catch (ImageExtractionInputExceptionV2 exception) {
            throw new DesignExceptionV2(designCode(exception), exception.getMessage(), exception);
        }
    }

    private static void requireDeclaredDigest(
            GenerationRequestV2.ReferenceImageSource source,
            SanitizedArgbImageV2 image
    ) {
        source.expectedSha256().ifPresent(expected -> {
            if (!expected.equals(image.sourceChecksum())) {
                // The digests themselves are safe to omit: the operator can recompute them, and the
                // message must stay useful in a log without describing file contents.
                throw failure(DesignFailureCodeV2.INVALID_REQUEST,
                        "reference image bytes do not match the declared expectedSha256: " + source.file());
            }
        });
    }

    /**
     * Re-encodes the sanitized raster as PNG. Only pixels survive, which is what keeps source
     * metadata out of the provider payload; the resulting bytes are the submitted content.
     */
    private static byte[] encodePng(SanitizedArgbImageV2 image, String relativePath) {
        BufferedImage target = new BufferedImage(
                image.width(), image.length(), BufferedImage.TYPE_INT_ARGB);
        target.setRGB(0, 0, image.width(), image.length(), image.argbPixels(), 0, image.width());
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            if (!ImageIO.write(target, "png", buffer)) {
                throw failure(DesignFailureCodeV2.INVALID_REQUEST,
                        "no PNG encoder is available for the prepared reference image: " + relativePath);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new DesignExceptionV2(
                    DesignFailureCodeV2.INVALID_REQUEST,
                    "failed to re-encode the reference image: " + relativePath,
                    exception);
        }
    }

    private static DesignFailureCodeV2 designCode(ImageExtractionInputExceptionV2 exception) {
        return switch (exception.failureCode()) {
            case UNSAFE_PATH, HARD_LINK_ALIAS, INVALID_PATH_DESCRIPTOR -> DesignFailureCodeV2.PATH_SECURITY;
            case FILE_TOO_LARGE, TOTAL_BYTES_EXCEEDED, DIMENSIONS_EXCEEDED, ASPECT_RATIO_EXCEEDED,
                 PIXELS_EXCEEDED, DECODE_BUDGET_EXCEEDED, WORKING_BUDGET_EXCEEDED ->
                    DesignFailureCodeV2.BUDGET_EXCEEDED;
            case MISSING_FILE, SOURCE_CHANGED, UNSUPPORTED_FORMAT, INVALID_MAGIC, CORRUPT_IMAGE,
                 MULTI_FRAME -> DesignFailureCodeV2.INVALID_REQUEST;
        };
    }

    private static DesignExceptionV2 failure(DesignFailureCodeV2 code, String message) {
        return new DesignExceptionV2(code, message);
    }
}
