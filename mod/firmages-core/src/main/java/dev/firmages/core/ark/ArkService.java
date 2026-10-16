package dev.firmages.core.ark;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.net.ArkSyncPayload;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginRegistry;
import dev.firmages.core.origin.OriginSavedData;
import dev.firmages.core.origin.OriginService;
import dev.firmages.core.shrine.ShrineDataLoader;
import dev.firmages.core.shrine.ShrineTier;
import dev.firmages.core.text.Numbers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The Origin Archipelago at runtime (SPEC §31.8), post-game only: nothing outside this package reads its state, and a
 * world that never touches it finishes the pack. After {@code finale_won} each island's plinth takes living samples
 * of its Age (flora, fauna, work, and from the Arcane island on a crown of four alloy ingots); every group done grows
 * the next stage template, and a complete island lights its beam in the ring of nine sockets around the altar.
 */
public final class ArkService {
    /** Blocks around the plinth in which a led animal counts. */
    public static final double FAUNA_RANGE = 4.0;
    private static final Map<Integer, ArkGrowth.Batch> BATCHES = new HashMap<>();

    /** What a click with an item did. */
    public enum Use { DONE, PASS }

    private ArkService() {}

    public static void register(IEventBus bus) {
        bus.addListener(ArkService::onReload);
        bus.addListener(ArkService::onServerTick);
        bus.addListener(ArkService::onChangedDimension);
        bus.addListener(ArkService::onLogin);
        bus.addListener(ArkService::onServerStopped);
    }

    private static void onReload(AddReloadListenerEvent event) {
        event.addListener(new ArkDataLoader());
    }

    // ---------------------------------------------------------------- lookups

    /** True once the final boss fell: before that every island sleeps. */
    public static boolean awake(MinecraftServer server) {
        return OriginSavedData.get(server).won();
    }

    public static ArkProgress progress(MinecraftServer server, int i) {
        return OriginSavedData.get(server).ark().island(i);
    }

    /** The island whose plinth stands at {@code pos} (its recorded position, or within the island's reach). */
    public static Optional<Integer> islandAt(ServerLevel level, BlockPos pos) {
        if (level.dimension() != OriginRegistry.ORIGIN) return Optional.empty();
        long l = pos.asLong();
        for (int i = 0; i < ArkData.ISLANDS; i++) if (progress(level.getServer(), i).plinth == l) return Optional.of(i);
        for (int i = 0; i < ArkData.ISLANDS; i++) {
            BlockPos top = OriginArena.islandTop(i);
            int r = OriginArena.arkRadius(i) + 1;
            int dx = pos.getX() - top.getX();
            int dz = pos.getZ() - top.getZ();
            if (dx * dx + dz * dz <= r * r && pos.getY() >= top.getY() - ArkWork.BELOW && pos.getY() <= top.getY() + ArkWork.ABOVE) return Optional.of(i);
        }
        return Optional.empty();
    }

    /** Matches by item id, or by item tag through the registry. */
    public static final ArkRules.Matcher MATCHER = (what, itemId) -> {
        if (!what.byTag()) return what.ids().contains(itemId);
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        ResourceLocation tag = ResourceLocation.tryParse(what.tag());
        if (id == null || tag == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        return BuiltInRegistries.ITEM.wrapAsHolder(BuiltInRegistries.ITEM.get(id)).is(TagKey.create(Registries.ITEM, tag));
    };

    /** Records the plinths the arena step stood up ({@link OriginArena#migrateV3}). */
    public static void recordPlinths(MinecraftServer server, BlockPos[] plinths) {
        ArkState s = OriginSavedData.get(server).ark();
        for (int i = 0; i < plinths.length && i < ArkData.ISLANDS; i++) if (plinths[i] != null) s.island(i).plinth = plinths[i].asLong();
        s.setDirty();
    }

    // ---------------------------------------------------------------- samples

    /**
     * Gives {@code stack} (or what of it the island takes) to the island of the plinth at {@code pos}: by hand
     * ({@code player} set) or by pipe. @return how many items were (or, simulated, would be) taken
     */
    public static int offer(ServerLevel level, BlockPos pos, ItemStack stack, boolean simulate, @Nullable ServerPlayer player) {
        if (stack.isEmpty() || !awake(level.getServer())) return 0;
        Optional<Integer> island = islandAt(level, pos);
        if (island.isEmpty()) return 0;
        int i = island.get();
        Optional<ArkData.Island> isl = ArkDataLoader.current().island(i);
        if (isl.isEmpty()) return 0;
        ArkProgress p = progress(level.getServer(), i);
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Optional<ArkRules.Take> take = ArkRules.accept(isl.get(), p, id, stack.getCount(), MATCHER);
        if (take.isEmpty()) return 0;
        if (simulate) return take.get().count();
        List<String> done = ArkRules.apply(isl.get(), p, id, take.get(), MATCHER);
        OriginSavedData.get(level.getServer()).ark().setDirty();
        FirmagesCore.LOGGER.info("The Origin: island age_{} took {} x {} for its {}{}", i, take.get().count(), id, take.get().group(),
            player != null ? " from " + player.getGameProfile().getName() : " by pipe");
        if (player != null) {
            player.displayClientMessage(progressLine(isl.get(), p, take.get().group()), true);
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.2F);
        groupsDone(level, i, done);
        return take.get().count();
    }

    /** Right-click with an item: a led animal first, then the item as a sample; a block item it does not want is placed. */
    public static Use useWithItem(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
        Optional<Integer> island = islandAt(level, pos);
        if (island.isEmpty()) {
            tell(player, Component.translatable("firmages.ark.inert"));
            return Use.DONE;
        }
        if (!awake(level.getServer())) {
            tell(player, Component.translatable("firmages.ark.sleeps"));
            return Use.DONE;
        }
        if (fauna(level, island.get(), pos, player)) return Use.DONE;
        int taken = offer(level, pos, stack, false, player);
        if (taken > 0) {
            if (!player.getAbilities().instabuild) stack.shrink(taken);
            return Use.DONE;
        }
        if (stack.getItem() instanceof BlockItem) return Use.PASS;
        tell(player, Component.translatable("firmages.ark.not_wanted"));
        return Use.DONE;
    }

    /** Right-click with an empty hand: a led animal, then the island's work, then what it still waits for. */
    public static void useEmptyHand(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<Integer> island = islandAt(level, pos);
        if (island.isEmpty()) {
            tell(player, Component.translatable("firmages.ark.inert"));
            return;
        }
        if (!awake(level.getServer())) {
            tell(player, Component.translatable("firmages.ark.sleeps"));
            return;
        }
        int i = island.get();
        if (fauna(level, i, pos, player)) return;
        if (checkWork(level, i)) return;
        tell(player, status(level.getServer(), i));
    }

    /**
     * Animals that {@code player} leads within {@link #FAUNA_RANGE} of the plinth: the first one the island asks for
     * becomes its fauna sample (persistent, the lead goes back to the player). @return true if one was taken
     */
    public static boolean fauna(ServerLevel level, int i, BlockPos plinth, ServerPlayer player) {
        Optional<ArkData.Island> isl = ArkDataLoader.current().island(i);
        ArkProgress p = progress(level.getServer(), i);
        if (isl.isEmpty() || !isl.get().hasFauna() || p.has("fauna")) return false;
        List<Mob> led = level.getEntitiesOfClass(Mob.class, new AABB(plinth).inflate(FAUNA_RANGE), m -> m.isAlive() && m.getLeashHolder() == player);
        for (Mob m : led) {
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString();
            if (!isl.get().fauna().contains(type)) continue;
            m.setPersistenceRequired();
            m.dropLeash(true, false);
            if (!player.getAbilities().instabuild && !player.getInventory().add(new ItemStack(Items.LEAD))) player.drop(new ItemStack(Items.LEAD), false);
            p.faunaEntity = type;
            p.groups.add("fauna");
            OriginSavedData.get(level.getServer()).ark().setDirty();
            FirmagesCore.LOGGER.info("The Origin: island age_{} took a {} from {} as its fauna", i, type, player.getGameProfile().getName());
            level.playSound(null, plinth, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.0F);
            groupsDone(level, i, List.of("fauna"));
            return true;
        }
        if (!led.isEmpty()) tell(player, Component.translatable("firmages.ark.wrong_animal"));
        return !led.isEmpty();
    }

    /** Checks island {@code i}'s work (plinth click, {@code ark check}). @return true if the work group became done now */
    public static boolean checkWork(ServerLevel level, int i) {
        Optional<ArkData.Island> isl = ArkDataLoader.current().island(i);
        ArkProgress p = progress(level.getServer(), i);
        if (isl.isEmpty() || p.has("work") || !awake(level.getServer())) return false;
        Optional<BlockPos> at = ArkWork.find(level, OriginArena.islandTop(i), i, isl.get().work());
        if (at.isEmpty()) return false;
        p.groups.add("work");
        OriginSavedData.get(level.getServer()).ark().setDirty();
        FirmagesCore.LOGGER.info("The Origin: island age_{} hears its work: {} at {}", i,
            BuiltInRegistries.BLOCK.getKey(level.getBlockState(at.get()).getBlock()), at.get().toShortString());
        groupsDone(level, i, List.of("work"));
        return true;
    }

    /** Groups that became done: tell the players in The Origin, grow, maybe complete. */
    private static void groupsDone(ServerLevel level, int i, List<String> done) {
        if (done.isEmpty()) return;
        for (String g : done) {
            FirmagesCore.LOGGER.info("The Origin: island age_{}: group {} done", i, g);
            broadcast(level, Component.translatable("firmages.ark.done." + g, Component.translatable("firmages.ark.island." + i)));
        }
        update(level.getServer());
    }

    // ---------------------------------------------------------------- growth and completion

    /**
     * Once a second: islands without fauna get that group once the finale is won, every island whose groups outrun
     * its stage starts growing the next one, and a complete island lights its socket.
     */
    public static void update(MinecraftServer server) {
        ServerLevel level = OriginService.level(server);
        if (level == null || !awake(server)) return;
        ArkData data = ArkDataLoader.current();
        ArkState state = OriginSavedData.get(server).ark();
        boolean changed = false;
        boolean celebrate = false;
        for (int i = 0; i < ArkData.ISLANDS; i++) {
            Optional<ArkData.Island> isl = data.island(i);
            if (isl.isEmpty()) continue;
            ArkProgress p = state.island(i);
            List<String> auto = ArkRules.settle(isl.get(), p, MATCHER, true);
            if (!auto.isEmpty()) {
                for (String g : auto) FirmagesCore.LOGGER.info("The Origin: island age_{}: group {} done", i, g);
                state.setDirty();
                changed = true;
            }
            if (ArkRules.targetStage(p) > p.stage && !BATCHES.containsKey(i)) startStage(level, i, isl.get(), p, p.stage + 1);
            if (!p.complete && p.stage >= ArkRules.targetStage(p) && ArkRules.isComplete(isl.get(), p)) {
                p.complete = true;
                state.setDirty();
                changed = true;
                FirmagesCore.LOGGER.info("The Origin: island age_{} is complete; its beam lights at the altar ({} of {})", i,
                    Integer.bitCount(state.completeMask()), ArkData.ISLANDS);
                broadcast(level, Component.translatable("firmages.ark.complete", Component.translatable("firmages.ark.island." + i)));
                if (ArkRules.allComplete(state.completeMask())) {
                    celebrate = true;
                    FirmagesCore.LOGGER.info("The Origin: all nine islands are complete; nine beams stand at the altar");
                    broadcast(level, Component.translatable("firmages.ark.all"));
                }
            }
        }
        if (changed) sync(server, celebrate);
    }

    private static void startStage(ServerLevel level, int i, ArkData.Island isl, ArkProgress p, int stage) {
        if (stage < 1 || stage > isl.stages().size()) return;
        ResourceLocation id = ResourceLocation.tryParse(isl.stages().get(stage - 1));
        Optional<List<ArkGrowth.Step>> blocks = id == null ? Optional.empty() : ArkGrowth.blocks(level, id, OriginArena.islandTop(i));
        if (blocks.isEmpty()) {
            FirmagesCore.LOGGER.error("The Origin: stage template {} of island age_{} is missing; the stage counts as grown without blocks", id, i);
            p.stage = stage;
            OriginSavedData.get(level.getServer()).ark().setDirty();
            sync(level.getServer(), false);
            return;
        }
        BlockPos plinth = p.plinth != Long.MIN_VALUE ? BlockPos.of(p.plinth) : OriginArena.islandTop(i).above();
        int ticks = ServerConfig.loaded() ? ServerConfig.ARK_GROWTH_TICKS.get() : 200;
        BATCHES.put(i, new ArkGrowth.Batch(i, stage, ArkGrowth.schedule(blocks.get(), plinth, ticks)));
        FirmagesCore.LOGGER.info("The Origin: island age_{} begins to grow stage {} ({} blocks over {} ticks)", i, stage, blocks.get().size(), ticks);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 == 0) update(server);
        if (BATCHES.isEmpty()) return;
        ServerLevel level = OriginService.level(server);
        if (level == null) return;
        ArkState state = OriginSavedData.get(server).ark();
        List<Integer> finished = new ArrayList<>();
        for (ArkGrowth.Batch b : BATCHES.values()) {
            ArkProgress p = state.island(b.island);
            int before = b.placed;
            boolean done = ArkGrowth.tick(level, p, b);
            // Every grown block is recorded at once, so a save mid-stage knows it (a later stage may replace only those).
            if (b.placed != before) state.setDirty();
            if (done) {
                p.stage = b.stage;
                finished.add(b.island);
                FirmagesCore.LOGGER.info("The Origin: island age_{} grew to stage {} ({} blocks placed, {} kept for blocks already there)",
                    b.island, b.stage, b.placed, b.kept);
            }
        }
        if (finished.isEmpty()) return;
        finished.forEach(BATCHES::remove);
        state.setDirty();
        sync(server, false);
        update(server);
    }

    /** True while island {@code i} grows a stage. */
    public static boolean growing(int i) {
        return BATCHES.containsKey(i);
    }

    // ---------------------------------------------------------------- operators

    /**
     * {@code ark grant <island> <group>} (debug): marks the group done as if its samples came (the crown: every entry
     * full). @return false if it was already done, the island has no rules or the finale is not won
     */
    public static boolean grant(MinecraftServer server, int i, String group) {
        Optional<ArkData.Island> isl = ArkDataLoader.current().island(i);
        ArkProgress p = progress(server, i);
        if (isl.isEmpty() || p.has(group) || !awake(server)) return false;
        if (group.equals(ArkData.CROWN)) {
            for (int k = 0; k < isl.get().crown().size(); k++) p.crown.put(k, isl.get().crown().get(k).count());
        }
        p.groups.add(group);
        OriginSavedData.get(server).ark().setDirty();
        FirmagesCore.LOGGER.info("The Origin: island age_{}: group {} granted by an operator", i, group);
        ServerLevel level = OriginService.level(server);
        if (level != null) groupsDone(level, i, List.of(group));
        else update(server);
        return true;
    }

    /**
     * {@code ark reset <island>} (debug): forgets the island's samples and takes away its grown blocks that still stand
     * (a player's block in their place stays). The plinth and the animal stay. @return the blocks removed
     */
    public static int reset(MinecraftServer server, int i) {
        BATCHES.remove(i);
        ArkProgress p = progress(server, i);
        ServerLevel level = OriginService.level(server);
        int removed = level == null ? 0 : ArkGrowth.remove(level, p);
        p.clear();
        OriginSavedData.get(server).ark().setDirty();
        FirmagesCore.LOGGER.info("The Origin: island age_{} reset by an operator ({} grown blocks removed)", i, removed);
        sync(server, false);
        return removed;
    }

    // ---------------------------------------------------------------- text

    /** What the island still waits for, or that it is whole. */
    public static Component status(MinecraftServer server, int i) {
        Optional<ArkData.Island> isl = ArkDataLoader.current().island(i);
        if (isl.isEmpty()) return Component.translatable("firmages.ark.inert");
        ArkProgress p = progress(server, i);
        if (p.complete) return Component.translatable("firmages.ark.whole");
        for (String g : ArkData.ALL_GROUPS) {
            if (p.has(g) || (g.equals(ArkData.CROWN) && !isl.get().hasCrown())) continue;
            return progressLine(isl.get(), p, g);
        }
        return Component.translatable("firmages.ark.growing");
    }

    /** "The island waits for four kinds of sapling: two of four given." for a group with a count, else the plain wish. */
    static Component progressLine(ArkData.Island isl, ArkProgress p, String group) {
        Component wish = Component.translatable("firmages.ark." + isl.age() + "." + group);
        if (p.has(group)) return Component.translatable("firmages.ark.done." + group, Component.translatable("firmages.ark.island." + isl.island()));
        int given;
        int needed;
        switch (group) {
            case "flora" -> {
                ArkData.Flora best = isl.flora().get(0);
                for (ArkData.Flora f : isl.flora()) if (ArkRules.given(f, p, MATCHER) > ArkRules.given(best, p, MATCHER)) best = f;
                given = ArkRules.given(best, p, MATCHER);
                needed = best.needed();
            }
            case ArkData.CROWN -> {
                given = 0;
                needed = 0;
                for (int k = 0; k < isl.crown().size(); k++) {
                    given += p.crown.getOrDefault(k, 0);
                    needed += isl.crown().get(k).count();
                }
            }
            default -> {
                return Component.translatable("firmages.ark.wait", wish);
            }
        }
        if (given == 0) return Component.translatable("firmages.ark.wait", wish);
        return Component.translatable("firmages.ark.wait.progress", wish, Numbers.words(given), Numbers.words(needed));
    }

    /** One status line per island for {@code ark status}. */
    public static List<String> statusLines(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        ArkData data = ArkDataLoader.current();
        ArkState s = OriginSavedData.get(server).ark();
        out.add(String.format(Locale.ROOT, "Archipelago: %s, islands with rules %s, complete %d of %d%s", awake(server) ? "awake (finale won)" : "asleep (finale not won)",
            data.islands().keySet(), Integer.bitCount(s.completeMask()), ArkData.ISLANDS, data.errors().isEmpty() ? "" : ", data errors " + data.errors()));
        for (int i = 0; i < ArkData.ISLANDS; i++) {
            ArkProgress p = s.island(i);
            Optional<ArkData.Island> isl = data.island(i);
            String crown = isl.map(x -> x.hasCrown() ? (p.has(ArkData.CROWN) ? "done" : p.crown.toString() + " of " + x.crown().stream().map(ArkData.CrownEntry::count).toList()) : "none").orElse("?");
            out.add(String.format(Locale.ROOT, "  age_%d: groups %s, flora given %s, fauna %s, crown %s, stage %d%s%s, plinth %s, %d grown blocks", i,
                p.groups, p.flora, p.faunaEntity.isEmpty() ? "-" : p.faunaEntity, crown, p.stage, growing(i) ? " (growing)" : "",
                p.complete ? ", COMPLETE" : "", p.plinth == Long.MIN_VALUE ? "none" : BlockPos.of(p.plinth).toShortString(), p.grown.size()));
        }
        return out;
    }

    private static void tell(ServerPlayer player, Component msg) {
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static void broadcast(ServerLevel level, Component msg) {
        for (ServerPlayer p : level.players()) p.displayClientMessage(msg.copy().withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    // ---------------------------------------------------------------- sync

    /** The payload for the current state. */
    public static ArkSyncPayload payload(MinecraftServer server, boolean celebrate) {
        ArkState s = OriginSavedData.get(server).ark();
        byte[] stages = new byte[ArkData.ISLANDS];
        int[] colors = new int[ArkData.ISLANDS];
        for (int i = 0; i < ArkData.ISLANDS; i++) {
            stages[i] = (byte) s.island(i).stage;
            colors[i] = ShrineDataLoader.current().tier(i).map(ShrineTier::response).map(ShrineTier.Response::beamColor).orElse(0xFFFFFFFF);
        }
        return new ArkSyncPayload(stages, s.completeMask(), colors, celebrate);
    }

    /** Sends the state to everyone in The Origin. */
    public static void sync(MinecraftServer server, boolean celebrate) {
        ServerLevel level = OriginService.level(server);
        if (level == null || level.players().isEmpty()) return;
        ArkSyncPayload payload = payload(server, celebrate);
        for (ServerPlayer p : level.players()) PacketDistributor.sendToPlayer(p, payload);
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getTo() == OriginRegistry.ORIGIN && event.getEntity() instanceof ServerPlayer p) PacketDistributor.sendToPlayer(p, payload(p.server, false));
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.level().dimension() == OriginRegistry.ORIGIN) PacketDistributor.sendToPlayer(p, payload(p.server, false));
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        BATCHES.clear();
    }

    /** The registered item for an id, if any (selftest). */
    static Optional<Item> item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(rl).filter(it -> it != Items.AIR);
    }
}
