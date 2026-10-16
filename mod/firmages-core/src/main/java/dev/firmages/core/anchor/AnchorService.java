package dev.firmages.core.anchor;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.crown.LensItem;
import dev.firmages.core.shrine.Blessing;
import dev.firmages.core.shrine.BlessingIndex;
import dev.firmages.core.shrine.BlessingSources;
import dev.firmages.core.shrine.Blessings;
import dev.firmages.core.shrine.ShrineState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Sky Anchors (SPEC-himmel §29), server thread only. An anchor is a blessing source of the kind {@code ANCHOR}
 * ({@link BlessingSources}): from the Space Age, while the home shrine is intact, it carries the blessings of the
 * shards it holds AND whose tiers are awakened within {@code anchor.radius}, in its own dimension (any dimension).
 * Hearthward follows the tier-0 shard. Keystone lending and the Gathering change nothing: they never touch the shrine's
 * {@code intact} flag or its awakened tiers, which is all an anchor reads. A shard is never a relic.
 */
public final class AnchorService {
    /** The Space Age as last seen by the tick, so its grant or revoke marks the blessing index stale. */
    private static volatile boolean lastAgeOpen;

    private AnchorService() {}

    /** True while the Space Age is open (anchors act). */
    public static boolean ageOpen(MinecraftServer server) {
        return AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_7);
    }

    /** The anchors' blessing sources, with their full shard masks (the index cuts them to the awakened tiers). */
    public static List<BlessingSources.Source> sources(MinecraftServer server, int awakened) {
        if (!ageOpen(server)) return List.of();
        AnchorSavedData sd = AnchorSavedData.get(server);
        int radius = ServerConfig.anchorRadius();
        List<BlessingSources.Source> out = new ArrayList<>();
        for (Map.Entry<GlobalPos, Integer> e : sd.anchors().entrySet()) {
            GlobalPos where = e.getKey();
            ServerLevel level = server.getLevel(where.dimension());
            if (level == null) continue; // a dimension that is gone
            if (level.isLoaded(where.pos()) && !level.getBlockState(where.pos()).is(AnchorRegistry.SKY_ANCHOR.get())) {
                sd.remove(where); // checked against the block: the anchor is gone (a command, a world edit)
                continue;
            }
            int tiers = e.getValue();
            if (AnchorRules.count(tiers) == 0) continue;
            out.add(new BlessingSources.Source(BlessingSources.Kind.ANCHOR, where.dimension().location().toString(), where.pos().getX(),
                where.pos().getY(), where.pos().getZ(), radius, tiers, AnchorRules.holds(tiers, 0)));
        }
        return out;
    }

    /** Server tick: a grant or revoke of the Space Age marks the index stale (once a second). */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        boolean open = ageOpen(server);
        if (open != lastAgeOpen) {
            lastAgeOpen = open;
            BlessingIndex.markDirty();
        }
    }

    static void clear() {
        lastAgeOpen = false;
    }

    // ---------------------------------------------------------------- interaction

    /**
     * Right-click with a shard: it goes into its tier's slot; a second shard of the same tier is refused.
     *
     * @return true when the shard was set
     */
    public static boolean insert(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, ItemStack stack) {
        if (!(level.getBlockEntity(pos) instanceof SkyAnchorBlockEntity be)) return false;
        int tier = BlessingShardItem.tier(stack);
        if (tier < 0) return false;
        if (AnchorRules.holds(be.tiers, tier)) {
            if (player != null) player.displayClientMessage(Component.translatable("firmages.anchor.duplicate").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        setTiers(level, pos, be, AnchorRules.with(be.tiers, tier));
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.6F + tier * 0.1F);
        return true;
    }

    /** Sneak and right-click with an empty hand: the highest shard comes back (empty when there is none). */
    public static ItemStack takeHighest(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SkyAnchorBlockEntity be)) return ItemStack.EMPTY;
        int tier = AnchorRules.highest(be.tiers);
        if (tier < 0) return ItemStack.EMPTY;
        setTiers(level, pos, be, be.tiers & ~(1 << tier));
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 0.6F + tier * 0.1F);
        return BlessingShardItem.of(tier);
    }

    private static void setTiers(ServerLevel level, BlockPos pos, SkyAnchorBlockEntity be, int tiers) {
        be.setTiers(tiers);
        AnchorSavedData.get(level.getServer()).put(GlobalPos.of(level.dimension(), pos.immutable()), tiers);
        BlessingIndex.markDirty();
        refresh(level, pos, be);
    }

    static void onPlaced(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SkyAnchorBlockEntity be)) return;
        AnchorSavedData.get(level.getServer()).put(GlobalPos.of(level.dimension(), pos.immutable()), be.tiers);
        BlessingIndex.markDirty();
        refresh(level, pos, be);
    }

    /** The block is going: its shards drop, its record goes. */
    static void onRemoved(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SkyAnchorBlockEntity be) {
            for (int t = 0; t < BlessingSources.TIERS; t++) {
                if (AnchorRules.holds(be.tiers, t)) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, BlessingShardItem.of(t));
            }
            be.tiers = 0;
        }
        if (AnchorSavedData.get(level.getServer()).remove(GlobalPos.of(level.dimension(), pos.immutable()))) BlessingIndex.markDirty();
    }

    /** The block entity loaded: its own record wins (checked against the block on load). */
    static void onLoaded(ServerLevel level, BlockPos pos, SkyAnchorBlockEntity be) {
        if (AnchorSavedData.get(level.getServer()).put(GlobalPos.of(level.dimension(), pos.immutable()), be.tiers)) {
            FirmagesCore.LOGGER.debug("Sky Anchor at {} {} registered on load (tiers {})", level.dimension().location(), pos, be.tiers);
            BlessingIndex.markDirty();
        }
    }

    // ---------------------------------------------------------------- view

    /** What an anchor holding {@code tiers} does right now. */
    public static AnchorRules.Status status(MinecraftServer server, int tiers) {
        ShrineState.Snapshot st = ShrineState.get();
        return AnchorRules.status(ageOpen(server), st != null && st.intact(), st == null ? 0 : st.awakened(), tiers);
    }

    /** The tiers an anchor holding {@code tiers} carries right now. */
    public static int activeMask(MinecraftServer server, int tiers) {
        ShrineState.Snapshot st = ShrineState.get();
        return AnchorRules.activeMask(ageOpen(server), st != null && st.intact(), st == null ? 0 : st.awakened(), tiers);
    }

    /** Refreshes the synced view and the {@code active} blockstate of one anchor (once a second and on every change). */
    public static void refresh(ServerLevel level, BlockPos pos, SkyAnchorBlockEntity be) {
        MinecraftServer server = level.getServer();
        AnchorRules.Status status = status(server, be.tiers);
        int active = activeMask(server, be.tiers);
        List<String> ids = Blessings.blessingsOfMask(active).stream().map(Blessing::nameKey).toList(); // lang keys
        int top = AnchorRules.highest(active);
        int beam = top < 0 ? 0 : 0xFF000000 | LensItem.serverColor(top);
        int radius = ServerConfig.anchorRadius();
        if (status != be.status || active != be.activeMask || !ids.equals(be.blessings) || beam != be.beamColor || radius != be.radius) {
            be.status = status;
            be.activeMask = active;
            be.blessings = ids;
            be.beamColor = beam;
            be.radius = radius;
            be.changed();
        }
        BlockState s = level.getBlockState(pos);
        if (s.is(AnchorRegistry.SKY_ANCHOR.get()) && s.getValue(SkyAnchorBlock.ACTIVE) != (active != 0)) {
            level.setBlock(pos, s.setValue(SkyAnchorBlock.ACTIVE, active != 0), 3);
        }
    }
}
