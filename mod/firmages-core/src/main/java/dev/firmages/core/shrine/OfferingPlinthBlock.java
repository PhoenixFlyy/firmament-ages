package dev.firmages.core.shrine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** {@code firmages:offering_plinth}: one item per plinth, drawn on top by its renderer. */
public final class OfferingPlinthBlock extends BaseEntityBlock {
    public static final MapCodec<OfferingPlinthBlock> CODEC = simpleCodec(OfferingPlinthBlock::new);
    /** True while the plinth holds an enshrined relic. */
    public static final BooleanProperty AWAKENED = BooleanProperty.create("awakened");
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 14, 14);

    public OfferingPlinthBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AWAKENED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AWAKENED);
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
        return new OfferingPlinthBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // Building next to a plinth: block items that are no offering are placed as usual.
        if (stack.getItem() instanceof BlockItem && !ShrineService.isOffering(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        ShrineService.offerAtPlinth((ServerLevel) level, pos, stack, (ServerPlayer) player);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ShrineService.usePlinthEmptyHand((ServerLevel) level, pos, (ServerPlayer) player);
        return InteractionResult.CONSUME;
    }

    /** A plinth holding an item, or waiting for its lent relic, cannot be broken in survival; the relic stays forever. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!player.isCreative() && level.getBlockEntity(pos) instanceof OfferingPlinthBlockEntity be && (!be.isEmpty() || be.isLent())) return 0.0F;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof OfferingPlinthBlockEntity be) {
            if (!be.isEmpty()) ShrineService.onPlinthRemoved(sl, pos, be.isRelic(), be.drainForRemoval());
            else if (be.isLent()) ShrineService.onLentPlinthRemoved(sl, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
