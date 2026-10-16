package dev.firmages.core.compat.ae2;

import appeng.api.storage.StorageCells;
import appeng.me.cells.BasicCellInventory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * {@code /firmages balance cells} (SPEC §34.2): the item types a fresh cell of each id holds, read through AE2's own
 * {@code StorageCells.getCellInventory}, so the cell-type mixins are checked on a server without a client. Loaded only
 * when AE2 is present.
 */
public final class CellTypes {
    public static final List<String> PROBES = List.of("ae2:item_storage_cell_1k", "ae2:item_storage_cell_256k", "ae2:portable_item_cell_16k",
        "megacells:item_storage_cell_1m", "ae2:fluid_storage_cell_64k");

    private CellTypes() {}

    public static String describe(List<String> ids) {
        StringBuilder out = new StringBuilder("cell types:");
        for (String id : ids) {
            var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id));
            if (item.isEmpty()) {
                out.append(' ').append(id).append(" missing;");
                continue;
            }
            var cell = StorageCells.getCellInventory(new ItemStack(item.get()), null);
            out.append(' ').append(id).append(' ')
                .append(cell instanceof BasicCellInventory b ? String.valueOf(b.getTotalItemTypes()) : "not a basic cell").append(';');
        }
        return out.toString();
    }
}
