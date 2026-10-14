package dev.firmages.core.client.immersion;

import com.mojang.blaze3d.platform.InputConstants;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.compat.ftbquests.FtbQuestsScreens;
import dev.firmages.core.compat.progressivestages.LockedTooltip;
import dev.firmages.core.compat.tfc.TfcClient;
import dev.firmages.core.config.ClientConfig;
import dev.firmages.core.hardship.CaveDwellerGate;
import dev.firmages.core.immersion.AgeStatus;
import dev.firmages.core.immersion.TooltipTrim;
import dev.firmages.core.immersion.ViewerGate;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ToastAddEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client half of the immersion features (SPEC §18): silent joins (blocked screens and toasts), no advancements screen
 * or mod list, EMI, Jade and the TFC inventory tabs gated by Age, the HUD Age indicator, the first-join welcome and the
 * TFC display tweaks, the Ars Nouveau HUD by Age and the locked-item tooltip trim; and the Cave Dweller sound mute
 * (SPEC §19); EMI's recipe screens stay shut while the viewer is locked, the EMI audit after each EMI reload and the
 * skipped ProgressiveStages EMI reload while an Age reload is pending (SPEC §21), the Ponder plugin (§21.6).
 * Physical client only.
 */
public final class ImmersionClient {
    /** Hold to show the Age indicator. H: free as a plain key in the pack (Left Alt is Create's tool menu/toolbelt, Railways and Copycats; dev/keybinds.md). */
    public static final KeyMapping SHOW_AGE = new KeyMapping("key.firmages.show_age", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.firmages");
    private static final ResourceLocation TFC_THIRST_LAYER = ResourceLocation.fromNamespaceAndPath("tfc", "thirst");
    /** Ars Nouveau 5.13.3 {@code ClientHandler} [verified, javap]: the mana bar shows whenever mana is below the maximum
     *  (from the first spawn on) and has no off switch; the spell HUD shows with a spell item in hand. */
    private static final Set<ResourceLocation> ARS_HUD_LAYERS = Set.of(
        ResourceLocation.fromNamespaceAndPath("ars_nouveau", "mana_hud"), ResourceLocation.fromNamespaceAndPath("ars_nouveau", "spell_hud"));
    /** Ticks after the first status before the welcome title, and from the title to the welcome screen. */
    private static final int WELCOME_TITLE_DELAY = 40;
    private static final int WELCOME_SCREEN_DELAY = 100;
    /** EMI's recipe and recipe-tree screens. {@code EmiApi.displayRecipes} and the JEI API through JEMI open them without
     *  checking {@code EmiConfig.enabled} [verified, javap, EMI 1.1.24], e.g. from a recipe click area of another GUI. */
    static final Set<String> EMI_SCREENS = Set.of("dev.emi.emi.screen.RecipeScreen", "dev.emi.emi.screen.BoMScreen");

    private static boolean keyHeld;
    private static long keyReleasedAt = Long.MIN_VALUE;
    private static Boolean viewerWasAllowed;
    private static boolean openQuestsNextTick;
    private static int welcomePhase; // 0 none, 1 waiting for the title, 2 waiting for the screen
    private static int welcomeTicks;
    private static Set<String> modNames;
    private static Boolean lockedTooltips;
    private static Boolean emiLoaded;

    private ImmersionClient() {}

    static boolean ageKeyHeld() {
        return keyHeld;
    }

    static long ageKeyReleasedAt() {
        return keyReleasedAt;
    }

    /** {@code firmages:age_status} arrived. */
    public static void onStatus(AgeStatus status) {
        EmiReloadGate.onStatus(status);
        ClientAgeStatus.receive(status);
    }

    /** {@code firmages:welcome} arrived: title first, then the screen. */
    public static void onWelcome() {
        welcomePhase = 1;
        welcomeTicks = 0;
    }

    /** Jade's ray-trace callback asks this (SPEC §18.3); before the first status, nothing is shown. */
    public static boolean blockInfoAllowed() {
        return ClientAgeStatus.known() && ClientAgeStatus.get().blockInfo();
    }

    @EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(SHOW_AGE);
        }

        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent event) {
            event.registerBelow(VanillaGuiLayers.BOSS_OVERLAY, AgeIndicatorLayer.ID, AgeIndicatorLayer::render);
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            if (ModList.get().isLoaded("ponder")) event.enqueueWork(PonderHooks::register);
        }
    }

    @EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {}

        @SubscribeEvent
        public static void toast(ToastAddEvent event) {
            if (!ClientConfig.get(ClientConfig.HIDE_TOASTS, true)) return;
            if (ClientConfig.get(ClientConfig.BLOCKED_TOASTS, ClientConfig.DEFAULT_BLOCKED_TOASTS).contains(event.getToast().getClass().getName())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void screenOpening(ScreenEvent.Opening event) {
            if (event.getNewScreen() == null) return;
            String name = event.getNewScreen().getClass().getName();
            if (ClientConfig.get(ClientConfig.BLOCKED_SCREENS, ClientConfig.DEFAULT_BLOCKED_SCREENS).contains(name)) {
                FirmagesCore.LOGGER.info("Screen {} blocked (ui.blockedScreens)", name);
                event.setCanceled(true);
            } else if (EMI_SCREENS.contains(name) && !viewerOpen()) {
                event.setCanceled(true); // the recipe viewer is not open in this Age (§21.3)
            } else if (event.getNewScreen() instanceof AdvancementsScreen && ClientConfig.get(ClientConfig.REDIRECT_ADVANCEMENTS, true)) {
                event.setCanceled(true);
                openQuestsNextTick = FtbQuestsScreens.available(); // not inside setScreen
            }
        }

        @SubscribeEvent(priority = EventPriority.LOW) // after TFC adds its inventory tabs
        public static void screenInit(ScreenEvent.Init.Post event) {
            if (ClientConfig.get(ClientConfig.HIDE_MENU_BUTTONS, true)) MenuButtons.apply(event);
            if (TfcClient.available()) TfcClient.applyTabs(event, ClientAgeStatus.get().tabs()); // before the first status: Dawn
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            boolean down = mc.player != null && SHOW_AGE.isDown();
            if (keyHeld && !down) keyReleasedAt = Util.getMillis();
            keyHeld = down;
            if (mc.player == null) return;
            gateViewer();
            if (emiLoaded()) EmiAuditHooks.tick();
            if (openQuestsNextTick) {
                openQuestsNextTick = false;
                FtbQuestsScreens.open();
            }
            tickWelcome(mc);
        }

        /** SPEC §19: once the status says the Cave Dweller no longer hunts, every sound of its namespace is dropped
         *  (its timed cave noises arrive by its own packet even though no dweller can exist). */
        @SubscribeEvent
        public static void sound(PlaySoundEvent event) {
            SoundInstance s = event.getSound();
            if (s != null && CaveDwellerGate.SOUND_NAMESPACE.equals(s.getLocation().getNamespace())
                && ClientAgeStatus.known() && !ClientAgeStatus.get().caveDweller()) {
                event.setSound(null);
            }
        }

        @SubscribeEvent
        public static void layer(RenderGuiLayerEvent.Pre event) {
            if (event.getName().equals(TFC_THIRST_LAYER) && ClientConfig.get(ClientConfig.HIDE_THIRST_BAR, true)) event.setCanceled(true);
            else if (ARS_HUD_LAYERS.contains(event.getName()) && !AgeStatus.reached(ClientAgeStatus.get().age(), ClientConfig.manaHudFromAge())) {
                event.setCanceled(true); // before the first status: Dawn
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST) // after every mod added its lines
        public static void tooltip(ItemTooltipEvent event) {
            List<Component> lines = event.getToolTip();
            if (lines.size() < 2) return;
            if (ClientConfig.get(ClientConfig.TRIM_LOCKED_TOOLTIPS, true) && lockedTooltips() && LockedTooltip.trim(event.getItemStack(), lines)) return;
            boolean nutrition = ClientConfig.get(ClientConfig.HIDE_NUTRITION, true);
            boolean modName = ClientConfig.get(ClientConfig.HIDE_MOD_NAME_TOOLTIPS, true);
            boolean sizeWeight = ClientConfig.get(ClientConfig.HIDE_SIZE_WEIGHT, true);
            if (!nutrition && !modName && !sizeWeight) return;
            Set<String> names = modName ? modNames() : Set.of();
            for (int i = lines.size() - 1; i >= 1; i--) { // line 0 is the item name
                Component line = lines.get(i);
                if ((nutrition && mentionsNutrition(line))
                    || (sizeWeight && TooltipTrim.isSizeWeight(translationKeys(line, new ArrayList<>())))
                    || (modName && names.contains(ChatFormatting.stripFormatting(line.getString()).trim()))) {
                    lines.remove(i);
                }
            }
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientAgeStatus.reset();
            viewerWasAllowed = null;
            openQuestsNextTick = false;
            welcomePhase = 0;
        }
    }

    /** The recipe viewer is open: by the Age (before the first status the world counts as Dawn), for operators and in creative. */
    private static boolean viewerOpen() {
        LocalPlayer p = Minecraft.getInstance().player;
        return ViewerGate.open(ClientAgeStatus.known() && ClientAgeStatus.get().recipeViewer(),
            p != null && p.hasPermissions(2), p != null && p.isCreative());
    }

    /** EMI follows the Age (ViewerGate). */
    private static void gateViewer() {
        if (!EmiBridge.available()) return;
        boolean allowed = viewerOpen();
        Boolean set = ViewerGate.target(allowed, viewerWasAllowed, EmiBridge.isEnabled());
        if (set != null) {
            EmiBridge.setEnabled(set);
            if (viewerWasAllowed == null || viewerWasAllowed != allowed) FirmagesCore.LOGGER.info("EMI {} (Age {})", set ? "shown" : "hidden", ClientAgeStatus.get().age());
        }
        viewerWasAllowed = allowed;
    }

    private static void tickWelcome(Minecraft mc) {
        if (welcomePhase == 0 || mc.level == null) return;
        if (mc.screen != null) return; // wait for the terrain screen and anything else
        welcomeTicks++;
        if (welcomePhase == 1 && welcomeTicks >= WELCOME_TITLE_DELAY) {
            mc.gui.setTimes(10, 70, 20);
            mc.gui.setSubtitle(Component.translatable("firmages.welcome.subtitle").withStyle(ChatFormatting.GRAY));
            mc.gui.setTitle(Component.translatable("firmages.welcome.title").withStyle(ChatFormatting.GOLD));
            welcomePhase = 2;
            welcomeTicks = 0;
        } else if (welcomePhase == 2 && welcomeTicks >= WELCOME_SCREEN_DELAY) {
            welcomePhase = 0;
            mc.setScreen(new WelcomeScreen());
        }
    }

    private static boolean mentionsNutrition(FormattedText text) {
        if (!(text instanceof Component c)) return false;
        if (c.getContents() instanceof TranslatableContents tc) {
            if (ViewerGate.isNutritionKey(tc.getKey())) return true;
            for (Object arg : tc.getArgs()) if (arg instanceof Component a && mentionsNutrition(a)) return true;
        }
        for (Component sibling : c.getSiblings()) if (mentionsNutrition(sibling)) return true;
        return false;
    }

    /** Every translation key in {@code c}, its arguments and siblings. */
    private static List<String> translationKeys(Component c, List<String> out) {
        if (c.getContents() instanceof TranslatableContents tc) {
            out.add(tc.getKey());
            for (Object arg : tc.getArgs()) if (arg instanceof Component a) translationKeys(a, out);
        }
        for (Component sibling : c.getSiblings()) translationKeys(sibling, out);
        return out;
    }

    private static boolean emiLoaded() {
        if (emiLoaded == null) emiLoaded = ModList.get().isLoaded("emi");
        return emiLoaded;
    }

    /** EMI classes stay behind these holders, so this class loads without EMI, ProgressiveStages or Ponder. */
    private static final class EmiAuditHooks {
        static void tick() {
            dev.firmages.core.compat.emi.EmiAudit.tick();
        }
    }

    private static final class PonderHooks {
        static void register() {
            dev.firmages.core.compat.ponder.FirmagesPonderPlugin.register();
        }
    }

    private static boolean lockedTooltips() {
        if (lockedTooltips == null) lockedTooltips = ModList.get().isLoaded("progressivestages"); // LockedTooltip needs PS classes
        return lockedTooltips;
    }

    /** Display names of every loaded mod plus "Minecraft", for lines that only name a mod. */
    private static Set<String> modNames() {
        if (modNames == null) {
            Set<String> names = new HashSet<>();
            names.add("Minecraft");
            for (IModInfo info : ModList.get().getMods()) names.add(info.getDisplayName());
            modNames = Set.copyOf(names);
        }
        return modNames;
    }
}
