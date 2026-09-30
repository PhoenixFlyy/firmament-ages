package dev.firmages.core.mixin;

import com.google.gson.JsonElement;
import dev.firmages.core.gate.RecipeGate;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * m2 hook (SPEC §4.1). Priority 1500, above KubeJS (1100). HEAD keeps the recipe JSON map: KubeJS rewrites the same
 * map instance at its own HEAD ({@code map.clear(); map.putAll(...)}), so at TAIL it holds the post-KubeJS JSON.
 * TAIL runs the gate, which replaces the parsed recipes with the kept ones.
 */
@Mixin(value = RecipeManager.class, priority = 1500)
public abstract class RecipeManagerMixin {
    @Shadow
    @Final
    private HolderLookup.Provider registries;

    @Unique
    private Map<ResourceLocation, JsonElement> firmages$json;

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("HEAD"))
    private void firmages$keepJson(Map<ResourceLocation, JsonElement> json, ResourceManager resources, ProfilerFiller profiler, CallbackInfo ci) {
        firmages$json = json;
    }

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("TAIL"))
    private void firmages$gate(Map<ResourceLocation, JsonElement> json, ResourceManager resources, ProfilerFiller profiler, CallbackInfo ci) {
        Map<ResourceLocation, JsonElement> kept = firmages$json != null ? firmages$json : json;
        firmages$json = null;
        RecipeGate.filter((RecipeManager) (Object) this, kept, resources, registries);
    }
}
