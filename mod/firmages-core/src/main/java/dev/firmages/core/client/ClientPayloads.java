package dev.firmages.core.client;

import dev.firmages.core.net.AgeTransitionPayload;
import dev.firmages.core.net.ShrinePreviewPayload;

/** Client handlers of the S2C payloads; called only on the physical client. */
public final class ClientPayloads {
    private ClientPayloads() {}

    public static void ceremony(AgeTransitionPayload payload) {
        CeremonyPlayer.start(payload);
    }

    public static void preview(ShrinePreviewPayload payload) {
        if (payload.clear()) PreviewBridge.clear();
        else PreviewBridge.show(payload);
    }
}
