package dev.firmages.core.mixin.curios;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.firmages.core.client.immersion.GridLockClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.client.gui.CuriosScreen;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;

/**
 * The Curios screen while the Dawn grid lock holds (SPEC §19.3), as the player inventory: Curios 9.5.1
 * {@code CuriosScreen} [verified, javap] blits the vanilla inventory texture at {@code leftPos, topPos} in
 * {@code renderBg} (its slot panel lies left of it), draws its title at 97,6 with
 * {@code GuiGraphics.drawString(Font, Component, int, int, int, boolean)} in {@code renderLabels} and keeps its
 * recipe-book button in the field {@code recipeBookButton} (null in creative). Client only; fails soft.
 */
@Mixin(value = CuriosScreen.class, remap = false)
public abstract class CuriosScreenMixin extends EffectRenderingInventoryScreen<CuriosContainer> {
    @Shadow private ImageButton recipeBookButton;

    private CuriosScreenMixin(CuriosContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("HEAD"), require = 0)
    private void firmages$closeRecipeBook(CallbackInfo ci) {
        GridLockClient.closeRecipeBook();
    }

    @WrapOperation(method = "renderLabels", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"))
    private int firmages$hideTitle(GuiGraphics g, Font font, Component text, int x, int y, int color, boolean shadow, Operation<Integer> original) {
        return GridLockClient.locked() ? 0 : original.call(g, font, text, x, y, color, shadow);
    }

    @Inject(method = "renderBg", at = @At("TAIL"), require = 0)
    private void firmages$paintOverGrid(GuiGraphics g, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        boolean locked = GridLockClient.locked();
        if (recipeBookButton != null) recipeBookButton.visible = !locked;
        if (locked) GridLockClient.paintOver(g, leftPos, topPos);
    }
}
