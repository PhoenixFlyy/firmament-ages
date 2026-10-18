package dev.firmages.core.command;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.boss.BossRegistry;
import dev.firmages.core.boss.PrimordialAbility;
import dev.firmages.core.boss.PrimordialEntity;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /firmages debug attack <name> <seconds>} (needs {@code debug.allowSimulate}, SPEC §39 "to check on a real fight"):
 * a debug player fights The Primordial by script. Every 10 ticks (the boss's hurt cooldown per attacker) it stands
 * 1.8 blocks from the boss's axis, faces it and attacks with a full swing through {@code Player.attack}, so the real
 * damage pipeline runs (armour, {@code boss.hitCap}, the pool scaling, the closed veil, the counters: a melee hit on the
 * grounded keel or from below during the pull opens the veil). In phase 3 it picks up the consecrated shards that fell
 * within 24 blocks into its off hand, as a player gathers them. It never dodges and never shoots, and it is healed every
 * swing (its survival is not what is measured: the ring's shots, the close strike and the storm of round G4 land on it
 * like on a standing player, the incoming side of the model is {@code FightModel.secondsToDie}, not this bot). It floats
 * at the boss's own height (HOVER 7 since round G4), so its swings reach the body as a flying player's would. A debug player is not ticked, so a held item's attribute modifiers
 * never apply: give it the reference build's numbers with {@code /attribute <name> minecraft:generic.attack_damage base
 * set 49.5} (a Chaotic sword with two Chaotic damage modules, SPEC §39). A recorder samples the boss every tick while any bot fights: ticks,
 * veil-open ticks and counted hits per phase; at the boss's death or the bots' end it logs one summary and writes
 * {@code logs/firmages-boss-fight.json} for the model check (dev/harmonie-messung.md).
 */
final class BossFightBot {
    private static final int SWING_TICKS = 10;
    /** name -> {ticks left, index} */
    private static final Map<String, int[]> BOTS = new LinkedHashMap<>();
    @Nullable
    private static Recorder rec;
    @Nullable
    private static Field strengthTicker;

    private BossFightBot() {}

    static void start(ServerPlayer p, int seconds) {
        BOTS.put(p.getGameProfile().getName(), new int[] {seconds * 20, BOTS.size()});
        if (rec == null) rec = new Recorder();
        FirmagesCore.LOGGER.info("[debug-player {}] attacks The Primordial for up to {} s ({} bot(s))", p.getGameProfile().getName(), seconds, BOTS.size());
    }

    static void tick(MinecraftServer server) {
        if (BOTS.isEmpty() && rec == null) return;
        PrimordialEntity boss = findBoss(server);
        if (rec != null) rec.sample(boss, BOTS.size());
        var it = BOTS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayerByName(e.getKey());
            int left = --e.getValue()[0];
            if (p == null || left <= 0 || (boss == null && rec != null && rec.ticks > 0)) {
                FirmagesCore.LOGGER.info("[debug-player {}] stops attacking", e.getKey());
                it.remove();
                continue;
            }
            if (boss == null || p.level() != boss.level() || (left + e.getValue()[1] * 3) % SWING_TICKS != 0) continue;
            swing(p, boss, e.getValue()[1]);
        }
        if (rec != null && (BOTS.isEmpty() || (boss == null && rec.ticks > 0))) {
            rec.finish(server, boss);
            rec = null;
            BOTS.clear();
        }
    }

    private static void swing(ServerPlayer p, PrimordialEntity boss, int index) {
        if (boss.shielded() && boss.level() instanceof ServerLevel sl && dev.firmages.core.boss.ArenaAltars.present(sl)) {
            tendAltar(p, sl, index);
            return;
        }
        double a = index * Math.PI / 2 + boss.tickCount * 0.01;
        p.teleportTo((ServerLevel) boss.level(), boss.getX() + Math.cos(a) * 1.8, boss.getY(), boss.getZ() + Math.sin(a) * 1.8, p.getYRot(), p.getXRot());
        p.lookAt(EntityAnchorArgument.Anchor.EYES, boss.getEyePosition());
        p.setHealth(p.getMaxHealth());
        p.clearFire();
        if (boss.phase() >= 3 && p.getOffhandItem().isEmpty()) gatherShards(p);
        fullSwing(p);
        float before = boss.getHealth();
        boolean veil = boss.veilOpen();
        int phase = boss.phase();
        p.attack(boss);
        p.swing(InteractionHand.MAIN_HAND);
        float lost = before - boss.getHealth();
        if (rec != null) rec.hit(phase, veil, boss.veilOpen(), lost);
    }

    /**
     * Round G3 (SPEC §39.1): while the altar shield stands the bot walks to a dark altar (its own first, by index) and
     * channels it with an empty hand, standing on the socket.
     */
    private static void tendAltar(ServerPlayer p, ServerLevel sl, int index) {
        dev.firmages.core.boss.ArenaAltars.sync(sl);
        var circuit = dev.firmages.core.boss.ArenaAltars.circuit();
        java.util.List<net.minecraft.core.BlockPos> altars = dev.firmages.core.origin.OriginArena.altars();
        for (int k = 0; k < altars.size(); k++) {
            int i = (index + k) % altars.size();
            if (circuit.active(i) || (circuit.channeler(i) != null && !circuit.channeler(i).equals(p.getUUID()))) continue;
            net.minecraft.core.BlockPos a = altars.get(i);
            p.teleportTo(sl, a.getX() + 1.5, a.getY(), a.getZ() + 0.5, p.getYRot(), p.getXRot());
            p.setHealth(p.getMaxHealth());
            dev.firmages.core.boss.ArenaAltars.startChannel(sl, a, p);
            return;
        }
    }

    /** The shards on the floor near the bot go to its off hand (walking over them). */
    private static void gatherShards(ServerPlayer p) {
        for (ItemEntity item : p.level().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(24),
            i -> i.getItem().is(BossRegistry.CONSECRATED_SHARD.get()))) {
            ItemStack s = item.getItem();
            ItemStack off = p.getOffhandItem();
            if (off.isEmpty()) {
                p.setItemInHand(InteractionHand.OFF_HAND, s.copy());
                item.discard();
            } else if (ItemStack.isSameItemSameComponents(off, s)) {
                off.grow(s.getCount());
                item.discard();
            }
        }
    }

    /** A full attack charge, as after a pause (the field is LivingEntity's; Mojang names at run time). */
    private static void fullSwing(ServerPlayer p) {
        try {
            if (strengthTicker == null) {
                strengthTicker = LivingEntity.class.getDeclaredField("attackStrengthTicker");
                strengthTicker.setAccessible(true);
            }
            strengthTicker.setInt(p, 1000);
        } catch (ReflectiveOperationException e) {
            FirmagesCore.LOGGER.warn("debug attack: cannot charge the swing: {}", e.toString());
        }
    }

    @Nullable
    private static PrimordialEntity findBoss(MinecraftServer server) {
        for (ServerLevel l : server.getAllLevels()) {
            for (var e : l.getAllEntities()) {
                if (e instanceof PrimordialEntity b && b.isAlive()) return b;
            }
        }
        return null;
    }

    /** Samples the boss every tick of the fight. */
    private static final class Recorder {
        int ticks;
        int party;
        final int[] phaseTicks = new int[3];
        final int[] veilTicks = new int[3];
        final int[] swings = new int[3];
        final int[] counted = new int[3];
        final int[] countedOpen = new int[3];
        final int[] opened = new int[3];
        final double[] lost = new double[3];
        int lockTicks;
        int shatterTicks;
        float startHealth = -1;

        void sample(@Nullable PrimordialEntity boss, int bots) {
            if (boss == null) return;
            party = Math.max(party, bots);
            if (startHealth < 0) startHealth = boss.getHealth();
            ticks++;
            int i = Math.min(3, Math.max(1, boss.phase())) - 1;
            phaseTicks[i]++;
            if (boss.veilOpen()) veilTicks[i]++;
            if (boss.phaseLock() > 0) lockTicks++;
            if (boss.ability() == PrimordialAbility.SHATTER) shatterTicks++;
        }

        void hit(int phase, boolean veilBefore, boolean veilAfter, float healthLost) {
            int i = Math.min(3, Math.max(1, phase)) - 1;
            swings[i]++;
            if (healthLost > 0) {
                counted[i]++;
                lost[i] += healthLost;
                if (veilBefore) countedOpen[i]++;
            }
            if (!veilBefore && veilAfter) opened[i]++;
        }

        void finish(MinecraftServer server, @Nullable PrimordialEntity boss) {
            boolean won = boss == null || !boss.isAlive();
            String line = String.format(Locale.ROOT,
                "Boss fight record: %d bot(s), %s after %d ticks (%.2f min); phase ticks %d/%d/%d, veil open %.0f%%/%.0f%%/%.0f%%, "
                    + "swings %d/%d/%d, counted %d/%d/%d (with the veil open %d/%d/%d), veil opened by a hit %d/%d/%d, phase locks %d ticks, shatter %d ticks",
                party, won ? "boss dead" : "boss alive (" + boss.getHealth() + ")", ticks, ticks / 1200.0, phaseTicks[0], phaseTicks[1], phaseTicks[2],
                pct(veilTicks[0], phaseTicks[0]), pct(veilTicks[1], phaseTicks[1]), pct(veilTicks[2], phaseTicks[2]), swings[0], swings[1], swings[2],
                counted[0], counted[1], counted[2], countedOpen[0], countedOpen[1], countedOpen[2], opened[0], opened[1], opened[2], lockTicks, shatterTicks);
            FirmagesCore.LOGGER.info(line);
            JsonObject o = new JsonObject();
            o.addProperty("party", party);
            o.addProperty("won", won);
            o.addProperty("ticks", ticks);
            o.addProperty("start_health", startHealth);
            o.addProperty("lock_ticks", lockTicks);
            o.addProperty("shatter_ticks", shatterTicks);
            JsonArray phases = new JsonArray();
            for (int i = 0; i < 3; i++) {
                JsonObject ph = new JsonObject();
                ph.addProperty("phase", i + 1);
                ph.addProperty("ticks", phaseTicks[i]);
                ph.addProperty("veil_open_ticks", veilTicks[i]);
                ph.addProperty("swings", swings[i]);
                ph.addProperty("counted", counted[i]);
                ph.addProperty("counted_veil_open", countedOpen[i]);
                ph.addProperty("veil_opened_by_hit", opened[i]);
                ph.addProperty("health_lost", lost[i]);
                phases.add(ph);
            }
            o.add("phases", phases);
            o.addProperty("summary", line);
            Path file = server.getServerDirectory().resolve("logs").resolve("firmages-boss-fight-" + party + ".json");
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8);
            } catch (IOException e) {
                FirmagesCore.LOGGER.warn("Boss fight record: cannot write {}", file, e);
            }
        }

        private static double pct(int a, int b) {
            return b == 0 ? 0 : 100.0 * a / b;
        }
    }
}
