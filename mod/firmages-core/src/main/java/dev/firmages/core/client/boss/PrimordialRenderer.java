package dev.firmages.core.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.boss.PrimordialAbility;
import dev.firmages.core.boss.PrimordialEntity;
import dev.firmages.core.boss.PrimordialGeometry;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The Primordial on the client: the GeckoLib model in translucent dark glass, two emissive layers (the nodes and gold
 * edges always, the eye from phase 3 on), a flare on the node that charges or washes, and the ceremony beam (the
 * vanilla beacon beam, as {@code ShrineHeartRenderer} draws it) from the lit node to the floor while a sweep burns.
 * The node's position is read from the rendered bone (so animations that tilt the body move the beam with it).
 */
public final class PrimordialRenderer extends GeoEntityRenderer<PrimordialEntity> {
    static final ResourceLocation GLOW = tex("primordial_glowmask");
    static final ResourceLocation EYE_GLOW = tex("primordial_glowmask_eye");
    static final ResourceLocation FLARE = tex("primordial_flare");

    /** The lit node's centre in the base pose's frame of this frame (captured while the model renders), or null. */
    private Vector3f litNode;
    private Matrix4f basePose;

    public PrimordialRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PrimordialModel());
        this.shadowRadius = 0;
        addRenderLayer(new GlowLayer(this, GLOW, e -> true));
        addRenderLayer(new GlowLayer(this, EYE_GLOW, e -> e.phase() >= 3));
        addRenderLayer(new NodeTracker(this));
    }

    static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "textures/entity/" + name + ".png");
    }

    @Override
    public void render(PrimordialEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        litNode = null;
        basePose = new Matrix4f(pose.last().pose());
        super.render(entity, yaw, partialTick, pose, buffers, light);
        if (litNode == null || entity.isDeadOrDying()) return;
        int node = entity.litNode();
        int color = PrimordialGeometry.NODE_COLORS[node];
        long time = entity.level().getGameTime();
        float beam = entity.beamYaw();
        if (beam != PrimordialEntity.NO_BEAM) {
            Vector3f to = PrimordialGeometry.beamTarget(beam).toVector3f().sub(litNode);
            float len = to.length();
            pose.pushPose();
            pose.translate(litNode.x, litNode.y, litNode.z);
            pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, to.x / len, to.y / len, to.z / len));
            pose.translate(-0.5F, 0, -0.5F);
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0F, time, 0, Mth.ceil(len),
                FastColor.ARGB32.opaque(color), 0.25F, 0.32F);
            pose.popPose();
        }
        // the flare: white while a volley charges, the node's colour while a sweep washes or burns
        boolean charge = entity.ability() == PrimordialAbility.NODE_VOLLEY;
        float pulse = 0.5F + 0.5F * Mth.sin((time + partialTick) * 0.6F);
        float size = (charge ? 1.6F : 1.2F) + 0.4F * pulse;
        int c = charge ? 0xF4F0FF : color;
        pose.pushPose();
        pose.translate(litNode.x, litNode.y, litNode.z);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        flare(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(FLARE)), size, FastColor.ARGB32.color(230, c));
        pose.popPose();
    }

    /** A camera-facing quad of the soft flare texture, {@code size} blocks across. */
    static void flare(PoseStack pose, VertexConsumer vc, float size, int argb) {
        PoseStack.Pose p = pose.last();
        float h = size / 2;
        vertex(vc, p, -h, -h, 0, 1, argb);
        vertex(vc, p, h, -h, 1, 1, argb);
        vertex(vc, p, h, h, 1, 0, argb);
        vertex(vc, p, -h, h, 0, 0, argb);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float u, float v, int argb) {
        vc.addVertex(p, x, y, 0).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
    }

    @Override
    protected float getDeathMaxRotation(PrimordialEntity entity) {
        return 0; // it does not topple: the die animation collapses it
    }

    @Override
    public boolean shouldShowName(PrimordialEntity entity) {
        return false; // the boss bar carries the name
    }

    /** Re-renders the model with an emissive texture at full brightness while {@code when} holds. */
    static final class GlowLayer extends GeoRenderLayer<PrimordialEntity> {
        private final ResourceLocation texture;
        private final java.util.function.Predicate<PrimordialEntity> when;

        GlowLayer(GeoRenderer<PrimordialEntity> renderer, ResourceLocation texture, java.util.function.Predicate<PrimordialEntity> when) {
            super(renderer);
            this.texture = texture;
            this.when = when;
        }

        @Override
        public void render(PoseStack pose, PrimordialEntity entity, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int light, int overlay) {
            if (!when.test(entity)) return;
            RenderType glow = RenderType.entityTranslucentEmissive(texture);
            getRenderer().reRender(model, pose, buffers, entity, glow, buffers.getBuffer(glow), partialTick, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
    }

    /** Captures where the lit node is drawn this frame (the node cube's centre in its shard bone). */
    final class NodeTracker extends GeoRenderLayer<PrimordialEntity> {
        NodeTracker(GeoRenderer<PrimordialEntity> renderer) {
            super(renderer);
        }

        @Override
        public void renderForBone(PoseStack pose, PrimordialEntity entity, GeoBone bone, RenderType renderType, MultiBufferSource buffers,
                                  VertexConsumer buffer, float partialTick, int light, int overlay) {
            int node = entity.litNode();
            if (node < 0 || basePose == null || !bone.getName().equals("shard_" + node)) return;
            Vector3f c = PrimordialGeometry.nodeCentreModel();
            pose.last().pose().transformPosition(c);
            litNode = new Matrix4f(basePose).invert().transformPosition(c);
        }
    }
}
