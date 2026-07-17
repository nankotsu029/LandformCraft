package com.github.nankotsu029.landformcraft.format.v2.release;

import com.github.nankotsu029.landformcraft.model.v2.ReleaseArtifactDescriptorV2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Path/type consumption tracker shared by Release 2 capability verifiers. */
final class ReleaseCapabilityArtifactIndexV2 {
    private static final int ARTIFACT_VERSION = 1;

    private final Map<String, ReleaseArtifactDescriptorV2> byPath = new HashMap<>();
    private final Map<String, List<ReleaseArtifactDescriptorV2>> byType = new HashMap<>();
    private final Set<String> consumed = new HashSet<>();

    ReleaseCapabilityArtifactIndexV2(List<ReleaseArtifactDescriptorV2> artifacts) throws IOException {
        for (ReleaseArtifactDescriptorV2 artifact : artifacts) {
            if (byPath.putIfAbsent(artifact.path(), artifact) != null) {
                throw new IOException("Release 2 contains a duplicate artifact path");
            }
            byType.computeIfAbsent(artifact.artifactType(), ignored -> new ArrayList<>()).add(artifact);
        }
    }

    ReleaseArtifactDescriptorV2 singleton(String type, String path) throws IOException {
        List<ReleaseArtifactDescriptorV2> values = ofType(type);
        if (values.size() != 1 || !values.getFirst().path().equals(path)) {
            throw new IOException("Release 2 required artifact set differs for " + type);
        }
        return requirePath(path, type);
    }

    ReleaseArtifactDescriptorV2 requirePath(String path, String type) throws IOException {
        ReleaseArtifactDescriptorV2 value = byPath.get(path);
        if (value == null || !value.artifactType().equals(type)
                || value.artifactVersion() != ARTIFACT_VERSION) {
            throw new IOException("Release 2 artifact type/version/path is invalid: " + path);
        }
        consumed.add(path);
        return value;
    }

    List<ReleaseArtifactDescriptorV2> ofType(String type) {
        return byType.getOrDefault(type, List.of()).stream()
                .sorted(java.util.Comparator.comparing(ReleaseArtifactDescriptorV2::path)).toList();
    }

    void requireNoUnexpectedArtifacts() throws IOException {
        if (consumed.size() != byPath.size()) {
            List<String> unexpected = byPath.keySet().stream().filter(path -> !consumed.contains(path))
                    .sorted().toList();
            throw new IOException("Release 2 contains an unexpected artifact: " + unexpected.getFirst());
        }
    }
}
