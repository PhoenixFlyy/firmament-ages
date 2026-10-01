package dev.firmages.core.shrine;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** {@code firmages:consecrated_lamp}: the consecrated family member with {@code lit} (light 15 while lit, the default). */
public final class ConsecratedLampBlock extends ConsecratedBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ConsecratedLampBlock(Properties properties) {
        super(Consecration.Role.LAMP, properties);
        registerDefaultState(defaultBlockState().setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }
}
