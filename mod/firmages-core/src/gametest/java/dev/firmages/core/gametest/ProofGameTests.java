package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.shrine.ShrineProof;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
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
 * Level G tests of proof of work (SPEC §22, §35): the Tally Chute as a tithe (it takes and consumes only what the
 * Industrial Age proof of the test datapack still wants, refuses the rest, counts nothing twice; the criterion with
 * item, tag and Age), its team (two party members both earn the criterion), the saved tally surviving a save and
 * load, and the silo launch with stand-ins for Ad Astra's rocket and
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

    private static void ok(GameTestHelper h, boolean condition, String message) {
        if (!condition) FirmagesCore.LOGGER.error("Proof GameTest assertion failed: {}", message);
        h.assertTrue(condition, message);
    }

    private static boolean reloadIdle(MinecraftServer s) {
        return AgeService.status(s).phase() == ReloadScheduler.Phase.IDLE;
    }

    private static void agesUpTo(MinecraftServer server, AgeId last) {
        for (int i = AgeId.all().size() - 1; i > last.ordinal(); i--) AgeService.simulate(server, AgeId.all().get(i), false);
        for (int i = 0; i <= last.ordinal(); i++) AgeService.simulate(server, AgeId.all().get(i), true);
    }

    /** A Tally Chute at {@code chute} owned by {@code owner}, a hopper (pointing down) above it with {@code load}. */
    private static BlockPos line(GameTestHelper h, BlockPos chute, UUID owner, ItemStack load) {
        h.setBlock(chute, ProofRegistry.TALLY_CHUTE.get());
        ((TallyChuteBlockEntity) h.getBlockEntity(chute)).setOwner(owner);
        BlockPos hopper = chute.above();
        h.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        ((HopperBlockEntity) h.getBlockEntity(hopper)).setItem(0, load);
        return hopper;
    }

    private static int hopperCount(GameTestHelper h, BlockPos pos, net.minecraft.world.item.Item item) {
        HopperBlockEntity c = (HopperBlockEntity) h.getBlockEntity(pos);
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    /**
     * The chute is a tithe (SPEC §22.1, §35; review fix 0.12.0). In the Industrial Age the test datapack asks for eight
     * iron ingots: two hopper lines of 5 and 10 iron give exactly eight to the chutes, which consume them, and the rest
     * stays in the hoppers; gold (not asked) stays in its hopper; a hand gives nothing once the proof is met; item, tag
     * and Age criteria follow the count; and an ingot taken once is gone, so nothing can send it around again: the
     * count stays at eight while the line idles.
     */
    @GameTest(template = "shrine_area", batch = "firmages_10_proof_tally", timeoutTicks = 1200)
    public static void tallyChuteTakesTheProof(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer[] p = new ServerPlayer[1];
        UUID[] team = new UUID[1];
        BlockPos chuteA = new BlockPos(2, 1, 2);
        BlockPos chuteB = new BlockPos(5, 1, 2);
        BlockPos chuteGold = new BlockPos(8, 1, 2);
        BlockPos[] hoppers = new BlockPos[3];
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                agesUpTo(server, AgeId.AGE_4);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_4 setup still running"))
            .thenExecute(() -> {
                p[0] = spawn(h, "gt_tally");
                team[0] = TallyService.teamOf(p[0].getUUID());
                ok(h, ShrineProof.room(server, p[0].getUUID(), "minecraft:iron_ingot") == 8,
                    "the test datapack asks for eight iron ingots in the Industrial Age: " + ShrineProof.room(server, p[0].getUUID(), "minecraft:iron_ingot"));
                ok(h, ShrineProof.room(server, p[0].getUUID(), "minecraft:gold_ingot") == 0, "gold is not asked");
                hoppers[0] = line(h, chuteA, p[0].getUUID(), new ItemStack(Items.IRON_INGOT, 5));
                hoppers[1] = line(h, chuteB, p[0].getUUID(), new ItemStack(Items.IRON_INGOT, 10));
                hoppers[2] = line(h, chuteGold, p[0].getUUID(), new ItemStack(Items.GOLD_INGOT, 4));
            })
            .thenWaitUntil(() -> h.assertTrue(hopperCount(h, hoppers[0], Items.IRON_INGOT) + hopperCount(h, hoppers[1], Items.IRON_INGOT) == 7,
                "eight of fifteen iron taken"))
            .thenIdle(40)
            .thenExecute(() -> {
                TallyCounts c = TallyData.get(server).of(team[0]);
                ok(h, c.total("minecraft:iron_ingot") == 8, "eight iron counted: " + c.view());
                ok(h, hopperCount(h, hoppers[0], Items.IRON_INGOT) + hopperCount(h, hoppers[1], Items.IRON_INGOT) == 7, "the rest stays in the hoppers");
                ok(h, hopperCount(h, hoppers[2], Items.GOLD_INGOT) == 4 && c.total("minecraft:gold_ingot") == 0, "gold refused and kept");
                TallyChuteBlockEntity a = (TallyChuteBlockEntity) h.getBlockEntity(chuteA);
                TallyChuteBlockEntity b = (TallyChuteBlockEntity) h.getBlockEntity(chuteB);
                ok(h, a.passed() + b.passed() == 8 && "minecraft:iron_ingot".equals(a.lastItem()), "each chute knows what it took: " + a.passed() + " + " + b.passed());
                ok(h, ShrineProof.room(server, p[0].getUUID(), "minecraft:iron_ingot") == 0, "the proof is met, the chutes want nothing more");
                ItemStack hand = new ItemStack(Items.IRON_INGOT, 2);
                ok(h, a.take(hand) == 0 && hand.getCount() == 2, "a hand gives nothing once the proof is met");
                TallyService.fireDirty(server);
                ok(h, done(p[0], rl("test/tally_iron")), "item criterion (8 iron ingots)");
                ok(h, done(p[0], rl("test/tally_iron_tag")), "tag criterion (#c:ingots/iron, 8)");
                ok(h, !done(p[0], rl("test/tally_late")), "Age criterion: nothing counted in age_9");
                int loose = 0;
                for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(16))) {
                    if (e.getItem().is(Items.IRON_INGOT)) loose += e.getItem().getCount();
                }
                ok(h, loose == 0, "the taken iron is gone, not dropped: " + loose);
            })
            .thenIdle(100)
            .thenExecute(() -> ok(h, TallyData.get(server).of(team[0]).total("minecraft:iron_ingot") == 8, "an idle line counts nothing twice"))
            .thenExecute(() -> {
                DebugPlayerCommands.despawn(p[0]);
                // the hoppers still hold seven iron: without their chutes nothing is fed once the count is forgotten
                for (BlockPos c : List.of(chuteA, chuteB, chuteGold)) h.setBlock(c, Blocks.AIR);
                TallyData.get(server).forget(team[0]); // offeringWaitsForProof reads the best team's count
                agesUpTo(server, AgeId.DAWN);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> {
                ok(h, ShrineProof.room(server, p[0].getUUID(), "minecraft:iron_ingot") == 0, "in Dawn no proof is asked");
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(false);
            })
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
        data.forget(team);
        h.succeed();
    }

    /** Two members of one party: a chute owned by one counts for the party, and both earn the criterion. */
    @GameTest(template = "shrine_area", batch = "firmages_10_proof_team", timeoutTicks = 1200)
    public static void tallyCountsForTheParty(GameTestHelper h) {
        if (!ModList.get().isLoaded(FtbTeamsCompat.MOD_ID) || !FTBTeamsAPI.api().isManagerLoaded()) {
            h.succeed();
            return;
        }
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer[] pl = new ServerPlayer[2];
        PartyTeam[] party = new PartyTeam[1];
        BlockPos chute = new BlockPos(3, 1, 3);
        BlockPos[] hopper = new BlockPos[1];
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                agesUpTo(server, AgeId.AGE_4);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_4 setup still running"))
            .thenExecute(() -> {
                pl[0] = spawn(h, "gt_tally_a");
                pl[1] = spawn(h, "gt_tally_b");
                try {
                    Team t = FTBTeamsAPI.api().getManager().createPartyTeam(pl[0], "gt_tally", null, null);
                    party[0] = (PartyTeam) t;
                    party[0].join(pl[1]);
                } catch (Exception e) {
                    throw new IllegalStateException("party for the tally test: " + e, e);
                }
                h.assertTrue(TallyService.teamOf(pl[0].getUUID()).equals(party[0].getId()) && TallyService.teamOf(pl[1].getUUID()).equals(party[0].getId()),
                    "both count for the party");
                hopper[0] = line(h, chute, pl[0].getUUID(), new ItemStack(Items.IRON_INGOT, 4));
            })
            .thenWaitUntil(() -> h.assertTrue(hopperCount(h, hopper[0], Items.IRON_INGOT) == 0, "4 iron into the chute"))
            .thenExecute(() -> {
                TallyService.fireDirty(server);
                h.assertTrue(TallyData.get(server).of(party[0].getId()).total("minecraft:iron_ingot") == 4, "the party's tally");
                h.assertTrue(ShrineProof.room(server, pl[1].getUUID(), "minecraft:iron_ingot") == 4, "the other member's chute wants the party's rest");
                h.assertTrue(done(pl[0], rl("test/tally_team")) && done(pl[1], rl("test/tally_team")), "both members earn the criterion");
            })
            .thenExecute(() -> {
                try {
                    party[0].forceDisband(server.createCommandSourceStack());
                } catch (Exception e) {
                    FirmagesCore.LOGGER.warn("GameTest tally party: cannot disband: {}", e.toString());
                }
                h.setBlock(chute, Blocks.AIR);
                TallyData.get(server).forget(party[0].getId());
                DebugPlayerCommands.despawn(pl[0]);
                DebugPlayerCommands.despawn(pl[1]);
                agesUpTo(server, AgeId.DAWN);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> ServerConfig.DEBUG_ALLOW_SIMULATE.set(false))
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
