package dev.firmages.core.net;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.launch.LaunchRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * S2C {@code firmages:launch_fx} (SPEC §31.7, §31.9, protocol "6"): a Launch Complex changed its phase. Sent to the
 * players within {@code launch.fxRange} of the cradle; the client draws the climb, the trench jets and the descent from
 * it even where the cradle's chunk is not loaded. The cradle's block-entity sync carries the same for players nearby.
 *
 * @param cradle     the probe cradle
 * @param phase      {@link LaunchRules.Phase} ordinal
 * @param phaseStart game time the phase began
 * @param probe      the probe item (its model is drawn at scale 3), empty without a probe
 * @param trench     {@link Direction#get3DDataValue()} of the side the flame trench opens to, -1 for none
 */
public record LaunchFxPayload(BlockPos cradle, int phase, long phaseStart, ItemStack probe, int trench) implements CustomPacketPayload {
    public static final Type<LaunchFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "launch_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LaunchFxPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeBlockPos(p.cradle);
            buf.writeVarInt(p.phase);
            buf.writeVarLong(p.phaseStart);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.probe);
            buf.writeByte(p.trench);
        },
        buf -> new LaunchFxPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarLong(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(LaunchFxPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                dev.firmages.core.client.LaunchFx.update(p.cradle, LaunchRules.Phase.byOrdinal(p.phase), p.phaseStart, p.probe,
                    p.trench >= 0 && p.trench < 6 ? Direction.from3DDataValue(p.trench) : null);
            }
        });
    }

    /** Client side of the cradle's block-entity sync (common code calls it; it only reaches client classes on a client). */
    public static void clientUpdate(BlockPos cradle, LaunchRules.Phase phase, long phaseStart, ItemStack probe, @Nullable Direction trench) {
        if (FMLEnvironment.dist.isClient()) dev.firmages.core.client.LaunchFx.update(cradle, phase, phaseStart, probe, trench);
    }
}
