package dev.firmages.core.mixin.curios;

import dev.firmages.core.hardship.GridLockMenu;
import dev.firmages.core.hardship.GridLockService;
import dev.firmages.core.hardship.GridSlots;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;

/**
 * The Curios screen has its own 2x2 crafting grid with its own recipe lookup (Curios 9.5.1
 * {@code CuriosContainer.slotsChanged} calls {@code RecipeManager.getRecipeFor} itself, not
 * {@code CraftingMenu.slotChangedCraftingGrid}) [verified, javap]. Without this it would be a way around the Dawn
 * grid lock (SPEC §19.3). Its {@code <init>(int, Inventory)} adds the grid's {@code ResultSlot} as slot 0 and the four grid slots as
 * slots 1 to 4, exactly like the player inventory.
 */
@Mixin(value = CuriosContainer.class, remap = false)
public abstract class CuriosContainerMixin implements GridLockMenu {
    @Shadow @Final public Player player;
    @Shadow @Final private CraftingContainer craftMatrix;
    @Shadow @Final private ResultContainer craftResult;

    @Override
    public Player firmages$gridOwner() {
        return player;
    }

    /**
     * After the constructor: the grid's result (slot 0) and the four grid slots (1 to 4) become {@link GridSlots} at
     * the same index and position. Replaced after construction rather than wrapped at their {@code new}: a
     * {@code @WrapOperation} on the {@code NEW} in Curios' constructor did not apply (0/1, 2026-10-16 GameTest).
     */
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;)V", at = @At("TAIL"))
    private void firmages$gridSlots(int windowId, Inventory inventory, CallbackInfo ci) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        GridSlots.replaceGrid(self.slots, player, craftMatrix, craftResult);
    }

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
    private void firmages$gridLocked(Container changed, CallbackInfo ci) {
        if (GridLockService.refuseGridChange(player, craftMatrix, craftResult)) ci.cancel();
    }
}
