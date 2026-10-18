package dev.firmages.core.gametest;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.boss.PrimordialEntity;
import dev.firmages.core.origin.OriginAccess;
import dev.firmages.core.origin.OriginSpawns;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import java.util.List;
import com.enviouse.progressivestages.server.enforcement.DimensionEnforcer;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.ceremony.CeremonyService;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.compat.sgjourney.SgjGates;
import dev.firmages.core.net.AgeTransitionPayload;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginEchoes;
import dev.firmages.core.shrine.ConsecratedBlock;
import dev.firmages.core.shrine.Consecration;
import dev.firmages.core.shrine.ShrineRegistry;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import dev.firmages.core.origin.OriginEvent;
import dev.firmages.core.origin.OriginRegistry;
import dev.firmages.core.origin.OriginSavedData;
import dev.firmages.core.origin.OriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.povstalec.sgjourney.common.block_entities.stargate.AbstractStargateEntity;
import net.povstalec.sgjourney.common.data.StargateNetwork;
import net.povstalec.sgjourney.common.sgjourney.Address;
import net.povstalec.sgjourney.common.sgjourney.AddressRegion;
import net.povstalec.sgjourney.common.sgjourney.Dialing;
import net.povstalec.sgjourney.common.sgjourney.Galaxy;
import net.povstalec.sgjourney.common.sgjourney.SpaceLocation;
import net.povstalec.sgjourney.common.sgjourney.StargateInfo;

import java.util.Arrays;
import java.util.Optional;

/**
 * Level G tests of The Origin (SPEC §16): the datapack dimension with its biome and void-like type, the arena and
 * altar built at server start, Stargate Journey's space location with the fixed address and the return gate in
 * the network, ProgressiveStages' age_9 dimension lock (stand-in stage file from src/gametest/config), the
 * Gathering trigger with its event and function tag, and the final boss granting finale_won with the FINALE
 * ceremony. Each test sits in its own batch, because the Gathering waits for every online player.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class OriginGameTests {
    private static int gatherings;
    private static int victories;
    private static int summons;

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(OriginGameTests.class);
    }

    @SubscribeEvent
    public static void onGathering(OriginEvent.Gathering e) {
        gatherings++;
    }

    @SubscribeEvent
    public static void onVictory(OriginEvent.Victory e) {
        victories++;
    }

    /** Counts {@code /firmages origin fight}; the core's boss ({@code boss/BossEvents}) answers it. */
    @SubscribeEvent
    public static void onSummonBoss(OriginEvent.SummonBoss e) {
        summons++;
    }

    /** The Primordials alive in The Origin (the core summons one on every Gathering and {@code origin fight}). */
    private static List<PrimordialEntity> primordials(ServerLevel o) {
        List<PrimordialEntity> out = new java.util.ArrayList<>();
        for (Entity e : o.getAllEntities()) {
            if (e instanceof PrimordialEntity b && b.isAlive()) out.add(b);
        }
        return out;
    }

    private static void discardPrimordials(ServerLevel o) {
        primordials(o).forEach(Entity::discard);
    }

    private static void ok(GameTestHelper h, boolean cond, String what) {
        if (!cond) h.fail(what);
    }

    private static ServerLevel origin(GameTestHelper h) {
        ServerLevel level = OriginService.level(h.getLevel().getServer());
        if (level == null) h.fail("firmages:origin is not loaded");
        return level;
    }

    /** Round F1: a return gate built by an older version loses the DHD's fusion core once (OriginService.migrateDhdCore). */
    @GameTest(template = "empty", batch = "firmages_5_origin_world", timeoutTicks = 100)
    public static void oldReturnGateLosesItsDhdCore(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = origin(h);
        OriginSavedData sd = OriginSavedData.get(server);
        ok(h, sd.gateBuilt() && sd.dhdCoreTaken(), "the gate built at server start records its DHD core as taken");
        o.getChunk(OriginArena.dhd());
        if (!(o.getBlockEntity(OriginArena.dhd()) instanceof net.povstalec.sgjourney.common.block_entities.dhd.AbstractDHDEntity dhd)) {
            h.fail("no DHD at " + OriginArena.dhd());
            return;
        }
        net.minecraft.world.item.ItemStack core = new net.minecraft.world.item.ItemStack(
            BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("sgjourney", "fusion_core")));
        dhd.energyItemHandler.setStackInSlot(0, core.copy());
        sd.setDhdCoreTaken(false); // a world whose gate an older version built
        OriginService.migrateDhdCore(server, o);
        ok(h, dhd.energyItemHandler.getStackInSlot(0).isEmpty() && sd.dhdCoreTaken(), "the old core is taken");
        dhd.energyItemHandler.setStackInSlot(0, core.copy()); // a core put in later stays: the migration runs once
        OriginService.migrateDhdCore(server, o);
        ok(h, !dhd.energyItemHandler.getStackInSlot(0).isEmpty(), "the migration ran a second time");
        dhd.energyItemHandler.setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "firmages_5_origin_world", timeoutTicks = 400)
    public static void originDimensionArenaAndGate(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = origin(h);
        ok(h, !o.dimensionType().hasSkyLight() && o.dimensionType().effectsLocation().equals(ResourceLocation.withDefaultNamespace("the_end")),
            "void-like dimension type (no skylight, End sky)");
        ok(h, o.getBiome(OriginArena.ALTAR).is(ResourceKey.create(Registries.BIOME, OriginRegistry.ORIGIN_ID)), "biome firmages:origin");
        OriginSavedData sd = OriginSavedData.get(server);
        ok(h, sd.arenaVersion() == OriginArena.VERSION && sd.gateBuilt(), "arena v" + sd.arenaVersion() + " and gate built at server start");
        ok(h, o.getBlockState(OriginArena.ALTAR).is(OriginRegistry.ORIGIN_ALTAR.get()), "altar in the arena centre");
        ok(h, !o.getBlockState(new BlockPos(12, OriginArena.FLOOR_Y, 5)).isAir() && o.getBlockState(new BlockPos(OriginArena.radius() + 20, OriginArena.FLOOR_Y, 0)).isAir(),
            "arena floor inside, void outside");
        ok(h, o.getBlockState(OriginArena.ALTAR).getDestroySpeed(o, OriginArena.ALTAR) < 0, "altar is unbreakable");
        BlockEntity gate = o.getBlockEntity(OriginArena.gateBase());
        BlockEntity dhd = o.getBlockEntity(OriginArena.dhd());
        ok(h, gate != null && "sgjourney:milky_way_stargate".equals(String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(gate.getType()))),
            "Milky Way stargate at " + OriginArena.gateBase() + ", found " + gate);
        ok(h, dhd != null && "sgjourney:milky_way_dhd".equals(String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(dhd.getType()))),
            "DHD at " + OriginArena.dhd() + ", found " + dhd);

        // Stargate Journey: The Origin is a space location in its own address region with the fixed address.
        SpaceLocation sl = SpaceLocation.fromDimension(server, OriginRegistry.ORIGIN);
        ok(h, sl != null && sl.isInStargateNetwork(), "space location registered and in the network");
        AddressRegion region = sl.getAddressRegion();
        ok(h, region != null && region.getResourceKey().location().equals(OriginRegistry.ORIGIN_ID), "address region firmages:origin, was " + region);
        Address.Immutable addr = region.getAddressInGalaxy(ResourceKey.create(Galaxy.REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("sgjourney", "milky_way")));
        ok(h, addr != null && Arrays.equals(addr.toArray(), new int[] {9, 16, 21, 33, 2, 37}), "Milky Way address 9-16-21-33-2-37, was " + addr);

        // v2, Origin echoes: the islands are consecrated stone with a trim rim in their Age's accent, no end rod
        for (int i = 0; i < OriginArena.ISLANDS; i++) {
            BlockPos top = OriginArena.islandTop(i);
            int r = OriginArena.islandRadius(i);
            BlockState centre = o.getBlockState(top);
            BlockState rim = o.getBlockState(top.east(r));
            ok(h, centre.is(ShrineRegistry.CONSECRATED.get(Consecration.Role.STONE).get()) && centre.getValue(ConsecratedBlock.ACCENT) == i,
                "island " + i + " is consecrated stone with accent " + i + ", found " + centre);
            ok(h, rim.is(ShrineRegistry.CONSECRATED.get(Consecration.Role.TRIM).get()) && rim.getValue(ConsecratedBlock.ACCENT) == i,
                "island " + i + " has a trim rim, found " + rim);
            ok(h, o.getBlockState(top.above()).isAir(), "island " + i + ": no end rod");
            ChunkPos cp = new ChunkPos(OriginEchoes.position(i));
            o.setChunkForced(cp.x, cp.z, true);
        }
        h.startSequence()
            .thenWaitUntil(() -> {
                for (int i = 0; i < OriginArena.ISLANDS; i++) {
                    if (!o.areEntitiesLoaded(ChunkPos.asLong(OriginEchoes.position(i)))) h.fail("island " + i + " entities not loaded yet");
                }
            })
            .thenExecute(() -> {
                int expected = 0;
                for (int i = 0; i < OriginArena.ISLANDS; i++) if (OriginEchoes.signature(i).isPresent()) expected++;
                int first = OriginEchoes.tick(o);
                int second = OriginEchoes.tick(o);
                ok(h, expected > 0 && first == expected && second == expected, "one echo per island whose signature item exists ("
                    + expected + " in this run), and no second one: " + first + ", " + second);
                for (int i = 0; i < OriginArena.ISLANDS; i++) {
                    Entity e = o.getEntity(OriginEchoes.uuid(i));
                    if (OriginEchoes.signature(i).isEmpty()) {
                        ok(h, e == null, "no echo without a signature item on island " + i);
                        continue;
                    }
                    ok(h, e instanceof Display.ItemDisplay d && d.getSlot(0).get().is(OriginEchoes.signature(i).get()) && e.getTags().contains(OriginEchoes.TAG)
                        && e.blockPosition().equals(OriginEchoes.position(i)), "island " + i + " carries its Age's signature item, found " + e);
                }
                ok(h, o.getEntities(EntityType.ITEM_DISPLAY, e -> e.getTags().contains(OriginEchoes.TAG)).size() == expected, "no duplicate echoes");
                for (int i = 0; i < OriginArena.ISLANDS; i++) {
                    ChunkPos cp = new ChunkPos(OriginEchoes.position(i));
                    o.setChunkForced(cp.x, cp.z, false);
                }
            })
            .thenWaitUntil(() -> {
                if (StargateNetwork.get(server).getStargatesInDimension(OriginRegistry.ORIGIN).isEmpty()) h.fail("return gate not in the stargate network yet");
            })
            .thenSucceed();
    }

    /** Runs {@code command} as {@code src}; -1 when the dispatcher refuses it (unknown for this permission level). */
    private static int run(MinecraftServer server, CommandSourceStack src, String command) {
        try {
            return server.getCommands().getDispatcher().execute(command, src);
        } catch (CommandSyntaxException e) {
            return -1;
        }
    }

    /**
     * The operators' test commands (Felix 2026-10-14): without permission 2 none of them exists; {@code origin tp
     * <players> force} lets a survival player without age_9 into The Origin and both age_9 locks (firmages-core's and
     * ProgressiveStages', which re-checks every second) let him stay until he leaves; a plain tp is turned back again
     * afterwards; {@code origin gathering} starts the Gathering far from the altar and wakes The Primordial;
     * {@code origin fight} refuses while it lives and summons it once it is gone; after the win both refuse.
     */
    @GameTest(template = "empty", batch = "firmages_5_origin_ops", timeoutTicks = 400)
    public static void operatorCommands(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = origin(h);
        OriginService.reset(server);
        gatherings = 0;
        summons = 0;
        List<BlockPos> area = List.of(OriginArena.ALTAR);
        forceChunks(o, area, true); // the altar's chunk ticks entities, as it does for players standing there
        ServerPlayer[] p = new ServerPlayer[1];
        BlockPos home = h.absolutePos(new BlockPos(1, 1, 1));
        h.startSequence()
            .thenWaitUntil(() -> entitiesLoaded(h, o, area))
            .thenExecute(() -> {
                discardPrimordials(o);
                ok(h, !AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9), "test starts without age_9");
                p[0] = DebugPlayerCommands.spawn(server, h.getLevel(), "gt_operator", Vec3.atCenterOf(home));
                p[0].setGameMode(GameType.SURVIVAL);
                CommandSourceStack none = p[0].createCommandSourceStack().withPermission(0);
                ok(h, run(server, none, "firmages origin tp @s force") == -1 && p[0].level() != o, "permission 0: no forced tp");
                ok(h, run(server, none, "firmages origin gathering") == -1 && gatherings == 0, "permission 0: no Gathering");
                ok(h, run(server, none, "firmages origin fight") == -1 && summons == 0, "permission 0: no boss");
                CommandSourceStack op = p[0].createCommandSourceStack().withPermission(2);
                ok(h, run(server, op, "firmages origin tp @s") == 0 && p[0].level() != o, "a plain tp keeps the lock");
                ok(h, run(server, op, "firmages origin tp @s force") == 1 && p[0].level() == o, "the forced tp lets him in");
                ok(h, !DimensionEnforcer.isDimensionLockedForPlayer(p[0], OriginRegistry.ORIGIN_ID), "ProgressiveStages' lock lets the visitor stay");
            })
            .thenIdle(45) // ProgressiveStages checks the current dimension once a second
            .thenExecute(() -> {
                ok(h, p[0].level() == o, "still in The Origin after ProgressiveStages' checks, in " + p[0].level().dimension().location());
                CommandSourceStack op = p[0].createCommandSourceStack().withPermission(2);
                ok(h, run(server, op, "firmages origin gathering") == 1 && gatherings == 1 && OriginSavedData.get(server).gatherings() == 1,
                    "the Gathering starts at the arrival point, 14 blocks from the altar");
                ok(h, primordials(o).size() == 1 && OriginService.fightActive(o), "the Gathering woke The Primordial: " + primordials(o).size());
                ok(h, run(server, op, "firmages origin fight") == 0 && summons == 1 && primordials(o).size() == 1,
                    "while it lives the summon is refused");
                discardPrimordials(o);
                ok(h, run(server, op, "firmages origin fight") == 1 && summons == 2 && primordials(o).size() == 1,
                    "with no boss alive the command summons The Primordial");
                PrimordialEntity b = primordials(o).get(0);
                BlockPos al = OriginArena.ALTAR;
                ok(h, b.getTags().contains(OriginService.FINAL_BOSS_TAG) && Math.abs(b.getX() - (al.getX() + 0.5)) < 1.0E-6
                    && Math.abs(b.getZ() - (al.getZ() + 0.5)) < 1.0E-6 && b.getY() > al.getY(), "tagged, above the altar at " + b.position());
                discardPrimordials(o);
                p[0].teleportTo(h.getLevel(), home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0, 0);
                ok(h, p[0].level() == h.getLevel() && DimensionEnforcer.isDimensionLockedForPlayer(p[0], OriginRegistry.ORIGIN_ID),
                    "leaving ends the forced visit");
                ok(h, run(server, op, "firmages origin tp @s") == 0 && p[0].level() != o, "the next plain tp is turned back again");
                OriginSavedData.get(server).setWon(true);
                ok(h, run(server, op, "firmages origin gathering") == 0 && run(server, op, "firmages origin fight") == 0 && summons == 2,
                    "after the win both commands refuse");
            })
            .thenExecute(() -> {
                discardPrimordials(o);
                forceChunks(o, area, false);
                OriginService.reset(server);
                DebugPlayerCommands.despawn(p[0]);
            })
            .thenSucceed();
    }

    /** A Milky Way gate in the overworld (as players build one) resolves The Origin's address: simulated dial, enough energy assumed. */
    @GameTest(template = "gate_area", batch = "firmages_5_origin_dial", timeoutTicks = 200)
    public static void overworldGateCanDialTheOrigin(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(1, 0, 1));
        StructureTemplate t = level.getStructureManager().get(OriginArena.GATE_TEMPLATE).orElse(null);
        ok(h, t != null, "Stargate Journey pedestal template present");
        StructurePlaceSettings settings = new StructurePlaceSettings();
        t.placeInWorld(level, origin, origin, settings, level.random, 2);
        ok(h, SgjGates.finishPlacement(level, t.getBoundingBox(settings, origin)) == 2, "gate and DHD generated");
        AbstractStargateEntity<?> gate = (AbstractStargateEntity<?>) level.getBlockEntity(origin.offset(3, 2, 9));
        StargateInfo.FeedbackMessage f = gate.engageStargate(new Address.Immutable(9, 16, 21, 33, 2, 37), false, Dialing.Action.SIMULATE_ENOUGH_ENERGY);
        ok(h, f.feedback().name().startsWith("CONNECTION_ESTABLISHED"), "dialing 9-16-21-33-2-37 reaches The Origin, feedback " + f.feedback());
        StargateInfo.FeedbackMessage wrong = gate.engageStargate(new Address.Immutable(3, 4, 5, 6, 7, 8), false, Dialing.Action.SIMULATE_ENOUGH_ENERGY);
        ok(h, !wrong.feedback().name().startsWith("CONNECTION_ESTABLISHED"), "an unknown address does not connect, feedback " + wrong.feedback());
        FirmagesCore.LOGGER.info("GameTest dial: Origin address -> {}, unknown address -> {}", f.feedback(), wrong.feedback());
        h.succeed();
    }

    @GameTest(template = "empty", batch = "firmages_5_origin_lock", timeoutTicks = 200)
    public static void originNeedsAge9(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = origin(h);
        ServerPlayer p = DebugPlayerCommands.spawn(server, h.getLevel(), "gt_locked", Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
        try {
            p.setGameMode(GameType.SURVIVAL);
            ok(h, DimensionEnforcer.isDimensionLockedForPlayer(p, OriginRegistry.ORIGIN_ID), "ProgressiveStages locks firmages:origin without age_9");
            BlockPos a = OriginArena.arrival();
            p.teleportTo(o, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0, 0);
            ok(h, p.level() != o, "a survival player without age_9 is not let in");
        } finally {
            DebugPlayerCommands.despawn(p);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "firmages_5_origin_finale", timeoutTicks = 200)
    public static void gatheringAndFinalBoss(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = origin(h);
        OriginService.reset(server);
        gatherings = 0;
        victories = 0;
        // The altar's chunk ticks entities, as it does for players who stand there: the summoned boss is then loaded.
        List<BlockPos> area = List.of(OriginArena.ALTAR, OriginArena.arrival());
        forceChunks(o, area, true);
        h.startSequence()
            .thenWaitUntil(() -> entitiesLoaded(h, o, area))
            .thenExecute(() -> gatheringAndFinalBossSteps(h, server, o, area))
            .thenSucceed();
    }

    private static void gatheringAndFinalBossSteps(GameTestHelper h, MinecraftServer server, ServerLevel o, List<BlockPos> area) {
        ServerPlayer p = DebugPlayerCommands.spawn(server, h.getLevel(), "gt_origin", Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
        try {
            p.setGameMode(GameType.CREATIVE); // creative bypasses the age_9 lock (ProgressiveStages allow_creative_bypass)
            BlockPos a = OriginArena.arrival();
            server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(2), "firmages origin tp");
            ok(h, p.level() == o && p.blockPosition().equals(a), "/firmages origin tp put the creative test player at the arrival point, at " + p.blockPosition());
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "firmages origin status");
            ok(h, !OriginService.checkGathering(server), "no Gathering at the arrival point (14 blocks out)");

            BlockPos al = OriginArena.ALTAR;
            p.teleportTo(o, al.getX() + 2.5, al.getY(), al.getZ() + 1.5, 0, 0);
            ok(h, OriginService.gathered(server) && OriginService.checkGathering(server), "Gathering fires when everyone stands at the altar");
            ok(h, gatherings == 1 && OriginSavedData.get(server).gatherings() == 1, "one Gathering event, count " + gatherings);
            ok(h, primordials(o).size() == 1 && primordials(o).get(0).partySize() == 1, "the Gathering of one woke one Primordial sized for one");
            discardPrimordials(o); // this test wins with tagged zombies; BossGameTests kill the real one
            ok(h, !OriginService.checkGathering(server), "no second start while still gathered");
            p.teleportTo(o, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0, 0);
            ok(h, !OriginService.checkGathering(server), "breaking up does nothing");
            p.teleportTo(o, al.getX() + 2.5, al.getY(), al.getZ() + 1.5, 0, 0);
            ok(h, !OriginService.checkGathering(server) && gatherings == 1, "re-gathering within the cooldown does not start again");

            // Function tags run as the server at the altar.
            ok(h, OriginService.runFunctions(server, o, al, ResourceLocation.fromNamespaceAndPath("firmages_test", "origin_probe")) == 1
                && o.getBlockState(al.above(3)).is(Blocks.GOLD_BLOCK), "function tag ran at the altar");
            o.setBlock(al.above(3), Blocks.AIR.defaultBlockState(), 3);

            // An untagged zombie is just a zombie.
            Zombie plain = spawnZombie(o, al.east(4), false);
            plain.hurt(plain.damageSources().genericKill(), Float.MAX_VALUE);
            ok(h, !OriginSavedData.get(server).won() && victories == 0, "an untagged death changes nothing");
            // A tagged boss that dies outside The Origin (lured through a portal, summoned elsewhere) wins nothing.
            Zombie elsewhere = spawnZombie(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1)), true);
            elsewhere.hurt(elsewhere.damageSources().genericKill(), Float.MAX_VALUE);
            ok(h, elsewhere.isDeadOrDying() && !OriginSavedData.get(server).won() && victories == 0, "a tagged death in the overworld does not count");

            int sentBefore = CeremonyService.sentCount();
            Zombie boss = spawnZombie(o, al.east(4), true);
            boss.hurt(boss.damageSources().genericKill(), Float.MAX_VALUE);
            ok(h, boss.isDeadOrDying(), "boss died");
            ok(h, OriginSavedData.get(server).won() && victories == 1, "the tagged boss's death wins the finale");
            ok(h, ProgressiveStagesAPI.hasStage(p, StageId.of(OriginService.FINALE_STAGE)), "finale_won granted to the online player");
            Optional<AgeTransitionPayload> last = CeremonyService.last();
            ok(h, CeremonyService.sentCount() == sentBefore + 1 && last.isPresent() && last.get().stage().equals(CeremonyService.FINALE) && last.get().full()
                && last.get().shrine().map(g -> g.dimension() == OriginRegistry.ORIGIN && g.pos().equals(al)).orElse(false),
                "one FINALE payload at the altar, got " + last);

            Zombie again = spawnZombie(o, al.west(4), true);
            again.hurt(again.damageSources().genericKill(), Float.MAX_VALUE);
            ok(h, victories == 1 && CeremonyService.sentCount() == sentBefore + 1, "a second tagged boss changes nothing");
            ok(h, !OriginService.checkGathering(server), "no Gathering after the win");
        } finally {
            discardPrimordials(o);
            DebugPlayerCommands.despawn(p);
            OriginService.reset(server);
            forceChunks(o, area, false);
        }
    }

    /**
     * Felix 2026-10-14: during the fight, walking away from the altar and back announced the Gathering again every
     * time once the cooldown had passed. While the fight runs (The Primordial the Gathering woke) no arrival starts it
     * again, even with the cooldown at 0; once the fight is gone (a wipe) the next arrival does and wakes it anew.
     */
    @GameTest(template = "empty", batch = "firmages_5_origin_repeat", timeoutTicks = 400)
    public static void noRepeatWhileFightRuns(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = origin(h);
        OriginService.reset(server);
        gatherings = 0;
        int cooldown = ServerConfig.ORIGIN_GATHER_COOLDOWN.get();
        BlockPos al = OriginArena.ALTAR;
        BlockPos a = OriginArena.arrival();
        List<BlockPos> area = List.of(al, al.south(8), a);
        ServerPlayer[] p = new ServerPlayer[1];
        Runnable cleanup = () -> {
            ServerConfig.ORIGIN_GATHER_COOLDOWN.set(cooldown);
            discardPrimordials(o);
            if (p[0] != null) DebugPlayerCommands.despawn(p[0]);
            forceChunks(o, area, false);
            OriginService.reset(server);
        };
        forceChunks(o, area, true);
        h.startSequence()
            .thenWaitUntil(() -> entitiesLoaded(h, o, area))
            .thenExecute(() -> {
                try {
                    p[0] = DebugPlayerCommands.spawn(server, h.getLevel(), "gt_regather", Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
                    p[0].setGameMode(GameType.CREATIVE);
                    p[0].teleportTo(o, al.getX() + 2.5, al.getY(), al.getZ() + 1.5, 0, 0);
                    ok(h, !OriginService.fightActive(o), "no fight before the Gathering");
                    ok(h, OriginService.checkGathering(server) && gatherings == 1, "first arrival starts the Gathering");
                    ok(h, primordials(o).size() == 1 && OriginService.fightActive(o), "The Primordial woke: a running fight");
                    ServerConfig.ORIGIN_GATHER_COOLDOWN.set(0);
                    for (int i = 0; i < 3; i++) {
                        p[0].teleportTo(o, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0, 0);
                        ok(h, !OriginService.checkGathering(server), "leaving the altar does nothing");
                        p[0].teleportTo(o, al.getX() + 2.5, al.getY(), al.getZ() + 1.5, 0, 0);
                        ok(h, !OriginService.checkGathering(server) && !OriginService.checkGathering(server),
                            "returning during the fight does not start it again");
                    }
                    ok(h, gatherings == 1 && OriginSavedData.get(server).gatherings() == 1, "one Gathering (and one announcement), count " + gatherings);
                    ok(h, primordials(o).size() == 1, "one Primordial, not one per return: " + primordials(o).size());
                    discardPrimordials(o); // the wipe: it withdrew
                    ok(h, !OriginService.fightActive(o), "no fight once it withdrew");
                    p[0].teleportTo(o, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0, 0);
                    OriginService.checkGathering(server);
                    p[0].teleportTo(o, al.getX() + 2.5, al.getY(), al.getZ() + 1.5, 0, 0);
                    ok(h, OriginService.checkGathering(server) && gatherings == 2, "after the wipe the next arrival starts the Gathering again");
                    ok(h, primordials(o).size() == 1, "and wakes The Primordial anew");
                } finally {
                    cleanup.run();
                }
            })
            .thenSucceed();
    }

    /**
     * {@code /firmages origin kill [boss]} (operators): {@code kill} takes every hostile in The Origin but the final
     * boss, {@code kill boss} the boss, whose death wins the finale the normal way; without permission 2 neither exists.
     */
    @GameTest(template = "empty", batch = "firmages_5_origin_kill", timeoutTicks = 400)
    public static void operatorKill(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = origin(h);
        OriginService.reset(server);
        victories = 0;
        BlockPos al = OriginArena.ALTAR;
        List<BlockPos> area = List.of(al, al.east(4), al.west(4), al.north(4), al.south(8));
        List<Entity> spawned = new java.util.ArrayList<>();
        forceChunks(o, area, true);
        h.startSequence()
            .thenWaitUntil(() -> entitiesLoaded(h, o, area))
            .thenExecute(() -> {
                try {
                    Zombie wave = spawnZombie(o, al.east(4), false);
                    Zombie second = spawnZombie(o, al.west(4), false);
                    Zombie boss = spawnZombie(o, al.south(8), true);
                    net.minecraft.world.entity.animal.Cow cow = EntityType.COW.create(o);
                    cow.moveTo(al.getX() + 0.5, al.getY(), al.getZ() - 3.5);
                    o.addFreshEntity(cow);
                    spawned.addAll(List.of(wave, second, boss, cow));
                    for (Zombie z : List.of(wave, second, boss)) z.setNoAi(true);
                    CommandSourceStack none = server.createCommandSourceStack().withPermission(0);
                    ok(h, run(server, none, "firmages origin kill") == -1 && run(server, none, "firmages origin kill boss") == -1 && wave.isAlive(),
                        "permission 0: no kill commands");
                    CommandSourceStack op = server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
                    ok(h, OriginService.fightActive(o), "fight running");
                    int first = run(server, op, "firmages origin kill");
                    ok(h, first >= 2 && wave.isDeadOrDying() && second.isDeadOrDying(), "kill takes both hostiles (" + first + " killed)");
                    ok(h, boss.isAlive() && cow.isAlive(), "the boss and a passive mob are spared");
                    ok(h, !OriginSavedData.get(server).won() && victories == 0, "no win from the hostiles");
                    ok(h, run(server, op, "firmages origin kill") == 0, "nothing left to kill: the command fails");
                    ok(h, run(server, op, "firmages origin kill boss") == 1 && boss.isDeadOrDying(), "kill boss takes the boss");
                    ok(h, OriginSavedData.get(server).won() && victories == 1, "its death wins the finale the normal way");
                    ok(h, run(server, op, "firmages origin kill boss") == 0, "no boss left: the command fails");
                } finally {
                    for (Entity e : spawned) if (!e.isRemoved()) e.discard();
                    forceChunks(o, area, false);
                    OriginService.reset(server);
                }
            })
            .thenSucceed();
    }

    private static void forceChunks(ServerLevel level, List<BlockPos> at, boolean force) {
        for (BlockPos pos : at) {
            ChunkPos cp = new ChunkPos(pos);
            level.setChunkForced(cp.x, cp.z, force);
        }
    }

    private static void entitiesLoaded(GameTestHelper h, ServerLevel level, List<BlockPos> at) {
        for (BlockPos pos : at) {
            if (!level.areEntitiesLoaded(ChunkPos.asLong(pos))) h.fail("entities at " + pos + " not loaded yet");
        }
    }

    /**
     * The mod's own Origin lock (OriginAccess) next to ProgressiveStages': a survival player without age_9 is turned
     * back by the travel event itself; creative passes; once age_9 is a real ProgressiveStages grant, the call
     * Stargate Journey's wormhole makes for players ({@code ServerPlayer#teleportTo(ServerLevel, ...)}, verified with
     * javap in 0.6.49) brings him in.
     */
    @GameTest(template = "empty", batch = "firmages_5_origin_travel", timeoutTicks = 2400)
    public static void originTravelLock(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = origin(h);
        ServerPlayer[] p = new ServerPlayer[1];
        StageId age9 = StageId.of("age_9");
        h.startSequence()
            .thenExecute(() -> {
                p[0] = DebugPlayerCommands.spawn(server, h.getLevel(), "gt_traveller", Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
                p[0].setGameMode(GameType.SURVIVAL);
                ok(h, !AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9), "test starts without age_9");
                ok(h, !OriginAccess.mayEnter(p[0]), "no entry without age_9");
                EntityTravelToDimensionEvent in = new EntityTravelToDimensionEvent(p[0], OriginRegistry.ORIGIN);
                OriginAccess.onTravel(in);
                ok(h, in.isCanceled(), "travel into The Origin cancelled by firmages-core itself");
                EntityTravelToDimensionEvent nether = new EntityTravelToDimensionEvent(p[0], net.minecraft.world.level.Level.NETHER);
                OriginAccess.onTravel(nether);
                ok(h, !nether.isCanceled(), "other dimensions are none of its business");
                BlockPos a = OriginArena.arrival();
                p[0].teleportTo(o, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0, 0);
                ok(h, p[0].level() != o, "the wormhole path leaves a player without age_9 outside");
                p[0].setGameMode(GameType.CREATIVE);
                ok(h, OriginAccess.mayEnter(p[0]), "creative passes, as with ProgressiveStages");
                p[0].setGameMode(GameType.SURVIVAL);
                if (!ProgressiveStagesAPI.grantStage(p[0], age9, StageCause.API)) ProgressiveStagesAPI.grantStageBypass(p[0], age9, StageCause.API);
            })
            .thenWaitUntil(() -> h.assertTrue(AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9) && reloadIdle(server), "age_9 grant and its reload"))
            .thenExecute(() -> {
                ok(h, OriginAccess.mayEnter(p[0]), "age_9 opens The Origin");
                BlockPos a = OriginArena.arrival();
                p[0].teleportTo(o, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0, 0);
                ok(h, p[0].level() == o, "an age_9 player comes through the wormhole path, now in " + p[0].level().dimension().location());
                BlockPos back = h.absolutePos(new BlockPos(1, 1, 1));
                p[0].teleportTo(h.getLevel(), back.getX() + 0.5, back.getY(), back.getZ() + 0.5, 0, 0);
                ok(h, p[0].level() == h.getLevel(), "and home again");
                ProgressiveStagesAPI.revokeStage(p[0], age9, StageCause.API);
            })
            .thenWaitUntil(() -> h.assertTrue(!AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_9) && reloadIdle(server), "age_9 revoke and its reload"))
            .thenExecute(() -> DebugPlayerCommands.despawn(p[0]))
            .thenSucceed();
    }

    /** mob_9: The Origin spawns only from data/firmages/origin/spawns.json (1.0: endermen only). */
    @GameTest(template = "empty", batch = "firmages_5_origin_world")
    public static void originSpawnList(GameTestHelper h) {
        ServerLevel o = origin(h);
        WeightedRandomList<MobSpawnSettings.SpawnerData> none = WeightedRandomList.create();
        List<Object> monsters = EventHooks.getPotentialSpawns(o, MobCategory.MONSTER, OriginArena.ALTAR.above(), none).unwrap().stream()
            .map(d -> (Object) d.type).toList();
        ok(h, monsters.equals(List.of(EntityType.ENDERMAN)), "Origin monsters from the spawn list: " + monsters);
        WeightedRandomList<MobSpawnSettings.SpawnerData> cows = WeightedRandomList.create(new MobSpawnSettings.SpawnerData(EntityType.COW, 8, 4, 4));
        ok(h, EventHooks.getPotentialSpawns(o, MobCategory.CREATURE, OriginArena.ALTAR.above(), cows).isEmpty(), "no passive mobs in The Origin");
        ok(h, EventHooks.getPotentialSpawns(h.getLevel(), MobCategory.CREATURE, h.absolutePos(BlockPos.ZERO), cows).unwrap().size() == 1,
            "other dimensions keep their own lists");
        ok(h, OriginSpawns.list().errors().isEmpty() && OriginSpawns.list().of("monster").size() == 1, "spawn list file parsed: " + OriginSpawns.list());
        h.succeed();
    }

    private static boolean reloadIdle(MinecraftServer s) {
        return AgeService.status(s).phase() == ReloadScheduler.Phase.IDLE;
    }

    private static Zombie spawnZombie(ServerLevel level, BlockPos at, boolean boss) {
        Zombie z = EntityType.ZOMBIE.create(level);
        z.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        if (boss) z.addTag(OriginService.FINAL_BOSS_TAG);
        level.addFreshEntity(z);
        return z;
    }
}
