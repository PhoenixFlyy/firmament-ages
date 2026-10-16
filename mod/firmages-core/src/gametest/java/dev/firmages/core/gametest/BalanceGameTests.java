package dev.firmages.core.gametest;

import blusunrize.immersiveengineering.api.IEEnums;
import blusunrize.immersiveengineering.common.blocks.metal.CapacitorBlockEntity;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.balance.EnergyAudit;
import dev.firmages.core.balance.EnergyMeter;
import dev.firmages.core.balance.OreAudit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Level G tests of the balance tools (SPEC §37, §38): {@code balance.feCap} caps what an IE capacitor pushes into its
 * neighbour and what is drawn out of it, per game tick; the energy audit meters the same pair per block type; the ore
 * audit reads the metal table from the jar and measures the live recipes.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class BalanceGameTests {
    private static final String LV = "immersiveengineering:capacitor_lv";
    private static final String MV = "immersiveengineering:capacitor_mv";

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(BalanceGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean condition, String message) {
        if (!condition) h.fail(message);
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    }

    /** An LV capacitor (filled, pushing up) under an MV capacitor (taking from below), both freshly looked up. */
    private static BlockPos[] pair(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos src = h.absolutePos(new BlockPos(1, 1, 1));
        BlockPos dst = src.above();
        level.setBlockAndUpdate(src, block(LV).defaultBlockState());
        level.setBlockAndUpdate(dst, block(MV).defaultBlockState());
        CapacitorBlockEntity s = (CapacitorBlockEntity) level.getBlockEntity(src);
        CapacitorBlockEntity d = (CapacitorBlockEntity) level.getBlockEntity(dst);
        // fill the source through its input side (UP by default), then make UP its output and DOWN the sink's input
        IEnergyStorage in = level.getCapability(Capabilities.EnergyStorage.BLOCK, src, Direction.UP);
        for (int i = 0; i < 2000 && in.getEnergyStored() < in.getMaxEnergyStored(); i++) in.receiveEnergy(1000, false);
        s.sideConfig.put(Direction.UP, IEEnums.IOSideConfig.OUTPUT);
        d.sideConfig.put(Direction.UP, IEEnums.IOSideConfig.NONE);
        d.sideConfig.put(Direction.DOWN, IEEnums.IOSideConfig.INPUT);
        s.setChanged();
        d.setChanged();
        level.invalidateCapabilities(src);
        level.invalidateCapabilities(dst);
        return new BlockPos[] {src, dst};
    }

    private static int stored(ServerLevel level, BlockPos pos, Direction side) {
        IEnergyStorage e = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
        return e == null ? -1 : e.getEnergyStored();
    }

    /** SPEC §37: a capped LV capacitor pushes 50 FE per tick instead of its 256, and gives 50 per tick when drawn. */
    @GameTest(template = "empty", batch = "firmages_5_balance_cap", timeoutTicks = 200)
    public static void feCapLimitsAPushingCapacitor(GameTestHelper h) {
        if (!ModList.get().isLoaded("immersiveengineering")) {
            h.succeed();
            return;
        }
        EnergyMeter.setCapsForTest(List.of(LV + "=50"));
        ServerLevel level = h.getLevel();
        BlockPos[] p = pair(h);
        int[] start = new int[1];
        h.runAfterDelay(5, () -> start[0] = stored(level, p[1], Direction.DOWN));
        h.runAfterDelay(45, () -> {
            try {
                int got = stored(level, p[1], Direction.DOWN) - start[0];
                ok(h, got >= 50 * 39 && got <= 50 * 41, "the capped LV capacitor pushed " + got + " FE in 40 ticks (50 per tick expected)");
                // drawn out through its output side: 50 in this tick at most, whatever was pushed this tick counts too
                IEnergyStorage out = level.getCapability(Capabilities.EnergyStorage.BLOCK, p[0], Direction.UP);
                int first = out.extractEnergy(10_000, false);
                int second = out.extractEnergy(10_000, false);
                ok(h, first <= 50 && second == 0, "drawn in one tick: " + first + " then " + second + " (cap 50)");
            } finally {
                EnergyMeter.setCapsForTest(List.of());
            }
            h.succeed();
        });
    }

    /** SPEC §38: the energy audit meters the pair: the LV capacitor gives 256 FE/t, the MV one takes it. */
    @GameTest(template = "empty", batch = "firmages_5_balance_audit", timeoutTicks = 200)
    public static void energyAuditMetersPerBlockType(GameTestHelper h) {
        if (!ModList.get().isLoaded("immersiveengineering")) {
            h.succeed();
            return;
        }
        EnergyMeter.setCapsForTest(List.of());
        ServerLevel level = h.getLevel();
        BlockPos[] p = pair(h);
        EnergyAudit[] audit = new EnergyAudit[1];
        h.runAfterDelay(2, () -> {
            audit[0] = new EnergyAudit(level, p[0], 4, 40, level.getServer().createCommandSourceStack().withSuppressedOutput());
            EnergyMeter.start(audit[0]);
        });
        h.runAfterDelay(60, () -> {
            ok(h, EnergyMeter.running() == null, "the audit is over after 40 ticks");
            List<String> lines = audit[0].lines();
            String lv = lines.stream().filter(l -> l.contains(LV)).findFirst().orElse("");
            String mv = lines.stream().filter(l -> l.contains(MV)).findFirst().orElse("");
            double out = number(lv, "out "), in = number(mv, "in ");
            ok(h, out > 240 && out < 270, "the LV capacitor gave about 256 FE/t: " + lines);
            ok(h, in > 240 && in < 270, "the MV capacitor took about 256 FE/t: " + lines);
            h.succeed();
        });
    }

    private static double number(String line, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(key) + "([0-9.]+)").matcher(line);
        return m.find() ? Double.parseDouble(m.group(1)) : -1;
    }

    /** SPEC §38: the ore audit finds the metal table in the jar and measures every recipe (the run has few ore recipes). */
    @GameTest(template = "empty", batch = "firmages_5_balance_ore")
    public static void oreAuditReadsTheTable(GameTestHelper h) {
        ok(h, OreAudit.table(h.getLevel().getServer()).isPresent(), "data/firmages/balance/metals.json is in the jar");
        Optional<OreAudit.Result> r = OreAudit.run(h.getLevel().getServer());
        ok(h, r.isPresent() && r.get().recipes() > 0 && !r.get().lines().isEmpty(), "the ore audit ran on the live recipes: " + r.map(OreAudit.Result::lines));
        h.succeed();
    }
}
