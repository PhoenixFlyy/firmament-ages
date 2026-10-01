package dev.firmages.core.net;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;

/**
 * S2C {@code firmages:age_transition} (SPEC §8, §11): one packet drives the whole client-side ceremony, which runs
 * on client ticks and so keeps going while the server freezes in the Age reload.
 *
 * @param stage          the granted Age stage ({@code age_1})
 * @param tier           the shrine tier that granted it, or -1 for a grant from elsewhere
 * @param full           FULL (shrine prayer) or SHORT (quest, admin) ceremony
 * @param shrine         the heart, for the beam and the converging particles
 * @param beamColor      ARGB
 * @param skyTint        RGB
 * @param sting          sound event played at the start
 * @param voiceKey       lang key of Caelum's line (subtitle and chat)
 * @param particles      particle type ids for the beam
 * @param blessingName   lang key of the blessing name, empty if none
 * @param blessingDesc   lang key of the blessing description, empty if none
 */
public record AgeTransitionPayload(String stage, int tier, boolean full, Optional<GlobalPos> shrine, int beamColor, int skyTint,
                                   ResourceLocation sting, String voiceKey, List<ResourceLocation> particles,
                                   String blessingName, String blessingDesc) implements CustomPacketPayload {
    public static final Type<AgeTransitionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "age_transition"));
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<GlobalPos>> OPT_POS =
        ByteBufCodecs.optional(GlobalPos.STREAM_CODEC).cast();
    private static final StreamCodec<RegistryFriendlyByteBuf, List<ResourceLocation>> IDS =
        ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).cast();
    public static final StreamCodec<RegistryFriendlyByteBuf, AgeTransitionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.stage);
            buf.writeVarInt(p.tier);
            buf.writeBoolean(p.full);
            OPT_POS.encode(buf, p.shrine);
            buf.writeInt(p.beamColor);
            buf.writeInt(p.skyTint);
            buf.writeResourceLocation(p.sting);
            buf.writeUtf(p.voiceKey);
            IDS.encode(buf, p.particles);
            buf.writeUtf(p.blessingName);
            buf.writeUtf(p.blessingDesc);
        },
        buf -> new AgeTransitionPayload(buf.readUtf(), buf.readVarInt(), buf.readBoolean(), OPT_POS.decode(buf), buf.readInt(), buf.readInt(),
            buf.readResourceLocation(), buf.readUtf(), IDS.decode(buf), buf.readUtf(), buf.readUtf()));

    /** Ticks the FULL ceremony runs on the client (about 12 s). */
    public static final int FULL_TICKS = 240;
    /** Ticks the SHORT ceremony runs. */
    public static final int SHORT_TICKS = 120;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(AgeTransitionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) dev.firmages.core.client.ClientPayloads.ceremony(payload);
        });
    }
}
