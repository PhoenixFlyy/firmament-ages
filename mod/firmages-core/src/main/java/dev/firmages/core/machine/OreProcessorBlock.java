package dev.firmages.core.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * {@code firmages:ore_processor} (SPEC §40), the Singularity Age's ore step: TFC ore pieces, a Mekanism chemical and a
 * working fluid in, six times the metal of the piece out as dust, for a fixed FE per tick. Right-click with an item puts
 * it in (or fills a tank from a fluid container); right-click with an empty hand says what it does or waits for; sneak
 * and right-click with an empty hand takes the results.
 */
public final class OreProcessorBlock extends BaseEntityBlock {
    public static final MapCodec<OreProcessorBlock> CODEC = simpleCodec(OreProcessorBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public OreProcessorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OreProcessorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, MachineRegistry.ORE_PROCESSOR_BE.get(), (l, p, s, be) -> be.serverTick((ServerLevel) l));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof OreProcessorBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (FluidUtil.interactWithFluidHandler(player, hand, be.fluidHandler())) return ItemInteractionResult.CONSUME;
        ItemStack left = be.insertFromHand(stack.copy());
        if (left.getCount() == stack.getCount()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!player.getAbilities().instabuild) stack.setCount(left.getCount());
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof OreProcessorBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            ItemStack out = be.takeByHand();
            if (!out.isEmpty() && !player.getInventory().add(out)) player.drop(out, false);
        } else {
            ((ServerPlayer) player).displayClientMessage(MachineText.processorState(be), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof OreProcessorBlockEntity be) be.dropContents();
        super.onRemove(state, level, pos, newState, moving);
    }
}
