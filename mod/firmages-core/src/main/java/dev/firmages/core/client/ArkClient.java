package dev.firmages.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.firmages.core.ark.ArkRules;
import dev.firmages.core.net.ArkSyncPayload;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Archipelago on the client (SPEC §31.8): a beacon beam in the island's colour rises from each socket of a complete
 * island in the ring of nine around the altar (the heart's beam routine, vanilla {@link BeaconRenderer}), and when the
 * ninth lights the FINALE sky tint shows once more for ten seconds. State from {@code firmages:ark_sync}.
 */
public final class ArkClient {
    /** The FINALE ceremony's sky tint (CeremonyService.startFinale). */
    private static final int FINALE_TINT = 0xD9C2FF;
    private static final int TINT_TICKS = 200;
    private static final int BEAM_HEIGHT = 1024;

    private static volatile int complete;
    private static volatile int[] colors = new int[0];
    /** Client game time until which the tint shows, -1 for none. */
    private static long tintUntil = -1;

    private ArkClient() {}

    static void onSync(ArkSyncPayload p) {
        complete = p.complete();
        colors = p.colors().clone();
        Minecraft mc = Minecraft.getInstance();
        if (p.celebrate() && mc.level != null) tintUntil = mc.level.getGameTime() + TINT_TICKS;
    }

    static void reset() {
        complete = 0;
        colors = new int[0];
        tintUntil = -1;
    }

    /** The FINALE tint while it shows, as {r, g, b, strength}, or null. */
    @Nullable
    static float[] skyTint(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (tintUntil < 0 || mc.level == null || mc.level.dimension() != OriginRegistry.ORIGIN) return null;
        double left = tintUntil - (mc.level.getGameTime() + partialTick);
        if (left <= 0) {
            tintUntil = -1;
            return null;
        }
        double gone = TINT_TICKS - left;
        float s = (float) (Mth.clamp(gone / 20.0, 0, 1) * Mth.clamp(left / 40.0, 0, 1));
        return new float[] {((FINALE_TINT >> 16) & 0xFF) / 255F, ((FINALE_TINT >> 8) & 0xFF) / 255F, (FINALE_TINT & 0xFF) / 255F, s};
    }

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        int mask = complete;
        int[] cols = colors;
        Minecraft mc = Minecraft.getInstance();
        if (mask == 0 || mc.level == null || mc.level.dimension() != OriginRegistry.ORIGIN) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long time = mc.level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (int i = 0; i < cols.length; i++) {
            if ((mask & (1 << i)) == 0) continue;
            int[] s = ArkRules.socket(i);
            pose.pushPose();
            pose.translate(OriginArena.ALTAR.getX() + s[0] - cam.x, OriginArena.ALTAR.getY() - cam.y, OriginArena.ALTAR.getZ() + s[1] - cam.z);
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partial, 1.0F, time, 0, BEAM_HEIGHT, cols[i], 0.12F, 0.18F);
            pose.popPose();
        }
        buffers.endBatch();
    }
}
