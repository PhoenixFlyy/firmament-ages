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
import net.povstalec.sgjourney.common.data.StargateNetwork;

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
}
