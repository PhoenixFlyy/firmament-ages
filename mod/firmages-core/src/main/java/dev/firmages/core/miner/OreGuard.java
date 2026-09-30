package dev.firmages.core.miner;

import com.enviouse.progressivestages.common.lock.LockRegistry;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.age.AgeGate;
import dev.firmages.core.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

/**
 * m3 safety net for automated breakers (SPEC §5): Create drill and deployer, Mekanism lasers, the Digital Miner.
 * <ul>
 *   <li>{@code BreakEvent} by a {@link FakePlayer} on a block of a locked {@code age_blocks} Age is canceled
 *       (the Digital Miner then skips the block [verified in research]).</li>
 *   <li>{@code BlockDropsEvent} of such a block without a breaker or with a FakePlayer breaker (Create's
 *       {@code BlockHelper.destroyBlockAs} always posts it [verified in research]) drops the ProgressiveStages
 *       disguise item ({@code drop_as} of the block's ore override) instead, or nothing without an override.</li>
 * </ul>
 * Real players are left to ProgressiveStages' own ore disguise.
 */
public final class OreGuard {
    private OreGuard() {}

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!ServerConfig.minerOreGuard() || !(event.getPlayer() instanceof FakePlayer)) return;
        if (AgeGate.isLocked(event.getState().getBlock())) event.setCanceled(true);
    }

    public static void onDrops(BlockDropsEvent event) {
        if (!ServerConfig.minerOreGuard()) return;
        Entity breaker = event.getBreaker();
        if (breaker != null && !(breaker instanceof FakePlayer)) return;
        Block block = event.getState().getBlock();
        if (!AgeGate.isLocked(block)) return;
        event.getDrops().clear();
        event.setDroppedExperience(0);
        ItemStack disguise = disguiseDrop(block);
        if (!disguise.isEmpty()) {
            BlockPos p = event.getPos();
            event.getDrops().add(new ItemEntity(event.getLevel(), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, disguise));
        }
    }

    /** The PS {@code drop_as} item of the block's ore override, or empty. */
    public static ItemStack disguiseDrop(Block block) {
        try {
            List<LockRegistry.OreOverrideEntry> overrides = LockRegistry.getInstance().getOreOverridesFor(block);
            for (LockRegistry.OreOverrideEntry o : overrides) {
                if (o.dropAs == null) continue;
                Item item = BuiltInRegistries.ITEM.get(o.dropAs);
                if (item != Items.AIR) return new ItemStack(item);
            }
        } catch (RuntimeException | LinkageError e) {
            FirmagesCore.LOGGER.warn("OreGuard: cannot read the ProgressiveStages ore override of {}: {}", BuiltInRegistries.BLOCK.getKey(block), e.toString());
        }
        return ItemStack.EMPTY;
    }
}
