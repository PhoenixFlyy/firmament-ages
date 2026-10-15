package dev.firmages.core.shrine;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Blessings of the awakened tiers (SPEC §7.6), active only while the shrine is intact, at the blessing sources of
 * {@link BlessingIndex} (SPEC-himmel §29.5): the heart, every lit and intact Wayshrine, later the Sky Anchors.
 * <ul>
 *   <li>{@code sanctuary} (Hearthward): no natural monster spawns within a source that carries it (the heart: within
 *       {@code 12 + 4 * (awakened - 1)} blocks). Spawners and ritual summons are untouched.</li>
 *   <li>Effects of the blessings of the tiers in reach (the OR of the masks of every source within its radius of the
 *       player): transient attribute modifiers {@code firmages:blessing/<name>/<index>}, status effects and an XP bonus
 *       on orb pickup. Checked every second; a broken shrine, leaving every radius or a data reload removes them on the
 *       next check. TFC food decay and tool durability are never touched. {@code shrine.blessings.everywhere} is gone
 *       (S4A, Felix 2026-10-15): the Sky Anchors carry the blessings away from the shrine from the Space Age on.</li>
 * </ul>
 */
public final class Blessings {
    static final int PERIOD = 20;
    /** Status effects last 13 s and are refreshed below 11 s, so night vision never flickers (it does below 10 s). */
    static final int EFFECT_TICKS = 260;
    static final int EFFECT_REFRESH = 220;

    private record Applied(Holder<Attribute> attribute, ResourceLocation id) {}

    private static final Map<UUID, Set<Applied>> APPLIED = new HashMap<>();
    private static final Map<UUID, Double> XP_BONUS = new HashMap<>();

    private Blessings() {}

    /** True when an awakened tier carries a blessing with {@code flag}. */
    static boolean granted(int awakened, String flag) {
        return grantedMask(BlessingSources.allTiers(awakened), flag);
    }

    /** True when a tier of {@code mask} carries a blessing with {@code flag}. */
    static boolean grantedMask(int mask, String flag) {
        for (Blessing b : blessingsOfMask(mask)) {
            if (b.has(flag)) return true;
        }
        return false;
    }

    /** The blessings of the tiers 0..awakened-1, in tier order. */
    static List<Blessing> blessingsOf(int awakened) {
        return blessingsOfMask(BlessingSources.allTiers(awakened));
    }

    /** The blessings of the tiers in {@code mask} (bit k = tier k), in tier order, each once. */
    public static List<Blessing> blessingsOfMask(int mask) {
        ShrineData d = ShrineDataLoader.current();
        Map<String, Blessing> out = new LinkedHashMap<>();
        for (int k = 0; k < BlessingSources.TIERS; k++) {
            if ((mask & (1 << k)) == 0) continue;
            Optional<Blessing> b = d.tier(k).flatMap(ShrineTier::blessing).map(id -> d.blessings().get(id));
            b.ifPresent(x -> out.putIfAbsent(x.id(), x));
        }
        return List.copyOf(out.values());
    }

    /** Blessings in force now: the awakened tiers' blessings while the shrine is intact, else none. */
    public static List<Blessing> activeBlessings(MinecraftServer s) {
        ShrineState.Snapshot st = ShrineState.get();
        if (st == null || !st.intact() || st.awakened() <= 0) return List.of();
        return blessingsOf(st.awakened());
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

    /** The tiers in force where {@code p} stands: the OR of every blessing source in reach (0 = none). */
    public static int maskAt(ServerPlayer p) {
        return BlessingSources.mask(BlessingIndex.current(p.server), p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ());
    }

    /** True when a blessing source reaches {@code p}. */
    public static boolean withinRadius(ServerPlayer p) {
        return maskAt(p) != 0;
    }

    // ---------------------------------------------------------------- application

    /** Server tick: every second, every player's blessing effects follow the sources and their position. */
    public static void tick(MinecraftServer s) {
        if (s.getTickCount() % PERIOD != 0) return;
        BlessingIndex.current(s); // rebuilt when stale, so the spawn check sees it with nobody online too
        for (ServerPlayer p : s.getPlayerList().getPlayers()) apply(p);
    }

    /** Applies or removes the blessing effects of one player now (also called by the GameTests). */
    public static void apply(ServerPlayer p) {
        apply(p, blessingsOfMask(maskAt(p)));
    }

    /** Sets exactly the effects of {@code active} on {@code p} and removes every other blessing modifier of this class. */
    public static void apply(ServerPlayer p, List<Blessing> active) {
        Map<Applied, AttributeModifier> want = new HashMap<>();
        double xp = 0;
        for (Blessing b : active) {
            for (int i = 0; i < b.effects().size(); i++) {
                Blessing.Effect e = b.effects().get(i);
                switch (e.kind()) {
                    case ATTRIBUTE -> {
                        Optional<Holder.Reference<Attribute>> attr = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(e.target()));
                        if (attr.isEmpty()) {
                            warnOnce("attribute " + e.target() + " of blessing " + b.id() + " is not registered");
                            continue;
                        }
                        ResourceLocation id = modifierId(b, i);
                        want.put(new Applied(attr.get(), id), new AttributeModifier(id, e.amount(), operation(e.operation())));
                    }
                    case MOB_EFFECT -> {
                        Optional<Holder.Reference<MobEffect>> eff = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(e.target()));
                        if (eff.isEmpty()) {
                            warnOnce("status effect " + e.target() + " of blessing " + b.id() + " is not registered");
                            continue;
                        }
                        MobEffectInstance cur = p.getEffect(eff.get());
                        if (cur == null || (cur.getAmplifier() <= e.amplifier() && !cur.isInfiniteDuration() && cur.getDuration() <= EFFECT_REFRESH)) {
                            p.addEffect(new MobEffectInstance(eff.get(), EFFECT_TICKS, e.amplifier(), true, false, true));
                        }
                    }
                    case XP_BONUS -> xp += e.amount();
                }
            }
        }
        Set<Applied> had = APPLIED.computeIfAbsent(p.getUUID(), u -> new HashSet<>());
        for (Applied a : had) {
            if (want.containsKey(a)) continue;
            AttributeInstance inst = p.getAttribute(a.attribute());
            if (inst != null) inst.removeModifier(a.id());
        }
        had.clear();
        for (Map.Entry<Applied, AttributeModifier> w : want.entrySet()) {
            AttributeInstance inst = p.getAttribute(w.getKey().attribute());
            if (inst == null) continue; // not a player attribute
            AttributeModifier cur = inst.getModifier(w.getKey().id());
            if (cur == null || cur.amount() != w.getValue().amount() || cur.operation() != w.getValue().operation()) {
                inst.addOrUpdateTransientModifier(w.getValue());
            }
            had.add(w.getKey());
        }
        if (xp > 0) XP_BONUS.put(p.getUUID(), xp);
        else XP_BONUS.remove(p.getUUID());
    }

    /** Extra share of picked-up XP for {@code p} right now (0 = none). */
    public static double xpBonus(Player p) {
        return XP_BONUS.getOrDefault(p.getUUID(), 0.0);
    }

    static ResourceLocation modifierId(Blessing b, int index) {
        String path = b.id().substring(b.id().indexOf(':') + 1);
        return ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "blessing/" + path + "/" + index);
    }

    static AttributeModifier.Operation operation(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
    }

    private static final Set<String> WARNED = new HashSet<>();

    private static void warnOnce(String what) {
        if (WARNED.add(what)) FirmagesCore.LOGGER.warn("Shrine blessing: {}; that effect is skipped", what);
    }

    /** {@code PlayerXpEvent.PickupXp}: a blessed player's orb is worth {@code 1 + bonus} times as much (fractions by chance). */
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        double bonus = xpBonus(event.getEntity());
        if (bonus <= 0 || event.getOrb().value <= 0) return;
        double extra = event.getOrb().value * bonus;
        int whole = (int) extra;
        if (event.getEntity().getRandom().nextDouble() < extra - whole) whole++;
        event.getOrb().value += whole;
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        APPLIED.remove(event.getEntity().getUUID());
        XP_BONUS.remove(event.getEntity().getUUID());
    }

    static void clear() {
        BlessingIndex.clear();
        APPLIED.clear();
        XP_BONUS.clear();
        WARNED.clear();
    }

    // ---------------------------------------------------------------- sanctuary

    /**
     * {@code MobSpawnEvent.PositionCheck}: Hearthward refuses natural monster spawns within every source that carries
     * it (the heart, a lit Wayshrine, an anchor with the tier-0 shard). Reads the last built index; the blessing tick
     * rebuilds it within a second of any change.
     */
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        List<BlessingSources.Source> sources = BlessingIndex.peek();
        if (sources.isEmpty()) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) return;
        if (event.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (BlessingSources.sanctuaryAt(sources, level.dimension().location().toString(), event.getX(), event.getY(), event.getZ())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /** Pure check used by the GameTest: would a natural monster spawn at that distance from the heart be refused now? */
    public static boolean refusesSpawnAt(double distance) {
        ShrineState.Snapshot st = ShrineState.get();
        if (st == null || !st.intact() || st.awakened() <= 0 || !granted(st.awakened(), Blessing.SANCTUARY)) return false;
        return distance <= sanctuaryRadius(st.awakened());
    }

    /** Would a natural monster spawn at that point be refused now by any source (GameTests, status)? */
    public static boolean refusesSpawnAt(ServerLevel level, double x, double y, double z) {
        return BlessingSources.sanctuaryAt(BlessingIndex.current(level.getServer()), level.dimension().location().toString(), x, y, z);
    }

    /** Attribute modifier ids this build would set for the given blessings (status command, tests). */
    public static List<ResourceLocation> modifierIds(List<Blessing> blessings) {
        List<ResourceLocation> out = new ArrayList<>();
        for (Blessing b : blessings) {
            for (int i = 0; i < b.effects().size(); i++) {
                if (b.effects().get(i).kind() == Blessing.Effect.Kind.ATTRIBUTE) out.add(modifierId(b, i));
            }
        }
        return out;
    }
}
