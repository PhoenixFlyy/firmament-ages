package dev.firmages.core.ark;

import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.shrine.ShrineService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleBiFunction;

/**
 * Work samples (SPEC §31.8): a block on the island that satisfies one of the island's work rules when the plinth checks.
 * The rule types reuse the shrine rites ({@code blockstate}, {@code energy} through {@link ShrineService#storedEnergy})
 * plus {@code kinetic}, whose speed comes from {@link #kineticSpeed} (Create's kinetic block entities in the pack,
 * {@code compat/create/CreateKinetics}; a stand-in in the GameTests, which have no Create).
 */
public final class ArkWork {
    /** How far above the island's top the check looks (a water wheel on a frame, an AE2 controller on a tower). */
    public static final int ABOVE = 12;
    public static final int BELOW = 3;

    /** RPM of the kinetic block at a position, 0 for none. */
    public static volatile ToDoubleBiFunction<Level, BlockPos> kineticSpeed = (level, pos) -> 0;

    private ArkWork() {}

    /** The first block on island {@code i} (top centre {@code top}) that satisfies one of {@code rules}. */
    public static Optional<BlockPos> find(ServerLevel level, BlockPos top, int i, List<ArkData.Work> rules) {
        int r = OriginArena.arkRadius(i) + 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = top.getY() - BELOW; y <= top.getY() + ABOVE; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > r * r) continue;
                    m.set(top.getX() + x, y, top.getZ() + z);
                    if (!level.isLoaded(m)) continue;
                    BlockState st = level.getBlockState(m);
                    if (st.isAir()) continue;
                    for (ArkData.Work w : rules) {
                        if (satisfies(level, m, st, w)) return Optional.of(m.immutable());
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** One block against one rule. */
    public static boolean satisfies(Level level, BlockPos pos, BlockState st, ArkData.Work w) {
        if (!isBlock(st, w)) return false;
        return switch (w.type()) {
            case BLOCKSTATE -> property(st, w);
            case ENERGY -> level instanceof ServerLevel sl && ShrineService.storedEnergy(sl, List.of(pos)) >= w.amount();
            case KINETIC -> Math.abs(kineticSpeed.applyAsDouble(level, pos)) >= w.rpm();
        };
    }

    static boolean isBlock(BlockState st, ArkData.Work w) {
        if (!w.block().isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(w.block());
            return id != null && BuiltInRegistries.BLOCK.getKey(st.getBlock()).equals(id);
        }
        ResourceLocation tag = ResourceLocation.tryParse(w.tag());
        return tag != null && st.is(TagKey.create(Registries.BLOCK, tag));
    }

    private static boolean property(BlockState st, ArkData.Work w) {
        for (Property<?> p : st.getProperties()) {
            if (!p.getName().equals(w.property())) continue;
            if (!w.values().isEmpty()) return w.values().contains(valueName(st, p));
            return p instanceof IntegerProperty ip && st.getValue(ip) >= w.min();
        }
        return false;
    }

    private static <T extends Comparable<T>> String valueName(BlockState st, Property<T> p) {
        return p.getName(st.getValue(p));
    }

    /** Ids of the rules' blocks that are not registered (selftest and status). */
    public static List<String> unknownBlocks(List<ArkData.Work> rules) {
        return rules.stream().filter(w -> !w.block().isEmpty()).map(ArkData.Work::block)
            .filter(b -> ResourceLocation.tryParse(b) == null || !BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(b))).toList();
    }

    /**
     * True if some registered block of a {@code blockstate} rule has the rule's property, and each named value is one
     * of that property's values (selftest: a property renamed by a mod update would leave the work unreachable).
     */
    public static boolean propertyFits(ArkData.Work w) {
        List<net.minecraft.world.level.block.Block> blocks;
        if (!w.block().isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(w.block());
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return false;
            blocks = List.of(BuiltInRegistries.BLOCK.get(id));
        } else {
            ResourceLocation tag = ResourceLocation.tryParse(w.tag());
            if (tag == null) return false;
            blocks = BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, tag))
                .map(h -> h.stream().map(net.minecraft.core.Holder::value).toList()).orElse(List.of());
        }
        for (net.minecraft.world.level.block.Block b : blocks) {
            Property<?> p = b.getStateDefinition().getProperty(w.property());
            if (p == null) continue;
            if (w.values().isEmpty() ? p instanceof IntegerProperty : w.values().stream().allMatch(v -> p.getValue(v).isPresent())) return true;
        }
        return false;
    }

    /** True if some block exists for the rule (by id, or a tag with at least one block). */
    public static boolean resolvable(ArkData.Work w) {
        if (!w.block().isEmpty()) return unknownBlocks(List.of(w)).isEmpty();
        ResourceLocation tag = ResourceLocation.tryParse(w.tag());
        return tag != null && BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, tag)).map(h -> h.size() > 0).orElse(false);
    }
}
