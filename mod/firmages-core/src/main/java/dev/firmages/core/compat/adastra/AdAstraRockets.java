package dev.firmages.core.compat.adastra;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.proof.SiloLaunch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ad Astra's rockets for the silo launch (SPEC §22.4). Ad Astra is no compile dependency, so the rocket is found by
 * class name and {@code hasLaunched()} through a method handle. Ad Astra 1.16.26 [verified, javap]: every rocket tier
 * is {@code earth.terrarium.adastra.common.entities.vehicles.Rocket} (public {@code boolean hasLaunched()}, true from
 * the end of the countdown on); the pad is {@code ad_astra:launch_pad} (3x3 parts, 2 pixels high).
 */
public final class AdAstraRockets implements SiloLaunch.Rockets {
    public static final String MOD_ID = "ad_astra";
    static final String ROCKET_CLASS = "earth.terrarium.adastra.common.entities.vehicles.Rocket";
    static final ResourceLocation LAUNCH_PAD = ResourceLocation.fromNamespaceAndPath(MOD_ID, "launch_pad");

    /** Per entity class: the handle of {@code hasLaunched}, or a marker for "not a rocket". */
    private final Map<Class<?>, Object> handles = new ConcurrentHashMap<>();
    private static final Object NONE = new Object();

    @Override
    public boolean isRocket(Entity e) {
        return handle(e.getClass()) != NONE;
    }

    @Override
    public boolean launched(Entity e) {
        Object h = handle(e.getClass());
        if (h == NONE) return false;
        try {
            return (boolean) ((MethodHandle) h).invoke(e);
        } catch (Throwable t) {
            FirmagesCore.LOGGER.warn("Ad Astra: Rocket.hasLaunched() failed ({}); silo launches are not detected", t.toString());
            handles.put(e.getClass(), NONE);
            return false;
        }
    }

    @Override
    public boolean isPad(BlockState state) {
        return LAUNCH_PAD.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private Object handle(Class<?> type) {
        return handles.computeIfAbsent(type, AdAstraRockets::lookup);
    }

    private static Object lookup(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (!ROCKET_CLASS.equals(c.getName())) continue;
            try {
                return MethodHandles.publicLookup().findVirtual(c, "hasLaunched", MethodType.methodType(boolean.class));
            } catch (ReflectiveOperationException e) {
                FirmagesCore.LOGGER.warn("Ad Astra: {} has no public hasLaunched(); silo launches are not detected", ROCKET_CLASS);
                return NONE;
            }
        }
        return NONE;
    }
}
