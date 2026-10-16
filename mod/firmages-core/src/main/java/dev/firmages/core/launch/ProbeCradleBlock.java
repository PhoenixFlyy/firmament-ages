package dev.firmages.core.launch;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * {@code firmages:probe_cradle} (SPEC §31.3): the centre of the pad and the anchor of the complex's multiblock (key
 * {@code 0}); the probe stands on it and the launch renderer draws from it. It has no inventory: the launch control
 * three blocks away holds the probe.
 */
public final class ProbeCradleBlock extends BaseEntityBlock {
    public static final MapCodec<ProbeCradleBlock> CODEC = simpleCodec(ProbeCradleBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 6, 16), Block.box(1, 6, 1, 3, 14, 3), Block.box(13, 6, 1, 15, 14, 3),
        Block.box(1, 6, 13, 3, 14, 15), Block.box(13, 6, 13, 15, 14, 15));

    public ProbeCradleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProbeCradleBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel sl && !old.is(this)) LaunchService.blockChanged(sl, pos);
    }
}
