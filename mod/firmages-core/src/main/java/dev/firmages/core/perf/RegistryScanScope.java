package dev.firmages.core.perf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Which mod files TFCRegistryAPI has to scan for registry enums (SPEC §20). Pure, no Minecraft.
 *
 * <p>TFCRegistryAPI 1.2 looks for enums that implement one of seven interfaces: TFC's {@code RegistryMetal},
 * {@code RegistryRock}, {@code RegistrySoilVariant}, {@code RegistryWood} and its own {@code RegistryOre},
 * {@code RegistrySand}, {@code RegistrySoil}. Such an enum is compiled against TFC or TFCRegistryAPI, so it ships in
 * the mod file of one of them or in a file with a mod that declares a dependency on one of them
 * ({@code [[dependencies.<mod>]]} in its {@code neoforge.mods.toml}, any type). Those are the {@link #ROOTS}.
 * A file is selected as a whole: every mod of a selected file stays, in list order, so the scan of a selected file
 * runs exactly as before (same files, same order, same mod id per entry).
 */
public final class RegistryScanScope {
    /** The mods whose types TFCRegistryAPI's enums implement. */
    public static final Set<String> ROOTS = Set.of("tfc", "tfcregistryapi");

    private RegistryScanScope() {}

    /** One {@code [[mods]]} entry: the mod file it lives in, its id and the mod ids it declares dependencies on. */
    public record ModEntry(String file, String modId, Collection<String> dependencies) {}

    /** True when the entry is one of the roots or declares a dependency on one. */
    public static boolean touchesRoots(ModEntry mod, Set<String> roots) {
        if (roots.contains(mod.modId())) return true;
        for (String dep : mod.dependencies()) {
            if (roots.contains(dep)) return true;
        }
        return false;
    }

    /** The mod files with at least one entry that {@link #touchesRoots touches the roots}. */
    public static Set<String> selectFiles(List<ModEntry> mods, Set<String> roots) {
        Set<String> files = new HashSet<>();
        for (ModEntry mod : mods) {
            if (touchesRoots(mod, roots)) files.add(mod.file());
        }
        return files;
    }

    /** The mods of the selected files, in their original order. */
    public static <T> List<T> select(List<T> mods, Function<T, ModEntry> view, Set<String> roots) {
        List<ModEntry> entries = new ArrayList<>(mods.size());
        for (T mod : mods) entries.add(view.apply(mod));
        Set<String> files = selectFiles(entries, roots);
        List<T> out = new ArrayList<>();
        for (int i = 0; i < mods.size(); i++) {
            if (files.contains(entries.get(i).file())) out.add(mods.get(i));
        }
        return out;
    }
}
