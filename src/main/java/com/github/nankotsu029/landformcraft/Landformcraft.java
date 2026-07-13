package com.github.nankotsu029.landformcraft;

import com.github.nankotsu029.landformcraft.core.GenerationExecutors;
import com.github.nankotsu029.landformcraft.paper.PaperMainThreadDispatcher;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

/** Paper entry point. Business and generation logic belongs in non-Paper modules. */
public final class Landformcraft extends JavaPlugin {
    private GenerationExecutors executors;
    private PaperMainThreadDispatcher mainThreadDispatcher;

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

        getLogger().info("LandformCraft initialized. Generation features are introduced phase by phase; see docs/roadmap.md.");
    }

    @Override
    public void onDisable() {
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
}
