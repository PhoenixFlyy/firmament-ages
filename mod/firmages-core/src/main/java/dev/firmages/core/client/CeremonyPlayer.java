package dev.firmages.core.client;

import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.net.AgeTransitionPayload;
import dev.firmages.core.shrine.ShrineRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client timeline of the Age ceremony (SPEC §8). Runs on client ticks, so it keeps going while the server is
 * frozen by the Age reload. FULL (about 12 s):
 * <pre>
 * t=0    music stops, sting, particles converge on the heart
 * t=40   beam in the Age colour, choir
 * t=80   sky tint rises (held until t=200, gone at t=240)
 * t=100  title "The ... Age dawns", subtitle and one chat line: Caelum's voice line
 * t=160  chat line with the blessing
 * </pre>
 * SHORT (6 s): sting, title and voice line at once, a short beam and a faint tint.
 */
public final class CeremonyPlayer {
    @Nullable private static AgeTransitionPayload active;
    private static int ticks;
    private static final RandomSource RANDOM = RandomSource.create();

    private CeremonyPlayer() {}

    public static void start(AgeTransitionPayload payload) {
        active = payload;
        ticks = 0;
    }

    public static void reset() {
        active = null;
        ticks = 0;
    }

    public static boolean running() {
        return active != null;
    }

    static void tick() {
        AgeTransitionPayload p = active;
        if (p == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        int t = ticks++;
        if (p.full()) fullTimeline(mc, p, t);
        else shortTimeline(mc, p, t);
        if (ticks >= (p.full() ? AgeTransitionPayload.FULL_TICKS : AgeTransitionPayload.SHORT_TICKS)) reset();
    }

    private static void fullTimeline(Minecraft mc, AgeTransitionPayload p, int t) {
        if (t == 0) {
            if (ClientConfig.STOP_MUSIC.get()) mc.getMusicManager().stopPlaying();
            play(mc, p.sting(), 1.0F);
        }
        if (t < 40 && t % 2 == 0) converge(mc, p);
        if (t == 40) play(mc, ShrineRegistry.CHOIR.getId(), 1.0F);
        if (t >= 40 && t % 2 == 0) beamParticles(mc, p);
        if (t == 100) words(mc, p);
        if (t == 160 && !p.blessingName().isEmpty()) {
            mc.player.displayClientMessage(Component.translatable("firmages.ceremony.blessing", Component.translatable(p.blessingName()),
                Component.translatable(p.blessingDesc())).withStyle(ChatFormatting.AQUA), false);
        }
    }

    private static void shortTimeline(Minecraft mc, AgeTransitionPayload p, int t) {
        if (t == 0) play(mc, p.sting(), 1.0F);
        if (t == 10) words(mc, p);
        if (t % 3 == 0 && t < 80) beamParticles(mc, p);
    }

    private static void words(Minecraft mc, AgeTransitionPayload p) {
        Component title = Component.translatable("firmages.age." + p.stage() + ".title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component voice = Component.translatable(p.voiceKey());
        mc.gui.setTimes(10, 80, 30);
        mc.gui.setSubtitle(voice.copy().withStyle(ChatFormatting.GRAY));
        mc.gui.setTitle(title);
        mc.player.displayClientMessage(Component.translatable("firmages.ceremony.voice", voice).withStyle(ChatFormatting.GOLD), false);
    }

    private static void play(Minecraft mc, ResourceLocation id, float pitch) {
        SoundEvent e = BuiltInRegistries.SOUND_EVENT.get(id);
        if (e == null) e = SoundEvent.createVariableRangeEvent(id);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, 1.0F));
    }

    /** The heart, if it is in the player's dimension and within 96 blocks. */
    @Nullable
    private static BlockPos nearHeart(Minecraft mc, AgeTransitionPayload p) {
        if (p.shrine().isEmpty()) return null;
        GlobalPos gp = p.shrine().get();
        if (mc.level == null || mc.player == null || gp.dimension() != mc.level.dimension()) return null;
        return gp.pos().distToCenterSqr(mc.player.position()) <= 96 * 96 ? gp.pos() : null;
    }

    private static void converge(Minecraft mc, AgeTransitionPayload p) {
        BlockPos h = nearHeart(mc, p);
        if (h == null) return;
        int n = (int) Math.ceil(6 * ClientConfig.PARTICLE_SCALE.get());
        for (int i = 0; i < n; i++) {
            double dx = (RANDOM.nextDouble() - 0.5) * 8;
            double dy = RANDOM.nextDouble() * 3;
            double dz = (RANDOM.nextDouble() - 0.5) * 8;
            // The enchant particle flies from (pos + delta) to pos.
            mc.level.addParticle(ParticleTypes.ENCHANT, h.getX() + 0.5, h.getY() + 1.5, h.getZ() + 0.5, dx, dy, dz);
        }
    }

    private static void beamParticles(Minecraft mc, AgeTransitionPayload p) {
        BlockPos h = nearHeart(mc, p);
        if (h == null) return;
        List<ParticleOptions> types = particleTypes(p);
        int n = (int) Math.ceil(3 * ClientConfig.PARTICLE_SCALE.get());
        for (int i = 0; i < n; i++) {
            ParticleOptions type = types.get(RANDOM.nextInt(types.size()));
            double y = h.getY() + 1.2 + RANDOM.nextDouble() * 12;
            mc.level.addParticle(type, h.getX() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.6, y, h.getZ() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.6,
                0, 0.05 + RANDOM.nextDouble() * 0.05, 0);
        }
    }

    private static List<ParticleOptions> particleTypes(AgeTransitionPayload p) {
        List<ParticleOptions> out = new ArrayList<>();
        for (ResourceLocation id : p.particles()) {
            if (BuiltInRegistries.PARTICLE_TYPE.get(id) instanceof SimpleParticleType s) out.add(s);
        }
        if (out.isEmpty()) out.add(ParticleTypes.END_ROD);
        return out;
    }

    /** Beam strength 0..1 at {@code pos} (the heart renderer draws it), and its colour via {@link #beamColor()}. */
    public static float beamStrength(BlockPos pos, float partialTick) {
        AgeTransitionPayload p = active;
        if (p == null || p.shrine().isEmpty() || !p.shrine().get().pos().equals(pos)) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || p.shrine().get().dimension() != mc.level.dimension()) return 0;
        float t = ticks + partialTick;
        if (p.full()) {
            if (t < 40) return 0;
            return Mth.clamp((t - 40) / 20F, 0, 1) * Mth.clamp((AgeTransitionPayload.FULL_TICKS - t) / 40F, 0, 1);
        }
        return Mth.clamp(t / 10F, 0, 1) * Mth.clamp((AgeTransitionPayload.SHORT_TICKS - t) / 30F, 0, 1);
    }

    public static int beamColor() {
        AgeTransitionPayload p = active;
        return p == null ? 0xFFFFFFFF : p.beamColor();
    }

    /** Ticks since the start plus partial tick: the beam animation clock (game time freezes during the reload). */
    public static float clock(float partialTick) {
        return ticks + partialTick;
    }

    /** Sky tint as {r, g, b, strength} (0..1), or null. */
    @Nullable
    public static float[] skyTint(float partialTick) {
        AgeTransitionPayload p = active;
        if (p == null || !ClientConfig.SKY_TINT.get()) return null;
        float t = ticks + partialTick;
        float s;
        if (p.full()) {
            s = Mth.clamp((t - 80) / 20F, 0, 1) * Mth.clamp((AgeTransitionPayload.FULL_TICKS - t) / 40F, 0, 1);
        } else {
            s = 0.5F * Mth.clamp(t / 20F, 0, 1) * Mth.clamp((AgeTransitionPayload.SHORT_TICKS - t) / 30F, 0, 1);
        }
        if (s <= 0) return null;
        int c = p.skyTint();
        return new float[] {((c >> 16) & 0xFF) / 255F, ((c >> 8) & 0xFF) / 255F, (c & 0xFF) / 255F, s};
    }
}
