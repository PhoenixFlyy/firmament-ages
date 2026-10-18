package dev.firmages.core.isles;

import com.mojang.serialization.MapCodec;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * A Wind Seal of an isle ruin (G3, Felix I1B, SPEC §30.13). Four seals stand around the sealed lid of the crystal
 * chamber, each facing one point of the compass. The seal worn by the wind ({@link #FIRST}) wakes first, the others
 * follow with the wind, clockwise round the compass; a seal pressed out of turn puts every seal of the ruin back to
 * sleep. When the fourth wakes, the lid ({@code firmages:wind_seal_lid}) within {@link #LID_REACH} blocks falls away.
 * The seals answer only from the Industrial Age (server Age), like the crystal they guard. Unbreakable, no drops.
 */
public final class WindSealBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<WindSealBlock> CODEC = simpleCodec(WindSealBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty FIRST = BooleanProperty.create("first");
    /** The seals of one ruin lie within this many blocks of each other. */
    public static final int SEAL_REACH = 6;
    /** The lid blocks lie within this many blocks of the last seal. */
    public static final int LID_REACH = 8;

    public WindSealBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(FIRST, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, LIT, FIRST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        press(sl, pos);
        return InteractionResult.CONSUME;
    }

    /** What a press did. */
    public enum Outcome { ASLEEP, AWAKE, WRONG, SOLVED, NOTHING }

    /** Presses the seal at {@code pos}: the puzzle's whole rule (also called by the GameTests). */
    public static Outcome press(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WindSealBlock) || state.getValue(LIT)) return Outcome.NOTHING;
        if (!AgeState.get(level.getServer()).snapshot().isUnlocked(AgeId.AGE_4)) {
            level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.6F, 0.6F);
            return Outcome.ASLEEP;
        }
        List<BlockPos> seals = seals(level, pos);
        BlockPos first = null;
        int lit = 0;
        for (BlockPos p : seals) {
            BlockState s = level.getBlockState(p);
            if (s.getValue(FIRST)) first = p;
            if (s.getValue(LIT)) lit++;
        }
        Direction want = null;
        if (first != null) {
            want = level.getBlockState(first).getValue(FACING);
            for (int i = 0; i < lit; i++) want = want.getClockWise();
        }
        if (want == null || state.getValue(FACING) != want) {
            for (BlockPos p : seals) level.setBlock(p, level.getBlockState(p).setValue(LIT, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BREEZE_DEFLECT, SoundSource.BLOCKS, 1.0F, 0.6F);
            return Outcome.WRONG;
        }
        level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 0.7F + 0.2F * lit);
        if (lit + 1 < 4) return Outcome.AWAKE;
        int r = LID_REACH;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -r, -r), pos.offset(r, r, r))) {
            if (level.getBlockState(p).is(IsleRegistry.WIND_SEAL_LID.get())) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.playSound(null, pos, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.BLOCKS, 1.5F, 0.8F);
        return Outcome.SOLVED;
    }

    /** The seals of the ruin around {@code pos} (this one included). */
    static List<BlockPos> seals(ServerLevel level, BlockPos pos) {
        List<BlockPos> out = new ArrayList<>();
        int r = SEAL_REACH;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -2, -r), pos.offset(r, 2, r))) {
            if (level.getBlockState(p).getBlock() instanceof WindSealBlock) out.add(p.immutable());
        }
        return out;
    }
}
