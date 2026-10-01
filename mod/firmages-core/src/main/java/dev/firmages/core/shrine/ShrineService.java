package dev.firmages.core.shrine;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.ceremony.CeremonyService;
import dev.firmages.core.compat.modonomicon.ShrineMultiblocks;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.net.ShrinePreviewPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server logic of the shrine (SPEC §7.2, §7.3): validation, offering, rites, prayer, awakening.
 * <p>
 * Ritual: build the ring of the current tier, lay the Age's signature item on the ring's plinth, do the tier's
 * rite, then sneak and hold use on the heart with an empty hand. When the prayer completes the offering becomes a
 * relic, the next Age is granted through ProgressiveStages to every online player (one FTB team: the team gets
 * it), and the FULL ceremony starts. A broken structure refuses prayers and pauses the blessings; stages are never
 * revoked. Everything runs on the server thread.
 */
public final class ShrineService {
    /** A player counts as praying while their last use is at most this old (use repeats every 4 ticks). */
    static final int PRAY_TIMEOUT_TICKS = 10;
    static final int VALIDATE_INTERVAL = 200;
    static final int AWAKENING_TICKS = 240;
    private static final int MESSAGE_COOLDOWN = 20;

    private ShrineService() {}

    // ---------------------------------------------------------------- lookup

    static ShrineData data() {
        return ShrineDataLoader.current();
    }

    static Set<AgeId> unlocked(MinecraftServer s) {
        return AgeService.state(s).snapshot().unlocked();
    }

    /** The heart's level and block entity, if the heart's chunk is loaded. */
    public static Optional<Heart> heart(MinecraftServer s) {
        Optional<GlobalPos> gp = ShrineSavedData.get(s).heart();
        if (gp.isEmpty()) return Optional.empty();
        ServerLevel level = s.getLevel(gp.get().dimension());
        if (level == null || !level.isLoaded(gp.get().pos())) return Optional.empty();
        return level.getBlockEntity(gp.get().pos()) instanceof ShrineHeartBlockEntity be ? Optional.of(new Heart(level, gp.get().pos(), be)) : Optional.empty();
    }

    public record Heart(ServerLevel level, BlockPos pos, ShrineHeartBlockEntity be) {}

    /** The heart within {@code radius} of {@code pos} in {@code level}. */
    static Optional<Heart> heartNear(ServerLevel level, BlockPos pos, int radius) {
        return heart(level.getServer()).filter(h -> h.level == level && Math.abs(h.pos.getX() - pos.getX()) <= radius
            && Math.abs(h.pos.getZ() - pos.getZ()) <= radius && Math.abs(h.pos.getY() - pos.getY()) <= radius);
    }

    /** Horizontal radius of the shrine: the largest ring, or the fallback plinth radius. */
    static int shrineRadius() {
        ShrineData d = data();
        int r = d.fallback().map(ShrineTier::plinthRadius).orElse(0);
        for (ShrineTier t : d.tiers().values()) {
            r = Math.max(r, t.multiblock().map(id -> ShrineMultiblocks.radius(ResourceLocation.parse(id))).orElse(0));
        }
        return Math.max(r, 4);
    }

    // ---------------------------------------------------------------- placement (one shrine per server)

    public static boolean mayPlaceHeart(ServerLevel level, BlockPos pos, @Nullable Player player) {
        ShrineSavedData sd = ShrineSavedData.get(level.getServer());
        Optional<GlobalPos> gp = sd.heart();
        if (gp.isEmpty() || (gp.get().dimension() == level.dimension() && gp.get().pos().equals(pos))) return true;
        ServerLevel other = level.getServer().getLevel(gp.get().dimension());
        if (other != null && other.isLoaded(gp.get().pos()) && !other.getBlockState(gp.get().pos()).is(ShrineRegistry.SHRINE_HEART.get())) {
            FirmagesCore.LOGGER.warn("Shrine heart record at {} has no heart block any more; forgetting it", gp.get());
            sd.setHeart(null);
            return true;
        }
        if (player != null) {
            BlockPos h = gp.get().pos();
            player.displayClientMessage(msg("firmages.shrine.heart_elsewhere", h.getX() + " " + h.getY() + " " + h.getZ()).withStyle(ChatFormatting.GOLD), true);
        }
        return false;
    }

    public static void onHeartPlaced(ServerLevel level, BlockPos pos) {
        ShrineSavedData sd = ShrineSavedData.get(level.getServer());
        GlobalPos gp = GlobalPos.of(level.dimension(), pos);
        if (sd.heart().filter(gp::equals).isPresent()) return;
        if (sd.heart().isPresent() && !mayPlaceHeart(level, pos, null)) {
            FirmagesCore.LOGGER.warn("A second shrine heart was placed at {} while {} is the shrine; it stays inert", gp, sd.heart().get());
            return;
        }
        sd.setHeart(gp);
        FirmagesCore.LOGGER.info("Shrine heart placed at {}", gp);
    }

    public static void onHeartRemoved(ServerLevel level, BlockPos pos) {
        ShrineSavedData sd = ShrineSavedData.get(level.getServer());
        if (sd.heart().filter(g -> g.dimension() == level.dimension() && g.pos().equals(pos)).isEmpty()) return;
        sd.setHeart(null);
        ShrineState.clear();
        FirmagesCore.LOGGER.info("Shrine heart at {} removed; relics stay on their plinths", pos);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg("firmages.shrine.heart_removed").withStyle(ChatFormatting.GOLD));
        }
    }

    // ---------------------------------------------------------------- validation (SPEC §7.2)

    /** Re-validates every ring, updates the heart blockstate, the SavedData and the sanctuary cache. */
    public static void validate(ServerLevel level, BlockPos pos, ShrineHeartBlockEntity be) {
        ShrineData d = data();
        int highest = d.highestRing();
        Rotation[] rot = new Rotation[highest + 1];
        int valid = -1;
        boolean chain = true;
        for (int k = 0; k <= highest; k++) {
            Optional<String> mb = d.tier(k).flatMap(ShrineTier::multiblock);
            if (mb.isEmpty()) continue; // a tier without own ring adds nothing to the structure
            ShrineMultiblocks.RingCheck c = ShrineMultiblocks.check(level, pos, ResourceLocation.parse(mb.get()));
            rot[k] = c.rotation();
            if (chain && c.valid()) valid = k;
            else chain = false;
        }
        be.ringRotations = rot;
        be.validRing = valid;
        Set<AgeId> unlocked = unlocked(level.getServer());
        int awakened = ShrineRules.awakened(unlocked);
        boolean intact = highest < 0 || valid >= Math.min(Math.max(awakened - 1, 0), highest);
        Optional<Integer> tier = ShrineRules.currentTier(unlocked);
        boolean ready = tier.isPresent() && ringsReady(be, tier.get());
        BlockState state = level.getBlockState(pos);
        if (state.is(ShrineRegistry.SHRINE_HEART.get())) {
            BlockState ns = state.setValue(ShrineHeartBlock.AWAKENED, Math.min(10, awakened)).setValue(ShrineHeartBlock.READY, ready);
            if (ns != state) level.setBlock(pos, ns, 3);
        }
        ShrineSavedData sd = ShrineSavedData.get(level.getServer());
        boolean wasIntact = sd.intact();
        boolean known = sd.lastValidated() > 0;
        sd.setValidation(intact, valid, level.getGameTime());
        ShrineState.set(level.dimension(), pos, intact, awakened);
        if (known && wasIntact != intact && awakened > 0) {
            Component m = msg(intact ? "firmages.shrine.rekindled" : "firmages.shrine.dormant").withStyle(intact ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) p.sendSystemMessage(m);
            level.playSound(null, pos, intact ? ShrineRegistry.KINDLED.get() : ShrineRegistry.REFUSED.get(), SoundSource.BLOCKS, 1.0F, intact ? 1.0F : 0.6F);
            FirmagesCore.LOGGER.info("Shrine {} (valid ring {}, awakened {})", intact ? "intact again" : "broken: blessings paused", valid, awakened);
        }
        be.validationDue = false;
        be.nextValidation = level.getGameTime() + VALIDATE_INTERVAL;
    }

    /** True when every ring the tier needs stands (rings above the highest defined ring are not required). */
    static boolean ringsReady(ShrineHeartBlockEntity be, int tier) {
        int highest = data().highestRing();
        return highest < 0 || be.validRing >= Math.min(tier, highest);
    }

    /** The Age set changed (any source): the heart re-validates on its next tick ({@code awakened}, blessings). */
    public static void onAgeChanged(MinecraftServer s) {
        heart(s).ifPresent(h -> h.be.invalidate());
    }

    /** Marks the heart for re-validation when {@code pos} is inside the shrine (block placed, broken, exploded). */
    public static void onBlockChanged(ServerLevel level, BlockPos pos) {
        ShrineState.Snapshot st = ShrineState.get();
        if (st == null || st.dimension() != level.dimension()) return;
        int r = shrineRadius() + 1;
        BlockPos h = st.heart();
        if (Math.abs(h.getX() - pos.getX()) > r || Math.abs(h.getZ() - pos.getZ()) > r || Math.abs(h.getY() - pos.getY()) > 16) return;
        if (level.isLoaded(h) && level.getBlockEntity(h) instanceof ShrineHeartBlockEntity be) be.invalidate();
    }

    // ---------------------------------------------------------------- plinths

    /** The plinth of {@code tier} around a validated heart, if the ring stands (or any matching plinth for the fallback). */
    static Optional<BlockPos> plinthOf(ServerLevel level, BlockPos heart, ShrineHeartBlockEntity be, ShrineTier t) {
        if (t.multiblock().isPresent()) {
            Rotation r = t.tier() < be.ringRotations.length ? be.ringRotations[t.tier()] : null;
            if (r == null) return Optional.empty();
            return ShrineMultiblocks.positions(level, heart, ResourceLocation.parse(t.multiblock().get()), r, t.plinthKey()).stream().findFirst();
        }
        // Fallback tier: the plinth that holds this tier's offering.
        for (BlockPos p : fallbackPlinths(level, heart, be, t)) {
            if (level.getBlockEntity(p) instanceof OfferingPlinthBlockEntity pb && !pb.isEmpty() && pb.tier() == t.tier()) return Optional.of(p);
        }
        return Optional.empty();
    }

    /** Plinths within the fallback radius that are no ring's plinth. */
    static List<BlockPos> fallbackPlinths(ServerLevel level, BlockPos heart, ShrineHeartBlockEntity be, ShrineTier t) {
        List<BlockPos> ringPlinths = ringPlinthPositions(level, heart, be);
        List<BlockPos> out = new ArrayList<>();
        int r = t.plinthRadius();
        for (BlockPos p : BlockPos.betweenClosed(heart.offset(-r, -2, -r), heart.offset(r, 3, r))) {
            if (level.getBlockState(p).is(ShrineRegistry.OFFERING_PLINTH.get()) && !ringPlinths.contains(p)) out.add(p.immutable());
        }
        return out;
    }

    private static List<BlockPos> ringPlinthPositions(ServerLevel level, BlockPos heart, ShrineHeartBlockEntity be) {
        List<BlockPos> out = new ArrayList<>();
        for (ShrineTier t : data().tiers().values()) {
            if (t.multiblock().isEmpty()) continue;
            ResourceLocation id = ResourceLocation.parse(t.multiblock().get());
            Rotation r = t.tier() < be.ringRotations.length ? be.ringRotations[t.tier()] : null;
            out.addAll(r != null ? ShrineMultiblocks.positions(level, heart, id, r, t.plinthKey())
                : ShrineMultiblocks.positionsAnyRotation(level, heart, id, t.plinthKey()));
        }
        return out;
    }

    /** Which tier a plinth serves: a ring plinth (any rotation while the ring is incomplete), else the fallback. */
    static Optional<Integer> tierOfPlinth(ServerLevel level, BlockPos heart, ShrineHeartBlockEntity be, BlockPos plinth, Optional<Integer> current) {
        ShrineData d = data();
        for (ShrineTier t : d.tiers().values()) {
            if (t.multiblock().isEmpty()) continue;
            ResourceLocation id = ResourceLocation.parse(t.multiblock().get());
            Rotation r = t.tier() < be.ringRotations.length ? be.ringRotations[t.tier()] : null;
            List<BlockPos> ps = r != null ? ShrineMultiblocks.positions(level, heart, id, r, t.plinthKey())
                : ShrineMultiblocks.positionsAnyRotation(level, heart, id, t.plinthKey());
            if (ps.contains(plinth)) return Optional.of(t.tier());
        }
        if (level.getBlockEntity(plinth) instanceof OfferingPlinthBlockEntity pb && pb.tier() >= 0) return Optional.of(pb.tier());
        if (current.isPresent()) {
            Optional<ShrineTier> t = d.tier(current.get());
            if (t.isPresent() && t.get().multiblock().isEmpty()
                && Math.abs(plinth.getX() - heart.getX()) <= t.get().plinthRadius() && Math.abs(plinth.getZ() - heart.getZ()) <= t.get().plinthRadius()) {
                return current;
            }
        }
        return Optional.empty();
    }

    static Optional<Item> offeringItem(int tier) {
        return data().offeringFor(tier).map(ResourceLocation::tryParse).flatMap(BuiltInRegistries.ITEM::getOptional)
            .filter(i -> i != net.minecraft.world.item.Items.AIR);
    }

    /** Result of an offering attempt (also used by the GameTests). */
    public enum OfferResult { ACCEPTED, ACCEPTED_LATE, WRONG_ITEM, OCCUPIED, NOT_SHRINE, LATER_TIER, RUINS, NO_TIER, UNKNOWN_OFFERING }

    /** Right-click on a plinth with an item. */
    public static OfferResult offerAtPlinth(ServerLevel level, BlockPos plinthPos, ItemStack stack, @Nullable ServerPlayer player) {
        OfferResult r = tryOffer(level, plinthPos, stack, player);
        if (player != null && r != OfferResult.ACCEPTED && r != OfferResult.ACCEPTED_LATE) {
            level.playSound(null, plinthPos, ShrineRegistry.REFUSED.get(), SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        return r;
    }

    private static OfferResult tryOffer(ServerLevel level, BlockPos plinthPos, ItemStack stack, @Nullable ServerPlayer player) {
        if (!(level.getBlockEntity(plinthPos) instanceof OfferingPlinthBlockEntity pb)) return OfferResult.NOT_SHRINE;
        Optional<Heart> heart = heartNear(level, plinthPos, shrineRadius() + 1);
        if (heart.isEmpty()) {
            tell(player, msg("firmages.shrine.no_heart"));
            return OfferResult.NOT_SHRINE;
        }
        Heart h = heart.get();
        if (!pb.isEmpty()) {
            tell(player, msg("firmages.shrine.plinth.occupied", pb.item().getHoverName()));
            return OfferResult.OCCUPIED;
        }
        validate(level, h.pos, h.be);
        Set<AgeId> unlocked = unlocked(level.getServer());
        Optional<Integer> current = ShrineRules.currentTier(unlocked);
        Optional<Integer> slot = tierOfPlinth(level, h.pos, h.be, plinthPos, current);
        if (slot.isEmpty()) {
            tell(player, msg("firmages.shrine.plinth.outside"));
            return OfferResult.NOT_SHRINE;
        }
        int k = slot.get();
        Optional<Item> expected = offeringItem(k);
        if (expected.isEmpty()) {
            tell(player, msg("firmages.shrine.plinth.unknown_offering"));
            FirmagesCore.LOGGER.warn("Shrine tier {} has no known offering item ({}); check firmages_shrine/offerings.json", k, data().offeringFor(k));
            return OfferResult.UNKNOWN_OFFERING;
        }
        if (current.isEmpty()) {
            tell(player, msg(ShrineRules.highest(unlocked) == AgeId.DAWN ? "firmages.shrine.sleeping" : "firmages.shrine.complete"));
            return OfferResult.NO_TIER;
        }
        if (k > current.get()) {
            tell(player, msg("firmages.shrine.plinth.later"));
            return OfferResult.LATER_TIER;
        }
        if (!stack.is(expected.get())) {
            tell(player, msg("firmages.shrine.plinth.wrong", new ItemStack(expected.get()).getHoverName()));
            return OfferResult.WRONG_ITEM;
        }
        if (!ringsReady(h.be, k)) {
            refuseRuins(level, h, player, k);
            return OfferResult.RUINS;
        }
        if (k < current.get()) {
            // An earlier Age that came without its offering (quest or admin grant): the relic completes the set.
            ItemStack one = stack.split(1);
            pb.offer(one, k);
            pb.enshrine(k);
            ShrineSavedData.get(level.getServer()).putRelic(new ShrineSavedData.Relic(plinthPos.immutable(), k, BuiltInRegistries.ITEM.getKey(one.getItem()).toString()));
            level.playSound(null, plinthPos, ShrineRegistry.ACCEPTED.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.END_ROD, plinthPos.getX() + 0.5, plinthPos.getY() + 1.2, plinthPos.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.02);
            tell(player, msg("firmages.shrine.late_offering", one.getHoverName(), ageName(AgeId.all().get(k + 1))));
            FirmagesCore.LOGGER.info("Late offering for tier {} enshrined at {}", k, plinthPos);
            return OfferResult.ACCEPTED_LATE;
        }
        ItemStack one = stack.split(1);
        pb.offer(one, k);
        h.be.prayerProgress = 0;
        h.be.setChanged();
        level.playSound(null, plinthPos, ShrineRegistry.ACCEPTED.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        level.sendParticles(ParticleTypes.ENCHANT, plinthPos.getX() + 0.5, plinthPos.getY() + 1.4, plinthPos.getZ() + 0.5, 30, 0.4, 0.5, 0.4, 0.4);
        tell(player, msg("firmages.shrine.offered", one.getHoverName()).withStyle(ChatFormatting.GOLD));
        ShrineTier t = data().tier(k).orElseThrow();
        for (Component c : unmetRites(level, h, t, true)) tell(player, c);
        FirmagesCore.LOGGER.info("Offering for tier {} laid on {} by {}", k, plinthPos, player == null ? "test" : player.getGameProfile().getName());
        return OfferResult.ACCEPTED;
    }

    /** Right-click on the heart with an item: the current tier's offering goes onto its plinth. @return true if handled */
    public static boolean offerAtHeart(ServerLevel level, BlockPos heartPos, ItemStack stack, ServerPlayer player) {
        Optional<Integer> current = ShrineRules.currentTier(unlocked(level.getServer()));
        if (current.isEmpty()) return false;
        Optional<Item> expected = offeringItem(current.get());
        if (expected.isEmpty() || !stack.is(expected.get())) return false;
        if (!(level.getBlockEntity(heartPos) instanceof ShrineHeartBlockEntity be)) return false;
        validate(level, heartPos, be);
        ShrineTier t = data().tier(current.get()).orElseThrow();
        Optional<BlockPos> plinth = plinthOf(level, heartPos, be, t);
        if (plinth.isEmpty() && t.multiblock().isEmpty()) {
            plinth = fallbackPlinths(level, heartPos, be, t).stream()
                .filter(p -> level.getBlockEntity(p) instanceof OfferingPlinthBlockEntity pb && pb.isEmpty()).findFirst();
        }
        if (plinth.isEmpty()) {
            if (!ringsReady(be, current.get())) refuseRuins(level, new Heart(level, heartPos, be), player, current.get());
            else tell(player, msg("firmages.shrine.no_plinth"));
            return true;
        }
        offerAtPlinth(level, plinth.get(), stack, player);
        return true;
    }

    /** Empty-hand use on a plinth: sneak takes an offering back; otherwise it says what lies or belongs there. */
    public static void usePlinthEmptyHand(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof OfferingPlinthBlockEntity pb)) return;
        if (pb.isEmpty()) {
            Optional<Heart> h = heartNear(level, pos, shrineRadius() + 1);
            Optional<Integer> slot = h.flatMap(x -> tierOfPlinth(level, x.pos, x.be, pos, ShrineRules.currentTier(unlocked(level.getServer()))));
            Optional<Item> want = slot.flatMap(ShrineService::offeringItem);
            tell(player, want.isPresent() ? msg("firmages.shrine.plinth.wants", new ItemStack(want.get()).getHoverName())
                : msg(h.isPresent() ? "firmages.shrine.plinth.outside" : "firmages.shrine.no_heart"));
            return;
        }
        if (pb.isRelic()) {
            tell(player, msg("firmages.shrine.relic_locked", pb.item().getHoverName()));
            return;
        }
        if (!player.isShiftKeyDown()) {
            tell(player, msg("firmages.shrine.offered", pb.item().getHoverName()));
            return;
        }
        ItemStack back = pb.take();
        if (!player.getInventory().add(back)) player.drop(back, false);
        heartNear(level, pos, shrineRadius() + 1).ifPresent(h -> {
            h.be.prayerProgress = 0;
            h.be.prayers.clear();
            h.be.setChanged();
        });
        tell(player, msg("firmages.shrine.taken_back", back.getHoverName()));
    }

    /** A plinth with an item was removed (creative, command, explosion protection bypassed): the item drops. */
    public static void onPlinthRemoved(ServerLevel level, BlockPos pos, boolean wasRelic, ItemStack item) {
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item);
        if (wasRelic) {
            ShrineSavedData.get(level.getServer()).removeRelic(pos);
            FirmagesCore.LOGGER.warn("Relic plinth at {} was removed; the relic {} dropped", pos, item);
        }
    }

    /** {@code /firmages shrine extract}: takes a relic or offering off a plinth. */
    public static Optional<ItemStack> extract(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof OfferingPlinthBlockEntity pb) || pb.isEmpty()) return Optional.empty();
        boolean relic = pb.isRelic();
        ItemStack out = pb.take();
        if (relic) ShrineSavedData.get(level.getServer()).removeRelic(pos);
        FirmagesCore.LOGGER.info("Operator extracted {} from plinth {} (relic: {})", out, pos, relic);
        return Optional.of(out);
    }

    // ---------------------------------------------------------------- rites

    /** Kindles the heart (the Stone Age rite): {@code lit=true}, stays lit. */
    public static void kindle(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack, InteractionHand hand) {
        BlockState s = level.getBlockState(pos);
        if (!s.is(ShrineRegistry.SHRINE_HEART.get()) || s.getValue(ShrineHeartBlock.LIT)) return;
        level.setBlock(pos, s.setValue(ShrineHeartBlock.LIT, true), 3);
        if (stack.isDamageableItem()) stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        else if (!player.isCreative() && stack.is(net.minecraft.world.item.Items.FIRE_CHARGE)) stack.shrink(1);
        level.playSound(null, pos, ShrineRegistry.KINDLED.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 25, 0.3, 0.2, 0.3, 0.02);
        tell(player, msg("firmages.shrine.kindled").withStyle(ChatFormatting.GOLD));
        if (level.getBlockEntity(pos) instanceof ShrineHeartBlockEntity be) be.invalidate();
    }

    /** Any right-click on a block: completes {@code interact} rites (the bell) inside the shrine. */
    public static void onBlockUsed(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        ShrineState.Snapshot st = ShrineState.get();
        if (st == null || st.dimension() != level.dimension()) return;
        int r = shrineRadius() + 1;
        if (Math.abs(st.heart().getX() - pos.getX()) > r || Math.abs(st.heart().getZ() - pos.getZ()) > r) return;
        Optional<Integer> tier = ShrineRules.currentTier(unlocked(level.getServer()));
        if (tier.isEmpty()) return;
        Optional<ShrineTier> t = data().tier(tier.get());
        if (t.isEmpty() || !(level.getBlockEntity(st.heart()) instanceof ShrineHeartBlockEntity be)) return;
        for (ShrineTier.Rite rite : t.get().rites()) {
            if (rite.type() != ShrineTier.Rite.Type.INTERACT || be.interacted.contains(rite.tag())) continue;
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, ResourceLocation.parse(rite.tag()));
            if (!state.is(tag)) continue;
            be.interacted.add(rite.tag());
            be.setChanged();
            tell(player, msg("firmages.shrine.rite.heard").withStyle(ChatFormatting.GOLD));
        }
    }

    /**
     * Hints for every rite of {@code t} that is not done yet (empty = all done). {@code skipChorus} leaves out
     * {@code players_praying}, which can only be met once the others pray too (checked again at completion).
     */
    static List<Component> unmetRites(ServerLevel level, Heart h, ShrineTier t, boolean skipChorus) {
        List<Component> out = new ArrayList<>();
        for (ShrineTier.Rite rite : t.rites()) {
            if (!rite.type().supported() || (skipChorus && rite.type() == ShrineTier.Rite.Type.PLAYERS_PRAYING) || riteDone(level, h, t, rite)) continue;
            out.add(rite.hint().isEmpty() ? msg("firmages.shrine.rite." + rite.type().name().toLowerCase(java.util.Locale.ROOT), rite.min())
                : msg(rite.hint(), rite.min()));
        }
        return out;
    }

    private static boolean riteDone(ServerLevel level, Heart h, ShrineTier t, ShrineTier.Rite rite) {
        return switch (rite.type()) {
            case BLOCKSTATE -> {
                if (t.multiblock().isEmpty()) yield true;
                Rotation r = t.tier() < h.be.ringRotations.length ? h.be.ringRotations[t.tier()] : null;
                if (r == null) yield false;
                List<BlockPos> ps = ShrineMultiblocks.positions(level, h.pos, ResourceLocation.parse(t.multiblock().get()), r, rite.key());
                if (ps.isEmpty()) yield true;
                for (BlockPos p : ps) {
                    if (!hasProperty(level.getBlockState(p), rite.property(), rite.value())) yield false;
                }
                yield true;
            }
            case INTERACT -> h.be.interacted.contains(rite.tag());
            case SKY -> level.isNight() && level.canSeeSky(h.pos.above()) && !level.isRaining();
            case PLAYERS_PRAYING -> level.getServer().getPlayerCount() < rite.min() || h.be.prayers.size() >= rite.min();
            default -> true;
        };
    }

    private static boolean hasProperty(BlockState state, String name, String value) {
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals(name)) return valueName(state, p).equals(value);
        }
        return false;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> p) {
        return p.getName(state.getValue(p));
    }

    // ---------------------------------------------------------------- prayer

    /** Rite checks of {@link #refusal}. */
    enum Rites { ALL, SKIP_CHORUS, SKIP }

    /** Why the shrine cannot hear a prayer now, or empty when it can. */
    static Optional<Component> refusal(ServerLevel level, Heart h, Rites rites, boolean sendPreview, @Nullable ServerPlayer player) {
        Set<AgeId> unlocked = unlocked(level.getServer());
        Optional<Integer> tier = ShrineRules.currentTier(unlocked);
        if (tier.isEmpty()) return Optional.of(msg(ShrineRules.highest(unlocked) == AgeId.DAWN ? "firmages.shrine.sleeping" : "firmages.shrine.complete"));
        Optional<ShrineTier> t = data().tier(tier.get());
        if (t.isEmpty()) return Optional.of(msg("firmages.shrine.plinth.unknown_offering"));
        if (!ringsReady(h.be, tier.get())) {
            if (sendPreview && player != null) sendPreview(player, h, firstIncompleteRing(h.be).orElse(tier.get()));
            return Optional.of(ruinsMessage(level, h, firstIncompleteRing(h.be).orElse(tier.get())));
        }
        Optional<BlockPos> plinth = plinthOf(level, h.pos, h.be, t.get());
        boolean offered = plinth.isPresent() && level.getBlockEntity(plinth.get()) instanceof OfferingPlinthBlockEntity pb
            && !pb.isEmpty() && !pb.isRelic() && offeringItem(tier.get()).map(pb.item()::is).orElse(false);
        if (!offered) {
            Component item = offeringItem(tier.get()).map(i -> new ItemStack(i).getHoverName()).orElse(Component.literal("?"));
            return Optional.of(msg("firmages.shrine.no_offering", item));
        }
        if (rites == Rites.SKIP) return Optional.empty();
        List<Component> unmet = unmetRites(level, h, t.get(), rites == Rites.SKIP_CHORUS);
        return unmet.isEmpty() ? Optional.empty() : Optional.of(unmet.get(0));
    }

    /** Why a prayer started now would be refused (status, GameTests); empty when the shrine would listen. */
    public static Optional<Component> prayerRefusal(MinecraftServer s) {
        Optional<Heart> h = heart(s);
        if (h.isEmpty()) return Optional.of(msg("firmages.shrine.cmd.no_heart"));
        validate(h.get().level, h.get().pos, h.get().be);
        return refusal(h.get().level, h.get(), Rites.SKIP_CHORUS, false, null);
    }

    /** Sneak plus held use on the heart with an empty main hand. */
    public static void pray(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof ShrineHeartBlockEntity be)) return;
        if (!ShrineSavedData.get(level.getServer()).heart().filter(g -> g.dimension() == level.dimension() && g.pos().equals(pos)).isPresent()) {
            tellThrottled(be, level, player, msg("firmages.shrine.inert"));
            return;
        }
        long now = level.getGameTime();
        if (be.awakening(now)) {
            tellThrottled(be, level, player, msg("firmages.shrine.awakening"));
            return;
        }
        Heart h = new Heart(level, pos, be);
        if (!be.prayers.containsKey(player.getUUID())) {
            validate(level, pos, be);
            Optional<Component> no = refusal(level, h, Rites.SKIP_CHORUS, true, player);
            if (no.isPresent()) {
                if (tellThrottled(be, level, player, no.get())) level.playSound(null, pos, ShrineRegistry.REFUSED.get(), SoundSource.BLOCKS, 0.6F, 0.8F);
                return;
            }
        }
        be.prayers.put(player.getUUID(), now);
    }

    /** Heart server tick: validation schedule, prayer progress, completion. */
    static void tickHeart(ServerLevel level, BlockPos pos, BlockState state, ShrineHeartBlockEntity be) {
        long now = level.getGameTime();
        if (be.validationDue || now >= be.nextValidation) validate(level, pos, be);
        if (be.awakening(now)) {
            be.prayers.clear();
            if (now % 5 == 0) level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 3, 0.2, 1.0, 0.2, 0.02);
            return;
        }
        int radius = ServerConfig.loaded() ? ServerConfig.SHRINE_PRAY_RADIUS.get() : 6;
        List<ServerPlayer> praying = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, Long>> it = be.prayers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive() || p.level() != level || now - e.getValue() > PRAY_TIMEOUT_TICKS
                || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > (double) radius * radius) {
                it.remove();
            } else {
                praying.add(p);
            }
        }
        int n = praying.size();
        if (n == 0 && be.prayerProgress == 0) return;
        Optional<Integer> tier = ShrineRules.currentTier(unlocked(level.getServer()));
        Optional<ShrineTier> t = tier.flatMap(x -> data().tier(x));
        if (t.isEmpty()) {
            be.prayerProgress = 0;
            be.prayers.clear();
            return;
        }
        int target = ShrineRules.prayerTarget(prayerTicks(t.get()), minPrayerTicks(), n);
        be.prayerProgress = ShrineRules.stepProgress(be.prayerProgress, n);
        be.setChanged();
        if (n == 0) return;
        if (now % 4 == 0) {
            String bar = ShrineRules.bar(be.prayerProgress, target, 20);
            Component c = n == 1 ? msg("firmages.shrine.prayer.progress", bar) : msg("firmages.shrine.prayer.progress_many", n, bar);
            for (ServerPlayer p : praying) p.displayClientMessage(c.copy().withStyle(ChatFormatting.GOLD), true);
        }
        if (now % 3 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 4 * n, 1.2, 0.6, 1.2, 0.6);
        }
        if (now % 40 == 0) level.playSound(null, pos, ShrineRegistry.PRAYER.get(), SoundSource.BLOCKS, 0.8F, 0.7F + 0.1F * n);
        if (be.prayerProgress >= target) {
            Heart h = new Heart(level, pos, be);
            Optional<Component> no = refusal(level, h, Rites.ALL, false, null);
            if (no.isPresent()) {
                be.prayerProgress = 0;
                be.prayers.clear();
                for (ServerPlayer p : praying) p.displayClientMessage(no.get(), true);
                return;
            }
            complete(h, t.get(), praying.stream().map(p -> p.getGameProfile().getName()).toList());
        }
    }

    static int prayerTicks(ShrineTier t) {
        return t.prayerTicks().orElse((ServerConfig.loaded() ? ServerConfig.SHRINE_PRAYER_SECONDS.get() : 10) * 20);
    }

    static int minPrayerTicks() {
        return (ServerConfig.loaded() ? ServerConfig.SHRINE_MIN_PRAYER_SECONDS.get() : 4) * 20;
    }

    // ---------------------------------------------------------------- awakening (SPEC §7.3 AWAKENING)

    /**
     * The prayer is heard. Order (crash-safe): (1) relic and SavedData persisted, (2) the ceremony expects the
     * grant, (3) the stage is granted, (4) the FULL ceremony runs (from the stage event, or right here when the
     * Age was already held), (5) re-validation updates {@code awakened} and the blessings.
     */
    static void complete(Heart h, ShrineTier t, List<String> by) {
        ServerLevel level = h.level;
        MinecraftServer s = level.getServer();
        AgeId stage = AgeId.byId(t.grants()).orElseThrow();
        BlockPos plinth = plinthOf(level, h.pos, h.be, t).orElseThrow();
        OfferingPlinthBlockEntity pb = (OfferingPlinthBlockEntity) level.getBlockEntity(plinth);
        pb.enshrine(t.tier());
        ShrineSavedData sd = ShrineSavedData.get(s);
        sd.putRelic(new ShrineSavedData.Relic(plinth.immutable(), t.tier(), BuiltInRegistries.ITEM.getKey(pb.item().getItem()).toString()));
        sd.addGranted(stage.id());
        h.be.prayerProgress = 0;
        h.be.prayers.clear();
        h.be.interacted.clear();
        h.be.awakeningUntil = level.getGameTime() + AWAKENING_TICKS;
        h.be.setChanged();
        FirmagesCore.LOGGER.info("Shrine prayer heard (tier {}, by {}): granting {}", t.tier(), by, stage.id());
        CeremonyService.expectShrineGrant(s, stage, GlobalPos.of(level.dimension(), h.pos), t);
        grant(s, stage);
        CeremonyService.ensureFullStarted(s, stage);
        h.be.invalidate();
    }

    /**
     * Grants {@code stage} through ProgressiveStages to every online player (in {@code ftb_teams} mode the first
     * call grants it to the team, offline members included). Without any player online (GameTests, console)
     * the grant uses the debug simulate path when {@code debug.allowSimulate} is on; otherwise it waits for the
     * next login ({@link #onLogin}).
     */
    static void grant(MinecraftServer s, AgeId stage) {
        List<ServerPlayer> players = s.getPlayerList().getPlayers();
        StageId id = StageId.of(stage.id());
        for (ServerPlayer p : players) {
            try {
                if (ProgressiveStagesAPI.hasStage(p, id)) continue;
                if (!ProgressiveStagesAPI.grantStage(p, id, StageCause.API) && !ProgressiveStagesAPI.hasStage(p, id)) {
                    FirmagesCore.LOGGER.warn("ProgressiveStages refused {} for {}; granting without the dependency check", stage.id(), p.getGameProfile().getName());
                    ProgressiveStagesAPI.grantStageBypass(p, id, StageCause.API);
                }
            } catch (RuntimeException | LinkageError e) {
                FirmagesCore.LOGGER.error("Cannot grant {} to {} through ProgressiveStages", stage.id(), p.getGameProfile().getName(), e);
            }
        }
        if (players.isEmpty()) {
            if (ServerConfig.allowSimulate()) {
                AgeService.simulate(s, stage, true);
            } else {
                FirmagesCore.LOGGER.warn("Shrine granted {} with nobody online; the next player to log in receives it", stage.id());
            }
        }
    }

    /** Login: a player who lacks an Age the shrine granted gets it (per-player ProgressiveStages modes). */
    public static void onLogin(ServerPlayer player) {
        ShrineSavedData sd = ShrineSavedData.get(player.server);
        for (String stage : sd.grantedStages()) {
            StageId id = StageId.of(stage);
            try {
                if (!ProgressiveStagesAPI.hasStage(player, id)) {
                    FirmagesCore.LOGGER.info("Shrine catch-up: granting {} to {}", stage, player.getGameProfile().getName());
                    if (!ProgressiveStagesAPI.grantStage(player, id, StageCause.API)) ProgressiveStagesAPI.grantStageBypass(player, id, StageCause.API);
                }
            } catch (RuntimeException | LinkageError e) {
                FirmagesCore.LOGGER.error("Shrine catch-up of {} for {} failed", stage, player.getGameProfile().getName(), e);
            }
        }
    }

    /** An Age was revoked (admin repair): the shrine stops re-granting it at login. Relics stay. */
    public static void onRevoked(MinecraftServer s, String stage) {
        ShrineSavedData.get(s).removeGranted(stage);
    }

    /**
     * {@code /firmages shrine simulate_pray} and the GameTests: completes the current prayer at once. The ring and
     * the offering must be in place; rites and praying players are skipped. @return null on success, else why not
     */
    @Nullable
    public static Component simulatePray(MinecraftServer s) {
        Optional<Heart> heart = heart(s);
        if (heart.isEmpty()) return msg("firmages.shrine.cmd.no_heart");
        Heart h = heart.get();
        validate(h.level, h.pos, h.be);
        Optional<Component> no = refusal(h.level, h, Rites.SKIP, false, null);
        if (no.isPresent()) return no.get();
        ShrineTier t = ShrineRules.currentTier(unlocked(s)).flatMap(x -> data().tier(x)).orElseThrow();
        complete(h, t, List.of("simulate_pray"));
        return null;
    }

    // ---------------------------------------------------------------- inspection, preview

    /** Empty-hand use on the heart (not sneaking): what Caelum wants now, plus the ghost preview of a missing ring. */
    public static void inspect(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof ShrineHeartBlockEntity be)) return;
        Long last = be.lastMessage.get(player.getUUID());
        if (last != null && level.getGameTime() - last < MESSAGE_COOLDOWN) return;
        be.lastMessage.put(player.getUUID(), level.getGameTime());
        validate(level, pos, be);
        Heart h = new Heart(level, pos, be);
        Set<AgeId> unlocked = unlocked(level.getServer());
        player.sendSystemMessage(msg("firmages.shrine.inspect.header", ShrineRules.awakened(unlocked)).withStyle(ChatFormatting.GOLD));
        Optional<Component> no = refusal(level, h, Rites.SKIP_CHORUS, true, player);
        Optional<Integer> tier = ShrineRules.currentTier(unlocked);
        if (no.isPresent()) {
            player.sendSystemMessage(no.get());
        } else if (tier.isPresent()) {
            player.sendSystemMessage(msg("firmages.shrine.inspect.ready"));
        }
        if (!ShrineSavedData.get(level.getServer()).intact() && ShrineRules.awakened(unlocked) > 0) {
            player.sendSystemMessage(msg("firmages.shrine.dormant").withStyle(ChatFormatting.GRAY));
        }
    }

    static Optional<Integer> firstIncompleteRing(ShrineHeartBlockEntity be) {
        ShrineData d = data();
        for (int k = 0; k <= d.highestRing(); k++) {
            if (d.tier(k).flatMap(ShrineTier::multiblock).isPresent() && (k >= be.ringRotations.length || be.ringRotations[k] == null)) return Optional.of(k);
        }
        return Optional.empty();
    }

    private static Component ruinsMessage(ServerLevel level, Heart h, int ring) {
        Optional<String> mb = data().tier(ring).flatMap(ShrineTier::multiblock);
        if (mb.isEmpty()) return msg("firmages.shrine.ruins.unknown");
        ResourceLocation id = ResourceLocation.parse(mb.get());
        ShrineMultiblocks.RingCheck c = ShrineMultiblocks.check(level, h.pos, id);
        return msg("firmages.shrine.ruins", ShrineMultiblocks.name(id), c.matched(), c.total());
    }

    private static void refuseRuins(ServerLevel level, Heart h, @Nullable ServerPlayer player, int tier) {
        int ring = firstIncompleteRing(h.be).orElse(tier);
        tell(player, ruinsMessage(level, h, ring));
        if (player != null) sendPreview(player, h, ring);
    }

    static void sendPreview(ServerPlayer player, Heart h, int ring) {
        Optional<String> mb = data().tier(ring).flatMap(ShrineTier::multiblock);
        if (mb.isEmpty()) return;
        ResourceLocation id = ResourceLocation.parse(mb.get());
        ShrineMultiblocks.RingCheck c = ShrineMultiblocks.check(h.level, h.pos, id);
        if (!c.known()) return;
        PacketDistributor.sendToPlayer(player, new ShrinePreviewPayload(id, h.pos, c.bestRotation().ordinal(), false));
    }

    // ---------------------------------------------------------------- status (command)

    public static List<Component> status(MinecraftServer s) {
        List<Component> out = new ArrayList<>();
        ShrineSavedData sd = ShrineSavedData.get(s);
        ShrineData d = data();
        Set<AgeId> unlocked = unlocked(s);
        out.add(Component.literal("Shrine of Caelum").withStyle(ChatFormatting.GOLD));
        out.add(Component.literal("Data: tiers " + d.tiers().keySet() + ", fallback " + d.fallback().isPresent() + ", rings up to "
            + d.highestRing() + ", offerings " + d.offerings() + (d.errors().isEmpty() ? "" : ", ERRORS " + d.errors())));
        if (sd.heart().isEmpty()) {
            out.add(Component.literal("No heart placed."));
            return out;
        }
        GlobalPos gp = sd.heart().get();
        out.add(Component.literal("Heart " + gp.pos().toShortString() + " in " + gp.dimension().location() + "; intact " + sd.intact()
            + ", valid ring " + sd.lastValidRing() + ", awakened " + ShrineRules.awakened(unlocked) + ", relics " + sd.relics().size()
            + ", shrine grants " + sd.grantedStages()));
        Optional<Heart> heart = heart(s);
        if (heart.isEmpty()) {
            out.add(Component.literal("Heart chunk not loaded; last validation at game time " + sd.lastValidated()));
            return out;
        }
        Heart h = heart.get();
        validate(h.level, h.pos, h.be);
        for (int k = 0; k <= d.highestRing(); k++) {
            Optional<String> mb = d.tier(k).flatMap(ShrineTier::multiblock);
            if (mb.isEmpty()) continue;
            ShrineMultiblocks.RingCheck c = ShrineMultiblocks.check(h.level, h.pos, ResourceLocation.parse(mb.get()));
            out.add(Component.literal("Ring " + k + " (" + mb.get() + "): " + (c.valid() ? "complete, rotation " + c.rotation()
                : c.matched() + "/" + c.total() + " blocks, missing " + c.missing())));
        }
        Optional<Integer> tier = ShrineRules.currentTier(unlocked);
        if (tier.isEmpty()) {
            out.add(Component.literal("No current tier (highest Age " + ShrineRules.highest(unlocked).id() + ")"));
            return out;
        }
        ShrineTier t = d.tier(tier.get()).orElse(null);
        if (t == null) {
            out.add(Component.literal("Tier " + tier.get() + " has no definition and there is no fallback"));
            return out;
        }
        Optional<BlockPos> plinth = plinthOf(h.level, h.pos, h.be, t);
        String plinthText = plinth.map(p -> p.toShortString() + " holds " + (h.level.getBlockEntity(p) instanceof OfferingPlinthBlockEntity pb
            ? pb.item() + (pb.isRelic() ? " (relic)" : "") : "?")).orElse("none found");
        out.add(Component.literal("Tier " + tier.get() + (t.fallback() ? " (fallback)" : "") + " grants " + t.grants() + "; offering "
            + d.offeringFor(tier.get()).orElse("?") + "; plinth " + plinthText));
        List<String> rites = unmetRites(h.level, h, t, false).stream().map(Component::getString).toList();
        long now = h.level.getGameTime();
        out.add(Component.literal("Phase " + phase(h) + "; rites not done " + rites + "; prayer " + h.be.prayerProgress + "/"
            + ShrineRules.prayerTarget(prayerTicks(t), minPrayerTicks(), Math.max(1, h.be.prayers.size())) + " with " + h.be.prayers.size()
            + " praying" + (h.be.awakening(now) ? "; awakening for " + (h.be.awakeningUntil - now) + " ticks" : "")));
        out.add(Component.literal("Sanctuary radius " + Blessings.sanctuaryRadius(s) + " (active: " + Blessings.sanctuaryActive(s) + ")"));
        return out;
    }

    /** State-machine phase (SPEC §7.3), derived from the world. */
    public static String phase(Heart h) {
        long now = h.level.getGameTime();
        if (h.be.awakening(now)) return "AWAKENING";
        if (!h.be.prayers.isEmpty()) return "PRAYING";
        Set<AgeId> unlocked = unlocked(h.level.getServer());
        Optional<Integer> tier = ShrineRules.currentTier(unlocked);
        if (tier.isEmpty() || !ringsReady(h.be, tier.get())) return "IDLE";
        if (refusal(h.level, h, Rites.SKIP, false, null).isPresent()) return "READY";
        return refusal(h.level, h, Rites.SKIP_CHORUS, false, null).isEmpty() ? "RITE_DONE" : "OFFERED";
    }

    // ---------------------------------------------------------------- helpers

    static Component ageName(AgeId age) {
        return Component.translatable("firmages.age." + age.id() + ".name");
    }

    static net.minecraft.network.chat.MutableComponent msg(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static void tell(@Nullable ServerPlayer player, Component c) {
        if (player != null) player.displayClientMessage(c, false);
    }

    /** Actionbar message at most once per second per player (use repeats every 4 ticks while held). */
    private static boolean tellThrottled(ShrineHeartBlockEntity be, Level level, ServerPlayer player, Component c) {
        Long last = be.lastMessage.get(player.getUUID());
        if (last != null && level.getGameTime() - last < MESSAGE_COOLDOWN) return false;
        be.lastMessage.put(player.getUUID(), level.getGameTime());
        player.displayClientMessage(c, true);
        return true;
    }
}
