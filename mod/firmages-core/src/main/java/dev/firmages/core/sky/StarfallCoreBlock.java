package dev.firmages.core.sky;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * {@code firmages:starfall_core} (SPEC §25.4): the glowing heart of a crater. {@code metal} picks its drop and its tool
 * tier (as the meteorite of its metal: a starsteel core needs steel), {@code hot} gives light 15 and the smoke column
 * until the block entity's {@code coolsAt}. The double drop of a hot crater is the loot condition
 * {@code firmages:starfall_hot}, which reads the site, not this blockstate.
 */
public final class StarfallCoreBlock extends BaseEntityBlock {
    public static final MapCodec<StarfallCoreBlock> CODEC = simpleCodec(StarfallCoreBlock::new);
    public static final EnumProperty<SkyMetal> METAL = EnumProperty.create("metal", SkyMetal.class);
    public static final BooleanProperty HOT = BooleanProperty.create("hot");

    public StarfallCoreBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(METAL, SkyMetal.METEORIC_IRON).setValue(HOT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(METAL, HOT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StarfallCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, SkyRegistry.STARFALL_CORE_BE.get(), StarfallCoreBlockEntity::serverTick);
    }

    /** The meteorite whose tool tier this core shares. */
    public static BlockState meteoriteOf(BlockState core) {
        return (core.getValue(METAL) == SkyMetal.STARSTEEL ? SkyRegistry.STARSTEEL_METEORITE : SkyRegistry.METEORITE).get().defaultBlockState();
    }

    /** Drops follow the metal's meteorite: the held tool must be correct for it. */
    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return player.getMainHandItem().isCorrectToolForDrops(meteoriteOf(state));
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness == -1.0F) return 0.0F;
        int div = canHarvestBlock(state, level, pos, player) ? 30 : 100;
        return player.getDigSpeed(state, pos) / hardness / div;
    }

    /** A column of signal smoke above a hot core (the signal of a fresh crater, SPEC §25.4). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(HOT)) return;
        if (random.nextInt(3) == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, pos.getX() + 0.5 + random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1),
                pos.getY() + 1.0 + random.nextDouble(), pos.getZ() + 0.5 + random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1), 0.0, 0.07, 0.0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0, 0, 0);
        }
    }
}
