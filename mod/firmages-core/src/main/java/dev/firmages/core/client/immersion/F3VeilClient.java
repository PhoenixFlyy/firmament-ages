package dev.firmages.core.client.immersion;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.immersion.F3Veil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client half of {@link F3Veil} (SPEC §18.11): while the map is closed for the player ({@link MapGateClient#open}: the
 * world's Age before {@code ui.mapFromAge}, creative mode exempt) the debug screen shows {@code ???} for everything but
 * the system data.
 * <ul>
 * <li>Vanilla F3 (BetterF3 absent or switched off in its options): NeoForge 21.1.252 builds both columns, posts
 * {@code CustomizeGuiOverlayEvent.DebugText} with them and draws what the listeners leave [verified, sources]; this
 * listener runs last (LOWEST), after other mods added their lines, and veils the lists in place.</li>
 * <li>BetterF3 11.0.3 cancels that path at the head of {@code DebugScreenOverlay}'s render lambda and draws its own
 * modules [verified, javap]; {@code mixin/betterf3/BaseModuleMixin} passes each module's formatted lines through
 * {@link #veilModule}.</li>
 * </ul>
 */
@EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
public final class F3VeilClient {
    private static final Map<String, Optional<Field>> FIELDS = new ConcurrentHashMap<>();

    private F3VeilClient() {}

    /** The debug screen shows everything (the map's flag, creative mode exempt). */
    public static boolean open() {
        return MapGateClient.open();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void debugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (open()) return;
        replace(event.getLeft(), F3Veil.veilLeft(event.getLeft()));
        replace(event.getRight(), F3Veil.veilRight(event.getRight()));
    }

    private static void replace(List<String> target, List<String> lines) {
        target.clear();
        target.addAll(lines);
    }

    /**
     * One BetterF3 module's lines, veiled unless the module is system data ({@link F3Veil#keepsModule}) or the map is
     * open: each line's label in the module's name colour, {@code ???} in its value colour.
     */
    public static List<Component> veilModule(Object module, List<Component> lines) {
        if (lines == null || lines.isEmpty() || F3Veil.keepsModule(module.getClass().getSimpleName()) || open()) return lines;
        TextColor nameColor = color(module, "nameColor");
        TextColor valueColor = color(module, "valueColor");
        List<String> plain = new ArrayList<>(lines.size());
        for (Component c : lines) plain.add(c.getString());
        List<Component> out = new ArrayList<>(plain.size());
        for (String line : F3Veil.veil(plain)) {
            if (line.isEmpty()) {
                out.add(Component.empty());
                continue;
            }
            int colon = line.lastIndexOf(": " + F3Veil.VEIL);
            MutableComponent value = Component.literal(F3Veil.VEIL).withStyle(s -> s.withColor(valueColor));
            out.add(colon > 0 ? Component.literal(line.substring(0, colon + 2)).withStyle(s -> s.withColor(nameColor)).append(value) : value);
        }
        return out;
    }

    /** A module's public colour field ({@code BaseModule.nameColor}, {@code valueColor}), null if it is not there. */
    private static TextColor color(Object module, String field) {
        Field f = FIELDS.computeIfAbsent(module.getClass().getName() + "#" + field, k -> {
            try {
                return Optional.of(module.getClass().getField(field));
            } catch (ReflectiveOperationException | RuntimeException e) {
                return Optional.empty();
            }
        }).orElse(null);
        try {
            return f != null && f.get(module) instanceof TextColor c ? c : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
