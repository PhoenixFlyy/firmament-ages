package dev.firmages.core.command;

import com.google.gson.JsonObject;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.balance.OneIron;
import dev.firmages.core.balance.OreAudit;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.machine.MachineRegistry;
import dev.firmages.core.machine.OreProcessingRecipe;
import dev.firmages.core.machine.ProcessorRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.Optional;

import static dev.firmages.core.command.SelfTest.check;

/**
 * Suite {@code endgame} (SPEC §16.2a, §20.1, §34.4, §40, §41): the pack data of the end game round on the running
 * server. The Ore Processor's recipes cover every ore piece of the metal table at its 6x factor (from the Singularity
 * Age on; before it they are locked and the case says so); the catalysable tag names machines and no generator; loot
 * and merchants lose the vanilla iron ingot; Stargate Journey's dial costs are reactor-scale; the regrowth guard is on.
 */
public final class EndgameSuite implements SelfTest.Suite {
    /** The harmony plan's reactor scale for a dial (table 2.1, the Gathering sink): 1 G FE. */
    public static final long REACTOR_DIAL = 1_000_000_000L;
    private final MinecraftServer server;

    public EndgameSuite(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public String name() {
        return "endgame";
    }

    @Override
    public void run(SelfTest.Recorder r) {
        ServerLevel level = server.overworld();
        r.test("processor_recipes_cover_the_table", () -> {
            boolean open = AgeService.state(server).snapshot().isUnlocked(AgeId.byId("age_9").orElseThrow());
            var all = ProcessorRecipes.all(level);
            if (!open) {
                check(all.isEmpty(), all.size() + " ore_processing recipes live before the Singularity Age");
                return "locked until age_9 (0 live)";
            }
            Optional<JsonObject> table = OreAudit.table(server);
            check(table.isPresent(), "no metal table");
            JsonObject t = table.get();
            var grades = t.getAsJsonObject("grade_mb");
            double factor = t.getAsJsonObject("stations").getAsJsonObject("ore_processor").get("factor").getAsDouble();
            double tol = t.get("tolerance").getAsDouble();
            int pieces = 0;
            int bad = 0;
            StringBuilder first = new StringBuilder();
            for (var m : t.getAsJsonObject("metals").entrySet()) {
                JsonObject d = m.getValue().getAsJsonObject();
                boolean listed = false;
                for (var s : d.getAsJsonArray("stations")) listed |= s.getAsString().equals("ore_processor");
                if (!listed) continue;
                for (var pre : d.getAsJsonArray("ores")) {
                    for (var g : grades.entrySet()) {
                        String piece = pre.getAsString().replace("{g}", g.getKey());
                        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(piece));
                        pieces++;
                        Optional<RecipeHolder<OreProcessingRecipe>> h = ProcessorRecipes.forInput(level, new ItemStack(it));
                        double got = h.map(x -> x.value().result().getCount() * 100.0 / (x.value().input().count() * g.getValue().getAsDouble())).orElse(0.0);
                        if (h.isEmpty() || Math.abs(got - factor) > tol * factor
                            || !BuiltInRegistries.ITEM.getKey(h.get().value().result().getItem()).toString().equals(d.get("dust").getAsString())) {
                            bad++;
                            if (first.isEmpty()) first.append(piece).append(" -> ").append(h.map(x -> x.id().toString()).orElse("none")).append(" at ").append(got);
                        }
                    }
                }
            }
            check(bad == 0, bad + " of " + pieces + " ore pieces without a 6x recipe to their dust, first: " + first);
            return pieces + " ore pieces at " + factor + "x, " + all.size() + " recipes";
        });
        r.test("catalysable_machines", () -> {
            var tag = BuiltInRegistries.BLOCK.getTag(MachineRegistry.CATALYSABLE);
            check(tag.isPresent() && tag.get().size() > 0, "firmages:catalysable is empty");
            int n = tag.get().size();
            StringBuilder gen = new StringBuilder();
            tag.get().forEach(b -> {
                String id = b.getKey().location().toString();
                if (id.contains("generator") || id.contains("miner") || id.contains("excavator")) gen.append(id).append(' ');
            });
            check(gen.isEmpty(), "generators or miners in firmages:catalysable: " + gen);
            return n + " machines";
        });
        r.test("loot_gives_no_vanilla_iron", () -> {
            Map<Item, Item> map = OneIron.redirects();
            check(map.containsKey(Items.IRON_INGOT), "balance.merchantRedirects has no entry for minecraft:iron_ingot");
            Item to = map.get(Items.IRON_INGOT);
            LootTable lt = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.parse("minecraft:chests/village/village_weaponsmith")));
            int iron = 0;
            int wrought = 0;
            for (int i = 0; i < 200; i++) {
                for (ItemStack s : lt.getRandomItems(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                    .create(LootContextParamSets.CHEST))) {
                    if (s.is(Items.IRON_INGOT)) iron += s.getCount();
                    if (s.is(to)) wrought += s.getCount();
                }
            }
            check(iron == 0 && wrought > 0, "200 village weaponsmith chests gave " + iron + " vanilla iron ingots and " + wrought + " "
                + BuiltInRegistries.ITEM.getKey(to));
            return "200 chests: 0 vanilla iron, " + wrought + " " + BuiltInRegistries.ITEM.getKey(to);
        });
        r.test("stargate_dials_cost_reactor_power", () -> {
            if (!ModList.get().isLoaded("sgjourney")) return "Stargate Journey not loaded";
            long[] c = dev.firmages.core.compat.sgjourney.SgjGates.costs();
            check(c[1] >= REACTOR_DIAL, "interstellar dial " + c[1] + " FE, below the reactor scale " + REACTOR_DIAL);
            return "system-wide " + c[0] + " FE, interstellar " + c[1] + " FE, wormhole " + c[2] + " FE/t";
        });
        r.test("regrowth_guard_on", () -> {
            check(ServerConfig.regrowthLoadedChunksOnly(), "perf.regrowthLoadedChunksOnly is off");
            long[] n = dev.firmages.core.perf.RegrowthGuard.counts();
            return "on; " + n[0] + " looked, " + n[1] + " skipped since start";
        });
    }
}
