package dev.firmages.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.firmages.core.hardship.InventoryRowsService;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Locked inventory rows take nothing (SPEC §19.4). Minecraft 1.21.1 {@code Inventory} [verified, javap]:
 * {@code getFreeSlot} loops {@code items.get(i).isEmpty()}; {@code getSlotWithRemainingSpace} tries the selected
 * hotbar slot and the offhand (40) first, then loops {@code hasRemainingSpaceForItem(items.get(i), stack)} (its third
 * call); {@code add}, {@code addResource} and {@code placeItemBackInInventory} find their slot only through these two.
 * {@code Inventory} does not override {@code Container.canPlaceItem}, which NeoForge's {@code InvWrapper} asks before
 * every insert: this mixin adds it.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin implements Container {
    @Shadow @Final public Player player;

    @WrapOperation(method = "getFreeSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z"))
    private boolean firmages$freeAndOpen(ItemStack stack, Operation<Boolean> original, @Local(ordinal = 0) int i) {
        return original.call(stack) && !InventoryRowsService.locked(player, i);
    }

    @WrapOperation(method = "getSlotWithRemainingSpace", at = @At(value = "INVOKE", ordinal = 2,
        target = "Lnet/minecraft/world/entity/player/Inventory;hasRemainingSpaceForItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean firmages$spaceAndOpen(Inventory self, ItemStack destination, ItemStack origin, Operation<Boolean> original,
                                          @Local(ordinal = 0) int i) {
        return original.call(self, destination, origin) && !InventoryRowsService.locked(player, i);
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return !InventoryRowsService.locked(player, index);
    }
}
