package dev.firmages.core.loot;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeIndex;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Prefilled containers of structure templates (SPEC §19.5): {@code mixin/StructureTemplateMixin} hands every block
 * entity's data that {@code StructureTemplate.placeInWorld} loads through {@link #filter}. Whatever the block entity
 * (chests, barrels, TFC vessels, piles and racks, pedestals), the stacks of an Age the server does not hold and every
 * disabled stack go, nested contents included ({@link PlacedContents}). Works on the data before the load, so world
 * generation threads touch no block entity, capability or chunk. The template's own data stays unchanged (a copy is
 * filtered).
 */
public final class TemplateContents {
    private static final AtomicLong PLACED = new AtomicLong();
    private static final AtomicLong REMOVED = new AtomicLong();

    /** {@link PlacedContents.Nodes} over NBT. */
    static final PlacedContents.Nodes NBT = new PlacedContents.Nodes() {
        @Override
        public boolean isCompound(Object node) {
            return node instanceof CompoundTag;
        }

        @Override
        public boolean isList(Object node) {
            return node instanceof ListTag;
        }

        @Override
        public List<String> keys(Object compound) {
            return new ArrayList<>(((CompoundTag) compound).getAllKeys());
        }

        @Override
        public Object get(Object compound, String key) {
            return ((CompoundTag) compound).get(key);
        }

        @Override
        public String string(Object compound, String key) {
            return ((CompoundTag) compound).get(key) instanceof StringTag s ? s.getAsString() : null;
        }

        @Override
        public boolean isNumber(Object compound, String key) {
            return ((CompoundTag) compound).get(key) instanceof NumericTag;
        }

        @Override
        public void remove(Object compound, String key) {
            ((CompoundTag) compound).remove(key);
        }

        @Override
        public int size(Object list) {
            return ((ListTag) list).size();
        }

        @Override
        public Object get(Object list, int index) {
            return ((ListTag) list).get(index);
        }

        @Override
        public void remove(Object list, int index) {
            ((ListTag) list).remove(index);
        }
    };

    private TemplateContents() {}

    /** The data the block entity loads: unchanged, or a copy without the stacks of the Ages the server lacks. */
    public static CompoundTag filter(CompoundTag nbt) {
        if (nbt == null || !ServerConfig.lootFilter()) return nbt;
        AgeIndex idx = AgeIndex.current();
        if (idx.items().isEmpty() && idx.disabledItems().isEmpty()) return nbt;
        Set<AgeId> unlocked = AgeService.currentSnapshot().unlocked();
        Predicate<String> drop = id -> dropped(id, idx, unlocked);
        PLACED.incrementAndGet();
        if (PlacedContents.filter(nbt, NBT, drop, false) == 0) return nbt;
        CompoundTag copy = nbt.copy();
        REMOVED.addAndGet(PlacedContents.filter(copy, NBT, drop, true));
        return copy;
    }

    /** True for a registered item the Ages do not hold or that is disabled; unknown ids and air stay. */
    static boolean dropped(String id, AgeIndex idx, Set<AgeId> unlocked) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        if (item == null || item == Items.AIR) return false;
        return !LootAgeRule.keep(idx.ageOf(item), idx.isDisabled(item), unlocked);
    }

    /** Block entities placed by templates and stacks removed since start (diagnostics). */
    public static long[] counts() {
        return new long[] {PLACED.get(), REMOVED.get()};
    }
}
