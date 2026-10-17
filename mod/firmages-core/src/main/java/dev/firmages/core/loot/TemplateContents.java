package dev.firmages.core.loot;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeIndex;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.nbt.CompoundTag;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Prefilled containers and entities of structure templates (SPEC §19.5): {@code mixin/StructureTemplateMixin} hands
 * every block entity's data that {@code StructureTemplate.placeInWorld} loads through {@link #filter} and every
 * entity's data that {@code addEntitiesToWorld} creates through {@link #filterEntity}. Whatever the block entity
 * (chests, barrels, TFC vessels, piles and racks, pedestals) or entity (armor stands, mobs, item frames, items on the
 * ground, chest minecarts, donkeys), the stacks of an Age the server does not hold and every disabled stack go, nested
 * contents included ({@link PlacedContents}). Works on the data before the load, so world generation threads touch no
 * block entity, entity, capability or chunk. The template's own data stays unchanged (a copy is filtered).
 */
public final class TemplateContents {
    private static final AtomicLong PLACED = new AtomicLong();
    private static final AtomicLong REMOVED = new AtomicLong();
    private static final AtomicLong ENTITIES = new AtomicLong();

    private TemplateContents() {}

    /** The data the block entity loads: unchanged, or a copy without the stacks of the Ages the server lacks. */
    public static CompoundTag filter(CompoundTag nbt) {
        Predicate<String> drop = serverDrop(nbt);
        if (drop == null) return nbt;
        PLACED.incrementAndGet();
        if (PlacedContents.filter(nbt, NbtContents.NODES, drop, false) == 0) return nbt;
        CompoundTag copy = nbt.copy();
        REMOVED.addAndGet(PlacedContents.filter(copy, NbtContents.NODES, drop, true));
        return copy;
    }

    /**
     * The data of an entity the template places, filtered in place ({@code addEntitiesToWorld} hands over its own copy
     * of the template's entry): without the stacks of the Ages the server lacks, a villager's trades aside
     * ({@link PlacedContents#ENTITY_SKIP}). Null when the entity is an item entity whose stack went: nothing is placed.
     */
    public static CompoundTag filterEntity(CompoundTag nbt) {
        Predicate<String> drop = serverDrop(nbt);
        if (drop == null) return nbt;
        ENTITIES.incrementAndGet();
        int removed = PlacedContents.filterEntity(nbt, NbtContents.NODES, drop, true);
        if (removed == 0) return nbt;
        REMOVED.addAndGet(removed);
        return PlacedContents.itemEntityEmpty(nbt, NbtContents.NODES) ? null : nbt;
    }

    /** What goes under the server's Ages, or null when nothing can go (no data, {@code gate.lootFilter} off, no index). */
    private static Predicate<String> serverDrop(CompoundTag nbt) {
        if (nbt == null || !ServerConfig.lootFilter()) return null;
        AgeIndex idx = AgeIndex.current();
        if (idx.items().isEmpty() && idx.disabledItems().isEmpty()) return null;
        Set<AgeId> unlocked = AgeService.currentSnapshot().unlocked();
        return id -> NbtContents.dropped(id, idx, unlocked);
    }

    /** Block entities and entities placed by templates and stacks removed since start (diagnostics). */
    public static long[] counts() {
        return new long[] {PLACED.get(), REMOVED.get(), ENTITIES.get()};
    }
}
