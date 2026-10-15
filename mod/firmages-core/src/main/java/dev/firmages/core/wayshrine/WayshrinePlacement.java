package dev.firmages.core.wayshrine;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Places a Wayshrine ruin template by code, outside the jigsaw worldgen: the tutorial Wayshrine (§28.4) and the
 * Wind Shrine of the Drifting Isles (S6B).
 */
public final class WayshrinePlacement {
    /** The type the Drifting Isles carry (S6B, Felix 2026-10-15: it replaces the Glacier Cairn). */
    public static final String ISLE_TYPE = "wind_shrine";
    static final ResourceKey<StructureProcessorList> PROCESSORS = ResourceKey.create(Registries.PROCESSOR_LIST,
        ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "wayshrine"));

    private WayshrinePlacement() {}

    /** The ruin template of {@code type}, if its data and file exist. */
    public static Optional<StructureTemplate> template(ServerLevel level, String type) {
        return WayshrineService.data().type(type).map(t -> ResourceLocation.parse(t.structure()))
            .flatMap(rl -> level.getStructureManager().get(rl));
    }

    /** The core's place inside a template of this size: the centre of the bottom layer (see dev/gen_wayshrines.py). */
    public static BlockPos coreOffset(StructureTemplate t) {
        return new BlockPos(t.getSize().getX() / 2, 0, t.getSize().getZ() / 2);
    }

    /**
     * Places the ruin of {@code type} so that its core lands on {@code core}, turned by {@code rotation}, weathered by the
     * processor list {@code firmages:wayshrine} when it is loaded (with TFC). {@code clip} limits the blocks to one box
     * (a chunk during worldgen), null places all of them.
     *
     * @return the core position, or empty when the type or its template is missing
     */
    public static Optional<BlockPos> placeRuin(ServerLevelAccessor level, String type, BlockPos core, Rotation rotation, RandomSource random,
                                               @Nullable BoundingBox clip) {
        Optional<StructureTemplate> t = template(level.getLevel(), type);
        if (t.isEmpty()) {
            FirmagesCore.LOGGER.warn("Wayshrine {}: no ruin template; nothing placed at {}", type, core);
            return Optional.empty();
        }
        BlockPos origin = core.subtract(coreOffset(t.get()).rotate(rotation));
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setRandom(random);
        if (clip != null) settings.setBoundingBox(clip);
        level.registryAccess().registry(Registries.PROCESSOR_LIST).flatMap(r -> r.getOptional(PROCESSORS))
            .ifPresent(list -> list.list().forEach(settings::addProcessor));
        t.get().placeInWorld(level, origin, origin, settings, random, 2);
        // Outside worldgen the block entity was added before the template loaded its type: register it now.
        if (level instanceof ServerLevel sl && sl.getBlockEntity(core) instanceof WayshrineCoreBlockEntity be) WayshrineService.onCoreLoaded(sl, core, be);
        return Optional.of(core.immutable());
    }

    /**
     * Hook for the Drifting Isles (M13, SPEC-himmel §30, S6B): places the ruin of the Wind Shrine on an isle top, its core
     * at {@code floorCentre} (the core sits in the floor: one block below the walking surface). M13 calls it from its
     * isle piece's {@code postProcess} for the one top position per cluster that carries a shrine, with the chunk box as
     * {@code clip} (so each chunk of the isle writes only its own part), and from the fallback placement of existing
     * worlds with {@code clip} null. Not called anywhere before M13 merges.
     * <p>
     * The core registers itself when its chunk loads, like a worldgen ruin; the Wind Shrine then works as every other
     * type (restoring from the Industrial Age, the offering, the rite, the prayer). The pointer knows it only once it is
     * registered or cached (it has no structure of its own to search for); see the open point in the M16 notes.
     *
     * @return the core position, or empty when the Wind Shrine's data or template is missing
     */
    public static Optional<BlockPos> placeIsleShrine(ServerLevelAccessor level, BlockPos floorCentre, Rotation rotation, RandomSource random,
                                                     @Nullable BoundingBox clip) {
        return placeRuin(level, ISLE_TYPE, floorCentre, rotation, random, clip);
    }
}
