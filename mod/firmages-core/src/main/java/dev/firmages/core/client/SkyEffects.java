package dev.firmages.core.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * Ceremony sky tint (SPEC §8 MVP): the fog colour blends towards the Age tint, and right after the sky a
 * translucent box around the camera tints the sky (terrain draws over it; no depth write, no depth test).
 * Overlay-based on purpose, so it never fights TFC's sky or Enhanced Celestials. [PoC] with Sodium.
 */
public final class SkyEffects {
    private SkyEffects() {}

    /** Translucent position-colour quads without depth test or depth write. */
    private static final class TintType extends RenderType {
        private TintType() {
            super("firmages_sky_tint", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true, () -> {}, () -> {});
        }

        static final RenderType TINT = create("firmages_sky_tint", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder()
                .setShaderState(POSITION_COLOR_SHADER)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setDepthTestState(NO_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false));
    }

    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float[] tint = CeremonyPlayer.skyTint((float) event.getPartialTick());
        if (tint == null) return;
        float s = tint[3] * 0.7F;
        event.setRed(event.getRed() * (1 - s) + tint[0] * s);
        event.setGreen(event.getGreen() * (1 - s) + tint[1] * s);
        event.setBlue(event.getBlue() * (1 - s) + tint[2] * s);
    }

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        float[] tint = CeremonyPlayer.skyTint(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (tint == null) return;
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        Matrix4f m = pose.last().pose();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(TintType.TINT);
        int r = (int) (tint[0] * 255);
        int g = (int) (tint[1] * 255);
        int b = (int) (tint[2] * 255);
        int a = (int) (tint[3] * 110);
        float d = 64;
        // Six faces of a cube around the camera.
        quad(vc, m, r, g, b, a, -d, d, -d, d, d, -d, d, d, d, -d, d, d);     // top
        quad(vc, m, r, g, b, a, -d, -d, -d, -d, -d, d, d, -d, d, d, -d, -d); // bottom
        quad(vc, m, r, g, b, a, -d, -d, -d, d, -d, -d, d, d, -d, -d, d, -d); // north
        quad(vc, m, r, g, b, a, -d, -d, d, -d, d, d, d, d, d, d, -d, d);     // south
        quad(vc, m, r, g, b, a, -d, -d, -d, -d, d, -d, -d, d, d, -d, -d, d); // west
        quad(vc, m, r, g, b, a, d, -d, -d, d, -d, d, d, d, d, d, d, -d);     // east
        buffers.endBatch(TintType.TINT);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, int r, int g, int b, int a,
                             float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x4, y4, z4).setColor(r, g, b, a);
    }
}
