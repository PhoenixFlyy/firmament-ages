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
 * S2C {@code firmages:age_status} (SPEC §11, §18, §19): the world's current Age, its goal item, whether EMI and Jade
 * may show, which TerraFirmaCraft inventory tabs are open ({@link InventoryTab} bit mask) and whether the Cave Dweller
 * still hunts, whether the hand-breaking rule holds, whether an Age swap is pending, whether the shrine's Gathering
 * is done and whether the inventory crafting grid is locked. Sent on login, after every change of the global Age state and after every Age swap or reload. Drives the HUD
 * Age indicator and goal, the EMI/Jade gating, the TFC tabs, the Cave Dweller sound mute, the client's break-speed
 * prediction, the single EMI reload per Age change and the hidden crafting grid (§19.3).
 */
public record AgeStatusPayload(String age, String goalItem, boolean recipeViewer, boolean blockInfo, byte tabs, boolean caveDweller,
                               boolean toolRule, boolean reloadPending, boolean gathered, boolean gridLock)
    implements CustomPacketPayload {
    public static final Type<AgeStatusPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "age_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AgeStatusPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.age);
            buf.writeUtf(p.goalItem);
            buf.writeBoolean(p.recipeViewer);
            buf.writeBoolean(p.blockInfo);
            buf.writeByte(p.tabs);
            buf.writeBoolean(p.caveDweller);
            buf.writeBoolean(p.toolRule);
            buf.writeBoolean(p.reloadPending);
            buf.writeBoolean(p.gathered);
            buf.writeBoolean(p.gridLock);
        },
        buf -> new AgeStatusPayload(buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readBoolean(), buf.readByte(), buf.readBoolean(),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

    public static AgeStatusPayload of(AgeStatus s) {
        return new AgeStatusPayload(s.age().id(), s.goalItem(), s.recipeViewer(), s.blockInfo(), (byte) InventoryTab.mask(s.tabs()), s.caveDweller(),
            s.toolRule(), s.reloadPending(), s.gathered(), s.gridLock());
    }

    public AgeStatus status() {
        return new AgeStatus(AgeId.byId(age).orElse(AgeId.DAWN), goalItem, recipeViewer, blockInfo, InventoryTab.fromMask(tabs), caveDweller, toolRule,
            reloadPending, gathered, gridLock);
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
