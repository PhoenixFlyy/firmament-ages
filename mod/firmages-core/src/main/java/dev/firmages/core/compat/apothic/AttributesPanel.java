package dev.firmages.core.compat.apothic;

import dev.firmages.core.FirmagesCore;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Apothic Attributes' panel state while the inventory buttons are not shown (SPEC §18.2). ApothicAttributes 2.11.0
 * [verified, javap]: {@code AttributesLibClient.addAttribComponent} ({@code ScreenEvent.Init.Post}, HIGH) opens the
 * panel again ({@code toggleVisibility}, which also shifts the inventory to the right) when the static
 * {@code AttributesGui.wasOpen} (public) or {@code swappedFromCurios} (protected) is true. Both are cleared before the
 * inventory lays itself out, so a panel opened in a later Age (or before an Age revoke) does not leave the inventory
 * shifted once the panel is removed. By name only: no compile dependency, nothing happens without the mod. Client only.
 */
public final class AttributesPanel {
    private static final String GUI = "dev.shadowsoffire.apothic_attributes.client.AttributesGui";
    private static Field wasOpen;
    private static Field swappedFromCurios;
    private static boolean resolved;

    private AttributesPanel() {}

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        if (!ModList.get().isLoaded("apothic_attributes")) return;
        try {
            Class<?> gui = Class.forName(GUI);
            wasOpen = staticBoolean(gui, "wasOpen");
            swappedFromCurios = staticBoolean(gui, "swappedFromCurios");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            FirmagesCore.LOGGER.warn("Apothic Attributes panel reset off: {}", e.toString());
        }
    }

    private static Field staticBoolean(Class<?> owner, String name) throws ReflectiveOperationException {
        Field f = owner.getDeclaredField(name);
        if (!Modifier.isStatic(f.getModifiers()) || f.getType() != boolean.class) throw new NoSuchFieldException(name + " is not a static boolean");
        f.setAccessible(true);
        return f;
    }

    /** Keeps the panel closed on the next inventory layout. */
    public static void keepClosed() {
        resolve();
        try {
            if (wasOpen != null) wasOpen.setBoolean(null, false);
            if (swappedFromCurios != null) swappedFromCurios.setBoolean(null, false);
        } catch (ReflectiveOperationException e) {
            FirmagesCore.LOGGER.debug("Apothic Attributes panel reset failed: {}", e.toString());
        }
    }
}
