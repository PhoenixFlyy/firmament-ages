package dev.firmages.core.boss;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A falling star of The Primordial: either a star-fall straight down onto a marked floor spot (phase 3, the shatter,
 * the late enrage) or a node bolt aimed at a player (phase 1 volley). It bursts where it meets a block or a living
 * entity: damage in a small radius, Darkness for the bolts. Players under Firmament Ward are passed over. It never
 * outlives {@link #MAX_LIFE} ticks and is not saved with the chunk.
 */
public class StarFallEntity extends Projectile {
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(StarFallEntity.class, EntityDataSerializers.INT);
    static final int MAX_LIFE = 200;
    /** A star-fall starts this high above its floor spot and falls 1 block per tick: 1.5 s between mark and impact. */
    static final int FALL_HEIGHT = 30;

    private float damage = 10;
    private float radius = 1.5F;
    private boolean darkness;
    /** For a star-fall: the floor spot marked with gold dust while it falls; NaN for a bolt. */
    private double markY = Double.NaN;
    private int life;

    public StarFallEntity(EntityType<? extends StarFallEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** A star-fall onto (x, floorY, z): it appears {@link #FALL_HEIGHT} blocks above and falls straight down. */
    public static StarFallEntity fall(ServerLevel level, Entity owner, double x, double floorY, double z, int color, float damage) {
        StarFallEntity e = new StarFallEntity(BossRegistry.STAR_FALL.get(), level);
        e.setOwner(owner);
        e.setPos(x, floorY + FALL_HEIGHT, z);
        e.setDeltaMovement(0, -1.0, 0);
        e.markY = floorY;
        e.damage = damage;
        e.radius = 2.0F;
        e.setColor(color);
        level.addFreshEntity(e);
        return e;
    }

    /** A node bolt from {@code from} at {@code to}, 0.9 blocks per tick. */
    public static StarFallEntity bolt(ServerLevel level, Entity owner, Vec3 from, Vec3 to, int color, float damage) {
        StarFallEntity e = new StarFallEntity(BossRegistry.STAR_FALL.get(), level);
        e.setOwner(owner);
        e.setPos(from);
        Vec3 d = to.subtract(from);
        e.setDeltaMovement(d.lengthSqr() < 1.0E-4 ? new Vec3(0, -0.9, 0) : d.normalize().scale(0.9));
        e.damage = damage;
        e.radius = 1.5F;
        e.darkness = true;
        e.setColor(color);
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR, 0xF4F0FF);
    }

    public int getColor() {
        return entityData.get(DATA_COLOR);
    }

    void setColor(int rgb) {
        entityData.set(DATA_COLOR, rgb & 0xFFFFFF);
    }

    public boolean isFalling() {
        return getDeltaMovement().horizontalDistanceSqr() < 1.0E-6;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        if (!level().isClientSide) {
            if (++life > MAX_LIFE) {
                discard();
                return;
            }
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                burst(hit.getLocation());
                return;
            }
            ServerLevel sl = (ServerLevel) level();
            if (!Double.isNaN(markY) && life % 2 == 0) {
                // the mark on the floor: a gold crack that tightens as the star comes down
                double r = radius * (0.4 + 0.6 * Math.max(0, getY() - markY) / FALL_HEIGHT);
                for (int k = 0; k < 6; k++) {
                    double a = (life * 0.3) + k * Math.PI / 3;
                    sl.sendParticles(GOLD_DUST, getX() + Math.cos(a) * r, markY + 0.1, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
        }
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
    }

    static final DustParticleOptions GOLD_DUST = new DustParticleOptions(new Vector3f(0.886F, 0.706F, 0.180F), 1.2F);

    @Override
    protected boolean canHitEntity(Entity target) {
        return target instanceof LivingEntity le && le.isAlive() && !(target instanceof PrimordialEntity) && target != getOwner()
            && !(target instanceof ServerPlayer p && (p.isSpectator() || warded(p)));
    }

    static boolean warded(LivingEntity e) {
        return e.hasEffect(BossRegistry.FIRMAMENT_WARD);
    }

    private void burst(Vec3 at) {
        ServerLevel sl = (ServerLevel) level();
        Entity owner = getOwner();
        AABB box = new AABB(at, at).inflate(radius);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, this::canHitEntity)) {
            if (e.distanceToSqr(at) > (radius + e.getBbWidth()) * (radius + e.getBbWidth())) continue;
            e.hurt(damageSources().mobProjectile(this, owner instanceof LivingEntity le ? le : null), damage);
            if (darkness) e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0), this);
        }
        int c = getColor();
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(FastColor.ARGB32.red(c) / 255F, FastColor.ARGB32.green(c) / 255F,
            FastColor.ARGB32.blue(c) / 255F), 1.6F);
        sl.sendParticles(dust, at.x, at.y + 0.2, at.z, 24, radius * 0.5, 0.2, radius * 0.5, 0.0);
        sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, 10, 0.2, 0.2, 0.2, 0.15);
        sl.playSound(null, at.x, at.y, at.z, BossRegistry.STAR_FALL_IMPACT.get(), SoundSource.HOSTILE, 0.7F, 1.4F);
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
