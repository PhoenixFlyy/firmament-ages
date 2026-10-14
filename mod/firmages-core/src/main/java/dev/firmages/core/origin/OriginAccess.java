package dev.firmages.core.origin;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Origin's own door lock (SPEC §16.1, Doc 08 §10.3): a player without {@code age_9} never travels into
 * {@code firmages:origin}, whatever moves him. Every dimension change of vanilla and of the pack's mods goes through
 * {@code Entity#changeDimension} (NeoForge posts {@link EntityTravelToDimensionEvent} there): portals, commands of
 * any permission level ({@code /tp}, {@code /execute in}, FTB Essentials {@code /back}, {@code /tpa}), the Mekanism
 * teleporter, Ars Nouveau warp scrolls and portals, and Stargate Journey's wormhole ({@code Wormhole#transportPlayer}
 * calls {@code ServerPlayer#teleportTo(ServerLevel, ...)} [verified, javap 0.6.49]). This works next to the
 * ProgressiveStages dimension lock of {@code age_9.toml}, without its config. Creative and spectator players pass
 * (as with ProgressiveStages' creative bypass). The Age comes from the server-wide AgeState (one team).
 * <p>
 * Operators' forced visit ({@code /firmages origin tp <players> force}): the player passes this lock and
 * ProgressiveStages' ({@code mixin/ps/DimensionLockBypassMixin}) until he leaves The Origin, respawns elsewhere or the
 * server stops. Nothing else opens the door.
 */
public final class OriginAccess {
    private OriginAccess() {}

    private static final Set<UUID> FORCED = ConcurrentHashMap.newKeySet();

    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, OriginAccess::onTravel);
        bus.addListener(OriginAccess::onChangedDimension);
        bus.addListener(OriginAccess::onRespawn);
        bus.addListener(OriginAccess::onServerStopped);
    }

    /** An operator lets {@code p} into The Origin regardless of the Age (the next travel there and the stay). */
    public static void allowForcedVisit(ServerPlayer p) {
        FORCED.add(p.getUUID());
    }

    public static void endForcedVisit(ServerPlayer p) {
        FORCED.remove(p.getUUID());
    }

    /** True while {@code p} has an operator's forced visit and {@code dimension} is The Origin (the ProgressiveStages mixin asks). */
    public static boolean forcedVisit(ServerPlayer p, ResourceLocation dimension) {
        return OriginRegistry.ORIGIN_ID.equals(dimension) && FORCED.contains(p.getUUID());
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getFrom() == OriginRegistry.ORIGIN && event.getTo() != OriginRegistry.ORIGIN) FORCED.remove(event.getEntity().getUUID());
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity().level().dimension() != OriginRegistry.ORIGIN) FORCED.remove(event.getEntity().getUUID());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        FORCED.clear();
    }

    /** True when {@code p} may enter The Origin now: {@code age_9} unlocked, creative or spectator, or a forced visit. */
    public static boolean mayEnter(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator() || FORCED.contains(p.getUUID())) return true;
        return AgeService.state(p.server).snapshot().isUnlocked(AgeId.AGE_9);
    }

    /** {@link EntityTravelToDimensionEvent} (public for the GameTests). */
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getDimension() != OriginRegistry.ORIGIN || !(event.getEntity() instanceof ServerPlayer p) || mayEnter(p)) return;
        event.setCanceled(true);
        p.displayClientMessage(Component.translatable("firmages.origin.locked").withStyle(ChatFormatting.DARK_PURPLE), true);
        FirmagesCore.LOGGER.info("The Origin: {} was turned back (no age_9) from {}", p.getGameProfile().getName(), p.level().dimension().location());
    }
}
