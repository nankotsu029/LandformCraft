package com.github.nankotsu029.landformcraft.core.v2.placement.reservation;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

/** Default FileStore-backed disk probe for Release 2 reservations. */
public final class FileStoreDiskSpaceProbeV2 implements PlacementDiskSpaceProbeV2 {
    @Override
    public long usableBytes(Path root) throws IOException {
        return Files.getFileStore(root).getUsableSpace();
    }

    @Override
    public String fileStoreKey(Path root) throws IOException {
        FileStore store = Files.getFileStore(root);
        return store.name() + "|" + store.type();
    }
}
