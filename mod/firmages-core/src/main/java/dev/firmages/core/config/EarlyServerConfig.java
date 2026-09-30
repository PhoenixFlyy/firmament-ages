package dev.firmages.core.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import dev.firmages.core.FirmagesCore;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Reads {@code firmages-server.toml} values before NeoForge loads the server config: server configs load after the
 * initial datapack load, but the recipe gate and the boot snapshot need them during that load. Candidates in
 * order: the per-world override {@code <world>/serverconfig/} (dedicated server only), {@code config/} (where
 * NeoForge 21.1 keeps server configs), then {@code defaultconfigs/}. The first file that has the key wins.
 */
public final class EarlyServerConfig {
    public static final String FILE = "firmages-server.toml";

    private EarlyServerConfig() {}

    /** World directory of a dedicated server ({@code level-name} from server.properties), else null. */
    public static Path dedicatedWorldDir() {
        if (!FMLEnvironment.dist.isDedicatedServer()) return null;
        return FMLPaths.GAMEDIR.get().resolve(levelNameFromServerProperties()).normalize();
    }

    public static String levelNameFromServerProperties() {
        Path props = FMLPaths.GAMEDIR.get().resolve("server.properties");
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(props)) {
            p.load(r);
        } catch (IOException e) {
            return "world";
        }
        String name = p.getProperty("level-name", "world").trim();
        return name.isEmpty() ? "world" : name;
    }

    public static List<Path> candidates(Path worldDir) {
        List<Path> out = new ArrayList<>();
        if (worldDir != null) out.add(worldDir.resolve("serverconfig").resolve(FILE));
        out.add(FMLPaths.CONFIGDIR.get().resolve(FILE));
        out.add(FMLPaths.GAMEDIR.get().resolve("defaultconfigs").resolve(FILE));
        return out;
    }

    /** The raw value of a dotted key (e.g. {@code gate.allowRecipes}) from the first candidate file that has it. */
    public static Optional<Object> value(Path worldDir, String key) {
        for (Path c : candidates(worldDir)) {
            if (!Files.isRegularFile(c)) continue;
            try (Reader r = Files.newBufferedReader(c)) {
                Config cfg = new TomlParser().parse(r);
                Object v = cfg.get(key);
                if (v != null) return Optional.of(v);
            } catch (Exception e) {
                FirmagesCore.LOGGER.warn("Cannot read {} from {}: {}", key, c, e.toString());
            }
        }
        return Optional.empty();
    }

    public static Optional<List<String>> stringList(Path worldDir, String key) {
        return value(worldDir, key).filter(v -> v instanceof List<?>).map(v -> ((List<?>) v).stream().map(String::valueOf).toList());
    }

    public static Optional<Boolean> bool(Path worldDir, String key) {
        return value(worldDir, key).filter(v -> v instanceof Boolean).map(v -> (Boolean) v);
    }
}
