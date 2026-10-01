package dev.firmages.core.shrine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * {@code firmages:consecrated_<role>} (SPEC §17): what a ring block becomes once Caelum accepted the ring. One
 * family with a shared look; {@code accent} (0..8) is the ring / Age index and selects the one colour accent.
 * Unbreakable like bedrock (hardness -1, blast resistance 3,600,000, pistons blocked, never replaced by fluids,
 * no entity may destroy it, explosions skip it). Only while the shrine is in maintenance mode a survival player can
 * break it; it then drops the original block stored for its position ({@link ShrineConsecration}). Its own loot
 * table is empty.
 */
public class ConsecratedBlock extends Block {
    public static final IntegerProperty ACCENT = IntegerProperty.create("accent", 0, Consecration.MAX_ACCENT);
    /** Hardness used for the destroy speed while maintenance is on (stone). */
    static final float MAINTENANCE_HARDNESS = 1.5F;

    private final Consecration.Role role;

    public ConsecratedBlock(Consecration.Role role, Properties properties) {
        super(properties);
        this.role = role;
        registerDefaultState(stateDefinition.any().setValue(ACCENT, 0));
    }

    public Consecration.Role role() {
        return role;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACCENT);
    }

    /** Breakable by players only during maintenance, at stone speed; never by anything else (hardness stays -1). */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!ShrineConsecration.maintenanceActive(player.level())) return 0.0F;
        return player.getDigSpeed(state, pos) / MAINTENANCE_HARDNESS / 30.0F;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity be, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, be, tool);
        if (level instanceof ServerLevel sl) ShrineConsecration.dropOriginal(sl, pos, player, tool);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!(newState.getBlock() instanceof ConsecratedBlock) && level instanceof ServerLevel sl) ShrineConsecration.onConsecratedGone(sl, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    /** Explosions never remove it, whatever their power (the Detonate event filter removes it from the list too). */
    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return role.transparent();
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return role.transparent() ? 1.0F : super.getShadeBrightness(state, level, pos);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return role == Consecration.Role.GLASS && adjacent.is(this) || super.skipRendering(state, adjacent, side);
    }
}
