package dev.firmages.core.balance;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.firmages.core.FirmagesCore;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /firmages energy audit [radius] [seconds]} (SPEC §38, harmony plan §6): for every block with an FE storage
 * within {@code radius} blocks (horizontal, the whole height) of where it starts, the FE that went in and out through
 * its capability and the change of its stored FE over the run; summed per block type and divided by the ticks. "In"
 * is what a block received, "out" what was drawn out of it plus what it pushed into a neighbour (a lookup from a side
 * faces the pusher, {@link EnergyMeter}). For a generator that pushes, "out" is its output; for a machine, "in" minus
 * the stored change is its use; cables carry as much in as out. Mekanism's strict energy link counts the same way
 * (converted to FE). Written to {@code logs/firmages-energy-audit.json}; the chat shows the top block types.
 */
public final class EnergyAudit {
    private final ServerLevel level;
    private final BlockPos centre;
    private final int radius;
    private final int ticks;
    private final CommandSourceStack source;
    private long startTick;
    private final Map<BlockPos, long[]> flows = new ConcurrentHashMap<>(); // pos -> {in, out}
    private final Map<BlockPos, Long> storedAtStart = new HashMap<>();
    private final Map<BlockPos, String> blockOf = new HashMap<>();
    private List<String> lastLines = List.of();
    private List<TypeFlow> lastTypes = List.of();

    public EnergyAudit(ServerLevel level, BlockPos centre, int radius, int ticks, CommandSourceStack source) {
        this.level = level;
        this.centre = centre.immutable();
        this.radius = radius;
        this.ticks = ticks;
        this.source = source;
    }

    public boolean covers(Level l, BlockPos p) {
        if (l != level) return false;
        long dx = p.getX() - centre.getX(), dz = p.getZ() - centre.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    void in(BlockPos p, long fe) {
        if (covers(level, p)) flows.computeIfAbsent(p, k -> new long[2])[0] += fe;
    }

    void out(BlockPos p, long fe) {
        if (covers(level, p)) flows.computeIfAbsent(p, k -> new long[2])[1] += fe;
    }

    /** {@code fe} went into a block from the side that faces {@code from}: the block at {@code from} gave it. */
    void pushed(Level l, BlockPos from, long fe) {
        if (covers(l, from) && l.getBlockEntity(from) != null) flows.computeIfAbsent(from.immutable(), k -> new long[2])[1] += fe;
    }

    boolean due(MinecraftServer server) {
        return level.getGameTime() - startTick >= ticks;
    }

    /** Every block entity within the radius (plus one block, the neighbours pushers feed) in loaded chunks. */
    private List<BlockEntity> blockEntities(int extra) {
        List<BlockEntity> out = new ArrayList<>();
        int r = radius + extra;
        int c0x = SectionPos.blockToSectionCoord(centre.getX() - r), c1x = SectionPos.blockToSectionCoord(centre.getX() + r);
        int c0z = SectionPos.blockToSectionCoord(centre.getZ() - r), c1z = SectionPos.blockToSectionCoord(centre.getZ() + r);
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    long dx = be.getBlockPos().getX() - centre.getX(), dz = be.getBlockPos().getZ() - centre.getZ();
                    if (dx * dx + dz * dz <= (long) r * r) out.add(be);
                }
            }
        }
        return out;
    }

    /** FE stored in the block at {@code pos} (null side first, then each side), or -1 without an FE storage. */
    private long stored(BlockPos pos) {
        IEnergyStorage e = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        for (int i = 0; e == null && i < Direction.values().length; i++) e = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.values()[i]);
        return e == null ? -1 : Math.max(0, e.getEnergyStored());
    }

    /** Takes the starting stock and makes every capability cache in the radius look the storage up again (now metered). */
    void begin() {
        startTick = level.getGameTime();
        for (BlockEntity be : blockEntities(1)) {
            BlockPos p = be.getBlockPos().immutable();
            level.invalidateCapabilities(p);
            if (!covers(level, p)) continue;
            long s = stored(p);
            if (s >= 0) {
                storedAtStart.put(p, s);
                blockOf.put(p, BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString());
            }
        }
        FirmagesCore.LOGGER.info("Energy audit started at {} in {}: radius {}, {} ticks, {} FE storage blocks", centre.toShortString(),
            level.dimension().location(), radius, ticks, storedAtStart.size());
    }

    void cancel(String why) {
        source.sendFailure(Component.literal("Energy audit at " + centre.toShortString() + " cancelled: " + why));
    }

    /** The run is over: sums per block type, the report, and the caches go back to the plain storage. */
    void finish() {
        long t = Math.max(1, level.getGameTime() - startTick);
        Map<BlockPos, Long> storedAtEnd = new HashMap<>();
        for (BlockEntity be : blockEntities(1)) {
            BlockPos p = be.getBlockPos().immutable();
            level.invalidateCapabilities(p);
            if (!covers(level, p)) continue;
            long s = stored(p);
            if (s >= 0) {
                storedAtEnd.put(p, s);
                blockOf.putIfAbsent(p, BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString());
            }
        }
        Map<String, Row> rows = new TreeMap<>();
        for (BlockPos p : blockOf.keySet()) {
            String id = blockOf.get(p);
            long[] f = flows.getOrDefault(p, new long[2]);
            long delta = storedAtEnd.getOrDefault(p, 0L) - storedAtStart.getOrDefault(p, storedAtEnd.getOrDefault(p, 0L));
            rows.computeIfAbsent(id, Row::new).add(f[0], f[1], delta);
        }
        for (Map.Entry<BlockPos, long[]> e : flows.entrySet()) {
            if (blockOf.containsKey(e.getKey())) continue; // a block that came or went during the run
            String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(e.getKey()).getBlock()).toString();
            rows.computeIfAbsent(id, Row::new).add(e.getValue()[0], e.getValue()[1], 0);
        }
        List<Row> sorted = new ArrayList<>(rows.values());
        sorted.sort(Comparator.comparingLong((Row r) -> -(Math.abs(r.in) + Math.abs(r.out) + Math.abs(r.delta))).thenComparing(r -> r.id));
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "Energy audit at %s, radius %d, %d ticks: %d block types, %d FE storage blocks (FE/t per type: in / out / stored change / net use)",
            centre.toShortString(), radius, t, sorted.size(), blockOf.size()));
        for (Row r : sorted) {
            lines.add(String.format(Locale.ROOT, "  %s x%d: in %.1f, out %.1f, stored %+.1f, net %+.1f FE/t", r.id, r.count,
                r.in / (double) t, r.out / (double) t, r.delta / (double) t, (r.in - r.out - r.delta) / (double) t));
        }
        lastLines = List.copyOf(lines);
        List<TypeFlow> types = new ArrayList<>();
        for (Row r : sorted) types.add(new TypeFlow(r.id, r.count, r.in / (double) t, r.out / (double) t, r.delta / (double) t));
        lastTypes = List.copyOf(types);
        JsonObject json = new JsonObject();
        json.addProperty("dimension", level.dimension().location().toString());
        json.addProperty("centre", centre.toShortString());
        json.addProperty("radius", radius);
        json.addProperty("ticks", t);
        JsonArray arr = new JsonArray();
        for (Row r : sorted) {
            JsonObject o = new JsonObject();
            o.addProperty("block", r.id);
            o.addProperty("count", r.count);
            o.addProperty("in_fe_per_tick", r.in / (double) t);
            o.addProperty("out_fe_per_tick", r.out / (double) t);
            o.addProperty("stored_change_fe_per_tick", r.delta / (double) t);
            o.addProperty("net_use_fe_per_tick", (r.in - r.out - r.delta) / (double) t);
            arr.add(o);
        }
        json.add("types", arr);
        Path file = level.getServer().getServerDirectory().resolve("logs").resolve("firmages-energy-audit.json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FirmagesCore.LOGGER.warn("Energy audit: cannot write {}", file, e);
        }
        for (String l : lines) FirmagesCore.LOGGER.info(l);
        int shown = 0;
        for (String l : lines) {
            if (shown++ > 15) break;
            source.sendSuccess(() -> Component.literal(l), false);
        }
        source.sendSuccess(() -> Component.literal("Energy audit report: " + file).withStyle(ChatFormatting.GRAY), false);
    }

    /** The report lines of the finished run (GameTests). */
    public List<String> lines() {
        return lastLines;
    }

    public int ticks() {
        return ticks;
    }

    /** The per-type flows of the finished run in FE per tick (the reference runs, {@link ReferenceRun}). */
    public List<TypeFlow> types() {
        return lastTypes;
    }

    /** One block type of a finished audit: FE per tick in, out and the change of what it stores. */
    public record TypeFlow(String block, int count, double in, double out, double stored) {
        public double netUse() {
            return in - out - stored;
        }
    }

    private static final class Row {
        final String id;
        int count;
        long in;
        long out;
        long delta;

        Row(String id) {
            this.id = id;
        }

        void add(long in, long out, long delta) {
            count++;
            this.in += in;
            this.out += out;
            this.delta += delta;
        }
    }
}
