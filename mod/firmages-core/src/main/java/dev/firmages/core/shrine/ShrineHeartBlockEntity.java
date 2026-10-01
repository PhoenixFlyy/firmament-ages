package dev.firmages.core.shrine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * State of the heart (SPEC §2.5): prayer progress, the praying players (transient), the awakening timer, the
 * blocks used for {@code interact} rites since the last awakening, and the cached ring validation. Relics live on
 * the plinths ({@link OfferingPlinthBlockEntity}); the server logic is in {@link ShrineService}.
 */
public final class ShrineHeartBlockEntity extends BlockEntity {
    int prayerProgress;
    long awakeningUntil;
    final Set<String> interacted = new LinkedHashSet<>();
    /** Player -> game time of the last prayer use. */
    final Map<UUID, Long> prayers = new HashMap<>();
    /** Player -> game time of the last refusal or info message (spam guard while use is held). */
    final Map<UUID, Long> lastMessage = new HashMap<>();
    /** Ring validation cache: rotation of each complete ring (index = tier), null = incomplete. */
    Rotation[] ringRotations = new Rotation[0];
    int validRing = -1;
    boolean validationDue = true;
    long nextValidation;

    public ShrineHeartBlockEntity(BlockPos pos, BlockState state) {
        super(ShrineRegistry.SHRINE_HEART_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ShrineHeartBlockEntity be) {
        ShrineService.tickHeart((ServerLevel) level, pos, state, be);
    }

    public int prayerProgress() {
        return prayerProgress;
    }

    public int prayingCount() {
        return prayers.size();
    }

    public int validRing() {
        return validRing;
    }

    public boolean awakening(long gameTime) {
        return gameTime < awakeningUntil;
    }

    public Set<String> interacted() {
        return Set.copyOf(interacted);
    }

    /** Marks the cached validation stale; the next server tick validates again. */
    public void invalidate() {
        validationDue = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("prayerProgress", prayerProgress);
        tag.putLong("awakeningUntil", awakeningUntil);
        ListTag list = new ListTag();
        interacted.forEach(s -> list.add(StringTag.valueOf(s)));
        tag.put("interacted", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        prayerProgress = tag.getInt("prayerProgress");
        awakeningUntil = tag.getLong("awakeningUntil");
        interacted.clear();
        ListTag list = tag.getList("interacted", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) interacted.add(list.getString(i));
        validationDue = true;
    }
}
