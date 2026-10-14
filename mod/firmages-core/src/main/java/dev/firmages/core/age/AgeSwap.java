package dev.firmages.core.age;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.compat.recipes.RecipeCaches;
import dev.firmages.core.gate.RecipeGate;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.util.List;
import java.util.Locale;

/**
 * The Age swap (SPEC §3.3): what an Age change needs from a datapack reload, without the reload. Nothing a datapack
 * load builds depends on the Age except the recipe list and three block tags, so on the server thread:
 * <ol>
 *   <li>the recipe gate's master list of the last load is filtered for the new Ages and replaces the live recipes
 *       ({@link RecipeGate#apply});</li>
 *   <li>the prospecting and miner tags are rebound ({@link AgeOreTags});</li>
 *   <li>the machine recipe caches are flushed ({@link RecipeCaches}: Mekanism, Create, TerraFirmaCraft, Immersive
 *       Engineering, Mystical Agriculture). No {@code TagsUpdatedEvent} is posted: its listeners take it for the end of
 *       a datapack load;</li>
 *   <li>the clients get what a reload sends them except the tags: {@code OnDatapackSyncEvent} (mods that sync
 *       recipe-derived data, posted on every join too), the recipe packet and the recipe book.</li>
 * </ol>
 * The full reload stays for {@code /reload}, {@code /firmages reload}, the boot reconcile and {@code gate.ageSwap = false};
 * a swap that cannot run (no master: the gate failed on the last load) or fails falls back to it.
 */
public final class AgeSwap {

    /** @param done false: nothing or only part was swapped, the caller reloads the datapacks */
    public record Result(boolean done, String detail, long millis) {}

    private AgeSwap() {}

    public static Result run(MinecraftServer s, AgeSnapshot snap, String reason) {
        long t0 = System.nanoTime();
        RecipeManager rm = s.getRecipeManager();
        if (RecipeGate.master(rm) == null) {
            return new Result(false, "no recipe master for the live recipes (the gate failed on the last datapack load)", 0);
        }
        try {
            RecipeGate.Applied a = RecipeGate.apply(rm, snap);
            if (a == null) return new Result(false, "no recipe master", 0);
            long t1 = System.nanoTime();
            AgeOreTags.Result tags = AgeOreTags.apply(s.registryAccess(), snap);
            long t2 = System.nanoTime();
            List<String> flushed = RecipeCaches.flush(s.registryAccess(), rm, s.getResourceManager());
            long t3 = System.nanoTime();
            PlayerList players = s.getPlayerList();
            NeoForge.EVENT_BUS.post(new OnDatapackSyncEvent(players, null));
            boolean restored = false;
            if (!RecipeGate.sameRecipes(rm, a.live())) {
                FirmagesCore.LOGGER.warn("Age swap: an OnDatapackSyncEvent listener changed the recipe list ({} recipes, expected {}); "
                    + "restoring the Age-filtered list", rm.getRecipes().size(), a.live().size());
                rm.replaceRecipes(a.live());
                RecipeCaches.flush(s.registryAccess(), rm, s.getResourceManager());
                restored = true;
            }
            ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(rm.getOrderedRecipes());
            for (ServerPlayer p : players.getPlayers()) {
                p.connection.send(packet);
                p.getRecipeBook().sendInitialRecipeBook(p);
            }
            long t4 = System.nanoTime();
            String detail = String.format(Locale.ROOT,
                "recipes %d -> %d (+%d -%d, master %d%s); filter %d ms, ore tags %d ms (%s, %d locked ore blocks), caches %d ms %s, "
                    + "datapack sync and recipe packets to %d player(s) %d ms%s",
                a.before(), a.after(), a.added(), a.removed(), a.master(), a.foreign() > 0 ? ", " + a.foreign() + " unknown to the master kept" : "",
                ms(t0, t1), ms(t1, t2), tags.changed() ? "rebound " + tags.sizes() : "unchanged", tags.locked(), ms(t2, t3), flushed,
                players.getPlayerCount(), ms(t3, t4), restored ? " (recipes restored)" : "");
            return new Result(true, detail, ms(t0, t4));
        } catch (RuntimeException | LinkageError e) {
            FirmagesCore.LOGGER.error("Age swap ({}) failed; a datapack reload follows", reason, e);
            return new Result(false, "failed: " + e, ms(t0, System.nanoTime()));
        }
    }

    private static long ms(long from, long to) {
        return (to - from) / 1_000_000L;
    }
}
