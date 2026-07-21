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
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskDraftV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedMaskPromotionRecordCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedMaskPromotionRecordV2;

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
 * Explicit draft→numeric land-water PNG promotion. Requires confidence threshold and UNKNOWN
 * handling; re-enters the V2-1 strict decoder before atomic publish. Never promotes implicitly.
 */
public final class ExtractedMaskPromotionServiceV2 {
    public static final int WATER_SAMPLE = ExtractedMaskPromotionOptionsV2.WATER_SAMPLE;
    public static final int LAND_SAMPLE = ExtractedMaskPromotionOptionsV2.LAND_SAMPLE;

    private final NumericPngEncoder encoder = new NumericPngEncoder();
    private final NumericPngDecoder decoder = new NumericPngDecoder();
    private final SecureConstraintMapSourceLoader loader = new SecureConstraintMapSourceLoader();
    private final ExtractedMaskPromotionRecordCodecV2 codec = new ExtractedMaskPromotionRecordCodecV2();

    public ExtractedMaskPromotionRecordV2 promote(
            Path targetDirectory,
            ExtractedMaskDraftV2 draft,
            ExtractedMaskPromotionOptionsV2 options,
            CancellationToken cancellationToken
    ) throws IOException {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        cancellationToken.throwIfCancellationRequested();

        long pixels = (long) draft.width() * draft.length();
        if (pixels > ExtractedMaskPromotionRecordV2.MAXIMUM_PIXELS
                || draft.width() > ExtractedMaskPromotionRecordV2.MAXIMUM_DIMENSION
                || draft.length() > ExtractedMaskPromotionRecordV2.MAXIMUM_DIMENSION) {
            throw new ExtractedMaskPromotionExceptionV2(
                    ExtractedMaskPromotionFailureCodeV2.PIXEL_BUDGET_EXCEEDED,
                    "promoted map exceeds V2-1 numeric PNG pixel / dimension limits");
        }

        PromotedRaster promoted = promoteRaster(draft, options, cancellationToken);
        byte[] pngBytes = encoder.encodeU8(draft.width(), draft.length(), promoted.samples());
        String mapSha256 = Sha256.bytes(pngBytes);
        if (pngBytes.length > ExtractedMaskPromotionRecordV2.MAXIMUM_MAP_BYTES) {
            throw new ExtractedMaskPromotionExceptionV2(
                    ExtractedMaskPromotionFailureCodeV2.PIXEL_BUDGET_EXCEEDED,
                    "promoted PNG exceeds the map byte budget");
        }

        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "promotion target requires a parent");
        Files.createDirectories(parent);
        if (Files.exists(target)) {
            throw new IOException("extracted mask promotion target already exists: " + target);
        }
        Path staging = parent.resolve(".tmp-extracted-mask-promotion-" + UUID.randomUUID());
        boolean published = false;
        try {
            cancellationToken.throwIfCancellationRequested();
            Files.createDirectory(staging);
            Path mapPath = staging.resolve(ExtractedMaskPromotionRecordV2.MAP_PATH);
            Files.write(mapPath, pngBytes);
            ExtractedMaskPromotionRecordV2 pending = new ExtractedMaskPromotionRecordV2(
                    ExtractedMaskPromotionRecordV2.VERSION,
                    ExtractedMaskPromotionRecordV2.PROMOTION_VERSION,
                    ExtractedMaskPromotionRecordV2.ROLE,
                    draft.sourceChecksum(),
                    draft.semanticChecksum(),
                    draft.algorithmVersion(),
                    options.confidenceThreshold(),
                    options.unknownHandling().name(),
                    options.noDataSample(),
                    draft.width(),
                    draft.length(),
                    ExtractedMaskPromotionRecordV2.MAP_PATH,
                    mapSha256,
                    pngBytes.length,
                    ExtractedMaskPromotionRecordV2.SOURCE_ID,
                    promoted.waterCells(),
                    promoted.landCells(),
                    promoted.noDataCells(),
                    promoted.thresholdSuppressedCells());
            ExtractedMaskPromotionRecordV2 sealed = codec.seal(pending);
            codec.write(staging.resolve(ExtractedMaskPromotionRecordCodecV2.INDEX_FILE_NAME), sealed);

            verifyThroughV2StrictDecoder(staging, sealed, promoted.samples(), cancellationToken);

            ExtractedMaskPromotionRecordV2 verified = codec.readAndVerify(
                    staging.resolve(ExtractedMaskPromotionRecordCodecV2.INDEX_FILE_NAME),
                    staging,
                    cancellationToken);
            if (!verified.equals(sealed)) {
                throw new ExtractedMaskPromotionExceptionV2(
                        ExtractedMaskPromotionFailureCodeV2.ARTIFACT_TAMPERED,
                        "extracted mask promotion changed during strict read-back");
            }

            // Final cancel observation; the atomic directory move is the commit point.
            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for extracted mask promotion", exception);
            }
            published = true;
            return sealed;
        } finally {
            if (!published) {
                deleteTree(staging);
            }
        }
    }

    private void verifyThroughV2StrictDecoder(
            Path staging,
            ExtractedMaskPromotionRecordV2 record,
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
            if (loaded.size() != 1) {
                throw new ExtractedMaskPromotionExceptionV2(
                        ExtractedMaskPromotionFailureCodeV2.DECODE_VERIFY_FAILED,
                        "strict decoder load did not return exactly one source");
            }
            DecodedNumericRaster raster = decoder.decode(
                    loaded.getFirst(),
                    specification,
                    new NumericPngEncoding(
                            NumericPngEncoding.CURRENT_VERSION,
                            NumericPngEncoding.NumericKind.CATEGORICAL,
                            NumericPngEncoding.SampleType.U8),
                    ConstraintMapDecodeLimits.defaults(),
                    cancellationToken::isCancellationRequested);
            if (raster.width() != record.width() || raster.length() != record.length()) {
                throw new ExtractedMaskPromotionExceptionV2(
                        ExtractedMaskPromotionFailureCodeV2.DECODE_VERIFY_FAILED,
                        "strict decoder dimensions do not match the promotion record");
            }
            if (!raster.sourceChecksum().equals(record.mapSha256())) {
                throw new ExtractedMaskPromotionExceptionV2(
                        ExtractedMaskPromotionFailureCodeV2.DECODE_VERIFY_FAILED,
                        "strict decoder source checksum does not match the promotion record");
            }
            for (int z = 0; z < record.length(); z++) {
                if ((z & 31) == 0) {
                    cancellationToken.throwIfCancellationRequested();
                }
                for (int x = 0; x < record.width(); x++) {
                    int expected = Byte.toUnsignedInt(expectedSamples[z * record.width() + x]);
                    if (raster.sample(x, z) != expected) {
                        throw new ExtractedMaskPromotionExceptionV2(
                                ExtractedMaskPromotionFailureCodeV2.DECODE_VERIFY_FAILED,
                                "strict decoder sample mismatch after promotion");
                    }
                    if (expected != WATER_SAMPLE && expected != LAND_SAMPLE
                            && (record.noDataSample() == null || expected != record.noDataSample())) {
                        throw new ExtractedMaskPromotionExceptionV2(
                                ExtractedMaskPromotionFailureCodeV2.DECODE_VERIFY_FAILED,
                                "promoted sample is outside the land-water / no-data alphabet");
                    }
                }
            }
        } catch (ConstraintMapInputException exception) {
            throw new ExtractedMaskPromotionExceptionV2(
                    ExtractedMaskPromotionFailureCodeV2.DECODE_VERIFY_FAILED,
                    "V2-1 strict decoder rejected the promoted map",
                    exception);
        }
    }

    private static PromotedRaster promoteRaster(
            ExtractedMaskDraftV2 draft,
            ExtractedMaskPromotionOptionsV2 options,
            CancellationToken cancellationToken
    ) {
        int width = draft.width();
        int length = draft.length();
        byte[] samples = new byte[Math.multiplyExact(width, length)];
        int water = 0;
        int land = 0;
        int noData = 0;
        int suppressed = 0;
        for (int z = 0; z < length; z++) {
            if ((z & 31) == 0) {
                cancellationToken.throwIfCancellationRequested();
            }
            for (int x = 0; x < width; x++) {
                int classValue = draft.classAt(x, z);
                int confidence = draft.confidenceAt(x, z);
                boolean belowThreshold = confidence < options.confidenceThreshold();
                boolean unresolved = classValue == ExtractedMaskDraftV2.CLASS_UNKNOWN || belowThreshold;
                if (belowThreshold && classValue != ExtractedMaskDraftV2.CLASS_UNKNOWN) {
                    suppressed++;
                }
                int sample;
                if (!unresolved) {
                    sample = switch (classValue) {
                        case ExtractedMaskDraftV2.CLASS_WATER -> WATER_SAMPLE;
                        case ExtractedMaskDraftV2.CLASS_LAND -> LAND_SAMPLE;
                        default -> throw new ExtractedMaskPromotionExceptionV2(
                                ExtractedMaskPromotionFailureCodeV2.INVALID_OPTIONS,
                                "draft contains an unsupported class value");
                    };
                } else {
                    sample = switch (options.unknownHandling()) {
                        case REJECT -> throw new ExtractedMaskPromotionExceptionV2(
                                ExtractedMaskPromotionFailureCodeV2.UNRESOLVED_UNKNOWN,
                                "explicit UNKNOWN handling REJECT found unresolved cells");
                        case MAP_TO_WATER -> WATER_SAMPLE;
                        case MAP_TO_LAND -> LAND_SAMPLE;
                        case MAP_TO_NODATA -> options.noDataSample();
                    };
                }
                samples[z * width + x] = (byte) sample;
                if (sample == WATER_SAMPLE) {
                    water++;
                } else if (sample == LAND_SAMPLE) {
                    land++;
                } else {
                    noData++;
                }
            }
        }
        return new PromotedRaster(samples, water, land, noData, suppressed);
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
            int waterCells,
            int landCells,
            int noDataCells,
            int thresholdSuppressedCells
    ) {
    }
}
