package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.boss.AgeShotEntity;
import dev.firmages.core.boss.ArenaLights;
import dev.firmages.core.boss.BossRegistry;
import dev.firmages.core.boss.BossShots;
import dev.firmages.core.boss.BossShotsService;
import dev.firmages.core.boss.OriginSkyState;
import dev.firmages.core.boss.PrimordialEntity;
import dev.firmages.core.boss.PrimordialGeometry;
import dev.firmages.core.boss.ShotAge;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Level G tests of round G4 (SPEC §39.2, §16.6): the halo fires its ten segments in Age order at the phase's pace, each
 * Age's shot leaves its mark on a survival player, the ring's edge strikes within four blocks, the sky's mood follows
 * the synced phase and the storm's bolts are harmless. The boss runs with NoAI and the tests step it by hand; the
 * target players are survival debug players (the boss ignores creative ones).
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class RingGameTests {
    private RingGameTests() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(RingGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean cond, String what) {
        if (!cond) h.fail(what);
    }

    private static PrimordialEntity spawnBoss(ServerLevel level, Vec3 at) {
        PrimordialEntity b = BossRegistry.PRIMORDIAL.get().create(level);
        b.moveTo(at.x, at.y, at.z, 0, 0);
        b.setNoAi(true);
        b.setPartySize(1);
        level.addFreshEntity(b);
        b.initFight();
        b.setPos(b.home());
        return b;
    }

    /** A survival debug player with 200 health (it must survive every mark) and no invulnerability frames. */
    private static ServerPlayer survivor(GameTestHelper h, ServerLevel level, String name, Vec3 at) {
        ServerPlayer p = DebugPlayerCommands.spawn(h.getLevel().getServer(), level, name, at);
        p.setGameMode(GameType.SURVIVAL);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        p.setHealth(200);
        p.invulnerableTime = 0;
        return p;
    }

    private static void steps(PrimordialEntity b, int n) {
        for (int i = 0; i < n; i++) b.fightStep();
    }

    private static List<AgeShotEntity> shots(ServerLevel level, AABB box) {
        List<AgeShotEntity> out = new ArrayList<>(level.getEntitiesOfClass(AgeShotEntity.class, box));
        out.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
        return out;
    }

    @GameTest(template = "empty", batch = "firmages_7_ring_order", timeoutTicks = 200)
    public static void ringFiresEveryAgeInOrder(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        PrimordialEntity b = spawnBoss(level, h.absoluteVec(new Vec3(1.5, 1, 1.5)));
        ServerPlayer p = survivor(h, level, "gt_ring_a", h.absoluteVec(new Vec3(1.5, 1, 12.5)));
        try {
            BossShots t = b.shotTable();
            ok(h, t.intervalTicks(1) == 30 && t.intervalTicks(3) == 14, "the pack's table is loaded (30 / 20 / 14 ticks)");
            ok(h, Math.abs(PrimordialGeometry.SPIN_DEGREES_PER_TICK - 27) < 1e-9, "the ring turns 1.5 times a second");
            AABB box = h.getBounds().inflate(32);
            steps(b, t.intervalTicks(1) - 1);
            ok(h, shots(level, box).isEmpty() && b.shotsFired() == 0, "nothing before the first interval");
            steps(b, 1);
            List<AgeShotEntity> first = shots(level, box);
            ok(h, first.size() == 1 && first.get(0).age() == ShotAge.STONE, "the first shot is Stone's boulder");
            // the step turns the body 6 degrees after the shot: the node moved a little since
            ok(h, first.get(0).position().distanceTo(b.nodeWorld(0)) < 0.5, "fired from segment 0's node, " + first.get(0).position() + " vs " + b.nodeWorld(0));
            ok(h, first.get(0).getDeltaMovement().length() > 0.89 && first.get(0).getDeltaMovement().length() < 0.91, "at the row's speed 0.9");
            ok(h, first.get(0).getOwner() == b, "owned by the boss");
            steps(b, t.intervalTicks(1) * 8);
            List<AgeShotEntity> all = shots(level, box);
            List<ShotAge> order = new ArrayList<>();
            for (AgeShotEntity s : all) if (order.isEmpty() || order.get(order.size() - 1) != s.age()) order.add(s.age());
            List<ShotAge> want = List.of(ShotAge.STONE, ShotAge.BRONZE, ShotAge.IRON, ShotAge.ARCANE, ShotAge.INDUSTRIAL, ShotAge.ELECTRIC,
                ShotAge.INFORMATION, ShotAge.SPACE, ShotAge.QUANTUM);
            ok(h, order.equals(want), "nine segments in Age order in phase 1, got " + order);
            ok(h, all.size() == 11 && b.shotsFired() == 9, "Information fires a fan of three: 11 entities for 9 releases, got " + all.size());
            ok(h, all.stream().noneMatch(s -> s.age() == ShotAge.SINGULARITY), "no black hole before phase 3");
            all.forEach(AgeShotEntity::discard);

            b.enterPhase(3);
            steps(b, PrimordialEntity.PHASE3_LOCK);
            ok(h, shots(level, box).isEmpty(), "nothing fires during the phase lock");
            steps(b, t.intervalTicks(3) * 28);
            List<AgeShotEntity> late = shots(level, box);
            long holes = late.stream().filter(s -> s.age() == ShotAge.SINGULARITY).count();
            ok(h, holes == 1, "phase 3, 28 shots: the black hole once (one ring cycle in three), got " + holes);
            ok(h, late.stream().filter(s -> s.age() == ShotAge.SINGULARITY).allMatch(s -> s.row().phaseFrom() == 3), "its row says phase 3");
            late.forEach(AgeShotEntity::discard);
        } finally {
            b.discard();
            DebugPlayerCommands.despawn(p);
        }
        ok(h, level.getEntitiesOfClass(AgeShotEntity.class, h.getBounds().inflate(40)).isEmpty(), "its shots go with it");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "firmages_7_ring_marks", timeoutTicks = 400)
    public static void everyAgeLeavesItsMark(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        PrimordialEntity b = spawnBoss(level, h.absoluteVec(new Vec3(1.5, 1, 1.5)));
        Vec3 at = h.absoluteVec(new Vec3(1.5, 1, 20.5));
        ServerPlayer p = survivor(h, level, "gt_ring_p", at);
        ServerPlayer q = survivor(h, level, "gt_ring_q", at.add(3, 0, 0));
        try {
            BossShots t = BossShotsService.table(level.getServer());
            for (ShotAge age : ShotAge.values()) {
                p.setHealth(200);
                q.setHealth(200);
                p.invulnerableTime = 0;
                q.invulnerableTime = 0;
                p.setDeltaMovement(Vec3.ZERO);
                p.setRemainingFireTicks(0);
                p.removeAllEffects();
                p.setTicksFrozen(0);
                List<AgeShotEntity> fired = AgeShotEntity.fire(level, b, t, age, at.add(0, 1, -2), at.add(0, 1, 0));
                ok(h, fired.size() == t.shot(age).count(), age.key + ": " + t.shot(age).count() + " shot(s)");
                AgeShotEntity s = fired.get(0);
                for (int k = 1; k < fired.size(); k++) fired.get(k).discard();
                s.impact(level, at.add(0, 1, 0), p);
                float lost = 200 - p.getHealth();
                ok(h, lost > 0, age.key + ": the burst hurts, lost " + lost);
                switch (age) {
                    case STONE -> ok(h, p.getDeltaMovement().horizontalDistance() > 0.5, "Stone: knocked back, " + p.getDeltaMovement());
                    case BRONZE -> ok(h, p.getRemainingFireTicks() >= 100, "Bronze: burning 6 s, " + p.getRemainingFireTicks());
                    case IRON -> ok(h, s.row().pierce() == 2 && s.isRemoved(), "Iron: the lance's row runs through two");
                    case ARCANE -> ok(h, p.hasEffect(MobEffects.LEVITATION) && p.hasEffect(MobEffects.BLINDNESS), "Arcane: levitation and blindness");
                    case INDUSTRIAL -> {
                        List<AreaEffectCloud> clouds = level.getEntitiesOfClass(AreaEffectCloud.class, new AABB(at, at).inflate(3));
                        ok(h, clouds.size() == 1 && clouds.get(0).getDuration() == 80, "Industrial: a 4 s steam cloud, " + clouds.size());
                        clouds.forEach(AreaEffectCloud::discard);
                    }
                    case ELECTRIC -> {
                        ok(h, q.getHealth() < 200, "Electric: the chain bolt jumps to the second player, " + q.getHealth());
                        List<LightningBolt> bolts = level.getEntitiesOfClass(LightningBolt.class, new AABB(at, at).inflate(3));
                        ok(h, bolts.size() == 1, "Electric: a bolt strikes the impact, " + bolts.size());
                        bolts.forEach(LightningBolt::discard);
                    }
                    case INFORMATION -> ok(h, p.hasEffect(MobEffects.HUNGER) && p.hasEffect(MobEffects.WITHER), "Information: hunger and wither");
                    case SPACE -> {
                        ok(h, s.isWell() && !s.isRemoved(), "Space: the shot stays as a gravity well");
                        p.setDeltaMovement(Vec3.ZERO);
                        p.setPos(at.x, at.y, at.z + 4);
                        s.tick();
                        ok(h, p.getDeltaMovement().z < -0.1, "Space: the well pulls the player in, " + p.getDeltaMovement());
                        for (int k = 0; k < 70 && !s.isRemoved(); k++) s.tick();
                        ok(h, s.isRemoved(), "Space: the well ends after 3 s");
                        ok(h, p.getDeltaMovement().y > 0.2, "Space: then the shove, " + p.getDeltaMovement());
                        p.setPos(at.x, at.y, at.z);
                    }
                    case QUANTUM -> ok(h, p.getTicksFrozen() >= p.getTicksRequiredToFreeze() + 80 && p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                        "Quantum: frozen 2 s and slowed, " + p.getTicksFrozen());
                    case SINGULARITY -> {
                        ok(h, s.isWell(), "Singularity: the black hole stays");
                        float before = p.getHealth();
                        p.invulnerableTime = 0;
                        for (int k = 0; k < 70 && !s.isRemoved(); k++) {
                            p.invulnerableTime = 0;
                            s.tick();
                        }
                        ok(h, s.isRemoved() && p.getHealth() < before, "Singularity: the implosion hits, " + before + " -> " + p.getHealth());
                    }
                }
                if (!s.isRemoved()) s.discard();
            }
        } finally {
            b.discard();
            DebugPlayerCommands.despawn(p);
            DebugPlayerCommands.despawn(q);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "firmages_7_ring_melee", timeoutTicks = 200)
    public static void theRingsEdgeStrikesWithinFourBlocks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        PrimordialEntity b = spawnBoss(level, h.absoluteVec(new Vec3(1.5, 1, 1.5)));
        ServerPlayer near = survivor(h, level, "gt_ring_near", h.absoluteVec(new Vec3(3.5, 1, 1.5)));
        ServerPlayer far = survivor(h, level, "gt_ring_far", h.absoluteVec(new Vec3(1.5, 1, 7.5)));
        try {
            ok(h, Math.abs(b.getY() - (b.floorY() + PrimordialGeometry.HOVER)) < 1e-6, "hovers " + PrimordialGeometry.HOVER + " above its floor");
            steps(b, 1);
            ok(h, b.meleeStrikes() == 1 && near.getHealth() < 200, "the player 2 blocks off its axis on the floor takes the ring's edge");
            ok(h, near.getDeltaMovement().horizontalDistance() > 0.5, "and flies, " + near.getDeltaMovement());
            ok(h, far.getHealth() == 200, "the player 6 blocks away does not");
            near.setHealth(200);
            near.invulnerableTime = 0;
            steps(b, b.shotTable().melee().cooldownTicks() - 1);
            ok(h, b.meleeStrikes() == 1, "no second strike within 2 s");
            steps(b, 1);
            ok(h, b.meleeStrikes() == 2, "the next strike after 2 s");
        } finally {
            b.discard();
            DebugPlayerCommands.despawn(near);
            DebugPlayerCommands.despawn(far);
        }
        h.succeed();
    }

    /** The sky's mood is a pure function of the phase the entity syncs; the storm's bolts fall only in the arena and are harmless. */
    @GameTest(template = "empty", batch = "firmages_7_ring_sky", timeoutTicks = 300)
    public static void skyMoodFollowsThePhaseAndTheStormIsHarmless(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = OriginService.level(server);
        if (o == null) h.fail("firmages:origin is not loaded");
        OriginService.reset(server);
        List<int[]> chunks = new ArrayList<>();
        for (int x = -4; x <= 3; x++) {
            for (int z = -4; z <= 3; z++) {
                o.setChunkForced(x, z, true);
                chunks.add(new int[] {x, z});
            }
        }
        h.startSequence()
            .thenWaitUntil(() -> {
                // the storm's bolts fall up to the rim: every forced chunk must tick entities, or a bolt lands in a pending section
                for (int[] c : chunks) {
                    if (!o.isPositionEntityTicking(new BlockPos(c[0] * 16 + 8, 65, c[1] * 16 + 8))) h.fail("chunk " + c[0] + "," + c[1] + " does not tick entities yet");
                }
            })
            .thenExecute(() -> {
                BlockPos a = OriginArena.ALTAR;
                ServerPlayer p = survivor(h, o, "gt_ring_sky", new Vec3(a.getX() + 0.5, a.getY(), a.getZ() + 14.5));
                PrimordialEntity b = null;
                try {
                    b = spawnBoss(o, new Vec3(a.getX() + 0.5, a.getY() + PrimordialGeometry.HOVER, a.getZ() + 0.5));
                    ok(h, ArenaLights.applies(o), "in The Origin's arena");
                    OriginSkyState sky = new OriginSkyState();
                    ok(h, sky.aim(b.phase(), b.isDeadOrDying(), 0) == OriginSkyState.Mood.CLEAR, "phase 1: clear stars");
                    b.enterPhase(2);
                    ok(h, sky.aim(b.phase(), b.isDeadOrDying(), 0) == OriginSkyState.Mood.AURORA, "phase 2: aurora");
                    b.enterPhase(3);
                    ok(h, sky.aim(b.phase(), b.isDeadOrDying(), 0) == OriginSkyState.Mood.ECLIPSE, "phase 3: eclipse");
                    steps(b, PrimordialEntity.PHASE3_LOCK);
                    int interval = b.shotTable().stormIntervalTicks(3);
                    ok(h, interval == 30, "phase 3: a bolt every 1.5 s");
                    AABB arena = new AABB(a).inflate(OriginArena.radius() + 2, 8, OriginArena.radius() + 2);
                    int before = b.stormBolts();
                    steps(b, interval * 4);
                    ok(h, b.stormBolts() - before == 4, "four bolts in 6 s, got " + (b.stormBolts() - before));
                    List<LightningBolt> bolts = o.getEntitiesOfClass(LightningBolt.class, arena);
                    ok(h, bolts.size() >= 4, "the bolts stand in the arena, found " + bolts.size());
                    double r = OriginArena.radius();
                    for (LightningBolt bolt : bolts) {
                        double d = Math.hypot(bolt.getX() - a.getX() - 0.5, bolt.getZ() - a.getZ() - 0.5);
                        ok(h, d >= r * 0.6 - 0.01 && d <= r, "a bolt between the floor's edge and the rim, " + d + " of " + r);
                    }
                    for (int k = 0; k < 3; k++) o.getEntitiesOfClass(LightningBolt.class, arena).forEach(LightningBolt::tick);
                    ok(h, p.getHealth() == 200 && !p.isOnFire(), "visual only: the player 14 blocks out is untouched, health " + p.getHealth());
                    ok(h, o.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, arena, e -> e.isOnFire()).isEmpty(), "nothing burns");
                    ok(h, net.minecraft.core.BlockPos.betweenClosedStream(arena).noneMatch(pos -> o.getBlockState(pos).is(Blocks.FIRE)), "no fire on the floor");
                    b.hurt(b.damageSources().genericKill(), Float.MAX_VALUE);
                    ok(h, sky.aim(b.phase(), b.isDeadOrDying(), 100) == OriginSkyState.Mood.DAWN, "its death: dawn");
                } finally {
                    if (b != null && !b.isRemoved()) b.discard();
                    DebugPlayerCommands.despawn(p);
                    OriginService.reset(server);
                    chunks.forEach(c -> o.setChunkForced(c[0], c[1], false));
                }
            })
            .thenSucceed();
    }
}
