package com.github.nankotsu029.landformcraft.format;

import com.github.nankotsu029.landformcraft.model.ExportManifest;

import java.nio.file.Path;

public record ReleaseVerification(Path verifiedPath, ExportManifest manifest, int verifiedFiles, int verifiedTiles) {
}
