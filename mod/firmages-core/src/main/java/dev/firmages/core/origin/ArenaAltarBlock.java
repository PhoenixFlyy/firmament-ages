package dev.firmages.core.origin;

import dev.firmages.core.boss.ArenaAltars;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One of the four fight altars of the grand arena (round G3, SPEC §39.1): unbreakable, no loot, never an item. A
 * right click with an empty hand starts a 3 s channel ({@link ArenaAltars}); {@link #ACTIVE} shows a lit altar.
 */
public class ArenaAltarBlock extends Block {
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;

    public ArenaAltarBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ACTIVE);
    }

    /** Creative pick block gives the Gathering altar's item (the fight altar has none of its own). */
    @Override
    public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        return new net.minecraft.world.item.ItemStack(OriginRegistry.ORIGIN_ALTAR_ITEM.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || state.getValue(ACTIVE)) return InteractionResult.PASS;
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            return ArenaAltars.startChannel(sl, pos, sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }
}
