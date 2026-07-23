package com.github.nankotsu029.landformcraft.core.v2.binding;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.CanonicalConstraintRasterV2;
import com.github.nankotsu029.landformcraft.core.v2.ConstraintCompilationExceptionV2;
import com.github.nankotsu029.landformcraft.core.v2.ConstraintCompilationFailureCodeV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapDecodeLimits;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapSourceSpec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.DecodedNumericRaster;
import com.github.nankotsu029.landformcraft.format.v2.constraint.LoadedConstraintMapSource;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngDecoder;
import com.github.nankotsu029.landformcraft.format.v2.constraint.NumericPngEncoding;
import com.github.nankotsu029.landformcraft.format.v2.constraint.SecureConstraintMapSourceLoader;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * V2-18-06 reusable constraint-map binding: turns one declared constraint-map source into a canonical,
 * XZ-addressable {@link BoundConstraintFieldV2}.
 *
 * <p>The {@code 2026-07-22} macro foundation audit found the export spine compiles map-level HARD input
 * but never reads its bytes: {@code HardPreflightGateV2} (V2-18-03) resolves a {@code LAND_WATER_MASK}
 * reference only far enough to prove existence, digest, and dimensions, and deliberately stops before
 * pixel decode. This class completes the missing chain — secure resolve → digest → decode → canonical
 * field registration — as one permanent, feature-neutral component, so any generation stage can consume
 * a map's normalized field instead of re-implementing the secure pipeline. It reuses the existing
 * {@link SecureConstraintMapSourceLoader} (path, symlink, hard-link, digest, and byte-budget defenses),
 * {@link NumericPngDecoder} (strict grayscale decode, bit-depth / dimension / decode-budget checks), and
 * {@link CanonicalConstraintRasterV2} (normalized XZ registration, categorical legend mapping, no-data
 * handling). It owns no coastal knowledge; the permanent consumer is the {@code V2-18-09} macro
 * foundation stage, which exposes the bound mask to the coastal modifier compositor as its HARD
 * land-water source (the interim {@code ProvisionalCoastalLandWaterSourceV2} adapter was retired
 * when that stage landed).</p>
 *
 * <p>Binding decodes source bytes, so it is heavy CPU + I/O work and must run off the Paper main thread,
 * which the export application services already guarantee. It writes nothing and mutates no intent, so
 * it never changes any terrain field / tile / block semantic checksum.</p>
 */
public final class ConstraintMapFieldBindingV2 {
    private final SecureConstraintMapSourceLoader loader;
    private final NumericPngDecoder decoder;

    public ConstraintMapFieldBindingV2() {
        this(new SecureConstraintMapSourceLoader(), new NumericPngDecoder());
    }

    ConstraintMapFieldBindingV2(SecureConstraintMapSourceLoader loader, NumericPngDecoder decoder) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
    }

    /**
     * Binds the constraint-map source referenced by {@code binding} inside {@code request}. Resolves the
     * source by id, then delegates to {@link #bind(Path, GenerationRequestV2.Bounds,
     * GenerationRequestV2.ConstraintMapSource, TerrainIntentV2.ConstraintMapBinding,
     * ConstraintMapDecodeLimits, CancellationToken)}.
     */
    public BoundConstraintFieldV2 bind(
            Path requestPath,
            GenerationRequestV2 request,
            TerrainIntentV2.ConstraintMapBinding binding,
            ConstraintMapDecodeLimits limits,
            CancellationToken token
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(binding, "binding");
        return bind(requestPath, request.bounds(), resolveSource(request, binding.sourceId()),
                binding, limits, token);
    }

    /**
     * Binds one already-resolved source + binding pair into a canonical field over the request bounds.
     * Every failure surfaces a stable, redaction-safe failure code — {@code UNSAFE_PATH},
     * {@code CHECKSUM_MISMATCH}, {@code DIMENSIONS_MISMATCH}, {@code SAMPLE_TYPE_MISMATCH},
     * {@code DECODE_BUDGET_EXCEEDED} from the loader / decoder and {@code UNKNOWN_LABEL},
     * {@code INVALID_NO_DATA}, {@code INVALID_BINDING} from canonical registration.
     */
    public BoundConstraintFieldV2 bind(
            Path requestPath,
            GenerationRequestV2.Bounds bounds,
            GenerationRequestV2.ConstraintMapSource source,
            TerrainIntentV2.ConstraintMapBinding binding,
            ConstraintMapDecodeLimits limits,
            CancellationToken token
    ) {
        Objects.requireNonNull(requestPath, "requestPath");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(token, "token");
        token.throwIfCancellationRequested();
        if (!source.sourceId().equals(binding.sourceId())) {
            throw new ConstraintCompilationExceptionV2(ConstraintCompilationFailureCodeV2.INVALID_BINDING,
                    "constraint binding references a different source than the resolved map");
        }

        ConstraintMapSourceSpec spec = new ConstraintMapSourceSpec(
                source.sourceId(), source.file(), source.expectedSha256(),
                source.expectedWidth(), source.expectedLength());
        LoadedConstraintMapSource loaded = loader.load(
                requestPath, List.of(spec), limits, token::isCancellationRequested).getFirst();
        DecodedNumericRaster decoded = decoder.decode(
                loaded, spec, numericEncoding(source), limits, token::isCancellationRequested);
        CanonicalConstraintRasterV2 raster = new CanonicalConstraintRasterV2(
                bounds, source, binding, decoded, token);
        return new BoundConstraintFieldV2(
                source, binding, raster, bounds.width(), bounds.length(), loaded.sourceChecksum());
    }

    private static GenerationRequestV2.ConstraintMapSource resolveSource(
            GenerationRequestV2 request,
            String sourceId
    ) {
        return request.constraintMaps().stream()
                .filter(candidate -> candidate.sourceId().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new ConstraintCompilationExceptionV2(
                        ConstraintCompilationFailureCodeV2.INVALID_BINDING,
                        "no declared constraint-map source matches the binding sourceId"));
    }

    private static NumericPngEncoding numericEncoding(GenerationRequestV2.ConstraintMapSource source) {
        NumericPngEncoding.NumericKind kind =
                source.decoderKind() == GenerationRequestV2.DecoderKind.HEIGHT_RASTER
                        ? NumericPngEncoding.NumericKind.HEIGHT
                        : NumericPngEncoding.NumericKind.CATEGORICAL;
        NumericPngEncoding.SampleType sampleType =
                source.encoding().sampleType() == GenerationRequestV2.SampleType.U8
                        ? NumericPngEncoding.SampleType.U8
                        : NumericPngEncoding.SampleType.U16;
        return new NumericPngEncoding(NumericPngEncoding.CURRENT_VERSION, kind, sampleType);
    }
}
