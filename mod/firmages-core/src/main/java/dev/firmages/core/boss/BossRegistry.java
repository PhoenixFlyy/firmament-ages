package dev.firmages.core.boss;

import dev.firmages.core.FirmagesCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Primordial, the end boss of The Origin (docs/primordial.md): its entity, the two projectiles, the consecrated
 * shard it sheds, the Firmament Ward the shard grants, the cosmetic Firmament Glass and the sounds (sounds.json maps
 * them onto vanilla events).
 */
public final class BossRegistry {
    private BossRegistry() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, FirmagesCore.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FirmagesCore.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FirmagesCore.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, FirmagesCore.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FirmagesCore.MOD_ID);

    /** 6 x 7 blocks: the halo (radius about 3) and every node lie inside, so a shot at a node meets the hitbox. */
    public static final DeferredHolder<EntityType<?>, EntityType<PrimordialEntity>> PRIMORDIAL = ENTITY_TYPES.register("primordial",
        () -> EntityType.Builder.of(PrimordialEntity::new, MobCategory.MONSTER).sized(6.0F, 7.0F).fireImmune()
            .clientTrackingRange(10).updateInterval(1).build("primordial"));
    public static final DeferredHolder<EntityType<?>, EntityType<StarFallEntity>> STAR_FALL = ENTITY_TYPES.register("star_fall",
        () -> EntityType.Builder.<StarFallEntity>of(StarFallEntity::new, MobCategory.MISC).sized(0.6F, 0.6F).fireImmune()
            .clientTrackingRange(8).updateInterval(1).build("star_fall"));
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownShard>> THROWN_SHARD = ENTITY_TYPES.register("thrown_shard",
        () -> EntityType.Builder.<ThrownShard>of(ThrownShard::new, MobCategory.MISC).sized(0.25F, 0.25F)
            .clientTrackingRange(4).updateInterval(10).build("thrown_shard"));

    public static final DeferredItem<ConsecratedShardItem> CONSECRATED_SHARD = ITEMS.register("consecrated_shard",
        () -> new ConsecratedShardItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));

    /** Purely cosmetic: glass with the constellation of the Firmament (recipe: one shard, four glass). */
    public static final DeferredBlock<TransparentBlock> FIRMAMENT_GLASS = BLOCKS.register("firmament_glass",
        () -> new TransparentBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).mapColor(MapColor.COLOR_PURPLE)));
    public static final DeferredItem<BlockItem> FIRMAMENT_GLASS_ITEM = ITEMS.registerSimpleBlockItem("firmament_glass", FIRMAMENT_GLASS);

    public static final DeferredHolder<MobEffect, MobEffect> FIRMAMENT_WARD = EFFECTS.register("firmament_ward", FirmamentWardEffect::new);

    public static final DeferredHolder<SoundEvent, SoundEvent> IDLE = sound("primordial.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEIL_OPEN = sound("primordial.veil_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEIL_CLOSE = sound("primordial.veil_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAM_TELEGRAPH = sound("primordial.slam_telegraph");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAM = sound("primordial.slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE = sound("primordial.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANTERNS_OUT = sound("primordial.lanterns_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEAM = sound("primordial.beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> PULL = sound("primordial.pull");
    public static final DeferredHolder<SoundEvent, SoundEvent> STAR_FALL_IMPACT = sound("primordial.star_fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHATTER = sound("primordial.shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHARD = sound("primordial.shard");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = sound("primordial.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH_STAR = sound("primordial.death_star");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, name)));
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        EFFECTS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(BossRegistry::attributes);
        modBus.addListener(BossRegistry::creativeTab);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(PRIMORDIAL.get(), PrimordialEntity.createAttributes().build());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(CONSECRATED_SHARD.get());
        if (event.getTabKey() == CreativeModeTabs.COLORED_BLOCKS) event.accept(FIRMAMENT_GLASS_ITEM.get());
    }
}
