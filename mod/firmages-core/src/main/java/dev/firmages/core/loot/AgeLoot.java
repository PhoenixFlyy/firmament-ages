package dev.firmages.core.loot;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageId;
import com.mojang.serialization.MapCodec;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeIndex;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifierManager;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The loot Age gate (SPEC §19.5): what {@link AgeLootModifier} does to one roll, whose Ages count, and the modifier
 * order. The Ages are the player's team stages when the roll names a player (the chest's opener, the block's breaker,
 * the mob's killer, the angler), else the server's Ages (the global Age state the recipe gate filters with): a hopper
 * that unpacks a chest, a machine's fake player, a piglin's barter. The Age of an item is one {@link AgeIndex} lookup.
 */
public final class AgeLoot {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, FirmagesCore.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AgeLootModifier>> AGE_FILTER =
        LOOT_MODIFIERS.register("age_filter", () -> AgeLootModifier.CODEC);

    /** The params that name who a roll is for, in the order ProgressiveStages' own loot filter reads them. */
    private static final List<LootContextParam<? extends Entity>> PLAYER_PARAMS = List.of(LootContextParams.LAST_DAMAGE_PLAYER,
        LootContextParams.THIS_ENTITY, LootContextParams.ATTACKING_ENTITY, LootContextParams.DIRECT_ATTACKING_ENTITY);

    private static final AtomicLong ROLLS = new AtomicLong();
    private static final AtomicLong REMOVED = new AtomicLong();
    private static volatile WeakReference<LootModifierManager> manager = new WeakReference<>(null);

    private AgeLoot() {}

    public static void register(IEventBus modBus) {
        LOOT_MODIFIERS.register(modBus);
    }

    /**
     * Removes the stacks the roll's Ages do not hold, then the same from the contents of every stack that stays (a
     * shulker box, a bundle, a TFC vessel or sack: {@link NbtContents#filterNested}); returns how many. Does nothing
     * with {@code gate.lootFilter} off.
     */
    public static int filter(List<ItemStack> loot, LootContext context) {
        if (loot.isEmpty() || !ServerConfig.lootFilter()) return 0;
        AgeIndex idx = AgeIndex.current();
        if (idx.items().isEmpty() && idx.disabledItems().isEmpty()) return 0;
        Set<AgeId> unlocked = unlockedFor(context);
        int n = LootAgeRule.filter(loot, s -> s.isEmpty() ? null : idx.ageOf(s.getItem()),
            s -> !s.isEmpty() && idx.isDisabled(s.getItem()), unlocked);
        n += NbtContents.filterNested(loot, context.getLevel().registryAccess(), id -> NbtContents.dropped(id, idx, unlocked));
        ROLLS.incrementAndGet();
        if (n > 0) REMOVED.addAndGet(n);
        return n;
    }

    /** The Ages the roll counts: its player's team stages, else the server's Ages. */
    public static Set<AgeId> unlockedFor(LootContext context) {
        ServerPlayer p = player(context);
        return p != null ? playerAges(p) : AgeService.currentSnapshot().unlocked();
    }

    /** The real player a roll is for, or null: fake players (machines) and mobs count as nobody. */
    public static ServerPlayer player(LootContext context) {
        for (LootContextParam<? extends Entity> param : PLAYER_PARAMS) {
            ServerPlayer p = realPlayer(context.getParamOrNull(param));
            if (p != null) return p;
        }
        return null;
    }

    private static ServerPlayer realPlayer(Entity e) {
        if (e instanceof Projectile projectile) e = projectile.getOwner();
        return e instanceof ServerPlayer p && !(p instanceof FakePlayer) ? p : null;
    }

    /** The Ages among the ProgressiveStages stages of the player's team; Dawn always. */
    public static EnumSet<AgeId> playerAges(ServerPlayer player) {
        List<String> ids = new ArrayList<>();
        for (StageId id : ProgressiveStagesAPI.getStages(player)) ids.add(id.getPath());
        return LootAgeRule.ages(ids);
    }

    /** A modifier order and the registered-modifier map it was made from (identity), for the mixin's cache. */
    public record ModOrder(Object source, List<IGlobalLootModifier> mods) {}

    /** {@code getAllLootMods} as NeoForge applies it, with this mod's age filter moved behind every other modifier. */
    public static List<IGlobalLootModifier> filterLast(Collection<IGlobalLootModifier> mods) {
        List<IGlobalLootModifier> out = new ArrayList<>(mods.size());
        List<IGlobalLootModifier> ours = new ArrayList<>(1);
        for (IGlobalLootModifier m : mods) (m instanceof AgeLootModifier ? ours : out).add(m);
        out.addAll(ours);
        return List.copyOf(out);
    }

    /** Called by the mixin when the loot modifiers load: the manager the self-test reads. */
    public static void noteManager(LootModifierManager m) {
        manager = new WeakReference<>(m);
    }

    /** The loot modifiers in the order rolls apply them; empty before the first datapack load. */
    public static List<IGlobalLootModifier> appliedOrder() {
        LootModifierManager m = manager.get();
        return m == null ? List.of() : List.copyOf(m.getAllLootMods());
    }

    /** Rolls the filter saw and stacks it removed since start (diagnostics). */
    public static long[] counts() {
        return new long[] {ROLLS.get(), REMOVED.get()};
    }
}
