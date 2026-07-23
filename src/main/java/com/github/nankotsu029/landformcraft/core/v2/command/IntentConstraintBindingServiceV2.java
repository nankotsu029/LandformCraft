package com.github.nankotsu029.landformcraft.core.v2.command;

import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapDecodeLimits;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapInputException;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapPngHeaderV2;
import com.github.nankotsu029.landformcraft.format.v2.constraint.ConstraintMapSourceSpec;
import com.github.nankotsu029.landformcraft.format.v2.constraint.LoadedConstraintMapSource;
import com.github.nankotsu029.landformcraft.format.v2.constraint.SecureConstraintMapSourceLoader;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Authoring of the {@code mapReferences} bindings that connect a request's constraint sources to an
 * intent (V2-19-04).
 *
 * <p>Declaring a source was reachable from the command surface; turning it into an intent binding was
 * not. The binding carries a canonical {@code artifactId} whose digest half must equal the declared
 * input digest of its source — the rule
 * {@code SurfaceReleaseCapabilityVerifierV2.verifyIntentBindings} enforces on every published
 * surface Release since V2-18-07. Hand-writing it means copying a SHA-256 into a JSON string by
 * hand, and getting it wrong only surfaces at export time. This service computes it from the
 * request instead, so the rule is held by code rather than by the author (and never by a provider,
 * which cannot know the digest and must not be asked to invent one).</p>
 *
 * <p>{@link #verify} is the read-only counterpart: it re-derives what each binding should say and
 * re-resolves the map bytes, so an operator can confirm a hand-edited or provider-produced intent
 * before an export does. Nothing here decodes pixels, writes an artifact, or promotes a SOFT
 * declaration to HARD.</p>
 */
public final class IntentConstraintBindingServiceV2 {
    private final SecureConstraintMapSourceLoader loader = new SecureConstraintMapSourceLoader();
    private final ConstraintMapDecodeLimits limits = ConstraintMapDecodeLimits.defaults();

    /** Canonical artifact-id prefix per role, mirroring {@link TerrainIntentV2.ConstraintMapBinding}. */
    public static String artifactPrefix(TerrainIntentV2.ConstraintMapRole role) {
        return switch (role) {
            case LAND_WATER_MASK -> "constraint:land-water:sha256-";
            case HEIGHT_GUIDE -> "constraint:height-guide:sha256-";
            case ZONE_LABEL_MAP -> "constraint:zone-label-map:sha256-";
        };
    }

    /**
     * Adds or replaces the binding for {@code sourceSlug} and returns the updated intent.
     *
     * <p>The binding id is the source slug: one declared source has at most one binding, so the two
     * identifiers cannot drift apart. Strength, sampling, tolerance and weight stay the author's
     * explicit choice — {@link TerrainIntentV2.ConstraintMapBinding} rejects the combinations that
     * are not meaningful (a categorical map interpolated, a HARD mask with tolerance, a weighted
     * HARD binding).</p>
     */
    public TerrainIntentV2 bind(
            GenerationRequestV2 request,
            TerrainIntentV2 intent,
            String sourceSlug,
            TerrainIntentV2.ConstraintMapRole role,
            TerrainIntentV2.Strength strength,
            TerrainIntentV2.Sampling sampling,
            int toleranceBlocks,
            int weightMillionths
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(sourceSlug, "sourceSlug");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(sampling, "sampling");
        if (!request.requestId().equals(intent.intentId())) {
            throw new IllegalArgumentException(
                    "request and intent identify different subjects: " + request.requestId()
                            + " vs " + intent.intentId());
        }
        String sourceId = "constraint-source:" + sourceSlug;
        GenerationRequestV2.ConstraintMapSource source = declaredSource(request, sourceId);
        requireRoleMatchesEncoding(role, source);

        TerrainIntentV2.ConstraintMapBinding binding = new TerrainIntentV2.ConstraintMapBinding(
                sourceSlug,
                sourceId,
                role,
                artifactPrefix(role) + source.expectedSha256(),
                strength,
                sampling,
                toleranceBlocks,
                weightMillionths);

        List<TerrainIntentV2.ConstraintMapBinding> bindings = new ArrayList<>();
        for (TerrainIntentV2.ConstraintMapBinding existing : intent.mapReferences()) {
            if (!existing.id().equals(binding.id()) && !existing.sourceId().equals(sourceId)) {
                bindings.add(existing);
            }
        }
        bindings.add(binding);
        return withMapReferences(intent, bindings);
    }

    /**
     * Re-derives and re-resolves every binding of {@code intent} against {@code request}. The
     * returned rows are ordered by binding id; a row with no problems is fully consistent with both
     * the declaration and the bytes on disk.
     */
    public Report verify(
            Path requestPath,
            GenerationRequestV2 request,
            TerrainIntentV2 intent,
            CancellationToken cancellationToken
    ) {
        Objects.requireNonNull(requestPath, "requestPath");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Map<String, GenerationRequestV2.ConstraintMapSource> declared = new HashMap<>();
        for (GenerationRequestV2.ConstraintMapSource source : request.constraintMaps()) {
            declared.put(source.sourceId(), source);
        }

        List<BindingStatus> statuses = new ArrayList<>();
        List<String> boundSourceIds = new ArrayList<>();
        for (TerrainIntentV2.ConstraintMapBinding binding : intent.mapReferences()) {
            cancellationToken.throwIfCancellationRequested();
            boundSourceIds.add(binding.sourceId());
            statuses.add(status(requestPath, declared.get(binding.sourceId()), binding, cancellationToken));
        }
        List<String> unboundSources = declared.keySet().stream()
                .filter(sourceId -> !boundSourceIds.contains(sourceId))
                .sorted()
                .toList();
        return new Report(List.copyOf(statuses), unboundSources);
    }

    private BindingStatus status(
            Path requestPath,
            GenerationRequestV2.ConstraintMapSource source,
            TerrainIntentV2.ConstraintMapBinding binding,
            CancellationToken cancellationToken
    ) {
        List<String> problems = new ArrayList<>();
        if (source == null) {
            problems.add("the request declares no constraint source with id " + binding.sourceId());
            return new BindingStatus(binding, null, List.copyOf(problems));
        }
        String expectedArtifactId = artifactPrefix(binding.role()) + source.expectedSha256();
        if (!binding.artifactId().equals(expectedArtifactId)) {
            problems.add("artifactId does not reference the declared input digest of " + binding.sourceId());
        }
        try {
            requireRoleMatchesEncoding(binding.role(), source);
        } catch (IllegalArgumentException exception) {
            problems.add(exception.getMessage());
        }
        problems.addAll(resolveProblems(requestPath, source, cancellationToken));
        return new BindingStatus(binding, source, List.copyOf(problems));
    }

    /** Existence, digest and IHDR dimensions, through the same envelope the HARD preflight gate uses. */
    private List<String> resolveProblems(
            Path requestPath,
            GenerationRequestV2.ConstraintMapSource source,
            CancellationToken cancellationToken
    ) {
        ConstraintMapSourceSpec spec;
        try {
            spec = new ConstraintMapSourceSpec(
                    source.sourceId(), source.file(), source.expectedSha256(),
                    source.expectedWidth(), source.expectedLength());
        } catch (ConstraintMapInputException exception) {
            return List.of(exception.code() + ": " + exception.getMessage());
        }
        LoadedConstraintMapSource loaded;
        try {
            loaded = loader.load(requestPath, List.of(spec), limits, cancellationToken::isCancellationRequested)
                    .getFirst();
        } catch (ConstraintMapInputException exception) {
            return List.of(exception.code() + ": " + exception.getMessage());
        }
        String mismatch = ConstraintMapPngHeaderV2.dimensionMismatch(
                loaded.contentCopy(), source.expectedWidth(), source.expectedLength());
        return mismatch == null ? List.of() : List.of(mismatch);
    }

    private static GenerationRequestV2.ConstraintMapSource declaredSource(
            GenerationRequestV2 request,
            String sourceId
    ) {
        return request.constraintMaps().stream()
                .filter(source -> source.sourceId().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "the request declares no constraint source with id " + sourceId));
    }

    /**
     * The request declaration carries a decoder and an encoding but no role, so the author states the
     * role and this check refuses the combinations that cannot mean what they say. Nothing is inferred
     * from the encoding.
     */
    private static void requireRoleMatchesEncoding(
            TerrainIntentV2.ConstraintMapRole role,
            GenerationRequestV2.ConstraintMapSource source
    ) {
        boolean height = source.encoding() instanceof GenerationRequestV2.HeightEncoding;
        if (role == TerrainIntentV2.ConstraintMapRole.HEIGHT_GUIDE != height) {
            throw new IllegalArgumentException("role " + role + " does not match the declared "
                    + source.decoderKind() + " encoding of " + source.sourceId());
        }
        if (role == TerrainIntentV2.ConstraintMapRole.LAND_WATER_MASK
                && source.encoding() instanceof GenerationRequestV2.CategoricalEncoding categorical) {
            List<String> labels = categorical.labels().stream()
                    .map(GenerationRequestV2.LabelMapping::label)
                    .toList();
            if (!labels.equals(List.of("water", "land"))) {
                throw new IllegalArgumentException(
                        "LAND_WATER_MASK requires the water/land label pair, not " + labels);
            }
        }
    }

    private static TerrainIntentV2 withMapReferences(
            TerrainIntentV2 intent,
            List<TerrainIntentV2.ConstraintMapBinding> bindings
    ) {
        return new TerrainIntentV2(
                intent.intentVersion(),
                intent.intentId(),
                intent.theme(),
                intent.coordinateSystem(),
                intent.features(),
                intent.relations(),
                intent.constraints(),
                intent.environment(),
                bindings,
                intent.structures(),
                intent.provenance());
    }

    /** One binding together with its declared source and everything found wrong with the pair. */
    public record BindingStatus(
            TerrainIntentV2.ConstraintMapBinding binding,
            GenerationRequestV2.ConstraintMapSource source,
            List<String> problems
    ) {
        public BindingStatus {
            Objects.requireNonNull(binding, "binding");
            problems = List.copyOf(problems);
        }

        public boolean consistent() {
            return problems.isEmpty();
        }
    }

    /** Verification result: one row per binding plus the declared sources nothing binds. */
    public record Report(List<BindingStatus> bindings, List<String> unboundSourceIds) {
        public Report {
            bindings = List.copyOf(bindings);
            unboundSourceIds = List.copyOf(unboundSourceIds);
        }

        public boolean consistent() {
            return bindings.stream().allMatch(BindingStatus::consistent);
        }
    }
}
