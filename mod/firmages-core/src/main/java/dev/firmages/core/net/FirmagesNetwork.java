package dev.firmages.core.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration (SPEC §11, protocol version "5" since the sky features' M10: {@code firmages:starfall_streak}).
 * All payloads are S2C and optional for the client.
 */
public final class FirmagesNetwork {
    public static final String VERSION = "5";

    private FirmagesNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION).optional();
        registrar.playToClient(ReloadStatePayload.TYPE, ReloadStatePayload.STREAM_CODEC, ReloadStatePayload::handle);
        registrar.playToClient(AgeTransitionPayload.TYPE, AgeTransitionPayload.STREAM_CODEC, AgeTransitionPayload::handle);
        registrar.playToClient(ShrinePreviewPayload.TYPE, ShrinePreviewPayload.STREAM_CODEC, ShrinePreviewPayload::handle);
        registrar.playToClient(ShrineSyncPayload.TYPE, ShrineSyncPayload.STREAM_CODEC, ShrineSyncPayload::handle);
        registrar.playToClient(AgeStatusPayload.TYPE, AgeStatusPayload.STREAM_CODEC, AgeStatusPayload::handle);
        registrar.playToClient(WelcomePayload.TYPE, WelcomePayload.STREAM_CODEC, WelcomePayload::handle);
        registrar.playToClient(StarfallStreakPayload.TYPE, StarfallStreakPayload.STREAM_CODEC, StarfallStreakPayload::handle);
        // M17 (SPEC §31.9): firmages:ark_sync. The version goes to "7" at merge, after M14's "6" (SPEC §24.6).
        registrar.playToClient(ArkSyncPayload.TYPE, ArkSyncPayload.STREAM_CODEC, ArkSyncPayload::handle);
    }
}
