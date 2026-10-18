package dev.firmages.core.immersion;

import dev.firmages.core.age.AgeId;

import java.util.Set;

/**
 * JourneyMap by Age (SPEC §18.2, Felix M1A 2026-10-17): before {@code firmages-server.toml [ui] mapFromAge} (default
 * {@code age_2}, the Iron Age, as the FTB Chunks map before it; the server decides and sends {@code map} in
 * {@code firmages:age_status}) the minimap is not drawn, the full map, the waypoint
 * screens and the minimap options do not open and JourneyMap's keys do nothing; players in creative mode have the map
 * in every Age, operators in survival do not ({@link ViewerGate#open}). JourneyMap's splash ("About") screen, which the
 * full map opens once per JourneyMap version, never opens (no feature announcements, §18): the gate marks it as viewed
 * before the full map initialises ({@link #splashPending}); cancelling the splash itself closed the full map, because
 * {@code UIManager.open(Screen)} closes the current screen (the full map) before it opens the splash (G3, M did nothing).
 * Pure Java (unit-tested); the
 * client half is {@code client/immersion/MapGateClient}, the mod lookups {@code compat/journeymap/JourneyMapBridge}.
 *
 * <p>JourneyMap 6.0.9 [verified, javap]: it registers no GUI layer; {@code NeoForgeHudOverlayEvents} draws the minimap
 * from {@code RenderGuiEvent.Pre} (LOWEST) or from {@code RenderGuiLayerEvent.Pre} of the vanilla effects, scoreboard or
 * debug layer (setting {@code hudRenderLayer}), so cancelling either event would hide vanilla HUD parts too. The draw
 * itself, {@code MiniMap.drawMap}, returns at once while {@code InternalStateHandler.isMinimapEnabled()} is false, which is
 * the server's permission and the API flag {@code minimapEnabledApi} together; the API flag is set by the public API
 * {@code IClientAPI.toggleMinimap(boolean)} ({@code journeymap.api.client.impl.ClientAPI}), is true from the start and is
 * not reset on a world change. Keys: every JourneyMap {@code KeyMapping} is named {@code key.journeymap.*} and is read by
 * {@code KeybindingHandler.consume()} with {@code consumeClick()} in {@code ClientTickEvent.Post}.
 */
public final class MapGate {
    /** Felix M1A 2026-10-17: the map arrives with the Iron Age, as the FTB Chunks map did (stage ftbchunks_mapping). */
    public static final AgeId DEFAULT_FROM = AgeId.AGE_2;
    public static final String FULLSCREEN = "journeymap.client.ui.fullscreen.Fullscreen";
    public static final String WAYPOINT_MANAGER = "journeymap.client.ui.waypointmanager.WaypointManager";
    public static final String WAYPOINT_EDITOR = "journeymap.client.ui.waypointmanager.waypoint.WaypointEditor";
    /** The minimap options draw a live minimap preview. */
    public static final String MINIMAP_OPTIONS = "journeymap.client.ui.option.MinimapOptions";
    /** {@code Fullscreen.init} opens it while {@code CoreProperties.splashViewed} differs from the running version. */
    public static final String SPLASH = "journeymap.client.ui.dialog.about.AboutScreen";
    /** Screens that show the map or its waypoints. */
    public static final Set<String> MAP_SCREENS = Set.of(FULLSCREEN, WAYPOINT_MANAGER, WAYPOINT_EDITOR, MINIMAP_OPTIONS);
    public static final String KEY_PREFIX = "key.journeymap.";

    private MapGate() {}

    /**
     * @param ageAllows {@code map} of {@code firmages:age_status}: the world's Age is {@code ui.mapFromAge} or later
     *                  (false before the first status)
     * @param creative  the player is in creative mode
     * @return minimap, full map, waypoints and keys are there for this player
     */
    public static boolean open(boolean ageAllows, boolean creative) {
        return ViewerGate.open(ageAllows, creative);
    }

    /** A screen that does not open: the splash always, the map screens while the map is closed (by class name). */
    public static boolean screenBlocked(String screenClass, boolean mapOpen) {
        if (screenClass == null) return false;
        return SPLASH.equals(screenClass) || !mapOpen && MAP_SCREENS.contains(screenClass);
    }

    /**
     * The full map would open the splash: JourneyMap's {@code CoreProperties.splashViewed} is empty or not the running
     * version ({@code Fullscreen.init} [verified, javap 6.0.9]).
     */
    public static boolean splashPending(String viewed, String version) {
        return version != null && !version.equals(viewed);
    }

    /** A JourneyMap key mapping (by its name), whose presses are dropped while the map is closed. */
    public static boolean isMapKey(String keyName) {
        return keyName != null && keyName.startsWith(KEY_PREFIX);
    }

    /**
     * The minimap's API flag for this tick.
     *
     * @param mapOpen      the map is open for the player
     * @param apiEnabled   JourneyMap's current API flag ({@code IClientAPI.minimapEnabled()})
     * @param closedByGate this gate switched the flag off and has not switched it back yet
     * @return the value to set through {@code toggleMinimap}, or {@code null} to leave it (another mod's switch is
     *         never undone)
     */
    public static Boolean minimapTarget(boolean mapOpen, boolean apiEnabled, boolean closedByGate) {
        if (!mapOpen) return apiEnabled ? Boolean.FALSE : null;
        return closedByGate && !apiEnabled ? Boolean.TRUE : null;
    }
}
