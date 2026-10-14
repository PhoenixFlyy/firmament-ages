package dev.firmages.core.net;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.shrine.ShrineRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * S2C {@code firmages:shrine_sync} (SPEC §11, §17, §7.10): the shrine's maintenance mode and the original block id of
 * every consecrated position, for the client's destroy-progress prediction (consecrated blocks are breakable only
 * in maintenance) and the Jade tooltip; since protocol "4" also what the living shrine needs (idle beam, hum, relic
 * dust): the heart's position, whether the shrine is intact, whether the finale is won, whether the server shows
 * the idle beam ({@code shrine.permanentBeam}) and the ceremony beam colour of each tier. The heart's {@code lit}
 * and {@code awakened} come from its block state on the client. Sent on login, on every maintenance change, and (at
 * most once a second) when the stored originals, the heart, the intact flag or the finale change.
 *
 * @param dimension   the heart's dimension id ("" without a shrine)
 * @param maintenance maintenance mode is on
 * @param secondsLeft seconds of maintenance left when sent
 * @param originals   position (long) -> original block id
 * @param heartPos    the heart's position ({@link BlockPos#asLong}), meaningless without a dimension
 * @param intact      the rings of the awakened tiers stand
 * @param finale      the final boss fell ({@code finale_won})
 * @param idleBeam    {@code shrine.permanentBeam}
 * @param tierBeam    ARGB ceremony beam colour of tier 0..8
 */
public record ShrineSyncPayload(String dimension, boolean maintenance, int secondsLeft, Map<Long, String> originals,
                                long heartPos, boolean intact, boolean finale, boolean idleBeam, List<Integer> tierBeam) implements CustomPacketPayload {
    public static final Type<ShrineSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "shrine_sync"));
    private static final StreamCodec<io.netty.buffer.ByteBuf, Map<Long, String>> ORIGINALS =
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.VAR_LONG, ByteBufCodecs.STRING_UTF8);
    private static final StreamCodec<io.netty.buffer.ByteBuf, List<Integer>> COLORS = ByteBufCodecs.INT.apply(ByteBufCodecs.list(64));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShrineSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.dimension);
            buf.writeBoolean(p.maintenance);
            buf.writeVarInt(p.secondsLeft);
            ORIGINALS.encode(buf, p.originals);
            buf.writeLong(p.heartPos);
            buf.writeBoolean(p.intact);
            buf.writeBoolean(p.finale);
            buf.writeBoolean(p.idleBeam);
            COLORS.encode(buf, p.tierBeam);
        },
        buf -> new ShrineSyncPayload(buf.readUtf(), buf.readBoolean(), buf.readVarInt(), ORIGINALS.decode(buf), buf.readLong(), buf.readBoolean(),
            buf.readBoolean(), buf.readBoolean(), COLORS.decode(buf)));

    // Client state (common classes only, so blocks and the Jade plugin may read it on either side).
    private static volatile String clientDimension = "";
    private static volatile boolean clientMaintenance;
    private static volatile long clientMaintenanceEndsMillis;
    private static volatile Map<Long, String> clientOriginals = Map.of();
    private static volatile long clientHeartPos;
    private static volatile boolean clientIntact;
    private static volatile boolean clientFinale;
    private static volatile boolean clientIdleBeam;
    private static volatile int[] clientTierBeam = new int[0];

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
            clientHeartPos = p.heartPos();
            clientIntact = p.intact();
            clientFinale = p.finale();
            clientIdleBeam = p.idleBeam();
            clientTierBeam = p.tierBeam().stream().mapToInt(Integer::intValue).toArray();
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

    /** Client: the synced shrine heart, if there is one in {@code dimension}. */
    public static Optional<BlockPos> clientHeart(String dimension) {
        return dimension.equals(clientDimension) ? Optional.of(BlockPos.of(clientHeartPos)) : Optional.empty();
    }

    /** Client: the shrine is intact as last synced. */
    public static boolean clientIntact() {
        return clientIntact;
    }

    /**
     * Client: the idle beam's ARGB colour for the heart at {@code pos} with block state {@code awakened} and {@code lit},
     * or 0 for none (another heart, the beam off on the server, cold, broken, nothing awakened yet).
     */
    public static int clientIdleBeamColor(String dimension, BlockPos pos, int awakened, boolean lit) {
        if (!clientIdleBeam || !dimension.equals(clientDimension) || pos.asLong() != clientHeartPos) return 0;
        return ShrineRules.idleBeamColor(Math.min(awakened, clientTierBeam.length), clientIntact, lit, clientFinale, clientTierBeam);
    }

    /** Client: the ceremony beam colour (RGB) of tier {@code tier}, if synced. */
    public static Optional<Integer> clientTierColor(int tier) {
        int[] c = clientTierBeam;
        return tier >= 0 && tier < c.length ? Optional.of(c[tier] & 0xFFFFFF) : Optional.empty();
    }

    public static void clientReset() {
        clientDimension = "";
        clientMaintenance = false;
        clientOriginals = Map.of();
        clientHeartPos = 0;
        clientIntact = false;
        clientFinale = false;
        clientIdleBeam = false;
        clientTierBeam = new int[0];
    }
}
