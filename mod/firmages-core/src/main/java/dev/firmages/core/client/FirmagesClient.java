package dev.firmages.core.client;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.boss.BossRegistry;
import dev.firmages.core.client.boss.PrimordialRenderer;
import dev.firmages.core.client.boss.StarFallRenderer;
import dev.firmages.core.net.ReloadStatePayload;
import dev.firmages.core.shrine.ShrineRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
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
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

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
            event.registerEntityRenderer(BossRegistry.PRIMORDIAL.get(), PrimordialRenderer::new);
            event.registerEntityRenderer(BossRegistry.STAR_FALL.get(), StarFallRenderer::new);
            event.registerEntityRenderer(BossRegistry.THROWN_SHARD.get(), ThrownItemRenderer::new);
            event.registerBlockEntityRenderer(dev.firmages.core.crown.CrownRegistry.STAR_ANVIL_BE.get(), CrownRenderer::new);
            event.registerBlockEntityRenderer(dev.firmages.core.crown.CrownRegistry.LENS_MOUNT_BE.get(), LensMountRenderer::new);
            event.registerBlockEntityRenderer(dev.firmages.core.wayshrine.WayshrineRegistry.CORE_BE.get(), WayshrineCoreRenderer::new);
            event.registerBlockEntityRenderer(dev.firmages.core.launch.LaunchRegistry.PROBE_CRADLE_BE.get(), LaunchRenderer::new);
        }

        /** Lens items: layer 1 (the glass) in the colour of the lens's shrine tier (SPEC §27.4), white until synced. */
        @SubscribeEvent
        public static void itemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
            for (var lens : dev.firmages.core.crown.CrownRegistry.LENSES) {
                event.register((stack, tint) -> tint != 1 || !(stack.getItem() instanceof dev.firmages.core.crown.LensItem l) ? -1
                    : 0xFF000000 | dev.firmages.core.net.ShrineSyncPayload.clientTierColor(l.index()).orElse(0xFFFFFF), lens.get());
            }
        }
    }

    @EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private static int hintTicks;

        private GameEvents() {}

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            CeremonyPlayer.tick();
            ShrineAmbience.tick(Minecraft.getInstance());
            LaunchFx.tick(Minecraft.getInstance()); // the launch trail, trench jets and landing (SPEC §31.7)
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
            SkyStreaks.onRenderStage(event); // the falling star (SPEC §25.9)
            ArkClient.onRenderStage(event); // the Archipelago's beams at the altar (SPEC §31.8)
            LaunchFx.onRenderStage(event); // launches beyond the loaded chunks (SPEC §31.7)
        }

        @SubscribeEvent
        public static void music(SelectMusicEvent event) {
            AgeMusic.onSelect(event); // ceremony silence, Firmament night silence, per-Age music (SPEC §23)
        }

        @SubscribeEvent
        public static void sound(PlaySoundEvent event) {
            AgeMusic.onPlaySound(event);
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            CeremonyPlayer.reset();
            PreviewBridge.clear();
            dev.firmages.core.net.ShrineSyncPayload.clientReset();
            AgeMusic.reset();
            SkyStreaks.reset();
            ArkClient.reset();
            LaunchFx.reset();
        }
    }
}
