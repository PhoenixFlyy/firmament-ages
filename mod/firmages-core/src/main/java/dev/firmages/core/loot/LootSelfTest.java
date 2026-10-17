package dev.firmages.core.loot;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeIndex;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.command.SelfTest;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.firmages.core.command.SelfTest.check;
import static dev.firmages.core.command.SelfTest.checkEquals;

/** The loot Age gate's cases of {@code /firmages selftest server} (SPEC §19.5): against the running server's modifiers. */
public final class LootSelfTest {
    private LootSelfTest() {}

    public static void run(SelfTest.Recorder r, MinecraftServer server) {
        r.test("loot_filter_runs_last", () -> {
            check(ServerConfig.lootFilter(), "gate.lootFilter is off");
            List<IGlobalLootModifier> mods = AgeLoot.appliedOrder();
            check(!mods.isEmpty(), "no loot modifier manager seen (mixin LootModifierManagerMixin not applied?)");
            checkEquals(1L, mods.stream().filter(m -> m instanceof AgeLootModifier).count(), "firmages:age_filter modifiers loaded");
            check(mods.get(mods.size() - 1) instanceof AgeLootModifier,
                "the age filter is not the last global loot modifier: " + mods.get(mods.size() - 1).getClass().getName());
            return mods.size() + " global loot modifiers, firmages:age_filter last";
        });
        r.test("loot_filter_rolls_server_ages", () -> {
            // A chest table built here, unpacked without a player (a hopper's roll): the server's Ages decide.
            Set<AgeId> unlocked = AgeService.state(server).snapshot().unlocked();
            AgeIndex idx = AgeIndex.current();
            Optional<Item> locked = idx.items().entrySet().stream().filter(e -> !unlocked.contains(e.getValue())).map(Map.Entry::getKey)
                .min(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
            if (locked.isEmpty()) locked = idx.disabledItems().stream().min(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
            check(locked.isPresent(), "no item of a locked Age and no disabled item in the index");
            Item free = BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR && LootAgeRule.keep(idx.ageOf(i), idx.isDisabled(i), unlocked))
                .findFirst().orElseThrow();
            LootTable t = LootTable.lootTable().withPool(LootPool.lootPool().add(LootItem.lootTableItem(locked.get())))
                .withPool(LootPool.lootPool().add(LootItem.lootTableItem(free))).build();
            t.setLootTableId(ResourceLocation.fromNamespaceAndPath("firmages", "selftest/age_filter"));
            List<ItemStack> loot = t.getRandomItems(new LootParams.Builder(server.overworld())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(server.overworld().getSharedSpawnPos())).create(LootContextParamSets.CHEST));
            Item gone = locked.get();
            check(loot.stream().noneMatch(s -> s.is(gone)), BuiltInRegistries.ITEM.getKey(gone) + " survived the roll: " + loot);
            check(loot.stream().anyMatch(s -> s.is(free)), BuiltInRegistries.ITEM.getKey(free) + " was removed: " + loot);
            long[] n = AgeLoot.counts();
            return BuiltInRegistries.ITEM.getKey(gone) + " (" + Optional.ofNullable(idx.ageOf(gone)).map(AgeId::id).orElse("disabled")
                + ") removed, " + BuiltInRegistries.ITEM.getKey(free) + " kept; " + n[0] + " rolls, " + n[1] + " stacks removed since start";
        });
    }
}
