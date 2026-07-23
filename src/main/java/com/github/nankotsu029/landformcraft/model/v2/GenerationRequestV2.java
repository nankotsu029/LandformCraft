package com.github.nankotsu029.landformcraft.model.v2;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;

/**
 * Version-2 request contract. AI reference images and deterministic constraint maps are intentionally
 * different input types and collections.
 */
public record GenerationRequestV2(
        int requestVersion,
        String requestId,
        Bounds bounds,
        String prompt,
        List<ReferenceImageSource> referenceImages,
        List<ConstraintMapSource> constraintMaps,
        GenerationSettings generation,
        ConstraintMapBudget constraintMapBudget,
        Optional<FoundationBaseLevels> foundationBaseLevels
) {
    public static final int VERSION = 2;
    public static final int MAX_REFERENCE_IMAGES = 16;
    public static final int MAX_CONSTRAINT_MAPS = 32;
    public static final int MAX_PROMPT_LENGTH = 20_000;
    public static final int FIXED_SCALE = 1_000_000;
    public static final int MIN_FIXED_Y_BLOCK = -2_147;
    public static final int MAX_FIXED_Y_BLOCK = 2_147;
    public static final long MAX_TOTAL_SOURCE_BYTES = 32L * 1024L * 1024L;
    public static final long MAX_DECODED_BYTES = 32L * 1024L * 1024L;
    public static final long MAX_CONSTRAINT_PIXELS = 16_000_000L;
    public static final long MAX_ARTIFACT_BYTES = 64L * 1024L * 1024L;
    public static final long MAX_RESIDENT_BYTES = 96L * 1024L * 1024L;
    private static final Pattern CONSTRAINT_SOURCE_ID =
            Pattern.compile("constraint-source:[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern CONSTRAINT_MAP_PATH =
            Pattern.compile("[A-Za-z0-9._/-]{1,512}");

    public GenerationRequestV2 {
        if (requestVersion != VERSION) {
            throw new IllegalArgumentException("requestVersion must be exactly 2");
        }
        requestId = V2Validation.slug(requestId, "requestId");
        Objects.requireNonNull(bounds, "bounds");
        prompt = V2Validation.nonBlank(prompt, "prompt", MAX_PROMPT_LENGTH);
        referenceImages = V2Validation.sorted(referenceImages, "referenceImages", MAX_REFERENCE_IMAGES,
                Comparator.comparing(ReferenceImageSource::id));
        constraintMaps = V2Validation.sorted(constraintMaps, "constraintMaps", MAX_CONSTRAINT_MAPS,
                Comparator.comparing(ConstraintMapSource::sourceId));
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(constraintMapBudget, "constraintMapBudget");
        Objects.requireNonNull(foundationBaseLevels, "foundationBaseLevels");
        foundationBaseLevels.ifPresent(levels -> levels.requireWithin(bounds));
        validateSources(referenceImages, constraintMaps, bounds, constraintMapBudget);
    }

    /**
     * ADR 0038 D2-2(b): declared per-medium provisional base elevation for the macro foundation
     * stage. Together with a HARD {@code LAND_WATER_MASK} map reference this forms the explicit
     * foundation input; a request without it keeps the legacy surface-baseline path (ADR 0038 D8-2).
     */
    public record FoundationBaseLevels(int landSurfaceY, int waterBedY) {
        void requireWithin(Bounds bounds) {
            if (landSurfaceY < bounds.minY() || landSurfaceY > bounds.maxY()
                    || waterBedY < bounds.minY() || waterBedY > bounds.maxY()) {
                throw new IllegalArgumentException(
                        "foundation base levels are outside the request bounds");
            }
        }
    }

    private static void validateSources(
            List<ReferenceImageSource> references,
            List<ConstraintMapSource> maps,
            Bounds bounds,
            ConstraintMapBudget budget
    ) {
        Set<String> referenceIds = new HashSet<>();
        Set<String> sourceIds = new HashSet<>();
        Set<String> lexicalPaths = new HashSet<>();
        for (ReferenceImageSource reference : references) {
            if (!referenceIds.add(reference.id())) {
                throw new IllegalArgumentException("duplicate reference image id: " + reference.id());
            }
            if (!lexicalPaths.add(reference.file())) {
                throw new IllegalArgumentException("duplicate input path: " + reference.file());
            }
        }
        if (maps.size() > budget.maximumMapCount()) {
            throw new IllegalArgumentException("constraint map count exceeds declared budget");
        }
        long declaredPixels = 0L;
        long minimumDecodedBytes = 0L;
        for (ConstraintMapSource map : maps) {
            if (!sourceIds.add(map.sourceId())) {
                throw new IllegalArgumentException("duplicate constraint map sourceId: " + map.sourceId());
            }
            if (!lexicalPaths.add(map.file())) {
                throw new IllegalArgumentException("duplicate input path: " + map.file());
            }
            long sourceAspect = Math.multiplyExact((long) map.coordinateMapping().crop().width(), bounds.length());
            long targetAspect = Math.multiplyExact((long) map.coordinateMapping().crop().length(), bounds.width());
            if (sourceAspect != targetAspect) {
                throw new IllegalArgumentException("constraint map crop aspect does not match request bounds: "
                        + map.sourceId());
            }
            if (map.encoding() instanceof HeightEncoding height) {
                validateHeightOutputRange(height, bounds, map.sourceId());
            }
            long pixels = Math.multiplyExact((long) map.expectedWidth(), map.expectedLength());
            declaredPixels = Math.addExact(declaredPixels, pixels);
            int bytesPerSample = map.encoding().sampleType() == SampleType.U8 ? 1 : 2;
            minimumDecodedBytes = Math.addExact(
                    minimumDecodedBytes,
                    Math.multiplyExact(pixels, bytesPerSample));
        }
        if (declaredPixels > budget.maximumPixels()) {
            throw new IllegalArgumentException("constraint map pixels exceed declared budget");
        }
        if (minimumDecodedBytes > budget.maximumDecodedBytes()) {
            throw new IllegalArgumentException("constraint map decoded bytes exceed declared budget");
        }
    }

    private static void validateHeightOutputRange(HeightEncoding encoding, Bounds bounds, String sourceId) {
        long base = switch (encoding.valueMeaning()) {
            case ABSOLUTE_BLOCK_Y -> 0L;
            case BLOCKS_ABOVE_REQUEST_MIN_Y -> Math.multiplyExact((long) bounds.minY(), FIXED_SCALE);
            case BLOCKS_RELATIVE_TO_WATER_LEVEL -> Math.multiplyExact((long) bounds.waterLevel(), FIXED_SCALE);
        };
        long first = decodedHeight(encoding.validSampleRange().minimum(), encoding, base);
        long second = decodedHeight(encoding.validSampleRange().maximum(), encoding, base);
        long minimum = Math.min(first, second);
        long maximum = Math.max(first, second);
        long allowedMinimum = Math.multiplyExact((long) bounds.minY(), FIXED_SCALE);
        long allowedMaximum = Math.multiplyExact((long) bounds.maxY(), FIXED_SCALE);
        if (minimum < allowedMinimum || maximum > allowedMaximum) {
            throw new IllegalArgumentException("height encoding output is outside request bounds: " + sourceId);
        }
    }

    private static long decodedHeight(int sample, HeightEncoding encoding, long base) {
        return Math.addExact(base, Math.addExact(
                Math.multiplyExact((long) sample, encoding.valueScaleMillionths()),
                encoding.valueOffsetMillionths()));
    }

    public record Bounds(int width, int length, int minY, int maxY, int waterLevel) {
        public Bounds {
            if (width < 1 || width > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING
                    || length < 1 || length > ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING) {
                throw new IllegalArgumentException(
                        "bounds horizontal dimensions must be in 1.."
                                + ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING);
            }
            if (minY < MIN_FIXED_Y_BLOCK || maxY > MAX_FIXED_Y_BLOCK
                    || waterLevel < MIN_FIXED_Y_BLOCK || waterLevel > MAX_FIXED_Y_BLOCK) {
                throw new IllegalArgumentException(
                        "bounds Y values must fit signed I32 fixed-point millionths (-2147..2147)");
            }
            if (minY >= maxY || (long) maxY - minY + 1L > 512L
                    || waterLevel < minY || waterLevel > maxY) {
                throw new IllegalArgumentException("bounds vertical range is invalid");
            }
        }
    }

    /**
     * Reference image roles are AI proposal input only. No role produces coordinate constraints,
     * height guides, masks, or any HARD geometry; deterministic constraints use {@link ConstraintMapSource}
     * instead. Oblique and multi-view roles are never auto-converted to a top-down view and never infer
     * unobserved underground terrain.
     */
    public enum ReferenceImageRole {
        MOOD_REFERENCE,
        TOP_DOWN_SKETCH,
        MATERIAL_REFERENCE,
        STRUCTURE_REFERENCE,
        OBLIQUE_TERRAIN_REFERENCE,
        MULTI_VIEW_REFERENCE
    }

    public record ReferenceImageSource(String id, String file, ReferenceImageRole role) {
        public ReferenceImageSource {
            id = V2Validation.slug(id, "reference image id");
            file = V2Validation.safeRelativePath(file, "reference image file");
            Objects.requireNonNull(role, "role");
        }
    }

    public enum DecoderKind { CATEGORICAL_RASTER, HEIGHT_RASTER }
    public enum SampleType { U8, U16 }
    public enum RasterChannel { GRAY }
    public enum CoordinateOrigin { NORTH_WEST }
    public enum XAxis { EAST }
    public enum ZAxis { SOUTH }
    public enum PixelReference { PIXEL_CENTER }
    public enum AspectMismatchPolicy { REJECT }
    public enum QuarterTurn { DEGREES_0, DEGREES_90, DEGREES_180, DEGREES_270 }
    public enum HeightValueMeaning {
        ABSOLUTE_BLOCK_Y,
        BLOCKS_ABOVE_REQUEST_MIN_Y,
        BLOCKS_RELATIVE_TO_WATER_LEVEL
    }

    public record PixelCrop(int x, int z, int width, int length) {
        public PixelCrop {
            if (x < 0 || z < 0 || width < 1 || length < 1) {
                throw new IllegalArgumentException("crop must be a positive pixel rectangle");
            }
        }
    }

    /** Transform order is quarter-turn, flip X/Z, then crop in the transformed pixel space. */
    public record CoordinateMapping(
            CoordinateOrigin origin,
            XAxis xAxis,
            ZAxis zAxis,
            PixelReference pixelReference,
            AspectMismatchPolicy aspectMismatchPolicy,
            QuarterTurn rotation,
            boolean flipX,
            boolean flipZ,
            PixelCrop crop
    ) {
        public CoordinateMapping {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(xAxis, "xAxis");
            Objects.requireNonNull(zAxis, "zAxis");
            Objects.requireNonNull(pixelReference, "pixelReference");
            Objects.requireNonNull(aspectMismatchPolicy, "aspectMismatchPolicy");
            Objects.requireNonNull(rotation, "rotation");
            Objects.requireNonNull(crop, "crop");
        }
    }

    public sealed interface ConstraintMapEncoding permits CategoricalEncoding, HeightEncoding {
        int encodingVersion();
        SampleType sampleType();
        RasterChannel channel();
        NoData noData();
    }

    public sealed interface NoData permits NoDataForbidden, NoDataSentinel { }

    public record NoDataForbidden() implements NoData { }

    public record NoDataSentinel(int sample) implements NoData {
        public NoDataSentinel {
            if (sample < 0 || sample > 65_535) {
                throw new IllegalArgumentException("no-data sample must be in 0..65535");
            }
        }
    }

    public record LabelMapping(int sample, String label) {
        public LabelMapping {
            if (sample < 0 || sample > 65_535) {
                throw new IllegalArgumentException("label sample must be in 0..65535");
            }
            label = V2Validation.slug(label, "label");
        }
    }

    public record CategoricalEncoding(
            int encodingVersion,
            SampleType sampleType,
            RasterChannel channel,
            List<LabelMapping> labels,
            NoData noData
    ) implements ConstraintMapEncoding {
        public CategoricalEncoding {
            if (encodingVersion != 1) {
                throw new IllegalArgumentException("categorical encodingVersion must be exactly 1");
            }
            Objects.requireNonNull(sampleType, "sampleType");
            Objects.requireNonNull(channel, "channel");
            labels = V2Validation.sorted(labels, "labels", 4_096,
                    Comparator.comparingInt(LabelMapping::sample));
            Objects.requireNonNull(noData, "noData");
            if (labels.isEmpty()) {
                throw new IllegalArgumentException("categorical encoding requires labels");
            }
            Set<Integer> samples = new HashSet<>();
            Set<String> names = new HashSet<>();
            for (LabelMapping label : labels) {
                requireSampleFits(sampleType, label.sample(), "label sample");
                if (!samples.add(label.sample()) || !names.add(label.label())) {
                    throw new IllegalArgumentException("categorical labels must have unique samples and names");
                }
            }
            if (noData instanceof NoDataSentinel sentinel) {
                requireSampleFits(sampleType, sentinel.sample(), "no-data sample");
                if (samples.contains(sentinel.sample())) {
                    throw new IllegalArgumentException("no-data sample must not also be a label");
                }
            }
        }
    }

    public record IntRange(int minimum, int maximum) {
        public IntRange {
            if (minimum < 0 || minimum > maximum || maximum > 65_535) {
                throw new IllegalArgumentException("sample range is invalid");
            }
        }
    }

    public record HeightEncoding(
            int encodingVersion,
            SampleType sampleType,
            RasterChannel channel,
            HeightValueMeaning valueMeaning,
            long valueScaleMillionths,
            long valueOffsetMillionths,
            IntRange validSampleRange,
            NoData noData
    ) implements ConstraintMapEncoding {
        public HeightEncoding {
            if (encodingVersion != 1) {
                throw new IllegalArgumentException("height encodingVersion must be exactly 1");
            }
            Objects.requireNonNull(sampleType, "sampleType");
            Objects.requireNonNull(channel, "channel");
            Objects.requireNonNull(valueMeaning, "valueMeaning");
            if (valueScaleMillionths == 0L) {
                throw new IllegalArgumentException("height valueScaleMillionths must not be zero");
            }
            Objects.requireNonNull(validSampleRange, "validSampleRange");
            Objects.requireNonNull(noData, "noData");
            requireSampleFits(sampleType, validSampleRange.minimum(), "minimum sample");
            requireSampleFits(sampleType, validSampleRange.maximum(), "maximum sample");
            if (noData instanceof NoDataSentinel sentinel) {
                requireSampleFits(sampleType, sentinel.sample(), "no-data sample");
                if (sentinel.sample() >= validSampleRange.minimum()
                        && sentinel.sample() <= validSampleRange.maximum()) {
                    throw new IllegalArgumentException("no-data sample must be outside validSampleRange");
                }
            }
        }
    }

    public record ConstraintMapSource(
            String sourceId,
            String file,
            String expectedSha256,
            int expectedWidth,
            int expectedLength,
            DecoderKind decoderKind,
            CoordinateMapping coordinateMapping,
            ConstraintMapEncoding encoding
    ) {
        public ConstraintMapSource {
            sourceId = V2Validation.nonBlank(sourceId, "constraint map sourceId", 96);
            if (!CONSTRAINT_SOURCE_ID.matcher(sourceId).matches()) {
                throw new IllegalArgumentException("constraint map sourceId must use constraint-source:<slug>");
            }
            file = V2Validation.safeRelativePath(file, "constraint map file");
            if (!CONSTRAINT_MAP_PATH.matcher(file).matches()) {
                throw new IllegalArgumentException(
                        "constraint map file may contain only ASCII letters, digits, '.', '_', '-', and '/'");
            }
            expectedSha256 = V2Validation.checksum(expectedSha256, "expectedSha256");
            if (expectedWidth < 1 || expectedWidth > 4_096 || expectedLength < 1 || expectedLength > 4_096
                    || (long) expectedWidth * expectedLength > 4_000_000L) {
                throw new IllegalArgumentException("expected constraint map dimensions exceed limits");
            }
            Objects.requireNonNull(decoderKind, "decoderKind");
            Objects.requireNonNull(coordinateMapping, "coordinateMapping");
            Objects.requireNonNull(encoding, "encoding");
            boolean compatible = decoderKind == DecoderKind.CATEGORICAL_RASTER
                    && encoding instanceof CategoricalEncoding
                    || decoderKind == DecoderKind.HEIGHT_RASTER && encoding instanceof HeightEncoding;
            if (!compatible) {
                throw new IllegalArgumentException("decoderKind does not match encoding");
            }
            int transformedWidth = switch (coordinateMapping.rotation()) {
                case DEGREES_0, DEGREES_180 -> expectedWidth;
                case DEGREES_90, DEGREES_270 -> expectedLength;
            };
            int transformedLength = switch (coordinateMapping.rotation()) {
                case DEGREES_0, DEGREES_180 -> expectedLength;
                case DEGREES_90, DEGREES_270 -> expectedWidth;
            };
            PixelCrop crop = coordinateMapping.crop();
            if ((long) crop.x() + crop.width() > transformedWidth
                    || (long) crop.z() + crop.length() > transformedLength) {
                throw new IllegalArgumentException("crop is outside transformed image dimensions");
            }
        }
    }

    public record GenerationSettings(long globalSeed, int tileSize) {
        public GenerationSettings {
            if (tileSize < 32 || tileSize > 256 || Integer.bitCount(tileSize) != 1) {
                throw new IllegalArgumentException("tileSize must be one of 32, 64, 128, 256");
            }
        }
    }

    public record ConstraintMapBudget(
            int maximumMapCount,
            long maximumTotalSourceBytes,
            long maximumDecodedBytes,
            long maximumPixels,
            long maximumArtifactBytes,
            long maximumResidentBytes
    ) {
        public ConstraintMapBudget {
            if (maximumMapCount < 1 || maximumMapCount > MAX_CONSTRAINT_MAPS
                    || maximumTotalSourceBytes < 1L || maximumTotalSourceBytes > MAX_TOTAL_SOURCE_BYTES
                    || maximumDecodedBytes < 1L || maximumDecodedBytes > MAX_DECODED_BYTES
                    || maximumPixels < 1L || maximumPixels > MAX_CONSTRAINT_PIXELS
                    || maximumArtifactBytes < 1L || maximumArtifactBytes > MAX_ARTIFACT_BYTES
                    || maximumResidentBytes < 1L || maximumResidentBytes > MAX_RESIDENT_BYTES) {
                throw new IllegalArgumentException("constraint map budget values are invalid");
            }
        }

        public static ConstraintMapBudget defaults() {
            return new ConstraintMapBudget(
                    16,
                    MAX_TOTAL_SOURCE_BYTES,
                    MAX_DECODED_BYTES,
                    MAX_CONSTRAINT_PIXELS,
                    MAX_ARTIFACT_BYTES,
                    MAX_RESIDENT_BYTES);
        }
    }

    private static void requireSampleFits(SampleType sampleType, int sample, String field) {
        int maximum = sampleType == SampleType.U8 ? 255 : 65_535;
        if (sample < 0 || sample > maximum) {
            throw new IllegalArgumentException(field + " does not fit " + sampleType);
        }
    }
}
