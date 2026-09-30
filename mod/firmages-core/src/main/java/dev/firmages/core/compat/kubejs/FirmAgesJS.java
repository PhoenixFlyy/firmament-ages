package dev.firmages.core.compat.kubejs;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;

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
}
