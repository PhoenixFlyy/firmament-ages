package dev.firmages.core.shrine;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.compat.modonomicon.ShrineMultiblocks;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.net.ShrineSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Consecration and maintenance mode (SPEC §17, M7). When Caelum accepts a ring's offering, every block of the ring
 * that has a role in {@code consecration.json} becomes {@code firmages:consecrated_<role>} with the ring's accent,
 * from the heart outward over {@code shrine.consecrationTicks} (after the Age reload, so the freeze does not cut the
 * show in two); the original block state of each position goes into {@link ShrineSavedData} (NBT). Rings of
 * awakened tiers that still stand in Age material (existing worlds, Ages from quests or admins, repaired holes) are
 * consecrated on their next validation. Consecrated blocks are unbreakable; maintenance mode (sneak and punch the
 * heart with an empty hand, or the command) lets survival players break them for {@code shrine.maintenanceSeconds},
 * and they drop their stored original. A hole only makes the ring invalid (blessings pause, prayers are refused);
 * placing the original material or a consecrated block of the role repairs it, and it is consecrated again once
 * maintenance is off. Removing the heart turns every consecrated block back into its original.
 * Everything runs on the server thread.
 */
public final class ShrineConsecration {
    /** Ticks after the reload before a ceremony's consecration starts. */
    static final int CEREMONY_DELAY = 20;
    /** Up to this many blocks are consecrated at once (repairs), more are spread from the heart outward. */
    static final int INSTANT_MAX = 4;
    private static final int RETRY_TICKS = 20;
    private static final int MAX_RETRIES = 30;
    private static final int TOGGLE_COOLDOWN = 10;

    /** One scheduled block change. {@code expected}: the state seen at scheduling; anything else at run time skips it. */
    record Task(BlockPos pos, int ring, Consecration.Role role, BlockState expected, int offset, @Nullable BlockState revertTo, int retries) {
        boolean revert() {
            return revertTo != null;
        }

        Task retry() {
            return new Task(pos, ring, role, expected, offset + RETRY_TICKS, revertTo, retries + 1);
        }
    }

    static final class Batch {
        final ResourceKey<Level> dimension;
        final List<Task> tasks;
        final boolean afterReload;
        final long earliest;
        /** paced time when the batch started, and when the Age reload was first seen idle (after-reload batches) */
        double start = -1;
        double idleAt = -1;
        /** blocks changed so far and the ticks (relative to start) of the first and the last change */
        int changed;
        double firstTick = -1;
        double lastTick = -1;
        final java.util.Set<Integer> rings = new java.util.TreeSet<>();

        Batch(ResourceKey<Level> dimension, List<Task> tasks, boolean afterReload, long earliest) {
            this.dimension = dimension;
            this.tasks = new ArrayList<>(tasks);
            this.afterReload = afterReload;
            this.earliest = earliest;
        }
    }

    private static final List<Batch> BATCHES = new ArrayList<>();
    /** Positions with a scheduled task (pos.asLong()). */
    private static final Map<Long, Task> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST_TOGGLE = new HashMap<>();
    private static long clock;
    /**
     * The batches' own time: one per server tick, except while the server catches up after a freeze. After a long
     * tick (an Age reload, a grant stall) vanilla runs the missed ticks back to back whenever its "Can't keep up"
     * warning is rate-limited (15 s), and on {@code clock} a 6 s ceremony would turn in a fraction of a second. While
     * that debt lasts, a tick counts only its real duration. A server that is simply fast (the GameTest server,
     * {@code /tick sprint}) has no debt and counts every tick.
     */
    private static double paced;
    private static long lastTickNanos;
    private static long behindNanos;
    private static final long MAX_BEHIND_NANOS = 30_000_000_000L;
    private static boolean internal;
    private static boolean syncDirty;
    private static int converted;

    private ShrineConsecration() {}

    // ---------------------------------------------------------------- maintenance

    /** Maintenance mode is on (client: as last synced). */
    public static boolean maintenanceActive(Level level) {
        if (level.isClientSide()) return ShrineSyncPayload.clientMaintenance();
        return level.getServer() != null && maintenanceActive(level.getServer());
    }

    public static boolean maintenanceActive(MinecraftServer s) {
        long until = ShrineSavedData.get(s).maintenanceUntil();
        return until > 0 && s.overworld().getGameTime() < until;
    }

    public static int maintenanceSecondsLeft(MinecraftServer s) {
        long left = ShrineSavedData.get(s).maintenanceUntil() - s.overworld().getGameTime();
        return maintenanceActive(s) ? (int) ((left + 19) / 20) : 0;
    }

    static int maintenanceSeconds() {
        return ServerConfig.loaded() ? ServerConfig.SHRINE_MAINTENANCE_SECONDS.get() : 60;
    }

    /** Sneak plus an empty-hand punch on the heart toggles maintenance mode for the team. */
    public static void toggleMaintenance(ServerLevel level, BlockPos heart, ServerPlayer player) {
        Long last = LAST_TOGGLE.get(player.getUUID());
        if (last != null && clock - last < TOGGLE_COOLDOWN) return;
        LAST_TOGGLE.put(player.getUUID(), clock);
        if (!ShrineService.isTheHeart(level, heart)) {
            player.displayClientMessage(ShrineService.msg("firmages.shrine.inert"), true);
            return;
        }
        setMaintenance(level.getServer(), !maintenanceActive(level.getServer()), player.getGameProfile().getName());
    }

    /** Turns maintenance mode on or off. @param by the player's name, or null (timer, command without player) @return true if it changed */
    public static boolean setMaintenance(MinecraftServer s, boolean on, @Nullable String by) {
        ShrineSavedData sd = ShrineSavedData.get(s);
        // off also ends a mode whose time ran out but that was not switched off yet
        if (on ? maintenanceActive(s) || sd.heart().isEmpty() : sd.maintenanceUntil() == 0) return false;
        int seconds = maintenanceSeconds();
        sd.setMaintenanceUntil(on ? s.overworld().getGameTime() + seconds * 20L : 0);
        Component m = on
            ? ShrineService.msg("firmages.shrine.maintenance.on", by == null ? "An operator" : by, seconds).withStyle(ChatFormatting.GOLD)
            : ShrineService.msg(by == null ? "firmages.shrine.maintenance.off" : "firmages.shrine.maintenance.off_by", by == null ? "" : by)
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) p.sendSystemMessage(m);
        FirmagesCore.LOGGER.info("Shrine maintenance {} ({})", on ? "on for " + seconds + " s" : "off", by == null ? "timer or console" : by);
        ShrineService.heart(s).ifPresent(h -> {
            BlockState st = h.level().getBlockState(h.pos());
            if (st.is(ShrineRegistry.SHRINE_HEART.get()) && st.getValue(ShrineHeartBlock.MAINTENANCE) != on) {
                h.level().setBlock(h.pos(), st.setValue(ShrineHeartBlock.MAINTENANCE, on), 3);
            }
            h.level().playSound(null, h.pos(), ShrineRegistry.MAINTENANCE.get(), SoundSource.BLOCKS, 1.0F, on ? 0.8F : 1.2F);
            // off: the next validation consecrates what stands again
            h.be().invalidate();
        });
        syncAll(s);
        return true;
    }

    // ---------------------------------------------------------------- consecration

    /** The ceremony of ring {@code ring}: every block of the ring with a role, from the heart outward. @return blocks scheduled */
    public static int scheduleRing(ServerLevel level, BlockPos heart, int ring, Rotation rotation, boolean afterReload) {
        Optional<String> mb = ShrineService.data().tier(ring).flatMap(ShrineTier::multiblock);
        if (mb.isEmpty() || rotation == null) return 0;
        List<ShrineMultiblocks.Cell> cells = ShrineMultiblocks.cells(level, heart, ResourceLocation.parse(mb.get()), rotation);
        List<Pending> todo = new ArrayList<>();
        for (ShrineMultiblocks.Cell c : cells) {
            if (needsConsecration(level, c, ring)) todo.add(new Pending(c.pos(), ring, c.role()));
        }
        schedule(level, heart, todo, afterReload, ServerConfig.loaded() ? ServerConfig.SHRINE_CONSECRATION_TICKS.get() : 120);
        if (!todo.isEmpty()) FirmagesCore.LOGGER.info("Shrine: consecrating ring {} ({} blocks{})", ring, todo.size(), afterReload ? ", after the reload" : "");
        return todo.size();
    }

    /**
     * After every validation: the standing rings of awakened tiers (ring k with k below {@code awakened}) are
     * consecrated where they still hold Age material (migration of existing worlds, Ages from quests, repairs).
     * Never while maintenance is on.
     */
    static void afterValidation(ServerLevel level, BlockPos heart, ShrineMultiblocks.RingCheck[] checks, int awakened) {
        if (maintenanceActive(level.getServer())) return;
        List<Pending> todo = new ArrayList<>();
        for (int k = 0; k < checks.length && k < awakened; k++) {
            ShrineMultiblocks.RingCheck c = checks[k];
            if (c == null || !c.valid()) continue;
            for (ShrineMultiblocks.Cell cell : c.cells()) {
                if (needsConsecration(level, cell, k)) todo.add(new Pending(cell.pos(), k, cell.role()));
            }
        }
        if (todo.isEmpty()) return;
        int span = todo.size() <= INSTANT_MAX ? 0 : ServerConfig.loaded() ? ServerConfig.SHRINE_CONSECRATION_TICKS.get() : 120;
        FirmagesCore.LOGGER.info("Shrine: consecrating {} standing block(s) of awakened rings (rings {})", todo.size(),
            todo.stream().map(Pending::ring).distinct().sorted().toList());
        schedule(level, heart, todo, false, span);
    }

    private record Pending(BlockPos pos, int ring, Consecration.Role role) {}

    private static boolean needsConsecration(Level level, ShrineMultiblocks.Cell c, int ring) {
        if (c.role() == null || !c.ok() || PENDING.containsKey(c.pos().asLong())) return false;
        if (c.consecrated()) return c.accent() != ring;
        return !level.getBlockState(c.pos()).isAir();
    }

    /** Spreads {@code todo} over {@code span} ticks by horizontal distance from the heart (then height). */
    private static void schedule(ServerLevel level, BlockPos heart, List<Pending> todo, boolean afterReload, int span) {
        if (todo.isEmpty()) return;
        double min = Double.MAX_VALUE;
        double max = 0;
        for (Pending p : todo) {
            double d = dist(heart, p.pos());
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        List<Task> tasks = new ArrayList<>();
        for (Pending p : todo) {
            double f = max - min < 0.001 ? 0 : (dist(heart, p.pos()) - min) / (max - min);
            int offset = (int) Math.round(f * span) + Math.max(0, p.pos().getY() - heart.getY());
            Task t = new Task(p.pos(), p.ring(), p.role(), level.getBlockState(p.pos()), offset, null, 0);
            tasks.add(t);
            PENDING.put(p.pos().asLong(), t);
        }
        tasks.sort(Comparator.comparingInt(Task::offset));
        BATCHES.add(new Batch(level.dimension(), tasks, afterReload, clock + (afterReload ? CEREMONY_DELAY : 0)));
    }

    private static double dist(BlockPos heart, BlockPos p) {
        double dx = p.getX() - heart.getX();
        double dz = p.getZ() - heart.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The heart is gone: nothing is consecrated any more, every consecrated block turns back into its original. */
    static void onHeartRemoved(ServerLevel level) {
        BATCHES.clear();
        PENDING.clear();
        syncDirty = true; // clients drop the maintenance flag and the originals with the heart
        ShrineSavedData sd = ShrineSavedData.get(level.getServer());
        List<Task> tasks = new ArrayList<>();
        for (ShrineSavedData.Original o : sd.originals().values()) {
            Optional<BlockState> orig = toState(o.state());
            BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            tasks.add(new Task(o.pos(), o.ring(), null, air, 0, orig.orElse(air), 0));
        }
        if (tasks.isEmpty()) return;
        tasks.sort(Comparator.comparingInt(t -> t.pos().getY()));
        for (Task t : tasks) PENDING.put(t.pos().asLong(), t);
        BATCHES.add(new Batch(level.dimension(), tasks, false, clock + 1));
        FirmagesCore.LOGGER.info("Shrine heart removed: {} consecrated block(s) turn back into their originals", tasks.size());
    }

    /** Server tick: maintenance timer and countdown, scheduled consecrations, the client sync. */
    public static void tick(MinecraftServer s) {
        clock++;
        paced += pacedStep(s);
        ShrineSavedData sd = ShrineSavedData.get(s);
        if (sd.maintenanceUntil() > 0) {
            if (!maintenanceActive(s)) {
                setMaintenance(s, false, null);
            } else if (clock % 20 == 0) {
                countdown(s, sd);
            }
        }
        for (Iterator<Batch> it = BATCHES.iterator(); it.hasNext(); ) {
            Batch b = it.next();
            ServerLevel level = s.getLevel(b.dimension);
            if (level == null) {
                b.tasks.forEach(t -> PENDING.remove(t.pos().asLong()));
                it.remove();
                continue;
            }
            if (b.start < 0) {
                if (clock < b.earliest) continue;
                if (b.afterReload) {
                    // CEREMONY_DELAY ticks after the Age reload is idle, so the reload freeze does not cut the batch in two
                    if (AgeService.status(s).phase() != ReloadScheduler.Phase.IDLE) {
                        b.idleAt = -1;
                        continue;
                    }
                    if (b.idleAt < 0) b.idleAt = paced;
                    if (paced < b.idleAt + CEREMONY_DELAY) continue;
                }
                b.start = paced;
            }
            boolean sound = false;
            List<Task> later = new ArrayList<>();
            for (Iterator<Task> ti = b.tasks.iterator(); ti.hasNext(); ) {
                Task t = ti.next();
                if (b.start + t.offset() > paced) break;
                ti.remove();
                if (!level.isLoaded(t.pos())) {
                    if (t.retries() < MAX_RETRIES) later.add(t.retry());
                    else PENDING.remove(t.pos().asLong());
                    continue;
                }
                PENDING.remove(t.pos().asLong());
                if (t.revert()) {
                    revert(level, t);
                } else if (consecrate(level, t, !sound)) {
                    sound = true;
                    b.changed++;
                    b.rings.add(t.ring());
                    if (b.firstTick < 0) b.firstTick = paced - b.start;
                    b.lastTick = paced - b.start;
                    FirmagesCore.LOGGER.debug("Shrine: consecrated {} (ring {}, {}) at +{} ticks", t.pos().toShortString(), t.ring(), t.role(), Math.round(paced - b.start));
                }
            }
            for (Task t : later) {
                PENDING.put(t.pos().asLong(), t);
                b.tasks.add(t);
            }
            if (!later.isEmpty()) b.tasks.sort(Comparator.comparingInt(Task::offset));
            if (b.tasks.isEmpty()) {
                it.remove();
                if (b.changed > 0) FirmagesCore.LOGGER.info("Shrine: consecration done, {} block(s) of rings {} from +{} to +{} ticks", b.changed, b.rings, Math.round(b.firstTick), Math.round(b.lastTick));
            }
        }
        if (syncDirty && clock % 20 == 0) {
            syncDirty = false;
            syncAll(s);
        }
    }

    private static double pacedStep(MinecraftServer s) {
        long now = System.nanoTime();
        long dt = lastTickNanos == 0 ? 0 : now - lastTickNanos;
        lastTickNanos = now;
        long perTick = s.tickRateManager().nanosecondsPerTick();
        if (dt == 0 || s.tickRateManager().isSprinting()) {
            behindNanos = 0;
            return 1.0;
        }
        if (dt > perTick) {
            behindNanos = Math.min(MAX_BEHIND_NANOS, behindNanos + dt - perTick);
            return 1.0;
        }
        if (behindNanos <= 0) return 1.0;
        behindNanos -= perTick - dt;
        if (dt >= perTick * 4 / 5) behindNanos = 0; // on schedule again
        return (double) dt / perTick;
    }

    private static boolean consecrate(ServerLevel level, Task t, boolean withSound) {
        BlockState cur = level.getBlockState(t.pos());
        if (!cur.equals(t.expected())) return false; // changed since scheduling: the next validation decides again
        BlockState target = ShrineRegistry.consecrated(t.role(), t.ring());
        if (cur.getBlock() instanceof ConsecratedBlock) {
            setInternal(level, t.pos(), target);
            return false;
        }
        Optional<String> enc = encode(cur);
        if (enc.isEmpty()) return false;
        ShrineSavedData.get(level.getServer()).putOriginal(new ShrineSavedData.Original(t.pos().immutable(), t.ring(), enc.get()));
        if (level.getBlockEntity(t.pos()) != null) level.removeBlockEntity(t.pos()); // no inventory spills, no machine teardown drops
        setInternal(level, t.pos(), target);
        converted++;
        syncDirty = true;
        double x = t.pos().getX() + 0.5;
        double y = t.pos().getY() + 0.5;
        double z = t.pos().getZ() + 0.5;
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 6, 0.35, 0.35, 0.35, 0.02);
        level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.4, z, 10, 0.4, 0.4, 0.4, 0.5);
        if (withSound) level.playSound(null, t.pos(), ShrineRegistry.CONSECRATE.get(), SoundSource.BLOCKS, 0.6F, 0.8F + 0.06F * t.ring());
        return true;
    }

    private static void revert(ServerLevel level, Task t) {
        ShrineSavedData sd = ShrineSavedData.get(level.getServer());
        sd.removeOriginal(t.pos());
        syncDirty = true;
        if (!(level.getBlockState(t.pos()).getBlock() instanceof ConsecratedBlock)) return;
        BlockState to = t.revertTo();
        if (to == null || to.isAir()) {
            FirmagesCore.LOGGER.warn("Consecrated block at {} has no known original; it is removed", t.pos());
            to = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        setInternal(level, t.pos(), to);
        level.sendParticles(ParticleTypes.SMOKE, t.pos().getX() + 0.5, t.pos().getY() + 0.5, t.pos().getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.01);
    }

    private static void setInternal(ServerLevel level, BlockPos pos, BlockState state) {
        boolean was = internal;
        internal = true;
        try {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        } finally {
            internal = was;
        }
    }

    // ---------------------------------------------------------------- breaking and repair

    /** A consecrated block left its position (broken in maintenance, creative, commands): the ring re-validates. */
    static void onConsecratedGone(ServerLevel level, BlockPos pos) {
        if (internal) return;
        ShrineService.onBlockChanged(level, pos);
    }

    /** Survival breaking outside maintenance (and any breaking by machines) is refused. */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof ConsecratedBlock)) return;
        Player p = event.getPlayer();
        if (p.isCreative() && !(p instanceof FakePlayer)) return; // operators
        if (!(p instanceof FakePlayer) && event.getLevel() instanceof ServerLevel sl && maintenanceActive(sl.getServer())) return;
        event.setCanceled(true);
        if (p instanceof ServerPlayer sp && !(p instanceof FakePlayer)) sp.displayClientMessage(ShrineService.msg("firmages.shrine.consecrated.locked"), true);
    }

    /** Broken in maintenance by a survival player: the stored original drops (its loot, else its item); nothing without a record. */
    static void dropOriginal(ServerLevel level, BlockPos pos, Player player, ItemStack tool) {
        Optional<ShrineSavedData.Original> o = ShrineSavedData.get(level.getServer()).original(pos);
        if (o.isEmpty()) {
            FirmagesCore.LOGGER.warn("Consecrated block at {} was broken but has no stored original; it drops nothing", pos);
            return;
        }
        Optional<BlockState> orig = toState(o.get().state());
        if (orig.isEmpty() || orig.get().isAir()) {
            FirmagesCore.LOGGER.warn("Consecrated block at {}: the stored original {} is not a known block; it drops nothing", pos, o.get().state());
            return;
        }
        List<ItemStack> drops = List.of();
        try {
            drops = Block.getDrops(orig.get(), level, pos, null, player, tool);
        } catch (RuntimeException e) {
            FirmagesCore.LOGGER.warn("Loot of the original {} at {} failed; dropping its block item", o.get().state(), pos, e);
        }
        if (drops.isEmpty() && orig.get().getBlock().asItem() != Items.AIR) drops = List.of(new ItemStack(orig.get().getBlock().asItem()));
        if (drops.isEmpty()) FirmagesCore.LOGGER.warn("The original {} at {} has no drops and no item", o.get().state(), pos);
        for (ItemStack d : drops) Block.popResource(level, pos, d);
        FirmagesCore.LOGGER.info("Consecrated block at {} broken in maintenance by {}: dropped {}", pos, player.getGameProfile().getName(), drops);
    }

    // ---------------------------------------------------------------- block state codec

    static Optional<String> encode(BlockState state) {
        Map<String, String> props = new TreeMap<>();
        for (Map.Entry<Property<?>, Comparable<?>> e : state.getValues().entrySet()) props.put(e.getKey().getName(), valueName(e.getKey(), e.getValue()));
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        try {
            return Optional.of(Consecration.encodeState(new Consecration.StateSpec(id, props)));
        } catch (IllegalArgumentException e) {
            FirmagesCore.LOGGER.warn("Cannot store the state {} ({}); keeping only the block", state, e.getMessage());
            try {
                return Optional.of(Consecration.encodeState(new Consecration.StateSpec(id, Map.of())));
            } catch (IllegalArgumentException e2) {
                return Optional.empty();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String valueName(Property<T> p, Comparable<?> v) {
        return p.getName((T) v);
    }

    /** The stored original as a block state; unknown blocks are empty, unknown properties keep their default. */
    public static Optional<BlockState> toState(String encoded) {
        Optional<Consecration.StateSpec> spec = Consecration.decodeState(encoded);
        if (spec.isEmpty()) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(spec.get().block());
        Optional<Block> block = id == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(id);
        if (block.isEmpty()) return Optional.empty();
        BlockState s = block.get().defaultBlockState();
        for (Map.Entry<String, String> e : spec.get().properties().entrySet()) {
            Property<?> p = block.get().getStateDefinition().getProperty(e.getKey());
            if (p != null) s = with(s, p, e.getValue());
        }
        return Optional.of(s);
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, String v) {
        return p.getValue(v).map(x -> s.setValue(p, x)).orElse(s);
    }

    // ---------------------------------------------------------------- client sync

    static ShrineSyncPayload payload(MinecraftServer s) {
        ShrineSavedData sd = ShrineSavedData.get(s);
        Map<Long, String> originals = new LinkedHashMap<>();
        for (ShrineSavedData.Original o : sd.originals().values()) {
            originals.put(o.pos().asLong(), Consecration.decodeState(o.state()).map(Consecration.StateSpec::block).orElse(o.state()));
        }
        String dim = sd.heart().map(GlobalPos::dimension).map(k -> k.location().toString()).orElse("");
        return new ShrineSyncPayload(dim, maintenanceActive(s), maintenanceSecondsLeft(s), originals);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, payload(player.server));
    }

    static void syncAll(MinecraftServer s) {
        if (s.getPlayerList().getPlayerCount() > 0) PacketDistributor.sendToAllPlayers(payload(s));
    }

    private static void countdown(MinecraftServer s, ShrineSavedData sd) {
        Optional<GlobalPos> gp = sd.heart();
        if (gp.isEmpty()) return;
        int r = ShrineService.shrineRadius() + 16;
        Component c = ShrineService.msg("firmages.shrine.maintenance.countdown", maintenanceSecondsLeft(s)).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (p.level().dimension() == gp.get().dimension() && p.blockPosition().closerThan(gp.get().pos(), r)) p.displayClientMessage(c, true);
        }
    }

    // ---------------------------------------------------------------- status, tests

    public static int pendingCount() {
        return PENDING.size();
    }

    public static boolean isPending(BlockPos pos) {
        return PENDING.containsKey(pos.asLong());
    }

    /** Blocks consecrated since the server started. */
    public static int convertedCount() {
        return converted;
    }

    /** {@code /firmages shrine originals}: one line per ring, the full list goes to the log. */
    public static List<Component> originalsReport(MinecraftServer s) {
        ShrineSavedData sd = ShrineSavedData.get(s);
        Optional<ServerLevel> level = sd.heart().map(g -> s.getLevel(g.dimension()));
        Map<Integer, int[]> perRing = new TreeMap<>();
        for (ShrineSavedData.Original o : sd.originals().values()) {
            int[] n = perRing.computeIfAbsent(o.ring(), k -> new int[2]);
            n[0]++;
            boolean here = level.isPresent() && level.get().isLoaded(o.pos()) && level.get().getBlockState(o.pos()).getBlock() instanceof ConsecratedBlock;
            if (!here) n[1]++;
            FirmagesCore.LOGGER.info("Shrine original ring {} at {}: {}{}", o.ring(), o.pos().toShortString(), o.state(), here ? "" : " (not consecrated now)");
        }
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(sd.originals().size() + " stored original(s), " + PENDING.size() + " block change(s) scheduled; full list in the server log")
            .withStyle(ChatFormatting.GOLD));
        perRing.forEach((ring, n) -> out.add(Component.literal("  ring " + ring + ": " + n[0] + " original(s)" + (n[1] > 0 ? ", " + n[1] + " not consecrated now (hole, unloaded or replaced)" : ""))));
        return out;
    }

    public static Component statusLine(MinecraftServer s) {
        return Component.literal("Consecration: " + ShrineSavedData.get(s).originals().size() + " originals stored, " + PENDING.size() + " scheduled; maintenance "
            + (maintenanceActive(s) ? "on, " + maintenanceSecondsLeft(s) + " s left" : "off"));
    }

    public static void clear() {
        BATCHES.clear();
        PENDING.clear();
        LAST_TOGGLE.clear();
        syncDirty = false;
        converted = 0;
    }
}
