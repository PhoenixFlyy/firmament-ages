package dev.firmages.core.immersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The F3 veil (SPEC §18.11, Felix 2026-10-17, round G2: "F3 shows only ??? for everything that is not about the
 * computer's resources until the minimap is there"). Before {@code ui.mapFromAge} (the map's Age, the same flag as
 * {@link MapGate}; creative mode exempt, operators in survival not) every debug line except the system data keeps its
 * label and shows {@code ???} as its value; a line without a label becomes {@code ???}, and a run of those collapses
 * into one (the number of block tags says nothing either). System data: the Minecraft version, FPS, the server tick
 * and packets, the graphics settings, Java, memory, CPU, GPU and display, the help lines. Pure Java (unit-tested);
 * the client half is {@code client/immersion/F3VeilClient} and, for BetterF3, {@code mixin/betterf3/BaseModuleMixin}.
 */
public final class F3Veil {
    public static final String VEIL = "???";
    /** BetterF3 11.0.3 modules shown as they are [verified, javap]: version, FPS, Java, memory, CPU, GPU, display and
     *  time, graphics settings, server tick and packets, the help lines, the other mods' system lines (the right
     *  column minus vanilla's) and the spacer. Coordinates, location (biome, light, day), target, entities, chunks,
     *  sounds (with the cave mood) and the other mods' game lines are veiled, as is any module not listed. */
    public static final Set<String> KEPT_BETTERF3_MODULES = Set.of("MinecraftModule", "FpsModule", "SystemModule", "GraphicsModule",
        "ServerModule", "HelpModule", "MiscRightModule", "EmptyModule");
    /** Vanilla marks a targeted block, fluid or entity section of the right column by an underlined heading. */
    private static final String UNDERLINE = "§n"; // ChatFormatting.UNDERLINE
    /** Vanilla 1.21.1 {@code getGameInformation}: {@code "\"%s\" server%s, %.0f tx, %.0f rx"} on a remote server. */
    private static final Pattern REMOTE_SERVER = Pattern.compile("^\".*\" server.*, \\S+ tx, \\S+ rx$");

    /** GLFW key code of F3+C: copies the player's position as a teleport command (vanilla {@code KeyboardHandler}). */
    public static final int KEY_COPY_LOCATION = 67;
    /** GLFW key code of F3+I: copies the targeted block's or entity's data as a command. */
    public static final int KEY_COPY_TARGET = 73;

    private F3Veil() {}

    /**
     * An F3 key combination while the veil is up: F3+C and F3+I copy nothing and give the answers vanilla gives with
     * reduced debug info [verified, NeoForge 21.1.252 sources of {@code KeyboardHandler.handleDebugKeys}]: C unhandled
     * ({@code false}, so releasing F3 still toggles the screen), I handled ({@code true}). Null: vanilla handles the key.
     */
    public static Boolean vetoedDebugKey(int key) {
        return switch (key) {
            case KEY_COPY_LOCATION -> Boolean.FALSE;
            case KEY_COPY_TARGET -> Boolean.TRUE;
            default -> null;
        };
    }

    public static boolean keepsModule(String simpleClassName) {
        return KEPT_BETTERF3_MODULES.contains(simpleClassName);
    }

    /**
     * One line veiled: {@code "Biome: tfc:plains"} to {@code "Biome: ???"}; a line without a label, or whose text before
     * the first {@code ": "} holds an id ({@code "minecraft:overworld FC: 0"}), to {@code ???}.
     */
    public static String veilLine(String line) {
        if (line == null || line.isEmpty()) return line;
        int colon = line.indexOf(": ");
        return colon > 0 && line.lastIndexOf(':', colon - 1) < 0 ? line.substring(0, colon) + ": " + VEIL : VEIL;
    }

    /** Every line veiled, a run of label-less lines collapsed into one {@code ???}; empty lines stay. */
    public static List<String> veil(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) add(out, veilLine(line));
        return out;
    }

    /**
     * Vanilla's left column (NeoForge's {@code CustomizeGuiOverlayEvent.DebugText} list: {@code getGameInformation()},
     * then the chart and help lines, then other mods' lines): the version line, the FPS line, the server tick line and
     * the help lines stay, everything else is veiled.
     */
    public static List<String> veilLeft(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) add(out, keptLeft(line) ? line : veilLine(line));
        return out;
    }

    static boolean keptLeft(String line) {
        if (line == null || line.isEmpty()) return true;
        return line.startsWith("Minecraft ") || line.contains(" fps T: ") || line.startsWith("Integrated server @")
            || REMOTE_SERVER.matcher(line).matches() || line.startsWith("Debug charts: ") || line.startsWith("For help: ");
    }

    /**
     * Vanilla's right column: the system lines (Java, memory, CPU, display, GPU, OpenGL) and other mods' lines stay;
     * each targeted block, fluid or entity section (an underlined heading up to the next empty line) is veiled.
     */
    public static List<String> veilRight(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        boolean inTarget = false;
        for (String line : lines) {
            if (line == null || line.isEmpty()) inTarget = false;
            else if (line.startsWith(UNDERLINE)) inTarget = true;
            add(out, inTarget ? veilLine(line) : line);
        }
        return out;
    }

    private static void add(List<String> out, String line) {
        if (VEIL.equals(line) && !out.isEmpty() && VEIL.equals(out.get(out.size() - 1))) return;
        out.add(line);
    }
}
