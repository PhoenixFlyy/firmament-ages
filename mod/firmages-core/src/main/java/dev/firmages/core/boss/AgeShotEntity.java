package dev.firmages.core.boss;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A shot of The Primordial's halo (round G4, SPEC §39.2): one entity class, its behaviour chosen by its {@link ShotAge}
 * and the row of {@code balance/boss_shots.json}. It flies straight from the segment that fired it toward a player,
 * leaves a trail in its Age's colour and bursts where it meets a block or a living target: damage in the row's radius,
 * knockback, and the Age's mark (fire, levitation and blindness, a steam cloud, a chain bolt, hunger and wither, a
 * gravity well, frost, the black hole's implosion). The Iron lance passes through {@code pierce} targets. A well stays
 * at its impact for {@code well_ticks}, pulling, then ends with a shove (Space) or the implosion (Singularity). Players
 * under Firmament Ward are passed over, like the star-falls. Never saved with the chunk, never older than
 * {@link #MAX_LIFE} ticks.
 */
public class AgeShotEntity extends Projectile {
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(AgeShotEntity.class, EntityDataSerializers.INT);
    /** The shot sits at its impact as a gravity well (the renderer draws it large and turning). */
    private static final EntityDataAccessor<Boolean> DATA_WELL = SynchedEntityData.defineId(AgeShotEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int MAX_LIFE = 200;
    /** The Age in NBT, by its key ({@code stone} .. {@code singularity}). */
    public static final Codec<ShotAge> AGE_CODEC = Codec.STRING.xmap(ShotAge::byKey, a -> a.key);

    private BossShots.Shot row = BossShots.defaults().shot(ShotAge.STONE);
    private int life;
    private int pierced;
    private int wellLeft;
    private Vec3 wellAt = Vec3.ZERO;
    private final Set<Integer> struck = new HashSet<>();

    public AgeShotEntity(EntityType<? extends AgeShotEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * Fires segment {@code age} from {@code from} at {@code to}: {@code count} shots in a fan of {@code spread_deg}
     * around the line. @return the shots, in the order they were added
     */
    public static List<AgeShotEntity> fire(ServerLevel level, Entity owner, BossShots table, ShotAge age, Vec3 from, Vec3 to) {
        BossShots.Shot row = table.shot(age);
        Vec3 d = to.subtract(from);
        Vec3 dir = d.lengthSqr() < 1.0E-6 ? new Vec3(0, -1, 0) : d.normalize();
        int n = Math.max(1, row.count());
        List<AgeShotEntity> out = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            double yaw = n == 1 ? 0 : Math.toRadians(row.spreadDeg()) * (k - (n - 1) / 2.0);
            Vec3 v = yaw == 0 ? dir : dir.yRot((float) yaw);
            AgeShotEntity e = new AgeShotEntity(BossRegistry.AGE_SHOT.get(), level);
            e.setOwner(owner);
            e.setAge(age, table);
            e.setPos(from);
            e.setDeltaMovement(v.scale(row.speed()));
            level.addFreshEntity(e);
            out.add(e);
        }
        return out;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_AGE, 0);
        builder.define(DATA_WELL, false);
    }

    public ShotAge age() {
        return ShotAge.byIndex(entityData.get(DATA_AGE));
    }

    public boolean isWell() {
        return entityData.get(DATA_WELL);
    }

    public BossShots.Shot row() {
        return row;
    }

    void setAge(ShotAge age, BossShots table) {
        entityData.set(DATA_AGE, age.ordinal());
        row = table.shot(age);
    }

    public int color() {
        return age().color();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (!isWell()) {
                Vec3 m = getDeltaMovement();
                setPos(getX() + m.x, getY() + m.y, getZ() + m.z);
            }
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (++life > MAX_LIFE + row.wellTicks()) {
            discard();
            return;
        }
        if (isWell()) {
            wellStep(sl);
            return;
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le) {
            if (!strike(sl, le, hit.getLocation())) return;
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            impact(sl, hit.getLocation(), null);
            return;
        }
        trail(sl, row.trail());
        Vec3 m = getDeltaMovement();
        setPos(getX() + m.x, getY() + m.y, getZ() + m.z);
    }

    private void trail(ServerLevel sl, int n) {
        if (n <= 0) return;
        sl.sendParticles(dust(1.0F), getX(), getY(), getZ(), n, 0.12, 0.12, 0.12, 0.0);
    }

    private DustParticleOptions dust(float size) {
        int c = color();
        return new DustParticleOptions(new Vector3f(FastColor.ARGB32.red(c) / 255F, FastColor.ARGB32.green(c) / 255F, FastColor.ARGB32.blue(c) / 255F), size);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return hittable(target) && !struck.contains(target.getId());
    }

    private boolean hittable(Entity target) {
        return target instanceof LivingEntity le && le.isAlive() && !(target instanceof PrimordialEntity) && target != getOwner()
            && !(target instanceof AgeShotEntity) && !(target instanceof Player p && (p.isSpectator() || p.isCreative()))
            && !(target instanceof ServerPlayer p && StarFallEntity.warded(p));
    }

    /**
     * A living target in the way: the Iron lance runs it through and flies on until its pierce count is spent; every
     * other shot bursts here. @return true when the shot flies on
     */
    private boolean strike(ServerLevel sl, LivingEntity target, Vec3 at) {
        if (row.pierce() > 0 && pierced < row.pierce()) {
            pierced++;
            struck.add(target.getId());
            hurt(target, row.damage());
            mark(sl, target, at);
            sl.sendParticles(dust(1.4F), at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.0);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.ARROW_HIT, SoundSource.HOSTILE, 1.0F, 0.6F);
            if (pierced < row.pierce()) return true; // flies on to the next target
            discard();
            return false;
        }
        impact(sl, at, target);
        return false;
    }

    private boolean hurt(LivingEntity e, float amount) {
        Entity owner = getOwner();
        return e.hurt(damageSources().mobProjectile(this, owner instanceof LivingEntity le ? le : null), amount);
    }

    /** The burst at {@code at}: everyone in the radius takes the damage, the knockback and the Age's mark. */
    public void impact(ServerLevel sl, Vec3 at, @Nullable LivingEntity direct) {
        float radius = row.radius();
        List<LivingEntity> hit = new ArrayList<>();
        if (direct != null) hit.add(direct);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), this::hittable)) {
            if (e != direct && e.distanceToSqr(at) <= (radius + e.getBbWidth()) * (radius + e.getBbWidth())) hit.add(e);
        }
        for (LivingEntity e : hit) {
            if (!hurt(e, row.damage())) continue;
            if (row.knockback() > 0) e.knockback(row.knockback(), at.x - e.getX(), at.z - e.getZ());
            mark(sl, e, at);
        }
        burstFx(sl, at, hit);
        if (row.wellTicks() > 0) {
            wellAt = at;
            wellLeft = row.wellTicks();
            setDeltaMovement(Vec3.ZERO);
            setPos(at.x, at.y, at.z);
            entityData.set(DATA_WELL, true);
            return;
        }
        discard();
    }

    /** The Age's mark on one target. */
    private void mark(ServerLevel sl, LivingEntity e, Vec3 at) {
        switch (age()) {
            case BRONZE -> e.igniteForSeconds((float) row.num("fire_seconds", 0));
            case ARCANE -> {
                e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, row.ticks("levitation_seconds"), 0), this);
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, row.ticks("blindness_seconds"), 0), this);
            }
            case ELECTRIC -> chain(sl, e, row.chainHops());
            case INFORMATION -> {
                e.addEffect(new MobEffectInstance(MobEffects.HUNGER, row.ticks("hunger_seconds"), 0), this);
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, row.ticks("wither_seconds"), 0), this);
            }
            case QUANTUM -> {
                int freeze = row.ticks("freeze_seconds");
                // frozen ticks thaw two a tick: the target stays fully frozen for the row's seconds
                e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + freeze * 2));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, row.ticks("slowness_seconds"), 1), this);
            }
            default -> {}
        }
    }

    /** The Electric bolt jumps from {@code from} to the nearest player not yet struck, {@code hops} times. */
    private void chain(ServerLevel sl, LivingEntity from, int hops) {
        Set<Integer> done = new HashSet<>();
        done.add(from.getId());
        LivingEntity cur = from;
        for (int h = 0; h < hops; h++) {
            LivingEntity next = null;
            double best = row.chainRange() * row.chainRange();
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, cur.getBoundingBox().inflate(row.chainRange()), this::hittable)) {
                double d = e.distanceToSqr(cur);
                if (!done.contains(e.getId()) && d < best) {
                    best = d;
                    next = e;
                }
            }
            if (next == null) return;
            done.add(next.getId());
            Vec3 a = cur.position().add(0, cur.getBbHeight() / 2, 0), b = next.position().add(0, next.getBbHeight() / 2, 0);
            for (int k = 0; k <= 8; k++) {
                Vec3 p = a.lerp(b, k / 8.0);
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.02);
            }
            hurt(next, row.chainDamage());
            cur = next;
        }
    }

    private void burstFx(ServerLevel sl, Vec3 at, List<LivingEntity> hit) {
        sl.sendParticles(dust(1.6F), at.x, at.y + 0.2, at.z, 20, row.radius() * 0.5, 0.2, row.radius() * 0.5, 0.0);
        SoundEvent sound = SoundEvents.GENERIC_EXPLODE.value();
        float pitch = 1.0F;
        switch (age()) {
            case STONE -> {
                sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                pitch = 0.8F;
            }
            case BRONZE -> {
                sl.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 24, 0.6, 0.4, 0.6, 0.05);
                sound = SoundEvents.FIRECHARGE_USE;
            }
            case IRON -> sound = SoundEvents.ARROW_HIT;
            case ARCANE -> {
                sl.sendParticles(ParticleTypes.WITCH, at.x, at.y + 0.5, at.z, 20, 0.6, 0.6, 0.6, 0.1);
                sound = SoundEvents.ILLUSIONER_CAST_SPELL;
            }
            case INDUSTRIAL -> {
                AreaEffectCloud cloud = new AreaEffectCloud(sl, at.x, at.y, at.z);
                cloud.setOwner(getOwner() instanceof LivingEntity le ? le : null);
                cloud.setRadius((float) row.num("cloud_radius", 2.5));
                cloud.setDuration(row.ticks("cloud_seconds"));
                cloud.setRadiusOnUse(0);
                cloud.setRadiusPerTick(0);
                cloud.setParticle(ParticleTypes.CLOUD);
                int slow = (int) row.num("slowness", 1), poison = (int) row.num("poison", 0);
                if (slow > 0) cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, slow - 1));
                if (poison > 0) cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 40, poison - 1));
                sl.addFreshEntity(cloud);
                sound = SoundEvents.FIRE_EXTINGUISH;
                pitch = 0.6F;
            }
            case ELECTRIC -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
                if (bolt != null) {
                    bolt.moveTo(at.x, at.y, at.z);
                    bolt.setVisualOnly(true);
                    sl.addFreshEntity(bolt);
                }
                sound = SoundEvents.LIGHTNING_BOLT_IMPACT;
            }
            case INFORMATION -> {
                sl.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 0.5, at.z, 20, 0.5, 0.5, 0.5, 0.3);
                sound = SoundEvents.AMETHYST_BLOCK_BREAK;
                pitch = 1.4F;
            }
            case SPACE -> {
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.5, at.z, 30, 1.0, 0.5, 1.0, 0.05);
                sound = SoundEvents.PORTAL_TRIGGER;
                pitch = 1.6F;
            }
            case QUANTUM -> {
                for (int k = 0; k < 24; k++) {
                    double a = k * Math.PI / 12;
                    sl.sendParticles(ParticleTypes.SNOWFLAKE, at.x + Math.cos(a) * row.radius(), at.y + 0.2, at.z + Math.sin(a) * row.radius(), 1, 0, 0.05, 0, 0.01);
                }
                sound = SoundEvents.GLASS_BREAK;
                pitch = 0.7F;
            }
            case SINGULARITY -> {
                sl.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.5, at.z, 30, 0.8, 0.8, 0.8, 0.02);
                sound = SoundEvents.PORTAL_TRIGGER;
                pitch = 0.4F;
            }
        }
        sl.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, age() == ShotAge.STONE ? 1.2F : 0.8F, pitch);
    }

    // ---- gravity wells (Space, Singularity) -----------------------------------------------------------------------------

    private void wellStep(ServerLevel sl) {
        double r = row.wellRadius();
        boolean hole = age() == ShotAge.SINGULARITY;
        for (Entity e : sl.getEntities(this, new AABB(wellAt, wellAt).inflate(r), e -> pulled(e, hole))) {
            Vec3 d = wellAt.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double dist = d.length();
            if (dist > r || dist < 0.3) continue;
            Vec3 v = d.scale(row.pull() / dist);
            if (e instanceof LivingEntity) {
                e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(v));
                e.hurtMarked = true;
            } else {
                e.setDeltaMovement(e.getDeltaMovement().scale(0.5).add(v.scale(2)));
                if (hole && dist < 1.0) {
                    if (!(e instanceof Projectile p && p.getOwner() == getOwner())) e.discard(); // swallowed
                }
            }
        }
        if (life % 2 == 0) {
            double a = life * 0.4;
            sl.sendParticles(hole ? ParticleTypes.SQUID_INK : ParticleTypes.REVERSE_PORTAL, wellAt.x + Math.cos(a) * r * 0.6, wellAt.y + 0.5,
                wellAt.z + Math.sin(a) * r * 0.6, 3, 0.2, 0.3, 0.2, 0.0);
            sl.sendParticles(dust(1.2F), wellAt.x, wellAt.y + 0.5, wellAt.z, 2, 0.3, 0.3, 0.3, 0.0);
        }
        if (--wellLeft > 0) return;
        wellEnd(sl, hole);
        discard();
    }

    private boolean pulled(Entity e, boolean hole) {
        if (e instanceof LivingEntity) return hittable(e);
        if (!hole) return false;
        return e instanceof ItemEntity || (e instanceof Projectile && !(e instanceof StarFallEntity) && !(e instanceof AgeShotEntity));
    }

    private void wellEnd(ServerLevel sl, boolean hole) {
        double r = hole ? row.implosionRadius() : row.wellRadius(); // the shove reaches everyone the well was pulling
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(wellAt, wellAt).inflate(r), this::hittable)) {
            if (e.distanceToSqr(wellAt) > r * r) continue;
            if (hole) {
                hurt(e, row.implosionDamage());
            } else {
                e.knockback(row.push(), wellAt.x - e.getX(), wellAt.z - e.getZ());
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.4, 0));
                e.hurtMarked = true;
            }
        }
        if (hole) {
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, wellAt.x, wellAt.y + 0.5, wellAt.z, 2, 0.5, 0.5, 0.5, 0);
            sl.sendParticles(ParticleTypes.FLASH, wellAt.x, wellAt.y + 0.5, wellAt.z, 1, 0, 0, 0, 0);
            sl.playSound(null, wellAt.x, wellAt.y, wellAt.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F, 0.5F);
        } else {
            sl.sendParticles(ParticleTypes.SONIC_BOOM, wellAt.x, wellAt.y + 0.5, wellAt.z, 1, 0, 0, 0, 0);
            sl.playSound(null, wellAt.x, wellAt.y, wellAt.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.0F, 1.5F);
        }
    }

    // ---- persistence (the age by codec; a shot is never saved with the chunk) --------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        AGE_CODEC.encodeStart(NbtOps.INSTANCE, age()).result().ifPresent(t -> tag.put("Age", t));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        Tag t = tag.get("Age");
        if (t != null) AGE_CODEC.parse(NbtOps.INSTANCE, t).result().ifPresent(a -> setAge(a, BossShots.defaults()));
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
    public boolean isNoGravity() {
        return true;
    }
}
