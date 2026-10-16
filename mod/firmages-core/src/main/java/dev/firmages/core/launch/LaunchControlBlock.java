package dev.firmages.core.launch;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * {@code firmages:launch_control} (SPEC §31.3 to §31.5), at the foot of the gantry tower, its front toward the cradle.
 * Right-click with an empty hand: an unfinished complex shows its ghost and says what it lacks; a complete one steps
 * the throttle and names the phase. Right-click with a probe, a module or a fuel container puts it in; sneak and
 * right-click with an empty hand takes the returns, then the probe, then the modules back. A redstone signal on the
 * back sets the throttle, one on any other face holds the launch; the back gives redstone 15 during the countdown and
 * the climb. Breaking it mid-flight keeps the mission in the dropped item.
 */
public final class LaunchControlBlock extends BaseEntityBlock {
    public static final MapCodec<LaunchControlBlock> CODEC = simpleCodec(LaunchControlBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The throttle step the front shows (steps beyond three show three). */
    public static final IntegerProperty THROTTLE = IntegerProperty.create("throttle", 1, 3);

    public LaunchControlBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(THROTTLE, 1));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, THROTTLE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchControlBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, LaunchRegistry.LAUNCH_CONTROL_BE.get(), (l, p, s, be) -> be.serverTick((ServerLevel) l));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof LaunchControlBlockEntity be) be.setOwner(p.getUUID());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof LaunchControlBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (FluidUtil.interactWithFluidHandler(player, hand, be.handFluid())) return ItemInteractionResult.CONSUME;
        ItemStack left = be.insertFromHand(stack.copy());
        if (left.getCount() == stack.getCount()) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (!player.getAbilities().instabuild) stack.setCount(left.getCount());
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LaunchControlBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;
        if (player.isShiftKeyDown()) {
            ItemStack out = be.takeByHand();
            if (!out.isEmpty() && !player.getInventory().add(out)) player.drop(out, false);
            return InteractionResult.CONSUME;
        }
        be.requestValidation();
        LaunchService.Complex c = LaunchService.check(level, pos);
        if (c.cradle() == null) {
            sp.displayClientMessage(LaunchText.waitLine(LaunchRules.Wait.NO_CRADLE, -1, 0), true);
        } else if (!c.formed()) {
            sp.displayClientMessage(LaunchText.waitLine(LaunchRules.Wait.UNFORMED, c.missing(), 0), true);
            LaunchService.preview(c).ifPresent(p -> PacketDistributor.sendToPlayer(sp, p));
        } else {
            int step = be.cycleThrottle();
            int steps = LaunchDataLoader.current().steps().size();
            sp.displayClientMessage(Component.translatable("firmages.launch.status", LaunchText.throttle(be.redstoneThrottle() ? be.step() : step, steps,
                be.redstoneThrottle()), c.complete() ? LaunchText.phaseLine(be.phase(), be.countdownLeft(), be.percent())
                : LaunchText.waitLine(c.problem(), c.missing(), be.launchMb())).withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** Redstone 15 out of the back (toward the block behind the control) during the countdown and the climb. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == state.getValue(FACING) && level.getBlockEntity(pos) instanceof LaunchControlBlockEntity be && be.emitting() ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel sl && !old.is(this)) LaunchService.blockChanged(sl, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LaunchControlBlockEntity be) be.dropContents();
        super.onRemove(state, level, pos, newState, moving);
    }
}
