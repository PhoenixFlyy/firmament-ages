package dev.firmages.core.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.firmages.core.boss.AgeShotEntity;
import dev.firmages.core.boss.ShotAge;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * A shot of the halo (round G4): its Age's 16 x 16 sprite ({@code textures/entity/shot_<age>.png}, dev/gen_primordial.py)
 * as a camera-facing, full-bright quad that turns as it flies, with a soft flare in the Age's colour behind it. A gravity
 * well (Space, Singularity) is drawn three times as large and spinning.
 */
public final class AgeShotRenderer extends EntityRenderer<AgeShotEntity> {
    private static final ResourceLocation[] SPRITES = new ResourceLocation[ShotAge.COUNT];

    static {
        for (ShotAge a : ShotAge.values()) SPRITES[a.ordinal()] = PrimordialRenderer.tex("shot_" + a.key);
    }

    public AgeShotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public void render(AgeShotEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = e.tickCount + partialTick;
        boolean well = e.isWell();
        float size = well ? 2.6F + 0.3F * Mth.sin(t * 0.3F) : 0.9F;
        pose.pushPose();
        pose.translate(0, e.getBbHeight() / 2, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        VertexConsumer flare = buffers.getBuffer(RenderType.entityTranslucentEmissive(PrimordialRenderer.FLARE));
        PrimordialRenderer.flare(pose, flare, size * 1.8F, FastColor.ARGB32.color(well ? 120 : 160, e.color()));
        pose.mulPose(Axis.ZP.rotationDegrees(t * (well ? 6 : 14)));
        VertexConsumer sprite = buffers.getBuffer(RenderType.entityTranslucentEmissive(SPRITES[e.age().ordinal()]));
        PrimordialRenderer.flare(pose, sprite, size, 0xFFFFFFFF);
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AgeShotEntity e) {
        return SPRITES[e.age().ordinal()];
    }
}
