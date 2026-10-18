package dev.firmages.core.hardship;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.immersion.AgeStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.io.Reader;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Creeping Darkness (SPEC §19.7, S1f): hostile mobs that spawn in the dark get the night row of the world's Age
 * ({@link Nights}, {@code data/firmages/balance/nights.json}). {@code FinalizeSpawnEvent} decides and marks the mob
 * (it fires before {@code Mob.finalizeSpawn}, whose own equipment would overwrite ours); {@code EntityJoinLevelEvent}
 * of the fresh mob then adds the health and damage modifiers, fills its health, puts on the gear in empty slots and,
 * for a natural spawn, queues the extra night spawns. Only monsters (category MONSTER or {@link Enemy}), never a boss
 * ({@code c:bosses}) or the Cave Dweller, only natural, chunk, structure, patrol, reinforcement, jockey and event
 * spawns (not spawners, eggs, commands, conversions). Day and sky-lit spawns stay vanilla.
 */
public final class NightsService {
    public static final ResourceLocation TABLE = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "balance/nights.json");
    public static final ResourceLocation HEALTH_ID = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "night_health");
    public static final ResourceLocation DAMAGE_ID = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "night_damage");
    /** Persistent-data key between the two events: 1 = natural spawn (may bring extras), 2 = other dark spawn. */
    public static final String MARK = "firmages:night";
    private static final TagKey<EntityType<?>> ARMORED = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "blood_moon_armored"));
    private static final TagKey<EntityType<?>> MELEE = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "blood_moon_melee"));
    private static final Set<MobSpawnType> TYPES = Set.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION, MobSpawnType.STRUCTURE,
        MobSpawnType.PATROL, MobSpawnType.REINFORCEMENT, MobSpawnType.EVENT, MobSpawnType.JOCKEY);
    private static final Map<String, EquipmentSlot> SLOTS = Map.of("head", EquipmentSlot.HEAD, "chest", EquipmentSlot.CHEST,
        "legs", EquipmentSlot.LEGS, "feet", EquipmentSlot.FEET, "mainhand", EquipmentSlot.MAINHAND);

    private static volatile ResourceManager cachedFor;
    private static volatile Nights cached = Nights.parse(new JsonObject());

    private NightsService() {}

    public static void register(IEventBus gameBus) {
        gameBus.addListener(NightsService::onFinalize);
        gameBus.addListener(NightsService::onJoin);
    }

    /** True when the table is in the server's resources. */
    public static boolean tablePresent(MinecraftServer s) {
        return s.getResourceManager().getResource(TABLE).isPresent();
    }

    /** The table of the server's current resources (read again after a datapack reload); missing = neutral rows. */
    public static Nights table(MinecraftServer s) {
        ResourceManager rm = s.getResourceManager();
        if (rm != cachedFor) {
            Nights t = Nights.parse(new JsonObject());
            Optional<Resource> r = rm.getResource(TABLE);
            if (r.isPresent()) {
                try (Reader reader = r.get().openAsReader()) {
                    t = Nights.parse(JsonParser.parseReader(reader).getAsJsonObject());
                } catch (Exception e) {
                    FirmagesCore.LOGGER.warn("Nights: cannot read {}", TABLE, e);
                }
            } else {
                FirmagesCore.LOGGER.warn("Nights: {} missing, nights stay vanilla", TABLE);
            }
            cached = t;
            cachedFor = rm;
        }
        return cached;
    }

    /** The night row of the world's Age. */
    public static Nights.Row row(MinecraftServer s) {
        return table(s).row(AgeStatus.current(AgeService.state(s).snapshot()));
    }

    public static boolean eligible(Mob mob, MobSpawnType type) {
        return TYPES.contains(type)
            && (mob.getType().getCategory() == MobCategory.MONSTER || mob instanceof Enemy)
            && !mob.getType().is(Tags.EntityTypes.BOSSES)
            && !CaveDwellerService.isDweller(mob);
    }

    public static boolean dark(ServerLevel level, BlockPos pos) {
        return Nights.dark(level.getBrightness(LightLayer.SKY, pos), level.dimensionType().hasSkyLight(), level.isNight());
    }

    static void onFinalize(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        ServerLevel level = event.getLevel().getLevel();
        if (!eligible(mob, event.getSpawnType()) || !dark(level, BlockPos.containing(event.getX(), event.getY(), event.getZ()))) return;
        mob.getPersistentData().putByte(MARK, (byte) (event.getSpawnType() == MobSpawnType.NATURAL ? 1 : 2));
    }

    static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof Mob mob) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!mob.getPersistentData().contains(MARK)) return;
        boolean natural = mob.getPersistentData().getByte(MARK) == 1;
        mob.getPersistentData().remove(MARK);
        Nights.Row row = row(level.getServer());
        apply(mob, row);
        if (natural) {
            int extra = Nights.extraSpawns(row.nightSpawn(), level.random.nextDouble());
            if (extra > 0) queueExtras(level, mob.getType(), mob.blockPosition(), extra);
        }
    }

    /** Puts a row on a mob: modifiers, full health, gear in empty slots (armour for the armoured tag, a sword for the melee tag). */
    public static void apply(Mob mob, Nights.Row row) {
        modifier(mob, Attributes.MAX_HEALTH, HEALTH_ID, row.health());
        modifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, row.damage());
        mob.setHealth(mob.getMaxHealth());
        if (row.gearChance() <= 0) return;
        boolean armored = mob.getType().is(ARMORED), melee = mob.getType().is(MELEE);
        for (Map.Entry<String, String> g : row.gearItems().entrySet()) {
            EquipmentSlot slot = SLOTS.get(g.getKey());
            if (slot == EquipmentSlot.MAINHAND ? !melee : !armored) continue;
            if (!mob.getItemBySlot(slot).isEmpty() || mob.getRandom().nextDouble() >= row.gearChance()) continue;
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(g.getValue()));
            item.ifPresent(i -> mob.setItemSlot(slot, new ItemStack(i)));
        }
    }

    private static void modifier(Mob mob, Holder<Attribute> attribute, ResourceLocation id, double factor) {
        AttributeInstance inst = mob.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(id);
        if (factor > 1) inst.addPermanentModifier(new AttributeModifier(id, factor - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    /** Extra night spawns next tick (never inside the join event); spawned as EVENT, so they get the row but bring no extras. */
    private static void queueExtras(ServerLevel level, EntityType<?> type, BlockPos pos, int n) {
        MinecraftServer s = level.getServer();
        s.tell(new TickTask(s.getTickCount() + 1, () -> {
            for (int i = 0; i < n; i++) type.spawn(level, pos, MobSpawnType.EVENT);
        }));
    }
}
