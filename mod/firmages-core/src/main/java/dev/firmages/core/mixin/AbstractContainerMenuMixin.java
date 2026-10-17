package dev.firmages.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.firmages.core.hardship.InventoryRowsService;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Locked inventory rows in every menu (SPEC §19.4), whatever slot class a mod uses for the player's inventory.
 * Minecraft 1.21.1 {@code AbstractContainerMenu} [verified, javap]: the public {@code clicked} hands every click type
 * (pickup, quick move, swap, clone, throw, drag, pickup all) to {@code doClick}; the hook sits on {@code clicked},
 * because Sophisticated Core 1.5.13 overrides {@code doClick} with a full copy but ends its own {@code clicked} in
 * {@code super.clicked}, and AE2 19.2.18 likewise [verified, javap]. {@code moveItemStackTo}, which most
 * {@code quickMoveStack}s (Tom's Storage's terminal pull too) use to fill the player's slots, first tops up stacks
 * ({@code Slot.getItem}), then fills empty slots that pass {@code Slot.mayPlace}. Both sides run this, so the client
 * predicts what the server does.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow @Final public NonNullList<Slot> slots;

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void firmages$lockedRowClick(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (slotId >= 0 && slotId < slots.size() && InventoryRowsService.lockedSlot(slots.get(slotId))) ci.cancel();
    }

    @WrapOperation(method = "moveItemStackTo", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack firmages$lockedRowHoldsNothing(Slot slot, Operation<ItemStack> original) {
        return InventoryRowsService.lockedSlot(slot) ? ItemStack.EMPTY : original.call(slot);
    }

    @WrapOperation(method = "moveItemStackTo", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/Slot;mayPlace(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean firmages$lockedRowTakesNothing(Slot slot, ItemStack stack, Operation<Boolean> original) {
        return !InventoryRowsService.lockedSlot(slot) && original.call(slot, stack);
    }
}
