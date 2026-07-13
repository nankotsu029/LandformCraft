package com.github.nankotsu029.landformcraft.core;

import com.github.nankotsu029.landformcraft.ai.spi.ImportedJsonTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.model.GenerationStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperWorkflowServiceTest {
    @Test
    void persistsMonotonicJobsAndListsValidatedCandidatesByRequest(@TempDir Path directory) throws Exception {
        try (GenerationExecutors executors = GenerationExecutors.create(8, 2, 16);
             PaperWorkflowService workflow = new PaperWorkflowService(
                     directory, executors,
                     (provider, model) -> new ImportedJsonTerrainDesignProvider(
                             executors, Path.of("examples/rocky-coast/terrain-intent.json")),
                     Clock.systemUTC())) {
            workflow.createRequest("paper-workflow-test").join();
            var designId = workflow.startDesign("paper-workflow-test", "import", "fixture.json");
            awaitStage(workflow, designId, GenerationStage.READY, Duration.ofSeconds(10));

            var generationId = workflow.startGenerate(designId);
            assertFalse(terminal(workflow.jobStatus(generationId).join().stage()));
            awaitStage(workflow, generationId, GenerationStage.READY, Duration.ofSeconds(20));

            assertEquals(java.util.List.of(generationId), workflow.candidates("paper-workflow-test").join());
            var files = workflow.candidateValidate(generationId).join();
            assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("validation.png")));
            assertEquals(GenerationStage.READY, workflow.jobStatus(generationId).join().stage());
        }
    }

    private static void awaitStage(
            PaperWorkflowService workflow, java.util.UUID jobId, GenerationStage expected, Duration timeout
    ) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            GenerationStage stage = workflow.jobStatus(jobId).join().stage();
            if (stage == expected) {
                return;
            }
            if (terminal(stage)) {
                throw new AssertionError("job ended in " + stage + " instead of " + expected);
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("job did not reach " + expected + " before timeout");
    }

    private static boolean terminal(GenerationStage stage) {
        return stage == GenerationStage.READY || stage == GenerationStage.FAILED
                || stage == GenerationStage.CANCELLED;
    }
}
