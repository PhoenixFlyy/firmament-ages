package dev.firmages.core.config;

import dev.firmages.core.age.AgeId;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * {@code firmages-server.toml} (per world, SPEC §9). Server configs load when the server starts, so values read
 * during the initial datapack load fall back to the defaults ({@link #loaded()} is false then).
 */
public final class ServerConfig {
    public static final ModConfigSpec SPEC;

    // gate
    public static final ModConfigSpec.BooleanValue GATE_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BOOT_FALLBACK_STAGES;
    public static final ModConfigSpec.IntValue RELOAD_DELAY_TICKS;
    public static final ModConfigSpec.IntValue COALESCE_TICKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXEMPT_RECIPE_TYPES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOW_RECIPES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DENY_RECIPES;
    public static final ModConfigSpec.BooleanValue RELOAD_ON_REVOKE;
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
    public static final ModConfigSpec.LongValue SHRINE_ENERGY_CAPACITY;
    public static final ModConfigSpec.BooleanValue SHRINE_PERMANENT_BEAM;
    public static final ModConfigSpec.BooleanValue SHRINE_CLEAR_WEATHER;
    public static final ModConfigSpec.BooleanValue SHRINE_BLESSINGS_EVERYWHERE;
    // origin
    public static final ModConfigSpec.IntValue ORIGIN_GATHER_RADIUS;
    public static final ModConfigSpec.IntValue ORIGIN_GATHER_COOLDOWN;
    public static final ModConfigSpec.ConfigValue<String> ORIGIN_FINALE_GATEWAY;
    // debug
    public static final ModConfigSpec.BooleanValue DEBUG_ALLOW_SIMULATE;

    public static final List<String> DEFAULT_BOOT_FALLBACK = List.of("dawn");
    public static final String DEFAULT_FINALE_GATEWAY = "firmages:the_origin";
    public static final List<String> DEFAULT_EXEMPT_TYPES = List.of("immersiveengineering:mineral_mix", "tfc:collapse", "tfc:landslide");

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Machine-recipe Age gate and Age reloads").push("gate");
        GATE_ENABLED = b.comment("Recipe filter on/off (off = log only).").define("enabled", true);
        BOOT_FALLBACK_STAGES = b.comment("Fail-strict Age snapshot for the initial datapack load when there is no valid mirror file.",
                "Keep it equal to the ProgressiveStages starting stages. 'dawn' is always included.")
            .defineListAllowEmpty("bootFallbackStages", DEFAULT_BOOT_FALLBACK, () -> "dawn", ServerConfig::isAgeId);
        RELOAD_DELAY_TICKS = b.comment("Ticks between an Age change and the datapack reload.").defineInRange("reloadDelayTicks", 60, 0, 1200);
        COALESCE_TICKS = b.comment("Further Age changes extend the pending reload up to this many ticks after the first request.")
            .defineInRange("coalesceTicks", 100, 0, 2400);
        EXEMPT_RECIPE_TYPES = b.comment("Recipe types that are never filtered (world data).")
            .defineListAllowEmpty("exemptRecipeTypes", DEFAULT_EXEMPT_TYPES, () -> "", ServerConfig::isString);
        ALLOW_RECIPES = b.comment("Recipe ids that are always kept.").defineListAllowEmpty("allowRecipes", List.of(), () -> "", ServerConfig::isString);
        DENY_RECIPES = b.comment("Recipe ids that are always dropped.").defineListAllowEmpty("denyRecipes", List.of(), () -> "", ServerConfig::isString);
        RELOAD_ON_REVOKE = b.comment("Reload datapacks when an Age is revoked.").define("reloadOnRevoke", true);
        b.pop();

        b.comment("Miner filter (m3)").push("miner");
        MINER_IE_EXCAVATOR = b.define("ieExcavator", true);
        MINER_ORE_GUARD = b.define("oreGuard", true);
        b.pop();

        b.comment("Stage-aware prospecting (m1), read by the KubeJS tag script through FirmAges.prospectingEnabled()").push("prospecting");
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
        SHRINE_ENERGY_CAPACITY = b.defineInRange("energyCapacity", 100_000_000L, 0L, Long.MAX_VALUE);
        SHRINE_PERMANENT_BEAM = b.define("permanentBeam", false);
        SHRINE_CLEAR_WEATHER = b.define("clearWeather", true);
        b.comment("Blessing effects (attributes, status effects, XP) act on players within the sanctuary radius of the heart while the shrine is intact.").push("blessings");
        SHRINE_BLESSINGS_EVERYWHERE = b.comment("true: attribute and XP blessings act everywhere (team-wide, SPEC 7.6); status effects stay near the shrine.")
            .define("everywhere", false);
        b.pop();
        b.pop();

        b.comment("The Origin and the Gathering at its altar (the end-boss trigger)").push("origin");
        ORIGIN_GATHER_RADIUS = b.comment("Every online player (spectators excepted) must stand this close to the altar to start the fight.")
            .defineInRange("gatherRadius", 6, 1, 32);
        ORIGIN_GATHER_COOLDOWN = b.comment("Seconds before a broken-up Gathering can start the fight again.")
            .defineInRange("gatherCooldownSeconds", 30, 0, 3600);
        ORIGIN_FINALE_GATEWAY = b.comment("Gateways to Eternity gate the end-boss script opens at the Gathering; only its completion in The Origin",
                "summons the final boss (read by the script through FirmAges.finaleGateway()).")
            .define("finaleGateway", DEFAULT_FINALE_GATEWAY, o -> o instanceof String str && str.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"));
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

    public static boolean prospectingEnabled() {
        return !loaded() || PROSPECTING_ENABLED.get();
    }

    public static int reloadDelayTicks() {
        return loaded() ? RELOAD_DELAY_TICKS.get() : 60;
    }

    public static int coalesceTicks() {
        return loaded() ? COALESCE_TICKS.get() : 100;
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


    /** {@code origin.finaleGateway}: the gate id the end-boss script opens and waits for. */
    public static String finaleGateway() {
        return loaded() ? ORIGIN_FINALE_GATEWAY.get() : DEFAULT_FINALE_GATEWAY;
    }

    public static boolean allowSimulate() {
        return loaded() && DEBUG_ALLOW_SIMULATE.get();
    }

    private static boolean isAgeId(Object o) {
        return o instanceof String s && AgeId.byId(s).isPresent();
    }

    private static boolean isString(Object o) {
        return o instanceof String;
    }
}
