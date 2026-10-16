package dev.firmages.core.isles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** The JSON form of {@link IsleSettings} in {@code data/firmages/worldgen/structure/drifting_isle.json} (SPEC §30.3). */
public final class IsleCodecs {
    private IsleCodecs() {}

    static final Codec<IsleSettings.Range> RANGE_OBJECT = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.fieldOf("min").forGetter(IsleSettings.Range::min),
        Codec.INT.fieldOf("max").forGetter(IsleSettings.Range::max)).apply(i, IsleSettings.Range::new));
    static final Codec<IsleSettings.Range> RANGE_PAIR = Codec.INT.listOf(2, 2).xmap(l -> new IsleSettings.Range(l.get(0), l.get(1)),
        r -> List.of(r.min(), r.max()));

    private record Radii(IsleSettings.Range small, IsleSettings.Range medium, IsleSettings.Range large) {
        static final Codec<Radii> CODEC = RecordCodecBuilder.create(i -> i.group(
            RANGE_PAIR.fieldOf("small").forGetter(Radii::small),
            RANGE_PAIR.fieldOf("medium").forGetter(Radii::medium),
            RANGE_PAIR.fieldOf("large").forGetter(Radii::large)).apply(i, Radii::new));
    }

    private record Weights(int small, int medium, int large) {
        static final Codec<Weights> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 1000).fieldOf("small").forGetter(Weights::small),
            Codec.intRange(0, 1000).fieldOf("medium").forGetter(Weights::medium),
            Codec.intRange(0, 1000).fieldOf("large").forGetter(Weights::large)).apply(i, Weights::new));
    }

    private static final Codec<IsleSize> SIZE = Codec.STRING.comapFlatMap(
        s -> IsleSize.byId(s).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "unknown isle size " + s)), IsleSize::id);

    public static final MapCodec<IsleSettings> SETTINGS = RecordCodecBuilder.<IsleSettings>mapCodec(i -> i.group(
        RANGE_OBJECT.fieldOf("top_y").forGetter(IsleSettings::topY),
        Codec.intRange(0, 200).fieldOf("min_clearance").forGetter(IsleSettings::minClearance),
        RANGE_OBJECT.fieldOf("cluster").forGetter(IsleSettings::cluster),
        Radii.CODEC.fieldOf("radius").forGetter(s -> new Radii(s.small(), s.medium(), s.large())),
        Weights.CODEC.fieldOf("weights").forGetter(s -> new Weights(s.weightSmall(), s.weightMedium(), s.weightLarge())),
        Codec.floatRange(0, 50).fieldOf("crystal_clusters_per_100_top").forGetter(IsleSettings::crystalsPer100Top),
        SIZE.fieldOf("lode_from").forGetter(IsleSettings::lodeFrom),
        Codec.intRange(0, 8).fieldOf("trees_max").forGetter(IsleSettings::treesMax),
        Codec.floatRange(0, 1).fieldOf("wind_shrine_chance").forGetter(IsleSettings::windShrineChance)
    ).apply(i, (top, clear, cluster, radii, w, crystals, lode, trees, shrine) -> new IsleSettings(top, clear, cluster, radii.small(), radii.medium(),
        radii.large(), w.small(), w.medium(), w.large(), crystals, lode, trees, shrine))).validate(s -> {
            String p = s.problem();
            return p == null ? DataResult.success(s) : DataResult.error(() -> p);
        });
}
