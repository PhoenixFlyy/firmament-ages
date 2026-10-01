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
 * the Hearthward sanctuary and the ceremony payload. The test datapack {@code firmages_test:offerings} replaces the
 * Hearthstone (a KubeJS item, absent here) by a heart of the sea.
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
