package dev.firmages.core.sky;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * {@code firmages:aurora_net} (SPEC §26.4): a single block that catches aurora light at height {@code min_y} or above,
 * under open sky, wherever {@link AuroraRules} holds at its column. Players raise it on frames of any blocks; there is
 * no validation. The state {@code catching} glows (light 10). Pipes drain it from any side; a bucket takes a bucket.
 */
public final class AuroraNetBlock extends BaseEntityBlock {
    public static final MapCodec<AuroraNetBlock> CODEC = simpleCodec(AuroraNetBlock::new);
    public static final BooleanProperty CATCHING = BooleanProperty.create("catching");
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(1, 3, 1, 15, 14, 15), Block.box(0, 14, 0, 16, 16, 16));

    public AuroraNetBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CATCHING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CATCHING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AuroraNetBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, HarvestRegistry.AURORA_NET_BE.get(), (l, p, s, be) -> be.serverTick((ServerLevel) l));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || FluidUtil.getFluidHandler(stack).isEmpty() || !(level.getBlockEntity(pos) instanceof AuroraNetBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        return FluidUtil.interactWithFluidHandler(player, hand, be.drainOnly()) ? ItemInteractionResult.CONSUME
            : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
