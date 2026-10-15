package dev.firmages.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.crown.CrownService;
import dev.firmages.core.crown.LensMountBlockEntity;
import dev.firmages.core.crown.StarAnvilBlock;
import dev.firmages.core.crown.StarAnvilBlockEntity;
import dev.firmages.core.net.ShrineSyncPayload;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * The Star Anvil's renderer (SPEC §27.11): a thin beam in the tier colour from every lens that is in down to the
 * anvil top (the vanilla beacon beam, posed along the segment, as {@link ShrineHeartRenderer} uses it), a flash down
 * the beams when a forging completes, a short white column into the sky while it forges, the inputs circling above
 * the anvil and the result glowing. Beams follow the client toggle {@code effects.crownBeams}.
 */
public final class CrownRenderer implements BlockEntityRenderer<StarAnvilBlockEntity> {
    private static final int COLUMN_HEIGHT = 48;
    private final ItemRenderer items;

    public CrownRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(StarAnvilBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        long gameTime = level.getGameTime();
        float time = gameTime + partialTick;
        renderItems(be, level, time, pose, buffers);
        if (!ClientConfig.get(ClientConfig.CROWN_BEAMS, true) || !be.formed()) return;
        boolean flash = be.flashUntil() > gameTime;
        BlockPos anvil = be.getBlockPos();
        for (BlockPos m : be.mounts()) {
            if (!(level.getBlockEntity(m) instanceof LensMountBlockEntity mb) || !mb.fullyIn()) continue;
            int rgb = ShrineSyncPayload.clientTierColor(mb.lensIndex()).orElse(0xFFFFFF);
            int color = FastColor.ARGB32.color(255, FastColor.ARGB32.red(rgb), FastColor.ARGB32.green(rgb), FastColor.ARGB32.blue(rgb));
            Vector3f from = new Vector3f(m.getX() - anvil.getX() + 0.5F, m.getY() - anvil.getY() + LensMountRenderer.PIVOT_Y, m.getZ() - anvil.getZ() + 0.5F);
            beam(pose, buffers, partialTick, gameTime, from, new Vector3f(0.5F, 1.0F, 0.5F), color, flash ? 0.07F : 0.035F, flash ? 0.11F : 0.06F);
        }
        if (be.getBlockState().getValue(StarAnvilBlock.ACTIVE)) {
            pose.pushPose();
            pose.translate(0, 1.0, 0);
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, gameTime, 0, COLUMN_HEIGHT,
                0xFFFFFFFF, 0.04F, 0.07F);
            pose.popPose();
        }
    }

    /** A beacon beam from {@code from} to {@code to} (block-local coordinates of the anvil). */
    private static void beam(PoseStack pose, MultiBufferSource buffers, float partialTick, long gameTime, Vector3f from, Vector3f to, int color,
                             float radius, float glow) {
        Vector3f d = new Vector3f(to).sub(from);
        float len = d.length();
        if (len < 0.01F) return;
        int h = Math.max(1, Mth.ceil(len));
        pose.pushPose();
        pose.translate(from.x(), from.y(), from.z());
        pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, d.x() / len, d.y() / len, d.z() / len));
        pose.scale(1.0F, len / h, 1.0F);
        pose.translate(-0.5, 0, -0.5); // renderBeaconBeam centres itself on (0.5, 0.5)
        BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, gameTime, 0, h, color, radius, glow);
        pose.popPose();
    }

    private void renderItems(StarAnvilBlockEntity be, Level level, float time, PoseStack pose, MultiBufferSource buffers) {
        List<ItemStack> in = be.inputs();
        int n = (int) in.stream().filter(s -> !s.isEmpty()).count();
        int lit = LevelRenderer.getLightColor(level, be.getBlockPos().above());
        int k = 0;
        for (ItemStack s : in) {
            if (s.isEmpty()) continue;
            double a = Math.toRadians(time * 2.0 + k * 360.0 / Math.max(1, n));
            pose.pushPose();
            pose.translate(0.5 + Math.cos(a) * 0.42, 1.25 + Mth.sin(time / 10F + k) * 0.04, 0.5 + Math.sin(a) * 0.42);
            pose.mulPose(Axis.YP.rotationDegrees((time * 3F) % 360));
            pose.scale(0.35F, 0.35F, 0.35F);
            items.renderStatic(s, ItemDisplayContext.GROUND, lit, OverlayTexture.NO_OVERLAY, pose, buffers, level, (int) be.getBlockPos().asLong() + k);
            pose.popPose();
            k++;
        }
        ItemStack out = be.stack(StarAnvilBlockEntity.OUTPUT);
        if (out.isEmpty()) return;
        pose.pushPose();
        pose.translate(0.5, 1.15, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees((time * 1.2F) % 360));
        pose.scale(0.5F, 0.5F, 0.5F);
        items.renderStatic(out, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, level, (int) be.getBlockPos().asLong());
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(StarAnvilBlockEntity be) {
        return be.formed();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(StarAnvilBlockEntity be) {
        BlockPos p = be.getBlockPos();
        if (!be.formed()) return new AABB(p).expandTowards(0, 1, 0);
        int up = be.getBlockState().getValue(StarAnvilBlock.ACTIVE) ? COLUMN_HEIGHT + 1 : CrownService.HEIGHT;
        return new AABB(p).inflate(CrownService.RADIUS, 0, CrownService.RADIUS).expandTowards(0, up, 0);
    }
}
