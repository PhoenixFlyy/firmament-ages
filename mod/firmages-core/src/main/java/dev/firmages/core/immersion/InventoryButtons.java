package dev.firmages.core.immersion;

import dev.firmages.core.age.AgeId;

import java.util.Set;

/**
 * The Curios and Apothic Attributes buttons of the survival inventory by Age (SPEC §18.2, Felix 2026-10-17). Before
 * {@code firmages-client.toml [ui] inventoryButtonsFromAge} (default {@code age_0}, so only in Dawn) the inventory
 * screen shows neither, the Curios key does nothing and the Curios screen does not open; operators and players in
 * creative mode have both in every Age, as with the recipe viewer ({@link ViewerGate#open}). Pure Java (unit-tested);
 * the client half is {@code client/immersion/InventoryButtonGate}, the mod lookups {@code compat/curios} and
 * {@code compat/apothic}.
 *
 * <p>Classes [verified, javap]: Curios 9.5.1 adds {@code CuriosButton} (an {@code ImageButton}) to {@code InventoryScreen}
 * in {@code GuiEventHandler.onInventoryGuiInit} ({@code ScreenEvent.Init.Post}, NORMAL); its key
 * {@code KeyRegistry.openCurios} (G) sends the same open packet from {@code ClientEventHandler.onClientTick}; the screen is
 * {@code CuriosScreen}. Apothic Attributes 2.11.0 adds, in {@code AttributesLibClient.addAttribComponent} (HIGH), the
 * panel {@code AttributesGui} (a renderable listener, not a screen), its toggle button {@code AttributesGui$1} (an
 * anonymous {@code ImageButton}) and {@code AttributesGui$HideUnchangedButton}; it has no key.
 */
public final class InventoryButtons {
    /** Felix 2026-10-17: Dawn shows neither button. */
    public static final AgeId DEFAULT_FROM = AgeId.AGE_0;
    public static final String CURIOS_BUTTON = "top.theillusivec4.curios.client.gui.CuriosButton";
    public static final String CURIOS_SCREEN = "top.theillusivec4.curios.client.gui.CuriosScreen";
    public static final String ATTRIBUTES_PANEL = "dev.shadowsoffire.apothic_attributes.client.AttributesGui";
    public static final String ATTRIBUTES_TOGGLE = ATTRIBUTES_PANEL + "$1";
    public static final String ATTRIBUTES_HIDE_UNCHANGED = ATTRIBUTES_PANEL + "$HideUnchangedButton";
    /** Everything the two mods add to the survival inventory screen. */
    public static final Set<String> INVENTORY_WIDGETS = Set.of(CURIOS_BUTTON, ATTRIBUTES_PANEL, ATTRIBUTES_TOGGLE, ATTRIBUTES_HIDE_UNCHANGED);

    private InventoryButtons() {}

    /**
     * @param worldAge the world's Age from {@code firmages:age_status} (Dawn before it arrives)
     * @param from     {@code ui.inventoryButtonsFromAge}
     * @return the buttons, the Curios key and the Curios screen are there for this player
     */
    public static boolean shown(AgeId worldAge, AgeId from, boolean operator, boolean creative) {
        return ViewerGate.open(AgeStatus.reached(worldAge, from), operator, creative);
    }

    /** A widget of the inventory screen that goes while the buttons are not shown (by its class name). */
    public static boolean removed(String widgetClass) {
        return widgetClass != null && INVENTORY_WIDGETS.contains(widgetClass);
    }

    /** A screen that does not open while the buttons are not shown (by its class name). */
    public static boolean screenBlocked(String screenClass) {
        return CURIOS_SCREEN.equals(screenClass);
    }
}
