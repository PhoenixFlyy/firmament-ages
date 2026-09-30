package dev.firmages.core.net;

import dev.firmages.core.FirmagesCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C {@code firmages:reload_state} (SPEC §11): an Age reload starts or ends. */
public record ReloadStatePayload(boolean running) implements CustomPacketPayload {
    public static final Type<ReloadStatePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "reload_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReloadStatePayload> STREAM_CODEC =
        ByteBufCodecs.BOOL.<RegistryFriendlyByteBuf>cast().map(ReloadStatePayload::new, ReloadStatePayload::running);

    /** Client state, read by the ceremony client code. */
    private static volatile boolean clientRunning;

    public static boolean clientRunning() {
        return clientRunning;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client handler; only uses common classes, so it is safe to reference from common code. */
    static void handle(ReloadStatePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            clientRunning = payload.running();
            if (payload.running() && ctx.player() != null) {
                ctx.player().displayClientMessage(
                    Component.translatableWithFallback("firmages.reload.running", "The world realigns..."), true);
            }
        });
    }
}
