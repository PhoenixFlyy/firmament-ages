package dev.firmages.core.compat.jade;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.shrine.OfferingPlinthBlock;
import dev.firmages.core.shrine.OfferingPlinthBlockEntity;
import dev.firmages.core.shrine.ShrineHeartBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade tooltips for the shrine (SPEC §7.6, M6): the heart shows its awakened tiers, whether the next ring stands
 * and whether it is kindled; a plinth shows its Age, its relic or offering, and a lent relic. Everything comes from
 * the synced blockstates and the plinth block entity, so no server data provider is needed. Jade finds this class by
 * its annotation and loads it only when Jade is installed.
 */
@WailaPlugin
public final class FirmagesJadePlugin implements IWailaPlugin {
    static final ResourceLocation HEART = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "shrine_heart");
    static final ResourceLocation PLINTH = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "offering_plinth");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Heart.INSTANCE, ShrineHeartBlock.class);
        registration.registerBlockComponent(Plinth.INSTANCE, OfferingPlinthBlock.class);
    }

    enum Heart implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState s = accessor.getBlockState();
            int awakened = s.getValue(ShrineHeartBlock.AWAKENED);
            tooltip.add(Component.translatable("firmages.jade.heart.awakened", Math.min(awakened, 9)).withStyle(ChatFormatting.GOLD));
            if (awakened < 9) {
                tooltip.add(Component.translatable(s.getValue(ShrineHeartBlock.READY) ? "firmages.jade.heart.ready" : "firmages.jade.heart.not_ready")
                    .withStyle(s.getValue(ShrineHeartBlock.READY) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
            if (!s.getValue(ShrineHeartBlock.LIT)) tooltip.add(Component.translatable("firmages.jade.heart.cold").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public ResourceLocation getUid() {
            return HEART;
        }
    }

    enum Plinth implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof OfferingPlinthBlockEntity be)) return;
            int tier = be.tier();
            if (tier >= 0 && tier + 1 < AgeId.all().size()) {
                tooltip.add(Component.translatable("firmages.jade.plinth.age",
                    Component.translatable("firmages.age." + AgeId.all().get(tier + 1).id() + ".name")).withStyle(ChatFormatting.GOLD));
            }
            if (be.isLent()) {
                tooltip.add(Component.translatable("firmages.jade.plinth.lent").withStyle(ChatFormatting.LIGHT_PURPLE));
            } else if (be.isRelic()) {
                tooltip.add(Component.translatable("firmages.jade.plinth.relic", be.item().getHoverName()));
            } else if (!be.isEmpty()) {
                tooltip.add(Component.translatable("firmages.jade.plinth.offering", be.item().getHoverName()));
            } else {
                tooltip.add(Component.translatable("firmages.jade.plinth.empty").withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return PLINTH;
        }
    }
}
