package dev.firmages.core.ceremony;

import com.mojang.serialization.Codec;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.immersion.AgeStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Echo of the ceremony (SPEC §8.2): a friend who was offline when the team opened a new Age sees that Age's SHORT
 * ceremony once, {@link #DELAY_TICKS} after joining, plus one chat line naming every Age that dawned while away.
 * The player attachment {@code firmages:seen_age} (saved with the player, kept through death) holds the newest Age
 * whose ceremony the player saw; every ceremony broadcast sets it for everyone online. The first join only records
 * the current Age (the welcome covers it). The finale and the Gathering are never replayed: they are shared moments.
 */
public final class CeremonyEcho {
    private CeremonyEcho() {}

    /** Ticks after the login before the echo plays (after the terrain screen). */
    public static final int DELAY_TICKS = 100;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, FirmagesCore.MOD_ID);
    /** The newest Age whose ceremony the player saw; "" until the first join recorded it. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> SEEN_AGE = ATTACHMENTS.register("seen_age",
        () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).copyOnDeath().build());

    private record Pending(UUID player, long due) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    public static void register(IEventBus modBus, IEventBus gameBus) {
        ATTACHMENTS.register(modBus);
        // LOW: after AgeService's login reconcile and the welcome of ImmersionService
        gameBus.addListener(EventPriority.LOW, CeremonyEcho::onLogin);
    }

    private static AgeId current(MinecraftServer s) {
        return AgeStatus.current(AgeService.state(s).snapshot());
    }

    /** A ceremony of {@code age} went out to everyone online: they saw it. */
    static void markSeen(MinecraftServer s, AgeId age) {
        if (s.getPlayerList() == null) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            Optional<AgeId> seen = AgeId.byId(p.getData(SEEN_AGE.get()));
            if (seen.isEmpty() || seen.get().ordinal() < age.ordinal()) p.setData(SEEN_AGE.get(), age.id());
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) login(p);
    }

    /** Login: the first join records the current Age; a later one schedules the echo. @return true if scheduled */
    public static boolean login(ServerPlayer p) {
        Optional<AgeId> seen = AgeId.byId(p.getData(SEEN_AGE.get()));
        AgeId now = current(p.server);
        if (seen.isEmpty()) {
            p.setData(SEEN_AGE.get(), now.id());
            return false;
        }
        if (now.ordinal() < seen.get().ordinal()) p.setData(SEEN_AGE.get(), now.id()); // an Age was revoked (admin repair)
        if (now.ordinal() <= seen.get().ordinal()) return false;
        PENDING.removeIf(x -> x.player.equals(p.getUUID()));
        PENDING.add(new Pending(p.getUUID(), p.server.getTickCount() + DELAY_TICKS));
        return true;
    }

    /** Server tick (from {@link CeremonyService#tick}): plays the echoes that are due. */
    static void tick(MinecraftServer s) {
        if (PENDING.isEmpty()) return;
        long now = s.getTickCount();
        List<Pending> due = new ArrayList<>();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending x = it.next();
            if (x.due > now) continue;
            it.remove();
            due.add(x);
        }
        for (Pending x : due) {
            ServerPlayer p = s.getPlayerList().getPlayer(x.player);
            if (p != null) play(p); // logged out again: the next login schedules it anew
        }
    }

    /** Plays the echo now if the player still lacks the current Age's ceremony. @return true if it played */
    public static boolean play(ServerPlayer p) {
        AgeId now = current(p.server);
        AgeId seen = AgeId.byId(p.getData(SEEN_AGE.get())).orElse(AgeId.DAWN);
        if (now.ordinal() <= seen.ordinal()) return false; // a ceremony reached the player in the meantime
        PacketDistributor.sendToPlayer(p, CeremonyService.echoPayload(p, now));
        p.sendSystemMessage(Component.translatable("firmages.ceremony.missed", missedNames(seen, now)).withStyle(ChatFormatting.GOLD));
        p.setData(SEEN_AGE.get(), now.id());
        FirmagesCore.LOGGER.info("Ceremony echo of {} for {} (last seen {})", now.id(), p.getGameProfile().getName(), seen.id());
        return true;
    }

    /** The names of the Ages after {@code seen} up to {@code now}, joined by ", ". */
    static Component missedNames(AgeId seen, AgeId now) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (AgeId a : AgeId.all()) {
            if (a.ordinal() <= seen.ordinal() || a.ordinal() > now.ordinal()) continue;
            if (!first) out.append(", ");
            out.append(Component.translatable("firmages.age." + a.id() + ".name"));
            first = false;
        }
        return out;
    }

    /** Echoes waiting for their tick (GameTests). */
    public static int pendingCount() {
        return PENDING.size();
    }

    static void clear() {
        PENDING.clear();
    }
}
