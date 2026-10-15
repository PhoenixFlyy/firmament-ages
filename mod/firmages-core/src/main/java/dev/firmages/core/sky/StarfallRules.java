package dev.firmages.core.sky;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * The pure rules of Starfall (SPEC §25.3): the night roll with pity and the first-fall guarantee, the site choice with
 * its rejection rules against a {@link WorldView}, the moves of a site that fails its base check, the deterministic
 * old-crater regions, the eight bearings and the distant sound volume. No Minecraft types, so JUnit covers it.
 */
public final class StarfallRules {
    /** Attempts of one site search (SPEC §25.3). */
    public static final int SITE_ATTEMPTS = 16;
    /** A site that fails its base check moves this far at most, and burns after this many moves. */
    public static final int MOVE_DISTANCE = 128;
    public static final int MAX_MOVES = 3;
    /** Impact time window of the night ({@code dayTime % 24000}) and the dusk at which the night is rolled. */
    public static final int DUSK = 13000;
    public static final int IMPACT_MIN = 14000;
    public static final int IMPACT_MAX = 22000;
    /** The streak is sent this many ticks before the impact. */
    public static final int STREAK_TICKS = 120;
    /** The players who see the body and the impact particles stand within this distance. */
    public static final int NEAR = 256;
    /** The hot-core bonus covers blocks within this distance of the core. */
    public static final int HOT_RADIUS = 12;

    private StarfallRules() {}

    /** Why a night brings a fall, or {@link #NONE}. */
    public enum Reason { NONE, GUARANTEED, FIRST_FALL, PITY, CHANCE }

    /**
     * The roll of one clear night. Order: a guarantee (KubeJS {@code starfallTonight}, a burned site), the first fall
     * of a new metal, pity after {@code pityNights} clear nights without a fall, then the Age's chance.
     */
    public static Reason roll(boolean guaranteed, String lastMetal, String metal, int nightsSinceFall, int pityNights, double chance, double random) {
        if (guaranteed) return Reason.GUARANTEED;
        if (!metal.equals(lastMetal)) return Reason.FIRST_FALL;
        if (nightsSinceFall >= pityNights) return Reason.PITY;
        return random < chance ? Reason.CHANCE : Reason.NONE;
    }

    /** What the site choice may not touch; the world answers without loading or generating a chunk. */
    public interface WorldView {
        /** The noise biome of the column is in {@code #firmages:starfall/no_impact}. */
        boolean noImpactBiome(int x, int z);

        /** An FTB Chunks claim lies within {@code marginChunks} chunks of the column. */
        boolean claimedNear(int x, int z, int marginChunks);

        /** The column lies inside the box of a known Drifting Isle or an isle fallback record (SPEC §30). */
        boolean insideIsle(int x, int z);
    }

    /** Fixed limits of one search: the heart (if recorded), its clearance, the respawn points and their clearance. */
    public record Limits(Optional<int[]> heart, int shrineClearance, List<int[]> spawns, int spawnClearance, int claimMarginChunks) {}

    /** Why a column is refused, or null when it is free. */
    public static String reject(int x, int z, Limits limits, WorldView world) {
        if (limits.heart().isPresent() && within(x, z, limits.heart().get(), limits.shrineClearance())) return "shrine";
        for (int[] s : limits.spawns()) if (within(x, z, s, limits.spawnClearance())) return "spawn";
        if (world.noImpactBiome(x, z)) return "biome";
        if (world.insideIsle(x, z)) return "isle";
        if (limits.claimMarginChunks() >= 0 && world.claimedNear(x, z, limits.claimMarginChunks())) return "claim";
        return null;
    }

    /** A free column at a random bearing and distance from the anchor, after at most {@link #SITE_ATTEMPTS} tries. */
    public static Optional<int[]> chooseSite(int anchorX, int anchorZ, int minDistance, int maxDistance, Limits limits, WorldView world, Random rng) {
        for (int i = 0; i < SITE_ATTEMPTS; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            int d = minDistance + (maxDistance > minDistance ? rng.nextInt(maxDistance - minDistance + 1) : 0);
            int x = anchorX + (int) Math.round(Math.cos(angle) * d);
            int z = anchorZ + (int) Math.round(Math.sin(angle) * d);
            if (reject(x, z, limits, world) == null) return Optional.of(new int[] {x, z});
        }
        return Optional.empty();
    }

    /** The target of move number {@code move} (1-based) of a site: a seeded bearing, 48 to 128 blocks away. */
    public static int[] moveTarget(int x, int z, long siteSeed, int move) {
        Random r = new Random(mix(siteSeed, 0x5EED0000L + move));
        double angle = r.nextDouble() * Math.PI * 2;
        int d = 48 + r.nextInt(MOVE_DISTANCE - 48 + 1);
        return new int[] {x + (int) Math.round(Math.cos(angle) * d), z + (int) Math.round(Math.sin(angle) * d)};
    }

    /**
     * The old crater of a region ({@code regionSize} by {@code regionSize} blocks), if it has one: kept with
     * {@code chance} from a hash of the world seed and the region; its centre lies at least 64 blocks inside the region.
     */
    public static Optional<int[]> oldCrater(long worldSeed, int regionX, int regionZ, int regionSize, double chance) {
        long h = mix(mix(worldSeed, 0x01DC7A7E5L), ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL));
        double roll = (h >>> 11) * 0x1.0p-53;
        if (roll >= chance) return Optional.empty();
        int span = Math.max(1, regionSize - 128);
        long h2 = mix(h, 0x2545F4914F6CDD1DL);
        int ox = 64 + (int) Math.floorMod(h2, span);
        int oz = 64 + (int) Math.floorMod(h2 >>> 32, span);
        return Optional.of(new int[] {regionX * regionSize + ox, regionZ * regionSize + oz});
    }

    /** The region of a block coordinate. */
    public static int region(int block, int regionSize) {
        return Math.floorDiv(block, regionSize);
    }

    /** One of the eight bearings from (x, z) toward (tx, tz): north is -Z, east is +X. */
    public static String bearing(double x, double z, double tx, double tz) {
        double dx = tx - x;
        double dz = tz - z;
        if (dx == 0 && dz == 0) return "north";
        double deg = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        int idx = (int) Math.floorMod(Math.round(deg / 45.0), 8);
        return BEARINGS[idx];
    }

    public static final String[] BEARINGS = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};

    /** Volume of the distant boom: 1 within 256 blocks, falling linearly to 0.25 at 1,500 blocks and beyond. */
    public static float distantVolume(double distance) {
        if (distance <= NEAR) return 1.0F;
        if (distance >= 1500) return 0.25F;
        return (float) (1.0 - 0.75 * (distance - NEAR) / (1500.0 - NEAR));
    }

    /** True once the game time has passed the cooling time. */
    public static boolean cold(long gameTime, long coolsAt) {
        return gameTime >= coolsAt;
    }

    /** "1 day 6 h" style parts of a tick count (24,000 ticks a day, 1,000 an hour), as {days, hours}; at least one hour. */
    public static int[] daysHours(long ticks) {
        long t = Math.max(0, ticks);
        long days = t / 24000;
        long hours = (t % 24000 + 999) / 1000;
        if (hours == 24) {
            days++;
            hours = 0;
        }
        if (days == 0 && hours == 0) hours = 1;
        return new int[] {(int) days, (int) hours};
    }

    /** SplitMix64 of two values: the seeds of sites and regions. */
    public static long mix(long a, long b) {
        long z = a * 0x9E3779B97F4A7C15L + b;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static boolean within(int x, int z, int[] p, int r) {
        long dx = x - p[0];
        long dz = z - p[1];
        return dx * dx + dz * dz < (long) r * r;
    }
}
