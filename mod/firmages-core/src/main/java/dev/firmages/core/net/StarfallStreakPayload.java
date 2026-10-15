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
 * S2C {@code firmages:starfall_streak} (SPEC §25.10, protocol "5"): a star falls. Sent to every overworld player
 * {@link dev.firmages.core.sky.StarfallRules#STREAK_TICKS} ticks before the impact; the client draws the streak toward
 * the target and, for a near player, the falling body (SPEC §25.9).
 *
 * @param startAngle the streak's entry angle in degrees (seeded, so every player sees the same star)
 * @param target     the impact column (y = the ground, or the sea level when unknown)
 * @param impactTime game time of the impact
 * @param metal      {@code meteoric_iron} or {@code starsteel} (the colour of the body)
 * @param near       the player stands within 256 blocks of the target
 */
public record StarfallStreakPayload(float startAngle, BlockPos target, long impactTime, String metal, boolean near) implements CustomPacketPayload {
    public static final Type<StarfallStreakPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "starfall_streak"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StarfallStreakPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeFloat(p.startAngle);
            buf.writeBlockPos(p.target);
            buf.writeVarLong(p.impactTime);
            buf.writeUtf(p.metal, 32);
            buf.writeBoolean(p.near);
        },
        buf -> new StarfallStreakPayload(buf.readFloat(), buf.readBlockPos(), buf.readVarLong(), buf.readUtf(32), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(StarfallStreakPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) dev.firmages.core.client.ClientPayloads.starfall(payload);
        });
    }
}
