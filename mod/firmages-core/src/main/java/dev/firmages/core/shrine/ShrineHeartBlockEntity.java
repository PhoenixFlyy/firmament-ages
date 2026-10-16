package dev.firmages.core.shrine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * State of the heart (SPEC §2.5): prayer progress, the praying players (transient), the awakening timer, the
 * blocks used for {@code interact} rites since the last awakening, the FE drawn for an {@code energy_consume} rite
 * (SPEC §36) and the cached ring validation. Relics live on the plinths ({@link OfferingPlinthBlockEntity}); the
 * server logic is in {@link ShrineService}. Synced to the client for Jade: the proof of work of the current offering
 * and the FE drawn and needed (SPEC §35, §36), refreshed once a second.
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
    /** A second heart that is not the shrine (checked once a second); it neither validates nor hears prayers. */
    boolean inert;
    long nextValidation;
    /** FE drawn for {@link #drawnFor} ({@code tier_<N>} or {@code gathering}, SPEC §36); spent when the prayer is heard. */
    long drawn;
    String drawnFor = "";
    /** Cached once a second by the server tick: FE the current work still takes (0 = the heart takes none). */
    long drawRoom;
    /** The drawn value the clients last saw. */
    long syncedDrawn = -1;
    // synced for Jade (server: the last values sent)
    long drawNeed;
    long proofHave;
    long proofNeed;
    String proofItem = "";
    private final IEnergyStorage energy = new Sink();

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

    public long drawn() {
        return drawn;
    }

    public long drawNeed() {
        return drawNeed;
    }

    public long proofHave() {
        return proofHave;
    }

    public long proofNeed() {
        return proofNeed;
    }

    /** Item id the proof line names (empty: no proof for the current offering). */
    public String proofItem() {
        return proofItem;
    }

    /** The heart's FE input (SPEC §36): receive-only, null-side and every side. */
    public IEnergyStorage energy() {
        return energy;
    }

    /** Sets the synced Jade values; true when one changed (the caller sends a block update). */
    boolean setSynced(long drawNeed, long proofHave, long proofNeed, String proofItem) {
        boolean changed = drawNeed != this.drawNeed || proofHave != this.proofHave || proofNeed != this.proofNeed || !proofItem.equals(this.proofItem);
        this.drawNeed = drawNeed;
        this.proofHave = proofHave;
        this.proofNeed = proofNeed;
        this.proofItem = proofItem;
        return changed;
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
        tag.putLong("drawn", drawn);
        tag.putString("drawnFor", drawnFor);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("drawn", drawn);
        tag.putLong("drawNeed", drawNeed);
        tag.putLong("proofHave", proofHave);
        tag.putLong("proofNeed", proofNeed);
        tag.putString("proofItem", proofItem);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        prayerProgress = tag.getInt("prayerProgress");
        awakeningUntil = tag.getLong("awakeningUntil");
        interacted.clear();
        ListTag list = tag.getList("interacted", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) interacted.add(list.getString(i));
        drawn = tag.getLong("drawn");
        drawnFor = tag.getString("drawnFor");
        // the client's Jade values (absent from the saved data, so a server load keeps 0 until the first refresh)
        drawNeed = tag.getLong("drawNeed");
        proofHave = tag.getLong("proofHave");
        proofNeed = tag.getLong("proofNeed");
        proofItem = tag.getString("proofItem");
        validationDue = true;
    }

    /** Takes FE while the shrine's current work has an unmet {@code energy_consume} rite; never gives any back. */
    private final class Sink implements IEnergyStorage {
        @Override
        public int receiveEnergy(int max, boolean simulate) {
            if (level == null || level.isClientSide || max <= 0 || drawRoom <= 0) return 0;
            int take = (int) Math.min(max, drawRoom);
            if (!simulate) {
                drawn += take;
                drawRoom -= take;
                setChanged();
            }
            return take;
        }

        @Override
        public int extractEnergy(int max, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, drawn);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, drawn + Math.max(0, drawRoom));
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return drawRoom > 0;
        }
    }
}
