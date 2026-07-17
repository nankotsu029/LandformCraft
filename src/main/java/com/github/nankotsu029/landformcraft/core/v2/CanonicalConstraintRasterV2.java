package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.constraint.DecodedNumericRaster;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngEncoding;
import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Lazy canonical desired/actual/residual views for one decoded and semantically bound map. */
public final class CanonicalConstraintRasterV2 {
    public static final int FIXED_SCALE = 1_000_000;

    private final GenerationRequestV2.Bounds bounds;
    private final GenerationRequestV2.ConstraintMapSource source;
    private final TerrainIntentV2.ConstraintMapBinding binding;
    private final DecodedNumericRaster raster;
    private final ConstraintMapSamplerV2 sampler;
    private final Map<Integer, Integer> categoricalValues;
    private final int noDataRawSample;
    private final boolean hasNoData;

    public CanonicalConstraintRasterV2(
            GenerationRequestV2.Bounds bounds,
            GenerationRequestV2.ConstraintMapSource source,
            TerrainIntentV2.ConstraintMapBinding binding,
            DecodedNumericRaster raster,
            CancellationToken cancellationToken
    ) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.source = Objects.requireNonNull(source, "source");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.raster = Objects.requireNonNull(raster, "raster");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (!source.sourceId().equals(binding.sourceId())) {
            throw failure(ConstraintCompilationFailureCodeV2.INVALID_BINDING,
                    "constraint binding references a different source");
        }
        if (raster.width() != source.expectedWidth() || raster.length() != source.expectedLength()) {
            throw failure(ConstraintCompilationFailureCodeV2.DIMENSIONS_MISMATCH,
                    "decoded constraint dimensions do not match the request");
        }
        requireSampleType();
        requireRoleAndSampling();
        this.sampler = new ConstraintMapSamplerV2(raster.width(), raster.length(), source.coordinateMapping());
        this.hasNoData = source.encoding().noData() instanceof GenerationRequestV2.NoDataSentinel;
        this.noDataRawSample = hasNoData
                ? ((GenerationRequestV2.NoDataSentinel) source.encoding().noData()).sample() : -1;
        this.categoricalValues = categoricalValues();
        validateEverySourceSample(cancellationToken);
    }

    public GenerationRequestV2.ConstraintMapSource source() {
        return source;
    }

    public TerrainIntentV2.ConstraintMapBinding binding() {
        return binding;
    }

    public int desiredRawAt(int x, int z) {
        return switch (binding.role()) {
            case LAND_WATER_MASK, ZONE_LABEL_MAP -> categoricalRawAt(x, z);
            case HEIGHT_GUIDE -> heightRawAt(x, z);
        };
    }

    public int actualRawAt(int x, int z) {
        int desired = desiredRawAt(x, z);
        if (isCanonicalNoData(desired)) return desired;
        return switch (binding.role()) {
            case LAND_WATER_MASK, ZONE_LABEL_MAP -> desired;
            case HEIGHT_GUIDE -> roundToWholeBlockWithinBounds(desired);
        };
    }

    public int residualRawAt(int x, int z) {
        int desired = desiredRawAt(x, z);
        if (isCanonicalNoData(desired)) return Integer.MIN_VALUE;
        int difference = Math.subtractExact(desired, actualRawAt(x, z));
        return binding.role() == TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK
                ? Math.multiplyExact(difference, FIXED_SCALE) : difference;
    }

    /** Converts the role-specific sidecar sentinel to the renderer's common no-data value. */
    public int desiredDiagnosticAt(int x, int z) {
        int value = desiredRawAt(x, z);
        return isCanonicalNoData(value) ? Integer.MIN_VALUE : value;
    }

    /** Converts the role-specific sidecar sentinel to the renderer's common no-data value. */
    public int actualDiagnosticAt(int x, int z) {
        int value = actualRawAt(x, z);
        return isCanonicalNoData(value) ? Integer.MIN_VALUE : value;
    }

    public int residualDiagnosticAt(int x, int z) {
        return residualRawAt(x, z);
    }

    public int constraintErrorAt(int x, int z) {
        int desired = desiredRawAt(x, z);
        if (isCanonicalNoData(desired)) {
            return binding.strength() == TerrainIntentV2.Strength.HARD ? 1 : 0;
        }
        long tolerance = (long) binding.toleranceBlocks() * FIXED_SCALE;
        return switch (binding.role()) {
            case LAND_WATER_MASK, ZONE_LABEL_MAP -> desired == actualRawAt(x, z) ? 0 : 1;
            case HEIGHT_GUIDE -> Math.abs((long) residualRawAt(x, z)) <= tolerance ? 0 : 1;
        };
    }

    public FieldArtifactDescriptorV2.Definition desiredDefinition(String fieldId) {
        return switch (binding.role()) {
            case LAND_WATER_MASK -> definition(
                    fieldId,
                    FieldArtifactDescriptorV2.FieldSemantic.DESIRED_LAND_WATER,
                    FieldArtifactDescriptorV2.FieldValueType.U8,
                    FieldArtifactDescriptorV2.Sampling.NEAREST,
                    FIXED_SCALE,
                    canonicalNoData());
            case HEIGHT_GUIDE -> definition(
                    fieldId,
                    FieldArtifactDescriptorV2.FieldSemantic.DESIRED_HEIGHT,
                    FieldArtifactDescriptorV2.FieldValueType.I32,
                    fieldSampling(),
                    1,
                    canonicalNoData());
            case ZONE_LABEL_MAP -> definition(
                    fieldId,
                    FieldArtifactDescriptorV2.FieldSemantic.ZONE_LABEL_MAP,
                    FieldArtifactDescriptorV2.FieldValueType.U16,
                    FieldArtifactDescriptorV2.Sampling.NEAREST,
                    FIXED_SCALE,
                    canonicalNoData());
        };
    }

    public FieldArtifactDescriptorV2.Definition actualDefinition(String fieldId) {
        return switch (binding.role()) {
            case LAND_WATER_MASK -> definition(
                    fieldId,
                    FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_LAND_WATER,
                    FieldArtifactDescriptorV2.FieldValueType.U8,
                    FieldArtifactDescriptorV2.Sampling.NEAREST,
                    FIXED_SCALE,
                    canonicalNoData());
            case HEIGHT_GUIDE -> definition(
                    fieldId,
                    FieldArtifactDescriptorV2.FieldSemantic.ACTUAL_HEIGHT,
                    FieldArtifactDescriptorV2.FieldValueType.I32,
                    fieldSampling(),
                    1,
                    canonicalNoData());
            case ZONE_LABEL_MAP -> desiredDefinition(fieldId);
        };
    }

    public FieldArtifactDescriptorV2.Definition residualDefinition(String fieldId) {
        return switch (binding.role()) {
            case LAND_WATER_MASK -> definition(
                    fieldId,
                    FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_LAND_WATER,
                    FieldArtifactDescriptorV2.FieldValueType.I32,
                    FieldArtifactDescriptorV2.Sampling.NEAREST,
                    1,
                    Integer.MIN_VALUE);
            case HEIGHT_GUIDE -> definition(
                    fieldId,
                    FieldArtifactDescriptorV2.FieldSemantic.RESIDUAL_HEIGHT,
                    FieldArtifactDescriptorV2.FieldValueType.I32,
                    fieldSampling(),
                    1,
                    Integer.MIN_VALUE);
            case ZONE_LABEL_MAP -> throw failure(
                    ConstraintCompilationFailureCodeV2.INVALID_BINDING,
                    "zone label maps do not define a residual field in V2-1");
        };
    }

    public FieldArtifactDescriptorV2.Provenance provenance() {
        return new FieldArtifactDescriptorV2.Provenance(
                FieldArtifactDescriptorV2.SourceKind.CONSTRAINT_MAP,
                source.sourceId(),
                raster.sourceChecksum(),
                "numeric-png",
                "1",
                ConstraintMapSamplerV2.CANONICALIZATION_VERSION);
    }

    public String artifactId(FieldArtifactDescriptorV2 descriptor) {
        String semantic = switch (binding.role()) {
            case LAND_WATER_MASK -> "land-water";
            case HEIGHT_GUIDE -> "height-guide";
            case ZONE_LABEL_MAP -> "zone-label-map";
        };
        return "constraint:" + semantic + ":sha256-" + descriptor.semanticChecksum();
    }

    public List<ConstraintFieldIndexV2.LabelEntry> canonicalLabels() {
        if (!(source.encoding() instanceof GenerationRequestV2.CategoricalEncoding categorical)) {
            return List.of();
        }
        return categorical.labels().stream()
                .map(label -> new ConstraintFieldIndexV2.LabelEntry(
                        label.sample(), categoricalValues.get(label.sample()), label.label()))
                .sorted(Comparator.comparingInt(ConstraintFieldIndexV2.LabelEntry::sourceSample))
                .toList();
    }

    public boolean hasAnyHardError(CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (binding.strength() != TerrainIntentV2.Strength.HARD) return false;
        for (int z = 0; z < bounds.length(); z++) {
            if ((z & 31) == 0) cancellationToken.throwIfCancellationRequested();
            for (int x = 0; x < bounds.width(); x++) {
                if (constraintErrorAt(x, z) != 0) return true;
            }
        }
        return false;
    }

    private void requireSampleType() {
        GenerationRequestV2.SampleType requested = source.encoding().sampleType();
        NumericPngEncoding.SampleType decoded = raster.sampleType();
        boolean kindMatches = source.encoding() instanceof GenerationRequestV2.HeightEncoding
                ? raster.kind() == NumericPngEncoding.NumericKind.HEIGHT
                : raster.kind() == NumericPngEncoding.NumericKind.CATEGORICAL;
        if (!requested.name().equals(decoded.name()) || !kindMatches) {
            throw failure(ConstraintCompilationFailureCodeV2.INVALID_BINDING,
                    "decoded numeric raster does not match request encoding");
        }
    }

    private void requireRoleAndSampling() {
        boolean height = source.encoding() instanceof GenerationRequestV2.HeightEncoding;
        if (height != (binding.role() == TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE)) {
            throw failure(ConstraintCompilationFailureCodeV2.INVALID_BINDING,
                    "constraint role does not match numeric encoding");
        }
        if (binding.role() != TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE
                && binding.sampling() != TerrainIntentV2.Sampling.NEAREST) {
            throw failure(ConstraintCompilationFailureCodeV2.INVALID_BINDING,
                    "categorical constraint maps require nearest sampling");
        }
    }

    private Map<Integer, Integer> categoricalValues() {
        if (!(source.encoding() instanceof GenerationRequestV2.CategoricalEncoding categorical)) {
            return Map.of();
        }
        Map<Integer, Integer> result = new HashMap<>();
        if (binding.role() == TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK) {
            for (GenerationRequestV2.LabelMapping label : categorical.labels()) {
                int value = switch (label.label()) {
                    case "water" -> 0;
                    case "land" -> 1;
                    default -> throw failure(ConstraintCompilationFailureCodeV2.UNKNOWN_LABEL,
                            "land-water encoding contains an unknown label");
                };
                result.put(label.sample(), value);
            }
            if (!result.containsValue(0) || !result.containsValue(1)) {
                throw failure(ConstraintCompilationFailureCodeV2.UNKNOWN_LABEL,
                        "land-water encoding requires exact water and land labels");
            }
        } else {
            var labels = categorical.labels().stream()
                    .sorted(Comparator.comparing(GenerationRequestV2.LabelMapping::label))
                    .toList();
            for (int index = 0; index < labels.size(); index++) {
                result.put(labels.get(index).sample(), index + 1);
            }
        }
        return Map.copyOf(result);
    }

    private void validateEverySourceSample(CancellationToken cancellationToken) {
        for (int z = 0; z < raster.length(); z++) {
            if ((z & 31) == 0) cancellationToken.throwIfCancellationRequested();
            for (int x = 0; x < raster.width(); x++) {
                int sample = raster.sample(x, z);
                if (isSourceNoData(sample)) {
                    if (binding.strength() == TerrainIntentV2.Strength.HARD) {
                        throw failure(ConstraintCompilationFailureCodeV2.INVALID_NO_DATA,
                                "hard constraint map contains no-data");
                    }
                } else if (source.encoding() instanceof GenerationRequestV2.CategoricalEncoding) {
                    if (!categoricalValues.containsKey(sample)) {
                        throw failure(ConstraintCompilationFailureCodeV2.UNKNOWN_LABEL,
                                "categorical map contains an unmapped sample");
                    }
                } else {
                    var height = (GenerationRequestV2.HeightEncoding) source.encoding();
                    if (sample < height.validSampleRange().minimum()
                            || sample > height.validSampleRange().maximum()) {
                        throw failure(ConstraintCompilationFailureCodeV2.SAMPLE_OUT_OF_RANGE,
                                "height map contains a sample outside validSampleRange");
                    }
                }
            }
        }
    }

    private int categoricalRawAt(int x, int z) {
        int raw = sampler.sampleNearest(
                x, z, bounds.width(), bounds.length(), raster::sample);
        if (isSourceNoData(raw)) return canonicalNoData();
        Integer value = categoricalValues.get(raw);
        if (value == null) {
            throw failure(ConstraintCompilationFailureCodeV2.UNKNOWN_LABEL,
                    "categorical map contains an unmapped sample");
        }
        return value;
    }

    private int heightRawAt(int x, int z) {
        if (binding.sampling() == TerrainIntentV2.Sampling.NEAREST) {
            int raw = sampler.sampleNearest(
                    x, z, bounds.width(), bounds.length(), raster::sample);
            if (isSourceNoData(raw)) return canonicalNoData();
            return Math.toIntExact(heightSemanticValue(raw));
        }
        ConstraintMapSamplerV2.SemanticSample sampled = sampler.sampleFixedBilinear(
                x,
                z,
                bounds.width(),
                bounds.length(),
                (rawX, rawZ) -> {
                    int raw = raster.sample(rawX, rawZ);
                    return isSourceNoData(raw)
                            ? ConstraintMapSamplerV2.SemanticSample.missing()
                            : ConstraintMapSamplerV2.SemanticSample.value(heightSemanticValue(raw));
                });
        return sampled.noData() ? canonicalNoData() : Math.toIntExact(sampled.valueMillionths());
    }

    private long heightSemanticValue(int sample) {
        var encoding = (GenerationRequestV2.HeightEncoding) source.encoding();
        long base = switch (encoding.valueMeaning()) {
            case ABSOLUTE_BLOCK_Y -> 0L;
            case BLOCKS_ABOVE_REQUEST_MIN_Y -> (long) bounds.minY() * FIXED_SCALE;
            case BLOCKS_RELATIVE_TO_WATER_LEVEL -> (long) bounds.waterLevel() * FIXED_SCALE;
        };
        return Math.addExact(base, Math.addExact(
                Math.multiplyExact((long) sample, encoding.valueScaleMillionths()),
                encoding.valueOffsetMillionths()));
    }

    private FieldArtifactDescriptorV2.Definition definition(
            String fieldId,
            FieldArtifactDescriptorV2.FieldSemantic semantic,
            FieldArtifactDescriptorV2.FieldValueType valueType,
            FieldArtifactDescriptorV2.Sampling sampling,
            long scaleMillionths,
            int noData
    ) {
        return new FieldArtifactDescriptorV2.Definition(
                fieldId,
                semantic,
                valueType,
                bounds.width(),
                bounds.length(),
                FieldArtifactDescriptorV2.CoordinateSpace.RELEASE_LOCAL_XZ,
                sampling,
                scaleMillionths,
                0,
                hasNoData,
                hasNoData ? noData : 0);
    }

    private FieldArtifactDescriptorV2.Sampling fieldSampling() {
        return binding.sampling() == TerrainIntentV2.Sampling.NEAREST
                ? FieldArtifactDescriptorV2.Sampling.NEAREST
                : FieldArtifactDescriptorV2.Sampling.BILINEAR_FIXED;
    }

    private boolean isSourceNoData(int raw) {
        return hasNoData && raw == noDataRawSample;
    }

    private boolean isCanonicalNoData(int raw) {
        return hasNoData && raw == canonicalNoData();
    }

    private int canonicalNoData() {
        return switch (binding.role()) {
            case LAND_WATER_MASK -> 255;
            case ZONE_LABEL_MAP -> 65_535;
            case HEIGHT_GUIDE -> Integer.MIN_VALUE;
        };
    }

    private int roundToWholeBlockWithinBounds(int valueMillionths) {
        long magnitude = Math.abs((long) valueMillionths);
        long rounded = (magnitude + FIXED_SCALE / 2L) / FIXED_SCALE * FIXED_SCALE;
        long signed = valueMillionths < 0 ? -rounded : rounded;
        long minimum = (long) bounds.minY() * FIXED_SCALE;
        long maximum = (long) bounds.maxY() * FIXED_SCALE;
        return Math.toIntExact(Math.max(minimum, Math.min(maximum, signed)));
    }

    private static ConstraintCompilationExceptionV2 failure(
            ConstraintCompilationFailureCodeV2 code,
            String message
    ) {
        return new ConstraintCompilationExceptionV2(code, message);
    }
}
