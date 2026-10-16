package dev.firmages.core.launch;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * {@code firmages:launch_port} (SPEC §31.3, §31.6): one to four sit on the pad rim. A port of a complete complex passes
 * probes, modules and fuel to its launch control and gives the returns, so pipes and an AE2 pattern provider can feed
 * the complex from any side of the pad.
 */
public final class LaunchPortBlock extends BaseEntityBlock {
    public static final MapCodec<LaunchPortBlock> CODEC = simpleCodec(LaunchPortBlock::new);

    public LaunchPortBlock(Properties properties) {
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchPortBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel sl && !old.is(this)) LaunchService.blockChanged(sl, pos);
    }
}
