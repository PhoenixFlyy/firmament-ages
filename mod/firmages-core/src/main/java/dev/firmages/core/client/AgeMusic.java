package dev.firmages.core.client;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.client.immersion.ClientAgeStatus;
import dev.firmages.core.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-Age music (SPEC §23): where the game would play its generic background music (the game, creative and underwater
 * tracks and the overworld biome tracks; every dimension), the client plays {@code firmages:music.age_N} of the world's
 * Age instead ({@code assets/firmages/sounds.json}; Dawn uses age_0). Nether, End, menu, credits and every other mod's
 * music stay. While a Firmament night plays its own track ({@code firmages:music.firmament_night}, looped by Enhanced
 * Celestials 2 as an ambient sound through the lunar event's {@code sound_track} modifier), the background music is
 * silent, so the two never overlap. During the Age ceremony nothing plays ({@code effects.stopMusic}). Switch:
 * {@code music.ageMusic} in {@code firmages-client.toml}.
 */
public final class AgeMusic {
    /** The sound event Enhanced Celestials 2 loops during a Firmament night. */
    public static final ResourceLocation NIGHT_TRACK = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "music.firmament_night");
    /** Vanilla's game-music delays (Musics.createGameMusic): 10 to 20 minutes between tracks, no cut-in. */
    private static final int MIN_DELAY = 12000;
    private static final int MAX_DELAY = 24000;

    private static final Map<AgeId, Music> MUSIC = new EnumMap<>(AgeId.class);
    @Nullable
    private static SoundInstance night;

    private AgeMusic() {}

    /** The Age track for {@code age}. */
    public static Music music(AgeId age) {
        return MUSIC.computeIfAbsent(age == AgeId.DAWN ? AgeId.AGE_0 : age, a -> new Music(Holder.direct(
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "music." + a.id()))), MIN_DELAY, MAX_DELAY, false));
    }

    /** True for the generic background music the Age tracks replace. */
    public static boolean replaceable(@Nullable Music music) {
        if (music == null) return false;
        ResourceLocation id = music.getEvent().value().getLocation();
        if (!"minecraft".equals(id.getNamespace())) return false;
        String p = id.getPath();
        return p.equals("music.game") || p.equals("music.creative") || p.equals("music.under_water") || p.startsWith("music.overworld.");
    }

    static void onSelect(SelectMusicEvent event) {
        if (CeremonyPlayer.running() && ClientConfig.get(ClientConfig.STOP_MUSIC, true)) {
            event.overrideMusic(null);
            return;
        }
        if (nightPlaying()) {
            event.overrideMusic(null);
            return;
        }
        if (!ClientConfig.get(ClientConfig.AGE_MUSIC, true) || !ClientAgeStatus.known() || !replaceable(event.getMusic())) return;
        event.setMusic(music(ClientAgeStatus.get().age()));
    }

    static void onPlaySound(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s != null && NIGHT_TRACK.equals(s.getLocation())) night = s;
    }

    private static boolean nightPlaying() {
        if (night == null) return false;
        if (Minecraft.getInstance().getSoundManager().isActive(night)) return true;
        night = null;
        return false;
    }

    static void reset() {
        night = null;
    }
}
