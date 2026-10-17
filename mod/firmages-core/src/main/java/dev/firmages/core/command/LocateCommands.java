package dev.firmages.core.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.anchor.AnchorSavedData;
import dev.firmages.core.ark.ArkProgress;
import dev.firmages.core.isles.IslePiece;
import dev.firmages.core.isles.IsleSavedData;
import dev.firmages.core.isles.IsleSpec;
import dev.firmages.core.launch.LaunchService;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginRegistry;
import dev.firmages.core.origin.OriginSavedData;
import dev.firmages.core.shrine.ShrineSavedData;
import dev.firmages.core.sky.SkySavedData;
import dev.firmages.core.sky.StarfallSite;
import dev.firmages.core.wayshrine.WayshrineSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /firmages locate [radius]} (SPEC §10.3, round G2, Felix: the pack's structures near me): every pack structure
 * within {@code radius} blocks (default 512, at most 2048) of the caller, nearest first, each line a click to teleport.
 * Stored places come from the SavedData that holds them (Wayshrines, the tutorial Wayshrine, isle clusters placed by
 * code and the known isle start, Starfall sites, the shrine heart, Sky Anchors, The Origin's archipelago); world
 * structures of {@code #firmages:locate} come from the structure starts of loaded chunks only (no chunk is loaded or
 * generated); Launch Complexes from the controls of loaded chunks (no store keeps their positions).
 */
public final class LocateCommands {
    public static final TagKey<Structure> LOCATE = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "locate"));
    private static final TagKey<Structure> WAYSHRINE = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "wayshrine"));

    /** One line: the place and its label. */
    public record Found(LocateRules.Hit hit, Component label) {}

    private LocateCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> locate() {
        return Commands.literal("locate")
            .executes(c -> locate(c.getSource(), LocateRules.DEFAULT_RADIUS))
            .then(Commands.argument("radius", IntegerArgumentType.integer(1, LocateRules.MAX_RADIUS))
                .executes(c -> locate(c.getSource(), IntegerArgumentType.getInteger(c, "radius"))));
    }

    private static int locate(CommandSourceStack src, int radius) {
        Vec3 at = src.getPosition();
        int r = LocateRules.clampRadius(radius);
        List<Found> found = find(src.getLevel(), at, r);
        src.sendSuccess(() -> Component.literal("Pack structures within " + r + " blocks of " + (int) Math.floor(at.x) + " " + (int) Math.floor(at.z)
            + ": " + found.size() + (found.isEmpty() ? " (world structures count in loaded chunks only)" : "")).withStyle(ChatFormatting.GOLD), false);
        int shown = 0;
        for (Found f : found) {
            if (shown++ == LocateRules.MAX_LINES) {
                int more = found.size() - LocateRules.MAX_LINES;
                src.sendSuccess(() -> Component.literal("... and " + more + " more; use a smaller radius").withStyle(ChatFormatting.GRAY), false);
                break;
            }
            src.sendSuccess(() -> line(f), false);
        }
        return found.size();
    }

    private static Component line(Found f) {
        LocateRules.Hit h = f.hit();
        String tp = LocateRules.teleport(h.x(), h.y(), h.z());
        MutableComponent c = Component.literal(String.format(java.util.Locale.ROOT, "%5d m  ", h.distance())).withStyle(ChatFormatting.GRAY)
            .append(f.label().copy().withStyle(ChatFormatting.WHITE))
            .append(Component.literal("  " + LocateRules.coords(h.x(), h.y(), h.z())).withStyle(ChatFormatting.AQUA));
        return c.withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tp))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tp))));
    }

    /** Every pack structure of {@code level} within {@code radius} blocks of {@code at}, nearest first. Public for the GameTests. */
    public static List<Found> find(ServerLevel level, Vec3 at, int radius) {
        MinecraftServer server = level.getServer();
        ResourceKey<Level> dim = level.dimension();
        List<Found> out = new ArrayList<>();
        Adder add = (kind, label, x, y, z) -> {
            if (LocateRules.within(at.x, at.z, x, z, radius)) {
                out.add(new Found(new LocateRules.Hit(kind, x, y, z, LocateRules.distance(at.x, at.z, x, z)), label));
            }
        };

        // Wayshrines (SPEC §28.6): one record per core that has loaded once; the tutorial one by its flag.
        WayshrineSavedData ws = WayshrineSavedData.get(server);
        Optional<BlockPos> tutorial = dim == Level.OVERWORLD ? ws.tutorialPos() : Optional.empty();
        List<BlockPos> shrineCores = new ArrayList<>();
        for (WayshrineSavedData.Record rec : ws.records()) {
            if (rec.where().dimension() != dim) continue;
            BlockPos p = rec.where().pos();
            shrineCores.add(p);
            boolean tut = tutorial.isPresent() && tutorial.get().equals(p);
            String state = switch (rec.phase()) {
                case RUIN -> "ruin";
                case RESTORED -> "restored, not lit";
                case LIT -> rec.blessing() ? "lit, blessing" : "lit, broken, no blessing";
            };
            Component label = Component.literal(tut ? "Tutorial Wayshrine: " : rec.phase() == dev.firmages.core.wayshrine.WayshrinePhase.RUIN ? "Wayshrine ruin: " : "Wayshrine: ")
                .append(typeName(rec.type())).append(" (" + state + ")");
            add.add("wayshrine", label, p.getX(), p.getY() + 1, p.getZ());
        }
        if (tutorial.isPresent() && !shrineCores.contains(tutorial.get())) {
            BlockPos p = tutorial.get();
            add.add("wayshrine", Component.literal("Tutorial Wayshrine (not loaded since placed)"), p.getX(), p.getY() + 1, p.getZ());
        }

        // World structures of #firmages:locate whose start chunk is loaded.
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<int[]> isleStarts = new ArrayList<>();
        int[] cr = LocateRules.chunkRange(at.x, at.z, radius);
        for (int cx = cr[0]; cx <= cr[1]; cx++) {
            for (int cz = cr[2]; cz <= cr[3]; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
                    StructureStart start = e.getValue();
                    if (start == null || !start.isValid()) continue;
                    Holder<Structure> h = structures.wrapAsHolder(e.getKey());
                    if (!h.is(LOCATE)) continue;
                    ResourceLocation id = structures.getKey(e.getKey());
                    if (id == null) continue;
                    BoundingBox bb = start.getBoundingBox();
                    if (h.is(WAYSHRINE)) {
                        if (shrineCores.stream().anyMatch(p -> LocateRules.inBox(p.getX(), p.getZ(), bb.minX(), bb.minZ(), bb.maxX(), bb.maxZ(), 2))) continue;
                        add.add("wayshrine", Component.literal("Wayshrine ruin: ").append(typeName(lastSegment(id.getPath()))).append(" (structure)"),
                            bb.getCenter().getX(), bb.maxY() + 1, bb.getCenter().getZ());
                        continue;
                    }
                    Optional<IsleSpec> isle = largestIsle(start.getPieces());
                    if (isle.isPresent()) {
                        IsleSpec s = isle.get();
                        isleStarts.add(new int[] {s.x(), s.z()});
                        long n = start.getPieces().stream().filter(p -> p instanceof IslePiece).count();
                        boolean wind = start.getPieces().stream().anyMatch(p -> p instanceof IslePiece ip && ip.spec().shrine());
                        add.add("isles", Component.literal("Drifting Isles (" + n + " isle" + (n == 1 ? "" : "s") + (wind ? ", Wind Shrine" : "") + ")"),
                            s.x(), s.top() + 1, s.z());
                        continue;
                    }
                    add.add(id.toString(), Component.translatableWithFallback("firmages.locate.structure." + id.getNamespace() + "." + id.getPath().replace('/', '.'),
                        LocateRules.pretty(id.getPath())), bb.getCenter().getX(), bb.maxY() + 1, bb.getCenter().getZ());
                }
            }
        }

        if (dim == Level.OVERWORLD) {
            // Isle clusters placed by code (the fallback and operator clusters, SPEC §30.3) and the known worldgen start.
            IsleSavedData isd = IsleSavedData.get(server);
            for (IsleSavedData.Cluster cl : isd.clusters()) {
                IsleSpec main = cl.isles.stream().max(java.util.Comparator.comparingInt(IsleSpec::radius)).orElse(null);
                if (main == null) continue;
                String origin = cl.origin == IsleSavedData.Origin.FALLBACK ? "fallback" : "operator";
                add.add("isles", Component.literal("Drifting Isles (" + origin + " cluster, " + cl.isles.size() + " isle" + (cl.isles.size() == 1 ? "" : "s")
                    + (cl.shrineCore().isPresent() ? ", Wind Shrine" : "") + (cl.done() ? "" : ", not placed yet") + ")"), main.x(), main.top() + 1, main.z());
            }
            isd.knownStart().ifPresent(p -> {
                boolean listed = isleStarts.stream().anyMatch(s -> s[0] == p.getX() && s[1] == p.getZ());
                if (!listed) add.add("isles", Component.literal("Drifting Isles (known start, not loaded)"), p.getX(), p.getY() + 1, p.getZ());
            });

            // Starfall sites (SPEC §25.8): every site with its state; a crater has its core.
            for (StarfallSite s : SkySavedData.get(server).sites()) {
                int y = s.core != null ? s.core.getY() + 1 : s.centreY != Integer.MIN_VALUE ? s.centreY + 1 : surfaceY(level, s.x, s.z);
                String what = s.state == StarfallSite.State.PENDING ? "Starfall site (falls soon: " : "Starfall crater (";
                add.add("starfall", Component.literal(what + s.metal + ", " + s.size + ", " + s.state.name().toLowerCase(java.util.Locale.ROOT)
                    + (s.origin == StarfallSite.Origin.OLD ? ", old" : "") + ")"), s.x, y, s.z);
            }
        }

        // The shrine heart (SPEC §7), the one player build in the list.
        ShrineSavedData sd = ShrineSavedData.get(server);
        sd.heart().filter(g -> g.dimension() == dim).ifPresent(g ->
            add.add("shrine", Component.literal("Shrine heart" + (sd.intact() ? "" : " (broken)")), g.pos().getX(), g.pos().getY() + 1, g.pos().getZ()));

        // Sky Anchors (SPEC §29): a record per anchor, in every dimension.
        for (Map.Entry<GlobalPos, Integer> e : AnchorSavedData.get(server).anchors().entrySet()) {
            if (e.getKey().dimension() != dim) continue;
            BlockPos p = e.getKey().pos();
            add.add("anchor", Component.literal("Sky Anchor (" + e.getValue() + " tier" + (e.getValue() == 1 ? "" : "s") + ")"), p.getX(), p.getY() + 1, p.getZ());
        }

        // Launch Complexes (SPEC §31.3): only the controls of loaded chunks are known.
        for (BlockPos p : LaunchService.controls(level)) {
            add.add("launch", Component.literal("Launch Complex control (loaded)"), p.getX(), p.getY() + 1, p.getZ());
        }

        // The Origin Archipelago (SPEC §31.8), only in The Origin and once the arena stands.
        if (dim == OriginRegistry.ORIGIN) {
            OriginSavedData od = OriginSavedData.get(server);
            if (od.arenaVersion() > 0) {
                add.add("origin", Component.literal("Origin altar"), OriginArena.ALTAR.getX(), OriginArena.ALTAR.getY() + 1, OriginArena.ALTAR.getZ());
                for (int i = 0; i < OriginArena.ISLANDS; i++) {
                    BlockPos p = OriginArena.islandTop(i);
                    ArkProgress ap = od.ark().island(i);
                    add.add("origin", Component.literal("Archipelago island: ").append(Component.translatable("firmages.age.age_" + i + ".name"))
                        .append(" (" + (ap.complete() ? "complete" : "stage " + ap.stage()) + ")"), p.getX(), p.getY() + 1, p.getZ());
                }
            }
        }
        return LocateRules.sorted(out, Found::hit);
    }

    @FunctionalInterface
    private interface Adder {
        void add(String kind, Component label, int x, int y, int z);
    }

    private static Component typeName(String type) {
        return Component.translatableWithFallback("firmages.wayshrine.type." + type, LocateRules.pretty(type));
    }

    private static String lastSegment(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static Optional<IsleSpec> largestIsle(List<StructurePiece> pieces) {
        return pieces.stream().filter(p -> p instanceof IslePiece).map(p -> ((IslePiece) p).spec())
            .max(java.util.Comparator.comparingInt(IsleSpec::radius));
    }

    /** The surface y + 1 at {@code (x, z)} when its chunk is loaded, else {@link LocateRules#UNKNOWN_Y} (nothing is loaded). */
    private static int surfaceY(ServerLevel level, int x, int z) {
        LevelChunk c = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        return c == null ? LocateRules.UNKNOWN_Y : c.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
    }
}
