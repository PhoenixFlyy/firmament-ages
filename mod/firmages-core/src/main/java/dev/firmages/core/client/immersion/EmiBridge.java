package dev.firmages.core.client.immersion;

import dev.firmages.core.FirmagesCore;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * EMI's global visibility flag (SPEC §18.3). EMI 1.1.24 [verified, research ux-screens §5.1, jar and source tag]:
 * {@code dev.emi.emi.config.EmiConfig.enabled} is a {@code public static boolean}; while false, EMI renders nothing and
 * handles no mouse or keys except its toggle. EMI has no API for it, so it is set by reflection (no compile-time
 * dependency). The value is not written to {@code emi.css}, so the shipped file stays as it is.
 */
final class EmiBridge {
    private static Field enabled;
    private static boolean resolved;

    private EmiBridge() {}

    private static Field field() {
        if (!resolved) {
            resolved = true;
            if (ModList.get().isLoaded("emi")) {
                try {
                    Field f = Class.forName("dev.emi.emi.config.EmiConfig").getField("enabled");
                    if (Modifier.isStatic(f.getModifiers()) && f.getType() == boolean.class) enabled = f;
                    else FirmagesCore.LOGGER.warn("EMI gating off: EmiConfig.enabled is not a static boolean");
                } catch (ReflectiveOperationException | LinkageError e) {
                    FirmagesCore.LOGGER.warn("EMI gating off: cannot find EmiConfig.enabled ({})", e.toString());
                }
            }
        }
        return enabled;
    }

    static boolean available() {
        return field() != null;
    }

    static boolean isEnabled() {
        try {
            Field f = field();
            return f != null && f.getBoolean(null);
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    /**
     * {@code dev.emi.emi.runtime.EmiReloadManager.reloadTags()} (public static, EMI 1.1.24 [verified, javap]): marks the
     * tags as synchronized and reloads EMI if the recipes already are, which is the state after a recipe sync without
     * tags (an Age swap). False if EMI is absent or the call failed.
     */
    static boolean reloadTags() {
        return callReloadManager("reloadTags");
    }

    /** {@code EmiReloadManager.reload()}: a full EMI reload with the client's current recipes and tags. */
    static boolean reload() {
        return callReloadManager("reload");
    }

    private static boolean callReloadManager(String method) {
        if (!ModList.get().isLoaded("emi")) return false;
        try {
            Class.forName("dev.emi.emi.runtime.EmiReloadManager").getMethod(method).invoke(null);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            FirmagesCore.LOGGER.warn("EMI: cannot call EmiReloadManager.{} ({})", method, e.toString());
            return false;
        }
    }

    static void setEnabled(boolean value) {
        try {
            Field f = field();
            if (f != null) f.setBoolean(null, value);
        } catch (IllegalAccessException e) {
            FirmagesCore.LOGGER.warn("EMI gating: cannot set EmiConfig.enabled ({})", e.toString());
        }
    }
}
