package dev.firmages.core.gametest;

import dev.firmages.core.hardship.GridLockService;
import dev.firmages.core.hardship.GridSlots;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;

/** Curios checks of {@link HardshipGameTests}; only loaded when Curios is in the run. */
final class CuriosProbe {
    private CuriosProbe() {}

    private static void ok(GameTestHelper h, boolean cond, String what) {
        if (!cond) h.fail(what);
    }

    /** SPEC §19.3: the Curios screen's own 2x2 grid follows the lock ({@code lock} locks it unless {@code canLock} is false; four {@code part} make {@code result}). */
    static void gridLocked(GameTestHelper h, ServerPlayer p, Runnable lock, Runnable open, boolean canLock, Item part, Item result) {
        CuriosContainer menu = new CuriosContainer(9, p.getInventory());
        ok(h, menu.getSlot(0) instanceof GridSlots.Result, "Curios slot 0 is the grid's result: " + menu.getSlot(0).getClass().getName());
        for (int i = 1; i <= 4; i++) {
            ok(h, menu.getSlot(i) instanceof GridSlots.Input, "Curios slot " + i + " is a grid slot: " + menu.getSlot(i).getClass().getName());
        }
        Container grid = menu.getSlot(1).container;
        if (canLock) {
            lock.run();
            ok(h, GridLockService.locked(p), "locked");
            ok(h, !menu.getSlot(1).isActive() && !menu.getSlot(1).mayPlace(new ItemStack(part)) && !menu.getSlot(0).isActive(),
                "the locked Curios grid is inactive and takes nothing");
            for (int i = 0; i < grid.getContainerSize(); i++) grid.setItem(i, new ItemStack(part));
            ok(h, grid.isEmpty() && menu.getSlot(0).getItem().isEmpty(), "four planks in the locked Curios grid make nothing and leave it");
            ok(h, p.getInventory().countItem(part) == 4, "the planks are back in the inventory: " + p.getInventory().countItem(part));
        }
        open.run();
        p.getInventory().clearContent();
        for (int i = 0; i < grid.getContainerSize(); i++) grid.setItem(i, new ItemStack(part));
        ok(h, menu.getSlot(0).getItem().is(result), "open: the Curios grid crafts, got " + menu.getSlot(0).getItem());
        grid.clearContent();
    }
}
