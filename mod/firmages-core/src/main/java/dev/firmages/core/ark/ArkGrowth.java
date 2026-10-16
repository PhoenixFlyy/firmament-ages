package dev.firmages.core.ark;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Grows one stage template of an island (SPEC §31.8): the blocks of {@code data/firmages/structure/ark/age_N/stage_K.nbt}
 * appear from the plinth outward over {@code ark.growthTicks}, in the manner of the consecration's batch placer (§17):
 * a list of steps with a tick offset by distance, worked off on the server tick, waiting while a chunk is unloaded.
 * A step writes only into air or into a block the island's own earlier stages placed and that still stands
 * ({@link ArkProgress#grown()}), never over a player's block.
 *
 * <p>Template frame (shared with {@code dev/gen_ark.py}): 25 by 22 by 25 blocks, the island's top centre at template
 * (12, 6, 12), so a stage reaches 12 blocks out, five layers under the surface (roots, drips) and 15 above it.
 */
public final class ArkGrowth {
    public static final int HALF = 12;
    public static final int BELOW = 6;

    /** One block of a stage. */
    public record Step(BlockPos pos, BlockState state, int offset) {}

    /** A stage on its way. {@code next}: the first step not worked off; {@code clock}: ticks the batch has run. */
    static final class Batch {
        final int island;
        final int stage;
        final List<Step> steps;
        int next;
        int clock;
        int placed;
        int kept;

        Batch(int island, int stage, List<Step> steps) {
            this.island = island;
            this.stage = stage;
            this.steps = steps;
        }

        boolean done() {
            return next >= steps.size();
        }
    }

    private ArkGrowth() {}

    /** Where template position (0, 0, 0) lies for an island with top centre {@code top}. */
    public static BlockPos origin(BlockPos top) {
        return top.offset(-HALF, -BELOW, -HALF);
    }

    /** The template's blocks in world positions (air and structure voids left out), or empty if it is not loaded. */
    public static Optional<List<Step>> blocks(ServerLevel level, ResourceLocation template, BlockPos top) {
        Optional<StructureTemplate> t = level.getServer().getStructureManager().get(template);
        if (t.isEmpty()) return Optional.empty();
        CompoundTag nbt = t.get().save(new CompoundTag());
        ListTag palette = nbt.contains("palette", Tag.TAG_LIST) ? nbt.getList("palette", Tag.TAG_COMPOUND)
            : nbt.getList("palettes", Tag.TAG_LIST).isEmpty() ? new ListTag() : nbt.getList("palettes", Tag.TAG_LIST).getList(0);
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) states.add(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), palette.getCompound(i)));
        BlockPos o = origin(top);
        List<Step> out = new ArrayList<>();
        ListTag blocks = nbt.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            int s = b.getInt("state");
            if (s < 0 || s >= states.size()) continue;
            BlockState st = states.get(s);
            if (st.isAir() || st.is(Blocks.STRUCTURE_VOID)) continue;
            ListTag p = b.getList("pos", Tag.TAG_INT);
            out.add(new Step(o.offset(p.getInt(0), p.getInt(1), p.getInt(2)), st, 0));
        }
        return Optional.of(out);
    }

    /** Orders {@code steps} from the plinth outward over {@code ticks} (bottom up within a column). */
    public static List<Step> schedule(List<Step> steps, BlockPos plinth, int ticks) {
        double max = 0;
        for (Step s : steps) max = Math.max(max, Math.hypot(s.pos().getX() - plinth.getX(), s.pos().getZ() - plinth.getZ()));
        List<Step> out = new ArrayList<>(steps.size());
        for (Step s : steps) {
            out.add(new Step(s.pos(), s.state(), ArkRules.growthTick(s.pos().getX() - plinth.getX(), s.pos().getZ() - plinth.getZ(), max, ticks)));
        }
        out.sort(Comparator.comparingInt(Step::offset).thenComparingInt(s -> s.pos().getY()));
        return out;
    }

    /** May a stage block go to {@code pos}: air, or a block the island grew there that still stands. */
    public static boolean free(ServerLevel level, ArkProgress p, BlockPos pos) {
        BlockState now = level.getBlockState(pos);
        if (now.isAir()) return true;
        String grown = p.grown.get(pos.asLong());
        return grown != null && grown.equals(BuiltInRegistries.BLOCK.getKey(now.getBlock()).toString());
    }

    /**
     * Works off the steps that are due. A step in an unloaded chunk holds the batch (the clock stops) until the chunk
     * is loaded again. @return true when the batch is finished
     */
    static boolean tick(ServerLevel level, ArkProgress p, Batch b) {
        while (!b.done() && b.steps.get(b.next).offset() <= b.clock) {
            Step s = b.steps.get(b.next);
            if (!level.isLoaded(s.pos())) return false;
            if (free(level, p, s.pos())) {
                level.setBlock(s.pos(), s.state(), Block.UPDATE_CLIENTS);
                p.grown.put(s.pos().asLong(), BuiltInRegistries.BLOCK.getKey(s.state().getBlock()).toString());
                b.placed++;
            } else {
                b.kept++;
            }
            b.next++;
        }
        b.clock++;
        return b.done();
    }

    /**
     * Takes away every block the island grew that still stands ({@code ark reset}); a block a player put in its place
     * stays. @return the number of blocks removed
     */
    static int remove(ServerLevel level, ArkProgress p) {
        int n = 0;
        // Top down, so plants go before the soil under them.
        List<Long> order = new ArrayList<>(p.grown.keySet());
        order.sort(Comparator.comparingInt((Long l) -> BlockPos.getY(l)).reversed());
        for (long l : order) {
            BlockPos pos = BlockPos.of(l);
            BlockState now = level.getBlockState(pos);
            if (!now.isAir() && p.grown.get(l).equals(BuiltInRegistries.BLOCK.getKey(now.getBlock()).toString())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                n++;
            }
        }
        FirmagesCore.LOGGER.debug("Archipelago: {} grown blocks removed", n);
        return n;
    }
}
