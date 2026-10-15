package dev.firmages.core.shrine;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The blessing sources in force (SPEC-himmel §29.5): an immutable list, swapped atomically. It is marked stale on a
 * shrine validation ({@link ShrineState#set}), an Age change, a Wayshrine phase or {@code intact} change, an anchor change
 * and a data reload, and rebuilt on the next read, so a burst of changes costs one rebuild. It is empty while the home
 * shrine is not intact.
 * <p>
 * The heart is built in. Further sources come from providers: the Wayshrines register theirs at mod construction; the
 * Sky Anchors (the anchor half of M16) plug in the same way with {@link #registerProvider}.
 */
public final class BlessingIndex {
    /** A kind of blessing source besides the heart. Called on the server thread during a rebuild. */
    @FunctionalInterface
    public interface Provider {
        /**
         * The candidate sources of this kind, with their full masks (the index cuts them to the awakened tiers).
         *
         * @param awakened the number of awakened tiers (0..9)
         */
        List<BlessingSources.Source> sources(MinecraftServer server, int awakened);
    }

    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static volatile List<BlessingSources.Source> current = List.of();
    private static volatile boolean dirty = true;

    private BlessingIndex() {}

    public static void registerProvider(Provider p) {
        PROVIDERS.add(p);
    }

    /** Marks the index stale; the next {@link #current} rebuilds it. */
    public static void markDirty() {
        dirty = true;
    }

    /** The sources in force now (rebuilt first when stale). Server thread. */
    public static List<BlessingSources.Source> current(MinecraftServer server) {
        if (dirty) rebuild(server);
        return current;
    }

    /** The last built list without a rebuild (hot paths that run off a stale flag are fine with it). */
    public static List<BlessingSources.Source> peek() {
        return current;
    }

    public static void rebuild(MinecraftServer server) {
        dirty = false;
        ShrineState.Snapshot st = ShrineState.get();
        if (st == null || !st.intact() || st.awakened() <= 0) {
            current = List.of();
            return;
        }
        int awakened = st.awakened();
        List<BlessingSources.Source> candidates = new ArrayList<>();
        candidates.add(new BlessingSources.Source(BlessingSources.Kind.HEART, st.dimension().location().toString(), st.heart().getX(),
            st.heart().getY(), st.heart().getZ(), Blessings.sanctuaryRadius(awakened), BlessingSources.allTiers(awakened), false));
        for (Provider p : PROVIDERS) {
            try {
                candidates.addAll(p.sources(server, awakened));
            } catch (RuntimeException e) {
                dev.firmages.core.FirmagesCore.LOGGER.error("Blessing source provider {} failed; its sources are skipped", p, e);
            }
        }
        // Hearthward follows the tiers a source carries (the tier-0 blessing has the sanctuary flag).
        current = BlessingSources.inForce(true, awakened, candidates).stream()
            .map(s -> new BlessingSources.Source(s.kind(), s.dimension(), s.x(), s.y(), s.z(), s.radius(), s.tierMask(),
                Blessings.grantedMask(s.tierMask(), Blessing.SANCTUARY)))
            .toList();
    }

    /** Server stop: forget everything. */
    static void clear() {
        current = List.of();
        dirty = true;
    }
}
