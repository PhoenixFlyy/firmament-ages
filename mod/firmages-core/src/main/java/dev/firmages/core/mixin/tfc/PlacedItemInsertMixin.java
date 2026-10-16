package dev.firmages.core.mixin.tfc;

import dev.firmages.core.hardship.GroundCraft;
import net.dries007.tfc.common.blockentities.InventoryBlockEntity;
import net.dries007.tfc.common.blockentities.PlacedItemBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The ground-craft hint (SPEC §19.3): TFC 4.2.11 lays every item on a pile through
 * {@code PlacedItemBlockEntity.insertItem(Player, ItemStack, int)}, for a right-click on a pile and for the place key
 * (V, {@code PlaceBlockSpecialPacket}, which also starts new piles) [verified, javap]. When it returns true on the
 * server, {@link GroundCraft#onPartLaid} may name the result the pile now makes.
 */
@Mixin(value = PlacedItemBlockEntity.class, remap = false)
public abstract class PlacedItemInsertMixin {
    @Inject(method = "insertItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)Z", at = @At("RETURN"))
    private void firmages$groundCraftHint(Player player, ItemStack stack, int slot, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || player == null) return;
        InventoryBlockEntity<?> self = (InventoryBlockEntity<?>) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide()) return;
        GroundCraft.onPartLaid(player, self.getLevel(), self.getInventory());
    }
}
