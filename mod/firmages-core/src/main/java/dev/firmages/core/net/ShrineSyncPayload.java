package dev.firmages.core.net;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * S2C {@code firmages:shrine_sync} (SPEC §11, §17): the shrine's maintenance mode and the original block id of
 * every consecrated position, for the client's destroy-progress prediction (consecrated blocks are breakable only
 * in maintenance) and the Jade tooltip. Sent on login, on every maintenance change and (at most once a second) when
 * the stored originals change.
 *
 * @param dimension   the heart's dimension id ("" without a shrine)
 * @param maintenance maintenance mode is on
 * @param secondsLeft seconds of maintenance left when sent
 * @param originals   position (long) -> original block id
 */
public record ShrineSyncPayload(String dimension, boolean maintenance, int secondsLeft, Map<Long, String> originals) implements CustomPacketPayload {
    public static final Type<ShrineSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "shrine_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShrineSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ShrineSyncPayload::dimension,
        ByteBufCodecs.BOOL, ShrineSyncPayload::maintenance,
        ByteBufCodecs.VAR_INT, ShrineSyncPayload::secondsLeft,
        ByteBufCodecs.<io.netty.buffer.ByteBuf, Long, String, Map<Long, String>>map(HashMap::new, ByteBufCodecs.VAR_LONG, ByteBufCodecs.STRING_UTF8)
            .<RegistryFriendlyByteBuf>cast(), ShrineSyncPayload::originals,
        ShrineSyncPayload::new);

    // Client state (common classes only, so blocks and the Jade plugin may read it on either side).
    private static volatile String clientDimension = "";
    private static volatile boolean clientMaintenance;
    private static volatile long clientMaintenanceEndsMillis;
    private static volatile Map<Long, String> clientOriginals = Map.of();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ShrineSyncPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            clientDimension = p.dimension();
            clientMaintenance = p.maintenance();
            clientMaintenanceEndsMillis = System.currentTimeMillis() + p.secondsLeft() * 1000L;
            clientOriginals = Map.copyOf(p.originals());
        });
    }

    /** Client: maintenance mode as last synced (false once its time ran out, even before the off packet). */
    public static boolean clientMaintenance() {
        return clientMaintenance && System.currentTimeMillis() < clientMaintenanceEndsMillis + 2000L;
    }

    public static int clientSecondsLeft() {
        return clientMaintenance() ? (int) Math.max(0, (clientMaintenanceEndsMillis - System.currentTimeMillis()) / 1000L) : 0;
    }

    /** Client: the original block id at {@code pos} in dimension {@code dimension}, if synced. */
    public static Optional<String> clientOriginal(String dimension, BlockPos pos) {
        if (!dimension.equals(clientDimension)) return Optional.empty();
        return Optional.ofNullable(clientOriginals.get(pos.asLong()));
    }

    public static void clientReset() {
        clientDimension = "";
        clientMaintenance = false;
        clientOriginals = Map.of();
    }
}
