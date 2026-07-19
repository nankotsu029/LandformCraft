package com.github.nankotsu029.landformcraft.model.v2.design;

import java.util.Objects;
import java.util.regex.Pattern;

/** Soft draft evidence summary. Contains no pixel arrays or absolute filesystem paths. */
public record ImageDraftEvidenceV2(
        int schemaVersion,
        String algorithmVersion,
        String sourceChecksum,
        String semanticChecksum,
        SoftDraftConfirmationStateV2 confirmationState,
        int width,
        int length,
        int waterCells,
        int landCells,
        int unknownCells,
        String sourceRelativePath
) {
    public static final int VERSION = 1;

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SAFE_RELATIVE_PATH = Pattern.compile(
            "^(?!/)(?![A-Za-z]:)(?!.*\\\\)(?!.*(?:^|/)\\.\\.?(/|$))(?!.*//).+$");

    public ImageDraftEvidenceV2 {
        if (schemaVersion != VERSION) {
            throw new IllegalArgumentException("schemaVersion must be 1");
        }
        algorithmVersion = requireNonBlank(algorithmVersion, "algorithmVersion", 64);
        sourceChecksum = requireChecksum(sourceChecksum, "sourceChecksum");
        semanticChecksum = requireChecksum(semanticChecksum, "semanticChecksum");
        Objects.requireNonNull(confirmationState, "confirmationState");
        if (width < 1 || length < 1) {
            throw new IllegalArgumentException("width and length must be positive");
        }
        if (waterCells < 0 || landCells < 0 || unknownCells < 0) {
            throw new IllegalArgumentException("cell counts must not be negative");
        }
        long pixels = (long) width * length;
        if (waterCells + landCells + unknownCells != pixels) {
            throw new IllegalArgumentException("cell counts must cover every pixel");
        }
        if (sourceRelativePath != null) {
            if (sourceRelativePath.isBlank() || sourceRelativePath.length() > 512
                    || !SAFE_RELATIVE_PATH.matcher(sourceRelativePath).matches()) {
                throw new IllegalArgumentException("sourceRelativePath must be a safe relative path");
            }
        }
    }

    public ImageDraftEvidenceV2 withConfirmationState(SoftDraftConfirmationStateV2 state) {
        return new ImageDraftEvidenceV2(
                schemaVersion,
                algorithmVersion,
                sourceChecksum,
                semanticChecksum,
                state,
                width,
                length,
                waterCells,
                landCells,
                unknownCells,
                sourceRelativePath
        );
    }

    private static String requireNonBlank(String value, String fieldName, int maximumLength) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " must be non-blank and <= " + maximumLength);
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
}
