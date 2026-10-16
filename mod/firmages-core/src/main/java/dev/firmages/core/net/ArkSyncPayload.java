package dev.firmages.core.net;

import dev.firmages.core.FirmagesCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C {@code firmages:ark_sync} (SPEC §31.8, protocol "7" at merge): the Archipelago for the beams at the altar. Sent on
 * entering The Origin and to everyone in The Origin when an island changes.
 *
 * @param stages    the stage of each island (0..3), index = island
 * @param complete  bit {@code i} set for each complete island (its socket beam is lit)
 * @param colors    the beam colour of each island (ARGB, the tier's {@code response.beam_color})
 * @param celebrate the ninth beam lit just now: the client shows the FINALE sky tint once more for ten seconds
 */
public record ArkSyncPayload(byte[] stages, int complete, int[] colors, boolean celebrate) implements CustomPacketPayload {
    public static final Type<ArkSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "ark_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArkSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> write(buf, p),
        ArkSyncPayload::read);

    private static void write(ByteBuf buf, ArkSyncPayload p) {
        buf.writeByte(p.stages.length);
        buf.writeBytes(p.stages);
        buf.writeInt(p.complete);
        buf.writeByte(p.colors.length);
        for (int c : p.colors) buf.writeInt(c);
        buf.writeBoolean(p.celebrate);
    }

    private static ArkSyncPayload read(ByteBuf buf) {
        byte[] stages = new byte[Math.min(32, buf.readUnsignedByte())];
        buf.readBytes(stages);
        int complete = buf.readInt();
        int[] colors = new int[Math.min(32, buf.readUnsignedByte())];
        for (int i = 0; i < colors.length; i++) colors[i] = buf.readInt();
        return new ArkSyncPayload(stages, complete, colors, buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ArkSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) dev.firmages.core.client.ClientPayloads.ark(payload);
        });
    }
}
