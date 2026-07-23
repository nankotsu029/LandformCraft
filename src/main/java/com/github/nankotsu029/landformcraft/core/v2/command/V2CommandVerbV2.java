package com.github.nankotsu029.landformcraft.core.v2.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The official v2 command surface (ADR 0035 D5, V2-12-03).
 *
 * <p>Each constant is one canonical {@code v2 <verb> [operation] ...} form together with its
 * permission node and arity. The table is
 * Bukkit-free so the CLI and the Paper adapter route through exactly the same contract.</p>
 *
 * <p>Token counts include the leading {@code v2} token.</p>
 */
public enum V2CommandVerbV2 {
    REQUEST_VALIDATE("request", "validate", 4, 4,
            "v2 request validate <generation-request-v2.json>", "request", Surface.BOTH, null),
    REQUEST_INFO("request", "info", 4, 4,
            "v2 request info <generation-request-v2.json>", "request", Surface.BOTH, null),
    REQUEST_CREATE("request", "create", 4, 4,
            "v2 request create <request-id>", "request.create", Surface.BOTH, null),
    REQUEST_BOUNDS("request", "bounds", 9, 9,
            "v2 request bounds <request-id> <width> <length> <min-y> <max-y> <water-level>",
            "request.edit", Surface.BOTH, null),
    /**
     * Paper-only: takes bounds from the operator's current WorldEdit selection, which has no CLI
     * equivalent. Declared as its own operation token because the router selects on
     * {@code (verb, operation)} before arity.
     */
    REQUEST_SELECTION("request", "selection", 4, 4,
            "v2 request selection <request-id>", "request.edit", Surface.PAPER, null),
    /** Paper form: captures the operator's next chat message as the prompt. */
    REQUEST_PROMPT("request", "prompt", 4, 4,
            "v2 request prompt <request-id>", "request.edit", Surface.PAPER, null),
    /** CLI form: the prompt is given inline, since a CLI has no chat to capture. */
    REQUEST_PROMPT_INLINE("request", "prompt", 5, 64,
            "v2 request prompt <request-id> <prompt...>", "request.edit", Surface.CLI, null),
    /**
     * Declares the single land/water constraint map source a {@code surface-2_5d} export requires.
     * The canonical categorical form lives in the store; only the file, digest, and dimensions vary.
     */
    REQUEST_CONSTRAINT_MAP("request", "constraint-map", 9, 9,
            "v2 request constraint-map <request-id> <source-slug> <file> <sha256> <width> <length>",
            "request.edit", Surface.BOTH, null),
    /**
     * Replaces the generation settings. Export-relevant since the resolved mask must match the
     * seed's composed geometry (V2-18-09/10), so authoring must be able to reproduce a mask's seed.
     */
    REQUEST_GENERATION("request", "generation", 6, 6,
            "v2 request generation <request-id> <global-seed> <tile-size>",
            "request.edit", Surface.BOTH, null),
    /**
     * Declares the macro foundation's per-medium provisional base elevation (V2-18-10, ADR 0038 D2-2).
     * Together with the constraint map source this is the explicit foundation input the surface owner
     * gate requires, so authoring can reach a passing {@code v2 export} without hand-editing JSON.
     */
    REQUEST_FOUNDATION_BASE_LEVELS("request", "foundation-base-levels", 6, 6,
            "v2 request foundation-base-levels <request-id> <land-surface-y> <water-bed-y>",
            "request.edit", Surface.BOTH, null),
    REQUEST_LIST("request", "list", 3, 3,
            "v2 request list", "request", Surface.BOTH, null),
    DESIGN("design", null, 5, 6,
            "v2 design <import|fixture|openai|anthropic> <request-v2.json> <intent-or-model> [designs-root]",
            "design", Surface.BOTH, null),
    GENERATE("generate", null, 9, 9,
            "v2 generate <request-v2.json> <terrain-intent-v2.json> <exports-root> <release-id> "
                    + "<land|water> <land-surface-y> <water-bed-y>",
            "export", Surface.BOTH, null),
    EXPORT("export", null, 9, 9,
            "v2 export <request-v2.json> <terrain-intent-v2.json> <exports-root> <release-id> "
                    + "<land|water> <land-surface-y> <water-bed-y>",
            "export", Surface.BOTH, null),
    /**
     * Reserves one export and issues a single-use confirmation token (V2-12-09). Paper-only: the
     * plan lives in the running server, which is also where the confirmed job executes.
     */
    EXPORT_PLAN("export", "plan", 10, 10,
            "v2 export plan <request-v2.json> <terrain-intent-v2.json> <exports-root> <release-id> "
                    + "<land|water> <land-surface-y> <water-bed-y>",
            "export", Surface.PAPER, null),
    /** Consumes the confirmation token and queues the asynchronous export job (V2-12-09). */
    EXPORT_CREATE("export", "create", 5, 5,
            "v2 export create <plan-id> <token>", "export", Surface.PAPER, null),
    /**
     * V2-15-10 / ADR 0039 Candidate A: publishes the {@code hydrology-plan} + {@code surface-2_5d}
     * Release (the {@code OFFLINE_PRODUCTION} route for {@code RIVER} / {@code MEANDERING_RIVER}
     * alongside the coastal contributors) instead of the plain {@code surface-2_5d} form. This never
     * promotes a Paper {@code paper_apply} capability; it stays CLI-only, like {@code migrate}.
     */
    EXPORT_HYDROLOGY("export", "hydrology-plan", 10, 10,
            "v2 export hydrology-plan <request-v2.json> <terrain-intent-v2.json> <exports-root> "
                    + "<release-id> <land|water> <land-surface-y> <water-bed-y>",
            "export", Surface.CLI, null),
    PREVIEW("preview", null, 3, 3,
            "v2 preview <release-directory-or-zip>", "preview", Surface.BOTH, null),
    JOB_STATUS("job", "status", 4, 4,
            "v2 job status <job-id>", "job", Surface.BOTH, null),
    JOB_CANCEL("job", "cancel", 4, 4,
            "v2 job cancel <job-id>", "job", Surface.BOTH, null),
    JOB_LIST("job", "list", 3, 3,
            "v2 job list", "job", Surface.BOTH, null),
    /** Published exports of one request — the v2 equivalent of a v1 candidate list. */
    CANDIDATE_LIST("candidate", "list", 4, 4,
            "v2 candidate list <request-id>", "candidate", Surface.BOTH, null),
    CANDIDATE_INFO("candidate", "info", 4, 4,
            "v2 candidate info <job-id>", "candidate", Surface.BOTH, null),
    /**
     * Deterministic image-extraction path (V2-14-01). {@code extract} turns an untrusted PNG/JPEG on
     * the operator's workstation into a sealed V2-7 draft bundle; {@code promote} re-loads a draft and
     * turns it into a V2-1 constraint map with the operator's explicit confidence/handling. Both read
     * arbitrary local image paths and stay {@code EXPERIMENTAL}, so they are CLI-only like migrate.
     */
    EXTRACT_LAND_WATER("extract", "land-water", 5, 5,
            "v2 extract land-water <image-file> <draft-output-dir>", "extract", Surface.CLI, null),
    EXTRACT_HEIGHT_GUIDE("extract", "height-guide", 5, 5,
            "v2 extract height-guide <image-file> <draft-output-dir>", "extract", Surface.CLI, null),
    EXTRACT_ZONE_LABEL("extract", "zone-label", 5, 5,
            "v2 extract zone-label <image-file> <draft-output-dir>", "extract", Surface.CLI, null),
    PROMOTE_LAND_WATER("promote", "land-water", 7, 8,
            "v2 promote land-water <draft-dir> <output-dir> <confidence-threshold> "
                    + "<reject|water|land|nodata> [nodata-sample]", "extract", Surface.CLI, null),
    PROMOTE_HEIGHT_GUIDE("promote", "height-guide", 10, 10,
            "v2 promote height-guide <draft-dir> <output-dir> <request-v2.json> <confidence-threshold> "
                    + "<absolute-block-y|blocks-above-min-y|blocks-relative-to-water> "
                    + "<scale-millionths> <offset-millionths>", "extract", Surface.CLI, null),
    PROMOTE_ZONE_LABEL("promote", "zone-label", 7, 8,
            "v2 promote zone-label <draft-dir> <output-dir> <request-v2.json> <confidence-threshold> "
                    + "[nodata-sample]", "extract", Surface.CLI, null),
    MIGRATE_INSPECT("migrate", "inspect", 5, 5,
            "v2 migrate inspect <intent|design|release> <v1-source>", "migrate", Surface.CLI, null),
    MIGRATE_APPLY("migrate", "apply", 8, 8,
            "v2 migrate apply <intent|design|release> <v1-source> <output-root> <migration-id> "
                    + "<strict|accept-lossy>", "migrate", Surface.CLI, null),

    PLACE_PLAN("place", "plan", 8, 8,
            "v2 place plan <release-path> <world> <x> <y> <z>", "plan", Surface.PAPER, "plan"),
    PLACE_CONFIRM("place", "confirm", 5, 5,
            "v2 place confirm <placement-id> <token>", "confirm", Surface.PAPER, "confirm"),
    PLACE_EXECUTE("place", "execute", 4, 4,
            "v2 place execute <placement-id>", "execute", Surface.PAPER, "execute"),
    STATUS("status", null, 3, 3,
            "v2 status <placement-id>", "status", Surface.PAPER, "status"),
    UNDO_PLAN("undo", "plan", 4, 4,
            "v2 undo plan <placement-id>", "undo", Surface.PAPER, "undo-plan"),
    UNDO_EXECUTE("undo", "execute", 5, 5,
            "v2 undo execute <placement-id> <token>", "undo", Surface.PAPER, "undo-execute"),
    /**
     * Retention snapshot cleanup (V2-12-10), the v2 equivalent of the v1 {@code cleanup} verb.
     * Paper-only and world-bound: it reads a placement's applied journal and deletes only
     * retention-window snapshot state through the recovery planner.
     */
    RETENTION_PLAN("retention", "plan", 4, 4,
            "v2 retention plan <placement-id>", "retention", Surface.PAPER, null),
    RETENTION_EXECUTE("retention", "execute", 6, 6,
            "v2 retention execute <placement-id> <plan-id> <token>", "retention", Surface.PAPER, null),
    RETENTION_STATUS("retention", "status", 4, 4,
            "v2 retention status <placement-id>", "retention", Surface.PAPER, null),
    /**
     * Read-only strict verification of a v2 placement journal file (V2-12-10), the v2 equivalent of
     * the v1 {@code journal-verify} verb. CLI-only: it reads an explicit artifact on the operator's
     * workstation and mutates nothing.
     */
    JOURNAL_VERIFY("journal-verify", null, 3, 3,
            "v2 journal-verify <placement-journal-v2.json>", "recovery", Surface.CLI, null),
    /**
     * Read-only inspection of a v2 placement journal or recovery plan file (V2-12-10). Gives the CLI
     * the recovery visibility the v1 {@code recovery status|diagnose} verbs offered, operating on an
     * explicit artifact rather than mutating a world.
     */
    RECOVERY_INSPECT("recovery", "inspect", 5, 5,
            "v2 recovery inspect <journal|plan> <placement-journal-or-recovery-plan-v2.json>",
            "recovery", Surface.CLI, null),
    RECOVER_DIAGNOSE("recover", "diagnose", 4, 4,
            "v2 recover diagnose <placement-id>", "recovery", Surface.PAPER, "recover-diagnose"),
    RECOVER_PLAN("recover", "plan", 5, 5,
            "v2 recover plan <rollback|accept> <placement-id>", "recovery", Surface.PAPER, "recover-plan"),
    RECOVER_EXECUTE("recover", "execute", 6, 6,
            "v2 recover execute <rollback|accept> <placement-id> <token>", "recovery", Surface.PAPER,
            "recover-execute");

    /** Root token of the official command path. */
    public static final String ROOT = "v2";
    private static final String PERMISSION_PREFIX = "landformcraft.v2.";

    public enum Surface {
        /** Offline verbs available from both the CLI and Paper. */
        BOTH,
        /** World-bound verbs; the CLI rejects them with {@link V2CommandErrorCodeV2#V2_PAPER_ONLY}. */
        PAPER,
        /**
         * Operator-workstation verbs that read arbitrary legacy asset paths and therefore stay off
         * the live-server surface; Paper rejects them with
         * {@link V2CommandErrorCodeV2#V2_CLI_ONLY}.
         */
        CLI
    }

    private final String verb;
    private final String operation;
    private final int minimumTokens;
    private final int maximumTokens;
    private final String usage;
    private final String permissionSuffix;
    private final Surface surface;
    private final String placementOperation;

    V2CommandVerbV2(
            String verb,
            String operation,
            int minimumTokens,
            int maximumTokens,
            String usage,
            String permissionSuffix,
            Surface surface,
            String placementOperation
    ) {
        this.verb = verb;
        this.operation = operation;
        this.minimumTokens = minimumTokens;
        this.maximumTokens = maximumTokens;
        this.usage = usage;
        this.permissionSuffix = permissionSuffix;
        this.surface = surface;
        this.placementOperation = placementOperation;
    }

    public String verb() {
        return verb;
    }

    /** Operation token, or {@code null} for verbs that take their arguments directly. */
    public String operation() {
        return operation;
    }

    public int minimumTokens() {
        return minimumTokens;
    }

    public int maximumTokens() {
        return maximumTokens;
    }

    public String usage() {
        return usage;
    }

    public Surface surface() {
        return surface;
    }

    /**
     * Whether this verb can be invoked from {@code caller}. A {@link Surface#BOTH} verb runs
     * everywhere; a surface-bound verb runs only from its own surface.
     */
    public boolean availableOn(Surface caller) {
        Objects.requireNonNull(caller, "caller");
        return surface == Surface.BOTH || surface == caller;
    }

    /** Stable code for rejecting this verb on a surface it does not belong to. */
    V2CommandErrorCodeV2 wrongSurfaceCode() {
        return surface == Surface.PAPER
                ? V2CommandErrorCodeV2.V2_PAPER_ONLY
                : V2CommandErrorCodeV2.V2_CLI_ONLY;
    }

    /** Operator-facing reason this verb is bound to one surface. */
    String wrongSurfaceReason() {
        return surface == Surface.PAPER
                ? canonicalPrefix() + " mutates or reads a live world and is only available from the "
                        + "Paper command path"
                : canonicalPrefix() + " reads legacy asset paths on the operator's workstation and is "
                        + "only available from the CLI";
    }

    /** Canonical permission node introduced by ADR 0035 D4. */
    public String permission() {
        return PERMISSION_PREFIX + permissionSuffix;
    }

    /** Internal placement-operation token used by the Paper lifecycle adapter. */
    public String placementOperation() {
        return placementOperation;
    }

    /** Canonical command text, for example {@code v2 undo plan}. */
    public String canonicalPrefix() {
        return operation == null ? ROOT + " " + verb : ROOT + " " + verb + " " + operation;
    }

    /** Distinct verb tokens in declaration order, for help text and tab completion. */
    public static List<String> verbs() {
        return values().length == 0 ? List.of() : java.util.Arrays.stream(values())
                .map(V2CommandVerbV2::verb)
                .distinct()
                .toList();
    }

    /** Operation tokens declared for one verb, empty when the verb takes arguments directly. */
    public static List<String> operationsOf(String verb) {
        Objects.requireNonNull(verb, "verb");
        String normalized = verb.toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(values())
                .filter(candidate -> candidate.verb.equals(normalized) && candidate.operation != null)
                .map(V2CommandVerbV2::operation)
                // Surface-specific forms share an operation token; list it once.
                .distinct()
                .toList();
    }
}
