package dev.firmages.core.client;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.net.ReloadStatePayload;
import dev.firmages.core.shrine.ShrineRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client-only wiring (renderers, ceremony ticks, sky tint, reload hint). Loaded only on the physical client. */
public final class FirmagesClient {
    private FirmagesClient() {}

    @EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ShrineRegistry.SHRINE_HEART_BE.get(), ShrineHeartRenderer::new);
            event.registerBlockEntityRenderer(ShrineRegistry.OFFERING_PLINTH_BE.get(), OfferingPlinthRenderer::new);
        }
    }

    @EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private static int hintTicks;

        private GameEvents() {}

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            CeremonyPlayer.tick();
            Minecraft mc = Minecraft.getInstance();
            // The reload hint stays on the actionbar for the whole freeze (the server cannot send anything then).
            if (ReloadStatePayload.clientRunning() && mc.player != null) {
                if (hintTicks++ % 40 == 0) {
                    mc.player.displayClientMessage(Component.translatableWithFallback("firmages.reload.running", "The world realigns..."), true);
                }
            } else {
                hintTicks = 0;
            }
        }

        @SubscribeEvent
        public static void fog(ViewportEvent.ComputeFogColor event) {
            SkyEffects.onFogColor(event);
        }

        @SubscribeEvent
        public static void stage(RenderLevelStageEvent event) {
            SkyEffects.onRenderStage(event);
        }

        @SubscribeEvent
        public static void music(SelectMusicEvent event) {
            if (CeremonyPlayer.running() && dev.firmages.core.config.ClientConfig.STOP_MUSIC.get()) event.setCanceled(true);
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            CeremonyPlayer.reset();
            PreviewBridge.clear();
            dev.firmages.core.net.ShrineSyncPayload.clientReset();
        }
    }
}
