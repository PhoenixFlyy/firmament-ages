package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.sky.AuroraNetBlockEntity;
import dev.firmages.core.sky.AuroraRules;
import dev.firmages.core.sky.AuroraService;
import dev.firmages.core.sky.ClimateSources;
import dev.firmages.core.sky.HarvestRegistry;
import dev.firmages.core.sky.SkyHarvestData;
import dev.firmages.core.sky.SkyHarvestDataLoader;
import dev.firmages.core.sky.SkySavedData;
import dev.firmages.core.sky.StormRodBlock;
import dev.firmages.core.sky.StormRodBlockEntity;
import dev.firmages.core.sky.StormWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * Level G tests of the Sky Harvest (SPEC §26.9) with {@link TestClimateSource} (no TFC in the run): the Aurora Net
 * fills by the expected amount under the override, gives nothing too low, covered or with the override off, shares
 * with an adjacent net, is drained like a pipe and never filled, and has no energy capability; the Storm Rod's seeded
 * rolls strike four times as often in thunder as in rain, a strike is a visual-only bolt that fuses four sand into
 * four storm glass with no fire and no hurt or changed animal, and neither half has an energy capability.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkyHarvestGameTests {
    private static final BlockPos NET = new BlockPos(5, 10, 5);
    private static final BlockPos LOW = new BlockPos(5, 6, 15);
    private static final BlockPos COVERED = new BlockPos(15, 10, 15);
    private static final BlockPos PAIR_A = new BlockPos(18, 10, 5);
    private static final BlockPos PAIR_B = new BlockPos(19, 10, 5);
    private static final BlockPos ROD = new BlockPos(12, 2, 12);

    private SkyHarvestGameTests() {}

    private static void ok(GameTestHelper h, boolean c, String msg) {
        if (!c) h.fail(msg);
    }

    private static AuroraNetBlockEntity net(GameTestHelper h, BlockPos rel) {
        return (AuroraNetBlockEntity) h.getBlockEntity(rel);
    }

    /** Runs {@code steps} 20-tick steps of the net now and returns the mB it gained. */
    private static int steps(GameTestHelper h, BlockPos rel, int steps) {
        AuroraNetBlockEntity be = net(h, rel);
        int before = be.fluid().getAmount();
        for (int i = 0; i < steps; i++) be.step(h.getLevel());
        return be.fluid().getAmount() - before;
    }

    @GameTest(template = "sky_area", batch = "firmages_11_sky_harvest", skyAccess = true, timeoutTicks = 400)
    public static void auroraNetFills(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        SkySavedData sky = SkySavedData.get(level.getServer());
        String override = sky.auroraOverride();
        SkyHarvestData before = SkyHarvestDataLoader.current();
        BlockPos abs = h.absolutePos(NET);
        SkyHarvestData.Aurora a = before.aurora();
        // the net's own height is min_y here (the template sits far below Y 140); the band reads the test climate
        SkyHarvestDataLoader.set(new SkyHarvestData(new SkyHarvestData.Aurora(abs.getY(), a.referenceY(), a.maxAverageTemp(), a.chanceCold(),
            a.chanceWarm(), a.mbPerHour(), a.coldHalfFactor(), a.netCapacity(), a.shareRadius(), a.colors()), before.storm(), List.of()));
        ClimateSources.set(new TestClimateSource(-15F, -20F));
        sky.setAuroraOverride("on");
        Runnable restore = () -> {
            sky.setAuroraOverride(override);
            SkyHarvestDataLoader.set(before);
            ClimateSources.set(null);
        };
        h.startSequence()
            .thenExecute(() -> {
                for (BlockPos p : List.of(NET, LOW, COVERED, PAIR_A, PAIR_B)) h.setBlock(p, HarvestRegistry.AURORA_NET.get());
                h.setBlock(COVERED.above(3), Blocks.STONE);
            })
            // canSeeSky reads the sky light, which follows the roof once the light engine has run
            .thenWaitUntil(() -> {
                net(h, COVERED).step(level);
                h.assertTrue(net(h, COVERED).reason() == AuroraNetBlockEntity.Reason.NO_SKY, "covered net: " + net(h, COVERED).reason());
            })
            .thenExecute(() -> {
                AuroraRules.Result r = AuroraService.at(level, abs.getX(), abs.getZ());
                ok(h, r.active() && r.inBand(), "override on, cold test climate: " + r);
                double intensity = r.intensity();
                int got = steps(h, NET, 10);
                int want = (int) Math.floor(10 * AuroraRules.fillPerStep(SkyHarvestDataLoader.current().aurora(), intensity, 0, r.coldHalf()));
                ok(h, Math.abs(got - want) <= 1, "ten steps give about " + want + " mB: " + got);
                ok(h, net(h, NET).reason() == AuroraNetBlockEntity.Reason.CATCHING, "catching: " + net(h, NET).reason());
                ok(h, h.getBlockState(NET).getValue(dev.firmages.core.sky.AuroraNetBlock.CATCHING), "the catching state glows");
                ok(h, steps(h, LOW, 10) == 0 && net(h, LOW).reason() == AuroraNetBlockEntity.Reason.TOO_LOW, "too low: " + net(h, LOW).reason());
                ok(h, steps(h, COVERED, 10) == 0, "covered: nothing");
                double pairIntensity = AuroraService.at(level, h.absolutePos(PAIR_A).getX(), h.absolutePos(PAIR_A).getZ()).intensity();
                int pa = steps(h, PAIR_A, 10);
                int pb = steps(h, PAIR_B, 10);
                int half = (int) Math.floor(10 * AuroraRules.fillPerStep(SkyHarvestDataLoader.current().aurora(), pairIntensity, 1, r.coldHalf()));
                ok(h, Math.abs(pa - half) <= 1 && Math.abs(pb - half) <= 1, "two adjacent nets get half each (" + half + "): " + pa + ", " + pb);

                // a pipe drains it from any side and never fills it
                IFluidHandler cap = level.getCapability(Capabilities.FluidHandler.BLOCK, abs, Direction.WEST);
                ok(h, cap != null, "fluid capability on the side");
                int had = net(h, NET).fluid().getAmount();
                FluidStack out = cap.drain(5, IFluidHandler.FluidAction.EXECUTE);
                ok(h, out.getFluid() == HarvestRegistry.AURORA_LIGHT.get() && out.getAmount() == Math.min(5, had), "drained aurora light: " + out);
                ok(h, cap.fill(new FluidStack(HarvestRegistry.AURORA_LIGHT.get(), 100), IFluidHandler.FluidAction.EXECUTE) == 0, "never filled");
                for (Direction d : Direction.values()) {
                    ok(h, level.getCapability(Capabilities.EnergyStorage.BLOCK, abs, d) == null, "energy capability on " + d);
                }

                sky.setAuroraOverride("off");
                ok(h, steps(h, NET, 10) == 0 && net(h, NET).reason() == AuroraNetBlockEntity.Reason.NO_AURORA, "override off: nothing");
                ok(h, !h.getBlockState(NET).getValue(dev.firmages.core.sky.AuroraNetBlock.CATCHING), "no glow");
                sky.setAuroraOverride("on");
                ClimateSources.set(new TestClimateSource(5F, 0F));
                ok(h, steps(h, NET, 10) == 0, "a warm column stays off even with the override");
                restore.run();
            })
            .thenSucceed();
    }

    @GameTest(template = "sky_area", batch = "firmages_11_sky_storm", skyAccess = true, timeoutTicks = 400)
    public static void stormRodFusesSand(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // seeded rolls: thunder strikes four times as often as rain, a roof or dry weather never
        RandomSource r1 = RandomSource.create(1234L);
        RandomSource r2 = RandomSource.create(1234L);
        int rain = 0;
        int thunder = 0;
        int n = 240_000;
        for (int i = 0; i < n; i++) {
            if (StormRodBlockEntity.roll(r1, true, false, true)) rain++;
            if (StormRodBlockEntity.roll(r2, true, true, true)) thunder++;
        }
        double ratio = thunder / (double) rain;
        ok(h, Math.abs(rain - 1000) < 120, "rain: about one strike in 240 rolls (4,800 ticks): " + rain);
        ok(h, ratio > 3.6 && ratio < 4.4, "thunder four times rain: " + ratio);
        ok(h, !StormRodBlockEntity.roll(r1, false, false, true) && !StormRodBlockEntity.roll(r1, true, true, false), "dry or covered: never");
        // the operators' test storm (TFC owns the world's weather): only the rods see it, and it ends
        StormWeather.force(level, StormWeather.Mode.THUNDER, 100);
        ok(h, StormWeather.raining(level) && StormWeather.thundering(level), "a forced thunderstorm");
        StormWeather.force(level, StormWeather.Mode.CLEAR, 100);
        ok(h, !StormWeather.raining(level), "a forced clear sky");
        StormWeather.force(level, StormWeather.Mode.RAIN, 0);
        ok(h, StormWeather.active(level) == StormWeather.Mode.AUTO, "an override that ran out is gone");
        StormWeather.reset();

        BlockPos basin = h.absolutePos(ROD);
        Pig[] pig = new Pig[1];
        h.startSequence()
            .thenExecute(() -> {
                StormRodBlock.placeAt(level, basin);
                BlockState lower = level.getBlockState(basin);
                BlockState upper = level.getBlockState(basin.above());
                ok(h, lower.is(HarvestRegistry.STORM_ROD.get()) && upper.is(HarvestRegistry.STORM_ROD.get()), "two halves placed");
                IItemHandler top = level.getCapability(Capabilities.ItemHandler.BLOCK, basin.above(), Direction.UP);
                ok(h, top != null, "the rod takes sand from the top");
                ok(h, top.insertItem(0, new ItemStack(Items.DIRT), false).getCount() == 1, "dirt is refused");
                ItemStack left = new ItemStack(Items.SAND, 4);
                for (int i = 0; i < top.getSlots() && !left.isEmpty(); i++) left = top.insertItem(i, left, false);
                ok(h, left.isEmpty(), "four sand go in");
                for (Direction d : Direction.values()) {
                    ok(h, level.getCapability(Capabilities.EnergyStorage.BLOCK, basin, d) == null
                        && level.getCapability(Capabilities.EnergyStorage.BLOCK, basin.above(), d) == null, "energy capability on " + d);
                }
                pig[0] = EntityType.PIG.create(level);
                pig[0].moveTo(basin.getX() + 1.5, basin.getY() + 1, basin.getZ() + 0.5);
                pig[0].setNoAi(true);
                level.addFreshEntity(pig[0]);
                StormRodBlockEntity be = (StormRodBlockEntity) level.getBlockEntity(basin);
                int fused = be.strike(level);
                ok(h, fused == 4 && be.sand() == 0 && be.strikes() == 1, "four sand fused: " + fused + ", sand left " + be.sand());
                List<LightningBolt> bolts = level.getEntitiesOfClass(LightningBolt.class, new AABB(basin).inflate(3));
                ok(h, bolts.size() == 1, "one bolt at the tip: " + bolts.size());
            })
            .thenIdle(20)
            .thenExecute(() -> {
                IItemHandler below = level.getCapability(Capabilities.ItemHandler.BLOCK, basin, Direction.DOWN);
                int glass = 0;
                for (int i = 0; i < below.getSlots(); i++) {
                    ItemStack s = below.extractItem(i, 64, false);
                    if (s.is(HarvestRegistry.STORM_GLASS_ITEM.get())) glass += s.getCount();
                }
                ok(h, glass == 4, "four storm glass from below: " + glass);
                ok(h, pig[0].isAlive() && pig[0].getHealth() == pig[0].getMaxHealth() && !pig[0].isRemoved(), "the pig is unhurt and still a pig");
                int fire = 0;
                for (BlockPos p : BlockPos.betweenClosed(basin.offset(-4, -2, -4), basin.offset(4, 5, 4))) {
                    if (level.getBlockState(p).is(Blocks.FIRE)) fire++;
                }
                ok(h, fire == 0, "no fire: " + fire);
                StormRodBlockEntity be = (StormRodBlockEntity) level.getBlockEntity(basin);
                level.setWeatherParameters(0, 2000, true, false);
            })
            .thenWaitUntil(() -> {
                StormRodBlockEntity be = (StormRodBlockEntity) level.getBlockEntity(basin);
                h.assertTrue(be.reason() == StormRodBlockEntity.Reason.NO_SAND, "rain without sand: " + be.reason());
            })
            .thenExecute(() -> {
                level.setWeatherParameters(6000, 0, false, false);
                pig[0].discard();
                level.removeBlock(basin.above(), false);
                ok(h, level.getBlockState(basin).isAir(), "breaking the rod removes the basin");
            })
            .thenSucceed();
    }
}
