package dev.firmages.core.age;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * m1 and m3 in Java (SPEC §5): the three block tags that follow the Ages, rebound on the block registry after every
 * datapack load and on every Age swap. Ore blocks of a locked Age ({@code firmages:age_blocks/<age>}) leave
 * {@code tfc:prospectable} (TFC propick, Precision Prospecting hammer and drill) and
 * {@code precisionprospecting:prospectable_mineral} (its mineral prospector), unless {@code prospecting.enabled} is
 * false, and join {@code mekanism:miner_blacklist} (Digital Miner search and mining). All three are read live by those
 * tools, so the server needs no tag sync to the clients. The tags' load content (datapacks plus KubeJS, now without any
 * Age filter) is kept from the load and the Age-filtered version is bound with {@code Registry#bindTags} (the whole
 * block tag map, three entries replaced), which is what the load itself does.
 */
public final class AgeOreTags {
    public static final TagKey<Block> TFC_PROSPECTABLE = tag("tfc", "prospectable");
    public static final TagKey<Block> PP_PROSPECTABLE = tag("precisionprospecting", "prospectable_mineral");
    public static final TagKey<Block> MINER_BLACKLIST = tag("mekanism", "miner_blacklist");
    private static final List<TagKey<Block>> PROSPECT_TAGS = List.of(TFC_PROSPECTABLE, PP_PROSPECTABLE);

    /** Members of the three tags as the last datapack load bound them. */
    private static volatile Map<TagKey<Block>, List<Holder<Block>>> base = Map.of();

    /** What one {@link #apply} did. */
    public record Result(boolean changed, int locked, Map<String, Integer> sizes, long millis) {}

    private AgeOreTags() {}

    private static TagKey<Block> tag(String ns, String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ns, path));
    }

    /**
     * Mixin hook: the tags of a datapack load were just bound ({@code ReloadableServerResources#updateRegistryTags},
     * before {@code TagsUpdatedEvent}). Keeps their content and binds the version for the load's Age snapshot.
     */
    public static void onTagsBound(RegistryAccess access) {
        try {
            Registry<Block> reg = access.registryOrThrow(Registries.BLOCK);
            Map<TagKey<Block>, List<Holder<Block>>> b = new HashMap<>();
            for (TagKey<Block> key : List.of(TFC_PROSPECTABLE, PP_PROSPECTABLE, MINER_BLACKLIST)) {
                b.put(key, reg.getTag(key).map(set -> set.stream().toList()).orElse(List.of()));
            }
            base = Map.copyOf(b);
            Result r = apply(access, AgeService.snapshotForReload());
            FirmagesCore.LOGGER.info("Age ore tags after the datapack load: {} locked ore blocks, {} ({} ms)", r.locked(), r.sizes(), r.millis());
        } catch (RuntimeException e) {
            FirmagesCore.LOGGER.error("Age ore tags: cannot bind the Age-filtered prospecting and miner tags", e);
        }
    }

    /** Binds the three tags for {@code snap}. Server thread (or the load's apply phase). */
    public static Result apply(RegistryAccess access, AgeSnapshot snap) {
        long t0 = System.nanoTime();
        Registry<Block> reg = access.registryOrThrow(Registries.BLOCK);
        Set<Block> locked = lockedBlocks(AgeIndex.current(), snap);
        boolean prospect = ServerConfig.prospectingEnabled();
        Map<TagKey<Block>, List<Holder<Block>>> want = new HashMap<>();
        for (TagKey<Block> key : PROSPECT_TAGS) {
            List<Holder<Block>> members = base.getOrDefault(key, List.of());
            want.put(key, prospect ? members.stream().filter(h -> !locked.contains(h.value())).toList() : members);
        }
        LinkedHashSet<Holder<Block>> blacklist = new LinkedHashSet<>(base.getOrDefault(MINER_BLACKLIST, List.of()));
        for (Block b : locked) blacklist.add(reg.wrapAsHolder(b));
        want.put(MINER_BLACKLIST, List.copyOf(blacklist));

        boolean changed = false;
        for (Map.Entry<TagKey<Block>, List<Holder<Block>>> e : want.entrySet()) {
            List<Holder<Block>> now = reg.getTag(e.getKey()).map(set -> set.stream().toList()).orElse(null);
            if (now == null || !sameMembers(now, e.getValue())) changed = true;
        }
        if (changed) {
            Map<TagKey<Block>, List<Holder<Block>>> all = new HashMap<>();
            reg.getTags().forEach(p -> all.put(p.getFirst(), p.getSecond().stream().toList()));
            all.putAll(want);
            reg.bindTags(all);
        }
        Map<String, Integer> sizes = want.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey().location().toString(), e -> e.getValue().size(), (a, b) -> a, java.util.TreeMap::new));
        return new Result(changed, locked.size(), sizes, (System.nanoTime() - t0) / 1_000_000L);
    }

    /** Blocks of {@code firmages:age_blocks/<age>} of every locked Age. */
    static Set<Block> lockedBlocks(AgeIndex idx, AgeSnapshot snap) {
        Set<Block> out = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        idx.blocks().forEach((block, age) -> {
            if (!snap.isUnlocked(age)) out.add(block);
        });
        return out;
    }

    private static boolean sameMembers(List<Holder<Block>> a, List<Holder<Block>> b) {
        if (a.size() != b.size()) return false;
        Set<Block> s = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Holder<Block> h : a) s.add(h.value());
        for (Holder<Block> h : b) if (!s.contains(h.value())) return false;
        return true;
    }

    /** Diagnostics: the load content of the three tags (sizes). */
    public static Map<String, Integer> baseSizes() {
        Map<String, Integer> out = new java.util.TreeMap<>();
        base.forEach((k, v) -> out.put(k.location().toString(), v.size()));
        return out;
    }
}
