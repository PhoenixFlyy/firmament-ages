package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.balance.OneIron;
import dev.firmages.core.compat.mekanism.ProcessorChemicals;
import dev.firmages.core.compat.sgjourney.SgjGates;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.machine.ArcaneCatalystBlock;
import dev.firmages.core.machine.ArcaneCatalystBlockEntity;
import dev.firmages.core.machine.CatalystHooks;
import dev.firmages.core.machine.MachineRegistry;
import dev.firmages.core.machine.OreProcessorBlockEntity;
import dev.firmages.core.machine.ProcessorRules;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.perf.RegrowthGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Level G tests of the end game round (SPEC §16.2a, §20.1, §34.4, §40, §41): the Ore Processor runs a test recipe
 * (test data {@code firmages_test/recipe/ore_processing/raw_iron.json}: 2 raw iron, 30 mB hydrogen, 15 mB water, 3 mB
 * lava as lubricant, 20 ticks, 3 iron nuggets) with its FE per operation the same with and without lubricant; the
 * Arcane Catalyst quickens a hopper (flat FE) and an Ore Processor (measured FE; a real Mekanism machine is measured on a server
 * copy, Mekanism's recipes are locked at Dawn here) and does nothing without
 * Source or rotation; the loot modifier and the merchant redirect; the regrowth guard; a real Stargate dial at and below
 * the configured cost.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class EndgameGameTests {
    private static final ResourceLocation HYDROGEN = ResourceLocation.parse("mekanism:hydrogen");

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(EndgameGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean condition, String message) {
        if (!condition) h.fail(message);
    }

    private static int count(OreProcessorBlockEntity be, Item item) {
        int n = 0;
        for (int i = 1; i <= OreProcessorBlockEntity.OUTPUTS; i++) if (be.stack(i).is(item)) n += be.stack(i).getCount();
        return n;
    }

    // ---------------------------------------------------------------- Ore Processor (SPEC 40)

    /** SPEC §40.4: waits for the chemical, then the fluid; the lubricated operation takes half the time, both take the same FE. */
    @GameTest(template = "empty", batch = "firmages_6_processor", timeoutTicks = 200)
    public static void processorRunsItsRecipe(GameTestHelper h) {
        if (!ModList.get().isLoaded("mekanism")) {
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos, MachineRegistry.ORE_PROCESSOR.get().defaultBlockState());
        OreProcessorBlockEntity be = (OreProcessorBlockEntity) level.getBlockEntity(pos);
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
        IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.NORTH);
        IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.DOWN);
        ok(h, items != null && fluids != null && energy != null, "item, fluid and energy handlers on the processor");
        ok(h, !items.insertItem(0, new ItemStack(Items.COBBLESTONE, 4), false).isEmpty(), "cobblestone is no ore for it");
        ok(h, items.insertItem(0, new ItemStack(Items.RAW_IRON, 4), false).isEmpty(), "four raw iron go in");
        long fe = ServerConfig.processorFePerTick();
        int[] energyAtStart = new int[1];
        h.runAfterDelay(3, () -> {
            ok(h, be.reason() == ProcessorRules.Reason.NEEDS_CHEMICAL, "waits for its chemical: " + be.reason());
            ok(h, ProcessorChemicals.insert(be, ResourceLocation.parse("mekanism:oxygen"), 100) == 0, "a chemical no recipe uses is refused");
            ok(h, ProcessorChemicals.insert(be, HYDROGEN, 60) == 60, "60 mB hydrogen go in");
        });
        h.runAfterDelay(5, () -> {
            ok(h, be.reason() == ProcessorRules.Reason.NEEDS_FLUID, "waits for its fluid: " + be.reason());
            ok(h, fluids.fill(new FluidStack(Fluids.LAVA, 3), IFluidHandler.FluidAction.EXECUTE) == 3, "3 mB lava go into the lubricant tank");
            ok(h, fluids.fill(new FluidStack(Fluids.WATER, 30), IFluidHandler.FluidAction.EXECUTE) == 30, "30 mB water go into the fluid tank");
            ok(h, fluids.drain(10, IFluidHandler.FluidAction.EXECUTE).isEmpty() && items.extractItem(0, 1, false).isEmpty()
                && energy.extractEnergy(10, false) == 0, "nothing comes back out but the results");
        });
        h.runAfterDelay(6, () -> {
            ok(h, be.reason() == ProcessorRules.Reason.NO_POWER, "waits for power: " + be.reason());
            while (energy.receiveEnergy(Integer.MAX_VALUE, false) > 0) {
                // fill
            }
            energyAtStart[0] = energy.getEnergyStored();
            ok(h, energyAtStart[0] >= 2 * fe * 20, "the buffer holds two operations: " + energyAtStart[0]);
        });
        // the first operation runs lubricated (10 ticks), the second one plain (20 ticks)
        h.runAfterDelay(19, () -> {
            ok(h, count(be, Items.IRON_NUGGET) == 3, "the lubricated operation is done after about 10 ticks: " + count(be, Items.IRON_NUGGET));
            ok(h, be.lubricant().isEmpty(), "it took the lubricant");
        });
        h.runAfterDelay(30, () -> ok(h, count(be, Items.IRON_NUGGET) == 3, "the plain operation is not done 11 ticks after it began"));
        h.runAfterDelay(45, () -> {
            ok(h, count(be, Items.IRON_NUGGET) == 6, "two operations, six nuggets: " + count(be, Items.IRON_NUGGET));
            ok(h, be.stack(0).isEmpty() && be.chemicalAmount() == 0 && be.fluid().isEmpty(), "input, chemical and fluid used up");
            long used = energyAtStart[0] - be.energy();
            ok(h, used == 2 * fe * 20, "two operations cost " + used + " FE, " + (2 * fe * 20) + " expected (lubricant buys time, not power)");
            ok(h, be.reason() == ProcessorRules.Reason.IDLE, "idle again: " + be.reason());
            h.succeed();
        });
    }

    // ---------------------------------------------------------------- Arcane Catalyst (SPEC 41)

    private static void hooks(boolean turning, boolean source) {
        CatalystHooks.rotation = (l, p) -> turning ? 128 : 0;
        CatalystHooks.source = (l, p, r, a) -> source;
    }

    private static ArcaneCatalystBlockEntity catalyst(ServerLevel level, BlockPos at, Direction facing) {
        level.setBlockAndUpdate(at, MachineRegistry.ARCANE_CATALYST.get().defaultBlockState().setValue(ArcaneCatalystBlock.FACING, facing));
        ArcaneCatalystBlockEntity be = (ArcaneCatalystBlockEntity) level.getBlockEntity(at);
        IEnergyStorage e = level.getCapability(Capabilities.EnergyStorage.BLOCK, at, facing.getOpposite());
        while (e.receiveEnergy(Integer.MAX_VALUE, false) > 0) {
            // fill
        }
        return be;
    }

    /** A hopper with 128 cobblestone above a chest: it moves one item every 8 ticks. */
    private static void hopper(ServerLevel level, BlockPos at) {
        level.setBlockAndUpdate(at.below(), Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(at, Blocks.HOPPER.defaultBlockState());
        HopperBlockEntity hb = (HopperBlockEntity) level.getBlockEntity(at);
        hb.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        hb.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
    }

    private static int moved(ServerLevel level, BlockPos hopper) {
        ChestBlockEntity c = (ChestBlockEntity) level.getBlockEntity(hopper.below());
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) n += c.getItem(i).getCount();
        return n;
    }

    /** SPEC §41.4: with Source and rotation a hopper (test tag) moves half again as much; its FE is not readable, so the catalyst pays the flat rate. */
    @GameTest(template = "empty", batch = "firmages_6_catalyst_flat", timeoutTicks = 700)
    public static void catalystQuickensAHopperAtTheFlatRate(GameTestHelper h) {
        hooks(true, true);
        ServerLevel level = h.getLevel();
        BlockPos plain = h.absolutePos(new BlockPos(1, 2, 1));
        hopper(level, plain);
        BlockPos quick = h.absolutePos(new BlockPos(4, 2, 1));
        hopper(level, quick);
        ArcaneCatalystBlockEntity cat = catalyst(level, quick.west(), Direction.EAST);
        int start = cat.energy();
        h.runAfterDelay(401, () -> {
            int a = moved(level, plain), b = moved(level, quick);
            ok(h, a >= 45 && a <= 52 && b >= a * 1.4, "400 ticks: the plain hopper moved " + a + ", the quickened one " + b);
            long extra = cat.extraTicksGiven();
            ok(h, extra >= 190 && extra <= 205, "about 200 extra ticks in 400: " + extra);
            ok(h, cat.fePaid() == extra * ServerConfig.catalystFlatFe() && start - cat.energy() == cat.fePaid(),
                "the flat rate per extra tick: paid " + cat.fePaid() + " for " + extra);
            ok(h, cat.status() == ArcaneCatalystBlockEntity.Status.QUICKENING, "status " + cat.status());
            h.succeed();
        });
    }

    private static OreProcessorBlockEntity loadedProcessor(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, MachineRegistry.ORE_PROCESSOR.get().defaultBlockState());
        OreProcessorBlockEntity be = (OreProcessorBlockEntity) level.getBlockEntity(pos);
        be.itemHandler().insertItem(0, new ItemStack(Items.RAW_IRON, 64), false);
        ProcessorChemicals.insert(be, HYDROGEN, 16_000);
        be.fluidHandler().fill(new FluidStack(Fluids.WATER, 16_000), IFluidHandler.FluidAction.EXECUTE);
        return be;
    }

    /** SPEC §41.4: the Ore Processor (test tag) quickened: half again as many operations, the catalyst pays what each extra tick drew. */
    @GameTest(template = "empty", batch = "firmages_6_catalyst_measured", timeoutTicks = 400)
    public static void catalystQuickensAProcessorAndPaysItsDraw(GameTestHelper h) {
        if (!ModList.get().isLoaded("mekanism")) {
            h.succeed();
            return;
        }
        hooks(true, true);
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(1, 1, 1));
        BlockPos b = h.absolutePos(new BlockPos(5, 1, 1));
        OreProcessorBlockEntity plain = loadedProcessor(level, a);
        OreProcessorBlockEntity quick = loadedProcessor(level, b);
        ArcaneCatalystBlockEntity cat = catalyst(level, b.west(), Direction.EAST);
        h.onEachTick(() -> {
            plain.energyHandler().receiveEnergy(Integer.MAX_VALUE, false);
            quick.energyHandler().receiveEnergy(Integer.MAX_VALUE, false);
            cat.energyHandler().receiveEnergy(Integer.MAX_VALUE, false);
        });
        h.runAfterDelay(201, () -> {
            int na = count(plain, Items.IRON_NUGGET), nb = count(quick, Items.IRON_NUGGET);
            long extra = cat.extraTicksGiven();
            long fe = ServerConfig.processorFePerTick();
            FirmagesCore.LOGGER.info("GameTest catalyst on an Ore Processor: plain {} nuggets, quickened {}, {} extra ticks, {} FE paid", na, nb, extra, cat.fePaid());
            ok(h, na >= 27 && na <= 30 && nb >= 42, "200 ticks: plain " + na + " nuggets (10 operations), quickened " + nb + " (15 expected)");
            ok(h, extra >= 95 && extra <= 102, "about 100 extra ticks: " + extra);
            ok(h, cat.fePaid() == extra * fe, "paid what the processor drew in each extra tick (" + fe + " FE): " + cat.fePaid() + " for " + extra);
            h.succeed();
        });
    }

    /** SPEC §41.4: no Source, no rotation, no machine: no extra tick. */
    @GameTest(template = "empty", batch = "firmages_6_catalyst_idle", timeoutTicks = 200)
    public static void catalystNeedsSourceAndRotation(GameTestHelper h) {
        hooks(true, false);
        ServerLevel level = h.getLevel();
        BlockPos f1 = h.absolutePos(new BlockPos(2, 2, 1));
        hopper(level, f1);
        ArcaneCatalystBlockEntity noSource = catalyst(level, f1.west(), Direction.EAST);
        BlockPos f2 = h.absolutePos(new BlockPos(2, 2, 4));
        hopper(level, f2);
        ArcaneCatalystBlockEntity facingAway = catalyst(level, f2.west(), Direction.WEST);
        h.runAfterDelay(50, () -> {
            ok(h, noSource.status() == ArcaneCatalystBlockEntity.Status.NO_SOURCE && noSource.extraTicksGiven() == 0,
                "without Source: " + noSource.status() + ", " + noSource.extraTicksGiven() + " extra ticks");
            ok(h, facingAway.status() == ArcaneCatalystBlockEntity.Status.NO_MACHINE && facingAway.extraTicksGiven() == 0,
                "facing no machine: " + facingAway.status());
            hooks(false, true);
        });
        h.runAfterDelay(100, () -> {
            ok(h, noSource.status() == ArcaneCatalystBlockEntity.Status.NO_ROTATION && noSource.extraTicksGiven() == 0,
                "without rotation: " + noSource.status() + ", " + noSource.extraTicksGiven() + " extra ticks");
            hooks(true, true);
            h.succeed();
        });
    }

    // ---------------------------------------------------------------- One Iron (SPEC 34.4)

    /**
     * Test data turns iron ingots of every loot table into copper ingots (the pack: wrought iron); other loot stays.
     * The loot Age gate (SPEC 19.5) is off for this roll: copper is age_0 in the test tags, and the world is in Dawn
     * ({@code LootGameTests.ironSwapRunsBeforeTheGate} rolls the same table with the gate on).
     */
    @GameTest(template = "empty", batch = "firmages_6_one_iron", timeoutTicks = 40)
    public static void lootAndTradesLoseTheVanillaIngot(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        LootTable t = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.parse("firmages_test:iron_chest")));
        boolean gate = dev.firmages.core.config.ServerConfig.GATE_LOOT_FILTER.get();
        dev.firmages.core.config.ServerConfig.GATE_LOOT_FILTER.set(false);
        List<ItemStack> loot;
        try {
            loot = t.getRandomItems(new LootParams.Builder(level).create(LootContextParamSets.EMPTY));
        } finally {
            dev.firmages.core.config.ServerConfig.GATE_LOOT_FILTER.set(gate);
        }
        int copper = loot.stream().filter(s -> s.is(Items.COPPER_INGOT)).mapToInt(ItemStack::getCount).sum();
        ok(h, copper == 4 && loot.stream().noneMatch(s -> s.is(Items.IRON_INGOT)) && loot.stream().anyMatch(s -> s.is(Items.BREAD)),
            "the chest gives 4 copper ingots, no iron, and its bread: " + loot);
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.IRON_INGOT, 4), Optional.empty(), new ItemStack(Items.EMERALD), 2, 12, 5, 0.05F, 0));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), Optional.empty(), new ItemStack(Items.IRON_INGOT, 2), 0, 12, 5, 0.05F, 0));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), Optional.empty(), new ItemStack(Items.BREAD, 2), 0, 12, 5, 0.05F, 0));
        int changed = OneIron.redirect(offers, Map.of(Items.IRON_INGOT, Items.COPPER_INGOT));
        ok(h, changed == 2, "two offers rewritten: " + changed);
        ok(h, offers.get(0).getItemCostA().item().value() == Items.COPPER_INGOT && offers.get(0).getItemCostA().count() == 4
            && offers.get(0).getUses() == 2, "the buy offer asks 4 copper and keeps its uses: " + OneIron.describe(offers));
        ok(h, offers.get(1).getResult().is(Items.COPPER_INGOT) && offers.get(1).getResult().getCount() == 2, "the sell offer gives 2 copper");
        ok(h, offers.get(2).getResult().is(Items.BREAD), "the bread offer stays");
        h.succeed();
    }

    // ---------------------------------------------------------------- regrowth guard (SPEC 20.1)

    @GameTest(template = "empty", batch = "firmages_6_regrowth", timeoutTicks = 40)
    public static void regrowthLooksOnlyIntoLoadedChunks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos here = h.absolutePos(new BlockPos(1, 1, 1));
        RegrowthGuard.reset();
        ok(h, RegrowthGuard.mayLook(level, here.getX(), here.getZ()), "the test's own chunk is loaded");
        int far = 29_000_000;
        ok(h, !level.hasChunk(far >> 4, far >> 4) && !RegrowthGuard.mayLook(level, far, far), "a column 29 million blocks out is skipped");
        ok(h, !level.hasChunk(far >> 4, far >> 4), "and its chunk was not loaded by the look");
        long[] n = RegrowthGuard.counts();
        ok(h, n[0] == 1 && n[1] == 1, "one look, one skip: " + n[0] + ", " + n[1]);
        h.succeed();
    }

    // ---------------------------------------------------------------- Stargate cost (SPEC 16.2a)

    /** A real dial of The Origin: one FE below the interstellar cost fails, the cost connects and is spent; the Origin's gate keeps a trip home. */
    @GameTest(template = "empty", batch = "firmages_6_sgj_cost", timeoutTicks = 200)
    public static void dialingTheOriginCostsTheConfiguredFe(GameTestHelper h) {
        if (!ModList.get().isLoaded("sgjourney")) {
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        BlockPos at = h.absolutePos(new BlockPos(1, 0, 1));
        long cost = SgjGates.costs()[1];
        List<String> lines = SgjGates.trial(level, at, OriginArena.GATE_TEMPLATE, new BlockPos(3, 2, 9), new int[] {9, 16, 21, 33, 2, 37},
            dev.firmages.core.origin.OriginService.level(level.getServer()), OriginArena.gateBase());
        FirmagesCore.LOGGER.info("GameTest stargate cost {} FE: {}", cost, lines);
        ok(h, lines.size() == 2, "two dials: " + lines);
        ok(h, lines.get(0).contains("NOT_ENOUGH_POWER"), "one FE short: " + lines.get(0));
        ok(h, lines.get(1).contains("CONNECTION_ESTABLISHED"), "at the cost: " + lines.get(1));
        long[] e = SgjGates.energy(level, at.offset(3, 2, 9));
        ok(h, e != null && e[0] < cost, "the dial spent the cost: " + (e == null ? "no gate" : e[0] + " FE left"));
        ok(h, SgjGates.keepCharged(level, at.offset(3, 2, 9)) > 0 && SgjGates.energy(level, at.offset(3, 2, 9))[0]
            == Math.min(SgjGates.homeTrip(), e[1]), "keepCharged fills a gate to one trip home");
        h.succeed();
    }
}
