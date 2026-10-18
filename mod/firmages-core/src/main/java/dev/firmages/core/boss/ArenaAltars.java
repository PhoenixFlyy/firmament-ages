package dev.firmages.core.boss;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.origin.ArenaAltarBlock;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginRegistry;
import dev.firmages.core.origin.OriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.UUID;

/**
 * The fight altars of the grand arena in the world (round G3, SPEC §39.1): one {@link AltarCircuit} for The Origin,
 * kept in step with the {@link ArenaAltarBlock} states (the blocks are the truth across restarts; channels live in
 * memory). A channel runs while its player stays within {@link #REACH} of the altar, alive and in The Origin; it
 * shows enchanting glyphs and a hum, and its end lights the altar with a beacon sound. {@link PrimordialEntity} raises
 * its shield in phase 2 and 3 until all four burn and strikes one every {@link AltarCircuit#STRIKE_TICKS}.
 */
public final class ArenaAltars {
    /** A channeller must stay this close (blocks, from the altar's centre). */
    public static final double REACH = 3.5;

    private static final AltarCircuit CIRCUIT = new AltarCircuit();

    private ArenaAltars() {}

    public static void register(IEventBus bus) {
        bus.addListener(ArenaAltars::onServerTick);
        bus.addListener((ServerStoppedEvent e) -> CIRCUIT.resetAll());
    }

    /** The circuit as last synchronised (GameTests, status). */
    public static AltarCircuit circuit() {
        return CIRCUIT;
    }

    /** Altar index of {@code pos}, -1 if it is no fight altar of this world. */
    public static int index(BlockPos pos) {
        List<BlockPos> all = OriginArena.altars();
        for (int i = 0; i < all.size(); i++) if (all.get(i).equals(pos)) return i;
        return -1;
    }

    /** True when the world has the altars and they stand (their chunks loaded). */
    public static boolean present(ServerLevel level) {
        List<BlockPos> all = OriginArena.altars();
        if (all.isEmpty() || level.dimension() != OriginRegistry.ORIGIN) return false;
        for (BlockPos p : all) if (!level.isLoaded(p) || !level.getBlockState(p).is(OriginRegistry.ARENA_ALTAR.get())) return false;
        return true;
    }

    /** Reads the active flags from the blocks (a player cannot break them, an operator can). */
    public static void sync(ServerLevel level) {
        List<BlockPos> all = OriginArena.altars();
        for (int i = 0; i < all.size(); i++) {
            BlockState s = level.isLoaded(all.get(i)) ? level.getBlockState(all.get(i)) : null;
            boolean on = s != null && s.is(OriginRegistry.ARENA_ALTAR.get()) && s.getValue(ArenaAltarBlock.ACTIVE);
            if (on != CIRCUIT.active(i)) CIRCUIT.set(i, on);
        }
    }

    public static boolean allActive(ServerLevel level) {
        if (!present(level)) return false;
        sync(level);
        return CIRCUIT.allActive();
    }

    public static int activeCount(ServerLevel level) {
        if (!present(level)) return 0;
        sync(level);
        return CIRCUIT.activeCount();
    }

    /** A right click with an empty hand. @return true if the channel runs */
    public static boolean startChannel(ServerLevel level, BlockPos pos, ServerPlayer player) {
        int i = index(pos);
        if (i < 0 || level.dimension() != OriginRegistry.ORIGIN || player.isSpectator()) return false;
        sync(level);
        if (!CIRCUIT.start(i, player.getUUID(), level.getGameTime())) return false;
        level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.5F, 1.4F);
        return true;
    }

    /** One tick of every channel (every server tick while one runs; GameTests call it directly). */
    public static void tick(ServerLevel level) {
        if (!CIRCUIT.anyChannel()) return;
        List<BlockPos> all = OriginArena.altars();
        long now = level.getGameTime();
        for (int i = 0; i < all.size(); i++) {
            UUID who = CIRCUIT.channeler(i);
            if (who == null) continue;
            BlockPos p = all.get(i);
            ServerPlayer sp = level.getServer().getPlayerList().getPlayer(who);
            if (sp == null || sp.level() != level || !sp.isAlive() || sp.isSpectator()
                || sp.position().distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) > REACH * REACH) {
                CIRCUIT.cancel(i);
                level.playSound(null, p, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6F, 1.6F);
                continue;
            }
            if (now % 4 == 0) {
                float f = CIRCUIT.progress(i, now);
                level.sendParticles(ParticleTypes.ENCHANT, p.getX() + 0.5, p.getY() + 1.6, p.getZ() + 0.5, 8, 1.2, 0.6, 1.2, 0.6);
                level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 1.1 + 2 * f, p.getZ() + 0.5, 1, 0.05, 0.05, 0.05, 0.0);
            }
            if (now % 20 == 0) level.playSound(null, p, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.2F, 1.0F + CIRCUIT.progress(i, now) * 0.8F);
        }
        for (int i : CIRCUIT.tick(now)) {
            setBlock(level, i, true);
            BlockPos p = all.get(i);
            level.playSound(null, p, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0F, 1.0F);
            level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 40, 0.3, 1.5, 0.3, 0.08);
            FirmagesCore.LOGGER.debug("The Origin: altar {} lit ({} of 4)", i, CIRCUIT.activeCount());
        }
    }

    /** The boss strikes one lit altar with a (harmless) bolt. @return the altar struck, -1 if none burns */
    public static int strike(ServerLevel level, RandomSource random) {
        if (!present(level)) return -1;
        sync(level);
        int i = CIRCUIT.strike(random::nextInt);
        if (i < 0) return -1;
        setBlock(level, i, false);
        BlockPos p = OriginArena.altars().get(i);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.playSound(null, p, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0F, 0.7F);
        return i;
    }

    /** Every altar idle ({@code /firmages origin altars reset}, the start of phase 2 and 3). @return altars present */
    public static int resetAll(ServerLevel level) {
        CIRCUIT.resetAll();
        int n = 0;
        for (int i = 0; i < OriginArena.altars().size(); i++) if (setBlock(level, i, false)) n++;
        return n;
    }

    /** Every altar lit ({@code /firmages origin altars complete}). @return altars present */
    public static int completeAll(ServerLevel level) {
        CIRCUIT.completeAll();
        int n = 0;
        for (int i = 0; i < OriginArena.altars().size(); i++) if (setBlock(level, i, true)) n++;
        return n;
    }

    /** Lights or darkens altar {@code i} at once (GameTests). @return false if it is not loaded */
    public static boolean set(ServerLevel level, int i, boolean on) {
        CIRCUIT.set(i, on);
        return setBlock(level, i, on);
    }

    private static boolean setBlock(ServerLevel level, int i, boolean on) {
        BlockPos p = OriginArena.altars().get(i);
        if (!level.isLoaded(p)) return false;
        BlockState s = level.getBlockState(p);
        if (!s.is(OriginRegistry.ARENA_ALTAR.get())) return false;
        if (s.getValue(ArenaAltarBlock.ACTIVE) != on) level.setBlock(p, s.setValue(ArenaAltarBlock.ACTIVE, on), Block.UPDATE_ALL);
        return true;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (!CIRCUIT.anyChannel()) return;
        ServerLevel level = OriginService.level(event.getServer());
        if (level != null) tick(level);
    }
}
