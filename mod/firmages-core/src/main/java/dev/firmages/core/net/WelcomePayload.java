package dev.firmages.core.net;

import dev.firmages.core.FirmagesCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C {@code firmages:welcome} (SPEC §11, §18.5): the player's first join. The client shows the title, then the
 * one-time welcome screen. The server marks the player as welcomed when it sends this.
 */
public record WelcomePayload() implements CustomPacketPayload {
    public static final WelcomePayload INSTANCE = new WelcomePayload();
    public static final Type<WelcomePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "welcome"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WelcomePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(WelcomePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) dev.firmages.core.client.ClientPayloads.welcome();
        });
    }
}
