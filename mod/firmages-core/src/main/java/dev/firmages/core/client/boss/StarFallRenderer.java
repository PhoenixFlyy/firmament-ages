package dev.firmages.core.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.firmages.core.boss.StarFallEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.Vec3;

/** A falling star or node bolt: a bright head in its Age colour and a fading trail of four smaller flares behind it. */
public final class StarFallRenderer extends EntityRenderer<StarFallEntity> {
    public StarFallRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public void render(StarFallEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(PrimordialRenderer.FLARE));
        int c = e.getColor();
        Vec3 back = e.getDeltaMovement().lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : e.getDeltaMovement().normalize().scale(-0.45);
        for (int k = 4; k >= 0; k--) {
            pose.pushPose();
            pose.translate(back.x * k, back.y * k + 0.3, back.z * k);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            int alpha = k == 0 ? 255 : 170 - 30 * k;
            PrimordialRenderer.flare(pose, vc, k == 0 ? 1.1F : 0.8F - 0.12F * k, FastColor.ARGB32.color(alpha, k == 0 ? 0xF4F0FF : c));
            if (k == 0) PrimordialRenderer.flare(pose, vc, 1.8F, FastColor.ARGB32.color(150, c));
            pose.popPose();
        }
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(StarFallEntity e) {
        return PrimordialRenderer.FLARE;
    }
}
