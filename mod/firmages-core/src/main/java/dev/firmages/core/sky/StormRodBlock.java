package dev.firmages.core.sky;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * {@code firmages:storm_rod} (SPEC §26.5): two blocks high, placed and broken as one like a door. The lower half is
 * the basin with the block entity (16 sand in, 16 storm glass out, a plain container screen); the upper half is the
 * rod, whose tip must see the sky. Right-click either half opens the basin.
 */
public final class StormRodBlock extends BaseEntityBlock {
    public static final MapCodec<StormRodBlock> CODEC = simpleCodec(StormRodBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape LOWER = Shapes.or(Block.box(1, 0, 1, 15, 2, 15), Block.box(2, 2, 2, 14, 8, 14), Block.box(6, 8, 6, 10, 16, 10));
    private static final VoxelShape UPPER = Shapes.or(Block.box(6.5, 0, 6.5, 9.5, 12, 9.5), Block.box(5.5, 12, 5.5, 10.5, 15, 10.5));

    public StormRodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
    }

    /** The basin of the rod at {@code pos} (either half). */
    public static BlockPos basin(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        return pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(ctx) ? defaultBlockState() : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** Places a whole rod (both halves), for commands and tests. */
    public static void placeAt(LevelAccessor level, BlockPos basin) {
        BlockState s = HarvestRegistry.STORM_ROD.get().defaultBlockState();
        level.setBlock(basin, s.setValue(HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
        level.setBlock(basin.above(), s.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (facing.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (facing == Direction.UP)) {
            return facingState.is(this) && facingState.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, facing, facingState, level, pos, facingPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    /** Creative breaking of the rod removes the basin without a drop (the door rule). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState b = level.getBlockState(below);
            if (b.is(this) && b.getValue(HALF) == DoubleBlockHalf.LOWER) {
                if (level.getBlockEntity(below) instanceof StormRodBlockEntity be) be.dropContents();
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(b));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new StormRodBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || state.getValue(HALF) != DoubleBlockHalf.LOWER) return null;
        return createTickerHelper(type, HarvestRegistry.STORM_ROD_BE.get(), (l, p, s, be) -> be.serverTick((ServerLevel) l));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(basin(state, pos)) instanceof StormRodBlockEntity be)) return InteractionResult.PASS;
        if (!level.isClientSide) player.openMenu(be, buf -> buf.writeBlockPos(be.getBlockPos()));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof StormRodBlockEntity be) be.dropContents();
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected long getSeed(BlockState state, BlockPos pos) {
        return net.minecraft.util.Mth.getSeed(basin(state, pos));
    }
}
