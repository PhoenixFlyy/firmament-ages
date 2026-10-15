package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.compat.ftbteams.FtbTeamsCompat;
import dev.firmages.core.proof.ProofRegistry;
import dev.firmages.core.proof.SiloLaunch;
import dev.firmages.core.proof.TallyChuteBlockEntity;
import dev.firmages.core.proof.TallyCounts;
import dev.firmages.core.proof.TallyData;
import dev.firmages.core.proof.TallyService;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Level G tests of proof of work (SPEC §22): the Tally Chute in a hopper line (counting, a stack of chutes counting
 * once, a full outlet blocking, the criterion with item, tag and Age), its team (two party members both earn the
 * criterion), the saved tally surviving a save and load, and the silo launch with stand-ins for Ad Astra's rocket and
 * Create's hatch (neither mod is in the GameTest run).
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class ProofGameTests {
    private static final String ROCKET_TAG = "firmages.gt_rocket";
    private static final String LAUNCHED_TAG = "firmages.gt_launched";

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(ProofGameTests.class);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, path);
    }

    private static boolean done(ServerPlayer p, ResourceLocation id) {
        AdvancementHolder adv = p.server.getAdvancements().get(id);
        if (adv == null) throw new IllegalStateException("advancement " + id + " not loaded");
        return p.getAdvancements().getOrStartProgress(adv).isDone();
    }

    private static ServerPlayer spawn(GameTestHelper h, String name) {
        ServerPlayer p = DebugPlayerCommands.spawn(h.getLevel().getServer(), h.getLevel(), name, Vec3.atCenterOf(h.absolutePos(new BlockPos(0, 1, 0))));
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    /** chest at {@code base}, then {@code chutes} Tally Chutes owned by {@code owner}, then a hopper (pointing down) with {@code load}. */
    private static BlockPos line(GameTestHelper h, BlockPos base, int chutes, UUID owner, ItemStack load) {
        h.setBlock(base, Blocks.CHEST);
        for (int i = 1; i <= chutes; i++) {
            h.setBlock(base.above(i), ProofRegistry.TALLY_CHUTE.get());
            ((TallyChuteBlockEntity) h.getBlockEntity(base.above(i))).setOwner(owner);
        }
        BlockPos hopper = base.above(chutes + 1);
        h.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        ((HopperBlockEntity) h.getBlockEntity(hopper)).setItem(0, load);
        return hopper;
    }

    private static int chestCount(GameTestHelper h, BlockPos pos, net.minecraft.world.item.Item item) {
        ChestBlockEntity c = (ChestBlockEntity) h.getBlockEntity(pos);
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    /**
     * A hopper line through one chute and through a stack of two: every item arrives in the chest, the team counts
     * each item once, and item, tag and Age criteria follow the count. A chute over a full chest takes nothing.
     */
    @GameTest(template = "shrine_area", batch = "firmages_10_proof", timeoutTicks = 400)
    public static void tallyHopperLine(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = spawn(h, "gt_tally");
        UUID team = TallyService.teamOf(p.getUUID());
        BlockPos cobbleChest = new BlockPos(2, 1, 2);
        BlockPos dirtChest = new BlockPos(5, 1, 2);
        BlockPos fullChest = new BlockPos(8, 1, 2);
        line(h, cobbleChest, 1, p.getUUID(), new ItemStack(Items.COBBLESTONE, 10));
        line(h, dirtChest, 2, p.getUUID(), new ItemStack(Items.DIRT, 5));
        BlockPos fullHopper = line(h, fullChest, 1, p.getUUID(), new ItemStack(Items.COBBLESTONE, 3));
        ChestBlockEntity full = (ChestBlockEntity) h.getBlockEntity(fullChest);
        for (int i = 0; i < full.getContainerSize(); i++) full.setItem(i, new ItemStack(Items.STONE, 64));
        AgeId[] ageAtStart = new AgeId[1];
        h.startSequence()
            .thenExecute(() -> ageAtStart[0] = dev.firmages.core.immersion.AgeStatus.current(dev.firmages.core.age.AgeService.state(server).snapshot()))
            .thenWaitUntil(() -> {
                h.assertTrue(chestCount(h, cobbleChest, Items.COBBLESTONE) == 10, "10 cobblestone through one chute");
                h.assertTrue(chestCount(h, dirtChest, Items.DIRT) == 5, "5 dirt through two chutes");
            })
            .thenIdle(20)
            .thenExecute(() -> {
                TallyCounts c = TallyData.get(server).of(team);
                h.assertTrue(c.total("minecraft:cobblestone") == 10, "cobblestone counted once each: " + c.view());
                h.assertTrue(c.total("minecraft:dirt") == 5, "a stack of two chutes counts once: " + c.view());
                h.assertTrue(((HopperBlockEntity) h.getBlockEntity(fullHopper)).getItem(0).getCount() == 3, "a full outlet takes nothing");
                h.assertTrue(chestCount(h, fullChest, Items.COBBLESTONE) == 0, "nothing squeezed into the full chest");
                TallyChuteBlockEntity top = (TallyChuteBlockEntity) h.getBlockEntity(dirtChest.above(2));
                TallyChuteBlockEntity bottom = (TallyChuteBlockEntity) h.getBlockEntity(dirtChest.above(1));
                h.assertTrue(top.passed() == 5 && bottom.passed() == 5 && "minecraft:dirt".equals(bottom.lastItem()) && bottom.lastTeamCount() == 5,
                    "each chute knows what passed it; the lowest one counted for the team");
                TallyService.fireDirty(server);
                h.assertTrue(done(p, rl("test/tally_cobble")), "item criterion (10 cobblestone)");
                h.assertTrue(done(p, rl("test/tally_dirt_tag")), "tag criterion (#minecraft:dirt, 5)");
                h.assertTrue(ageAtStart[0] == AgeId.AGE_9 || !done(p, rl("test/tally_late")), "Age criterion: nothing counted in age_9");
            })
            .thenExecute(() -> DebugPlayerCommands.despawn(p))
            .thenSucceed();
    }

    /** The tally is SavedData: a save and load keeps every team, Age and item. */
    @GameTest(template = "empty", batch = "firmages_10_proof")
    public static void tallySurvivesSave(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        UUID team = UUID.nameUUIDFromBytes("gt_tally_save".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        TallyService.count(server, team, "minecraft:iron_ingot", 7);
        TallyData data = TallyData.get(server);
        CompoundTag tag = data.save(new CompoundTag(), server.registryAccess());
        TallyData back = TallyData.load(tag, server.registryAccess());
        h.assertTrue(back.of(team).total("minecraft:iron_ingot") == 7, "count after load: " + back.of(team).view());
        h.assertTrue(back.of(team).view().equals(data.of(team).view()), "same Ages and items after load");
        h.assertTrue(data.isDirty(), "a count marks the tally for saving");
        h.succeed();
    }

    /** Two members of one party: a chute owned by one counts for the party, and both earn the criterion. */
    @GameTest(template = "shrine_area", batch = "firmages_10_proof_team", timeoutTicks = 400)
    public static void tallyCountsForTheParty(GameTestHelper h) {
        if (!ModList.get().isLoaded(FtbTeamsCompat.MOD_ID) || !FTBTeamsAPI.api().isManagerLoaded()) {
            h.succeed();
            return;
        }
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer a = spawn(h, "gt_tally_a");
        ServerPlayer b = spawn(h, "gt_tally_b");
        PartyTeam[] party = new PartyTeam[1];
        BlockPos chest = new BlockPos(3, 1, 3);
        h.startSequence()
            .thenExecute(() -> {
                try {
                    Team t = FTBTeamsAPI.api().getManager().createPartyTeam(a, "gt_tally", null, null);
                    party[0] = (PartyTeam) t;
                    party[0].join(b);
                } catch (Exception e) {
                    throw new IllegalStateException("party for the tally test: " + e, e);
                }
                h.assertTrue(TallyService.teamOf(a.getUUID()).equals(party[0].getId()) && TallyService.teamOf(b.getUUID()).equals(party[0].getId()),
                    "both count for the party");
                line(h, chest, 1, a.getUUID(), new ItemStack(Items.GRAVEL, 4));
            })
            .thenWaitUntil(() -> h.assertTrue(chestCount(h, chest, Items.GRAVEL) == 4, "4 gravel through the chute"))
            .thenExecute(() -> {
                TallyService.fireDirty(server);
                h.assertTrue(TallyData.get(server).of(party[0].getId()).total("minecraft:gravel") == 4, "the party's tally");
                h.assertTrue(done(a, rl("test/tally_team")) && done(b, rl("test/tally_team")), "both members earn the criterion");
            })
            .thenExecute(() -> {
                try {
                    party[0].forceDisband(server.createCommandSourceStack());
                } catch (Exception e) {
                    FirmagesCore.LOGGER.warn("GameTest tally party: cannot disband: {}", e.toString());
                }
                DebugPlayerCommands.despawn(a);
                DebugPlayerCommands.despawn(b);
            })
            .thenSucceed();
    }

    /**
     * The silo launch with stand-ins: a boat tagged as a rocket on a lodestone pad at the bottom of a pit eight deep,
     * a hatch probe that reports a 150-block contraption while the rocket waits, and the launch (a tag). The crew earns
     * {@code firmages:silo_launch}; the decision also refuses a shallow pit, a launch without crew and one without a
     * hatch.
     */
    @GameTest(template = "silo_area", batch = "firmages_10_proof", timeoutTicks = 200)
    public static void siloLaunchWithStandIns(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        AtomicInteger hatch = new AtomicInteger(150);
        SiloLaunch.useStandIns(new SiloLaunch.Rockets() {
            @Override
            public boolean isRocket(Entity e) {
                return e.getTags().contains(ROCKET_TAG);
            }

            @Override
            public boolean launched(Entity e) {
                return e.getTags().contains(LAUNCHED_TAG);
            }

            @Override
            public boolean isPad(net.minecraft.world.level.block.state.BlockState state) {
                return state.is(Blocks.LODESTONE);
            }
        }, (l, box) -> hatch.get());
        BlockPos padRel = new BlockPos(5, 1, 5);
        BlockPos pad = h.absolutePos(padRel);
        h.setBlock(padRel, Blocks.LODESTONE);
        // a 5 x 5 pit: walls 3 blocks from the pad on all four sides, 8 high above the pad
        for (int y = 2; y <= 9; y++) {
            for (int i = 2; i <= 8; i++) {
                h.setBlock(new BlockPos(2, y, i), Blocks.STONE);
                h.setBlock(new BlockPos(8, y, i), Blocks.STONE);
                h.setBlock(new BlockPos(i, y, 2), Blocks.STONE);
                h.setBlock(new BlockPos(i, y, 8), Blocks.STONE);
            }
        }
        ServerPlayer p = DebugPlayerCommands.spawn(server, level, "gt_silo", Vec3.atBottomCenterOf(pad.above()));
        p.setGameMode(GameType.SURVIVAL);
        h.assertTrue(SiloLaunch.judge(level, pad, List.of(p), true) == SiloLaunch.Result.SILO, "deep pit, crew and hatch: a silo");
        h.assertTrue(SiloLaunch.judge(level, pad, List.of(), true) == SiloLaunch.Result.NO_CREW, "no crew");
        h.assertTrue(SiloLaunch.judge(level, pad, List.of(p), false) == SiloLaunch.Result.NO_HATCH, "no hatch");
        h.setBlock(new BlockPos(8, 9, 5), Blocks.AIR); // one wall column on the east side one block short
        for (int i = 2; i <= 8; i++) h.setBlock(new BlockPos(8, 9, i), Blocks.AIR);
        h.assertTrue(SiloLaunch.judge(level, pad, List.of(p), true) == SiloLaunch.Result.SHALLOW, "seven deep on one side: shallow");
        for (int i = 2; i <= 8; i++) h.setBlock(new BlockPos(8, 9, i), Blocks.STONE);

        Boat boat = EntityType.BOAT.create(level);
        boat.moveTo(pad.getX() + 0.5, pad.getY() + 1, pad.getZ() + 0.5);
        boat.addTag(ROCKET_TAG);
        level.addFreshEntity(boat);
        h.startSequence()
            .thenExecute(() -> {
                h.assertTrue(p.startRiding(boat, true), "the crew boards the rocket");
                SiloLaunch.tick(server); // on the pad, hatch moving
                hatch.set(0);           // the hatch has opened and become blocks again
                boat.addTag(LAUNCHED_TAG);
                SiloLaunch.tick(server);
                h.assertTrue(done(p, ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "silo_launch")), "the crew earns firmages:silo_launch");
            })
            .thenExecute(() -> {
                p.stopRiding();
                boat.discard();
                SiloLaunch.useStandIns(null, null);
                DebugPlayerCommands.despawn(p);
            })
            .thenSucceed();
    }
}
