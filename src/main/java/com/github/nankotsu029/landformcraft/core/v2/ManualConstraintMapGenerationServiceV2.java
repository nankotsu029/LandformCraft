package com.github.nankotsu029.landformcraft.core.v2;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapDecodeLimits;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapSourceSpec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.DecodedNumericRaster;
import com.github.nankotsu029.landformcraft.format.v2.constraint.LoadedConstraintMapSource;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngDecoder;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngEncoding;
import com.github.nankotsu029.landformcraft.format.v2.constraint.SecureConstraintMapSourceLoader;
import com.github.nankotsu029.landformcraft.format.v2.field.ConstraintFieldIndexCodecV2;
import com.github.nankotsu029.landformcraft.format.v2.field.LfcGridWriterV1;
import com.github.nankotsu029.landformcraft.model.v2.ConstraintFieldIndexV2;
import com.github.nankotsu029.landformcraft.model.v2.FieldArtifactDescriptorV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;
import com.github.nankotsu029.landformcraft.preview.v2.ConstraintDiagnosticFieldsV2;
import com.github.nankotsu029.landformcraft.preview.v2.ConstraintMapPreviewRendererV2;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * V2-1 manual path: strict request + local numeric maps -> canonical fields and diagnostics.
 * It never calls an AI provider, terrain feature generator, Release publisher, or Paper placement.
 */
public final class ManualConstraintMapGenerationServiceV2 {
    private static final long PREVIEW_BYTES_PER_PIXEL_WORST_CASE = 5L;
    private static final long BUNDLE_METADATA_ALLOWANCE = 1024L * 1024L;

    private final SecureConstraintMapSourceLoader sourceLoader = new SecureConstraintMapSourceLoader();
    private final NumericPngDecoder decoder = new NumericPngDecoder();
    private final LfcGridWriterV1 fieldWriter = new LfcGridWriterV1();
    private final ConstraintFieldIndexCodecV2 indexCodec = new ConstraintFieldIndexCodecV2();
    private final ConstraintMapPreviewRendererV2 previewRenderer = new ConstraintMapPreviewRendererV2();
    private final LandformV2DataCodec dataCodec = new LandformV2DataCodec();

    /**
     * Canonicalizes a manual draft and returns bindings containing the computed artifact IDs.
     * The caller can place those bindings into a frozen TerrainIntent and rerun {@link #generateFrozen}.
     */
    public ManualConstraintMapResultV2 prepareManual(
            Path requestPath,
            GenerationRequestV2 request,
            TerrainIntentV2 draftIntent,
            Path targetDirectory,
            CancellationToken cancellationToken
    ) throws IOException {
        return generate(requestPath, request, draftIntent, targetDirectory, cancellationToken, false);
    }

    /** Generates only when every TerrainIntent artifact ID matches the canonical field checksum. */
    public ManualConstraintMapResultV2 generateFrozen(
            Path requestPath,
            GenerationRequestV2 request,
            TerrainIntentV2 frozenIntent,
            Path targetDirectory,
            CancellationToken cancellationToken
    ) throws IOException {
        return generate(requestPath, request, frozenIntent, targetDirectory, cancellationToken, true);
    }

    private ManualConstraintMapResultV2 generate(
            Path requestPath,
            GenerationRequestV2 request,
            TerrainIntentV2 intent,
            Path targetDirectory,
            CancellationToken cancellationToken,
            boolean requireFrozenArtifactIds
    ) throws IOException {
        Objects.requireNonNull(requestPath, "requestPath");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        if (!request.requestId().equals(intent.intentId())) {
            throw failure(ConstraintCompilationFailureCodeV2.INVALID_BINDING,
                    "requestId and intentId must match");
        }
        validateBindingSources(request, intent);
        cancellationToken.throwIfCancellationRequested();

        ConstraintMapDecodeLimits decodeLimits = decodeLimits(request.constraintMapBudget());
        List<ConstraintMapSourceSpec> specifications = request.constraintMaps().stream()
                .map(ManualConstraintMapGenerationServiceV2::sourceSpec)
                .toList();
        List<LoadedConstraintMapSource> loaded = sourceLoader.load(
                requestPath, specifications, decodeLimits, cancellationToken::isCancellationRequested);
        Map<String, LoadedConstraintMapSource> loadedById = new HashMap<>();
        for (LoadedConstraintMapSource source : loaded) loadedById.put(source.sourceId(), source);

        Map<String, GenerationRequestV2.ConstraintMapSource> requestById = new HashMap<>();
        for (GenerationRequestV2.ConstraintMapSource source : request.constraintMaps()) {
            requestById.put(source.sourceId(), source);
        }
        Map<String, ConstraintMapSourceSpec> specificationsById = new HashMap<>();
        for (ConstraintMapSourceSpec specification : specifications) {
            specificationsById.put(specification.sourceId(), specification);
        }

        List<CanonicalConstraintRasterV2> rasters = new ArrayList<>();
        long sourceBytes = 0L;
        long maximumSingleSourceBytes = 0L;
        long expectedDecodedBytes = 0L;
        long expectedMaximumSingleDecodedBytes = 0L;
        for (LoadedConstraintMapSource source : loaded) {
            sourceBytes = Math.addExact(sourceBytes, source.sourceBytes());
            maximumSingleSourceBytes = Math.max(maximumSingleSourceBytes, source.sourceBytes());
        }
        for (TerrainIntentV2.ConstraintMapBinding binding : intent.mapReferences()) {
            GenerationRequestV2.ConstraintMapSource source = requestById.get(binding.sourceId());
            long cells = Math.multiplyExact((long) source.expectedWidth(), source.expectedLength());
            long bytes = Math.multiplyExact(
                    cells, source.encoding().sampleType() == GenerationRequestV2.SampleType.U8 ? 1L : 2L);
            expectedDecodedBytes = Math.addExact(expectedDecodedBytes, bytes);
            expectedMaximumSingleDecodedBytes = Math.max(expectedMaximumSingleDecodedBytes, bytes);
        }
        if (expectedDecodedBytes > request.constraintMapBudget().maximumDecodedBytes()) {
            throw failure(ConstraintCompilationFailureCodeV2.BUDGET_EXCEEDED,
                    "constraint maps exceed the aggregate decoded-byte budget");
        }
        long peakResidentBytes = estimatePeakResidentBytes(
                request,
                sourceBytes,
                maximumSingleSourceBytes,
                expectedDecodedBytes,
                expectedMaximumSingleDecodedBytes);
        if (peakResidentBytes > request.constraintMapBudget().maximumResidentBytes()) {
            throw failure(ConstraintCompilationFailureCodeV2.BUDGET_EXCEEDED,
                    "constraint map stage exceeds the resident-memory budget");
        }

        long decodedBytes = 0L;
        for (TerrainIntentV2.ConstraintMapBinding binding : intent.mapReferences()) {
            cancellationToken.throwIfCancellationRequested();
            GenerationRequestV2.ConstraintMapSource source = requestById.get(binding.sourceId());
            NumericPngEncoding encoding = numericEncoding(source);
            DecodedNumericRaster decoded = decoder.decode(
                    loadedById.get(binding.sourceId()),
                    specificationsById.get(binding.sourceId()),
                    encoding,
                    decodeLimits,
                    cancellationToken::isCancellationRequested);
            long bytes = Math.multiplyExact(
                    (long) decoded.width() * decoded.length(), decoded.sampleType().bytes());
            decodedBytes = Math.addExact(decodedBytes, bytes);
            rasters.add(new CanonicalConstraintRasterV2(
                    request.bounds(), source, binding, decoded, cancellationToken));
        }
        if (decodedBytes != expectedDecodedBytes) {
            throw failure(ConstraintCompilationFailureCodeV2.BUDGET_EXCEEDED,
                    "decoded constraint-map size differs from admitted dimensions");
        }
        validateHardConstraints(rasters, request.bounds(), cancellationToken);
        validateUniqueRoles(rasters);

        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "targetDirectory must have a parent");
        requireOutputParent(parent);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("manual constraint target already exists: " + target);
        }
        Path staging = parent.resolve(".tmp-constraint-bundle-" + UUID.randomUUID());
        try {
            Files.createDirectory(staging);
            List<FieldArtifactDescriptorV2> fields = new ArrayList<>();
            List<ConstraintFieldIndexV2.AppliedBinding> applied = new ArrayList<>();
            List<TerrainIntentV2.ConstraintMapBinding> canonicalBindings = new ArrayList<>();
            long estimatedSidecarBytes = preflightSidecars(
                    rasters, request.constraintMapBudget(), cancellationToken);
            long estimatedArtifactBytes = estimateBundleArtifactBytes(request, estimatedSidecarBytes);
            if (estimatedArtifactBytes > request.constraintMapBudget().maximumArtifactBytes()) {
                throw failure(ConstraintCompilationFailureCodeV2.BUDGET_EXCEEDED,
                        "constraint output exceeds the artifact-byte budget");
            }

            for (int index = 0; index < rasters.size(); index++) {
                cancellationToken.throwIfCancellationRequested();
                CanonicalConstraintRasterV2 raster = rasters.get(index);
                WrittenBinding written = writeBinding(staging, index, raster, request, cancellationToken);
                fields.addAll(written.fields());
                String artifactId = raster.artifactId(written.desired());
                if (requireFrozenArtifactIds && !artifactId.equals(raster.binding().artifactId())) {
                    throw failure(ConstraintCompilationFailureCodeV2.CHECKSUM_MISMATCH,
                            "TerrainIntent constraint artifact checksum does not match canonical field");
                }
                TerrainIntentV2.ConstraintMapBinding canonical = new TerrainIntentV2.ConstraintMapBinding(
                        raster.binding().id(),
                        raster.binding().sourceId(),
                        raster.binding().role(),
                        artifactId,
                        raster.binding().strength(),
                        raster.binding().sampling(),
                        raster.binding().toleranceBlocks(),
                        raster.binding().weightMillionths());
                canonicalBindings.add(canonical);
                List<String> fieldIds = written.fields().stream()
                        .map(field -> field.definition().fieldId()).toList();
                applied.add(new ConstraintFieldIndexV2.AppliedBinding(
                        canonical.id(),
                        canonical.sourceId(),
                        canonical.role(),
                        canonical.strength(),
                        canonical.sampling(),
                        canonical.toleranceBlocks(),
                        canonical.weightMillionths(),
                        canonical.artifactId(),
                        written.desired().definition().fieldId(),
                        fieldIds,
                        raster.canonicalLabels()));
            }

            String requestChecksum = dataCodec.generationRequestChecksum(request);
            String intentChecksum = dataCodec.terrainIntentChecksum(intent);
            ConstraintFieldIndexV2 fieldIndex = new ConstraintFieldIndexV2(
                    ConstraintFieldIndexV2.VERSION,
                    request.requestId(),
                    requestChecksum,
                    intentChecksum,
                    applied,
                    fields);
            Path stagedIndex = staging.resolve("fields/index.json");
            indexCodec.write(stagedIndex, fieldIndex);
            ConstraintFieldIndexV2 verified = indexCodec.readAndVerify(
                    stagedIndex, staging, requestChecksum, intentChecksum, cancellationToken);
            List<Path> stagedPreviews = previewRenderer.render(
                    staging.resolve("previews"),
                    diagnosticFields(request.bounds(), rasters),
                    cancellationToken);
            if (stagedPreviews.size() != ConstraintMapPreviewRendererV2.FILE_NAMES.size()) {
                throw new IOException("constraint preview renderer returned an incomplete layer set");
            }
            long actualArtifactBytes = directoryBytes(staging);
            if (actualArtifactBytes > request.constraintMapBudget().maximumArtifactBytes()) {
                throw failure(ConstraintCompilationFailureCodeV2.BUDGET_EXCEEDED,
                        "constraint output exceeds the artifact-byte budget after rendering");
            }
            ManualConstraintMapResultV2 result = new ManualConstraintMapResultV2(
                    target,
                    target.resolve("fields/index.json"),
                    verified,
                    canonicalBindings,
                    ConstraintMapPreviewRendererV2.FILE_NAMES.stream()
                            .map(target.resolve("previews")::resolve).toList(),
                    peakResidentBytes,
                    actualArtifactBytes);
            cancellationToken.throwIfCancellationRequested();
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic move is required for constraint bundle publication", exception);
            }
            // The atomic directory move is the commit point. Cancellation observed after this point
            // belongs to the caller's next operation; deleting a committed canonical bundle would
            // create an observable publication race.
            return result;
        } finally {
            deleteTree(staging);
        }
    }

    private long preflightSidecars(
            List<CanonicalConstraintRasterV2> rasters,
            GenerationRequestV2.ConstraintMapBudget budget,
            CancellationToken cancellationToken
    ) {
        long result = 0L;
        for (int index = 0; index < rasters.size(); index++) {
            cancellationToken.throwIfCancellationRequested();
            CanonicalConstraintRasterV2 raster = rasters.get(index);
            String base = String.format(java.util.Locale.ROOT, "constraint.%03d", index);
            var provenance = raster.provenance();
            result = Math.addExact(result, fieldWriter.estimateArtifactBytes(
                    raster.desiredDefinition(base + ".desired"), provenance));
            if (raster.binding().role() != TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP) {
                result = Math.addExact(result, fieldWriter.estimateArtifactBytes(
                        raster.actualDefinition(base + ".actual"), provenance));
                result = Math.addExact(result, fieldWriter.estimateArtifactBytes(
                        raster.residualDefinition(base + ".residual"), provenance));
            }
        }
        if (result > budget.maximumArtifactBytes()) {
            throw failure(ConstraintCompilationFailureCodeV2.BUDGET_EXCEEDED,
                    "constraint sidecars exceed the artifact-byte budget");
        }
        return result;
    }

    private WrittenBinding writeBinding(
            Path root,
            int index,
            CanonicalConstraintRasterV2 raster,
            GenerationRequestV2 request,
            CancellationToken cancellationToken
    ) throws IOException {
        String sequence = String.format(java.util.Locale.ROOT, "%03d", index);
        String base = "constraint." + sequence;
        String role = roleSlug(raster.binding().role());
        LfcGridWriterV1.WriteLimits limits = new LfcGridWriterV1.WriteLimits(
                Math.min(64L * 1024L * 1024L, request.constraintMapBudget().maximumArtifactBytes()),
                Math.min(16L * 1024L * 1024L, request.constraintMapBudget().maximumResidentBytes()));
        FieldArtifactDescriptorV2 desired = fieldWriter.write(
                root,
                "fields/" + sequence + "-desired-" + role + ".lfgrid",
                raster.desiredDefinition(base + ".desired"),
                raster.provenance(),
                raster::desiredRawAt,
                cancellationToken,
                limits);
        if (raster.binding().role() == TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP) {
            return new WrittenBinding(desired, List.of(desired));
        }
        FieldArtifactDescriptorV2 actual = fieldWriter.write(
                root,
                "fields/" + sequence + "-actual-" + role + ".lfgrid",
                raster.actualDefinition(base + ".actual"),
                raster.provenance(),
                raster::actualRawAt,
                cancellationToken,
                limits);
        FieldArtifactDescriptorV2 residual = fieldWriter.write(
                root,
                "fields/" + sequence + "-residual-" + role + ".lfgrid",
                raster.residualDefinition(base + ".residual"),
                raster.provenance(),
                raster::residualRawAt,
                cancellationToken,
                limits);
        return new WrittenBinding(desired, List.of(desired, actual, residual));
    }

    private static ConstraintDiagnosticFieldsV2 diagnosticFields(
            GenerationRequestV2.Bounds bounds,
            List<CanonicalConstraintRasterV2> rasters
    ) {
        Map<TerrainIntentV2.ConstraintMapRole, CanonicalConstraintRasterV2> byRole =
                new EnumMap<>(TerrainIntentV2.ConstraintMapRole.class);
        for (CanonicalConstraintRasterV2 raster : rasters) {
            byRole.putIfAbsent(raster.binding().role(), raster);
        }
        CanonicalConstraintRasterV2 land = byRole.get(TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK);
        CanonicalConstraintRasterV2 height = byRole.get(TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE);
        CanonicalConstraintRasterV2 zones = byRole.get(TerrainIntentV2.ConstraintMapRole.ZONE_LABEL_MAP);
        return new ConstraintDiagnosticFieldsV2(
                bounds.width(),
                bounds.length(),
                Math.toIntExact((long) bounds.minY() * CanonicalConstraintRasterV2.FIXED_SCALE),
                Math.toIntExact((long) bounds.maxY() * CanonicalConstraintRasterV2.FIXED_SCALE),
                land == null ? (x, z) -> ConstraintDiagnosticFieldsV2.NO_DATA : land::desiredDiagnosticAt,
                land == null ? (x, z) -> ConstraintDiagnosticFieldsV2.NO_DATA : land::actualDiagnosticAt,
                land == null ? (x, z) -> ConstraintDiagnosticFieldsV2.NO_DATA : land::residualDiagnosticAt,
                height == null ? (x, z) -> ConstraintDiagnosticFieldsV2.NO_DATA : height::desiredDiagnosticAt,
                height == null ? (x, z) -> ConstraintDiagnosticFieldsV2.NO_DATA : height::actualDiagnosticAt,
                height == null ? (x, z) -> ConstraintDiagnosticFieldsV2.NO_DATA : height::residualDiagnosticAt,
                zones == null ? (x, z) -> ConstraintDiagnosticFieldsV2.NO_DATA : zones::desiredDiagnosticAt,
                (x, z) -> constraintErrorAt(rasters, x, z));
    }

    private static void validateBindingSources(GenerationRequestV2 request, TerrainIntentV2 intent) {
        if (intent.mapReferences().isEmpty()) {
            throw failure(ConstraintCompilationFailureCodeV2.INVALID_BINDING,
                    "manual constraint generation requires at least one map binding");
        }
        Set<String> requestIds = new HashSet<>();
        for (GenerationRequestV2.ConstraintMapSource source : request.constraintMaps()) {
            requestIds.add(source.sourceId());
        }
        Set<String> bound = new HashSet<>();
        for (TerrainIntentV2.ConstraintMapBinding binding : intent.mapReferences()) {
            if (!requestIds.contains(binding.sourceId()) || !bound.add(binding.sourceId())) {
                throw failure(ConstraintCompilationFailureCodeV2.INVALID_BINDING,
                        "constraint binding source is missing or duplicated");
            }
        }
        if (!bound.equals(requestIds)) {
            throw failure(ConstraintCompilationFailureCodeV2.INVALID_BINDING,
                    "every manual constraint source must have exactly one semantic binding");
        }
    }

    private static void validateHardConstraints(
            List<CanonicalConstraintRasterV2> rasters,
            GenerationRequestV2.Bounds bounds,
            CancellationToken cancellationToken
    ) {
        for (CanonicalConstraintRasterV2 raster : rasters) {
            cancellationToken.throwIfCancellationRequested();
            if (raster.hasAnyHardError(cancellationToken)) {
                throw failure(ConstraintCompilationFailureCodeV2.HARD_CONSTRAINT_CONFLICT,
                        "hard constraint cannot be reconciled within tolerance");
            }
        }
        for (int first = 0; first < rasters.size(); first++) {
            CanonicalConstraintRasterV2 left = rasters.get(first);
            if (left.binding().strength() != TerrainIntentV2.Strength.HARD) continue;
            for (int second = first + 1; second < rasters.size(); second++) {
                CanonicalConstraintRasterV2 right = rasters.get(second);
                if (right.binding().strength() != TerrainIntentV2.Strength.HARD
                        || left.binding().role() != right.binding().role()) continue;
                long tolerance = left.binding().role() == TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE
                        ? (long) (left.binding().toleranceBlocks() + right.binding().toleranceBlocks())
                        * CanonicalConstraintRasterV2.FIXED_SCALE : 0L;
                for (int z = 0; z < bounds.length(); z++) {
                    if ((z & 31) == 0) cancellationToken.throwIfCancellationRequested();
                    for (int x = 0; x < bounds.width(); x++) {
                        if (Math.abs((long) left.desiredRawAt(x, z) - right.desiredRawAt(x, z)) > tolerance) {
                            throw failure(ConstraintCompilationFailureCodeV2.HARD_CONSTRAINT_CONFLICT,
                                    "hard constraint maps conflict");
                        }
                    }
                }
            }
        }
    }

    private static void validateUniqueRoles(List<CanonicalConstraintRasterV2> rasters) {
        Set<TerrainIntentV2.ConstraintMapRole> roles = new HashSet<>();
        for (CanonicalConstraintRasterV2 raster : rasters) {
            if (!roles.add(raster.binding().role())) {
                throw failure(ConstraintCompilationFailureCodeV2.INVALID_BINDING,
                        "V2-1 accepts exactly one binding for each constraint-map role");
            }
        }
    }

    private static long estimatePeakResidentBytes(
            GenerationRequestV2 request,
            long sourceBytes,
            long maximumSingleSourceBytes,
            long decodedBytes,
            long maximumSingleDecodedBytes
    ) {
        long decodePeak = Math.addExact(
                Math.addExact(sourceBytes, maximumSingleSourceBytes),
                Math.addExact(
                        Math.addExact(decodedBytes, maximumSingleDecodedBytes),
                        1024L * 1024L));
        long previewImage = Math.multiplyExact(
                (long) request.bounds().width() * request.bounds().length(), Integer.BYTES);
        return Math.max(decodePeak, Math.addExact(Math.addExact(sourceBytes, decodedBytes), previewImage));
    }

    private static long estimateBundleArtifactBytes(
            GenerationRequestV2 request,
            long sidecarBytes
    ) {
        long cells = Math.multiplyExact((long) request.bounds().width(), request.bounds().length());
        long previews = Math.multiplyExact(
                Math.multiplyExact(cells, PREVIEW_BYTES_PER_PIXEL_WORST_CASE),
                ConstraintMapPreviewRendererV2.FILE_NAMES.size());
        return Math.addExact(Math.addExact(sidecarBytes, previews), BUNDLE_METADATA_ALLOWANCE);
    }

    private static ConstraintMapDecodeLimits decodeLimits(GenerationRequestV2.ConstraintMapBudget budget) {
        return new ConstraintMapDecodeLimits(
                budget.maximumMapCount(),
                Math.min(
                        budget.maximumTotalSourceBytes(),
                        ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_SOURCE_BYTES),
                budget.maximumTotalSourceBytes(),
                ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_DIMENSION,
                ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_ASPECT_RATIO,
                Math.min(budget.maximumPixels(), ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_PIXELS),
                Math.min(
                        budget.maximumDecodedBytes(),
                        ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_DECODED_SAMPLE_BYTES),
                Math.min(
                        budget.maximumResidentBytes(),
                        ConstraintMapDecodeLimits.TRUSTED_MAXIMUM_WORKING_BYTES));
    }

    private static ConstraintMapSourceSpec sourceSpec(GenerationRequestV2.ConstraintMapSource source) {
        return new ConstraintMapSourceSpec(
                source.sourceId(),
                source.file(),
                source.expectedSha256(),
                source.expectedWidth(),
                source.expectedLength());
    }

    private static NumericPngEncoding numericEncoding(GenerationRequestV2.ConstraintMapSource source) {
        NumericPngEncoding.NumericKind kind = source.decoderKind() == GenerationRequestV2.DecoderKind.HEIGHT_RASTER
                ? NumericPngEncoding.NumericKind.HEIGHT : NumericPngEncoding.NumericKind.CATEGORICAL;
        NumericPngEncoding.SampleType sampleType = source.encoding().sampleType() == GenerationRequestV2.SampleType.U8
                ? NumericPngEncoding.SampleType.U8 : NumericPngEncoding.SampleType.U16;
        return new NumericPngEncoding(NumericPngEncoding.CURRENT_VERSION, kind, sampleType);
    }

    private static String roleSlug(TerrainIntentV2.ConstraintMapRole role) {
        return switch (role) {
            case LAND_WATER_MASK -> "land-water";
            case HEIGHT_GUIDE -> "height";
            case ZONE_LABEL_MAP -> "zones";
        };
    }

    private static void requireOutputParent(Path parent) throws IOException {
        if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("constraint output parent is not a safe directory");
            }
        } else {
            Files.createDirectories(parent);
            if (Files.isSymbolicLink(parent)) {
                throw new IOException("constraint output parent must not be a symbolic link");
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static int constraintErrorAt(List<CanonicalConstraintRasterV2> rasters, int x, int z) {
        for (CanonicalConstraintRasterV2 raster : rasters) {
            if (raster.constraintErrorAt(x, z) != 0) return 1;
        }
        return 0;
    }

    private static long directoryBytes(Path root) throws IOException {
        long total = 0L;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                total = Math.addExact(total, Files.size(path));
            }
        }
        return total;
    }

    private static ConstraintCompilationExceptionV2 failure(
            ConstraintCompilationFailureCodeV2 code,
            String message
    ) {
        return new ConstraintCompilationExceptionV2(code, message);
    }

    private record WrittenBinding(
            FieldArtifactDescriptorV2 desired,
            List<FieldArtifactDescriptorV2> fields
    ) {
        private WrittenBinding {
            Objects.requireNonNull(desired, "desired");
            fields = List.copyOf(fields);
        }
    }
}
