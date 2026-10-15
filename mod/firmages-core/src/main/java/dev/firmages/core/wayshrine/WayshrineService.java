package dev.firmages.core.wayshrine;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.compat.modonomicon.ShrineMultiblocks;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.net.ShrinePreviewPayload;
import dev.firmages.core.shrine.BlessingIndex;
import dev.firmages.core.shrine.BlessingSources;
import dev.firmages.core.shrine.ShrineDataLoader;
import dev.firmages.core.shrine.ShrineRules;
import dev.firmages.core.shrine.ShrineService;
import dev.firmages.core.shrine.ShrineState;
import dev.firmages.core.shrine.ShrineTier;
import dev.firmages.core.text.Numbers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server logic of the Wayshrines (SPEC-himmel §28.7): registration of the cores, validation of the restored form,
 * offering, rite, prayer, lighting, the Wayshrine Stone, and the blessing sources of the lit ones. Everything runs on the
 * server thread.
 * <p>
 * Flow: a ruin refuses and shows the ghost of its restored form; the fellowship rebuilds it from the local materials
 * ({@code phase=restored}); from the type's {@code min_age} the offering of the current Age is laid on the core (one
 * item; a wrong one is refused with the expected item's name; sneak and punch the core with an empty hand to take it
 * back), the Age's rite is done, and sneaking with a held right-click on the core prays. The prayer lights it for good:
 * it carries the blessings of every awakened tier and Hearthward within {@code wayshrine.radius} while its form and the
 * home shrine stand. The first lighting drops its Wayshrine Stone. There is no travel (V5A).
 * <p>
 * Deviation from §28.7: the offering is taken back by sneaking and punching the core (not sneak and right-click, which
 * is already the prayer on the same block).
 */
public final class WayshrineService {
    static final int VALIDATE_INTERVAL = 200;
    static final int PRAY_TIMEOUT_TICKS = 10;
    static final int MIN_PRAYER_TICKS = 40;
    private static final int MESSAGE_COOLDOWN = 20;
    /** Horizontal and vertical reach of a Wayshrine's box around its core (types are at most 11 by 8 by 11). */
    static final int BOX_XZ = 6;
    static final int BOX_DOWN = 2;
    static final int BOX_UP = 9;
    public static final TagKey<Structure> STRUCTURES = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "wayshrine"));

    private WayshrineService() {}

    // ---------------------------------------------------------------- lookup

    static WayshrineData data() {
        return WayshrineDataLoader.current();
    }

    /** The highest Age the world holds. */
    public static AgeId currentAge(MinecraftServer s) {
        return ShrineRules.highest(AgeService.state(s).snapshot().unlocked());
    }

    static int awakened(MinecraftServer s) {
        return ShrineRules.awakened(AgeService.state(s).snapshot().unlocked());
    }

    public static Component typeName(String type) {
        return Component.translatableWithFallback("firmages.wayshrine.type." + type, type);
    }

    /** The registered item of the offering of the current Age, if there is one. */
    public static Optional<Item> expectedOffering(MinecraftServer s) {
        return data().offeringFor(currentAge(s)).map(ResourceLocation::tryParse).flatMap(BuiltInRegistries.ITEM::getOptional)
            .filter(i -> i != Items.AIR);
    }

    @Nullable
    static WayshrineCoreBlockEntity core(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof WayshrineCoreBlockEntity be ? be : null;
    }

    // ---------------------------------------------------------------- records

    /** A core's chunk loaded: it registers itself (worldgen, the tutorial and the isle hook all end up here). */
    static void onCoreLoaded(ServerLevel level, BlockPos pos, WayshrineCoreBlockEntity be) {
        be.invalidate();
        if (be.type.isEmpty()) return;
        WayshrineSavedData sd = WayshrineSavedData.get(level.getServer());
        GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
        if (sd.record(gp).isPresent()) return;
        BlockState st = level.getBlockState(pos);
        WayshrinePhase phase = st.hasProperty(WayshrineCoreBlock.PHASE) ? st.getValue(WayshrineCoreBlock.PHASE) : WayshrinePhase.RUIN;
        boolean intact = st.hasProperty(WayshrineCoreBlock.INTACT) && st.getValue(WayshrineCoreBlock.INTACT);
        sd.put(new WayshrineSavedData.Record(gp, be.type, phase, intact, "", 0, false));
        FirmagesCore.LOGGER.info("Wayshrine {} at {} registered ({})", be.type, gp, phase.getSerializedName());
    }

    /** A core was removed by force (creative, commands): its record goes. */
    public static void onCoreRemoved(ServerLevel level, BlockPos pos) {
        WayshrineSavedData.get(level.getServer()).remove(GlobalPos.of(level.dimension(), pos.immutable())).ifPresent(r -> {
            FirmagesCore.LOGGER.warn("Wayshrine core {} at {} was removed; its record is gone", r.type(), r.where());
            if (r.blessing()) BlessingIndex.markDirty();
        });
    }

    /** The blessing sources of the lit and intact Wayshrines (registered with {@link BlessingIndex} at mod construction). */
    public static List<BlessingSources.Source> sources(MinecraftServer s, int awakened) {
        List<BlessingSources.Source> out = new ArrayList<>();
        int radius = ServerConfig.wayshrineRadius();
        for (WayshrineSavedData.Record r : WayshrineSavedData.get(s).records()) {
            if (!r.blessing()) continue;
            BlockPos p = r.where().pos();
            out.add(new BlessingSources.Source(BlessingSources.Kind.WAYSHRINE, r.where().dimension().location().toString(), p.getX(), p.getY(),
                p.getZ(), radius, BlessingSources.allTiers(awakened), false));
        }
        return out;
    }

    // ---------------------------------------------------------------- validation

    static void tick(ServerLevel level, BlockPos pos, BlockState state, WayshrineCoreBlockEntity be) {
        long now = level.getGameTime();
        if (be.validationDue || now >= be.nextValidation) validate(level, pos, be);
        tickPrayer(level, pos, be, now);
    }

    /**
     * Checks the restored form (any rotation), updates {@code phase} and {@code intact}, the record, the beam and the
     * synced view. A change of a lit Wayshrine's {@code intact} rebuilds the blessing index.
     */
    public static ShrineMultiblocks.RingCheck validate(ServerLevel level, BlockPos pos, WayshrineCoreBlockEntity be) {
        be.validationDue = false;
        be.nextValidation = level.getGameTime() + VALIDATE_INTERVAL;
        Optional<WayshrineData.Type> t = data().type(be.type);
        BlockState st = level.getBlockState(pos);
        if (t.isEmpty() || !st.is(WayshrineRegistry.CORE.get())) {
            return new ShrineMultiblocks.RingCheck(false, null, Rotation.NONE, 0, 0, List.of("unknown Wayshrine type " + be.type), List.of());
        }
        ShrineMultiblocks.RingCheck c = ShrineMultiblocks.check(level, pos, ResourceLocation.parse(t.get().multiblock()));
        boolean valid = c.known() && c.valid();
        be.rotation = valid ? c.rotation() : null;
        WayshrinePhase phase = st.getValue(WayshrineCoreBlock.PHASE);
        WayshrinePhase np = phase == WayshrinePhase.LIT ? WayshrinePhase.LIT : valid ? WayshrinePhase.RESTORED : WayshrinePhase.RUIN;
        boolean wasIntact = st.getValue(WayshrineCoreBlock.INTACT);
        if (np != phase || valid != wasIntact) {
            level.setBlock(pos, st.setValue(WayshrineCoreBlock.PHASE, np).setValue(WayshrineCoreBlock.INTACT, valid), 3);
            if (np == WayshrinePhase.RUIN) be.prayers.clear();
        }
        WayshrineSavedData sd = WayshrineSavedData.get(level.getServer());
        GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
        WayshrineSavedData.Record rec = sd.record(gp).orElse(new WayshrineSavedData.Record(gp, be.type, np, valid, "", 0, false));
        sd.put(rec.withState(np, valid));
        if (np == WayshrinePhase.LIT && valid != wasIntact) {
            BlessingIndex.markDirty();
            FirmagesCore.LOGGER.info("Wayshrine {} at {} {}", be.type, gp, valid ? "stands again: its blessings return" : "is broken: it falls quiet");
        }
        int missing = c.known() ? Math.max(0, c.total() - c.matched()) : -1;
        int beam = beamColor(np, valid, t.get().ruinBeam());
        float width = np == WayshrinePhase.LIT ? 0.08F : 0.05F;
        int radius = ServerConfig.wayshrineRadius();
        if (missing != be.missing || beam != be.beamColor || width != be.beamWidth || radius != be.radius) {
            be.missing = missing;
            be.beamColor = beam;
            be.beamWidth = width;
            be.radius = radius;
            be.changed();
        }
        return c;
    }

    /**
     * The beam (SPEC-himmel §28.8): a faint beam in the type's colour over a ruin or a restored Wayshrine, the colour of
     * the latest awakened tier over a lit one while its form and the home shrine stand, none while it is quiet.
     */
    static int beamColor(WayshrinePhase phase, boolean intact, int ruinBeam) {
        if (phase != WayshrinePhase.LIT) return (0x40 << 24) | (ruinBeam & 0xFFFFFF);
        ShrineState.Snapshot st = ShrineState.get();
        if (!intact || st == null || !st.intact() || st.awakened() <= 0) return 0;
        int rgb = ShrineDataLoader.current().tier(Math.min(st.awakened(), BlessingSources.TIERS) - 1)
            .map(x -> x.response().beamColor()).orElse(0xFFFFD27F) & 0xFFFFFF;
        return (ShrineRules.IDLE_BEAM_ALPHA << 24) | rgb;
    }

    /** A block inside a known Wayshrine's box was placed, broken or blown up: that core validates on its next tick. */
    public static void onBlockChanged(ServerLevel level, BlockPos pos) {
        for (WayshrineSavedData.Record r : WayshrineSavedData.get(level.getServer()).records()) {
            if (r.where().dimension() != level.dimension()) continue;
            BlockPos c = r.where().pos();
            if (Math.abs(c.getX() - pos.getX()) > BOX_XZ || Math.abs(c.getZ() - pos.getZ()) > BOX_XZ) continue;
            int dy = pos.getY() - c.getY();
            if (dy < -BOX_DOWN || dy > BOX_UP) continue;
            WayshrineCoreBlockEntity be = core(level, c);
            if (be != null) be.invalidate();
        }
    }

    // ---------------------------------------------------------------- interaction

    /** Plain right-click with an empty hand: the ghost and the missing count, what it wants, or the pointer. */
    public static void inspect(ServerLevel level, BlockPos pos, ServerPlayer player) {
        WayshrineCoreBlockEntity be = core(level, pos);
        if (be == null || !throttle(be, level, player)) return;
        Optional<WayshrineData.Type> t = data().type(be.type);
        if (t.isEmpty()) {
            player.displayClientMessage(msg("firmages.wayshrine.unknown"), true);
            return;
        }
        validate(level, pos, be);
        WayshrinePhase phase = level.getBlockState(pos).getValue(WayshrineCoreBlock.PHASE);
        if (phase == WayshrinePhase.LIT) {
            if (be.rotation == null) {
                sendPreview(level, pos, t.get(), player);
                player.displayClientMessage(msg("firmages.wayshrine.broken").withStyle(ChatFormatting.GRAY), true);
            } else {
                player.displayClientMessage(WayshrinePointer.line(level.getServer(), player, false), true);
            }
            return;
        }
        Optional<Component> no = refusal(level, pos, be, true);
        if (be.rotation == null) sendPreview(level, pos, t.get(), player);
        player.displayClientMessage(no.orElse(msg("firmages.wayshrine.ready")), true);
    }

    /** Right-click with an item: the offering of the current Age is laid on a restored core. @return true if handled */
    public static boolean useWithItem(ServerLevel level, BlockPos pos, ItemStack stack, ServerPlayer player) {
        BlockState st = level.getBlockState(pos);
        if (!st.is(WayshrineRegistry.CORE.get()) || st.getValue(WayshrineCoreBlock.PHASE) != WayshrinePhase.RESTORED) return false;
        offer(level, pos, stack, player);
        return true;
    }

    public enum OfferResult { ACCEPTED, WRONG_ITEM, OCCUPIED, NOT_RESTORED, TOO_EARLY, LIT, UNKNOWN_OFFERING }

    /** Lays one item of {@code stack} on the core when it is the current Age's offering. */
    public static OfferResult offer(ServerLevel level, BlockPos pos, ItemStack stack, @Nullable ServerPlayer player) {
        WayshrineCoreBlockEntity be = core(level, pos);
        if (be == null) return OfferResult.NOT_RESTORED;
        Optional<WayshrineData.Type> t = data().type(be.type);
        validate(level, pos, be);
        BlockState st = level.getBlockState(pos);
        OfferResult r;
        Component line;
        Optional<Item> expected = expectedOffering(level.getServer());
        if (t.isEmpty() || st.getValue(WayshrineCoreBlock.PHASE) == WayshrinePhase.RUIN) {
            r = OfferResult.NOT_RESTORED;
            line = missingLine(be.missing);
        } else if (st.getValue(WayshrineCoreBlock.PHASE) == WayshrinePhase.LIT) {
            r = OfferResult.LIT;
            line = msg("firmages.wayshrine.lit_already");
        } else if (currentAge(level.getServer()).ordinal() < t.get().minAge().ordinal()) {
            r = OfferResult.TOO_EARLY;
            line = msg("firmages.wayshrine.waits");
        } else if (!be.offering.isEmpty()) {
            r = OfferResult.OCCUPIED;
            line = msg("firmages.wayshrine.occupied", be.offering.getHoverName());
        } else if (expected.isEmpty()) {
            r = OfferResult.UNKNOWN_OFFERING;
            line = msg("firmages.wayshrine.unknown_offering");
        } else if (!stack.is(expected.get())) {
            r = OfferResult.WRONG_ITEM;
            line = msg("firmages.wayshrine.wrong", new ItemStack(expected.get()).getHoverName());
        } else {
            be.offering = stack.split(1);
            be.prayerProgress = 0;
            be.prayers.clear();
            be.changed();
            level.playSound(null, pos, dev.firmages.core.shrine.ShrineRegistry.ACCEPTED.get(), SoundSource.BLOCKS, 0.8F, 1.1F);
            level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, 25, 0.4, 0.4, 0.4, 0.4);
            if (player != null) {
                player.displayClientMessage(msg("firmages.wayshrine.offered", be.offering.getHoverName()).withStyle(ChatFormatting.GOLD), false);
                unmetRites(level, pos, be, true).stream().findFirst().ifPresent(c -> player.displayClientMessage(c, false));
            }
            FirmagesCore.LOGGER.info("Wayshrine {} at {}: offering {} laid by {}", be.type, pos, be.offering, player == null ? "test" : player.getGameProfile().getName());
            return OfferResult.ACCEPTED;
        }
        if (player != null) {
            player.displayClientMessage(line, true);
            level.playSound(null, pos, dev.firmages.core.shrine.ShrineRegistry.REFUSED.get(), SoundSource.BLOCKS, 0.6F, 1.2F);
            if (r == OfferResult.NOT_RESTORED && t.isPresent()) sendPreview(level, pos, t.get(), player);
        }
        return r;
    }

    /** Sneak and punch the core with an empty hand: the offering comes back until the prayer lights the Wayshrine. */
    public static boolean takeBack(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        WayshrineCoreBlockEntity be = core(level, pos);
        if (be == null || be.offering.isEmpty()) return false;
        ItemStack back = be.offering;
        be.offering = ItemStack.EMPTY;
        be.prayerProgress = 0;
        be.prayers.clear();
        be.changed();
        if (player != null) {
            if (!player.getInventory().add(back)) player.drop(back, false);
            player.displayClientMessage(msg("firmages.wayshrine.taken_back", back.getHoverName()), true);
        }
        return true;
    }

    // ---------------------------------------------------------------- rites and prayer

    /**
     * Why the Wayshrine cannot be prayed at now, or empty when it listens. {@code skipChorus} leaves out
     * {@code players_praying}, which is met only once the others pray too (checked again at completion).
     */
    public static Optional<Component> refusal(ServerLevel level, BlockPos pos, WayshrineCoreBlockEntity be, boolean skipChorus) {
        Optional<WayshrineData.Type> t = data().type(be.type);
        if (t.isEmpty()) return Optional.of(msg("firmages.wayshrine.unknown"));
        BlockState st = level.getBlockState(pos);
        if (st.getValue(WayshrineCoreBlock.PHASE) == WayshrinePhase.LIT) return Optional.of(msg("firmages.wayshrine.lit_already"));
        if (be.rotation == null) return Optional.of(missingLine(be.missing));
        if (currentAge(level.getServer()).ordinal() < t.get().minAge().ordinal()) return Optional.of(msg("firmages.wayshrine.waits"));
        Optional<Item> expected = expectedOffering(level.getServer());
        if (expected.isEmpty()) return Optional.of(msg("firmages.wayshrine.unknown_offering"));
        if (be.offering.isEmpty() || !be.offering.is(expected.get())) {
            return Optional.of(msg("firmages.wayshrine.wants", new ItemStack(expected.get()).getHoverName()));
        }
        return unmetRites(level, pos, be, skipChorus).stream().findFirst();
    }

    /** Hints for every rite of the current Age that is not done yet. */
    static List<Component> unmetRites(ServerLevel level, BlockPos pos, WayshrineCoreBlockEntity be, boolean skipChorus) {
        List<Component> out = new ArrayList<>();
        Optional<WayshrineData.Type> t = data().type(be.type);
        if (t.isEmpty()) return out;
        for (ShrineTier.Rite rite : data().ritesFor(currentAge(level.getServer()))) {
            if (skipChorus && rite.type() == ShrineTier.Rite.Type.PLAYERS_PRAYING) continue;
            if (riteDone(level, pos, be, t.get(), rite)) continue;
            Object arg = rite.type() == ShrineTier.Rite.Type.ENERGY ? String.format(java.util.Locale.ROOT, "%,d", rite.amount()) : Numbers.words(rite.min());
            String key = rite.hint().isEmpty() ? "firmages.wayshrine.rite." + rite.type().name().toLowerCase(java.util.Locale.ROOT) : rite.hint();
            out.add(msg(key, arg));
        }
        return out;
    }

    static boolean riteDone(ServerLevel level, BlockPos pos, WayshrineCoreBlockEntity be, WayshrineData.Type t, ShrineTier.Rite rite) {
        return switch (rite.type()) {
            case BLOCKSTATE -> {
                if (be.rotation == null) yield false;
                for (BlockPos p : keyPositions(level, pos, be, t, rite.key())) {
                    if (!hasProperty(level.getBlockState(p), rite.property(), rite.value())) yield false;
                }
                yield true;
            }
            case SKY -> level.isNight() && level.canSeeSky(pos.above()) && !level.isRaining();
            case PLAYERS_PRAYING -> level.getServer().getPlayerCount() < rite.min() || be.prayers.size() >= rite.min();
            case ENERGY -> be.rotation != null && ShrineService.storedEnergy(level, keyPositions(level, pos, be, t, rite.key())) >= rite.amount();
            default -> true;
        };
    }

    static List<BlockPos> keyPositions(ServerLevel level, BlockPos pos, WayshrineCoreBlockEntity be, WayshrineData.Type t, char key) {
        if (be.rotation == null) return List.of();
        return ShrineMultiblocks.positions(level, pos, ResourceLocation.parse(t.multiblock()), be.rotation, key);
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

    /** Sneak plus held right-click on the core with an empty main hand. */
    public static void pray(ServerLevel level, BlockPos pos, ServerPlayer player) {
        WayshrineCoreBlockEntity be = core(level, pos);
        if (be == null) return;
        if (!be.prayers.containsKey(player.getUUID())) {
            validate(level, pos, be);
            Optional<Component> no = refusal(level, pos, be, true);
            if (no.isPresent()) {
                if (throttle(be, level, player)) {
                    player.displayClientMessage(no.get(), true);
                    level.playSound(null, pos, dev.firmages.core.shrine.ShrineRegistry.REFUSED.get(), SoundSource.BLOCKS, 0.5F, 0.9F);
                    if (be.rotation == null) data().type(be.type).ifPresent(t -> sendPreview(level, pos, t, player));
                }
                return;
            }
        }
        be.prayers.put(player.getUUID(), level.getGameTime());
    }

    static int prayerTicks() {
        return ServerConfig.wayshrinePrayerSeconds() * 20;
    }

    private static void tickPrayer(ServerLevel level, BlockPos pos, WayshrineCoreBlockEntity be, long now) {
        if (be.prayers.isEmpty() && be.prayerProgress == 0) return;
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
        int target = ShrineRules.prayerTarget(prayerTicks(), MIN_PRAYER_TICKS, n);
        be.prayerProgress = ShrineRules.stepProgress(be.prayerProgress, n);
        be.setChanged();
        if (n == 0) return;
        if (now % 4 == 0) {
            String bar = ShrineRules.bar(be.prayerProgress, target, 20);
            Component c = n == 1 ? msg("firmages.wayshrine.prayer.progress", bar) : msg("firmages.wayshrine.prayer.progress_many", Numbers.capitalized(n), bar);
            for (ServerPlayer p : praying) p.displayClientMessage(c.copy().withStyle(ChatFormatting.GOLD), true);
        }
        if (now % 3 == 0) level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 3 * n, 0.8, 0.4, 0.8, 0.5);
        if (be.prayerProgress >= target) {
            Optional<Component> no = refusal(level, pos, be, false);
            if (no.isPresent()) {
                be.prayerProgress = 0;
                be.prayers.clear();
                for (ServerPlayer p : praying) p.displayClientMessage(no.get(), true);
                return;
            }
            light(level, pos, be, praying.stream().map(p -> p.getGameProfile().getName()).toList());
        }
    }

    // ---------------------------------------------------------------- lighting

    /**
     * The prayer is heard: the offering is consumed, the Wayshrine lights for good, the fellowship hears of it, and the
     * first lighting drops the Wayshrine Stone.
     */
    static void light(ServerLevel level, BlockPos pos, WayshrineCoreBlockEntity be, List<String> by) {
        MinecraftServer s = level.getServer();
        AgeId age = currentAge(s);
        be.offering = ItemStack.EMPTY;
        be.prayerProgress = 0;
        be.prayers.clear();
        BlockState st = level.getBlockState(pos);
        level.setBlock(pos, st.setValue(WayshrineCoreBlock.PHASE, WayshrinePhase.LIT).setValue(WayshrineCoreBlock.INTACT, true), 3);
        WayshrineSavedData sd = WayshrineSavedData.get(s);
        GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
        WayshrineSavedData.Record old = sd.record(gp).orElse(new WayshrineSavedData.Record(gp, be.type, WayshrinePhase.LIT, true, "", 0, false));
        boolean first = !old.stoneGiven();
        sd.put(new WayshrineSavedData.Record(gp, be.type, WayshrinePhase.LIT, true, age.id(), s.overworld().getGameTime(), true));
        BlessingIndex.markDirty();
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 60, 0.6, 1.2, 0.6, 0.08);
        level.playSound(null, pos, WayshrineRegistry.LIT.get(), SoundSource.BLOCKS, 1.2F, 1.0F);
        Component line = msg("firmages.wayshrine.burns", typeName(be.type)).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) p.sendSystemMessage(line);
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, STRUCTURES);
        if (start.isValid()) level.structureManager().addReference(start); // the pointer skips it like an explorer-map target
        if (first) dropStone(level, pos, be.type);
        be.changed();
        validate(level, pos, be);
        FirmagesCore.LOGGER.info("Wayshrine {} at {} lit in {} by {}{}", be.type, gp, age.id(), by, first ? "; its stone dropped" : "");
    }

    /** The Wayshrine Stone, 1.2 blocks above the core with no velocity, never despawning, invulnerable. */
    public static ItemEntity dropStone(ServerLevel level, BlockPos pos, String type) {
        ItemEntity e = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, WayshrineStoneItem.of(type, pos), 0, 0, 0);
        e.setUnlimitedLifetime();
        e.setInvulnerable(true);
        e.setPickUpDelay(20);
        level.addFreshEntity(e);
        return e;
    }

    // ---------------------------------------------------------------- commands

    /** {@code light <pos>} (needs {@code debug.allowSimulate}): offering, rite and prayer of a restored Wayshrine at once. */
    @Nullable
    public static Component simulateLight(ServerLevel level, BlockPos pos) {
        WayshrineCoreBlockEntity be = core(level, pos);
        if (be == null) return Component.literal("No Wayshrine core at " + pos.toShortString());
        validate(level, pos, be);
        BlockState st = level.getBlockState(pos);
        if (st.getValue(WayshrineCoreBlock.PHASE) == WayshrinePhase.LIT) return msg("firmages.wayshrine.lit_already");
        if (be.rotation == null) return missingLine(be.missing);
        Optional<WayshrineData.Type> t = data().type(be.type);
        if (t.isEmpty()) return msg("firmages.wayshrine.unknown");
        if (currentAge(level.getServer()).ordinal() < t.get().minAge().ordinal()) return msg("firmages.wayshrine.waits");
        light(level, pos, be, List.of("simulate"));
        return null;
    }

    /** {@code reset <pos> [stone]}: back to ruin or restored as the world stands; {@code stone} also forgets the stone. */
    @Nullable
    public static Component reset(ServerLevel level, BlockPos pos, boolean stone) {
        WayshrineCoreBlockEntity be = core(level, pos);
        if (be == null) return Component.literal("No Wayshrine core at " + pos.toShortString());
        be.offering = ItemStack.EMPTY;
        be.prayerProgress = 0;
        be.prayers.clear();
        BlockState st = level.getBlockState(pos);
        boolean wasBlessing = st.getValue(WayshrineCoreBlock.PHASE) == WayshrinePhase.LIT && st.getValue(WayshrineCoreBlock.INTACT);
        level.setBlock(pos, st.setValue(WayshrineCoreBlock.PHASE, WayshrinePhase.RUIN).setValue(WayshrineCoreBlock.INTACT, false), 3);
        WayshrineSavedData sd = WayshrineSavedData.get(level.getServer());
        GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
        sd.record(gp).ifPresent(r -> sd.put(new WayshrineSavedData.Record(gp, r.type(), WayshrinePhase.RUIN, false, "", 0, !stone && r.stoneGiven())));
        if (wasBlessing) BlessingIndex.markDirty();
        validate(level, pos, be);
        be.changed();
        FirmagesCore.LOGGER.info("Wayshrine {} at {} reset by an operator{}", be.type, gp, stone ? " (stone forgotten)" : "");
        return null;
    }

    /** {@code status [pos]}: the lines of one Wayshrine. */
    public static List<Component> status(ServerLevel level, BlockPos pos) {
        List<Component> out = new ArrayList<>();
        WayshrineCoreBlockEntity be = core(level, pos);
        if (be == null) {
            out.add(Component.literal("No Wayshrine core at " + pos.toShortString()));
            return out;
        }
        ShrineMultiblocks.RingCheck c = validate(level, pos, be);
        BlockState st = level.getBlockState(pos);
        Optional<WayshrineData.Type> t = data().type(be.type);
        out.add(Component.literal("Wayshrine " + be.type + " at " + pos.toShortString() + ": phase " + st.getValue(WayshrineCoreBlock.PHASE).getSerializedName()
            + ", intact " + st.getValue(WayshrineCoreBlock.INTACT) + ", facing " + st.getValue(WayshrineCoreBlock.FACING).getSerializedName()
            + t.map(x -> ", min " + x.minAge().id() + ", multiblock " + x.multiblock()).orElse(", UNKNOWN TYPE")).withStyle(ChatFormatting.GOLD));
        out.add(Component.literal("Form: " + (c.valid() ? "stands, rotation " + c.rotation() : c.matched() + "/" + c.total() + " blocks, missing " + c.missing())));
        if (t.isPresent()) {
            for (Map.Entry<Character, Integer> e : missingByKey(c).entrySet()) {
                out.add(Component.literal("  key " + e.getKey() + ": " + e.getValue() + " missing"));
            }
        }
        out.add(Component.literal("Offering: " + (be.offering.isEmpty() ? "none" : be.offering) + "; wanted in " + currentAge(level.getServer()).id() + ": "
            + data().offeringFor(currentAge(level.getServer())).orElse("none")));
        out.add(Component.literal("Rites not done: " + unmetRites(level, pos, be, false).stream().map(Component::getString).toList()
            + "; prayer " + be.prayerProgress + "/" + ShrineRules.prayerTarget(prayerTicks(), MIN_PRAYER_TICKS, Math.max(1, be.prayers.size()))
            + " with " + be.prayers.size() + " praying"));
        WayshrineSavedData.get(level.getServer()).record(GlobalPos.of(level.dimension(), pos.immutable())).ifPresent(r ->
            out.add(Component.literal("Record: lit in " + (r.litAge().isEmpty() ? "-" : r.litAge()) + ", stone given " + r.stoneGiven()
                + ", carries blessings " + r.blessing() + " within " + ServerConfig.wayshrineRadius())));
        return out;
    }

    private static Map<Character, Integer> missingByKey(ShrineMultiblocks.RingCheck c) {
        Map<Character, Integer> out = new java.util.TreeMap<>();
        for (ShrineMultiblocks.Cell cell : c.cells()) {
            if (cell.counts() && !cell.ok()) out.merge(cell.key(), 1, Integer::sum);
        }
        return out;
    }

    // ---------------------------------------------------------------- helpers

    static void sendPreview(ServerLevel level, BlockPos pos, WayshrineData.Type t, ServerPlayer player) {
        Rotation r = WayshrineCoreBlock.rotationOf(level.getBlockState(pos));
        PacketDistributor.sendToPlayer(player, new ShrinePreviewPayload(ResourceLocation.parse(t.multiblock()), pos.immutable(), r.ordinal(), false));
    }

    /** "Seven blocks are missing." (words up to twelve). */
    public static Component missingLine(int missing) {
        if (missing == 1) return msg("firmages.wayshrine.missing.one");
        return msg("firmages.wayshrine.missing.many", Numbers.capitalized(Math.max(missing, 0)));
    }

    static MutableComponent msg(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /** At most one actionbar line per second per player (use repeats every four ticks while held). */
    private static boolean throttle(WayshrineCoreBlockEntity be, ServerLevel level, ServerPlayer player) {
        Long last = be.lastMessage.get(player.getUUID());
        if (last != null && level.getGameTime() - last < MESSAGE_COOLDOWN) return false;
        be.lastMessage.put(player.getUUID(), level.getGameTime());
        return true;
    }
}
