package dev.firmages.core.client;

import dev.firmages.core.client.immersion.ImmersionClient;
import dev.firmages.core.net.AgeStatusPayload;
import dev.firmages.core.net.AgeTransitionPayload;
import dev.firmages.core.net.ShrinePreviewPayload;

/** Client handlers of the S2C payloads; called only on the physical client. */
public final class ClientPayloads {
    private ClientPayloads() {}

    public static void ceremony(AgeTransitionPayload payload) {
        CeremonyPlayer.start(payload);
    }

    public static void ageStatus(AgeStatusPayload payload) {
        ImmersionClient.onStatus(payload.status());
    }

    public static void welcome() {
        ImmersionClient.onWelcome();
    }

    public static void preview(ShrinePreviewPayload payload) {
        if (payload.clear()) PreviewBridge.clear();
        else PreviewBridge.show(payload);
    }
}
