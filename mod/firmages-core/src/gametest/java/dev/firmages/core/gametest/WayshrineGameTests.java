package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.compat.modonomicon.ShrineMultiblocks;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.net.ShrinePreviewPayload;
import dev.firmages.core.shrine.Blessings;
import dev.firmages.core.shrine.ShrineState;
import dev.firmages.core.wayshrine.WayshrineCoreBlock;
import dev.firmages.core.wayshrine.WayshrineCoreBlockEntity;
import dev.firmages.core.wayshrine.WayshrineKind;
import dev.firmages.core.wayshrine.WayshrinePhase;
import dev.firmages.core.wayshrine.WayshrinePlacement;
import dev.firmages.core.wayshrine.WayshrineRegistry;
import dev.firmages.core.wayshrine.WayshrineRules;
import dev.firmages.core.wayshrine.WayshrineSavedData;
import dev.firmages.core.wayshrine.WayshrineService;
import dev.firmages.core.wayshrine.WayshrineStoneBlock;
import dev.firmages.core.wayshrine.WayshrineStoneItem;
import dev.firmages.core.wayshrine.WayshrineTutorial;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Level G tests of the Wayshrines (SPEC-himmel §28.10, §29.8) on the stand-in type {@code stand_in} of
 * {@code src/gametest/resources} (vanilla blocks, written by {@code dev/gen_wayshrines.py}): a 5 by 3 by 5 ruin that misses
 * its light, two posts and a floor brick. The test datapack {@code firmages_test} sets vanilla offerings (age_1: a gold
 * ingot). Each test runs in its own batch: the Age changes are global.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class WayshrineGameTests {
    private static final BlockPos CORE = new BlockPos(5, 1, 5);
    private static final String TYPE = "stand_in";

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(WayshrineGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean condition, String message) {
        if (!condition) FirmagesCore.LOGGER.error("Wayshrine GameTest assertion failed: {}", message);
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

    /** Sets AgeState to exactly {@code dawn..last} through the simulate path. */
    private static void agesUpTo(MinecraftServer server, AgeId last) {
        for (int i = AgeId.all().size() - 1; i > last.ordinal(); i--) AgeService.simulate(server, AgeId.all().get(i), false);
        for (int i = 0; i <= last.ordinal(); i++) AgeService.simulate(server, AgeId.all().get(i), true);
    }

    /** The restored blocks of the stand-in (no core), turned like the ruin: the fellowship's repair. */
    private static void restore(ServerLevel level, BlockPos core, Rotation rot) {
        StructureTemplate t = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath("firmages", "wayshrine/stand_in_restored")).orElseThrow();
        BlockPos origin = core.subtract(WayshrinePlacement.coreOffset(t).rotate(rot));
        t.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(rot), level.getRandom(), 2);
    }

    private static void clearArea(ServerLevel level, BlockPos core) {
        for (BlockPos p : BlockPos.betweenClosed(core.offset(-3, 0, -3), core.offset(3, 3, 3))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
    }

    private static List<ItemEntity> stones(ServerLevel level, BlockPos core) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(core).inflate(4), e -> e.getItem().is(WayshrineRegistry.STONE_ITEM.get()));
    }

    private static ResourceLocation standInForm() {
        return ResourceLocation.fromNamespaceAndPath("firmages", "wayshrine_stand_in");
    }

    /**
     * §28.10 restoreAndLight: the ruin refuses and sends one preview payload with the ruin's rotation; the repair gives
     * {@code restored}; below {@code min_age} it refuses; a wrong offering is refused, the right one takes one item and a
     * sneak-punch returns it; an unmet rite blocks the prayer with its hint; the prayer lights it; exactly one Wayshrine
     * Stone drops, invulnerable and never despawning, and a second lighting drops none; BE and SavedData NBT round trip.
     */
    @GameTest(template = "shrine_area", batch = "firmages_4_wayshrine_light", timeoutTicks = 4000)
    public static void restoreAndLight(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        BlockPos core = h.absolutePos(CORE);
        Rotation rot = Rotation.CLOCKWISE_90;
        String name = "gt_wayfarer";
        ServerPlayer[] p = new ServerPlayer[1];
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                agesUpTo(server, AgeId.AGE_0);
                ok(h, WayshrinePlacement.placeRuin(level, TYPE, core, rot, level.getRandom(), null).isPresent(), "the stand-in ruin is placed");
                p[0] = DebugPlayerCommands.spawn(server, level, name, Vec3.atCenterOf(core.east(2).above()));
                p[0].setGameMode(GameType.SURVIVAL);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_0 setup still running"))
            .thenExecute(() -> {
                WayshrineCoreBlockEntity be = (WayshrineCoreBlockEntity) level.getBlockEntity(core);
                ok(h, be != null && TYPE.equals(be.type()), "the template's core names its type");
                BlockState st = level.getBlockState(core);
                ok(h, st.getValue(WayshrineCoreBlock.FACING) == Direction.EAST && WayshrineCoreBlock.rotationOf(st) == rot, "the core turned with the ruin");
                GlobalPos gp = GlobalPos.of(level.dimension(), core);
                ok(h, WayshrineSavedData.get(server).record(gp).isPresent(), "the core registered itself");

                // ---- the ruin refuses and shows its ghost
                ShrineMultiblocks.RingCheck c = WayshrineService.validate(level, core, be);
                ok(h, !c.valid() && be.missing() == 4, "the ruin never validates; four blocks missing, was " + be.missing() + " " + c.missing());
                ok(h, level.getBlockState(core).getValue(WayshrineCoreBlock.PHASE) == WayshrinePhase.RUIN, "phase ruin");
                DebugPlayerCommands.record(name);
                WayshrineService.inspect(level, core, p[0]);
                List<CustomPacketPayload> sent = DebugPlayerCommands.recorded(name).stream().filter(ShrinePreviewPayload.class::isInstance).toList();
                ok(h, sent.size() == 1, "one preview payload, got " + sent.size());
                if (sent.size() == 1) {
                    ShrinePreviewPayload pv = (ShrinePreviewPayload) sent.get(0);
                    ok(h, pv.multiblock().equals(standInForm()) && pv.heart().equals(core) && pv.rotation() == rot.ordinal() && !pv.clear(),
                        "the preview anchors the stand-in form at the core in the ruin's rotation: " + pv);
                }
                ok(h, WayshrineService.offer(level, core, new ItemStack(Items.GOLD_INGOT), null) == WayshrineService.OfferResult.NOT_RESTORED,
                    "a ruin takes no offering");

                // ---- the repair
                restore(level, core, rot);
                c = WayshrineService.validate(level, core, be);
                ok(h, c.valid() && c.rotation() == rot, "the restored form stands in the ruin's rotation: " + c.rotation() + " missing " + c.missing());
                ok(h, level.getBlockState(core).getValue(WayshrineCoreBlock.PHASE) == WayshrinePhase.RESTORED
                    && level.getBlockState(core).getValue(WayshrineCoreBlock.INTACT), "phase restored, intact");
                ok(h, be.missing() == 0 && be.beamColor() != 0 && (be.beamColor() >>> 24) == 0x40, "the faint ruin beam stays: " + Integer.toHexString(be.beamColor()));

                // ---- below min_age
                ok(h, WayshrineService.offer(level, core, new ItemStack(Items.GOLD_INGOT), null) == WayshrineService.OfferResult.TOO_EARLY, "age_0: too early");
                ok(h, isKey(WayshrineService.refusal(level, core, be, true), "firmages.wayshrine.waits"), "it waits for a later Age");
                AgeService.simulate(server, AgeId.AGE_1, true);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_1 grant still running"))
            .thenExecute(() -> {
                WayshrineCoreBlockEntity be = (WayshrineCoreBlockEntity) level.getBlockEntity(core);
                // ---- the offering
                ItemStack diamond = new ItemStack(Items.DIAMOND);
                ok(h, WayshrineService.offer(level, core, diamond, p[0]) == WayshrineService.OfferResult.WRONG_ITEM && diamond.getCount() == 1,
                    "a wrong offering is refused");
                ItemStack gold = new ItemStack(Items.GOLD_INGOT, 3);
                ok(h, WayshrineService.offer(level, core, gold, p[0]) == WayshrineService.OfferResult.ACCEPTED, "the gold ingot is taken");
                ok(h, gold.getCount() == 2 && be.offering().is(Items.GOLD_INGOT) && be.offering().getCount() == 1, "exactly one item");
                ok(h, WayshrineService.offer(level, core, gold, p[0]) == WayshrineService.OfferResult.OCCUPIED, "one offering at a time");
                ok(h, WayshrineService.takeBack(level, core, p[0]) && be.offering().isEmpty() && p[0].getInventory().countItem(Items.GOLD_INGOT) == 1,
                    "sneak and punch: the offering comes back");
                ok(h, WayshrineService.offer(level, core, gold, p[0]) == WayshrineService.OfferResult.ACCEPTED, "laid again");

                // ---- the rite blocks the prayer
                Optional<Component> no = WayshrineService.refusal(level, core, be, true);
                ok(h, isKey(no, "firmages.wayshrine.rite.kindle"), "the cold light blocks the prayer: " + text(no));
                p[0].setShiftKeyDown(true);
                WayshrineService.pray(level, core, p[0]);
                ok(h, be.prayerProgress() == 0, "a refused prayer does not start");
                List<BlockPos> light = ShrineMultiblocks.positions(level, core, standInForm(), rot, 'L');
                ok(h, light.size() == 1 && level.getBlockState(light.get(0)).is(Blocks.CANDLE), "the light is the candle: " + light);
                for (BlockPos l : light) level.setBlock(l, level.getBlockState(l).setValue(BlockStateProperties.LIT, true), 3);
                ok(h, WayshrineService.refusal(level, core, be, true).isEmpty(), "kindled: the Wayshrine listens, " + text(WayshrineService.refusal(level, core, be, true)));
            })
            .thenExecuteFor(160, () -> {
                if (level.getBlockState(core).getValue(WayshrineCoreBlock.PHASE) != WayshrinePhase.LIT && level.getGameTime() % 4 == 0) {
                    p[0].setShiftKeyDown(true);
                    WayshrineService.pray(level, core, p[0]);
                }
            })
            .thenExecute(() -> {
                WayshrineCoreBlockEntity be = (WayshrineCoreBlockEntity) level.getBlockEntity(core);
                ok(h, level.getBlockState(core).getValue(WayshrineCoreBlock.PHASE) == WayshrinePhase.LIT && level.getBlockState(core).getValue(WayshrineCoreBlock.INTACT),
                    "the prayer lit it");
                ok(h, be.offering().isEmpty(), "the offering is consumed");
                List<ItemEntity> s = stones(level, core);
                ok(h, s.size() == 1, "exactly one Wayshrine Stone, got " + s.size());
                if (s.size() == 1) {
                    ItemEntity e = s.get(0);
                    ok(h, e.isInvulnerable() && e.getAge() < 0, "the stone never despawns and cannot be destroyed");
                    ok(h, e.getY() >= core.getY() + 0.99 && e.getDeltaMovement().horizontalDistanceSqr() == 0, "the stone rests above the core");
                    ok(h, TYPE.equals(WayshrineStoneItem.typeOf(e.getItem())), "the stone names its Wayshrine");
                    var st = e.getItem().get(DataComponents.BLOCK_STATE);
                    ok(h, st != null && st.get(WayshrineStoneBlock.KIND) == WayshrineKind.FOREST_RING, "an unknown type gives a Forest Ring stone");
                }
                GlobalPos gp = GlobalPos.of(level.dimension(), core);
                WayshrineSavedData.Record r = WayshrineSavedData.get(server).record(gp).orElse(null);
                ok(h, r != null && r.lit() && r.intact() && r.stoneGiven() && r.litAge().equals("age_1"), "the record: " + r);

                // ---- persistence
                WayshrineCoreBlockEntity copy = new WayshrineCoreBlockEntity(core, level.getBlockState(core));
                copy.loadWithComponents(be.saveWithFullMetadata(server.registryAccess()), server.registryAccess());
                ok(h, TYPE.equals(copy.type()) && copy.offering().isEmpty(), "BE NBT round trip");
                ok(h, WayshrineSavedData.reload(WayshrineSavedData.get(server), server.registryAccess()).record(gp).equals(Optional.ofNullable(r)),
                    "SavedData round trip");

                // ---- a second lighting drops no stone
                ok(h, WayshrineService.reset(level, core, false) == null, "reset");
                ok(h, level.getBlockState(core).getValue(WayshrineCoreBlock.PHASE) == WayshrinePhase.RESTORED, "after the reset the form still stands");
                ok(h, WayshrineService.simulateLight(level, core) == null, "lit again");
                ok(h, stones(level, core).size() == 1, "no second stone");
                ok(h, WayshrineService.reset(level, core, true) == null && WayshrineService.simulateLight(level, core) == null && stones(level, core).size() == 2,
                    "reset with stone: the stone can drop again");

                // ---- clean up
                stones(level, core).forEach(ItemEntity::discard);
                p[0].setShiftKeyDown(false);
                DebugPlayerCommands.despawn(p[0]);
                clearArea(level, core);
                ok(h, WayshrineSavedData.get(server).record(gp).isEmpty(), "a removed core drops its record");
                agesUpTo(server, AgeId.DAWN);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> ServerConfig.DEBUG_ALLOW_SIMULATE.set(false))
            .thenSucceed();
    }

    /**
     * §29.8 blessingsAtWayshrine: three tiers awakened and the home shrine far away; a lit stand-in Wayshrine gives the
     * blessings at 20 blocks and none at 30; a fourth tier joins without a visit; a broken pattern removes them within
     * one validation and the repair restores them; a broken home ring silences the Wayshrine; Hearthward inside, not
     * outside.
     */
    @GameTest(template = "shrine_area", batch = "firmages_4_wayshrine_bless", timeoutTicks = 3000)
    public static void blessingsAtWayshrine(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        BlockPos core = h.absolutePos(CORE);
        BlockPos farHeart = core.east(500);
        ShrineState.Snapshot prev = ShrineState.get();
        ServerPlayer[] p = new ServerPlayer[1];
        BlockPos[] post = new BlockPos[1];
        h.startSequence()
            .thenExecute(() -> {
                ServerConfig.DEBUG_ALLOW_SIMULATE.set(true);
                agesUpTo(server, AgeId.AGE_3);
                WayshrinePlacement.placeRuin(level, TYPE, core, Rotation.NONE, level.getRandom(), null);
                restore(level, core, Rotation.NONE);
                p[0] = DebugPlayerCommands.spawn(server, level, "gt_blessed_far", Vec3.atCenterOf(core.east(20)));
                p[0].setGameMode(GameType.SURVIVAL);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the age_3 setup still running"))
            .thenExecute(() -> {
                ShrineState.set(level.dimension(), farHeart, true, 3);
                ok(h, WayshrineService.simulateLight(level, core) == null, "the stand-in lights in age_3");
                p[0].setPos(core.getX() + 20.5, core.getY(), core.getZ() + 0.5);
                Blessings.apply(p[0]);
                ok(h, Math.abs(p[0].getMaxHealth() - 21.0F) < 1e-4 && Blessings.xpBonus(p[0]) == 0, "20 blocks out: Hearthward, Skyreading, Iron Will ("
                    + p[0].getMaxHealth() + ")");
                ok(h, Blessings.refusesSpawnAt(level, core.getX() + 10.5, core.getY(), core.getZ() + 0.5), "Hearthward inside");
                ok(h, !Blessings.refusesSpawnAt(level, core.getX() + 30.5, core.getY(), core.getZ() + 0.5), "no Hearthward outside");
                p[0].setPos(core.getX() + 30.5, core.getY(), core.getZ() + 0.5);
                Blessings.apply(p[0]);
                ok(h, p[0].getMaxHealth() == 20.0F, "30 blocks out: nothing");
                p[0].setPos(core.getX() + 20.5, core.getY(), core.getZ() + 0.5);
                ShrineState.set(level.dimension(), farHeart, true, 4); // a new Age awakens tier 3 (Attunement) at the home shrine
                Blessings.apply(p[0]);
                ok(h, Blessings.xpBonus(p[0]) > 0, "the new tier arrives without a visit");
                // ---- a broken pattern
                post[0] = core.offset(2, 1, 2);
                ok(h, level.getBlockState(post[0]).is(Blocks.OAK_LOG), "a post of the stand-in at " + post[0]);
                level.setBlock(post[0], Blocks.AIR.defaultBlockState(), 3);
                WayshrineService.onBlockChanged(level, post[0]);
            })
            .thenIdle(2)
            .thenExecute(() -> {
                ok(h, !level.getBlockState(core).getValue(WayshrineCoreBlock.INTACT)
                    && level.getBlockState(core).getValue(WayshrineCoreBlock.PHASE) == WayshrinePhase.LIT, "broken: lit but quiet");
                Blessings.apply(p[0]);
                ok(h, p[0].getMaxHealth() == 20.0F && Blessings.xpBonus(p[0]) == 0, "a broken Wayshrine carries nothing");
                ok(h, ((WayshrineCoreBlockEntity) level.getBlockEntity(core)).beamColor() == 0, "and shows no beam");
                level.setBlock(post[0], Blocks.OAK_LOG.defaultBlockState(), 3);
                WayshrineService.onBlockChanged(level, post[0]);
            })
            .thenIdle(2)
            .thenExecute(() -> {
                ok(h, level.getBlockState(core).getValue(WayshrineCoreBlock.INTACT), "repaired");
                Blessings.apply(p[0]);
                ok(h, Math.abs(p[0].getMaxHealth() - 21.0F) < 1e-4 && Blessings.xpBonus(p[0]) > 0, "the repair restores them without a new offering");
                ShrineState.set(level.dimension(), farHeart, false, 4);
                Blessings.apply(p[0]);
                ok(h, p[0].getMaxHealth() == 20.0F && !Blessings.refusesSpawnAt(level, core.getX() + 10.5, core.getY(), core.getZ() + 0.5),
                    "a broken home ring silences the Wayshrine");
            })
            .thenExecute(() -> {
                if (prev == null) ShrineState.clear();
                else ShrineState.set(prev.dimension(), prev.heart(), prev.intact(), prev.awakened());
                Blessings.apply(p[0], List.of());
                DebugPlayerCommands.despawn(p[0]);
                clearArea(level, core);
                agesUpTo(server, AgeId.DAWN);
            })
            .thenWaitUntil(() -> h.assertTrue(reloadIdle(server), "reload after the clean-up still running"))
            .thenExecute(() -> ServerConfig.DEBUG_ALLOW_SIMULATE.set(false))
            .thenSucceed();
    }

    /**
     * §28.10 tutorialPlacement: the first start of this GameTest world placed the tutorial Forest Ring 300 to 800 blocks
     * from the shared spawn, on land, once; a second start places none; with the flag cleared it is placed again.
     */
    @GameTest(template = "empty", batch = "firmages_4_wayshrine_tutorial", timeoutTicks = 1200)
    public static void tutorialPlacement(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel ow = server.overworld();
        WayshrineSavedData sd = WayshrineSavedData.get(server);
        ok(h, sd.tutorialPlaced() && sd.tutorialPos().isPresent(), "the server start placed the tutorial Wayshrine");
        BlockPos pos = sd.tutorialPos().orElseThrow();
        BlockPos spawn = ow.getSharedSpawnPos();
        double d = Math.hypot(pos.getX() - spawn.getX(), pos.getZ() - spawn.getZ());
        ok(h, d >= WayshrineRules.TUTORIAL_MIN - 1 && d <= WayshrineRules.TUTORIAL_MAX + 1, "300 to 800 blocks from the spawn: " + (int) d);
        ok(h, ow.getBlockState(pos).is(WayshrineRegistry.CORE.get()), "the core stands at " + pos);
        ok(h, ow.getBlockEntity(pos) instanceof WayshrineCoreBlockEntity be && WayshrineTutorial.TYPE.equals(be.type()), "a Forest Ring");
        ok(h, ow.getFluidState(pos.above()).isEmpty(), "on land");
        long known = sd.records().stream().filter(r -> r.type().equals(WayshrineTutorial.TYPE)).count();
        ok(h, WayshrineTutorial.ensure(server).isEmpty(), "a second start places none");
        ok(h, sd.records().stream().filter(r -> r.type().equals(WayshrineTutorial.TYPE)).count() == known, "no second record");
        // with the flag cleared (an existing world updating to this feature) it is placed once more
        sd.setTutorial(null);
        Optional<BlockPos> again = WayshrineTutorial.ensure(server);
        ok(h, again.isPresent() && sd.tutorialPlaced(), "placed on a world without one");
        again.ifPresent(a -> {
            double d2 = Math.hypot(a.getX() - spawn.getX(), a.getZ() - spawn.getZ());
            ok(h, d2 >= WayshrineRules.TUTORIAL_MIN - 1 && d2 <= WayshrineRules.TUTORIAL_MAX + 1, "again 300 to 800 blocks out: " + (int) d2);
            if (!a.equals(pos)) ow.setBlock(a, Blocks.AIR.defaultBlockState(), 3); // its record goes with the core
        });
        sd.setTutorial(pos);
        h.succeed();
    }

    /**
     * The ghost alignment on a rotated ruin (§28.10 M, checked here without a client): for every structure rotation the
     * core's facing gives that rotation, the ghost is sent in it, and the restored form validates in exactly it, so the
     * Modonomicon pattern and the ruin template share one frame.
     */
    @GameTest(template = "shrine_area", batch = "firmages_4_wayshrine_tutorial")
    public static void formTurnsWithTheRuin(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos core = h.absolutePos(CORE);
        for (Rotation rot : Rotation.values()) {
            WayshrinePlacement.placeRuin(level, TYPE, core, rot, level.getRandom(), null);
            WayshrineCoreBlockEntity be = (WayshrineCoreBlockEntity) level.getBlockEntity(core);
            ok(h, WayshrineCoreBlock.rotationOf(level.getBlockState(core)) == rot, rot + ": the core's facing gives the rotation");
            ShrineMultiblocks.RingCheck ruin = WayshrineService.validate(level, core, be);
            ok(h, !ruin.valid() && ruin.bestRotation() == rot, rot + ": the ruin matches best in its own rotation, not " + ruin.bestRotation());
            restore(level, core, rot);
            ShrineMultiblocks.RingCheck c = WayshrineService.validate(level, core, be);
            ok(h, c.valid() && c.rotation() == rot, rot + ": the restored form validates in " + c.rotation() + ", missing " + c.missing());
            clearArea(level, core);
        }
        h.succeed();
    }

    /**
     * The S6B hook the Drifting Isles (M13) call: the Wind Shrine ruin lands with its core where asked, registers itself
     * and is a ruin of the Wind Shrine type.
     */
    @GameTest(template = "shrine_area", batch = "firmages_4_wayshrine_tutorial")
    public static void isleShrineHook(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos core = h.absolutePos(CORE);
        Optional<BlockPos> placed = WayshrinePlacement.placeIsleShrine(level, core, Rotation.COUNTERCLOCKWISE_90, level.getRandom(), null);
        ok(h, placed.equals(Optional.of(core)), "the hook places the Wind Shrine at the core position");
        ok(h, level.getBlockEntity(core) instanceof WayshrineCoreBlockEntity be && WayshrinePlacement.ISLE_TYPE.equals(be.type()), "a Wind Shrine core");
        ok(h, level.getBlockState(core).getValue(WayshrineCoreBlock.FACING) == Direction.WEST, "turned as asked");
        ok(h, WayshrineSavedData.get(level.getServer()).record(GlobalPos.of(level.dimension(), core)).map(r -> r.phase() == WayshrinePhase.RUIN).orElse(false),
            "registered as a ruin");
        level.setBlock(core, Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }
}
