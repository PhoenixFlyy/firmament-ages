package dev.firmages.core.config;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.hardship.CaveDwellerGate;
import dev.firmages.core.hardship.GridLock;
import dev.firmages.core.hardship.ToolRule;
import dev.firmages.core.immersion.InventoryTab;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code firmages-server.toml} (per world, SPEC §9). Server configs load when the server starts, so values read
 * during the initial datapack load fall back to the defaults ({@link #loaded()} is false then).
 */
public final class ServerConfig {
    public static final ModConfigSpec SPEC;

    // gate
    public static final ModConfigSpec.BooleanValue GATE_ENABLED;
    public static final ModConfigSpec.BooleanValue GATE_LOOT_FILTER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BOOT_FALLBACK_STAGES;
    public static final ModConfigSpec.IntValue RELOAD_DELAY_TICKS;
    public static final ModConfigSpec.IntValue COALESCE_TICKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXEMPT_RECIPE_TYPES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOW_RECIPES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DENY_RECIPES;
    public static final ModConfigSpec.BooleanValue RELOAD_ON_REVOKE;
    public static final ModConfigSpec.BooleanValue WARMUP_RELOAD;
    public static final ModConfigSpec.BooleanValue AGE_SWAP;
    public static final ModConfigSpec.IntValue WARMUP_DELAY_TICKS;
    // miner
    public static final ModConfigSpec.BooleanValue MINER_IE_EXCAVATOR;
    public static final ModConfigSpec.BooleanValue MINER_ORE_GUARD;
    // prospecting
    public static final ModConfigSpec.BooleanValue PROSPECTING_ENABLED;
    // reactor
    public static final ModConfigSpec.BooleanValue REACTOR_ITEM_HANDLER;
    public static final ModConfigSpec.BooleanValue REACTOR_CONTROLLER_ENABLED;
    public static final ModConfigSpec.DoubleValue REACTOR_SHUTDOWN_AT_CONVERSION;
    public static final ModConfigSpec.IntValue REACTOR_MIN_FUEL;
    public static final ModConfigSpec.IntValue REACTOR_MAX_CHAOS;
    // shrine
    public static final ModConfigSpec.IntValue SHRINE_PRAYER_SECONDS;
    public static final ModConfigSpec.IntValue SHRINE_MIN_PRAYER_SECONDS;
    public static final ModConfigSpec.IntValue SHRINE_PRAY_RADIUS;
    public static final ModConfigSpec.IntValue SHRINE_SANCTUARY_BASE_RADIUS;
    public static final ModConfigSpec.IntValue SHRINE_SANCTUARY_PER_TIER;
    public static final ModConfigSpec.BooleanValue SHRINE_PERMANENT_BEAM;
    public static final ModConfigSpec.BooleanValue SHRINE_CLEAR_WEATHER;
    public static final ModConfigSpec.IntValue SHRINE_MAINTENANCE_SECONDS;
    public static final ModConfigSpec.IntValue SHRINE_CONSECRATION_TICKS;
    // crown (SPEC §27.12)
    public static final ModConfigSpec.BooleanValue CROWN_ENABLED;
    public static final ModConfigSpec.BooleanValue CROWN_REQUIRE_OPEN_SKY;
    public static final ModConfigSpec.DoubleValue CROWN_NIGHT_TIME_FACTOR;
    public static final ModConfigSpec.IntValue CROWN_VALIDATE_TICKS;
    public static final ModConfigSpec.IntValue CROWN_SWING_TICKS;
    // launch complex (SPEC §31.9)
    public static final ModConfigSpec.BooleanValue LAUNCH_ENABLED;
    public static final ModConfigSpec.IntValue LAUNCH_FX_RANGE;
    public static final ModConfigSpec.IntValue LAUNCH_COUNTDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue LAUNCH_AUTO_RELAUNCH;
    // wayshrines (SPEC-himmel §28.9)
    public static final ModConfigSpec.IntValue WAYSHRINE_RADIUS;
    public static final ModConfigSpec.IntValue WAYSHRINE_PRAYER_SECONDS;
    public static final ModConfigSpec.IntValue WAYSHRINE_POINTER_RADIUS_CHUNKS;
    public static final ModConfigSpec.BooleanValue WAYSHRINE_TUTORIAL;
    // Sky Anchors (SPEC-himmel §29.7)
    public static final ModConfigSpec.IntValue ANCHOR_RADIUS;
    // origin
    public static final ModConfigSpec.IntValue ORIGIN_GATHER_RADIUS;
    public static final ModConfigSpec.IntValue ORIGIN_GATHER_COOLDOWN;
    public static final ModConfigSpec.IntValue ORIGIN_GATHER_IDLE_MINUTES;
    // the Archipelago (SPEC §31.9)
    public static final ModConfigSpec.IntValue ARK_GROWTH_TICKS;
    // boss (The Primordial, docs/primordial.md)
    public static final ModConfigSpec.DoubleValue BOSS_EFFECTIVE_HEALTH;
    public static final ModConfigSpec.DoubleValue BOSS_PER_PLAYER;
    public static final ModConfigSpec.DoubleValue BOSS_VEIL_FACTOR;
    public static final ModConfigSpec.DoubleValue BOSS_HIT_CAP;
    public static final ModConfigSpec.IntValue BOSS_ENRAGE_MINUTES;
    public static final ModConfigSpec.IntValue BOSS_STARFALL_ENRAGE_MINUTES;
    public static final ModConfigSpec.IntValue BOSS_WIPE_SECONDS;
    // sync
    public static final ModConfigSpec.BooleanValue SYNC_COALESCE;
    // team (SPEC §2.3)
    public static final ModConfigSpec.BooleanValue TEAM_AUTO_JOIN;
    // ui (immersion, SPEC §18)
    public static final ModConfigSpec.ConfigValue<String> UI_EMI_FROM_AGE;
    public static final ModConfigSpec.ConfigValue<String> UI_JADE_FROM_AGE;
    public static final ModConfigSpec.ConfigValue<String> UI_INVENTORY_BUTTONS_FROM_AGE;
    public static final ModConfigSpec.ConfigValue<String> UI_MAP_FROM_AGE;
    /** {@code ui.calendarTabFromAge}, {@code climateTabFromAge}, {@code nutritionTabFromAge}, {@code fieldGuideTabFromAge}. */
    public static final Map<InventoryTab, ModConfigSpec.ConfigValue<String>> UI_TAB_FROM_AGE;
    public static final ModConfigSpec.BooleanValue UI_WELCOME;
    // tfc (SPEC §18.6)
    public static final ModConfigSpec.BooleanValue TFC_DISABLE_THIRST;
    public static final ModConfigSpec.BooleanValue TFC_NO_NUTRITION_MALUS;
    // hardship (SPEC §19)
    public static final ModConfigSpec.ConfigValue<String> HARDSHIP_CAVE_DWELLER_UNTIL;
    public static final ModConfigSpec.ConfigValue<String> HARDSHIP_TOOL_RULE_UNTIL;
    public static final ModConfigSpec.ConfigValue<String> HARDSHIP_GRID_LOCK_UNTIL;
    // perf (SPEC §20); read at mod construction through EarlyServerConfig, the value here only documents the key
    public static final ModConfigSpec.BooleanValue PERF_LIMIT_TFC_REGISTRY_API_SCAN;
    public static final ModConfigSpec.BooleanValue PERF_REGROWTH_LOADED_ONLY;
    // starfall (SPEC §25.10)
    public static final ModConfigSpec.BooleanValue STARFALL_ENABLED;
    public static final ModConfigSpec.IntValue STARFALL_CARVE_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue STARFALL_COOL_TICKS;
    public static final ModConfigSpec.IntValue STARFALL_MAX_PENDING;
    public static final ModConfigSpec.IntValue STARFALL_SHRINE_CLEARANCE;
    public static final ModConfigSpec.IntValue STARFALL_SPAWN_CLEARANCE;
    public static final ModConfigSpec.IntValue STARFALL_CLAIM_MARGIN_CHUNKS;
    public static final ModConfigSpec.IntValue STARFALL_INHABITED_TICKS;
    public static final ModConfigSpec.BooleanValue STARFALL_OLD_CRATERS;
    public static final ModConfigSpec.IntValue STARFALL_OLD_CRATER_REGION;
    public static final ModConfigSpec.ConfigValue<String> STARFALL_AUTOMATION_AGE;
    // sky harvest (SPEC §26.8)
    public static final ModConfigSpec.BooleanValue SKY_HARVEST_ENABLED;
    // isles (SPEC §30.9)
    public static final ModConfigSpec.IntValue ISLES_LODE_YIELD;
    public static final ModConfigSpec.BooleanValue ISLES_FALLBACK;
    public static final ModConfigSpec.IntValue ISLES_FALLBACK_RADIUS;
    // balance
    public static final ModConfigSpec.BooleanValue BALANCE_ALTERNATOR_FUEL_ONLY;
    public static final ModConfigSpec.IntValue BALANCE_AE2_CELL_TYPES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BALANCE_FE_CAP;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BALANCE_MERCHANT_REDIRECTS;
    // machines (SPEC 40, 41)
    public static final ModConfigSpec.IntValue PROCESSOR_FE_PER_TICK;
    public static final ModConfigSpec.DoubleValue PROCESSOR_LUBRICANT_SPEED;
    public static final ModConfigSpec.DoubleValue CATALYST_EXTRA_TICKS;
    public static final ModConfigSpec.DoubleValue CATALYST_FE_SURCHARGE;
    public static final ModConfigSpec.IntValue CATALYST_FLAT_FE;
    public static final ModConfigSpec.IntValue CATALYST_SOURCE_PER_SECOND;
    public static final ModConfigSpec.IntValue CATALYST_SOURCE_RANGE;
    public static final ModConfigSpec.IntValue CATALYST_MIN_RPM;
    // commands
    public static final ModConfigSpec.BooleanValue COMMANDS_CONFIRM_STOP;
    // debug
    public static final ModConfigSpec.BooleanValue DEBUG_ALLOW_SIMULATE;

    public static final List<String> DEFAULT_BOOT_FALLBACK = List.of("dawn");
    /** Felix 2026-10-13: the recipe viewer (EMI) and the block overlay (Jade) both open with the Bronze Age (Jade
     *  moved from the Stone Age, H9); nothing of the machinery shows in Dawn or the Stone Age. */
    public static final AgeId DEFAULT_EMI_FROM_AGE = AgeId.AGE_1;
    public static final AgeId DEFAULT_JADE_FROM_AGE = AgeId.AGE_1;
    public static final List<String> DEFAULT_EXEMPT_TYPES = List.of("immersiveengineering:mineral_mix", "tfc:collapse", "tfc:landslide");

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Machine-recipe Age gate and Age reloads").push("gate");
        GATE_ENABLED = b.comment("Recipe filter on/off (off = log only).").define("enabled", true);
        BOOT_FALLBACK_STAGES = b.comment("Fail-strict Age snapshot for the initial datapack load when there is no valid mirror file.",
                "Keep it equal to the ProgressiveStages starting stages. 'dawn' is always included.")
            .defineListAllowEmpty("bootFallbackStages", DEFAULT_BOOT_FALLBACK, () -> "dawn", ServerConfig::isAgeId);
        RELOAD_DELAY_TICKS = b.comment("Ticks between an Age change and the Age swap (or datapack reload).").defineInRange("reloadDelayTicks", 60, 0, 1200);
        COALESCE_TICKS = b.comment("Further Age changes extend the pending reload up to this many ticks after the first request.")
            .defineInRange("coalesceTicks", 100, 0, 2400);
        EXEMPT_RECIPE_TYPES = b.comment("Recipe types that are never filtered (world data).")
            .defineListAllowEmpty("exemptRecipeTypes", DEFAULT_EXEMPT_TYPES, () -> "", ServerConfig::isString);
        ALLOW_RECIPES = b.comment("Recipe ids that are always kept.").defineListAllowEmpty("allowRecipes", List.of(), () -> "", ServerConfig::isString);
        DENY_RECIPES = b.comment("Recipe ids that are always dropped.").defineListAllowEmpty("denyRecipes", List.of(), () -> "", ServerConfig::isString);
        RELOAD_ON_REVOKE = b.comment("Swap (or reload) when an Age is revoked.").define("reloadOnRevoke", true);
        AGE_SWAP = b.comment("An Age change re-filters the recipes kept from the last datapack load, rebinds the prospecting and miner tags,",
                "flushes the machine caches and sends the recipes to the players (about a second), instead of reloading every datapack",
                "(9 to 15 s with the whole server frozen). false = the old full reload on every Age change. /reload, /firmages reload and",
                "the boot reconcile always reload in full.")
            .define("ageSwap", true);
        WARMUP_RELOAD = b.comment("Dedicated server, only with ageSwap = false: one datapack reload with the same Ages shortly after start, while no",
                "player is online, so the first Age reload players wait for runs warm (the first reload after a boot is about 1 s slower).",
                "Skipped when a boot reconcile reload runs anyway, when a player is online or any reload already ran.")
            .define("warmupReload", true);
        WARMUP_DELAY_TICKS = b.comment("Ticks after server start before the warm-up reload.").defineInRange("warmupDelayTicks", 20, 0, 6000);
        GATE_LOOT_FILTER = b.comment("Generated loot (chests, barrels, archaeology, fishing, mob and block drops) keeps only items of the Ages",
                "its player's team holds (without a player: the server's Ages) and never an item of age_items/disabled.",
                "false = loot as the loot tables roll it.")
            .define("lootFilter", true);
        b.pop();

        b.comment("Miner filter (m3)").push("miner");
        MINER_IE_EXCAVATOR = b.define("ieExcavator", true);
        MINER_ORE_GUARD = b.define("oreGuard", true);
        b.pop();

        b.comment("Stage-aware prospecting (m1): ore blocks of locked Ages leave tfc:prospectable and precisionprospecting:prospectable_mineral").push("prospecting");
        PROSPECTING_ENABLED = b.define("enabled", true);
        b.pop();

        b.comment("Draconic reactor refuelling (m4)").push("reactor");
        REACTOR_ITEM_HANDLER = b.comment("Stabilizers and injectors take awakened draconium and give chaos fragments to pipes while the reactor is COLD.")
            .define("itemHandler", true);
        b.comment("firmages:reactor_controller: shuts the reactor down, swaps chaos for fuel when COLD and emits redstone when READY.",
            "It never starts the reactor (no charge, no activation): starting stays a player action.").push("controller");
        REACTOR_CONTROLLER_ENABLED = b.define("enabled", true);
        REACTOR_SHUTDOWN_AT_CONVERSION = b.comment("Converted share (chaos / (fuel + chaos)) at which a running reactor is shut down; 0.80 = 20 % fuel left.")
            .defineInRange("shutdownAtConversion", 0.80, 0.0, 1.0);
        REACTOR_MIN_FUEL = b.comment("Fuel the swap tops up to before READY (10368 = 8 awakened draconium blocks, the maximum).",
                "Also met once not even a nugget fits any more.")
            .defineInRange("minFuel", 10368, 0, 10383);
        REACTOR_MAX_CHAOS = b.comment("Chaos that may stay in the reactor at READY. A rest below 16 always stays (no fragment holds it).")
            .defineInRange("maxChaos", 0, 0, 10383);
        b.pop();
        b.pop();

        b.comment("The Shrine").push("shrine");
        SHRINE_PRAYER_SECONDS = b.defineInRange("prayerSeconds", 10, 1, 600);
        SHRINE_MIN_PRAYER_SECONDS = b.defineInRange("minPrayerSeconds", 4, 0, 600);
        SHRINE_PRAY_RADIUS = b.defineInRange("prayRadius", 6, 1, 32);
        b.push("sanctuary");
        SHRINE_SANCTUARY_BASE_RADIUS = b.defineInRange("baseRadius", 12, 0, 256);
        SHRINE_SANCTUARY_PER_TIER = b.defineInRange("perTier", 4, 0, 64);
        b.pop();
        SHRINE_PERMANENT_BEAM = b.comment("Thin idle beam in the colour of the latest awakened Age while the heart is lit and the shrine intact;",
                "gold after the finale. Players can hide it on their side (firmages-client.toml effects.idleBeam).")
            .define("permanentBeam", true);
        SHRINE_CLEAR_WEATHER = b.define("clearWeather", true);
        SHRINE_MAINTENANCE_SECONDS = b.comment("Maintenance mode (sneak and punch the heart with an empty hand): seconds until it ends by itself. Only while it is on, consecrated shrine blocks can be broken.")
            .defineInRange("maintenanceSeconds", 60, 5, 3600);
        SHRINE_CONSECRATION_TICKS = b.comment("Ticks the consecration of a ring takes, from the heart outward (after the Age reload).")
            .defineInRange("consecrationTicks", 120, 0, 1200);
        b.pop();

        b.comment("Wayshrines: ruined shrines of Caelum in the wide world (SPEC-himmel 28). A lit and intact Wayshrine carries the",
            "blessings of the awakened tiers and Hearthward into its radius while the home shrine stands.").push("wayshrine");
        WAYSHRINE_RADIUS = b.comment("Blocks around a lit Wayshrine in which its blessings act.").defineInRange("radius", 24, 8, 64);
        WAYSHRINE_PRAYER_SECONDS = b.comment("Seconds of prayer that light a Wayshrine, divided by the number of praying players (at least 2 s).")
            .defineInRange("prayerSeconds", 5, 2, 600);
        WAYSHRINE_POINTER_RADIUS_CHUNKS = b.comment("Chunks the pointer (right-click on the heart or a lit Wayshrine) searches for a sleeping Wayshrine.")
            .defineInRange("pointerRadiusChunks", 256, 8, 512);
        WAYSHRINE_TUTORIAL = b.comment("Place one Forest Ring 300 to 800 blocks from the world spawn at the first start of a new world (never on a world older than three days or on land with traces of players).")
            .define("tutorial", true);
        b.pop();

        b.comment("Sky Anchors: from the Space Age an anchor carries the blessings of the shards it holds (and whose tiers are awakened)",
            "into its radius, in any dimension, while the home shrine stands (SPEC-himmel 29).").push("anchor");
        ANCHOR_RADIUS = b.comment("Blocks around a Sky Anchor in which its blessings act.").defineInRange("radius", 24, 8, 64);
        b.pop();

        b.comment("The Lens Crown: the Star Anvil forges celestial alloys and lenses from a chord of lenses (SPEC 27)").push("crown");
        CROWN_ENABLED = b.comment("false: the anvil never forges (the recipes stay).").define("enabled", true);
        CROWN_REQUIRE_OPEN_SKY = b.comment("Forging needs the open sky above the anvil (rain does not matter; the Nether, the End and The Origin never count).")
            .define("requireOpenSky", true);
        CROWN_NIGHT_TIME_FACTOR = b.comment("Forge time factor under the night sky: 0.5 makes night twice as fast.")
            .defineInRange("nightTimeFactor", 0.5, 0.1, 1.0);
        CROWN_VALIDATE_TICKS = b.comment("A loaded crown re-validates this often (ticks), besides every block change inside it.")
            .defineInRange("validateTicks", 100, 20, 1200);
        CROWN_SWING_TICKS = b.comment("Ticks a lens takes to swing in or out; it counts only once it is fully in.")
            .defineInRange("swingTicks", 10, 1, 100);
        b.pop();

        b.comment("The Launch Complex: unmanned probes bring voidglass and deep-space samples (SPEC 31). Fuel, launch cost, throttle and",
                "the modules are data: data/firmages/firmages_launch/.").push("launch");
        LAUNCH_ENABLED = b.comment("false: no probe launches (a probe in flight still comes home).").define("enabled", true);
        LAUNCH_FX_RANGE = b.comment("Players within this many blocks of a launch see its flame trail and descent.")
            .defineInRange("fxRange", 256, 16, 1024);
        LAUNCH_COUNTDOWN_TICKS = b.comment("Ticks of the countdown before ignition; the control gives a redstone signal on its back meanwhile.")
            .defineInRange("countdownTicks", 100, 20, 1200);
        LAUNCH_AUTO_RELAUNCH = b.comment("A returned probe goes back into its slot and flies again with the next module. false = it lands in",
                "the output with the returns, where pipes can take it.")
            .define("autoRelaunch", true);
        b.pop();

        b.comment("The Origin and the Gathering at its altar (the end-boss trigger)").push("origin");
        ORIGIN_GATHER_RADIUS = b.comment("Every online player (spectators and idle players excepted) must stand this close to the altar to start the fight.")
            .defineInRange("gatherRadius", 6, 1, 32);
        ORIGIN_GATHER_IDLE_MINUTES = b.comment("A player who has not moved or acted for this many minutes is not waited for at the altar; 0 = wait for everybody.")
            .defineInRange("gatherIdleMinutes", 5, 0, 1440);
        ORIGIN_GATHER_COOLDOWN = b.comment("Seconds before a broken-up Gathering can start the fight again.")
            .defineInRange("gatherCooldownSeconds", 30, 0, 3600);
        b.pop();

        b.comment("The Origin Archipelago after the finale: the nine islands take samples of their Age and grow (SPEC 31.8)").push("ark");
        ARK_GROWTH_TICKS = b.comment("Ticks over which a stage of an island grows from the plinth outward (0 = at once).")
            .defineInRange("growthTicks", 200, 0, 1200);
        b.pop();

        b.comment("The Primordial, the end boss (docs/primordial.md). Its health stays at the attribute cap of 1024; the damage it takes is",
                "scaled so the fight lasts as if it had effectiveHealth * ((1 - perPlayerFactor) + perPlayerFactor * players) health.",
                "The defaults come from the fight model (SPEC 39, boss/FightModel): 1, 2 and 4 players in Chaotic gear fight 8.4, 7.2 and",
                "6.5 minutes. The server log names the model's numbers each time it wakes.").push("boss");
        BOSS_EFFECTIVE_HEALTH = b.comment("Effective health for one player.")
            .defineInRange("effectiveHealth", dev.firmages.core.boss.FightModel.EFFECTIVE_HEALTH, 100.0, 1000000.0);
        BOSS_PER_PLAYER = b.comment("Share of the effective health each further player of the Gathering adds (0.7: 2 players 1.7x, 4 players 3.1x).")
            .defineInRange("perPlayerFactor", dev.firmages.core.boss.FightModel.PER_PLAYER_FACTOR, 0.0, 1.0);
        BOSS_VEIL_FACTOR = b.comment("Damage factor while the veil is closed (1 = the veil does not matter).")
            .defineInRange("veilFactor", dev.firmages.core.boss.FightModel.VEIL_FACTOR, 0.0, 1.0);
        BOSS_HIT_CAP = b.comment("The most one hit counts after armour, before the scaling (0 = no cap). A Draconic Evolution sword full of",
                "damage modules hits for about 650; with the cap the fight does not depend on the module count.")
            .defineInRange("hitCap", dev.firmages.core.boss.FightModel.HIT_CAP, 0.0, 10000.0);
        BOSS_ENRAGE_MINUTES = b.comment("After this many minutes of fighting the veil stays open only half as long.")
            .defineInRange("enrageMinutes", dev.firmages.core.boss.FightModel.ENRAGE_MINUTES, 1, 120);
        BOSS_STARFALL_ENRAGE_MINUTES = b.comment("After this many minutes star-fall rains in every phase, twice as often.")
            .defineInRange("starfallEnrageMinutes", dev.firmages.core.boss.FightModel.STARFALL_ENRAGE_MINUTES, 1, 120);
        BOSS_WIPE_SECONDS = b.comment("With no living player within 48 blocks of the altar for this many seconds, the boss withdraws and the",
                "lanterns come back (a wipe); the next Gathering summons it anew.")
            .defineInRange("wipeSeconds", 30, 5, 3600);
        b.pop();

        b.comment("ProgressiveStages client syncs").push("sync");
        SYNC_COALESCE = b.comment("Send the ProgressiveStages lock and stage sync once per player at the end of the tick instead of once per",
                "stage change (an Age grant changes several stages; each full sync costs about 0.3 s per player). Order unchanged.")
            .define("coalesce", true);
        b.pop();

        b.comment("One fellowship per server (SPEC 2.3)").push("team");
        TEAM_AUTO_JOIN = b.comment("At login a player outside an FTB Teams party joins the party that holds the Ages. A server without a party",
                "gets one: the player team with the most Ages founds it (on a new server the first player) and everybody online joins.",
                "false = players form parties themselves (/ftbteams party create, invite, join).")
            .define("autoJoin", true);
        b.pop();

        b.comment("Immersion: what players see of the pack's machinery (SPEC 18)").push("ui");
        UI_EMI_FROM_AGE = b.comment("First Age with the recipe viewer (EMI). Before it EMI is hidden, its toggle key included.")
            .define("emiFromAge", DEFAULT_EMI_FROM_AGE.id(), ServerConfig::isAgeId);
        UI_JADE_FROM_AGE = b.comment("First Age with the block-info overlay (Jade).")
            .define("jadeFromAge", DEFAULT_JADE_FROM_AGE.id(), ServerConfig::isAgeId);
        UI_INVENTORY_BUTTONS_FROM_AGE = b.comment("First Age with the Curios and Apothic Attributes buttons in the inventory, the Curios key and the Curios screen.",
                "Before it all of them are gone for players in survival; creative mode keeps them.")
            .define("inventoryButtonsFromAge", dev.firmages.core.immersion.InventoryButtons.DEFAULT_FROM.id(), ServerConfig::isAgeId);
        UI_MAP_FROM_AGE = b.comment("First Age with JourneyMap: the minimap, the full map, the waypoint screens and JourneyMap's keys.",
                "Before it all of them are gone for players in survival; creative mode keeps them.")
            .define("mapFromAge", dev.firmages.core.immersion.MapGate.DEFAULT_FROM.id(), ServerConfig::isAgeId);
        Map<InventoryTab, ModConfigSpec.ConfigValue<String>> tabs = new EnumMap<>(InventoryTab.class);
        tabs.put(InventoryTab.CALENDAR, b.comment("First Age with TerraFirmaCraft's calendar tab in the inventory, or \"never\".")
            .define(InventoryTab.CALENDAR.configKey(), InventoryTab.CALENDAR.defaultValue(), InventoryTab::isValid));
        tabs.put(InventoryTab.CLIMATE, b.comment("First Age with TerraFirmaCraft's climate tab, or \"never\".")
            .define(InventoryTab.CLIMATE.configKey(), InventoryTab.CLIMATE.defaultValue(), InventoryTab::isValid));
        tabs.put(InventoryTab.NUTRITION, b.comment("First Age with TerraFirmaCraft's nutrition tab, or \"never\" (nutrition only gives a bonus here, see [tfc]).")
            .define(InventoryTab.NUTRITION.configKey(), InventoryTab.NUTRITION.defaultValue(), InventoryTab::isValid));
        tabs.put(InventoryTab.FIELD_GUIDE, b.comment("First Age with TerraFirmaCraft's Field Guide tab, or \"never\". A hidden tab cannot be opened at all,",
                "its server packet included; the Field Guide item is not affected.")
            .define(InventoryTab.FIELD_GUIDE.configKey(), InventoryTab.FIELD_GUIDE.defaultValue(), InventoryTab::isValid));
        UI_TAB_FROM_AGE = Collections.unmodifiableMap(tabs);
        UI_WELCOME = b.comment("Each player's first join shows the title and the one-time welcome screen.")
            .define("welcome", true);
        b.pop();

        b.comment("TerraFirmaCraft survival (SPEC 18.6); food decay and tool durability stay TFC's").push("tfc");
        TFC_DISABLE_THIRST = b.comment("Thirst never drops (it stays full), so dehydration effects never apply.")
            .define("disableThirst", true);
        TFC_NO_NUTRITION_MALUS = b.comment("Nutrition only gives a bonus: the health modifier never drops below TFC's starting value",
                "(nutritionDefaultHealthModifier); a varied diet still raises it up to the maximum.")
            .define("noNutritionMalus", true);
        b.pop();

        b.comment("Hardship of the early Ages (SPEC 19)").push("hardship");
        HARDSHIP_CAVE_DWELLER_UNTIL = b.comment("First Age without Cave Dweller ReEvolved, or \"never\". From this Age on it cannot spawn or be",
                "summoned, dwellers in loaded chunks vanish, saved ones are refused when their chunk loads, and its cave noises are muted.")
            .define("caveDwellerUntilAge", CaveDwellerGate.DEFAULT_UNTIL.id(), CaveDwellerGate::isValid);
        HARDSHIP_TOOL_RULE_UNTIL = b.comment("First Age without the hand-breaking rule, or \"never\" to turn it off. Below this Age blocks in",
                "#firmages:needs_tool_early (logs, raw and hardened rock, ores, sandstone) cannot be broken without a correct tool:",
                "logs need an axe (a stone axe or hammer counts), rock and ore a pickaxe. Default age_0: the rule holds in Dawn only.")
            .define("toolRuleUntilAge", ToolRule.DEFAULT_UNTIL.id(), ToolRule::isValid);
        HARDSHIP_GRID_LOCK_UNTIL = b.comment("First Age with the 2x2 crafting grid of the inventory, or \"never\" to keep it open. Below this Age the",
                "inventory grid (and the one of the Curios screen) is closed and hidden; items laid on a block with the place key",
                "(TerraFirmaCraft, V) are crafted by sneaking and using the pile with an empty hand. Default age_0: Dawn only.")
            .define("gridLockUntilAge", GridLock.DEFAULT_UNTIL.id(), GridLock::isValid);
        b.pop();

        b.comment("Start-up performance (SPEC 20). Read once at game start, before worlds load: a dedicated server reads",
                "<world>/serverconfig/firmages-server.toml, then config/, then defaultconfigs/; a client reads config/, then defaultconfigs/.").push("perf");
        PERF_LIMIT_TFC_REGISTRY_API_SCAN = b.comment("TFCRegistryAPI looks for registry enums only in mod files that are or depend on TFC or",
                "TFCRegistryAPI, instead of loading every class of every mod seven times (~37 s, ~17k error lines). false = original scan.")
            .define("limitTfcRegistryApiScan", true);
        PERF_REGROWTH_LOADED_ONLY = b.comment("TFC Regrowing Forests: a leaf's random tick spreads a sapling only into a chunk that is already loaded.",
                "The mod looks up the target column's height, which loads (and generates) the chunk on the main thread; next to a",
                "forceloaded area during a Chunky pregen one tick waited 120 s. false = the mod's own behaviour. Read at every spread.")
            .define("regrowthLoadedChunksOnly", true);
        b.pop();

        b.comment("Starfall (SPEC 25): from the Iron Age a star falls on some clear nights; the only source of meteoric iron and starsteel.",
                "Balance values (chances, distances, sizes, metals) are data: data/firmages/firmages_sky/starfall.json.").push("starfall");
        STARFALL_ENABLED = b.comment("Live falls at night and old craters in new chunks. false = no new falls (operator commands still work).")
            .define("enabled", true);
        STARFALL_CARVE_BLOCKS_PER_TICK = b.comment("Block changes per server tick across all craters (a large crater is about 1,100 changes).")
            .defineInRange("carveBlocksPerTick", 64, 1, 4096);
        STARFALL_COOL_TICKS = b.comment("Ticks a core glows after the impact (48000 = two days); meteorites within 12 blocks of a glowing core drop double.")
            .defineInRange("coolTicks", 48000, 0, 2_400_000);
        STARFALL_MAX_PENDING = b.comment("No new fall while this many craters wait to be carved (their chunks were never loaded).")
            .defineInRange("maxPending", 6, 1, 64);
        STARFALL_SHRINE_CLEARANCE = b.comment("No fall within this many blocks of the shrine heart (or the sanctuary radius + 64, if larger).")
            .defineInRange("shrineClearance", 256, 0, 4096);
        STARFALL_SPAWN_CLEARANCE = b.comment("No fall within this many blocks of a player's respawn point or the world spawn.")
            .defineInRange("spawnClearance", 128, 0, 4096);
        STARFALL_CLAIM_MARGIN_CHUNKS = b.comment("No fall within this many chunks of a claimed chunk (FTB Chunks).")
            .defineInRange("claimMarginChunks", 2, 0, 16);
        STARFALL_INHABITED_TICKS = b.comment("A crater moves away from chunks where players spent more than this many ticks (12000 = ten minutes).")
            .defineInRange("inhabitedTicks", 12000, 0, Integer.MAX_VALUE);
        STARFALL_OLD_CRATERS = b.comment("Old cold craters in newly generated chunks (meteoric iron, one candidate per region).")
            .define("oldCraters", true);
        STARFALL_OLD_CRATER_REGION = b.comment("Side of the square region (blocks) that holds at most one old crater.")
            .defineInRange("oldCraterRegion", 1024, 256, 16384);
        STARFALL_AUTOMATION_AGE = b.comment("From this Age every crater also records an Immersive Engineering excavator vein (\"never\" = off).")
            .define("automationAge", "age_6", o -> o instanceof String s && (s.equals("never") || AgeId.byId(s).isPresent()));
        b.pop();

        b.comment("Sky Harvest (SPEC 26): the Storm Rod fuses sand into storm glass in rain, the Aurora Net catches aurora light.",
                "Balance values (aurora band, chances, yield, strike rate) are data: data/firmages/firmages_sky/sky_harvest.json.").push("skyHarvest");
        SKY_HARVEST_ENABLED = b.comment("false: rods are never struck and nets catch nothing (operator commands still report).")
            .define("enabled", true);
        b.pop();

        b.comment("Drifting Isles (SPEC 30): static isles of skystone above the clouds, the only source of firmament crystal.",
                "Shape and density are data: data/firmages/worldgen/structure/drifting_isle.json and structure_set/drifting_isles.json.").push("isles");
        ISLES_LODE_YIELD = b.comment("Firmament crystals a broken lode yields; the lode stands again on the next tick.")
            .defineInRange("lodeYield", 2, 1, 8);
        ISLES_FALLBACK = b.comment("On a world older than the isles: from the Industrial Age, place one cluster with the Wind Shrine near the heart",
                "when no isle start lies within fallbackRadius.").define("fallback", true);
        ISLES_FALLBACK_RADIUS = b.comment("Blocks around the shrine heart (else the world spawn) in which an isle must exist from the Industrial Age.")
            .defineInRange("fallbackRadius", 600, 200, 4096);
        b.pop();

        b.comment("Balance hooks of the harmony round (SPEC 34).").push("balance");
        BALANCE_ALTERNATOR_FUEL_ONLY = b.comment("Felix H1A: the Crafts and Additions alternator gives FE only for the share of its network's rotation",
                "that comes from fuelled sources (steam engines on an actively heated boiler, diesel engines). Water wheels, windmills,",
                "hand cranks and electric motors turn it but give no FE. false = the alternator's own rate.")
            .define("alternatorFuelOnly", true);
        BALANCE_AE2_CELL_TYPES = b.comment("Item types per AE2 item storage cell (AE2's own limit is 63; MEGA item cells too, portable cells keep theirs).",
                "Fluid and chemical cells keep their own limit. Synced to the clients with the server config.")
            .defineInRange("ae2CellTypes", 126, 63, 252);
        BALANCE_FE_CAP = b.comment("FE safety net (SPEC 37): \"<block id>=<FE per tick>\" caps what one block of that type gives per game tick through",
                "the energy capability, both what is drawn out of it and what it pushes into a neighbour (also Mekanism's own energy",
                "link). For sources a mod's own config cannot tame. Example: [\"createappliedkinetics:energy_provider=0\"]. Applies to",
                "new capability lookups: placed or reloaded blocks, or after a restart.")
            .defineListAllowEmpty("feCap", List.of(), () -> "", dev.firmages.core.balance.FeCaps::validEntry);
        BALANCE_MERCHANT_REDIRECTS = b.comment("One Iron in trades (SPEC 34.4): \"<item>=<item>\" makes every merchant offer (villagers, wandering",
                "traders, mod traders) that gives or asks for the first item give or ask for the second, rewritten when a player",
                "right-clicks the merchant. Loot is the global loot modifier firmages:replace_item (data).")
            .defineListAllowEmpty("merchantRedirects", List.of("minecraft:iron_ingot=tfc:metal/ingot/wrought_iron"), () -> "",
                dev.firmages.core.balance.OneIron::validEntry);
        b.pop();

        b.comment("The pack's own machines: the Ore Processor of the Singularity Age (SPEC 40) and the Arcane Catalyst (SPEC 41).").push("machines");
        PROCESSOR_FE_PER_TICK = b.comment("FE the Ore Processor draws per tick of work at base speed; an operation costs this times its time.",
                "Its recipes are data (firmages:ore_processing, written from metals.json).")
            .defineInRange("oreProcessorFePerTick", 15_000, 1_000, 10_000_000);
        PROCESSOR_LUBRICANT_SPEED = b.comment("Speed of an operation that starts with its lubricant; the FE per tick rises with it, the FE per operation stays.")
            .defineInRange("oreProcessorLubricantSpeed", 2.0, 1.0, 4.0);
        CATALYST_EXTRA_TICKS = b.comment("Extra work ticks per game tick the Arcane Catalyst gives the machine it faces (0.5 = +50 % speed).")
            .defineInRange("catalystExtraTicks", 0.5, 0.0, 1.0);
        CATALYST_FE_SURCHARGE = b.comment("FE the catalyst pays per extra tick, as a share of what the machine drew in that tick (1.0: an extra",
                "operation costs twice the FE).").defineInRange("catalystFeSurcharge", 1.0, 0.0, 4.0);
        CATALYST_FLAT_FE = b.comment("FE per extra tick when the machine's energy cannot be read (a multiblock part without an energy port).")
            .defineInRange("catalystFlatFe", 512, 0, 1_000_000);
        CATALYST_SOURCE_PER_SECOND = b.comment("Source the catalyst draws each second from source jars within catalystSourceRange blocks.")
            .defineInRange("catalystSourcePerSecond", 10, 0, 10_000);
        CATALYST_SOURCE_RANGE = b.defineInRange("catalystSourceRange", 6, 1, 16);
        CATALYST_MIN_RPM = b.comment("Rotation a shaft or cog beside the catalyst must turn at.").defineInRange("catalystMinRpm", 64, 0, 256);
        b.pop();

        b.comment("Operator commands of the dedicated server (SPEC 10.1)").push("commands");
        COMMANDS_CONFIRM_STOP = b.comment("/stop asks for /stop confirm: a confirmed stop ends the start script's loop, /restart starts the server again.",
                "false = /stop stops at once, as in vanilla (a hosting panel whose stop button sends \"stop\").")
            .define("confirmStop", true);
        b.pop();

        b.push("debug");
        DEBUG_ALLOW_SIMULATE = b.comment("Enables /firmages ages simulate (self-tests only).").define("allowSimulate", false);
        b.pop();

        SPEC = b.build();
    }

    private ServerConfig() {}

    public static boolean loaded() {
        return SPEC.isLoaded();
    }

    public static boolean confirmStop() {
        return !loaded() || COMMANDS_CONFIRM_STOP.get();
    }

    public static boolean prospectingEnabled() {
        return !loaded() || PROSPECTING_ENABLED.get();
    }

    public static int reloadDelayTicks() {
        return loaded() ? RELOAD_DELAY_TICKS.get() : 60;
    }

    public static int coalesceTicks() {
        return loaded() ? COALESCE_TICKS.get() : 100;
    }

    public static boolean syncCoalesce() {
        return !loaded() || SYNC_COALESCE.get();
    }

    public static boolean minerIeExcavator() {
        return !loaded() || MINER_IE_EXCAVATOR.get();
    }

    public static boolean minerOreGuard() {
        return !loaded() || MINER_ORE_GUARD.get();
    }

    public static boolean reloadOnRevoke() {
        return !loaded() || RELOAD_ON_REVOKE.get();
    }

    /** {@code gate.ageSwap}: Age changes swap instead of reloading (true while the config is not loaded). */
    public static boolean ageSwap() {
        return !loaded() || AGE_SWAP.get();
    }

    /** {@code gate.lootFilter}: the loot Age gate (SPEC §19.5) is on (true while the config is not loaded). */
    public static boolean lootFilter() {
        return !loaded() || GATE_LOOT_FILTER.get();
    }

    public static boolean warmupReload() {
        return loaded() && WARMUP_RELOAD.get();
    }

    public static int warmupDelayTicks() {
        return loaded() ? WARMUP_DELAY_TICKS.get() : 20;
    }



    public static AgeId emiFromAge() {
        return loaded() ? AgeId.byId(UI_EMI_FROM_AGE.get()).orElse(DEFAULT_EMI_FROM_AGE) : DEFAULT_EMI_FROM_AGE;
    }

    public static AgeId jadeFromAge() {
        return loaded() ? AgeId.byId(UI_JADE_FROM_AGE.get()).orElse(DEFAULT_JADE_FROM_AGE) : DEFAULT_JADE_FROM_AGE;
    }

    /** {@code ui.inventoryButtonsFromAge}; an unknown value counts as the default. */
    public static AgeId inventoryButtonsFromAge() {
        AgeId fallback = dev.firmages.core.immersion.InventoryButtons.DEFAULT_FROM;
        return loaded() ? AgeId.byId(UI_INVENTORY_BUTTONS_FROM_AGE.get()).orElse(fallback) : fallback;
    }

    /** {@code ui.mapFromAge}; an unknown value counts as the default. */
    public static AgeId mapFromAge() {
        AgeId fallback = dev.firmages.core.immersion.MapGate.DEFAULT_FROM;
        return loaded() ? AgeId.byId(UI_MAP_FROM_AGE.get()).orElse(fallback) : fallback;
    }

    /** The first Age with TFC's {@code tab} in the inventory, empty for never. */
    public static Optional<AgeId> tabFromAge(InventoryTab tab) {
        return tab.fromAge(loaded() ? UI_TAB_FROM_AGE.get(tab).get() : tab.defaultValue());
    }

    public static boolean welcome() {
        return !loaded() || UI_WELCOME.get();
    }

    public static boolean tfcDisableThirst() {
        return !loaded() || TFC_DISABLE_THIRST.get();
    }

    public static boolean tfcNoNutritionMalus() {
        return !loaded() || TFC_NO_NUTRITION_MALUS.get();
    }

    /** {@code hardship.caveDwellerUntilAge}: the first Age without the Cave Dweller, empty for never. */
    public static Optional<AgeId> caveDwellerUntil() {
        return CaveDwellerGate.until(loaded() ? HARDSHIP_CAVE_DWELLER_UNTIL.get() : CaveDwellerGate.DEFAULT_UNTIL.id());
    }

    /** {@code hardship.toolRuleUntilAge}: the first Age without the hand-breaking rule, empty when it is off. */
    public static Optional<AgeId> toolRuleUntil() {
        return ToolRule.until(loaded() ? HARDSHIP_TOOL_RULE_UNTIL.get() : ToolRule.DEFAULT_UNTIL.id());
    }

    /** {@code hardship.gridLockUntilAge}: the first Age with an open inventory crafting grid, empty when the lock is off. */
    public static Optional<AgeId> gridLockUntil() {
        return GridLock.until(loaded() ? HARDSHIP_GRID_LOCK_UNTIL.get() : GridLock.DEFAULT_UNTIL.id());
    }

    /** {@code team.autoJoin}. */
    public static boolean teamAutoJoin() {
        return !loaded() || TEAM_AUTO_JOIN.get();
    }

    /** {@code boss.effectiveHealth}: the effective health The Primordial is scaled to for one player. */
    public static double bossEffectiveHealth() {
        return loaded() ? BOSS_EFFECTIVE_HEALTH.get() : dev.firmages.core.boss.FightModel.EFFECTIVE_HEALTH;
    }

    public static double bossPerPlayer() {
        return loaded() ? BOSS_PER_PLAYER.get() : dev.firmages.core.boss.FightModel.PER_PLAYER_FACTOR;
    }

    public static double bossVeilFactor() {
        return loaded() ? BOSS_VEIL_FACTOR.get() : dev.firmages.core.boss.FightModel.VEIL_FACTOR;
    }

    /** {@code boss.hitCap}: the most one hit counts after armour (0 = no cap). */
    public static double bossHitCap() {
        return loaded() ? BOSS_HIT_CAP.get() : dev.firmages.core.boss.FightModel.HIT_CAP;
    }

    public static int bossEnrageMinutes() {
        return loaded() ? BOSS_ENRAGE_MINUTES.get() : dev.firmages.core.boss.FightModel.ENRAGE_MINUTES;
    }

    public static int bossStarfallEnrageMinutes() {
        return loaded() ? BOSS_STARFALL_ENRAGE_MINUTES.get() : dev.firmages.core.boss.FightModel.STARFALL_ENRAGE_MINUTES;
    }

    public static int bossWipeSeconds() {
        return loaded() ? BOSS_WIPE_SECONDS.get() : 30;
    }

    /** {@code origin.gatherIdleMinutes}: 0 = nobody is left out. */
    public static int gatherIdleMinutes() {
        return loaded() ? ORIGIN_GATHER_IDLE_MINUTES.get() : 5;
    }

    public static boolean skyHarvestEnabled() {
        return !loaded() || SKY_HARVEST_ENABLED.get();
    }

    /** {@code isles.lodeYield}. */
    public static int islesLodeYield() {
        return loaded() ? ISLES_LODE_YIELD.get() : 2;
    }

    /** {@code isles.fallback}. */
    public static boolean islesFallback() {
        return !loaded() || ISLES_FALLBACK.get();
    }

    /** {@code isles.fallbackRadius}. */
    public static int islesFallbackRadius() {
        return loaded() ? ISLES_FALLBACK_RADIUS.get() : 600;
    }

    public static boolean starfallEnabled() {
        return !loaded() || STARFALL_ENABLED.get();
    }

    public static int starfallCarveBlocksPerTick() {
        return loaded() ? STARFALL_CARVE_BLOCKS_PER_TICK.get() : 64;
    }

    public static int starfallCoolTicks() {
        return loaded() ? STARFALL_COOL_TICKS.get() : 48000;
    }

    public static int starfallMaxPending() {
        return loaded() ? STARFALL_MAX_PENDING.get() : 6;
    }

    public static int starfallShrineClearance() {
        return loaded() ? STARFALL_SHRINE_CLEARANCE.get() : 256;
    }

    public static int starfallSpawnClearance() {
        return loaded() ? STARFALL_SPAWN_CLEARANCE.get() : 128;
    }

    public static int starfallClaimMarginChunks() {
        return loaded() ? STARFALL_CLAIM_MARGIN_CHUNKS.get() : 2;
    }

    public static int starfallInhabitedTicks() {
        return loaded() ? STARFALL_INHABITED_TICKS.get() : 12000;
    }

    public static boolean starfallOldCraters() {
        return !loaded() || STARFALL_OLD_CRATERS.get();
    }

    public static int starfallOldCraterRegion() {
        return loaded() ? STARFALL_OLD_CRATER_REGION.get() : 1024;
    }

    /** {@code starfall.automationAge}: the first Age with excavator veins at craters, empty for never. */
    public static Optional<AgeId> starfallAutomationAge() {
        return AgeId.byId(loaded() ? STARFALL_AUTOMATION_AGE.get() : "age_6");
    }

    public static boolean crownEnabled() {
        return !loaded() || CROWN_ENABLED.get();
    }

    public static boolean crownRequireOpenSky() {
        return !loaded() || CROWN_REQUIRE_OPEN_SKY.get();
    }

    public static double crownNightTimeFactor() {
        return loaded() ? CROWN_NIGHT_TIME_FACTOR.get() : 0.5;
    }

    public static int crownValidateTicks() {
        return loaded() ? CROWN_VALIDATE_TICKS.get() : 100;
    }

    public static int crownSwingTicks() {
        return loaded() ? CROWN_SWING_TICKS.get() : 10;
    }

    public static boolean launchEnabled() {
        return !loaded() || LAUNCH_ENABLED.get();
    }

    public static int launchFxRange() {
        return loaded() ? LAUNCH_FX_RANGE.get() : 256;
    }

    public static int launchCountdownTicks() {
        return loaded() ? LAUNCH_COUNTDOWN_TICKS.get() : 100;
    }

    public static boolean launchAutoRelaunch() {
        return !loaded() || LAUNCH_AUTO_RELAUNCH.get();
    }

    public static boolean allowSimulate() {
        return loaded() && DEBUG_ALLOW_SIMULATE.get();
    }

    /** {@code wayshrine.radius}. */
    public static int wayshrineRadius() {
        return loaded() ? WAYSHRINE_RADIUS.get() : 24;
    }

    /** {@code wayshrine.prayerSeconds}. */
    public static int wayshrinePrayerSeconds() {
        return loaded() ? WAYSHRINE_PRAYER_SECONDS.get() : 5;
    }

    /** {@code anchor.radius}. */
    public static int anchorRadius() {
        return loaded() ? ANCHOR_RADIUS.get() : 24;
    }

    /** {@code wayshrine.pointerRadiusChunks}. */
    public static int wayshrinePointerRadiusChunks() {
        return loaded() ? WAYSHRINE_POINTER_RADIUS_CHUNKS.get() : 256;
    }

    /** {@code wayshrine.tutorial}. */
    /** {@code perf.regrowthLoadedChunksOnly}. */
    public static boolean regrowthLoadedChunksOnly() {
        return !loaded() || PERF_REGROWTH_LOADED_ONLY.get();
    }

    /** {@code balance.alternatorFuelOnly} (Felix H1A). */
    public static boolean alternatorFuelOnly() {
        return !loaded() || BALANCE_ALTERNATOR_FUEL_ONLY.get();
    }

    public static long processorFePerTick() {
        return loaded() ? PROCESSOR_FE_PER_TICK.get() : 15_000;
    }

    public static double processorLubricantSpeed() {
        return loaded() ? PROCESSOR_LUBRICANT_SPEED.get() : 2.0;
    }

    public static double catalystExtraTicks() {
        return loaded() ? CATALYST_EXTRA_TICKS.get() : 0.5;
    }

    public static double catalystFeSurcharge() {
        return loaded() ? CATALYST_FE_SURCHARGE.get() : 1.0;
    }

    public static int catalystFlatFe() {
        return loaded() ? CATALYST_FLAT_FE.get() : 512;
    }

    public static int catalystSourcePerSecond() {
        return loaded() ? CATALYST_SOURCE_PER_SECOND.get() : 10;
    }

    public static int catalystSourceRange() {
        return loaded() ? CATALYST_SOURCE_RANGE.get() : 6;
    }

    public static int catalystMinRpm() {
        return loaded() ? CATALYST_MIN_RPM.get() : 64;
    }

    /** {@code balance.merchantRedirects}. */
    public static List<? extends String> merchantRedirects() {
        return loaded() ? BALANCE_MERCHANT_REDIRECTS.get() : List.of("minecraft:iron_ingot=tfc:metal/ingot/wrought_iron");
    }

    /** {@code balance.ae2CellTypes}: item types per AE2 item cell. */
    public static int ae2CellTypes() {
        return loaded() ? BALANCE_AE2_CELL_TYPES.get() : 126;
    }

    public static boolean wayshrineTutorial() {
        return !loaded() || WAYSHRINE_TUTORIAL.get();
    }

    /** Keys removed from this file that NeoForge drops silently when it corrects the file: key -> the WARN line. */
    public static final Map<String, String> REMOVED_KEYS = Map.of(
        "shrine.blessings.everywhere",
        "shrine.blessings.everywhere was removed: Sky Anchors carry the blessings away from the shrine from the Space Age on.");

    /**
     * Mod construction: one WARN per removed key that an existing {@code firmages-server.toml} still holds (SPEC-himmel
     * §29.7, S4A). Read through {@link EarlyServerConfig} before NeoForge loads and corrects the file.
     */
    public static void warnRemovedKeys() {
        java.nio.file.Path world = EarlyServerConfig.dedicatedWorldDir();
        REMOVED_KEYS.forEach((key, line) -> {
            if (EarlyServerConfig.value(world, key).isPresent()) dev.firmages.core.FirmagesCore.LOGGER.warn(line);
        });
    }

    private static boolean isAgeId(Object o) {
        return o instanceof String s && AgeId.byId(s).isPresent();
    }

    private static boolean isString(Object o) {
        return o instanceof String;
    }
}
