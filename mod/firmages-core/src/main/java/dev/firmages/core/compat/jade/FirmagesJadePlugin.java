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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.IBoxElement;
import snownee.jade.api.ui.IElement;

/**
 * Jade tooltips for the shrine (SPEC §7.6, M6): the heart shows its awakened tiers, whether the next ring stands,
 * whether it is kindled, maintenance mode, the proof of work of the current offering and the FE it drew (SPEC §35, §36,
 * from the heart's synced block entity); a plinth shows its Age, its relic or offering, and a lent relic; a
 * consecrated block (M7, SPEC §17) its role, the Age of its ring, its original material and the maintenance state; a
 * Tally Chute (SPEC §22.1) the team's count of the last item it passed and its own total.
 * Everything comes from the synced blockstates, the plinth block entity and the {@code firmages:shrine_sync}
 * payload, so no server data provider is needed. Before {@code ui.jadeFromAge} the overlay is suppressed entirely
 * (SPEC §18.3); after it, a block of an Age the player lacks shows only "Unknown" and a sigil ({@link Unknown}, SPEC
 * §21.14). Jade finds this class by
 * its annotation and loads it only when Jade is installed.
 */
@WailaPlugin
public final class FirmagesJadePlugin implements IWailaPlugin {
    static final ResourceLocation HEART = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "shrine_heart");
    static final ResourceLocation PLINTH = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "offering_plinth");
    static final ResourceLocation CONSECRATED = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "consecrated");
    static final ResourceLocation TALLY = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "tally_chute");
    static final ResourceLocation STARFALL_CORE = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "starfall_core");
    static final ResourceLocation ANVIL = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "star_anvil");
    static final ResourceLocation MOUNT = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "lens_mount");
    static final ResourceLocation WAYSHRINE = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "wayshrine_core");
    static final ResourceLocation ANCHOR = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "sky_anchor");
    static final ResourceLocation LAUNCH_CONTROL = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "launch_control");
    static final ResourceLocation LAUNCH_PORT = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "launch_port");
    static final ResourceLocation AURORA_NET = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "aurora_net");
    static final ResourceLocation STORM_ROD = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "storm_rod");
    static final ResourceLocation ORE_PROCESSOR = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "ore_processor");
    static final ResourceLocation ARCANE_CATALYST = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "arcane_catalyst");
    static final ResourceLocation UNKNOWN_BLOCK = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, "unknown_block");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Heart.INSTANCE, ShrineHeartBlock.class);
        registration.registerBlockComponent(Plinth.INSTANCE, OfferingPlinthBlock.class);
        registration.registerBlockComponent(Consecrated.INSTANCE, ConsecratedBlock.class);
        registration.registerBlockComponent(Tally.INSTANCE, TallyChuteBlock.class);
        registration.registerBlockComponent(StarfallCore.INSTANCE, dev.firmages.core.sky.StarfallCoreBlock.class);
        registration.registerBlockComponent(Crown.ANVIL, dev.firmages.core.crown.StarAnvilBlock.class);
        registration.registerBlockComponent(Crown.MOUNT, dev.firmages.core.crown.LensMountBlock.class);
        registration.registerBlockComponent(Wayshrine.INSTANCE, WayshrineCoreBlock.class);
        registration.registerBlockComponent(Anchor.INSTANCE, dev.firmages.core.anchor.SkyAnchorBlock.class);
        registration.registerBlockComponent(Launch.CONTROL, dev.firmages.core.launch.LaunchControlBlock.class);
        registration.registerBlockComponent(Launch.PORT, dev.firmages.core.launch.LaunchPortBlock.class);
        registration.registerBlockComponent(SkyHarvest.NET, dev.firmages.core.sky.AuroraNetBlock.class);
        registration.registerBlockComponent(SkyHarvest.ROD, dev.firmages.core.sky.StormRodBlock.class);
        registration.registerBlockComponent(Machines.PROCESSOR, dev.firmages.core.machine.OreProcessorBlock.class);
        registration.registerBlockComponent(Machines.CATALYST, dev.firmages.core.machine.ArcaneCatalystBlock.class);
        // SPEC §18.3: no overlay before ui.jadeFromAge. Jade skips the tooltip when a callback returns null
        // (WailaTickHandler [verified, javap]); the callback also covers entities and Jade's own toggle key.
        // Priority 10000: callbacks run by ascending priority (Jade PriorityStore), so this one has the last word.
        registration.addRayTraceCallback(10000, (hit, accessor, original) -> ImmersionClient.blockInfoAllowed() ? accessor : null);
        // SPEC §21.14: a block of an Age the player lacks is "Unknown". The icon goes through Jade's own icon slot
        // (the last icon provider by priority wins); the name replaces everything Jade and every plugin collected, in
        // the tooltip-collected callback, which runs after all providers (WailaTickHandler.tickClient [verified, javap]).
        registration.registerBlockIcon(Unknown.INSTANCE, Block.class);
        registration.addTooltipCollectedCallback(10000, Unknown::collected);
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
            // SPEC §35, §36: the proof of work of the current offering and the FE the heart draws (synced block entity)
            if (accessor.getBlockEntity() instanceof dev.firmages.core.shrine.ShrineHeartBlockEntity be) {
                if (be.proofNeed() > 0) {
                    ResourceLocation id = ResourceLocation.tryParse(be.proofItem());
                    Component item = id == null ? Component.literal(be.proofItem()) : BuiltInRegistries.ITEM.get(id).getDescription();
                    String need = String.format(java.util.Locale.ROOT, "%,d", be.proofNeed());
                    tooltip.add(be.proofHave() >= be.proofNeed()
                        ? Component.translatable("firmages.jade.heart.proof_met", need, item).withStyle(ChatFormatting.GREEN)
                        : Component.translatable("firmages.jade.heart.proof", String.format(java.util.Locale.ROOT, "%,d", be.proofHave()), need, item)
                            .withStyle(ChatFormatting.YELLOW));
                }
                if (be.drawNeed() > 0) {
                    tooltip.add(Component.translatable("firmages.jade.heart.drawn", String.format(java.util.Locale.ROOT, "%,d", be.drawn()),
                        String.format(java.util.Locale.ROOT, "%,d", be.drawNeed()))
                        .withStyle(be.drawn() >= be.drawNeed() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                }
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

    /** A Tally Chute: the team's total of the last item it counted, what the current proof still wants, its own total, or that it has no owner. */
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
            ResourceLocation want = ResourceLocation.tryParse(be.wantItem());
            if (be.want() > 0 && want != null && BuiltInRegistries.ITEM.containsKey(want)) {
                tooltip.add(Component.translatable("firmages.jade.tally.wants", be.want(), BuiltInRegistries.ITEM.get(want).getDescription())
                    .withStyle(ChatFormatting.YELLOW));
            } else {
                tooltip.add(Component.translatable("firmages.jade.tally.wants_nothing").withStyle(ChatFormatting.GRAY));
            }
            if (be.passed() > 0) tooltip.add(Component.translatable("firmages.jade.tally.passed", be.passed()).withStyle(ChatFormatting.GRAY));
        }

        @Override
        public ResourceLocation getUid() {
            return TALLY;
        }
    }

    /** A starfall core (SPEC §25.4): "Glowing: cools in 1 day 6 h" from the synced {@code coolsAt}, or "Cold". */
    enum StarfallCore implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof dev.firmages.core.sky.StarfallCoreBlockEntity be)) return;
            long left = be.coolsAt() - accessor.getLevel().getGameTime();
            if (left <= 0) {
                tooltip.add(Component.translatable("firmages.jade.starfall_core.cold").withStyle(ChatFormatting.GRAY));
                return;
            }
            int[] dh = dev.firmages.core.sky.StarfallRules.daysHours(left);
            Component time = dh[0] > 0
                ? Component.translatable(dh[0] == 1 ? "firmages.jade.starfall_core.day_hours" : "firmages.jade.starfall_core.days_hours", dh[0], dh[1])
                : Component.translatable("firmages.jade.starfall_core.hours", dh[1]);
            tooltip.add(Component.translatable("firmages.jade.starfall_core.glowing", time).withStyle(ChatFormatting.GOLD));
        }

        @Override
        public ResourceLocation getUid() {
            return STARFALL_CORE;
        }
    }

    /**
     * The Lens Crown (SPEC §27.11): the anvil names the pending recipe's chord and what it does ("Forging Starlit Iron,
     * 45 %") or why not ("The sky is hidden", "Swing in: Bronze Lens"); a mount names its lens and whether it is in.
     * Everything comes from the synced block entities.
     */
    enum Crown implements IBlockComponentProvider {
        ANVIL, MOUNT;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (this == ANVIL && accessor.getBlockEntity() instanceof dev.firmages.core.crown.StarAnvilBlockEntity be) {
                if (!be.chord().isEmpty()) tooltip.add(dev.firmages.core.crown.CrownText.chordLine(be.chord()).withStyle(ChatFormatting.GOLD));
                tooltip.add(dev.firmages.core.crown.CrownText.state(be).withStyle(be.reason().forging() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            } else if (this == MOUNT && accessor.getBlockEntity() instanceof dev.firmages.core.crown.LensMountBlockEntity be) {
                tooltip.add(dev.firmages.core.crown.CrownText.mount(be));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return this == ANVIL ? FirmagesJadePlugin.ANVIL : FirmagesJadePlugin.MOUNT;
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

    /**
     * A Sky Anchor (SPEC-himmel §29.6), from its synced block entity: "Sky Anchor: four shards", then why it is quiet
     * ("Quiet until the Space Age", "Quiet: the shrine is broken") or the blessings in force and their radius.
     */
    enum Anchor implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof dev.firmages.core.anchor.SkyAnchorBlockEntity be)) return;
            int n = dev.firmages.core.anchor.AnchorRules.count(be.tiers());
            tooltip.add(Component.translatable(n == 0 ? "firmages.jade.anchor.none" : n == 1 ? "firmages.jade.anchor.one" : "firmages.jade.anchor.shards",
                Numbers.words(n)).withStyle(ChatFormatting.GOLD));
            switch (be.status()) {
                case BEFORE_AGE -> tooltip.add(Component.translatable("firmages.jade.anchor.before_age").withStyle(ChatFormatting.GRAY));
                case BROKEN -> tooltip.add(Component.translatable("firmages.jade.anchor.broken").withStyle(ChatFormatting.GRAY));
                case SLEEPING -> tooltip.add(Component.translatable("firmages.jade.anchor.sleeping").withStyle(ChatFormatting.GRAY));
                case EMPTY -> { }
                case ACTIVE -> {
                    tooltip.add(Component.translatable("firmages.jade.anchor.active", Numbers.words(be.radius())).withStyle(ChatFormatting.GREEN));
                    for (String key : be.blessings()) tooltip.add(Component.literal("  ").append(Component.translatable(key)));
                }
            }
        }

        @Override
        public ResourceLocation getUid() {
            return ANCHOR;
        }
    }

    /**
     * The Launch Complex (SPEC §31.5): the control names the phase ("In flight, 45 %"), why it waits ("The sky is
     * closed above the cradle"), the throttle and the fuel; a port says whether it feeds a control. From the synced
     * block entities.
     */
    enum Launch implements IBlockComponentProvider {
        CONTROL, PORT;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (this == CONTROL && accessor.getBlockEntity() instanceof dev.firmages.core.launch.LaunchControlBlockEntity be) {
                var phase = be.phase();
                var wait = be.waitReason();
                if (phase != dev.firmages.core.launch.LaunchRules.Phase.IDLE) {
                    tooltip.add(dev.firmages.core.launch.LaunchText.phaseLine(phase, be.countdownLeft(), be.percent()).withStyle(ChatFormatting.GOLD));
                }
                if (phase == dev.firmages.core.launch.LaunchRules.Phase.IDLE || wait != dev.firmages.core.launch.LaunchRules.Wait.NONE) {
                    tooltip.add(dev.firmages.core.launch.LaunchText.waitLine(wait, be.missing(), be.launchMb())
                        .withStyle(wait == dev.firmages.core.launch.LaunchRules.Wait.NONE ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                }
                tooltip.add(dev.firmages.core.launch.LaunchText.throttle(be.step(), be.steps(), be.redstoneThrottle()));
                var f = be.fluid();
                tooltip.add(f.isEmpty() ? Component.translatable("firmages.jade.launch.fuel_empty").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("firmages.jade.launch.fuel", f.getHoverName(), f.getAmount()));
            } else if (this == PORT && accessor.getBlockEntity() instanceof dev.firmages.core.launch.LaunchPortBlockEntity be) {
                tooltip.add(Component.translatable(be.controlPos() != null ? "firmages.jade.launch.port_linked" : "firmages.jade.launch.port_unlinked")
                    .withStyle(be.controlPos() != null ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return this == CONTROL ? LAUNCH_CONTROL : LAUNCH_PORT;
        }
    }

    /**
     * The Sky Harvest (SPEC §26.4, §26.5), from the synced block entities: the net says whether it catches the aurora
     * or why not, the rod whether the storm fuses its sand or what it waits for (the upper half reads the basin below).
     * The tank and the slots are Jade's own lines.
     */
    enum SkyHarvest implements IBlockComponentProvider {
        NET, ROD;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (this == NET && accessor.getBlockEntity() instanceof dev.firmages.core.sky.AuroraNetBlockEntity be) {
                var r = be.reason();
                Component line = switch (r) {
                    case CATCHING -> Component.translatable("firmages.jade.aurora_net.catching");
                    case NO_AURORA -> Component.translatable("firmages.jade.aurora_net.none");
                    case TOO_LOW -> Component.translatable("firmages.jade.aurora_net.too_low", Numbers.words(be.minY()));
                    case NO_SKY -> Component.translatable("firmages.jade.aurora_net.no_sky");
                };
                tooltip.add(line.copy().withStyle(r == dev.firmages.core.sky.AuroraNetBlockEntity.Reason.CATCHING ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            } else if (this == ROD) {
                var pos = dev.firmages.core.sky.StormRodBlock.basin(accessor.getBlockState(), accessor.getPosition());
                if (!(accessor.getLevel().getBlockEntity(pos) instanceof dev.firmages.core.sky.StormRodBlockEntity be)) return;
                var r = be.reason();
                tooltip.add(Component.translatable("firmages.jade.storm_rod." + r.name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(r == dev.firmages.core.sky.StormRodBlockEntity.Reason.STORM ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return this == NET ? AURORA_NET : STORM_ROD;
        }
    }

    /**
     * The pack's machines (SPEC §40.7, §41.6), from their synced block entities: the Ore Processor's state ("Processing:
     * 21 Iron Dust, 45 %" or what it waits for), its FE and its three tanks; the Arcane Catalyst's state and FE.
     */
    enum Machines implements IBlockComponentProvider {
        PROCESSOR, CATALYST;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (this == PROCESSOR && accessor.getBlockEntity() instanceof dev.firmages.core.machine.OreProcessorBlockEntity be) {
                tooltip.add(dev.firmages.core.machine.MachineText.processorState(be));
                dev.firmages.core.machine.MachineText.processorTanks(be).forEach(tooltip::add);
            } else if (this == CATALYST && accessor.getBlockEntity() instanceof dev.firmages.core.machine.ArcaneCatalystBlockEntity be) {
                tooltip.add(dev.firmages.core.machine.MachineText.catalystState(be));
                tooltip.add(Component.translatable("firmages.processor.energy", dev.firmages.core.machine.MachineText.n(be.energy()),
                    dev.firmages.core.machine.MachineText.n(dev.firmages.core.machine.ArcaneCatalystBlockEntity.CAPACITY)));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return this == PROCESSOR ? ORE_PROCESSOR : ARCANE_CATALYST;
        }
    }

    /**
     * A block of an Age the player does not have (SPEC §21.14, {@link dev.firmages.core.immersion.UnknownBlocks}): the
     * icon becomes the sigil and the whole tooltip one line, "Unknown", tagged as Jade's object name. Required, so
     * Jade's plugin settings cannot switch it off.
     */
    enum Unknown implements IBlockComponentProvider {
        INSTANCE;

        static boolean unknown(BlockAccessor accessor) {
            Item item = accessor.isFakeBlock() ? accessor.getFakeBlock().getItem() : accessor.getBlock().asItem();
            var player = net.minecraft.client.Minecraft.getInstance().player;
            return dev.firmages.core.immersion.UnknownBlocks.unknown(dev.firmages.core.client.immersion.ClientAgeView.current(),
                item == Items.AIR ? null : item, BuiltInRegistries.BLOCK.getKey(accessor.getBlock()).toString(), player != null && player.isCreative());
        }

        static void collected(IBoxElement box, Accessor<?> accessor) {
            if (!(accessor instanceof BlockAccessor block) || !unknown(block)) return;
            ITooltip tooltip = box.getTooltip();
            tooltip.clear();
            tooltip.add(IThemeHelper.get().title(Component.translatable(dev.firmages.core.immersion.UnknownBlocks.NAME_KEY)), JadeIds.CORE_OBJECT_NAME);
            box.clearBoxProgress();
        }

        @Override
        public IElement getIcon(BlockAccessor accessor, IPluginConfig config, IElement currentIcon) {
            return unknown(accessor) ? new Sigil() : currentIcon;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {}

        @Override
        public ResourceLocation getUid() {
            return UNKNOWN_BLOCK;
        }

        @Override
        public int getDefaultPriority() {
            return 1_000_000; // icon providers run by ascending priority and the last one wins
        }

        @Override
        public boolean isRequired() {
            return true;
        }
    }

    /** The unknown sigil, 16x16 in an 18x18 slot like Jade's item icon. */
    static final class Sigil extends snownee.jade.api.ui.Element {
        static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(FirmagesCore.MOD_ID, dev.firmages.core.immersion.UnknownBlocks.SIGIL);

        @Override
        public net.minecraft.world.phys.Vec2 getSize() {
            return new net.minecraft.world.phys.Vec2(18, 18);
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics g, float x, float y, float maxX, float maxY) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.blit(TEXTURE, Math.round(x) + 1, Math.round(y) + 1, 0, 0, 16, 16, 16, 16);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        }
    }
}
