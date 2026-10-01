package dev.firmages.core.compat.modonomicon;

import com.klikli_dev.modonomicon.api.multiblock.Multiblock;
import com.klikli_dev.modonomicon.data.MultiblockDataManager;
import com.mojang.datafixers.util.Pair;
import dev.firmages.core.shrine.ConsecratedBlock;
import dev.firmages.core.shrine.Consecration;
import dev.firmages.core.shrine.ShrineDataLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The only class that touches Modonomicon on the server (SPEC §7.2). Shrine rings are Modonomicon dense/sparse
 * multiblocks in {@code data/<ns>/modonomicon/multiblocks/}, anchored at the heart (pattern character {@code 0}).
 * <p>
 * The multiblocks stay the build recipe in Age materials (also for the ghost preview). Since M7 (SPEC §17) a ring
 * position also matches when it holds a consecrated block of the role its pattern key has in
 * {@code consecration.json}, so a ring validates before, during and after its consecration. This class therefore
 * runs the validation itself from {@code simulate} instead of {@code Multiblock.validate}.
 */
public final class ShrineMultiblocks {
    private ShrineMultiblocks() {}

    /**
     * One position of a ring in one rotation.
     *
     * @param key         the pattern character of this position (corrected for the Modonomicon layer bug)
     * @param role        the consecrated role of the key, or null (heart, plinth, unmapped, any)
     * @param counts      the matcher counts towards the ring's blocks (false for {@code _})
     * @param ok          the Age material matcher or a consecrated block of the right role matches
     * @param consecrated the position holds a consecrated block of the right role
     * @param accent      the consecrated block's accent, or -1
     */
    public record Cell(BlockPos pos, char key, @Nullable Consecration.Role role, boolean counts, boolean ok, boolean consecrated, int accent) {}

    /**
     * Result of checking one ring.
     *
     * @param known        the multiblock id is loaded
     * @param rotation     the rotation the ring is complete in, or null
     * @param bestRotation the rotation with the most matching blocks (for the ghost preview and the status)
     * @param matched      matching counted blocks in {@code bestRotation}
     * @param total        counted blocks of the ring
     * @param missing      up to 8 missing positions with the expected block, in {@code bestRotation}
     * @param cells        every position of {@code bestRotation}
     */
    public record RingCheck(boolean known, Rotation rotation, Rotation bestRotation, int matched, int total, List<String> missing, List<Cell> cells) {
        public boolean valid() {
            return rotation != null;
        }

        /** Counted positions that do not match in {@code bestRotation}. */
        public List<BlockPos> missingPositions() {
            return cells.stream().filter(c -> c.counts() && !c.ok()).map(Cell::pos).toList();
        }
    }

    public static Optional<Multiblock> get(ResourceLocation id) {
        MultiblockDataManager m = MultiblockDataManager.get();
        if (m == null) return Optional.empty();
        return Optional.ofNullable(m.getMultiblock(id));
    }

    public static RingCheck check(Level level, BlockPos heart, ResourceLocation id) {
        Optional<Multiblock> mb = get(id);
        if (mb.isEmpty()) return new RingCheck(false, null, Rotation.NONE, 0, 0, List.of("multiblock " + id + " is not loaded"), List.of());
        Multiblock m = mb.get();
        Map<Character, Consecration.Role> roles = ShrineDataLoader.ringRoles(id);
        Rotation valid = null;
        Rotation best = Rotation.NONE;
        int bestMatched = -1;
        int total = 0;
        List<String> missing = List.of();
        List<Cell> bestCells = List.of();
        // Modonomicon's order: a symmetrical multiblock is complete in NONE, else the first rotation that matches.
        Rotation[] order = m.isSymmetrical() ? new Rotation[] {Rotation.NONE} : Rotation.values();
        for (Rotation r : order) {
            List<Probe> probes = probe(m, level, heart, r, roles);
            int matched = 0;
            int counted = 0;
            boolean all = true;
            List<String> miss = new ArrayList<>();
            for (Probe pr : probes) {
                Cell c = pr.cell();
                if (!c.ok()) all = false;
                if (!c.counts()) continue;
                counted++;
                if (c.ok()) {
                    matched++;
                } else if (miss.size() < 8) {
                    miss.add(c.pos().getX() + " " + c.pos().getY() + " " + c.pos().getZ() + ": " + pr.expected());
                }
            }
            if (matched > bestMatched || all) {
                bestMatched = matched;
                best = r;
                total = counted;
                missing = miss;
                bestCells = probes.stream().map(Probe::cell).toList();
            }
            if (all) {
                valid = r;
                break;
            }
        }
        return new RingCheck(true, valid, best, Math.max(0, bestMatched), total, List.copyOf(missing), bestCells);
    }

    /** The cells of ring {@code id} in rotation {@code rotation} (empty when the multiblock is not loaded). */
    public static List<Cell> cells(Level level, BlockPos heart, ResourceLocation id, Rotation rotation) {
        Optional<Multiblock> mb = get(id);
        if (mb.isEmpty()) return List.of();
        return probe(mb.get(), level, heart, rotation, ShrineDataLoader.ringRoles(id)).stream().map(Probe::cell).toList();
    }

    /** World positions of the pattern character {@code key} in rotation {@code rotation}. */
    public static List<BlockPos> positions(Level level, BlockPos heart, ResourceLocation id, Rotation rotation, char key) {
        return cells(level, heart, id, rotation).stream().filter(c -> c.key() == key).map(Cell::pos).toList();
    }

    /** The positions of {@code key} in every rotation (for a plinth placed before its ring is complete). */
    public static List<BlockPos> positionsAnyRotation(Level level, BlockPos heart, ResourceLocation id, char key) {
        List<BlockPos> out = new ArrayList<>();
        for (Rotation r : Rotation.values()) out.addAll(positions(level, heart, id, r, key));
        return out;
    }

    /** Horizontal half-size of the ring around the heart (for the shrine bounding box). */
    public static int radius(ResourceLocation id) {
        return get(id).map(m -> Math.max(m.getSize().getX(), m.getSize().getZ()) / 2 + 1).orElse(0);
    }

    public static Component name(ResourceLocation id) {
        return Component.translatableWithFallback("multiblock." + id.getNamespace() + "." + id.getPath(), id.toString());
    }

    /** A cell plus the display name of the expected Age material (status text). */
    private record Probe(Cell cell, String expected) {}

    /**
     * The positions of a ring in one rotation with their real pattern character, tested against the Age material
     * matcher and the consecrated role.
     * <p>
     * [verified, javap 1.120.7] {@code DenseMultiblock.simulate} pairs the matcher of local layer y (counted from
     * the bottom, as the structure is validated) with the character of pattern layer y counted from the top, so
     * the reported characters are mirrored vertically. The heart ({@code 0}) calibrates it: when the result that
     * reports {@code 0} is not at the heart, characters are mapped back to their real layer. Rings must keep the
     * heart off the exact middle layer (all shipped rings have it in the bottom layer).
     */
    private static List<Probe> probe(Multiblock m, Level level, BlockPos heart, Rotation r, Map<Character, Consecration.Role> roles) {
        Pair<BlockPos, Collection<Multiblock.SimulateResult>> sim = m.simulate(level, heart, r, false, false);
        int bottom = sim.getFirst().getY();
        int sizeY = m.getSize().getY();
        boolean mirrored = false;
        for (Multiblock.SimulateResult res : sim.getSecond()) {
            Character c = res.getCharacter();
            if (c != null && c == '0' && !res.getWorldPosition().equals(heart)) mirrored = true;
        }
        Map<BlockPos, Character> chars = new HashMap<>();
        for (Multiblock.SimulateResult res : sim.getSecond()) {
            Character c = res.getCharacter();
            if (c == null) continue;
            BlockPos p = res.getWorldPosition();
            chars.put(mirrored ? new BlockPos(p.getX(), bottom + sizeY - 1 - (p.getY() - bottom), p.getZ()) : p.immutable(), c);
        }
        List<Probe> out = new ArrayList<>();
        for (Multiblock.SimulateResult res : sim.getSecond()) {
            BlockPos p = res.getWorldPosition().immutable();
            char key = chars.getOrDefault(p, ' ');
            Consecration.Role role = roles.get(key);
            BlockState s = level.getBlockState(p);
            boolean consecrated = role != null && s.getBlock() instanceof ConsecratedBlock cb && cb.role() == role;
            boolean counts = res.getStateMatcher().countsTowardsTotalBlocks();
            boolean ok = consecrated || res.test(level, r);
            String expected = counts && !ok ? res.getStateMatcher().getDisplayedState(0).getBlock().getName().getString() : "";
            out.add(new Probe(new Cell(p, key, role, counts, ok, consecrated, consecrated ? s.getValue(ConsecratedBlock.ACCENT) : -1), expected));
        }
        return out;
    }
}
