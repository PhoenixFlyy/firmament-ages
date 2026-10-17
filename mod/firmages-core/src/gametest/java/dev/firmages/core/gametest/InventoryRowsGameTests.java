package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.AgeSnapshot;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.hardship.InventoryRows;
import dev.firmages.core.hardship.InventoryRowsService;
import dev.firmages.core.immersion.AgeStatus;
import dev.firmages.core.immersion.ImmersionService;
import dev.firmages.core.net.AgeStatusPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Level G tests of the inventory rows by Age (SPEC §19.4, Felix 2026-10-17). The world's Age in the GameTest run is
 * whatever earlier batches left, so each test sets {@code hardship.inventoryRows.<current Age>} to the row count it
 * plays (0 as in Dawn, 2 as in the Stone Age) and restores it; the defaults per Age are checked through the live
 * config on Dawn and Stone Age snapshots. Every test body is synchronous (no tick passes, so the once-a-second sweep
 * never runs in between).
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class InventoryRowsGameTests {
    /** Ten items that never stack with each other. */
    private static final Item[] TEN = {Items.STICK, Items.FLINT, Items.DIRT, Items.SAND, Items.GRAVEL, Items.OAK_LOG, Items.APPLE, Items.BONE,
        Items.FEATHER, Items.STRING};

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(InventoryRowsGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean cond, String what) {
        if (!cond) h.fail(what);
    }

    private static AgeStatus sync(MinecraftServer server, AgeStatus status) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        AgeStatusPayload.STREAM_CODEC.encode(buf, AgeStatusPayload.of(status));
        return AgeStatusPayload.STREAM_CODEC.decode(buf).status();
    }

    private static AgeId current(MinecraftServer server) {
        return AgeStatus.current(AgeService.state(server).snapshot());
    }

    private static boolean lockedEmpty(Inventory inv, int from, int to) {
        for (int i = from; i < to; i++) if (!inv.getItem(i).isEmpty()) return false;
        return true;
    }

    /** {@code ItemEntity.setUnlimitedLifetime} sets the age to this; such an item never despawns. */
    private static final int UNLIMITED = -32768;

    /** The item entities of {@code item} that lie around {@code p} (what the sweep dropped). */
    private static List<ItemEntity> droppedNear(ServerPlayer p, Item item) {
        return p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4), e -> e.isAlive() && e.getItem().is(item));
    }

    private static String ages(List<ItemEntity> items) {
        return items.stream().map(e -> e.getItem().getCount() + " " + e.getItem().getItem() + " age " + e.getAge()).toList().toString();
    }

    /**
     * Dawn (no open row): the config keys and their defaults; ten pickups fill the nine hotbar slots and the tenth
     * stays on the ground; {@code Inventory.add}, the player's item-handler capability and
     * {@code ItemHandlerHelper.giveItemToPlayer} put nothing into a locked slot; the inventory menu's main-row slots
     * are inactive and take nothing; the status carries 0 rows through encoding; creative and fake players are exempt,
     * and an item a creative player left in a locked row moves out when they turn survival.
     */
    @GameTest(template = "empty", batch = "firmages_7_hardship")
    public static void inventoryRowsHotbarOnlyInDawn(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        for (AgeId a : AgeId.all()) {
            ModConfigSpec.IntValue v = ServerConfig.HARDSHIP_INVENTORY_ROWS.get(a);
            ok(h, List.of("hardship", "inventoryRows", a.id()).equals(v.getPath()) && v.getDefault() == InventoryRows.defaultRows(a),
                "[hardship.inventoryRows] " + a.id() + " = " + InventoryRows.defaultRows(a) + ", got " + v.getPath() + " = " + v.getDefault());
        }
        ok(h, InventoryRows.defaultRows(AgeId.DAWN) == 0 && InventoryRows.defaultRows(AgeId.AGE_0) == 2 && InventoryRows.defaultRows(AgeId.AGE_1) == 3,
            "defaults: Dawn 0, Stone Age 2, Bronze Age 3");
        ModConfigSpec.IntValue key = ServerConfig.HARDSHIP_INVENTORY_ROWS.get(current(server));
        int was = key.get();
        ServerPlayer p = HardshipGameTests.survivor(h, "gt_rows_dawn");
        Inventory inv = p.getInventory();
        try {
            key.set(0); // the world's Age carries the hotbar only, as Dawn does
            ok(h, InventoryRowsService.worldRows(server) == 0 && InventoryRowsService.openRows(p) == 0, "no open row in " + current(server));

            Vec3 at = p.position();
            List<ItemEntity> drops = new ArrayList<>();
            for (Item item : TEN) {
                ItemEntity e = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(item));
                e.setNoPickUpDelay();
                level.addFreshEntity(e);
                drops.add(e);
            }
            for (ItemEntity e : drops) e.playerTouch(p);
            long picked = drops.stream().filter(ItemEntity::isRemoved).count();
            ItemEntity tenth = drops.get(9);
            ok(h, picked == 9 && !tenth.isRemoved() && tenth.getItem().is(Items.STRING), "nine pickups fill the hotbar, the tenth stays on the ground: picked " + picked);
            for (int i = 0; i < 9; i++) ok(h, !inv.getItem(i).isEmpty(), "hotbar slot " + i + " holds a pickup");
            ok(h, lockedEmpty(inv, 9, 36), "the three main rows stay empty");
            tenth.discard();

            ok(h, !inv.add(new ItemStack(Items.STRING)) && lockedEmpty(inv, 9, 36), "Inventory.add (/give, quest rewards) finds no slot");
            ok(h, inv.add(new ItemStack(Items.STICK)) && inv.getItem(0).getCount() == 2, "a hotbar stack still grows");
            IItemHandler cap = p.getCapability(Capabilities.ItemHandler.ENTITY);
            ok(h, cap != null && cap.insertItem(10, new ItemStack(Items.STRING), false).getCount() == 1 && !cap.isItemValid(10, new ItemStack(Items.STRING))
                && lockedEmpty(inv, 9, 36), "the player's item handler inserts nothing into a locked slot");
            ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(Items.STRING));
            ok(h, lockedEmpty(inv, 9, 36) && inv.countItem(Items.STRING) == 0, "giveItemToPlayer drops what the hotbar cannot take");
            droppedNear(p, Items.STRING).forEach(ItemEntity::discard);

            for (int m = 9; m <= 35; m++) {
                Slot s = p.inventoryMenu.getSlot(m);
                ok(h, !s.isActive() && !s.mayPlace(new ItemStack(Items.STRING)), "inventory menu slot " + m + " inactive and takes nothing");
            }
            for (int m = 36; m <= 44; m++) ok(h, p.inventoryMenu.getSlot(m).isActive(), "hotbar menu slot " + m + " stays");
            ok(h, p.inventoryMenu.getSlot(45).mayPlace(new ItemStack(Items.SHIELD)), "the offhand stays");

            AgeStatus st = ImmersionService.status(server);
            ok(h, st.inventoryRows() == 0 && sync(server, st).inventoryRows() == 0, "status: inventoryRows = 0 reaches the client");

            p.setGameMode(GameType.CREATIVE);
            ok(h, InventoryRowsService.openRows(p) == InventoryRows.ALL && inv.add(new ItemStack(Items.STRING)) && inv.getItem(9).is(Items.STRING),
                "creative is exempt: the item lands in slot 9");
            p.setGameMode(GameType.SURVIVAL);
            ok(h, InventoryRowsService.sweep(p) == 1 && lockedEmpty(inv, 9, 36) && inv.countItem(Items.STRING) == 0,
                "back in survival the sweep moves it out (the hotbar is full, so it drops)");
            List<ItemEntity> string = droppedNear(p, Items.STRING);
            ok(h, string.size() == 1 && string.get(0).getAge() == UNLIMITED, "the dropped string never despawns: " + ages(string));
            string.forEach(ItemEntity::discard);
            ok(h, InventoryRowsService.openRows(FakePlayerFactory.getMinecraft(level)) == InventoryRows.ALL, "fake players (machines) are exempt");
        } finally {
            key.set(was);
            DebugPlayerCommands.despawn(p);
        }
        h.succeed();
    }

    /** A menu with a mod-style player slot that overrides {@code mayPlace}: only the menu mixin stops it. */
    private static final class ProbeMenu extends AbstractContainerMenu {
        ProbeMenu(Inventory inv) {
            super(null, 0);
            for (int index : new int[] {9, 0}) {
                addSlot(new Slot(inv, index, 0, 0) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return true;
                    }

                    @Override
                    public boolean isActive() {
                        return true;
                    }
                });
            }
        }

        boolean move(ItemStack stack) {
            return moveItemStackTo(stack, 0, slots.size(), false);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    /**
     * Stone Age (two open rows, 18..35; the top row 9..17 locked): clicking, right-clicking and swapping into a locked
     * slot of a chest menu does nothing; shift-clicking 27 single items out of a chest fills only the open slots (4
     * free hotbar slots and 18 row slots: 22 move, 5 stay); a mod slot that overrides {@code mayPlace} still takes
     * nothing through clicks or {@code moveItemStackTo}.
     */
    @GameTest(template = "empty", batch = "firmages_7_hardship")
    public static void inventoryRowsShiftClickFromChest(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ModConfigSpec.IntValue key = ServerConfig.HARDSHIP_INVENTORY_ROWS.get(current(server));
        int was = key.get();
        ServerPlayer p = HardshipGameTests.survivor(h, "gt_rows_chest");
        Inventory inv = p.getInventory();
        try {
            key.set(2); // as in the Stone Age
            SimpleContainer chest = new SimpleContainer(27);
            ChestMenu menu = ChestMenu.threeRows(1, inv, chest); // slots 0..26 chest, 27..53 inventory 9..35, 54..62 hotbar
            ok(h, menu.getSlot(27).getContainerSlot() == 9 && menu.getSlot(36).getContainerSlot() == 18, "menu slot 27 shows index 9, 36 shows 18");

            menu.setCarried(new ItemStack(Items.DIAMOND, 4));
            menu.clicked(27, 0, ClickType.PICKUP, p);
            menu.clicked(27, 1, ClickType.PICKUP, p);
            ok(h, inv.getItem(9).isEmpty() && menu.getCarried().getCount() == 4, "a click into the locked top row places nothing");
            menu.clicked(36, 1, ClickType.PICKUP, p);
            ok(h, inv.getItem(18).is(Items.DIAMOND) && inv.getItem(18).getCount() == 1, "a click into an open row places one");
            menu.setCarried(ItemStack.EMPTY);
            inv.setItem(0, new ItemStack(Items.EMERALD));
            menu.clicked(27, 0, ClickType.SWAP, p);
            ok(h, inv.getItem(9).isEmpty() && inv.getItem(0).is(Items.EMERALD), "a number-key swap into the locked row does nothing");

            inv.clearContent();
            for (int i = 0; i < 5; i++) inv.setItem(i, new ItemStack(Items.TORCH, i + 1)); // 4 free hotbar slots
            List<Item> kinds = new ArrayList<>();
            for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                if (kinds.size() == 27) break;
                if (item != Items.AIR && item != Items.TORCH && new ItemStack(item).getMaxStackSize() > 1) kinds.add(item);
            }
            for (int i = 0; i < 27; i++) chest.setItem(i, new ItemStack(kinds.get(i)));
            for (int i = 0; i < 27; i++) menu.quickMoveStack(p, i);
            int left = 0;
            for (int i = 0; i < 27; i++) if (!chest.getItem(i).isEmpty()) left++;
            ok(h, left == 5, "22 of 27 fit the open slots, 5 stay in the chest: " + left + " left");
            ok(h, lockedEmpty(inv, 9, 18), "the locked top row stays empty");
            for (int i = 18; i < 36; i++) ok(h, !inv.getItem(i).isEmpty(), "open slot " + i + " filled");
            for (int i = 0; i < 27; i++) menu.clicked(i, 0, ClickType.QUICK_MOVE, p); // the click path too
            ok(h, lockedEmpty(inv, 9, 18), "shift-clicks keep the top row empty");

            inv.clearContent();
            inv.setItem(0, new ItemStack(Items.EMERALD));
            ProbeMenu probe = new ProbeMenu(inv);
            ok(h, !probe.move(new ItemStack(Items.DIAMOND)) && inv.getItem(9).isEmpty(), "moveItemStackTo skips a locked mod slot");
            probe.setCarried(new ItemStack(Items.DIAMOND));
            probe.clicked(0, 0, ClickType.PICKUP, p);
            ok(h, inv.getItem(9).isEmpty() && probe.getCarried().is(Items.DIAMOND), "a click on a locked mod slot does nothing");
            probe.setCarried(ItemStack.EMPTY);
        } finally {
            key.set(was);
            DebugPlayerCommands.despawn(p);
        }
        h.succeed();
    }

    /**
     * The Stone Age opens two rows: the status of a Dawn and a Stone Age world through the live config is 0 and 2 rows
     * (and 3 in the Bronze Age), through encoding. A login with items in locked rows (a world from before) and one
     * free hotbar slot moves the first stack there and drops the second at the player's feet without a lifetime; when
     * the rows open, the Age-change sweep moves an item out of the still locked top row and leaves the opened rows
     * alone.
     */
    @GameTest(template = "empty", batch = "firmages_7_hardship")
    public static void inventoryRowsOpenWithTheStoneAge(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        for (AgeId a : List.of(AgeId.DAWN, AgeId.AGE_0, AgeId.AGE_1)) {
            AgeSnapshot snap = AgeSnapshot.of(a == AgeId.DAWN ? List.of() : AgeId.all().subList(1, a.ordinal() + 1), 1, 0);
            AgeStatus s = AgeStatus.of(snap, t -> Optional.empty(), AgeId.AGE_1, AgeId.AGE_1, t -> Optional.empty(), Optional.empty(), Optional.empty())
                .withInventoryRows(ServerConfig::inventoryRows);
            int want = a == AgeId.DAWN ? 0 : a == AgeId.AGE_0 ? 2 : 3;
            ok(h, s.age() == a && s.inventoryRows() == want && sync(server, s).inventoryRows() == want, a.id() + ": " + want + " rows, got " + s.inventoryRows());
        }
        ModConfigSpec.IntValue key = ServerConfig.HARDSHIP_INVENTORY_ROWS.get(current(server));
        int was = key.get();
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        ServerPlayer a = HardshipGameTests.survivor(h, "gt_rows_login");
        ServerPlayer b = null;
        try {
            key.set(0);
            for (int i = 0; i < 8; i++) a.getInventory().setItem(i, new ItemStack(Items.TORCH)); // one hotbar slot free
            a.getInventory().setItem(12, new ItemStack(Items.FLINT, 5)); // saved before the rows were locked
            a.getInventory().setItem(30, new ItemStack(Items.BONE, 3));
            DebugPlayerCommands.despawn(a);
            b = DebugPlayerCommands.spawn(server, level, "gt_rows_login", at);
            Inventory inv = b.getInventory();
            List<ItemEntity> bones = droppedNear(b, Items.BONE);
            ok(h, lockedEmpty(inv, 9, 36) && inv.getItem(8).is(Items.FLINT) && inv.countItem(Items.FLINT) == 5 && inv.countItem(Items.BONE) == 0,
                "login: the flint left the locked rows for the free hotbar slot, flint " + inv.countItem(Items.FLINT) + ", bone " + inv.countItem(Items.BONE));
            ok(h, bones.size() == 1 && bones.get(0).getItem().getCount() == 3 && bones.get(0).getAge() == UNLIMITED,
                "login: the bones that found no slot lie at the player's feet and never despawn: " + ages(bones));
            bones.forEach(ItemEntity::discard);

            key.set(2); // the Stone Age arrives
            ok(h, InventoryRowsService.openRows(b) == 2 && ImmersionService.status(server).inventoryRows() == 2, "two rows open");
            inv.clearContent();
            inv.setItem(9, new ItemStack(Items.FLINT, 2));
            inv.setItem(20, new ItemStack(Items.BONE, 1));
            InventoryRowsService.sweepAll(server);
            ok(h, inv.getItem(9).isEmpty() && inv.getItem(20).is(Items.BONE) && inv.countItem(Items.FLINT) == 2,
                "the Age-change sweep empties the top row and leaves the open rows");
            for (int i = 0; i < 9; i++) inv.setItem(i, new ItemStack(Items.TORCH));
            ok(h, inv.add(new ItemStack(Items.STRING)) && inv.getItem(18).is(Items.STRING), "with a full hotbar a pickup lands in the first open row (18)");
        } finally {
            key.set(was);
            DebugPlayerCommands.despawn(b != null ? b : a);
        }
        h.succeed();
    }
}
