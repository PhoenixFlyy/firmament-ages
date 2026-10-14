package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeIndex;
import dev.firmages.core.age.AgeOreTags;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.CacheGeneration;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.gate.RecipeGate;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The Age swap (SPEC §3.3, §12): an Age change re-filters the recipes of the last load instead of reloading the
 * datapacks. These tests prove the swap gives exactly what a full reload gives, measure both, and check that
 * {@code /reload} and {@code gate.ageSwap = false} still run the full reload with the gate. Each test changes Ages, so
 * each has its own batch, and each restores {@code [dawn]}.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class AgeSwapGameTests {
    private static final String IRON = "firmages:test/iron_from_gravel";
    private static final String COPPER = "firmages:test/copper_from_sand";
    private static final TagKey<Block> PROBE_LOCKED = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("firmages", "test/locked_ores"));

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(AgeSwapGameTests.class);
    }

    private static boolean idle(MinecraftServer server) {
        return AgeService.status(server).phase() == ReloadScheduler.Phase.IDLE && !AgeService.reloadPending();
    }

    private static Set<String> recipeIds(MinecraftServer server) {
        return server.getRecipeManager().getRecipeIds().map(ResourceLocation::toString).collect(Collectors.toCollection(TreeSet::new));
    }

    /** Members of the three Age ore tags, as "tag block" lines. */
    private static Set<String> oreTags(MinecraftServer server) {
        Set<String> out = new TreeSet<>();
        var reg = server.registryAccess().registryOrThrow(Registries.BLOCK);
        for (TagKey<Block> key : List.of(AgeOreTags.TFC_PROSPECTABLE, AgeOreTags.PP_PROSPECTABLE, AgeOreTags.MINER_BLACKLIST)) {
            reg.getTag(key).ifPresent(set -> set.forEach(h -> out.add(key.location() + " " + reg.getKey(h.value()))));
        }
        return out;
    }

    private static List<String> diff(Set<String> a, Set<String> b) {
        List<String> out = new ArrayList<>();
        for (String s : a) if (!b.contains(s)) out.add("-" + s);
        for (String s : b) if (!a.contains(s)) out.add("+" + s);
        return out.subList(0, Math.min(10, out.size()));
    }

    /**
     * Two Ages granted in one tick: one coalesced swap. Then a full datapack reload with the same Ages: recipes and ore
     * tags must be identical. Both durations go to the log ("GameTest Age swap vs full reload"). Revoking returns to
     * exactly the boot recipe set of Dawn.
     */
    @GameTest(template = "empty", batch = "firmages_3_swap", timeoutTicks = 4000)
    public static void swapMatchesFullReload(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        List<Set<String>> sets = new ArrayList<>(List.of(Set.of(), Set.of(), Set.of(), Set.of(), Set.of()));
        long[] ms = new long[2];
        int[] gen = new int[1];
        int[] loads = new int[1];
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(idle(server), "reload pending"))
            .thenExecute(() -> {
                sets.set(0, recipeIds(server));
                gen[0] = CacheGeneration.get();
                loads[0] = AgeIndex.loadsBegun();
                h.assertTrue(AgeService.simulate(server, AgeId.AGE_0, true) && AgeService.simulate(server, AgeId.AGE_2, true), "grant age_0 and age_2");
                h.assertTrue(AgeService.reloadPending(), "a swap is pending after the grants (sent to clients as reloadPending)");
            })
            .thenWaitUntil(() -> h.assertTrue(CacheGeneration.get() > gen[0] && idle(server), "swap not finished"))
            .thenExecute(() -> {
                h.assertTrue(CacheGeneration.get() == gen[0] + 1, "one coalesced swap for two grants");
                h.assertTrue("swap".equals(AgeService.lastMode()), "ran as an Age swap: " + AgeService.lastMode());
                h.assertTrue(AgeIndex.loadsBegun() == loads[0], "the swap began no datapack load");
                ms[0] = AgeService.status(server).lastReloadMillis();
                sets.set(1, recipeIds(server));
                sets.set(2, oreTags(server));
                h.assertTrue(sets.get(1).contains(IRON) && sets.get(1).contains(COPPER), "iron and copper recipes live after the swap");
                gen[0] = CacheGeneration.get();
                AgeService.forceReload("gametest: full reload to compare with the swap");
            })
            .thenWaitUntil(() -> h.assertTrue(AgeIndex.loadsBegun() > loads[0] && CacheGeneration.get() > gen[0] && idle(server), "full reload not finished"))
            .thenExecute(() -> {
                h.assertTrue("reload".equals(AgeService.lastMode()), "/firmages reload is a full reload: " + AgeService.lastMode());
                ms[1] = AgeService.status(server).lastReloadMillis();
                sets.set(3, recipeIds(server));
                Set<String> tagsAfterReload = oreTags(server);
                h.assertTrue(sets.get(1).equals(sets.get(3)), "swap and full reload give the same recipes, diff " + diff(sets.get(1), sets.get(3)));
                h.assertTrue(sets.get(2).equals(tagsAfterReload), "swap and full reload give the same ore tags, diff " + diff(sets.get(2), tagsAfterReload));
                FirmagesCore.LOGGER.info("GameTest Age swap vs full reload: swap {} ms, full datapack reload {} ms, {} live recipes identical, "
                    + "{} ore tag entries identical", ms[0], ms[1], sets.get(1).size(), sets.get(2).size());
                gen[0] = CacheGeneration.get();
                AgeService.simulate(server, AgeId.AGE_2, false);
                AgeService.simulate(server, AgeId.AGE_0, false);
            })
            .thenWaitUntil(() -> h.assertTrue(CacheGeneration.get() > gen[0] && idle(server), "revoke swap not finished"))
            .thenExecute(() -> {
                h.assertTrue("swap".equals(AgeService.lastMode()), "the revoke ran as a swap: " + AgeService.lastMode());
                Set<String> dawn = recipeIds(server);
                h.assertTrue(dawn.equals(sets.get(0)), "back at the boot recipe set of Dawn, diff " + diff(sets.get(0), dawn));
            })
            .thenSucceed();
    }

    /**
     * The vanilla {@code /reload} still loads everything with the gate: recipes filtered with the live Ages, a new master
     * the next swap works on, the ore tags and the KubeJS probe of the new load.
     */
    @GameTest(template = "empty", batch = "firmages_4_reload_command", timeoutTicks = 4000)
    public static void vanillaReloadKeepsGateAndSwap(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        int[] gen = new int[1];
        int[] loads = new int[1];
        RecipeManager[] before = new RecipeManager[1];
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(idle(server), "reload pending"))
            .thenExecute(() -> {
                gen[0] = CacheGeneration.get();
                AgeService.simulate(server, AgeId.AGE_0, true);
            })
            .thenWaitUntil(() -> h.assertTrue(CacheGeneration.get() > gen[0] && idle(server), "age_0 swap not finished"))
            .thenExecute(() -> {
                before[0] = server.getRecipeManager();
                loads[0] = AgeIndex.loadsBegun();
                try {
                    server.getCommands().getDispatcher().execute("reload", server.createCommandSourceStack());
                } catch (Exception e) {
                    h.fail("/reload failed: " + e);
                }
            })
            .thenWaitUntil(() -> h.assertTrue(server.getRecipeManager() != before[0] && RecipeGate.master(server.getRecipeManager()) != null,
                "/reload not finished"))
            .thenExecute(() -> {
                h.assertTrue(AgeIndex.loadsBegun() > loads[0], "/reload began a datapack load");
                h.assertTrue(RecipeGate.lastFailure() == null, "gate ran: " + RecipeGate.lastFailure());
                h.assertTrue(RecipeGate.lastReport().unlocked().equals(List.of("dawn", "age_0")), "filtered with the live Ages: " + RecipeGate.lastReport().unlocked());
                Set<String> ids = recipeIds(server);
                h.assertTrue(ids.contains(COPPER) && !ids.contains(IRON), "copper (age_0) live, iron (age_2) not");
                h.assertTrue(!Blocks.IRON_ORE.defaultBlockState().is(AgeOreTags.TFC_PROSPECTABLE)
                    && Blocks.IRON_ORE.defaultBlockState().is(AgeOreTags.MINER_BLACKLIST), "ore tags of the new load filtered");
                if (ModList.get().isLoaded("kubejs")) {
                    h.assertTrue(Blocks.IRON_ORE.defaultBlockState().is(PROBE_LOCKED), "the KubeJS probe ran in the /reload with the live Ages");
                }
                loads[0] = AgeIndex.loadsBegun();
                gen[0] = CacheGeneration.get();
                AgeService.simulate(server, AgeId.AGE_2, true);
            })
            .thenWaitUntil(() -> h.assertTrue(CacheGeneration.get() > gen[0] && idle(server), "age_2 swap not finished"))
            .thenExecute(() -> {
                h.assertTrue("swap".equals(AgeService.lastMode()) && AgeIndex.loadsBegun() == loads[0], "age_2 swapped on the /reload's master");
                h.assertTrue(recipeIds(server).contains(IRON), "iron live after the swap");
                h.assertTrue(GateGameTests.ironMachines(server)[0], "furnace finds iron");
                gen[0] = CacheGeneration.get();
                AgeService.simulate(server, AgeId.AGE_2, false);
                AgeService.simulate(server, AgeId.AGE_0, false);
            })
            .thenWaitUntil(() -> h.assertTrue(CacheGeneration.get() > gen[0] && idle(server), "revoke swap not finished"))
            .thenExecute(() -> {
                Set<String> ids = recipeIds(server);
                h.assertTrue(!ids.contains(IRON) && !ids.contains(COPPER), "dawn again");
            })
            .thenSucceed();
    }

    /** {@code gate.ageSwap = false}: an Age change runs the full datapack reload as before 0.9.0, with the same result. */
    @GameTest(template = "empty", batch = "firmages_4_no_swap", timeoutTicks = 4000)
    public static void ageSwapOffReloadsInFull(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        int[] gen = new int[1];
        int[] loads = new int[1];
        boolean was = ServerConfig.AGE_SWAP.get();
        h.startSequence()
            .thenWaitUntil(() -> h.assertTrue(idle(server), "reload pending"))
            .thenExecute(() -> {
                ServerConfig.AGE_SWAP.set(false);
                gen[0] = CacheGeneration.get();
                loads[0] = AgeIndex.loadsBegun();
                if (!AgeService.simulate(server, AgeId.AGE_2, true)) {
                    ServerConfig.AGE_SWAP.set(was); // leave the switch as found for the other batches
                    h.fail("age_2 was already unlocked (an earlier test did not restore [dawn])");
                }
            })
            .thenWaitUntil(() -> h.assertTrue(CacheGeneration.get() > gen[0] && idle(server), "age_2 reload not finished"))
            .thenExecute(() -> {
                ServerConfig.AGE_SWAP.set(was);
                h.assertTrue(AgeService.lastMode().startsWith("reload") && AgeIndex.loadsBegun() > loads[0],
                    "gate.ageSwap = false reloads the datapacks: " + AgeService.lastMode());
                h.assertTrue(recipeIds(server).contains(IRON) && GateGameTests.ironMachines(server)[0], "iron live and found after the reload");
                gen[0] = CacheGeneration.get();
                AgeService.simulate(server, AgeId.AGE_2, false);
            })
            .thenWaitUntil(() -> h.assertTrue(CacheGeneration.get() > gen[0] && idle(server), "revoke swap not finished"))
            .thenExecute(() -> h.assertTrue(!recipeIds(server).contains(IRON), "iron gone again"))
            .thenSucceed();
    }
}
