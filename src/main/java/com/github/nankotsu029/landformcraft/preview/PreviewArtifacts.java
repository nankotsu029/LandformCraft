package com.github.nankotsu029.landformcraft.preview;

import java.nio.file.Path;
import java.util.List;

public record PreviewArtifacts(List<Path> files) {
    public PreviewArtifacts {
        files = List.copyOf(files);
    }
}
