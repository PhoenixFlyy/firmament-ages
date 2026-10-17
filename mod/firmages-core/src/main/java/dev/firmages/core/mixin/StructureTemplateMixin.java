package dev.firmages.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.firmages.core.loot.TemplateContents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/**
 * Prefilled containers and entities of structure templates follow the server's Ages (SPEC §19.5). {@code placeInWorld}
 * loads each placed block entity's data with its one call {@code BlockEntity.loadWithComponents(CompoundTag, Provider)}
 * [verified, javap of the 21.1.252 merged jar: {@code placeInWorld(ServerLevelAccessor, BlockPos, BlockPos,
 * StructurePlaceSettings, RandomSource, int)}, offset 503]; it then places the entities through
 * {@code addEntitiesToWorld(ServerLevelAccessor, BlockPos, StructurePlaceSettings)}, which creates each one with its one
 * call {@code createEntityIgnoreException(ServerLevelAccessor, CompoundTag)} (offset 156, {@code EntityType.create}) on
 * its own copy of the template's entry [verified, javap of the same jar]. Jigsaw pieces ({@code SinglePoolElement.place}),
 * legacy template pieces, {@code /place template} and structure blocks all go through both. The wrapped calls get the
 * data without the stacks of a later Age ({@link TemplateContents#filter}, {@link TemplateContents#filterEntity}); an
 * item entity whose stack went is not created.
 */
@Mixin(StructureTemplate.class)
abstract class StructureTemplateMixin {
    @WrapOperation(method = "placeInWorld", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V"))
    private void firmages$filterContents(BlockEntity blockEntity, CompoundTag nbt, HolderLookup.Provider registries, Operation<Void> original) {
        original.call(blockEntity, TemplateContents.filter(nbt), registries);
    }

    @WrapOperation(method = "addEntitiesToWorld", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;createEntityIgnoreException(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Optional;"))
    private Optional<Entity> firmages$filterEntity(ServerLevelAccessor level, CompoundTag nbt, Operation<Optional<Entity>> original) {
        CompoundTag filtered = TemplateContents.filterEntity(nbt);
        return filtered == null ? Optional.empty() : original.call(level, filtered);
    }
}
