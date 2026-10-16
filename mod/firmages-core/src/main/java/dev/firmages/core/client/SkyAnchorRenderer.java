package dev.firmages.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.firmages.core.anchor.AnchorRules;
import dev.firmages.core.anchor.BlessingShardItem;
import dev.firmages.core.anchor.SkyAnchorBlockEntity;
import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.shrine.BlessingSources;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

/**
 * A Sky Anchor (SPEC-himmel §29.6): its shards circle the top in their tier colours (the shard item is tinted by
 * {@link FirmagesClient}), and an active anchor shows the lit-Wayshrine beam in the colour of its highest tier in force,
 * hidden by the client toggle {@code effects.idleBeam} like the Wayshrine beams.
 */
public final class SkyAnchorRenderer implements BlockEntityRenderer<SkyAnchorBlockEntity> {
    private static final int BEAM_HEIGHT = 1024;
    private final ItemRenderer items;

    public SkyAnchorRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(SkyAnchorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        if (beam(be) != 0) {
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, be.getLevel().getGameTime(), 1, BEAM_HEIGHT,
                be.beamColor(), 0.15F, 0.22F);
        }
        int n = AnchorRules.count(be.tiers());
        if (n == 0) return;
        float time = be.getLevel().getGameTime() + partialTick;
        int lightAbove = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        int i = 0;
        for (int tier = 0; tier < BlessingSources.TIERS; tier++) {
            if (!AnchorRules.holds(be.tiers(), tier)) continue;
            float angle = time * 1.2F + i * 360.0F / n;
            pose.pushPose();
            pose.translate(0.5, 1.15 + 0.04 * Math.sin((time + i * 9) * 0.08), 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(angle % 360));
            pose.translate(0.38, 0, 0);
            pose.mulPose(Axis.YP.rotationDegrees(90));
            pose.scale(0.32F, 0.32F, 0.32F);
            items.renderStatic(BlessingShardItem.of(tier), ItemDisplayContext.FIXED, lightAbove, OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(),
                (int) be.getBlockPos().asLong() + tier);
            pose.popPose();
            i++;
        }
    }

    private static int beam(SkyAnchorBlockEntity be) {
        return ClientConfig.get(ClientConfig.IDLE_BEAM, true) ? be.beamColor() : 0;
    }

    @Override
    public boolean shouldRenderOffScreen(SkyAnchorBlockEntity be) {
        return beam(be) != 0;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(SkyAnchorBlockEntity be) {
        return beam(be) != 0 ? AABB.INFINITE : new AABB(be.getBlockPos()).inflate(0.5).expandTowards(0, 1, 0);
    }
}
