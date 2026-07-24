package com.github.nankotsu029.landformcraft.core.v2.command;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.design.DesignDispatchRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.design.TerrainDesignApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.ExportBudgetV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportRequestV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2ExportResultV2;
import com.github.nankotsu029.landformcraft.core.v2.export.Release2HydrologyExportApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.export.SurfaceBaselineV2;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.release.ReleaseCoreVerifierV2;
import com.github.nankotsu029.landformcraft.format.v2.release.VerifiedReleaseViewV2;
import com.github.nankotsu029.landformcraft.generator.v2.coast.HardLandWaterSourceV2;
import com.github.nankotsu029.landformcraft.model.v2.CoastalPreviewIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignAuditV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignCapabilityV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignPathKindV2;
import com.github.nankotsu029.landformcraft.model.v2.design.DesignSupportLintV2;
import com.github.nankotsu029.landformcraft.preview.v2.CoastalPreviewIndexCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.design.DesignArtifactsV2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Surface-independent backend for the offline v2 verbs (V2-12-03).
 *
 * <p>The CLI and the Paper adapter share this service so {@code lfc v2 export} and
 * {@code /lfc v2 export} run the exact same production path. Callers pass already-resolved absolute
 * paths; sandboxing untrusted operator input is the surface adapter's responsibility.</p>
 */
public final class V2WorkflowServiceV2 {
    private static final CancellationToken NEVER = () -> false;

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final TerrainDesignApplicationServiceV2 designService;
    private final Release2ExportApplicationServiceV2 exportService;
    private final Release2HydrologyExportApplicationServiceV2 hydrologyExportService;

    public V2WorkflowServiceV2(
            GenerationExecutors executors,
            TerrainDesignApplicationServiceV2.ProviderFactory providerFactory
    ) {
        Objects.requireNonNull(executors, "executors");
        this.designService = new TerrainDesignApplicationServiceV2(executors, providerFactory);
        this.exportService = new Release2ExportApplicationServiceV2(executors);
        this.hydrologyExportService = new Release2HydrologyExportApplicationServiceV2(executors);
    }

    /** Strict read of a v2 generation request. Returns operator-facing facts only, never raw paths. */
    public Map<String, Object> inspectRequest(Path request) throws IOException {
        Objects.requireNonNull(request, "request");
        GenerationRequestV2 value = codec.readGenerationRequest(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", value.requestId());
        result.put("requestVersion", value.requestVersion());
        result.put("width", value.bounds().width());
        result.put("length", value.bounds().length());
        result.put("minY", value.bounds().minY());
        result.put("maxY", value.bounds().maxY());
        result.put("waterLevel", value.bounds().waterLevel());
        result.put("tileSize", value.generation().tileSize());
        result.put("globalSeed", value.generation().globalSeed());
        result.put("constraintMaps", value.constraintMaps().size());
        result.put("referenceImages", value.referenceImages().size());
        result.put("checksum", codec.generationRequestChecksum(value));
        return java.util.Collections.unmodifiableMap(result);
    }

    /**
     * Dispatches one design run and publishes the sealed design package.
     *
     * <p>Returns the pending stage rather than blocking: the design service already owns an
     * executor task, so joining it from inside another executor task would hold an I/O permit while
     * waiting for a second one.</p>
     */
    public java.util.concurrent.CompletableFuture<DesignArtifactsV2> design(
            String pathKind,
            Path request,
            String modelOrIntentPath,
            Path designsRoot
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(designsRoot, "designsRoot");
        DesignPathKindV2 kind = designPathKind(pathKind);
        return designService.design(new DesignDispatchRequestV2(
                        2,
                        kind,
                        EnumSet.of(DesignCapabilityV2.TERRAIN_INTENT_V2_STRUCTURED),
                        request,
                        designsRoot,
                        modelOrIntentPath,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(NEVER)));
    }

    /**
     * Runs the production Release 2 export path (V2-12-02). {@code createZip=false} is the
     * {@code v2 generate} form, which publishes the strict Release directory without a ZIP.
     */
    public Release2ExportResultV2 export(
            Path request,
            Path terrainIntent,
            Path workRoot,
            Path exportsRoot,
            String releaseId,
            SurfaceBaselineV2 baseline,
            boolean createZip
    ) throws IOException {
        return exportService.exportNow(new Release2ExportRequestV2(
                request, terrainIntent, workRoot, exportsRoot, releaseId, baseline, createZip,
                ExportBudgetV2.defaults(), Optional.of(NEVER)));
    }

    /**
     * V2-15-10 / ADR 0039 Candidate A: runs the {@code hydrology-plan} production export path
     * ({@link Release2HydrologyExportApplicationServiceV2}) instead of the plain {@code surface-2_5d}
     * path, so an intent declaring {@code RIVER} / {@code MEANDERING_RIVER} alongside the coastal
     * contributors can select the {@code OFFLINE_PRODUCTION} route. Never promotes a Paper
     * {@code paper_apply} capability.
     */
    public Release2ExportResultV2 exportHydrology(
            Path request,
            Path terrainIntent,
            Path workRoot,
            Path exportsRoot,
            String releaseId,
            SurfaceBaselineV2 baseline
    ) throws IOException {
        return hydrologyExportService.exportNow(new Release2ExportRequestV2(
                request, terrainIntent, workRoot, exportsRoot, releaseId, baseline, true,
                ExportBudgetV2.defaults(), Optional.of(NEVER)));
    }

    /** Strictly verifies a published Release and lists its sealed diagnostic previews. */
    public Map<String, Object> inspectPreviews(Path release) throws IOException {
        Objects.requireNonNull(release, "release");
        try (VerifiedReleaseViewV2 view = new ReleaseCoreVerifierV2().openVerified(release, NEVER)) {
            CoastalPreviewIndexV2 previews = new CoastalPreviewIndexCodecV2().readAndVerify(
                    view.root().resolve("previews/index.json"), view.root().resolve("previews"), NEVER);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("releaseId", view.verification().manifest().releaseId());
            result.put("manifestChecksum", view.verification().manifest().canonicalChecksum());
            result.put("requiredCapabilities", view.verification().manifest().requiredCapabilities());
            result.put("previewIndexChecksum", previews.canonicalChecksum());
            result.put("previewLayers", previews.layers().stream()
                    .map(layer -> layer.layerId().name())
                    .toList());
            return java.util.Collections.unmodifiableMap(result);
        }
    }

    /** Parses the explicit {@code <land|water> <land-surface-y> <water-bed-y>} baseline triple. */
    public static SurfaceBaselineV2 baseline(String classification, String landSurfaceY, String waterBedY) {
        HardLandWaterSourceV2.Classification kind = switch (
                Objects.requireNonNull(classification, "classification").toLowerCase(Locale.ROOT)) {
            case "land" -> HardLandWaterSourceV2.Classification.LAND;
            case "water" -> HardLandWaterSourceV2.Classification.WATER;
            default -> throw new IllegalArgumentException(
                    "surface baseline must be 'land' or 'water', not '" + classification + "'");
        };
        return new SurfaceBaselineV2(kind, integer(landSurfaceY, "land-surface-y"),
                integer(waterBedY, "water-bed-y"));
    }

    /** Machine-readable summary of one export result. Contains no absolute source paths. */
    public static Map<String, Object> summarize(Release2ExportResultV2 result) {
        Objects.requireNonNull(result, "result");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("releaseId", result.releaseId());
        summary.put("releaseDirectory", result.releaseDirectory().getFileName().toString());
        summary.put("zip", result.zip().map(path -> path.getFileName().toString()).orElse(""));
        summary.put("blueprintChecksum", result.blueprintChecksum());
        summary.put("manifestChecksum", result.manifestChecksum());
        summary.put("requiredCapabilities", result.requiredCapabilities());
        summary.put("tiles", result.tileIds().size());
        summary.put("placementEligible", result.eligibility().eligible());
        summary.put("verifiedFiles", result.eligibility().verifiedFiles());
        // V2-18-02 report-only diagnostic: never written to the sealed blueprint or Release
        // manifest, so its presence here has no effect on any checksum.
        result.intentContributionCoverage().ifPresent(
                coverage -> summary.put("intentContributionCoverage", coverage.toSummaryMap()));
        // V2-19-14 report-only pre-pass result (ADR 0043 D5): the sealed intent carries the reconciled
        // geometry, so this is where the operator sees which rigid offset was applied to their
        // declaration. Nothing here is written into the Release.
        result.maskFeatureReconcile().ifPresent(
                reconcile -> summary.put("maskFeatureReconcile", reconcile.toSummaryMap()));
        // V2-18-09 NON_GATING warnings (ADR 0038 D8-1), e.g. the deprecated surface-baseline
        // argument being ignored on an explicit-foundation request. CLI summary only, never sealed.
        if (!result.warnings().isEmpty()) {
            summary.put("warnings", result.warnings().stream()
                    .map(warning -> Map.of("ruleId", warning.ruleId(), "message", warning.message()))
                    .toList());
        }
        return java.util.Collections.unmodifiableMap(summary);
    }

    /**
     * V2-19-08 report-only design lint summary for the CLI and Paper design surfaces. Empty for an
     * audit that carries no lint (a v1 migration bundle). Nothing here gates: {@code dispatchDryRun}
     * says what would happen at export, and the design package is already published either way.
     */
    public static Map<String, Object> summarizeSupportLint(DesignAuditV2 audit) {
        Objects.requireNonNull(audit, "audit");
        Optional<DesignSupportLintV2> lint = audit.supportLintOrEmpty();
        if (lint.isEmpty()) {
            return Map.of();
        }
        DesignSupportLintV2 report = lint.get();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("supportLintContract", report.surface().contractVersion());
        summary.put("dispatchDryRun", report.dispatchDryRun().name());
        summary.put("selectablePipelines", report.selectablePipelineIds());
        summary.put("reachableKinds", report.surface().reachableKinds());
        summary.put("declaredKinds", report.declaredKinds());
        summary.put("supportLintFindings", report.findings().stream()
                .map(finding -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("ruleId", finding.ruleId());
                    entry.put("gateClass", finding.gateClass().name());
                    entry.put("featureKinds", finding.featureKinds());
                    entry.put("detail", finding.detail());
                    return entry;
                })
                .toList());
        return java.util.Collections.unmodifiableMap(summary);
    }

    /** Design path kinds the v2 command surface exposes. */
    public static List<String> designPathKinds() {
        return List.of("import", "fixture", "openai", "anthropic");
    }

    private static DesignPathKindV2 designPathKind(String value) {
        return switch (Objects.requireNonNull(value, "pathKind").toLowerCase(Locale.ROOT)) {
            case "import" -> DesignPathKindV2.IMPORT;
            case "fixture" -> DesignPathKindV2.FIXTURE;
            case "openai" -> DesignPathKindV2.OPENAI;
            case "anthropic" -> DesignPathKindV2.ANTHROPIC;
            default -> throw new IllegalArgumentException(
                    "design path must be one of " + String.join(", ", designPathKinds()));
        };
    }

    private static int integer(String value, String field) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be an integer", exception);
        }
    }
}
