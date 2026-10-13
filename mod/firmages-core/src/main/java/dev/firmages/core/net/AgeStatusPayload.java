package dev.firmages.core.net;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.immersion.AgeStatus;
import dev.firmages.core.immersion.InventoryTab;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C {@code firmages:age_status} (SPEC §11, §18): the world's current Age, its goal item, whether EMI and Jade may
 * show and which TerraFirmaCraft inventory tabs are open ({@link InventoryTab} bit mask). Sent on login and after every
 * change of the global Age state. Drives the HUD Age indicator, the EMI/Jade gating and the TFC tabs on the client.
 */
public record AgeStatusPayload(String age, String goalItem, boolean recipeViewer, boolean blockInfo, byte tabs) implements CustomPacketPayload {
    public static final Type<AgeStatusPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "age_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AgeStatusPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.age);
            buf.writeUtf(p.goalItem);
            buf.writeBoolean(p.recipeViewer);
            buf.writeBoolean(p.blockInfo);
            buf.writeByte(p.tabs);
        },
        buf -> new AgeStatusPayload(buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readBoolean(), buf.readByte()));

    public static AgeStatusPayload of(AgeStatus s) {
        return new AgeStatusPayload(s.age().id(), s.goalItem(), s.recipeViewer(), s.blockInfo(), (byte) InventoryTab.mask(s.tabs()));
    }

    public AgeStatus status() {
        return new AgeStatus(AgeId.byId(age).orElse(AgeId.DAWN), goalItem, recipeViewer, blockInfo, InventoryTab.fromMask(tabs));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(AgeStatusPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) dev.firmages.core.client.ClientPayloads.ageStatus(payload);
        });
    }
}
