package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.crown.CrownRegistry;
import dev.firmages.core.crown.CrownRules;
import dev.firmages.core.crown.CrownService;
import dev.firmages.core.crown.LensMountBlock;
import dev.firmages.core.crown.LensMountBlockEntity;
import dev.firmages.core.crown.StarAnvilBlock;
import dev.firmages.core.crown.StarAnvilBlockEntity;
import dev.firmages.core.crown.StarforgeRecipe;
import dev.firmages.core.net.ShrinePreviewPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

/**
 * Level G tests of the Lens Crown (SPEC §27.13). The crown is built from the vanilla stand-ins of the
 * {@code firmages_test} crown tags (smooth stone, chiseled stone bricks, tinted glass) around a Star Anvil at (6,1,6) of
 * the empty 13 x 6 x 13 template {@code crown_area}; the recipes are {@code firmages_test:starforge/*} with vanilla
 * items: {@code dawn} (Dawn Triad: two clay balls and a flint, 100 mB water, two bricks), {@code pair} (Stone and Iron:
 * quartz and glowstone dust), its twin with the same ingredients (dropped with a WARN), {@code late} (an age_4 result)
 * and {@code bad_chord} (rejected for its unknown chord).
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class CrownGameTests {
    private static final BlockPos ANVIL = new BlockPos(6, 1, 6);
    /** Socket offsets from the anvil (dx, dz): three per side at -2, 0 and +2. */
    private static final int[][] SOCKETS = {{-2, -4}, {0, -4}, {2, -4}, {-2, 4}, {0, 4}, {2, 4}, {-4, -2}, {-4, 0}, {-4, 2}, {4, -2}, {4, 0}, {4, 2}};
    private static final ResourceLocation DAWN = ResourceLocation.fromNamespaceAndPath("firmages_test", "starforge/dawn");
    private static final ResourceLocation PAIR = ResourceLocation.fromNamespaceAndPath("firmages_test", "starforge/pair");
    private static final ResourceLocation TWIN = ResourceLocation.fromNamespaceAndPath("firmages_test", "starforge/pair_twin");
    private static final ResourceLocation LATE = ResourceLocation.fromNamespaceAndPath("firmages_test", "starforge/late");
    private static final ResourceLocation BAD = ResourceLocation.fromNamespaceAndPath("firmages_test", "starforge/bad_chord");

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(CrownGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean condition, String message) {
        if (!condition) FirmagesCore.LOGGER.error("Crown GameTest assertion failed: {}", message);
        h.assertTrue(condition, message);
    }

    /** The 53-position crown with rock in every socket. */
    private static void buildCrown(GameTestHelper h) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) == 4 || Math.abs(dz) == 4) h.setBlock(ANVIL.offset(dx, 0, dz), Blocks.SMOOTH_STONE);
            }
        }
        for (int[] c : new int[][] {{-4, -4}, {-4, 4}, {4, -4}, {4, 4}}) {
            h.setBlock(ANVIL.offset(c[0], 1, c[1]), Blocks.CHISELED_STONE_BRICKS);
            h.setBlock(ANVIL.offset(c[0], 2, c[1]), Blocks.TINTED_GLASS);
        }
        for (int[] s : SOCKETS) h.setBlock(ANVIL.offset(s[0], 1, s[1]), Blocks.SMOOTH_STONE);
        h.setBlock(ANVIL, CrownRegistry.STAR_ANVIL.get());
    }

    private static BlockPos socket(int i) {
        return ANVIL.offset(SOCKETS[i][0], 1, SOCKETS[i][1]);
    }

    /** A mount on socket {@code i} holding lens {@code lens} (or none for -1); returns its relative position. */
    private static BlockPos mount(GameTestHelper h, int i, int lens) {
        BlockPos p = socket(i);
        h.setBlock(p, CrownRegistry.LENS_MOUNT.get());
        if (lens >= 0) ((LensMountBlockEntity) h.getBlockEntity(p)).setLens(new ItemStack(CrownRegistry.lens(lens)));
        return p;
    }

    /** The block outside the ring next to socket {@code i}, where its redstone line goes. */
    private static BlockPos line(int i) {
        int dx = SOCKETS[i][0];
        int dz = SOCKETS[i][1];
        return socket(i).offset(Math.abs(dx) == 4 ? Integer.signum(dx) : 0, 0, Math.abs(dz) == 4 ? Integer.signum(dz) : 0);
    }

    private static void power(GameTestHelper h, int i, boolean on) {
        h.setBlock(line(i), on ? Blocks.REDSTONE_BLOCK : Blocks.AIR);
    }

    private static StarAnvilBlockEntity anvil(GameTestHelper h) {
        return (StarAnvilBlockEntity) h.getBlockEntity(ANVIL);
    }

    private static boolean formed(GameTestHelper h) {
        return h.getBlockState(ANVIL).getValue(StarAnvilBlock.FORMED);
    }

    private static int outputCount(GameTestHelper h) {
        return anvil(h).stack(StarAnvilBlockEntity.OUTPUT).getCount();
    }

    private static boolean reloadIdle(MinecraftServer s) {
        return AgeService.status(s).phase() == ReloadScheduler.Phase.IDLE;
    }

    private static void agesUpTo(MinecraftServer server, AgeId last) {
        for (int i = AgeId.all().size() - 1; i > last.ordinal(); i--) AgeService.simulate(server, AgeId.all().get(i), false);
        for (int i = 0; i <= last.ordinal(); i++) AgeService.simulate(server, AgeId.all().get(i), true);
    }

    /**
     * The crown validates from the stand-ins (four-fold symmetric, so every rotation), a missing pillar breaks it, rock
     * sockets count, it grows to twelve mounts that all turn toward the anvil, and an unformed crown offers its ghost.
     */
    @GameTest(template = "crown_area", batch = "firmages_11_crown", skyAccess = true, timeoutTicks = 400)
    public static void crownValidatesAndGrows(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(ANVIL);
        h.startSequence()
            .thenExecute(() -> buildCrown(h))
            .thenIdle(3)
            .thenExecute(() -> {
                ok(h, formed(h), "the crown with rock sockets is formed: " + CrownService.check(level, abs).missing());
                ok(h, CrownService.check(level, abs).total() == 53, "53 counted positions");
                ok(h, CrownService.preview(level, abs).isEmpty(), "no ghost for a standing crown");
                h.setBlock(ANVIL.offset(4, 1, 4), Blocks.AIR);
                anvil(h).requestValidation();
            })
            .thenIdle(3)
            .thenExecute(() -> {
                ok(h, !formed(h) && anvil(h).missing() == 1, "a missing pillar breaks the crown: missing " + anvil(h).missing());
                ok(h, anvil(h).reason() == CrownRules.Reason.UNFORMED, "reason unformed");
                Optional<ShrinePreviewPayload> ghost = CrownService.preview(level, abs);
                ok(h, ghost.isPresent() && ghost.get().multiblock().equals(CrownService.CROWN_ID) && ghost.get().heart().equals(abs),
                    "the ghost of the crown, anchored at the anvil");
                h.setBlock(ANVIL.offset(4, 1, 4), Blocks.CHISELED_STONE_BRICKS);
                for (int i = 0; i < SOCKETS.length; i++) {
                    h.setBlock(socket(i), CrownRegistry.LENS_MOUNT.get().defaultBlockState().setValue(LensMountBlock.FACING, Direction.EAST));
                }
                anvil(h).requestValidation();
            })
            .thenIdle(3)
            .thenExecute(() -> {
                ok(h, formed(h), "twelve mounts on the sockets: formed");
                ok(h, anvil(h).mounts().size() == 12, "twelve mounts known: " + anvil(h).mounts().size());
                for (int i = 0; i < SOCKETS.length; i++) {
                    Direction toward = Direction.getNearest(-SOCKETS[i][0], 0, -SOCKETS[i][1]);
                    ok(h, h.getBlockState(socket(i)).getValue(LensMountBlock.FACING) == toward, "mount " + i + " faces the anvil (" + toward + ")");
                }
            })
            .thenSucceed();
    }

    /**
     * A hopper feeds the Dawn Triad recipe; two of three lenses in do nothing; all three forge after 100 ticks (plus the
     * swing); a fourth lens in does nothing; a chord change resets the progress and keeps the items; a roof pauses;
     * night halves the time.
     */
    @GameTest(template = "crown_area", batch = "firmages_11_crown_forge", skyAccess = true, timeoutTicks = 2400)
    public static void redstoneChordForges(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        long[] mark = new long[1];
        double[] kept = new double[1];
        long dayTime = level.getDayTime();
        h.startSequence()
            .thenExecute(() -> {
                level.setDayTime(6000);
                buildCrown(h);
                mount(h, 0, 0);
                mount(h, 1, 1);
                mount(h, 2, 2);
                mount(h, 10, 3);
                BlockPos hopper = ANVIL.west();
                h.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
                HopperBlockEntity hb = (HopperBlockEntity) h.getBlockEntity(hopper);
                hb.setItem(0, new ItemStack(Items.CLAY_BALL, 8));
                hb.setItem(1, new ItemStack(Items.FLINT, 4));
                anvil(h).handTank().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
                power(h, 0, true);
                power(h, 1, true);
            })
            .thenWaitUntil(() -> h.assertTrue(anvil(h).pendingRecipe() != null && anvil(h).pendingRecipe().id().equals(DAWN), "the hopper fills the dawn set"))
            .thenIdle(60)
            .thenExecute(() -> {
                StarAnvilBlockEntity be = anvil(h);
                ok(h, outputCount(h) == 0 && be.progress() == 0, "two of three lenses forge nothing");
                ok(h, be.reason() == CrownRules.Reason.WRONG_CHORD && be.reasonLens() == 2 && !be.swingOut(), "Swing in: Iron Lens");
                ok(h, be.inputs().get(0).getCount() + be.inputs().get(1).getCount() + be.inputs().get(2).getCount() == 3,
                    "the slots hold exactly one set (two clay, one flint): " + be.inputs());
                power(h, 2, true);
                mark[0] = level.getGameTime();
            })
            .thenWaitUntil(() -> h.assertTrue(outputCount(h) == 2, "two bricks"))
            .thenExecute(() -> {
                long took = level.getGameTime() - mark[0];
                ok(h, took >= 100 && took <= 125, "the Dawn Triad takes 100 ticks after the swing: " + took);
                ok(h, anvil(h).fluid().getAmount() == 900, "100 mB water used: " + anvil(h).fluid().getAmount());
                power(h, 10, true);
            })
            .thenWaitUntil(() -> h.assertTrue(anvil(h).pendingRecipe() != null, "the hopper fills the next set"))
            .thenIdle(150)
            .thenExecute(() -> {
                ok(h, outputCount(h) == 2, "a fourth lens in makes another chord: nothing forged");
                ok(h, anvil(h).reason() == CrownRules.Reason.WRONG_CHORD && anvil(h).swingOut() && anvil(h).reasonLens() == 3, "Swing out: Arcane Lens");
                power(h, 10, false);
            })
            .thenWaitUntil(() -> h.assertTrue(anvil(h).progress() >= 30, "forging again"))
            .thenExecute(() -> {
                power(h, 1, false);
                kept[0] = anvil(h).inputs().stream().mapToInt(ItemStack::getCount).sum();
            })
            .thenIdle(3)
            .thenExecute(() -> {
                ok(h, anvil(h).progress() == 0, "a chord change resets the progress");
                ok(h, anvil(h).inputs().stream().mapToInt(ItemStack::getCount).sum() == kept[0], "and keeps every item");
                power(h, 1, true);
            })
            .thenWaitUntil(() -> h.assertTrue(anvil(h).progress() >= 30, "forging again after the chord is back"))
            .thenExecute(() -> h.setBlock(ANVIL.above(4), Blocks.STONE))
            // canSeeSky reads the sky light, which follows the roof once the light engine has run
            .thenWaitUntil(() -> h.assertTrue(anvil(h).reason() == CrownRules.Reason.SKY_HIDDEN, "a roof hides the sky: " + anvil(h).reason()))
            .thenExecute(() -> kept[0] = anvil(h).progress())
            .thenIdle(20)
            .thenExecute(() -> {
                ok(h, anvil(h).progress() == kept[0] && kept[0] >= 30, "a covered sky pauses and keeps the progress: " + kept[0]);
                h.setBlock(ANVIL.above(4), Blocks.AIR);
            })
            .thenWaitUntil(() -> h.assertTrue(outputCount(h) == 4, "the second forging completes"))
            .thenExecute(() -> {
                level.setDayTime(18000);
                ok(h, anvil(h).stack(StarAnvilBlockEntity.OUTPUT).is(Items.BRICK), "bricks");
            })
            .thenWaitUntil(() -> h.assertTrue(level.isNight() && anvil(h).progress() > 0 && anvil(h).progress() < 50, "the third set forges at night"))
            .thenExecute(() -> {
                kept[0] = anvil(h).progress();
                mark[0] = level.getGameTime();
            })
            .thenIdle(10)
            .thenExecute(() -> {
                double gained = anvil(h).progress() - kept[0];
                long ticks = level.getGameTime() - mark[0];
                ok(h, Math.abs(gained - 2.0 * ticks) < 0.01, "a night tick counts twice: " + gained + " in " + ticks + " ticks");
                level.setDayTime(dayTime);
            })
            .thenSucceed();
    }

    /** The comparator gives the pending recipe's chord signal: Dawn Triad 1, Stone and Iron 8, a partial set 0. */
    @GameTest(template = "crown_area", batch = "firmages_11_crown", skyAccess = true, timeoutTicks = 200)
    public static void comparatorNamesTheChord(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos comparator = ANVIL.east();
        h.startSequence()
            .thenExecute(() -> {
                buildCrown(h);
                h.setBlock(comparator.below(), Blocks.SMOOTH_STONE); // a comparator needs a floor
                h.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST));
                anvil(h).insertFromHand(new ItemStack(Items.CLAY_BALL, 2));
                anvil(h).insertFromHand(new ItemStack(Items.FLINT));
            })
            .thenIdle(6)
            .thenExecute(() -> {
                BlockPos abs = h.absolutePos(ANVIL);
                BlockState s = level.getBlockState(abs);
                ok(h, s.getAnalogOutputSignal(level, abs) == 1, "Dawn Triad signal 1: " + s.getAnalogOutputSignal(level, abs));
                ok(h, ((ComparatorBlockEntity) h.getBlockEntity(comparator)).getOutputSignal() == 1, "the comparator outputs 1");
                while (!anvil(h).takeByHand().isEmpty()) {
                    // empty the slots
                }
                anvil(h).insertFromHand(new ItemStack(Items.QUARTZ));
                anvil(h).insertFromHand(new ItemStack(Items.GLOWSTONE_DUST));
            })
            .thenIdle(6)
            .thenExecute(() -> {
                ok(h, anvil(h).signal() == 8, "Stone and Iron signal 8: " + anvil(h).signal());
                ok(h, ((ComparatorBlockEntity) h.getBlockEntity(comparator)).getOutputSignal() == 8, "the comparator outputs 8");
                anvil(h).takeByHand();
            })
            .thenIdle(6)
            .thenExecute(() -> {
                ok(h, anvil(h).signal() == 0, "a partial set matches nothing: " + anvil(h).signal());
                ok(h, ((ComparatorBlockEntity) h.getBlockEntity(comparator)).getOutputSignal() == 0, "the comparator outputs 0");
            })
            .thenSucceed();
    }

    /**
     * Side rules (top and sides insert what fits the open recipes, the bottom only gives the result), the tank takes
     * only a recipe fluid, anvil and mount NBT round trip, a mount with a lens is unbreakable in survival, the twin
     * recipe is dropped and the unknown chord rejected.
     */
    @GameTest(template = "crown_area", batch = "firmages_11_crown", skyAccess = true, timeoutTicks = 200)
    public static void handlersAndSave(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.startSequence()
            .thenExecute(() -> {
                buildCrown(h);
                mount(h, 0, 4);
            })
            .thenIdle(2)
            .thenExecute(() -> {
                BlockPos abs = h.absolutePos(ANVIL);
                IItemHandler side = level.getCapability(Capabilities.ItemHandler.BLOCK, abs, Direction.NORTH);
                IItemHandler top = level.getCapability(Capabilities.ItemHandler.BLOCK, abs, Direction.UP);
                IItemHandler bottom = level.getCapability(Capabilities.ItemHandler.BLOCK, abs, Direction.DOWN);
                ok(h, side != null && top != null && bottom != null, "handlers on every side");
                ItemStack left = side.insertItem(0, new ItemStack(Items.CLAY_BALL, 64), false);
                ok(h, left.getCount() == 62, "a slot takes only what the recipe needs (two clay): " + left.getCount());
                ok(h, side.insertItem(1, new ItemStack(Items.CLAY_BALL, 64), true).getCount() == 64, "clay stays in its one slot");
                ok(h, top.insertItem(1, new ItemStack(Items.STICK), true).getCount() == 1, "a stick fits no recipe");
                ok(h, top.insertItem(1, new ItemStack(Items.FLINT, 5), false).getCount() == 4, "one flint goes in from the top");
                ok(h, side.extractItem(0, 64, true).isEmpty(), "the sides give nothing back");
                ok(h, bottom.insertItem(0, new ItemStack(Items.CLAY_BALL), true).getCount() == 1, "the bottom takes nothing");
                ok(h, bottom.extractItem(0, 64, true).isEmpty(), "no result yet");

                IFluidHandler fluid = level.getCapability(Capabilities.FluidHandler.BLOCK, abs, Direction.EAST);
                ok(h, fluid != null && level.getCapability(Capabilities.FluidHandler.BLOCK, abs, Direction.DOWN) == null, "tank from the sides only");
                ok(h, fluid.fill(new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 0,
                    "lava is no recipe fluid");
                ok(h, fluid.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 5000), IFluidHandler.FluidAction.EXECUTE) == 4000,
                    "water fills the 4,000 mB tank");
                ok(h, fluid.drain(1000, IFluidHandler.FluidAction.SIMULATE).isEmpty(), "pipes cannot drain it");

                // the result through NBT, then the bottom gives it out
                StarAnvilBlockEntity be = anvil(h);
                CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
                ItemStackHandler items = new ItemStackHandler(5);
                for (int i = 0; i < 4; i++) items.setStackInSlot(i, be.stack(i).copy());
                items.setStackInSlot(StarAnvilBlockEntity.OUTPUT, new ItemStack(Items.BRICK, 3));
                tag.put("items", items.serializeNBT(level.registryAccess()));
                be.loadWithComponents(tag, level.registryAccess());
                ok(h, bottom.extractItem(0, 64, false).getCount() == 3, "the bottom gives the result");

                CompoundTag saved = be.saveWithoutMetadata(level.registryAccess());
                StarAnvilBlockEntity copy = new StarAnvilBlockEntity(abs, h.getBlockState(ANVIL));
                copy.loadWithComponents(saved, level.registryAccess());
                ok(h, copy.stack(0).getCount() == 2 && copy.stack(0).is(Items.CLAY_BALL) && copy.stack(1).is(Items.FLINT), "inputs round trip");
                ok(h, copy.fluid().getAmount() == 4000, "tank round trip");

                LensMountBlockEntity m = (LensMountBlockEntity) h.getBlockEntity(socket(0));
                LensMountBlockEntity mc = new LensMountBlockEntity(h.absolutePos(socket(0)), h.getBlockState(socket(0)));
                mc.loadWithComponents(m.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
                ok(h, mc.lensIndex() == 4, "mount round trip: Industrial Lens");
                BlockPos mpos = h.absolutePos(socket(0));
                ok(h, level.getBlockState(mpos).getDestroyProgress(h.makeMockPlayer(GameType.SURVIVAL), level, mpos) == 0.0F,
                    "a mount with a lens cannot be broken in survival");
                ok(h, h.getBlockState(socket(0)).getValue(LensMountBlock.LENS) == 4, "blockstate lens 4");

                List<RecipeHolder<StarforgeRecipe>> live = CrownService.recipes(level);
                ok(h, live.stream().anyMatch(r -> r.id().equals(PAIR)) && live.stream().noneMatch(r -> r.id().equals(TWIN)),
                    "the twin with the same ingredients is dropped, the earlier id stays");
                ok(h, level.getRecipeManager().byKey(BAD).isEmpty(), "an unknown chord rejects the file");
            })
            .thenSucceed();
    }

    /** In age_3 the age_4 test recipe is gone from the recipe manager; after the Age swap to age_4 it is back. */
    @GameTest(template = "crown_area", batch = "firmages_11_crown_gate", timeoutTicks = 2400)
    public static void gateFollowsAges(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                agesUpTo(server, AgeId.AGE_3);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_3 setup"))
            .thenExecute(() -> {
                ok(h, server.getRecipeManager().byKey(LATE).isEmpty(), "age_3: the age_4 forging is not there");
                ok(h, server.getRecipeManager().byKey(DAWN).isPresent(), "an unlocked forging is there");
                ok(h, CrownService.recipes(h.getLevel()).stream().noneMatch(r -> r.id().equals(LATE)), "nor in the anvil's list");
                AgeService.simulate(server, AgeId.AGE_4, true);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server) && server.getRecipeManager().byKey(LATE).isPresent(), "age_4: the forging is back"))
            .thenExecute(() -> {
                ok(h, CrownService.recipes(h.getLevel()).stream().anyMatch(r -> r.id().equals(LATE)), "the anvil sees it after the swap");
                agesUpTo(server, AgeId.DAWN);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload back to dawn"))
            .thenSucceed();
    }
}
