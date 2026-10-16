package dev.firmages.core.sky;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The bucket of aurora light (SPEC §26.4): it fills and empties tanks (its fluid capability is registered in
 * {@link HarvestRegistry}, since NeoForge only wraps the plain {@code BucketItem} class) and never pours the light into
 * the world.
 */
public final class AuroraLightBucketItem extends BucketItem {
    public AuroraLightBucketItem(Fluid content, Properties properties) {
        super(content, properties);
    }

    @Override
    public boolean emptyContents(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult result, @Nullable ItemStack container) {
        return false;
    }
}
