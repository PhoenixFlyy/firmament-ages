package dev.firmages.core.ark;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The island plinth (SPEC §31.8). Right-click with a sample item gives it to the island; right-click while leading an
 * animal gives the animal; right-click with an empty hand checks the island's work and tells what it still waits for.
 * Before the finale every island sleeps.
 */
public final class ArkPlinthBlock extends Block {
    public ArkPlinthBlock(Properties props) {
        super(props);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SUCCESS;
        ArkService.Use r = ArkService.useWithItem((ServerLevel) level, pos, (ServerPlayer) player, stack);
        return switch (r) {
            case DONE -> ItemInteractionResult.CONSUME;
            case PASS -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ArkService.useEmptyHand((ServerLevel) level, pos, (ServerPlayer) player);
        return InteractionResult.CONSUME;
    }
}
