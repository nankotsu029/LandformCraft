package com.github.nankotsu029.landformcraft.core.v2.export;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.release.EnvironmentReleaseSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.release.HydrologyReleaseSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.release.SparseVolumeReleaseSourceV2;
import com.github.nankotsu029.landformcraft.format.v2.release.SurfaceReleaseSourceV2;
import com.github.nankotsu029.landformcraft.generator.v2.TerrainQuery;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.model.v2.WorldBlueprintV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Compile-time-only production export handler registered in the V2-15-05 dispatch spine.
 * Implementations own one complete generator → validator → preview → export chain and must not be
 * discovered from external classes, scripts, or service loaders.
 */
public interface ProductionExportPipelineV2 {
    PipelineDescriptor descriptor();

    /**
     * {@code requestSource} is the path of the sealed generation-request file the caller read;
     * the V2-18-09 macro foundation stage resolves constraint-map bytes relative to it through the
     * secure loader, so the pipeline never guesses an input location.
     */
    GeneratedSurface generate(
            GenerationRequestV2 request,
            Path requestSource,
            TerrainIntentV2 intent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException;

    /**
     * Shared {@code hydrology-plan} artifact generation (V2-15-06). Surface-only pipelines reject
     * this path; hydrology pipelines reject {@link #generate}.
     */
    default GeneratedHydrology generateHydrology(
            GenerationRequestV2 request,
            Path requestSource,
            TerrainIntentV2 intent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        throw new IOException("production pipeline does not produce hydrology-plan artifacts: "
                + descriptor().pipelineId());
    }

    /**
     * Shared {@code environment-fields} artifact generation (V2-15-07). Surface／hydrology pipelines
     * reject this path; environment pipelines reject {@link #generate} and {@link #generateHydrology}.
     */
    default GeneratedEnvironment generateEnvironment(
            GenerationRequestV2 request,
            Path requestSource,
            TerrainIntentV2 intent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        throw new IOException("production pipeline does not produce environment-fields artifacts: "
                + descriptor().pipelineId());
    }

    /**
     * Shared {@code sparse-volume} artifact generation (V2-15-08). Earlier capability pipelines
     * reject this path; the sparse-volume pipeline rejects all lower-prefix generation methods.
     */
    default GeneratedSparseVolume generateSparseVolume(
            GenerationRequestV2 request,
            Path requestSource,
            TerrainIntentV2 intent,
            SurfaceBaselineV2 baseline,
            Path workRoot,
            ExportBudgetV2 budget,
            CancellationToken token
    ) throws IOException {
        throw new IOException("production pipeline does not produce sparse-volume artifacts: "
                + descriptor().pipelineId());
    }

    record GeneratedSurface(
            SurfaceReleaseSourceV2 source,
            WorldBlueprintV2 blueprint,
            Optional<IntentContributionCoverageV2> intentContributionCoverage,
            Optional<MaskFeatureReconcileV2> maskFeatureReconcile,
            List<ExportWarningV2> warnings
    ) {
        public GeneratedSurface {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(blueprint, "blueprint");
            Objects.requireNonNull(intentContributionCoverage, "intentContributionCoverage");
            Objects.requireNonNull(maskFeatureReconcile, "maskFeatureReconcile");
            warnings = List.copyOf(warnings);
        }
    }

    record GeneratedHydrology(
            HydrologyReleaseSourceV2 source,
            WorldBlueprintV2 blueprint,
            TerrainQuery baseTerrain
    ) {
        public GeneratedHydrology {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(blueprint, "blueprint");
            Objects.requireNonNull(baseTerrain, "baseTerrain");
        }
    }

    record GeneratedEnvironment(
            EnvironmentReleaseSourceV2 source,
            WorldBlueprintV2 blueprint,
            TerrainQuery baseTerrain
    ) {
        public GeneratedEnvironment {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(blueprint, "blueprint");
            Objects.requireNonNull(baseTerrain, "baseTerrain");
        }
    }

    record GeneratedSparseVolume(
            SparseVolumeReleaseSourceV2 source,
            WorldBlueprintV2 blueprint
    ) {
        public GeneratedSparseVolume {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(blueprint, "blueprint");
        }
    }

    record PipelineDescriptor(
            String pipelineId,
            HandlerSet handlers,
            List<TerrainIntentV2.FeatureKind> executableKinds,
            List<TerrainIntentV2.FeatureKind> contractOnlyKinds,
            List<String> requiredCapabilities
    ) {
        public static final int MAXIMUM_FEATURE_KINDS = 128;
        public static final int MAXIMUM_CAPABILITIES = 16;

        public PipelineDescriptor {
            pipelineId = stableId(pipelineId, "pipelineId");
            Objects.requireNonNull(handlers, "handlers");
            executableKinds = stableKinds(executableKinds, "executableKinds");
            contractOnlyKinds = stableKinds(contractOnlyKinds, "contractOnlyKinds");
            requiredCapabilities = stableIds(requiredCapabilities, "requiredCapabilities",
                    MAXIMUM_CAPABILITIES);
            if (executableKinds.isEmpty()) {
                throw new IllegalArgumentException("production pipeline must execute at least one feature kind");
            }
            if (executableKinds.stream().anyMatch(contractOnlyKinds::contains)) {
                throw new IllegalArgumentException("feature kind cannot be executable and contract-only");
            }
        }

        private static List<TerrainIntentV2.FeatureKind> stableKinds(
                List<TerrainIntentV2.FeatureKind> values,
                String field
        ) {
            Objects.requireNonNull(values, field);
            if (values.size() > MAXIMUM_FEATURE_KINDS) {
                throw new IllegalArgumentException(field + " exceeds dispatch budget");
            }
            List<TerrainIntentV2.FeatureKind> stable = values.stream()
                    .map(value -> Objects.requireNonNull(value, field + " entry"))
                    .sorted(Comparator.comparing(Enum::name))
                    .toList();
            if (stable.stream().distinct().count() != stable.size()) {
                throw new IllegalArgumentException(field + " contains duplicate feature kind");
            }
            return stable;
        }
    }

    record HandlerSet(
            String generatorHandlerId,
            String validatorHandlerId,
            String previewHandlerId,
            String exportHandlerId
    ) {
        public HandlerSet {
            generatorHandlerId = stableId(generatorHandlerId, "generatorHandlerId");
            validatorHandlerId = stableId(validatorHandlerId, "validatorHandlerId");
            previewHandlerId = stableId(previewHandlerId, "previewHandlerId");
            exportHandlerId = stableId(exportHandlerId, "exportHandlerId");
        }
    }

    Pattern STABLE_ID = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,95}$");

    private static String stableId(String value, String field) {
        if (value == null || !STABLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a stable lowercase identifier");
        }
        return value;
    }

    private static List<String> stableIds(List<String> values, String field, int maximum) {
        Objects.requireNonNull(values, field);
        if (values.isEmpty() || values.size() > maximum) {
            throw new IllegalArgumentException(field + " outside dispatch budget");
        }
        List<String> stable = values.stream()
                .map(value -> stableId(value, field + " entry"))
                .sorted()
                .toList();
        if (stable.stream().distinct().count() != stable.size()) {
            throw new IllegalArgumentException(field + " contains duplicate identifier");
        }
        return stable;
    }
}
