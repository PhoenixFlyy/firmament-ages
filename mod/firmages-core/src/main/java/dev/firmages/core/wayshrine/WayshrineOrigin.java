package dev.firmages.core.wayshrine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Item component {@code firmages:wayshrine} of a Wayshrine Stone (SPEC-himmel §28.5): the type and core position of the
 * Wayshrine that gave it. The stone's look comes from its block state {@code kind} (kept through placing and breaking
 * by the loot table's {@code copy_state}); this component only travels with the first drop.
 */
public record WayshrineOrigin(String type, BlockPos pos) {
    public static final Codec<WayshrineOrigin> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("type").forGetter(WayshrineOrigin::type),
        BlockPos.CODEC.fieldOf("pos").forGetter(WayshrineOrigin::pos)).apply(i, WayshrineOrigin::new));
    public static final StreamCodec<ByteBuf, WayshrineOrigin> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, WayshrineOrigin::type, BlockPos.STREAM_CODEC, WayshrineOrigin::pos, WayshrineOrigin::new);
}
