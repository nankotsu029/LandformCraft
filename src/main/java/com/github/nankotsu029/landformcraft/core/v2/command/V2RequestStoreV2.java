package com.github.nankotsu029.landformcraft.core.v2.command;

import com.github.nankotsu029.landformcraft.format.v2.LandformV2DataCodec;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.TerrainIntentV2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Authoring store for v2 generation requests (V2-12-08, ADR 0035 D11).
 *
 * <p>v1 could create and edit a request from inside the game or the CLI; v2 could only strictly read
 * one that already existed on disk. This store closes that gap (coverage audit F1) without changing
 * the {@link GenerationRequestV2} contract: every mutation reads the current request, replaces one
 * field, and writes the result back through {@link LandformV2DataCodec}, which validates against the
 * strict schema and publishes atomically.</p>
 *
 * <p>The store is Bukkit-free and holds no state, so the CLI and the Paper adapter share it. Request
 * IDs are portable slugs resolved inside {@code root}; the surfaces never pass a caller-supplied
 * path.</p>
 */
public final class V2RequestStoreV2 {
    /** File suffix of a stored request, so a requests root can hold nothing else by accident. */
    public static final String SUFFIX = ".request-v2.json";

    private static final Pattern REQUEST_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final GenerationRequestV2.Bounds DEFAULT_BOUNDS =
            new GenerationRequestV2.Bounds(128, 128, -32, 160, 62);
    private static final String DEFAULT_PROMPT = "Describe the terrain before validation.";
    private static final GenerationRequestV2.GenerationSettings DEFAULT_GENERATION =
            new GenerationRequestV2.GenerationSettings(0L, 64);

    private final LandformV2DataCodec codec = new LandformV2DataCodec();
    private final PromotedConstraintSourceFactoryV2 sourceFactory = new PromotedConstraintSourceFactoryV2();
    private final Path root;

    public V2RequestStoreV2(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    /**
     * Creates a request with conservative defaults. Fails if the ID is already taken: authoring must
     * never silently overwrite an operator's work.
     */
    public GenerationRequestV2 create(String requestId) throws IOException {
        String id = requireRequestId(requestId);
        Path target = resolve(id);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("v2 request '" + id + "' already exists");
        }
        GenerationRequestV2 request = new GenerationRequestV2(
                GenerationRequestV2.VERSION,
                id,
                DEFAULT_BOUNDS,
                DEFAULT_PROMPT,
                List.of(),
                List.of(),
                DEFAULT_GENERATION,
                GenerationRequestV2.ConstraintMapBudget.defaults(),
                java.util.Optional.empty());
        publish(target, request);
        return request;
    }

    /**
     * Replaces the bounds. The water level is clamped into the new vertical range rather than
     * rejected, matching the v1 editing behaviour that kept a request editable in any order.
     */
    public GenerationRequestV2 bounds(
            String requestId,
            int width,
            int length,
            int minY,
            int maxY,
            int waterLevel
    ) throws IOException {
        String id = requireRequestId(requestId);
        Path target = resolve(id);
        GenerationRequestV2 current = read(target, id);
        int clamped = Math.max(minY, Math.min(maxY, waterLevel));
        GenerationRequestV2 updated = withBounds(current,
                new GenerationRequestV2.Bounds(width, length, minY, maxY, clamped));
        publish(target, updated);
        return updated;
    }

    /**
     * Replaces the horizontal and vertical extent while keeping the request's current water level,
     * clamped into the new range. This is the form the Paper WorldEdit-selection verb uses, since a
     * selection carries no water level.
     */
    public GenerationRequestV2 boundsKeepingWaterLevel(
            String requestId,
            int width,
            int length,
            int minY,
            int maxY
    ) throws IOException {
        String id = requireRequestId(requestId);
        return bounds(id, width, length, minY, maxY, read(resolve(id), id).bounds().waterLevel());
    }

    /**
     * Declares the single land/water constraint map source a {@code surface-2_5d} export requires.
     *
     * <p>The export path reads this declaration for provenance only — source id and digest — but
     * refuses to run without exactly one entry, so authoring cannot reach {@code v2 export} unless it
     * can set one. The canonical categorical form is fixed here rather than exposed as a dozen
     * command arguments: U8 grayscale, north-west origin, east/south axes, pixel centres, no
     * rotation or flip, full-image crop, samples {@code 0=water} and {@code 1=land}, no-data
     * forbidden. Anything else stays a hand-authored file; this method never guesses one.</p>
     *
     * <p>Setting a source replaces the previous declaration, matching the export's "exactly one"
     * rule.</p>
     */
    public GenerationRequestV2 constraintMap(
            String requestId,
            String sourceSlug,
            String file,
            String expectedSha256,
            int expectedWidth,
            int expectedLength
    ) throws IOException {
        String id = requireRequestId(requestId);
        Path target = resolve(id);
        GenerationRequestV2 current = read(target, id);
        GenerationRequestV2.ConstraintMapSource source = new GenerationRequestV2.ConstraintMapSource(
                "constraint-source:" + Objects.requireNonNull(sourceSlug, "sourceSlug"),
                file,
                expectedSha256,
                expectedWidth,
                expectedLength,
                GenerationRequestV2.DecoderKind.CATEGORICAL_RASTER,
                new GenerationRequestV2.CoordinateMapping(
                        GenerationRequestV2.CoordinateOrigin.NORTH_WEST,
                        GenerationRequestV2.XAxis.EAST,
                        GenerationRequestV2.ZAxis.SOUTH,
                        GenerationRequestV2.PixelReference.PIXEL_CENTER,
                        GenerationRequestV2.AspectMismatchPolicy.REJECT,
                        GenerationRequestV2.QuarterTurn.DEGREES_0,
                        false,
                        false,
                        new GenerationRequestV2.PixelCrop(0, 0, expectedWidth, expectedLength)),
                new GenerationRequestV2.CategoricalEncoding(
                        1,
                        GenerationRequestV2.SampleType.U8,
                        GenerationRequestV2.RasterChannel.GRAY,
                        List.of(
                                new GenerationRequestV2.LabelMapping(0, "water"),
                                new GenerationRequestV2.LabelMapping(1, "land")),
                        new GenerationRequestV2.NoDataForbidden()));
        GenerationRequestV2 updated = new GenerationRequestV2(
                current.requestVersion(), current.requestId(), current.bounds(), current.prompt(),
                current.referenceImages(), List.of(source), current.generation(),
                current.constraintMapBudget(), current.foundationBaseLevels());
        publish(target, updated);
        return updated;
    }

    /**
     * Declares one constraint map source of any role from its sealed promotion record (V2-19-04).
     *
     * <p>Unlike {@link #constraintMap}, this adds to the declaration set instead of replacing it: a
     * source with the same id is updated in place and every other source is preserved, which is what
     * makes a multi-map request authorable at all. The role, encoding, dimensions and digest are read
     * from the {@code promote} output rather than supplied as arguments, so a declaration always
     * describes a map that was actually produced and nothing about the encoding is guessed.</p>
     *
     * <p>The map file itself is not copied here; the declaration names the request-relative path the
     * operator places it at, exactly as {@link #constraintMap} does. Which roles a generator actually
     * consumes is a separate question from which ones can be declared — today only a HARD
     * {@code LAND_WATER_MASK} reaches a consumer.</p>
     */
    public GenerationRequestV2 constraintSource(
            String requestId,
            String sourceSlug,
            TerrainIntentV2.ConstraintMapRole role,
            Path promotionDirectory,
            String file
    ) throws IOException {
        String id = requireRequestId(requestId);
        Path target = resolve(id);
        GenerationRequestV2 current = read(target, id);
        GenerationRequestV2.ConstraintMapSource source = sourceFactory.fromPromotion(
                promotionDirectory,
                role,
                "constraint-source:" + Objects.requireNonNull(sourceSlug, "sourceSlug"),
                file);
        List<GenerationRequestV2.ConstraintMapSource> sources = new ArrayList<>();
        for (GenerationRequestV2.ConstraintMapSource existing : current.constraintMaps()) {
            if (!existing.sourceId().equals(source.sourceId())) {
                sources.add(existing);
            }
        }
        sources.add(source);
        GenerationRequestV2 updated = new GenerationRequestV2(
                current.requestVersion(), current.requestId(), current.bounds(), current.prompt(),
                current.referenceImages(), sources, current.generation(),
                current.constraintMapBudget(), current.foundationBaseLevels());
        publish(target, updated);
        return updated;
    }

    /**
     * Replaces the generation settings (global seed and tile size).
     *
     * <p>Authoring could not set them, which was harmless while the surface path filled unowned cells
     * from a baseline. Since {@code V2-18-09} the HARD {@code LAND_WATER_MASK} is resolved into
     * generation and must agree with the composed feature geometry, and since {@code V2-18-10} every
     * surface export takes that path — so a request whose seed does not match the mask's provenance
     * can never export. Exposing the seed keeps authoring able to reproduce a mask's request instead
     * of silently producing an unexportable one.</p>
     */
    public GenerationRequestV2 generation(String requestId, long globalSeed, int tileSize)
            throws IOException {
        String id = requireRequestId(requestId);
        Path target = resolve(id);
        GenerationRequestV2 current = read(target, id);
        GenerationRequestV2 updated = new GenerationRequestV2(
                current.requestVersion(), current.requestId(), current.bounds(), current.prompt(),
                current.referenceImages(), current.constraintMaps(),
                new GenerationRequestV2.GenerationSettings(globalSeed, tileSize),
                current.constraintMapBudget(), current.foundationBaseLevels());
        publish(target, updated);
        return updated;
    }

    /**
     * Declares the per-medium provisional base elevation of the macro foundation (ADR 0038 D2-2(b)).
     *
     * <p>Since {@code V2-18-10} a {@code surface-2_5d} export requires an effective foundation owner
     * on every cell, and the only wired foundation input is a HARD {@code LAND_WATER_MASK} map
     * reference together with these declared levels. Authoring could set the map source but not the
     * levels, so an authored request could not reach a passing export; this verb closes that gap
     * without widening the contract — both values are plain block Y and are validated against the
     * request bounds by {@link GenerationRequestV2}. Nothing is inferred from the bounds or the mask.
     * </p>
     */
    public GenerationRequestV2 foundationBaseLevels(
            String requestId,
            int landSurfaceY,
            int waterBedY
    ) throws IOException {
        String id = requireRequestId(requestId);
        Path target = resolve(id);
        GenerationRequestV2 current = read(target, id);
        GenerationRequestV2 updated = new GenerationRequestV2(
                current.requestVersion(), current.requestId(), current.bounds(), current.prompt(),
                current.referenceImages(), current.constraintMaps(), current.generation(),
                current.constraintMapBudget(),
                java.util.Optional.of(new GenerationRequestV2.FoundationBaseLevels(landSurfaceY, waterBedY)));
        publish(target, updated);
        return updated;
    }

    /** Replaces the prompt. Refuses text that resembles a credential (AGENTS.md §12). */
    public GenerationRequestV2 prompt(String requestId, String prompt) throws IOException {
        String id = requireRequestId(requestId);
        Objects.requireNonNull(prompt, "prompt");
        if (looksSecret(prompt)) {
            throw new IllegalArgumentException(
                    "prompt resembles a secret and was refused; set provider keys through environment "
                            + "variables only");
        }
        Path target = resolve(id);
        GenerationRequestV2 current = read(target, id);
        GenerationRequestV2 updated = new GenerationRequestV2(
                current.requestVersion(), current.requestId(), current.bounds(), prompt,
                current.referenceImages(), current.constraintMaps(), current.generation(),
                current.constraintMapBudget(), current.foundationBaseLevels());
        publish(target, updated);
        return updated;
    }

    /** Strictly reads one stored request. */
    public GenerationRequestV2 read(String requestId) throws IOException {
        String id = requireRequestId(requestId);
        return read(resolve(id), id);
    }

    /**
     * Request IDs in deterministic order. Each candidate is strictly read, so a file whose contents
     * do not match its name is reported rather than silently listed.
     */
    public List<String> list() throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (var paths = Files.list(root)) {
            paths.filter(path -> path.getFileName().toString().endsWith(SUFFIX))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .forEach(files::add);
        }
        List<String> ids = new ArrayList<>(files.size());
        for (Path file : files) {
            String name = file.getFileName().toString();
            String id = name.substring(0, name.length() - SUFFIX.length());
            ids.add(read(file, id).requestId());
        }
        return List.copyOf(ids);
    }

    /** Absolute path of one stored request, for the surfaces to hand to {@code v2 export}. */
    public Path pathOf(String requestId) {
        return resolve(requireRequestId(requestId));
    }

    /** Path relative to {@code root}, which is what the Paper surface accepts as a command argument. */
    public String relativePathOf(String requestId) {
        return requireRequestId(requestId) + SUFFIX;
    }

    public Path root() {
        return root;
    }

    private GenerationRequestV2 read(Path target, String id) throws IOException {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("v2 request '" + id + "' does not exist");
        }
        GenerationRequestV2 request = codec.readGenerationRequest(target);
        if (!request.requestId().equals(id)) {
            throw new IllegalArgumentException(
                    "stored v2 request declares id '" + request.requestId() + "' but is filed as '" + id + "'");
        }
        return request;
    }

    private void publish(Path target, GenerationRequestV2 request) throws IOException {
        Files.createDirectories(root);
        if (Files.isSymbolicLink(target)) {
            throw new IOException("v2 request path must not be a symbolic link");
        }
        // writeGenerationRequest validates against the strict schema and moves atomically into place.
        codec.writeGenerationRequest(target, request);
    }

    private Path resolve(String id) {
        Path target = root.resolve(id + SUFFIX).normalize();
        if (!target.startsWith(root) || !target.getParent().equals(root)) {
            throw new IllegalArgumentException("v2 request path escapes the requests root");
        }
        return target;
    }

    private static GenerationRequestV2 withBounds(
            GenerationRequestV2 current,
            GenerationRequestV2.Bounds bounds
    ) {
        return new GenerationRequestV2(
                current.requestVersion(), current.requestId(), bounds, current.prompt(),
                current.referenceImages(), current.constraintMaps(), current.generation(),
                current.constraintMapBudget(), current.foundationBaseLevels());
    }

    private static String requireRequestId(String value) {
        Objects.requireNonNull(value, "requestId");
        if (!REQUEST_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "v2 request id must be a lowercase portable slug, not '" + value + "'");
        }
        return value;
    }

    /** Same filter the v1 CLI applied to prompts, so the v2 path is not weaker (AGENTS.md §12). */
    static boolean looksSecret(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("authorization:") || lower.contains("api_key=")
                || lower.contains("apikey=") || value.matches(".*sk-[A-Za-z0-9_-]{16,}.*");
    }
}
