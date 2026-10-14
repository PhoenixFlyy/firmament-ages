package dev.firmages.core.shrine;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.ceremony.CeremonyService;
import dev.firmages.core.immersion.ImmersionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The Gathering (SPEC §7.5): in the Singularity Age the team prays once more at the intact shrine with all nine
 * relics enshrined, and Caelum gives the relics back for the last forging (the Ultimate Singularity takes the nine
 * items). Same prayer as an Age (sneak and hold use on the heart, 10 s / n, at least 4 s); the only rite is the
 * chorus (two players together once two are online). On completion every relic leaves its plinth the way a lent
 * relic does (record kept and marked lent, the plinth keeps its glow and takes the relic back), the nine items drop
 * above the heart, the heart shows {@code awakened=10} and the GATHERING ceremony plays. A relic laid back can be
 * taken again by sneak-use with an empty hand, so nothing can be lost. No Age is granted, nothing reloads.
 */
public final class ShrineGathering {
    private ShrineGathering() {}

    /** Which tiers' relics rest on their plinths now and which are lent out (records checked against the plinths). */
    record Relics(Set<Integer> present, Set<Integer> lent, List<ShrineSavedData.Relic> enshrined) {}

    static Relics relics(ServerLevel level) {
        Set<Integer> present = new HashSet<>();
        Set<Integer> lent = new HashSet<>();
        List<ShrineSavedData.Relic> enshrined = new ArrayList<>();
        for (ShrineSavedData.Relic r : ShrineSavedData.get(level.getServer()).relics().values()) {
            if (r.lent()) {
                lent.add(r.tier());
            } else if (level.isLoaded(r.plinth()) && level.getBlockEntity(r.plinth()) instanceof OfferingPlinthBlockEntity pb && pb.isRelic()) {
                if (present.add(r.tier())) enshrined.add(r);
            }
        }
        enshrined.sort(Comparator.comparingInt(ShrineSavedData.Relic::tier));
        return new Relics(present, lent, enshrined);
    }

    /** True while the Gathering is the shrine's work: age_9 is the highest Age and it has not happened yet. */
    public static boolean pending(MinecraftServer s) {
        return ShrineRules.highest(ShrineService.unlocked(s)) == AgeId.AGE_9 && !ShrineSavedData.get(s).gathered();
    }

    /**
     * Why the Gathering prayer is not heard now, or empty when it is. Call after a validation (the intact flag).
     *
     * @param chorus also check that enough players pray together
     */
    static Optional<Component> refusal(ServerLevel level, ShrineService.Heart h, boolean chorus, boolean sendPreview, @Nullable ServerPlayer player) {
        MinecraftServer s = level.getServer();
        ShrineSavedData sd = ShrineSavedData.get(s);
        Relics relics = relics(level);
        Optional<ShrineRules.GatheringBlock> block = ShrineRules.gatheringBlock(ShrineService.unlocked(s), sd.gathered(),
            ShrineConsecration.maintenanceActive(s), sd.intact(), relics.present(), relics.lent());
        if (block.isPresent()) {
            return Optional.of(switch (block.get()) {
                case NOT_SINGULARITY -> ShrineService.msg("firmages.shrine.sleeping");
                case DONE -> ShrineService.msg("firmages.shrine.gathered");
                case MAINTENANCE -> ShrineService.msg("firmages.shrine.maintenance.no_prayer");
                case BROKEN -> {
                    int ring = ShrineService.firstIncompleteRing(h.be()).orElse(ShrineData.MAX_TIER);
                    if (sendPreview && player != null) ShrineService.sendPreview(player, h, ring);
                    yield ShrineService.ruinsMessage(level, h, ring);
                }
                case RELIC_LENT -> ShrineService.msg("firmages.shrine.gathering.lent");
                case RELIC_MISSING -> ShrineService.msg("firmages.shrine.gathering.missing",
                    ShrineService.ageName(AgeId.all().get(ShrineRules.firstMissingRelic(relics.present()).orElse(0) + 1)));
            });
        }
        if (chorus) {
            int need = ShrineRules.gatheringChorus(s.getPlayerCount());
            if (h.be().prayers.size() < need) return Optional.of(ShrineService.msg("firmages.shrine.rite.chorus", need));
        }
        return Optional.empty();
    }

    /** The prayer is heard: the nine relics come off their plinths and drop above the heart. */
    static void complete(ShrineService.Heart h, List<String> by) {
        ServerLevel level = h.level();
        MinecraftServer s = level.getServer();
        ShrineSavedData sd = ShrineSavedData.get(s);
        double x = h.pos().getX() + 0.5;
        double y = h.pos().getY() + 1.2;
        double z = h.pos().getZ() + 0.5;
        int released = 0;
        for (ShrineSavedData.Relic r : relics(level).enshrined()) {
            if (!(level.getBlockEntity(r.plinth()) instanceof OfferingPlinthBlockEntity pb)) continue;
            ItemStack out = pb.lend();
            sd.putRelic(new ShrineSavedData.Relic(r.plinth(), r.tier(), r.item(), true));
            ItemEntity e = new ItemEntity(level, x, y, z, out, 0, 0, 0);
            e.setUnlimitedLifetime(); // a relic never despawns
            e.setInvulnerable(true); // nor burns, nor explodes
            e.setPickUpDelay(20);
            level.addFreshEntity(e);
            BlockPos p = r.plinth();
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 25, 0.3, 0.4, 0.3, 0.02);
            released++;
        }
        sd.setGathered(true);
        h.be().prayerProgress = 0;
        h.be().prayers.clear();
        h.be().interacted.clear();
        h.be().awakeningUntil = level.getGameTime() + ShrineService.AWAKENING_TICKS;
        h.be().setChanged();
        FirmagesCore.LOGGER.info("Shrine: the Gathering is heard (by {}): {} relic(s) released above the heart at {}", by, released, h.pos());
        CeremonyService.startGathering(s, GlobalPos.of(level.dimension(), h.pos()));
        ImmersionService.broadcast(s); // the HUD goal turns from "the Gathering" to "Beyond the Firmament"
        h.be().invalidate(); // awakened=10 on the next validation
    }
}
