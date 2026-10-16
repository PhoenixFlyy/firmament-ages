package dev.firmages.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.launch.LaunchRules;
import dev.firmages.core.launch.ProbeCradleBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.phys.AABB;

/**
 * The probe cradle's renderer (SPEC §31.7): the probe standing on the cradle, its climb and its descent, drawn by
 * {@link LaunchFx#draw}. Off-screen rendering and an infinite box while the probe is in the air, view distance 256.
 */
public final class LaunchRenderer implements BlockEntityRenderer<ProbeCradleBlockEntity> {
    private final ItemRenderer items;
    private final BlockRenderDispatcher blocks;

    public LaunchRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
        this.blocks = ctx.getBlockRenderDispatcher();
    }

    private static LaunchFx.Fx fx(ProbeCradleBlockEntity be) {
        return LaunchFx.get(be.getBlockPos()).filter(f -> f.phaseStart() >= be.phaseStart())
            .orElseGet(() -> new LaunchFx.Fx(be.getBlockPos(), be.phase(), be.phaseStart(), be.probe(), be.trench()));
    }

    @Override
    public void render(ProbeCradleBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(be.getLevel() instanceof ClientLevel level)) return;
        LaunchFx.Fx fx = fx(be);
        boolean air = fx.phase() == LaunchRules.Phase.ASCENT || fx.phase() == LaunchRules.Phase.DESCENT;
        if (air && !ClientConfig.get(ClientConfig.LAUNCH_FX, true)) return;
        LaunchFx.draw(fx, pose, buffers, items, blocks, level, level.getGameTime() + partialTick, LaunchFx.restLight(level, be.getBlockPos()));
    }

    @Override
    public boolean shouldRenderOffScreen(ProbeCradleBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(ProbeCradleBlockEntity be) {
        LaunchFx.Fx fx = fx(be);
        if (fx.phase() == LaunchRules.Phase.ASCENT || fx.phase() == LaunchRules.Phase.DESCENT) return AABB.INFINITE;
        return new AABB(be.getBlockPos()).expandTowards(0, 4, 0).inflate(1, 0, 1);
    }
}
