package dev.firmages.core.compat.sgjourney;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.povstalec.sgjourney.common.block_entities.StructureGenEntity;
import net.povstalec.sgjourney.common.block_entities.dhd.AbstractDHDEntity;
import net.povstalec.sgjourney.common.block_entities.stargate.AbstractStargateEntity;
import net.povstalec.sgjourney.common.config.CommonStargateConfig;
import net.povstalec.sgjourney.common.data.StargateNetwork;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

/**
 * Stargate Journey 0.6.49 coupling of The Origin's return gate [verified, javap]. Its pedestal templates store
 * the gate and DHD with {@code generation_step} SETUP; worldgen then calls {@code generateInStructure} (SETUP to
 * READY) and the chunk load calls {@code generate()}, which adds the gate to the network and gives the DHD its
 * energy core. A template placed by code skips both, so {@link #finishPlacement} does them. Only loaded when
 * Stargate Journey is present.
 */
public final class SgjGates {
    private SgjGates() {}

    /** @return how many gates and DHDs inside {@code box} were generated */
    public static int finishPlacement(ServerLevel level, BoundingBox box) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockEntity be = level.getBlockEntity(p);
            if (!(be instanceof StructureGenEntity sg) || sg.generationStep() == StructureGenEntity.Step.GENERATED) continue;
            if (sg.generationStep() == StructureGenEntity.Step.SETUP) sg.generateInStructure(level, level.random);
            if (be instanceof AbstractDHDEntity dhd) {
                dhd.generate();
                n++;
            } else if (be instanceof AbstractStargateEntity<?> gate) {
                gate.generate();
                n++;
            }
        }
        return n;
    }

    /** True if the network knows a stargate in {@code dimension}. */
    public static boolean inNetwork(MinecraftServer server, ResourceKey<Level> dimension) {
        return !StargateNetwork.get(server).getStargatesInDimension(dimension).isEmpty();
    }

    /**
     * The dial costs Stargate Journey reads from {@code sgjourney-common.toml} (0.6.51 [verified, javap:
     * {@code CommonStargateConfig}]): {system-wide cost, interstellar cost, interstellar draw per tick, max open seconds}.
     * The pack sets them in {@code config/sgjourney-common.toml} (SPEC §16.2a).
     */
    public static long[] costs() {
        return new long[] {CommonStargateConfig.system_wide_connection_energy_cost.get(), CommonStargateConfig.interstellar_connection_energy_cost.get(),
            CommonStargateConfig.interstellar_connection_energy_draw.get(), CommonStargateConfig.max_wormhole_open_time.get()};
    }

    /** FE the Origin's return gate keeps for one trip home: the interstellar cost plus the draw of a full open time. */
    public static long homeTrip() {
        long[] c = costs();
        return c[1] + c[2] * c[3] * 20L;
    }

    /** The gate at {@code pos}: {stored FE, capacity}, or null when there is none. */
    public static long[] energy(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AbstractStargateEntity<?> gate
            ? new long[] {gate.getEnergyStorage().getTrueEnergyStored(), gate.getEnergyStorage().getTrueMaxEnergyStored()} : null;
    }

    /**
     * Keeps the Origin's return gate charged for one trip home ({@link #homeTrip}), so a dial out of The Origin never
     * strands anyone; the dial into The Origin pays the full interstellar cost at home. Returns the FE added (0 when
     * the gate holds enough or is missing).
     */
    public static long keepCharged(Level level, BlockPos gatePos) {
        if (!(level.getBlockEntity(gatePos) instanceof AbstractStargateEntity<?> gate)) return 0;
        long need = Math.min(homeTrip(), gate.getEnergyStorage().getTrueMaxEnergyStored());
        long have = gate.getEnergyStorage().getTrueEnergyStored();
        if (have >= need) return 0;
        gate.getEnergyStorage().setEnergy(need);
        return need - have;
    }

    /**
     * Takes the energy core SGJ's {@code generate()} put into the DHD at {@code dhdPos} (a fusion core of 2.2 to 6.6
     * G FE): The Origin's gate is charged by {@link #keepCharged} instead, and the core would be free power at home.
     */
    public static boolean removeEnergyCore(Level level, BlockPos dhdPos) {
        if (!(level.getBlockEntity(dhdPos) instanceof AbstractDHDEntity dhd)) return false;
        boolean had = !dhd.energyItemHandler.getStackInSlot(0).isEmpty();
        dhd.energyItemHandler.setStackInSlot(0, ItemStack.EMPTY);
        return had;
    }

    /** Lines for {@code /firmages origin gate}: the dial costs and the Origin's return gate. */
    public static java.util.List<String> statusLines(MinecraftServer server, @org.jetbrains.annotations.Nullable Level origin, BlockPos gatePos) {
        long[] c = costs();
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("Dial costs: system-wide " + c[0] + " FE, interstellar " + c[1] + " FE (The Origin), interstellar wormhole " + c[2]
            + " FE/t for up to " + c[3] + " s");
        long[] e = origin == null || !origin.isLoaded(gatePos) ? null : energy(origin, gatePos);
        out.add(e == null ? "The Origin's return gate: not loaded" : "The Origin's return gate: " + e[0] + " of " + e[1] + " FE (keeps " + homeTrip() + ")");
        return out;
    }

    /**
     * {@code /firmages origin gate trial <pos>} (debug.allowSimulate): places SGJ's pedestal gate at {@code pos}, dials
     * {@code address} for real with one FE less than the interstellar cost, then with the cost, and reports the
     * feedback and the FE the gate spent; the connection is closed again.
     */
    public static java.util.List<String> trial(ServerLevel level, BlockPos pos, ResourceLocation template, BlockPos gateOffset, int[] address,
                                               @org.jetbrains.annotations.Nullable ServerLevel target, BlockPos targetGate) {
        java.util.List<String> out = new java.util.ArrayList<>();
        // the far gate's chunk is loaded, so closing the connection finds both gates (SGJ logs an ERROR otherwise)
        if (target != null) target.getChunk(targetGate);
        var t = level.getStructureManager().get(template).orElse(null);
        if (t == null) {
            out.add("no template " + template);
            return out;
        }
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
        t.placeInWorld(level, pos, pos, settings, level.random, 2);
        finishPlacement(level, t.getBoundingBox(settings, pos));
        if (!(level.getBlockEntity(pos.offset(gateOffset)) instanceof AbstractStargateEntity<?> gate)) {
            out.add("no gate at " + pos.offset(gateOffset).toShortString());
            return out;
        }
        long cost = costs()[1];
        var addr = new net.povstalec.sgjourney.common.sgjourney.Address.Immutable(address);
        gate.getEnergyStorage().setEnergy(cost - 1);
        var low = gate.engageStargate(addr, false, net.povstalec.sgjourney.common.sgjourney.Dialing.Action.EXECUTE);
        out.add("with " + (cost - 1) + " FE: " + low.feedback() + ", gate holds " + gate.getEnergyStorage().getTrueEnergyStored() + " FE");
        if (low.feedback().name().startsWith("CONNECTION_ESTABLISHED")) gate.resetStargate(net.povstalec.sgjourney.common.sgjourney.StargateInfo.Feedback.CONNECTION_ENDED_BY_DISCONNECT);
        gate.getEnergyStorage().setEnergy(cost);
        var full = gate.engageStargate(addr, false, net.povstalec.sgjourney.common.sgjourney.Dialing.Action.EXECUTE);
        out.add("with " + cost + " FE: " + full.feedback() + ", gate holds " + gate.getEnergyStorage().getTrueEnergyStored() + " FE afterwards");
        if (full.feedback().name().startsWith("CONNECTION_ESTABLISHED")) gate.resetStargate(net.povstalec.sgjourney.common.sgjourney.StargateInfo.Feedback.CONNECTION_ENDED_BY_DISCONNECT);
        return out;
    }
}
