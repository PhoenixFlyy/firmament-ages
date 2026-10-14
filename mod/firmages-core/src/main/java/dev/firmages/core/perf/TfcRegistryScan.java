package dev.firmages.core.perf;

import com.mojang.logging.LogUtils;
import dev.firmages.core.config.EarlyServerConfig;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runtime side of {@link RegistryScanScope} for {@code mixin/tfcregistryapi/HelpersMixin} (SPEC §20). Runs during mod
 * construction, before any NeoForge config is loaded, so the switch {@value #CONFIG_KEY} is read straight from
 * {@code firmages-server.toml} ({@link EarlyServerConfig}, default true).
 *
 * <p>A mod file that is a directory is left out as well: the API opens every file with
 * {@code FileSystems.newFileSystem(path, null)}, which throws {@code ProviderNotFoundException} for a directory and
 * aborts mod loading. Only dev runs have such files (firmages-core's own classes in a GameTest run); in the pack every
 * mod file is a jar, so the scope there is exactly {@link RegistryScanScope#select}.
 */
public final class TfcRegistryScan {
    public static final String CONFIG_KEY = "perf.limitTfcRegistryApiScan";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Boolean enabled;
    private static volatile boolean logged;

    private TfcRegistryScan() {}

    public static boolean enabled() {
        Boolean e = enabled;
        if (e == null) {
            e = EarlyServerConfig.bool(EarlyServerConfig.dedicatedWorldDir(), CONFIG_KEY).orElse(true);
            enabled = e;
            if (!e) LOGGER.info("TFCRegistryAPI enum scan: {} = false, scanning every mod file", CONFIG_KEY);
        }
        return e;
    }

    /** The mods of {@code ModList.getMods()} whose files TFCRegistryAPI scans; all of them when switched off. */
    public static List<IModInfo> limit(List<IModInfo> mods) {
        if (!enabled()) return mods;
        List<IModInfo> kept = new ArrayList<>(RegistryScanScope.select(mods, TfcRegistryScan::entry, RegistryScanScope.ROOTS));
        kept.removeIf(m -> Files.isDirectory(m.getOwningFile().getFile().getFilePath()));
        if (!logged) {
            logged = true;
            long files = mods.stream().map(m -> entry(m).file()).distinct().count();
            long keptFiles = kept.stream().map(m -> entry(m).file()).distinct().count();
            LOGGER.info("TFCRegistryAPI enum scan limited to {} of {} mod files (mods that are or depend on {}): {}",
                keptFiles, files, RegistryScanScope.ROOTS,
                kept.stream().map(IModInfo::getModId).collect(Collectors.joining(", ")));
        }
        return kept;
    }

    private static RegistryScanScope.ModEntry entry(IModInfo mod) {
        return new RegistryScanScope.ModEntry(
            mod.getOwningFile().getFile().getFilePath().toString(),
            mod.getModId(),
            mod.getDependencies().stream().map(IModInfo.ModVersion::getModId).toList());
    }
}
