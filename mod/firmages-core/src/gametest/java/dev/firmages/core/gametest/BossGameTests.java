package dev.firmages.core.gametest;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageId;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.boss.ArenaLights;
import dev.firmages.core.boss.BossRegistry;
import dev.firmages.core.boss.PrimordialAbility;
import dev.firmages.core.boss.PrimordialEntity;
import dev.firmages.core.boss.StarFallEntity;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginSavedData;
import dev.firmages.core.origin.OriginService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Level G tests of The Primordial (docs/primordial.md): phase thresholds and the lanterns of Nine Lights, the veil and
 * the keel window, per-attacker hurt cooldowns and the effective pool, the shatter's six shards, the finale through the
 * tag, the return home, and that nothing of the fight stays behind. The boss runs with NoAI and the tests step its
 * fight by hand ({@link PrimordialEntity#fightStep}); the players are creative, which the boss never targets, so only
 * what a test does happens. Each test has its own batch (the Origin tests win the finale and reset it).
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class BossGameTests {
    private BossGameTests() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(BossGameTests.class);
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
        b.setPos(b.home()); // hovering at once (a summoned boss rises there over its first ticks)
        return b;
    }

    private static ServerPlayer player(GameTestHelper h, ServerLevel level, String name, Vec3 at) {
        ServerPlayer p = DebugPlayerCommands.spawn(h.getLevel().getServer(), level, name, at);
        p.setGameMode(GameType.CREATIVE); // never a target; creative also passes the age_9 lock of The Origin
        return p;
    }

    /** Lets its per-attacker hurt cooldowns run out (they count its own ticks). */
    private static void cooldown(PrimordialEntity b) {
        b.tickCount += PrimordialEntity.IFRAMES + 1;
    }

    private static void steps(PrimordialEntity b, int n) {
        for (int i = 0; i < n; i++) b.fightStep();
    }

    /** Hits {@code b} for {@code amount} as {@code p}. @return the health it lost (0 when the hit did not count) */
    private static float hit(PrimordialEntity b, ServerPlayer p, float amount) {
        float before = b.getHealth();
        b.hurt(p.damageSources().playerAttack(p), amount);
        return before - b.getHealth();
    }

    private static List<ChunkForce> forceArena(ServerLevel o) {
        List<ChunkForce> out = new ArrayList<>();
        for (int x = -2; x <= 1; x++) {
            for (int z = -2; z <= 1; z++) {
                o.setChunkForced(x, z, true);
                out.add(new ChunkForce(x, z));
            }
        }
        return out;
    }

    private record ChunkForce(int x, int z) {}

    private static void release(ServerLevel o, List<ChunkForce> chunks) {
        chunks.forEach(c -> o.setChunkForced(c.x(), c.z(), false));
    }

    private static Vec3 aboveAltar() {
        BlockPos a = OriginArena.ALTAR;
        return new Vec3(a.getX() + 0.5, a.getY() + 2.5, a.getZ() + 0.5);
    }

    /** 14 blocks south of the altar: inside the arena, outside the gather radius (no Gathering in these tests). */
    private static Vec3 arenaEdge(int dx) {
        BlockPos a = OriginArena.ALTAR;
        return new Vec3(a.getX() + 0.5 + dx, a.getY(), a.getZ() + 14.5);
    }

    @GameTest(template = "empty", batch = "firmages_6_boss_phases", timeoutTicks = 200)
    public static void phaseThresholdsAndLanterns(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = OriginService.level(server);
        if (o == null) h.fail("firmages:origin is not loaded");
        OriginService.reset(server);
        List<ChunkForce> chunks = forceArena(o);
        h.startSequence()
            .thenWaitUntil(() -> {
                if (!o.isPositionEntityTicking(OriginArena.ALTAR)) h.fail("the arena does not tick entities yet");
            })
            .thenExecute(() -> {
                ServerPlayer p = player(h, o, "gt_boss_a", arenaEdge(0));
                ServerPlayer q = player(h, o, "gt_boss_b", arenaEdge(2));
                PrimordialEntity b = null;
                try {
                    List<BlockPos> lit = ArenaLights.lit(o);
                    ok(h, lit.size() >= 8, "the arena has its sea lanterns (spoke marks and rim), found " + lit.size());
                    b = spawnBoss(o, aboveAltar());
                    ok(h, b.getTags().contains(OriginService.FINAL_BOSS_TAG), "tagged as the final boss from birth");
                    ok(h, b.getMaxHealth() == PrimordialEntity.MAX_HEALTH && b.phase() == 1, "health 1024, phase 1");
                    ok(h, b.home().distanceTo(aboveAltar()) < 1.0E-6, "home above the altar, hover " + b.home());
                    ok(h, EventHooks.getPotentialSpawns(o, MobCategory.MONSTER, OriginArena.ALTAR.east(12), WeightedRandomList.create()).isEmpty(),
                        "nothing spawns in the arena while it lives");

                    float max = b.getMaxHealth();
                    b.setHealth(max * 2 / 3F + 3);
                    hit(b, p, 1000);
                    ok(h, b.phase() == 2 && Math.abs(b.getHealth() - max * 2 / 3F) < 0.01F, "one big hit stops at two thirds and enters phase 2, health "
                        + b.getHealth());
                    ok(h, b.phaseLock() == PrimordialEntity.PHASE2_LOCK, "phase 2 starts with its lock");
                    ok(h, ArenaLights.lit(o).isEmpty() && b.darkened().size() == lit.size(), "Nine Lights: every lantern out, " + b.darkened().size()
                        + " of " + lit.size() + " remembered");
                    ok(h, lit.stream().allMatch(pos -> o.getBlockState(pos).is(Blocks.POLISHED_BLACKSTONE)), "the lanterns became polished blackstone");
                    ok(h, hit(b, q, 50) == 0, "no damage while the phase changes");
                    steps(b, PrimordialEntity.PHASE2_LOCK);
                    ok(h, b.phaseLock() == 0, "the lock runs out with the fight steps");

                    b.setHealth(max / 3F + 3);
                    hit(b, q, 1000);
                    ok(h, b.phase() == 3 && Math.abs(b.getHealth() - max / 3F) < 0.01F, "phase 3 at one third, health " + b.getHealth());
                    ok(h, ArenaLights.lit(o).isEmpty() && b.darkened().size() == lit.size(), "still dark in phase 3");

                    b.hurt(b.damageSources().genericKill(), Float.MAX_VALUE);
                    ok(h, b.isDeadOrDying(), "it dies");
                    ok(h, ArenaLights.lit(o).size() == lit.size() && lit.stream().allMatch(pos -> o.getBlockState(pos).is(Blocks.SEA_LANTERN)),
                        "every lantern burns again after its death");
                } finally {
                    if (b != null && !b.isRemoved()) b.discard();
                    DebugPlayerCommands.despawn(p);
                    DebugPlayerCommands.despawn(q);
                    OriginService.reset(server);
                    release(o, chunks);
                }
            })
            .thenSucceed();
    }

    @GameTest(template = "empty", batch = "firmages_6_boss_veil", timeoutTicks = 200)
    public static void veilAndKeelWindow(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        PrimordialEntity b = spawnBoss(level, h.absoluteVec(new Vec3(1.5, 1, 1.5)));
        ServerPlayer p = player(h, level, "gt_boss_veil", h.absoluteVec(new Vec3(1.5, 1, 9.5)));
        try {
            ok(h, Math.abs(b.getY() - (b.floorY() + 2.5)) < 1.0E-6, "hovers 2.5 blocks above its floor");
            float closed = hit(b, p, 20);
            ok(h, closed > 0 && !b.veilOpen(), "a hit lands on the closed veil, " + closed);
            cooldown(b);
            b.startAbility(PrimordialAbility.KEEL_SLAM);
            steps(b, PrimordialEntity.SLAM_IMPACT - 5);
            ok(h, hit(b, p, 20) > 0 && !b.veilOpen(), "a hit during the telegraph does not open the veil");
            cooldown(b);
            steps(b, 5);
            ok(h, b.abilityTick() == PrimordialEntity.SLAM_IMPACT && Math.abs(b.getY() - b.floorY()) < 1.0E-6,
                "the keel lies on the floor at the impact, y " + b.getY() + ", floor " + b.floorY());
            float open = hit(b, p, 20);
            ok(h, b.veilOpen() && b.veilTicks() == PrimordialEntity.VEIL_MELEE, "a melee hit on the grounded keel opens the veil for 6 s");
            float ratio = open / closed;
            ok(h, Math.abs(ratio - 1 / ServerConfig.bossVeilFactor()) < 0.05, "the open veil takes 4x the damage, ratio " + ratio);
            steps(b, PrimordialEntity.VEIL_MELEE);
            ok(h, !b.veilOpen(), "the veil closes after 6 s");
            ok(h, b.ability() == PrimordialAbility.NONE && Math.abs(b.getY() - b.home().y) < 1.0E-6, "the slam is over, it hovers again");
        } finally {
            b.discard();
            DebugPlayerCommands.despawn(p);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "firmages_6_boss_attackers", timeoutTicks = 200)
    public static void perAttackerDamageAndPool(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        PrimordialEntity b = spawnBoss(level, h.absoluteVec(new Vec3(1.5, 1, 1.5)));
        ServerPlayer p = player(h, level, "gt_boss_p", h.absoluteVec(new Vec3(1.5, 1, 9.5)));
        ServerPlayer q = player(h, level, "gt_boss_q", h.absoluteVec(new Vec3(3.5, 1, 9.5)));
        try {
            ok(h, Math.abs(b.effectivePool() - 2400) < 1.0E-6, "one player: effective health 2400, was " + b.effectivePool());
            b.setPartySize(2);
            ok(h, Math.abs(b.effectivePool() - 3480) < 1.0E-6, "two players: 3480");
            b.setPartySize(4);
            ok(h, Math.abs(b.effectivePool() - 5640) < 1.0E-6, "four players: 5640");
            b.setPartySize(1);

            float first = hit(b, p, 20);
            float again = hit(b, p, 20);
            float other = hit(b, q, 20);
            ok(h, first > 0 && again == 0, "the same attacker within 10 ticks does not count twice");
            ok(h, Math.abs(other - first) < 1.0E-3, "a second attacker's hit counts in full in the same tick: " + first + " and " + other);
            // 20 damage, armour 12 / toughness 8 -> 14.4, times 1024 / 2400, times the closed veil 0.25
            float expected = (float) (14.4 * PrimordialEntity.MAX_HEALTH / 2400 * ServerConfig.bossVeilFactor());
            ok(h, Math.abs(first - expected) < 0.01, "scaled to the effective pool behind the closed veil: " + first + ", expected " + expected);
        } finally {
            b.discard();
            DebugPlayerCommands.despawn(p);
            DebugPlayerCommands.despawn(q);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "firmages_6_boss_shatter", timeoutTicks = 200)
    public static void shatterDropsSixShards(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        PrimordialEntity b = spawnBoss(level, h.absoluteVec(new Vec3(1.5, 1, 1.5)));
        ServerPlayer p = player(h, level, "gt_boss_shard", h.absoluteVec(new Vec3(1.5, 1, 12.5)));
        try {
            b.enterPhase(3);
            ok(h, b.phase() == 3, "phase 3");
            b.startAbility(PrimordialAbility.SHATTER);
            steps(b, PrimordialEntity.SHATTER_PEAK);
            Vec3 home = b.home();
            AABB floor = new AABB(home, home).inflate(10, 4, 10);
            List<ItemEntity> shards = level.getEntitiesOfClass(ItemEntity.class, floor, e -> e.getItem().is(BossRegistry.CONSECRATED_SHARD.get()));
            ok(h, shards.size() == PrimordialEntity.SHARDS_PER_SHATTER, "six consecrated shards on the floor, found " + shards.size());
            ok(h, shards.stream().allMatch(s -> s.lifespan == PrimordialEntity.SHARD_LIFESPAN), "shards on the floor vanish after 60 s");
            List<StarFallEntity> stars = level.getEntitiesOfClass(StarFallEntity.class, new AABB(home, home).inflate(16, 40, 16));
            ok(h, stars.size() == 9, "nine stars fall at once, found " + stars.size());
            ok(h, b.getY() > home.y + 2.5, "it rose for the shatter, y " + b.getY());
            ok(h, hit(b, p, 20) == 0, "untouchable while the sky breaks");
            shards.forEach(Entity::discard);
        } finally {
            b.discard();
            DebugPlayerCommands.despawn(p);
        }
        ok(h, level.getEntitiesOfClass(StarFallEntity.class, h.getBounds().inflate(40)).isEmpty(), "its star-falls go with it");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "firmages_6_boss_finale", timeoutTicks = 200)
    public static void deathGrantsFinaleThroughTheTag(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = OriginService.level(server);
        if (o == null) h.fail("firmages:origin is not loaded");
        OriginService.reset(server);
        List<ChunkForce> chunks = forceArena(o);
        ServerPlayer p = player(h, o, "gt_boss_win", arenaEdge(0));
        PrimordialEntity b = spawnBoss(o, aboveAltar());
        try {
            ok(h, b.getTags().contains(OriginService.FINAL_BOSS_TAG) && !OriginSavedData.get(server).won(), "tagged, not won yet");
            b.hurt(b.damageSources().genericKill(), Float.MAX_VALUE);
            ok(h, OriginSavedData.get(server).won(), "its death wins the finale (OriginService, the tag)");
            ok(h, ProgressiveStagesAPI.hasStage(p, StageId.of(OriginService.FINALE_STAGE)), "finale_won granted to the online player");
        } finally {
            DebugPlayerCommands.despawn(p);
            OriginService.reset(server);
        }
        h.startSequence()
            .thenIdle(PrimordialEntity.DEATH_TICKS + 2)
            .thenExecute(() -> {
                release(o, chunks);
                ok(h, b.isRemoved(), "the body is gone after the die animation");
            })
            .thenSucceed();
    }

    @GameTest(template = "empty", batch = "firmages_6_boss_home", timeoutTicks = 200)
    public static void returnsHome(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        PrimordialEntity b = spawnBoss(level, h.absoluteVec(new Vec3(1.5, 1, 1.5)));
        ServerPlayer p = player(h, level, "gt_boss_home", h.absoluteVec(new Vec3(1.5, 1, 9.5)));
        try {
            Vec3 home = b.home();
            b.setPos(home.x + 40, home.y, home.z);
            b.fightStep();
            ok(h, b.position().distanceTo(home) < 1.0E-6, "dragged 40 blocks away it is home at once, at " + b.position());
            b.setPos(home.x, home.y + 20, home.z);
            b.fightStep();
            ok(h, b.position().distanceTo(home) < 1.0E-6, "lifted 20 blocks it is home at once");
            b.setPos(home.x + 6, home.y, home.z);
            b.fightStep();
            ok(h, b.position().distanceTo(home) > 5, "within its leash it drifts, no jump");
        } finally {
            b.discard();
            DebugPlayerCommands.despawn(p);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "firmages_6_boss_leftovers", timeoutTicks = 300)
    public static void nothingStaysBehind(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        PrimordialEntity b = spawnBoss(level, h.absoluteVec(new Vec3(1.5, 1, 1.5)));
        ServerPlayer p = player(h, level, "gt_boss_left", h.absoluteVec(new Vec3(1.5, 1, 12.5)));
        AABB around = h.getBounds().inflate(48);
        try {
            b.enterPhase(3);
            b.startAbility(PrimordialAbility.SHATTER);
            steps(b, PrimordialEntity.SHATTER_PEAK);
            ok(h, !level.getEntitiesOfClass(StarFallEntity.class, around).isEmpty(), "star-falls in the air");
            b.hurt(b.damageSources().genericKill(), Float.MAX_VALUE);
            ok(h, level.getEntitiesOfClass(StarFallEntity.class, around).isEmpty(), "its star-falls vanish with its death");
        } finally {
            DebugPlayerCommands.despawn(p);
        }
        h.startSequence()
            .thenIdle(PrimordialEntity.DEATH_TICKS + 2)
            .thenExecute(() -> {
                List<ItemEntity> shards = level.getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(BossRegistry.CONSECRATED_SHARD.get()));
                shards.forEach(Entity::discard);
                ok(h, level.getEntitiesOfClass(PrimordialEntity.class, around).isEmpty(), "no boss left");
                ok(h, level.getEntitiesOfClass(Monster.class, around).isEmpty(), "no adds: no monster ever appeared");
                int count = shards.stream().mapToInt(s -> s.getItem().getCount()).sum(); // shards on the floor merge
                ok(h, count == PrimordialEntity.SHARDS_PER_SHATTER && shards.stream().allMatch(s -> s.lifespan == PrimordialEntity.SHARD_LIFESPAN),
                    "only the shards stayed, and those for 60 s at most: " + count);
            })
            .thenSucceed();
    }

    /**
     * The client assets load with GeckoLib's own parsers (the same calls as its cache on a resource reload): the geo
     * bakes, the halo and node bones the code drives exist, every animation the entity plays exists and names only
     * existing bones, and each ability's animation is exactly as long as its server timeline.
     */
    @GameTest(template = "empty", batch = "firmages_6_boss_assets")
    public static void assetsLoadWithGeckoLib(GameTestHelper h) throws IOException {
        Path root = ModList.get().getModFileById(FirmagesCore.MOD_ID).getFile().findResource("assets", FirmagesCore.MOD_ID);
        Model model;
        JsonObject animJson;
        try (Reader geo = Files.newBufferedReader(root.resolve("geo/entity/primordial.geo.json"));
             Reader anim = Files.newBufferedReader(root.resolve("animations/entity/primordial.animation.json"))) {
            model = KeyFramesAdapter.GEO_GSON.fromJson(geo, Model.class);
            animJson = JsonParser.parseReader(anim).getAsJsonObject();
        }
        BakedGeoModel baked = BakedModelFactory.DEFAULT_FACTORY
            .constructGeoModel(GeometryTree.fromModel(model));
        Set<String> bones = new HashSet<>();
        ArrayDeque<GeoBone> todo = new ArrayDeque<>(baked.topLevelBones());
        while (!todo.isEmpty()) {
            GeoBone b = todo.pop();
            bones.add(b.getName());
            todo.addAll(b.getChildBones());
        }
        ok(h, bones.size() == 28, "28 bones baked, found " + bones.size());
        for (String b : List.of("halo", "halo_spin", "eye", "core_a", "core_b", "keel", "spire")) ok(h, bones.contains(b), "bone " + b);
        for (int i = 0; i < 9; i++) ok(h, bones.contains("shard_" + i), "node shard " + i);
        BakedAnimations anims = KeyFramesAdapter.GEO_GSON
            .fromJson(GsonHelper.getAsJsonObject(animJson, "animations"), BakedAnimations.class);
        for (String a : List.of("idle", "hurt", "veil_open", "veil_close", "split", "die")) ok(h, anims.getAnimation(a) != null, "animation " + a);
        ok(h, anims.animations().size() == 11, "eleven animations, found " + anims.animations().keySet());
        for (Animation a : anims.animations().values()) {
            for (BoneAnimation ba : a.boneAnimations()) {
                ok(h, bones.contains(ba.boneName()), "animation " + a.name() + " moves unknown bone " + ba.boneName());
            }
        }
        for (PrimordialAbility ab : PrimordialAbility.values()) {
            if (ab == PrimordialAbility.NONE) continue;
            Animation a = anims.getAnimation(ab.animation);
            ok(h, a != null && Math.round(a.length()) == ab.length, ab + ": animation " + (a == null ? "missing" : a.length() + " ticks") + ", timeline " + ab.length);
        }
        h.succeed();
    }
}
