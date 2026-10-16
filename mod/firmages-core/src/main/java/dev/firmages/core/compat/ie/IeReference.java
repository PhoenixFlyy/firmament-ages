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
                    : v instanceof java.util.Collection<?> c ? c.size() + " entries" : String.valueOf(v);
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
}
