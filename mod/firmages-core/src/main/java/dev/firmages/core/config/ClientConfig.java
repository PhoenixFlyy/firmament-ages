package dev.firmages.core.config;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.immersion.HudFade;
import dev.firmages.core.immersion.MenuWidgetFilter;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** {@code firmages-client.toml} (SPEC §9, §18). Read by the ceremony and the immersion client code. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SKY_TINT;
    public static final ModConfigSpec.BooleanValue STOP_MUSIC;
    public static final ModConfigSpec.DoubleValue PARTICLE_SCALE;
    public static final ModConfigSpec.BooleanValue AMBIENT_DRONE;
    public static final ModConfigSpec.BooleanValue IDLE_BEAM;
    public static final ModConfigSpec.BooleanValue SKY_STREAKS;
    public static final ModConfigSpec.BooleanValue CROWN_BEAMS;
    public static final ModConfigSpec.BooleanValue LAUNCH_FX;
    public static final ModConfigSpec.BooleanValue AGE_MUSIC;

    public static final ModConfigSpec.EnumValue<HudFade.Mode> HUD_MODE;
    public static final ModConfigSpec.IntValue HUD_SIGN_SECONDS;
    public static final ModConfigSpec.BooleanValue HIDE_TOASTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_TOASTS;
    public static final ModConfigSpec.BooleanValue REDIRECT_ADVANCEMENTS;
    public static final ModConfigSpec.BooleanValue HIDE_MENU_BUTTONS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> HIDDEN_MENU_WIDGETS;
    public static final ModConfigSpec.BooleanValue HIDE_MOD_NAME_TOOLTIPS;
    public static final ModConfigSpec.BooleanValue TRIM_LOCKED_TOOLTIPS;
    public static final ModConfigSpec.ConfigValue<String> MANA_HUD_FROM_AGE;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_SCREENS;
    public static final ModConfigSpec.IntValue QUEST_PARAGRAPH_GAP;
    public static final ModConfigSpec.IntValue QUEST_WINDOW_PERCENT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CHAT_BLOCKLIST;
    public static final ModConfigSpec.BooleanValue HIDE_THIRST_BAR;
    public static final ModConfigSpec.BooleanValue HIDE_NUTRITION;
    public static final ModConfigSpec.BooleanValue HIDE_SIZE_WEIGHT;

    /** Felix 2026-10-14: Ars Nouveau's mana bar appears with the Arcane Age, when its items unlock. */
    public static final AgeId DEFAULT_MANA_HUD_FROM_AGE = AgeId.AGE_3;

    /** Toasts of the vanilla advancement, recipe and tutorial systems and Patchouli's book-entry toast. */
    public static final List<String> DEFAULT_BLOCKED_TOASTS = List.of(
        "net.minecraft.client.gui.components.toasts.AdvancementToast",
        "net.minecraft.client.gui.components.toasts.RecipeToast",
        "net.minecraft.client.gui.components.toasts.TutorialToast",
        "vazkii.patchouli.client.base.ClientAdvancements$LexiconToast");
    /** None shipped: the one join screen of the pack's mods was Enhanced Celestials 1's "Enhanced Celestials 2 is here!"
     *  (gone with the switch to Enhanced Celestials 2, which opens no screen on join; SPEC §18.1). */
    public static final List<String> DEFAULT_BLOCKED_SCREENS = List.of();

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Age ceremony effects").push("effects");
        SKY_TINT = b.define("skyTint", true);
        STOP_MUSIC = b.define("stopMusic", true);
        PARTICLE_SCALE = b.defineInRange("particleScale", 1.0, 0.0, 4.0);
        AMBIENT_DRONE = b.comment("A low hum near the lit, intact shrine heart (within 16 blocks), deeper with every Age.")
            .define("ambientDrone", true);
        IDLE_BEAM = b.comment("Show the shrine's thin idle beam (the server turns it on with shrine.permanentBeam).")
            .define("idleBeam", true);
        SKY_STREAKS = b.comment("Draw the streak and the falling body of a falling star (SPEC 25.9).")
            .define("skyStreaks", true);
        CROWN_BEAMS = b.comment("The Lens Crown's beams from each lens in to the Star Anvil, and its column into the sky while it forges.")
            .define("crownBeams", true);
        LAUNCH_FX = b.comment("The Launch Complex: the probe climbing on its flame trail, the trench jets and the descent under canopies.")
            .define("launchFx", true);
        b.pop();

        b.comment("Music (SPEC 23)").push("music");
        AGE_MUSIC = b.comment("The background music follows the world's Age (sounds firmages:music.age_0 .. age_9) wherever the game would play",
                "its generic game, creative, underwater or overworld biome music. Nether, End, menus and other mods' music stay.")
            .define("ageMusic", true);
        b.pop();

        b.comment("Immersion (SPEC 18): what the client shows of the pack's machinery").push("ui");
        HUD_MODE = b.comment("Age sign top left (the Age, its goal and the pinned quests): FADE = shown after joining, after an Age",
                "change and after a press of the 'Show Age' key (default H, shown while held), then fades. ALWAYS or NEVER.")
            .defineEnum("hudMode", HudFade.Mode.FADE);
        HUD_SIGN_SECONDS = b.comment("Seconds the Age sign stays after joining, an Age change or a press of the 'Show Age' key (FADE).",
                "Replaces hudFadeSeconds (6) of firmages-core 1.0.2 and earlier.")
            .defineInRange("hudSignSeconds", 8, 1, 120);
        HIDE_TOASTS = b.comment("Drop the toasts listed in blockedToasts (advancement, recipe, tutorial, book entries).")
            .define("hideToasts", true);
        BLOCKED_TOASTS = b.comment("Fully qualified toast class names to drop.")
            .defineListAllowEmpty("blockedToasts", DEFAULT_BLOCKED_TOASTS, () -> "", ClientConfig::isString);
        REDIRECT_ADVANCEMENTS = b.comment("The advancements screen opens the quest screen instead (FTB Quests).")
            .define("redirectAdvancements", true);
        HIDE_MENU_BUTTONS = b.comment("Hide the Mods and Advancements buttons on the title and pause screens, and the Language and Accessibility buttons on the title screen.")
            .define("hideMenuButtons", true);
        HIDDEN_MENU_WIDGETS = b.comment("Further widgets other mods add to the title and pause screens, hidden there: fully qualified class names",
                "(inner classes with $) or translation keys of the label. Default: Supplementaries' config gear (it opens every mod's config).")
            .defineListAllowEmpty("hiddenMenuWidgets", MenuWidgetFilter.DEFAULT_HIDDEN, () -> "", ClientConfig::isString);
        HIDE_MOD_NAME_TOOLTIPS = b.comment("Drop item tooltip lines that are only a mod's name.")
            .define("hideModNameTooltips", true);
        TRIM_LOCKED_TOOLTIPS = b.comment("Items that ProgressiveStages reports locked show only their (masked) name and ProgressiveStages' lines;",
                "every other mod's lines (TFC metal, heat, size) are dropped.")
            .define("trimLockedTooltips", true);
        MANA_HUD_FROM_AGE = b.comment("First Age with Ars Nouveau's mana bar and spell HUD (GUI layers ars_nouveau:mana_hud, ars_nouveau:spell_hud).",
                "Before it both are hidden; the world's Age comes from the server's status (Dawn until it arrives).")
            .define("manaHudFromAge", DEFAULT_MANA_HUD_FROM_AGE.id(), ClientConfig::isAgeId);

        BLOCKED_SCREENS = b.comment("Fully qualified screen class names that are never opened (mod announcement screens).")
            .defineListAllowEmpty("blockedScreens", DEFAULT_BLOCKED_SCREENS, () -> "", ClientConfig::isString);
        QUEST_PARAGRAPH_GAP = b.comment("Pixels (GUI scale 1) between the paragraphs of a quest description and after its subtitle.",
                "FTB Quests itself uses a full blank line (9) between paragraphs and 7 after the subtitle; a text line is 9.",
                "Below 6 the gaps title to task headings, task icons to separator and separator to text shrink by 6 minus this",
                "value (at most 4); 9 gives FTB's original spacing everywhere.")
            .defineInRange("questParagraphGap", 4, 0, 9);
        QUEST_WINDOW_PERCENT = b.comment("Size of the quest book (FTB Quests) in percent of the game window, centred; follows window size and GUI scale.",
                "0 or 100 = fullscreen as in FTB Quests; values from 1 to 49 count as 50.")
            .defineInRange("questWindowPercent", 75, 0, 100);
        b.pop();

        b.comment("Chat safety net (SPEC 18.1)").push("chat");
        CHAT_BLOCKLIST = b.comment("System and mod chat lines whose text matches one of these Java regular expressions are dropped",
                "(player chat is never touched). Each dropped line is logged at INFO. The built-in patterns (the join notices of",
                "Create Train Utilities and JourneyMap, Ars Nouveau's Makeship ad) always apply; list further ones here.")
            .defineListAllowEmpty("blocklist", List.of(), () -> "", ClientConfig::isString);
        b.pop();

        b.comment("TerraFirmaCraft displays (SPEC 18.6)").push("tfc");
        HIDE_THIRST_BAR = b.comment("Hide TFC's thirst bar (thirst is off, see tfc.disableThirst in firmages-server.toml).")
            .define("hideThirstBar", true);
        HIDE_NUTRITION = b.comment("Hide the nutrition lines of TFC's food tooltips. TFC's inventory tabs follow the server's [ui] *TabFromAge.")
            .define("hideNutrition", true);
        HIDE_SIZE_WEIGHT = b.comment("Hide TFC's size and weight line (weight and size icons, e.g. 'Light Small') on every item tooltip.")
            .define("hideSizeWeight", true);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {}

    public static boolean loaded() {
        return SPEC.isLoaded();
    }

    /** {@code value} if the client config is loaded, else {@code fallback}. */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value, T fallback) {
        return loaded() ? value.get() : fallback;
    }

    /** {@code ui.manaHudFromAge}; an unknown value counts as the default. */
    public static AgeId manaHudFromAge() {
        return AgeId.byId(get(MANA_HUD_FROM_AGE, DEFAULT_MANA_HUD_FROM_AGE.id())).orElse(DEFAULT_MANA_HUD_FROM_AGE);
    }


    private static boolean isAgeId(Object o) {
        return o instanceof String s && AgeId.byId(s).isPresent();
    }

    private static boolean isString(Object o) {
        return o instanceof String;
    }
}
