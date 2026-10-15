package dev.firmages.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.crown.LensMountBlock;
import dev.firmages.core.crown.LensMountBlockEntity;
import dev.firmages.core.net.ShrineSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * The lens in a mount (SPEC §27.11): the greyscale lens glass as a quad tinted with its tier colour (from
 * {@code firmages:shrine_sync}), upright when out and tilted 45 degrees toward the anvil when in, swinging smoothly in
 * between.
 */
public final class LensMountRenderer implements BlockEntityRenderer<LensMountBlockEntity> {
    /** Height of the swing axis in the block (the posts' pins), shared with the beam origin of {@link CrownRenderer}. */
    public static final float PIVOT_Y = 9.5F / 16F;
    private static final float HALF = 5F / 16F;
    private static final ResourceLocation GLASS = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "block/lens_glass");

    public LensMountRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(LensMountBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int idx = be.lensIndex();
        if (idx < 0) return;
        Direction facing = be.getBlockState().hasProperty(LensMountBlock.FACING) ? be.getBlockState().getValue(LensMountBlock.FACING) : Direction.NORTH;
        int max = Math.max(1, be.swingMax());
        float step = be.target() ? partialTick : -partialTick;
        float frac = Math.max(0F, Math.min(1F, (be.swing() + (be.swing() > 0 && be.swing() < max ? step : 0)) / max));
        int rgb = ShrineSyncPayload.clientTierColor(idx).orElse(0xFFFFFF);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(GLASS);
        pose.pushPose();
        pose.translate(0.5, PIVOT_Y, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(-45F * frac)); // top toward local -Z, which is the facing (the anvil)
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose p = pose.last();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = 210;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        // front (toward -Z) and back, so the lens shows from both sides
        vertex(vc, p, -HALF, -HALF, u0, v1, r, g, b, a, light, -1);
        vertex(vc, p, -HALF, HALF, u0, v0, r, g, b, a, light, -1);
        vertex(vc, p, HALF, HALF, u1, v0, r, g, b, a, light, -1);
        vertex(vc, p, HALF, -HALF, u1, v1, r, g, b, a, light, -1);
        vertex(vc, p, HALF, -HALF, u1, v1, r, g, b, a, light, 1);
        vertex(vc, p, HALF, HALF, u1, v0, r, g, b, a, light, 1);
        vertex(vc, p, -HALF, HALF, u0, v0, r, g, b, a, light, 1);
        vertex(vc, p, -HALF, -HALF, u0, v1, r, g, b, a, light, 1);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float u, float v, int r, int g, int b, int a, int light, int nz) {
        vc.addVertex(p, x, y, 0).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, nz);
    }
}
