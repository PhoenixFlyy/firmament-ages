package dev.firmages.core.compat.kubejs;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;
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
 * without KubeJS. All answers use the snapshot of the datapack load in progress (§3.2), so tag scripts see the
 * Ages the reload is being built for.
 */
public final class FirmAgesJS {
    private FirmAgesJS() {}

    /** True if the Age is unlocked. Unknown ids and non-Age stages return false. */
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

    /** The altar of the Gathering, {x, y, z} in The Origin (spawn the waves and the boss around it). */
    public static int[] originAltar() {
        return new int[] {OriginArena.ALTAR.getX(), OriginArena.ALTAR.getY(), OriginArena.ALTAR.getZ()};
    }

    /**
     * The Gateways to Eternity gate the boss script opens at the Gathering ({@code origin.finaleGateway}, default
     * {@code firmages:the_origin}); the script summons the final boss only when exactly this gate completes in The Origin.
     */
    public static String finaleGateway() {
        return ServerConfig.finaleGateway();
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
}
