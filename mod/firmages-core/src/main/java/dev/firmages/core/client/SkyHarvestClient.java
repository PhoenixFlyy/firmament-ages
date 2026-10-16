package dev.firmages.core.client;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.sky.HarvestRegistry;
import dev.firmages.core.sky.StormRodBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client side of the Sky Harvest (SPEC §26.7): the Storm Rod's screen, the aurora light textures, and the strike's
 * thunder at reduced volume. There is no aurora renderer: the pack's client draws the northern lights over the cold
 * north already (decision 2026-10-16 in {@code dev/decisions-while-away.md}); the net's {@code catching} glow and Jade
 * carry the state.
 */
public final class SkyHarvestClient {
    private SkyHarvestClient() {}

    static final ResourceLocation STILL = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "block/aurora_light_still");
    static final ResourceLocation FLOW = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "block/aurora_light_flow");

    @EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void screens(RegisterMenuScreensEvent event) {
            event.register(HarvestRegistry.STORM_ROD_MENU.get(), StormRodScreen::new);
        }

        @SubscribeEvent
        public static void fluids(RegisterClientExtensionsEvent event) {
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return STILL;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return FLOW;
                }
            }, HarvestRegistry.AURORA_LIGHT_TYPE.get());
        }
    }

    @EventBusSubscriber(modid = FirmagesCore.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {}

        /**
         * A struck Storm Rod's bolt would thunder across the whole world (vanilla volume 10,000); in rain that is every
         * few minutes per rod. Its thunder is swapped for {@code firmages:storm_rod.strike} (vanilla thunder, heard
         * within about 64 blocks). Natural lightning is untouched.
         */
        @SubscribeEvent
        public static void sound(PlaySoundEvent event) {
            SoundInstance s = event.getSound();
            if (s == null || !SoundEvents.LIGHTNING_BOLT_THUNDER.getLocation().equals(s.getLocation())) return;
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            BlockState tip = level.getBlockState(BlockPos.containing(s.getX(), s.getY() - 0.5, s.getZ()));
            if (!tip.is(HarvestRegistry.STORM_ROD.get()) || tip.getValue(StormRodBlock.HALF) != DoubleBlockHalf.UPPER) return;
            event.setSound(new SimpleSoundInstance(HarvestRegistry.STORM_ROD_STRIKE.get(), SoundSource.WEATHER, 4.0F,
                0.8F + level.random.nextFloat() * 0.2F, level.random, s.getX(), s.getY(), s.getZ()));
        }
    }
}
