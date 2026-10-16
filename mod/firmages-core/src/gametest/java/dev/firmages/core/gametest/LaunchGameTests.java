package dev.firmages.core.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.launch.LaunchControlBlock;
import dev.firmages.core.launch.LaunchControlBlockEntity;
import dev.firmages.core.launch.LaunchData;
import dev.firmages.core.launch.LaunchDataLoader;
import dev.firmages.core.launch.LaunchRegistry;
import dev.firmages.core.launch.LaunchRules;
import dev.firmages.core.launch.LaunchService;
import dev.firmages.core.launch.Mission;
import dev.firmages.core.launch.TrenchShape;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Level G tests of the Launch Complex (SPEC §31.10). The complex stands in the empty 15 x 27 x 15 template
 * {@code launch_area}, built from the vanilla stand-ins of the {@code firmages_test:launch/*} tags (smooth stone pad,
 * iron block gantry, brick trench); the cradle is at (7,6,7), the control three blocks east at (10,6,7), one port on
 * the pad's north rim, the flame trench opens west. Water is the test copy of {@code #ad_astra:fuel}. The launch data
 * of these tests is the shipped data with short flights: launch 1000 mB, sampler work 400, deep-space work 800.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class LaunchGameTests {
    private static final String BATCH = "firmages_12_launch";
    private static final BlockPos CRADLE = new BlockPos(7, 6, 7);
    private static final BlockPos CONTROL = CRADLE.east(LaunchService.CRADLE_DISTANCE);
    private static final BlockPos PORT = CRADLE.north(2);
    /** The free cell behind the control (its back face). */
    private static final BlockPos BEHIND = CONTROL.east();
    private static final int LAUNCH_MB = 1000;
    private static final int WORK = 400;

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(LaunchGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean condition, String message) {
        if (!condition) FirmagesCore.LOGGER.error("Launch GameTest assertion failed: {}", message);
        h.assertTrue(condition, message);
    }

    private static JsonElement json(String s) {
        return JsonParser.parseString(s);
    }

    /** The shipped files with short flights (the same object for every test of the batch, so parallel tests agree). */
    private static LaunchData testData(String samplerReturn) {
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        files.put(ResourceLocation.fromNamespaceAndPath("firmages", "fuel"), json("{\"fluid_tag\": \"ad_astra:fuel\", \"launch_mb\": " + LAUNCH_MB
            + ", \"throttle\": [{\"burn\": 1, \"progress\": 1}, {\"burn\": 3, \"progress\": 2}, {\"burn\": 8, \"progress\": 4}]}"));
        files.put(ResourceLocation.fromNamespaceAndPath("firmages", "module/sampler"), json("{\"module\": \"firmages:sampler_module\", \"age\": \"age_7\", "
            + "\"flight_work\": " + WORK + ", \"returns\": [{\"item\": \"" + samplerReturn + "\", \"count\": 4}]}"));
        files.put(ResourceLocation.fromNamespaceAndPath("firmages", "module/deep_space"), json("{\"module\": \"firmages:deep_space_module\", "
            + "\"age\": \"age_8\", \"flight_work\": 800, \"returns\": [{\"item\": \"firmages:deep_space_sample\", \"count\": 2}, "
            + "{\"item\": \"firmages:voidglass\", \"count\": 2}]}"));
        return LaunchDataLoader.build(files);
    }

    private static LaunchData shortFlights;

    private static void useTestData() {
        if (shortFlights == null || LaunchDataLoader.current() != shortFlights) {
            if (shortFlights == null) shortFlights = testData("firmages:voidglass");
            LaunchDataLoader.set(shortFlights);
        }
    }

    /** The complex without its control: pad, rim, port, gantry, trench. */
    private static void buildComplex(GameTestHelper h) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) h.setBlock(CRADLE.offset(dx, 0, dz), Blocks.SMOOTH_STONE);
        }
        h.setBlock(CRADLE, LaunchRegistry.PROBE_CRADLE.get());
        h.setBlock(PORT, LaunchRegistry.LAUNCH_PORT.get());
        for (int y = 0; y < 20; y++) {
            for (int[] c : new int[][] {{3, -1}, {3, 1}, {5, -1}, {5, 1}}) h.setBlock(CRADLE.offset(c[0], y, c[1]), Blocks.IRON_BLOCK);
        }
        for (int[] c : new int[][] {{4, -1}, {4, 1}, {3, 0}, {5, 0}}) h.setBlock(CRADLE.offset(c[0], 19, c[1]), Blocks.IRON_BLOCK);
        for (TrenchShape.Cell cell : TrenchShape.CELLS) {
            if (cell.kind() == TrenchShape.Kind.WALL) h.setBlock(LaunchService.trenchPos(CRADLE, Direction.WEST, cell), Blocks.BRICKS);
        }
    }

    private static LaunchControlBlockEntity placeControl(GameTestHelper h) {
        h.setBlock(CONTROL, LaunchRegistry.LAUNCH_CONTROL.get().defaultBlockState().setValue(LaunchControlBlock.FACING, Direction.WEST));
        return control(h);
    }

    private static LaunchControlBlockEntity control(GameTestHelper h) {
        return (LaunchControlBlockEntity) h.getBlockEntity(CONTROL);
    }

    private static void load(LaunchControlBlockEntity be, int water, int modules) {
        if (water > 0) be.automationFluid().fill(new FluidStack(Fluids.WATER, water), IFluidHandler.FluidAction.EXECUTE);
        be.automationItems().insertItem(LaunchControlBlockEntity.PROBE_SLOT, new ItemStack(LaunchRegistry.PROBE.get()), false);
        if (modules > 0) be.automationItems().insertItem(LaunchControlBlockEntity.MODULE_SLOT, new ItemStack(LaunchRegistry.SAMPLER_MODULE.get(), modules), false);
    }

    private static int outputCount(LaunchControlBlockEntity be, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = LaunchControlBlockEntity.OUT_FIRST; i < LaunchControlBlockEntity.SLOTS; i++) {
            if (be.stack(i).is(item)) n += be.stack(i).getCount();
        }
        return n;
    }

    private static LaunchService.Complex check(GameTestHelper h) {
        return LaunchService.check(h.getLevel(), h.absolutePos(CONTROL));
    }

    /**
     * The test multiblock validates with the control at the tower's foot, one port and the trench open west; a blocked
     * trench, no port or five ports fail; pipes and a chest on free cells change nothing.
     */
    @GameTest(template = "launch_area", batch = BATCH, skyAccess = true, timeoutTicks = 200)
    public static void complexValidates(GameTestHelper h) {
        useTestData();
        buildComplex(h);
        placeControl(h);
        LaunchService.Complex c = check(h);
        ok(h, c.ring() != null && c.ring().known(), "multiblock " + LaunchService.COMPLEX_ID + " loaded");
        ok(h, c.complete(), "complete: " + c.problem() + ", ring " + (c.ring() == null ? "-" : c.ring().missing()) + ", trench failure " + c.trenchFailure());
        ok(h, c.ring().total() == dev.firmages.core.command.LaunchSuite.COUNTED, "counted positions: " + c.ring().total());
        ok(h, c.trench() == Direction.WEST && c.ports().size() == 1, "trench west, one port: " + c.trench() + " " + c.ports());
        // pipes and storage on free cells
        h.setBlock(CRADLE.offset(-4, 0, 0), Blocks.CHEST);
        h.setBlock(CRADLE.offset(4, 0, 0), Blocks.HOPPER);
        h.setBlock(CRADLE.offset(0, 0, 4), Blocks.COBBLESTONE_WALL);
        ok(h, check(h).complete(), "free cells take anything: " + check(h).problem());
        // the trench
        BlockPos channel = LaunchService.trenchPos(CRADLE, Direction.WEST, new TrenchShape.Cell(1, 0, 2, TrenchShape.Kind.CHANNEL));
        h.setBlock(channel, Blocks.STONE);
        ok(h, check(h).problem() == LaunchRules.Wait.TRENCH, "a blocked channel: " + check(h).problem());
        h.setBlock(channel, Blocks.AIR);
        BlockPos mouth = LaunchService.trenchPos(CRADLE, Direction.WEST, new TrenchShape.Cell(TrenchShape.REACH + 1, 1, 1, TrenchShape.Kind.OPENING));
        h.setBlock(mouth, Blocks.DIRT);
        ok(h, check(h).problem() == LaunchRules.Wait.TRENCH, "a closed mouth: " + check(h).problem());
        h.setBlock(mouth, Blocks.AIR);
        BlockPos wall = LaunchService.trenchPos(CRADLE, Direction.WEST, new TrenchShape.Cell(0, -2, 3, TrenchShape.Kind.WALL));
        h.setBlock(wall, Blocks.DIRT);
        ok(h, check(h).problem() == LaunchRules.Wait.TRENCH, "a wall of the wrong block: " + check(h).problem());
        h.setBlock(wall, Blocks.BRICKS);
        ok(h, check(h).complete(), "the trench restored");
        // ports
        h.setBlock(PORT, Blocks.SMOOTH_STONE);
        ok(h, check(h).problem() == LaunchRules.Wait.PORTS, "no port: " + check(h).problem());
        for (BlockPos p : List.of(CRADLE.offset(-2, 0, -2), CRADLE.offset(-1, 0, -2), CRADLE.offset(0, 0, -2), CRADLE.offset(1, 0, -2),
            CRADLE.offset(2, 0, -2))) {
            h.setBlock(p, LaunchRegistry.LAUNCH_PORT.get());
        }
        ok(h, check(h).problem() == LaunchRules.Wait.PORTS && check(h).ports().size() == 5, "five ports: " + check(h).ports().size());
        h.setBlock(CRADLE.offset(-2, 0, -2), Blocks.SMOOTH_STONE);
        ok(h, check(h).complete() && check(h).ports().size() == 4, "four ports");
        // the gantry
        h.setBlock(CRADLE.offset(5, 10, 1), Blocks.AIR);
        ok(h, check(h).problem() == LaunchRules.Wait.UNFORMED, "a missing gantry block: " + check(h).problem());
        h.succeed();
    }

    /**
     * Water in the test fuel tag: the countdown takes nothing, ignition takes the module and launch_mb, the flight burns
     * one mB per tick at step one and pauses on an empty tank without losing progress, and the probe comes home with
     * exactly four voidglass into the output and back into its slot.
     */
    @GameTest(template = "launch_area", batch = BATCH, skyAccess = true, timeoutTicks = 1800)
    public static void missionRunsOnFuel(GameTestHelper h) {
        useTestData();
        buildComplex(h);
        LaunchControlBlockEntity be = placeControl(h);
        load(be, LAUNCH_MB + 200, 1);
        long[] pausedAt = new long[1];
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(be.phase() == LaunchRules.Phase.COUNTDOWN, "countdown: " + be.waitReason()))
            .thenExecute(() -> ok(h, be.fluid().getAmount() == LAUNCH_MB + 200 && !be.stack(LaunchControlBlockEntity.PROBE_SLOT).isEmpty()
                && be.stack(LaunchControlBlockEntity.MODULE_SLOT).getCount() == 1, "the countdown takes nothing"))
            .thenWaitUntil(() -> h.assertTrue(be.phase() == LaunchRules.Phase.ASCENT, "ascent"))
            .thenExecute(() -> ok(h, be.fluid().getAmount() == 200 && be.stack(LaunchControlBlockEntity.PROBE_SLOT).isEmpty()
                && be.stack(LaunchControlBlockEntity.MODULE_SLOT).isEmpty(), "ignition took launch_mb, the probe and the module: " + be.fluid().getAmount()))
            .thenWaitUntil(() -> h.assertTrue(be.waitReason() == LaunchRules.Wait.FUEL_EMPTY, "the flight waits on fuel"))
            .thenExecute(() -> {
                Mission m = be.mission();
                ok(h, m != null && m.phase() == LaunchRules.Phase.FLIGHT && m.progress() == 200 && be.fluid().isEmpty(),
                    "200 mB fly 200 progress at step one: " + (m == null ? "-" : m.progress()));
                pausedAt[0] = m.progress();
            })
            .thenIdle(40)
            .thenExecute(() -> {
                ok(h, be.mission() != null && be.mission().progress() == pausedAt[0], "no progress without fuel");
                be.automationFluid().fill(new FluidStack(Fluids.WATER, 300), IFluidHandler.FluidAction.EXECUTE);
            })
            .thenWaitUntil(() -> h.assertTrue(be.mission() == null, "the probe is home"))
            .thenExecute(() -> {
                ok(h, outputCount(be, LaunchRegistry.VOIDGLASS.get()) == 4, "four voidglass: " + be.slots());
                ok(h, be.stack(LaunchControlBlockEntity.PROBE_SLOT).is(LaunchRegistry.PROBE.get()), "the probe is back in its slot");
                ok(h, be.fluid().getAmount() == 100, "200 more mB burnt: " + be.fluid().getAmount());
                int others = 0;
                for (int i = LaunchControlBlockEntity.OUT_FIRST; i < LaunchControlBlockEntity.SLOTS; i++) {
                    if (!be.stack(i).isEmpty() && !be.stack(i).is(LaunchRegistry.VOIDGLASS.get())) others++;
                }
                ok(h, others == 0, "nothing else comes back: " + be.slots());
            })
            .thenIdle(20)
            .thenExecute(() -> ok(h, be.phase() == LaunchRules.Phase.IDLE && be.waitReason() == LaunchRules.Wait.NO_MODULE, "no module, no relaunch"))
            .thenSucceed();
    }

    /** Redstone 15 on the back selects step three: 400 work in 100 ticks for 800 mB (step one: 400 ticks, 400 mB). */
    @GameTest(template = "launch_area", batch = BATCH, skyAccess = true, timeoutTicks = 1200)
    public static void throttleShortensFlight(GameTestHelper h) {
        useTestData();
        buildComplex(h);
        h.setBlock(BEHIND, Blocks.REDSTONE_BLOCK);
        LaunchControlBlockEntity be = placeControl(h);
        load(be, LAUNCH_MB + 2000, 1);
        long[] flightStart = new long[2];
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(be.phase() == LaunchRules.Phase.FLIGHT, "flight"))
            .thenIdle(1) // the tick that enters FLIGHT still reads the throttle of the climb (no back signal while it emits)
            .thenExecute(() -> {
                flightStart[0] = h.getLevel().getGameTime() - 1;
                flightStart[1] = be.fluid().getAmount() + 8L;
                ok(h, be.step() == 3 && be.redstoneThrottle(), "redstone 15 is step three: " + be.step());
            })
            .thenWaitUntil(() -> h.assertTrue(be.phase() == LaunchRules.Phase.DESCENT, "descent"))
            .thenExecute(() -> {
                long ticks = h.getLevel().getGameTime() - flightStart[0];
                long burnt = flightStart[1] - be.fluid().getAmount();
                ok(h, ticks >= 98 && ticks <= 104, "100 ticks of flight at step three: " + ticks);
                ok(h, burnt >= 784 && burnt <= 808, "8 mB per tick: " + burnt);
            })
            .thenSucceed();
    }

    /** A block over the cradle closes the sky: the control waits and says so; once it is gone the countdown begins. */
    @GameTest(template = "launch_area", batch = BATCH, skyAccess = true, timeoutTicks = 300)
    public static void closedSkyWaits(GameTestHelper h) {
        useTestData();
        buildComplex(h);
        BlockPos roof = CRADLE.above(12);
        h.setBlock(roof, Blocks.STONE);
        LaunchControlBlockEntity be = placeControl(h);
        load(be, LAUNCH_MB, 1);
        h.startSequence()
            .thenIdle(40)
            .thenExecute(() -> {
                ok(h, be.phase() == LaunchRules.Phase.IDLE && be.waitReason() == LaunchRules.Wait.SKY_CLOSED, "waits under a roof: " + be.waitReason());
                h.setBlock(roof, Blocks.AIR);
            })
            .thenWaitUntil(() -> h.assertTrue(be.phase() == LaunchRules.Phase.COUNTDOWN, "countdown under the open sky"))
            .thenSucceed();
    }

    /** During the countdown and the climb a lamp behind the control lights (the silo hatch); in flight it goes out. */
    @GameTest(template = "launch_area", batch = BATCH, skyAccess = true, timeoutTicks = 800)
    public static void countdownEmitsRedstone(GameTestHelper h) {
        useTestData();
        buildComplex(h);
        h.setBlock(BEHIND, Blocks.REDSTONE_LAMP);
        LaunchControlBlockEntity be = placeControl(h);
        load(be, LAUNCH_MB + 1000, 1);
        h.startSequence()
            .thenIdle(5)
            .thenExecute(() -> ok(h, !h.getBlockState(BEHIND).getValue(RedstoneLampBlock.LIT) || be.phase() == LaunchRules.Phase.COUNTDOWN, "dark before"))
            .thenWaitUntil(() -> h.assertTrue(be.phase() == LaunchRules.Phase.COUNTDOWN, "countdown"))
            .thenWaitUntil(() -> h.assertTrue(h.getBlockState(BEHIND).getValue(RedstoneLampBlock.LIT), "the lamp lights in the countdown"))
            .thenWaitUntil(() -> h.assertTrue(be.phase() == LaunchRules.Phase.ASCENT, "ascent"))
            .thenExecute(() -> ok(h, h.getBlockState(BEHIND).getValue(RedstoneLampBlock.LIT), "still lit in the climb"))
            .thenWaitUntil(() -> h.assertTrue(be.phase() == LaunchRules.Phase.FLIGHT, "flight"))
            .thenWaitUntil(() -> h.assertTrue(!h.getBlockState(BEHIND).getValue(RedstoneLampBlock.LIT), "the lamp goes out in flight"))
            .thenSucceed();
    }

    /** Save and load keep the phase, the progress, the slots, the tank and the owner. */
    @GameTest(template = "launch_area", batch = BATCH, skyAccess = true, timeoutTicks = 800)
    public static void missionSurvivesSave(GameTestHelper h) {
        useTestData();
        buildComplex(h);
        LaunchControlBlockEntity be = placeControl(h);
        be.setOwner(java.util.UUID.nameUUIDFromBytes("gt_launch_save".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        load(be, LAUNCH_MB + 1000, 3);
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(be.mission() != null && be.mission().phase() == LaunchRules.Phase.FLIGHT && be.mission().progress() > 20, "flight"))
            .thenExecute(() -> {
                ServerLevel level = h.getLevel();
                CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
                LaunchControlBlockEntity copy = new LaunchControlBlockEntity(h.absolutePos(CONTROL), be.getBlockState());
                copy.loadWithComponents(tag, level.registryAccess());
                ok(h, be.mission().equals(copy.mission()), "the mission after load: " + copy.mission());
                ok(h, copy.fluid().getAmount() == be.fluid().getAmount() && copy.fluid().getFluid() == Fluids.WATER, "the tank after load");
                ok(h, copy.stack(LaunchControlBlockEntity.MODULE_SLOT).getCount() == 2, "the modules after load");
                ok(h, be.owner().equals(copy.owner()), "the owner after load");
            })
            .thenSucceed();
    }

    /** Breaking the control in flight drops it with the mission; placed again it flies on and brings the returns. */
    @GameTest(template = "launch_area", batch = BATCH, skyAccess = true, timeoutTicks = 1600)
    public static void controlDropKeepsMission(GameTestHelper h) {
        useTestData();
        buildComplex(h);
        LaunchControlBlockEntity be = placeControl(h);
        load(be, LAUNCH_MB + 1000, 1);
        BlockState[] state = new BlockState[1];
        ItemStack[] dropped = new ItemStack[1];
        Mission[] carried = new Mission[1];
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(be.mission() != null && be.mission().phase() == LaunchRules.Phase.FLIGHT && be.mission().progress() > 10, "flight"))
            .thenExecute(() -> {
                state[0] = h.getBlockState(CONTROL);
                carried[0] = be.mission();
                h.getLevel().destroyBlock(h.absolutePos(CONTROL), true);
            })
            .thenIdle(1)
            .thenExecute(() -> {
                List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(CONTROL)).inflate(3));
                for (ItemEntity e : drops) {
                    if (e.getItem().is(LaunchRegistry.LAUNCH_CONTROL_ITEM.get())) {
                        dropped[0] = e.getItem().copy();
                        e.discard();
                    }
                }
                ok(h, dropped[0] != null, "the control drops itself: " + drops.stream().map(ItemEntity::getItem).toList());
                Mission m = dropped[0].get(LaunchRegistry.MISSION.get());
                ok(h, m != null && m.phase() == LaunchRules.Phase.FLIGHT && m.progress() >= carried[0].progress() && m.probe().is(LaunchRegistry.PROBE.get()),
                    "the item carries the mission with the probe: " + m);
                ok(h, drops.stream().noneMatch(e -> e.getItem().is(LaunchRegistry.PROBE.get())), "the probe stays in the mission");
                h.setBlock(CONTROL, state[0]);
                LaunchControlBlockEntity again = control(h);
                again.applyComponentsFromItemStack(dropped[0]);
                again.automationFluid().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
                ok(h, again.mission() != null && again.mission().progress() == m.progress(), "placed again, it resumes");
            })
            .thenWaitUntil(() -> h.assertTrue(control(h).mission() == null, "the probe is home"))
            .thenExecute(() -> {
                LaunchControlBlockEntity again = control(h);
                ok(h, outputCount(again, LaunchRegistry.VOIDGLASS.get()) == 4, "four voidglass after the break: " + again.slots());
                ok(h, again.stack(LaunchControlBlockEntity.PROBE_SLOT).is(LaunchRegistry.PROBE.get()), "the probe came home");
            })
            .thenSucceed();
    }

    /** A module file that returns an ore, a raw material or an Ad Astra item does not load (SPEC §31.4, §31.11 3). */
    @GameTest(template = "empty", batch = BATCH)
    public static void oreReturnRejected(GameTestHelper h) {
        for (String bad : List.of("minecraft:raw_iron", "minecraft:iron_ore", "ad_astra:desh_ore")) {
            LaunchData d = testData(bad);
            ok(h, d.module("firmages:sampler_module").isEmpty(), bad + " must not load");
            ok(h, d.errors().stream().anyMatch(e -> e.contains(bad)), bad + " gives an error: " + d.errors());
            ok(h, d.module("firmages:deep_space_module").isPresent(), "the other module still loads");
        }
        ok(h, testData("firmages:voidglass").errors().isEmpty(), "the shipped return loads: " + testData("firmages:voidglass").errors());
        h.succeed();
    }

    private static boolean done(ServerPlayer p, ResourceLocation id) {
        AdvancementHolder adv = p.server.getAdvancements().get(id);
        if (adv == null) throw new IllegalStateException("advancement " + id + " not loaded");
        return p.getAdvancements().getOrStartProgress(adv).isDone();
    }

    /** A flight counts for the control's owner's team and fires firmages:probe for it, never for another player. */
    @GameTest(template = "launch_area", batch = BATCH, skyAccess = true, timeoutTicks = 1200)
    public static void probeCriterionForTheParty(GameTestHelper h) {
        useTestData();
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer owner = DebugPlayerCommands.spawn(server, h.getLevel(), "gt_launch_owner", Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
        ServerPlayer other = DebugPlayerCommands.spawn(server, h.getLevel(), "gt_launch_other", Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 1, 1))));
        owner.setGameMode(GameType.SURVIVAL);
        other.setGameMode(GameType.SURVIVAL);
        ResourceLocation first = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "probe_first");
        buildComplex(h);
        h.setBlock(BEHIND, Blocks.REDSTONE_BLOCK);
        LaunchControlBlockEntity be = placeControl(h);
        be.setOwner(owner.getUUID());
        load(be, LAUNCH_MB + 1000, 1);
        h.startSequence()
            .thenExecute(() -> ok(h, !done(owner, first), "no probe yet"))
            .thenWaitUntil(() -> h.assertTrue(be.phase() == LaunchRules.Phase.FLIGHT, "flight"))
            .thenWaitUntil(() -> h.assertTrue(be.mission() == null, "the probe is home"))
            .thenExecute(() -> {
                LaunchService.fireDirty(server);
                ok(h, done(owner, first), "the owner's team has its first probe");
                ok(h, !done(other, first), "another team has none");
                ok(h, !done(owner, ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "probe_sixteen")), "one is not sixteen");
            })
            .thenExecute(() -> {
                DebugPlayerCommands.despawn(owner);
                DebugPlayerCommands.despawn(other);
            })
            .thenSucceed();
    }
}
