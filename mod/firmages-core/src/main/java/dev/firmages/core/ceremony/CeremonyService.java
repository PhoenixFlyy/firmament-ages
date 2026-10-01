package dev.firmages.core.ceremony;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.net.AgeTransitionPayload;
import dev.firmages.core.shrine.Blessing;
import dev.firmages.core.shrine.ShrineData;
import dev.firmages.core.shrine.ShrineDataLoader;
import dev.firmages.core.shrine.ShrineRules;
import dev.firmages.core.shrine.ShrineSavedData;
import dev.firmages.core.shrine.ShrineTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * m7, the Age transition ceremony (SPEC §8). A grant that the shrine's prayer caused gets the FULL ceremony at
 * the shrine; every other Age grant (quest, admin) gets the SHORT one. Server side this sends one
 * {@link AgeTransitionPayload} to every player and spawns visual-only lightning before the reload freeze; the
 * client runs the timeline on its own ticks. The coalesced reload of {@code ReloadScheduler} lands about 3 s in
 * ({@code gate.reloadDelayTicks}).
 */
public final class CeremonyService {
    /** How long a shrine grant waits for its stage event (ProgressiveStages may fire it once per team member). */
    private static final int PENDING_TICKS = 200;

    private record Pending(AgeId stage, GlobalPos heart, ShrineTier tier, long tick, boolean started) {}

    private record Bolt(GlobalPos pos, long dueTick) {}

    @Nullable private static Pending pending;
    @Nullable private static AgeTransitionPayload last;
    private static int sent;
    private static AgeId lastShort;
    private static long lastShortTick = Long.MIN_VALUE;
    private static final List<Bolt> bolts = new ArrayList<>();

    private CeremonyService() {}

    /** The shrine is about to grant {@code stage}: the stage event that follows starts the FULL ceremony. */
    public static void expectShrineGrant(MinecraftServer s, AgeId stage, GlobalPos heart, ShrineTier tier) {
        pending = new Pending(stage, heart, tier, s.getTickCount(), false);
    }

    /** Called by the shrine after its grant: starts the FULL ceremony if no stage event did (Age already held). */
    public static void ensureFullStarted(MinecraftServer s, AgeId stage) {
        Pending p = pending;
        if (p != null && p.stage == stage && !p.started) startFull(s, p);
    }

    /** AgeState really gained {@code age} (from {@code AgeService}, after the change was persisted). */
    public static void onAgeGranted(MinecraftServer s, AgeId age) {
        if (age == AgeId.DAWN) return;
        Pending p = pending;
        if (p != null && p.stage == age && s.getTickCount() - p.tick <= PENDING_TICKS) {
            if (!p.started) startFull(s, p);
            return;
        }
        if (age == lastShort && s.getTickCount() - lastShortTick <= PENDING_TICKS) return;
        lastShort = age;
        lastShortTick = s.getTickCount();
        AgeTransitionPayload payload = payload(age, false, heartOf(s), tierGranting(age));
        broadcast(payload);
        FirmagesCore.LOGGER.info("Ceremony SHORT for {} (grant from outside the shrine)", age.id());
    }

    /** {@code /firmages ceremony preview}: plays the ceremony to one player only; no grant, no lightning. */
    public static void preview(ServerPlayer player, AgeId age, boolean full) {
        PacketDistributor.sendToPlayer(player, payload(age, full, heartOf(player.server), tierGranting(age)));
    }

    private static void startFull(MinecraftServer s, Pending p) {
        pending = new Pending(p.stage, p.heart, p.tier, p.tick, true);
        AgeTransitionPayload payload = payload(p.stage, true, Optional.of(p.heart), Optional.of(p.tier));
        broadcast(payload);
        ServerLevel level = s.getLevel(p.heart.dimension());
        if (level != null) {
            if (p.tier.response().lightning()) {
                long now = s.getTickCount();
                for (int i = 0; i < 4; i++) {
                    int dx = (i % 2 == 0 ? 1 : -1) * 5;
                    int dz = (i < 2 ? 1 : -1) * 5;
                    bolts.add(new Bolt(GlobalPos.of(p.heart.dimension(), p.heart.pos().offset(dx, 0, dz)), now + 15 + i * 8L));
                }
            }
            if (ServerConfig.loaded() && ServerConfig.SHRINE_CLEAR_WEATHER.get() && level.isRaining()) {
                level.setWeatherParameters(6000, 0, false, false);
            }
        }
        FirmagesCore.LOGGER.info("Ceremony FULL for {} at {} (tier {})", p.stage.id(), p.heart, p.tier.tier());
    }

    private static void broadcast(AgeTransitionPayload payload) {
        last = payload;
        sent++;
        PacketDistributor.sendToAllPlayers(payload);
    }

    private static AgeTransitionPayload payload(AgeId age, boolean full, Optional<GlobalPos> heart, Optional<ShrineTier> tier) {
        ShrineData data = ShrineDataLoader.current();
        ShrineTier.Response r = tier.map(ShrineTier::response).orElse(ShrineTier.Response.DEFAULT);
        String voice = tier.map(ShrineTier::voiceKey).orElse("firmages.shrine.voice." + age.id());
        Optional<Blessing> blessing = tier.flatMap(ShrineTier::blessing).map(id -> data.blessings().get(id));
        List<ResourceLocation> particles = r.particles().stream().map(ResourceLocation::tryParse).filter(java.util.Objects::nonNull).toList();
        ResourceLocation sting = Optional.ofNullable(ResourceLocation.tryParse(r.sting()))
            .orElse(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "shrine.sting.stone"));
        return new AgeTransitionPayload(age.id(), tier.map(ShrineTier::tier).orElse(-1), full, heart, r.beamColor(), r.skyTint(),
            sting, voice, particles, blessing.map(Blessing::nameKey).orElse(""), blessing.map(Blessing::descriptionKey).orElse(""));
    }

    private static Optional<ShrineTier> tierGranting(AgeId age) {
        return ShrineRules.tierGranting(age).flatMap(t -> ShrineDataLoader.current().tier(t));
    }

    private static Optional<GlobalPos> heartOf(MinecraftServer s) {
        return ShrineSavedData.get(s).heart();
    }

    /** Server tick: visual-only lightning of a running FULL ceremony. */
    public static void tick(MinecraftServer s) {
        if (bolts.isEmpty()) return;
        long now = s.getTickCount();
        for (Iterator<Bolt> it = bolts.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            if (b.dueTick > now) continue;
            it.remove();
            ServerLevel level = s.getLevel(b.pos.dimension());
            if (level == null || !level.isLoaded(b.pos.pos())) continue;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) continue;
            BlockPos at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, b.pos.pos());
            bolt.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    public static void onServerStopped() {
        pending = null;
        last = null;
        sent = 0;
        lastShort = null;
        lastShortTick = Long.MIN_VALUE;
        bolts.clear();
    }

    /** The last ceremony payload broadcast (status command, GameTests). */
    public static Optional<AgeTransitionPayload> last() {
        return Optional.ofNullable(last);
    }

    public static int sentCount() {
        return sent;
    }
}
