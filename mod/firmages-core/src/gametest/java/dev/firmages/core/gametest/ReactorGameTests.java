package dev.firmages.core.gametest;

import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorCore;
import com.brandon3055.draconicevolution.blocks.reactor.tileentity.TileReactorStabilizer;
import com.brandon3055.draconicevolution.init.DEContent;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.compat.draconic.DraconicReactors;
import dev.firmages.core.reactor.ReactorControllerBlock;
import dev.firmages.core.reactor.ReactorControllerBlockEntity;
import dev.firmages.core.reactor.ReactorCycle;
import dev.firmages.core.reactor.ReactorFuel;
import dev.firmages.core.reactor.ReactorItems;
import dev.firmages.core.reactor.ReactorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Level G tests of m4 (SPEC §6, §12) against a real Draconic Evolution 3.1.4 reactor: a core with four
 * stabilizers formed in the test area, the controller on top of one stabilizer, a chest of nuggets on top of the
 * controller. The reactor is never charged: the test sets the RUNNING state and the burnt fuel directly and lets
 * DE's own STOPPING -> COOLING -> COLD path run (temperature 20, so it never heats or explodes).
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class ReactorGameTests {
    private static final BlockPos CORE = new BlockPos(7, 3, 7);
    private static final BlockPos STAB_WEST = CORE.west(5);
    private static final BlockPos CONTROLLER = STAB_WEST.above();
    private static final BlockPos CHEST = CONTROLLER.above();

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(ReactorGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean cond, String what) {
        if (!cond) h.fail(what);
    }

    private static TileReactorCore formReactor(GameTestHelper h) {
        h.setBlock(CORE, DEContent.REACTOR_CORE.get());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = CORE.relative(d, 5);
            h.setBlock(p, DEContent.REACTOR_STABILIZER.get());
            TileReactorStabilizer s = (TileReactorStabilizer) h.getBlockEntity(p);
            s.facing.set(d.getOpposite()); // facing the core
        }
        TileReactorCore core = (TileReactorCore) h.getBlockEntity(CORE);
        core.attemptInitialization();
        return core;
    }

    @GameTest(template = "reactor_area", batch = "firmages_4_reactor", timeoutTicks = 600)
    public static void controllerCycleOnRealReactor(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ok(h, DraconicReactors.enabled(), "Draconic coupling enabled at common setup");
        TileReactorCore core = formReactor(h);
        ok(h, core.structureValid.get() && core.reactorState.get() == TileReactorCore.ReactorState.COLD,
            "reactor formed and COLD (state " + core.reactorState.get() + ", error " + core.structureError.get() + ")");

        h.setBlock(CONTROLLER, ReactorRegistry.REACTOR_CONTROLLER.get());
        h.setBlock(CHEST, Blocks.CHEST);
        ((ChestBlockEntity) h.getBlockEntity(CHEST)).setItem(0, new ItemStack(DEContent.NUGGET_DRACONIUM_AWAKENED.get(), 64));
        ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) h.getBlockEntity(CONTROLLER);
        BlockPos abs = h.absolutePos(CONTROLLER);

        // Pipe view: fuel only into slot 0, nothing out of slot 0, nothing into the output slots.
        IItemHandler pipe = level.getCapability(Capabilities.ItemHandler.BLOCK, abs, Direction.UP);
        ok(h, pipe != null, "controller exposes an item handler");
        ok(h, pipe.insertItem(0, new ItemStack(Items.STONE, 4), false).getCount() == 4, "stone refused");
        ItemStack blocks = new ItemStack(DEContent.AWAKENED_DRACONIUM_BLOCK.get(), 16);
        ok(h, pipe.insertItem(0, blocks, false).isEmpty(), "16 awakened draconium blocks accepted");
        ok(h, pipe.extractItem(0, 1, true).isEmpty(), "fuel slot cannot be emptied by pipes");
        ok(h, pipe.insertItem(1, new ItemStack(DEContent.AWAKENED_DRACONIUM_BLOCK.get()), true).getCount() == 1, "output slot takes nothing");

        // A cold, empty reactor is refuelled at once and reported READY.
        be.step();
        ok(h, be.cycle().state() == ReactorCycle.State.READY, "READY after the first swap, was " + be.cycle().state() + " (" + be.cycle().detail() + ")");
        ok(h, core.reactableFuel.get() == 10368, "fuel 10368, was " + core.reactableFuel.get());
        ok(h, be.items().getStackInSlot(0).getCount() == 8, "8 blocks used");
        BlockState st = level.getBlockState(abs);
        ok(h, st.getValue(ReactorControllerBlock.READY) && st.getSignal(level, abs, Direction.NORTH) == 15, "redstone 15 while READY");
        ok(h, st.getAnalogOutputSignal(level, abs) == 15, "comparator 15 for a full reactor");

        // m4 port on the east stabilizer while COLD and full: no room for an ingot, no chaos to take.
        BlockPos stabEast = h.absolutePos(CORE.east(5));
        IItemHandler port = level.getCapability(Capabilities.ItemHandler.BLOCK, stabEast, null);
        ok(h, port != null, "stabilizer exposes the fuel port");
        ok(h, port.insertItem(0, new ItemStack(DEContent.INGOT_DRACONIUM_AWAKENED.get()), false).getCount() == 1, "full reactor takes no ingot");
        ok(h, port.getStackInSlot(1).isEmpty(), "no chaos to extract");

        // The reactor "ran" to 80 % conversion: the controller sees RUNNING, then shuts it down.
        core.reactableFuel.set(2073.6);
        core.convertedFuel.set(8294.4);
        core.reactorState.set(TileReactorCore.ReactorState.RUNNING);
        ok(h, port.insertItem(0, new ItemStack(DEContent.INGOT_DRACONIUM_AWAKENED.get()), false).getCount() == 1, "port refuses fuel while RUNNING");
        ok(h, port.extractItem(1, 64, false).isEmpty(), "port gives no chaos while RUNNING");
        be.step();
        ok(h, be.cycle().state() == ReactorCycle.State.MONITOR && !level.getBlockState(abs).getValue(ReactorControllerBlock.READY), "MONITOR and no signal once started");
        be.step();
        ok(h, be.cycle().state() == ReactorCycle.State.SHUTDOWN, "SHUTDOWN at 80 %, was " + be.cycle().state());
        ok(h, core.reactorState.get() == TileReactorCore.ReactorState.STOPPING, "DE accepted the shutdown, state " + core.reactorState.get());

        h.startSequence()
            .thenWaitUntil(() -> {
                if (core.reactorState.get() != TileReactorCore.ReactorState.COLD) h.fail("waiting for COLD, " + core.reactorState.get());
                if (be.cycle().state() != ReactorCycle.State.READY) h.fail("waiting for READY, " + be.cycle().state() + " (" + be.cycle().detail() + ")");
            })
            .thenExecute(() -> {
                ok(h, core.convertedFuel.get() < ReactorFuel.NUGGET, "chaos removed down to the rest, " + core.convertedFuel.get());
                ok(h, ReactorFuel.full(core.reactableFuel.get(), core.convertedFuel.get()), "refuelled until nothing fits, fuel " + core.reactableFuel.get());
                ok(h, be.items().getStackInSlot(0).getCount() == 2, "6 more blocks from slot 0, left " + be.items().getStackInSlot(0).getCount());
                ok(h, ((ChestBlockEntity) h.getBlockEntity(CHEST)).getItem(0).getCount() == 32, "32 nuggets pulled from the chest");
                ok(h, count(be, ReactorFuel.CHAOS_LARGE) == 6 && count(be, ReactorFuel.CHAOS_MEDIUM) == 3 && count(be, ReactorFuel.CHAOS_SMALL) == 5,
                    "chaos out as 6 large, 3 medium, 5 small fragments");
                ok(h, core.reactorState.get() == TileReactorCore.ReactorState.COLD, "the controller never starts the reactor");
                ok(h, level.getBlockState(abs).getValue(ReactorControllerBlock.READY), "READY signal again");
                ItemStack out = pipe.extractItem(1, 64, false);
                ok(h, ReactorItems.id(out).equals(ReactorFuel.CHAOS_LARGE) && out.getCount() == 6, "pipes extract the chaos fragments");
                ok(h, be.statusLines().get(0).contains("READY"), "status line names the state");
                ok(h, command(level, "firmages reactor status") >= 1, "/firmages reactor status lists the controller");

                // m4 port while COLD: fuel in by item value, chaos out largest first.
                core.reactableFuel.set(10000);
                core.convertedFuel.set(306.4);
                ok(h, port.insertItem(0, new ItemStack(DEContent.INGOT_DRACONIUM_AWAKENED.get(), 4), false).getCount() == 4, "ingots refused: no room");
                core.convertedFuel.set(0);
                ItemStack rest = port.insertItem(0, new ItemStack(DEContent.INGOT_DRACONIUM_AWAKENED.get(), 4), false);
                ok(h, rest.getCount() == 2 && core.reactableFuel.get() == 10288, "2 ingots fit (room 383), fuel " + core.reactableFuel.get());
                core.convertedFuel.set(306.4);
                ok(h, port.insertItem(0, new ItemStack(Items.STONE), false).getCount() == 1, "port refuses other items");
                ItemStack medium = port.extractItem(1, 64, false);
                ok(h, ReactorItems.id(medium).equals(ReactorFuel.CHAOS_MEDIUM) && medium.getCount() == 2, "2 medium fragments first");
                ItemStack small = port.extractItem(1, 64, false);
                ok(h, ReactorItems.id(small).equals(ReactorFuel.CHAOS_SMALL) && small.getCount() == 1, "then 1 small fragment");
                ok(h, port.extractItem(1, 64, false).isEmpty() && Math.abs(core.convertedFuel.get() - 2.4) < 1e-6, "a rest below 16 stays");
            })
            .thenSucceed();
    }

    private static int command(ServerLevel level, String cmd) {
        try {
            return level.getServer().getCommands().getDispatcher().execute(cmd, level.getServer().createCommandSourceStack().withSuppressedOutput());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return -1;
        }
    }

    private static int count(ReactorControllerBlockEntity be, String id) {
        int n = 0;
        for (int i = ReactorControllerBlockEntity.FIRST_OUT; i < ReactorControllerBlockEntity.SLOTS; i++) {
            ItemStack s = be.items().getStackInSlot(i);
            if (ReactorItems.id(s).equals(id)) n += s.getCount();
        }
        return n;
    }
}
