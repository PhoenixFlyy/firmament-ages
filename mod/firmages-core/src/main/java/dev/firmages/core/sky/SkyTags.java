package dev.firmages.core.sky;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

/** Tags of the sky features (SPEC §25.3, §30.4); the values ship in the mod jar, the pack can add to them. */
public final class SkyTags {
    private SkyTags() {}

    /** Biomes where no star lands: oceans, rivers and lakes (TFC biome ids as optional entries). */
    public static final TagKey<Biome> NO_IMPACT = TagKey.create(Registries.BIOME, rl("starfall/no_impact"));
    /** Block entities that belong to the land (TFC tick counters, bushes, saplings, beehives): no reason to move a crater. */
    public static final TagKey<Block> NATURAL_BLOCK_ENTITIES = TagKey.create(Registries.BLOCK, rl("starfall/natural_block_entities"));
    /** Blocks only players place (planks, bricks, glass, rails, worked stone): a crater moves away from them. */
    public static final TagKey<Block> PLAYER_BLOCKS = TagKey.create(Registries.BLOCK, rl("starfall/player_blocks"));
    /** The body of a Drifting Isle (skystone, crystal cluster, lode; SPEC §30.4): no crater under an isle. */
    public static final TagKey<Block> ISLE_BODY = TagKey.create(Registries.BLOCK, rl("isle/body"));

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, path);
    }
}
