package dev.firmages.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.firmages.core.shrine.ShrineHeartBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.AABB;

/** Draws the ceremony beam above the heart (vanilla beacon beam in the Age colour). */
public final class ShrineHeartRenderer implements BlockEntityRenderer<ShrineHeartBlockEntity> {
    private static final int BEAM_HEIGHT = 1024;

    public ShrineHeartRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ShrineHeartBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float s = CeremonyPlayer.beamStrength(be.getBlockPos(), partialTick);
        if (s <= 0) return;
        int c = CeremonyPlayer.beamColor();
        int color = FastColor.ARGB32.color((int) (255 * s), FastColor.ARGB32.red(c), FastColor.ARGB32.green(c), FastColor.ARGB32.blue(c));
        float clock = CeremonyPlayer.clock(partialTick);
        BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, clock - (int) clock, 1.0F, (long) clock, 1, BEAM_HEIGHT,
            color, 0.15F + 0.1F * s, 0.2F + 0.15F * s);
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
        return CeremonyPlayer.running() ? AABB.INFINITE : new AABB(be.getBlockPos());
    }
}
