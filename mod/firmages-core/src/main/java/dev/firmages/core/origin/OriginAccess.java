package dev.firmages.core.origin;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

/**
 * The Origin's own door lock (SPEC §16.1, Doc 08 §10.3): a player without {@code age_9} never travels into
 * {@code firmages:origin}, whatever moves him. Every dimension change of vanilla and of the pack's mods goes through
 * {@code Entity#changeDimension} (NeoForge posts {@link EntityTravelToDimensionEvent} there): portals, commands of
 * any permission level ({@code /tp}, {@code /execute in}, FTB Essentials {@code /back}, {@code /tpa}), the Mekanism
 * teleporter, Ars Nouveau warp scrolls and portals, and Stargate Journey's wormhole ({@code Wormhole#transportPlayer}
 * calls {@code ServerPlayer#teleportTo(ServerLevel, ...)} [verified, javap 0.6.49]). This works next to the
 * ProgressiveStages dimension lock of {@code age_9.toml}, without its config. Creative and spectator players pass
 * (as with ProgressiveStages' creative bypass). The Age comes from the server-wide AgeState (one team).
 */
public final class OriginAccess {
    private OriginAccess() {}

    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, OriginAccess::onTravel);
    }

    /** True when {@code p} may enter The Origin now: {@code age_9} unlocked, or creative or spectator. */
    public static boolean mayEnter(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return true;
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
