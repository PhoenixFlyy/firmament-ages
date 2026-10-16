package dev.firmages.core.shrine;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.proof.TallyData;
import dev.firmages.core.proof.TallyService;
import dev.firmages.core.proof.TallyTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Proof of work and the power sink of the late Ages at the shrine (SPEC §35, §36).
 * <ul>
 *   <li><b>Tally proof (Felix H5A):</b> from the Industrial Age an offering is refused until the fellowship's Tally
 *   Chutes took the Age's product ({@code firmages_shrine/tallies.json}). A chute takes only what the current proof
 *   still wants ({@link #room}) and keeps nothing: what it counts is consumed, so a loop or a re-fed stockpile can
 *   never count twice (review fix 0.12.0). The offering player's team counts (one fellowship per server, SPEC §2.3);
 *   without a player (heart line, status, GameTests) the team with the highest count.</li>
 *   <li><b>Energy sink ({@code energy_consume}):</b> the heart takes FE through its energy capability while the
 *   shrine's current work (a tier, or the Gathering in the Singularity Age) has {@code energy_consume} rites, up to
 *   their sum; the rite holds once that much is drawn, and the FE is spent when the prayer is heard.</li>
 * </ul>
 */
public final class ShrineProof {
    private ShrineProof() {}

    /** The progress of a proof: what counts, how many passed, how many are needed, and the item the lines name. */
    public record Progress(TallyRequirement req, long have, Item shown) {
        public boolean met() {
            return have >= req.count();
        }
    }

    static Predicate<String> filter(TallyRequirement r) {
        return TallyTrigger.filter(r.item().map(ResourceLocation::parse), r.tag().map(ResourceLocation::parse));
    }

    /** The item a requirement names in messages: the item itself, or the first item of the tag (air when none). */
    static Item shown(TallyRequirement r) {
        if (r.item().isPresent()) return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(r.item().get())).orElse(Items.AIR);
        TagKey<Item> key = TagKey.create(Registries.ITEM, ResourceLocation.parse(r.tag().orElseThrow()));
        return BuiltInRegistries.ITEM.getTag(key).flatMap(set -> set.stream().findFirst()).map(Holder::value).orElse(Items.AIR);
    }

    /**
     * The proof the offering of {@code tier} waits for, counted for {@code player}'s team (null: the team with the most).
     * Empty when the tier has no requirement.
     */
    public static Optional<Progress> progress(MinecraftServer s, int tier, @Nullable ServerPlayer player) {
        return progressOf(s, tier, player == null ? null : player.getUUID());
    }

    /** As {@link #progress}, for the team of the player with id {@code member} (null: the team with the most). */
    public static Optional<Progress> progressOf(MinecraftServer s, int tier, @Nullable UUID member) {
        Optional<TallyRequirement> req = ShrineService.data().tallyFor(tier);
        if (req.isEmpty()) return Optional.empty();
        AgeId from = AgeId.byId(req.get().age()).orElse(null);
        Predicate<String> f = filter(req.get());
        long have = member != null ? TallyService.countsOf(s, member).sum(f, from) : TallyData.get(s).best(f, from);
        return Optional.of(new Progress(req.get(), have, shown(req.get())));
    }

    /** The current proof for {@code member}'s team, empty when the shrine's current work asks none. */
    public static Optional<Progress> current(MinecraftServer s, UUID member) {
        return ShrineRules.currentTier(ShrineService.unlocked(s)).flatMap(t -> progressOf(s, t, member));
    }

    /**
     * How many more items with id {@code item} the shrine's current proof wants from {@code owner}'s team: the
     * requirement of the tier the shrine works on, if its filter takes the item, minus what the team counted since the
     * requirement's Age dawned. 0 when no proof is asked now, the item does not count, or the proof is met.
     */
    public static long room(MinecraftServer s, UUID owner, String item) {
        Optional<Integer> tier = ShrineRules.currentTier(ShrineService.unlocked(s));
        if (tier.isEmpty()) return 0;
        Optional<TallyRequirement> req = ShrineService.data().tallyFor(tier.get());
        if (req.isEmpty()) return 0;
        Predicate<String> f = filter(req.get());
        if (!f.test(item)) return 0;
        AgeId from = AgeId.byId(req.get().age()).orElse(null);
        return Math.max(0, req.get().count() - TallyService.countsOf(s, owner).sum(f, from));
    }

    static Component name(Progress p) {
        return p.shown() == Items.AIR ? Component.literal(p.req().item().orElseGet(() -> "#" + p.req().tag().orElseThrow()))
            : new ItemStack(p.shown()).getHoverName();
    }

    /** Caelum's line for an unmet proof: "... must count 512 Steel Ingot in the Industrial Age. They have counted 312." */
    static MutableComponent missingLine(Progress p) {
        AgeId age = AgeId.byId(p.req().age()).orElse(AgeId.AGE_0);
        return ShrineService.msg("firmages.shrine.proof.missing", fmt(p.req().count()), name(p), ShrineService.ageName(age), fmt(p.have()));
    }

    static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    // ---------------------------------------------------------------- energy sink (energy_consume)

    /** The shrine's current work that may draw FE: {@code tier_<N>} or {@code gathering}; empty in Dawn or after the Gathering. */
    static Optional<String> drawSlot(MinecraftServer s) {
        Optional<Integer> tier = ShrineRules.currentTier(ShrineService.unlocked(s));
        if (tier.isPresent()) return Optional.of("tier_" + tier.get());
        return ShrineGathering.pending(s) ? Optional.of("gathering") : Optional.empty();
    }

    /** The {@code energy_consume} rites of a work slot. */
    static List<ShrineTier.Rite> drawRites(String slot) {
        ShrineData d = ShrineService.data();
        List<ShrineTier.Rite> rites = slot.equals("gathering") ? d.gatheringRites()
            : d.tier(Integer.parseInt(slot.substring("tier_".length()))).map(ShrineTier::rites).orElse(List.of());
        return rites.stream().filter(r -> r.type() == ShrineTier.Rite.Type.ENERGY_CONSUME).toList();
    }

    /** FE the heart must draw for {@code slot}: the sum of its {@code energy_consume} rites (0 = none). */
    static long drawNeed(String slot) {
        long n = 0;
        for (ShrineTier.Rite r : drawRites(slot)) n += r.amount();
        return n;
    }

    /** FE the heart has drawn for the shrine's current work (0 when it drew for an earlier one). */
    static long drawn(MinecraftServer s, ShrineHeartBlockEntity be) {
        Optional<String> slot = drawSlot(s);
        return slot.isPresent() && slot.get().equals(be.drawnFor) ? be.drawn : 0;
    }
}
