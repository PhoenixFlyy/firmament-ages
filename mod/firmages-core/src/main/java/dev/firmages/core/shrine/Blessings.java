package dev.firmages.core.shrine;

import dev.firmages.core.config.ServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import java.util.Optional;

/**
 * Blessings of the awakened tiers (SPEC §7.6), active only while the shrine is intact. MVP: Hearthward, the flag
 * {@code sanctuary} of the Stone tier: no natural monster spawns within {@code 12 + 4 * (awakened - 1)} blocks of
 * the heart. Spawners, Gateways and ritual summons are untouched.
 */
public final class Blessings {
    private Blessings() {}

    /** True when an awakened tier carries a blessing with {@code flag}. */
    static boolean granted(int awakened, String flag) {
        ShrineData d = ShrineDataLoader.current();
        for (int k = 0; k < awakened; k++) {
            Optional<Blessing> b = d.tier(k).flatMap(ShrineTier::blessing).map(id -> d.blessings().get(id));
            if (b.isPresent() && b.get().has(flag)) return true;
        }
        return false;
    }

    public static int sanctuaryRadius(int awakened) {
        int base = ServerConfig.loaded() ? ServerConfig.SHRINE_SANCTUARY_BASE_RADIUS.get() : 12;
        int per = ServerConfig.loaded() ? ServerConfig.SHRINE_SANCTUARY_PER_TIER.get() : 4;
        return ShrineRules.sanctuaryRadius(awakened, base, per);
    }

    public static int sanctuaryRadius(MinecraftServer s) {
        ShrineState.Snapshot st = ShrineState.get();
        return st == null ? 0 : sanctuaryRadius(st.awakened());
    }

    public static boolean sanctuaryActive(MinecraftServer s) {
        ShrineState.Snapshot st = ShrineState.get();
        return st != null && st.intact() && granted(st.awakened(), Blessing.SANCTUARY);
    }

    /** {@code MobSpawnEvent.PositionCheck}: Hearthward refuses natural monster spawns near the heart. */
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        ShrineState.Snapshot st = ShrineState.get();
        if (st == null || !st.intact() || st.awakened() <= 0) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) return;
        if (event.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != st.dimension()) return;
        if (!granted(st.awakened(), Blessing.SANCTUARY)) return;
        double r = sanctuaryRadius(st.awakened());
        if (st.heart().distToCenterSqr(event.getX(), event.getY(), event.getZ()) <= r * r) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /** Pure check used by the GameTest: would a natural monster spawn at that distance be refused now? */
    public static boolean refusesSpawnAt(double distance) {
        ShrineState.Snapshot st = ShrineState.get();
        if (st == null || !st.intact() || st.awakened() <= 0 || !granted(st.awakened(), Blessing.SANCTUARY)) return false;
        return distance <= sanctuaryRadius(st.awakened());
    }
}
