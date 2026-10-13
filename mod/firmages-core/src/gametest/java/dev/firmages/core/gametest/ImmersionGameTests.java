package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeService;
import dev.firmages.core.age.AgeSnapshot;
import dev.firmages.core.command.DebugPlayerCommands;
import dev.firmages.core.config.ServerConfig;
import dev.firmages.core.immersion.AgeStatus;
import dev.firmages.core.immersion.ImmersionService;
import dev.firmages.core.immersion.InventoryTab;
import dev.firmages.core.net.AgeStatusPayload;
import dev.firmages.core.net.WelcomePayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Level G tests of the immersion features (SPEC §18): the login status and the once-per-player welcome on real
 * (headless) players, the status broadcast, the TFC tabs per Age as synced, and the TerraFirmaCraft thirst, nutrition
 * and tab mixins against TFC itself (TFC and Patchouli are only in the opt-in run {@code -PgametestTfc}). Client-only
 * parts are unit-tested ({@code ImmersionTest}).
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class ImmersionGameTests {
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(ImmersionGameTests.class);
    }

    private static void ok(GameTestHelper h, boolean cond, String what) {
        if (!cond) h.fail(what);
    }

    private static ServerPlayer join(GameTestHelper h, String name) {
        DebugPlayerCommands.record(name);
        return DebugPlayerCommands.spawn(h.getLevel().getServer(), h.getLevel(), name, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1))));
    }

    private static long count(List<CustomPacketPayload> payloads, Class<?> type) {
        return payloads.stream().filter(type::isInstance).count();
    }

    /**
     * SPEC §18.5: a player's first join gets the age status and the welcome; the flag is saved with the player, so a
     * rejoin gets the status only; {@code /firmages welcome reset} brings the welcome back once.
     */
    @GameTest(template = "empty", batch = "firmages_6_immersion")
    public static void welcomeOncePerPlayer(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String name = "gt_welcome";
        ServerPlayer p = join(h, name);
        List<CustomPacketPayload> first = DebugPlayerCommands.recorded(name);
        ok(h, count(first, WelcomePayload.class) == 1, "first join: one welcome, got " + first);
        ok(h, p.getData(ImmersionService.WELCOMED.get()), "first join: flag set");
        AgeStatusPayload status = first.stream().filter(AgeStatusPayload.class::isInstance).map(AgeStatusPayload.class::cast)
            .reduce((a, b) -> b).orElse(null);
        ok(h, status != null && status.status().equals(ImmersionService.status(server)), "login status = server status, got " + status);
        DebugPlayerCommands.despawn(p);

        ServerPlayer again = join(h, name);
        List<CustomPacketPayload> second = DebugPlayerCommands.recorded(name);
        ok(h, count(second, WelcomePayload.class) == 0 && count(second, AgeStatusPayload.class) >= 1,
            "rejoin: status but no welcome (flag saved with the player), got " + second);
        ok(h, !ImmersionService.welcomeIfNew(again), "welcomeIfNew is false for a welcomed player");
        ImmersionService.resetWelcome(again);
        ok(h, ImmersionService.welcomeIfNew(again) && !ImmersionService.welcomeIfNew(again), "after reset: exactly one more welcome");
        DebugPlayerCommands.despawn(again);
        h.succeed();
    }

    /** SPEC §18.3/18.4: the status follows the global Age state and the [ui] config, and a broadcast reaches players. */
    @GameTest(template = "empty", batch = "firmages_6_immersion")
    public static void statusFollowsAgesAndConfig(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        AgeStatus st = ImmersionService.status(server);
        ok(h, st.age() == AgeStatus.current(AgeService.state(server).snapshot()), "status Age is the highest unlocked Age: " + st);
        ok(h, st.recipeViewer() == AgeStatus.reached(st.age(), ServerConfig.emiFromAge())
            && st.blockInfo() == AgeStatus.reached(st.age(), ServerConfig.jadeFromAge()), "viewer and overlay follow ui.*FromAge: " + st);
        // Felix 2026-10-13 (late): Jade from the Stone Age, EMI from the Bronze Age (the defaults a new world gets)
        ok(h, "age_1".equals(ServerConfig.UI_EMI_FROM_AGE.getDefault()) && "age_0".equals(ServerConfig.UI_JADE_FROM_AGE.getDefault()),
            "defaults emiFromAge = age_1, jadeFromAge = age_0, got " + ServerConfig.UI_EMI_FROM_AGE.getDefault() + ", " + ServerConfig.UI_JADE_FROM_AGE.getDefault());
        String was = ServerConfig.UI_EMI_FROM_AGE.get();
        try {
            ServerConfig.UI_EMI_FROM_AGE.set("dawn");
            ok(h, ImmersionService.status(server).recipeViewer(), "emiFromAge = dawn opens EMI at once");
            ServerConfig.UI_EMI_FROM_AGE.set("age_9");
            ok(h, st.age() == dev.firmages.core.age.AgeId.AGE_9 || !ImmersionService.status(server).recipeViewer(), "emiFromAge = age_9 keeps EMI closed");
        } finally {
            ServerConfig.UI_EMI_FROM_AGE.set(was);
        }
        String name = "gt_status";
        ServerPlayer p = join(h, name);
        try {
            DebugPlayerCommands.record(name);
            ImmersionService.broadcast(server);
            List<CustomPacketPayload> got = DebugPlayerCommands.recorded(name);
            ok(h, got.size() == 1 && got.get(0) instanceof AgeStatusPayload s && s.status().equals(ImmersionService.status(server)),
                "broadcast sends the status once, got " + got);
        } finally {
            DebugPlayerCommands.despawn(p);
        }
        h.succeed();
    }

    /**
     * SPEC §18.6: the TFC tabs as the client receives them ({@code firmages:age_status} encoded and decoded) in every
     * Age with Felix's defaults: none in Dawn, the calendar from the Stone Age, the climate page from the Iron Age,
     * nutrition and the Field Guide never. A config change applies to the next status.
     */
    @GameTest(template = "empty", batch = "firmages_6_immersion")
    public static void tfcTabsFollowAges(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        // Felix 2026-10-13: the defaults a new world gets
        Map<InventoryTab, String> defaults = Map.of(InventoryTab.CALENDAR, "age_0", InventoryTab.CLIMATE, "age_2",
            InventoryTab.NUTRITION, "never", InventoryTab.FIELD_GUIDE, "never");
        for (InventoryTab t : InventoryTab.values()) {
            ok(h, defaults.get(t).equals(ServerConfig.UI_TAB_FROM_AGE.get(t).getDefault()),
                "default " + t.configKey() + " = " + defaults.get(t) + ", got " + ServerConfig.UI_TAB_FROM_AGE.get(t).getDefault());
        }
        Map<InventoryTab, String> was = new HashMap<>();
        for (InventoryTab t : InventoryTab.values()) was.put(t, ServerConfig.UI_TAB_FROM_AGE.get(t).get());
        try {
            defaults.forEach((t, v) -> ServerConfig.UI_TAB_FROM_AGE.get(t).set(v));
            List<AgeId> unlocked = new ArrayList<>();
            for (AgeId age : AgeId.all()) {
                unlocked.add(age);
                AgeStatus status = AgeStatus.of(AgeSnapshot.of(List.copyOf(unlocked), 1, 0), tier -> Optional.empty(),
                    ServerConfig.emiFromAge(), ServerConfig.jadeFromAge(), ServerConfig::tabFromAge);
                AgeStatus synced = sync(server, status);
                Set<InventoryTab> expected = EnumSet.noneOf(InventoryTab.class);
                if (age.ordinal() >= AgeId.AGE_0.ordinal()) expected.add(InventoryTab.CALENDAR);
                if (age.ordinal() >= AgeId.AGE_2.ordinal()) expected.add(InventoryTab.CLIMATE);
                ok(h, synced.equals(status) && synced.tabs().equals(expected), age + ": client gets tabs " + expected + ", got " + synced);
            }
            AgeStatus now = ImmersionService.status(server);
            ok(h, now.tabs().equals(InventoryTab.open(now.age(), ServerConfig::tabFromAge)), "the server status uses ui.*TabFromAge: " + now);
            ServerConfig.UI_TAB_FROM_AGE.get(InventoryTab.NUTRITION).set("dawn");
            ok(h, ImmersionService.status(server).tabs().contains(InventoryTab.NUTRITION), "nutritionTabFromAge = dawn opens the tab at once");
            ServerConfig.UI_TAB_FROM_AGE.get(InventoryTab.CALENDAR).set("never");
            ok(h, !ImmersionService.status(server).tabs().contains(InventoryTab.CALENDAR), "calendarTabFromAge = never closes the tab");
        } finally {
            was.forEach((t, v) -> ServerConfig.UI_TAB_FROM_AGE.get(t).set(v));
        }
        if (ModList.get().isLoaded("tfc")) { // the server guard against TFC's own packet handler (opt-in run -PgametestTfc)
            ServerPlayer p = join(h, "gt_tfc_tabs");
            try {
                p.setGameMode(GameType.SURVIVAL);
                TfcProbe.tabGuard(h, p);
            } finally {
                DebugPlayerCommands.despawn(p);
            }
        }
        h.succeed();
    }

    /** The status after the trip through {@code firmages:age_status}'s codec, as the client decodes it. */
    private static AgeStatus sync(MinecraftServer server, AgeStatus status) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        AgeStatusPayload.STREAM_CODEC.encode(buf, AgeStatusPayload.of(status));
        return AgeStatusPayload.STREAM_CODEC.decode(buf).status();
    }

    /**
     * SPEC §18.6: with TFC loaded, thirst never drops and nutrition never lowers health below the starting value.
     * TFC is only in the opt-in run {@code gradlew runGameTestServer -PgametestTfc}; without it this test passes empty.
     */
    @GameTest(template = "empty", batch = "firmages_6_immersion")
    public static void tfcThirstAndNutrition(GameTestHelper h) {
        if (!ModList.get().isLoaded("tfc")) {
            FirmagesCore.LOGGER.info("GameTest tfcThirstAndNutrition: TerraFirmaCraft not in this run (-PgametestTfc), skipped");
            h.succeed();
            return;
        }
        ServerPlayer p = join(h, "gt_tfc");
        try {
            p.setGameMode(GameType.SURVIVAL);
            TfcProbe.check(h, p);
        } finally {
            DebugPlayerCommands.despawn(p);
        }
        h.succeed();
    }
}
