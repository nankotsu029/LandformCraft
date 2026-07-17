package com.github.nankotsu029.landformcraft.model.v2;

import java.math.BigInteger;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable metadata and integrity references for one compact v2 field sidecar. */
public record FieldArtifactDescriptorV2(
        String relativePath,
        Definition definition,
        String encodingVersion,
        String artifactChecksum,
        String semanticChecksum,
        Provenance provenance
) {
    public static final String ENCODING_VERSION = "LFC_GRID_V1";
    public static final long FIXED_SCALE = 1_000_000L;
    public static final long MAX_ABSOLUTE_SEMANTIC_VALUE = 4_000_000_000_000L;

    private static final Pattern RELATIVE_PATH = Pattern.compile(
            "(?:[a-z0-9][a-z0-9._-]{0,63}/){0,7}[a-z0-9][a-z0-9._-]{0,63}\\.lfgrid"
    );
    private static final Pattern PROVENANCE_SOURCE = Pattern.compile(
            "(?:constraint-source|manual-source|derived-source):[a-z0-9][a-z0-9._-]{0,63}"
    );

    public FieldArtifactDescriptorV2 {
        relativePath = V2Validation.nonBlank(relativePath, "field artifact path", 320);
        if (!RELATIVE_PATH.matcher(relativePath).matches() || relativePath.contains("..")) {
            throw new IllegalArgumentException("field artifact path must be a canonical relative .lfgrid path");
        }
        Objects.requireNonNull(definition, "definition");
        if (!ENCODING_VERSION.equals(encodingVersion)) {
            throw new IllegalArgumentException("unsupported field encoding version");
        }
        artifactChecksum = V2Validation.checksum(artifactChecksum, "artifactChecksum");
        semanticChecksum = V2Validation.checksum(semanticChecksum, "semanticChecksum");
        Objects.requireNonNull(provenance, "provenance");
    }

    public record Definition(
            String fieldId,
            FieldSemantic semantic,
            FieldValueType valueType,
            int width,
            int length,
            CoordinateSpace coordinateSpace,
            Sampling sampling,
            long scaleMillionths,
            long offsetMillionths,
            boolean hasNoData,
            int noDataRaw
    ) {
        public Definition {
            fieldId = V2Validation.qualifiedId(fieldId, "fieldId");
            Objects.requireNonNull(semantic, "semantic");
            Objects.requireNonNull(valueType, "valueType");
            if (width < 1 || width > 1_000 || length < 1 || length > 1_000) {
                throw new IllegalArgumentException("field dimensions must be within 1..1000");
            }
            Objects.requireNonNull(coordinateSpace, "coordinateSpace");
            Objects.requireNonNull(sampling, "sampling");
            if (scaleMillionths == 0L || Math.abs(scaleMillionths) > 1_000_000_000L
                    || Math.abs(offsetMillionths) > MAX_ABSOLUTE_SEMANTIC_VALUE) {
                throw new IllegalArgumentException("field scale/offset is outside the fixed-point range");
            }
            if (!hasNoData && noDataRaw != 0) {
                throw new IllegalArgumentException("noDataRaw must be zero when no-data is disabled");
            }
            if (hasNoData && !valueType.contains(noDataRaw)) {
                throw new IllegalArgumentException("noDataRaw is outside the value type");
            }
            requireSemanticRange(valueType, scaleMillionths, offsetMillionths);
            if ((semantic == FieldSemantic.LAND_WATER_MASK
                    || semantic == FieldSemantic.ZONE_LABEL_MAP
                    || semantic == FieldSemantic.DESIRED_LAND_WATER
                    || semantic == FieldSemantic.ACTUAL_LAND_WATER
                    || semantic == FieldSemantic.RESIDUAL_LAND_WATER
                    || semantic == FieldSemantic.HYDROLOGY_FLOW_DIRECTION
                    || semantic == FieldSemantic.HYDROLOGY_FLOW_ACCUMULATION
                    || semantic == FieldSemantic.GEOLOGY_PROVINCE_ID
                    || semantic == FieldSemantic.GEOLOGY_FORMATION_ID
                    || semantic == FieldSemantic.GEOLOGY_HARDNESS
                    || semantic == FieldSemantic.GEOLOGY_PERMEABILITY)
                    && sampling != Sampling.NEAREST) {
                throw new IllegalArgumentException("categorical and routing fields require nearest sampling");
            }
        }

        public long cellCount() {
            return (long) width * length;
        }

        public long payloadBytes() {
            return Math.multiplyExact(cellCount(), valueType.bytesPerValue());
        }

        public void requireRawValue(int rawValue) {
            if (!valueType.contains(rawValue)) {
                throw new IllegalArgumentException("raw field value is outside " + valueType);
            }
        }

        public boolean isNoData(int rawValue) {
            return hasNoData && rawValue == noDataRaw;
        }

        public long semanticValueMillionths(int rawValue) {
            requireRawValue(rawValue);
            if (isNoData(rawValue)) {
                throw new IllegalStateException("no-data has no semantic numeric value");
            }
            return Math.addExact(Math.multiplyExact((long) rawValue, scaleMillionths), offsetMillionths);
        }

        private static void requireSemanticRange(
                FieldValueType valueType,
                long scaleMillionths,
                long offsetMillionths
        ) {
            BigInteger scale = BigInteger.valueOf(scaleMillionths);
            BigInteger offset = BigInteger.valueOf(offsetMillionths);
            BigInteger first = BigInteger.valueOf(valueType.minimumRaw()).multiply(scale).add(offset);
            BigInteger second = BigInteger.valueOf(valueType.maximumRaw()).multiply(scale).add(offset);
            BigInteger limit = BigInteger.valueOf(MAX_ABSOLUTE_SEMANTIC_VALUE);
            if (first.abs().compareTo(limit) > 0 || second.abs().compareTo(limit) > 0) {
                throw new IllegalArgumentException("field semantic fixed-point range is too large");
            }
        }
    }

    public record Provenance(
            SourceKind sourceKind,
            String sourceId,
            String sourceChecksum,
            String decoderId,
            String decoderVersion,
            String transformId
    ) {
        public Provenance {
            Objects.requireNonNull(sourceKind, "sourceKind");
            sourceId = V2Validation.nonBlank(sourceId, "sourceId", 96);
            if (!PROVENANCE_SOURCE.matcher(sourceId).matches()
                    || !sourceId.startsWith(sourceKind.prefix())) {
                throw new IllegalArgumentException("sourceId does not match the provenance source kind");
            }
            sourceChecksum = V2Validation.checksum(sourceChecksum, "sourceChecksum");
            decoderId = V2Validation.qualifiedId(decoderId, "decoderId");
            decoderVersion = V2Validation.nonBlank(decoderVersion, "decoderVersion", 64);
            transformId = V2Validation.qualifiedId(transformId, "transformId");
        }
    }

    public enum FieldSemantic {
        LAND_WATER_MASK,
        HEIGHT_GUIDE,
        ZONE_LABEL_MAP,
        DESIRED_LAND_WATER,
        ACTUAL_LAND_WATER,
        RESIDUAL_LAND_WATER,
        DESIRED_HEIGHT,
        ACTUAL_HEIGHT,
        RESIDUAL_HEIGHT,
        HYDROLOGY_FLOW_DIRECTION,
        HYDROLOGY_FLOW_ACCUMULATION,
        GEOLOGY_PROVINCE_ID,
        GEOLOGY_FORMATION_ID,
        GEOLOGY_HARDNESS,
        GEOLOGY_PERMEABILITY
    }

    public enum FieldValueType {
        U8(1, 0, 255),
        U16(2, 0, 65_535),
        I32(4, Integer.MIN_VALUE, Integer.MAX_VALUE);

        private final int bytesPerValue;
        private final int minimumRaw;
        private final int maximumRaw;

        FieldValueType(int bytesPerValue, int minimumRaw, int maximumRaw) {
            this.bytesPerValue = bytesPerValue;
            this.minimumRaw = minimumRaw;
            this.maximumRaw = maximumRaw;
        }

        public int bytesPerValue() {
            return bytesPerValue;
        }

        public int minimumRaw() {
            return minimumRaw;
        }

        public int maximumRaw() {
            return maximumRaw;
        }

        public boolean contains(int rawValue) {
            return rawValue >= minimumRaw && rawValue <= maximumRaw;
        }
    }

    public enum CoordinateSpace {
        RELEASE_LOCAL_XZ
    }

    public enum Sampling {
        NEAREST,
        BILINEAR_FIXED
    }

    public enum SourceKind {
        MANUAL("manual-source:"),
        CONSTRAINT_MAP("constraint-source:"),
        DERIVED("derived-source:");

        private final String prefix;

        SourceKind(String prefix) {
            this.prefix = prefix;
        }

        String prefix() {
            return prefix;
        }
    }
}
