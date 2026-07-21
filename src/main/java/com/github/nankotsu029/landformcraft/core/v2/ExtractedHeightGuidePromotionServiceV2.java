package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapDecodeLimits;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapInputException;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapSourceSpec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.DecodedNumericRaster;
import com.github.nankotsu029.landformcraft.format.v2.constraint.LoadedConstraintMapSource;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngDecoder;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngEncoder;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngEncoding;
import com.github.nankotsu029.landformcraft.format.v2.constraint.SecureConstraintMapSourceLoader;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedHeightGuideDraftV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedHeightGuidePromotionRecordCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedHeightGuidePromotionRecordV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Explicit draft→numeric height-guide PNG promotion. Requires confidence threshold and one of the
 * three V2-1 {@link GenerationRequestV2.HeightValueMeaning} values; re-enters the strict decoder and
 * checks residual consistency before atomic publish.
 */
public final class ExtractedHeightGuidePromotionServiceV2 {
    private final NumericPngEncoder encoder = new NumericPngEncoder();
    private final NumericPngDecoder decoder = new NumericPngDecoder();
    private final SecureConstraintMapSourceLoader loader = new SecureConstraintMapSourceLoader();
    private final ExtractedHeightGuidePromotionRecordCodecV2 codec =
            new ExtractedHeightGuidePromotionRecordCodecV2();

    public ExtractedHeightGuidePromotionRecordV2 promote(
            Path targetDirectory,
            ExtractedHeightGuideDraftV2 draft,
            ExtractedHeightGuidePromotionOptionsV2 options,
            GenerationRequestV2.Bounds bounds,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();
        if (bounds.width() != draft.width() || bounds.length() != draft.length()) {
            throw new ExtractedHeightGuidePromotionExceptionV2(
                    ExtractedHeightGuidePromotionFailureCodeV2.INVALID_OPTIONS,
                    "promotion bounds dimensions must match the height guide draft");
        }
        if (draft.width() > 1_000 || draft.length() > 1_000) {
            throw new ExtractedHeightGuidePromotionExceptionV2(
                    ExtractedHeightGuidePromotionFailureCodeV2.INVALID_OPTIONS,
                    "residual-linked height promotion requires Request-compatible dimensions (≤1000)");
        }

        long pixels = (long) draft.width() * draft.length();
        if (pixels > ExtractedHeightGuidePromotionRecordV2.MAXIMUM_PIXELS
                || draft.width() > ExtractedHeightGuidePromotionRecordV2.MAXIMUM_DIMENSION
                || draft.length() > ExtractedHeightGuidePromotionRecordV2.MAXIMUM_DIMENSION) {
            throw new ExtractedHeightGuidePromotionExceptionV2(
                    ExtractedHeightGuidePromotionFailureCodeV2.PIXEL_BUDGET_EXCEEDED,
                    "promoted height map exceeds V2-1 numeric PNG pixel / dimension limits");
        }

        PromotedRaster promoted = promoteRaster(draft, options, cancellationToken);
        byte[] pngBytes = encoder.encodeU8(draft.width(), draft.length(), promoted.samples());
        String mapSha256 = Sha256.bytes(pngBytes);
        if (pngBytes.length > ExtractedHeightGuidePromotionRecordV2.MAXIMUM_MAP_BYTES) {
            throw new ExtractedHeightGuidePromotionExceptionV2(
                    ExtractedHeightGuidePromotionFailureCodeV2.PIXEL_BUDGET_EXCEEDED,
                    "promoted height PNG exceeds the map byte budget");
        }

        GenerationRequestV2.HeightEncoding encoding = new GenerationRequestV2.HeightEncoding(
                1,
                GenerationRequestV2.SampleType.U8,
                GenerationRequestV2.RasterChannel.GRAY,
                options.valueMeaning(),
                options.valueScaleMillionths(),
                options.valueOffsetMillionths(),
                new GenerationRequestV2.IntRange(0, ExtractedHeightGuideDraftV2.MAXIMUM_VALID_SAMPLE),
                new GenerationRequestV2.NoDataSentinel(options.noDataSample()));

        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "promotion target requires a parent");
        Files.createDirectories(parent);
        if (Files.exists(target)) {
            throw new IOException("extracted height guide promotion target already exists: " + target);
        }
        Path staging = parent.resolve(".tmp-extracted-height-guide-promotion-" + UUID.randomUUID());
        boolean published = false;
        try {
            cancellationToken.throwIfCancellationRequested();
            Files.createDirectory(staging);
            Path mapPath = staging.resolve(ExtractedHeightGuidePromotionRecordV2.MAP_PATH);
            Files.write(mapPath, pngBytes);
            ExtractedHeightGuidePromotionRecordV2 pending = new ExtractedHeightGuidePromotionRecordV2(
                    ExtractedHeightGuidePromotionRecordV2.VERSION,
                    ExtractedHeightGuidePromotionRecordV2.PROMOTION_VERSION,
                    ExtractedHeightGuidePromotionRecordV2.ROLE,
                    draft.sampleSpaceDeclaration(),
                    draft.sourceChecksum(),
                    draft.semanticChecksum(),
                    draft.algorithmVersion(),
                    options.confidenceThreshold(),
                    options.valueMeaning().name(),
                    options.valueScaleMillionths(),
                    options.valueOffsetMillionths(),
                    0,
                    ExtractedHeightGuideDraftV2.MAXIMUM_VALID_SAMPLE,
                    options.noDataSample(),
                    draft.width(),
                    draft.length(),
                    ExtractedHeightGuidePromotionRecordV2.MAP_PATH,
                    mapSha256,
                    pngBytes.length,
                    ExtractedHeightGuidePromotionRecordV2.SOURCE_ID,
                    promoted.validCells(),
                    promoted.noDataCells(),
                    promoted.thresholdSuppressedCells());
            ExtractedHeightGuidePromotionRecordV2 sealed = codec.seal(pending);
            codec.write(staging.resolve(ExtractedHeightGuidePromotionRecordCodecV2.INDEX_FILE_NAME), sealed);

            verifyThroughV2StrictDecoderAndResidual(
                    staging, sealed, encoding, bounds, promoted.samples(), cancellationToken);

            ExtractedHeightGuidePromotionRecordV2 verified = codec.readAndVerify(
                    staging.resolve(ExtractedHeightGuidePromotionRecordCodecV2.INDEX_FILE_NAME),
                    staging,
                    cancellationToken);
            if (!verified.equals(sealed)) {
                throw new ExtractedHeightGuidePromotionExceptionV2(
                        ExtractedHeightGuidePromotionFailureCodeV2.ARTIFACT_TAMPERED,
                        "extracted height guide promotion changed during strict read-back");
            }

            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for extracted height guide promotion", exception);
            }
            published = true;
            return sealed;
        } finally {
            if (!published) {
                deleteTree(staging);
            }
        }
    }

    private void verifyThroughV2StrictDecoderAndResidual(
            Path staging,
            ExtractedHeightGuidePromotionRecordV2 record,
            GenerationRequestV2.HeightEncoding encoding,
            GenerationRequestV2.Bounds bounds,
            byte[] expectedSamples,
            CancellationToken cancellationToken
    ) {
        try {
            Path requestPath = staging.resolve("request-v2.json");
            ConstraintMapSourceSpec specification = new ConstraintMapSourceSpec(
                    record.sourceId(),
                    record.mapPath(),
                    record.mapSha256(),
                    record.width(),
                    record.length());
            List<LoadedConstraintMapSource> loaded = loader.load(
                    requestPath,
                    List.of(specification),
                    ConstraintMapDecodeLimits.defaults(),
                    cancellationToken::isCancellationRequested);
            DecodedNumericRaster raster = decoder.decode(
                    loaded.getFirst(),
                    specification,
                    new NumericPngEncoding(
                            NumericPngEncoding.CURRENT_VERSION,
                            NumericPngEncoding.NumericKind.HEIGHT,
                            NumericPngEncoding.SampleType.U8),
                    ConstraintMapDecodeLimits.defaults(),
                    cancellationToken::isCancellationRequested);
            if (raster.width() != record.width() || raster.length() != record.length()
                    || !raster.sourceChecksum().equals(record.mapSha256())) {
                throw new ExtractedHeightGuidePromotionExceptionV2(
                        ExtractedHeightGuidePromotionFailureCodeV2.DECODE_VERIFY_FAILED,
                        "strict decoder result does not match the height promotion record");
            }
            for (int z = 0; z < record.length(); z++) {
                if ((z & 31) == 0) {
                    cancellationToken.throwIfCancellationRequested();
                }
                for (int x = 0; x < record.width(); x++) {
                    int expected = Byte.toUnsignedInt(expectedSamples[z * record.width() + x]);
                    if (raster.sample(x, z) != expected) {
                        throw new ExtractedHeightGuidePromotionExceptionV2(
                                ExtractedHeightGuidePromotionFailureCodeV2.DECODE_VERIFY_FAILED,
                                "strict decoder sample mismatch after height promotion");
                    }
                }
            }

            GenerationRequestV2.CoordinateMapping mapping = new GenerationRequestV2.CoordinateMapping(
                    GenerationRequestV2.CoordinateOrigin.NORTH_WEST,
                    GenerationRequestV2.XAxis.EAST,
                    GenerationRequestV2.ZAxis.SOUTH,
                    GenerationRequestV2.PixelReference.PIXEL_CENTER,
                    GenerationRequestV2.AspectMismatchPolicy.REJECT,
                    GenerationRequestV2.QuarterTurn.DEGREES_0,
                    false,
                    false,
                    new GenerationRequestV2.PixelCrop(0, 0, record.width(), record.length()));
            GenerationRequestV2.ConstraintMapSource source = new GenerationRequestV2.ConstraintMapSource(
                    record.sourceId(),
                    record.mapPath(),
                    record.mapSha256(),
                    record.width(),
                    record.length(),
                    GenerationRequestV2.DecoderKind.HEIGHT_RASTER,
                    mapping,
                    encoding);
            TerrainIntentV2.ConstraintMapBinding binding = new TerrainIntentV2.ConstraintMapBinding(
                    "height-guide-binding",
                    record.sourceId(),
                    TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE,
                    "constraint:height-guide:sha256-" + record.mapSha256(),
                    TerrainIntentV2.Strength.SOFT,
                    TerrainIntentV2.Sampling.NEAREST,
                    0,
                    1_000_000);
            CanonicalConstraintRasterV2 canonical = new CanonicalConstraintRasterV2(
                    bounds, source, binding, raster, cancellationToken);
            for (int z = 0; z < record.length(); z++) {
                if ((z & 31) == 0) {
                    cancellationToken.throwIfCancellationRequested();
                }
                for (int x = 0; x < record.width(); x++) {
                    int sample = raster.sample(x, z);
                    if (sample == record.noDataSample()) {
                        if (canonical.desiredDiagnosticAt(x, z) != Integer.MIN_VALUE
                                || canonical.residualDiagnosticAt(x, z) != Integer.MIN_VALUE) {
                            throw new ExtractedHeightGuidePromotionExceptionV2(
                                    ExtractedHeightGuidePromotionFailureCodeV2.RESIDUAL_INCONSISTENT,
                                    "no-data height cell residual is inconsistent");
                        }
                        continue;
                    }
                    int desired = canonical.desiredRawAt(x, z);
                    int actual = canonical.actualRawAt(x, z);
                    int residual = canonical.residualRawAt(x, z);
                    if (residual != Math.subtractExact(desired, actual)) {
                        throw new ExtractedHeightGuidePromotionExceptionV2(
                                ExtractedHeightGuidePromotionFailureCodeV2.RESIDUAL_INCONSISTENT,
                                "height residual does not equal desired - actual");
                    }
                    if (Math.abs((long) residual) >= CanonicalConstraintRasterV2.FIXED_SCALE) {
                        throw new ExtractedHeightGuidePromotionExceptionV2(
                                ExtractedHeightGuidePromotionFailureCodeV2.RESIDUAL_INCONSISTENT,
                                "soft height residual exceeds one whole block");
                    }
                }
            }
        } catch (ConstraintMapInputException | ConstraintCompilationExceptionV2 exception) {
            throw new ExtractedHeightGuidePromotionExceptionV2(
                    ExtractedHeightGuidePromotionFailureCodeV2.DECODE_VERIFY_FAILED,
                    "V2-1 height path rejected the promoted map",
                    exception);
        }
    }

    private static PromotedRaster promoteRaster(
            ExtractedHeightGuideDraftV2 draft,
            ExtractedHeightGuidePromotionOptionsV2 options,
            CancellationToken cancellationToken
    ) {
        int width = draft.width();
        int length = draft.length();
        byte[] samples = new byte[Math.multiplyExact(width, length)];
        int valid = 0;
        int noData = 0;
        int suppressed = 0;
        for (int z = 0; z < length; z++) {
            if ((z & 31) == 0) {
                cancellationToken.throwIfCancellationRequested();
            }
            for (int x = 0; x < width; x++) {
                boolean draftNoData = draft.isNoData(x, z);
                int confidence = draft.confidenceAt(x, z);
                boolean belowThreshold = !draftNoData && confidence < options.confidenceThreshold();
                if (belowThreshold) {
                    suppressed++;
                }
                int sample;
                if (draftNoData || belowThreshold) {
                    sample = options.noDataSample();
                    noData++;
                } else {
                    sample = draft.sampleAt(x, z);
                    if (sample < 0 || sample > ExtractedHeightGuideDraftV2.MAXIMUM_VALID_SAMPLE) {
                        throw new ExtractedHeightGuidePromotionExceptionV2(
                                ExtractedHeightGuidePromotionFailureCodeV2.INVALID_OPTIONS,
                                "draft sample is outside the valid U8 height range");
                    }
                    valid++;
                }
                samples[z * width + x] = (byte) sample;
            }
        }
        return new PromotedRaster(samples, valid, noData, suppressed);
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record PromotedRaster(
            byte[] samples,
            int validCells,
            int noDataCells,
            int thresholdSuppressedCells
    ) {
    }
}
