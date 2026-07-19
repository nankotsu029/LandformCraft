package com.github.nankotsu029.landformcraft.core.v2.placement.apply;

import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.WorldAabbV2;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, restartable source for the same final block stream used by preview/export.
 * Implementations are internal adapters over already verified Release 2 artifacts or a sealed
 * final resolver; dynamic feature plugins are not accepted at this boundary.
 */
public interface PlacementCanonicalBlockSourceV2 {
    String SOURCE_CONTRACT_VERSION = "release-2-placement-canonical-block-source-v1";

    SourceBindingV2 binding();

    /**
     * Opens a bounded cursor containing exactly one final block for every coordinate in
     * {@code mutationRegion}, in canonical X-fastest, then Z, then Y order. Repeated cursors must
     * return byte-identical values.
     */
    BlockCursorV2 openTile(
            PlacementPlanV2 plan,
            PlacementPlanV2.TileRefV2 tile,
            WorldAabbV2 mutationRegion
    ) throws IOException;

    interface BlockCursorV2 extends AutoCloseable {
        /** Returns the next canonical block or {@code null} at the stable end of the stream. */
        PlacementDesiredBlockV2 next() throws IOException;

        @Override
        void close() throws IOException;
    }

    record SourceBindingV2(
            String sourceContractVersion,
            String releaseManifestChecksum,
            List<String> requiredCapabilities,
            List<Integer> overlayOrdinals,
            String immutableFingerprint
    ) {
        private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

        public SourceBindingV2 {
            if (!SOURCE_CONTRACT_VERSION.equals(sourceContractVersion)) {
                throw new IllegalArgumentException("unknown canonical block source contract");
            }
            releaseManifestChecksum = checksum(releaseManifestChecksum, "releaseManifestChecksum");
            Objects.requireNonNull(requiredCapabilities, "requiredCapabilities");
            requiredCapabilities = requiredCapabilities.stream().map(value -> {
                if (value == null || value.isBlank() || value.length() > 64) {
                    throw new IllegalArgumentException("invalid source capability");
                }
                return value;
            }).sorted().toList();
            if (requiredCapabilities.stream().distinct().count() != requiredCapabilities.size()) {
                throw new IllegalArgumentException("source capabilities must be unique");
            }
            Objects.requireNonNull(overlayOrdinals, "overlayOrdinals");
            overlayOrdinals = List.copyOf(overlayOrdinals);
            if (overlayOrdinals.isEmpty()) {
                throw new IllegalArgumentException("source requires at least one overlay ordinal");
            }
            int previous = -1;
            for (Integer ordinal : overlayOrdinals) {
                if (ordinal == null || ordinal < 0
                        || ordinal > PlacementDesiredBlockV2.MAXIMUM_OVERLAY_ORDINAL
                        || ordinal <= previous) {
                    throw new IllegalArgumentException(
                            "source overlay ordinals must be strictly increasing and bounded");
                }
                previous = ordinal;
            }
            immutableFingerprint = checksum(immutableFingerprint, "immutableFingerprint");
        }

        private static String checksum(String value, String field) {
            if (value == null || !CHECKSUM.matcher(value).matches()) {
                throw new IllegalArgumentException(field + " must be a lowercase sha-256 digest");
            }
            return value;
        }
    }
}
