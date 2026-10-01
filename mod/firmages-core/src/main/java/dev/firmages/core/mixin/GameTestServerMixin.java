package dev.firmages.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Lifecycle;
import dev.firmages.core.FirmagesCore;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Test infrastructure only: vanilla's {@code GameTestServer} bakes its world from the flat preset with an empty
 * dimension registry, so datapack dimensions never load in GameTests [verified, 21.1.252 source]. This hands it
 * this mod's own datapack dimensions ({@code firmages:origin}), so the Origin GameTests run against the real level.
 * Other mods' datapack dimensions stay out (they would only slow the test boot). A normal server never runs
 * {@code GameTestServer}.
 */
@Mixin(GameTestServer.class)
abstract class GameTestServerMixin {
    @WrapOperation(method = "lambda$create$1", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/WorldDimensions;bake(Lnet/minecraft/core/Registry;)Lnet/minecraft/world/level/levelgen/WorldDimensions$Complete;"))
    private static WorldDimensions.Complete firmages$withOwnDimensions(WorldDimensions dimensions, Registry<LevelStem> empty,
                                                                     Operation<WorldDimensions.Complete> original,
                                                                     @Local(argsOnly = true) WorldLoader.DataLoadContext ctx) {
        MappedRegistry<LevelStem> stems = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable());
        ctx.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM).holders()
            .filter(h -> h.key().location().getNamespace().equals(FirmagesCore.MOD_ID))
            .forEach(h -> Registry.register(stems, h.key(), h.value()));
        return original.call(dimensions, stems.freeze());
    }
}
