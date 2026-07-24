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
        Optional<FoundationBaseLevels> foundationBaseLevels,
        Optional<FoundationDetail> foundationDetail,
        Optional<MaskFeatureReconcile> maskFeatureReconcile
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
        Objects.requireNonNull(foundationDetail, "foundationDetail");
        foundationDetail.ifPresent(detail -> {
            // ADR 0041 D3: coherent detail replaces the per-medium base level, so it is only meaningful
            // on the explicit-foundation path and its amplitude must not push a background cell out of
            // the request's vertical extent or across the water level (a dry pit / a vanished water
            // column). Both checks need the water level and the base levels, so they live here where the
            // whole request is visible rather than inside FoundationDetail alone.
            if (foundationBaseLevels.isEmpty()) {
                throw new IllegalArgumentException(
                        "foundation detail requires declared foundation base levels");
            }
            detail.requireCompatibleWith(bounds, foundationBaseLevels.get());
        });
        Objects.requireNonNull(maskFeatureReconcile, "maskFeatureReconcile");
        maskFeatureReconcile.ifPresent(reconcile -> {
            // ADR 0043 D3: the pre-pass aligns the declared geometry with the HARD land-water mask, so
            // it is only meaningful on the explicit-foundation path. The evaluation budget needs the
            // domain, so both checks live here where the whole request is visible.
            if (foundationBaseLevels.isEmpty()) {
                throw new IllegalArgumentException(
                        "mask feature reconcile requires declared foundation base levels");
            }
            reconcile.requireAffordableFor(bounds);
        });
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

    /**
     * ADR 0041 D3: optional coherent multi-scale detail for the macro foundation's background
     * elevation. It replaces the flat per-medium {@link FoundationBaseLevels} on cells the background
     * owns and no {@code HEIGHT_GUIDE}, producer or surface modifier claims; it never changes the
     * land-water medium. {@code wavelengthBlocks} is a power of two and {@code frequency = 1 /
     * wavelength}; the amplitude is a hard bound (kernel {@code coherent-detail-fixed-v1}). Absent
     * leaves every pre-V2-19-12 request byte-identical, so the field is Optional.
     */
    public record FoundationDetail(
            int landAmplitudeBlocks,
            int waterAmplitudeBlocks,
            int wavelengthBlocks,
            int octaves
    ) {
        public static final int MAX_AMPLITUDE_BLOCKS = 32;
        public static final int MIN_WAVELENGTH_BLOCKS = 8;
        public static final int MAX_WAVELENGTH_BLOCKS = 1024;
        public static final int MIN_OCTAVES = 1;
        public static final int MAX_OCTAVES = 6;
        /** The finest octave's grid spacing (wavelength >> (octaves-1)) must stay a usable grid. */
        public static final int MIN_GRID_SPACING_BLOCKS = 4;

        public FoundationDetail {
            if (landAmplitudeBlocks < 0 || landAmplitudeBlocks > MAX_AMPLITUDE_BLOCKS
                    || waterAmplitudeBlocks < 0 || waterAmplitudeBlocks > MAX_AMPLITUDE_BLOCKS) {
                throw new IllegalArgumentException(
                        "foundation detail amplitudes must be within 0.." + MAX_AMPLITUDE_BLOCKS + " blocks");
            }
            if (landAmplitudeBlocks == 0 && waterAmplitudeBlocks == 0) {
                throw new IllegalArgumentException(
                        "foundation detail declares zero amplitude on both mediums (a no-op)");
            }
            if (wavelengthBlocks < MIN_WAVELENGTH_BLOCKS || wavelengthBlocks > MAX_WAVELENGTH_BLOCKS
                    || Integer.bitCount(wavelengthBlocks) != 1) {
                throw new IllegalArgumentException(
                        "foundation detail wavelength must be a power of two within "
                                + MIN_WAVELENGTH_BLOCKS + ".." + MAX_WAVELENGTH_BLOCKS + " blocks");
            }
            if (octaves < MIN_OCTAVES || octaves > MAX_OCTAVES) {
                throw new IllegalArgumentException(
                        "foundation detail octaves must be within " + MIN_OCTAVES + ".." + MAX_OCTAVES);
            }
            if ((wavelengthBlocks >> (octaves - 1)) < MIN_GRID_SPACING_BLOCKS) {
                throw new IllegalArgumentException(
                        "foundation detail wavelength " + wavelengthBlocks + " cannot carry " + octaves
                                + " octaves (finest grid spacing below " + MIN_GRID_SPACING_BLOCKS + ")");
            }
        }

        /**
         * ADR 0041 D5 declaration-time fail-closed: with the request's water level and the declared
         * base levels visible, verify that {@code base ± amplitude} stays inside the vertical extent
         * and never crosses the water level. Rejected, not clamped.
         */
        void requireCompatibleWith(Bounds bounds, FoundationBaseLevels levels) {
            if (landAmplitudeBlocks > 0) {
                requireBandWithin(levels.landSurfaceY(), landAmplitudeBlocks, bounds, "land surface");
                if (levels.landSurfaceY() - landAmplitudeBlocks < bounds.waterLevel()) {
                    throw new IllegalArgumentException(
                            "foundation land detail would sink a land surface below the water level");
                }
            }
            if (waterAmplitudeBlocks > 0) {
                requireBandWithin(levels.waterBedY(), waterAmplitudeBlocks, bounds, "water bed");
                if (levels.waterBedY() + waterAmplitudeBlocks > bounds.waterLevel() - 1) {
                    throw new IllegalArgumentException(
                            "foundation water detail would raise a sea bed into the water surface");
                }
            }
        }

        private static void requireBandWithin(int center, int amplitude, Bounds bounds, String label) {
            if (center - amplitude < bounds.minY() || center + amplitude > bounds.maxY()) {
                throw new IllegalArgumentException(
                        "foundation detail " + label + " band leaves the request vertical extent");
            }
        }
    }

    /**
     * ADR 0043 D3 (V2-19-14): optional mask ⇔ feature reconcile pre-pass. When declared, the export
     * spine aligns the declared feature geometry with the HARD {@code LAND_WATER_MASK} by a single
     * rigid integer-block translation bounded by {@code toleranceBlocks} on each axis, before the
     * Blueprint is compiled. The mask itself is never moved and no fail-closed gate is relaxed;
     * absent leaves every pre-V2-19-14 request byte-identical, so the field is Optional.
     */
    public record MaskFeatureReconcile(int toleranceBlocks) {
        public static final int MIN_TOLERANCE_BLOCKS = 1;
        public static final int MAX_TOLERANCE_BLOCKS = 8;
        /**
         * ADR 0043 D6: the search evaluates at most one candidate per domain cell per offset, so the
         * declared domain and tolerance bound the whole pre-pass up front. Rejected, never clamped.
         */
        public static final long MAX_CANDIDATE_EVALUATIONS = 128_000_000L;

        public MaskFeatureReconcile {
            if (toleranceBlocks < MIN_TOLERANCE_BLOCKS || toleranceBlocks > MAX_TOLERANCE_BLOCKS) {
                throw new IllegalArgumentException("mask feature reconcile tolerance must be within "
                        + MIN_TOLERANCE_BLOCKS + ".." + MAX_TOLERANCE_BLOCKS + " blocks");
            }
        }

        /** Candidate offsets of the Chebyshev ball this tolerance describes, {@code (0,0)} included. */
        public long candidateCount() {
            long side = 2L * toleranceBlocks + 1L;
            return Math.multiplyExact(side, side);
        }

        void requireAffordableFor(Bounds bounds) {
            long evaluations = Math.multiplyExact(
                    Math.multiplyExact((long) bounds.width(), (long) bounds.length()), candidateCount());
            if (evaluations > MAX_CANDIDATE_EVALUATIONS) {
                throw new IllegalArgumentException("mask feature reconcile tolerance " + toleranceBlocks
                        + " needs " + evaluations + " candidate evaluations over a " + bounds.width()
                        + "x" + bounds.length() + " domain, above the " + MAX_CANDIDATE_EVALUATIONS
                        + " budget");
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

    /**
     * Declared AI reference image. {@code expectedSha256} is optional (V2-19-03): when present the
     * design path admits the file only if the bytes on disk hash to it, so an image swapped after
     * authoring cannot reach a provider unnoticed. Absent means "no declared digest" and leaves the
     * canonical request bytes unchanged for requests written before the field existed.
     */
    public record ReferenceImageSource(
            String id,
            String file,
            ReferenceImageRole role,
            Optional<String> expectedSha256
    ) {
        private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

        public ReferenceImageSource {
            id = V2Validation.slug(id, "reference image id");
            file = V2Validation.safeRelativePath(file, "reference image file");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(expectedSha256, "expectedSha256");
            expectedSha256.ifPresent(value -> {
                if (!SHA_256.matcher(value).matches()) {
                    throw new IllegalArgumentException(
                            "reference image expectedSha256 must be a lowercase SHA-256");
                }
            });
        }

        public ReferenceImageSource(String id, String file, ReferenceImageRole role) {
            this(id, file, role, Optional.empty());
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
