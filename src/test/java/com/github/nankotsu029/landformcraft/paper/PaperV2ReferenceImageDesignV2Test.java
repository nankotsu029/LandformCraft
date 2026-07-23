package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.ai.spi.v2.DesignExceptionV2;
import com.github.nankotsu029.landformcraft.ai.spi.v2.DesignFailureCodeV2;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignArtifactsV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-19-03: the Paper offline design verb reaches the same repaired path as the CLI. The adapter is
 * exercised directly (no {@code JavaPlugin} instance, per AGENTS §13) with a plugin-owned workspace,
 * which is exactly what {@code /lfc v2 design} hands it.
 */
class PaperV2ReferenceImageDesignV2Test {
    @Test
    void theOfflineDesignVerbPublishesAnImageCarryingRequestFromTheWorkspace(@TempDir Path root)
            throws Exception {
        Path workspace = workspaceWithObliqueMultiView(root);
        try (GenerationExecutors executors = GenerationExecutors.create(2, 1, 4)) {
            PaperV2WorkflowServiceV2 workflow = new PaperV2WorkflowServiceV2(executors, null, workspace);

            DesignArtifactsV2 artifacts = workflow
                    .design("fixture", "requests/oblique-multi-view.request-v2.json", "terrain-intent-v2.json")
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);

            assertEquals("oblique-multi-view-64", artifacts.audit().requestId());
            assertEquals("fixture-v2", artifacts.audit().providerId());
            assertTrue(artifacts.directory().startsWith(workspace.resolve("designs")),
                    "the design package stays inside the plugin workspace: " + artifacts.directory());
            assertTrue(Files.isRegularFile(artifacts.directory().resolve("terrain-intent-v2.json")));
        }
    }

    @Test
    void anImageDeclaredOutsideTheWorkspaceIsRejectedAsAPathFailure(@TempDir Path root) throws Exception {
        Path workspace = workspaceWithObliqueMultiView(root);
        Path escaping = workspace.resolve("requests/references/cove-north.png");
        Path outside = root.resolve("outside.png");
        Files.move(escaping, outside);
        Files.createSymbolicLink(escaping, outside);

        try (GenerationExecutors executors = GenerationExecutors.create(2, 1, 4)) {
            PaperV2WorkflowServiceV2 workflow = new PaperV2WorkflowServiceV2(executors, null, workspace);

            CompletionException failure = assertThrows(CompletionException.class, () -> workflow
                    .design("fixture", "requests/oblique-multi-view.request-v2.json", "terrain-intent-v2.json")
                    .toCompletableFuture()
                    .join());

            Throwable cause = failure.getCause();
            assertTrue(cause instanceof DesignExceptionV2, String.valueOf(cause));
            assertEquals(DesignFailureCodeV2.PATH_SECURITY, ((DesignExceptionV2) cause).code());
            assertFalse(Files.exists(workspace.resolve("designs").resolve("oblique-multi-view-64")),
                    "a rejected design must not leave a package behind");
        }
    }

    private static Path workspaceWithObliqueMultiView(Path root) throws IOException {
        Path workspace = root.resolve("plugin-workspace");
        Path requests = workspace.resolve("requests");
        Files.createDirectories(requests.resolve("references"));
        Files.copy(Path.of("examples/v2/diagnostic/oblique-multi-view.request-v2.json"),
                requests.resolve("oblique-multi-view.request-v2.json"));
        Files.copy(Path.of("examples/v2/diagnostic/oblique-multi-view.terrain-intent-v2.json"),
                requests.resolve("terrain-intent-v2.json"));
        try (var images = Files.list(Path.of("examples/v2/diagnostic/references"))) {
            for (Path image : images.sorted().toList()) {
                Files.copy(image, requests.resolve("references").resolve(image.getFileName().toString()));
            }
        }
        return workspace;
    }
}
