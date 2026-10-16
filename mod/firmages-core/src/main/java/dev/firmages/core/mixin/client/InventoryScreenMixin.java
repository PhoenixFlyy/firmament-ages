package dev.firmages.core.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.firmages.core.client.immersion.GridLockClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The player inventory screen while the Dawn grid lock holds (SPEC §19.3): grid, arrow and result frame are painted
 * over, the "Crafting" title is not drawn and the recipe-book button is hidden; the grid slots themselves are inactive
 * ({@code GridSlots}), so they are neither drawn nor hovered. Everything follows the lock live (an Age change while
 * the screen is open shows the grid at once). Minecraft 1.21.1 {@code InventoryScreen} [verified, javap]: {@code init}
 * adds exactly one widget itself (the recipe-book {@code ImageButton}, {@code addRenderableWidget}),
 * {@code renderLabels} draws only the title ({@code GuiGraphics.drawString(Font, Component, int, int, int, boolean)}),
 * {@code renderBg} blits the inventory texture. Client only; every injection fails soft.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends EffectRenderingInventoryScreen<InventoryMenu> {
    @Unique private AbstractWidget firmages$recipeButton;

    private InventoryScreenMixin(InventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("HEAD"), require = 0)
    private void firmages$closeRecipeBook(CallbackInfo ci) {
        GridLockClient.closeRecipeBook();
    }

    @WrapOperation(method = "init", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
    private GuiEventListener firmages$rememberRecipeButton(InventoryScreen self, GuiEventListener widget, Operation<GuiEventListener> original) {
        if (widget instanceof AbstractWidget w) {
            firmages$recipeButton = w;
            w.visible = !GridLockClient.locked();
        }
        return original.call(self, widget);
    }

    @WrapOperation(method = "renderLabels", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"))
    private int firmages$hideTitle(GuiGraphics g, Font font, Component text, int x, int y, int color, boolean shadow, Operation<Integer> original) {
        return GridLockClient.locked() ? 0 : original.call(g, font, text, x, y, color, shadow);
    }

    @Inject(method = "renderBg", at = @At("TAIL"), require = 0)
    private void firmages$paintOverGrid(GuiGraphics g, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        boolean locked = GridLockClient.locked();
        if (firmages$recipeButton != null) firmages$recipeButton.visible = !locked;
        if (locked) GridLockClient.paintOver(g, leftPos, topPos);
    }
}
