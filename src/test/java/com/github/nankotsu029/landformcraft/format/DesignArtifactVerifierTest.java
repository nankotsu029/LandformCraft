package com.github.nankotsu029.landformcraft.format;

import com.github.nankotsu029.landformcraft.ai.spi.ImportedJsonTerrainDesignProvider;
import com.github.nankotsu029.landformcraft.core.FileGenerationJobRepository;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.TerrainDesignApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DesignArtifactVerifierTest {
    @Test
    void readsBackThenRejectsTamperedAndMissingArtifacts(@TempDir Path directory) throws Exception {
        Path design;
        try (GenerationExecutors executors = GenerationExecutors.create(4, 1, 8)) {
            var provider = new ImportedJsonTerrainDesignProvider(
                    executors, Path.of("examples/rocky-coast/terrain-intent.json")
            );
            var jobs = new FileGenerationJobRepository(directory.resolve("jobs"), executors);
            design = new TerrainDesignApplicationService(executors, provider, jobs)
                    .start(Path.of("examples/rocky-coast/request.yml"), directory.resolve("designs"))
                    .completion().join().directory();
        }
        DesignArtifactVerifier verifier = new DesignArtifactVerifier();
        assertEquals(3, verifier.verify(design).verifiedFiles());
        assertEquals(0, verifier.verify(design).imageEvidence().images().size());

        Path evidence = design.resolve("image-evidence.json");
        String originalEvidence = Files.readString(evidence);
        Path checksums = design.resolve("checksums.sha256");
        String originalChecksums = Files.readString(checksums);
        Files.writeString(evidence, originalEvidence.replace("rocky-coast-001", "tampered-001"));
        assertThrows(IOException.class, () -> verifier.verify(design));

        Files.writeString(evidence, originalEvidence);
        Files.writeString(evidence, originalEvidence.replace("imported-json", "fixture"));
        Files.writeString(checksums, originalChecksums.replaceFirst(
                "[0-9a-f]{64}  image-evidence.json",
                Sha256.file(evidence) + "  image-evidence.json"
        ));
        assertThrows(IOException.class, () -> verifier.verify(design));

        Files.writeString(evidence, originalEvidence);
        Files.writeString(checksums, originalChecksums);
        Files.delete(evidence);
        assertThrows(IOException.class, () -> verifier.verify(design));
        Files.writeString(evidence, originalEvidence);

        Path intent = design.resolve("terrain-intent.json");
        String original = Files.readString(intent);
        Files.writeString(intent, original.replace("岩礁", "改変済み"));
        assertThrows(IOException.class, () -> verifier.verify(design));

        Files.writeString(intent, original);
        Files.delete(design.resolve("audit.json"));
        assertThrows(IOException.class, () -> verifier.verify(design));
    }
}
