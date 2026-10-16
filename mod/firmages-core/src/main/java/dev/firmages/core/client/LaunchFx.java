package dev.firmages.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.launch.LaunchRules;
import dev.firmages.core.launch.ProbeCradleBlockEntity;
import dev.firmages.core.launch.TrenchShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The launch spectacle on the client (SPEC §31.7, no GeckoLib, no entity): the probe's item model at scale 3 standing on
 * the cradle, climbing 300 blocks with ease-in on a trail of vanilla particles, flame jets out of the trench mouth, and
 * the fall home under three white canopies. The state per cradle comes from {@code firmages:launch_fx} and from the
 * cradle's block-entity sync; the cradle's renderer ({@link LaunchRenderer}) draws it where the chunk is loaded, the
 * level stage pass draws it for players farther away. Vanilla sounds only.
 */
public final class LaunchFx {
    /** Ticks after ignition the trench breathes fire. */
    private static final int JET_TICKS = 70;
    private static final double RANGE = 288;
    private static final float SCALE = 3.0F;
    /** The cradle's base is six pixels high. */
    private static final float BASE = 6 / 16F;

    public record Fx(BlockPos cradle, LaunchRules.Phase phase, long phaseStart, ItemStack probe, @Nullable Direction trench) {}

    private static final Map<BlockPos, Fx> ACTIVE = new HashMap<>();

    private LaunchFx() {}

    /** A payload or a cradle sync. */
    public static void update(BlockPos cradle, LaunchRules.Phase phase, long phaseStart, ItemStack probe, @Nullable Direction trench) {
        synchronized (ACTIVE) {
            if (phase == LaunchRules.Phase.IDLE && probe.isEmpty()) ACTIVE.remove(cradle);
            else ACTIVE.put(cradle.immutable(), new Fx(cradle.immutable(), phase, phaseStart, probe.copy(), trench));
        }
    }

    public static Optional<Fx> get(BlockPos cradle) {
        synchronized (ACTIVE) {
            return Optional.ofNullable(ACTIVE.get(cradle));
        }
    }

    static void reset() {
        synchronized (ACTIVE) {
            ACTIVE.clear();
        }
    }

    private static List<Fx> snapshot() {
        synchronized (ACTIVE) {
            return List.copyOf(ACTIVE.values());
        }
    }

    /** Height of the probe's foot over the cradle base, or NaN while it is out of sight. */
    static double height(Fx fx, double now) {
        double t = now - fx.phaseStart();
        return switch (fx.phase()) {
            case IDLE, COUNTDOWN -> 0;
            case ASCENT -> LaunchRules.ascentHeight(t);
            case FLIGHT -> Double.NaN;
            case DESCENT -> LaunchRules.descentHeight(t);
        };
    }

    // ---------------------------------------------------------------- drawing

    /**
     * Draws {@code fx} with {@code pose} at the cradle's block origin.
     *
     * @param light the packed light at the cradle (used while the probe stands on it)
     */
    static void draw(Fx fx, PoseStack pose, MultiBufferSource buffers, ItemRenderer items, BlockRenderDispatcher blocks, ClientLevel level, double now,
                     int light) {
        if (fx.probe().isEmpty()) return;
        double h = height(fx, now);
        if (Double.isNaN(h)) return;
        boolean moving = fx.phase() == LaunchRules.Phase.ASCENT || fx.phase() == LaunchRules.Phase.DESCENT;
        int lit = moving ? LightTexture.FULL_BRIGHT : light;
        double shake = fx.phase() == LaunchRules.Phase.COUNTDOWN || (fx.phase() == LaunchRules.Phase.ASCENT && now - fx.phaseStart() < 40)
            ? Mth.sin((float) now * 2.7F) * 0.02 : 0;
        pose.pushPose();
        pose.translate(0.5 + shake, BASE + h + SCALE / 2.0, 0.5);
        pose.scale(SCALE, SCALE, SCALE);
        items.renderStatic(fx.probe(), ItemDisplayContext.NONE, lit, OverlayTexture.NO_OVERLAY, pose, buffers, level, (int) fx.cradle().asLong());
        pose.popPose();
        if (fx.phase() == LaunchRules.Phase.DESCENT && now - fx.phaseStart() < LaunchRules.DESCENT_TICKS) canopies(pose, buffers, blocks, h);
    }

    /** Three white canopies above the falling capsule, swaying a little. */
    private static void canopies(PoseStack pose, MultiBufferSource buffers, BlockRenderDispatcher blocks, double h) {
        double top = BASE + h + SCALE;
        for (int k = 0; k < 3; k++) {
            pose.pushPose();
            pose.translate(0.5, top + 3.2, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(k * 120F + 15F));
            pose.translate(1.6, 0, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(-14F));
            pose.pushPose();
            pose.scale(2.4F, 0.35F, 2.4F);
            pose.translate(-0.5, 0, -0.5);
            blocks.renderSingleBlock(Blocks.WHITE_WOOL.defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            pose.translate(0, 0.35, 0);
            pose.scale(1.4F, 0.3F, 1.4F);
            pose.translate(-0.5, 0, -0.5);
            blocks.renderSingleBlock(Blocks.WHITE_WOOL.defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    /** Launches whose cradle chunk this client has not loaded (players beyond the view distance but within range). */
    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !ClientConfig.get(ClientConfig.LAUNCH_FX, true)) return;
        List<Fx> all = snapshot();
        if (all.isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        double now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (Fx fx : all) {
            if (fx.phase() != LaunchRules.Phase.ASCENT && fx.phase() != LaunchRules.Phase.DESCENT) continue;
            if (level.getBlockEntity(fx.cradle()) instanceof ProbeCradleBlockEntity) continue; // its renderer draws it
            if (cam.distanceToSqr(Vec3.atCenterOf(fx.cradle())) > RANGE * RANGE) continue;
            PoseStack pose = new PoseStack();
            pose.mulPose(event.getModelViewMatrix());
            pose.translate(fx.cradle().getX() - cam.x, fx.cradle().getY() - cam.y, fx.cradle().getZ() - cam.z);
            draw(fx, pose, buffers, mc.getItemRenderer(), mc.getBlockRenderer(), level, now, LightTexture.FULL_BRIGHT);
            drew = true;
        }
        if (drew) buffers.endBatch();
    }

    // ---------------------------------------------------------------- particles and sound

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || !ClientConfig.get(ClientConfig.LAUNCH_FX, true)) return;
        long now = level.getGameTime();
        RandomSource rng = level.random;
        for (Fx fx : snapshot()) {
            if (mc.player.distanceToSqr(Vec3.atCenterOf(fx.cradle())) > RANGE * RANGE) continue;
            double t = now - fx.phaseStart();
            double x = fx.cradle().getX() + 0.5;
            double y = fx.cradle().getY() + BASE;
            double z = fx.cradle().getZ() + 0.5;
            boolean jets = fx.phase() == LaunchRules.Phase.ASCENT && t < JET_TICKS;
            if (jets && fx.trench() != null) jets(level, rng, fx);
            switch (fx.phase()) {
                case COUNTDOWN -> {
                    if (rng.nextInt(3) == 0) level.addParticle(ParticleTypes.CLOUD, x + rng.nextGaussian() * 0.6, y + 0.2, z + rng.nextGaussian() * 0.6, 0, 0.02, 0);
                }
                case ASCENT -> {
                    double h = LaunchRules.ascentHeight(t);
                    if (t < 2) {
                        level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true, x, y + 0.5, z, 0, 0, 0);
                        for (int i = 0; i < 6; i++) {
                            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, x + rng.nextGaussian(), y, z + rng.nextGaussian(),
                                rng.nextGaussian() * 0.05, 0.05, rng.nextGaussian() * 0.05);
                        }
                    }
                    for (int i = 0; i < 4; i++) {
                        level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, x + rng.nextGaussian() * 0.25, y + h - 0.2, z + rng.nextGaussian() * 0.25,
                            rng.nextGaussian() * 0.04, -0.35 - rng.nextDouble() * 0.2, rng.nextGaussian() * 0.04);
                    }
                    level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, x + rng.nextGaussian() * 0.4, y + h * rng.nextDouble(),
                        z + rng.nextGaussian() * 0.4, 0, 0.01, 0);
                    if (now % 20 == 0) level.playLocalSound(x, y + h, z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 4.0F, 0.5F, false);
                }
                case DESCENT -> {
                    if (t >= LaunchRules.DESCENT_TICKS - 8 && t < LaunchRules.DESCENT_TICKS) {
                        for (int i = 0; i < 3; i++) level.addParticle(ParticleTypes.POOF, x + rng.nextGaussian() * 0.7, y + 0.1, z + rng.nextGaussian() * 0.7, 0, 0.03, 0);
                    }
                }
                default -> {
                }
            }
        }
    }

    /** Fire and smoke blown out of the trench mouth. */
    private static void jets(ClientLevel level, RandomSource rng, Fx fx) {
        Direction d = fx.trench();
        BlockPos mouth = fx.cradle().relative(d, TrenchShape.REACH + 1).below(2);
        double x = mouth.getX() + 0.5;
        double y = mouth.getY() + 0.5;
        double z = mouth.getZ() + 0.5;
        for (int i = 0; i < 3; i++) {
            double sx = d.getStepX() * (0.4 + rng.nextDouble() * 0.3) + rng.nextGaussian() * 0.05;
            double sz = d.getStepZ() * (0.4 + rng.nextDouble() * 0.3) + rng.nextGaussian() * 0.05;
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, x + rng.nextGaussian() * 0.6, y + rng.nextGaussian() * 0.5, z + rng.nextGaussian() * 0.6,
                sx, rng.nextGaussian() * 0.02, sz);
        }
        level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, x, y + 0.5, z, d.getStepX() * 0.3, 0.05, d.getStepZ() * 0.3);
    }

    /** The cradle's light for the probe at rest. */
    static int restLight(ClientLevel level, BlockPos cradle) {
        return LevelRenderer.getLightColor(level, cradle.above());
    }
}
