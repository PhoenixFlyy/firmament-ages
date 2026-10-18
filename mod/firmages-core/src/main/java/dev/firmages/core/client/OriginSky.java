package dev.firmages.core.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.firmages.core.boss.OriginSkyState;
import dev.firmages.core.boss.OriginSkyState.Mood;
import dev.firmages.core.boss.PrimordialEntity;
import dev.firmages.core.boss.PrimordialGeometry;
import dev.firmages.core.origin.OriginRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * The Origin's sky by the fight (round G3, SPEC §16.6): {@link OriginSkyState} picks the mood from the phase the boss
 * entity syncs (its own entity data, no new packet) and eases it over 2 s. The fog colour follows every mood (that is
 * all a shader pack sees: with an Iris shader pack in use only the fog changes); without one, camera-relative quads
 * right after the sky draw a star field (phase 1 and 2), aurora curtains in the Lens Crown's colours (phase 2), an
 * eclipse with a red horizon and a slowly tilting constellation (phase 3) and a dawn gradient after the boss falls.
 * The End sky of the dimension type stays underneath. No shader files.
 */
public final class OriginSky {
    private OriginSky() {}

    private static final OriginSkyState STATE = new OriginSkyState();
    private static final int STARS = 360;
    private static final float[][] STAR_DIRS = new float[STARS][];
    private static final float D = 60;
    private static int irisCheck;
    private static boolean irisShaders;

    static {
        RandomSource r = RandomSource.create(0x0F1A_A6E5L);
        for (int i = 0; i < STARS; i++) {
            double y = r.nextDouble() * 1.4 - 0.4, a = r.nextDouble() * Math.PI * 2, h = Math.sqrt(1 - y * y);
            STAR_DIRS[i] = new float[] {(float) (Math.cos(a) * h), (float) y, (float) (Math.sin(a) * h), 0.15F + r.nextFloat() * 0.25F};
        }
    }

    private static final class SkyType extends RenderType {
        private SkyType() {
            super("firmages_origin_sky", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true, () -> {}, () -> {});
        }

        static final RenderType SKY = create("firmages_origin_sky", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
            CompositeState.builder()
                .setShaderState(POSITION_COLOR_SHADER)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setDepthTestState(NO_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false));
    }

    static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.level.dimension() != OriginRegistry.ORIGIN) {
            STATE.clear();
            return;
        }
        int phase = 0;
        boolean dying = false;
        double best = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof PrimordialEntity b)) continue;
            double d = b.distanceToSqr(mc.player);
            if (d >= best) continue;
            best = d;
            phase = b.phase();
            dying = b.isDeadOrDying();
        }
        STATE.tick(phase, dying, mc.level.getGameTime());
        if (irisCheck-- <= 0) {
            irisCheck = 100;
            irisShaders = irisShadersInUse();
        }
    }

    /** True when Iris is loaded and a shader pack is on (reflection: Iris is optional). */
    private static boolean irisShadersInUse() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            return (Boolean) api.getMethod("isShaderPackInUse").invoke(inst);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            return false;
        }
    }

    static boolean inOrigin() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == OriginRegistry.ORIGIN;
    }

    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inOrigin()) return;
        float r = event.getRed(), g = event.getGreen(), b = event.getBlue();
        float[][] tint = {{0.10F, 0.32F, 0.30F}, {0.12F, 0.0F, 0.02F}, {0.95F, 0.62F, 0.45F}};
        Mood[] moods = {Mood.AURORA, Mood.ECLIPSE, Mood.DAWN};
        for (int i = 0; i < 3; i++) {
            float w = STATE.weight(moods[i]) * 0.8F;
            r = r * (1 - w) + tint[i][0] * w;
            g = g * (1 - w) + tint[i][1] * w;
            b = b * (1 - w) + tint[i][2] * w;
        }
        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || !inOrigin() || irisShaders) return;
        Minecraft mc = Minecraft.getInstance();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double t = mc.level.getGameTime() + partial;
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(SkyType.SKY);
        float eclipse = STATE.weight(Mood.ECLIPSE), aurora = STATE.weight(Mood.AURORA), dawn = STATE.weight(Mood.DAWN);
        Matrix4f m = pose.last().pose();
        if (eclipse > 0) {
            dome(vc, m, -90, 90, 0, 0, 0, (int) (eclipse * 190), 0, 0, 0, (int) (eclipse * 190));
            dome(vc, m, -12, 0, 140, 10, 10, 0, 170, 12, 8, (int) (eclipse * 200));
            dome(vc, m, 0, 18, 170, 12, 8, (int) (eclipse * 200), 140, 10, 10, 0);
        }
        if (dawn > 0) {
            dome(vc, m, -15, 0, 255, 150, 90, 0, 255, 160, 100, (int) (dawn * 170));
            dome(vc, m, 0, 35, 255, 160, 100, (int) (dawn * 170), 150, 190, 235, (int) (dawn * 120));
            dome(vc, m, 35, 90, 150, 190, 235, (int) (dawn * 120), 120, 160, 225, (int) (dawn * 110));
        }
        // stars: clear in phase 1 and 2, red and tilting in the eclipse, gone at dawn
        float starA = Math.max(0, 1 - dawn);
        if (starA > 0) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees((float) (t * 0.02 % 360) * eclipse));
            Matrix4f sm = pose.last().pose();
            int sr = 255, sg = (int) (255 - 170 * eclipse), sb = (int) (255 - 190 * eclipse);
            for (float[] s : STAR_DIRS) star(vc, sm, s, sr, sg, sb, (int) (starA * 220));
            pose.popPose();
        }
        if (aurora > 0) {
            int[] cols = PrimordialGeometry.NODE_COLORS;
            for (int k = 0; k < 6; k++) curtain(vc, m, k * 60 + 15, 110, 14 + (k % 2) * 6, cols[3 + k], aurora, t, k);
        }
        buffers.endBatch(SkyType.SKY);
    }

    /** A ring band from elevation {@code e0} to {@code e1} (degrees), colour and alpha from the lower to the upper edge. */
    private static void dome(VertexConsumer vc, Matrix4f m, float e0, float e1, int r0, int g0, int b0, int a0, int r1, int g1, int b1, int a1) {
        int seg = 32;
        for (int i = 0; i < seg; i++) {
            double az0 = Math.PI * 2 * i / seg, az1 = Math.PI * 2 * (i + 1) / seg;
            vertex(vc, m, az0, e0, r0, g0, b0, a0);
            vertex(vc, m, az1, e0, r0, g0, b0, a0);
            vertex(vc, m, az1, e1, r1, g1, b1, a1);
            vertex(vc, m, az0, e1, r1, g1, b1, a1);
        }
    }

    /** An aurora curtain over {@code span} degrees of azimuth from {@code start}, waving with time. */
    private static void curtain(VertexConsumer vc, Matrix4f m, float start, float span, float base, int rgb, float w, double t, int k) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        int seg = 24;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.toRadians(start + span * i / seg), a1 = Math.toRadians(start + span * (i + 1) / seg);
            float w0 = (float) Math.sin(Math.PI * i / seg), w1 = (float) Math.sin(Math.PI * (i + 1) / seg);
            float l0 = base + 4 * (float) Math.sin(a0 * 3 + t * 0.03 + k), l1 = base + 4 * (float) Math.sin(a1 * 3 + t * 0.03 + k);
            int m0 = (int) (150 * w * w0), m1 = (int) (150 * w * w1);
            vertex(vc, m, a0, l0, r, g, b, 0);
            vertex(vc, m, a1, l1, r, g, b, 0);
            vertex(vc, m, a1, l1 + 8, r, g, b, m1);
            vertex(vc, m, a0, l0 + 8, r, g, b, m0);
            vertex(vc, m, a0, l0 + 8, r, g, b, m0);
            vertex(vc, m, a1, l1 + 8, r, g, b, m1);
            vertex(vc, m, a1, l1 + 26, r, g, b, 0);
            vertex(vc, m, a0, l0 + 26, r, g, b, 0);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, double az, double elDeg, int r, int g, int b, int a) {
        double el = Math.toRadians(elDeg);
        vc.addVertex(m, (float) (Math.cos(el) * Math.cos(az) * D), (float) (Math.sin(el) * D), (float) (Math.cos(el) * Math.sin(az) * D))
            .setColor(r, g, b, a);
    }

    /** A small quad facing the camera at direction {@code s} (x, y, z, size). */
    private static void star(VertexConsumer vc, Matrix4f m, float[] s, int r, int g, int b, int a) {
        float x = s[0] * D, y = s[1] * D, z = s[2] * D, h = s[3];
        // two axes perpendicular to the direction
        float ux = -s[2], uz = s[0];
        float ul = (float) Math.sqrt(ux * ux + uz * uz);
        if (ul < 1.0E-3F) {
            ux = 1;
            uz = 0;
            ul = 1;
        }
        ux /= ul;
        uz /= ul;
        float vx = s[1] * uz, vy = s[2] * ux - s[0] * uz, vz = -s[1] * ux;
        vc.addVertex(m, x - ux * h - vx * h, y - vy * h, z - uz * h - vz * h).setColor(r, g, b, a);
        vc.addVertex(m, x + ux * h - vx * h, y - vy * h, z + uz * h - vz * h).setColor(r, g, b, a);
        vc.addVertex(m, x + ux * h + vx * h, y + vy * h, z + uz * h + vz * h).setColor(r, g, b, a);
        vc.addVertex(m, x - ux * h + vx * h, y + vy * h, z - uz * h + vz * h).setColor(r, g, b, a);
    }
}
