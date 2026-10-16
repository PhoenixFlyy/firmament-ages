package dev.firmages.core.hardship;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.compat.progressivestages.CraftLocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Ground crafting, the Dawn replacement for the locked grid (SPEC §19.3, G5): items laid on one block with
 * TerraFirmaCraft's place key (V) form a pile of up to four ({@code tfc:placed_item}); sneaking and using the pile
 * with an empty hand crafts it by the ordinary crafting recipes ({@code minecraft:crafting}, shaped and shapeless, as
 * a 2x2 grid). No recipe type of its own: EMI already shows these recipes, the recipe gate filters the recipe manager
 * the lookup reads, and on the server ProgressiveStages decides as in a crafting grid ({@link CraftLocks}).
 * <ul>
 * <li>The pile is tried as the player sees it first (far row on top), then in every other arrangement of its four
 * places ({@link GridLock#orders}), so where on the block a part lies never matters.</li>
 * <li>A match consumes the pile: every part is used once, a remainder (a damaged tool) stays on its place, the
 * result goes to the player (or drops at their feet), with particles of the parts and a soft sound. No match: the
 * event is left alone and TerraFirmaCraft hands back one item as usual.</li>
 * <li>The client runs the same lookup on its recipe manager and claims the click when it finds a match, so TFC's own
 * prediction does not take an item first; the block sync corrects any difference.</li>
 * <li>While the grid is locked, laying a part that completes a recipe shows an action-bar hint naming the result, at
 * most once per minute per player ({@code mixin/tfc/PlacedItemInsertMixin}).</li>
 * </ul>
 * Ground crafting stays allowed after Dawn (nothing to unlearn). Fake players (deployers) do not ground-craft.
 */
public final class GroundCraft {
    private static final boolean TFC = ModList.get() != null && ModList.get().isLoaded("tfc");

    private GroundCraft() {}

    static void register(IEventBus gameBus) {
        if (TFC) gameBus.addListener(EventPriority.HIGH, GroundCraft::onRightClick);
    }

    /**
     * A recipe that fits the pile.
     *
     * @param order  the pile slot of each grid cell (top-left, top-right, bottom-left, bottom-right)
     * @param placed the grid as a crafting input, trimmed, with its offset in the 2x2 grid
     */
    public record Match(RecipeHolder<CraftingRecipe> recipe, int[] order, CraftingInput.Positioned placed) {
        /** The recipe's declared result, for hints (the crafted stack is {@code assemble}'s). */
        public ItemStack preview(Level level) {
            return recipe.value().getResultItem(level.registryAccess());
        }
    }

    /** True if every slot of the pile is empty. */
    public static boolean isEmpty(IItemHandler pile) {
        for (int i = 0; i < Math.min(GridLock.PILE_SLOTS, pile.getSlots()); i++) if (!pile.getStackInSlot(i).isEmpty()) return false;
        return true;
    }

    /**
     * The first crafting recipe that fits the pile and that {@code allowed} lets through, trying the orders of
     * {@link GridLock#orders} for horizontal direction {@code facing} ({@code Direction.get2DDataValue}).
     */
    public static Optional<Match> find(Level level, IItemHandler pile, int facing, Predicate<RecipeHolder<CraftingRecipe>> allowed) {
        if (isEmpty(pile)) return Optional.empty();
        RecipeManager recipes = level.getRecipeManager();
        List<CraftingInput> tried = new ArrayList<>();
        for (int[] order : GridLock.orders(facing)) {
            List<ItemStack> cells = new ArrayList<>(GridLock.PILE_SLOTS);
            for (int slot : order) cells.add(slot < pile.getSlots() ? pile.getStackInSlot(slot).copy() : ItemStack.EMPTY);
            CraftingInput.Positioned placed = CraftingInput.ofPositioned(2, 2, cells);
            CraftingInput input = placed.input();
            if (tried.stream().anyMatch(t -> same(t, input))) continue; // trimming makes many orders the same grid
            tried.add(input);
            Optional<RecipeHolder<CraftingRecipe>> first = recipes.getRecipeFor(RecipeType.CRAFTING, input, level);
            if (first.isEmpty()) continue;
            if (allowed.test(first.get())) return Optional.of(new Match(first.get(), order, placed));
            for (RecipeHolder<CraftingRecipe> r : recipes.getRecipesFor(RecipeType.CRAFTING, input, level)) {
                if (allowed.test(r)) return Optional.of(new Match(r, order, placed));
            }
        }
        return Optional.empty();
    }

    private static boolean same(CraftingInput a, CraftingInput b) {
        if (a.width() != b.width() || a.height() != b.height()) return false;
        for (int i = 0; i < a.size(); i++) {
            ItemStack x = a.getItem(i), y = b.getItem(i);
            if (x.isEmpty() != y.isEmpty() || !ItemStack.isSameItemSameComponents(x, y)) return false;
        }
        return true;
    }

    /** The server's rule for a pile of {@code player}: ProgressiveStages' crafting locks on recipe, result and parts. */
    public static Predicate<RecipeHolder<CraftingRecipe>> allowedFor(ServerPlayer player, IItemHandler pile) {
        List<ItemStack> parts = parts(pile);
        return r -> CraftLocks.allowed(player, r, r.value().getResultItem(player.registryAccess()), parts);
    }

    private static List<ItemStack> parts(IItemHandler pile) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < Math.min(GridLock.PILE_SLOTS, pile.getSlots()); i++) {
            ItemStack s = pile.getStackInSlot(i);
            if (!s.isEmpty()) out.add(s.copy());
        }
        return out;
    }

    /**
     * Crafts {@code match} from the pile for {@code player}: assembles the result (with the crafting player set, as
     * TFC's damaging remainders need it), empties the pile, puts each remainder back on its place, gives the result
     * and fires what a crafting grid fires (crafted-by, {@code ItemCraftedEvent}, the recipe-crafted trigger, the
     * recipe unlock). @return the crafted stack, empty if the recipe made nothing (then the pile is untouched)
     */
    public static ItemStack craft(ServerPlayer player, ServerLevel level, IItemHandlerModifiable pile, Match match) {
        CraftingInput input = match.placed().input();
        CraftingRecipe recipe = match.recipe().value();
        ItemStack result;
        NonNullList<ItemStack> rest;
        CommonHooks.setCraftingPlayer(player);
        try {
            result = recipe.assemble(input, level.registryAccess());
            rest = recipe.getRemainingItems(input);
        } finally {
            CommonHooks.setCraftingPlayer(null);
        }
        if (result.isEmpty() || !result.isItemEnabled(level.enabledFeatures())) return ItemStack.EMPTY;
        List<ItemStack> used = parts(pile);
        for (int i = 0; i < Math.min(GridLock.PILE_SLOTS, pile.getSlots()); i++) pile.setStackInSlot(i, ItemStack.EMPTY);
        int left = match.placed().left(), top = match.placed().top();
        for (int row = 0; row < input.height(); row++) {
            for (int col = 0; col < input.width(); col++) {
                ItemStack remainder = rest.get(col + row * input.width());
                if (remainder.isEmpty()) continue;
                int slot = match.order()[(col + left) + (row + top) * 2];
                if (remainder.getCount() == 1 && slot < pile.getSlots()) pile.setStackInSlot(slot, remainder);
                else ItemHandlerHelper.giveItemToPlayer(player, remainder);
            }
        }
        result.onCraftedBy(level, player, result.getCount());
        EventHooks.firePlayerCraftingEvent(player, result, new SimpleContainer(used.toArray(ItemStack[]::new)));
        player.triggerRecipeCrafted(match.recipe(), used);
        if (!recipe.isSpecial()) player.awardRecipes(List.of(match.recipe()));
        ItemHandlerHelper.giveItemToPlayer(player, result.copy());
        FirmagesCore.LOGGER.debug("Ground craft {} by {}", match.recipe().id(), player.getGameProfile().getName());
        return result;
    }

    /** Particles of the parts and a soft sound where the pile lay. */
    static void effects(ServerLevel level, BlockPos pos, List<ItemStack> parts) {
        Vec3 c = Vec3.atBottomCenterOf(pos).add(0, 0.1, 0);
        for (ItemStack s : parts) {
            if (!s.isEmpty()) level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, s), c.x, c.y, c.z, 2, 0.2, 0.05, 0.2, 0.05);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 0.8f);
    }

    /** Sneak + empty main hand on a TFC pile: craft it if a recipe fits; otherwise TFC's own behaviour. */
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || event.isCanceled()) return;
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || player.isSpectator() || player instanceof FakePlayer) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        IItemHandlerModifiable pile = dev.firmages.core.compat.tfc.TfcPiles.pile(level, pos);
        if (pile == null) return;
        int facing = player.getDirection().get2DDataValue();
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
            if (level.isClientSide() && find(level, pile, facing, r -> true).isPresent()) claim(event);
            return;
        }
        Optional<Match> match = find(level, pile, facing, allowedFor(sp, pile));
        if (match.isEmpty()) return;
        List<ItemStack> parts = parts(pile);
        if (craft(sp, server, pile, match.get()).isEmpty()) return;
        dev.firmages.core.compat.tfc.TfcPiles.update(level, pos);
        effects(server, pos, parts);
        claim(event);
    }

    private static void claim(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * After a part was laid on a pile (server): while the grid is locked for the player and the pile now fits a
     * recipe they may craft, the action bar names the result, at most once per minute.
     */
    public static void onPartLaid(Player player, Level level, IItemHandler pile) {
        if (!(player instanceof ServerPlayer sp) || player instanceof FakePlayer || !GridLockService.locked(sp)) return;
        Optional<Match> match = find(level, pile, sp.getDirection().get2DDataValue(), allowedFor(sp, pile));
        if (match.isEmpty() || !GridLockService.takeHint(sp)) return;
        sp.displayClientMessage(Component.translatable("firmages.hint.ground_craft", match.get().preview(level).getHoverName()), true);
    }
}
