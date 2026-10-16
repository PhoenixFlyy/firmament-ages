package dev.firmages.core.isles;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.List;
import java.util.Optional;

/**
 * Structure type {@code firmages:drifting_isle} (SPEC §30.3): one cluster of one to four static isles per start, each
 * a code-shaped {@link IslePiece}. The plan is a pure function of the world seed and the start chunk
 * ({@link IsleLayout}); the heights come from the chunk generator's {@code getBaseHeight(WORLD_SURFACE_WG)}, which
 * TerraFirmaCraft's generator implements, so a start is deterministic per seed and {@code /locate} finds it. A start
 * whose main isle has no room in the band above the terrain is skipped.
 */
public final class DriftingIsleStructure extends Structure {
    public static final MapCodec<DriftingIsleStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        settingsCodec(i),
        IsleCodecs.SETTINGS.forGetter(DriftingIsleStructure::isles)).apply(i, DriftingIsleStructure::new));

    private final IsleSettings isles;

    public DriftingIsleStructure(StructureSettings settings, IsleSettings isles) {
        super(settings);
        this.isles = isles;
    }

    public IsleSettings isles() {
        return isles;
    }

    /** The resolved isles of the cluster that would start in {@code chunk} (empty when the main isle has no room). */
    public static List<IsleSpec> cluster(long worldSeed, ChunkPos chunk, IsleSettings s, IsleLayout.HeightSampler terrain) {
        boolean shrine = IsleLayout.shrineRoll(worldSeed, chunk.x, chunk.z, s.windShrineChance());
        List<IsleLayout.Plan> plan = IsleLayout.plan(IsleLayout.clusterSeed(worldSeed, chunk.x, chunk.z), chunk.getMiddleBlockX(),
            chunk.getMiddleBlockZ(), s, shrine);
        return IsleLayout.resolve(plan, s, terrain);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        IsleLayout.HeightSampler terrain = (x, z) -> ctx.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
            ctx.heightAccessor(), ctx.randomState());
        List<IsleSpec> specs = cluster(ctx.seed(), ctx.chunkPos(), isles, terrain);
        if (specs.isEmpty()) return Optional.empty();
        IsleSpec main = specs.get(0);
        return Optional.of(new GenerationStub(new BlockPos(main.x(), main.top(), main.z()), b -> {
            for (IsleSpec s : specs) b.addPiece(new IslePiece(s));
        }));
    }

    @Override
    public StructureType<?> type() {
        return IsleRegistry.DRIFTING_ISLE.get();
    }
}
