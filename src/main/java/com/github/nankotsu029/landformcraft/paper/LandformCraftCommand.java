package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.v2.DiagnosticBlueprintCompilerV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2CommandErrorCodeV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2CommandRouteV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2CommandRouterV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2CommandVerbV2;
import com.github.nankotsu029.landformcraft.model.v2.GenerationRequestV2;
import com.github.nankotsu029.landformcraft.model.v2.job.ExportJobSnapshotV2;
import com.github.nankotsu029.landformcraft.core.v2.command.V2WorkflowServiceV2;
import com.github.nankotsu029.landformcraft.core.CustomAssetService;
import com.github.nankotsu029.landformcraft.core.DoctorService;
import com.github.nankotsu029.landformcraft.core.LandformException;
import com.github.nankotsu029.landformcraft.core.LandformErrorCode;
import com.github.nankotsu029.landformcraft.core.v2.operations.OperationalMetricsCollectorV2;
import com.github.nankotsu029.landformcraft.cli.LandformCraftCli;
import com.github.nankotsu029.landformcraft.worldedit.IncompleteSelectionException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import com.github.nankotsu029.landformcraft.model.v2.scale.ScaleDimensionPolicyV2;

/** Colored administrative commands with confirm-gated mutations and non-blocking completion caches. */
public final class LandformCraftCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String ROOT = "/landformcraft ";

    private final PaperRelease2PlacementServiceV2 release2Placements;
    private final PaperOperationalOperationsServiceV2 release2Operations;
    private final PaperV2WorkflowServiceV2 v2Workflow;
    private final PaperWorldEditSelectionService selections;
    private final PaperMainThreadDispatcher dispatcher;
    private final GenerationExecutors executors;
    private final CustomAssetService assets;
    private final Path dataRoot;
    private final ConsoleConfirmationFileStore consoleConfirmations;
    private final String pluginVersion;
    private final String worldEditStatus;
    private final Map<UUID, PromptSession> prompts = new ConcurrentHashMap<>();
    private final Set<String> placementIds = ConcurrentHashMap.newKeySet();
    private final AtomicLong nextCompletionRefresh = new AtomicLong();
    private volatile List<String> releaseDirectories = List.of();

    public LandformCraftCommand(
            PaperRelease2PlacementServiceV2 release2Placements,
            PaperOperationalOperationsServiceV2 release2Operations,
            PaperV2WorkflowServiceV2 v2Workflow,
            PaperWorldEditSelectionService selections,
            PaperMainThreadDispatcher dispatcher,
            GenerationExecutors executors,
            CustomAssetService assets,
            Path dataRoot,
            String pluginVersion,
            String worldEditStatus
    ) {
        this.v2Workflow = v2Workflow;
        this.release2Placements = release2Placements;
        this.release2Operations = release2Operations;
        this.selections = selections;
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.executors = Objects.requireNonNull(executors, "executors");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot").toAbsolutePath().normalize();
        this.consoleConfirmations = new ConsoleConfirmationFileStore(
                this.dataRoot.resolve("confirmations"));
        this.pluginVersion = Objects.requireNonNull(pluginVersion, "pluginVersion");
        this.worldEditStatus = Objects.requireNonNull(worldEditStatus, "worldEditStatus");
        refreshCompletionCaches();
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        try {
            if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("help")) {
                requirePermission(sender, "landformcraft.help");
                showHelp(sender, label);
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("version")) {
                requirePermission(sender, "landformcraft.version");
                reportVersion(sender);
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("doctor")) {
                requirePermission(sender, "landformcraft.doctor");
                reportDoctor(sender);
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("ops")) {
                handleOps(sender, args);
                return true;
            }
            // Maintained, version-neutral verbs stay on the default surface regardless of the v1/v2
            // switch (ADR 0035 D10: asset=K3, selection=K2).
            if (args.length >= 2 && args[0].equalsIgnoreCase("asset")) {
                handleAsset(sender, args);
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("selection")) {
                requirePermission(sender, "landformcraft.selection");
                requirePlacementAvailable();
                requirePlayer(sender, player -> reportSelection(sender, player));
                return true;
            }
            // The explicit v2 root remains the stable version-qualified alias.
            if (args[0].equalsIgnoreCase(V2CommandVerbV2.ROOT)) {
                handleV2(sender, args);
                return true;
            }
            // Default surface is v2 (ADR 0035 D5). `/lfc <verb>` is an alias for `/lfc v2 <verb>`.
            handleV2(sender, prependV2Root(args));
            return true;
        } catch (IllegalArgumentException | LandformException exception) {
            sendFailure(sender, exception);
            return true;
        }
    }

    /** Rewrites {@code <verb> …} to {@code v2 <verb> …} so the default surface routes through v2. */
    private static String[] prependV2Root(String[] args) {
        String[] rooted = new String[args.length + 1];
        rooted[0] = V2CommandVerbV2.ROOT;
        System.arraycopy(args, 0, rooted, 1, args.length);
        return rooted;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        refreshCompletionCaches();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> roots = new java.util.ArrayList<>();
            // Maintained, version-neutral verbs.
            addIfAllowed(roots, sender, "landformcraft.help", "help");
            addIfAllowed(roots, sender, "landformcraft.version", "version");
            addIfAllowed(roots, sender, "landformcraft.doctor", "doctor");
            addIfAllowed(roots, sender, "landformcraft.doctor", "ops");
            addIfAllowed(roots, sender, "landformcraft.asset.read", "asset");
            addIfAllowed(roots, sender, "landformcraft.selection", "selection");
            // Default surface is v2 (ADR 0035 D5): offer the v2 verb tokens directly.
            roots.addAll(V2CommandRouterV2.complete(
                    new String[] {V2CommandVerbV2.ROOT, prefix},
                    V2CommandVerbV2.Surface.PAPER, verb -> allowsV2Verb(sender, verb)));
            // Explicit version prefixes.
            if (anyV2VerbAllowed(sender)) {
                roots.add(V2CommandVerbV2.ROOT);
            }
            return roots.stream().filter(value -> value.startsWith(prefix)).distinct().sorted().toList();
        }
        Collection<String> worlds = Bukkit.getWorlds().stream().map(world -> world.getName()).toList();
        List<String> coordinates = sender instanceof Player player
                ? List.of(
                        Integer.toString(player.getLocation().getBlockX()),
                        Integer.toString(player.getLocation().getBlockY()),
                        Integer.toString(player.getLocation().getBlockZ())
                ) : List.of("0", "0", "0");
        // Explicit v2, or the default surface (which is v2): route completion through the v2
        // router. For the default surface, prepend the implicit v2 root.
        boolean explicitV2Root = args[0].equalsIgnoreCase(V2CommandVerbV2.ROOT);
        String[] v2Args = explicitV2Root ? args : prependV2Root(args);
        List<String> verbs = V2CommandRouterV2.complete(
                v2Args, V2CommandVerbV2.Surface.PAPER, verb -> allowsV2Verb(sender, verb));
        if (!verbs.isEmpty()) {
            return verbs;
        }
        if (release2Placements == null || !Release2CommandSecurityV2.isEligibleOperator(sender)) {
            return List.of();
        }
        List<String> allowedWorlds = worlds.stream()
                .filter(release2Placements::allowsWorld)
                .toList();
        String[] placement = placementCompletionArguments(v2Args);
        return placement.length == 0 ? List.of() : LandformCraftSuggestions.completeRelease2(
                placement,
                allowsV2Verb(sender, V2CommandVerbV2.PLACE_PLAN),
                allowsV2Verb(sender, V2CommandVerbV2.PLACE_CONFIRM),
                allowsV2Verb(sender, V2CommandVerbV2.PLACE_EXECUTE),
                allowsV2Verb(sender, V2CommandVerbV2.STATUS),
                allowsV2Verb(sender, V2CommandVerbV2.UNDO_PLAN),
                allowsV2Verb(sender, V2CommandVerbV2.RECOVER_DIAGNOSE),
                placementIds,
                releaseDirectories,
                allowedWorlds,
                coordinates);
    }

    private void refreshCompletionCaches() {
        long now = System.nanoTime();
        long current = nextCompletionRefresh.get();
        if (current > now || !nextCompletionRefresh.compareAndSet(current, now + TimeUnit.SECONDS.toNanos(10))) {
            return;
        }
        // IDs are populated from plan/status responses. Filesystem discovery stays off the Paper
        // main thread and is intentionally not required for correct command execution.
    }

    private void reportSelection(CommandSender sender, Player player) {
        selections.selection(player).whenComplete((bounds, failure) -> dispatcher.run(() -> {
            if (failure != null) {
                Throwable actual = unwrap(failure);
                if (actual instanceof IncompleteSelectionException) {
                    sendWarning(sender, "WorldEditの範囲がまだ選択されていません。");
                    sender.sendMessage(Component.text("  //wand で選択ツールを取得し、左クリックと右クリックで2点を選んでください。",
                            NamedTextColor.WHITE));
                } else {
                    sendFailure(sender, actual);
                }
                return;
            }
            sender.sendMessage(prefix("✔", NamedTextColor.GREEN)
                    .append(Component.text("WorldEdit選択範囲", NamedTextColor.GREEN)));
            sender.sendMessage(detail("ワールド", bounds.worldName()));
            sender.sendMessage(detail("最小座標", coordinate(
                    bounds.minimumX(), bounds.minimumY(), bounds.minimumZ())));
            sender.sendMessage(detail("最大座標", coordinate(
                    bounds.maximumX(), bounds.maximumY(), bounds.maximumZ())));
            sender.sendMessage(detail("サイズ", String.format(
                    Locale.ROOT, "%d × %d × %d",
                    bounds.maximumX() - bounds.minimumX() + 1,
                    bounds.maximumY() - bounds.minimumY() + 1,
                    bounds.maximumZ() - bounds.minimumZ() + 1
            )));
        }));
    }

    private void reportVersion(CommandSender sender) {
        sender.sendMessage(prefix("◆", NamedTextColor.AQUA)
                .append(Component.text("LandformCraft version", NamedTextColor.AQUA)));
        sender.sendMessage(detail("Plugin", pluginVersion));
        sender.sendMessage(detail("Generator", DiagnosticBlueprintCompilerV2.GENERATOR_VERSION));
        sender.sendMessage(detail("Schema", "2"));
        sender.sendMessage(detail("Minecraft", "1.21.11"));
        sender.sendMessage(detail("Paper", Bukkit.getVersion()));
        sender.sendMessage(detail("WorldEdit / FAWE", worldEditStatus));
    }

    private void reportDoctor(CommandSender sender) {
        sendPending(sender, "read-only診断を実行しています…");
        report(sender, executors.supplyIo(() -> {
            try {
                return new DoctorService().inspect(dataRoot, "Paper " + Bukkit.getVersion());
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }), value -> {
            sender.sendMessage(detail("Java", value.javaVersion()));
            sender.sendMessage(detail("Paper", value.runtime()));
            sender.sendMessage(detail("WorldEdit / FAWE", worldEditStatus));
            sender.sendMessage(detail("Release 2 Undo path",
                    release2Placements != null && release2Placements.isRelease2Path()
                            ? "available (/lfc undo plan|execute; explicit root: /lfc v2 undo …)"
                            : "not injected"));
            sender.sendMessage(detail("Release 2 application path",
                    release2Placements != null && release2Placements.isRelease2Path()
                            ? "available (default /lfc place lifecycle; explicit root: /lfc v2 place …)"
                            : "not injected"));
            sender.sendMessage(detail("Release 2 Recovery path",
                    release2Placements != null && release2Placements.isRelease2Path()
                            ? "available (/lfc recover diagnose|plan|execute)"
                            : "not injected"));
            sender.sendMessage(detail("Release 2 ops path",
                    release2Operations != null && release2Operations.isRelease2Path()
                            ? "available (OperationalOperationsServiceV2)"
                            : "not injected"));
            sender.sendMessage(detail("data directory", value.dataDirectory()));
            sender.sendMessage(detail("writable / atomic move", value.writable() + " / " + value.atomicMove()));
            sender.sendMessage(detail("disk usable bytes", Long.toString(value.usableBytes())));
            sender.sendMessage(detail("OpenAI key present", Boolean.toString(value.openAiKeyPresent())));
            sender.sendMessage(detail("Anthropic key present", Boolean.toString(value.anthropicKeyPresent())));
            sender.sendMessage(detail("running jobs", Integer.toString(value.runningJobs())));
            sender.sendMessage(detail("RECOVERY_REQUIRED", Integer.toString(value.recoveryRequiredPlacements())));
            value.warnings().forEach(warning -> sendWarning(sender, warning));
        });
    }

    private void handleAsset(CommandSender sender, String[] args) {
        boolean mutating = args[1].equalsIgnoreCase("import") || args[1].equalsIgnoreCase("remove");
        requirePermission(sender, mutating ? "landformcraft.asset.manage" : "landformcraft.asset.read");
        report(sender, executors.supplyIo(() -> {
            try {
                return switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "list" -> assets.list();
                    case "info" -> {
                        requireLength(args, 3, "asset info <asset-id>");
                        yield assets.info(args[2]);
                    }
                    case "validate", "import" -> {
                        requireLength(args, 4, "asset " + args[1] + " <schematic> <metadata>");
                        yield args[1].equalsIgnoreCase("validate")
                                ? assets.validate(args[2], args[3]) : assets.importAsset(args[2], args[3]);
                    }
                    case "remove" -> {
                        requireLength(args, 3, "asset remove <asset-id>");
                        assets.remove(args[2]);
                        yield "removed: " + args[2];
                    }
                    default -> throw new IllegalArgumentException("未知のasset subcommandです。");
                };
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }), value -> sender.sendMessage(detail("Asset", value.toString())));
    }

    private void handleOps(CommandSender sender, String[] args) {
        requirePermission(sender, "landformcraft.doctor");
        if (release2Operations == null || !release2Operations.isRelease2Path()) {
            throw new IllegalStateException("Release 2 operational services are not injected");
        }
        String operation = args[1].toLowerCase(Locale.ROOT);
        if (operation.equals("metrics")) {
            report(sender, executors.supplyIo(() -> {
                try {
                    return release2Operations.captureMetrics(
                            OperationalMetricsCollectorV2.PlacementStageCountsV2.zeros(),
                            0L, 0L, 0, actorOf(sender));
                } catch (java.io.IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            }), snapshot -> {
                sender.sendMessage(detail("metrics contract", snapshot.contractVersion()));
                sender.sendMessage(detail("samples", Integer.toString(snapshot.samples().size())));
                sender.sendMessage(detail("checksum", snapshot.canonicalChecksum()));
            });
            return;
        }
        if (operation.equals("diagnose")) {
            requireLength(args, 3, "ops diagnose <correlation-id>");
            UUID correlationId = uuid(args[2]);
            report(sender, executors.supplyIo(() -> {
                try {
                    boolean openai = System.getenv("OPENAI_API_KEY") != null
                            && !System.getenv("OPENAI_API_KEY").isBlank();
                    boolean anthropic = System.getenv("ANTHROPIC_API_KEY") != null
                            && !System.getenv("ANTHROPIC_API_KEY").isBlank();
                    return release2Operations.diagnose(
                            correlationId, openai, anthropic, actorOf(sender));
                } catch (java.io.IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            }), report -> {
                sender.sendMessage(detail("correlation", report.correlationId().toString()));
                sender.sendMessage(detail("operation / stage", report.operation() + " / " + report.stage()));
                sender.sendMessage(detail("suggested", report.suggestedAction()));
                report.findings().forEach(finding -> sender.sendMessage(detail("finding", finding)));
            });
            return;
        }
        throw new IllegalArgumentException("opsはmetrics、diagnoseです。");
    }

    /**
     * Official v2 command path (ADR 0035 D5, V2-12-03).
     * Routing, arity, and permission nodes are shared with the CLI through {@link V2CommandRouterV2}.
     */
    private void handleV2(CommandSender sender, String[] args) {
        V2CommandRouteV2 route = V2CommandRouterV2.route(args, V2CommandVerbV2.Surface.PAPER);
        if (!route.accepted()) {
            throw new IllegalArgumentException(route.errorCode().orElseThrow().name() + ": "
                    + route.message() + " [v2CorrelationId=" + route.correlationId() + "]");
        }
        V2CommandVerbV2 verb = route.requireVerb();
        requireV2Permission(sender, verb, route);
        String[] tokens = route.arguments().toArray(String[]::new);
        if (verb.placementOperation() != null) {
            handleRelease2(sender, placementArguments(verb, tokens));
            return;
        }
        if (v2Workflow == null) {
            throw new IllegalStateException(V2CommandErrorCodeV2.V2_UNAVAILABLE.name()
                    + ": the offline v2 workflow is not available on this runtime [v2CorrelationId="
                    + route.correlationId() + "]");
        }
        switch (verb) {
            case REQUEST_VALIDATE, REQUEST_INFO -> report(sender, v2Workflow.inspectRequest(tokens[3]),
                    inspected -> reportMap(sender, route, inspected));
            case DESIGN -> report(sender, v2Workflow.design(tokens[2], tokens[3], tokens[4]), artifacts -> {
                sender.sendMessage(detail("requestId", artifacts.audit().requestId()));
                sender.sendMessage(detail("provider", artifacts.audit().providerId()));
                sender.sendMessage(detail("intent checksum", artifacts.audit().intentChecksum()));
                sender.sendMessage(detail("v2CorrelationId", route.correlationId()));
            });
            case GENERATE, EXPORT -> report(sender, v2Workflow.export(
                    tokens[2], tokens[3], tokens[5],
                    V2WorkflowServiceV2.baseline(tokens[6], tokens[7], tokens[8]),
                    verb == V2CommandVerbV2.EXPORT),
                    result -> reportMap(sender, route, V2WorkflowServiceV2.summarize(result)));
            case PREVIEW -> report(sender, v2Workflow.inspectPreviews(tokens[2]),
                    previews -> reportMap(sender, route, previews));
            case REQUEST_CREATE -> report(sender, v2Workflow.createRequest(tokens[3]),
                    request -> reportAuthoredRequest(sender, route, request));
            case REQUEST_BOUNDS -> report(sender, v2Workflow.setBounds(tokens[3],
                    integer(tokens[4]), integer(tokens[5]), integer(tokens[6]),
                    integer(tokens[7]), integer(tokens[8])),
                    request -> reportAuthoredRequest(sender, route, request));
            case REQUEST_CONSTRAINT_MAP -> report(sender, v2Workflow.setConstraintMap(tokens[3],
                    tokens[4], tokens[5], tokens[6], integer(tokens[7]), integer(tokens[8])),
                    request -> reportAuthoredRequest(sender, route, request));
            case REQUEST_SELECTION -> applySelectionBounds(sender, route, tokens[3]);
            case REQUEST_PROMPT -> beginV2PromptCapture(sender, route, tokens[3]);
            case EXPORT_PLAN -> report(sender, v2Workflow.planExport(tokens[3], tokens[4], tokens[6],
                    V2WorkflowServiceV2.baseline(tokens[7], tokens[8], tokens[9]), true, actorOf(sender)),
                    prepared -> {
                        sender.sendMessage(detail("Export plan ID", prepared.planId().toString()));
                        sender.sendMessage(detail("releaseId", prepared.releaseId()));
                        sender.sendMessage(detail("expires", prepared.expiresAt().toString()));
                        sender.sendMessage(confirmationDelivery(sender,
                                "v2-export-" + prepared.planId(),
                                ROOT + "v2 export create " + prepared.planId() + " "
                                        + prepared.confirmationToken()));
                        sender.sendMessage(detail("v2CorrelationId", route.correlationId()));
                    });
            case EXPORT_CREATE -> report(sender,
                    v2Workflow.createExport(tokens[3], tokens[4], actorOf(sender)), snapshot -> {
                        reportJobSnapshot(sender, route, snapshot);
                        discardConsoleConfirmation("v2-export-" + tokens[3]);
                    });
            case JOB_STATUS -> report(sender, v2Workflow.jobStatus(tokens[3]),
                    snapshot -> reportJobSnapshot(sender, route, snapshot));
            case JOB_CANCEL -> report(sender, v2Workflow.cancelJob(tokens[3]),
                    snapshot -> reportJobSnapshot(sender, route, snapshot));
            case JOB_LIST -> report(sender, v2Workflow.listJobs(), snapshots -> {
                snapshots.forEach(snapshot -> sender.sendMessage(
                        detail("Job", snapshot.jobId() + " " + snapshot.state().name())));
                sender.sendMessage(detail("v2CorrelationId", route.correlationId()));
            });
            case CANDIDATE_LIST -> report(sender, v2Workflow.candidates(tokens[3]), snapshots -> {
                snapshots.forEach(snapshot -> sender.sendMessage(
                        detail("Candidate", snapshot.jobId() + " -> " + snapshot.releaseId())));
                sender.sendMessage(detail("v2CorrelationId", route.correlationId()));
            });
            case CANDIDATE_INFO -> report(sender, v2Workflow.jobStatus(tokens[3]),
                    snapshot -> reportJobSnapshot(sender, route, snapshot));
            case RETENTION_PLAN, RETENTION_EXECUTE, RETENTION_STATUS -> handleV2Retention(sender, route, verb, tokens);
            case REQUEST_LIST -> report(sender, v2Workflow.listRequests(), ids -> {
                ids.forEach(id -> sender.sendMessage(detail("Request", id)));
                sender.sendMessage(detail("v2CorrelationId", route.correlationId()));
            });
            default -> throw new IllegalStateException("unroutable v2 verb: " + verb);
        }
    }

    /**
     * Reports one authored v2 request (V2-12-08), including the workspace-relative argument the
     * operator hands to {@code /lfc v2 design} and {@code /lfc v2 export}, so authoring and
     * generation connect without anyone typing a filesystem path.
     */
    private void reportAuthoredRequest(
            CommandSender sender, V2CommandRouteV2 route, GenerationRequestV2 request
    ) {
        sender.sendMessage(detail("requestId", request.requestId()));
        sender.sendMessage(detail("bounds", request.bounds().width() + "x" + request.bounds().length()
                + " y" + request.bounds().minY() + ".." + request.bounds().maxY()
                + " water=" + request.bounds().waterLevel()));
        sender.sendMessage(detail("constraint maps", Integer.toString(request.constraintMaps().size())));
        sender.sendMessage(detail("argument", v2Workflow.requestArgument(request.requestId())));
        sender.sendMessage(detail("v2CorrelationId", route.correlationId()));
    }

    /**
     * Takes the request's horizontal and vertical extent from the operator's current WorldEdit
     * selection. The stored water level is preserved (clamped into the new range), matching the v1
     * {@code request bounds selection} behaviour.
     */
    private void applySelectionBounds(CommandSender sender, V2CommandRouteV2 route, String requestId) {
        requirePlacementAvailable();
        requirePlayer(sender, player -> selections.selection(player)
                .thenCompose(bounds -> v2Workflow.setBoundsFromSelection(
                        requestId,
                        bounds.maximumX() - bounds.minimumX() + 1,
                        bounds.maximumZ() - bounds.minimumZ() + 1,
                        bounds.minimumY(),
                        bounds.maximumY()))
                .whenComplete((value, failure) -> dispatcher.run(() -> {
                    if (failure != null) {
                        sendFailure(sender, failure);
                    } else {
                        reportAuthoredRequest(sender, route, value);
                    }
                })));
    }

    /**
     * Arms the chat capture that stores the operator's next message as the v2 prompt. The session
     * expires and can be cancelled exactly like the v1 form; the store additionally refuses text that
     * resembles a credential.
     */
    private void beginV2PromptCapture(CommandSender sender, V2CommandRouteV2 route, String requestId) {
        requirePlayer(sender, player -> {
            prompts.put(player.getUniqueId(), new PromptSession(
                    requestId, Instant.now().plus(Duration.ofMinutes(5))));
            sender.sendMessage(Component.text(
                    "次のチャット1件をv2 requestのpromptとして保存します（5分で失効、cancelで取消）。"
                            + "秘密情報は入力しないでください。", NamedTextColor.YELLOW));
            sender.sendMessage(detail("v2CorrelationId", route.correlationId()));
        });
    }

    @EventHandler
    public void onPromptChat(AsyncChatEvent event) {
        PromptSession session = prompts.remove(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        if (!Instant.now().isBefore(session.expiresAt())) {
            dispatcher.run(() -> sendWarning(event.getPlayer(), "prompt入力は期限切れです。"));
            return;
        }
        String prompt = PlainTextComponentSerializer.plainText().serialize(event.message()).strip();
        if (prompt.equalsIgnoreCase("cancel")) {
            dispatcher.run(() -> sendWarning(event.getPlayer(), "prompt入力を取り消しました。"));
            return;
        }
        v2Workflow.setPrompt(session.requestId(), prompt).whenComplete((value, failure) ->
                dispatcher.run(() -> {
                    if (failure != null) {
                        sendFailure(event.getPlayer(), failure);
                    } else {
                        sendPromptStored(event.getPlayer(), value);
                    }
                }));
    }

    /**
     * Retention snapshot cleanup (V2-12-10), the v2 equivalent of the v1 {@code cleanup} verb. The
     * placement's applied journal supplies both the plan and journal the retention service needs, so
     * cleanup deletes only retention-window snapshot state through the recovery planner. Requires the
     * Release 2 placement path; without it there is nothing to clean.
     */
    private void handleV2Retention(
            CommandSender sender, V2CommandRouteV2 route, V2CommandVerbV2 verb, String[] tokens
    ) {
        if (release2Placements == null || !release2Placements.isRelease2Path()
                || release2Operations == null || !release2Operations.isRelease2Path()) {
            throw new IllegalStateException(V2CommandErrorCodeV2.V2_UNAVAILABLE.name()
                    + ": Release 2 retention is unavailable on this runtime [v2CorrelationId="
                    + route.correlationId() + "]");
        }
        UUID placementId = uuid(tokens[3]);
        switch (verb) {
            case RETENTION_STATUS -> report(sender, release2Placements.status(placementId), journal -> {
                sender.sendMessage(detail("placementId", journal.plan().placementId().toString()));
                sender.sendMessage(detail("state", journal.state().name()));
                sender.sendMessage(detail("snapshot bytes", Long.toString(journal.snapshotBytesUsed())));
                sender.sendMessage(detail("v2CorrelationId", route.correlationId()));
            });
            case RETENTION_PLAN -> report(sender, release2Placements.status(placementId)
                    .thenApply(journal -> {
                        try {
                            return release2Operations.planRetention(
                                    journal.plan(), journal, actorV2(sender), () -> false);
                        } catch (java.io.IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    }), prepared -> {
                        sender.sendMessage(detail("Retention plan ID", prepared.plan().planId().toString()));
                        sender.sendMessage(detail("reclaimable bytes",
                                Long.toString(prepared.recoveryPlan().totalBytes())));
                        String command = ROOT + "v2 retention execute " + placementId + " "
                                + prepared.plan().planId() + " " + prepared.confirmationToken();
                        sender.sendMessage(confirmationDelivery(sender,
                                "v2-retention-" + prepared.plan().planId(), command));
                        sender.sendMessage(detail("v2CorrelationId", route.correlationId()));
                    });
            case RETENTION_EXECUTE -> {
                UUID planId = uuid(tokens[4]);
                String token = tokens[5];
                report(sender, release2Placements.status(placementId)
                        .thenApply(journal -> {
                            try {
                                return release2Operations.executeRetention(planId, token,
                                        actorV2(sender), journal.plan(), journal, () -> false);
                            } catch (java.io.IOException exception) {
                                throw new java.io.UncheckedIOException(exception);
                            }
                        }), executed -> {
                            sender.sendMessage(detail("Retention plan ID", executed.planId().toString()));
                            sender.sendMessage(detail("executed", Boolean.toString(executed.executed())));
                            discardConsoleConfirmation("v2-retention-" + planId);
                            sender.sendMessage(detail("v2CorrelationId", route.correlationId()));
                        });
            }
            default -> throw new IllegalStateException("not a retention verb: " + verb);
        }
    }

    /** Reports one v2 export job snapshot (V2-12-09). Never echoes a confirmation token. */
    private void reportJobSnapshot(
            CommandSender sender, V2CommandRouteV2 route, ExportJobSnapshotV2 snapshot
    ) {
        sender.sendMessage(detail("Job ID", snapshot.jobId()));
        sender.sendMessage(detail("requestId / releaseId",
                snapshot.requestId() + " / " + snapshot.releaseId()));
        sender.sendMessage(detail("kind / state", snapshot.kind().name() + " / " + snapshot.state().name()));
        sender.sendMessage(detail("progress", snapshot.progressMillionths() + "/1000000"));
        sender.sendMessage(detail("updatedAt", snapshot.updatedAt()));
        sender.sendMessage(detail("message", snapshot.message()));
        sender.sendMessage(detail("v2CorrelationId", route.correlationId()));
    }

    /** Stable actor string an export plan and its confirmation are bound to. */
    private static String actorOf(CommandSender sender) {
        if (sender instanceof Player player) {
            return "PLAYER:" + player.getUniqueId();
        }
        if (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender) {
            return "CONSOLE";
        }
        throw new IllegalArgumentException("この操作はPlayerまたはCONSOLE identityからだけ実行できます。");
    }

    /** Confirms a captured v2 prompt without echoing it back into chat. */
    private void sendPromptStored(CommandSender sender, GenerationRequestV2 request) {
        sender.sendMessage(detail("requestId", request.requestId()));
        sender.sendMessage(detail("prompt length", Integer.toString(request.prompt().length())));
    }

    private void reportMap(
            CommandSender sender, V2CommandRouteV2 route, java.util.Map<String, Object> values
    ) {
        values.forEach((key, value) -> sender.sendMessage(detail(key, String.valueOf(value))));
        sender.sendMessage(detail("v2CorrelationId", route.correlationId()));
    }

    /**
     * Rebuilds the {@code v2 <operation> ...} argument vector {@link #handleRelease2} still consumes,
     * so the placement lifecycle keeps exactly one implementation across both command spellings.
     */
    private static String[] placementArguments(V2CommandVerbV2 verb, String[] canonical) {
        int skip = verb.operation() == null ? 2 : 3;
        String[] placement = new String[canonical.length - skip + 2];
        placement[0] = V2CommandVerbV2.ROOT;
        placement[1] = verb.placementOperation();
        System.arraycopy(canonical, skip, placement, 2, canonical.length - skip);
        return placement;
    }

    /** Requires the canonical permission node introduced by ADR 0035 D4. */
    private static void requireV2Permission(
            CommandSender sender, V2CommandVerbV2 verb, V2CommandRouteV2 route
    ) {
        if (sender.hasPermission(verb.permission())) {
            return;
        }
        throw new IllegalArgumentException(V2CommandErrorCodeV2.V2_PERMISSION_DENIED.name()
                + ": 権限がありません: " + verb.permission()
                + " [v2CorrelationId=" + route.correlationId() + "]");
    }

    private void handleRelease2(CommandSender sender, String[] args) {
        Release2CommandSecurityV2.requireOperator(sender);
        if (release2Placements == null || !release2Placements.isRelease2Path()) {
            throw new IllegalStateException("Release 2 placement application is unavailable");
        }
        String operation = args[1].toLowerCase(Locale.ROOT);
        switch (operation) {
            case "plan" -> {
                requirePermission(sender, "landformcraft.v2.plan");
                requireLength(args, 7, "v2 plan <release-path> <world> <x> <y> <z>");
                report(sender, release2Placements.plan(
                        args[2], args[3], integer(args[4]), integer(args[5]), integer(args[6]),
                        actorV2(sender)), prepared -> {
                    UUID id = prepared.plan().placementId();
                    Component idLine = detail("Release 2 Placement ID", id.toString());
                    Component stateLine = detail("state", prepared.journal().state().name());
                    Component effectLine = detail("effect blocks",
                            Long.toString(prepared.envelope().unionEffectEnvelope().volumeBlocks()));
                    sender.sendMessage(idLine);
                    sender.sendMessage(stateLine);
                    sender.sendMessage(effectLine);
                    mirrorOperatorMessage(sender, idLine);
                    mirrorOperatorMessage(sender, stateLine);
                    mirrorOperatorMessage(sender, effectLine);
                    String next = ROOT + "v2 place confirm " + id + " " + prepared.confirmationToken();
                    sender.sendMessage(confirmationDelivery(sender, "v2-confirm-" + id, next));
                });
            }
            case "confirm" -> {
                requirePermission(sender, "landformcraft.v2.confirm");
                requireLength(args, 4, "v2 confirm <placement-id> <token>");
                report(sender, release2Placements.confirm(uuid(args[2]), args[3], actorV2(sender)), confirmed -> {
                    discardConsoleConfirmation("v2-confirm-" + uuid(args[2]));
                    Component stateLine = detail("state", confirmed.journal().state().name());
                    sender.sendMessage(stateLine);
                    mirrorOperatorMessage(sender, stateLine);
                    Component nextLine = Component.text(
                            ROOT + "v2 place execute " + args[2], NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.suggestCommand(ROOT + "v2 place execute " + args[2]));
                    sender.sendMessage(nextLine);
                    mirrorOperatorMessage(sender, nextLine);
                });
            }
            case "execute" -> {
                requirePermission(sender, "landformcraft.v2.execute");
                requireLength(args, 3, "v2 execute <placement-id>");
                report(sender, release2Placements.execute(uuid(args[2]), actorV2(sender)), executed -> {
                    Component stateLine = detail("state", executed.journal().state().name());
                    Component outcomeLine = detail("outcome", executed.outcome());
                    sender.sendMessage(stateLine);
                    sender.sendMessage(outcomeLine);
                    mirrorOperatorMessage(sender, stateLine);
                    mirrorOperatorMessage(sender, outcomeLine);
                    if (executed.journal().message() != null && !executed.journal().message().isBlank()) {
                        Component messageLine = detail("message", executed.journal().message());
                        sender.sendMessage(messageLine);
                        mirrorOperatorMessage(sender, messageLine);
                    }
                });
            }
            case "status" -> {
                requirePermission(sender, "landformcraft.v2.status");
                requireLength(args, 3, "v2 status <placement-id>");
                report(sender, release2Placements.status(uuid(args[2])), journal -> {
                    Component idLine = detail("Release 2 Placement ID",
                            journal.plan().placementId().toString());
                    Component stateLine = detail("state", journal.state().name());
                    Component messageLine = detail("message", journal.message());
                    sender.sendMessage(idLine);
                    sender.sendMessage(stateLine);
                    sender.sendMessage(messageLine);
                    mirrorOperatorMessage(sender, idLine);
                    mirrorOperatorMessage(sender, stateLine);
                    mirrorOperatorMessage(sender, messageLine);
                });
            }
            case "undo-plan" -> {
                requirePermission(sender, "landformcraft.v2.undo");
                requireLength(args, 3, "v2 undo-plan <placement-id>");
                report(sender, release2Placements.prepareUndo(uuid(args[2]), actorV2(sender)), prepared -> {
                    String next = ROOT + "v2 undo execute " + args[2] + " " + prepared.plaintextToken();
                    Component stateLine = detail("state", prepared.preparedJournal().state().name());
                    sender.sendMessage(stateLine);
                    mirrorOperatorMessage(sender, stateLine);
                    sender.sendMessage(confirmationDelivery(sender, "v2-undo-" + uuid(args[2]), next));
                });
            }
            case "undo-execute" -> {
                requirePermission(sender, "landformcraft.v2.undo");
                requireLength(args, 4, "v2 undo-execute <placement-id> <token>");
                report(sender, release2Placements.executeUndo(
                        uuid(args[2]), args[3], actorV2(sender)), undone -> {
                    discardConsoleConfirmation("v2-undo-" + uuid(args[2]));
                    Component stateLine = detail("state", undone.undoneJournal().state().name());
                    sender.sendMessage(stateLine);
                    mirrorOperatorMessage(sender, stateLine);
                });
            }
            case "recover-diagnose" -> {
                requirePermission(sender, "landformcraft.v2.recovery");
                requireLength(args, 3, "v2 recover-diagnose <placement-id>");
                report(sender, release2Placements.diagnoseRecovery(uuid(args[2])), diagnosis -> {
                    sender.sendMessage(detail("classification", diagnosis.classification().name()));
                    diagnosis.findings().forEach(finding -> sender.sendMessage(detail("finding", finding)));
                });
            }
            case "recover-plan" -> {
                requirePermission(sender, "landformcraft.v2.recovery");
                requireLength(args, 4, "v2 recover-plan <rollback|accept> <placement-id>");
                var action = switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "rollback" -> com.github.nankotsu029.landformcraft.model.v2.placement
                            .PlacementRecoveryActionV2.ROLLBACK;
                    case "accept" -> com.github.nankotsu029.landformcraft.model.v2.placement
                            .PlacementRecoveryActionV2.ACCEPT;
                    default -> throw new IllegalArgumentException("recovery actionはrollbackまたはacceptです。");
                };
                report(sender, release2Placements.prepareRecovery(
                        uuid(args[3]), action, actorV2(sender)), prepared -> {
                    String next = ROOT + "v2 recover execute " + args[2].toLowerCase(Locale.ROOT)
                            + " " + args[3] + " " + prepared.plaintextToken();
                    sender.sendMessage(detail("classification",
                            prepared.recoveryPlan().classification().name()));
                    sender.sendMessage(confirmationDelivery(sender,
                            "v2-recover-" + args[2].toLowerCase(Locale.ROOT) + "-" + uuid(args[3]),
                            next));
                });
            }
            case "recover-execute" -> {
                requirePermission(sender, "landformcraft.v2.recovery");
                requireLength(args, 5,
                        "v2 recover-execute <rollback|accept> <placement-id> <token>");
                if (args[2].equalsIgnoreCase("rollback")) {
                    report(sender, release2Placements.executeRecoveryRollback(
                            uuid(args[3]), args[4], actorV2(sender)), rolledBack -> {
                        discardConsoleConfirmation("v2-recover-rollback-" + uuid(args[3]));
                        sender.sendMessage(detail("state",
                                rolledBack.rolledBackJournal().state().name()));
                    });
                } else if (args[2].equalsIgnoreCase("accept")) {
                    report(sender, release2Placements.executeRecoveryAccept(
                            uuid(args[3]), args[4], actorV2(sender)), accepted -> {
                        discardConsoleConfirmation("v2-recover-accept-" + uuid(args[3]));
                        sender.sendMessage(detail("state",
                                accepted.acceptedJournal().state().name()));
                    });
                } else {
                    throw new IllegalArgumentException("recovery actionはrollbackまたはacceptです。");
                }
            }
            default -> throw new IllegalArgumentException(
                    "v2はplan、confirm、execute、status、undo-plan、undo-execute、"
                            + "recover-diagnose、recover-plan、recover-executeです。");
        }
    }

    private static com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2.PlacementActorV2
            actorV2(CommandSender sender) {
        return Release2CommandSecurityV2.actor(sender);
    }

    private <T> void report(CommandSender sender, CompletionStage<T> stage, java.util.function.Consumer<T> success) {
        sendPending(sender, "処理中…");
        stage.whenComplete((value, failure) -> dispatcher.run(() -> {
            if (failure != null) {
                sendFailure(sender, failure);
                mirrorOperatorText(sender, unwrap(failure).getMessage());
            } else {
                success.accept(value);
            }
        }));
    }

    /**
     * Sends the one-time confirmation command to the operator. Players receive the clickable
     * chat component (player chat is not duplicated into the server log). CONSOLE／RCON output
     * is persisted into the standard Paper log, so for non-player senders the command is
     * written to an owner-only file instead and only the path is echoed; the file is discarded
     * on successful consumption via {@link #discardConsoleConfirmation(String)}.
     */
    private Component confirmationDelivery(CommandSender sender, String fileKey, String command) {
        if (sender instanceof Player) {
            return Component.text(command, NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.suggestCommand(command));
        }
        Path file = consoleConfirmations.store(fileKey, command);
        return Component.text(
                "確認コマンドをlogへ出力せず保存しました（10分有効・1回用）: " + file,
                NamedTextColor.YELLOW);
    }

    private void discardConsoleConfirmation(String fileKey) {
        try {
            consoleConfirmations.discard(fileKey);
        } catch (RuntimeException ignored) {
            // Cleanup of an already-consumed confirmation must not fail the operation.
        }
    }

    /**
     * RCON responses are dropped when the client disconnects before async completion. Mirror
     * console／RCON operator text into the server log for terminal states and identifiers.
     * Confirmation tokens are never mirrored: non-player senders receive them via
     * {@link #confirmationDelivery(CommandSender, String, String)} file delivery only.
     */
    private static void mirrorOperatorText(CommandSender sender, String text) {
        if (sender instanceof Player || text == null || text.isBlank()) {
            return;
        }
        Bukkit.getLogger().info("[LandformCraft] " + text);
    }

    private static void mirrorOperatorMessage(CommandSender sender, Component message) {
        if (sender instanceof Player) {
            return;
        }
        mirrorOperatorText(sender, PlainTextComponentSerializer.plainText().serialize(message));
    }

    private void showHelp(CommandSender sender, String label) {
        sender.sendMessage(prefix("◆", NamedTextColor.AQUA)
                .append(Component.text("コマンド一覧", NamedTextColor.AQUA)));
        helpSection(sender, "確認（version中立）");
        help(sender, "/" + label + " selection", "WorldEditの選択範囲を表示");
        help(sender, "/" + label + " version | doctor | ops … | asset …",
                "version表示／read-only診断／運用metrics／asset管理");
        helpSection(sender, "設計・生成（既定v2）");
        if (v2Workflow != null) {
            help(sender, "/" + label + " request create|bounds|selection|constraint-map|prompt|list …",
                    "v2 requestを作成・編集・列挙");
            help(sender, "/" + label + " request validate|info <request-v2.json>",
                    "v2 requestを厳密に検証");
            help(sender, "/" + label + " design <import|fixture|openai|anthropic> …",
                    "v2 Design Packageを作成");
            help(sender, "/" + label + " generate|export <request> <intent> <exports-root> <release-id> "
                    + "<land|water> <land-y> <water-y>", "Release 2を公開（exportはZIP付き）");
            help(sender, "/" + label + " export plan|create … | job … | candidate …",
                    "確認付き非同期exportとjob／candidate操作");
            help(sender, "/" + label + " preview <release>", "検証済みReleaseのpreview indexを表示");
        }
        if (release2Placements != null && release2Placements.isRelease2Path()) {
            helpSection(sender, "配置・復旧（既定v2）");
            help(sender, "/" + label + " place plan|confirm|execute …",
                    "Release 2の配置lifecycle");
            help(sender, "/" + label + " status <id> | undo plan|execute …",
                    "Release 2の状態確認とoperation-bound Undo");
            help(sender, "/" + label + " recover diagnose|plan|execute … | retention …",
                    "Release 2の保守的Recovery");
        }
        helpSection(sender, "明示root");
        help(sender, "/" + label + " v2 <verb> …", "既定v2経路の恒久的な明示形");
        if (release2Operations != null && release2Operations.isRelease2Path()) {
            sender.sendMessage(Component.text(
                    "  Release 2 opsは PaperOperationalOperationsServiceV2（metrics／diagnose）経由です。",
                    NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text("  Tabキーでサブコマンド、Release、world、IDを補完できます。",
                NamedTextColor.GRAY));
    }

    private static void helpSection(CommandSender sender, String title) {
        sender.sendMessage(Component.text("\n  " + title, NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
    }

    private static void help(CommandSender sender, String usage, String description) {
        sender.sendMessage(Component.text("  " + usage, NamedTextColor.YELLOW)
                .append(Component.text("  —  " + description, NamedTextColor.WHITE)));
    }

    private static Component prefix(String marker, NamedTextColor markerColor) {
        return Component.empty()
                .append(Component.text("[LandformCraft] ", NamedTextColor.AQUA)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text(marker + " ", markerColor));
    }

    private static Component detail(String name, String value) {
        return Component.text("  " + name + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private static String coordinate(int x, int y, int z) {
        return String.format(Locale.ROOT, "(%d, %d, %d)", x, y, z);
    }

    private static void sendPending(CommandSender sender, String text) {
        sender.sendMessage(prefix("…", NamedTextColor.YELLOW)
                .append(Component.text(text, NamedTextColor.YELLOW)));
    }

    private static void sendWarning(CommandSender sender, String text) {
        sender.sendMessage(prefix("!", NamedTextColor.YELLOW)
                .append(Component.text(text, NamedTextColor.YELLOW)));
    }

    private static void sendError(CommandSender sender, String text) {
        sender.sendMessage(prefix("✘", NamedTextColor.RED)
                .append(Component.text(text, NamedTextColor.RED)));
    }

    private static void sendFailure(CommandSender sender, Throwable throwable) {
        Throwable actual = unwrap(throwable);
        LandformException failure = actual instanceof LandformException domain ? domain
                : new LandformException(
                        actual instanceof IllegalArgumentException
                                ? LandformErrorCode.REQUEST_INVALID : LandformErrorCode.INTERNAL,
                        actual instanceof IllegalArgumentException && actual.getMessage() != null
                                ? actual.getMessage() : "処理は安全に失敗しました。",
                        "paper-command", "", "command",
                        "correlation IDを添えてサーバー管理者ログを確認してください。", actual);
        sendError(sender, failure.code().code() + ": " + failure.getMessage());
        sender.sendMessage(detail("correlation ID", failure.correlationId().toString()));
        sender.sendMessage(detail("operation / stage", failure.operation() + " / " + failure.stage()));
        sender.sendMessage(detail("suggested action", failure.suggestedAction()));
    }

    private void requirePlacementAvailable() {
        if (release2Placements == null || selections == null) {
            throw new LandformException(LandformErrorCode.CONFIG_INVALID,
                    "WorldEdit / FAWE placement integration is unavailable.", "paper-placement", "",
                    "integration", "Enable exactly one supported WorldEdit implementation and restart Paper.");
        }
    }

    private static void requireLength(String[] args, int expected, String usage) {
        if (args.length != expected) {
            throw new IllegalArgumentException("Usage: /landformcraft " + usage);
        }
    }

    private static int inclusive(int minimum, int maximum) {
        long value = (long) maximum - minimum + 1L;
        if (minimum > maximum || value < 1L || value > (long) ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING) {
            throw new IllegalArgumentException(
                    "選択範囲は各水平軸1.." + ScaleDimensionPolicyV2.MEDIUM_HORIZONTAL_CEILING + "である必要があります。");
        }
        return Math.toIntExact(value);
    }

    private static void requirePlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("このコマンドはゲーム内のプレイヤーだけが実行できます。");
        }
        action.accept(player);
    }

    private static int integer(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("座標は整数で入力してください: " + value, exception);
        }
    }

    private static void requirePermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            throw new IllegalArgumentException("権限がありません: " + permission);
        }
    }

    private static boolean allowsV2Verb(CommandSender sender, V2CommandVerbV2 verb) {
        return sender.hasPermission(verb.permission());
    }

    private boolean anyV2VerbAllowed(CommandSender sender) {
        for (V2CommandVerbV2 verb : V2CommandVerbV2.values()) {
            if (verb.surface() == V2CommandVerbV2.Surface.PAPER
                    && (release2Placements == null
                        || !Release2CommandSecurityV2.isEligibleOperator(sender))) {
                continue;
            }
            if (verb.surface() != V2CommandVerbV2.Surface.PAPER && v2Workflow == null) {
                continue;
            }
            if (allowsV2Verb(sender, verb)) return true;
        }
        return false;
    }

    /**
     * Maps a partially typed canonical {@code v2 ...} argument vector onto the placement
     * {@code v2 <operation> ...} shape so the existing argument-level suggester keeps working.
     * Returns an empty array when the tokens do not (yet) name a placement verb.
     */
    private static String[] placementCompletionArguments(String[] args) {
        if (args.length < 2) return new String[0];
        String verb = args[1].toLowerCase(Locale.ROOT);
        for (V2CommandVerbV2 candidate : V2CommandVerbV2.values()) {
            if (candidate.placementOperation() == null || !candidate.verb().equals(verb)) continue;
            int skip = candidate.operation() == null ? 2 : 3;
            if (candidate.operation() != null
                    && (args.length < 3 || !args[2].equalsIgnoreCase(candidate.operation()))) {
                continue;
            }
            if (args.length < skip) return new String[0];
            String[] placement = new String[args.length - skip + 2];
            placement[0] = V2CommandVerbV2.ROOT;
            placement[1] = candidate.placementOperation();
            System.arraycopy(args, skip, placement, 2, args.length - skip);
            return placement;
        }
        return new String[0];
    }

    private static void addIfAllowed(
            List<String> values, CommandSender sender, String permission, String candidate
    ) {
        if (sender.hasPermission(permission)) {
            values.add(candidate);
        }
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Placement IDの形式が正しくありません: " + value, exception);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException
                || current instanceof java.io.UncheckedIOException)) {
            current = current.getCause();
        }
        return current;
    }

    private static String message(Throwable failure) {
        Throwable current = unwrap(failure);
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }

    private record PromptSession(String requestId, Instant expiresAt) { }
}
