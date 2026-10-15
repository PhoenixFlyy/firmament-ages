package dev.firmages.core.wayshrine;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * {@code firmages:wayshrine_stone}, the Wayshrine Stone (S1A): a decorative block that drops itself, one per lit
 * Wayshrine, later the heart of a Sky Anchor. Blockstate {@code kind} = the type of the Wayshrine that gave it. It is
 * never a relic.
 */
public final class WayshrineStoneBlock extends Block {
    public static final MapCodec<WayshrineStoneBlock> CODEC = simpleCodec(WayshrineStoneBlock::new);
    public static final EnumProperty<WayshrineKind> KIND = EnumProperty.create("kind", WayshrineKind.class);

    public WayshrineStoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, WayshrineKind.FOREST_RING));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }
}
