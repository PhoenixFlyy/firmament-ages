package dev.firmages.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.firmages.core.loot.TemplateContents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Prefilled containers of structure templates follow the server's Ages (SPEC §19.5). {@code placeInWorld} loads each
 * placed block entity's data with its one call {@code BlockEntity.loadWithComponents(CompoundTag, Provider)} [verified,
 * javap of the 21.1.252 merged jar: {@code placeInWorld(ServerLevelAccessor, BlockPos, BlockPos, StructurePlaceSettings,
 * RandomSource, int)}, offset 503]; jigsaw pieces ({@code SinglePoolElement.place}), legacy template pieces,
 * {@code /place template} and structure blocks all go through it. The wrapped call gets the data without the stacks of
 * a later Age ({@link TemplateContents#filter}).
 */
@Mixin(StructureTemplate.class)
abstract class StructureTemplateMixin {
    @WrapOperation(method = "placeInWorld", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V"))
    private void firmages$filterContents(BlockEntity blockEntity, CompoundTag nbt, HolderLookup.Provider registries, Operation<Void> original) {
        original.call(blockEntity, TemplateContents.filter(nbt), registries);
    }
}
