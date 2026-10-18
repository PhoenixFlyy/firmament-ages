package dev.firmages.core.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.firmages.core.FirmagesCore;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The Origin's dimension effects (round G4, SPEC §16.6): the dimension type names {@code firmages:origin} as its
 * effects, registered through {@code RegisterDimensionSpecialEffectsEvent}, so the sky by phase is drawn inside the
 * level renderer's own sky pass ({@link #renderSky}: before the terrain, depth writes off, the level's projection) and
 * the terrain then covers it like any sky. Until round G4 the sky was camera-relative quads at the {@code AFTER_SKY}
 * stage without a depth test; on Felix's client (Sodium and Iris in the pack) they ended up over floors and walls.
 * With an Iris shader pack in use this hook is skipped (Iris draws the End sky of the sky type), only the fog colour
 * follows the mood. Fog and lightmap rules are the End's: fog at 0.15 of the biome colour, never foggy, no sunrise.
 */
public final class OriginSkyEffects extends DimensionSpecialEffects {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "origin");

    public OriginSkyEffects() {
        super(Float.NaN, false, SkyType.END, true, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor.scale(0.15);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    @Nullable
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix,
                             boolean isFoggy, Runnable setupFog) {
        if (OriginSky.irisShaders()) return false;
        setupFog.run();
        FogType fog = camera.getFluidInCamera();
        Minecraft mc = Minecraft.getInstance();
        boolean blocked = mc.player != null && (mc.player.hasEffect(MobEffects.BLINDNESS) || mc.player.hasEffect(MobEffects.DARKNESS));
        if (fog == FogType.POWDER_SNOW || fog == FogType.LAVA || blocked) return true;
        PoseStack pose = new PoseStack();
        pose.mulPose(modelViewMatrix);
        FogRenderer.levelFogColor();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        OriginSky.draw(pose, projectionMatrix, ticks + partialTick, level.getSkyFlashTime() > 0);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        return true;
    }
}
