package dev.firmages.core.crown;

import com.mojang.serialization.JsonOps;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.CacheGeneration;
import dev.firmages.core.compat.modonomicon.ShrineMultiblocks;
import dev.firmages.core.net.ShrinePreviewPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Lens Crown on the server (SPEC §27.5, §27.6): crown validation through {@link ShrineMultiblocks} (the multiblock
 * {@code firmages:lens_crown} has no ring roles, so no consecrated equivalents apply), the triggers that re-validate a
 * crown, the live {@code firmages:starforge} recipes (duplicates by ingredient list dropped with a WARN, the later id
 * loses), the open-sky rule and the anvil's empty-hand preview.
 */
public final class CrownService {
    public static final ResourceLocation CROWN_ID = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "lens_crown");
    /** Horizontal half-size and height of the crown around its anvil. */
    public static final int RADIUS = 4;
    public static final int HEIGHT = 3;
    public static final char SOCKET_KEY = 'M';

    /** Loaded anvils per dimension (server thread only). */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> ANVILS = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static RecipeManager cachedManager;
    private static int cachedGeneration = -1;
    private static List<RecipeHolder<StarforgeRecipe>> cached = List.of();
    private static int generation;
    private static boolean warnedUnknownMultiblock;

    private CrownService() {}

    public static void register(IEventBus bus) {
        bus.addListener(CrownService::onReloadListeners);
        bus.addListener(CrownService::onBreak);
        bus.addListener(CrownService::onPlace);
        bus.addListener(CrownService::onExplosion);
        bus.addListener(CrownService::onServerStopped);
    }

    private static void onReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ChordLibraryLoader());
    }

    static void onDataReloaded() {
        synchronized (WARNED) {
            WARNED.clear();
        }
        cachedManager = null;
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ANVILS.clear();
        cachedManager = null;
        cached = List.of();
    }

    // ---------------------------------------------------------------- tracking and triggers

    static void track(ServerLevel level, BlockPos pos) {
        ANVILS.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
    }

    static void untrack(ServerLevel level, BlockPos pos) {
        Set<BlockPos> s = ANVILS.get(level.dimension());
        if (s != null) s.remove(pos);
    }

    /** Every loaded anvil of this level. */
    public static List<BlockPos> anvils(ServerLevel level) {
        return List.copyOf(ANVILS.getOrDefault(level.dimension(), Set.of()));
    }

    /** Anvils of loaded crowns in this level whose crown box holds {@code pos}. */
    public static List<BlockPos> anvilsAround(ServerLevel level, BlockPos pos) {
        Set<BlockPos> s = ANVILS.get(level.dimension());
        if (s == null || s.isEmpty()) return List.of();
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos a : s) {
            int dy = pos.getY() - a.getY();
            if (Math.abs(pos.getX() - a.getX()) <= RADIUS && Math.abs(pos.getZ() - a.getZ()) <= RADIUS && dy >= 0 && dy < HEIGHT) out.add(a);
        }
        return out;
    }

    /** A block inside a crown box changed (or is about to): validate that crown on its next tick. */
    public static void blockChanged(ServerLevel level, BlockPos pos) {
        for (BlockPos a : anvilsAround(level, pos)) {
            if (level.getBlockEntity(a) instanceof StarAnvilBlockEntity be) be.requestValidation();
        }
        if (level.getBlockEntity(pos) instanceof StarAnvilBlockEntity be) be.requestValidation();
    }

    static void mountChanged(ServerLevel level, BlockPos pos) {
        blockChanged(level, pos);
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel sl) blockChanged(sl, event.getPos());
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel sl) blockChanged(sl, event.getPos());
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        for (BlockPos p : event.getAffectedBlocks()) blockChanged(sl, p);
    }

    // ---------------------------------------------------------------- validation

    /** The crown check of the anvil at {@code pos} (all four rotations; the pattern is four-fold symmetric). */
    public static ShrineMultiblocks.RingCheck check(Level level, BlockPos pos) {
        return ShrineMultiblocks.check(level, pos, CROWN_ID);
    }

    /** Validates the crown of {@code be}: formed flag, missing blocks, the mounts on its sockets and their facing. */
    static void validate(ServerLevel level, BlockPos pos, StarAnvilBlockEntity be) {
        ShrineMultiblocks.RingCheck c = check(level, pos);
        if (!c.known()) {
            if (!warnedUnknownMultiblock) {
                warnedUnknownMultiblock = true;
                FirmagesCore.LOGGER.error("Lens Crown: multiblock {} is not loaded; no crown can form", CROWN_ID);
            }
            be.setCrown(false, -1, List.of());
            return;
        }
        List<BlockPos> mounts = new ArrayList<>();
        for (ShrineMultiblocks.Cell cell : c.cells()) {
            if (cell.key() == SOCKET_KEY && level.getBlockState(cell.pos()).is(CrownRegistry.LENS_MOUNT.get())) mounts.add(cell.pos());
        }
        mounts.sort(Comparator.comparingLong(BlockPos::asLong));
        boolean formed = c.valid();
        if (formed) {
            for (BlockPos m : mounts) {
                BlockState s = level.getBlockState(m);
                Direction toward = Direction.getNearest(pos.getX() - m.getX(), 0, pos.getZ() - m.getZ());
                if (toward.getAxis().isHorizontal() && s.getValue(LensMountBlock.FACING) != toward) {
                    level.setBlock(m, s.setValue(LensMountBlock.FACING, toward), 3);
                }
            }
        }
        be.setCrown(formed, Math.max(0, c.total() - c.matched()), formed ? mounts : List.of());
    }

    /** Open sky above the anvil: a dimension with a sky and no ceiling (not the Nether, the End or The Origin). */
    public static boolean skyOpen(Level level, BlockPos anvil) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling() && level.canSeeSky(anvil.above());
    }

    // ---------------------------------------------------------------- recipes

    /**
     * The live {@code firmages:starforge} recipes by id; a recipe whose ingredient list repeats an earlier one is
     * dropped (WARN once per datapack load). Rebuilt after every load and every Age swap.
     */
    public static List<RecipeHolder<StarforgeRecipe>> recipes(Level level) {
        RecipeManager rm = level.getRecipeManager();
        int gen = CacheGeneration.get();
        if (rm == cachedManager && gen == cachedGeneration) return cached;
        List<RecipeHolder<StarforgeRecipe>> all = new ArrayList<>(rm.getAllRecipesFor(CrownRegistry.STARFORGE_TYPE.get()));
        all.sort(Comparator.comparing(h -> h.id().toString()));
        Map<String, ResourceLocation> seen = new HashMap<>();
        List<RecipeHolder<StarforgeRecipe>> out = new ArrayList<>();
        for (RecipeHolder<StarforgeRecipe> h : all) {
            String key = ingredientKey(h.value());
            ResourceLocation first = seen.putIfAbsent(key, h.id());
            if (first != null) {
                synchronized (WARNED) {
                    if (WARNED.add(h.id().toString())) {
                        FirmagesCore.LOGGER.warn("Starforge recipe {} has the same ingredients as {}; {} is ignored", h.id(), first, h.id());
                    }
                }
                continue;
            }
            out.add(h);
        }
        cached = List.copyOf(out);
        cachedManager = rm;
        cachedGeneration = gen;
        generation++;
        return cached;
    }

    /** Changes whenever {@link #recipes} was rebuilt, so anvils look their recipe up again. */
    static int recipeGeneration(Level level) {
        recipes(level);
        return generation;
    }

    /** The ingredient list as comparable text (order-independent). */
    static String ingredientKey(StarforgeRecipe r) {
        List<String> parts = new ArrayList<>();
        for (SizedIngredient i : r.ingredients()) {
            parts.add(SizedIngredient.FLAT_CODEC.encodeStart(JsonOps.INSTANCE, i).result().map(Object::toString).orElse(i.toString()));
        }
        parts.sort(null);
        return String.join("|", parts);
    }

    /** The recipe the inputs hold completely, if any (ingredient sets are disjoint, so at most one). */
    public static Optional<RecipeHolder<StarforgeRecipe>> match(Level level, List<ItemStack> inputs) {
        if (inputs.stream().allMatch(ItemStack::isEmpty)) return Optional.empty();
        for (RecipeHolder<StarforgeRecipe> h : recipes(level)) if (h.value().assignment(inputs) != null) return Optional.of(h);
        return Optional.empty();
    }

    /** A fluid some live starforge recipe takes (the tank accepts nothing else). */
    public static boolean fluidAccepted(Level level, FluidStack fluid) {
        if (fluid.isEmpty()) return false;
        for (RecipeHolder<StarforgeRecipe> h : recipes(level)) {
            if (h.value().fluid().map(f -> f.ingredient().test(fluid)).orElse(false)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- the anvil by hand

    /**
     * Right-click with an empty hand (SPEC §27.5): an unformed crown shows its ghost through {@code firmages:shrine_preview}
     * and says how many blocks it lacks; a formed crown names the chord it plays and the pending forging.
     */
    static void showCrown(ServerLevel level, BlockPos pos, StarAnvilBlockEntity be, ServerPlayer player) {
        be.requestValidation();
        ShrineMultiblocks.RingCheck c = check(level, pos);
        if (!c.valid()) {
            int missing = c.known() ? Math.max(0, c.total() - c.matched()) : -1;
            player.displayClientMessage(CrownText.lacks(missing), true);
            preview(level, pos).ifPresent(p -> PacketDistributor.sendToPlayer(player, p));
            return;
        }
        MutableComponent chord = ChordLibrary.current().byMask(be.lensesIn()).map(ch -> CrownText.chordLine(ch.id()))
            .orElseGet(() -> Component.translatable(be.lensesIn() == 0 ? "firmages.crown.chord_none" : "firmages.crown.chord_unnamed"));
        player.displayClientMessage(Component.translatable("firmages.crown.status", chord, CrownText.state(be)).withStyle(ChatFormatting.GOLD), true);
    }

    /** The ghost of an unformed crown ({@code firmages:shrine_preview}, anchored at the anvil), empty when it stands. */
    public static Optional<ShrinePreviewPayload> preview(Level level, BlockPos pos) {
        ShrineMultiblocks.RingCheck c = check(level, pos);
        if (!c.known() || c.valid()) return Optional.empty();
        return Optional.of(new ShrinePreviewPayload(CROWN_ID, pos, c.bestRotation().ordinal(), false));
    }

    // ---------------------------------------------------------------- status (command)

    public static List<Component> status(ServerLevel level, BlockPos pos) {
        List<Component> out = new ArrayList<>();
        if (!(level.getBlockEntity(pos) instanceof StarAnvilBlockEntity be)) {
            out.add(Component.literal("No Star Anvil at " + pos.toShortString()));
            return out;
        }
        ShrineMultiblocks.RingCheck c = check(level, pos);
        out.add(Component.literal("Lens Crown at " + pos.toShortString() + " in " + level.dimension().location()).withStyle(ChatFormatting.GOLD));
        out.add(Component.literal(c.known() ? (c.valid() ? "Formed, rotation " + c.rotation() : "Not formed: " + c.matched() + "/" + c.total()
            + " blocks, missing " + c.missing()) : "Multiblock " + CROWN_ID + " is not loaded"));
        StringBuilder m = new StringBuilder();
        for (BlockPos mp : be.mounts()) {
            if (level.getBlockEntity(mp) instanceof LensMountBlockEntity mb) {
                m.append(" ").append(mp.toShortString()).append("=").append(mb.lensIndex() < 0 ? "empty" : CrownRegistry.LENS_NAMES.get(mb.lensIndex()))
                    .append(mb.fullyIn() ? "(in)" : mb.target() ? "(swinging)" : "(out)");
            }
        }
        out.add(Component.literal("Mounts " + be.mounts().size() + ":" + m));
        out.add(Component.literal("Chord in: " + Chord.lenses(be.lensesIn()) + ChordLibrary.current().byMask(be.lensesIn()).map(ch -> " = " + ch.id()).orElse("")));
        RecipeHolder<StarforgeRecipe> r = be.pendingRecipe();
        out.add(Component.literal("Pending: " + (r == null ? "none" : r.id() + " (chord " + r.value().chord() + " " + Chord.lenses(r.value().chordMask())
            + ", signal " + r.value().signal() + ", time " + r.value().time() + ")") + "; progress " + String.format(java.util.Locale.ROOT, "%.1f", be.progress())
            + " (" + be.percent() + " %); reason " + be.reason()));
        FluidStack f = be.fluid();
        out.add(Component.literal("Tank: " + (f.isEmpty() ? "empty" : f.getAmount() + " mB " + net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f.getFluid()))
            + "; inputs " + be.inputs().stream().map(s -> s.isEmpty() ? "-" : s.getCount() + "x" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem())).toList()
            + "; output " + (be.stack(StarAnvilBlockEntity.OUTPUT).isEmpty() ? "-" : be.stack(StarAnvilBlockEntity.OUTPUT).getCount() + "x"
            + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(be.stack(StarAnvilBlockEntity.OUTPUT).getItem()))));
        out.add(Component.literal("Sky open " + skyOpen(level, pos) + ", night " + level.isNight() + ", signal " + be.signal()));
        return out;
    }
}
