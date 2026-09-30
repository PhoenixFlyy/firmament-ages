package dev.firmages.core.mixin;

import dev.firmages.core.age.AgeIndex;
import dev.firmages.core.gate.RecipeGate;
import net.minecraft.commands.Commands;
import net.minecraft.tags.TagManager;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Captures the resource manager of every server datapack load before any reload listener starts, so the
 * {@link AgeIndex} of that load is available to code that runs during tag loading (the KubeJS tag events call
 * {@code FirmAges.lockedOreBlocks()} there, before tags are bound). Priority 500 places this HEAD callback before
 * KubeJS's own HEAD callback (priority 1000), which re-runs the server scripts and pre-captures the tag events;
 * verified in the dev GameTest log (with 1500 the KubeJS run saw the previous index).
 */
@Mixin(value = ReloadableServerResources.class, priority = 500)
public abstract class ReloadableServerResourcesMixin {
    @Shadow
    @Final
    private RecipeManager recipes;
    @Shadow
    @Final
    private TagManager tagManager;

    /** m2: the recipe gate reads tag outputs from this load's tag manager (its result is complete before recipes apply). */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void firmages$linkTags(CallbackInfo ci) {
        RecipeGate.link(recipes, tagManager);
    }

    @Inject(method = "loadResources", at = @At("HEAD"))
    private static void firmages$captureResourceManager(ResourceManager resourceManager, LayeredRegistryAccess<RegistryLayer> registries,
                                                        FeatureFlagSet enabledFeatures, Commands.CommandSelection commandSelection,
                                                        int functionCompilationLevel, Executor backgroundExecutor, Executor gameExecutor,
                                                        CallbackInfoReturnable<CompletableFuture<ReloadableServerResources>> cir) {
        AgeIndex.beginLoad(resourceManager);
    }
}
