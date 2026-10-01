package dev.firmages.core.compat.jade;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.net.ShrineSyncPayload;
import dev.firmages.core.shrine.ConsecratedBlock;
import dev.firmages.core.shrine.OfferingPlinthBlock;
import dev.firmages.core.shrine.OfferingPlinthBlockEntity;
import dev.firmages.core.shrine.ShrineHeartBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * Jade tooltips for the shrine (SPEC §7.6, M6): the heart shows its awakened tiers, whether the next ring stands,
 * whether it is kindled and maintenance mode; a plinth shows its Age, its relic or offering, and a lent relic; a
 * consecrated block (M7, SPEC §17) its role, the Age of its ring, its original material and the maintenance state.
 * Everything comes from the synced blockstates, the plinth block entity and the {@code firmages:shrine_sync}
 * payload, so no server data provider is needed. Jade finds this class by
 * its annotation and loads it only when Jade is installed.
 */
@WailaPlugin
public final class FirmagesJadePlugin implements IWailaPlugin {
    static final ResourceLocation HEART = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "shrine_heart");
    static final ResourceLocation PLINTH = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "offering_plinth");
    static final ResourceLocation CONSECRATED = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "consecrated");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Heart.INSTANCE, ShrineHeartBlock.class);
        registration.registerBlockComponent(Plinth.INSTANCE, OfferingPlinthBlock.class);
        registration.registerBlockComponent(Consecrated.INSTANCE, ConsecratedBlock.class);
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
            if (s.getValue(ShrineHeartBlock.MAINTENANCE)) {
                tooltip.add(Component.translatable("firmages.jade.maintenance", ShrineSyncPayload.clientSecondsLeft()).withStyle(ChatFormatting.YELLOW));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return HEART;
        }
    }

    /**
     * A consecrated block: its role, the Age of its ring (the accent), the original material (synced by
     * {@code firmages:shrine_sync}) and whether maintenance mode lets it be broken now.
     */
    enum Consecrated implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlock() instanceof ConsecratedBlock cb)) return;
            int accent = accessor.getBlockState().getValue(ConsecratedBlock.ACCENT);
            tooltip.add(Component.translatable("firmages.jade.consecrated.role", Component.translatable("firmages.role." + cb.role().id()))
                .withStyle(ChatFormatting.GOLD));
            if (accent + 1 < AgeId.all().size()) {
                tooltip.add(Component.translatable("firmages.jade.consecrated.ring",
                    Component.translatable("firmages.age." + AgeId.all().get(accent + 1).id() + ".name")));
            }
            ShrineSyncPayload.clientOriginal(accessor.getLevel().dimension().location().toString(), accessor.getPosition())
                .map(ResourceLocation::tryParse).flatMap(BuiltInRegistries.BLOCK::getOptional)
                .ifPresentOrElse(b -> tooltip.add(Component.translatable("firmages.jade.consecrated.original", b.getName())),
                    () -> tooltip.add(Component.translatable("firmages.jade.consecrated.no_original").withStyle(ChatFormatting.GRAY)));
            tooltip.add(ShrineSyncPayload.clientMaintenance()
                ? Component.translatable("firmages.jade.maintenance", ShrineSyncPayload.clientSecondsLeft()).withStyle(ChatFormatting.YELLOW)
                : Component.translatable("firmages.jade.consecrated.locked").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public ResourceLocation getUid() {
            return CONSECRATED;
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
