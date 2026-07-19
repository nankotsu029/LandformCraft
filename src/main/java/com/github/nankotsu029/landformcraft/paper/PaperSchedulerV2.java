package com.github.nankotsu029.landformcraft.paper;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Minimal scheduler port used by the Release 2 world gateway. */
public interface PaperSchedulerV2 {
    <T> CompletionStage<T> supply(Supplier<T> operation);

    boolean isMainThread();
}
