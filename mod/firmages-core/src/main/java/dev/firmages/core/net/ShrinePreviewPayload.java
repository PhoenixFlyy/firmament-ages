package dev.firmages.core.net;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C {@code firmages:shrine_preview} (SPEC §11): show (or clear) the Modonomicon ghost preview of a ring,
 * anchored at the heart.
 *
 * @param multiblock ring multiblock id
 * @param heart      heart position (pattern character {@code 0})
 * @param rotation   {@link net.minecraft.world.level.block.Rotation} ordinal
 * @param clear      true clears the preview
 */
public record ShrinePreviewPayload(ResourceLocation multiblock, BlockPos heart, int rotation, boolean clear) implements CustomPacketPayload {
    public static final Type<ShrinePreviewPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "shrine_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShrinePreviewPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeResourceLocation(p.multiblock);
            buf.writeBlockPos(p.heart);
            buf.writeVarInt(p.rotation);
            buf.writeBoolean(p.clear);
        },
        buf -> new ShrinePreviewPayload(buf.readResourceLocation(), buf.readBlockPos(), buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ShrinePreviewPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) dev.firmages.core.client.ClientPayloads.preview(payload);
        });
    }
}
