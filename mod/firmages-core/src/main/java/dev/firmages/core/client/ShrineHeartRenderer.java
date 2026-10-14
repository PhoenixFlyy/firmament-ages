package dev.firmages.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.net.ShrineSyncPayload;
import dev.firmages.core.shrine.ShrineHeartBlock;
import dev.firmages.core.shrine.ShrineHeartBlockEntity;
import dev.firmages.core.shrine.ShrineRules;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws the beam above the heart (vanilla beacon beam): the ceremony beam in the Age colour while a ceremony runs,
 * otherwise the thin idle beam of the living shrine (SPEC §7.10) in the colour of the latest awakened Age, wider
 * and gold after the finale, none while the shrine is broken or the heart is cold.
 */
public final class ShrineHeartRenderer implements BlockEntityRenderer<ShrineHeartBlockEntity> {
    private static final int BEAM_HEIGHT = 1024;

    public ShrineHeartRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ShrineHeartBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float s = CeremonyPlayer.beamStrength(be.getBlockPos(), partialTick);
        if (s > 0) {
            int c = CeremonyPlayer.beamColor();
            int color = FastColor.ARGB32.color((int) (255 * s), FastColor.ARGB32.red(c), FastColor.ARGB32.green(c), FastColor.ARGB32.blue(c));
            float clock = CeremonyPlayer.clock(partialTick);
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, clock - (int) clock, 1.0F, (long) clock, 1, BEAM_HEIGHT,
                color, 0.15F + 0.1F * s, 0.2F + 0.15F * s);
            return;
        }
        int idle = idleColor(be);
        if (idle == 0 || be.getLevel() == null) return;
        float r = idle == ShrineRules.FINALE_BEAM ? 1.5F : 1.0F; // "Crowned"
        BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, be.getLevel().getGameTime(), 1, BEAM_HEIGHT,
            idle, 0.08F * r, 0.12F * r);
    }

    /** The idle beam's colour for this heart, 0 for none (client toggle, server config, state). */
    private static int idleColor(ShrineHeartBlockEntity be) {
        if (be.getLevel() == null || !ClientConfig.get(ClientConfig.IDLE_BEAM, true)) return 0;
        BlockState st = be.getBlockState();
        if (!st.hasProperty(ShrineHeartBlock.AWAKENED)) return 0;
        return ShrineSyncPayload.clientIdleBeamColor(be.getLevel().dimension().location().toString(), be.getBlockPos(),
            st.getValue(ShrineHeartBlock.AWAKENED), st.getValue(ShrineHeartBlock.LIT));
    }

    @Override
    public boolean shouldRenderOffScreen(ShrineHeartBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(ShrineHeartBlockEntity be) {
        return CeremonyPlayer.running() || idleColor(be) != 0 ? AABB.INFINITE : new AABB(be.getBlockPos());
    }
}
