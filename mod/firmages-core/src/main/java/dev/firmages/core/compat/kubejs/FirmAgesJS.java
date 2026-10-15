package dev.firmages.core.compat.kubejs;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.gate.RecipeMarkers;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginRegistry;
import dev.firmages.core.origin.OriginSavedData;
import dev.firmages.core.origin.OriginService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Optional;

/**
 * The KubeJS binding {@code FirmAges} (SPEC §3.4). Static methods only; no KubeJS types, so the class also loads
 * without KubeJS. All answers use the snapshot of the datapack load in progress (§3.2).
 * <p>Recipe scripts are Age-independent: an Age change re-filters the recipes of the last load without running the
 * scripts again (the Age swap, SPEC §3.3). They add every recipe at every Age and declare what a recipe needs with
 * {@link #stationAge}, {@link #requireAge} and {@link #untilAge}; they must not branch on {@link #isUnlocked}.
 */
public final class FirmAgesJS {
    private FirmAgesJS() {}

    /**
     * Every recipe whose type or serializer is {@code type} needs {@code age}, the Age of its machine (also the recipes
     * a script adds in the same event). Call it inside {@code ServerEvents.recipes}.
     */
    public static void stationAge(String type, String age) {
        RecipeMarkers.stationAge(type, age);
    }

    /** The recipe {@code recipeId} needs {@code age} on top of its outputs' Ages. Call it inside {@code ServerEvents.recipes}. */
    public static void requireAge(String recipeId, String age) {
        RecipeMarkers.requireAge(recipeId, age);
    }

    /**
     * The recipe {@code recipeId} exists only while {@code age} is locked: a stand-in that a later variant replaces
     * (a byproduct-stripped copy, for example). Call it inside {@code ServerEvents.recipes}.
     */
    public static void untilAge(String recipeId, String age) {
        RecipeMarkers.untilAge(recipeId, age);
    }

    /** True if the Age is unlocked. Unknown ids and non-Age stages return false. Not for recipe scripts (see the class comment). */
    public static boolean isUnlocked(String stageId) {
        Optional<AgeId> age = AgeId.byId(stageId);
        return age.isPresent() && AgeService.snapshotForReload().isUnlocked(age.get());
    }

    public static List<String> unlockedAges() {
        return AgeService.snapshotForReload().unlockedIds();
    }

    public static List<String> lockedAges() {
        return AgeService.snapshotForReload().locked().stream().map(AgeId::id).toList();
    }

    /**
     * Block ids of {@code firmages:age_blocks/<age>} for every locked Age, read from the tag JSON of the load in
     * progress (tag events run before tags are bound). Sorted, registered blocks only. See
     * {@link AgeService#lockedOreBlocks()} for the initial-load caveat.
     */
    public static List<String> lockedOreBlocks() {
        return AgeService.lockedOreBlocks();
    }

    /** {@code prospecting.enabled} (m1). True while the server config is not loaded yet (initial load). */
    public static boolean prospectingEnabled() {
        return ServerConfig.prospectingEnabled();
    }

    // ---- The Origin (SPEC §16.3), for the end-boss script ----

    /** Dimension id of The Origin, {@code firmages:origin}. */
    public static String originDimension() {
        return OriginRegistry.ORIGIN_ID.toString();
    }

    /** The altar of the Gathering, {x, y, z} in The Origin (summon the boss around it). */
    public static int[] originAltar() {
        return new int[] {OriginArena.ALTAR.getX(), OriginArena.ALTAR.getY(), OriginArena.ALTAR.getZ()};
    }

    /** Scoreboard tag the boss script adds to the final boss ({@code entity.addTag(FirmAges.finalBossTag())}). */
    public static String finalBossTag() {
        return OriginService.FINAL_BOSS_TAG;
    }

    /** True once the tagged final boss died (also false with no server running). */
    public static boolean isFinaleWon() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s != null && OriginSavedData.get(s).won();
    }

    // ---- The shrine's consecration (SPEC §17) ----

    /** True while the shrine's maintenance mode is on (consecrated blocks can be broken); false with no server running. */
    public static boolean shrineMaintenance() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s != null && dev.firmages.core.shrine.ShrineConsecration.maintenanceActive(s);
    }

    /** Number of consecrated shrine positions with a stored original block (0 with no server running). */
    public static int consecratedBlocks() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? 0 : dev.firmages.core.shrine.ShrineSavedData.get(s).originals().size();
    }

    // ---- Starfall (SPEC §25.3) ----

    /**
     * The next clear night (tonight, if the night has not passed 20000 yet) brings a falling star for sure, from the
     * Iron Age on. {@code server_scripts/firmages/firmament_night.js} calls it on a Firmament Night. False with no
     * server running.
     */
    public static boolean starfallTonight() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return false;
        s.execute(() -> dev.firmages.core.sky.StarfallService.requestTonight(s));
        return true;
    }

    // ---- The Lens Crown (SPEC §27.12) ----

    /**
     * The lens indices (0..8) of chord {@code name} ({@code "dawn_triad"} or {@code "firmages:dawn_triad"}) in the
     * loaded chord library, empty for an unknown chord.
     */
    public static List<Integer> crownChord(String name) {
        String id = name.contains(":") ? name : "firmages:" + name;
        return dev.firmages.core.crown.ChordLibrary.latest().get(id).map(dev.firmages.core.crown.Chord::lenses).orElse(List.of());
    }
}
