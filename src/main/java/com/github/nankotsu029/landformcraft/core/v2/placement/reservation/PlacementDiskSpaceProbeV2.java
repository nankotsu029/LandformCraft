package com.github.nankotsu029.landformcraft.core.v2.placement.reservation;

import java.io.IOException;
import java.nio.file.Path;

/** Probe for usable disk and FileStore identity. Tests inject fixed values. */
public interface PlacementDiskSpaceProbeV2 {
    long usableBytes(Path root) throws IOException;

    String fileStoreKey(Path root) throws IOException;
}
