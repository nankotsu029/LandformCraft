package com.github.nankotsu029.landformcraft;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.FilePlacementJournalRepository;
import com.github.nankotsu029.landformcraft.core.PlacementApplicationService;
import com.github.nankotsu029.landformcraft.core.PaperWorkflowService;
import com.github.nankotsu029.landformcraft.core.ProviderSettings;
import com.github.nankotsu029.landformcraft.core.SnapshotCleanupService;
import com.github.nankotsu029.landformcraft.core.CustomAssetService;
import com.github.nankotsu029.landformcraft.core.DiskBudgetPolicy;
import com.github.nankotsu029.landformcraft.core.PolicyPlacementWorldGateway;
import com.github.nankotsu029.landformcraft.core.WorldAccessPolicy;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignPolicy;
import com.github.nankotsu029.landformcraft.paper.LandformCraftCommand;
import com.github.nankotsu029.landformcraft.paper.PaperMainThreadDispatcher;
import com.github.nankotsu029.landformcraft.paper.PaperWorldEditPlacementGateway;
import com.github.nankotsu029.landformcraft.paper.PaperWorldEditSelectionService;
import com.github.nankotsu029.landformcraft.paper.PaperTerrainDesignProviderFactory;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Paper entry point. Business and generation logic belongs in non-Paper modules. */
public final class Landformcraft extends JavaPlugin {
    private GenerationExecutors executors;
    private PaperMainThreadDispatcher mainThreadDispatcher;
    private PlacementApplicationService placementService;
    private PaperWorkflowService workflowService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int automaticParallelism = Math.max(1, Math.min(4, availableProcessors - 1));
        int configuredParallelism = getConfig().getInt("executors.generation-parallelism", 0);
        int parallelism = configuredParallelism == 0
                ? automaticParallelism
                : requireRange(
                        configuredParallelism,
                        1,
                        GenerationExecutors.MAX_GENERATION_PARALLELISM,
                        "executors.generation-parallelism"
                );
        int ioConcurrency = requireRange(
                getConfig().getInt("executors.io-concurrency", GenerationExecutors.DEFAULT_IO_CONCURRENCY),
                1,
                GenerationExecutors.MAX_IO_CONCURRENCY,
                "executors.io-concurrency"
        );
        int queueCapacity = requireRange(
                getConfig().getInt("executors.generation-queue-capacity", 128),
                1,
                GenerationExecutors.MAX_GENERATION_QUEUE_CAPACITY,
                "executors.generation-queue-capacity"
        );

        executors = GenerationExecutors.create(ioConcurrency, parallelism, queueCapacity);
        mainThreadDispatcher = new PaperMainThreadDispatcher(this);

        configurePlacementCommands();

        getLogger().info("LandformCraft beta services initialized; run /lfc doctor before placement.");
    }

    @Override
    public void onDisable() {
        if (placementService != null) {
            placementService.stopAcceptingMutations();
            placementService = null;
        }
        if (workflowService != null) {
            workflowService.close();
            workflowService = null;
        }
        if (mainThreadDispatcher != null) {
            mainThreadDispatcher.close();
            mainThreadDispatcher = null;
        }
        if (executors != null) {
            boolean terminated = executors.shutdown(Duration.ofSeconds(5));
            if (!terminated) {
                getLogger().severe("Background tasks did not terminate within the shared 5-second shutdown budget.");
            }
            executors = null;
        }
    }

    private static int requireRange(int value, int minimum, int maximum, String path) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private void configurePlacementCommands() {
        PluginCommand command = Objects.requireNonNull(getCommand("landformcraft"),
                "landformcraft command is missing from plugin.yml");
        Path storageRoot = resolveStorageRoot();
        FilePlacementJournalRepository repository = new FilePlacementJournalRepository(
                storageRoot.resolve("placements"), executors
        );
        Clock clock = Clock.systemUTC();
        ProviderSettings providers = providerSettings();
        PaperWorkflowService workflow = new PaperWorkflowService(
                storageRoot, executors,
                new PaperTerrainDesignProviderFactory(storageRoot.resolve("imports"), executors, providers, clock),
                clock);
        workflowService = workflow;
        SnapshotCleanupService cleanup = new SnapshotCleanupService(
                storageRoot.resolve("snapshots"), storageRoot.resolve("cleanup-plans"), repository,
                executors, clock, requireRange(getConfig().getInt("retention.days", 30), 1, 36_500,
                "retention.days"));
        CustomAssetService assets = new CustomAssetService(
                storageRoot.resolve("imports"), storageRoot.resolve("assets"),
                storageRoot.resolve("exports"), clock);

        /*Plugin official = getServer().getPluginManager().getPlugin("WorldEdit");
        Plugin fawe = getServer().getPluginManager().getPlugin("FastAsyncWorldEdit");
        if (official != null && official.isEnabled() && fawe != null && fawe.isEnabled()) {
            throw new IllegalStateException("WorldEdit and FAWE must not be enabled together");
        }
        Plugin integration = official != null && official.isEnabled() ? official
                : fawe != null && fawe.isEnabled() ? fawe : null;
        boolean placementEnabled = getConfig().getBoolean("worldedit.enabled", true)
                && integration != null;
        String integrationStatus = !getConfig().getBoolean("worldedit.enabled", true)
                ? "disabled by config" : integration == null ? "not detected"
                : integration.getName() + " " + integration.getPluginMeta().getVersion();*/
        Plugin official = getServer().getPluginManager().getPlugin("WorldEdit");
        Plugin fawe = getServer().getPluginManager().getPlugin("FastAsyncWorldEdit");

        if (official != null
                && official.isEnabled()
                && fawe != null
                && fawe.isEnabled()
                && official != fawe) {
            throw new IllegalStateException(
                    "WorldEdit and FAWE must not be enabled together"
            );
        }

        Plugin integration = fawe != null && fawe.isEnabled()
                ? fawe
                : official != null && official.isEnabled()
                ? official
                : null;

        boolean worldEditEnabled = getConfig().getBoolean("worldedit.enabled", true);
        boolean placementEnabled = worldEditEnabled && integration != null;

        String integrationStatus = !worldEditEnabled
                ? "disabled by config"
                : integration == null
                ? "not detected"
                : integration.getName() + " "
                + integration.getPluginMeta().getVersion();
        PlacementApplicationService placements = null;
        PaperWorldEditSelectionService selections = null;
        if (placementEnabled) {
            placements = new PlacementApplicationService(
                    storageRoot.resolve("exports"), storageRoot.resolve("snapshots"), executors, repository,
                    new PolicyPlacementWorldGateway(
                            new PaperWorldEditPlacementGateway(mainThreadDispatcher, executors),
                            new WorldAccessPolicy(
                                    getConfig().getStringList("placement.allowed-worlds"),
                                    getConfig().getStringList("placement.denied-worlds"))),
                    clock,
                    new DiskBudgetPolicy(
                            requireLongRange(getConfig().getLong("disk.minimum-free-bytes", 536_870_912L),
                                    0L, Long.MAX_VALUE, "disk.minimum-free-bytes"),
                            requireLongRange(getConfig().getLong("disk.maximum-snapshot-bytes", 8_589_934_592L),
                                    1L, Long.MAX_VALUE, "disk.maximum-snapshot-bytes"),
                            requireLongRange(getConfig().getLong("disk.safety-margin-bytes", 268_435_456L),
                                    0L, Long.MAX_VALUE, "disk.safety-margin-bytes")
                    )
            );
            selections = new PaperWorldEditSelectionService(mainThreadDispatcher);
            placementService = placements;
        } else {
            getLogger().warning("WorldEdit/FAWE placement integration is unavailable; non-placement commands remain active.");
        }
        LandformCraftCommand commandHandler = new LandformCraftCommand(
                placements, selections, mainThreadDispatcher, workflow, cleanup, assets, storageRoot,
                getPluginMeta().getVersion(), integrationStatus
        );
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
        getServer().getPluginManager().registerEvents(commandHandler, this);
        if (placements != null) {
            placements.recoverInterrupted().whenComplete((recovered, failure) ->
                    mainThreadDispatcher.run(() -> {
                        if (failure != null) {
                            getLogger().severe("Could not inspect placement recovery journals: " + failure.getMessage());
                        } else if (!recovered.isEmpty()) {
                            getLogger().warning(recovered.size() + " interrupted placement(s) require recovery inspection.");
                        }
                    }));
        }
    }

    private ProviderSettings providerSettings() {
        TerrainDesignPolicy policy = new TerrainDesignPolicy(
                Duration.ofSeconds(requireRange(getConfig().getInt("provider-policy.timeout-seconds", 60),
                        1, 600, "provider-policy.timeout-seconds")),
                requireRange(getConfig().getInt("provider-policy.max-attempts", 3),
                        1, 10, "provider-policy.max-attempts"),
                Duration.ofMillis(requireLongRange(
                        getConfig().getLong("provider-policy.initial-backoff-millis", 250L),
                        0L, 60_000L, "provider-policy.initial-backoff-millis")),
                requireRange(getConfig().getInt("provider-policy.max-output-tokens", 4096),
                        256, 32_768, "provider-policy.max-output-tokens"),
                requireRange(getConfig().getInt("provider-policy.requests-per-minute", 20),
                        1, 10_000, "provider-policy.requests-per-minute"),
                requireLongRange(getConfig().getLong("provider-policy.process-token-budget", 100_000L),
                        256L, Long.MAX_VALUE, "provider-policy.process-token-budget")
        );
        return new ProviderSettings(
                getConfig().getBoolean("providers.openai.enabled", false),
                requiredString("providers.openai.api-key-env", "OPENAI_API_KEY"),
                requiredStringAllowEmpty("providers.openai.default-model", ""),
                getConfig().getBoolean("providers.anthropic.enabled", false),
                requiredString("providers.anthropic.api-key-env", "ANTHROPIC_API_KEY"),
                requiredStringAllowEmpty("providers.anthropic.default-model", ""),
                policy
        );
    }

    private String requiredString(String path, String fallback) {
        String value = getConfig().getString(path, fallback);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(path + " must not be blank");
        }
        return value.strip();
    }

    private String requiredStringAllowEmpty(String path, String fallback) {
        String value = getConfig().getString(path, fallback);
        if (value == null) {
            throw new IllegalArgumentException(path + " must not be null");
        }
        return value.strip();
    }

    private static long requireLongRange(long value, long minimum, long maximum, String path) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(path + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private Path resolveStorageRoot() {
        String configured = getConfig().getString("storage.root", "data");
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("storage.root must not be blank");
        }
        Path value = Path.of(configured);
        if (value.isAbsolute() || configured.contains("\\")) {
            throw new IllegalArgumentException("storage.root must be a portable relative path");
        }
        Path dataFolder = getDataFolder().toPath().toAbsolutePath().normalize();
        Path resolved = dataFolder.resolve(value).normalize();
        if (!resolved.startsWith(dataFolder)) {
            throw new IllegalArgumentException("storage.root escapes the plugin data folder");
        }
        return resolved;
    }
}
