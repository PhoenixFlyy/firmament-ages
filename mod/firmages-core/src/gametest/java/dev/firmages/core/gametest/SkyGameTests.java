package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.ReloadScheduler;
import dev.firmages.core.compat.ie.StarfallVeins;
import dev.firmages.core.net.StarfallStreakPayload;
import dev.firmages.core.shrine.ShrineSavedData;
import dev.firmages.core.sky.ClimateSources;
import dev.firmages.core.sky.CraterCarver;
import dev.firmages.core.sky.DeferredSites;
import dev.firmages.core.sky.SkyMetal;
import dev.firmages.core.sky.SkyRegistry;
import dev.firmages.core.sky.SkySavedData;
import dev.firmages.core.sky.StarfallCoreBlock;
import dev.firmages.core.sky.StarfallRules;
import dev.firmages.core.sky.StarfallService;
import dev.firmages.core.sky.StarfallSite;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Level G tests of Starfall (SPEC §25.11) on vanilla stand-in terrain: a forced impact carves within the per-tick
 * budget; a site in unloaded chunks waits and is carved by the tick step after a forceload; the base check moves a
 * site away from a chest, a plank road, an inhabited chunk, an isle, water and (after a move) the shrine, and burns it
 * after three moves; the hot core doubles drops by index lookups only; the tool tiers; the saved data round trip; the
 * IE excavator vein from the automation Age with the dirty mark and the reconcile; the payload codec. Night rolls and
 * old craters are off in the GameTest run ({@link StarfallService#setNaturalEvents}).
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class SkyGameTests {
    private static final BlockPos CENTRE = new BlockPos(12, 10, 12);
    private static final int GROUND = 9;

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        StarfallService.setNaturalEvents(false); // no night roll and no old crater may land in another test's area
        event.register(SkyGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean c, String msg) {
        if (!c) h.fail(msg);
    }

    /** Stone from y 1 to {@link #GROUND} over the whole 25 by 25 template, air above. */
    private static void terrain(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 1; y <= GROUND; y++) level.setBlock(h.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static int count(GameTestHelper h, Block b) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 0, 0)), h.absolutePos(new BlockPos(24, 25, 24)))) {
            if (h.getLevel().getBlockState(p).is(b)) n++;
        }
        return n;
    }

    private static boolean reloadIdle(MinecraftServer s) {
        return AgeService.status(s).phase() == ReloadScheduler.Phase.IDLE;
    }

    private static void agesUpTo(MinecraftServer server, AgeId last) {
        for (int i = AgeId.all().size() - 1; i > last.ordinal(); i--) AgeService.simulate(server, AgeId.all().get(i), false);
        for (int i = 0; i <= last.ordinal(); i++) AgeService.simulate(server, AgeId.all().get(i), true);
    }

    private static AgeId currentAge(MinecraftServer server) {
        return StarfallService.currentAge(server);
    }

    /** A forced impact carves the crater: never more than 64 changes in one tick, a hot core, meteorites in range. */
    @GameTest(template = "sky_area", batch = "firmages_11_sky_carve", timeoutTicks = 600)
    public static void forcedImpactCarves(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        SkySavedData d = SkySavedData.get(level.getServer());
        BlockPos c = h.absolutePos(CENTRE);
        StarfallSite[] s = new StarfallSite[1];
        long[] start = new long[1];
        h.startSequence()
            .thenExecute(() -> {
                terrain(h);
                CraterCarver.resetMax();
                start[0] = level.getGameTime();
                s[0] = StarfallService.plan(level, d, c.getX(), c.getZ(), "meteoric_iron", "small", level.getGameTime(), true);
            })
            .thenWaitUntil(() -> ok(h, s[0].state == StarfallSite.State.CARVED, "crater not carved yet: " + s[0]))
            .thenExecute(() -> {
                long ticks = level.getGameTime() - start[0];
                ok(h, CraterCarver.maxTickChanges() <= 64, "more than 64 changes in one tick: " + CraterCarver.maxTickChanges());
                ok(h, ticks >= 3 && ticks <= 60, "carve took " + ticks + " ticks");
                ok(h, s[0].impacted && s[0].core != null, "impact and core recorded");
                BlockState core = level.getBlockState(s[0].core);
                ok(h, core.is(SkyRegistry.STARFALL_CORE.get()) && core.getValue(StarfallCoreBlock.HOT)
                    && core.getValue(StarfallCoreBlock.METAL) == SkyMetal.METEORIC_IRON, "hot meteoric core at " + s[0].core + ": " + core);
                ok(h, s[0].core.getY() == c.getY() - 1 - 3, "core at the bowl floor (ground " + GROUND + " - depth 3): " + s[0].core.getY());
                int met = count(h, SkyRegistry.METEORITE.get());
                ok(h, met >= 10 && met <= 14, "meteorites " + met + " outside 10..14");
                ok(h, count(h, SkyRegistry.SKY_ROCK.get()) > 60, "sky rock lining and rim");
                ok(h, level.getBlockState(c.below()).isAir(), "the bowl is open above the core");
                ok(h, level.getBlockState(c.offset(9, -1, 0)).is(Blocks.STONE), "nothing beyond radius + 3 changed");
                ok(h, !DeferredSites.registered(Level.OVERWORLD, StarfallService.KIND, s[0].id), "a carved site leaves the chunk index");
            })
            .thenSucceed();
    }

    /** A site in unloaded chunks stays PENDING; a forceload makes the tick step carve it (never the load event itself). */
    @GameTest(template = "sky_area", batch = "firmages_11_sky_lazy", timeoutTicks = 1600)
    public static void lazyCarveOnLoad(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        SkySavedData d = SkySavedData.get(level.getServer());
        BlockPos c = h.absolutePos(CENTRE).offset(30000, 0, 0);
        StarfallSite[] s = new StarfallSite[1];
        List<ChunkPos> forced = new ArrayList<>();
        h.startSequence()
            .thenExecute(() -> s[0] = StarfallService.oldCrater(level, d, c.getX(), c.getZ(), true))
            .thenIdle(20)
            .thenExecute(() -> {
                ok(h, s[0].state == StarfallSite.State.PENDING, "unloaded site must stay PENDING: " + s[0]);
                ok(h, DeferredSites.registered(Level.OVERWORLD, StarfallService.KIND, s[0].id), "site in the chunk index");
                ok(h, level.getChunkSource().getChunkNow(c.getX() >> 4, c.getZ() >> 4) == null, "the far chunk is not loaded");
                for (long l : s[0].chunks(CraterCarver.plan(s[0]).reach())) {
                    ChunkPos cp = new ChunkPos(l);
                    forced.add(cp);
                    level.setChunkForced(cp.x, cp.z, true);
                }
                ok(h, s[0].state == StarfallSite.State.PENDING, "forcing a chunk does not carve at once");
            })
            .thenWaitUntil(() -> ok(h, s[0].state == StarfallSite.State.CARVED, "not carved after the forceload: " + s[0]))
            .thenExecute(() -> {
                ok(h, level.getBlockState(s[0].core).is(SkyRegistry.STARFALL_CORE.get()) && !level.getBlockState(s[0].core).getValue(StarfallCoreBlock.HOT),
                    "an old crater has a cold core");
                for (ChunkPos cp : forced) level.setChunkForced(cp.x, cp.z, false);
            })
            .thenSucceed();
    }

    /** A chest, a plank road, an inhabited chunk, an isle, water and the shrine move the site; three moves burn it. */
    @GameTest(template = "sky_area", batch = "firmages_11_sky_exclusions", timeoutTicks = 400)
    public static void exclusionsMoveTheSite(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        SkySavedData d = SkySavedData.get(server);
        BlockPos c = h.absolutePos(CENTRE);
        h.startSequence()
            .thenExecute(() -> {
                terrain(h);
                StarfallSite s = new StarfallSite(-1001, c.getX(), c.getZ(), "meteoric_iron", "small", StarfallSite.Origin.LIVE, 7L);
                ok(h, StarfallService.baseCheck(level, s) == null, "a clean place passes: " + StarfallService.baseCheck(level, s));
                BlockPos chest = c.offset(3, 0, 0);
                level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
                String why = StarfallService.baseCheck(level, s);
                ok(h, why != null && why.startsWith("block entity"), "a player's chest moves the site: " + why);
                level.setBlock(chest, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                for (int i = -2; i <= 2; i++) level.setBlock(c.offset(i, -1, 6), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
                why = StarfallService.baseCheck(level, s);
                ok(h, why != null && why.startsWith("player block"), "a plank road moves the site: " + why);
                for (int i = -2; i <= 2; i++) level.setBlock(c.offset(i, -1, 6), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                LevelChunk chunk = level.getChunkAt(c);
                long inhabited = chunk.getInhabitedTime();
                chunk.setInhabitedTime(20000);
                ok(h, "inhabited".equals(StarfallService.baseCheck(level, s)), "an inhabited chunk moves the site");
                chunk.setInhabitedTime(inhabited);
                StarfallService.IsleProbe isle = (l, x, z) -> Math.abs(x - c.getX()) < 40 && Math.abs(z - c.getZ()) < 40;
                StarfallService.registerIsleProbe(isle);
                ok(h, "isle".equals(StarfallService.baseCheck(level, s)), "a Drifting Isle overhead moves the site");
                StarfallService.unregisterIsleProbe(isle);
                for (int x = -5; x <= 5; x++) for (int z = -5; z <= 0; z++) level.setBlock(c.offset(x, 0, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                ok(h, "fluid".equals(StarfallService.baseCheck(level, s)), "a pond moves the site");
                for (int x = -5; x <= 5; x++) for (int z = -5; z <= 0; z++) level.setBlock(c.offset(x, 0, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                ShrineSavedData shrine = ShrineSavedData.get(server);
                Optional<GlobalPos> heart = shrine.heart();
                shrine.setHeart(GlobalPos.of(Level.OVERWORLD, c.offset(100, 0, 0)));
                s.moves = 1;
                ok(h, "shrine".equals(StarfallService.baseCheck(level, s)), "a moved site near the shrine heart moves again");
                shrine.setHeart(heart.orElse(null));
                CraterCarver.forget(s.id);

                // the real path: a move, then BURNED after the third move
                boolean guaranteed = d.guaranteedTonight();
                StarfallSite live = d.newSite(c.getX(), c.getZ(), "meteoric_iron", "small", StarfallSite.Origin.LIVE, 9L);
                live.impacted = true;
                level.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
                StarfallService.ready(level, live.id);
                ok(h, live.moves == 1 && live.state == StarfallSite.State.PENDING && (live.x != c.getX() || live.z != c.getZ()),
                    "the site moved once: " + live);
                live.x = c.getX();
                live.z = c.getZ();
                live.moves = StarfallRules.MAX_MOVES;
                d.setGuaranteedTonight(false);
                StarfallService.ready(level, live.id);
                ok(h, live.state == StarfallSite.State.BURNED, "three moves burn the site: " + live);
                ok(h, d.guaranteedTonight(), "a burned site makes the next clear night fall for sure");
                ok(h, !DeferredSites.registered(Level.OVERWORLD, StarfallService.KIND, live.id), "a burned site leaves the index");
                d.setGuaranteedTonight(guaranteed);
                level.setBlock(chest, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            })
            .thenSucceed();
    }

    private static int chunks(ServerLevel level, BlockPos pos, Item item) {
        BlockState state = level.getBlockState(pos);
        int n = 0;
        for (ItemStack s : Block.getDrops(state, level, pos, level.getBlockEntity(pos), null, new ItemStack(Items.NETHERITE_PICKAXE))) {
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** Meteorites within 12 blocks of a hot core drop double; after {@code starfall cool} single; one index read per block. */
    @GameTest(template = "sky_area", batch = "firmages_11_sky_hot", timeoutTicks = 200)
    public static void hotCoreDoubles(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        SkySavedData d = SkySavedData.get(level.getServer());
        h.startSequence()
            .thenExecute(() -> {
                BlockPos core = h.absolutePos(new BlockPos(12, 5, 12));
                StarfallSite s = d.newSite(core.getX(), core.getZ(), "meteoric_iron", "small", StarfallSite.Origin.LIVE, 11L);
                s.core = core;
                s.impacted = true;
                s.coolsAt = level.getGameTime() + 48000;
                s.state = StarfallSite.State.CARVED;
                d.indexCore(s);
                level.setBlock(core, SkyRegistry.STARFALL_CORE.get().defaultBlockState().setValue(StarfallCoreBlock.HOT, true), Block.UPDATE_ALL);
                BlockPos a = h.absolutePos(new BlockPos(14, 5, 12));
                BlockPos b = h.absolutePos(new BlockPos(12, 5, 20));
                BlockPos far = h.absolutePos(new BlockPos(0, 5, 0));
                for (BlockPos p : List.of(a, b, far)) level.setBlock(p, SkyRegistry.METEORITE.get().defaultBlockState(), Block.UPDATE_ALL);
                Item chunk = SkyRegistry.METEORIC_IRON_CHUNK.get();
                long reads = SkySavedData.INDEX_READS.get();
                ok(h, chunks(level, a, chunk) == 2 && chunks(level, b, chunk) == 2, "hot crater: two chunks per meteorite");
                ok(h, chunks(level, far, chunk) == 1, "beyond 12 blocks of the core: one chunk");
                ok(h, chunks(level, core, chunk) == 6, "a hot core gives six");
                ok(h, SkySavedData.INDEX_READS.get() - reads == 4, "one index read per block, no world scan: " + (SkySavedData.INDEX_READS.get() - reads));
                ok(h, StarfallService.setHot(level, s.id, false), "starfall cool");
                ok(h, chunks(level, a, chunk) == 1 && chunks(level, b, chunk) == 1, "cold crater: one chunk per meteorite");
                ok(h, chunks(level, core, chunk) == 3, "a cold core gives three");
                ok(h, !level.getBlockState(core).getValue(StarfallCoreBlock.HOT), "the core went cold");
                s.state = StarfallSite.State.BURNED;
                d.setDirty();
            })
            .thenSucceed();
    }

    /** Vanilla tool tiers stand in for TFC's: wrought iron = diamond, steel = netherite (SPEC §25.4). */
    @GameTest(template = "empty", batch = "firmages_11_sky_misc")
    public static void toolTiers(GameTestHelper h) {
        BlockState rock = SkyRegistry.SKY_ROCK.get().defaultBlockState();
        BlockState met = SkyRegistry.METEORITE.get().defaultBlockState();
        BlockState ss = SkyRegistry.STARSTEEL_METEORITE.get().defaultBlockState();
        BlockState ssCore = SkyRegistry.STARFALL_CORE.get().defaultBlockState().setValue(StarfallCoreBlock.METAL, SkyMetal.STARSTEEL);
        ok(h, rock.is(BlockTags.MINEABLE_WITH_PICKAXE) && new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(rock), "sky rock: any pickaxe");
        ok(h, met.is(BlockTags.NEEDS_DIAMOND_TOOL) && !met.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL), "meteorite: needs_diamond_tool only");
        ok(h, ss.is(BlockTags.NEEDS_DIAMOND_TOOL) && ss.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL), "starsteel meteorite: incorrect_for_diamond_tool");
        ok(h, !new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(met), "bronze (iron tier) fails on meteorite");
        ok(h, new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(met), "wrought iron (diamond tier) mines meteorite");
        ok(h, !new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(ss), "wrought iron fails on starsteel");
        ok(h, new ItemStack(Items.NETHERITE_PICKAXE).isCorrectToolForDrops(ss), "steel (netherite tier) mines starsteel");
        ok(h, !new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(StarfallCoreBlock.meteoriteOf(ssCore)), "a starsteel core needs steel");
        ok(h, SkyRegistry.STARFALL_CORE.get().defaultBlockState().is(BlockTags.NEEDS_DIAMOND_TOOL), "core: needs_diamond_tool");
        for (var b : SkyRegistry.blocks()) ok(h, !b.get().defaultBlockState().is(BlockTags.create(ResourceLocation.parse("c:ores"))), b.getId() + " in c:ores");
        h.succeed();
    }

    /** {@code firmages_sky} survives save and load; the chunk index is rebuilt; the test climate source answers. */
    @GameTest(template = "empty", batch = "firmages_11_sky_misc")
    public static void skySavedDataRoundTrip(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        SkySavedData d = new SkySavedData();
        StarfallSite a = d.newSite(100, -200, "starsteel", "medium", StarfallSite.Origin.LIVE, 42L);
        a.impactTime = 1000;
        a.coolsAt = 49000;
        a.centreY = 70;
        a.core = new BlockPos(100, 66, -200);
        a.state = StarfallSite.State.CARVING;
        a.cursor = 321;
        a.moves = 2;
        a.veinAdded = true;
        a.impacted = true;
        d.indexCore(a);
        StarfallSite b = d.newSite(-5, 7, "meteoric_iron", "old", StarfallSite.Origin.OLD, 43L);
        b.forced = true;
        d.setNightsSinceFall(3);
        d.setLastMetal("starsteel");
        d.setGuaranteedTonight(true);
        d.setLastRollDay(17);
        d.setAuroraOverride("on");
        SkySavedData r = SkySavedData.roundTrip(d, level.registryAccess());
        StarfallSite a2 = r.site(a.id).orElseThrow();
        StarfallSite b2 = r.site(b.id).orElseThrow();
        ok(h, a2.x == 100 && a2.z == -200 && "starsteel".equals(a2.metal) && "medium".equals(a2.size) && a2.origin == StarfallSite.Origin.LIVE
            && a2.seed == 42L && a2.impactTime == 1000 && a2.coolsAt == 49000 && a2.centreY == 70 && a.core.equals(a2.core)
            && a2.state == StarfallSite.State.CARVING && a2.cursor == 321 && a2.moves == 2 && a2.veinAdded && a2.impacted && !a2.forced, "site a: " + a2);
        ok(h, b2.origin == StarfallSite.Origin.OLD && b2.forced && b2.core == null && b2.centreY == Integer.MIN_VALUE, "site b: " + b2);
        ok(h, r.nightsSinceFall() == 3 && "starsteel".equals(r.lastMetal()) && r.guaranteedTonight() && r.lastRollDay() == 17
            && "on".equals(r.auroraOverride()) && r.auroraSeed() == d.auroraSeed(), "counters and aurora state");
        ok(h, r.siteNear(new BlockPos(105, 66, -195)).map(s -> s.id).orElse(-1L) == a.id, "chunk index rebuilt on load");
        ok(h, r.siteNear(new BlockPos(130, 66, -200)).isEmpty(), "beyond 12 blocks of the core: no site");
        StarfallSite c = r.newSite(0, 0, "meteoric_iron", "small", StarfallSite.Origin.LIVE, 1L);
        ok(h, c.id > b.id, "ids continue after load");
        TestClimateSource climate = new TestClimateSource(-4.5F, -12F);
        ClimateSources.set(climate);
        ok(h, ClimateSources.get().averageTemp(level, 0, 140, 0) == -4.5F && ClimateSources.get().instantTemp(level, 0, 140, 0) == -12F,
            "the test climate source answers");
        ClimateSources.set(null);
        h.succeed();
    }

    /** {@code selftest sky} passes on the test world, and the Starfall commands run (SPEC §24.7, §25.10). */
    @GameTest(template = "empty", batch = "firmages_11_sky_misc")
    public static void skySelftestAndCommands(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        dev.firmages.core.command.SelfTest.Report report = dev.firmages.core.command.SelfTest.run(List.of(new dev.firmages.core.command.SkySuite(server)));
        List<String> failed = report.cases().stream().filter(c -> !c.passed()).map(c -> c.name() + ": " + c.detail()).toList();
        ok(h, failed.isEmpty() && report.cases().size() >= 7, "selftest sky failures: " + failed);
        var source = server.createCommandSourceStack().withSuppressedOutput();
        var dispatcher = server.getCommands().getDispatcher();
        try {
            ok(h, dispatcher.execute("firmages starfall list", source) >= 0, "/firmages starfall list");
            ok(h, dispatcher.execute("firmages selftest sky", source) == 0, "/firmages selftest sky has failures");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            h.fail("command failed: " + e.getMessage());
        }
        h.succeed();
    }

    /** {@code firmages:starfall_streak} through its stream codec. */
    @GameTest(template = "empty", batch = "firmages_11_sky_misc")
    public static void skyPayloadCodecs(GameTestHelper h) {
        StarfallStreakPayload p = new StarfallStreakPayload(123.5F, new BlockPos(-1234, 71, 98765), 9_876_543_210L, "starsteel", true);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        StarfallStreakPayload.STREAM_CODEC.encode(buf, p);
        StarfallStreakPayload back = StarfallStreakPayload.STREAM_CODEC.decode(buf);
        ok(h, p.equals(back) && buf.readableBytes() == 0, "round trip: " + back);
        h.succeed();
    }

    /**
     * IE is in the GameTest run: no vein in age_5; after the age_6 grant the crater has a vein with the mix id and the
     * radius + 8, and IE's save data is dirty; a vein removed by hand comes back at the reconcile.
     */
    @GameTest(template = "empty", batch = "firmages_11_sky_veins", timeoutTicks = 2400)
    public static void excavatorVeinAtAutomationAge(GameTestHelper h) {
        if (!ModList.get().isLoaded(StarfallVeins.MOD_ID)) {
            h.succeed();
            return;
        }
        MinecraftServer server = h.getLevel().getServer();
        SkySavedData d = SkySavedData.get(server);
        AgeId[] before = new AgeId[1];
        StarfallSite[] s = new StarfallSite[1];
        ResourceLocation mix = ResourceLocation.parse("firmages:mineral/meteoric");
        h.startSequence()
            .thenExecute(() -> {
                before[0] = currentAge(server);
                agesUpTo(server, AgeId.AGE_5);
            })
            .thenWaitUntil(() -> ok(h, reloadIdle(server), "reload after the age_5 setup still running"))
            .thenExecute(() -> {
                s[0] = d.newSite(77_000, -77_000, "meteoric_iron", "small", StarfallSite.Origin.LIVE, 5L);
                s[0].state = StarfallSite.State.CARVED;
                StarfallService.reconcileVeins(server, true);
                ok(h, !StarfallVeins.present(77_000, -77_000, mix) && !s[0].veinAdded, "no vein before the automation Age");
                AgeService.simulate(server, AgeId.AGE_6, true);
            })
            .thenWaitUntil(() -> ok(h, reloadIdle(server), "reload after the age_6 grant still running"))
            .thenExecute(() -> {
                SavedData ie = ieSaveData();
                // the age_6 grant reconciles on its own and may have added the vein already; start from none
                StarfallVeins.remove(77_000, -77_000);
                s[0].veinAdded = false;
                if (ie != null) ie.setDirty(false);
                StarfallService.reconcileVeins(server, false);
                ok(h, StarfallVeins.present(77_000, -77_000, mix) && s[0].veinAdded, "vein after the age_6 grant: " + StarfallVeins.describe(77_000, -77_000));
                ok(h, StarfallVeins.describe(77_000, -77_000).stream().anyMatch(l -> l.contains(" r13 ")), "radius 5 + 8: " + StarfallVeins.describe(77_000, -77_000));
                ok(h, ie != null && ie.isDirty(), "IE's save data is marked dirty");
                ok(h, StarfallVeins.remove(77_000, -77_000) == 1 && !StarfallVeins.present(77_000, -77_000, mix), "vein removed by hand");
                StarfallService.reconcileVeins(server, true);
                ok(h, StarfallVeins.present(77_000, -77_000, mix), "the reconcile restores the vein");
                ok(h, StarfallService.reconcileVeins(server, true) == 0, "the reconcile is idempotent");
                StarfallVeins.remove(77_000, -77_000);
                s[0].state = StarfallSite.State.BURNED;
                d.setDirty();
                agesUpTo(server, before[0]);
            })
            .thenWaitUntil(() -> ok(h, reloadIdle(server), "reload after restoring the Ages still running"))
            .thenSucceed();
    }

    private static SavedData ieSaveData() {
        try {
            var f = blusunrize.immersiveengineering.common.IESaveData.class.getDeclaredField("INSTANCE");
            f.setAccessible(true);
            return (SavedData) f.get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
