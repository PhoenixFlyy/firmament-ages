package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.anchor.AnchorRegistry;
import dev.firmages.core.anchor.AnchorRules;
import dev.firmages.core.anchor.AnchorSavedData;
import dev.firmages.core.anchor.AnchorService;
import dev.firmages.core.anchor.BlessingShardItem;
import dev.firmages.core.anchor.SkyAnchorBlock;
import dev.firmages.core.anchor.SkyAnchorBlockEntity;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginService;
import dev.firmages.core.shrine.BlessingIndex;
import dev.firmages.core.shrine.BlessingSources;
import dev.firmages.core.shrine.Blessings;
import dev.firmages.core.shrine.OfferingPlinthBlockEntity;
import dev.firmages.core.shrine.ShrineRegistry;
import dev.firmages.core.shrine.ShrineSavedData;
import dev.firmages.core.shrine.ShrineService;
import dev.firmages.core.shrine.ShrineState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Level G tests of the Sky Anchors (SPEC-himmel §29.8). Each test runs in its own batch: the Age changes and the shrine
 * state are global. The home shrine is the {@link ShrineState} cache (what every blessing source reads).
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class AnchorGameTests {
    private AnchorGameTests() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(AnchorGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean condition, String message) {
        if (!condition) FirmagesCore.LOGGER.error("Anchor GameTest assertion failed: {}", message);
        h.assertTrue(condition, message);
    }

    private static boolean reloadIdle(MinecraftServer s) {
        return AgeService.status(s).phase() == ReloadScheduler.Phase.IDLE;
    }

    private static void agesUpTo(MinecraftServer server, AgeId last) {
        for (int i = AgeId.all().size() - 1; i > last.ordinal(); i--) AgeService.simulate(server, AgeId.all().get(i), false);
        for (int i = 0; i <= last.ordinal(); i++) AgeService.simulate(server, AgeId.all().get(i), true);
    }

    private static double modifier(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, String id) {
        var m = p.getAttribute(a).getModifier(ResourceLocation.parse(id));
        return m == null ? 0 : m.amount();
    }

    /** Places an anchor at {@code pos} and sets the shards of {@code tiers} through the service (as right-clicks do). */
    private static SkyAnchorBlockEntity anchor(ServerLevel level, BlockPos pos, int... tiers) {
        level.setBlock(pos, AnchorRegistry.SKY_ANCHOR.get().defaultBlockState(), 3);
        AnchorRegistry.SKY_ANCHOR.get().setPlacedBy(level, pos, level.getBlockState(pos), null, new ItemStack(AnchorRegistry.SKY_ANCHOR_ITEM.get()));
        for (int t : tiers) AnchorService.insert(level, pos, null, BlessingShardItem.of(t));
        return (SkyAnchorBlockEntity) level.getBlockEntity(pos);
    }

    private static void removeAnchor(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).forEach(e -> e.discard());
    }

    /**
     * §29.8 anchorInAnyDimension (acceptance criteria 1 and 2): an anchor in {@code firmages:origin} with the shards of
     * tiers 0 and 2, the home shrine in the overworld. Below the Space Age it is quiet; from age_7 a player 20 blocks out
     * gets Iron Will and Hearthward only (no Skyreading: tier 1 has no shard), at 25 blocks nothing; with tier 2 not
     * awakened its shard is inert; a broken home ring and a revoke of the Space Age silence it again.
     */
    @GameTest(template = "empty", batch = "firmages_13_anchor_dimension", timeoutTicks = 2400)
    public static void anchorInAnyDimension(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = OriginService.level(server);
        if (o == null) h.fail("firmages:origin is not loaded");
        BlockPos pos = OriginArena.ARRIVAL.offset(0, 6, 0);
        ShrineState.Snapshot prev = ShrineState.get();
        BlockPos farHeart = h.absolutePos(new BlockPos(1, 1, 1)).east(500);
        ServerPlayer[] p = new ServerPlayer[1];
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                agesUpTo(server, AgeId.AGE_6);
                o.getChunk(pos); // load the chunk
                anchor(o, pos, 0, 2);
                p[0] = DebugPlayerCommands.spawn(server, o, "gt_anchor_moon", Vec3.atCenterOf(pos.east(20)));
                p[0].setGameMode(GameType.SURVIVAL);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_6 setup still running"))
            .thenIdle(25)
            .thenExecute(() -> {
                ShrineState.set(h.getLevel().dimension(), farHeart, true, 9);
                SkyAnchorBlockEntity be = (SkyAnchorBlockEntity) o.getBlockEntity(pos);
                ok(h, be != null && be.tiers() == 0b101, "the anchor holds the shards of tiers 0 and 2: " + (be == null ? "-" : be.tiers()));
                ok(h, AnchorService.status(server, 0b101) == AnchorRules.Status.BEFORE_AGE, "age_6: quiet until the Space Age");
                Blessings.apply(p[0]);
                ok(h, p[0].getMaxHealth() == 20.0F && !Blessings.refusesSpawnAt(o, pos.getX() + 10.5, pos.getY(), pos.getZ() + 0.5),
                    "age_6: no blessing and no Hearthward at the anchor");
                AgeService.simulate(server, AgeId.AGE_7, true);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_7 grant still running"))
            .thenIdle(25) // the anchor tick notices the Space Age and marks the blessing index stale
            .thenExecute(() -> {
                ShrineState.set(h.getLevel().dimension(), farHeart, true, 9);
                p[0].setPos(pos.getX() + 20.5, pos.getY(), pos.getZ() + 0.5);
                Blessings.apply(p[0]);
                ok(h, Math.abs(p[0].getMaxHealth() - 21.0F) < 1e-4, "20 blocks from the anchor on another world: Iron Will (" + p[0].getMaxHealth() + ")");
                ok(h, modifier(p[0], Attributes.MOVEMENT_SPEED, "firmages:blessing/skyreading/0") == 0, "no Skyreading: tier 1 has no shard");
                ok(h, Blessings.refusesSpawnAt(o, pos.getX() + 10.5, pos.getY(), pos.getZ() + 0.5), "Hearthward with the tier-0 shard");
                ok(h, !Blessings.refusesSpawnAt(o, pos.getX() + 30.5, pos.getY(), pos.getZ() + 0.5), "no Hearthward 30 blocks out");
                ok(h, !Blessings.refusesSpawnAt(h.getLevel(), pos.getX() + 10.5, pos.getY(), pos.getZ() + 0.5), "the same spot in the overworld is not covered");
                p[0].setPos(pos.getX() + 25.5, pos.getY(), pos.getZ() + 0.5);
                Blessings.apply(p[0]);
                ok(h, p[0].getMaxHealth() == 20.0F, "25 blocks out: nothing");
                p[0].setPos(pos.getX() + 20.5, pos.getY(), pos.getZ() + 0.5);
                ShrineState.set(h.getLevel().dimension(), farHeart, true, 2); // tier 2 asleep
                Blessings.apply(p[0]);
                ok(h, p[0].getMaxHealth() == 20.0F && Blessings.refusesSpawnAt(o, pos.getX() + 10.5, pos.getY(), pos.getZ() + 0.5),
                    "an unawakened tier's shard is inert, the tier-0 shard still keeps Hearthward");
                ShrineState.set(h.getLevel().dimension(), farHeart, false, 9);
                Blessings.apply(p[0]);
                ok(h, p[0].getMaxHealth() == 20.0F && !Blessings.refusesSpawnAt(o, pos.getX() + 10.5, pos.getY(), pos.getZ() + 0.5),
                    "a broken home ring silences the anchor");
                ShrineState.set(h.getLevel().dimension(), farHeart, true, 9);
                AnchorService.refresh(o, pos, (SkyAnchorBlockEntity) o.getBlockEntity(pos));
                SkyAnchorBlockEntity be = (SkyAnchorBlockEntity) o.getBlockEntity(pos);
                ok(h, be.status() == AnchorRules.Status.ACTIVE && be.activeMask() == 0b101 && be.blessings().size() == 2 && be.beamColor() != 0
                    && o.getBlockState(pos).getValue(SkyAnchorBlock.ACTIVE), "the synced view: active, two blessings, a beam");
                AgeService.simulate(server, AgeId.AGE_7, false);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_7 revoke still running"))
            .thenIdle(25)
            .thenExecute(() -> {
                ShrineState.set(h.getLevel().dimension(), farHeart, true, 9);
                Blessings.apply(p[0]);
                ok(h, p[0].getMaxHealth() == 20.0F, "after a revoke of the Space Age the anchor is quiet");
            })
            .thenExecute(() -> {
                if (prev == null) ShrineState.clear();
                else ShrineState.set(prev.dimension(), prev.heart(), prev.intact(), prev.awakened());
                Blessings.apply(p[0], List.of());
                DebugPlayerCommands.despawn(p[0]);
                removeAnchor(o, pos);
                agesUpTo(server, AgeId.DAWN);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(false);
                ok(h, AnchorSavedData.get(server).tiers(GlobalPos.of(o.dimension(), pos)).isEmpty(), "the record went with the block");
            })
            .thenSucceed();
    }

    /**
     * §29.8 anchorKeepsThroughLending (acceptance criterion 3): in age_8 the Arcane Keystone relic (stand-in: amethyst
     * shard) is lent and returned through the shrine, then the Gathering's records are written (every relic lent,
     * {@code gathered}); an anchor's blessings in another dimension never change, because lending and the Gathering touch
     * neither the shrine's intact flag nor its awakened tiers. A shard is no relic: the plinth refuses it.
     */
    @GameTest(template = "shrine_area", batch = "firmages_13_anchor_lending", timeoutTicks = 3000)
    public static void anchorKeepsThroughLending(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        ServerLevel o = OriginService.level(server);
        if (o == null) h.fail("firmages:origin is not loaded");
        BlockPos heart = h.absolutePos(new BlockPos(5, 1, 5));
        BlockPos keystone = heart.east(5);
        BlockPos pos = OriginArena.ARRIVAL.offset(4, 6, 0);
        ShrineState.Snapshot prev = ShrineState.get();
        boolean gatheredBefore = ShrineSavedData.get(server).gathered();
        ServerPlayer[] p = new ServerPlayer[1];
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                agesUpTo(server, AgeId.AGE_8);
                level.setBlock(heart, ShrineRegistry.SHRINE_HEART.get().defaultBlockState(), 3);
                level.setBlock(keystone, ShrineRegistry.OFFERING_PLINTH.get().defaultBlockState(), 3);
                OfferingPlinthBlockEntity kb = (OfferingPlinthBlockEntity) level.getBlockEntity(keystone);
                kb.offer(new ItemStack(Items.AMETHYST_SHARD), 3);
                kb.enshrine(3);
                ShrineSavedData.get(server).putRelic(new ShrineSavedData.Relic(keystone, 3, "minecraft:amethyst_shard"));
                o.getChunk(pos);
                anchor(o, pos, 0, 2, 3);
                p[0] = DebugPlayerCommands.spawn(server, o, "gt_anchor_lend", Vec3.atCenterOf(pos.east(10)));
                p[0].setGameMode(GameType.SURVIVAL);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_8 setup still running"))
            .thenIdle(25)
            .thenExecute(() -> {
                ShrineState.set(level.dimension(), heart, true, 9);
                BlessingIndex.markDirty();
                int before = Blessings.maskAt(p[0]);
                List<BlessingSources.Source> sources = BlessingIndex.current(server);
                ok(h, before == 0b1101, "tiers 0, 2 and 3 of the anchor reach the player in The Origin: " + Integer.toBinaryString(before));
                // ---- lend and return through the shrine
                ItemStack lent = ShrineService.lend(level, keystone, null);
                ok(h, lent != null && !lent.isEmpty() && lent.is(Items.AMETHYST_SHARD), "age_8: the Keystone is lent");
                ok(h, ShrineState.get().intact() && ShrineState.get().awakened() == 9, "lending leaves the shrine state");
                BlessingIndex.markDirty();
                ok(h, Blessings.maskAt(p[0]) == before && BlessingIndex.current(server).equals(sources), "lent: the anchor is unchanged");
                ok(h, ShrineService.offerAtPlinth(level, keystone, BlessingShardItem.of(3), null) != ShrineService.OfferResult.RETURNED,
                    "a shard is no relic: the waiting plinth refuses it");
                ok(h, ShrineService.offerAtPlinth(level, keystone, new ItemStack(Items.AMETHYST_SHARD), null) == ShrineService.OfferResult.RETURNED,
                    "the Keystone comes back");
                BlessingIndex.markDirty();
                ok(h, Blessings.maskAt(p[0]) == before, "returned: the anchor is unchanged");
                // ---- the Gathering's records: gathered, every relic lent
                ShrineSavedData sd = ShrineSavedData.get(server);
                sd.setGathered(true);
                sd.putRelic(new ShrineSavedData.Relic(keystone, 3, "minecraft:amethyst_shard", true));
                BlessingIndex.markDirty();
                ok(h, Blessings.maskAt(p[0]) == before && BlessingIndex.current(server).equals(sources), "after the Gathering: the anchor is unchanged");
            })
            .thenExecute(() -> {
                ShrineSavedData sd = ShrineSavedData.get(server);
                sd.setGathered(gatheredBefore);
                sd.removeRelic(keystone);
                if (prev == null) ShrineState.clear();
                else ShrineState.set(prev.dimension(), prev.heart(), prev.intact(), prev.awakened());
                Blessings.apply(p[0], List.of());
                DebugPlayerCommands.despawn(p[0]);
                removeAnchor(o, pos);
                ShrineService.extract(level, keystone);
                level.setBlock(keystone, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(heart, Blocks.AIR.defaultBlockState(), 3);
                agesUpTo(server, AgeId.DAWN);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> ServerConfig.DEBUG_ALLOW_SIMULATE.set(false))
            .thenSucceed();
    }

    /**
     * §29.8 anchorShards (acceptance criterion 5): a shard goes into its tier's slot, a second one of that tier is
     * refused, sneak-use takes the highest back, the block entity and the SavedData survive an NBT round trip, and
     * breaking the anchor drops it with every shard; the record goes with it.
     */
    @GameTest(template = "empty", batch = "firmages_13_anchor_shards")
    public static void anchorShards(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        GlobalPos where = GlobalPos.of(level.dimension(), pos);
        SkyAnchorBlockEntity be = anchor(level, pos);
        ok(h, AnchorSavedData.get(server).tiers(where).orElse(-1) == 0, "placed: a record without shards");
        ItemStack s5 = BlessingShardItem.of(5);
        ok(h, AnchorService.insert(level, pos, null, s5) && s5.isEmpty() && be.tiers() == 0b10_0000, "the shard of tier 5 is set and used up");
        ItemStack again = BlessingShardItem.of(5);
        ok(h, !AnchorService.insert(level, pos, null, again) && again.getCount() == 1, "a second shard of tier 5 is refused and kept");
        ok(h, AnchorService.insert(level, pos, null, BlessingShardItem.of(1)) && AnchorService.insert(level, pos, null, BlessingShardItem.of(8)),
            "tiers 1 and 8 join");
        ok(h, AnchorSavedData.get(server).tiers(where).orElse(-1) == 0b1_0010_0010, "the record follows: "
            + Integer.toBinaryString(AnchorSavedData.get(server).tiers(where).orElse(-1)));
        ItemStack back = AnchorService.takeHighest(level, pos);
        ok(h, BlessingShardItem.tier(back) == 8 && be.tiers() == 0b10_0010, "sneak-use takes the highest (tier 8) back");
        ok(h, back.getHoverName().getString().equals(net.minecraft.network.chat.Component.translatable("item.firmages.blessing_shard.tier_8").getString()),
            "named by its tier");
        // round trips
        SkyAnchorBlockEntity copy = new SkyAnchorBlockEntity(pos, level.getBlockState(pos));
        copy.loadWithComponents(be.saveWithFullMetadata(server.registryAccess()), server.registryAccess());
        ok(h, copy.tiers() == 0b10_0010, "block entity NBT round trip");
        ok(h, AnchorSavedData.reload(AnchorSavedData.get(server), server.registryAccess()).tiers(where).orElse(-1) == 0b10_0010, "SavedData round trip");
        ItemStack round = ItemStack.parseOptional(server.registryAccess(), (net.minecraft.nbt.CompoundTag) BlessingShardItem.of(4).save(server.registryAccess()));
        ok(h, BlessingShardItem.tier(round) == 4, "the tier component survives an item round trip");
        // breaking drops the anchor and its shards
        level.destroyBlock(pos, true);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        List<Integer> tiers = drops.stream().map(e -> BlessingShardItem.tier(e.getItem())).filter(t -> t >= 0).sorted().toList();
        boolean anchorDropped = drops.stream().anyMatch(e -> e.getItem().is(AnchorRegistry.SKY_ANCHOR_ITEM.get()));
        ok(h, tiers.equals(List.of(1, 5)), "the shards of tiers 1 and 5 drop: " + tiers);
        ok(h, anchorDropped, "the anchor drops itself");
        ok(h, AnchorSavedData.get(server).tiers(where).isEmpty(), "the record is gone");
        drops.forEach(ItemEntity::discard);
        h.succeed();
    }
}
