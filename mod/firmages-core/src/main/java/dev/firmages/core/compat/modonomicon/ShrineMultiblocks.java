package dev.firmages.core.compat.modonomicon;

import com.klikli_dev.modonomicon.api.multiblock.Multiblock;
import com.klikli_dev.modonomicon.data.MultiblockDataManager;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The only class that touches Modonomicon on the server (SPEC §7.2). Shrine rings are Modonomicon dense/sparse
 * multiblocks in {@code data/<ns>/modonomicon/multiblocks/}, anchored at the heart (pattern character {@code 0}).
 */
public final class ShrineMultiblocks {
    private ShrineMultiblocks() {}

    /**
     * Result of checking one ring.
     *
     * @param known        the multiblock id is loaded
     * @param rotation     the rotation the ring is complete in, or null
     * @param bestRotation the rotation with the most matching blocks (for the ghost preview and the status)
     * @param matched      matching counted blocks in {@code bestRotation}
     * @param total        counted blocks of the ring
     * @param missing      up to 8 missing positions with the expected block, in {@code bestRotation}
     */
    public record RingCheck(boolean known, Rotation rotation, Rotation bestRotation, int matched, int total, List<String> missing) {
        public boolean valid() {
            return rotation != null;
        }
    }

    public static Optional<Multiblock> get(ResourceLocation id) {
        MultiblockDataManager m = MultiblockDataManager.get();
        if (m == null) return Optional.empty();
        return Optional.ofNullable(m.getMultiblock(id));
    }

    public static RingCheck check(Level level, BlockPos heart, ResourceLocation id) {
        Optional<Multiblock> mb = get(id);
        if (mb.isEmpty()) return new RingCheck(false, null, Rotation.NONE, 0, 0, List.of("multiblock " + id + " is not loaded"));
        Multiblock m = mb.get();
        Rotation valid = m.validate(level, heart);
        Rotation best = valid != null ? valid : Rotation.NONE;
        int bestMatched = -1;
        int total = 0;
        List<String> missing = List.of();
        for (Rotation r : valid != null ? new Rotation[] {valid} : Rotation.values()) {
            int matched = 0;
            int counted = 0;
            List<String> miss = new ArrayList<>();
            for (Multiblock.SimulateResult res : results(m, level, heart, r)) {
                if (!res.getStateMatcher().countsTowardsTotalBlocks()) continue;
                counted++;
                if (res.test(level, r)) {
                    matched++;
                } else if (miss.size() < 8) {
                    BlockPos p = res.getWorldPosition();
                    miss.add(p.getX() + " " + p.getY() + " " + p.getZ() + ": "
                        + res.getStateMatcher().getDisplayedState(0).getBlock().getName().getString());
                }
            }
            if (matched > bestMatched) {
                bestMatched = matched;
                best = r;
                total = counted;
                missing = miss;
            }
        }
        return new RingCheck(true, valid, best, Math.max(0, bestMatched), total, List.copyOf(missing));
    }

    /**
     * World positions of the pattern character {@code key} in rotation {@code rotation}.
     * <p>
     * [verified, javap 1.120.7] {@code DenseMultiblock.simulate} pairs the matcher of local layer y (counted from
     * the bottom, as the structure is validated) with the character of pattern layer y counted from the top, so
     * the reported characters are mirrored vertically. The heart ({@code 0}) calibrates it: when the result that
     * reports {@code 0} is not at the heart, characters are mapped back to their real layer. Rings must keep the
     * heart off the exact middle layer (all shipped rings have it in the bottom layer).
     */
    public static List<BlockPos> positions(Level level, BlockPos heart, ResourceLocation id, Rotation rotation, char key) {
        Optional<Multiblock> mb = get(id);
        if (mb.isEmpty()) return List.of();
        Pair<BlockPos, Collection<Multiblock.SimulateResult>> sim = mb.get().simulate(level, heart, rotation, false, false);
        int bottom = sim.getFirst().getY();
        int sizeY = mb.get().getSize().getY();
        boolean mirrored = false;
        for (Multiblock.SimulateResult res : sim.getSecond()) {
            Character c = res.getCharacter();
            if (c != null && c == '0' && !res.getWorldPosition().equals(heart)) mirrored = true;
        }
        List<BlockPos> out = new ArrayList<>();
        for (Multiblock.SimulateResult res : sim.getSecond()) {
            Character c = res.getCharacter();
            if (c == null || c != key) continue;
            BlockPos p = res.getWorldPosition();
            out.add(mirrored ? new BlockPos(p.getX(), bottom + sizeY - 1 - (p.getY() - bottom), p.getZ()) : p.immutable());
        }
        return out;
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

    private static Collection<Multiblock.SimulateResult> results(Multiblock m, Level level, BlockPos heart, Rotation r) {
        Pair<BlockPos, Collection<Multiblock.SimulateResult>> sim = m.simulate(level, heart, r, false, false);
        return sim.getSecond();
    }
}
