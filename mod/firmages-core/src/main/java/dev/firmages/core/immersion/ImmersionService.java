package dev.firmages.core.immersion;

import com.mojang.serialization.Codec;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.net.AgeStatusPayload;
import dev.firmages.core.net.WelcomePayload;
import dev.firmages.core.shrine.ShrineDataLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Server half of the immersion features (SPEC §18): the {@code firmages:age_status} sync (EMI, Jade and the TFC tabs
 * by Age) and the first-join welcome.
 * The welcome flag is a player data attachment ({@code firmages:welcomed}), saved with the player and kept through
 * death, so every friend who joins the shared world later gets the welcome once too.
 */
public final class ImmersionService {
    private ImmersionService() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, FirmagesCore.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> WELCOMED = ATTACHMENTS.register("welcomed",
        () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    public static void register(IEventBus modBus, IEventBus gameBus) {
        ATTACHMENTS.register(modBus);
        gameBus.addListener(ImmersionService::onPlayerLoggedIn);
    }

    /** The status the server would send now. */
    public static AgeStatus status(MinecraftServer s) {
        return AgeStatus.of(AgeService.state(s).snapshot(), tier -> ShrineDataLoader.current().offeringFor(tier),
            ServerConfig.emiFromAge(), ServerConfig.jadeFromAge(), ServerConfig::tabFromAge, ServerConfig.caveDwellerUntil(),
            ServerConfig.toolRuleUntil());
    }

    /** After every change of the global Age state (called by AgeService). */
    public static void broadcast(MinecraftServer s) {
        if (s.getPlayerList() == null || s.getPlayerList().getPlayerCount() == 0) return; // boot reconcile: nobody to tell
        PacketDistributor.sendToAllPlayers(AgeStatusPayload.of(status(s)));
    }

    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PacketDistributor.sendToPlayer(player, AgeStatusPayload.of(status(player.server)));
        welcomeIfNew(player);
    }

    /** Sends the welcome on the player's first join. @return true if it was sent now. */
    public static boolean welcomeIfNew(ServerPlayer player) {
        if (!ServerConfig.welcome() || player.getData(WELCOMED.get())) return false;
        player.setData(WELCOMED.get(), true);
        PacketDistributor.sendToPlayer(player, WelcomePayload.INSTANCE);
        FirmagesCore.LOGGER.info("Welcome sent to {} (first join)", player.getGameProfile().getName());
        return true;
    }

    /** {@code /firmages welcome reset}: the player gets the welcome again on the next join. */
    public static void resetWelcome(ServerPlayer player) {
        player.setData(WELCOMED.get(), false);
    }

    /** {@code /firmages welcome show}: the welcome now, without touching the flag. */
    public static void showWelcome(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, WelcomePayload.INSTANCE);
    }
}
