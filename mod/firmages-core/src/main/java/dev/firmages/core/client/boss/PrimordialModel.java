package dev.firmages.core.client.boss;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.boss.PrimordialEntity;
import dev.firmages.core.boss.PrimordialGeometry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Primordial's model (geo, animations and texture under assets/firmages/.../primordial, dev/gen_primordial.py). The
 * halo's tilt and spin are not keyframed: they are set here from {@link PrimordialGeometry}, the same closed form the
 * server uses for node hits, so the node a player aims at is where the server looks for it. The tilt eases toward the
 * phase's value instead of snapping.
 */
public final class PrimordialModel extends DefaultedEntityGeoModel<PrimordialEntity> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "primordial");

    public PrimordialModel() {
        super(ID);
    }

    @Override
    public RenderType getRenderType(PrimordialEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture); // dark glass: the void cube shows through the core
    }

    @Override
    public void setCustomAnimations(PrimordialEntity entity, long instanceId, AnimationState<PrimordialEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);
        float want = PrimordialGeometry.tilt(entity.phase());
        entity.clientTilt = Float.isNaN(entity.clientTilt) ? want : Mth.lerp(0.05F, entity.clientTilt, want);
        GeoBone halo = getAnimationProcessor().getBone("halo");
        GeoBone spin = getAnimationProcessor().getBone("halo_spin");
        if (halo != null) halo.setRotX(entity.clientTilt);
        if (spin != null) spin.setRotY(PrimordialGeometry.spin(entity.level().getGameTime() + state.getPartialTick()));
    }
}
