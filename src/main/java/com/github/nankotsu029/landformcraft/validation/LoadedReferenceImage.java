package com.github.nankotsu029.landformcraft.validation;

import com.github.nankotsu029.landformcraft.model.ReferenceImageRole;

import java.util.Objects;

public record LoadedReferenceImage(
        int index,
        String sourceFile,
        ReferenceImageRole role,
        String mediaType,
        byte[] content
) {
    public LoadedReferenceImage {
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(content, "content");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
