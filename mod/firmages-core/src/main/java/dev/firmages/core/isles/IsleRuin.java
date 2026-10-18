package dev.firmages.core.isles;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The small ruin of an isle cluster (G3, Felix I1B, SPEC §30.13): template {@code firmages:isle_ruin}
 * ({@code dev/gen_isle_ruin.py}), 7 by 7 by 7 blocks, its layer {@link #CORE_Y} lying in the isle's top surface. Below
 * it a crystal chamber under a sealed lid, above it four Wind Seals ({@link WindSealBlock}) and a chest
 * ({@code firmages:chests/isle_ruin}). One isle per cluster carries it: the first isle (main isle first) with a free
 * site ({@link IsleRuinSite#site}). The site is a pure function of the isle's spec and never changes the isle's body: it keeps
 * clear of the trees, the hollow and the Wind Shrine's flat top.
 */
public final class IsleRuin {
    public static final ResourceLocation TEMPLATE = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "isle_ruin");
    /** Half the template's footprint (7 by 7). */
    public static final int HALF = IsleRuinSite.HALF;
    /** The template layer that lies in the isle's top surface (the lid layer). */
    public static final int CORE_Y = IsleRuinSite.CORE_Y;

    private IsleRuin() {}

    /** The ruin's core: the centre of its lid layer, in the isle's top surface. */
    public static Optional<BlockPos> core(IsleSpec s) {
        return IsleRuinSite.site(s).map(c -> new BlockPos(s.x() + c[0], s.top(), s.z() + c[1]));
    }

    public static Rotation rotation(IsleSpec s) {
        return Rotation.values()[(int) Math.floorMod(IsleLayout.mix(s.seed(), 0x2B1), 4)];
    }

    /** The ruin's box in the world (for the fallback placer's slices). */
    public static Optional<BoundingBox> box(StructureTemplate t, IsleSpec s) {
        return core(s).map(core -> t.getBoundingBox(new StructurePlaceSettings().setRotation(rotation(s)), origin(core, s)));
    }

    static BlockPos origin(BlockPos core, IsleSpec s) {
        return core.subtract(new BlockPos(HALF, CORE_Y, HALF).rotate(rotation(s)));
    }

    /** Places the ruin on its isle, clipped to {@code clip} (a chunk during worldgen, a slice, or null for all of it). */
    public static boolean place(ServerLevelAccessor level, IsleSpec s, @Nullable BoundingBox clip) {
        if (!s.ruin()) return false;
        Optional<BlockPos> core = core(s);
        Optional<StructureTemplate> t = level.getLevel().getStructureManager().get(TEMPLATE);
        if (core.isEmpty() || t.isEmpty()) {
            if (t.isEmpty()) FirmagesCore.LOGGER.warn("Drifting Isles: no template {}; no ruin at {} {}", TEMPLATE, s.x(), s.z());
            return false;
        }
        RandomSource rand = RandomSource.create(IsleLayout.mix(s.seed(), 0x2B2));
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation(s)).setRandom(rand);
        if (clip != null) settings.setBoundingBox(clip);
        BlockPos origin = origin(core.get(), s);
        t.get().placeInWorld(level, origin, origin, settings, rand, 2);
        return true;
    }
}
