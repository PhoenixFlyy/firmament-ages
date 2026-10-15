package dev.firmages.core.boss;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Primordial, the end boss of The Origin (docs/primordial.md): the dark before the first light, the shrine inverted.
 * One entity, no adds. Three phases by health (The Dark Before, Nine Lights, Firmament Shatter); in each, the damage
 * window (the open veil) is earned by an action the attack itself creates. Health stays at the attribute cap of 1024;
 * the damage it takes is scaled to an effective pool by the size of the Gathering ({@link #scaleDamage}), each attacker
 * has his own hurt cooldown, and a single hit never skips a phase. It carries {@link OriginService#FINAL_BOSS_TAG}
 * from birth, so its death wins the finale the existing way (OriginService grants finale_won).
 *
 * <p>Server state machine: {@link #fightStep} once per tick from {@code customServerAiStep} (frozen with NoAI, which
 * the GameTests use to drive it by hand). Synched to clients: phase, ability, veil, lit node and the beam's angle; the
 * pose follows from those, GeckoLib's triggered animation only adds the hurt flinch.
 */
public class PrimordialEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(PrimordialEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ABILITY = SynchedEntityData.defineId(PrimordialEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_VEIL = SynchedEntityData.defineId(PrimordialEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_NODE = SynchedEntityData.defineId(PrimordialEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_BEAM = SynchedEntityData.defineId(PrimordialEntity.class, EntityDataSerializers.FLOAT);

    public static final float NO_BEAM = -1000.0F;
    public static final float MAX_HEALTH = 1024.0F;

    // ---- tuning (docs/primordial.md, "Numbers"); the per-world knobs are ServerConfig [boss] ----------------------------
    /** Ticks one attacker waits between two hits that count (per attacker, not shared). */
    public static final int IFRAMES = 10;
    public static final int SLAM_IMPACT = 30, SLAM_REST_END = 70;
    public static final int VOLLEY_FIRE = 20;
    public static final int BEAM_START = 20;
    public static final int PULL_START = 10, PULL_END = 50, PULL_IMPACT = 65, PULL_REST_END = 85;
    public static final int SHATTER_PEAK = 30;
    public static final int PHASE2_LOCK = 20, PHASE3_LOCK = 40;
    public static final int VEIL_MELEE = 120, VEIL_RANGED = 80, VEIL_SHARD = 160;
    public static final int DEATH_TICKS = 60;
    public static final int SHARDS_PER_SHATTER = 6;
    /** Consecrated shards on the floor vanish after 60 s; picked-up ones stay (keepsakes). */
    public static final int SHARD_LIFESPAN = 1200;
    static final int GAP = 30;
    static final int[] COOLDOWN = {0, 240, 160, 300, 400, 500};
    static final float SLAM_DAMAGE = 18, SLAM_RADIUS = 5, VOLLEY_DAMAGE = 10, BEAM_DAMAGE = 8, STAR_DAMAGE = 14;
    /** Beyond this horizontal distance from home (a /tp, a push) it returns at once. */
    public static final double LEASH = 24;
    static final double DRIFT_LIMIT = 10, ARENA = 48, PULL_RANGE = 24;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SPLIT = RawAnimation.begin().thenLoop("split");
    private static final RawAnimation DIE = RawAnimation.begin().thenPlayAndHold("die");
    private static final RawAnimation VEIL_OPENED = RawAnimation.begin().thenPlayAndHold("veil_open");
    private static final RawAnimation VEIL_CLOSED = RawAnimation.begin().thenPlayAndHold("veil_close");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("hurt");
    private static final RawAnimation[] ABILITY_ANIMS = new RawAnimation[PrimordialAbility.values().length];

    static {
        for (PrimordialAbility a : PrimordialAbility.values()) {
            if (a != PrimordialAbility.NONE) ABILITY_ANIMS[a.ordinal()] = RawAnimation.begin().thenPlay(a.animation);
        }
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.firmages.primordial"),
        BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_6);

    // ---- server state ---------------------------------------------------------------------------------------------------
    private boolean initialised;
    private Vec3 home = Vec3.ZERO;
    private double floorY;
    private int partySize;
    private int fightTicks;
    private int phaseLock;
    private int veilTicks;
    private PrimordialAbility ability = PrimordialAbility.NONE;
    private int abilityTick;
    private final int[] nextReady = new int[PrimordialAbility.values().length];
    private int globalReady;
    private final Map<UUID, Integer> lastHit = new HashMap<>();
    private final List<BlockPos> darkened = new ArrayList<>();
    private int starTimer;
    private int lonelyTicks;
    private double orbit;
    private float beamStart;
    private int beamDir = 1;

    /** Client: the halo tilt eases toward the phase's tilt (PrimordialModel). */
    public float clientTilt = Float.NaN;

    public PrimordialEntity(EntityType<? extends PrimordialEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.xpReward = 0;
        if (!level.isClientSide) this.addTag(OriginService.FINAL_BOSS_TAG);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.ARMOR, 12.0)
            .add(Attributes.ARMOR_TOUGHNESS, 8.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.FOLLOW_RANGE, 64.0)
            .add(Attributes.MOVEMENT_SPEED, 0.2)
            .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 1);
        builder.define(DATA_ABILITY, 0);
        builder.define(DATA_VEIL, false);
        builder.define(DATA_NODE, -1);
        builder.define(DATA_BEAM, NO_BEAM);
    }

    @Override
    protected void registerGoals() {}

    // ---- read access (renderer, GameTests) ------------------------------------------------------------------------------

    public int phase() {
        return entityData.get(DATA_PHASE);
    }

    public PrimordialAbility ability() {
        return PrimordialAbility.byId(entityData.get(DATA_ABILITY));
    }

    public boolean veilOpen() {
        return entityData.get(DATA_VEIL);
    }

    /** The node that charges, washes or casts the beam, -1 for none. */
    public int litNode() {
        return entityData.get(DATA_NODE);
    }

    /** The beam's yaw on the floor in degrees, {@link #NO_BEAM} while no beam burns. */
    public float beamYaw() {
        return entityData.get(DATA_BEAM);
    }

    public int abilityTick() {
        return abilityTick;
    }

    public int veilTicks() {
        return veilTicks;
    }

    public int partySize() {
        return partySize;
    }

    public Vec3 home() {
        return home;
    }

    public double floorY() {
        return floorY;
    }

    public int phaseLock() {
        return phaseLock;
    }

    public List<BlockPos> darkened() {
        return List.copyOf(darkened);
    }

    /** The Gathering's size (BossEvents, or NBT {@code PartySize}); unset, the players present at birth. */
    public void setPartySize(int n) {
        partySize = Math.max(1, n);
    }

    /** Effective health the damage is scaled to: effectiveHealth * ((1 - f) + f * players). */
    public double effectivePool() {
        double f = ServerConfig.bossPerPlayer();
        return ServerConfig.bossEffectiveHealth() * ((1 - f) + f * Math.max(1, partySize));
    }

    /** Factor on damage after armour: 1024 / pool, times the veil factor while the veil is closed. */
    public float damageFactor() {
        return (float) (getMaxHealth() / effectivePool() * (veilOpen() ? 1.0 : ServerConfig.bossVeilFactor()));
    }

    /** Health below which the current phase ends (2/3, 1/3, 0 of the maximum). */
    public float phaseFloor() {
        return switch (phase()) {
            case 1 -> getMaxHealth() * 2 / 3F;
            case 2 -> getMaxHealth() / 3F;
            default -> 0;
        };
    }

    /** The damage this boss takes, after armour (BossEvents, LivingDamageEvent.Pre): scaled, never past the phase floor. */
    public float scaleDamage(DamageSource source, float afterArmour) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return afterArmour;
        float d = afterArmour * damageFactor();
        if (phase() < 3) d = Math.min(d, Math.max(0, getHealth() - phaseFloor()));
        return d;
    }

    boolean enraged() {
        return fightTicks >= ServerConfig.bossEnrageMinutes() * 1200;
    }

    boolean starfallEnraged() {
        return fightTicks >= ServerConfig.bossStarfallEnrageMinutes() * 1200;
    }

    // ---- lifecycle ------------------------------------------------------------------------------------------------------

    /** Home at the altar (in The Origin) or where it was summoned, the tag, the party size, the waking title. Idempotent. */
    public void initFight() {
        if (initialised || !(level() instanceof ServerLevel sl)) return;
        initialised = true;
        if (ArenaLights.applies(sl)) {
            BlockPos a = OriginArena.ALTAR;
            floorY = a.getY();
            home = new Vec3(a.getX() + 0.5, floorY + PrimordialGeometry.HOVER, a.getZ() + 0.5);
        } else {
            floorY = blockPosition().getY();
            home = new Vec3(getX(), floorY + PrimordialGeometry.HOVER, getZ());
        }
        addTag(OriginService.FINAL_BOSS_TAG);
        if (partySize <= 0) partySize = Math.max(1, nearbyPlayers(false).size());
        nextReady[PrimordialAbility.NODE_VOLLEY.ordinal()] = 60;
        nextReady[PrimordialAbility.KEEL_SLAM.ordinal()] = 100;
        title(Component.translatable("entity.firmages.primordial").withStyle(ChatFormatting.DARK_PURPLE),
            Component.translatable("firmages.boss.primordial.wake").withStyle(ChatFormatting.GRAY), null);
        sound(BossRegistry.IDLE.get(), 2.0F, 1.0F);
        FirmagesCore.LOGGER.info("The Primordial wakes at {} in {} for a Gathering of {} (effective health {})", BlockPos.containing(home),
            sl.dimension().location(), partySize, Math.round(effectivePool()));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (!initialised) initFight();
            bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0, 1));
        }
        // The body never turns on its own: the model, the node hit tests and the beam all use the head yaw.
        yBodyRot = getYRot();
        yBodyRotO = yRotO;
        yHeadRot = getYRot();
        yHeadRotO = yRotO;
    }

    @Override
    protected void customServerAiStep() {
        fightStep();
    }

    /** One tick of the fight (public for the GameTests, which freeze the AI and step by hand). */
    public void fightStep() {
        if (!(level() instanceof ServerLevel sl) || isDeadOrDying()) return;
        if (!initialised) initFight();
        fightTicks++;
        if (phaseLock > 0) phaseLock--;
        if (veilTicks > 0 && --veilTicks == 0) closeVeil();
        if (fightTicks % 200 == 0) lastHit.values().removeIf(t -> tickCount - t > IFRAMES);
        if (leash()) return;
        if (wipe(sl)) return;
        stepAbility(sl);
        stepStarfall(sl);
        move();
        turn();
        ambient(sl);
    }

    /** Back home at once when dragged beyond {@link #LEASH} (horizontal) or 12 blocks off its floor. @return true if it jumped */
    private boolean leash() {
        double dx = getX() - home.x, dz = getZ() - home.z;
        if (dx * dx + dz * dz <= LEASH * LEASH && Math.abs(getY() - floorY) <= 12) return false;
        endAbility();
        setPos(home.x, home.y, home.z);
        if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.REVERSE_PORTAL, home.x, home.y + 3, home.z, 40, 2, 2, 2, 0.05);
        return true;
    }

    /** No living player within 48 blocks for {@code boss.wipeSeconds}: the dark withdraws and the lanterns return. */
    private boolean wipe(ServerLevel sl) {
        if (!nearbyPlayers(false).isEmpty()) {
            lonelyTicks = 0;
            return false;
        }
        if (++lonelyTicks < ServerConfig.bossWipeSeconds() * 20) return false;
        FirmagesCore.LOGGER.info("The Primordial: no player near it for {} s; it withdraws (wipe), the arena relights", ServerConfig.bossWipeSeconds());
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 3, getZ(), 120, 2, 3, 2, 0.1);
        clearStars(sl);
        discard();
        return true;
    }

    // ---- abilities ------------------------------------------------------------------------------------------------------

    private boolean ready(PrimordialAbility a) {
        return fightTicks >= nextReady[a.ordinal()];
    }

    private void stepAbility(ServerLevel sl) {
        if (ability == PrimordialAbility.NONE) {
            if (phaseLock == 0 && fightTicks >= globalReady) pickAbility();
            return;
        }
        int t = ++abilityTick;
        switch (ability) {
            case KEEL_SLAM -> slamStep(sl, t, SLAM_IMPACT, SLAM_REST_END);
            case NODE_VOLLEY -> volleyStep(sl, t);
            case BEAM_SWEEP -> beamStep(sl, t);
            case PULL -> pullStep(sl, t);
            case SHATTER -> shatterStep(sl, t);
            default -> {}
        }
        if (ability != PrimordialAbility.NONE && abilityTick >= ability.length) endAbility();
    }

    private void pickAbility() {
        boolean anyone = !targets().isEmpty();
        switch (phase()) {
            case 1 -> {
                if (!anyone) return;
                if (ready(PrimordialAbility.KEEL_SLAM)) startAbility(PrimordialAbility.KEEL_SLAM);
                else if (ready(PrimordialAbility.NODE_VOLLEY)) startAbility(PrimordialAbility.NODE_VOLLEY);
            }
            case 2 -> {
                if (!anyone) return;
                if (ready(PrimordialAbility.PULL)) startAbility(PrimordialAbility.PULL);
                else if (ready(PrimordialAbility.BEAM_SWEEP)) startAbility(PrimordialAbility.BEAM_SWEEP);
            }
            default -> {
                if (ready(PrimordialAbility.SHATTER)) startAbility(PrimordialAbility.SHATTER);
            }
        }
    }

    /** Starts an ability now (public for the GameTests and operators' debugging). */
    public void startAbility(PrimordialAbility a) {
        if (a == PrimordialAbility.NONE) {
            endAbility();
            return;
        }
        ability = a;
        abilityTick = 0;
        entityData.set(DATA_ABILITY, a.ordinal());
        nextReady[a.ordinal()] = fightTicks + COOLDOWN[a.ordinal()];
        switch (a) {
            case KEEL_SLAM -> sound(BossRegistry.SLAM_TELEGRAPH.get(), 3.0F, 0.7F);
            case NODE_VOLLEY -> {
                entityData.set(DATA_NODE, random.nextInt(9));
                sound(BossRegistry.CHARGE.get(), 2.0F, 1.2F);
            }
            case BEAM_SWEEP -> {
                entityData.set(DATA_NODE, random.nextInt(9));
                beamDir = random.nextBoolean() ? 1 : -1;
                Player p = nearestTarget();
                float toward = p == null ? random.nextFloat() * 360 : (float) Math.toDegrees(Math.atan2(p.getZ() - getZ(), p.getX() - getX()));
                beamStart = toward - 90 * beamDir;
                sound(BossRegistry.CHARGE.get(), 2.0F, 0.8F);
            }
            case PULL -> sound(BossRegistry.PULL.get(), 3.0F, 0.8F);
            default -> {}
        }
    }

    private void endAbility() {
        ability = PrimordialAbility.NONE;
        abilityTick = 0;
        entityData.set(DATA_ABILITY, 0);
        entityData.set(DATA_NODE, -1);
        entityData.set(DATA_BEAM, NO_BEAM);
        globalReady = fightTicks + GAP;
    }

    private void slamStep(ServerLevel sl, int t, int impact, int restEnd) {
        if (t == impact) slamImpact(sl);
        if (t > impact && t < restEnd && t % 4 == 0) {
            sl.sendParticles(StarFallEntity.GOLD_DUST, getX(), floorY + 0.2, getZ(), 6, 1.0, 0.1, 1.0, 0.0);
        }
    }

    private void slamImpact(ServerLevel sl) {
        Vec3 c = new Vec3(getX(), floorY, getZ());
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(SLAM_RADIUS, 3, SLAM_RADIUS), this::hittable)) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            if (dx * dx + dz * dz > SLAM_RADIUS * SLAM_RADIUS) continue;
            if (e.hurt(damageSources().mobAttack(this), SLAM_DAMAGE)) e.knockback(1.2, c.x - e.getX(), c.z - e.getZ());
        }
        for (int k = 0; k < 32; k++) {
            double a = k * Math.PI / 16;
            sl.sendParticles(StarFallEntity.GOLD_DUST, c.x + Math.cos(a) * SLAM_RADIUS, c.y + 0.2, c.z + Math.sin(a) * SLAM_RADIUS, 1, 0, 0.05, 0, 0);
        }
        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        sound(BossRegistry.SLAM.get(), 3.0F, 0.6F);
    }

    private void volleyStep(ServerLevel sl, int t) {
        int node = litNode();
        if (node < 0) return;
        Vec3 n = nodeWorld(node);
        if (t < VOLLEY_FIRE && t % 2 == 0) sl.sendParticles(ParticleTypes.END_ROD, n.x, n.y, n.z, 3, 0.15, 0.15, 0.15, 0.01);
        if (t != VOLLEY_FIRE) return;
        List<Player> tg = targets();
        if (tg.isEmpty()) return;
        tg.sort(Comparator.comparingDouble(this::distanceToSqr));
        for (int k = 0; k < 3; k++) {
            Player p = tg.get(k % tg.size());
            Vec3 to = p.getEyePosition();
            if (k >= tg.size()) to = to.add((random.nextDouble() - 0.5) * 3, 0, (random.nextDouble() - 0.5) * 3);
            StarFallEntity.bolt(sl, this, n, to, PrimordialGeometry.NODE_COLORS[node], VOLLEY_DAMAGE);
        }
        sound(BossRegistry.STAR_FALL_IMPACT.get(), 1.0F, 1.8F);
    }

    private void beamStep(ServerLevel sl, int t) {
        int node = litNode();
        if (node < 0) return;
        int c = PrimordialGeometry.NODE_COLORS[node];
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(FastColor.ARGB32.red(c) / 255F, FastColor.ARGB32.green(c) / 255F,
            FastColor.ARGB32.blue(c) / 255F), 1.5F);
        if (t < BEAM_START) {
            Vec3 n = nodeWorld(node);
            if (t % 2 == 0) sl.sendParticles(dust, n.x, n.y, n.z, 4, 0.3, 0.3, 0.3, 0.0);
            return;
        }
        if (t == BEAM_START) sound(BossRegistry.BEAM.get(), 3.0F, 1.0F);
        float a = beamStart + beamDir * 180.0F * (t - BEAM_START) / (PrimordialAbility.BEAM_SWEEP.length - BEAM_START);
        entityData.set(DATA_BEAM, a);
        Vec3 n = nodeWorld(node);
        Vec3 f = position().add(PrimordialGeometry.beamTarget(a));
        if (t % 2 == 0) sl.sendParticles(dust, f.x, floorY + 0.1, f.z, 3, 0.3, 0.05, 0.3, 0.0);
        if ((t - BEAM_START) % 10 != 0) return;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(n, f).inflate(1.5), this::hittable)) {
            if (segmentDistance(e.getBoundingBox().getCenter(), n, f) > 0.8 + e.getBbWidth() / 2) continue;
            if (e.hurt(damageSources().mobAttack(this), BEAM_DAMAGE)) e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0), this);
        }
    }

    private void pullStep(ServerLevel sl, int t) {
        if (t >= PULL_START && t < PULL_END) {
            for (Player p : targets()) {
                double dx = getX() - p.getX(), dz = getZ() - p.getZ();
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > PULL_RANGE || d < 1.0) continue;
                double s = Math.min(0.5, d * 0.12) / d;
                p.setDeltaMovement(dx * s, p.getDeltaMovement().y, dz * s);
                p.hurtMarked = true;
                if (t % 3 == 0) sl.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 4, 0.3, 0.5, 0.3, 0.02);
            }
        }
        slamStep(sl, t, PULL_IMPACT, PULL_REST_END);
    }

    private void shatterStep(ServerLevel sl, int t) {
        if (t != SHATTER_PEAK) return;
        sound(BossRegistry.SHATTER.get(), 4.0F, 0.5F);
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(k * 45);
            StarFallEntity.fall(sl, this, home.x + Math.cos(a) * 10, floorY, home.z + Math.sin(a) * 10, PrimordialGeometry.NODE_COLORS[k], STAR_DAMAGE);
        }
        Player p = nearestTarget();
        StarFallEntity.fall(sl, this, p != null ? p.getX() : home.x, floorY, p != null ? p.getZ() : home.z, PrimordialGeometry.NODE_COLORS[8], STAR_DAMAGE);
        for (int k = 0; k < SHARDS_PER_SHATTER; k++) {
            double a = random.nextDouble() * Math.PI * 2, d = 3 + random.nextDouble() * 6;
            ItemEntity shard = new ItemEntity(sl, home.x + Math.cos(a) * d, floorY + 0.5, home.z + Math.sin(a) * d,
                new ItemStack(BossRegistry.CONSECRATED_SHARD.get()), 0, 0.15, 0);
            shard.lifespan = SHARD_LIFESPAN;
            shard.setPickUpDelay(10);
            sl.addFreshEntity(shard);
        }
    }

    /** Star-fall: every 2 s in phase 3; after {@code boss.starfallEnrageMinutes} every second in every phase. */
    private void stepStarfall(ServerLevel sl) {
        boolean late = starfallEnraged();
        if ((phase() < 3 && !late) || phaseLock > 0) return;
        if (++starTimer < (late ? 20 : 40)) return;
        starTimer = 0;
        List<Player> tg = targets();
        if (tg.isEmpty()) return;
        Player p = tg.get(random.nextInt(tg.size()));
        double x = p.getX() + (random.nextDouble() - 0.5) * 6, z = p.getZ() + (random.nextDouble() - 0.5) * 6;
        double dx = x - home.x, dz = z - home.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d > OriginArena.RADIUS - 1) {
            x = home.x + dx * (OriginArena.RADIUS - 1) / d;
            z = home.z + dz * (OriginArena.RADIUS - 1) / d;
        }
        StarFallEntity.fall(sl, this, x, floorY, z, PrimordialGeometry.NODE_COLORS[random.nextInt(9)], STAR_DAMAGE);
    }

    // ---- movement -------------------------------------------------------------------------------------------------------

    private double targetHeight() {
        int t = abilityTick;
        double h = PrimordialGeometry.HOVER;
        return switch (ability) {
            case KEEL_SLAM -> slamHeight(t, 0, SLAM_IMPACT, SLAM_REST_END, PrimordialAbility.KEEL_SLAM.length);
            case PULL -> t < PULL_END ? h : slamHeight(t, PULL_END, PULL_IMPACT, PULL_REST_END, PrimordialAbility.PULL.length);
            case SHATTER -> t <= SHATTER_PEAK ? h + 3.0 * t / SHATTER_PEAK : h + 3.0 * (PrimordialAbility.SHATTER.length - t) / (PrimordialAbility.SHATTER.length - SHATTER_PEAK);
            default -> h;
        };
    }

    /** Wind-up (rises half a block), drop in 8 ticks, keel on the floor until restEnd, then back to hover height. */
    static double slamHeight(int t, int start, int impact, int restEnd, int end) {
        double h = PrimordialGeometry.HOVER;
        int drop = impact - 8;
        if (t < drop) return h + 0.5 * Math.max(0, t - start) / Math.max(1, drop - start);
        if (t < impact) return (h + 0.5) * (impact - t) / 8.0;
        if (t < restEnd) return 0;
        return h * Math.min(1.0, (t - restEnd) / (double) (end - restEnd));
    }

    private void move() {
        double tx, tz, speed;
        switch (ability) {
            case NONE -> {
                orbit += 0.004;
                tx = home.x + Math.cos(orbit) * 4;
                tz = home.z + Math.sin(orbit) * 4;
                speed = 0.06;
            }
            case KEEL_SLAM -> {
                Player p = abilityTick < SLAM_IMPACT - 8 ? nearestTarget() : null;
                tx = p != null ? p.getX() : getX();
                tz = p != null ? p.getZ() : getZ();
                double dx = tx - home.x, dz = tz - home.z, d = Math.sqrt(dx * dx + dz * dz);
                if (d > DRIFT_LIMIT) {
                    tx = home.x + dx * DRIFT_LIMIT / d;
                    tz = home.z + dz * DRIFT_LIMIT / d;
                }
                speed = 0.2;
            }
            case NODE_VOLLEY -> {
                tx = getX();
                tz = getZ();
                speed = 0;
            }
            default -> {
                tx = home.x;
                tz = home.z;
                speed = 0.15;
            }
        }
        double dx = tx - getX(), dz = tz - getZ(), d = Math.sqrt(dx * dx + dz * dz);
        if (d > speed) {
            dx = d < 1.0E-6 ? 0 : dx * speed / d;
            dz = d < 1.0E-6 ? 0 : dz * speed / d;
        }
        double dy = Mth.clamp(floorY + targetHeight() - getY(), -0.6, 0.6);
        setPos(getX() + dx, getY() + dy, getZ() + dz);
    }

    private void turn() {
        Player p = nearestTarget();
        if (p == null) return;
        float want = (float) (Mth.atan2(p.getZ() - getZ(), p.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(Mth.approachDegrees(getYRot(), want, 2.0F));
    }

    private void ambient(ServerLevel sl) {
        if (fightTicks % 80 == 0) sound(BossRegistry.IDLE.get(), 1.5F, 1.0F);
        if (fightTicks % 10 == 0) sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 7.6, getZ(), 1, 0.05, 0.05, 0.05, 0.01);
        if (phase() >= 3 && fightTicks % 4 == 0 && ArenaLights.applies(sl)) {
            // the gold cracks along the rings (d 4 and d 10) and the spokes: the only light left
            for (int k = 0; k < 3; k++) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = switch (random.nextInt(3)) {
                    case 0 -> 4;
                    case 1 -> 10;
                    default -> 4 + random.nextDouble() * 6;
                };
                if (r != 4 && r != 10) a = Math.round(a / (Math.PI / 4)) * (Math.PI / 4);
                sl.sendParticles(StarFallEntity.GOLD_DUST, home.x + Math.cos(a) * r, floorY + 0.05, home.z + Math.sin(a) * r, 1, 0.1, 0, 0.1, 0);
            }
        }
    }

    // ---- damage ---------------------------------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (isDeadOrDying() || isInvulnerableTo(source) || phaseLock > 0 || ability == PrimordialAbility.SHATTER) return false;
        Entity attacker = source.getEntity();
        // Only what someone does to it counts: no fall, fire, drowning, lightning or other bystanders' harm.
        if (attacker == null || attacker == this) return false;
        if (!initialised) initFight();
        Integer last = lastHit.get(attacker.getUUID());
        if (last != null && tickCount - last < IFRAMES) return false;
        counterplay(source, attacker);
        invulnerableTime = 0;
        boolean hurt = super.hurt(source, amount);
        invulnerableTime = 0;
        if (hurt) {
            lastHit.put(attacker.getUUID(), tickCount);
            triggerAnim("hurt", "hurt");
            checkPhase();
        }
        return hurt;
    }

    /** Opens the veil when the hit is the counter of the running attack (docs/primordial.md, phase table). */
    private void counterplay(DamageSource source, Entity attacker) {
        Entity direct = source.getDirectEntity();
        boolean melee = direct == attacker && attacker instanceof LivingEntity && !source.is(DamageTypeTags.IS_PROJECTILE);
        if (phase() >= 3) {
            if (melee && attacker instanceof LivingEntity le && le.getOffhandItem().is(BossRegistry.CONSECRATED_SHARD.get())) {
                if (!(le instanceof Player pl && pl.getAbilities().instabuild)) le.getOffhandItem().shrink(1);
                openVeil(VEIL_SHARD);
            }
            return;
        }
        int t = abilityTick;
        if (melee) {
            boolean keelDown = (ability == PrimordialAbility.KEEL_SLAM && t >= SLAM_IMPACT && t < SLAM_REST_END)
                || (ability == PrimordialAbility.PULL && t >= PULL_IMPACT && t < PULL_REST_END);
            boolean under = ability == PrimordialAbility.PULL && t >= PULL_START && t < PULL_IMPACT
                && Math.hypot(attacker.getX() - getX(), attacker.getZ() - getZ()) <= 2.5;
            if (keelDown) {
                openVeil(VEIL_MELEE);
            } else if (under) {
                openVeil(VEIL_MELEE); // struck from below before the slam: it never lands
                endAbility();
            }
        } else if (direct != null && direct != attacker) {
            int node = litNode();
            boolean exposed = (ability == PrimordialAbility.NODE_VOLLEY && t < VOLLEY_FIRE) || (ability == PrimordialAbility.BEAM_SWEEP && t >= BEAM_START);
            if (exposed && node >= 0) {
                Vec3 n = nodeWorld(node);
                if (PrimordialGeometry.pathHits(n, direct.position(), direct.getDeltaMovement())) {
                    if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.FLASH, n.x, n.y, n.z, 1, 0, 0, 0, 0);
                    openVeil(VEIL_RANGED);
                    endAbility();
                }
            }
        }
    }

    /** A thrown consecrated shard struck it: in phase 3 the veil opens. @return false before phase 3 (the shard drops) */
    public boolean shardStruck(@Nullable Entity thrower) {
        if (phase() < 3 || isDeadOrDying()) return false;
        openVeil(VEIL_SHARD);
        return true;
    }

    /** Opens the veil for {@code ticks} (half after {@code boss.enrageMinutes}); never shortens an open veil. */
    public void openVeil(int ticks) {
        int d = enraged() ? ticks / 2 : ticks;
        boolean was = veilOpen();
        veilTicks = Math.max(veilTicks, d);
        entityData.set(DATA_VEIL, true);
        if (!was) sound(BossRegistry.VEIL_OPEN.get(), 2.0F, 1.0F);
    }

    private void closeVeil() {
        veilTicks = 0;
        if (!veilOpen()) return;
        entityData.set(DATA_VEIL, false);
        sound(BossRegistry.VEIL_CLOSE.get(), 2.0F, 1.0F);
    }

    private void checkPhase() {
        float max = getMaxHealth(), h = getHealth();
        if (phase() == 1 && h <= max * 2 / 3F + 0.01F) enterPhase(2);
        else if (phase() == 2 && h <= max / 3F + 0.01F) enterPhase(3);
    }

    /** Enters phase 2 (Nine Lights: lanterns out) or 3 (Firmament Shatter); public for the GameTests. */
    public void enterPhase(int p) {
        if (!(level() instanceof ServerLevel sl) || p <= phase() || p > 3) return;
        entityData.set(DATA_PHASE, p);
        endAbility();
        starTimer = 0;
        if (darkened.isEmpty()) darkened.addAll(ArenaLights.extinguish(sl));
        if (p == 2) {
            phaseLock = PHASE2_LOCK;
            nextReady[PrimordialAbility.BEAM_SWEEP.ordinal()] = fightTicks + 60;
            nextReady[PrimordialAbility.PULL.ordinal()] = fightTicks + 200;
            sound(BossRegistry.LANTERNS_OUT.get(), 4.0F, 0.8F);
            title(Component.translatable("firmages.boss.primordial.phase.2").withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.translatable("firmages.boss.primordial.voice.nine_lights"), "firmages.boss.primordial.voice.nine_lights");
        } else {
            phaseLock = PHASE3_LOCK;
            nextReady[PrimordialAbility.SHATTER.ordinal()] = fightTicks + PHASE3_LOCK + 20;
            sound(BossRegistry.SHATTER.get(), 4.0F, 0.7F);
            title(Component.translatable("firmages.boss.primordial.phase.3").withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.translatable("firmages.boss.primordial.voice.shatter"), "firmages.boss.primordial.voice.shatter");
        }
        FirmagesCore.LOGGER.info("The Primordial: phase {} at {} health after {} s", p, Math.round(getHealth()), fightTicks / 20);
    }

    // ---- death and removal ----------------------------------------------------------------------------------------------

    @Override
    public void die(DamageSource source) {
        super.die(source); // LivingDeathEvent: OriginService grants finale_won through the tag
        if (!dead || !(level() instanceof ServerLevel sl)) return;
        endAbility();
        veilTicks = 0;
        entityData.set(DATA_VEIL, false);
        relight(sl);
        clearStars(sl);
        bossEvent.setProgress(0);
        sound(BossRegistry.DEATH.get(), 5.0F, 0.5F);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel sl) {
            if (deathTime % 3 == 0) sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 3.5, getZ(), 6, 1.2, 1.2, 1.2, 0.04);
            if (deathTime == DEATH_TICKS - 10) sound(BossRegistry.DEATH_STAR.get(), 4.0F, 1.0F);
            if (deathTime >= DEATH_TICKS && !isRemoved()) {
                sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 7.5, getZ(), 30, 0.1, 2.0, 0.1, 0.2);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl && reason.shouldDestroy()) {
            relight(sl);
            clearStars(sl);
            bossEvent.removeAllPlayers();
        }
        super.remove(reason);
    }

    private void relight(ServerLevel sl) {
        if (darkened.isEmpty()) return;
        ArenaLights.relight(sl, darkened);
        darkened.clear();
    }

    /** Its own star-falls and bolts go with it. */
    private void clearStars(ServerLevel sl) {
        for (StarFallEntity s : sl.getEntitiesOfClass(StarFallEntity.class, getBoundingBox().inflate(64), s -> s.getOwner() == this || s.getOwner() == null)) {
            s.discard();
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------------------

    /** Node {@code i}'s centre in the world now. */
    public Vec3 nodeWorld(int i) {
        return position().add(PrimordialGeometry.nodeOffset(i, getYRot(), PrimordialGeometry.tilt(phase()), PrimordialGeometry.spin(level().getGameTime())));
    }

    private boolean hittable(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof PrimordialEntity) && !(e instanceof Player p && (p.isSpectator() || p.isCreative()));
    }

    /** Players within 48 blocks of home; with {@code fighting}, only those in survival or adventure (the ones it attacks). */
    private List<Player> nearbyPlayers(boolean fighting) {
        List<Player> out = new ArrayList<>();
        for (Player p : level().players()) {
            if (!p.isAlive() || p.isSpectator() || (fighting && p.isCreative())) continue;
            if (p.distanceToSqr(home.x, p.getY(), home.z) <= ARENA * ARENA && Math.abs(p.getY() - floorY) < 32) out.add(p);
        }
        return out;
    }

    private List<Player> targets() {
        return nearbyPlayers(true);
    }

    @Nullable
    private Player nearestTarget() {
        Player best = null;
        double bd = Double.MAX_VALUE;
        for (Player p : targets()) {
            double d = distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    static double segmentDistance(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double t = len2 < 1.0E-9 ? 0 : Mth.clamp(p.subtract(a).dot(ab) / len2, 0, 1);
        return a.add(ab.scale(t)).distanceTo(p);
    }

    private void sound(SoundEvent e, float volume, float pitch) {
        level().playSound(null, getX(), getY() + 3, getZ(), e, SoundSource.HOSTILE, volume, pitch);
    }

    /** Title and subtitle for everyone near the arena; with a voice key also Caelum's chat line, as in the ceremonies. */
    private void title(Component title, Component subtitle, @Nullable String voiceKey) {
        if (!(level() instanceof ServerLevel sl)) return;
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(home.x, p.getY(), home.z) > 64 * 64) continue;
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 30, 10));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle.copy().withStyle(ChatFormatting.GRAY)));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            if (voiceKey != null) {
                p.sendSystemMessage(Component.translatable("firmages.ceremony.voice", Component.translatable(voiceKey)).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    // ---- boss bar, persistence, immunities ------------------------------------------------------------------------------

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", phase());
        tag.putInt("PartySize", partySize);
        tag.putInt("FightTicks", fightTicks);
        tag.putInt("VeilTicks", veilTicks);
        tag.putBoolean("Initialised", initialised);
        if (initialised) {
            tag.putDouble("HomeX", home.x);
            tag.putDouble("HomeY", home.y);
            tag.putDouble("HomeZ", home.z);
            tag.putDouble("FloorY", floorY);
        }
        tag.put("Darkened", new LongArrayTag(darkened.stream().mapToLong(BlockPos::asLong).toArray()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("PartySize")) partySize = Math.max(0, tag.getInt("PartySize"));
        if (tag.contains("Phase")) entityData.set(DATA_PHASE, Mth.clamp(tag.getInt("Phase"), 1, 3));
        fightTicks = tag.getInt("FightTicks");
        veilTicks = tag.getInt("VeilTicks");
        entityData.set(DATA_VEIL, veilTicks > 0);
        if (tag.getBoolean("Initialised") && tag.contains("HomeX")) {
            initialised = true;
            home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
            floorY = tag.getDouble("FloorY");
        }
        darkened.clear();
        for (long l : tag.getLongArray("Darkened")) darkened.add(BlockPos.of(l));
    }

    @Override
    public void checkDespawn() {}

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return effect.is(MobEffects.GLOWING);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public void push(Entity entity) {}

    @Override
    public void push(double x, double y, double z) {}

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void travel(Vec3 input) {}

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(10, 3, 10);
    }

    // ---- GeckoLib ------------------------------------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 10,
            s -> s.setAndContinue(isDeadOrDying() ? DIE : phase() >= 3 ? SPLIT : IDLE)));
        controllers.add(new AnimationController<>(this, "veil", 2, s -> s.setAndContinue(veilOpen() ? VEIL_OPENED : VEIL_CLOSED)));
        controllers.add(new AnimationController<>(this, "hurt", 0, s -> PlayState.STOP).triggerableAnim("hurt", HURT));
        controllers.add(new AnimationController<>(this, "ability", 4, s -> {
            PrimordialAbility a = ability();
            if (a == PrimordialAbility.NONE || isDeadOrDying()) return PlayState.STOP;
            return s.setAndContinue(ABILITY_ANIMS[a.ordinal()]);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
