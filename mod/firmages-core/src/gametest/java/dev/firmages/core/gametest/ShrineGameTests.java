package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.ceremony.CeremonyService;
import dev.firmages.core.config.ServerConfig;
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
 * energy rite's capability read. The test datapack {@code firmages_test:offerings} replaces the KubeJS signature items
 * (absent here): Hearthstone by a heart of the sea, Arcane Keystone by an amethyst shard, Quantum Core by a nether star.
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
     * all gone outside the radius or with a broken shrine, back everywhere with {@code shrine.blessings.everywhere};
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
            ServerConfig.SHRINE_BLESSINGS_EVERYWHERE.set(true);
            Blessings.apply(p);
            ok(h, Math.abs(p.getMaxHealth() - 23.0F) < 1e-4 && Blessings.xpBonus(p) > 0, "shrine.blessings.everywhere: blessed far away too");
            ServerConfig.SHRINE_BLESSINGS_EVERYWHERE.set(false);

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
            Blessings.apply(p, List.of(glow), true, false);
            net.minecraft.world.effect.MobEffectInstance nv = p.getEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
            ok(h, nv != null && nv.isAmbient() && nv.getDuration() > 200, "status effect near the shrine: " + nv);
            p.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
            Blessings.apply(p, List.of(glow), false, true);
            ok(h, !p.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION), "no status effect away from the shrine, even with everywhere");
            ok(h, p.getMaxHealth() == 20.0F, "the other modifiers are gone with their blessings");
        } finally {
            if (prev == null) dev.firmages.core.shrine.ShrineState.clear();
            else dev.firmages.core.shrine.ShrineState.set(prev.dimension(), prev.heart(), prev.intact(), prev.awakened());
            Blessings.apply(p, List.of(), false, false);
            dev.firmages.core.command.DebugPlayerCommands.despawn(p);
            ServerConfig.SHRINE_BLESSINGS_EVERYWHERE.set(false);
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
}
