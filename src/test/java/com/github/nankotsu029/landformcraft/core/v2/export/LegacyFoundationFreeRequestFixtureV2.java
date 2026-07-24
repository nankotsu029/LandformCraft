package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Builds the V2-18-10 negative fixture: an otherwise valid honored request with its explicit macro
 * foundation input removed (no {@code foundationBaseLevels}), i.e. exactly the legacy
 * surface-baseline request ADR 0038 D8-2 says the owner coverage gate must now reject.
 *
 * <p>The HARD {@code LAND_WATER_MASK} declaration and its PNG are kept and copied next to the
 * rewritten request, so the run still clears the V2-18-03 preflight gate and fails on the coverage
 * gate alone rather than on an unrelated unresolved input.</p>
 */
final class LegacyFoundationFreeRequestFixtureV2 {
    private LegacyFoundationFreeRequestFixtureV2() {
    }

    /** Writes the foundation-free copy of {@code honoredRequest} into {@code workspace}. */
    static Path write(Path honoredRequest, Path workspace) throws IOException {
        Files.createDirectories(workspace);
        LandformV2DataCodec codec = new LandformV2DataCodec();
        GenerationRequestV2 honored = codec.readGenerationRequest(honoredRequest);
        if (honored.foundationBaseLevels().isEmpty()) {
            throw new IllegalArgumentException("fixture already declares no foundation base levels");
        }
        for (GenerationRequestV2.ConstraintMapSource map : honored.constraintMaps()) {
            Path source = honoredRequest.getParent().resolve(map.file());
            Path target = workspace.resolve(map.file());
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
        }
        Path target = workspace.resolve(honoredRequest.getFileName().toString());
        codec.writeGenerationRequest(target, new GenerationRequestV2(
                honored.requestVersion(), honored.requestId(), honored.bounds(), honored.prompt(),
                honored.referenceImages(), honored.constraintMaps(), honored.generation(),
                honored.constraintMapBudget(), Optional.empty(), Optional.empty(),
                Optional.empty()));
        return target;
    }
}
