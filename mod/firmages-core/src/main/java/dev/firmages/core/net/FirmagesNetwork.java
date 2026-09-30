package dev.firmages.core.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration (SPEC §11, protocol version "1"). All payloads are S2C and optional for the client. */
public final class FirmagesNetwork {
    public static final String VERSION = "1";

    private FirmagesNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION).optional();
        registrar.playToClient(ReloadStatePayload.TYPE, ReloadStatePayload.STREAM_CODEC, ReloadStatePayload::handle);
    }
}
