package dev.firmages.core.compat.curios;

import dev.firmages.core.FirmagesCore;
import net.minecraft.client.KeyMapping;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Curios' open key while the inventory buttons are not shown (SPEC §18.2). Curios 9.5.1 [verified, javap]:
 * {@code top.theillusivec4.curios.client.KeyRegistry.openCurios} is a {@code public static KeyMapping} ("G"), and
 * {@code ClientEventHandler.onClientTick} ({@code ClientTickEvent.Post}) sends {@code CPacketOpenCurios} for every
 * {@code consumeClick()}, with or without a screen. Draining the clicks earlier in the tick makes the key do nothing.
 * By name only: no compile dependency, nothing happens without Curios. Client only.
 */
public final class CuriosKey {
    private static Field field;
    private static boolean resolved;

    private CuriosKey() {}

    private static Field field() {
        if (!resolved) {
            resolved = true;
            if (ModList.get().isLoaded("curios")) {
                try {
                    Field f = Class.forName("top.theillusivec4.curios.client.KeyRegistry").getField("openCurios");
                    if (Modifier.isStatic(f.getModifiers()) && KeyMapping.class.isAssignableFrom(f.getType())) field = f;
                    else FirmagesCore.LOGGER.warn("Curios key gate off: KeyRegistry.openCurios is not a static KeyMapping");
                } catch (ReflectiveOperationException | LinkageError e) {
                    FirmagesCore.LOGGER.warn("Curios key gate off: cannot find KeyRegistry.openCurios ({})", e.toString());
                }
            }
        }
        return field;
    }

    /** Drops every pending press of the Curios key; @return how many were dropped. */
    public static int drain() {
        Field f = field();
        if (f == null) return 0;
        try {
            if (!(f.get(null) instanceof KeyMapping key)) return 0;
            int n = 0;
            while (key.consumeClick()) n++;
            return n;
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }
}
