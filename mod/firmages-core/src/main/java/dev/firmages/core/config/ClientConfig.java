package dev.firmages.core.config;

import dev.firmages.core.immersion.ChatBlocklist;
import dev.firmages.core.immersion.HudFade;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** {@code firmages-client.toml} (SPEC §9, §18). Read by the ceremony and the immersion client code. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SKY_TINT;
    public static final ModConfigSpec.BooleanValue STOP_MUSIC;
    public static final ModConfigSpec.DoubleValue PARTICLE_SCALE;
    public static final ModConfigSpec.BooleanValue AMBIENT_DRONE;

    public static final ModConfigSpec.EnumValue<HudFade.Mode> HUD_MODE;
    public static final ModConfigSpec.IntValue HUD_FADE_SECONDS;
    public static final ModConfigSpec.BooleanValue HIDE_TOASTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_TOASTS;
    public static final ModConfigSpec.BooleanValue REDIRECT_ADVANCEMENTS;
    public static final ModConfigSpec.BooleanValue HIDE_MENU_BUTTONS;
    public static final ModConfigSpec.BooleanValue HIDE_MOD_NAME_TOOLTIPS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_SCREENS;
    public static final ModConfigSpec.IntValue QUEST_PARAGRAPH_GAP;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CHAT_BLOCKLIST;
    public static final ModConfigSpec.BooleanValue HIDE_THIRST_BAR;
    public static final ModConfigSpec.BooleanValue HIDE_NUTRITION;

    /** Toasts of the vanilla advancement, recipe and tutorial systems and Patchouli's book-entry toast. */
    public static final List<String> DEFAULT_BLOCKED_TOASTS = List.of(
        "net.minecraft.client.gui.components.toasts.AdvancementToast",
        "net.minecraft.client.gui.components.toasts.RecipeToast",
        "net.minecraft.client.gui.components.toasts.TutorialToast",
        "vazkii.patchouli.client.base.ClientAdvancements$LexiconToast");
    /** Enhanced Celestials 6.0.2.6 opens this on every world join ("Enhanced Celestials 2 is here!"). */
    public static final List<String> DEFAULT_BLOCKED_SCREENS = List.of(
        "dev.corgitaco.enhancedcelestials.client.gui.EC2AnnouncementScreen");

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Age ceremony effects").push("effects");
        SKY_TINT = b.define("skyTint", true);
        STOP_MUSIC = b.define("stopMusic", true);
        PARTICLE_SCALE = b.defineInRange("particleScale", 1.0, 0.0, 4.0);
        AMBIENT_DRONE = b.define("ambientDrone", true);
        b.pop();

        b.comment("Immersion (SPEC 18): what the client shows of the pack's machinery").push("ui");
        HUD_MODE = b.comment("Age indicator top left: FADE = shown after joining and after an Age change, then fades;",
                "also shown while the 'Show Age' key is held (default H). ALWAYS or NEVER.")
            .defineEnum("hudMode", HudFade.Mode.FADE);
        HUD_FADE_SECONDS = b.comment("Seconds the Age indicator stays after joining or an Age change (FADE).")
            .defineInRange("hudFadeSeconds", 6, 1, 120);
        HIDE_TOASTS = b.comment("Drop the toasts listed in blockedToasts (advancement, recipe, tutorial, book entries).")
            .define("hideToasts", true);
        BLOCKED_TOASTS = b.comment("Fully qualified toast class names to drop.")
            .defineListAllowEmpty("blockedToasts", DEFAULT_BLOCKED_TOASTS, () -> "", ClientConfig::isString);
        REDIRECT_ADVANCEMENTS = b.comment("The advancements screen opens the quest screen instead (FTB Quests).")
            .define("redirectAdvancements", true);
        HIDE_MENU_BUTTONS = b.comment("Hide the Mods and Advancements buttons on the title and pause screens.")
            .define("hideMenuButtons", true);
        HIDE_MOD_NAME_TOOLTIPS = b.comment("Drop item tooltip lines that are only a mod's name.")
            .define("hideModNameTooltips", true);
        BLOCKED_SCREENS = b.comment("Fully qualified screen class names that are never opened (mod announcement screens).")
            .defineListAllowEmpty("blockedScreens", DEFAULT_BLOCKED_SCREENS, () -> "", ClientConfig::isString);
        QUEST_PARAGRAPH_GAP = b.comment("Pixels (GUI scale 1) between the paragraphs of a quest description and after its subtitle.",
                "FTB Quests itself uses a full blank line (9) between paragraphs and 7 after the subtitle; a text line is 9.")
            .defineInRange("questParagraphGap", 4, 0, 9);
        b.pop();

        b.comment("Chat safety net (SPEC 18.1)").push("chat");
        CHAT_BLOCKLIST = b.comment("System and mod chat lines whose text matches one of these Java regular expressions are dropped",
                "(player chat is never touched). Each dropped line is logged at INFO.")
            .defineListAllowEmpty("blocklist", ChatBlocklist.DEFAULT_PATTERNS, () -> "", ClientConfig::isString);
        b.pop();

        b.comment("TerraFirmaCraft displays (SPEC 18.6)").push("tfc");
        HIDE_THIRST_BAR = b.comment("Hide TFC's thirst bar (thirst is off, see tfc.disableThirst in firmages-server.toml).")
            .define("hideThirstBar", true);
        HIDE_NUTRITION = b.comment("Hide TFC's nutrition tab in the inventory and the nutrition lines of food tooltips.")
            .define("hideNutrition", true);
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

    private static boolean isString(Object o) {
        return o instanceof String;
    }
}
