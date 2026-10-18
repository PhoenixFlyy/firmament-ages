package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.boss.AltarCircuit;
import dev.firmages.core.boss.ArenaAltars;
import dev.firmages.core.boss.BossRegistry;
import dev.firmages.core.boss.PrimordialEntity;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.origin.ArenaAltarBlock;
import dev.firmages.core.origin.GrandArenaPlan;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginEchoes;
import dev.firmages.core.origin.OriginLayout;
import dev.firmages.core.origin.OriginRegistry;
import dev.firmages.core.origin.OriginRelics;
import dev.firmages.core.origin.OriginSavedData;
import dev.firmages.core.origin.OriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Level G tests of the grand arena (round G3, SPEC §16.5, §39.1): the built Origin against {@link GrandArenaPlan} (whose
 * counts the JUnit test compares with dev/data/origin_arena.json), the relics, the altar channel, the shield of phase 2
 * and 3 and the boss's strike. The boss runs with NoAI and is stepped by hand, the players are creative.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class ArenaGameTests {
    private ArenaGameTests() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(ArenaGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean cond, String what) {
        if (!cond) h.fail(what);
    }

    private static ServerLevel origin(GameTestHelper h) {
        ServerLevel o = OriginService.level(h.getLevel().getServer());
        if (o == null) h.fail("firmages:origin is not loaded");
        return o;
    }

    private static List<long[]> force(ServerLevel o) {
        List<long[]> out = new ArrayList<>();
        for (int x = -2; x <= 1; x++) {
            for (int z = -2; z <= 1; z++) {
                o.setChunkForced(x, z, true);
                out.add(new long[] {x, z});
            }
        }
        return out;
    }

    private static void release(ServerLevel o, List<long[]> chunks) {
        chunks.forEach(c -> o.setChunkForced((int) c[0], (int) c[1], false));
    }

    private static ServerPlayer player(GameTestHelper h, ServerLevel level, String name, Vec3 at) {
        ServerPlayer p = DebugPlayerCommands.spawn(h.getLevel().getServer(), level, name, at);
        p.setGameMode(GameType.CREATIVE);
        return p;
    }

    /** Standing on the socket east of altar {@code i}. */
    private static Vec3 atAltar(int i) {
        BlockPos a = OriginArena.altars().get(i);
        return new Vec3(a.getX() + 1.5, a.getY(), a.getZ() + 0.5);
    }

    private static PrimordialEntity boss(ServerLevel o) {
        PrimordialEntity b = BossRegistry.PRIMORDIAL.get().create(o);
        BlockPos a = OriginArena.ALTAR;
        b.moveTo(a.getX() + 0.5, a.getY() + 2.5, a.getZ() + 0.5, 0, 0);
        b.setNoAi(true);
        b.setPartySize(1);
        o.addFreshEntity(b);
        b.initFight();
        b.setPos(b.home());
        return b;
    }

    private static void steps(PrimordialEntity b, int n) {
        for (int i = 0; i < n; i++) b.fightStep();
    }

    private static float hit(PrimordialEntity b, ServerPlayer p, float amount) {
        b.tickCount += PrimordialEntity.IFRAMES + 1;
        float before = b.getHealth();
        b.hurt(p.damageSources().playerAttack(p), amount);
        return before - b.getHealth();
    }

    /** Every cell of the plan stands in the built Origin (the gate's pedestal and dark lanterns aside), and the relics. */
    @GameTest(template = "empty", batch = "firmages_6_arena_plan", timeoutTicks = 600)
    public static void grandArenaMatchesThePlan(GameTestHelper h) {
        ServerLevel o = origin(h);
        MinecraftServer server = h.getLevel().getServer();
        ok(h, OriginArena.layout() == OriginLayout.GRAND && OriginSavedData.get(server).layout() == OriginLayout.GRAND,
            "a new Origin is grand: " + OriginArena.layout());
        ok(h, OriginArena.radius() == 3 * OriginArena.RADIUS, "three times the classic diameter");
        boolean sgj = ModList.get().isLoaded("sgjourney");
        BlockPos g = OriginArena.gateOrigin();
        AABB pedestal = new AABB(g.getX(), g.getY(), g.getZ(), g.getX() + 7, g.getY() + 9, g.getZ() + 13);
        Map<String, BlockState> states = new java.util.HashMap<>();
        Map<String, Integer> found = new TreeMap<>();
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (var e : GrandArenaPlan.cells().entrySet()) {
            long pk = e.getKey();
            BlockPos p = new BlockPos(GrandArenaPlan.unpackX(pk), GrandArenaPlan.unpackY(pk), GrandArenaPlan.unpackZ(pk));
            if (sgj && pedestal.contains(Vec3.atCenterOf(p))) continue;
            BlockState want = states.computeIfAbsent(e.getValue(), OriginArena::grandState);
            BlockState is = o.getBlockState(p);
            boolean same = e.getValue().equals("arena_altar") ? is.is(want.getBlock())
                : e.getValue().equals("lantern") ? is.is(Blocks.SEA_LANTERN) || is.is(Blocks.POLISHED_BLACKSTONE)
                : is == want;
            checked++;
            if (same) found.merge(e.getValue(), 1, Integer::sum);
            else if (wrong.size() < 5) wrong.add(e.getValue() + " at " + p.toShortString() + " is " + is);
        }
        ok(h, wrong.isEmpty(), "cells differ from the plan: " + wrong);
        ok(h, checked > 100_000, "the whole plan was checked: " + checked);
        for (BlockPos a : OriginArena.altars()) ok(h, o.getBlockState(a).is(OriginRegistry.ARENA_ALTAR.get()), "fight altar at " + a.toShortString());
        ok(h, o.getBlockState(OriginArena.ALTAR).is(OriginRegistry.ORIGIN_ALTAR.get()), "the Gathering altar in the centre");
        ok(h, o.getBlockState(OriginArena.arrival().below()).is(Blocks.POLISHED_DEEPSLATE), "arrival on the platform");
        ok(h, o.getBlockState(OriginArena.islandTop(0)).isSolid() && OriginArena.islandTop(0).getX() > GrandArenaPlan.RADIUS,
            "the islands lie beyond the arena");
        // relics: one per signature item, once
        long expected = java.util.stream.IntStream.range(0, 9).filter(i -> OriginEchoes.signature(i).isPresent()).count();
        List<long[]> relicChunks = new ArrayList<>();
        for (int i : new int[] {0, 4, 5, 8}) {
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(OriginRelics.position(i));
            o.setChunkForced(cp.x, cp.z, true);
            relicChunks.add(new long[] {cp.x, cp.z});
        }
        OriginRelics.tick(o);
        OriginRelics.tick(o);
        h.startSequence()
            .thenWaitUntil(() -> {
                List<Display.ItemDisplay> relics = o.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(-12, 70, 55, 13, 80, 85),
                    d -> d.getTags().contains(OriginRelics.TAG));
                if (relics.size() != expected) {
                    OriginRelics.tick(o);
                    h.fail("relics " + relics.size() + " of " + expected);
                }
            })
            .thenExecute(() -> release(o, relicChunks))
            .thenSucceed();
    }

    /** A right click with an empty hand channels an altar for 3 s; walking off ends it; a full hand does nothing. */
    @GameTest(template = "empty", batch = "firmages_6_arena_altars", timeoutTicks = 400)
    public static void altarChannelLightsAnAltar(GameTestHelper h) {
        ServerLevel o = origin(h);
        List<long[]> chunks = force(o);
        ArenaAltars.resetAll(o);
        ServerPlayer p = player(h, o, "gt_altar_a", atAltar(0));
        BlockPos a0 = OriginArena.altars().get(0), a1 = OriginArena.altars().get(1);
        BlockHitResult hit0 = new BlockHitResult(Vec3.atCenterOf(a0), Direction.UP, a0, false);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        ok(h, o.getBlockState(a0).useWithoutItem(o, p, hit0) == InteractionResult.PASS, "a full hand does nothing");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ok(h, o.getBlockState(a0).useWithoutItem(o, p, hit0) == InteractionResult.SUCCESS, "an empty hand starts the channel");
        ok(h, p.getUUID().equals(ArenaAltars.circuit().channeler(0)), "he channels altar 0");
        h.startSequence()
            .thenIdle(AltarCircuit.CHANNEL_TICKS / 2)
            .thenExecute(() -> ok(h, !o.getBlockState(a0).getValue(ArenaAltarBlock.ACTIVE), "not lit after 1.5 s"))
            .thenIdle(AltarCircuit.CHANNEL_TICKS / 2 + 3)
            .thenExecute(() -> {
                ok(h, o.getBlockState(a0).getValue(ArenaAltarBlock.ACTIVE), "lit after 3 s");
                ok(h, ArenaAltars.activeCount(o) == 1, "one altar burns");
                p.teleportTo(o, atAltar(1).x, atAltar(1).y, atAltar(1).z, 0, 0);
                ok(h, ArenaAltars.startChannel(o, a1, p), "channel altar 1");
            })
            .thenIdle(10)
            .thenExecute(() -> p.teleportTo(o, a1.getX() + 10.5, a1.getY(), a1.getZ() + 0.5, 0, 0))
            .thenIdle(AltarCircuit.CHANNEL_TICKS)
            .thenExecute(() -> {
                ok(h, !o.getBlockState(a1).getValue(ArenaAltarBlock.ACTIVE), "walking off ends the channel");
                ok(h, ArenaAltars.circuit().channeler(1) == null, "no channel left");
                ArenaAltars.resetAll(o);
                DebugPlayerCommands.despawn(p);
                release(o, chunks);
            })
            .thenSucceed();
    }

    /** Phase 2 raises the shield and darkens the altars; no hit counts until all four burn; then hits count again. */
    @GameTest(template = "empty", batch = "firmages_6_arena_shield", timeoutTicks = 300)
    public static void shieldFallsWhenTheFourAltarsBurn(GameTestHelper h) {
        ServerLevel o = origin(h);
        MinecraftServer server = h.getLevel().getServer();
        OriginService.reset(server);
        List<long[]> chunks = force(o);
        h.startSequence()
            .thenWaitUntil(() -> {
                if (!o.isPositionEntityTicking(OriginArena.ALTAR)) h.fail("the arena does not tick entities yet");
            })
            .thenExecute(() -> {
                ArenaAltars.completeAll(o);
                PrimordialEntity b = boss(o);
                ServerPlayer p = player(h, o, "gt_altar_shield", new Vec3(0.5, OriginArena.ALTAR.getY(), 6.5));
                try {
                    ok(h, !b.shielded() && hit(b, p, 20) > 0, "phase 1: no shield");
                    b.enterPhase(2);
                    ok(h, b.shielded(), "phase 2 raises the shield");
                    ok(h, ArenaAltars.activeCount(o) == 0, "and darkens every altar");
                    steps(b, b.phaseLock() + 1);
                    ok(h, hit(b, p, 20) == 0, "no hit counts behind the shield");
                    for (int i = 0; i < 3; i++) ArenaAltars.set(o, i, true);
                    steps(b, 1);
                    ok(h, b.shielded() && hit(b, p, 20) == 0, "three altars are not enough");
                    ArenaAltars.set(o, 3, true);
                    steps(b, 1);
                    ok(h, !b.shielded(), "the fourth altar drops the shield");
                    ok(h, hit(b, p, 20) > 0, "hits count again");
                    b.enterPhase(3);
                    ok(h, b.shielded() && ArenaAltars.activeCount(o) == 0, "phase 3 raises it again");
                    // a classic Origin has no altars and no shield
                    OriginArena.setLayout(OriginLayout.CLASSIC);
                    PrimordialEntity c = boss(o);
                    c.enterPhase(2);
                    ok(h, !c.shielded() && ArenaAltars.present(o) == false, "classic arena: no altars, no shield");
                    c.discard();
                } finally {
                    OriginArena.setLayout(OriginLayout.GRAND);
                    b.discard();
                    DebugPlayerCommands.despawn(p);
                    ArenaAltars.resetAll(o);
                    release(o, chunks);
                    OriginService.reset(server);
                }
            })
            .thenSucceed();
    }

    /** While the shield stands the boss strikes one lit altar every 20 s with a harmless bolt; with none lit, nothing. */
    @GameTest(template = "empty", batch = "firmages_6_arena_strike", timeoutTicks = 300)
    public static void bossStrikesALitAltarEveryTwentySeconds(GameTestHelper h) {
        ServerLevel o = origin(h);
        MinecraftServer server = h.getLevel().getServer();
        OriginService.reset(server);
        List<long[]> chunks = force(o);
        h.startSequence()
            .thenWaitUntil(() -> {
                if (!o.isPositionEntityTicking(OriginArena.ALTAR)) h.fail("the arena does not tick entities yet");
            })
            .thenExecute(() -> {
                PrimordialEntity b = boss(o);
                try {
                    b.enterPhase(3);
                    ArenaAltars.set(o, 0, true);
                    ArenaAltars.set(o, 2, true);
                    steps(b, AltarCircuit.STRIKE_TICKS - 1);
                    ok(h, ArenaAltars.activeCount(o) == 2, "no strike before 20 s");
                    steps(b, 1);
                    ok(h, ArenaAltars.activeCount(o) == 1, "one altar struck after 20 s, lit " + ArenaAltars.activeCount(o));
                    ok(h, !o.getEntitiesOfClass(LightningBolt.class, new AABB(OriginArena.ALTAR).inflate(24)).isEmpty(), "with a bolt");
                    steps(b, AltarCircuit.STRIKE_TICKS);
                    ok(h, ArenaAltars.activeCount(o) == 0, "the second after 40 s");
                    steps(b, AltarCircuit.STRIKE_TICKS);
                    ok(h, ArenaAltars.activeCount(o) == 0 && b.shielded(), "nothing left to strike, the shield stands");
                    ok(h, b.shieldTicks() == 3 * AltarCircuit.STRIKE_TICKS, "shield ticks " + b.shieldTicks());
                } finally {
                    b.discard();
                    ArenaAltars.resetAll(o);
                    release(o, chunks);
                    OriginService.reset(server);
                }
            })
            .thenSucceed();
    }
}
