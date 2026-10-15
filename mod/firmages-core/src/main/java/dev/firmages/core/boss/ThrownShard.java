package dev.firmages.core.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A consecrated shard thrown at The Primordial (sneak + use): a hit on the boss opens its veil like a shard in the
 * off hand ({@link PrimordialEntity#shardStruck}); a miss drops the shard where it lands, so no throw is wasted.
 */
public class ThrownShard extends ThrowableItemProjectile {
    public ThrownShard(EntityType<? extends ThrownShard> type, Level level) {
        super(type, level);
    }

    public ThrownShard(Level level, LivingEntity shooter) {
        super(BossRegistry.THROWN_SHARD.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return BossRegistry.CONSECRATED_SHARD.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide) return;
        boolean struck = result instanceof EntityHitResult eh && eh.getEntity() instanceof PrimordialEntity boss && boss.shardStruck(getOwner());
        if (!struck) {
            ItemEntity drop = new ItemEntity(level(), getX(), getY(), getZ(), getItem().copyWithCount(1));
            drop.setDefaultPickUpDelay();
            level().addFreshEntity(drop);
        }
        discard();
    }
}
