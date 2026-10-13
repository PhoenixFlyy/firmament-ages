package dev.firmages.core.hardship;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.immersion.AgeStatus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Server half of {@link CaveDwellerGate} (SPEC §19). No compile dependency on Cave Dweller ReEvolved: the entity is
 * matched by its registry id. Once the global Age state reaches {@code hardship.caveDwellerUntilAge}:
 * <ul>
 * <li>{@code EntityJoinLevelEvent} is cancelled for the dweller: its own timed spawns, {@code /summon} and dwellers
 * saved in a chunk that loads later (a refused entity is not tracked, so the chunk saves without it);</li>
 * <li>after the Age change itself, dwellers in loaded chunks are discarded.</li>
 * </ul>
 * The Age comes from {@code AgeService}'s state (no stage query per event).
 */
public final class CaveDwellerService {
    private static final ResourceLocation ENTITY = ResourceLocation.parse(CaveDwellerGate.ENTITY_ID);

    private CaveDwellerService() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(CaveDwellerService::onJoin);
    }

    /** True once the world's Age is {@code hardship.caveDwellerUntilAge} or later. */
    public static boolean over(MinecraftServer s) {
        return !CaveDwellerGate.hunts(AgeStatus.current(AgeService.state(s).snapshot()), ServerConfig.caveDwellerUntil());
    }

    public static boolean isDweller(Entity e) {
        return ENTITY.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
    }

    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !isDweller(event.getEntity())) return;
        MinecraftServer s = event.getLevel().getServer();
        if (s != null && over(s)) {
            event.setCanceled(true);
            FirmagesCore.LOGGER.debug("Cave Dweller refused at {} (hardship.caveDwellerUntilAge)", event.getEntity().blockPosition());
        }
    }

    /** After every change of the global Age state (AgeService): discards the dwellers in loaded chunks once the Age is reached. @return how many. */
    public static int despawnIfOver(MinecraftServer s) {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(ENTITY) || !over(s)) return 0;
        int n = 0;
        for (ServerLevel level : s.getAllLevels()) {
            List<Entity> found = new ArrayList<>();
            for (Entity e : level.getAllEntities()) if (isDweller(e)) found.add(e);
            for (Entity e : found) e.discard();
            n += found.size();
        }
        if (n > 0) FirmagesCore.LOGGER.info("Removed {} Cave Dweller(s): the world reached hardship.caveDwellerUntilAge", n);
        return n;
    }
}
