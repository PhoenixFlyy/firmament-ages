package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.CacheGeneration;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.gate.GateReport;
import dev.firmages.core.gate.GateRules;
import dev.firmages.core.gate.RecipeGate;
import dev.firmages.core.miner.ExcavatorFilter;
import dev.firmages.core.miner.OreGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Level G tests of m2 and m3 (SPEC §12) in the dev GameTest server. Test data in {@code src/gametest/resources/data}:
 * Age tags (copper age_0, iron + iron tools age_2, gold ore age_5, lava age_4, dragon egg disabled), the synthetic
 * recipes {@code firmages:test/*}, and {@code tfc:prospectable} / {@code precisionprospecting:prospectable_mineral}
 * for the pack's KubeJS hand-off scripts ({@code kubejs/server_scripts/firmages}, copied by prepareGametestRun).
 * Batches run one after another in no fixed order; every batch that changes Ages restores {@code [dawn]}.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class GateGameTests {
    private static final String IRON = "firmages:test/iron_from_gravel";
    private static final String COPPER = "firmages:test/copper_from_sand";
    private static final String STONE = "firmages:test/stone_from_dirt";
    private static final String TAG_ALL_LOCKED = "firmages:test/tag_all_locked";
    private static final String TAG_MIXED = "firmages:test/tag_mixed";
    private static final String DISABLED = "firmages:test/disabled_output";
    private static final String FLUID = "firmages:test/fluid_byproduct";

    private static final TagKey<Block> TFC_PROSPECTABLE = blockTag("tfc", "prospectable");
    private static final TagKey<Block> PP_PROSPECTABLE = blockTag("precisionprospecting", "prospectable_mineral");
    private static final TagKey<Block> MINER_BLACKLIST = blockTag("mekanism", "miner_blacklist");

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(GateGameTests.class);
    }

    private static TagKey<Block> blockTag(String ns, String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ns, path));
    }

    private static boolean has(MinecraftServer server, String id) {
        return server.getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent();
    }

    private static void expectRecipes(GameTestHelper helper, MinecraftServer server, String state, List<String> present, List<String> absent) {
        for (String id : present) helper.assertTrue(has(server, id), state + ": recipe " + id + " must be present");
        for (String id : absent) helper.assertTrue(!has(server, id), state + ": recipe " + id + " must be filtered");
    }

    private static boolean idle(MinecraftServer server) {
        return AgeService.status(server).phase() == ReloadScheduler.Phase.IDLE;
    }

    // ---------------------------------------------------------------- m2 at boot ([dawn])

    /** The initial load is filtered with the fail-strict boot snapshot [dawn]; verdicts and report as expected. */
    @GameTest(template = "empty", batch = "firmages_1_selftest", timeoutTicks = 1200)
    public static void recipeGateAtBoot(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(idle(server), "reload pending"))
            .thenExecute(() -> {
                expectRecipes(helper, server, "dawn", List.of(STONE, TAG_MIXED),
                    List.of(IRON, COPPER, TAG_ALL_LOCKED, DISABLED, FLUID, "minecraft:iron_pickaxe", "minecraft:iron_ingot_from_smelting_iron_ore"));
                GateReport rep = RecipeGate.lastReport();
                helper.assertTrue(rep != null, "gate report");
                helper.assertTrue(rep.enabled() && !rep.misconfigured(), "gate enabled and configured: " + rep.summary());
                helper.assertTrue(rep.unlocked().equals(List.of("dawn")), "filtered with [dawn]: " + rep.unlocked());
                helper.assertTrue(rep.tagSource().equals("tag manager"), "tag outputs read from the load's tag manager: " + rep.tagSource());
                GateRules.Verdict tag = rep.entry(TAG_ALL_LOCKED).verdict();
                helper.assertTrue("age_2".equals(tag.bucket()) && "#firmages:test/iron_tools".equals(tag.entry()), "tag verdict " + tag);
                helper.assertTrue(GateRules.DISABLED.equals(rep.entry(DISABLED).verdict().bucket()), "disabled verdict");
                GateRules.Verdict fluid = rep.entry(FLUID).verdict();
                helper.assertTrue("age_4".equals(fluid.bucket()) && "minecraft:lava".equals(fluid.entry()), "fluid byproduct verdict " + fluid);
                helper.assertTrue("age_0".equals(rep.entry(COPPER).verdict().bucket()), "copper verdict");
                helper.assertTrue(rep.entry(TAG_MIXED).verdict().reason() == GateRules.Reason.UNLOCKED, "mixed tag kept");
                helper.assertTrue(rep.droppedPerBucket().getOrDefault("age_2", 0) > 0, "vanilla iron recipes dropped: " + rep.droppedPerBucket());
                helper.assertTrue(rep.errorCount() == 0, "extraction errors: " + rep.errorCount());
                // Late pass: recipes added after RecipeManager#apply (LateRecipeInjector) are gated with the same rules.
                expectRecipes(helper, server, "dawn, late pass", List.of(LateRecipeInjector.LATE_STONE), List.of(LateRecipeInjector.LATE_IRON));
                helper.assertTrue(rep.lateRecipes() >= 2 && rep.lateDropped() >= 1, "late pass counted: " + rep.summary());
                GateReport.Entry late = rep.entry(LateRecipeInjector.LATE_IRON);
                helper.assertTrue(late != null && !late.verdict().keep() && "age_2".equals(late.verdict().bucket()), "late iron verdict " + late);
            })
            .thenSucceed();
    }

    /** /firmages recipes audit|why|locked run and write the audit file. */
    @GameTest(template = "empty", batch = "firmages_1_selftest")
    public static void recipeCommandsRun(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack();
        var dispatcher = server.getCommands().getDispatcher();
        try {
            helper.assertTrue(dispatcher.execute("firmages recipes audit", source) > 0, "audit reports dropped recipes");
            String audit = java.nio.file.Files.readString(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("logs").resolve(GateReport.AUDIT_FILE));
            helper.assertTrue(audit.contains(TAG_ALL_LOCKED) && audit.contains("Dropped per Age"), "audit content");
            helper.assertTrue(dispatcher.execute("firmages recipes why " + IRON, source) == 0, "why: iron recipe dropped");
            helper.assertTrue(dispatcher.execute("firmages recipes why " + STONE, source) == 1, "why: stone recipe kept");
            helper.assertTrue(dispatcher.execute("firmages recipes locked disabled", source) >= 1, "locked disabled lists the dragon egg recipe");
            helper.assertTrue(dispatcher.execute("firmages selftest gate", source) == 0, "/firmages selftest gate has failures");
        } catch (Exception e) {
            helper.fail("command failed: " + e);
        }
        helper.succeed();
    }

    /** Recipe ids whose detected outputs contain {@code marker} (a typed extractor's "item ..." entry). */
    static List<String> entriesWith(GateReport rep, String type, String marker) {
        return rep.entries().entrySet().stream()
            .filter(e -> e.getValue().type().equals(type) && e.getValue().outputs().contains(marker))
            .map(java.util.Map.Entry::getKey).sorted().toList();
    }

    static final String IE_IRON_DUST = "item #c:dusts/iron";
    static final String MEK_IRON_DUST = "item mekanism:dust_iron";

    /**
     * The typed extractors on real mod recipes: IE tag outputs through the TagOutput accessor (crusher recipes that
     * make #c:dusts/iron, all members age_2), Mekanism output definitions (mekanism:dust_iron, age_2); IE mineral
     * mixes are exempt and the MineralMix mixin keeps locked ores out of excavator rolls.
     */
    @GameTest(template = "empty", batch = "firmages_1_selftest", timeoutTicks = 1200)
    public static void modExtractorsAndExcavatorAtBoot(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean ie = ModList.get().isLoaded("immersiveengineering");
        boolean mek = ModList.get().isLoaded("mekanism");
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(idle(server), "reload pending"))
            .thenExecute(() -> {
                GateReport rep = RecipeGate.lastReport();
                if (ie) {
                    List<String> crusher = entriesWith(rep, "immersiveengineering:crusher", IE_IRON_DUST);
                    helper.assertTrue(!crusher.isEmpty(), "IE extractor recorded #c:dusts/iron as a tag output");
                    for (String id : crusher) {
                        GateRules.Verdict v = rep.entry(id).verdict();
                        helper.assertTrue(!v.keep() && "age_2".equals(v.bucket()), id + " locked by the all-members-locked tag rule: " + v);
                        helper.assertTrue(!has(server, id), id + " absent");
                    }
                    GateReport.TypeStats mix = rep.types().get("immersiveengineering:mineral_mix");
                    helper.assertTrue(mix != null && mix.total > 0 && mix.dropped == 0, "mineral mixes exempt and all kept");
                    helper.assertTrue(ModTestSupport.anyMixHasLockedOre(server), "some mix lists an iron ore (age_2)");
                    int[] rolls = ModTestSupport.rollAllMixes(server, 500);
                    helper.assertTrue(rolls[1] > 0, "rolled mixes with locked ores");
                    helper.assertTrue(rolls[0] == 0, "MineralMix mixin: " + rolls[0] + " locked ores rolled");
                }
                if (mek) {
                    List<String> enrich = rep.entries().entrySet().stream().filter(e -> e.getValue().outputs().contains(MEK_IRON_DUST))
                        .map(java.util.Map.Entry::getKey).toList();
                    helper.assertTrue(!enrich.isEmpty(), "Mekanism extractor recorded mekanism:dust_iron");
                    for (String id : enrich) {
                        helper.assertTrue(!rep.entry(id).verdict().keep(), id + " dropped while age_2 is locked");
                    }
                    helper.assertTrue(Blocks.IRON_ORE.defaultBlockState().is(MINER_BLACKLIST), "real mekanism:miner_blacklist holds iron ore");
                }
                helper.assertTrue(rep.errorCount() == 0, "extraction errors with IE/Mekanism loaded: " + rep.errorCount());
            })
            .thenSucceed();
    }

    // ---------------------------------------------------------------- m1 / m3 KubeJS hand-off tags ([dawn])

    /** The pack scripts: locked ores leave both prospecting tags and join the Digital Miner blacklist. */
    @GameTest(template = "empty", batch = "firmages_1_selftest", timeoutTicks = 1200)
    public static void kubejsOreTagsAtBoot(GameTestHelper helper) {
        if (!ModList.get().isLoaded("kubejs")) {
            helper.succeed();
            return;
        }
        MinecraftServer server = helper.getLevel().getServer();
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(idle(server), "reload pending"))
            .thenExecute(() -> {
                helper.assertTrue(Blocks.COAL_ORE.defaultBlockState().is(TFC_PROSPECTABLE), "coal ore (untagged) stays prospectable");
                helper.assertTrue(Blocks.EMERALD_ORE.defaultBlockState().is(TFC_PROSPECTABLE), "flattened tag keeps the rest of #c:ores");
                helper.assertTrue(!Blocks.IRON_ORE.defaultBlockState().is(TFC_PROSPECTABLE), "iron ore (age_2) left tfc:prospectable");
                helper.assertTrue(!Blocks.DEEPSLATE_IRON_ORE.defaultBlockState().is(TFC_PROSPECTABLE), "deepslate iron ore left tfc:prospectable");
                helper.assertTrue(!Blocks.GOLD_ORE.defaultBlockState().is(TFC_PROSPECTABLE), "gold ore (age_5) left tfc:prospectable");
                helper.assertTrue(Blocks.COAL_ORE.defaultBlockState().is(PP_PROSPECTABLE), "coal ore stays in the PP tag");
                helper.assertTrue(!Blocks.GOLD_ORE.defaultBlockState().is(PP_PROSPECTABLE), "gold ore left the PP tag");
                helper.assertTrue(Blocks.IRON_ORE.defaultBlockState().is(MINER_BLACKLIST), "iron ore on the miner blacklist");
                helper.assertTrue(Blocks.GOLD_ORE.defaultBlockState().is(MINER_BLACKLIST), "gold ore on the miner blacklist");
                helper.assertTrue(!Blocks.COAL_ORE.defaultBlockState().is(MINER_BLACKLIST), "coal ore not blacklisted");
                AgeService.Status st = AgeService.status(server);
                helper.assertTrue(!st.bootAnswersStale(), "the in-loader KubeJS run answered right on the initial load (no stale boot answers)");
            })
            .thenSucceed();
    }

    // ---------------------------------------------------------------- m3 ([dawn])

    /** OreGuard: fake-player breaks of locked ores are canceled; real players and unlocked blocks are not touched. */
    @GameTest(template = "empty", batch = "firmages_1_selftest")
    public static void oreGuardBreak(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        BlockEvent.BreakEvent locked = new BlockEvent.BreakEvent(level, pos, Blocks.IRON_ORE.defaultBlockState(), fake);
        OreGuard.onBreak(locked);
        helper.assertTrue(locked.isCanceled(), "fake player on iron ore (age_2) canceled");
        BlockEvent.BreakEvent coal = new BlockEvent.BreakEvent(level, pos, Blocks.COAL_ORE.defaultBlockState(), fake);
        OreGuard.onBreak(coal);
        helper.assertTrue(!coal.isCanceled(), "fake player on coal ore allowed");
        BlockEvent.BreakEvent player = new BlockEvent.BreakEvent(level, pos, Blocks.IRON_ORE.defaultBlockState(), helper.makeMockPlayer(GameType.SURVIVAL));
        OreGuard.onBreak(player);
        helper.assertTrue(!player.isCanceled(), "real players are left to ProgressiveStages");
        BlockEvent.BreakEvent posted = NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, Blocks.GOLD_ORE.defaultBlockState(), fake));
        helper.assertTrue(posted.isCanceled(), "OreGuard is registered on the event bus");
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.IRON_ORE);
        helper.assertTrue(!fake.gameMode.destroyBlock(pos), "fake player cannot break the locked ore");
        helper.assertBlockPresent(Blocks.IRON_ORE, new BlockPos(1, 1, 1));
        helper.succeed();
    }

    /** OreGuard: drops of locked ores without a breaker (Create's destroyBlockAs path) become the disguise drop. */
    @GameTest(template = "empty", batch = "firmages_1_selftest")
    public static void oreGuardDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockPos pos = helper.absolutePos(rel);
        List<ItemEntity> drops = new ArrayList<>(List.of(new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.RAW_IRON))));
        BlockDropsEvent e = new BlockDropsEvent(level, pos, Blocks.IRON_ORE.defaultBlockState(), null, drops, null, ItemStack.EMPTY);
        OreGuard.onDrops(e);
        ItemStack disguise = OreGuard.disguiseDrop(Blocks.IRON_ORE);
        helper.assertTrue(e.getDrops().stream().noneMatch(d -> d.getItem().is(Items.RAW_IRON)), "raw iron replaced");
        helper.assertTrue(e.getDrops().size() == (disguise.isEmpty() ? 0 : 1), "only the disguise drop (" + disguise + ")");
        // Through the real drop path: Block.dropResources with no breaker posts BlockDropsEvent.
        AABB box = new AABB(pos).inflate(1.5);
        Block.dropResources(Blocks.IRON_ORE.defaultBlockState(), level, pos, null, null, ItemStack.EMPTY);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, box, d -> d.getItem().is(Items.RAW_IRON)).isEmpty(), "no raw iron dropped");
        Block.dropResources(Blocks.COAL_ORE.defaultBlockState(), level, pos, null, null, ItemStack.EMPTY);
        helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, box, d -> d.getItem().is(Items.COAL)).isEmpty(), "coal ore drops coal");
        level.getEntitiesOfClass(ItemEntity.class, box).forEach(ItemEntity::discard);
        helper.succeed();
    }

    /** IE excavator filter on a stub mix (IE is not in the dev run): 10,000 rolls, a locked ore never passes. */
    @GameTest(template = "empty", batch = "firmages_1_selftest")
    public static void excavatorFilterStubMix(GameTestHelper helper) {
        int[] counts = rollStubMix(10_000);
        helper.assertTrue(counts[0] == 0, "iron ingot (age_2) rolled " + counts[0] + " times while locked");
        helper.assertTrue(counts[1] > 0 && counts[2] > 0, "cobblestone and spoils still roll: " + counts[1] + "/" + counts[2]);
        helper.assertTrue(ExcavatorFilter.locked(new ItemStack(Items.IRON_ORE)), "ore block item locked through its block (age_blocks)");
        helper.assertTrue(!ExcavatorFilter.locked(new ItemStack(Items.COAL_ORE)), "untagged ore block item passes");
        helper.succeed();
    }

    /** Stub mix: iron ingot or cobblestone, spoil gravel. Returns counts {iron, cobblestone, gravel}. */
    static int[] rollStubMix(int rolls) {
        Random rand = new Random(42);
        int[] counts = new int[3];
        for (int i = 0; i < rolls; i++) {
            ItemStack rolled = new ItemStack(rand.nextBoolean() ? Items.IRON_INGOT : Items.COBBLESTONE);
            ItemStack out = ExcavatorFilter.filter(rolled, () -> new ItemStack(Items.GRAVEL));
            if (out.is(Items.IRON_INGOT)) counts[0]++;
            else if (out.is(Items.COBBLESTONE)) counts[1]++;
            else if (out.is(Items.GRAVEL)) counts[2]++;
        }
        return counts;
    }

    // ---------------------------------------------------------------- unlock reloads (two Ages)

    /**
     * age_0 then age_2 unlocked through the reload path: recipes, KubeJS ore tags, excavator and OreGuard follow;
     * disabled and the age_4 byproduct stay locked. Revoking both restores [dawn].
     */
    @GameTest(template = "empty", batch = "firmages_3_gate", timeoutTicks = 2400)
    public static void unlockTwoAgesThroughReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean kubejs = ModList.get().isLoaded("kubejs");
        int[] gen = new int[1];
        helper.startSequence()
            .thenWaitUntil(() -> helper.assertTrue(idle(server), "reload pending"))
            .thenExecute(() -> {
                expectRecipes(helper, server, "dawn", List.of(STONE), List.of(IRON, COPPER, TAG_ALL_LOCKED));
                gen[0] = CacheGeneration.get();
                helper.assertTrue(AgeService.simulate(server, AgeId.AGE_0, true), "grant age_0");
            })
            .thenWaitUntil(() -> helper.assertTrue(CacheGeneration.get() > gen[0] && idle(server), "age_0 reload not finished"))
            .thenExecute(() -> {
                expectRecipes(helper, server, "age_0", List.of(STONE, COPPER), List.of(IRON, TAG_ALL_LOCKED, DISABLED, FLUID));
                helper.assertTrue(RecipeGate.lastReport().unlocked().equals(List.of("dawn", "age_0")), "report Ages");
                gen[0] = CacheGeneration.get();
                helper.assertTrue(AgeService.simulate(server, AgeId.AGE_2, true), "grant age_2");
            })
            .thenWaitUntil(() -> helper.assertTrue(CacheGeneration.get() > gen[0] && idle(server), "age_2 reload not finished"))
            .thenExecute(() -> {
                expectRecipes(helper, server, "age_2", List.of(STONE, COPPER, IRON, TAG_ALL_LOCKED, TAG_MIXED, "minecraft:iron_pickaxe",
                        LateRecipeInjector.LATE_IRON, LateRecipeInjector.LATE_STONE),
                    List.of(DISABLED, FLUID));
                helper.assertTrue(rollStubMix(2_000)[0] > 0, "excavator yields iron once age_2 is unlocked");
                BlockEvent.BreakEvent e = new BlockEvent.BreakEvent(helper.getLevel(), helper.absolutePos(BlockPos.ZERO),
                    Blocks.IRON_ORE.defaultBlockState(), FakePlayerFactory.getMinecraft(helper.getLevel()));
                OreGuard.onBreak(e);
                helper.assertTrue(!e.isCanceled(), "OreGuard lets miners break iron ore after the unlock");
                GateReport rep = RecipeGate.lastReport();
                if (ModList.get().isLoaded("immersiveengineering")) {
                    List<String> crusher = entriesWith(rep, "immersiveengineering:crusher", IE_IRON_DUST);
                    helper.assertTrue(!crusher.isEmpty() && crusher.stream().allMatch(id -> has(server, id)), "IE iron dust crusher recipes back");
                    helper.assertTrue(ModTestSupport.ironOreRolls(server, 500) > 0, "excavator yields iron ore once age_2 is unlocked");
                }
                if (ModList.get().isLoaded("mekanism")) {
                    helper.assertTrue(rep.entries().values().stream().filter(x -> x.outputs().contains(MEK_IRON_DUST)).allMatch(x -> x.verdict().keep()),
                        "Mekanism iron dust recipes kept");
                }
                if (kubejs) {
                    helper.assertTrue(Blocks.IRON_ORE.defaultBlockState().is(TFC_PROSPECTABLE), "iron ore prospectable again");
                    helper.assertTrue(!Blocks.IRON_ORE.defaultBlockState().is(MINER_BLACKLIST), "iron ore off the miner blacklist");
                    helper.assertTrue(!Blocks.GOLD_ORE.defaultBlockState().is(TFC_PROSPECTABLE), "gold ore (age_5) still hidden");
                    helper.assertTrue(Blocks.GOLD_ORE.defaultBlockState().is(MINER_BLACKLIST), "gold ore still blacklisted");
                }
                gen[0] = CacheGeneration.get();
                helper.assertTrue(AgeService.simulate(server, AgeId.AGE_2, false), "revoke age_2");
                helper.assertTrue(AgeService.simulate(server, AgeId.AGE_0, false), "revoke age_0");
            })
            .thenWaitUntil(() -> helper.assertTrue(CacheGeneration.get() > gen[0] && idle(server), "revoke reload not finished"))
            .thenExecute(() -> {
                expectRecipes(helper, server, "dawn again", List.of(STONE, TAG_MIXED, LateRecipeInjector.LATE_STONE),
                    List.of(IRON, COPPER, TAG_ALL_LOCKED, LateRecipeInjector.LATE_IRON));
                if (kubejs) helper.assertTrue(!Blocks.IRON_ORE.defaultBlockState().is(TFC_PROSPECTABLE), "iron ore hidden again");
            })
            .thenSucceed();
    }
}
