package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.model.v2.ReleaseManifestV2;

import java.nio.file.Path;

/** Result of strictly reading either a Release format 2 directory or transport ZIP. */
public record ReleaseCoreVerificationV2(
        Path verifiedPath,
        ReleaseManifestV2 manifest,
        int verifiedFiles,
        long verifiedBytes
) {
}
