package dev.firmages.core.mixin;

import dev.firmages.core.loot.AgeLoot;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifierManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Map;

/**
 * The loot Age gate runs after every other global loot modifier (SPEC §19.5). NeoForge 21.1.252 promises "layered
 * order" but hands {@code apply} a {@code HashMap} built in {@code prepare}, and LootJS 3.7.0 copies the map through a
 * {@code HashMap} again at the RETURN of {@code apply} [verified, source and javap]: the order is the hash order of the
 * ids. So {@code getAllLootMods}, which {@code CommonHooks.modifyLoot} iterates, answers with every
 * {@code AgeLootModifier} moved to the end, computed once per map (whoever replaced it last), and the One Iron swap
 * ({@code firmages:replace_item}) and the items other modifiers add are judged by the gate.
 */
@Mixin(value = LootModifierManager.class, remap = false)
abstract class LootModifierManagerMixin {
    @Shadow
    private Map<ResourceLocation, IGlobalLootModifier> registeredLootModifiers;

    /** The map the cached order was made from and that order, one immutable record (worldgen threads roll loot too). */
    @Unique
    private AgeLoot.ModOrder firmages$order;

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("RETURN"))
    private void firmages$noteManager(CallbackInfo ci) {
        AgeLoot.noteManager((LootModifierManager) (Object) this);
    }

    @Inject(method = "getAllLootMods", at = @At("RETURN"), cancellable = true)
    private void firmages$ageFilterLast(CallbackInfoReturnable<Collection<IGlobalLootModifier>> cir) {
        Map<ResourceLocation, IGlobalLootModifier> source = registeredLootModifiers;
        AgeLoot.ModOrder cached = firmages$order;
        if (cached == null || cached.source() != source) {
            cached = new AgeLoot.ModOrder(source, AgeLoot.filterLast(cir.getReturnValue()));
            firmages$order = cached;
        }
        cir.setReturnValue(cached.mods());
    }
}
