package dev.firmages.core.shrine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * {@code firmages:shrine_heart} (SPEC §2.5, §7.1): the fired-clay heart of the one shrine. Blockstate
 * {@code awakened} (0..10), {@code ready} (the current tier's rings stand) and {@code lit} (kindled, the Stone rite)
 * are read by FTB Quests observation tasks; {@code maintenance} shows maintenance mode (SPEC §17). Breaking it needs sneaking and a pickaxe.
 */
public final class ShrineHeartBlock extends BaseEntityBlock {
    public static final MapCodec<ShrineHeartBlock> CODEC = simpleCodec(ShrineHeartBlock::new);
    public static final IntegerProperty AWAKENED = IntegerProperty.create("awakened", 0, 10);
    public static final BooleanProperty READY = BooleanProperty.create("ready");
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    /** Maintenance mode is on (SPEC §17): consecrated blocks may be broken; drawn as a scaffold cage. */
    public static final BooleanProperty MAINTENANCE = BooleanProperty.create("maintenance");
    /** Items that kindle the heart (the Stone Age rite). */
    public static final TagKey<Item> KINDLERS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("firmages", "shrine/kindlers"));
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 15, 15);

    public ShrineHeartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AWAKENED, 0).setValue(READY, false).setValue(LIT, false).setValue(MAINTENANCE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AWAKENED, READY, LIT, MAINTENANCE);
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
        return new ShrineHeartBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ShrineRegistry.SHRINE_HEART_BE.get(), ShrineHeartBlockEntity::serverTick);
    }

    /** Only one shrine per server: a second heart is refused while the first one stands. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (ctx.getLevel() instanceof ServerLevel sl && !ShrineService.mayPlaceHeart(sl, ctx.getClickedPos(), ctx.getPlayer())) return null;
        return defaultBlockState();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock()) && level instanceof ServerLevel sl) ShrineService.onHeartPlaced(sl, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) ShrineService.onHeartRemoved(sl, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.is(KINDLERS)) {
            if (state.getValue(LIT)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide) ShrineService.kindle((ServerLevel) level, pos, (ServerPlayer) player, stack, hand);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        return ShrineService.offerAtHeart((ServerLevel) level, pos, stack, (ServerPlayer) player)
            ? ItemInteractionResult.CONSUME : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) ShrineService.pray((ServerLevel) level, pos, (ServerPlayer) player);
        else ShrineService.inspect((ServerLevel) level, pos, (ServerPlayer) player);
        return InteractionResult.CONSUME;
    }

    /** Sneak plus a pickaxe, or creative: the heart does not come loose by accident. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!player.isCreative() && !(player.isShiftKeyDown() && player.getMainHandItem().is(ItemTags.PICKAXES))) return 0.0F;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        if (state.getValue(LIT)) {
            level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.4, y, z + (random.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
            if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, x, y + 0.2, z, 0, 0.03, 0);
        }
        if (state.getValue(READY) && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, x, y + 0.6, z, (random.nextDouble() - 0.5) * 2, random.nextDouble() * 1.2, (random.nextDouble() - 0.5) * 2);
        }
    }
}
