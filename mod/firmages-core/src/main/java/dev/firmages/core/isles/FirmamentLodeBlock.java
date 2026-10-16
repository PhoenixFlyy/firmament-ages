package dev.firmages.core.isles;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The firmament lode (SPEC §30.5): one per medium or large isle, at the underside tip. Broken by any means that drops
 * items (a player, a fake player, a Create drill, the Digital Miner), it yields {@code isles.lodeYield} crystals (loot
 * table with {@code firmages:lode_yield}) and stands again on the next server tick: {@link #onRemove} queues the
 * position when the new state is air or a fluid and the block was not moved by a piston. Explosion resistance 1200, the
 * Wither and Dragon immune tags, {@code PushReaction.BLOCK} and {@code create:non_movable} keep it in place.
 */
public final class FirmamentLodeBlock extends Block {
    public static final MapCodec<FirmamentLodeBlock> CODEC = simpleCodec(FirmamentLodeBlock::new);

    public FirmamentLodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (level instanceof ServerLevel sl && !movedByPiston && !newState.is(this) && reformsInto(newState)) IsleService.queueReform(sl, pos);
    }

    /** The lode comes back into air or a fluid only: a block set in its place (an operator's fill) stays. */
    static boolean reformsInto(BlockState state) {
        return state.isAir() || (state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock);
    }
}
