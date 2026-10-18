package dev.firmages.core.client.immersion;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.compat.journeymap.JourneyMapBridge;
import dev.firmages.core.immersion.MapGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Client half of {@link MapGate} (SPEC §18.2): while the world's Age is before {@code ui.mapFromAge} and the player is
 * not in creative mode (operators in survival included),
 * <ul>
 * <li>JourneyMap's minimap API flag is off ({@code IClientAPI.toggleMinimap(false)}), so {@code MiniMap.drawMap} draws
 * nothing; when the Age opens the map the flag goes back on once, and only if this gate switched it off;</li>
 * <li>the presses of every {@code key.journeymap.*} key are dropped before JourneyMap reads them
 * ({@code ClientTickEvent.Pre}, HIGHEST; JourneyMap reads in {@code Post});</li>
 * <li>the full map, the waypoint manager and editor and the minimap options do not open ({@code ScreenEvent.Opening}).</li>
 * </ul>
 * JourneyMap's splash screen never opens: before the full map initialises the splash is marked as viewed
 * ({@link JourneyMapBridge#markSplashViewed}), so the full map does not close itself to show it. Without JourneyMap nothing happens.
 */
@EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
public final class MapGateClient {
    private static boolean closedByGate;

    private MapGateClient() {}

    /** The map is there for the local player (before the first status the world counts as Dawn). */
    public static boolean open() {
        LocalPlayer p = Minecraft.getInstance().player;
        return MapGate.open(ClientAgeStatus.get().map(), p != null && p.isCreative());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(ClientTickEvent.Pre event) {
        if (Minecraft.getInstance().player == null || !JourneyMapBridge.loaded()) return;
        boolean open = open();
        if (!open) JourneyMapBridge.drainKeys();
        Boolean now = JourneyMapBridge.minimapEnabled();
        if (now == null) return;
        Boolean set = MapGate.minimapTarget(open, now, closedByGate);
        if (set != null && JourneyMapBridge.setMinimap(set)) closedByGate = !set;
        else if (open) closedByGate = false;
    }

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        if (event.getNewScreen() == null || !JourneyMapBridge.loaded()) return;
        String name = event.getNewScreen().getClass().getName();
        if (!name.startsWith("journeymap.")) return;
        if (MapGate.FULLSCREEN.equals(name)) JourneyMapBridge.markSplashViewed();
        if (MapGate.screenBlocked(name, open())) event.setCanceled(true);
    }
}
