package dev.firmages.core.compat.ie;

import blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.Nullable;

/**
 * Immersive Engineering step of the energy reference runs ({@code balance/ReferenceRun}, harmony package A8):
 * {@code ie_form} forms a multiblock at its trigger block the way the Engineer's Hammer does after its checks
 * (IE 12.4.2 [javap {@code TemplateMultiblock.createStructure}]: a top-face click forms "from" the player's facing; an
 * unrotated template forms from the north). The hammer itself would ask the Age locks about the fake player.
 */
public final class IeReference {
    private IeReference() {}

    @Nullable
    public static String step(String op, JsonObject s, ServerLevel level, BlockPos pos) {
        if ("ie_rs".equals(op)) return redstone(level, pos, s.get("enabled_without_signal").getAsBoolean());
        if (!"ie_form".equals(op)) return null;
        MultiblockHandler.IMultiblock mb = MultiblockHandler.getByUniqueName(ResourceLocation.parse(s.get("name").getAsString()));
        if (mb == null) return "no IE multiblock " + s.get("name").getAsString();
        // the given side, else every horizontal side in turn (a template's front depends on how it was placed)
        java.util.List<Direction> sides = s.has("side") ? java.util.List.of(Direction.byName(s.get("side").getAsString()))
            : java.util.List.copyOf(Direction.Plane.HORIZONTAL.stream().toList());
        for (Direction side : sides) {
            for (Direction tryFace : java.util.List.of(side, side.getOpposite())) {
                if (mb.createStructure(level, pos, tryFace, FakePlayerFactory.getMinecraft(level))) {
                    return "formed from " + tryFace + ", now " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
                }
            }
        }
        return "not formed, trigger " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()) + ", trigger check up "
            + mb.isBlockTrigger(level.getBlockState(pos), Direction.UP, level);
    }

    /** An IE multiblock's logic state, field by field (reflection; dev probe), and whether its redstone lets it run. */
    @Nullable
    public static String probe(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockBE<?> mb)) return null;
        var helper = mb.getHelper();
        Object state = helper.getState();
        StringBuilder out = new StringBuilder(state.getClass().getSimpleName()).append(':');
        for (java.lang.reflect.Field f : state.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(state);
                String t = v instanceof net.neoforged.neoforge.fluids.capability.templates.FluidTank tank ? tank.getFluidAmount() + " mB"
                    : v instanceof java.util.Collection<?> c ? c.size() + " entries, " + present(c) + " present" : String.valueOf(v);
                if (v instanceof java.util.function.BiFunction<?, ?, ?> getter && f.getName().contains("recipe")) {
                    t = "recipe for the tank: " + recipeFor(state, getter, level);
                }
                out.append(' ').append(f.getName()).append('=').append(t.length() > 60 ? t.substring(0, 60) : t);
                if (v instanceof blusunrize.immersiveengineering.api.multiblocks.blocks.component.RedstoneControl.RSState rs) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    boolean on = rs.isEnabled((blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockContext) helper.getContext());
                    out.append(" (enabled ").append(on).append(')');
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                out.append(' ').append(f.getName()).append("=?");
            }
        }
        return out.toString();
    }

    /**
     * Sets a formed multiblock's redstone mode (the setting a player flips in its GUI or with a screwdriver):
     * {@code enabled_without_signal} true makes it run without a signal (IE's RSState field rsEnablesMachine false).
     */
    private static String redstone(ServerLevel level, BlockPos pos, boolean enabledWithoutSignal) {
        if (!(level.getBlockEntity(pos) instanceof blusunrize.immersiveengineering.api.multiblocks.blocks.logic.IMultiblockBE<?> mb)) return "no IE multiblock";
        Object state = mb.getHelper().getState();
        for (java.lang.reflect.Field f : state.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                if (f.get(state) instanceof blusunrize.immersiveengineering.api.multiblocks.blocks.component.RedstoneControl.RSState rs) {
                    java.lang.reflect.Field g = rs.getClass().getDeclaredField("rsEnablesMachine");
                    g.setAccessible(true);
                    g.setBoolean(rs, !enabledWithoutSignal);
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    boolean on = rs.isEnabled((blusunrize.immersiveengineering.api.multiblocks.blocks.env.IMultiblockContext) mb.getHelper().getContext());
                    return "rsEnablesMachine " + !enabledWithoutSignal + ", enabled now " + on;
                }
            } catch (ReflectiveOperationException e) {
                return "cannot set: " + e;
            }
        }
        return "no redstone control";
    }

    /** How many capability references of a list resolve now (IE's CapabilityReference.getNullable). */
    private static int present(java.util.Collection<?> refs) {
        int n = 0;
        for (Object r : refs) {
            try {
                java.lang.reflect.Method m = r.getClass().getMethod("getNullable");
                if (m.invoke(r) != null) n++;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return -1;
            }
        }
        return n;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String recipeFor(Object state, java.util.function.BiFunction getter, ServerLevel level) {
        for (java.lang.reflect.Field f : state.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                if (f.get(state) instanceof net.neoforged.neoforge.fluids.capability.templates.FluidTank tank) {
                    if (tank.getFluid().isEmpty()) return "empty tank";
                    Object r = getter.apply(level, tank.getFluid().getFluid());
                    return r == null ? "none for " + BuiltInRegistries.FLUID.getKey(tank.getFluid().getFluid()) : r.toString();
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                return "? " + e;
            }
        }
        return "no tank";
    }
}
