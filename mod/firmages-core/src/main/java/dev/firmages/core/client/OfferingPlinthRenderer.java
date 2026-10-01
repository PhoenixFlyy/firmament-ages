package dev.firmages.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.firmages.core.shrine.OfferingPlinthBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the item on top of a plinth, slowly turning; an enshrined relic floats higher and glows. */
public final class OfferingPlinthRenderer implements BlockEntityRenderer<OfferingPlinthBlockEntity> {
    private final ItemRenderer items;

    public OfferingPlinthRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(OfferingPlinthBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = be.item();
        if (stack.isEmpty() || be.getLevel() == null) return;
        float time = be.getLevel().getGameTime() + partialTick;
        boolean relic = be.isRelic();
        float bob = relic ? Mth.sin(time / 12F) * 0.06F : 0;
        pose.pushPose();
        pose.translate(0.5, (relic ? 1.25 : 1.05) + bob, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees((time * (relic ? 1.5F : 0.6F)) % 360));
        pose.scale(0.6F, 0.6F, 0.6F);
        int lit = relic ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        items.renderStatic(stack, ItemDisplayContext.GROUND, lit, OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong());
        pose.popPose();
    }
}
