package dev.firmages.core.age;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Datapack reload listener (added via {@code AddReloadListenerEvent}): builds the {@link AgeIndex} of the load
 * in the preparation phase and makes it current in the apply phase, together with the other server data.
 */
public final class AgeReloadListener implements PreparableReloadListener {

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager rm, ProfilerFiller prepProfiler,
                                          ProfilerFiller applyProfiler, Executor background, Executor game) {
        return CompletableFuture.supplyAsync(() -> AgeIndex.forResources(rm), background)
            .thenCompose(barrier::wait)
            .thenAcceptAsync(idx -> AgeService.checkLoadAnswers(AgeIndex.finishLoad(rm)), game);
    }

    @Override
    public String getName() {
        return "firmages:age_index";
    }
}
