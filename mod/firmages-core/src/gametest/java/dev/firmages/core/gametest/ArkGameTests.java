package dev.firmages.core.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.ark.ArkData;
import dev.firmages.core.ark.ArkDataLoader;
import dev.firmages.core.ark.ArkGrowth;
import dev.firmages.core.ark.ArkProgress;
import dev.firmages.core.ark.ArkRegistry;
import dev.firmages.core.ark.ArkService;
import dev.firmages.core.ark.ArkState;
import dev.firmages.core.ark.ArkWork;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.net.ArkSyncPayload;
import dev.firmages.core.origin.OriginArena;
import dev.firmages.core.origin.OriginSavedData;
import dev.firmages.core.origin.OriginService;
import dev.firmages.core.shrine.ConsecratedBlock;
import dev.firmages.core.shrine.Consecration;
import dev.firmages.core.shrine.ShrineRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleBiFunction;

/**
 * Level G tests of the Origin Archipelago (SPEC §31.10). The test run has no TFC, Create or Mekanism, so the islands
 * under test get vanilla rules ({@link ArkDataLoader#replaceForTests}) and a kinetic stand-in ({@link ArkWork#kineticSpeed}:
 * a lodestone "turns" at 32 RPM); the stage templates are the shipped ones. Each test works on its own island and
 * sits in its own batch, puts the finale flag, the data and the island back, and force-loads the island's chunks
 * (the growth waits while a chunk is unloaded, and nobody stands in The Origin here).
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class ArkGameTests {
    private static final String STAGES = "\"stages\": [\"firmages:ark/age_%1$d/stage_1\", \"firmages:ark/age_%1$d/stage_2\", \"firmages:ark/age_%1$d/stage_3\"]";

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(ArkGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean cond, String what) {
        if (!cond) h.fail(what);
    }

    private static ServerLevel origin(GameTestHelper h) {
        ServerLevel level = OriginService.level(h.getLevel().getServer());
        if (level == null) h.fail("firmages:origin is not loaded");
        return level;
    }

    /** Vanilla rules for island {@code i}: two kinds of sapling, a cow, a turning lodestone, and two iron ingots as its crown. */
    private static ArkData rules(int... islands) {
        Map<String, JsonElement> files = new HashMap<>();
        for (int i : islands) {
            files.put("test:island_" + i, JsonParser.parseString("{\"island\": " + i + ","
                + " \"flora\": {\"items\": [\"minecraft:oak_sapling\", \"minecraft:birch_sapling\", \"minecraft:spruce_sapling\"], \"distinct\": 2},"
                + " \"fauna\": {\"entities\": [\"minecraft:cow\"]},"
                + " \"work\": {\"type\": \"kinetic\", \"block\": \"minecraft:lodestone\", \"rpm\": 16},"
                + " \"crown\": [{\"item\": \"minecraft:iron_ingot\", \"count\": 2}], " + String.format(STAGES, i) + "}"));
        }
        ArkData d = ArkData.parse(files, c -> true);
        if (!d.errors().isEmpty()) throw new IllegalStateException(d.errors().toString());
        return d;
    }

    private static void forceIsland(ServerLevel o, int i, boolean on) {
        BlockPos top = OriginArena.islandTop(i);
        int reach = ArkGrowth.HALF + 1;
        for (int cx = (top.getX() - reach) >> 4; cx <= (top.getX() + reach) >> 4; cx++) {
            for (int cz = (top.getZ() - reach) >> 4; cz <= (top.getZ() + reach) >> 4; cz++) o.setChunkForced(cx, cz, on);
        }
    }

    private static BlockPos plinth(MinecraftServer server, int i) {
        return BlockPos.of(ArkService.progress(server, i).plinth());
    }

    /** The first air cell on island {@code i}'s top layer at distance 5..7 from the centre, for a player's block. */
    private static BlockPos freeSpot(ServerLevel o, int i) {
        BlockPos top = OriginArena.islandTop(i);
        for (int x = 5; x <= 7; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos p = top.offset(x, 1, z);
                if (o.getBlockState(p).isAir() && !o.getBlockState(p.below()).isAir()) return p;
            }
        }
        throw new IllegalStateException("no free spot on island " + i);
    }

    /** Puts the island back: its grown blocks go, the samples are forgotten. */
    private static void cleanUp(MinecraftServer server, int i, ArkData data, boolean won) {
        ArkService.reset(server, i);
        ArkDataLoader.replaceForTests(data);
        OriginSavedData.get(server).setWon(won);
    }

    /**
     * Before the finale every plinth stands but sleeps: nothing is taken by hand or by pipe, no group is granted, no
     * stage grows.
     */
    @GameTest(template = "empty", batch = "firmages_6_ark_inert", timeoutTicks = 200)
    public static void inertBeforeFinale(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = origin(h);
        OriginSavedData sd = OriginSavedData.get(server);
        boolean won = sd.won();
        ArkData old = ArkDataLoader.replaceForTests(rules(0));
        sd.setWon(false);
        ServerPlayer p = DebugPlayerCommands.spawn(server, h.getLevel(), "gt_ark_inert", Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
        try {
            ok(h, sd.arenaVersion() == OriginArena.VERSION && OriginArena.VERSION == 3, "arena v3 at server start, was v" + sd.arenaVersion());
            for (int i = 0; i < ArkData.ISLANDS; i++) {
                ok(h, ArkService.progress(server, i).plinth() != Long.MIN_VALUE && o.getBlockState(plinth(server, i)).is(ArkRegistry.ARK_PLINTH.get()),
                    "island " + i + " has its plinth");
            }
            BlockPos at = plinth(server, 0);
            ok(h, o.getBlockState(at).getDestroySpeed(o, at) < 0, "the plinth is unbreakable");
            ok(h, ArkService.offer(o, at, new ItemStack(Items.OAK_SAPLING, 3), false, null) == 0, "no sample by pipe before the finale");
            IItemHandler pipe = o.getCapability(Capabilities.ItemHandler.BLOCK, at, null);
            ok(h, pipe != null && pipe.insertItem(0, new ItemStack(Items.BIRCH_SAPLING), false).getCount() == 1, "the item handler refuses it too");
            ItemStack hand = new ItemStack(Items.OAK_SAPLING, 3);
            p.setItemInHand(InteractionHand.MAIN_HAND, hand);
            ok(h, ArkService.useWithItem(o, at, p, hand) == ArkService.Use.DONE && hand.getCount() == 3, "a right-click is answered and takes nothing");
            ok(h, !ArkService.grant(server, 0, "flora"), "no grant before the finale");
            ArkService.update(server);
            ArkProgress pr = ArkService.progress(server, 0);
            ok(h, pr.groups().isEmpty() && pr.stage() == 0 && pr.flora().isEmpty() && !ArkService.growing(0), "the island stays as it was: " + pr.groups());
            h.succeed();
        } finally {
            DebugPlayerCommands.despawn(p);
            cleanUp(server, 0, old, won);
        }
    }

    /**
     * After the finale distinct saplings count once each, a pipe gives only the rest of the crown, a cow led to the
     * plinth becomes persistent and stays, the turning stand-in is heard as the work; three groups grow three stages,
     * the island completes, its socket bit goes out in {@code firmages:ark_sync}, and the progress survives a save.
     */
    @GameTest(template = "empty", batch = "firmages_6_ark_samples", timeoutTicks = 1800)
    public static void samplesCountOnce(GameTestHelper h) {
        final int i = 1;
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = origin(h);
        OriginSavedData sd = OriginSavedData.get(server);
        boolean won = sd.won();
        ArkData old = ArkDataLoader.replaceForTests(rules(i));
        ToDoubleBiFunction<net.minecraft.world.level.Level, BlockPos> kinetic = ArkWork.kineticSpeed;
        ArkService.reset(server, i);
        sd.setWon(true);
        forceIsland(o, i, true);
        String name = "gt_ark_keeper";
        ServerPlayer p = DebugPlayerCommands.spawn(server, h.getLevel(), name, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
        DebugPlayerCommands.record(name);
        BlockPos at = plinth(server, i);
        Cow[] cow = new Cow[1];
        BlockPos[] stand = new BlockPos[1];
        Runnable finish = () -> {
            DebugPlayerCommands.despawn(p);
            if (cow[0] != null) cow[0].discard();
            if (stand[0] != null) o.setBlock(stand[0], Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            ArkWork.kineticSpeed = kinetic;
            forceIsland(o, i, false);
            cleanUp(server, i, old, won);
        };
        h.startSequence()
            // Nobody stands in The Origin: the forced chunks must reach entity status before the cow can be found.
            .thenWaitUntil(() -> {
                if (!o.areEntitiesLoaded(ChunkPos.asLong(at))) h.fail("island " + i + " entities not loaded yet");
            })
            .thenExecute(() -> {
        try {
            ArkProgress pr = ArkService.progress(server, i);
            // flora: distinct ids, one each
            ItemStack oak = new ItemStack(Items.OAK_SAPLING, 5);
            int expect = p.getAbilities().instabuild ? 5 : 4;
            ok(h, ArkService.useWithItem(o, at, p, oak) == ArkService.Use.DONE && oak.getCount() == expect, "one oak sapling taken, " + oak.getCount() + " left");
            // A block item the island no longer wants passes on to normal block use (it may be placed), taking nothing.
            ok(h, ArkService.useWithItem(o, at, p, oak) == ArkService.Use.PASS && oak.getCount() == expect, "a second oak sapling counts nothing");
            ItemStack bread = new ItemStack(Items.BREAD);
            ok(h, ArkService.useWithItem(o, at, p, bread) == ArkService.Use.DONE && bread.getCount() == 1, "an item it does not ask for is answered, not taken");
            ok(h, pr.flora().equals(Map.of("minecraft:oak_sapling", 1)) && !pr.groups().contains("flora"), "one kind given: " + pr.flora());
            ok(h, ArkService.offer(o, at, new ItemStack(Items.BIRCH_SAPLING, 4), false, null) == 1 && pr.groups().contains("flora"), "the second kind by pipe completes the flora");
            ok(h, ArkService.offer(o, at, new ItemStack(Items.SPRUCE_SAPLING), false, null) == 0, "a third kind is no longer taken");
            // crown: only the rest, by the item handler
            IItemHandler pipe = o.getCapability(Capabilities.ItemHandler.BLOCK, at, null);
            ok(h, pipe != null && pipe.insertItem(0, new ItemStack(Items.IRON_INGOT, 5), false).getCount() == 3 && pr.groups().contains(ArkData.CROWN),
                "the crown takes two of five iron ingots");
            ok(h, pipe.insertItem(0, new ItemStack(Items.DIRT), true).getCount() == 1 && pipe.extractItem(0, 1, false).isEmpty(), "nothing else goes in, nothing comes out");
            // fauna: a led cow
            Cow c = EntityType.COW.create(o);
            c.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);
            o.addFreshEntity(c);
            cow[0] = c;
            c.setLeashedTo(p, true);
            ok(h, ArkService.fauna(o, i, at, p) && c.isPersistenceRequired() && c.getLeashHolder() == null && "minecraft:cow".equals(pr.faunaEntity()),
                "the led cow becomes the island's animal and stays");
            // work: the turning stand-in on the island
            ArkWork.kineticSpeed = (level, pos) -> level.getBlockState(pos).is(Blocks.LODESTONE) ? 32 : 0;
            stand[0] = freeSpot(o, i);
            ok(h, !ArkService.checkWork(o, i), "no work before the stand-in turns");
            o.setBlock(stand[0], Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_ALL);
            ok(h, ArkService.checkWork(o, i) && pr.groups().contains("work"), "the turning stand-in is heard as the work");
        } catch (RuntimeException e) {
            finish.run();
            throw e;
        }
            })
            .thenWaitUntil(() -> {
                ArkProgress pr = ArkService.progress(server, i);
                if (!pr.complete()) h.fail("island " + i + " grows: stage " + pr.stage() + ", groups " + pr.groups());
            })
            .thenExecute(() -> {
                try {
                    ArkProgress pr = ArkService.progress(server, i);
                    ok(h, pr.stage() == 3 && !pr.grown().isEmpty(), "three stages grown, " + pr.grown().size() + " blocks");
                    ok(h, o.getBlockState(stand[0]).is(Blocks.LODESTONE) && o.getBlockState(at).is(ArkRegistry.ARK_PLINTH.get()), "the stand-in and the plinth stay");
                    ok(h, (sd.ark().completeMask() & (1 << i)) != 0, "the island's socket bit is set");
                    boolean synced = false;
                    for (CustomPacketPayload pl : DebugPlayerCommands.recorded(name)) {
                        if (pl instanceof ArkSyncPayload s && (s.complete() & (1 << i)) != 0 && s.stages()[i] == 3 && s.colors().length == ArkData.ISLANDS) synced = true;
                    }
                    ok(h, !synced, "the test player stands outside The Origin and gets no ark_sync");
                    ArkSyncPayload now = ArkService.payload(server, false);
                    ok(h, (now.complete() & (1 << i)) != 0 && now.stages()[i] == 3 && !now.celebrate(), "ark_sync carries the island: " + now.complete());
                    CompoundTag saved = sd.ark().save();
                    ArkState copy = new ArkState(() -> {});
                    copy.load(saved);
                    ArkProgress q = copy.island(i);
                    ok(h, q.groups().equals(pr.groups()) && q.stage() == 3 && q.complete() && q.grown().equals(pr.grown()) && q.plinth() == pr.plinth()
                        && q.flora().equals(pr.flora()) && q.crown().equals(pr.crown()), "the progress survives a save");
                    int grown = pr.grown().size();
                    int removed = ArkService.reset(server, i);
                    ok(h, removed > grown * 9 / 10 && ArkService.progress(server, i).grown().isEmpty(), "reset takes the grown blocks away: " + removed + " of " + grown);
                } finally {
                    finish.run();
                }
            })
            .thenSucceed();
    }

    /**
     * A stage writes only into air and into the island's own earlier blocks: a player's blocks in the template's cells
     * stay, and a reset takes away only what grew.
     */
    @GameTest(template = "empty", batch = "firmages_6_ark_overwrite", timeoutTicks = 800)
    public static void stagesNeverOverwritePlayerBlocks(GameTestHelper h) {
        final int i = 2;
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = origin(h);
        OriginSavedData sd = OriginSavedData.get(server);
        boolean won = sd.won();
        ArkData old = ArkDataLoader.replaceForTests(rules(i));
        ArkService.reset(server, i);
        sd.setWon(true);
        forceIsland(o, i, true);
        List<ArkGrowth.Step> steps = ArkGrowth.blocks(o, ResourceLocation.fromNamespaceAndPath("firmages", "ark/age_2/stage_1"), OriginArena.islandTop(i)).orElse(List.of());
        List<BlockPos> player = new ArrayList<>();
        for (ArkGrowth.Step s : steps) {
            if (player.size() < 3 && o.getBlockState(s.pos()).isAir() && !s.state().is(Blocks.GLASS)) player.add(s.pos());
        }
        Runnable finish = () -> {
            for (BlockPos b : player) o.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            forceIsland(o, i, false);
            cleanUp(server, i, old, won);
        };
        try {
            ok(h, steps.size() > 20 && player.size() == 3, "stage 1 of island 2 loaded with " + steps.size() + " blocks");
            for (BlockPos b : player) o.setBlock(b, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
            ok(h, ArkService.grant(server, i, "flora"), "flora granted");
        } catch (RuntimeException e) {
            finish.run();
            throw e;
        }
        h.startSequence()
            .thenWaitUntil(() -> {
                if (ArkService.progress(server, i).stage() < 1 || ArkService.growing(i)) h.fail("stage 1 grows");
            })
            .thenExecute(() -> {
                try {
                    ArkProgress pr = ArkService.progress(server, i);
                    for (BlockPos b : player) {
                        ok(h, o.getBlockState(b).is(Blocks.GLASS) && !pr.grown().containsKey(b.asLong()), "the player's glass at " + b.toShortString() + " stays");
                    }
                    ok(h, pr.grown().size() == steps.size() - player.size(), "every other cell grew: " + pr.grown().size() + " of " + (steps.size() - player.size()));
                    // A grown cell that a player took over is no longer the island's: a later stage keeps it.
                    BlockPos taken = BlockPos.of(pr.grown().keySet().iterator().next());
                    BlockState grownState = o.getBlockState(taken);
                    ok(h, ArkGrowth.free(o, pr, taken), "a grown block that still stands may be replaced by a later stage");
                    o.setBlock(taken, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
                    ok(h, !ArkGrowth.free(o, pr, taken), "a player's block in a grown cell is kept");
                    player.add(taken);
                    int grown = pr.grown().size();
                    int removed = ArkService.reset(server, i);
                    // Random ticks may have turned a grown block into another meanwhile (grass to dirt); such a block stays too.
                    ok(h, removed <= grown - 1 && removed >= (grown - 1) * 9 / 10, "reset removes the grown blocks but not the player's: " + removed + " of " + grown);
                    for (BlockPos b : player) ok(h, o.getBlockState(b).is(Blocks.GLASS), "glass stays after the reset at " + b.toShortString());
                    ok(h, !grownState.isAir(), "the cell held a grown block before");
                } finally {
                    finish.run();
                }
            })
            .thenSucceed();
    }

    /**
     * An island with every group and its crown is complete (by operator grants here): its bit is set, the status
     * says it is whole, and the other islands' bits stay clear.
     */
    @GameTest(template = "empty", batch = "firmages_6_ark_complete", timeoutTicks = 1600)
    public static void completeLightsSocket(GameTestHelper h) {
        final int i = 4;
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = origin(h);
        OriginSavedData sd = OriginSavedData.get(server);
        boolean won = sd.won();
        ArkData old = ArkDataLoader.replaceForTests(rules(i));
        ArkService.reset(server, i);
        sd.setWon(true);
        forceIsland(o, i, true);
        int before = sd.ark().completeMask();
        Runnable finish = () -> {
            forceIsland(o, i, false);
            cleanUp(server, i, old, won);
        };
        try {
            for (String g : ArkData.GROUPS) ok(h, ArkService.grant(server, i, g), g + " granted");
            ok(h, !ArkService.grant(server, i, "flora"), "a group is granted once");
            ok(h, !ArkService.progress(server, i).complete(), "not complete without the crown");
        } catch (RuntimeException e) {
            finish.run();
            throw e;
        }
        h.startSequence()
            .thenWaitUntil(() -> {
                if (ArkService.progress(server, i).stage() < 3) h.fail("island " + i + " grows to stage 3");
            })
            .thenExecute(() -> {
                try {
                    ArkService.update(server);
                    ok(h, !ArkService.progress(server, i).complete() && (sd.ark().completeMask() & (1 << i)) == 0, "three stages but no crown: no beam");
                    ok(h, ArkService.grant(server, i, ArkData.CROWN), "crown granted");
                    ok(h, ArkService.progress(server, i).complete() && sd.ark().completeMask() == (before | (1 << i)), "the crown completes the island: mask "
                        + Integer.toBinaryString(sd.ark().completeMask()));
                    ok(h, ArkService.status(server, i).getString().contains("whole"), "the plinth says the island is whole: " + ArkService.status(server, i).getString());
                    ArkSyncPayload pl = ArkService.payload(server, false);
                    ok(h, (pl.complete() & (1 << i)) != 0 && pl.colors().length == ArkData.ISLANDS && pl.stages()[i] == 3, "ark_sync lights socket " + i);
                } finally {
                    finish.run();
                }
            })
            .thenSucceed();
    }

    /**
     * The arena migration from version 2 to 3 changes no player block: on a version-2 island built off the arena,
     * a player's block on the new ring, a hole dug in the old top, a block under it and a block on the first plinth
     * spot all stay, while the ring fills and the plinth takes the next spot; a second run sets nothing. On the real
     * arena a world reset to version 2 migrates without refilling a hole in the disc, and the nine plinths stand.
     */
    @GameTest(template = "empty", batch = "firmages_6_ark_arena", timeoutTicks = 200)
    public static void arenaV3KeepsBuilds(GameTestHelper h) {
        final int i = 5;
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel o = origin(h);
        BlockPos c = new BlockPos(1000, OriginArena.FLOOR_Y, 1000);
        int r0 = OriginArena.islandRadius(i);
        int r = OriginArena.arkRadius(i);
        for (int cx = (c.getX() - r - 1) >> 4; cx <= (c.getX() + r + 1) >> 4; cx++) {
            for (int cz = (c.getZ() - r - 1) >> 4; cz <= (c.getZ() + r + 1) >> 4; cz++) o.setChunkForced(cx, cz, true);
        }
        BlockPos disc = new BlockPos(12, OriginArena.FLOOR_Y, 5);
        BlockState discState = o.getBlockState(disc);
        OriginSavedData sd = OriginSavedData.get(server);
        try {
            OriginArena.buildIslandV2(o, c, i);
            BlockPos ring = c.offset(r0 + 2, 0, 0);
            BlockPos hole = c.offset(1, 0, 1);
            BlockPos under = c.offset(r0 + 1, -2, 0);
            BlockPos spot = OriginArena.plinthSpots(c).get(0);
            ok(h, o.getBlockState(ring).isAir() && o.getBlockState(under).isAir() && !o.getBlockState(hole).isAir(), "a version-2 island");
            o.setBlock(ring, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
            o.setBlock(under, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            o.setBlock(hole, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            o.setBlock(spot, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
            int filled = OriginArena.widenIsland(o, c, i);
            BlockPos plinth = OriginArena.placePlinth(o, c);
            ok(h, filled > 100, "the ring and the deeper cone filled " + filled + " blocks");
            ok(h, o.getBlockState(ring).is(Blocks.GLASS) && o.getBlockState(under).is(Blocks.GLOWSTONE) && o.getBlockState(hole).isAir()
                && o.getBlockState(spot).is(Blocks.CHEST), "every player block and the hole stay");
            BlockState edge = o.getBlockState(c.offset(-r, 0, 0));
            BlockState mid = o.getBlockState(c.offset(0, 0, -(r0 + 2)));
            ok(h, edge.is(ShrineRegistry.CONSECRATED.get(Consecration.Role.TRIM).get()) && edge.getValue(ConsecratedBlock.ACCENT) == i, "a trim rim at radius " + r + ": " + edge);
            ok(h, mid.is(ShrineRegistry.CONSECRATED.get(Consecration.Role.STONE).get()) && mid.getValue(ConsecratedBlock.ACCENT) == i, "consecrated stone on the new ring: " + mid);
            ok(h, plinth != null && plinth.equals(OriginArena.plinthSpots(c).get(1)) && o.getBlockState(plinth).is(ArkRegistry.ARK_PLINTH.get()),
                "the plinth takes the next free spot: " + plinth);
            ok(h, OriginArena.widenIsland(o, c, i) == 0 && plinth.equals(OriginArena.placePlinth(o, c)), "a second run sets nothing and finds the plinth");

            // The real arena: back to version 2 with a hole in the disc; the migration keeps the hole.
            o.setBlock(disc, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            sd.setArenaVersion(2);
            OriginService.ensureBuilt(server);
            ok(h, sd.arenaVersion() == 3, "migrated to v3");
            ok(h, o.getBlockState(disc).isAir(), "the hole in the disc stays (no full rebuild)");
            for (int k = 0; k < ArkData.ISLANDS; k++) {
                ok(h, o.getBlockState(BlockPos.of(ArkService.progress(server, k).plinth())).is(ArkRegistry.ARK_PLINTH.get()), "plinth " + k + " stands");
            }
            h.succeed();
        } finally {
            o.setBlock(disc, discState, Block.UPDATE_CLIENTS);
            int y0 = c.getY() - r - 4;
            for (BlockPos b : BlockPos.betweenClosed(c.offset(-r - 1, y0 - c.getY(), -r - 1), c.offset(r + 1, 2, r + 1))) {
                if (!o.getBlockState(b).isAir()) o.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            for (int cx = (c.getX() - r - 1) >> 4; cx <= (c.getX() + r + 1) >> 4; cx++) {
                for (int cz = (c.getZ() - r - 1) >> 4; cz <= (c.getZ() + r + 1) >> 4; cz++) o.setChunkForced(cx, cz, false);
            }
        }
    }
}
