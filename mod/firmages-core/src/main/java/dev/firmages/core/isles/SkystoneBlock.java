package dev.firmages.core.isles;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Skystone (SPEC §30.4): a full, face-culling block that lets sky light through ({@code propagatesSkylightDown},
 * light block 0), so the land under an isle keeps sky light 15 (no dark patch, no spawns, crops grow). Mob spawns on it
 * are refused by its properties ({@code isValidSpawn} false). It is in neither {@code tfc:can_collapse} nor
 * {@code tfc:can_landslide}.
 */
public final class SkystoneBlock extends Block {
    public static final MapCodec<SkystoneBlock> CODEC = simpleCodec(SkystoneBlock::new);

    public SkystoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }
}
