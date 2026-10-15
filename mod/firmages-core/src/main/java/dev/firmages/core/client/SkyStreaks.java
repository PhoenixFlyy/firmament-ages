package dev.firmages.core.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.net.StarfallStreakPayload;
import dev.firmages.core.sky.StarfallRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The falling star (SPEC §25.9): a streak across the sky toward the crater in the
 * {@link StarfallRules#STREAK_TICKS} ticks before the impact, and for a player within 256 blocks the glowing body of
 * the last three seconds. Drawn as camera-relative quads right after the sky ({@code AFTER_SKY}, the {@link SkyEffects}
 * pattern), so terrain covers it like the real sky; no entity. Client toggle {@code effects.skyStreaks}.
 */
public final class SkyStreaks {
    private static final List<StarfallStreakPayload> ACTIVE = new ArrayList<>();
    private static final float SKY_RADIUS = 100.0F;

    private SkyStreaks() {}

    /** Additive position-colour quads without depth test or depth write. */
    private static final class StreakType extends RenderType {
        private StreakType() {
            super("firmages_starfall", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true, () -> {}, () -> {});
        }

        static final RenderType STREAK = create("firmages_starfall", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1024, false, true,
            CompositeState.builder()
                .setShaderState(POSITION_COLOR_SHADER)
                .setTransparencyState(LIGHTNING_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setDepthTestState(NO_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false));
    }

    static void start(StarfallStreakPayload payload) {
        synchronized (ACTIVE) {
            ACTIVE.add(payload);
        }
    }

    static void reset() {
        synchronized (ACTIVE) {
            ACTIVE.clear();
        }
    }

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ClientConfig.SKY_STREAKS.get()) return;
        List<StarfallStreakPayload> streaks;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partial;
        synchronized (ACTIVE) {
            ACTIVE.removeIf(p -> now > p.impactTime() + 10);
            if (ACTIVE.isEmpty()) return;
            streaks = List.copyOf(ACTIVE);
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        Matrix4f m = pose.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(StreakType.STREAK);
        for (StarfallStreakPayload p : streaks) draw(vc, m, p, cam, now);
        buffers.endBatch(StreakType.STREAK);
    }

    private static void draw(VertexConsumer vc, Matrix4f m, StarfallStreakPayload p, Vec3 cam, double now) {
        double start = p.impactTime() - StarfallRules.STREAK_TICKS;
        double t = (now - start) / StarfallRules.STREAK_TICKS;
        if (t < 0 || t > 1.05) return;
        Vector3f end = new Vector3f((float) (p.target().getX() + 0.5 - cam.x), (float) (p.target().getY() + 1 - cam.y), (float) (p.target().getZ() + 0.5 - cam.z));
        if (end.lengthSquared() < 1) end.set(0, -1, 0.001F);
        end.normalize();
        if (!p.near()) end.y = Math.max(end.y, 0.03F); // a far star sinks behind the horizon
        end.normalize();
        // the entry point: high in the sky, swung sideways by the seeded angle
        double az = Math.atan2(end.z, end.x) + Math.toRadians((p.startAngle() % 100) - 50);
        Vector3f from = new Vector3f((float) Math.cos(az) * 0.5F, 0.85F, (float) Math.sin(az) * 0.5F).normalize();
        boolean starsteel = "starsteel".equals(p.metal());
        int r = starsteel ? 170 : 255;
        int g = starsteel ? 225 : 215;
        int b = starsteel ? 255 : 160;
        double head = Math.min(1.0, t);
        double tail = Math.max(0.0, head - 0.3);
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            double a0 = tail + (head - tail) * i / segments;
            double a1 = tail + (head - tail) * (i + 1) / segments;
            Vector3f d0 = slerp(from, end, (float) ease(a0)).mul(SKY_RADIUS);
            Vector3f d1 = slerp(from, end, (float) ease(a1)).mul(SKY_RADIUS);
            float w0 = 0.15F + 0.6F * (float) ((a0 - tail) / Math.max(1e-3, head - tail));
            float w1 = 0.15F + 0.6F * (float) ((a1 - tail) / Math.max(1e-3, head - tail));
            int alpha0 = (int) (40 + 180 * (a0 - tail) / Math.max(1e-3, head - tail));
            int alpha1 = (int) (40 + 180 * (a1 - tail) / Math.max(1e-3, head - tail));
            segment(vc, m, d0, d1, w0, w1, r, g, b, alpha0, alpha1);
        }
        // the body: the last three seconds, for a player near the crater, a bright growing glow at the head
        if (p.near() && now >= p.impactTime() - 60) {
            float size = 1.2F + 3.0F * (float) Math.min(1.0, (now - (p.impactTime() - 60)) / 60.0);
            Vector3f h = slerp(from, end, (float) ease(head)).mul(SKY_RADIUS);
            glow(vc, m, h, size, 255, 240, 200, 230);
        } else {
            Vector3f h = slerp(from, end, (float) ease(head)).mul(SKY_RADIUS);
            glow(vc, m, h, 0.9F, 255, 250, 230, 200);
        }
    }

    private static double ease(double t) {
        return t * t; // slow at first, fast into the ground
    }

    private static Vector3f slerp(Vector3f a, Vector3f b, float t) {
        float dot = Math.max(-1F, Math.min(1F, a.dot(b)));
        float theta = (float) Math.acos(dot) * t;
        Vector3f rel = new Vector3f(b).sub(new Vector3f(a).mul(dot));
        if (rel.lengthSquared() < 1e-6F) return new Vector3f(a);
        rel.normalize();
        return new Vector3f(a).mul((float) Math.cos(theta)).add(rel.mul((float) Math.sin(theta)));
    }

    private static void segment(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, float wa, float wb, int r, int g, int bl, int aa, int ab) {
        Vector3f dir = new Vector3f(b).sub(a);
        Vector3f side = new Vector3f(dir).cross(a);
        if (side.lengthSquared() < 1e-6F) return;
        side.normalize();
        vc.addVertex(m, a.x - side.x * wa, a.y - side.y * wa, a.z - side.z * wa).setColor(r, g, bl, aa);
        vc.addVertex(m, a.x + side.x * wa, a.y + side.y * wa, a.z + side.z * wa).setColor(r, g, bl, aa);
        vc.addVertex(m, b.x + side.x * wb, b.y + side.y * wb, b.z + side.z * wb).setColor(r, g, bl, ab);
        vc.addVertex(m, b.x - side.x * wb, b.y - side.y * wb, b.z - side.z * wb).setColor(r, g, bl, ab);
    }

    /** A camera-facing square around {@code c} (camera-relative). */
    private static void glow(VertexConsumer vc, Matrix4f m, Vector3f c, float s, int r, int g, int b, int a) {
        Vector3f n = new Vector3f(c).normalize();
        Vector3f u = Math.abs(n.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f x = new Vector3f(u).cross(n).normalize().mul(s);
        Vector3f y = new Vector3f(n).cross(x).normalize().mul(s);
        vc.addVertex(m, c.x - x.x - y.x, c.y - x.y - y.y, c.z - x.z - y.z).setColor(r, g, b, a);
        vc.addVertex(m, c.x + x.x - y.x, c.y + x.y - y.y, c.z + x.z - y.z).setColor(r, g, b, a);
        vc.addVertex(m, c.x + x.x + y.x, c.y + x.y + y.y, c.z + x.z + y.z).setColor(r, g, b, a);
        vc.addVertex(m, c.x - x.x + y.x, c.y - x.y + y.y, c.z - x.z + y.z).setColor(r, g, b, a);
    }
}
