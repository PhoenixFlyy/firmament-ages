package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeIndex;
import dev.firmages.core.age.AgeMirror;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.CacheGeneration;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.command.SelfTest;
import dev.firmages.core.compat.progressivestages.LockSyncDedupe;
import dev.firmages.core.command.CoreSuite;
import dev.firmages.core.command.GateSuite;
import dev.firmages.core.command.ServerSuite;
import dev.firmages.core.compat.kubejs.FirmAgesJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Level G tests of the core (SPEC §12) in the dev GameTest server: {@code gradlew runGameTestServer}.
 * Test data: {@code src/gametest/resources/data/firmages/tags} (iron ores and ingots in age_2, gold ore in age_5,
 * lava in age_4) and the KubeJS probe {@code src/gametest/kubejs/server_scripts/firmages_probe.js}.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class CoreGameTests {
    private static final TagKey<Block> PROBE_LOCKED = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("firmages", "test/locked_ores"));
    private static final TagKey<Block> PROBE_RAN = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("firmages", "test/probe_ran"));

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(CoreGameTests.class);
    }

    private static boolean kubejs() {
        return ModList.get().isLoaded("kubejs");
    }

    /** The in-pack self-test suites pass on a fresh world with the test tags. */
    @GameTest(template = "empty", batch = "firmages_1_selftest")
    public static void selftestSuitesPass(GameTestHelper helper) {
        SelfTest.Report report = SelfTest.run(List.of(new CoreSuite(), new GateSuite(), new ServerSuite(helper.getLevel().getServer())));
        List<String> failed = report.cases().stream().filter(c -> !c.passed()).map(c -> c.suite() + "/" + c.name() + ": " + c.detail()).toList();
        helper.assertTrue(failed.isEmpty(), "selftest failures: " + failed);
        helper.succeed();
    }

    /** The M1 commands run without errors; the registry dump is written. */
    @GameTest(template = "empty", batch = "firmages_1_selftest")
    public static void commandsRun(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack();
        var dispatcher = server.getCommands().getDispatcher();
        try {
            helper.assertTrue(dispatcher.execute("firmages ages", source) >= 1, "/firmages ages");
            helper.assertTrue(dispatcher.execute("firmages selftest core", source) == 0, "/firmages selftest core has failures");
            helper.assertTrue(dispatcher.execute("firmages dump registry", source) > 0, "/firmages dump registry");
            java.nio.file.Path dump = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("logs").resolve(dev.firmages.core.command.RegistryDump.FILE);
            String json = java.nio.file.Files.readString(dump);
            helper.assertTrue(json.contains("\"minecraft:iron_ore\"") && json.contains("\"blocks\""), "dump content");
            try {
                dispatcher.execute("firmages ages simulate grant age_3", source);
                helper.fail("simulate must be refused while debug.allowSimulate = false");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
                // refused as expected
            }
        } catch (Exception e) {
            helper.fail("command failed: " + e);
        }
        helper.succeed();
    }

    /** AgeIndex from the tag JSON: nested tags, earliest Age wins, optional unknown ids skipped, fluids. */
    @GameTest(template = "empty", batch = "firmages_1_selftest")
    public static void ageIndexFromTagJson(GameTestHelper helper) {
        AgeIndex idx = AgeIndex.current();
        helper.assertTrue(idx.ageOf(Blocks.IRON_ORE) == AgeId.AGE_2, "iron_ore -> age_2 via nested tag, was " + idx.ageOf(Blocks.IRON_ORE));
        helper.assertTrue(idx.ageOf(Blocks.DEEPSLATE_IRON_ORE) == AgeId.AGE_2, "deepslate_iron_ore -> age_2");
        helper.assertTrue(idx.ageOf(Blocks.GOLD_ORE) == AgeId.AGE_5, "gold_ore -> age_5");
        helper.assertTrue(idx.ageOf(Blocks.STONE) == null, "stone untagged");
        helper.assertTrue(idx.ageOf(Items.IRON_INGOT) == AgeId.AGE_2, "iron_ingot in age_2 and age_3 -> earliest age_2");
        helper.assertTrue(idx.ageOf(Items.IRON_PICKAXE) == AgeId.AGE_2, "iron_pickaxe via nested item tag");
        helper.assertTrue(idx.ageOf(Items.COPPER_INGOT) == AgeId.AGE_0, "copper_ingot -> age_0");
        helper.assertTrue(idx.ageOf(Fluids.LAVA) == AgeId.AGE_4, "lava -> age_4");
        helper.assertTrue(idx.report().warnings().stream().anyMatch(w -> w.contains("minecraft:iron_ingot")), "duplicate warning for iron_ingot");
        helper.assertTrue(AgeIndex.loadsBegun() >= 1, "mixin captured the datapack load");
        helper.succeed();
    }

    /**
     * Unlock → coalesced reload → index, binding and the KubeJS tag follow; revoke → locked again after the reload.
     * Uses AgeService.simulate (the same path as a ProgressiveStages StageChangeEvent, minus PS).
     */
    @GameTest(template = "empty", batch = "firmages_2_reload", timeoutTicks = 1200)
    public static void unlockAndRevokeReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        String iron = "minecraft:iron_ore";
        int[] before = new int[3];
        int[] afterGrant = new int[3];
        helper.startSequence()
            // The initial load's KubeJS pre-capture cannot read tags yet; the boot reconcile reload must settle first.
            .thenWaitUntil(() -> {
                AgeService.Status st = AgeService.status(server);
                helper.assertTrue(st.phase() == ReloadScheduler.Phase.IDLE && (!st.bootReconcileRequested() || st.reloadsStarted() > 0),
                    "boot reconcile reload pending");
            })
            .thenExecute(() -> {
                AgeService.Status st = AgeService.status(server);
                helper.assertTrue(!st.bootAnswersStale() || st.bootReconcileRequested(), "stale boot answers without a boot reload");
                helper.assertTrue(FirmAgesJS.lockedOreBlocks().contains(iron), "iron_ore locked at start: " + FirmAgesJS.lockedOreBlocks());
                helper.assertTrue(!FirmAgesJS.isUnlocked("age_2"), "age_2 locked at start");
                if (kubejs()) {
                    helper.assertTrue(Blocks.BEDROCK.defaultBlockState().is(PROBE_RAN), "KubeJS probe script ran");
                    helper.assertTrue(Blocks.IRON_ORE.defaultBlockState().is(PROBE_LOCKED), "KubeJS tag has iron_ore while age_2 is locked");
                }
                before[0] = CacheGeneration.get();
                before[1] = AgeIndex.current().generation();
                before[2] = AgeIndex.loadsBegun();
                long versionBefore = AgeService.state(server).snapshot().version();
                helper.assertTrue(AgeService.simulate(server, AgeId.AGE_2, true), "grant changed AgeState");
                helper.assertTrue(!AgeService.simulate(server, AgeId.AGE_2, true), "second grant in the same tick is a no-op");
                AgeMirror.ReadResult m = AgeMirror.read(AgeService.mirrorPath(server));
                helper.assertTrue(m.snapshot().map(s -> s.isUnlocked(AgeId.AGE_2) && s.version() == versionBefore + 1).orElse(false),
                    "mirror written with age_2: " + m.detail());
            })
            .thenWaitUntil(() -> helper.assertTrue(CacheGeneration.get() > before[0], "reload after grant not finished yet"))
            .thenExecute(() -> {
                helper.assertTrue(CacheGeneration.get() == before[0] + 1, "exactly one reload for the grant, got " + (CacheGeneration.get() - before[0]));
                helper.assertTrue(AgeIndex.current().generation() > before[1], "AgeIndex rebuilt");
                helper.assertTrue(AgeIndex.loadsBegun() > before[2], "mixin captured the reload");
                helper.assertTrue(FirmAgesJS.isUnlocked("age_2"), "age_2 unlocked");
                helper.assertTrue(!FirmAgesJS.lockedOreBlocks().contains(iron), "iron_ore no longer locked");
                helper.assertTrue(FirmAgesJS.lockedOreBlocks().contains("minecraft:gold_ore"), "gold_ore (age_5) still locked");
                if (kubejs()) {
                    helper.assertTrue(!Blocks.IRON_ORE.defaultBlockState().is(PROBE_LOCKED), "KubeJS tag dropped iron_ore after the unlock reload");
                    helper.assertTrue(Blocks.GOLD_ORE.defaultBlockState().is(PROBE_LOCKED), "KubeJS tag keeps gold_ore");
                }
                afterGrant[0] = CacheGeneration.get();
                helper.assertTrue(AgeService.simulate(server, AgeId.AGE_2, false), "revoke changed AgeState");
            })
            .thenWaitUntil(() -> helper.assertTrue(CacheGeneration.get() > afterGrant[0], "reload after revoke not finished yet"))
            .thenExecute(() -> {
                helper.assertTrue(!FirmAgesJS.isUnlocked("age_2"), "age_2 locked again");
                helper.assertTrue(FirmAgesJS.lockedOreBlocks().contains(iron), "iron_ore locked again");
                if (kubejs()) {
                    helper.assertTrue(Blocks.IRON_ORE.defaultBlockState().is(PROBE_LOCKED), "KubeJS tag has iron_ore again");
                }
                AgeMirror.ReadResult m2 = AgeMirror.read(AgeService.mirrorPath(server));
                helper.assertTrue(m2.snapshot().map(s -> !s.isUnlocked(AgeId.AGE_2)).orElse(false), "mirror without age_2");
            })
            .thenSucceed();
    }

    /** A joining player gets one ProgressiveStages lock sync, not two (mixin.ps.PlayerJoinMixin). */
    @GameTest(template = "empty", batch = "firmages_1_ps_join", timeoutTicks = 100)
    public static void joinSendsOneLockSync(GameTestHelper helper) {
        if (!ModList.get().isLoaded("progressivestages")) {
            helper.succeed();
            return;
        }
        MinecraftServer server = helper.getLevel().getServer();
        long before = LockSyncDedupe.skippedOnJoin();
        ServerPlayer p = DebugPlayerCommands.spawn(server, helper.getLevel(), "gt_joiner", Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        try {
            long skipped = LockSyncDedupe.skippedOnJoin() - before;
            FirmagesCore.LOGGER.info("GameTest joinSendsOneLockSync: {} duplicate lock sync(s) skipped on join", skipped);
            helper.assertTrue(skipped >= 1, "the duplicate lock sync of onPlayerJoin was not skipped");
        } finally {
            DebugPlayerCommands.despawn(p);
        }
        helper.succeed();
    }
}
