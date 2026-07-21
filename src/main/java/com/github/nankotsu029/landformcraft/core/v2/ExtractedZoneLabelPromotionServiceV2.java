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
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedZoneLabelDraftArtifactCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedZoneLabelDraftV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.extract.ExtractedZoneLabelPromotionRecordCodecV2;
import com.github.nankotsu029.landformcraft.model.v2.ExtractedZoneLabelPromotionRecordV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.ZoneLabelProposalV2;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Explicit draft→numeric zone-label PNG promotion. Re-enters the V2-1 categorical decoder and
 * ZONE_LABEL_MAP canonical path before atomic publish.
 */
public final class ExtractedZoneLabelPromotionServiceV2 {
    private final NumericPngEncoder encoder = new NumericPngEncoder();
    private final NumericPngDecoder decoder = new NumericPngDecoder();
    private final SecureConstraintMapSourceLoader loader = new SecureConstraintMapSourceLoader();
    private final ExtractedZoneLabelPromotionRecordCodecV2 codec =
            new ExtractedZoneLabelPromotionRecordCodecV2();

    public ExtractedZoneLabelPromotionRecordV2 promote(
            Path targetDirectory,
            ExtractedZoneLabelDraftV2 draft,
            ExtractedZoneLabelPromotionOptionsV2 options,
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
            throw new ExtractedZoneLabelPromotionExceptionV2(
                    ExtractedZoneLabelPromotionFailureCodeV2.INVALID_OPTIONS,
                    "promotion bounds dimensions must match the zone label draft");
        }
        if (draft.width() > 1_000 || draft.length() > 1_000) {
            throw new ExtractedZoneLabelPromotionExceptionV2(
                    ExtractedZoneLabelPromotionFailureCodeV2.INVALID_OPTIONS,
                    "zone promotion requires Request-compatible dimensions (≤1000)");
        }
        for (var entry : draft.proposedLabels()) {
            if (entry.sample() == options.noDataSample()) {
                throw new ExtractedZoneLabelPromotionExceptionV2(
                        ExtractedZoneLabelPromotionFailureCodeV2.INVALID_OPTIONS,
                        "noDataSample collides with a proposed palette sample");
            }
        }

        long pixels = (long) draft.width() * draft.length();
        if (pixels > ExtractedZoneLabelPromotionRecordV2.MAXIMUM_PIXELS) {
            throw new ExtractedZoneLabelPromotionExceptionV2(
                    ExtractedZoneLabelPromotionFailureCodeV2.PIXEL_BUDGET_EXCEEDED,
                    "promoted zone map exceeds V2-1 numeric PNG pixel limits");
        }

        PromotedRaster promoted = promoteRaster(draft, options, cancellationToken);
        byte[] pngBytes = encoder.encodeU8(draft.width(), draft.length(), promoted.samples());
        String mapSha256 = Sha256.bytes(pngBytes);
        if (pngBytes.length > ExtractedZoneLabelPromotionRecordV2.MAXIMUM_MAP_BYTES) {
            throw new ExtractedZoneLabelPromotionExceptionV2(
                    ExtractedZoneLabelPromotionFailureCodeV2.PIXEL_BUDGET_EXCEEDED,
                    "promoted zone PNG exceeds the map byte budget");
        }

        List<ZoneLabelProposalV2> proposals =
                ExtractedZoneLabelDraftArtifactCodecV2.toProposals(draft.proposedLabels());
        List<GenerationRequestV2.LabelMapping> labels = new ArrayList<>(proposals.size());
        for (ZoneLabelProposalV2 proposal : proposals) {
            labels.add(new GenerationRequestV2.LabelMapping(proposal.sample(), proposal.label()));
        }
        GenerationRequestV2.CategoricalEncoding encoding = new GenerationRequestV2.CategoricalEncoding(
                1,
                GenerationRequestV2.SampleType.U8,
                GenerationRequestV2.RasterChannel.GRAY,
                labels,
                new GenerationRequestV2.NoDataSentinel(options.noDataSample()));

        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "promotion target requires a parent");
        Files.createDirectories(parent);
        if (Files.exists(target)) {
            throw new IOException("extracted zone label promotion target already exists: " + target);
        }
        Path staging = parent.resolve(".tmp-extracted-zone-label-promotion-" + UUID.randomUUID());
        boolean published = false;
        try {
            cancellationToken.throwIfCancellationRequested();
            Files.createDirectory(staging);
            Files.write(staging.resolve(ExtractedZoneLabelPromotionRecordV2.MAP_PATH), pngBytes);
            ExtractedZoneLabelPromotionRecordV2 pending = new ExtractedZoneLabelPromotionRecordV2(
                    ExtractedZoneLabelPromotionRecordV2.VERSION,
                    ExtractedZoneLabelPromotionRecordV2.PROMOTION_VERSION,
                    ExtractedZoneLabelPromotionRecordV2.ROLE,
                    draft.sampleSpaceDeclaration(),
                    draft.sourceChecksum(),
                    draft.semanticChecksum(),
                    draft.algorithmVersion(),
                    options.confidenceThreshold(),
                    options.noDataSample(),
                    draft.width(),
                    draft.length(),
                    ExtractedZoneLabelPromotionRecordV2.MAP_PATH,
                    mapSha256,
                    pngBytes.length,
                    ExtractedZoneLabelPromotionRecordV2.SOURCE_ID,
                    proposals,
                    promoted.labeledCells(),
                    promoted.noDataCells(),
                    promoted.thresholdSuppressedCells());
            ExtractedZoneLabelPromotionRecordV2 sealed = codec.seal(pending);
            codec.write(staging.resolve(ExtractedZoneLabelPromotionRecordCodecV2.INDEX_FILE_NAME), sealed);

            verifyThroughV2ZonePath(
                    staging, sealed, encoding, bounds, promoted.samples(), cancellationToken);

            ExtractedZoneLabelPromotionRecordV2 verified = codec.readAndVerify(
                    staging.resolve(ExtractedZoneLabelPromotionRecordCodecV2.INDEX_FILE_NAME),
                    staging,
                    cancellationToken);
            if (!verified.equals(sealed)) {
                throw new ExtractedZoneLabelPromotionExceptionV2(
                        ExtractedZoneLabelPromotionFailureCodeV2.ARTIFACT_TAMPERED,
                        "extracted zone label promotion changed during strict read-back");
            }

            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for extracted zone label promotion", exception);
            }
            published = true;
            return sealed;
        } finally {
            if (!published) {
                deleteTree(staging);
            }
        }
    }

    private void verifyThroughV2ZonePath(
            Path staging,
            ExtractedZoneLabelPromotionRecordV2 record,
            GenerationRequestV2.CategoricalEncoding encoding,
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
                            NumericPngEncoding.NumericKind.CATEGORICAL,
                            NumericPngEncoding.SampleType.U8),
                    ConstraintMapDecodeLimits.defaults(),
                    cancellationToken::isCancellationRequested);
            for (int z = 0; z < record.length(); z++) {
                if ((z & 31) == 0) {
                    cancellationToken.throwIfCancellationRequested();
                }
                for (int x = 0; x < record.width(); x++) {
                    int expected = Byte.toUnsignedInt(expectedSamples[z * record.width() + x]);
                    if (raster.sample(x, z) != expected) {
                        throw new ExtractedZoneLabelPromotionExceptionV2(
                                ExtractedZoneLabelPromotionFailureCodeV2.DECODE_VERIFY_FAILED,
                                "strict decoder sample mismatch after zone promotion");
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
                    GenerationRequestV2.DecoderKind.CATEGORICAL_RASTER,
                    mapping,
                    encoding);
            TerrainIntentV2.ConstraintMapBinding binding = new TerrainIntentV2.ConstraintMapBinding(
                    "zone-label-binding",
                    record.sourceId(),
                    TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP,
                    "constraint:zone-label-map:sha256-" + record.mapSha256(),
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
                        if (canonical.desiredDiagnosticAt(x, z) != Integer.MIN_VALUE) {
                            throw new ExtractedZoneLabelPromotionExceptionV2(
                                    ExtractedZoneLabelPromotionFailureCodeV2.DECODE_VERIFY_FAILED,
                                    "no-data zone cell did not map to canonical no-data");
                        }
                        continue;
                    }
                    int desired = canonical.desiredRawAt(x, z);
                    if (desired < 1 || desired > record.proposedLabels().size()) {
                        throw new ExtractedZoneLabelPromotionExceptionV2(
                                ExtractedZoneLabelPromotionFailureCodeV2.DECODE_VERIFY_FAILED,
                                "zone canonical ID is outside 1..N");
                    }
                    if (canonical.actualRawAt(x, z) != desired) {
                        throw new ExtractedZoneLabelPromotionExceptionV2(
                                ExtractedZoneLabelPromotionFailureCodeV2.DECODE_VERIFY_FAILED,
                                "zone actual must equal desired for categorical maps");
                    }
                }
            }
        } catch (ConstraintMapInputException | ConstraintCompilationExceptionV2 exception) {
            throw new ExtractedZoneLabelPromotionExceptionV2(
                    ExtractedZoneLabelPromotionFailureCodeV2.DECODE_VERIFY_FAILED,
                    "V2-1 zone path rejected the promoted map",
                    exception);
        }
    }

    private static PromotedRaster promoteRaster(
            ExtractedZoneLabelDraftV2 draft,
            ExtractedZoneLabelPromotionOptionsV2 options,
            CancellationToken cancellationToken
    ) {
        int width = draft.width();
        int length = draft.length();
        byte[] samples = new byte[Math.multiplyExact(width, length)];
        int labeled = 0;
        int noData = 0;
        int suppressed = 0;
        for (int z = 0; z < length; z++) {
            if ((z & 31) == 0) {
                cancellationToken.throwIfCancellationRequested();
            }
            for (int x = 0; x < width; x++) {
                boolean unknown = draft.isUnknown(x, z);
                int confidence = draft.confidenceAt(x, z);
                boolean below = !unknown && confidence < options.confidenceThreshold();
                if (below) {
                    suppressed++;
                }
                int sample;
                if (unknown || below) {
                    sample = options.noDataSample();
                    noData++;
                } else {
                    sample = draft.sampleAt(x, z);
                    labeled++;
                }
                samples[z * width + x] = (byte) sample;
            }
        }
        return new PromotedRaster(samples, labeled, noData, suppressed);
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
            int labeledCells,
            int noDataCells,
            int thresholdSuppressedCells
    ) {
    }
}
