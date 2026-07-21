package com.github.nankotsu029.landformcraft;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.core.ProviderSettings;
import com.github.nankotsu029.landformcraft.core.CustomAssetService;
import com.github.nankotsu029.landformcraft.core.WorldAccessPolicy;
import com.github.nankotsu029.landformcraft.core.CancellationToken;
import com.github.nankotsu029.landformcraft.core.v2.operations.OperationalAuditLogV2;
import com.github.nankotsu029.landformcraft.core.v2.operations.OperationalOperationsServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.operations.Release2RetentionServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.operations.RetentionCleanupPortV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.Release2PlacementApplicationServiceV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.apply.PlacementApplyLimitsV2;
import com.github.nankotsu029.landformcraft.core.v2.recovery.PlacementRecoveryCleanupPlanV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementJournalV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.PlacementPlanV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.Release2DiskBudgetV2;
import com.github.nankotsu029.landformcraft.core.v2.placement.Release2PlacementOperationStoreV2;
import com.github.nankotsu029.landformcraft.model.v2.catalog.PlacementDimensionLimitV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.Release2MeasuredDimensionGateV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.Release2MeasurementProfileV2;
import com.github.nankotsu029.landformcraft.model.v2.placement.Release2PlacementDimensionPolicyV2;
import com.github.nankotsu029.landformcraft.ai.spi.TerrainDesignPolicy;
import com.github.nankotsu029.landformcraft.paper.LandformCraftCommand;
import com.github.nankotsu029.landformcraft.paper.PaperMainThreadDispatcher;
import com.github.nankotsu029.landformcraft.paper.PaperOperationalOperationsServiceV2;
import com.github.nankotsu029.landformcraft.paper.PaperPlacementWorldGatewayV2;
import com.github.nankotsu029.landformcraft.paper.PaperRelease2PlacementServiceV2;
import com.github.nankotsu029.landformcraft.paper.PaperTerrainDesignProviderFactoryV2;
import com.github.nankotsu029.landformcraft.paper.PaperV2WorkflowServiceV2;
import com.github.nankotsu029.landformcraft.paper.PaperWorldEditSelectionService;
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
    private PaperRelease2PlacementServiceV2 release2PlacementService;

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
        if (release2PlacementService != null) {
            release2PlacementService.close();
            release2PlacementService = null;
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
        Clock clock = Clock.systemUTC();
        ProviderSettings providers = providerSettings();
        CustomAssetService assets = new CustomAssetService(
                storageRoot.resolve("imports"), storageRoot.resolve("assets"),
                storageRoot.resolve("exports"), clock);

        Plugin official = getServer().getPluginManager().getPlugin("WorldEdit");
        Plugin fawe = getServer().getPluginManager().getPlugin("FastAsyncWorldEdit");
        if (official != null && official.isEnabled() && fawe != null && fawe.isEnabled()
                && official != fawe) {
            throw new IllegalStateException("WorldEdit and FAWE must not be enabled together");
        }
        Plugin integration = fawe != null && fawe.isEnabled()
                ? fawe
                : official != null && official.isEnabled() ? official : null;
        boolean placementEnabled = getConfig().getBoolean("worldedit.enabled", true)
                && integration != null;
        String integrationStatus = !getConfig().getBoolean("worldedit.enabled", true)
                ? "disabled by config"
                : integration == null
                ? "not detected"
                : integration.getName() + " " + integration.getPluginMeta().getVersion();

        PaperWorldEditSelectionService selections = null;
        PaperRelease2PlacementServiceV2 release2Placements = null;
        if (placementEnabled) {
            WorldAccessPolicy worldAccessPolicy = new WorldAccessPolicy(
                    getConfig().getStringList("placement.allowed-worlds"),
                    getConfig().getStringList("placement.denied-worlds"));
            selections = new PaperWorldEditSelectionService(mainThreadDispatcher);
            try {
                release2Placements = new PaperRelease2PlacementServiceV2(
                        new Release2PlacementApplicationServiceV2(
                                storageRoot.resolve("releases-v2"),
                                storageRoot.resolve("placement-v2"),
                                executors,
                                new PaperPlacementWorldGatewayV2(mainThreadDispatcher),
                                clock,
                                release2DiskBudget(),
                                release2DimensionPolicy(integration == fawe),
                                Release2PlacementOperationStoreV2.WriteFaultInjectorV2.none(),
                                release2ApplyLimits()),
                        worldAccessPolicy);
                release2PlacementService = release2Placements;
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Release 2 placement storage initialization failed", exception);
            }
        } else {
            getLogger().warning(
                    "WorldEdit/FAWE placement integration is unavailable; non-placement commands remain active.");
        }

        OperationalAuditLogV2 operationsAudit = new OperationalAuditLogV2(storageRoot);
        RetentionCleanupPortV2 retentionPort = release2Placements != null
                ? release2Placements.retentionCleanupPort()
                : deferredRetentionCleanupPort();
        Release2RetentionServiceV2 retention = new Release2RetentionServiceV2(
                retentionPort, operationsAudit, clock);
        PaperOperationalOperationsServiceV2 release2Operations =
                new PaperOperationalOperationsServiceV2(
                        new OperationalOperationsServiceV2(executors, storageRoot, retention));
        PaperV2WorkflowServiceV2 v2Workflow = new PaperV2WorkflowServiceV2(
                executors,
                new PaperTerrainDesignProviderFactoryV2(executors, providers, clock),
                storageRoot.resolve("v2"));
        LandformCraftCommand commandHandler = new LandformCraftCommand(
                release2Placements, release2Operations, v2Workflow, selections,
                mainThreadDispatcher, executors, assets, storageRoot,
                getPluginMeta().getVersion(), integrationStatus);
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
        getServer().getPluginManager().registerEvents(commandHandler, this);

        if (release2Placements != null) {
            release2Placements.inspectRestartState().whenComplete((ambiguous, failure) ->
                    mainThreadDispatcher.run(() -> {
                        if (failure != null) {
                            getLogger().severe("Could not inspect Release 2 placement journals: "
                                    + failure.getMessage());
                        } else if (!ambiguous.isEmpty()) {
                            getLogger().warning(ambiguous.size()
                                    + " Release 2 placement(s) require explicit status/recovery inspection.");
                        }
                    }));
        }
    }

    /** Release 2 disk budget from the operator settings. */

    private Release2DiskBudgetV2 release2DiskBudget() {
        return new Release2DiskBudgetV2(
                requireLongRange(getConfig().getLong("disk.minimum-free-bytes", 536_870_912L),
                        0L, Long.MAX_VALUE, "disk.minimum-free-bytes"),
                requireLongRange(getConfig().getLong("disk.maximum-snapshot-bytes", 8_589_934_592L),
                        1L, Long.MAX_VALUE, "disk.maximum-snapshot-bytes"),
                requireLongRange(getConfig().getLong("disk.safety-margin-bytes", 268_435_456L),
                        0L, Long.MAX_VALUE, "disk.safety-margin-bytes"));
    }

    /**
     * V2-11-02 dimension policy, with the V2-11-06 runtime-dependent ceiling. The
     * normal-operation ceiling is clamped at startup to the dimension actually measured on the
     * detected runtime (1000 on FAWE 2.15.2, 64 otherwise), so an over-limit configuration value
     * is rejected here instead of silently widening placement beyond evidence. Above-limit
     * layouts require the explicitly enabled measurement profile (isolated world plus
     * CONSOLE/RCON operator), which stays off by default.
     */
    private Release2PlacementDimensionPolicyV2 release2DimensionPolicy(boolean fastAsyncWorldEdit) {
        Release2MeasuredDimensionGateV2 productionGate = Release2MeasuredDimensionGateV2.production(
                fastAsyncWorldEdit,
                getConfig().getInt(Release2MeasuredDimensionGateV2.CONFIG_WIDTH_KEY,
                        PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM),
                getConfig().getInt(Release2MeasuredDimensionGateV2.CONFIG_LENGTH_KEY,
                        PlacementDimensionLimitV2.SMOKE_MEASURED_MAXIMUM));
        Release2MeasurementProfileV2 profile = Release2MeasurementProfileV2.disabled();
        if (getConfig().getBoolean(Release2MeasurementProfileV2.CONFIG_ENABLED_KEY, false)) {
            profile = Release2MeasurementProfileV2.forIsolatedWorld(
                    requiredString(Release2MeasurementProfileV2.CONFIG_WORLD_KEY, ""),
                    getConfig().getInt(Release2MeasurementProfileV2.CONFIG_WIDTH_KEY, 0),
                    getConfig().getInt(Release2MeasurementProfileV2.CONFIG_LENGTH_KEY, 0));
            getLogger().warning("Release 2 measurement profile is ENABLED (" + profile.describe()
                    + "). This is for V2-11-04／V2-11-05 re-measurement on an isolated world only"
                    + " and does not promote any dimension in the Feature Support Catalog.");
        }
        return new Release2PlacementDimensionPolicyV2(productionGate, profile);
    }

    /**
     * V2-13-06 calibration override. It is accepted only with the existing isolated-world
     * measurement profile and only for the closed candidate set; normal servers use the compiled
     * production default and cannot tune this safety/performance boundary.
     */
    private PlacementApplyLimitsV2 release2ApplyLimits() {
        String key = Release2MeasurementProfileV2.CONFIG_PREFIX + ".apply-slice-blocks";
        int configured = getConfig().getInt(key, 0);
        if (configured == 0) {
            return PlacementApplyLimitsV2.defaults();
        }
        if (!getConfig().getBoolean(Release2MeasurementProfileV2.CONFIG_ENABLED_KEY, false)) {
            throw new IllegalArgumentException(
                    key + " requires placement.release2.measurement-profile.enabled=true");
        }
        PlacementApplyLimitsV2 limits = PlacementApplyLimitsV2.withSliceSize(configured);
        getLogger().warning("Release 2 apply slice calibration is ENABLED ("
                + configured + " mutations/slice). This is for V2-13-06 isolated-host"
                + " measurement only and does not change placement safety or catalog support.");
        return limits;
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

    private static RetentionCleanupPortV2 deferredRetentionCleanupPort() {
        return new RetentionCleanupPortV2() {
            @Override
            public PlacementRecoveryCleanupPlanV2 planCleanup(
                    PlacementPlanV2 placementPlan,
                    PlacementJournalV2 journal,
                    CancellationToken cancellation
            ) {
                throw new IllegalStateException(
                        "Release 2 retention cleanup requires PlacementRecoveryServiceV2 injection");
            }

            @Override
            public long executeCleanup(
                    PlacementRecoveryCleanupPlanV2 cleanupPlan,
                    PlacementPlanV2 placementPlan,
                    PlacementJournalV2 journal,
                    CancellationToken cancellation
            ) {
                throw new IllegalStateException(
                        "Release 2 retention cleanup requires PlacementRecoveryServiceV2 injection");
            }
        };
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
