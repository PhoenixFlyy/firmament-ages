package dev.firmages.core.gametest;

import blusunrize.immersiveengineering.api.crafting.StackWithChance;
import blusunrize.immersiveengineering.api.excavator.MineralMix;
import dev.firmages.core.miner.ExcavatorFilter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Random;

/** IE helpers for the GameTests; only loaded when Immersive Engineering is in the run. */
final class ModTestSupport {
    private ModTestSupport() {}

    static List<MineralMix> mineralMixes(MinecraftServer server) {
        return server.getRecipeManager().getRecipes().stream().map(RecipeHolder::value)
            .filter(r -> r instanceof MineralMix).map(r -> (MineralMix) r).toList();
    }

    /** True if some mix lists an ore that is locked now (resolved with the bound tags). */
    static boolean anyMixHasLockedOre(MinecraftServer server) {
        for (MineralMix mix : mineralMixes(server)) {
            for (StackWithChance s : mix.outputs) {
                if (ExcavatorFilter.locked(s.stack().get())) return true;
            }
        }
        return false;
    }

    /** Rolls every mix through the mixin-patched getRandomOre; returns {locked results, results of mixes with locked ores}. */
    static int[] rollAllMixes(MinecraftServer server, int rollsPerMix) {
        Random rand = new Random(7);
        int[] out = new int[2];
        for (MineralMix mix : mineralMixes(server)) {
            boolean hasLocked = mix.outputs.stream().anyMatch(s -> ExcavatorFilter.locked(s.stack().get()));
            for (int i = 0; i < rollsPerMix; i++) {
                ItemStack rolled = mix.getRandomOre(rand);
                if (ExcavatorFilter.locked(rolled)) out[0]++;
                if (hasLocked) out[1]++;
            }
        }
        return out;
    }

    /** How many rolls over all mixes give an iron ore block item (iron_ore, deepslate_iron_ore: age_2 in the test tags). */
    static int ironOreRolls(MinecraftServer server, int rollsPerMix) {
        Random rand = new Random(11);
        int n = 0;
        for (MineralMix mix : mineralMixes(server)) {
            for (int i = 0; i < rollsPerMix; i++) {
                ItemStack rolled = mix.getRandomOre(rand);
                if (rolled.getItem() instanceof BlockItem b && (b.getBlock() == Blocks.IRON_ORE || b.getBlock() == Blocks.DEEPSLATE_IRON_ORE)) n++;
            }
        }
        return n;
    }
}
