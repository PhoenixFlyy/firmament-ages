package dev.firmages.core.loot;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeIndex;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The NBT side of {@link PlacedContents} (SPEC §19.5), shared by the template filter ({@link TemplateContents}: block
 * entities and entities a template places) and the loot gate ({@link AgeLoot}: the nested contents of a rolled stack).
 */
public final class NbtContents {
    /** {@link PlacedContents.Nodes} over NBT. */
    static final PlacedContents.Nodes NODES = new PlacedContents.Nodes() {
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

        @Override
        public void empty(Object list, int index) {
            ((ListTag) list).set(index, new CompoundTag());
        }
    };

    private NbtContents() {}

    /** True for a registered item the Ages do not hold or that is disabled; unknown ids and air stay. */
    static boolean dropped(String id, AgeIndex idx, Set<AgeId> unlocked) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        if (item == null || item == Items.AIR) return false;
        return !LootAgeRule.keep(idx.ageOf(item), idx.isDisabled(item), unlocked);
    }

    /**
     * The nested contents of rolled loot: each stack with a component patch (a shulker box's {@code minecraft:container},
     * a bundle's {@code minecraft:bundle_contents}, TFC's {@code tfc:contents} and {@code tfc:vessel}, any mod's list
     * or field of stacks) loses the stacks {@code drop} accepts, at any depth, by the rules of {@link PlacedContents}.
     * The patch goes to NBT and back ({@code DataComponentPatch.CODEC}); a changed stack is replaced in the list by a
     * new one with the same item and count (transient components, which the codec skips, do not come along). A
     * patch that no longer decodes would let the late items through, so its stack goes. @return the stacks removed
     */
    static int filterNested(List<ItemStack> loot, HolderLookup.Provider registries, Predicate<String> drop) {
        int removed = 0;
        RegistryOps<Tag> ops = null;
        for (int i = 0; i < loot.size(); i++) {
            ItemStack stack = loot.get(i);
            if (stack.isEmpty() || stack.isComponentsPatchEmpty()) continue;
            if (ops == null) ops = registries.createSerializationContext(NbtOps.INSTANCE);
            Tag patch = DataComponentPatch.CODEC.encodeStart(ops, stack.getComponentsPatch()).result().orElse(null);
            if (patch == null) continue;
            int n = PlacedContents.filter(patch, NODES, drop, true);
            if (n == 0) continue;
            removed += n;
            DataComponentPatch filtered = DataComponentPatch.CODEC.parse(ops, patch).result().orElse(null);
            if (filtered == null) {
                FirmagesCore.LOGGER.warn("Loot gate: {} no longer decodes without its {} nested stacks of a later Age; the stack goes", stack, n);
                loot.remove(i--);
                continue;
            }
            loot.set(i, new ItemStack(stack.getItemHolder(), stack.getCount(), filtered));
        }
        return removed;
    }
}
