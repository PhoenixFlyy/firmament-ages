package dev.firmages.core.gametest;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.server.enforcement.DimensionEnforcer;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.ceremony.CeremonyService;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.compat.sgjourney.SgjGates;
import dev.firmages.core.net.AgeTransitionPayload;
import dev.firmages.core.origin.OriginArena;
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

    private static void ok(GameTestHelper h, boolean cond, String what) {
        if (!cond) h.fail(what);
    }

    private static ServerLevel origin(GameTestHelper h) {
        ServerLevel level = OriginService.level(h.getLevel().getServer());
        if (level == null) h.fail("firmages:origin is not loaded");
        return level;
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
        ok(h, !o.getBlockState(new BlockPos(12, OriginArena.FLOOR_Y, 5)).isAir() && o.getBlockState(new BlockPos(0, OriginArena.FLOOR_Y, 40)).isAir(),
            "arena floor inside, void outside");
        ok(h, o.getBlockState(OriginArena.ALTAR).getDestroySpeed(o, OriginArena.ALTAR) < 0, "altar is unbreakable");
        BlockEntity gate = o.getBlockEntity(OriginArena.GATE_BASE);
        BlockEntity dhd = o.getBlockEntity(OriginArena.DHD);
        ok(h, gate != null && "sgjourney:milky_way_stargate".equals(String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(gate.getType()))),
            "Milky Way stargate at " + OriginArena.GATE_BASE + ", found " + gate);
        ok(h, dhd != null && "sgjourney:milky_way_dhd".equals(String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(dhd.getType()))),
            "DHD at " + OriginArena.DHD + ", found " + dhd);

        // Stargate Journey: The Origin is a space location in its own address region with the fixed address.
        SpaceLocation sl = SpaceLocation.fromDimension(server, OriginRegistry.ORIGIN);
        ok(h, sl != null && sl.isInStargateNetwork(), "space location registered and in the network");
        AddressRegion region = sl.getAddressRegion();
        ok(h, region != null && region.getResourceKey().location().equals(OriginRegistry.ORIGIN_ID), "address region firmages:origin, was " + region);
        Address.Immutable addr = region.getAddressInGalaxy(ResourceKey.create(Galaxy.REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("sgjourney", "milky_way")));
        ok(h, addr != null && Arrays.equals(addr.toArray(), new int[] {9, 16, 21, 33, 2, 37}), "Milky Way address 9-16-21-33-2-37, was " + addr);
        h.succeedWhen(() -> {
            if (StargateNetwork.get(server).getStargatesInDimension(OriginRegistry.ORIGIN).isEmpty()) h.fail("return gate not in the stargate network yet");
        });
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
            BlockPos a = OriginArena.ARRIVAL;
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
        ServerPlayer p = DebugPlayerCommands.spawn(server, h.getLevel(), "gt_origin", Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
        try {
            p.setGameMode(GameType.CREATIVE); // creative bypasses the age_9 lock (ProgressiveStages allow_creative_bypass)
            BlockPos a = OriginArena.ARRIVAL;
            server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(2), "firmages origin tp");
            ok(h, p.level() == o && p.blockPosition().equals(a), "/firmages origin tp put the creative test player at the arrival point, at " + p.blockPosition());
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "firmages origin status");
            ok(h, !OriginService.checkGathering(server), "no Gathering at the arrival point (14 blocks out)");

            BlockPos al = OriginArena.ALTAR;
            p.teleportTo(o, al.getX() + 2.5, al.getY(), al.getZ() + 1.5, 0, 0);
            ok(h, OriginService.gathered(server) && OriginService.checkGathering(server), "Gathering fires when everyone stands at the altar");
            ok(h, gatherings == 1 && OriginSavedData.get(server).gatherings() == 1, "one Gathering event, count " + gatherings);
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
            DebugPlayerCommands.despawn(p);
            OriginService.reset(server);
        }
        h.succeed();
    }

    private static Zombie spawnZombie(ServerLevel level, BlockPos at, boolean boss) {
        Zombie z = EntityType.ZOMBIE.create(level);
        z.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        if (boss) z.addTag(OriginService.FINAL_BOSS_TAG);
        level.addFreshEntity(z);
        return z;
    }
}
