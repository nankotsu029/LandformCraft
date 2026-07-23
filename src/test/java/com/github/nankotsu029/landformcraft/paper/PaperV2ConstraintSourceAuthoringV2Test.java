package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.ExtractedMaskPromotionOptionsV2;
import com.github.nankotsu029.landformcraft.core.v2.ImageExtractionWorkflowServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2RequestStoreV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2-19-04 Paper parity for the generic constraint-source declaration. The adapter is exercised
 * directly (no {@code JavaPlugin}, AGENTS §13) because that is all {@code /lfc v2 request
 * constraint-source} does with the operator's tokens.
 */
class PaperV2ConstraintSourceAuthoringV2Test {
    private static final CancellationToken NEVER = () -> false;

    @Test
    void theOperatorCanDeclareAPromotedSourceFromInsideTheWorkspace(@TempDir Path root) throws Exception {
        Path workspace = root.resolve("plugin-workspace");
        new V2RequestStoreV2(workspace.resolve("requests")).create("paper-authoring");
        promoteLandWater(workspace.resolve("promoted"), root.resolve("coast.png"));

        try (GenerationExecutors executors = GenerationExecutors.create(2, 1, 4)) {
            PaperV2WorkflowServiceV2 workflow = new PaperV2WorkflowServiceV2(executors, null, workspace);

            GenerationRequestV2 request = workflow.setConstraintSource(
                    "paper-authoring", "coast-mask",
                    TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                    "promoted", "maps/coast.png")
                    .toCompletableFuture().get(30, TimeUnit.SECONDS);

            assertEquals(1, request.constraintMaps().size());
            assertEquals("constraint-source:coast-mask", request.constraintMaps().getFirst().sourceId());
            assertEquals("maps/coast.png", request.constraintMaps().getFirst().file());
        }
    }

    @Test
    void aPromotionDirectoryOutsideTheWorkspaceIsRejected(@TempDir Path root) throws Exception {
        Path workspace = root.resolve("plugin-workspace");
        new V2RequestStoreV2(workspace.resolve("requests")).create("paper-authoring");
        promoteLandWater(root.resolve("outside"), root.resolve("coast.png"));

        try (GenerationExecutors executors = GenerationExecutors.create(2, 1, 4)) {
            PaperV2WorkflowServiceV2 workflow = new PaperV2WorkflowServiceV2(executors, null, workspace);

            assertThrows(IllegalArgumentException.class, () -> workflow.setConstraintSource(
                    "paper-authoring", "coast-mask",
                    TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK,
                    "../outside", "maps/coast.png"));
        }
    }

    private static void promoteLandWater(Path promotionDirectory, Path image) throws IOException {
        BufferedImage raster = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < 8; z++) {
            for (int x = 0; x < 8; x++) {
                raster.setRGB(x, z, x < 4 ? 0x46_8C_46 : 0x0A_28_DC);
            }
        }
        Files.createDirectories(image.getParent());
        assertTrue(ImageIO.write(raster, "png", image.toFile()), "failed to write " + image);
        ImageExtractionWorkflowServiceV2 workflow = new ImageExtractionWorkflowServiceV2();
        Path draft = promotionDirectory.resolveSibling(promotionDirectory.getFileName() + "-draft");
        workflow.extractLandWater(image, draft, NEVER);
        workflow.promoteLandWater(
                draft, promotionDirectory, ExtractedMaskPromotionOptionsV2.rejectBelow(1), NEVER);
    }
}
