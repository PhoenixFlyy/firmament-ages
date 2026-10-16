package dev.firmages.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.firmages.core.hardship.GridLockMenu;
import dev.firmages.core.hardship.GridLockService;
import dev.firmages.core.hardship.GridSlots;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Dawn grid lock in the player inventory (SPEC §19.3). Minecraft 1.21.1 {@code InventoryMenu.<init>} creates one
 * {@code ResultSlot} and, as its first {@code new Slot(Container, int, int, int)}, the four grid slots (in a loop;
 * armour slots are {@code ArmorSlot}, inventory and hotbar the later {@code new Slot}s) [verified, source]. They
 * become {@link GridSlots}. {@code slotsChanged} asks {@link GridLockService#refuseGridChange} first, before
 * {@code CraftingMenu.slotChangedCraftingGrid} (and with it ProgressiveStages and Polymorph) runs.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin implements GridLockMenu {
    @Shadow @Final private Player owner;
    @Shadow @Final private CraftingContainer craftSlots;
    @Shadow @Final private ResultContainer resultSlots;

    @Override
    public Player firmages$gridOwner() {
        return owner;
    }

    @WrapOperation(method = "<init>", at = @At(value = "NEW",
        target = "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/Container;III)Lnet/minecraft/world/inventory/ResultSlot;"))
    private ResultSlot firmages$gridResult(Player player, CraftingContainer grid, Container result, int slot, int x, int y, Operation<ResultSlot> original) {
        return new GridSlots.Result(player, grid, result, slot, x, y);
    }

    @WrapOperation(method = "<init>", at = @At(value = "NEW",
        target = "(Lnet/minecraft/world/Container;III)Lnet/minecraft/world/inventory/Slot;", ordinal = 0))
    private Slot firmages$gridInput(Container grid, int slot, int x, int y, Operation<Slot> original, @Local(argsOnly = true) Inventory inventory) {
        return new GridSlots.Input(inventory.player, grid, slot, x, y);
    }

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
    private void firmages$gridLocked(Container changed, CallbackInfo ci) {
        if (GridLockService.refuseGridChange(owner, craftSlots, resultSlots)) ci.cancel();
    }
}
