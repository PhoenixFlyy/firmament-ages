package dev.firmages.core.client.immersion;

import com.google.gson.JsonParser;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.immersion.KeyGate;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Client half of {@link KeyGate} (SPEC §18.10): while the world's Age (from {@code firmages:age_status}, Dawn before
 * it) is before a mod's Age and the player is not in creative mode (operators in survival included),
 * <ul>
 * <li>every key mapping of that mod is gated ({@link GatedKey}, {@code mixin/client/KeyMappingMixin}: it reads as never
 * pressed), and each client tick ({@code ClientTickEvent.Pre}, HIGHEST, before the mods read their keys in
 * {@code Post}) its pending clicks are drained and it is set up ({@code setDown(false)});</li>
 * <li>a screen whose class is in one of that mod's packages does not open ({@code ScreenEvent.Opening}); a container
 * screen's menu, already set by {@code MenuScreens.create}, is closed on both sides.</li>
 * </ul>
 * The table is the pack file {@code assets/firmages/key_gates.json} (client reload listener); a status change applies
 * on the next tick. Nothing is said: a locked key does nothing (no explaining texts, §18).
 */
@EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
public final class KeyGateClient {
    static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "key_gates.json");

    private static volatile KeyGate.Table table = KeyGate.Table.EMPTY;
    private static volatile int generation;
    private static Object appliedFor;
    private static List<KeyMapping> gated = List.of();

    private KeyGateClient() {}

    public static KeyGate.Table table() {
        return table;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        AgeId age = ClientAgeStatus.get().age();
        boolean creative = p.isCreative();
        Object now = List.of(age, creative, generation);
        if (!now.equals(appliedFor)) {
            apply(mc.options.keyMappings, age, creative);
            appliedFor = now;
        }
        for (KeyMapping k : gated) {
            k.setDown(false);
            while (k.consumeClick()) {
                // drained
            }
        }
    }

    private static void apply(KeyMapping[] all, AgeId age, boolean creative) {
        KeyGate.Table t = table;
        List<KeyMapping> locked = new ArrayList<>();
        Set<String> mods = new TreeSet<>();
        for (KeyMapping k : all) {
            boolean lock = KeyGate.keyLocked(t, k.getName(), age, creative);
            if (k instanceof GatedKey g) g.firmages$setGated(lock);
            if (lock) {
                locked.add(k);
                t.modOfKey(k.getName()).ifPresent(mods::add);
            }
        }
        gated = List.copyOf(locked);
        FirmagesCore.LOGGER.info("Key gate ({}{}): {} keys of {} mods locked {}", age, creative ? ", creative" : "", locked.size(),
            mods.size(), mods);
    }

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        Screen screen = event.getNewScreen();
        LocalPlayer p = Minecraft.getInstance().player;
        if (screen == null || p == null) return; // no world, no Age: title and loading screens are never gated
        String name = screen.getClass().getName();
        if (!KeyGate.screenLocked(table, name, ClientAgeStatus.get().age(), p.isCreative())) return;
        event.setCanceled(true);
        if (screen instanceof MenuAccess<?> access && access.getMenu() == p.containerMenu) InventoryButtonGate.closeOpenMenu();
        FirmagesCore.LOGGER.debug("Key gate: screen {} of {} not opened", name, table.modOfClass(name).orElse("?"));
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        for (KeyMapping k : gated) if (k instanceof GatedKey g) g.firmages$setGated(false);
        gated = List.of();
        appliedFor = null;
    }

    static KeyGate.Table read(ResourceManager rm) {
        Optional<Resource> res = rm.getResource(FILE);
        if (res.isEmpty()) {
            FirmagesCore.LOGGER.warn("{} is missing: mod keys and screens are not gated by Age", FILE);
            return KeyGate.Table.EMPTY;
        }
        try (Reader r = res.get().openAsReader()) {
            KeyGate.Table t = KeyGate.parse(JsonParser.parseReader(r));
            FirmagesCore.LOGGER.info("Key gate table: {} mods, {} keys, {} packages", t.mods().size(), t.keys().size(), t.packages().size());
            return t;
        } catch (Exception e) {
            FirmagesCore.LOGGER.warn("Cannot read {}: {}", FILE, e.toString());
            return KeyGate.Table.EMPTY;
        }
    }

    @EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void reloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) rm -> {
                table = read(rm);
                generation++;
            });
        }
    }
}
