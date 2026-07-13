package com.github.nankotsu029.landformcraft.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public record ImageEvidenceEntry(
        int index,
        String imageId,
        String sourceFile,
        ReferenceImageRole role,
        boolean providerSubmitted,
        ImageValidationStatus validationStatus,
        String sourceMediaType,
        long sourceBytes,
        int sourceWidth,
        int sourceHeight,
        String sourceChecksum,
        String normalizedMediaType,
        long normalizedBytes,
        int normalizedWidth,
        int normalizedHeight,
        String normalizedChecksum,
        boolean metadataDetected,
        int exifOrientation,
        List<ImageTransformation> transformations,
        List<TopDownCoordinateMapping> coordinateMappings,
        Map<CardinalDirection, Double> edgeWaterRatios
) {
    private static final Pattern IMAGE_ID = Pattern.compile("image-[0-9]{2}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ImageEvidenceEntry {
        if (index < 0 || index >= 16) {
            throw new IllegalArgumentException("index must be between 0 and 15");
        }
        imageId = ModelValidation.requireNonBlank(imageId, "imageId");
        if (!IMAGE_ID.matcher(imageId).matches()) {
            throw new IllegalArgumentException("imageId must match image-[0-9]{2}");
        }
        sourceFile = ModelValidation.requireSafeRelativePath(sourceFile, "sourceFile");
        Objects.requireNonNull(role, "role");
        if (Objects.requireNonNull(validationStatus, "validationStatus") != ImageValidationStatus.VERIFIED) {
            throw new IllegalArgumentException("image evidence must be VERIFIED");
        }
        sourceMediaType = requireMediaType(sourceMediaType, "sourceMediaType");
        normalizedMediaType = requireMediaType(normalizedMediaType, "normalizedMediaType");
        if (!"image/png".equals(normalizedMediaType)) {
            throw new IllegalArgumentException("normalizedMediaType must be image/png");
        }
        requirePositive(sourceBytes, "sourceBytes");
        requirePositive(normalizedBytes, "normalizedBytes");
        requireDimension(sourceWidth, "sourceWidth");
        requireDimension(sourceHeight, "sourceHeight");
        requireDimension(normalizedWidth, "normalizedWidth");
        requireDimension(normalizedHeight, "normalizedHeight");
        sourceChecksum = requireChecksum(sourceChecksum, "sourceChecksum");
        normalizedChecksum = requireChecksum(normalizedChecksum, "normalizedChecksum");
        if (exifOrientation < 1 || exifOrientation > 8) {
            throw new IllegalArgumentException("exifOrientation must be between 1 and 8");
        }
        transformations = ModelValidation.immutableList(transformations, "transformations", 8);
        EnumSet<ImageTransformation> transformationSet = EnumSet.noneOf(ImageTransformation.class);
        for (ImageTransformation transformation : transformations) {
            if (!transformationSet.add(Objects.requireNonNull(transformation, "transformation"))) {
                throw new IllegalArgumentException("transformations must be unique");
            }
        }
        if (!transformationSet.containsAll(EnumSet.of(
                ImageTransformation.DECODED,
                ImageTransformation.COLOR_SPACE_NORMALIZED,
                ImageTransformation.METADATA_STRIPPED,
                ImageTransformation.PNG_REENCODED
        ))) {
            throw new IllegalArgumentException("required image transformations are missing");
        }
        if ((exifOrientation != 1)
                != transformationSet.contains(ImageTransformation.ORIENTATION_NORMALIZED)) {
            throw new IllegalArgumentException("orientation transformation must match EXIF orientation");
        }
        coordinateMappings = ModelValidation.immutableList(coordinateMappings, "coordinateMappings", 1);
        Objects.requireNonNull(edgeWaterRatios, "edgeWaterRatios");
        EnumMap<CardinalDirection, Double> ratios = new EnumMap<>(CardinalDirection.class);
        edgeWaterRatios.forEach((direction, ratio) -> ratios.put(
                Objects.requireNonNull(direction, "edgeWaterRatios direction"),
                ModelValidation.requireUnitInterval(ratio, "edgeWaterRatios value")
        ));
        edgeWaterRatios = Collections.unmodifiableMap(ratios);
        boolean topDown = role == ReferenceImageRole.TOP_DOWN_SKETCH;
        if (topDown != transformationSet.contains(ImageTransformation.TOP_DOWN_COORDINATES_NORMALIZED)) {
            throw new IllegalArgumentException("coordinate transformation must match TOP_DOWN_SKETCH role");
        }
        if (topDown && (coordinateMappings.size() != 1 || edgeWaterRatios.size() != 4)) {
            throw new IllegalArgumentException("TOP_DOWN_SKETCH requires one mapping and four edge observations");
        }
        if (!topDown && (!coordinateMappings.isEmpty() || !edgeWaterRatios.isEmpty())) {
            throw new IllegalArgumentException("only TOP_DOWN_SKETCH may contain coordinate observations");
        }
    }

    public ImageEvidenceEntry withProviderSubmitted(boolean submitted) {
        return new ImageEvidenceEntry(
                index, imageId, sourceFile, role, submitted, validationStatus, sourceMediaType, sourceBytes,
                sourceWidth, sourceHeight, sourceChecksum, normalizedMediaType, normalizedBytes,
                normalizedWidth, normalizedHeight, normalizedChecksum, metadataDetected, exifOrientation,
                transformations, coordinateMappings, edgeWaterRatios
        );
    }

    private static String requireMediaType(String value, String fieldName) {
        value = ModelValidation.requireNonBlank(value, fieldName, 32);
        if (!value.equals("image/png") && !value.equals("image/jpeg")) {
            throw new IllegalArgumentException(fieldName + " must be image/png or image/jpeg");
        }
        return value;
    }

    private static String requireChecksum(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (!SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a lowercase SHA-256");
        }
        return value;
    }

    private static void requirePositive(long value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static void requireDimension(int value, String fieldName) {
        if (value < 1 || value > 4096) {
            throw new IllegalArgumentException(fieldName + " must be between 1 and 4096");
        }
    }
}
