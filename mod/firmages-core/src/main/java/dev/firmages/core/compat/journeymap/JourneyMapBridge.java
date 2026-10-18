package dev.firmages.core.compat.journeymap;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.immersion.MapGate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * JourneyMap lookups for the map gate (SPEC §18.2, {@link MapGate}). JourneyMap 6.0.9 [verified, javap]: the public API
 * implementation {@code journeymap.api.client.impl.ClientAPI} is an enum with the constant {@code INSTANCE};
 * {@code toggleMinimap(boolean)} sets and {@code minimapEnabled()} reads {@code InternalStateHandler.minimapEnabledApi},
 * which {@code MiniMap.drawMap} checks before drawing anything. The keys are found by name among the game's key mappings.
 * By name only: no compile dependency, nothing happens without JourneyMap. Client only.
 */
public final class JourneyMapBridge {
    private static final String CLIENT_API = "journeymap.api.client.impl.ClientAPI";
    private static boolean resolved;
    private static Object api;
    private static Method toggle;
    private static Method enabled;
    private static List<KeyMapping> keys;

    private JourneyMapBridge() {}

    public static boolean loaded() {
        return ModList.get().isLoaded("journeymap");
    }

    private static boolean resolve() {
        if (!resolved) {
            resolved = true;
            if (loaded()) {
                try {
                    Class<?> c = Class.forName(CLIENT_API);
                    Object instance = c.getField("INSTANCE").get(null);
                    Method t = c.getMethod("toggleMinimap", boolean.class);
                    Method e = c.getMethod("minimapEnabled");
                    if (e.getReturnType() == boolean.class) {
                        api = instance;
                        toggle = t;
                        enabled = e;
                    } else FirmagesCore.LOGGER.warn("JourneyMap minimap gate off: ClientAPI.minimapEnabled() is not boolean");
                } catch (ReflectiveOperationException | LinkageError ex) {
                    FirmagesCore.LOGGER.warn("JourneyMap minimap gate off: cannot find {} ({})", CLIENT_API, ex.toString());
                }
            }
        }
        return api != null;
    }

    /** JourneyMap's minimap API flag, or {@code null} if JourneyMap is missing or not ready. */
    public static Boolean minimapEnabled() {
        if (!resolve()) return null;
        try {
            return (Boolean) enabled.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null; // the state handler is not there before JourneyMap's client init
        }
    }

    /** Sets JourneyMap's minimap API flag; @return true if it was set. */
    public static boolean setMinimap(boolean on) {
        if (!resolve()) return false;
        try {
            toggle.invoke(api, on);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Sets JourneyMap's {@code CoreProperties.splashViewed} to the running version, so {@code Fullscreen.init} does not
     * open the splash ({@code UIManager.openSplash} closes the full map first). JourneyMap 6.0.9 [verified, javap]:
     * {@code JourneymapClient.getInstance().getCoreProperties().splashViewed} (public {@code StringField} with
     * {@code get()}, {@code set(String)}, {@code save()}), version {@code Journeymap.JM_VERSION.toString()}.
     *
     * @return true if it was written now
     */
    public static boolean markSplashViewed() {
        if (!loaded()) return false;
        try {
            String version = String.valueOf(Class.forName("journeymap.common.Journeymap").getField("JM_VERSION").get(null));
            Class<?> client = Class.forName("journeymap.client.JourneymapClient");
            Object core = client.getMethod("getCoreProperties").invoke(client.getMethod("getInstance").invoke(null));
            Object field = core.getClass().getField("splashViewed").get(core);
            Object viewed = field.getClass().getMethod("get").invoke(field);
            if (!MapGate.splashPending(viewed == null ? null : viewed.toString(), version)) return false;
            field.getClass().getMethod("set", String.class).invoke(field, version);
            field.getClass().getMethod("save").invoke(field);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            FirmagesCore.LOGGER.warn("JourneyMap splash not marked as viewed ({})", e.toString());
            return false;
        }
    }

    /** Drops every pending press of JourneyMap's keys; @return how many were dropped. */
    public static int drainKeys() {
        if (!loaded()) return 0;
        if (keys == null) {
            List<KeyMapping> found = new ArrayList<>();
            for (KeyMapping k : Minecraft.getInstance().options.keyMappings) if (MapGate.isMapKey(k.getName())) found.add(k);
            keys = List.copyOf(found); // registered once at start-up (RegisterKeyMappingsEvent)
        }
        int n = 0;
        for (KeyMapping k : keys) while (k.consumeClick()) n++;
        return n;
    }
}
