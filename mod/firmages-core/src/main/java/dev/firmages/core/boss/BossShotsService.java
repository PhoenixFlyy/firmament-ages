package dev.firmages.core.boss;

import dev.firmages.core.FirmagesCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.Optional;

/** The server's copy of {@code data/firmages/balance/boss_shots.json} (round G4), read again after a datapack reload. */
public final class BossShotsService {
    public static final ResourceLocation TABLE = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "balance/boss_shots.json");
    private static BossShots cached = BossShots.defaults();
    private static ResourceManager cachedFor;

    private BossShotsService() {}

    public static boolean tablePresent(MinecraftServer s) {
        return s.getResourceManager().getResource(TABLE).isPresent();
    }

    public static BossShots table(MinecraftServer s) {
        ResourceManager rm = s.getResourceManager();
        if (rm != cachedFor) {
            BossShots t = BossShots.defaults();
            Optional<Resource> r = rm.getResource(TABLE);
            if (r.isPresent()) {
                try (Reader reader = r.get().openAsReader()) {
                    t = BossShots.parse(reader);
                } catch (Exception e) {
                    FirmagesCore.LOGGER.warn("Boss shots: cannot read {}, the built-in table fights", TABLE, e);
                }
            } else {
                FirmagesCore.LOGGER.warn("Boss shots: {} missing, the built-in table fights", TABLE);
            }
            cached = t;
            cachedFor = rm;
        }
        return cached;
    }
}
