package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.compat.modonomicon.ShrineMultiblocks;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.shrine.ConsecratedBlock;
import dev.firmages.core.shrine.Consecration;
import dev.firmages.core.shrine.ShrineConsecration;
import dev.firmages.core.shrine.ShrineHeartBlock;
import dev.firmages.core.shrine.ShrineHeartBlockEntity;
import dev.firmages.core.shrine.ShrineRegistry;
import dev.firmages.core.shrine.ShrineSavedData;
import dev.firmages.core.shrine.ShrineService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Level G tests of the consecration and maintenance mode (SPEC §17, M7) on the tier-0 ring from vanilla stand-ins
 * (8 cobblestone = stone, 8 oak logs = pillar, 4 hay = trim): the ring validates in Age material, mixed and
 * consecrated; the prayer consecrates it from the heart outward with every original stored; survival breaking,
 * explosions, pistons and fluids leave it alone; maintenance (sneak-punch on the heart) lets it break with the
 * original drop, a placed original repairs it and is consecrated again when maintenance ends (also by its timer);
 * an awakened ring that stands in Age material is consecrated on its first validation (migration); removing the
 * heart turns everything back into the originals. Every test starts from its own Age set and restores Dawn.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class ConsecrationGameTests {
    private static final BlockPos HEART = new BlockPos(5, 1, 5);
    private static final BlockPos PLINTH = new BlockPos(5, 1, 3);
    private static final BlockPos STONE = new BlockPos(6, 1, 5);
    private static final ResourceLocation RING_0 = ResourceLocation.parse("firmages:shrine_ring_0");
    private static final int RING_BLOCKS = 20;

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(ConsecrationGameTests.class);
    }

    private static void buildRing0(GameTestHelper h) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) h.setBlock(HEART.offset(dx, 0, dz), Blocks.COBBLESTONE);
            }
        }
        for (int[] c : new int[][] {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
            h.setBlock(HEART.offset(c[0], 0, c[1]), Blocks.OAK_LOG);
            h.setBlock(HEART.offset(c[0], 1, c[1]), Blocks.OAK_LOG);
            h.setBlock(HEART.offset(c[0], 2, c[1]), Blocks.HAY_BLOCK);
        }
        h.setBlock(PLINTH, ShrineRegistry.OFFERING_PLINTH.get());
        h.setBlock(HEART, ShrineRegistry.SHRINE_HEART.get());
    }

    /** The 20 ring positions with a role, by role, in absolute coordinates. */
    private static List<BlockPos> rolePositions(GameTestHelper h, Consecration.Role role) {
        List<BlockPos> out = new ArrayList<>();
        if (role == Consecration.Role.STONE) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) out.add(h.absolutePos(HEART.offset(dx, 0, dz)));
            }
        }
        for (int[] c : new int[][] {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
            if (role == Consecration.Role.PILLAR) {
                out.add(h.absolutePos(HEART.offset(c[0], 0, c[1])));
                out.add(h.absolutePos(HEART.offset(c[0], 1, c[1])));
            }
            if (role == Consecration.Role.TRIM) out.add(h.absolutePos(HEART.offset(c[0], 2, c[1])));
        }
        return out;
    }

    private static List<BlockPos> allRolePositions(GameTestHelper h) {
        List<BlockPos> out = new ArrayList<>();
        for (Consecration.Role r : List.of(Consecration.Role.STONE, Consecration.Role.PILLAR, Consecration.Role.TRIM)) out.addAll(rolePositions(h, r));
        return out;
    }

    private static int consecratedCount(GameTestHelper h) {
        int n = 0;
        for (Consecration.Role r : List.of(Consecration.Role.STONE, Consecration.Role.PILLAR, Consecration.Role.TRIM)) {
            for (BlockPos p : rolePositions(h, r)) {
                BlockState s = h.getLevel().getBlockState(p);
                if (s.getBlock() instanceof ConsecratedBlock cb && cb.role() == r && s.getValue(ConsecratedBlock.ACCENT) == 0) n++;
            }
        }
        return n;
    }

    private static boolean originalsBack(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (BlockPos p : rolePositions(h, Consecration.Role.STONE)) if (!level.getBlockState(p).is(Blocks.COBBLESTONE)) return false;
        for (BlockPos p : rolePositions(h, Consecration.Role.PILLAR)) if (!level.getBlockState(p).is(Blocks.OAK_LOG)) return false;
        for (BlockPos p : rolePositions(h, Consecration.Role.TRIM)) if (!level.getBlockState(p).is(Blocks.HAY_BLOCK)) return false;
        return true;
    }

    private static void ok(GameTestHelper h, boolean condition, String message) {
        if (!condition) FirmagesCore.LOGGER.error("Consecration GameTest assertion failed: {}", message);
        h.assertTrue(condition, message);
    }

    private static boolean isKey(Optional<Component> c, String key) {
        return c.isPresent() && c.get().getContents() instanceof TranslatableContents t && t.getKey().equals(key);
    }

    private static String text(Optional<Component> c) {
        return c.map(x -> x.getContents() instanceof TranslatableContents t ? t.getKey() : x.getString()).orElse("<none>");
    }

    private static boolean reloadIdle(MinecraftServer s) {
        return AgeService.status(s).phase() == ReloadScheduler.Phase.IDLE;
    }

    /** Sets AgeState to exactly {@code dawn..last} through the simulate path. */
    private static void agesUpTo(MinecraftServer server, AgeId last) {
        for (int i = AgeId.all().size() - 1; i > last.ordinal(); i--) AgeService.simulate(server, AgeId.all().get(i), false);
        for (int i = 0; i <= last.ordinal(); i++) AgeService.simulate(server, AgeId.all().get(i), true);
    }

    private static ShrineHeartBlockEntity heartBe(GameTestHelper h) {
        return (ShrineHeartBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(HEART));
    }

    /** Sneak plus an empty-hand punch on the heart, through the real block-break action path. */
    private static void punchHeart(GameTestHelper h, ServerPlayer p) {
        p.setShiftKeyDown(true);
        p.gameMode.handleBlockBreakAction(h.absolutePos(HEART), ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP,
            h.getLevel().getMaxBuildHeight(), 0);
        p.setShiftKeyDown(false);
    }

    /** The ring validates from Age material, in any mix with consecrated blocks of the right roles, and fully consecrated. */
    @GameTest(template = "shrine_area", batch = "firmages_4_consecrate_valid", timeoutTicks = 1200)
    public static void ringValidatesBeforeDuringAfter(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        BlockPos heart = h.absolutePos(HEART);
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                agesUpTo(server, AgeId.AGE_0); // awakened 0: nothing is consecrated by itself
                buildRing0(h);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_0 setup still running"))
            .thenExecute(() -> {
                ok(h, ShrineMultiblocks.check(level, heart, RING_0).valid(), "ring 0 valid in Age material");
                List<BlockPos> stones = rolePositions(h, Consecration.Role.STONE);
                for (int i = 0; i < stones.size(); i += 2) level.setBlock(stones.get(i), ShrineRegistry.consecrated(Consecration.Role.STONE, 0), 3);
                ok(h, ShrineMultiblocks.check(level, heart, RING_0).valid(), "ring 0 valid half consecrated");
                for (Consecration.Role r : List.of(Consecration.Role.STONE, Consecration.Role.PILLAR, Consecration.Role.TRIM)) {
                    for (BlockPos p : rolePositions(h, r)) level.setBlock(p, ShrineRegistry.consecrated(r, 0), 3);
                }
                ShrineMultiblocks.RingCheck all = ShrineMultiblocks.check(level, heart, RING_0);
                ok(h, all.valid() && all.matched() == all.total() && all.cells().stream().filter(ShrineMultiblocks.Cell::consecrated).count() == RING_BLOCKS,
                    "ring 0 valid fully consecrated: " + all.matched() + "/" + all.total());
                ShrineHeartBlockEntity be = heartBe(h);
                ShrineService.validate(level, heart, be);
                ok(h, be.validRing() == 0, "heart sees ring 0 consecrated: validRing " + be.validRing());
                BlockPos s = h.absolutePos(STONE);
                level.setBlock(s, ShrineRegistry.consecrated(Consecration.Role.BRICK, 0), 3);
                ok(h, !ShrineMultiblocks.check(level, heart, RING_0).valid(), "a consecrated block of the wrong role does not fit");
                level.setBlock(s, ShrineRegistry.consecrated(Consecration.Role.STONE, 5), 3);
                ok(h, ShrineMultiblocks.check(level, heart, RING_0).valid(), "the right role fits whatever its accent");
                level.setBlock(s, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 3);
                ok(h, ShrineMultiblocks.check(level, heart, RING_0).valid(), "Age material next to consecrated blocks fits");
                ok(h, ShrineMultiblocks.positions(level, heart, RING_0, all.rotation(), 'P').equals(List.of(h.absolutePos(PLINTH))),
                    "pattern keys still map to their positions");
                h.setBlock(HEART, Blocks.AIR);
                agesUpTo(server, AgeId.DAWN);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> ServerConfig.DEBUG_ALLOW_SIMULATE.set(false))
            .thenSucceed();
    }

    /**
     * The whole cycle: prayer, consecration from the heart outward with the originals stored, protection, maintenance
     * by the sneak-punch with the original drop, repair, re-consecration when maintenance ends, the maintenance timer,
     * and the heart's removal turning everything back.
     */
    @GameTest(template = "shrine_area", batch = "firmages_4_consecrate_cycle", timeoutTicks = 4000)
    public static void consecrationCycle(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        BlockPos heart = h.absolutePos(HEART);
        BlockPos stone = h.absolutePos(STONE);
        ServerPlayer[] p = new ServerPlayer[1];
        boolean[] sawMixed = new boolean[1];
        boolean[] mixedInvalid = new boolean[1];
        int[] firstSeen = {-1, -1};
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                agesUpTo(server, AgeId.AGE_0);
                buildRing0(h);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_0 setup still running"))
            .thenExecute(() -> {
                ShrineService.validate(level, heart, heartBe(h));
                ok(h, ShrineService.offerAtPlinth(level, h.absolutePos(PLINTH), new ItemStack(Items.HEART_OF_THE_SEA), null)
                    == ShrineService.OfferResult.ACCEPTED, "offering accepted");
                level.setBlock(heart, level.getBlockState(heart).setValue(ShrineHeartBlock.LIT, true), 3);
                Component no = ShrineService.simulatePray(server);
                ok(h, no == null, "prayer heard: " + (no == null ? "" : no.getString()));
                ok(h, ShrineConsecration.pendingCount() == RING_BLOCKS, "the ring's 20 blocks are scheduled: " + ShrineConsecration.pendingCount());
                ok(h, consecratedCount(h) == 0, "nothing turns before the reload");
            })
            .thenWaitUntil(() -> {
                // every tick until done: a ring in transformation still validates
                int n = consecratedCount(h);
                if (n > 0 && n < RING_BLOCKS) {
                    sawMixed[0] = true;
                    if (!ShrineMultiblocks.check(level, heart, RING_0).valid()) mixedInvalid[0] = true;
                }
                if (n > 0 && firstSeen[0] < 0) {
                    // from the heart outward: the inner stones turn before the corner posts
                    firstSeen[0] = (int) rolePositions(h, Consecration.Role.STONE).stream().filter(x -> level.getBlockState(x).getBlock() instanceof ConsecratedBlock).count();
                    firstSeen[1] = (int) rolePositions(h, Consecration.Role.PILLAR).stream().filter(x -> level.getBlockState(x).getBlock() instanceof ConsecratedBlock).count();
                }
                h.assertTrue(n == RING_BLOCKS, "consecration still running: " + n + "/" + RING_BLOCKS);
            })
            .thenExecute(() -> {
                ok(h, sawMixed[0] && !mixedInvalid[0], "the ring stayed valid while it turned (mixed state seen: " + sawMixed[0] + ")");
                ok(h, firstSeen[0] > 0 && firstSeen[1] == 0, "inner stones first: stones " + firstSeen[0] + ", posts " + firstSeen[1]);
                ShrineSavedData sd = ShrineSavedData.get(server);
                ok(h, sd.originals().size() == RING_BLOCKS && sd.originals().values().stream().allMatch(o -> o.ring() == 0), "20 originals of ring 0 stored");
                ok(h, sd.original(stone).map(o -> o.state().equals("minecraft:cobblestone")).orElse(false), "stone original: " + sd.original(stone));
                ok(h, sd.original(h.absolutePos(HEART.offset(2, 0, 2))).map(o -> o.state().equals("minecraft:oak_log[axis=y]")).orElse(false),
                    "post original with its axis");
                ok(h, ShrineSavedData.reload(sd, server.registryAccess()).originals().equals(sd.originals()), "originals NBT round trip");
                ok(h, ShrineConsecration.toState("minecraft:oak_log[axis=y]").map(s -> s.is(Blocks.OAK_LOG)).orElse(false), "original decodes to its block");
                ShrineHeartBlockEntity be = heartBe(h);
                ShrineService.validate(level, heart, be);
                ok(h, be.validRing() == 0 && sd.intact(), "consecrated ring valid, shrine intact");

                // ---- protection outside maintenance (the explosion first, before the test player stands nearby)
                level.explode(null, stone.getX() + 0.5, stone.getY() + 1.0, stone.getZ() + 0.5, 3.0F, Level.ExplosionInteraction.TNT);
                ok(h, consecratedCount(h) == RING_BLOCKS, "explosion left the ring: " + consecratedCount(h));
                p[0] = DebugPlayerCommands.spawn(server, level, "gt_mason", Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 1, 8))));
                p[0].setGameMode(GameType.SURVIVAL);
                BlockState cs = level.getBlockState(stone);
                ok(h, cs.getDestroyProgress(p[0], level, stone) == 0.0F, "no destroy progress outside maintenance");
                ok(h, !p[0].gameMode.destroyBlock(stone) && level.getBlockState(stone).getBlock() instanceof ConsecratedBlock, "survival break refused");
                ok(h, !cs.canBeReplaced(Fluids.WATER) && !cs.canBeReplaced(Fluids.LAVA), "fluids cannot replace it");
                ok(h, cs.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK, "pistons are blocked");
                BlockPos piston = h.absolutePos(STONE.east());
                level.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST), 3);
                level.setBlock(piston.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            })
            .thenIdle(4)
            .thenExecute(() -> {
                BlockPos piston = h.absolutePos(STONE.east());
                ok(h, !level.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED) && level.getBlockState(stone).getBlock() instanceof ConsecratedBlock,
                    "a powered piston cannot push it");
                level.setBlock(piston.east(), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(piston, Blocks.AIR.defaultBlockState(), 3);

                // ---- maintenance by the sneak-punch
                punchHeart(h, p[0]);
                ok(h, ShrineConsecration.maintenanceActive(server), "the punch turned maintenance on");
                ok(h, level.getBlockState(heart).is(ShrineRegistry.SHRINE_HEART.get()) && level.getBlockState(heart).getValue(ShrineHeartBlock.MAINTENANCE),
                    "heart stands and shows maintenance");
                ok(h, isKey(ShrineService.prayerRefusal(server), "firmages.shrine.maintenance.no_prayer"), "no prayer during maintenance: " + text(ShrineService.prayerRefusal(server)));
                ok(h, level.getBlockState(stone).getDestroyProgress(p[0], level, stone) > 0.0F, "destroy progress in maintenance");
                ok(h, p[0].gameMode.destroyBlock(stone) && level.getBlockState(stone).isAir(), "broken in maintenance");
                List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(stone).inflate(1.5));
                ok(h, drops.stream().anyMatch(e -> e.getItem().is(Items.COBBLESTONE)), "the original cobblestone dropped: " + drops);
                drops.forEach(e -> e.discard());
                ShrineHeartBlockEntity be = heartBe(h);
                ShrineService.validate(level, heart, be);
                ok(h, be.validRing() == -1 && !ShrineSavedData.get(server).intact(), "the hole breaks the ring, blessings pause");
                ok(h, AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_1), "Ages stay");
                ok(h, ShrineSavedData.get(server).original(stone).isPresent(), "the record of the hole stays");
                // repair with the original material: valid at once, consecrated again only when maintenance ends
                level.setBlock(stone, Blocks.COBBLESTONE.defaultBlockState(), 3);
                ShrineService.validate(level, heart, be);
                ok(h, be.validRing() == 0 && ShrineSavedData.get(server).intact(), "repaired with cobblestone");
                ok(h, level.getBlockState(stone).is(Blocks.COBBLESTONE) && !ShrineConsecration.isPending(stone), "not consecrated during maintenance");
            })
            .thenIdle(12) // the punch has a 10-tick cooldown
            .thenExecute(() -> {
                punchHeart(h, p[0]);
                ok(h, !ShrineConsecration.maintenanceActive(server) && !level.getBlockState(heart).getValue(ShrineHeartBlock.MAINTENANCE),
                    "the second punch ended maintenance");
            })
            .thenWaitUntil(() -> h.assertTrue(level.getBlockState(stone).getBlock() instanceof ConsecratedBlock, "repaired stone not consecrated yet"))
            .thenExecute(() -> {
                ok(h, consecratedCount(h) == RING_BLOCKS, "whole ring consecrated again");
                ok(h, ShrineSavedData.get(server).original(stone).map(o -> o.state().equals("minecraft:cobblestone")).orElse(false), "repair stored as the original");
                // the timer ends maintenance by itself
                ServerConfig.SHRINE_MAINTENANCE_SECONDS.set(5);
                ok(h, ShrineConsecration.setMaintenance(server, true, "gt"), "maintenance on by command path");
            })
            .thenWaitUntil(() -> h.assertTrue(!ShrineConsecration.maintenanceActive(server) && !level.getBlockState(heart).getValue(ShrineHeartBlock.MAINTENANCE),
                "maintenance still on"))
            .thenExecute(() -> {
                ServerConfig.SHRINE_MAINTENANCE_SECONDS.set(60);
                ok(h, ShrineSavedData.get(server).maintenanceUntil() == 0, "timer cleared");
                // ---- the heart goes: everything turns back
                DebugPlayerCommands.despawn(p[0]);
                h.setBlock(HEART, Blocks.AIR);
            })
            .thenWaitUntil(() -> h.assertTrue(originalsBack(h) && ShrineSavedData.get(server).originals().isEmpty(), "originals not back yet"))
            .thenExecute(() -> {
                ShrineService.extract(level, h.absolutePos(PLINTH));
                ShrineSavedData.get(server).removeGranted("age_1");
                agesUpTo(server, AgeId.DAWN);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> ServerConfig.DEBUG_ALLOW_SIMULATE.set(false))
            .thenSucceed();
    }

    /** Migration: in a world where age_1 is already held, ring 0 in Age material is consecrated on its first validation. */
    @GameTest(template = "shrine_area", batch = "firmages_4_consecrate_migrate", timeoutTicks = 2000)
    public static void awakenedRingMigrates(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                agesUpTo(server, AgeId.AGE_1);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_1 setup still running"))
            .thenExecute(() -> {
                ok(h, ShrineSavedData.get(server).originals().isEmpty(), "no originals before");
                buildRing0(h); // the heart validates on its first tick, like a loaded heart after an update
            })
            .thenWaitUntil(() -> h.assertTrue(consecratedCount(h) == RING_BLOCKS, "migration still running: " + consecratedCount(h)))
            .thenExecute(() -> {
                ShrineSavedData sd = ShrineSavedData.get(server);
                ok(h, sd.originals().size() == RING_BLOCKS, "originals stored by the migration: " + sd.originals().size());
                ok(h, allRolePositions(h).stream().allMatch(x -> sd.original(x).isPresent()), "every position has its original");
                ShrineHeartBlockEntity be = heartBe(h);
                ShrineService.validate(level, h.absolutePos(HEART), be);
                ok(h, be.validRing() == 0 && sd.intact(), "migrated ring valid, shrine intact");
                h.setBlock(HEART, Blocks.AIR);
            })
            .thenWaitUntil(() -> h.assertTrue(originalsBack(h) && ShrineSavedData.get(server).originals().isEmpty(), "originals not back yet"))
            .thenExecute(() -> agesUpTo(server, AgeId.DAWN))
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> ServerConfig.DEBUG_ALLOW_SIMULATE.set(false))
            .thenSucceed();
    }
}
