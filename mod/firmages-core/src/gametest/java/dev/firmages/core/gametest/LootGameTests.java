package dev.firmages.core.gametest;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.balance.ReplaceItemModifier;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.loot.AgeLoot;
import dev.firmages.core.loot.AgeLootModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Level G tests of the loot Age gate (SPEC §19.5). Test data: {@code firmages_test:age_chest} yields 3 rabbit hide
 * (Dawn in the test tags), 2 bread (untagged) and a totem of undying (age_9); {@code firmages_test:iron_chest} yields 4
 * iron ingots (age_2), which the test modifier {@code firmages_test:iron_to_copper} turns into copper ingots (age_0);
 * {@code firmages_test:nested_chest} yields a shulker box (totem, rabbit hide) and a bundle (copper ingot, bread; a totem
 * alone fills a bundle). The golden
 * helmet is disabled in the test tags.
 * Each test is its own batch (they change the server's Ages) and leaves the world in Dawn. The template test builds its
 * template from a chest in the test area ({@code StructureTemplate.fillFromWorld}) and places it with the real
 * {@code placeInWorld}.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class LootGameTests {
    private static final ResourceKey<LootTable> AGE_CHEST = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse("firmages_test:age_chest"));
    private static final ResourceKey<LootTable> IRON_CHEST = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse("firmages_test:iron_chest"));
    private static final ResourceKey<LootTable> NESTED_CHEST = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse("firmages_test:nested_chest"));
    private static final StageId AGE_9 = StageId.of("age_9");

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(LootGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean condition, String message) {
        if (!condition) h.fail(message);
    }

    private static boolean idle(MinecraftServer server) {
        return AgeService.status(server).phase() == ReloadScheduler.Phase.IDLE;
    }

    private static boolean dawn(MinecraftServer server) {
        return AgeService.state(server).snapshot().unlockedOrdered().equals(List.of(AgeId.DAWN));
    }

    /** How many of the item the containers hold together. */
    private static int count(Item item, Container... cs) {
        int n = 0;
        for (Container c : cs) for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    /** The containers' stacks as "count item", for messages. */
    private static String describe(Container... cs) {
        StringBuilder b = new StringBuilder("[");
        for (Container c : cs) for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty()) b.append(b.length() > 1 ? ", " : "").append(s.getCount()).append(' ').append(s.getItem());
        }
        return b.append(']').toString();
    }

    private static ChestBlockEntity lootChest(GameTestHelper h, BlockPos rel, long seed) {
        h.setBlock(rel, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) h.getBlockEntity(rel);
        chest.setLootTable(AGE_CHEST, seed);
        return chest;
    }

    /** The player opens the chest the way a right-click does: the menu unpacks the loot table for him. */
    private static void open(ServerPlayer p, ChestBlockEntity chest) {
        p.openMenu(chest);
        p.closeContainer();
    }

    private static List<ItemStack> roll(ServerLevel level, ResourceKey<LootTable> table, Entity entity, BlockPos at) {
        LootParams.Builder b = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at));
        if (entity != null) b.withParameter(LootContextParams.THIS_ENTITY, entity);
        return level.getServer().reloadableRegistries().getLootTable(table).getRandomItems(b.create(LootContextParamSets.CHEST));
    }

    private static int count(List<ItemStack> loot, Item item) {
        return loot.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /**
     * A Dawn player opens a loot chest: only Dawn and untagged items are in it. The server then holds age_9, the
     * player's team does not: a second chest he opens still has no totem (his team's Ages count, not the server's).
     */
    @GameTest(template = "empty", batch = "firmages_6_loot_opener", timeoutTicks = 1200)
    public static void chestFollowsTheOpenersAge(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer[] p = new ServerPlayer[1];
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(idle(server), "reload pending"))
            .thenExecute(() -> {
                ok(h, ServerConfig.lootFilter(), "gate.lootFilter defaults to true");
                ok(h, dawn(server), "test starts in Dawn: " + AgeService.state(server).snapshot().unlockedIds());
                p[0] = DebugPlayerCommands.spawn(server, h.getLevel(), "gt_looter", Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 3))));
                p[0].setGameMode(GameType.SURVIVAL);
                ok(h, AgeLoot.playerAges(p[0]).equals(java.util.EnumSet.of(AgeId.DAWN)), "the player's team holds Dawn only: " + AgeLoot.playerAges(p[0]));
                ChestBlockEntity chest = lootChest(h, new BlockPos(1, 1, 1), 42L);
                open(p[0], chest);
                ok(h, chest.getLootTable() == null, "opening unpacked the loot table");
                ok(h, count(Items.RABBIT_HIDE, chest) == 3 && count(Items.BREAD, chest) == 2 && count(Items.TOTEM_OF_UNDYING, chest) == 0,
                    "Dawn opener: 3 rabbit hide, 2 bread, no totem: " + describe(chest));
                ok(h, AgeService.simulate(server, AgeId.AGE_9, true), "server grant age_9");
            })
            .thenExecute(() -> {
                ChestBlockEntity chest = lootChest(h, new BlockPos(3, 1, 1), 43L);
                open(p[0], chest);
                ok(h, count(Items.TOTEM_OF_UNDYING, chest) == 0 && count(Items.RABBIT_HIDE, chest) == 3,
                    "the server holds age_9, the opener's team does not: no totem: " + describe(chest));
                ok(h, AgeService.simulate(server, AgeId.AGE_9, false), "server revoke age_9");
            })
            .thenWaitUntil(() -> h.assertTrue(dawn(server) && idle(server), "age_9 revoke and its swap"))
            .thenExecute(() -> DebugPlayerCommands.despawn(p[0]))
            .thenSucceed();
    }

    /**
     * A hopper unpacks a fresh loot chest without a player: the server's Ages decide (Dawn: no totem; age_9: the
     * totem). A roll whose entity is a fake player (a machine) counts as nobody's, so the server's Ages decide too.
     */
    @GameTest(template = "empty", batch = "firmages_6_loot_hopper", timeoutTicks = 1200)
    public static void hopperRollsWithTheServersAges(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        Entity fake = FakePlayerFactory.getMinecraft(level);
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(idle(server), "reload pending"))
            .thenExecute(() -> {
                ok(h, dawn(server), "test starts in Dawn: " + AgeService.state(server).snapshot().unlockedIds());
                h.setBlock(new BlockPos(1, 1, 1), Blocks.HOPPER);
                lootChest(h, new BlockPos(1, 2, 1), 7L);
                List<ItemStack> machine = roll(level, AGE_CHEST, fake, at);
                ok(h, count(machine, Items.TOTEM_OF_UNDYING) == 0 && count(machine, Items.RABBIT_HIDE) == 3,
                    "a fake player's roll in Dawn: no totem: " + machine);
            })
            .thenIdle(20)
            .thenExecute(() -> {
                ChestBlockEntity chest = (ChestBlockEntity) h.getBlockEntity(new BlockPos(1, 2, 1));
                HopperBlockEntity hopper = (HopperBlockEntity) h.getBlockEntity(new BlockPos(1, 1, 1));
                ok(h, chest.getLootTable() == null && count(Items.BREAD, chest, hopper) + count(Items.RABBIT_HIDE, chest, hopper) == 5,
                    "the hopper unpacked the chest and pulls from it: " + describe(chest, hopper));
                ok(h, count(Items.TOTEM_OF_UNDYING, chest, hopper) == 0, "Dawn server, no player: no totem: " + describe(chest, hopper));
                ok(h, AgeService.simulate(server, AgeId.AGE_9, true), "server grant age_9");
                h.setBlock(new BlockPos(3, 1, 1), Blocks.HOPPER);
                lootChest(h, new BlockPos(3, 2, 1), 8L);
                List<ItemStack> machine = roll(level, AGE_CHEST, fake, at);
                ok(h, count(machine, Items.TOTEM_OF_UNDYING) == 1, "a fake player's roll with the server in age_9 keeps the totem: " + machine);
            })
            .thenIdle(20)
            .thenExecute(() -> {
                ChestBlockEntity chest = (ChestBlockEntity) h.getBlockEntity(new BlockPos(3, 2, 1));
                HopperBlockEntity hopper = (HopperBlockEntity) h.getBlockEntity(new BlockPos(3, 1, 1));
                ok(h, chest.getLootTable() == null && count(Items.TOTEM_OF_UNDYING, chest, hopper) == 1
                        && count(Items.RABBIT_HIDE, chest, hopper) == 3 && count(Items.BREAD, chest, hopper) == 2,
                    "age_9 server, no player: everything stays: " + describe(chest, hopper));
                ok(h, AgeService.simulate(server, AgeId.AGE_9, false), "server revoke age_9");
            })
            .thenWaitUntil(() -> h.assertTrue(dawn(server) && idle(server), "age_9 revoke and its swap"))
            .thenSucceed();
    }

    /** A real ProgressiveStages grant of age_9 to the opener's team: the chest keeps everything; with the gate off too. */
    @GameTest(template = "empty", batch = "firmages_6_loot_grant", timeoutTicks = 1200)
    public static void grantKeepsEverything(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer[] p = new ServerPlayer[1];
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(idle(server), "reload pending"))
            .thenExecute(() -> {
                ok(h, dawn(server), "test starts in Dawn: " + AgeService.state(server).snapshot().unlockedIds());
                p[0] = DebugPlayerCommands.spawn(server, h.getLevel(), "gt_loot_heir", Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 3))));
                p[0].setGameMode(GameType.SURVIVAL);
                if (!ProgressiveStagesAPI.grantStage(p[0], AGE_9, StageCause.API)) ProgressiveStagesAPI.grantStageBypass(p[0], AGE_9, StageCause.API);
                ok(h, AgeLoot.playerAges(p[0]).contains(AgeId.AGE_9), "the team holds age_9: " + AgeLoot.playerAges(p[0]));
                ChestBlockEntity chest = lootChest(h, new BlockPos(1, 1, 1), 42L);
                open(p[0], chest);
                ok(h, count(Items.RABBIT_HIDE, chest) == 3 && count(Items.BREAD, chest) == 2 && count(Items.TOTEM_OF_UNDYING, chest) == 1,
                    "age_9 opener: everything stays: " + describe(chest));
                boolean was = ServerConfig.GATE_LOOT_FILTER.get();
                ProgressiveStagesAPI.revokeStage(p[0], AGE_9, StageCause.API);
                ServerConfig.GATE_LOOT_FILTER.set(false);
                try {
                    ChestBlockEntity off = lootChest(h, new BlockPos(3, 1, 1), 42L);
                    open(p[0], off);
                    ok(h, count(Items.TOTEM_OF_UNDYING, off) == 1, "gate.lootFilter = false: a Dawn opener gets the totem: " + describe(off));
                } finally {
                    ServerConfig.GATE_LOOT_FILTER.set(was);
                }
            })
            .thenWaitUntil(() -> h.assertTrue(dawn(server) && idle(server), "age_9 revoke and its swap"))
            .thenExecute(() -> DebugPlayerCommands.despawn(p[0]))
            .thenSucceed();
    }

    /**
     * The gate is the last global loot modifier: with age_0 (copper) held and age_2 (iron) not, the iron chest gives
     * its 4 copper ingots, so the iron-to-copper swap ran before the gate (the other order would leave nothing).
     */
    @GameTest(template = "empty", batch = "firmages_6_loot_order", timeoutTicks = 1200)
    public static void ironSwapRunsBeforeTheGate(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(idle(server), "reload pending"))
            .thenExecute(() -> {
                ok(h, dawn(server), "test starts in Dawn: " + AgeService.state(server).snapshot().unlockedIds());
                List<IGlobalLootModifier> mods = AgeLoot.appliedOrder();
                ok(h, mods.size() >= 2 && mods.get(mods.size() - 1) instanceof AgeLootModifier
                        && mods.stream().anyMatch(m -> m instanceof ReplaceItemModifier),
                    "the age filter is the last modifier, the test swap is loaded: " + mods.stream().map(m -> m.getClass().getSimpleName()).toList());
                List<ItemStack> dawnLoot = roll(level, IRON_CHEST, null, at);
                ok(h, count(dawnLoot, Items.COPPER_INGOT) == 0 && count(dawnLoot, Items.IRON_INGOT) == 0 && count(dawnLoot, Items.BREAD) == 1,
                    "Dawn: no copper, no iron, the bread: " + dawnLoot);
                ok(h, AgeService.simulate(server, AgeId.AGE_0, true), "server grant age_0");
                List<ItemStack> loot = roll(level, IRON_CHEST, null, at);
                ok(h, count(loot, Items.COPPER_INGOT) == 4 && count(loot, Items.IRON_INGOT) == 0 && count(loot, Items.BREAD) == 1,
                    "age_0: the swapped copper stays (swap before gate): " + loot);
                ok(h, AgeService.simulate(server, AgeId.AGE_0, false), "server revoke age_0");
            })
            .thenWaitUntil(() -> h.assertTrue(dawn(server) && idle(server), "age_0 revoke and its swap"))
            .thenSucceed();
    }

    /** A shulker box (totem and hide), a bundle (totem) and loose stacks for the template chest. */
    private static void fillTemplateChest(ChestBlockEntity chest) {
        chest.setItem(0, new ItemStack(Items.RABBIT_HIDE, 3));
        chest.setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.RABBIT_HIDE))));
        chest.setItem(2, box);
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.TOTEM_OF_UNDYING))));
        chest.setItem(3, bundle);
    }

    private static ItemStack find(Container c, Item item) {
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) return c.getItem(i);
        return ItemStack.EMPTY;
    }

    private static List<Item> boxItems(ItemStack box) {
        return box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream().map(ItemStack::getItem).toList();
    }

    private static int bundleSize(ItemStack bundle) {
        return bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).size();
    }

    /**
     * A structure template holds a chest with 3 rabbit hide (Dawn), a totem (age_9), a shulker box with a totem and a
     * hide, and a bundle with a totem. Placed with the server in Dawn: the hide, the box with only its hide and the empty
     * bundle; placed with the server in age_9: everything. The template itself keeps its data.
     */
    @GameTest(template = "empty", batch = "firmages_6_loot_template", timeoutTicks = 1200)
    public static void templateContentsFollowTheServersAge(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        StructureTemplate[] template = new StructureTemplate[1];
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(idle(server), "reload pending"))
            .thenExecute(() -> {
                ok(h, dawn(server), "test starts in Dawn: " + AgeService.state(server).snapshot().unlockedIds());
                h.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
                fillTemplateChest((ChestBlockEntity) h.getBlockEntity(new BlockPos(1, 1, 1)));
                template[0] = new StructureTemplate();
                template[0].fillFromWorld(level, h.absolutePos(new BlockPos(1, 1, 1)), new Vec3i(1, 1, 1), false, null);
                long removedBefore = dev.firmages.core.loot.TemplateContents.counts()[1];
                BlockPos at = h.absolutePos(new BlockPos(3, 1, 1));
                ok(h, template[0].placeInWorld(level, at, at, new StructurePlaceSettings(), level.getRandom(), 2), "template placed");
                ChestBlockEntity placed = (ChestBlockEntity) level.getBlockEntity(at);
                ItemStack box = find(placed, Items.SHULKER_BOX);
                ItemStack bundle = find(placed, Items.BUNDLE);
                ok(h, count(Items.RABBIT_HIDE, placed) == 3 && count(Items.TOTEM_OF_UNDYING, placed) == 0,
                    "Dawn: the hide stays, the totem goes: " + describe(placed));
                ok(h, !box.isEmpty() && boxItems(box).equals(List.of(Items.RABBIT_HIDE)), "Dawn: the box keeps only its hide: " + boxItems(box));
                ok(h, !bundle.isEmpty() && bundleSize(bundle) == 0, "Dawn: the bundle stays, emptied: " + bundleSize(bundle));
                ok(h, dev.firmages.core.loot.TemplateContents.counts()[1] - removedBefore == 3, "three stacks removed (loose, in the box, in the bundle)");
                ok(h, AgeService.simulate(server, AgeId.AGE_9, true), "server grant age_9");
                BlockPos at9 = h.absolutePos(new BlockPos(5, 1, 1));
                template[0].placeInWorld(level, at9, at9, new StructurePlaceSettings(), level.getRandom(), 2);
                ChestBlockEntity all = (ChestBlockEntity) level.getBlockEntity(at9);
                ok(h, count(Items.RABBIT_HIDE, all) == 3 && count(Items.TOTEM_OF_UNDYING, all) == 1
                        && boxItems(find(all, Items.SHULKER_BOX)).equals(List.of(Items.TOTEM_OF_UNDYING, Items.RABBIT_HIDE))
                        && bundleSize(find(all, Items.BUNDLE)) == 1,
                    "age_9: everything stays, the template kept its data: " + describe(all));
                ok(h, AgeService.simulate(server, AgeId.AGE_9, false), "server revoke age_9");
            })
            .thenWaitUntil(() -> h.assertTrue(dawn(server) && idle(server), "age_9 revoke and its swap"))
            .thenSucceed();
    }

    /** The entities of a class around the block {@code at} (what a one-block template placed there). */
    private static <T extends Entity> List<T> around(ServerLevel level, Class<T> type, BlockPos at) {
        return level.getEntitiesOfClass(type, new AABB(at).inflate(0.5), Entity::isAlive);
    }

    private static String items(List<ItemEntity> items) {
        return items.stream().map(e -> e.getItem().getCount() + " " + e.getItem().getItem()).toList().toString();
    }

    /**
     * H1: a template made with {@code fillFromWorld} (entities included) from an armor stand with a golden helmet
     * (disabled) and rabbit hide in its hand, an item entity with a totem (age_9), one with 2 rabbit hide and a chest
     * minecart with a totem in slot 0 and 3 rabbit hide in slot 1, placed with {@code placeInWorld}: in Dawn the stand
     * without its helmet but with its hide, no totem item, the hide item, the minecart with only its hide (3 stacks
     * removed); in age_9 the totem item and the minecart's totem are back, the disabled helmet is not.
     */
    @GameTest(template = "empty", batch = "firmages_6_loot_template_entities", timeoutTicks = 1200)
    public static void templateEntitiesFollowTheServersAge(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        StructureTemplate template = new StructureTemplate();
        BlockPos at = h.absolutePos(new BlockPos(3, 1, 1));
        BlockPos at9 = h.absolutePos(new BlockPos(5, 1, 1));
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(idle(server), "reload pending"))
            .thenExecute(() -> {
                ok(h, dawn(server), "test starts in Dawn: " + AgeService.state(server).snapshot().unlockedIds());
                BlockPos src = h.absolutePos(new BlockPos(1, 1, 1));
                Vec3 c = Vec3.atBottomCenterOf(src);
                ArmorStand stand = new ArmorStand(level, c.x, c.y, c.z);
                stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.RABBIT_HIDE));
                ItemEntity totem = new ItemEntity(level, c.x, c.y + 0.2, c.z, new ItemStack(Items.TOTEM_OF_UNDYING));
                ItemEntity hide = new ItemEntity(level, c.x, c.y + 0.2, c.z, new ItemStack(Items.RABBIT_HIDE, 2));
                MinecartChest cart = EntityType.CHEST_MINECART.create(level);
                cart.moveTo(c.x, c.y, c.z);
                cart.setItem(0, new ItemStack(Items.TOTEM_OF_UNDYING));
                cart.setItem(1, new ItemStack(Items.RABBIT_HIDE, 3));
                List<Entity> source = List.of(stand, totem, hide, cart);
                source.forEach(level::addFreshEntity);
                template.fillFromWorld(level, src, new Vec3i(1, 2, 1), true, null);
                source.forEach(Entity::discard);

                long removedBefore = dev.firmages.core.loot.TemplateContents.counts()[1];
                ok(h, template.placeInWorld(level, at, at, new StructurePlaceSettings(), level.getRandom(), 2), "template placed");
                List<ArmorStand> stands = around(level, ArmorStand.class, at);
                List<ItemEntity> loose = around(level, ItemEntity.class, at);
                List<MinecartChest> carts = around(level, MinecartChest.class, at);
                ok(h, stands.size() == 1 && stands.get(0).getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                        && stands.get(0).getItemBySlot(EquipmentSlot.MAINHAND).is(Items.RABBIT_HIDE),
                    "Dawn: the stand keeps its hide, not the disabled helmet: " + stands.stream().map(a -> a.getItemBySlot(EquipmentSlot.HEAD)).toList());
                ok(h, loose.size() == 1 && loose.get(0).getItem().is(Items.RABBIT_HIDE) && loose.get(0).getItem().getCount() == 2,
                    "Dawn: the hide lies there, the totem item was not placed: " + items(loose));
                ok(h, carts.size() == 1 && carts.get(0).getItem(0).isEmpty() && carts.get(0).getItem(1).is(Items.RABBIT_HIDE),
                    "Dawn: the minecart keeps only its hide: " + carts.stream().map(m -> describe(m)).toList());
                ok(h, dev.firmages.core.loot.TemplateContents.counts()[1] - removedBefore == 3, "three stacks removed (helmet, totem item, minecart totem)");
                ok(h, AgeService.simulate(server, AgeId.AGE_9, true), "server grant age_9");
                template.placeInWorld(level, at9, at9, new StructurePlaceSettings(), level.getRandom(), 2);
                List<ArmorStand> stands9 = around(level, ArmorStand.class, at9);
                List<ItemEntity> loose9 = around(level, ItemEntity.class, at9);
                List<MinecartChest> carts9 = around(level, MinecartChest.class, at9);
                ok(h, stands9.size() == 1 && stands9.get(0).getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "age_9: a disabled helmet never comes back");
                ok(h, loose9.size() == 2 && loose9.stream().anyMatch(e -> e.getItem().is(Items.TOTEM_OF_UNDYING)), "age_9: the totem item is placed: " + items(loose9));
                ok(h, carts9.size() == 1 && carts9.get(0).getItem(0).is(Items.TOTEM_OF_UNDYING), "age_9: the minecart keeps its totem, the template kept its data");
                ok(h, AgeService.simulate(server, AgeId.AGE_9, false), "server revoke age_9");
            })
            .thenWaitUntil(() -> h.assertTrue(dawn(server) && idle(server), "age_9 revoke and its swap"))
            .thenExecute(() -> {
                for (BlockPos p : List.of(at, at9)) level.getEntitiesOfClass(Entity.class, new AABB(p).inflate(1.5), e -> !(e instanceof ServerPlayer)).forEach(Entity::discard);
            })
            .thenSucceed();
    }

    /**
     * H2: rolled loot keeps only what may stay inside its stacks too. In Dawn (no player, the server's Ages) the
     * nested test chest gives the shulker box with only its rabbit hide and the bundle with only its bread.
     */
    @GameTest(template = "empty", batch = "firmages_6_loot_nested", timeoutTicks = 1200)
    public static void nestedLootFollowsTheAges(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(idle(server), "reload pending"))
            .thenExecute(() -> {
                ok(h, dawn(server), "test starts in Dawn: " + AgeService.state(server).snapshot().unlockedIds());
                List<ItemStack> loot = roll(level, NESTED_CHEST, null, h.absolutePos(new BlockPos(1, 1, 1)));
                ItemStack box = loot.stream().filter(s -> s.is(Items.SHULKER_BOX)).findFirst().orElse(ItemStack.EMPTY);
                ItemStack bundle = loot.stream().filter(s -> s.is(Items.BUNDLE)).findFirst().orElse(ItemStack.EMPTY);
                ok(h, !box.isEmpty() && boxItems(box).equals(List.of(Items.RABBIT_HIDE)), "Dawn: the rolled box keeps only its hide: " + boxItems(box));
                List<Item> inBundle = bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).itemCopyStream().map(ItemStack::getItem).toList();
                ok(h, !bundle.isEmpty() && inBundle.equals(List.of(Items.BREAD)), "Dawn: the rolled bundle keeps only its bread: " + inBundle);
            })
            .thenSucceed();
    }
}
