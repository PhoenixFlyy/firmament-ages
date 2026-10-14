package dev.firmages.core.client;

import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.net.ShrineSyncPayload;
import dev.firmages.core.shrine.ShrineHeartBlock;
import dev.firmages.core.shrine.ShrineRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * The living shrine's hum (SPEC §7.10, client config {@code effects.ambientDrone}): every 4 s, within 16 blocks of
 * the lit, intact heart, a low beacon hum that fades with distance; its pitch rises a little with every awakened Age.
 */
final class ShrineAmbience {
    static final int PERIOD = 80;
    static final double RANGE = 16.0;
    private static int ticks;

    private ShrineAmbience() {}

    static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (++ticks % PERIOD != 0 || !ClientConfig.get(ClientConfig.AMBIENT_DRONE, true)) return;
        Optional<BlockPos> heart = ShrineSyncPayload.clientHeart(mc.level.dimension().location().toString());
        if (heart.isEmpty() || !ShrineSyncPayload.clientIntact()) return;
        BlockPos h = heart.get();
        double d = Math.sqrt(h.distToCenterSqr(mc.player.position()));
        if (d > RANGE || !mc.level.isLoaded(h)) return;
        BlockState st = mc.level.getBlockState(h);
        if (!st.is(ShrineRegistry.SHRINE_HEART.get()) || !st.getValue(ShrineHeartBlock.LIT)) return;
        int awakened = Math.min(9, st.getValue(ShrineHeartBlock.AWAKENED));
        float volume = (float) (0.6 * (1.0 - d / RANGE));
        float pitch = 0.5F + 0.06F * awakened;
        mc.level.playLocalSound(h, ShrineRegistry.AMBIENT.get(), SoundSource.AMBIENT, volume, pitch, false);
    }
}
