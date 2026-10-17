package dev.firmages.core.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration (SPEC §11, protocol version "8" since round G2: {@code firmages:age_status} gained
 * {@code inventoryRows} (the open main inventory rows, §19.4); "7" since 1.0.1: {@code firmages:age_status} gained
 * {@code inventoryButtons} and {@code map} (the server decides {@code ui.inventoryButtonsFromAge} and {@code ui.mapFromAge});
 * "6" since 0.12.0: {@code firmages:age_status} gained {@code gridLock} (Dawn grid lock), new {@code firmages:launch_fx}
 * (M14) and {@code firmages:ark_sync} (M17); "5" since the sky features' M10: {@code firmages:starfall_streak}).
 * All payloads are S2C and optional for the client.
 */
public final class FirmagesNetwork {
    public static final String VERSION = "8";

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
        // M17 (SPEC §31.9): firmages:ark_sync, protocol "6" with the rest of 0.12.0 (SPEC §24.6).
        registrar.playToClient(ArkSyncPayload.TYPE, ArkSyncPayload.STREAM_CODEC, ArkSyncPayload::handle);
        registrar.playToClient(LaunchFxPayload.TYPE, LaunchFxPayload.STREAM_CODEC, LaunchFxPayload::handle);
    }
}
