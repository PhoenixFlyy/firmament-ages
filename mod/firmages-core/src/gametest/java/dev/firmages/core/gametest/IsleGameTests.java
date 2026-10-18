package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.isles.IsleLayout;
import dev.firmages.core.isles.IslePiece;
import dev.firmages.core.isles.IsleRegistry;
import dev.firmages.core.isles.IsleSavedData;
import dev.firmages.core.isles.IsleService;
import dev.firmages.core.isles.IsleShape;
import dev.firmages.core.isles.IsleSize;
import dev.firmages.core.isles.IsleSpec;
import dev.firmages.core.isles.IsleWriter;
import dev.firmages.core.isles.IsleRuin;
import dev.firmages.core.isles.IsleRuinSite;
import dev.firmages.core.isles.WindSealBlock;
import dev.firmages.core.sky.DeferredSites;
import dev.firmages.core.wayshrine.WayshrineCoreBlockEntity;
import dev.firmages.core.wayshrine.WayshrinePlacement;
import dev.firmages.core.wayshrine.WayshrinePointer;
import dev.firmages.core.wayshrine.WayshrineSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Level G tests of the Drifting Isles (SPEC §30.10). The run has no TerraFirmaCraft, so the tree pockets are vanilla
 * dirt and grass and no tree grows; the test datapack puts the two crystal blocks into {@code age_blocks/age_4}. Each
 * test that changes the Ages runs in its own batch (the Ages are global).
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class IsleGameTests {
    /** The centre column of an isle in the 25 by 26 by 25 {@code sky_area}. */
    private static final int CX = 12;
    private static final int CZ = 12;
    private static final int TOP = 18;

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        IsleService.setNaturalEvents(false); // no fallback cluster of its own in the test world
        event.register(IsleGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean c, String msg) {
        if (!c) h.fail(msg);
    }

    private static void agesUpTo(MinecraftServer server, AgeId last) {
        for (int i = AgeId.all().size() - 1; i > last.ordinal(); i--) AgeService.simulate(server, AgeId.all().get(i), false);
        for (int i = 0; i <= last.ordinal(); i++) AgeService.simulate(server, AgeId.all().get(i), true);
    }

    private static boolean reloadIdle(MinecraftServer s) {
        return AgeService.status(s).phase() == ReloadScheduler.Phase.IDLE;
    }

    private static IsleSpec spec(GameTestHelper h, int radius, IsleSize size, boolean shrine, long seed) {
        BlockPos c = h.absolutePos(new BlockPos(CX, TOP, CZ));
        return new IsleSpec(c.getX(), c.getZ(), radius, size, c.getY(), IsleLayout.depth(radius), seed, size != IsleSize.SMALL, shrine, 1,
            IsleSpec.crystalsFor(radius, 3F), false);
    }

    private static void clear(GameTestHelper h) {
        for (BlockPos p : BlockPos.betweenClosed(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(24, 25, 24)))) {
            h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The chunk columns an isle covers, as worldgen boxes (each chunk writes only its own part). */
    private static List<BoundingBox> chunkBoxes(IsleSpec s) {
        BoundingBox b = IslePiece.box(s);
        List<BoundingBox> out = new ArrayList<>();
        for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
            for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) out.add(new BoundingBox(cx << 4, b.minY(), cz << 4, (cx << 4) + 15, b.maxY(), (cz << 4) + 15));
        }
        return out;
    }

    /**
     * §30.10 shapeHasNoOre and isleKeepsSkyLight: a radius-12 medium isle written chunk by chunk holds only skystone, the
     * two crystal blocks and the dirt pocket; the air under it keeps sky light 15.
     */
    @GameTest(template = "sky_area", batch = "firmages_13_isles_shape", timeoutTicks = 400)
    public static void shapeAndSkyLight(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        IsleSpec s = new IsleSpec(h.absolutePos(new BlockPos(CX, 0, CZ)).getX(), h.absolutePos(new BlockPos(CX, 0, CZ)).getZ(), 11, IsleSize.MEDIUM,
            h.absolutePos(new BlockPos(0, 22, 0)).getY(), IsleLayout.depth(11), 77L, true, false, 1, IsleSpec.crystalsFor(11, 3F), false);
        IsleShape shape = IsleShape.of(s);
        BlockPos[] under = new BlockPos[1];
        h.startSequence()
            .thenExecute(() -> {
                clear(h);
                for (BoundingBox box : chunkBoxes(s)) IsleWriter.body(level, shape, box);
                Map<Block, Integer> seen = new HashMap<>();
                for (BlockPos p : BlockPos.betweenClosed(h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(24, 25, 24)))) {
                    BlockState st = level.getBlockState(p);
                    if (!st.isAir()) seen.merge(st.getBlock(), 1, Integer::sum);
                }
                List<Block> allowed = List.of(IsleRegistry.SKYSTONE.get(), IsleRegistry.CRYSTAL_CLUSTER.get(), IsleRegistry.LODE.get(), Blocks.DIRT,
                    Blocks.GRASS_BLOCK);
                for (Block b : seen.keySet()) ok(h, allowed.contains(b), "an isle holds " + b);
                ok(h, seen.getOrDefault(IsleRegistry.SKYSTONE.get(), 0) > 500, "skystone body: " + seen);
                ok(h, seen.getOrDefault(IsleRegistry.LODE.get(), 0) == 1, "one lode: " + seen);
                ok(h, seen.getOrDefault(IsleRegistry.CRYSTAL_CLUSTER.get(), 0) >= 2, "crystal clusters: " + seen);
                ok(h, level.getBlockState(new BlockPos(s.x(), shape.bottomAt(0, 0), s.z())).is(IsleRegistry.LODE.get()), "the lode is the tip");
                // the air two blocks under the body, halfway to the rim
                int dx = 5;
                under[0] = new BlockPos(s.x() + dx, shape.bottomAt(dx, 0) - 3, s.z());
                ok(h, level.getBlockState(under[0]).isAir() && level.getBlockState(under[0].above()).isAir(), "open air under the body at " + under[0]);
                ok(h, !level.getBlockState(under[0].above(3)).isAir(), "the body is above " + under[0]);
            })
            .thenWaitUntil(() -> ok(h, level.getBrightness(LightLayer.SKY, under[0]) == 15,
                "sky light under the isle " + level.getBrightness(LightLayer.SKY, under[0])))
            .thenExecute(() -> clear(h))
            .thenSucceed();
    }

    private static int crystals(ServerLevel level, BlockPos pos) {
        int n = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3), e -> e.getItem().is(IsleRegistry.FIRMAMENT_CRYSTAL.get()))) {
            n += e.getItem().getCount();
        }
        return n;
    }

    /** §30.10 lodeReforms: a player and a fake player each get two crystals; the lode stands again on the next tick, no duplicate. */
    @GameTest(template = "empty", batch = "firmages_13_isles_lode", timeoutTicks = 200)
    public static void lodeReforms(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos pos = h.absolutePos(rel);
        long[] t = new long[1];
        h.startSequence()
            .thenExecute(() -> {
                agesUpTo(level.getServer(), AgeId.AGE_4);
                h.setBlock(rel, IsleRegistry.LODE.get());
                ServerPlayer p = DebugPlayerCommands.spawn(level.getServer(), level, "isle_miner", Vec3.atCenterOf(pos.above(2)));
                p.setGameMode(GameType.SURVIVAL);
                boolean broke = p.gameMode.destroyBlock(pos);
                DebugPlayerCommands.despawn(p);
                ok(h, broke, "the player breaks the lode");
                ok(h, crystals(level, pos) == 2, "the player got " + crystals(level, pos) + " crystals");
                ok(h, level.getBlockState(pos).isAir(), "the lode is gone this tick");
                ok(h, IsleSavedData.get(level.getServer()).reform().contains(GlobalPos.of(level.dimension(), pos)), "queued to re-form");
                t[0] = level.getGameTime();
            })
            .thenWaitUntil(() -> ok(h, level.getBlockState(pos).is(IsleRegistry.LODE.get()), "the lode is not back"))
            .thenExecute(() -> {
                ok(h, level.getGameTime() - t[0] <= 2, "the lode came back after " + (level.getGameTime() - t[0]) + " ticks");
                FakePlayer fake = FakePlayerFactory.getMinecraft(level);
                fake.setGameMode(GameType.SURVIVAL);
                ok(h, fake.gameMode.destroyBlock(pos), "the fake player (a drill) breaks the lode in the Industrial Age");
                ok(h, crystals(level, pos) == 4, "after the fake player " + crystals(level, pos) + " crystals");
            })
            .thenWaitUntil(() -> ok(h, level.getBlockState(pos).is(IsleRegistry.LODE.get()), "the lode is not back after the fake player"))
            .thenIdle(5)
            .thenExecute(() -> {
                ok(h, crystals(level, pos) == 4, "a duplicate drop: " + crystals(level, pos));
                ok(h, IsleSavedData.get(level.getServer()).reform().isEmpty(), "the queue is empty");
                // an operator's fill stays: the lode does not come back into a block
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            })
            .thenIdle(3)
            .thenExecute(() -> {
                ok(h, level.getBlockState(pos).is(Blocks.STONE), "a block set in the lode's place stays");
                level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).forEach(e -> e.discard());
            })
            .thenWaitUntil(() -> ok(h, reloadIdle(level.getServer()), "the reload after the Age change still runs"))
            .thenSucceed();
    }

    /** §30.10 lodeLockedBeforeAge4: OreGuard cancels a fake player's break before the Industrial Age; nothing re-forms. */
    @GameTest(template = "empty", batch = "firmages_13_isles_guard", timeoutTicks = 100)
    public static void lodeLockedBeforeAge4(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos pos = h.absolutePos(rel);
        agesUpTo(level.getServer(), AgeId.AGE_3);
        h.setBlock(rel, IsleRegistry.LODE.get());
        FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        fake.setGameMode(GameType.SURVIVAL);
        ok(h, !fake.gameMode.destroyBlock(pos), "a fake player broke the lode in the Arcane Age");
        h.assertBlockPresent(IsleRegistry.LODE.get(), rel);
        ok(h, crystals(level, pos) == 0, "crystals dropped before the Industrial Age");
        ok(h, IsleSavedData.get(level.getServer()).reform().isEmpty(), "a refused break queued a re-form");
        agesUpTo(level.getServer(), AgeId.AGE_4);
        h.succeedWhen(() -> ok(h, reloadIdle(level.getServer()), "the reload after the Age change still runs"));
    }

    /**
     * §30.10 fallbackPlacesWhenLoaded and S6B: a fallback cluster far away places nothing while its chunks are unloaded,
     * then at most 64 blocks per tick after a forceload; the main isle carries the Wind Shrine, whose core registers, and
     * the pointer names it from the Industrial Age only.
     */
    @GameTest(template = "empty", batch = "firmages_13_isles_fallback", timeoutTicks = 4000)
    public static void fallbackPlacesWhenLoaded(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        int x = 6008;
        int z = -6008;
        IsleSavedData.Cluster[] c = new IsleSavedData.Cluster[1];
        List<ChunkPos> forced = new ArrayList<>();
        h.startSequence()
            .thenExecute(() -> {
                agesUpTo(server, AgeId.AGE_4);
                Optional<IsleSavedData.Cluster> k = IsleService.placeCluster(level, x, z, IsleSavedData.Origin.FALLBACK, true, level.getSharedSpawnPos(), false);
                ok(h, k.isPresent(), "a flat world has room for the cluster");
                c[0] = k.get();
                ok(h, c[0].isles.get(0).shrine(), "the fallback cluster carries the Wind Shrine");
                ok(h, IsleSavedData.get(server).fallback().map(f -> f.id).orElse(-1L) == c[0].id, "recorded as the fallback");
            })
            .thenIdle(20)
            .thenExecute(() -> {
                ok(h, !c[0].done() && IsleService.jobs() == 0, "nothing placed while the chunks are unloaded");
                ok(h, level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null, "the far chunk is not loaded");
                IsleService.resetMaxPerTick();
                for (IsleSpec s : c[0].isles) {
                    BoundingBox b = IslePiece.box(s);
                    for (int cx = (b.minX() - 8) >> 4; cx <= (b.maxX() + 8) >> 4; cx++) {
                        for (int cz = (b.minZ() - 8) >> 4; cz <= (b.maxZ() + 8) >> 4; cz++) {
                            ChunkPos cp = new ChunkPos(cx, cz);
                            if (!forced.contains(cp)) {
                                forced.add(cp);
                                level.setChunkForced(cx, cz, true);
                            }
                        }
                    }
                }
            })
            .thenWaitUntil(() -> ok(h, c[0].done(), "the cluster is not placed yet (" + IsleService.jobs() + " jobs)"))
            .thenExecute(() -> {
                ok(h, IsleService.maxPerTick() <= IsleService.BLOCKS_PER_TICK, "more than 64 blocks in one tick: " + IsleService.maxPerTick());
                for (int i = 0; i < c[0].isles.size(); i++) ok(h, !c[0].skipped[i], "isle " + i + " was skipped");
                IsleSpec main = c[0].isles.get(0);
                BlockPos core = IsleWriter.shrineCore(main);
                ok(h, level.getBlockState(core.below()).is(IsleRegistry.SKYSTONE.get()), "skystone under the shrine floor");
                ok(h, level.getBlockEntity(core) instanceof WayshrineCoreBlockEntity, "the Wind Shrine's core stands at " + core);
                ok(h, c[0].shrineCore().equals(Optional.of(core)), "the record names the core");
                ok(h, !DeferredSites.registered(Level.OVERWORLD, IsleService.KIND, c[0].id * 16), "a placed isle leaves the chunk index");
                BlockPos near = core.offset(40, 0, 40);
                ok(h, IsleService.nearestShrine(level, near, 512, p -> false).equals(Optional.of(core)), "the isle search finds the Wind Shrine");
                Optional<WayshrinePointer.Target> t4 = WayshrinePointer.nearest(level, near, false);
                ok(h, t4.isPresent() && t4.get().pos().equals(core) && WayshrinePlacement.ISLE_TYPE.equals(t4.get().type()),
                    "the pointer names the Wind Shrine in the Industrial Age: " + t4);
                agesUpTo(server, AgeId.AGE_3);
                Optional<WayshrinePointer.Target> t3 = WayshrinePointer.nearest(level, near, false);
                ok(h, t3.isEmpty() || !WayshrinePlacement.ISLE_TYPE.equals(t3.get().type()), "the pointer names an isle shrine before the Industrial Age: " + t3);
                agesUpTo(server, AgeId.AGE_4);
                // leave the world as it was for the other tests
                forced.forEach(cp -> level.setChunkForced(cp.x, cp.z, false));
                IsleSavedData.get(server).remove(c[0].id);
                WayshrineSavedData sd = WayshrineSavedData.get(server);
                sd.remove(GlobalPos.of(Level.OVERWORLD, core));
            })
            .thenWaitUntil(() -> ok(h, reloadIdle(server), "the reload after the Age change still runs"))
            .thenSucceed();
    }

    /**
     * S6B in the worldgen form: the Wind Shrine's isle written chunk box by chunk box (as {@code IslePiece.postProcess} does)
     * gets a whole body and one core of the Wind Shrine on its flat top.
     */
    @GameTest(template = "sky_area", batch = "firmages_13_isles_piece", timeoutTicks = 200)
    public static void pieceShrineByChunks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        IsleSpec s = spec(h, 10, IsleSize.MEDIUM, true, 4242L);
        IsleShape shape = IsleShape.of(s);
        clear(h);
        List<BoundingBox> boxes = chunkBoxes(s);
        ok(h, boxes.size() >= 2, "the isle spans " + boxes.size() + " chunk boxes");
        int cores = 0;
        for (BoundingBox box : boxes) {
            IsleWriter.body(level, shape, box);
            if (IsleWriter.shrine(level, s, box).isPresent()) cores++;
        }
        ok(h, cores == boxes.size(), "the shrine hook ran in every chunk box: " + cores);
        BlockPos core = IsleWriter.shrineCore(s);
        ok(h, level.getBlockEntity(core) instanceof WayshrineCoreBlockEntity, "the Wind Shrine's core stands at " + core);
        int[] wrong = new int[1];
        int[] total = new int[1];
        shape.emitAll((x, y, z, kind) -> {
            if (y >= s.top()) return; // the ruin's floor layer replaces the top
            total[0]++;
            if (!level.getBlockState(new BlockPos(x, y, z)).equals(IsleWriter.state(kind))) wrong[0]++;
        });
        ok(h, wrong[0] == 0, wrong[0] + " of " + total[0] + " body blocks differ after the chunk-by-chunk write");
        ok(h, level.getBlockState(core.above(8)).isAir() || core.above(8).getY() > h.absolutePos(new BlockPos(0, 25, 0)).getY(),
            "the ruin stays within eight blocks");
        WayshrineSavedData.get(level.getServer()).remove(GlobalPos.of(Level.OVERWORLD, core));
        clear(h);
        h.succeed();
    }

    /** G3 (I1B, SPEC §30.13): the ruin of a cluster, written chunk box by chunk box, stands in the isle's top surface. */
    @GameTest(template = "sky_area", batch = "firmages_13_isles_ruin", timeoutTicks = 400)
    public static void ruinPlacesByChunks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        IsleSpec s = null;
        for (long seed = 1; seed < 60 && s == null; seed++) {
            IsleSpec c = spec(h, 11, IsleSize.SMALL, false, seed);
            if (IsleRuinSite.site(c).isPresent()) s = c.withRuin(true);
        }
        ok(h, s != null, "no radius-11 isle with room for the ruin in 60 seeds");
        IsleSpec spec = s;
        IsleShape shape = IsleShape.of(spec);
        BlockPos core = IsleRuin.core(spec).orElseThrow();
        clear(h);
        for (BoundingBox box : chunkBoxes(spec)) {
            IsleWriter.body(level, shape, box);
            IsleRuin.place(level, spec, box);
        }
        int seals = 0;
        int first = 0;
        int lids = 0;
        int clusters = 0;
        for (BlockPos p : BlockPos.betweenClosed(core.offset(-4, -4, -4), core.offset(4, 4, 4))) {
            BlockState st = level.getBlockState(p);
            if (st.is(IsleRegistry.WIND_SEAL.get())) {
                seals++;
                if (st.getValue(WindSealBlock.FIRST)) first++;
                ok(h, p.getY() == core.getY() + 1, "a seal off the top surface at " + p.toShortString());
            }
            if (st.is(IsleRegistry.WIND_SEAL_LID.get())) {
                lids++;
                ok(h, p.getY() == core.getY(), "a lid off the top surface at " + p.toShortString());
            }
            if (st.is(IsleRegistry.CRYSTAL_CLUSTER.get()) && p.getY() < core.getY()) clusters++;
        }
        ok(h, seals == 4 && first == 1, "seals " + seals + ", worn " + first);
        ok(h, lids == 9, "lid blocks " + lids);
        ok(h, clusters >= 3, "crystal clusters in the chamber " + clusters);
        h.succeed();
    }

    /** G3 (I1B): the Wind Seals wake from the worn one clockwise; a wrong seal puts all back to sleep; the fourth opens the lid. */
    @GameTest(template = "empty", batch = "firmages_13_isles_seals", timeoutTicks = 200)
    public static void windSealPuzzle(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = new BlockPos(4, 2, 4);
        BlockState seal = IsleRegistry.WIND_SEAL.get().defaultBlockState();
        BlockPos n = c.north(3);
        BlockPos e = c.east(3);
        BlockPos so = c.south(3);
        BlockPos w = c.west(3);
        h.setBlock(n, seal.setValue(WindSealBlock.FACING, Direction.NORTH).setValue(WindSealBlock.FIRST, true));
        h.setBlock(e, seal.setValue(WindSealBlock.FACING, Direction.EAST));
        h.setBlock(so, seal.setValue(WindSealBlock.FACING, Direction.SOUTH));
        h.setBlock(w, seal.setValue(WindSealBlock.FACING, Direction.WEST));
        h.setBlock(c.below(), IsleRegistry.WIND_SEAL_LID.get());
        h.setBlock(c.below().east(), IsleRegistry.WIND_SEAL_LID.get());
        agesUpTo(level.getServer(), AgeId.AGE_3);
        ok(h, WindSealBlock.press(level, h.absolutePos(n)) == WindSealBlock.Outcome.ASLEEP, "a seal woke before the Industrial Age");
        agesUpTo(level.getServer(), AgeId.AGE_4);
        ok(h, WindSealBlock.press(level, h.absolutePos(e)) == WindSealBlock.Outcome.WRONG, "east first was taken");
        ok(h, WindSealBlock.press(level, h.absolutePos(n)) == WindSealBlock.Outcome.AWAKE, "the worn seal did not wake");
        ok(h, WindSealBlock.press(level, h.absolutePos(so)) == WindSealBlock.Outcome.WRONG, "south after north was taken");
        ok(h, !h.getBlockState(n).getValue(WindSealBlock.LIT), "a wrong seal left the worn one awake");
        ok(h, WindSealBlock.press(level, h.absolutePos(n)) == WindSealBlock.Outcome.AWAKE, "north");
        ok(h, WindSealBlock.press(level, h.absolutePos(e)) == WindSealBlock.Outcome.AWAKE, "east");
        ok(h, WindSealBlock.press(level, h.absolutePos(so)) == WindSealBlock.Outcome.AWAKE, "south");
        h.assertBlockPresent(IsleRegistry.WIND_SEAL_LID.get(), c.below());
        ok(h, WindSealBlock.press(level, h.absolutePos(w)) == WindSealBlock.Outcome.SOLVED, "west did not open the chamber");
        h.assertBlockNotPresent(IsleRegistry.WIND_SEAL_LID.get(), c.below());
        h.assertBlockNotPresent(IsleRegistry.WIND_SEAL_LID.get(), c.below().east());
        ok(h, WindSealBlock.press(level, h.absolutePos(w)) == WindSealBlock.Outcome.NOTHING, "a solved seal answered again");
        agesUpTo(level.getServer(), AgeId.DAWN);
        h.succeedWhen(() -> ok(h, reloadIdle(level.getServer()), "the reload after the Age change still runs"));
    }
}
