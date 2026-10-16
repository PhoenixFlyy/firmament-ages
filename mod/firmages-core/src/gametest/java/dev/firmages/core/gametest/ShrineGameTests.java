package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.ceremony.CeremonyService;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.immersion.ImmersionService;
import dev.firmages.core.net.AgeTransitionPayload;
import dev.firmages.core.shrine.Blessings;
import dev.firmages.core.shrine.OfferingPlinthBlock;
import dev.firmages.core.shrine.OfferingPlinthBlockEntity;
import dev.firmages.core.shrine.ShrineHeartBlock;
import dev.firmages.core.shrine.ShrineHeartBlockEntity;
import dev.firmages.core.shrine.ShrineRegistry;
import dev.firmages.core.compat.modonomicon.ShrineMultiblocks;
import dev.firmages.core.shrine.ShrineData;
import dev.firmages.core.shrine.ShrineDataLoader;
import dev.firmages.core.shrine.ShrineSavedData;
import dev.firmages.core.shrine.ShrineService;
import dev.firmages.core.shrine.ShrineTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Level G tests of the shrine and the ceremony (SPEC §12): a tier-0 shrine from vanilla stand-ins (the shrine tags
 * list TFC blocks as optional entries plus cobblestone, logs and hay), offering acceptance and refusal, the rite,
 * broken-structure refusal, prayer completion through the simulate path (no players online), relic persistence,
 * the Hearthward sanctuary and the ceremony payload; since M6 also Keystone lending, the blessing effects and the
 * energy rite's capability read; since the harmony round (SPEC §35, §36) the proof of work of an offering and the
 * heart's power draw. The test datapack {@code firmages_test:offerings} replaces the KubeJS signature items (absent
 * here): Hearthstone by a heart of the sea, Arcane Keystone by an amethyst shard, Pressure Core by an emerald, Data
 * Matrix by a diamond, Quantum Core by a nether star; its {@code tallies.json} asks eight iron ingots in the
 * Industrial Age and nothing later.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class ShrineGameTests {
    private static final BlockPos HEART = new BlockPos(5, 1, 5);
    private static final BlockPos PLINTH = new BlockPos(5, 1, 3);
    private static final BlockPos POST = new BlockPos(3, 1, 3);
    private static final BlockPos STONE = new BlockPos(6, 1, 5);

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(ShrineGameTests.class);
    }

    private static void buildRing0(GameTestHelper h) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) h.setBlock(HEART.offset(dx, 0, dz), Blocks.COBBLESTONE);
            }
        }
        for (int[] c : new int[][] {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
            h.setBlock(HEART.offset(c[0], 0, c[1]), Blocks.OAK_LOG);
            h.setBlock(HEART.offset(c[0], 1, c[1]), Blocks.OAK_LOG);
            h.setBlock(HEART.offset(c[0], 2, c[1]), Blocks.HAY_BLOCK);
        }
        h.setBlock(PLINTH, ShrineRegistry.OFFERING_PLINTH.get());
        h.setBlock(HEART, ShrineRegistry.SHRINE_HEART.get());
    }

    /** Assert and log: a sequence keeps running after a failed step and reports only the last failure. */
    private static void ok(GameTestHelper h, boolean condition, String message) {
        if (!condition) FirmagesCore.LOGGER.error("Shrine GameTest assertion failed: {}", message);
        h.assertTrue(condition, message);
    }

    private static boolean isKey(Optional<Component> c, String key) {
        return c.isPresent() && c.get().getContents() instanceof TranslatableContents t && t.getKey().equals(key);
    }

    private static String text(Optional<Component> c) {
        return c.map(x -> x.getContents() instanceof TranslatableContents t ? t.getKey() : x.getString()).orElse("<none>");
    }

    private static boolean reloadIdle(MinecraftServer s) {
        return AgeService.status(s).phase() == ReloadScheduler.Phase.IDLE;
    }

    /**
     * Data level, no ritual: the loaded shrine data (mod jar plus the test datapack) defines tiers 0..8, each with its
     * own ring that Modonomicon loaded, one plinth at distance N + 2, the rite keys present in the ring, a known
     * blessing and a grant of age_(N+1); the offerings cover age_0..age_8.
     */
    @GameTest(template = "empty", batch = "firmages_1_selftest")
    public static void shrineLadderData(GameTestHelper h) {
        ShrineData d = ShrineDataLoader.current();
        ok(h, d.errors().isEmpty(), "shrine data errors: " + d.errors());
        ok(h, d.highestRing() == ShrineData.MAX_TIER, "rings up to " + d.highestRing());
        BlockPos heart = h.absolutePos(new BlockPos(1, 1, 1));
        for (int n = 0; n <= ShrineData.MAX_TIER; n++) {
            ShrineTier t = d.tier(n).orElse(null);
            ok(h, t != null && !t.fallback(), "tier " + n + " has its own file");
            ok(h, t.grants().equals("age_" + (n + 1)), "tier " + n + " grants " + t.grants());
            ok(h, d.offeringFor(n).isPresent(), "tier " + n + " has an offering");
            ok(h, t.blessing().map(d.blessings()::containsKey).orElse(false), "tier " + n + " blessing " + t.blessing());
            ResourceLocation id = ResourceLocation.parse(t.multiblock().orElseThrow());
            ok(h, ShrineMultiblocks.get(id).isPresent(), id + " loaded by Modonomicon");
            List<BlockPos> plinth = ShrineMultiblocks.positions(h.getLevel(), heart, id, Rotation.NONE, t.plinthKey());
            int dist = n + 2;
            ok(h, plinth.size() == 1 && plinth.get(0).getY() == heart.getY()
                && Math.abs(plinth.get(0).getX() - heart.getX()) + Math.abs(plinth.get(0).getZ() - heart.getZ()) == dist
                && (plinth.get(0).getX() == heart.getX() || plinth.get(0).getZ() == heart.getZ()), id + " plinth at distance " + dist + ": " + plinth);
            for (ShrineTier.Rite r : t.rites()) {
                ok(h, r.type().supported(), "tier " + n + " rite " + r.type() + " is implemented");
                if (r.type() == ShrineTier.Rite.Type.BLOCKSTATE) {
                    ok(h, !ShrineMultiblocks.positions(h.getLevel(), heart, id, Rotation.NONE, r.key()).isEmpty(), id + " has rite key " + r.key());
                }
            }
        }
        for (int a = 0; a <= ShrineData.MAX_TIER; a++) ok(h, d.offerings().containsKey("age_" + a), "offering for age_" + a);
        ok(h, d.tier(ShrineData.MAX_TIER + 1).isEmpty(), "no tier after age_8");
        h.succeed();
    }

    /** Sets AgeState to exactly {@code dawn..last} through the simulate path. */
    private static void agesUpTo(MinecraftServer server, AgeId last) {
        for (int i = AgeId.all().size() - 1; i > last.ordinal(); i--) AgeService.simulate(server, AgeId.all().get(i), false);
        for (int i = 0; i <= last.ordinal(); i++) AgeService.simulate(server, AgeId.all().get(i), true);
    }

    /**
     * SPEC §7.4: in age_8 the Arcane Keystone relic (stand-in: amethyst shard, on the tier-3 plinth 5 east of the heart)
     * is lent by sneak-use with an empty hand; while it is away the Quantum Core (stand-in: nether star) is refused on
     * the tier-8 plinth, the plinth cannot be broken and the relic record, ring validation and intact flag stay; the
     * relic (or one of the rite's items) comes back by use. Before age_8 nothing is lent.
     */
    @GameTest(template = "shrine_area", batch = "firmages_3_shrine_lend", timeoutTicks = 3000)
    public static void keystoneLending(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        BlockPos heart = h.absolutePos(HEART);
        BlockPos keystone = h.absolutePos(HEART.east(5));
        BlockPos quantum = heart.north(10); // ring 8's plinth distance, outside the 11x11 template
        net.minecraft.server.level.ServerPlayer[] p = new net.minecraft.server.level.ServerPlayer[1];
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                agesUpTo(server, AgeId.AGE_7);
                h.setBlock(HEART, ShrineRegistry.SHRINE_HEART.get());
                h.setBlock(HEART.east(5), ShrineRegistry.OFFERING_PLINTH.get());
                level.setBlock(quantum, ShrineRegistry.OFFERING_PLINTH.get().defaultBlockState(), 3);
                OfferingPlinthBlockEntity kb = (OfferingPlinthBlockEntity) level.getBlockEntity(keystone);
                kb.offer(new ItemStack(Items.AMETHYST_SHARD), 3);
                kb.enshrine(3);
                ShrineSavedData.get(server).putRelic(new ShrineSavedData.Relic(keystone, 3, "minecraft:amethyst_shard"));
                p[0] = dev.firmages.core.command.DebugPlayerCommands.spawn(server, level, "gt_lender", net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 1, 5))));
                p[0].setGameMode(GameType.SURVIVAL);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_7 setup still running"))
            .thenExecute(() -> {
                OfferingPlinthBlockEntity kb = (OfferingPlinthBlockEntity) level.getBlockEntity(keystone);
                p[0].setShiftKeyDown(true);
                ShrineService.usePlinthEmptyHand(level, keystone, p[0]);
                ok(h, kb.isRelic() && !kb.isLent(), "age_7: the shrine lends nothing");
                AgeService.simulate(server, AgeId.AGE_8, true);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_8 grant still running"))
            .thenExecute(() -> {
                ShrineHeartBlockEntity be = (ShrineHeartBlockEntity) level.getBlockEntity(heart);
                ShrineService.validate(level, heart, be);
                int ring = be.validRing();
                boolean intact = ShrineSavedData.get(server).intact();
                int relics = ShrineSavedData.get(server).relics().size();
                OfferingPlinthBlockEntity kb = (OfferingPlinthBlockEntity) level.getBlockEntity(keystone);

                // ---- lend
                p[0].setShiftKeyDown(true);
                ShrineService.usePlinthEmptyHand(level, keystone, p[0]);
                ok(h, kb.isLent() && kb.isEmpty() && kb.tier() == 3, "age_8: the Keystone is lent, the plinth keeps its tier");
                ok(h, p[0].getInventory().countItem(Items.AMETHYST_SHARD) == 1, "the player holds the lent Keystone");
                ok(h, level.getBlockState(keystone).getValue(OfferingPlinthBlock.AWAKENED), "the waiting plinth still glows");
                ok(h, level.getBlockState(keystone).getDestroyProgress(h.makeMockPlayer(GameType.SURVIVAL), level, keystone) == 0.0F,
                    "a waiting plinth cannot be broken in survival");
                ShrineSavedData sd = ShrineSavedData.get(server);
                ok(h, sd.relics().size() == relics && sd.relicForTier(3).map(ShrineSavedData.Relic::lent).orElse(false), "relic record kept, marked lent");
                ok(h, ShrineService.lentRelicBlocking(server, 8).isPresent() && ShrineService.lentRelicBlocking(server, 7).isEmpty(),
                    "the lent Keystone blocks tier 8 only");
                ShrineService.validate(level, heart, be);
                ok(h, be.validRing() == ring && ShrineSavedData.get(server).intact() == intact, "ring validity and intact flag unchanged while lent");
                // persistence of the lent state
                OfferingPlinthBlockEntity copy = new OfferingPlinthBlockEntity(keystone, level.getBlockState(keystone));
                copy.loadWithComponents(kb.saveWithFullMetadata(server.registryAccess()), server.registryAccess());
                ok(h, copy.isLent() && copy.tier() == 3 && copy.isEmpty(), "lent plinth NBT round trip");
                ok(h, ShrineSavedData.reload(sd, server.registryAccess()).relicForTier(3).map(ShrineSavedData.Relic::lent).orElse(false), "lent flag in SavedData round trip");

                // ---- the Quantum Core is refused while the Keystone is away
                ShrineService.OfferResult r = ShrineService.offerAtPlinth(level, quantum, new ItemStack(Items.NETHER_STAR), null);
                ok(h, r == ShrineService.OfferResult.RELIC_LENT, "age_8 offering refused while lent: " + r);
                ok(h, ShrineService.offerAtPlinth(level, keystone, new ItemStack(Items.STONE), null) == ShrineService.OfferResult.WRONG_ITEM && kb.isLent(),
                    "a wrong item does not fill the waiting plinth");

                // ---- return
                ItemStack back = new ItemStack(Items.AMETHYST_SHARD, 2);
                ok(h, ShrineService.offerAtPlinth(level, keystone, back, null) == ShrineService.OfferResult.RETURNED, "Keystone returned");
                ok(h, back.getCount() == 1 && kb.isRelic() && !kb.isLent() && kb.item().is(Items.AMETHYST_SHARD), "one item enshrined again");
                ok(h, !ShrineSavedData.get(server).relicForTier(3).map(ShrineSavedData.Relic::lent).orElse(true)
                    && ShrineService.lentRelicBlocking(server, 8).isEmpty(), "record no longer lent, tier 8 free");
                ShrineService.OfferResult after = ShrineService.offerAtPlinth(level, quantum, new ItemStack(Items.NETHER_STAR), null);
                ok(h, after == ShrineService.OfferResult.RUINS, "with the Keystone back only the missing rings stop the offering: " + after);
                ok(h, ShrineService.lend(level, keystone, null) != null && kb.isLent(), "it can be lent again in age_8");
                ok(h, ShrineService.offerAtPlinth(level, keystone, new ItemStack(Items.AMETHYST_SHARD), null) == ShrineService.OfferResult.RETURNED, "and returned");

                // ---- clean up
                dev.firmages.core.command.DebugPlayerCommands.despawn(p[0]);
                ShrineService.extract(level, keystone);
                level.setBlock(quantum, Blocks.AIR.defaultBlockState(), 3);
                h.setBlock(HEART.east(5), Blocks.AIR);
                h.setBlock(HEART, Blocks.AIR);
                agesUpTo(server, AgeId.DAWN);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> ServerConfig.DEBUG_ALLOW_SIMULATE.set(false))
            .thenSucceed();
    }

    private static double modifier(net.minecraft.server.level.ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, String id) {
        net.minecraft.world.entity.ai.attributes.AttributeModifier m = p.getAttribute(a).getModifier(ResourceLocation.parse(id));
        return m == null ? 0 : m.amount();
    }

    /**
     * SPEC §7.6 (M6): the shipped blessings of nine awakened tiers on a player inside the blessing radius: +15 % max
     * health, +5 % speed, +5 % break speed, +0.5 reach, -20 % fall damage and +1 safe fall block, +1 luck, +10 % XP;
     * all gone outside the radius or with a broken shrine, back far from the heart within a lit Wayshrine's radius (the
     * blessing sources of SPEC-himmel §29.5 replace {@code shrine.blessings.everywhere});
     * three awakened tiers give only theirs. Status effects (no shipped blessing uses one) on a synthetic blessing.
     */
    @GameTest(template = "empty", batch = "firmages_3_shrine_bless")
    public static void blessingEffects(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        dev.firmages.core.shrine.ShrineState.Snapshot prev = dev.firmages.core.shrine.ShrineState.get();
        net.minecraft.server.level.ServerPlayer p = dev.firmages.core.command.DebugPlayerCommands.spawn(server, level, "gt_blessed",
            net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
        try {
            p.setGameMode(GameType.SURVIVAL);
            BlockPos near = p.blockPosition().east(10);
            dev.firmages.core.shrine.ShrineState.set(level.dimension(), near, true, 9);
            Blessings.apply(p);
            ok(h, Math.abs(p.getMaxHealth() - 23.0F) < 1e-4, "max health 20 * 1.15, was " + p.getMaxHealth());
            ok(h, modifier(p, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, "firmages:blessing/iron_will/0") == 0.05
                && modifier(p, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, "firmages:blessing/resolve/0") == 0.05, "health modifier ids per blessing");
            ok(h, modifier(p, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, "firmages:blessing/skyreading/0") == 0.05, "Skyreading speed");
            ok(h, modifier(p, net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED, "firmages:blessing/tireless_hands/0") == 0.05, "Tireless Hands");
            ok(h, Math.abs(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE) - 5.0) < 1e-6, "Long Arm reach 5.0");
            ok(h, Math.abs(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FALL_DAMAGE_MULTIPLIER) - 0.8) < 1e-6
                && Math.abs(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE) - 4.0) < 1e-6, "Starwalker");
            ok(h, Math.abs(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.LUCK) - 1.0) < 1e-6, "Resolve luck");
            ok(h, Math.abs(Blessings.xpBonus(p) - 0.10) < 1e-9, "Attunement +10 % XP");
            net.minecraft.world.entity.ExperienceOrb orb = new net.minecraft.world.entity.ExperienceOrb(level, p.getX(), p.getY(), p.getZ(), 20);
            Blessings.onPickupXp(new net.neoforged.neoforge.event.entity.player.PlayerXpEvent.PickupXp(p, orb));
            ok(h, orb.value == 22, "an orb of 20 is worth 22, was " + orb.value);
            Blessings.apply(p);
            ok(h, Math.abs(p.getMaxHealth() - 23.0F) < 1e-4, "applying again changes nothing");

            dev.firmages.core.shrine.ShrineState.set(level.dimension(), p.blockPosition().east(500), true, 9);
            Blessings.apply(p);
            ok(h, p.getMaxHealth() == 20.0F && Blessings.xpBonus(p) == 0 && modifier(p, net.minecraft.world.entity.ai.attributes.Attributes.LUCK, "firmages:blessing/resolve/1") == 0,
                "outside the radius: no blessing");
            net.minecraft.core.GlobalPos way = net.minecraft.core.GlobalPos.of(level.dimension(), p.blockPosition().east(3));
            dev.firmages.core.wayshrine.WayshrineSavedData.get(server).put(new dev.firmages.core.wayshrine.WayshrineSavedData.Record(way, "stand_in",
                dev.firmages.core.wayshrine.WayshrinePhase.LIT, true, "age_9", 0, true));
            dev.firmages.core.shrine.BlessingIndex.markDirty();
            Blessings.apply(p);
            ok(h, Math.abs(p.getMaxHealth() - 23.0F) < 1e-4 && Blessings.xpBonus(p) > 0, "a lit Wayshrine carries them far from the heart");
            dev.firmages.core.wayshrine.WayshrineSavedData.get(server).remove(way);
            dev.firmages.core.shrine.BlessingIndex.markDirty();

            dev.firmages.core.shrine.ShrineState.set(level.dimension(), near, false, 9);
            Blessings.apply(p);
            ok(h, p.getMaxHealth() == 20.0F && Blessings.activeBlessings(server).isEmpty(), "a broken shrine: blessings rest");

            dev.firmages.core.shrine.ShrineState.set(level.dimension(), near, true, 3);
            Blessings.apply(p);
            ok(h, Math.abs(p.getMaxHealth() - 21.0F) < 1e-4 && modifier(p, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, "firmages:blessing/skyreading/0") == 0.05
                && Blessings.xpBonus(p) == 0 && p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.LUCK) == 0,
                "three awakened tiers: Hearthward, Skyreading, Iron Will only");

            // status effects: near only, never refreshed far away
            dev.firmages.core.shrine.Blessing glow = new dev.firmages.core.shrine.Blessing("firmages_test:glow", "", "", java.util.Set.of(),
                List.of(new dev.firmages.core.shrine.Blessing.Effect(dev.firmages.core.shrine.Blessing.Effect.Kind.MOB_EFFECT, "minecraft:night_vision", "", 0, 0)));
            Blessings.apply(p, List.of(glow));
            net.minecraft.world.effect.MobEffectInstance nv = p.getEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
            ok(h, nv != null && nv.isAmbient() && nv.getDuration() > 200, "status effect near the shrine: " + nv);
            p.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
            dev.firmages.core.shrine.ShrineState.set(level.dimension(), p.blockPosition().east(500), true, 9);
            Blessings.apply(p);
            ok(h, !p.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION), "no status effect away from every source");
            ok(h, p.getMaxHealth() == 20.0F, "the other modifiers are gone with their blessings");
        } finally {
            if (prev == null) dev.firmages.core.shrine.ShrineState.clear();
            else dev.firmages.core.shrine.ShrineState.set(prev.dimension(), prev.heart(), prev.intact(), prev.awakened());
            Blessings.apply(p, List.of());
            dev.firmages.core.command.DebugPlayerCommands.despawn(p);
        }
        h.succeed();
    }

    /** The energy rite reads NeoForge's block energy capability: an IE HV capacitor (ring 5) charged through it. */
    @GameTest(template = "empty", batch = "firmages_3_shrine_bless")
    public static void energyRiteReadsCapacitors(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        net.minecraft.world.level.block.Block cap = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("immersiveengineering:capacitor_hv"));
        ok(h, cap != Blocks.AIR, "IE HV capacitor registered");
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(pos, cap.defaultBlockState(), 3);
        ok(h, ShrineService.storedEnergy(level, List.of(pos)) == 0, "empty capacitor");
        net.neoforged.neoforge.energy.IEnergyStorage e = null;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            net.neoforged.neoforge.energy.IEnergyStorage s = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, pos, d);
            if (s != null && s.canReceive()) e = s;
        }
        ok(h, e != null, "capacitor takes FE on some side");
        int in = 0;
        for (int i = 0; i < 50; i++) in += e.receiveEnergy(100_000, false);
        long stored = ShrineService.storedEnergy(level, List.of(pos));
        FirmagesCore.LOGGER.info("GameTest energy rite: IE HV capacitor took {} FE, the rite reads {} FE", in, stored);
        ok(h, in > 0 && stored == in, "the rite sums the stored FE: inserted " + in + ", read " + stored);
        ok(h, ShrineService.storedEnergy(level, List.of(pos, pos.above())) == in, "blocks without storage count 0");
        h.succeed();
    }

    @GameTest(template = "shrine_area", batch = "firmages_3_shrine", timeoutTicks = 3000)
    public static void shrineRitualEndToEnd(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        BlockPos heart = h.absolutePos(HEART);
        BlockPos plinth = h.absolutePos(PLINTH);
        int[] sentBefore = new int[1];
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                // Other batches may run before this one (batch order is a hash order): start from age_0 exactly.
                for (int i = AgeId.all().size() - 1; i >= 2; i--) AgeService.simulate(server, AgeId.all().get(i), false);
                AgeService.simulate(server, AgeId.AGE_0, true);
                buildRing0(h);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_0 grant still running"))
            .thenExecute(() -> {
                // ---- structure validation (tier 0 = Stone Age, offering grants age_1)
                ok(h, ShrineSavedData.get(server).heart().map(g -> g.pos().equals(heart)).orElse(false), "heart registered in SavedData");
                ShrineHeartBlockEntity be = (ShrineHeartBlockEntity) level.getBlockEntity(heart);
                ShrineService.validate(level, heart, be);
                ok(h, be.validRing() == 0, "ring 0 valid, ring 1 not: validRing " + be.validRing());
                BlockState hs = level.getBlockState(heart);
                ok(h, hs.getValue(ShrineHeartBlock.READY) && hs.getValue(ShrineHeartBlock.AWAKENED) == 0, "heart ready, awakened 0: " + hs);
                ok(h, !ShrineService.mayPlaceHeart(level, heart.offset(20, 0, 0), null), "second heart refused");
                // A missing post breaks the ring.
                h.setBlock(POST, Blocks.AIR);
                ShrineService.validate(level, heart, be);
                ok(h, be.validRing() == -1 && !level.getBlockState(heart).getValue(ShrineHeartBlock.READY), "ring 0 broken without its post");
                ok(h, isKey(ShrineService.prayerRefusal(server), "firmages.shrine.ruins"), "prayer refused in ruins: " + text(ShrineService.prayerRefusal(server)));
                h.setBlock(POST, Blocks.OAK_LOG);
                ShrineService.validate(level, heart, be);
                ok(h, be.validRing() == 0, "ring 0 valid again");

                // ---- offerings
                ShrineService.OfferResult wrong = ShrineService.offerAtPlinth(level, plinth, new ItemStack(Items.STONE), null);
                ok(h, wrong == ShrineService.OfferResult.WRONG_ITEM, "stone refused as the wrong item: " + wrong);
                ok(h, isKey(ShrineService.prayerRefusal(server), "firmages.shrine.no_offering"), "no prayer without the offering");
                ItemStack gift = new ItemStack(Items.HEART_OF_THE_SEA, 2);
                ShrineService.OfferResult r = ShrineService.offerAtPlinth(level, plinth, gift, null);
                ok(h, r == ShrineService.OfferResult.ACCEPTED, "offering accepted: " + r);
                ok(h, gift.getCount() == 1, "exactly one item taken, left " + gift.getCount());
                ok(h, ShrineService.offerAtPlinth(level, plinth, gift, null) == ShrineService.OfferResult.OCCUPIED, "second offering refused");
                OfferingPlinthBlockEntity pb = (OfferingPlinthBlockEntity) level.getBlockEntity(plinth);
                ok(h, pb.item().is(Items.HEART_OF_THE_SEA) && !pb.isRelic(), "offering on the plinth, not yet a relic");

                // ---- rite: the heart must be kindled
                ok(h, isKey(ShrineService.prayerRefusal(server), "firmages.shrine.rite.kindle"), "cold heart refuses: " + text(ShrineService.prayerRefusal(server)));
                level.setBlock(heart, level.getBlockState(heart).setValue(ShrineHeartBlock.LIT, true), 3);
                ok(h, ShrineService.prayerRefusal(server).isEmpty(), "shrine listens: " + text(ShrineService.prayerRefusal(server)));

                // ---- prayer completes: relic, grant (simulate path, nobody online), FULL ceremony
                sentBefore[0] = CeremonyService.sentCount();
                Component no = ShrineService.simulatePray(server);
                ok(h, no == null, "simulate_pray refused: " + (no == null ? "" : no.getString()));
                ok(h, pb.isRelic() && level.getBlockState(plinth).getValue(OfferingPlinthBlock.AWAKENED), "offering enshrined, plinth awakened");
                ok(h, ShrineSavedData.get(server).relicForTier(0).map(x -> x.plinth().equals(plinth) && x.item().equals("minecraft:heart_of_the_sea")).orElse(false),
                    "relic recorded in SavedData");
                ok(h, ShrineSavedData.get(server).grantedStages().contains("age_1"), "shrine grant recorded");
                ok(h, AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_1), "age_1 granted");
                AgeTransitionPayload p = CeremonyService.last().orElseThrow();
                ok(h, CeremonyService.sentCount() == sentBefore[0] + 1, "exactly one ceremony payload, sent " + (CeremonyService.sentCount() - sentBefore[0]));
                ok(h, p.full() && p.stage().equals("age_1") && p.tier() == 0 && p.beamColor() == 0xFFFFB347
                    && p.voiceKey().equals("firmages.shrine.voice.age_1") && p.shrine().map(g -> g.pos().equals(heart)).orElse(false)
                    && p.blessingName().equals("firmages.blessing.hearthward") && p.sting().toString().equals("firmages:shrine.sting.stone"),
                    "FULL ceremony payload: " + p);
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
                AgeTransitionPayload.STREAM_CODEC.encode(buf, p);
                ok(h, AgeTransitionPayload.STREAM_CODEC.decode(buf).equals(p), "payload round trip");
                ok(h, isKey(ShrineService.prayerRefusal(server), "firmages.shrine.ruins")
                    || ShrineService.phase(ShrineService.heart(server).orElseThrow()).equals("AWAKENING"), "awakening or next ring missing");
                // The relic cannot be broken in survival.
                BlockState ps = level.getBlockState(plinth);
                ok(h, ps.getDestroyProgress(h.makeMockPlayer(GameType.SURVIVAL), level, plinth) == 0.0F, "relic plinth unbreakable in survival");
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_1 grant still running"))
            .thenExecute(() -> {
                // ---- persistence: plinth block entity and SavedData survive a save/load round trip
                OfferingPlinthBlockEntity pb = (OfferingPlinthBlockEntity) level.getBlockEntity(plinth);
                CompoundTag tag = pb.saveWithFullMetadata(server.registryAccess());
                OfferingPlinthBlockEntity copy = new OfferingPlinthBlockEntity(plinth, level.getBlockState(plinth));
                copy.loadWithComponents(tag, server.registryAccess());
                ok(h, copy.isRelic() && copy.tier() == 0 && copy.item().is(Items.HEART_OF_THE_SEA), "plinth NBT round trip");
                ShrineSavedData sd = ShrineSavedData.reload(ShrineSavedData.get(server), server.registryAccess());
                ok(h, sd.heart().map(g -> g.pos().equals(heart)).orElse(false) && sd.relics().size() == 1
                    && sd.grantedStages().contains("age_1"), "SavedData round trip");

                // ---- after the grant: tier 1, awakened 1, Hearthward while intact; broken = blessings rest, Ages stay
                ShrineHeartBlockEntity be = (ShrineHeartBlockEntity) level.getBlockEntity(heart);
                ShrineService.validate(level, heart, be);
                BlockState hs = level.getBlockState(heart);
                ok(h, hs.getValue(ShrineHeartBlock.AWAKENED) == 1 && !hs.getValue(ShrineHeartBlock.READY), "awakened 1, ring 1 missing: " + hs);
                ok(h, ShrineSavedData.get(server).intact(), "intact with ring 0");
                ok(h, Blessings.refusesSpawnAt(5) && !Blessings.refusesSpawnAt(13), "Hearthward radius 12");
                h.setBlock(STONE, Blocks.AIR);
                ShrineService.validate(level, heart, be);
                ok(h, !ShrineSavedData.get(server).intact() && !Blessings.refusesSpawnAt(5), "broken shrine: blessing paused");
                ok(h, AgeService.state(server).snapshot().isUnlocked(AgeId.AGE_1), "a broken shrine never revokes");
                h.setBlock(STONE, Blocks.COBBLESTONE);
                ShrineService.validate(level, heart, be);
                ok(h, ShrineSavedData.get(server).intact() && Blessings.refusesSpawnAt(5), "repaired: blessing back");

                // ---- clean up: heart removal clears the record; Ages back to the start state
                h.setBlock(HEART, Blocks.AIR);
                ok(h, ShrineSavedData.get(server).heart().isEmpty(), "heart record cleared");
                ok(h, ShrineService.extract(level, plinth).map(s -> s.is(Items.HEART_OF_THE_SEA)).orElse(false), "operator extract");
                ShrineSavedData.get(server).removeGranted("age_1");
                AgeService.simulate(server, AgeId.AGE_1, false);
                AgeService.simulate(server, AgeId.AGE_0, false);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> ServerConfig.DEBUG_ALLOW_SIMULATE.set(false))
            .thenSucceed();
    }

    // ---------------------------------------------------------------- the Gathering and the living shrine

    /** Stand-in relics of tiers 0..8; tiers 0, 3 and 8 are the test datapack's offerings (late offering needs them). */
    private static final net.minecraft.world.item.Item[] RELICS = {Items.HEART_OF_THE_SEA, Items.COPPER_INGOT, Items.IRON_INGOT, Items.AMETHYST_SHARD,
        Items.GOLD_INGOT, Items.REDSTONE, Items.QUARTZ, Items.ENDER_PEARL, Items.NETHER_STAR};

    /**
     * All nine rings around {@code heart} in consecrated blocks (rotation NONE, accent = ring), a plinth per ring with
     * its enshrined stand-in relic and record. @return the plinth of each tier
     */
    private static BlockPos[] buildConsecratedShrine(ServerLevel level, BlockPos heart) {
        MinecraftServer server = level.getServer();
        ShrineData d = ShrineDataLoader.current();
        level.setBlock(heart, ShrineRegistry.SHRINE_HEART.get().defaultBlockState().setValue(ShrineHeartBlock.LIT, true), 3);
        BlockPos[] plinths = new BlockPos[ShrineData.MAX_TIER + 1];
        for (int k = 0; k <= ShrineData.MAX_TIER; k++) {
            ShrineTier t = d.tier(k).orElseThrow();
            ResourceLocation id = ResourceLocation.parse(t.multiblock().orElseThrow());
            for (ShrineMultiblocks.Cell c : ShrineMultiblocks.cells(level, heart, id, Rotation.NONE)) {
                if (c.key() == '0' || !c.counts()) continue;
                if (c.key() == t.plinthKey()) {
                    level.setBlock(c.pos(), ShrineRegistry.OFFERING_PLINTH.get().defaultBlockState(), 3);
                    plinths[k] = c.pos();
                } else if (c.role() != null) {
                    level.setBlock(c.pos(), ShrineRegistry.consecrated(c.role(), k), 3);
                }
            }
            OfferingPlinthBlockEntity pb = (OfferingPlinthBlockEntity) level.getBlockEntity(plinths[k]);
            pb.offer(new ItemStack(RELICS[k]), k);
            pb.enshrine(k);
            ShrineSavedData.get(server).putRelic(new ShrineSavedData.Relic(plinths[k], k,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(RELICS[k]).toString()));
        }
        return plinths;
    }

    /** Removes the heart (its record goes), every ring position and the plinths around {@code heart}. */
    private static void clearShrine(ServerLevel level, BlockPos heart) {
        level.setBlock(heart, Blocks.AIR.defaultBlockState(), 3);
        ShrineData d = ShrineDataLoader.current();
        for (int k = ShrineData.MAX_TIER; k >= 0; k--) {
            ResourceLocation id = ResourceLocation.parse(d.tier(k).orElseThrow().multiblock().orElseThrow());
            for (ShrineMultiblocks.Cell c : ShrineMultiblocks.cells(level, heart, id, Rotation.NONE)) {
                if (!level.getBlockState(c.pos()).isAir()) level.setBlock(c.pos(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static List<net.minecraft.world.entity.item.ItemEntity> itemsAbove(ServerLevel level, BlockPos heart) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(heart).inflate(3));
    }

    /**
     * SPEC §7.5, the Gathering: nine consecrated rings with nine enshrined (stand-in) relics. In age_8 no prayer
     * releases anything; in age_9 the shrine lends nothing before the Gathering; a lent relic and a missing relic
     * refuse the prayer with their own lines, the missing one comes back as a late offering; then the prayer releases
     * exactly nine item entities (never despawning, invulnerable) with the nine ids above the heart, every plinth waits
     * (lent, still glowing), the heart shows awakened=10, one GATHERING payload goes out, the HUD status says gathered,
     * the flag survives a SavedData round trip; a second prayer is refused; a relic laid back is enshrined again and
     * can be taken again by sneak-use; the shrine stays intact (blessings unchanged).
     */
    @GameTest(template = "empty", batch = "firmages_3_shrine_gathering", timeoutTicks = 4000)
    public static void gatheringReleasesRelics(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        BlockPos heart = h.absolutePos(new BlockPos(0, 1, 0)).above(24); // in open air: the rings reach 10 blocks out
        BlockPos[][] plinths = new BlockPos[1][];
        int[] sentBefore = new int[1];
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                agesUpTo(server, AgeId.AGE_8);
                plinths[0] = buildConsecratedShrine(level, heart);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_8 setup still running"))
            .thenExecute(() -> {
                ShrineHeartBlockEntity be = (ShrineHeartBlockEntity) level.getBlockEntity(heart);
                ShrineService.validate(level, heart, be);
                ok(h, be.validRing() == ShrineData.MAX_TIER && ShrineSavedData.get(server).intact(), "nine consecrated rings validate: ring " + be.validRing());
                ok(h, ShrineService.simulatePray(server) != null && itemsAbove(level, heart).isEmpty() && !ShrineSavedData.get(server).gathered(),
                    "age_8: no Gathering, nothing released");
                AgeService.simulate(server, AgeId.AGE_9, true);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_9 grant still running"))
            .thenExecute(() -> {
                ShrineSavedData sd = ShrineSavedData.get(server);
                ShrineHeartBlockEntity be = (ShrineHeartBlockEntity) level.getBlockEntity(heart);
                ShrineService.validate(level, heart, be);
                ok(h, level.getBlockState(heart).getValue(ShrineHeartBlock.AWAKENED) == 9 && sd.intact(), "age_9: awakened 9, intact");
                // SPEC §36: the Gathering's sink, 1 G FE into the heart
                ok(h, isKey(ShrineService.prayerRefusal(server), "firmages.shrine.gathering.energy"), "the Gathering wants power: "
                    + text(ShrineService.prayerRefusal(server)));
                net.neoforged.neoforge.energy.IEnergyStorage power = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, heart, null);
                ok(h, power.receiveEnergy(Integer.MAX_VALUE, false) == 1_000_000_000 && be.drawn() == 1_000_000_000, "the heart drew 1 G FE: " + be.drawn());
                ok(h, ShrineService.prayerRefusal(server).isEmpty(), "the Gathering listens: " + text(ShrineService.prayerRefusal(server)));
                ok(h, ShrineService.lend(level, plinths[0][3], null) == null, "nothing is lent before the Gathering");
                ok(h, !ImmersionService.status(server).gathered(), "HUD: Goal: the Gathering");

                // a relic lent out (in age_8) and never returned
                OfferingPlinthBlockEntity p3 = (OfferingPlinthBlockEntity) level.getBlockEntity(plinths[0][3]);
                ItemStack keystone = p3.lend();
                sd.putRelic(new ShrineSavedData.Relic(plinths[0][3], 3, "minecraft:amethyst_shard", true));
                ok(h, isKey(ShrineService.prayerRefusal(server), "firmages.shrine.gathering.lent"), "lent relic: " + text(ShrineService.prayerRefusal(server)));
                ok(h, ShrineService.offerAtPlinth(level, plinths[0][3], keystone, null) == ShrineService.OfferResult.RETURNED, "the lent relic comes back");

                // a relic missing (operator extract), laid back as a late offering
                ok(h, ShrineService.extract(level, plinths[0][0]).isPresent(), "extract the tier-0 relic");
                ok(h, isKey(ShrineService.prayerRefusal(server), "firmages.shrine.gathering.missing"), "missing relic: " + text(ShrineService.prayerRefusal(server)));
                ShrineService.OfferResult late = ShrineService.offerAtPlinth(level, plinths[0][0], new ItemStack(Items.HEART_OF_THE_SEA), null);
                ok(h, late == ShrineService.OfferResult.ACCEPTED_LATE, "age_9: the missing relic is a late offering: " + late);
                ok(h, ShrineService.prayerRefusal(server).isEmpty(), "complete again: " + text(ShrineService.prayerRefusal(server)));

                // the prayer
                sentBefore[0] = CeremonyService.sentCount();
                Component no = ShrineService.simulatePray(server);
                ok(h, no == null, "Gathering refused: " + (no == null ? "" : no.getString()));
                List<net.minecraft.world.entity.item.ItemEntity> items = itemsAbove(level, heart);
                List<String> ids = items.stream().map(e -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString()).sorted().toList();
                List<String> want = java.util.Arrays.stream(RELICS).map(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString()).sorted().toList();
                ok(h, ids.equals(want) && items.stream().allMatch(e -> e.getItem().getCount() == 1), "exactly the nine relics above the heart: " + ids);
                ok(h, items.stream().allMatch(e -> e.isInvulnerable() && e.getAge() < 0), "relic items never despawn and cannot be destroyed");
                for (int k = 0; k <= ShrineData.MAX_TIER; k++) {
                    OfferingPlinthBlockEntity pb = (OfferingPlinthBlockEntity) level.getBlockEntity(plinths[0][k]);
                    ok(h, pb.isLent() && pb.isEmpty() && pb.tier() == k && level.getBlockState(plinths[0][k]).getValue(OfferingPlinthBlock.AWAKENED),
                        "plinth " + k + " waits for its relic and keeps its glow");
                }
                ok(h, ShrineSavedData.get(server).gathered() && ShrineSavedData.get(server).lentRelics().size() == 9, "gathered, nine records kept as lent");
                ok(h, ShrineSavedData.reload(ShrineSavedData.get(server), server.registryAccess()).gathered(), "gathered in the SavedData round trip");
                AgeTransitionPayload p = CeremonyService.last().orElseThrow();
                ok(h, ((ShrineHeartBlockEntity) level.getBlockEntity(heart)).drawn() == 0, "the Gathering spent the 1 G FE");
                ok(h, CeremonyService.sentCount() == sentBefore[0] + 1 && p.stage().equals(CeremonyService.GATHERING) && p.full()
                    && p.voiceKey().equals("firmages.shrine.voice.gathering") && p.blessingName().equals("firmages.blessing.crowned")
                    && p.shrine().map(g -> g.pos().equals(heart)).orElse(false), "one GATHERING payload at the heart: " + p);
                ok(h, ImmersionService.status(server).gathered(), "HUD: Goal: Beyond the Firmament");
            })
            .thenIdle(5)
            .thenExecute(() -> {
                ShrineHeartBlockEntity be = (ShrineHeartBlockEntity) level.getBlockEntity(heart);
                ShrineService.validate(level, heart, be);
                ok(h, level.getBlockState(heart).getValue(ShrineHeartBlock.AWAKENED) == 10, "the heart shows awakened=10");
                ok(h, ShrineSavedData.get(server).intact() && Blessings.activeBlessings(server).size() == 9, "intact, nine blessings in force");
                int before = itemsAbove(level, heart).size();
                ok(h, isKey(ShrineService.prayerRefusal(server), "firmages.shrine.gathered") && ShrineService.simulatePray(server) != null
                    && itemsAbove(level, heart).size() == before, "a second Gathering is refused");
            })
            .thenIdle(245) // relics are not handed out while Caelum answers
            .thenExecute(() -> {
                // a relic laid back, then taken again
                ok(h, ShrineService.offerAtPlinth(level, plinths[0][8], new ItemStack(Items.NETHER_STAR), null) == ShrineService.OfferResult.RETURNED,
                    "a relic laid back after the Gathering is enshrined again");
                ItemStack again = ShrineService.lend(level, plinths[0][8], null);
                ok(h, again != null && again.is(Items.NETHER_STAR) && ((OfferingPlinthBlockEntity) level.getBlockEntity(plinths[0][8])).isLent(),
                    "and can be taken again by sneak-use");
                // clean up
                itemsAbove(level, heart).forEach(net.minecraft.world.entity.Entity::discard);
                clearShrine(level, heart);
                ShrineSavedData.get(server).setGathered(false);
                for (ShrineSavedData.Relic r : ShrineSavedData.get(server).relics().values()) ShrineSavedData.get(server).removeRelic(r.plinth());
                agesUpTo(server, AgeId.DAWN);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> ServerConfig.DEBUG_ALLOW_SIMULATE.set(false))
            .thenSucceed();
    }

    /**
     * SPEC §7.10, the living shrine: {@code firmages:shrine_sync} carries the heart, the intact flag, the finale, the
     * idle-beam switch ({@code shrine.permanentBeam}, on by default) and the nine tier colours; breaking a ring block
     * sends intact=false to the players within a second, the repair intact=true; the codec round-trips.
     */
    @GameTest(template = "shrine_area", batch = "firmages_3_shrine_living", timeoutTicks = 3000)
    public static void livingShrineSync(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        BlockPos heart = h.absolutePos(HEART);
        String name = "gt_living";
        net.minecraft.server.level.ServerPlayer[] p = new net.minecraft.server.level.ServerPlayer[1];
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                ok(h, ServerConfig.SHRINE_PERMANENT_BEAM.getDefault(), "shrine.permanentBeam defaults to true");
                agesUpTo(server, AgeId.AGE_1);
                buildRing0(h);
                p[0] = dev.firmages.core.command.DebugPlayerCommands.spawn(server, level, name, net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_1 setup still running"))
            .thenExecute(() -> {
                ShrineService.validate(level, heart, (ShrineHeartBlockEntity) level.getBlockEntity(heart));
                ok(h, ShrineSavedData.get(server).intact(), "ring 0 stands at age_1");
                dev.firmages.core.command.DebugPlayerCommands.record(name);
                h.setBlock(STONE, Blocks.AIR);
                ShrineService.validate(level, heart, (ShrineHeartBlockEntity) level.getBlockEntity(heart));
            })
            .thenIdle(25)
            .thenExecute(() -> {
                Optional<dev.firmages.core.net.ShrineSyncPayload> s = lastSync(name);
                ok(h, s.isPresent() && !s.get().intact() && s.get().heartPos() == heart.asLong() && s.get().idleBeam() && !s.get().finale()
                    && s.get().tierBeam().size() == 9 && s.get().tierBeam().get(0) == 0xFFFFB347, "broken: sync with intact=false, got " + s);
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
                dev.firmages.core.net.ShrineSyncPayload.STREAM_CODEC.encode(buf, s.orElseThrow());
                ok(h, dev.firmages.core.net.ShrineSyncPayload.STREAM_CODEC.decode(buf).equals(s.get()), "shrine_sync codec round trip");
                dev.firmages.core.command.DebugPlayerCommands.record(name);
                h.setBlock(STONE, Blocks.COBBLESTONE);
                ShrineService.validate(level, heart, (ShrineHeartBlockEntity) level.getBlockEntity(heart));
            })
            .thenIdle(25)
            .thenExecute(() -> {
                Optional<dev.firmages.core.net.ShrineSyncPayload> s = lastSync(name);
                ok(h, s.isPresent() && s.get().intact(), "repaired: sync with intact=true, got " + s);
                dev.firmages.core.command.DebugPlayerCommands.despawn(p[0]);
                h.setBlock(HEART, Blocks.AIR);
                ShrineService.extract(level, h.absolutePos(PLINTH));
                agesUpTo(server, AgeId.DAWN);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> ServerConfig.DEBUG_ALLOW_SIMULATE.set(false))
            .thenSucceed();
    }

    /**
     * SPEC §35 (Felix H5A): the Industrial Age offering (stand-in: an emerald) waits for the proof of work of the test
     * datapack's {@code tallies.json} (eight iron ingots through a Tally Chute in the Industrial Age): refused at five
     * with Caelum's line, accepted at eight; the heart's synced Jade values follow the count; the plinth's empty-hand line
     * names the proof too.
     */
    @GameTest(template = "empty", batch = "firmages_3_shrine_proof", timeoutTicks = 3000)
    public static void offeringWaitsForProof(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        BlockPos heart = h.absolutePos(new BlockPos(0, 1, 0)).above(24);
        BlockPos[][] plinths = new BlockPos[1][];
        net.minecraft.server.level.ServerPlayer[] p = new net.minecraft.server.level.ServerPlayer[1];
        String name = "gt_prover";
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                agesUpTo(server, AgeId.AGE_4);
                plinths[0] = buildConsecratedShrine(level, heart);
                for (int k = 4; k <= ShrineData.MAX_TIER; k++) ShrineService.extract(level, plinths[0][k]);
                p[0] = dev.firmages.core.command.DebugPlayerCommands.spawn(server, level, name, net.minecraft.world.phys.Vec3.atCenterOf(heart.above(2)));
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_4 setup still running"))
            .thenExecute(() -> {
                ShrineHeartBlockEntity be = (ShrineHeartBlockEntity) level.getBlockEntity(heart);
                ShrineService.validate(level, heart, be);
                ok(h, ShrineDataLoader.current().tallyFor(4).map(r -> r.count() == 8 && r.item().orElse("").equals("minecraft:iron_ingot")).orElse(false),
                    "the test datapack asks for eight iron ingots: " + ShrineDataLoader.current().tallyFor(4));
                long base = dev.firmages.core.proof.TallyService.countsOf(server, p[0].getUUID()).sum("minecraft:iron_ingot"::equals, AgeId.AGE_4);
                ok(h, base == 0, "no iron counted yet in the Industrial Age: " + base);
                dev.firmages.core.command.DebugPlayerCommands.record(name);
                ItemStack gift = new ItemStack(Items.EMERALD, 2);
                ShrineService.OfferResult r = ShrineService.offerAtPlinth(level, plinths[0][4], gift, p[0]);
                ok(h, r == ShrineService.OfferResult.NO_PROOF && gift.getCount() == 2, "refused without proof: " + r);
                ok(h, ((OfferingPlinthBlockEntity) level.getBlockEntity(plinths[0][4])).isEmpty(), "the plinth stays empty");
                dev.firmages.core.proof.TallyService.count(server, p[0].getUUID(), "minecraft:iron_ingot", 5);
                dev.firmages.core.proof.TallyService.count(server, p[0].getUUID(), "minecraft:gold_ingot", 50);
                r = ShrineService.offerAtPlinth(level, plinths[0][4], gift, p[0]);
                ok(h, r == ShrineService.OfferResult.NO_PROOF, "five of eight (and gold that does not count) is not enough: " + r);
                ShrineService.refreshHeart(level, heart, be);
                ok(h, be.proofNeed() == 8 && be.proofHave() == 5 && be.proofItem().equals("minecraft:iron_ingot"),
                    "the heart syncs 5 of 8 iron ingots: " + be.proofHave() + "/" + be.proofNeed() + " " + be.proofItem());
                ShrineService.usePlinthEmptyHand(level, plinths[0][4], p[0]);
                dev.firmages.core.proof.TallyService.count(server, p[0].getUUID(), "minecraft:iron_ingot", 3);
                r = ShrineService.offerAtPlinth(level, plinths[0][4], gift, p[0]);
                ok(h, r == ShrineService.OfferResult.ACCEPTED && gift.getCount() == 1, "accepted with eight counted: " + r);
                ShrineService.refreshHeart(level, heart, be);
                ok(h, be.proofHave() == 8, "the heart syncs 8 of 8");
                CompoundTag sync = be.getUpdateTag(server.registryAccess());
                ok(h, sync.getLong("proofNeed") == 8 && sync.getLong("proofHave") == 8 && sync.getString("proofItem").equals("minecraft:iron_ingot"),
                    "the update tag carries the proof: " + sync);
            })
            .thenIdle(3)
            .thenExecute(() -> {
                long lines = dev.firmages.core.command.DebugPlayerCommands.recordedChat(name).stream()
                    .filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("firmages.shrine.proof.missing")).count();
                FirmagesCore.LOGGER.info("GameTest proof of work: {} proof lines reached the player", lines);
                ok(h, lines >= 3, "the player read the proof line on both refusals and at the plinth: " + lines);
                dev.firmages.core.command.DebugPlayerCommands.despawn(p[0]);
                clearShrine(level, heart);
                for (ShrineSavedData.Relic rel : ShrineSavedData.get(server).relics().values()) ShrineSavedData.get(server).removeRelic(rel.plinth());
                agesUpTo(server, AgeId.DAWN);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> ServerConfig.DEBUG_ALLOW_SIMULATE.set(false))
            .thenSucceed();
    }

    /**
     * SPEC §36: ring 6 has the rite {@code energy_consume} 10 M FE. The heart takes FE through its energy capability
     * only for the current work and only up to the need, gives none back, the rite holds once it is full, the prayer
     * spends it, and in the next Age (ring 7 draws nothing) the heart takes nothing.
     */
    @GameTest(template = "empty", batch = "firmages_3_shrine_power", timeoutTicks = 3000)
    public static void heartDrawsPower(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        BlockPos heart = h.absolutePos(new BlockPos(0, 1, 0)).above(24);
        BlockPos[][] plinths = new BlockPos[1][];
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                agesUpTo(server, AgeId.AGE_6);
                plinths[0] = buildConsecratedShrine(level, heart);
                for (int k = 6; k <= ShrineData.MAX_TIER; k++) ShrineService.extract(level, plinths[0][k]);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_6 setup still running"))
            .thenExecute(() -> {
                ShrineHeartBlockEntity be = (ShrineHeartBlockEntity) level.getBlockEntity(heart);
                ShrineService.validate(level, heart, be);
                ok(h, ShrineService.offerAtPlinth(level, plinths[0][6], new ItemStack(Items.DIAMOND), null) == ShrineService.OfferResult.ACCEPTED,
                    "the Information Age offering (stand-in diamond) lies on its plinth");
                ok(h, isKey(ShrineService.prayerRefusal(server), "firmages.shrine.rite.heart_power"), "the heart wants power: " + text(ShrineService.prayerRefusal(server)));
                net.neoforged.neoforge.energy.IEnergyStorage e = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, heart, null);
                net.neoforged.neoforge.energy.IEnergyStorage side = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, heart,
                    net.minecraft.core.Direction.UP);
                ok(h, e != null && side != null && e.canReceive() && !e.canExtract(), "the heart takes FE from every side and gives none");
                ok(h, e.receiveEnergy(4_000_000, true) == 4_000_000 && be.drawn() == 0, "a simulated insert changes nothing");
                ok(h, e.receiveEnergy(4_000_000, false) == 4_000_000 && side.receiveEnergy(1_000_000, false) == 1_000_000 && be.drawn() == 5_000_000,
                    "5 M FE drawn: " + be.drawn());
                ok(h, e.extractEnergy(1_000_000, false) == 0 && be.drawn() == 5_000_000, "nothing comes back out");
                ok(h, isKey(ShrineService.prayerRefusal(server), "firmages.shrine.rite.heart_power"), "half is not enough");
                ok(h, e.receiveEnergy(Integer.MAX_VALUE, false) == 5_000_000 && be.drawn() == 10_000_000 && !e.canReceive()
                    && e.receiveEnergy(1000, false) == 0, "the heart takes exactly the rest: " + be.drawn());
                ShrineService.refreshHeart(level, heart, be);
                ok(h, be.drawNeed() == 10_000_000 && be.getUpdateTag(server.registryAccess()).getLong("drawn") == 10_000_000, "Jade sees 10 M of 10 M FE");
                ok(h, ShrineService.prayerRefusal(server).isEmpty(), "the rite holds (no player online: the chorus is met): " + text(ShrineService.prayerRefusal(server)));
                ok(h, ShrineService.simulatePray(server) == null, "the prayer is heard");
                ok(h, be.drawn() == 0 && !e.canReceive(), "the prayer spent the FE");
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_7 grant still running"))
            .thenExecute(() -> {
                ShrineHeartBlockEntity be = (ShrineHeartBlockEntity) level.getBlockEntity(heart);
                ok(h, AgeService.state(server).snapshot().unlocked().contains(AgeId.AGE_7), "the prayer granted the Space Age");
                ShrineService.refreshHeart(level, heart, be);
                net.neoforged.neoforge.energy.IEnergyStorage e = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, heart, null);
                ok(h, e.receiveEnergy(1000, false) == 0 && be.drawNeed() == 0, "ring 7 draws nothing: the heart takes no FE");
                clearShrine(level, heart);
                for (ShrineSavedData.Relic rel : ShrineSavedData.get(server).relics().values()) ShrineSavedData.get(server).removeRelic(rel.plinth());
                agesUpTo(server, AgeId.DAWN);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> ServerConfig.DEBUG_ALLOW_SIMULATE.set(false))
            .thenSucceed();
    }

    private static Optional<dev.firmages.core.net.ShrineSyncPayload> lastSync(String name) {
        return dev.firmages.core.command.DebugPlayerCommands.recorded(name).stream().filter(dev.firmages.core.net.ShrineSyncPayload.class::isInstance)
            .map(dev.firmages.core.net.ShrineSyncPayload.class::cast).reduce((a, b) -> b);
    }
}
