package dev.firmages.core.ark;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The nine islands' {@link ArkProgress}, saved as {@code ark.age_0} .. {@code ark.age_8} in {@code firmages_origin}. */
public final class ArkState {
    private final List<ArkProgress> islands = new ArrayList<>();
    private final Runnable dirty;

    public ArkState(Runnable dirty) {
        this.dirty = dirty;
        for (int i = 0; i < ArkData.ISLANDS; i++) islands.add(new ArkProgress());
    }

    public ArkProgress island(int i) {
        return islands.get(i);
    }

    public List<ArkProgress> all() {
        return islands;
    }

    public void setDirty() {
        dirty.run();
    }

    public int completeMask() {
        return ArkRules.completeMask(islands);
    }

    public void load(CompoundTag t) {
        for (int i = 0; i < ArkData.ISLANDS; i++) {
            islands.set(i, loadIsland(t.contains("age_" + i) ? t.getCompound("age_" + i) : null));
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        for (int i = 0; i < ArkData.ISLANDS; i++) t.put("age_" + i, saveIsland(islands.get(i)));
        return t;
    }

    /** One island's progress as NBT. */
    static CompoundTag saveIsland(ArkProgress p) {
        CompoundTag t = new CompoundTag();
        for (String g : p.groups) t.putBoolean(g, true);
        CompoundTag f = new CompoundTag();
        p.flora.forEach(f::putInt);
        t.put("flora_items", f);
        CompoundTag c = new CompoundTag();
        p.crown.forEach((k, v) -> c.putInt(String.valueOf(k), v));
        t.put("crown_items", c);
        if (!p.faunaEntity.isEmpty()) t.putString("fauna_entity", p.faunaEntity);
        t.putInt("stage", p.stage);
        t.putBoolean("complete", p.complete);
        if (p.plinth != Long.MIN_VALUE) t.putLong("plinth", p.plinth);
        // Grown blocks: positions as one long array and a palette of block ids with one index per position.
        Map<String, Integer> palette = new LinkedHashMap<>();
        long[] pos = new long[p.grown.size()];
        int[] idx = new int[p.grown.size()];
        int i = 0;
        for (Map.Entry<Long, String> e : p.grown.entrySet()) {
            pos[i] = e.getKey();
            idx[i] = palette.computeIfAbsent(e.getValue(), k -> palette.size());
            i++;
        }
        ListTag pal = new ListTag();
        for (String id : palette.keySet()) pal.add(StringTag.valueOf(id));
        t.putLongArray("grown_pos", pos);
        t.putIntArray("grown_idx", idx);
        t.put("grown_palette", pal);
        return t;
    }

    /** One island's progress from NBT ({@code null}: nothing yet). */
    static ArkProgress loadIsland(@Nullable CompoundTag t) {
        ArkProgress p = new ArkProgress();
        if (t == null) return p;
        for (String g : ArkData.ALL_GROUPS) if (t.getBoolean(g)) p.groups.add(g);
        CompoundTag f = t.getCompound("flora_items");
        for (String k : f.getAllKeys()) p.flora.put(k, f.getInt(k));
        CompoundTag c = t.getCompound("crown_items");
        for (String k : c.getAllKeys()) {
            try {
                p.crown.put(Integer.parseInt(k), c.getInt(k));
            } catch (NumberFormatException ignored) {
                // a hand-edited key; dropped
            }
        }
        p.faunaEntity = t.getString("fauna_entity");
        p.stage = t.getInt("stage");
        p.complete = t.getBoolean("complete");
        if (t.contains("plinth", Tag.TAG_LONG)) p.plinth = t.getLong("plinth");
        long[] pos = t.getLongArray("grown_pos");
        int[] idx = t.getIntArray("grown_idx");
        ListTag pal = t.getList("grown_palette", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(pos.length, idx.length); i++) {
            if (idx[i] >= 0 && idx[i] < pal.size()) p.grown.put(pos[i], pal.getString(idx[i]));
        }
        return p;
    }
}
