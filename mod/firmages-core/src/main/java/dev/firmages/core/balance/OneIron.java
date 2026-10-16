package dev.firmages.core.balance;

import com.mojang.serialization.MapCodec;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One Iron outside the recipes (SPEC §34.4, harmony plan fix 10 and §5 "Loot-Umleitung"): the vanilla iron ingot is
 * hidden, wrought iron is the iron. Loot goes through the global loot modifier {@link ReplaceItemModifier}; trades
 * are no loot, so every merchant's offers (villagers, the wandering trader, every mod trader that is a
 * {@link Merchant}) are rewritten when a player right-clicks it, before its trade screen opens: an offer that gives or
 * asks for an item of {@code balance.merchantRedirects} gives or asks for the target instead, with the same count,
 * uses, price and experience.
 */
public final class OneIron {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, FirmagesCore.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<ReplaceItemModifier>> REPLACE_ITEM =
        LOOT_MODIFIERS.register("replace_item", () -> ReplaceItemModifier.CODEC);

    private static final Set<String> WARNED = new HashSet<>();

    private OneIron() {}

    public static void register(IEventBus modBus, IEventBus bus) {
        LOOT_MODIFIERS.register(modBus);
        bus.addListener(EventPriority.HIGH, OneIron::onInteract);
    }

    /** Valid entry of {@code balance.merchantRedirects}: {@code "<item>=<item>"} with two resource locations. */
    public static boolean validEntry(Object o) {
        if (!(o instanceof String s)) return false;
        String[] p = s.split("=", -1);
        return p.length == 2 && ResourceLocation.tryParse(p[0].trim()) != null && ResourceLocation.tryParse(p[1].trim()) != null;
    }

    /** The redirects of the config, known items only (an unknown one is logged once). */
    public static Map<Item, Item> redirects() {
        Map<Item, Item> out = new LinkedHashMap<>();
        for (String e : ServerConfig.merchantRedirects()) {
            String[] p = e.split("=", -1);
            if (p.length != 2) continue;
            Optional<Item> from = item(p[0].trim(), e);
            Optional<Item> to = item(p[1].trim(), e);
            if (from.isPresent() && to.isPresent()) out.put(from.get(), to.get());
        }
        return out;
    }

    private static Optional<Item> item(String id, String entry) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Optional<Item> it = rl == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(rl);
        if (it.isEmpty()) {
            synchronized (WARNED) {
                if (WARNED.add(entry)) FirmagesCore.LOGGER.warn("balance.merchantRedirects: unknown item {} in \"{}\", entry ignored", id, entry);
            }
        }
        return it;
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        Entity target = event.getTarget();
        if (target instanceof Merchant m) redirect(m.getOffers(), redirects());
    }

    /** Rewrites {@code offers} in place; returns how many offers changed. */
    public static int redirect(MerchantOffers offers, Map<Item, Item> map) {
        if (map.isEmpty() || offers == null) return 0;
        int changed = 0;
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer o = offers.get(i);
            ItemCost a = cost(o.getItemCostA(), map);
            Optional<ItemCost> b = o.getItemCostB().map(c -> cost(c, map));
            ItemStack result = o.getResult();
            Item to = map.get(result.getItem());
            ItemStack res = to == null ? result : new ItemStack(to, result.getCount());
            boolean bSame = o.getItemCostB().isEmpty() || b.get() == o.getItemCostB().get();
            if (a == o.getItemCostA() && bSame && res == result) continue;
            MerchantOffer n = new MerchantOffer(a, b, res, o.getUses(), o.getMaxUses(), o.getXp(), o.getPriceMultiplier(), o.getDemand());
            n.setSpecialPriceDiff(o.getSpecialPriceDiff());
            offers.set(i, n);
            changed++;
        }
        return changed;
    }

    private static ItemCost cost(ItemCost c, Map<Item, Item> map) {
        Item to = map.get(c.item().value());
        return to == null ? c : new ItemCost(to, c.count());
    }

    /** Offer lines for {@code /firmages balance merchant}: "4 tfc:metal/ingot/wrought_iron -> 1 minecraft:emerald". */
    public static List<String> describe(MerchantOffers offers) {
        return offers.stream().map(o -> o.getItemCostA().count() + " " + id(o.getItemCostA().item().value())
            + o.getItemCostB().map(c -> " + " + c.count() + " " + id(c.item().value())).orElse("")
            + " -> " + o.getResult().getCount() + " " + id(o.getResult().getItem())).toList();
    }

    private static String id(Item i) {
        return BuiltInRegistries.ITEM.getKey(i).toString();
    }
}
