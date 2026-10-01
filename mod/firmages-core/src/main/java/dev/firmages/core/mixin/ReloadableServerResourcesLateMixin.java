package dev.firmages.core.mixin;

import dev.firmages.core.gate.RecipeGate;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * m2 late pass (see {@link RecipeGate#lateFilter}). {@code updateRegistryTags} binds the tags of a load and posts
 * {@code TagsUpdatedEvent}; Create Dragons Plus adds recipes at its TAIL (mixin priority 1000). Priority 1500 applies
 * this TAIL callback later, so it runs after theirs and after every TagsUpdatedEvent listener. It is a separate class
 * from {@link ReloadableServerResourcesMixin}, whose HEAD callback needs priority 500.
 */
@Mixin(value = ReloadableServerResources.class, priority = 1500)
public abstract class ReloadableServerResourcesLateMixin {
    @Shadow
    @Final
    private RecipeManager recipes;

    @Inject(method = "updateRegistryTags()V", at = @At("TAIL"))
    private void firmages$lateGate(CallbackInfo ci) {
        RecipeGate.lateFilter(recipes);
    }
}
