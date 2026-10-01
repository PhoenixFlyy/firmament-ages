package dev.firmages.core.reactor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Item side of {@link ReactorFuel}: DE items by registry id, so this class needs no Draconic Evolution classes. */
public final class ReactorItems {
    private ReactorItems() {}

    public static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static int fuelValue(ItemStack stack) {
        return stack.isEmpty() ? 0 : ReactorFuel.fuelValue(id(stack));
    }

    public static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        return item == null ? Items.AIR : item;
    }

    /** A stack of {@code count} of the {@code index}-th chaos fragment (0 large, 1 medium, 2 small). */
    public static ItemStack chaos(int index, int count) {
        Item item = item(ReactorFuel.CHAOS_ITEMS[index]);
        return item == Items.AIR || count <= 0 ? ItemStack.EMPTY : new ItemStack(item, count);
    }
}
