package dev.firmages.core.compat.jade;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeId;
import dev.firmages.core.client.immersion.ImmersionClient;
import dev.firmages.core.net.ShrineSyncPayload;
import dev.firmages.core.proof.TallyChuteBlock;
import dev.firmages.core.proof.TallyChuteBlockEntity;
import dev.firmages.core.shrine.ConsecratedBlock;
import dev.firmages.core.shrine.OfferingPlinthBlock;
import dev.firmages.core.shrine.OfferingPlinthBlockEntity;
import dev.firmages.core.shrine.ShrineHeartBlock;
import dev.firmages.core.text.Numbers;
import dev.firmages.core.wayshrine.WayshrineCoreBlock;
import dev.firmages.core.wayshrine.WayshrineCoreBlockEntity;
import dev.firmages.core.wayshrine.WayshrinePhase;
import dev.firmages.core.wayshrine.WayshrineService;
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
 * consecrated block (M7, SPEC §17) its role, the Age of its ring, its original material and the maintenance state; a
 * Tally Chute (SPEC §22.1) the team's count of the last item it passed and its own total.
 * Everything comes from the synced blockstates, the plinth block entity and the {@code firmages:shrine_sync}
 * payload, so no server data provider is needed. Before {@code ui.jadeFromAge} the overlay is suppressed entirely
 * (SPEC §18.3). Jade finds this class by
 * its annotation and loads it only when Jade is installed.
 */
@WailaPlugin
public final class FirmagesJadePlugin implements IWailaPlugin {
    static final ResourceLocation HEART = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "shrine_heart");
    static final ResourceLocation PLINTH = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "offering_plinth");
    static final ResourceLocation CONSECRATED = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "consecrated");
    static final ResourceLocation TALLY = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "tally_chute");
    static final ResourceLocation WAYSHRINE = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "wayshrine_core");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Heart.INSTANCE, ShrineHeartBlock.class);
        registration.registerBlockComponent(Plinth.INSTANCE, OfferingPlinthBlock.class);
        registration.registerBlockComponent(Consecrated.INSTANCE, ConsecratedBlock.class);
        registration.registerBlockComponent(Tally.INSTANCE, TallyChuteBlock.class);
        registration.registerBlockComponent(Wayshrine.INSTANCE, WayshrineCoreBlock.class);
        // SPEC §18.3: no overlay before ui.jadeFromAge. Jade skips the tooltip when a callback returns null
        // (WailaTickHandler [verified, javap]); the callback also covers entities and Jade's own toggle key.
        // Priority 10000: callbacks run by ascending priority (Jade PriorityStore), so this one has the last word.
        registration.addRayTraceCallback(10000, (hit, accessor, original) -> ImmersionClient.blockInfoAllowed() ? accessor : null);
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

    /** A Tally Chute: the team's total of the last item it counted, its own total, or that it has no owner. */
    enum Tally implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof TallyChuteBlockEntity be)) return;
            if (be.owner() == null) {
                tooltip.add(Component.translatable("firmages.jade.tally.no_owner").withStyle(ChatFormatting.GRAY));
                return;
            }
            ResourceLocation last = ResourceLocation.tryParse(be.lastItem());
            if (last != null && BuiltInRegistries.ITEM.containsKey(last)) {
                tooltip.add(Component.translatable("firmages.jade.tally.counted", be.lastTeamCount(), BuiltInRegistries.ITEM.get(last).getDescription())
                    .withStyle(ChatFormatting.GOLD));
            } else {
                tooltip.add(Component.translatable("firmages.jade.tally.empty").withStyle(ChatFormatting.GRAY));
            }
            if (be.passed() > 0) tooltip.add(Component.translatable("firmages.jade.tally.passed", be.passed()).withStyle(ChatFormatting.GRAY));
        }

        @Override
        public ResourceLocation getUid() {
            return TALLY;
        }
    }

    /**
     * A Wayshrine core (SPEC-himmel §28.8), from its synced block entity and block state: its name, how many blocks
     * are missing, the offering on it, whether it carries the blessings and in which radius, or that it is broken.
     */
    enum Wayshrine implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof WayshrineCoreBlockEntity be)) return;
            BlockState s = accessor.getBlockState();
            tooltip.add(Component.translatable("firmages.jade.wayshrine.name", WayshrineService.typeName(be.type())).withStyle(ChatFormatting.GOLD));
            WayshrinePhase phase = s.getValue(WayshrineCoreBlock.PHASE);
            if (phase == WayshrinePhase.LIT) {
                tooltip.add(s.getValue(WayshrineCoreBlock.INTACT)
                    ? Component.translatable("firmages.jade.wayshrine.lit", Numbers.words(be.radius())).withStyle(ChatFormatting.GREEN)
                    : Component.translatable("firmages.jade.wayshrine.broken").withStyle(ChatFormatting.GRAY));
                return;
            }
            if (phase == WayshrinePhase.RUIN && be.missing() > 0) {
                tooltip.add(Component.translatable(be.missing() == 1 ? "firmages.jade.wayshrine.ruined.one" : "firmages.jade.wayshrine.ruined",
                    Numbers.words(be.missing())).withStyle(ChatFormatting.GRAY));
            }
            if (!be.offering().isEmpty()) tooltip.add(Component.translatable("firmages.jade.wayshrine.offering", be.offering().getHoverName()));
        }

        @Override
        public ResourceLocation getUid() {
            return WAYSHRINE;
        }
    }
}
