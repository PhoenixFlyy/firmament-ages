package dev.firmages.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.wayshrine.WayshrineCoreBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * A Wayshrine core (SPEC-himmel §28.8): the beam the server chose (a faint one in the type's colour over a ruin or a
 * restored Wayshrine, the latest awakened tier's colour over a lit one, none while it is quiet), drawn with the heart's
 * vanilla beacon beam up to 1,024 blocks, hidden by the client toggle {@code effects.idleBeam}; and the offering, slowly
 * turning on top.
 */
public final class WayshrineCoreRenderer implements BlockEntityRenderer<WayshrineCoreBlockEntity> {
    private static final int BEAM_HEIGHT = 1024;
    private final ItemRenderer items;

    public WayshrineCoreRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(WayshrineCoreBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        if (beam(be) != 0) {
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, be.getLevel().getGameTime(), 1, BEAM_HEIGHT,
                be.beamColor(), be.beamWidth(), be.beamWidth() * 1.5F);
        }
        ItemStack stack = be.offering();
        if (stack.isEmpty()) return;
        float time = be.getLevel().getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5, 1.15, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees((time * 0.8F) % 360));
        pose.scale(0.55F, 0.55F, 0.55F);
        items.renderStatic(stack, ItemDisplayContext.GROUND, LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above()), OverlayTexture.NO_OVERLAY,
            pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong());
        pose.popPose();
    }

    private static int beam(WayshrineCoreBlockEntity be) {
        return ClientConfig.get(ClientConfig.IDLE_BEAM, true) ? be.beamColor() : 0;
    }

    @Override
    public boolean shouldRenderOffScreen(WayshrineCoreBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(WayshrineCoreBlockEntity be) {
        return beam(be) != 0 ? AABB.INFINITE : new AABB(be.getBlockPos()).expandTowards(0, 1, 0);
    }
}
