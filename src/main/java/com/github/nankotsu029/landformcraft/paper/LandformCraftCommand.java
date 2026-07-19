package com.github.nankotsu029.landformcraft.paper;

import com.github.nankotsu029.landformcraft.core.PlacementApplicationService;
import com.github.nankotsu029.landformcraft.core.PreparedPlacement;
import com.github.nankotsu029.landformcraft.core.PaperWorkflowService;
import com.github.nankotsu029.landformcraft.core.SnapshotCleanupService;
import com.github.nankotsu029.landformcraft.core.CustomAssetService;
import com.github.nankotsu029.landformcraft.core.DoctorService;
import com.github.nankotsu029.landformcraft.core.LandformException;
import com.github.nankotsu029.landformcraft.core.LandformErrorCode;
import com.github.nankotsu029.landformcraft.core.BlueprintCompiler;
import com.github.nankotsu029.landformcraft.core.v2.operations.OperationalMetricsCollectorV2;
import com.github.nankotsu029.landformcraft.cli.LandformCraftCli;
import com.github.nankotsu029.landformcraft.format.Sha256;
import com.github.nankotsu029.landformcraft.model.ActorIdentity;
import com.github.nankotsu029.landformcraft.model.ConfirmationAction;
import com.github.nankotsu029.landformcraft.model.PlacementJournal;
import com.github.nankotsu029.landformcraft.model.PlacementState;
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

/** Colored administrative commands with confirm-gated mutations and non-blocking completion caches. */
public final class LandformCraftCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String ROOT = "/landformcraft ";

    private final PlacementApplicationService placements;
    private final PaperRelease2PlacementServiceV2 release2Placements;
    private final PaperPlacementUndoServiceV2 release2Undo;
    private final PaperPlacementRecoveryServiceV2 release2Recovery;
    private final PaperOperationalOperationsServiceV2 release2Operations;
    private final PaperWorldEditSelectionService selections;
    private final PaperMainThreadDispatcher dispatcher;
    private final PaperWorkflowService workflow;
    private final SnapshotCleanupService cleanup;
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
            PlacementApplicationService placements,
            PaperWorldEditSelectionService selections,
            PaperMainThreadDispatcher dispatcher,
            PaperWorkflowService workflow,
            SnapshotCleanupService cleanup,
            CustomAssetService assets,
            Path dataRoot,
            String pluginVersion,
            String worldEditStatus
    ) {
        this(placements, null, null, null, selections, dispatcher, workflow, cleanup, assets, dataRoot,
                pluginVersion, worldEditStatus);
    }

    public LandformCraftCommand(
            PlacementApplicationService placements,
            PaperPlacementUndoServiceV2 release2Undo,
            PaperWorldEditSelectionService selections,
            PaperMainThreadDispatcher dispatcher,
            PaperWorkflowService workflow,
            SnapshotCleanupService cleanup,
            CustomAssetService assets,
            Path dataRoot,
            String pluginVersion,
            String worldEditStatus
    ) {
        this(placements, release2Undo, null, null, selections, dispatcher, workflow, cleanup, assets,
                dataRoot, pluginVersion, worldEditStatus);
    }

    public LandformCraftCommand(
            PlacementApplicationService placements,
            PaperPlacementUndoServiceV2 release2Undo,
            PaperPlacementRecoveryServiceV2 release2Recovery,
            PaperWorldEditSelectionService selections,
            PaperMainThreadDispatcher dispatcher,
            PaperWorkflowService workflow,
            SnapshotCleanupService cleanup,
            CustomAssetService assets,
            Path dataRoot,
            String pluginVersion,
            String worldEditStatus
    ) {
        this(placements, release2Undo, release2Recovery, null, selections, dispatcher, workflow, cleanup,
                assets, dataRoot, pluginVersion, worldEditStatus);
    }

    public LandformCraftCommand(
            PlacementApplicationService placements,
            PaperPlacementUndoServiceV2 release2Undo,
            PaperPlacementRecoveryServiceV2 release2Recovery,
            PaperOperationalOperationsServiceV2 release2Operations,
            PaperWorldEditSelectionService selections,
            PaperMainThreadDispatcher dispatcher,
            PaperWorkflowService workflow,
            SnapshotCleanupService cleanup,
            CustomAssetService assets,
            Path dataRoot,
            String pluginVersion,
            String worldEditStatus
    ) {
        this(placements, null, release2Undo, release2Recovery, release2Operations, selections,
                dispatcher, workflow, cleanup, assets, dataRoot, pluginVersion, worldEditStatus);
    }

    public LandformCraftCommand(
            PlacementApplicationService placements,
            PaperRelease2PlacementServiceV2 release2Placements,
            PaperPlacementUndoServiceV2 release2Undo,
            PaperPlacementRecoveryServiceV2 release2Recovery,
            PaperOperationalOperationsServiceV2 release2Operations,
            PaperWorldEditSelectionService selections,
            PaperMainThreadDispatcher dispatcher,
            PaperWorkflowService workflow,
            SnapshotCleanupService cleanup,
            CustomAssetService assets,
            Path dataRoot,
            String pluginVersion,
            String worldEditStatus
    ) {
        this.placements = placements;
        this.release2Placements = release2Placements;
        this.release2Undo = release2Undo;
        this.release2Recovery = release2Recovery;
        this.release2Operations = release2Operations;
        this.selections = selections;
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.workflow = Objects.requireNonNull(workflow, "workflow");
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
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
            if (args.length >= 2 && args[0].equalsIgnoreCase("r2")) {
                handleRelease2(sender, args);
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("request")) {
                handleRequest(sender, args);
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("design")) {
                handleDesign(sender, args);
                return true;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("generate")) {
                requirePermission(sender, "landformcraft.generate");
                UUID jobId = workflow.startGenerate(uuid(args[1]));
                sender.sendMessage(detail("Job ID", jobId.toString()));
                return true;
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("job")) {
                handleJob(sender, args);
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("candidate")) {
                handleCandidate(sender, args);
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("export")) {
                handleExport(sender, args);
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("asset")) {
                handleAsset(sender, args);
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("cleanup")) {
                handleCleanup(sender, args);
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("selection")) {
                requirePermission(sender, "landformcraft.selection");
                requirePlacementAvailable();
                requirePlayer(sender, player -> reportSelection(sender, player));
                return true;
            }
            if (args.length == 7 && args[0].equalsIgnoreCase("apply")
                    && args[1].equalsIgnoreCase("plan")) {
                requirePermission(sender, "landformcraft.apply.plan");
                requirePlacementAvailable();
                reportPrepared(sender, placements.plan(
                        args[2], args[3], integer(args[4]), integer(args[5]), integer(args[6]), actor(sender)
                ), "apply execute");
                return true;
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("apply")
                    && args[1].equalsIgnoreCase("execute")) {
                requirePermission(sender, "landformcraft.apply.execute");
                requirePlacementAvailable();
                reportJournal(sender, placements.execute(uuid(args[2]), args[3], actor(sender)),
                        () -> discardConsoleConfirmation("apply-execute-" + uuid(args[2])));
                return true;
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("apply")
                    && args[1].equalsIgnoreCase("status")) {
                requirePermission(sender, "landformcraft.apply.status");
                requirePlacementAvailable();
                reportJournal(sender, placements.status(uuid(args[2])));
                return true;
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("apply")
                    && args[1].equalsIgnoreCase("undo")) {
                requirePermission(sender, "landformcraft.undo.plan");
                requirePlacementAvailable();
                reportPrepared(sender, placements.prepareUndo(uuid(args[2]), actor(sender)), "undo execute");
                return true;
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("undo")
                    && args[1].equalsIgnoreCase("execute")) {
                requirePermission(sender, "landformcraft.undo.execute");
                requirePlacementAvailable();
                reportJournal(sender, placements.undo(uuid(args[2]), args[3], actor(sender)),
                        () -> discardConsoleConfirmation("undo-execute-" + uuid(args[2])));
                return true;
            }
            if (args.length >= 4 && args[0].equalsIgnoreCase("apply")
                    && args[1].equalsIgnoreCase("recover")) {
                handleRecovery(sender, args);
                return true;
            }
        } catch (IllegalArgumentException | LandformException exception) {
            sendFailure(sender, exception);
            return true;
        }
        sendError(sender, "コマンドの形式が正しくありません。");
        showHelp(sender, label);
        return true;
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
            List<String> roots = new java.util.ArrayList<>();
            addIfAllowed(roots, sender, "landformcraft.help", "help");
            addIfAllowed(roots, sender, "landformcraft.version", "version");
            addIfAllowed(roots, sender, "landformcraft.doctor", "doctor");
            addIfAllowed(roots, sender, "landformcraft.doctor", "ops");
            if (release2Placements != null && Release2CommandSecurityV2.isEligibleOperator(sender)
                    && (sender.hasPermission("landformcraft.r2.plan")
                    || sender.hasPermission("landformcraft.r2.confirm")
                    || sender.hasPermission("landformcraft.r2.execute")
                    || sender.hasPermission("landformcraft.r2.status")
                    || sender.hasPermission("landformcraft.r2.undo")
                    || sender.hasPermission("landformcraft.r2.recovery"))) {
                roots.add("r2");
            }
            addIfAllowed(roots, sender, "landformcraft.request.list", "request");
            addIfAllowed(roots, sender, "landformcraft.design.verify", "design");
            addIfAllowed(roots, sender, "landformcraft.generate", "generate");
            addIfAllowed(roots, sender, "landformcraft.job.status", "job");
            addIfAllowed(roots, sender, "landformcraft.candidate.read", "candidate");
            addIfAllowed(roots, sender, "landformcraft.export.verify", "export");
            addIfAllowed(roots, sender, "landformcraft.asset.read", "asset");
            addIfAllowed(roots, sender, "landformcraft.cleanup", "cleanup");
            addIfAllowed(roots, sender, "landformcraft.selection", "selection");
            if (sender.hasPermission("landformcraft.apply.plan")
                    || sender.hasPermission("landformcraft.apply.status")
                    || sender.hasPermission("landformcraft.recovery")) {
                roots.add("apply");
            }
            if (sender.hasPermission("landformcraft.undo.execute")) {
                roots.add("undo");
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return roots.stream().filter(value -> value.startsWith(prefix)).distinct().sorted().toList();
        }
        Collection<String> worlds = Bukkit.getWorlds().stream().map(world -> world.getName()).toList();
        List<String> coordinates = sender instanceof Player player
                ? List.of(
                        Integer.toString(player.getLocation().getBlockX()),
                        Integer.toString(player.getLocation().getBlockY()),
                        Integer.toString(player.getLocation().getBlockZ())
                ) : List.of("0", "0", "0");
        if (args[0].equalsIgnoreCase("r2")) {
            if (release2Placements == null || !Release2CommandSecurityV2.isEligibleOperator(sender)) {
                return List.of();
            }
            List<String> allowedWorlds = worlds.stream()
                    .filter(release2Placements::allowsWorld)
                    .toList();
            return LandformCraftSuggestions.completeRelease2(
                    args,
                    sender.hasPermission("landformcraft.r2.plan"),
                    sender.hasPermission("landformcraft.r2.confirm"),
                    sender.hasPermission("landformcraft.r2.execute"),
                    sender.hasPermission("landformcraft.r2.status"),
                    sender.hasPermission("landformcraft.r2.undo"),
                    sender.hasPermission("landformcraft.r2.recovery"),
                    placementIds,
                    releaseDirectories,
                    allowedWorlds,
                    coordinates);
        }
        return LandformCraftSuggestions.complete(
                args,
                sender.hasPermission("landformcraft.selection"),
                sender.hasPermission("landformcraft.apply.plan")
                        || sender.hasPermission("landformcraft.apply.execute"),
                sender.hasPermission("landformcraft.undo.plan")
                        || sender.hasPermission("landformcraft.undo.execute"),
                placementIds,
                releaseDirectories,
                worlds,
                coordinates
        );
    }

    private void refreshCompletionCaches() {
        long now = System.nanoTime();
        long current = nextCompletionRefresh.get();
        if (current > now || !nextCompletionRefresh.compareAndSet(current, now + TimeUnit.SECONDS.toNanos(10))) {
            return;
        }
        if (placements != null) {
            placements.placementIds()
                    .thenAccept(ids -> ids.forEach(id -> placementIds.add(id.toString())))
                    .exceptionally(failure -> null);
            placements.releaseDirectories()
                    .thenAccept(directories -> releaseDirectories = directories)
                    .exceptionally(failure -> null);
        }
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
        sender.sendMessage(detail("Generator", BlueprintCompiler.GENERATOR_VERSION));
        sender.sendMessage(detail("Schema", "1"));
        sender.sendMessage(detail("Minecraft", "1.21.11"));
        sender.sendMessage(detail("Paper", Bukkit.getVersion()));
        sender.sendMessage(detail("WorldEdit / FAWE", worldEditStatus));
    }

    private void reportDoctor(CommandSender sender) {
        sendPending(sender, "read-only診断を実行しています…");
        report(sender, workflow.io(() -> {
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
                            ? "available (/lfc r2 undo-plan|undo-execute)"
                            : release2Undo != null && release2Undo.isRelease2Path()
                            ? "available (PlacementUndoApplicationServiceV2)"
                            : "v1 only (Release 2 service not injected)"));
            sender.sendMessage(detail("Release 2 application path",
                    release2Placements != null && release2Placements.isRelease2Path()
                            ? "available (explicit /lfc r2 lifecycle)"
                            : "not injected"));
            sender.sendMessage(detail("Release 2 Recovery path",
                    release2Placements != null && release2Placements.isRelease2Path()
                            ? "available (/lfc r2 recover-diagnose|recover-plan|recover-execute)"
                            : release2Recovery != null && release2Recovery.isRelease2Path()
                            ? "available (PlacementRecoveryApplicationServiceV2)"
                            : "v1 only (Release 2 service not injected)"));
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

    private void handleRequest(CommandSender sender, String[] args) {
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                requirePermission(sender, "landformcraft.request.create");
                requireLength(args, 3, "request create <request-id>");
                reportRequest(sender, workflow.createRequest(args[2]));
            }
            case "bounds" -> {
                requirePermission(sender, "landformcraft.request.edit");
                if (args.length == 4 && args[2].equalsIgnoreCase("selection")) {
                    requirePlacementAvailable();
                    requirePlayer(sender, player -> selections.selection(player).thenCompose(bounds ->
                            workflow.requestInfo(args[3]).thenCompose(current -> workflow.setBounds(
                                    args[3], inclusive(bounds.minimumX(), bounds.maximumX()),
                                    inclusive(bounds.minimumZ(), bounds.maximumZ()),
                                    bounds.minimumY(), bounds.maximumY(),
                                    Math.max(bounds.minimumY(), Math.min(bounds.maximumY(),
                                            current.bounds().waterLevel()))
                            ))).whenComplete((value, failure) -> dispatcher.run(() -> {
                                if (failure != null) {
                                    sendFailure(sender, failure);
                                } else {
                                    sendRequest(sender, value);
                                }
                            })));
                } else {
                    requireLength(args, 9,
                            "request bounds <id> <minX> <minY> <minZ> <maxX> <maxY> <maxZ>");
                    int minX = integer(args[3]);
                    int minY = integer(args[4]);
                    int minZ = integer(args[5]);
                    int maxX = integer(args[6]);
                    int maxY = integer(args[7]);
                    int maxZ = integer(args[8]);
                    reportRequest(sender, workflow.requestInfo(args[2]).thenCompose(current ->
                            workflow.setBounds(args[2], inclusive(minX, maxX), inclusive(minZ, maxZ),
                                    minY, maxY, Math.max(minY, Math.min(maxY, current.bounds().waterLevel())))));
                }
            }
            case "prompt" -> {
                requirePermission(sender, "landformcraft.request.edit");
                requireLength(args, 3, "request prompt <request-id>");
                requirePlayer(sender, player -> {
                    prompts.put(player.getUniqueId(), new PromptSession(args[2], Instant.now().plus(Duration.ofMinutes(5))));
                    sender.sendMessage(Component.text(
                            "次のチャット1件をpromptとして保存します（5分で失効、cancelで取消）。秘密情報は入力しないでください。",
                            NamedTextColor.YELLOW));
                });
            }
            case "validate", "info" -> {
                requirePermission(sender, "landformcraft.request.validate");
                requireLength(args, 3, "request " + args[1] + " <request-id>");
                reportRequest(sender, workflow.requestInfo(args[2]));
            }
            case "list" -> {
                requirePermission(sender, "landformcraft.request.list");
                requireLength(args, 2, "request list");
                report(sender, workflow.requestList(), values -> values.forEach(value ->
                        sender.sendMessage(detail("Request", value.requestId()))));
            }
            default -> throw new IllegalArgumentException("未知のrequest subcommandです: " + args[1]);
        }
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
        workflow.setPrompt(session.requestId(), prompt).whenComplete((value, failure) ->
                dispatcher.run(() -> {
                    if (failure != null) {
                        sendFailure(event.getPlayer(), failure);
                    } else {
                        sendRequest(event.getPlayer(), value);
                    }
                }));
    }

    private void handleDesign(CommandSender sender, String[] args) {
        String operation = args[1].toLowerCase(Locale.ROOT);
        if (operation.equals("create")) {
            requirePermission(sender, "landformcraft.design.create");
            requireLength(args, 5, "design create <request-id> <openai|anthropic> <model-id>");
            UUID id = workflow.startDesign(args[2], args[3].toLowerCase(Locale.ROOT), args[4]);
            sender.sendMessage(detail("Job ID", id.toString()));
        } else if (operation.equals("import") || operation.equals("fixture")) {
            requirePermission(sender, "landformcraft.design.import");
            requireLength(args, 4, "design " + operation + " <request-id> <relative-json-path>");
            UUID id = workflow.startDesign(args[2], operation, args[3]);
            sender.sendMessage(detail("Job ID", id.toString()));
        } else if (operation.equals("status")) {
            requirePermission(sender, "landformcraft.job.status");
            requireLength(args, 3, "design status <job-id>");
            reportJob(sender, workflow.jobStatus(uuid(args[2])));
        } else if (operation.equals("info") || operation.equals("verify")) {
            requirePermission(sender, "landformcraft.design.verify");
            requireLength(args, 3, "design " + operation + " <design-id>");
            report(sender, workflow.designInfo(uuid(args[2])), value -> {
                sender.sendMessage(detail("Design ID", value.audit().jobId().toString()));
                sender.sendMessage(detail("Request", value.audit().requestId()));
                sender.sendMessage(detail("Provider / model", value.audit().providerId() + " / "
                        + value.audit().modelId()));
                sender.sendMessage(detail("verified files", Integer.toString(value.verifiedFiles())));
            });
        } else {
            throw new IllegalArgumentException("未知のdesign subcommandです: " + operation);
        }
    }

    private void handleJob(CommandSender sender, String[] args) {
        if (args[1].equalsIgnoreCase("status")) {
            requirePermission(sender, "landformcraft.job.status");
            reportJob(sender, workflow.jobStatus(uuid(args[2])));
        } else if (args[1].equalsIgnoreCase("cancel")) {
            requirePermission(sender, "landformcraft.job.cancel");
            reportJob(sender, workflow.cancel(uuid(args[2])));
        } else {
            throw new IllegalArgumentException("jobはstatusまたはcancelです。");
        }
    }

    private void handleCandidate(CommandSender sender, String[] args) {
        requirePermission(sender, "landformcraft.candidate.read");
        if (args[1].equalsIgnoreCase("list")) {
            requireLength(args, 3, "candidate list <request-id>");
            report(sender, workflow.candidates(args[2]), values -> values.forEach(value ->
                    sender.sendMessage(detail("Candidate", value.toString()))));
            return;
        }
        requireLength(args, 3, "candidate <info|preview|validate> <candidate-id>");
        UUID id = uuid(args[2]);
        if (!(args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("preview")
                || args[1].equalsIgnoreCase("validate"))) {
            throw new IllegalArgumentException("未知のcandidate subcommandです。");
        }
        CompletionStage<List<Path>> inspection = args[1].equalsIgnoreCase("preview")
                ? workflow.candidatePreview(id)
                : args[1].equalsIgnoreCase("validate")
                ? workflow.candidateValidate(id) : workflow.candidateInfo(id);
        report(sender, inspection, files -> {
            sender.sendMessage(detail("Candidate", id.toString()));
            sender.sendMessage(detail("candidate directory",
                    dataRoot.resolve("candidates").resolve(id.toString()).toString()));
            for (Path file : files) {
                try {
                    sender.sendMessage(detail(file.getFileName().toString(), Sha256.file(file)));
                } catch (java.io.IOException exception) {
                    sendFailure(sender, exception);
                }
            }
        });
    }

    private void handleExport(CommandSender sender, String[] args) {
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "plan" -> {
                requirePermission(sender, "landformcraft.export.plan");
                requireLength(args, 3, "export plan <candidate-id>");
                var prepared = workflow.planExport(uuid(args[2]), actor(sender));
                sender.sendMessage(detail("Export plan ID", prepared.planId().toString()));
                String command = ROOT + "export create " + prepared.planId() + " " + prepared.confirmationToken();
                sender.sendMessage(confirmationDelivery(sender, "export-" + prepared.planId(), command));
            }
            case "create" -> {
                requirePermission(sender, "landformcraft.export.execute");
                requireLength(args, 4, "export create <export-plan-id> <token>");
                sender.sendMessage(detail("Job ID", workflow.startExport(uuid(args[2]), args[3], actor(sender)).toString()));
                discardConsoleConfirmation("export-" + uuid(args[2]));
            }
            case "status" -> {
                requirePermission(sender, "landformcraft.job.status");
                requireLength(args, 3, "export status <job-id>");
                reportJob(sender, workflow.jobStatus(uuid(args[2])));
            }
            case "verify", "info" -> {
                requirePermission(sender, "landformcraft.export.verify");
                requireLength(args, 3, "export " + args[1] + " <request/release-id>");
                report(sender, workflow.verifyRelease(args[2]), value -> {
                    sender.sendMessage(detail("Release request", value.manifest().requestId()));
                    sender.sendMessage(detail("verified files / tiles", value.verifiedFiles() + " / "
                            + value.verifiedTiles()));
                });
            }
            case "list" -> {
                requirePermission(sender, "landformcraft.export.verify");
                requireLength(args, 2, "export list");
                report(sender, workflow.releases(), values -> values.forEach(value ->
                        sender.sendMessage(detail("Release", value))));
            }
            default -> throw new IllegalArgumentException("未知のexport subcommandです: " + args[1]);
        }
    }

    private void handleAsset(CommandSender sender, String[] args) {
        boolean mutating = args[1].equalsIgnoreCase("import") || args[1].equalsIgnoreCase("remove");
        requirePermission(sender, mutating ? "landformcraft.asset.manage" : "landformcraft.asset.read");
        report(sender, workflow.io(() -> {
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

    private void handleCleanup(CommandSender sender, String[] args) {
        if (args[1].equalsIgnoreCase("plan")) {
            requirePermission(sender, "landformcraft.cleanup");
            requireLength(args, 2, "cleanup plan");
            report(sender, cleanup.plan(actor(sender)), prepared -> {
                sender.sendMessage(detail("Cleanup plan", prepared.plan().planId().toString()));
                sender.sendMessage(detail("placements / bytes", prepared.plan().entries().size() + " / "
                        + prepared.plan().totalBytes()));
                String command = ROOT + "cleanup execute " + prepared.plan().planId() + " "
                        + prepared.confirmationToken();
                sender.sendMessage(confirmationDelivery(
                        sender, "cleanup-" + prepared.plan().planId(), command));
            });
        } else if (args[1].equalsIgnoreCase("execute")) {
            requirePermission(sender, "landformcraft.cleanup");
            requireLength(args, 4, "cleanup execute <plan-id> <token>");
            report(sender, cleanup.execute(uuid(args[2]), args[3], actor(sender)), value -> {
                sender.sendMessage(detail("deleted bytes", Long.toString(value.totalBytes())));
                discardConsoleConfirmation("cleanup-" + uuid(args[2]));
            });
        } else if (args[1].equalsIgnoreCase("status")) {
            requirePermission(sender, "landformcraft.cleanup");
            requireLength(args, 2, "cleanup status");
            report(sender, cleanup.status(), values -> sender.sendMessage(detail(
                    "cleanup plans", Integer.toString(values.size()))));
        } else {
            throw new IllegalArgumentException("cleanupはplan、execute、statusです。");
        }
    }

    private void handleOps(CommandSender sender, String[] args) {
        requirePermission(sender, "landformcraft.doctor");
        if (release2Operations == null || !release2Operations.isRelease2Path()) {
            throw new IllegalStateException("Release 2 operational services are not injected");
        }
        String operation = args[1].toLowerCase(Locale.ROOT);
        if (operation.equals("metrics")) {
            report(sender, workflow.io(() -> {
                try {
                    return release2Operations.captureMetrics(
                            OperationalMetricsCollectorV2.PlacementStageCountsV2.zeros(),
                            0L, 0L, 0, actor(sender).canonical());
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
            report(sender, workflow.io(() -> {
                try {
                    boolean openai = System.getenv("OPENAI_API_KEY") != null
                            && !System.getenv("OPENAI_API_KEY").isBlank();
                    boolean anthropic = System.getenv("ANTHROPIC_API_KEY") != null
                            && !System.getenv("ANTHROPIC_API_KEY").isBlank();
                    return release2Operations.diagnose(
                            correlationId, openai, anthropic, actor(sender).canonical());
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

    private void handleRelease2(CommandSender sender, String[] args) {
        Release2CommandSecurityV2.requireOperator(sender);
        if (release2Placements == null || !release2Placements.isRelease2Path()) {
            throw new IllegalStateException("Release 2 placement application is unavailable");
        }
        String operation = args[1].toLowerCase(Locale.ROOT);
        switch (operation) {
            case "plan" -> {
                requirePermission(sender, "landformcraft.r2.plan");
                requireLength(args, 7, "r2 plan <release-path> <world> <x> <y> <z>");
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
                    String next = ROOT + "r2 confirm " + id + " " + prepared.confirmationToken();
                    sender.sendMessage(confirmationDelivery(sender, "r2-confirm-" + id, next));
                });
            }
            case "confirm" -> {
                requirePermission(sender, "landformcraft.r2.confirm");
                requireLength(args, 4, "r2 confirm <placement-id> <token>");
                report(sender, release2Placements.confirm(uuid(args[2]), args[3], actorV2(sender)), confirmed -> {
                    discardConsoleConfirmation("r2-confirm-" + uuid(args[2]));
                    Component stateLine = detail("state", confirmed.journal().state().name());
                    sender.sendMessage(stateLine);
                    mirrorOperatorMessage(sender, stateLine);
                    Component nextLine = Component.text(
                            ROOT + "r2 execute " + args[2], NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.suggestCommand(ROOT + "r2 execute " + args[2]));
                    sender.sendMessage(nextLine);
                    mirrorOperatorMessage(sender, nextLine);
                });
            }
            case "execute" -> {
                requirePermission(sender, "landformcraft.r2.execute");
                requireLength(args, 3, "r2 execute <placement-id>");
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
                requirePermission(sender, "landformcraft.r2.status");
                requireLength(args, 3, "r2 status <placement-id>");
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
                requirePermission(sender, "landformcraft.r2.undo");
                requireLength(args, 3, "r2 undo-plan <placement-id>");
                report(sender, release2Placements.prepareUndo(uuid(args[2]), actorV2(sender)), prepared -> {
                    String next = ROOT + "r2 undo-execute " + args[2] + " " + prepared.plaintextToken();
                    Component stateLine = detail("state", prepared.preparedJournal().state().name());
                    sender.sendMessage(stateLine);
                    mirrorOperatorMessage(sender, stateLine);
                    sender.sendMessage(confirmationDelivery(sender, "r2-undo-" + uuid(args[2]), next));
                });
            }
            case "undo-execute" -> {
                requirePermission(sender, "landformcraft.r2.undo");
                requireLength(args, 4, "r2 undo-execute <placement-id> <token>");
                report(sender, release2Placements.executeUndo(
                        uuid(args[2]), args[3], actorV2(sender)), undone -> {
                    discardConsoleConfirmation("r2-undo-" + uuid(args[2]));
                    Component stateLine = detail("state", undone.undoneJournal().state().name());
                    sender.sendMessage(stateLine);
                    mirrorOperatorMessage(sender, stateLine);
                });
            }
            case "recover-diagnose" -> {
                requirePermission(sender, "landformcraft.r2.recovery");
                requireLength(args, 3, "r2 recover-diagnose <placement-id>");
                report(sender, release2Placements.diagnoseRecovery(uuid(args[2])), diagnosis -> {
                    sender.sendMessage(detail("classification", diagnosis.classification().name()));
                    diagnosis.findings().forEach(finding -> sender.sendMessage(detail("finding", finding)));
                });
            }
            case "recover-plan" -> {
                requirePermission(sender, "landformcraft.r2.recovery");
                requireLength(args, 4, "r2 recover-plan <rollback|accept> <placement-id>");
                var action = switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "rollback" -> com.github.nankotsu029.landformcraft.model.v2.placement
                            .PlacementRecoveryActionV2.ROLLBACK;
                    case "accept" -> com.github.nankotsu029.landformcraft.model.v2.placement
                            .PlacementRecoveryActionV2.ACCEPT;
                    default -> throw new IllegalArgumentException("recovery actionはrollbackまたはacceptです。");
                };
                report(sender, release2Placements.prepareRecovery(
                        uuid(args[3]), action, actorV2(sender)), prepared -> {
                    String next = ROOT + "r2 recover-execute " + args[2].toLowerCase(Locale.ROOT)
                            + " " + args[3] + " " + prepared.plaintextToken();
                    sender.sendMessage(detail("classification",
                            prepared.recoveryPlan().classification().name()));
                    sender.sendMessage(confirmationDelivery(sender,
                            "r2-recover-" + args[2].toLowerCase(Locale.ROOT) + "-" + uuid(args[3]),
                            next));
                });
            }
            case "recover-execute" -> {
                requirePermission(sender, "landformcraft.r2.recovery");
                requireLength(args, 5,
                        "r2 recover-execute <rollback|accept> <placement-id> <token>");
                if (args[2].equalsIgnoreCase("rollback")) {
                    report(sender, release2Placements.executeRecoveryRollback(
                            uuid(args[3]), args[4], actorV2(sender)), rolledBack -> {
                        discardConsoleConfirmation("r2-recover-rollback-" + uuid(args[3]));
                        sender.sendMessage(detail("state",
                                rolledBack.rolledBackJournal().state().name()));
                    });
                } else if (args[2].equalsIgnoreCase("accept")) {
                    report(sender, release2Placements.executeRecoveryAccept(
                            uuid(args[3]), args[4], actorV2(sender)), accepted -> {
                        discardConsoleConfirmation("r2-recover-accept-" + uuid(args[3]));
                        sender.sendMessage(detail("state",
                                accepted.acceptedJournal().state().name()));
                    });
                } else {
                    throw new IllegalArgumentException("recovery actionはrollbackまたはacceptです。");
                }
            }
            default -> throw new IllegalArgumentException(
                    "r2はplan、confirm、execute、status、undo-plan、undo-execute、"
                            + "recover-diagnose、recover-plan、recover-executeです。");
        }
    }

    private static com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2.PlacementActorV2
            actorV2(CommandSender sender) {
        return Release2CommandSecurityV2.actor(sender);
    }

    private void handleRecovery(CommandSender sender, String[] args) {
        requirePermission(sender, "landformcraft.recovery");
        requirePlacementAvailable();
        String operation = args[2].toLowerCase(Locale.ROOT);
        if (operation.equals("status")) {
            requireLength(args, 4, "apply recover status <placement-id>");
            reportJournal(sender, placements.status(uuid(args[3])));
        } else if (operation.equals("diagnose")) {
            requireLength(args, 4, "apply recover diagnose <placement-id>");
            report(sender, placements.diagnoseRecovery(uuid(args[3]), actor(sender)), value -> {
                sender.sendMessage(detail("classification", value.report().classification().name()));
                value.report().tileFindings().forEach(issue -> sendWarning(sender, issue));
                if (!value.confirmationToken().isEmpty()) {
                    String next = value.action() == ConfirmationAction.RECOVERY_ACCEPT ? "accept" : "rollback";
                    String command = ROOT + "apply recover " + next + " " + args[3] + " "
                            + value.confirmationToken();
                    sender.sendMessage(confirmationDelivery(sender,
                            "apply-recover-" + next + "-" + uuid(args[3]), command));
                }
            });
        } else if (operation.equals("rollback") || operation.equals("accept")) {
            requireLength(args, 5, "apply recover " + operation + " <placement-id> <token>");
            reportJournal(sender, operation.equals("rollback")
                    ? placements.recoverRollback(uuid(args[3]), args[4], actor(sender))
                    : placements.recoverAccept(uuid(args[3]), args[4], actor(sender)),
                    () -> discardConsoleConfirmation(
                            "apply-recover-" + operation + "-" + uuid(args[3])));
        } else {
            throw new IllegalArgumentException("未知のrecovery操作です。");
        }
    }

    private void reportRequest(CommandSender sender,
                               CompletionStage<com.github.nankotsu029.landformcraft.model.GenerationRequest> stage) {
        report(sender, stage, value -> sendRequest(sender, value));
    }

    private static void sendRequest(CommandSender sender,
                                    com.github.nankotsu029.landformcraft.model.GenerationRequest value) {
        sender.sendMessage(detail("Request", value.requestId()));
        sender.sendMessage(detail("bounds", value.bounds().width() + " × " + value.bounds().length()
                + ", Y " + value.bounds().minY() + ".." + value.bounds().maxY()));
        sender.sendMessage(detail("images", Integer.toString(value.images().size())));
    }

    private void reportJob(CommandSender sender,
                           CompletionStage<com.github.nankotsu029.landformcraft.model.GenerationJobSnapshot> stage) {
        report(sender, stage, value -> {
            sender.sendMessage(detail("Job ID", value.jobId().toString()));
            sender.sendMessage(detail("stage / progress", value.stage() + " / " + value.progress()));
            sender.sendMessage(detail("message", value.message()));
        });
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

    private void reportPrepared(
            CommandSender sender,
            CompletionStage<PreparedPlacement> stage,
            String nextCommand
    ) {
        sendPending(sender, "配置計画を検証しています…");
        stage.whenComplete((prepared, failure) -> dispatcher.run(() -> {
            if (failure != null) {
                sendFailure(sender, failure);
                return;
            }
            UUID id = prepared.journal().plan().placementId();
            placementIds.add(id.toString());
            boolean undo = prepared.journal().confirmationAction() == ConfirmationAction.UNDO;
            sender.sendMessage(prefix("✔", NamedTextColor.GREEN).append(Component.text(
                    undo ? "Undoの準備が完了しました。" : "配置計画の検証が完了しました。",
                    NamedTextColor.GREEN
            )));
            sender.sendMessage(detail("Placement ID", id.toString()));
            sender.sendMessage(detail("reserved bytes", Long.toString(prepared.journal().reservedBytes())));
            sender.sendMessage(detail("actor", prepared.journal().confirmationActor().canonical()));
            sender.sendMessage(detail("inclusive bounds", coordinate(
                    prepared.journal().plan().minimumX(), prepared.journal().plan().minimumY(),
                    prepared.journal().plan().minimumZ()) + " .. " + coordinate(
                    prepared.journal().plan().maximumX(), prepared.journal().plan().maximumY(),
                    prepared.journal().plan().maximumZ())));
            sender.sendMessage(Component.text("  ワールドはまだ変更されていません。確認期限は10分です。",
                    NamedTextColor.WHITE));
            String confirmation = ROOT + nextCommand + " " + id + " " + prepared.confirmationToken();
            if (sender instanceof Player) {
                sender.sendMessage(Component.text("  実行候補: ", NamedTextColor.GRAY)
                        .append(Component.text(confirmation, NamedTextColor.YELLOW)
                                .decorate(TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.suggestCommand(confirmation))
                                .hoverEvent(HoverEvent.showText(Component.text(
                                        "クリックしてチャット入力欄へコピー（自動実行はしません）",
                                        NamedTextColor.AQUA
                                )))));
            } else {
                sender.sendMessage(Component.text("  実行候補: ", NamedTextColor.GRAY)
                        .append(confirmationDelivery(sender,
                                nextCommand.replace(' ', '-') + "-" + id, confirmation)));
            }
        }));
    }

    private void reportJournal(CommandSender sender, CompletionStage<PlacementJournal> stage) {
        reportJournal(sender, stage, () -> { });
    }

    private void reportJournal(
            CommandSender sender,
            CompletionStage<PlacementJournal> stage,
            Runnable onSuccess
    ) {
        sendPending(sender, "Placementの状態を更新しています…");
        stage.whenComplete((journal, failure) -> dispatcher.run(() -> {
            if (failure != null) {
                sendFailure(sender, failure);
                return;
            }
            onSuccess.run();
            placementIds.add(journal.plan().placementId().toString());
            NamedTextColor color = journal.state() == PlacementState.RECOVERY_REQUIRED
                    ? NamedTextColor.RED
                    : journal.state() == PlacementState.APPLIED || journal.state() == PlacementState.UNDONE
                    ? NamedTextColor.GREEN : NamedTextColor.AQUA;
            sender.sendMessage(prefix("✔", color).append(Component.text(
                    "Placement: " + stateLabel(journal.state()), color
            )));
            sender.sendMessage(detail("Placement ID", journal.plan().placementId().toString()));
            sender.sendMessage(detail("詳細", journal.message()));
        }));
    }

    private void showHelp(CommandSender sender, String label) {
        sender.sendMessage(prefix("◆", NamedTextColor.AQUA)
                .append(Component.text("コマンド一覧", NamedTextColor.AQUA)));
        helpSection(sender, "確認");
        help(sender, "/" + label + " selection", "WorldEditの選択範囲を表示");
        help(sender, "/" + label + " version | doctor | ops …", "version表示／read-only診断／運用metrics");
        helpSection(sender, "設計・生成");
        help(sender, "/" + label + " request …", "request作成・範囲・prompt・検証・一覧");
        help(sender, "/" + label + " design … | generate <design-id> | job …", "設計と非同期生成");
        help(sender, "/" + label + " candidate … | export …", "候補確認とRelease作成・検証");
        helpSection(sender, "配置・復旧");
        help(sender, "/" + label + " apply plan <release> <world> <x> <y> <z>", "配置を検証（変更なし）");
        help(sender, "/" + label + " apply execute <id> <token>", "確認済み配置を実行");
        help(sender, "/" + label + " apply status <id>", "配置状態を表示");
        help(sender, "/" + label + " apply undo <id>", "Undoを準備（変更なし）");
        help(sender, "/" + label + " undo execute <id> <token>", "確認済みUndoを実行");
        help(sender, "/" + label + " apply recover … | cleanup …", "復旧診断／snapshot cleanup");
        if (release2Placements != null && release2Placements.isRelease2Path()) {
            help(sender, "/" + label + " r2 plan|confirm|execute|status …",
                    "Release 2専用の配置lifecycle（v1と明示分離）");
            help(sender, "/" + label + " r2 undo-plan|undo-execute …",
                    "Release 2 operation-bound Undo");
            help(sender, "/" + label + " r2 recover-diagnose|recover-plan|recover-execute …",
                    "Release 2の保守的Recovery");
        }
        if (release2Undo != null && release2Undo.isRelease2Path()) {
            sender.sendMessage(Component.text(
                    "  Release 2 Undoは PaperPlacementUndoServiceV2（isRelease2Path）経由です。",
                    NamedTextColor.GRAY));
        }
        if (release2Recovery != null && release2Recovery.isRelease2Path()) {
            sender.sendMessage(Component.text(
                    "  Release 2 復旧は PaperPlacementRecoveryServiceV2（isRelease2Path）経由です。",
                    NamedTextColor.GRAY));
        }
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
        if (placements == null || selections == null) {
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
        if (minimum > maximum || value < 1L || value > 1_000L) {
            throw new IllegalArgumentException("選択範囲は各水平軸1..1000である必要があります。");
        }
        return Math.toIntExact(value);
    }

    private static ActorIdentity actor(CommandSender sender) {
        if (sender instanceof Player player) {
            return ActorIdentity.player(player.getUniqueId());
        }
        if (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender) {
            return ActorIdentity.console();
        }
        throw new IllegalArgumentException("この操作はPlayerまたはCONSOLE identityからだけ実行できます。");
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

    private static String stateLabel(PlacementState state) {
        return switch (state) {
            case PLANNED -> "配置待ち";
            case APPLYING -> "配置中";
            case APPLIED -> "配置完了";
            case ROLLING_BACK -> "ロールバック中";
            case ROLLED_BACK -> "ロールバック完了";
            case UNDOING -> "Undo中";
            case UNDONE -> "Undo完了";
            case RECOVERY_REQUIRED -> "手動復旧が必要";
        };
    }
}
