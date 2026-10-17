package dev.firmages.core.gametest;

import dev.firmages.core.FirmagesCore;
import dev.firmages.core.command.LocateCommands;
import dev.firmages.core.command.LocateRules;
import dev.firmages.core.launch.LaunchRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Level G tests of round G2: the creative pick-block of every firmages-core block (SPEC §28.5, §30.4) and
 * {@code /firmages locate} (SPEC §10.3) on a loaded Launch Complex control.
 */
@GameTestHolder(FirmagesCore.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = FirmagesCore.MOD_ID)
public final class PickBlockGameTests {
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        event.register(PickBlockGameTests.class);
    }

    /**
     * Every block of the firmages namespace (in the GameTest run only firmages-core's: KubeJS runs no startup script there) answers the
     * pick-block path of the client ({@code BlockState.getCloneItemStack(HitResult, LevelReader, BlockPos, Player)}) with
     * an item of its own, for every state.
     */
    @GameTest(template = "empty", batch = "firmages_1_pick_block")
    public static void everyCoreBlockHasAPickItem(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        Player player = h.makeMockPlayer(GameType.CREATIVE);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        List<String> empty = new ArrayList<>();
        int blocks = 0;
        for (Block b : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
            if (!FirmagesCore.MOD_ID.equals(id.getNamespace())) continue;
            blocks++;
            for (BlockState st : b.getStateDefinition().getPossibleStates()) {
                ItemStack s = st.getCloneItemStack(hit, h.getLevel(), pos, player);
                if (s.isEmpty()) {
                    empty.add(id.toString());
                    break;
                }
            }
        }
        FirmagesCore.LOGGER.info("GameTest everyCoreBlockHasAPickItem: {} firmages blocks, without pick item: {}", blocks, empty);
        h.assertTrue(blocks >= 30, "only " + blocks + " firmages blocks registered");
        h.assertTrue(empty.isEmpty(), "blocks without a pick-block item: " + empty);
        h.succeed();
    }

    /** A loaded Launch Complex control is listed at distance 0 with its teleport target; 2,048 blocks away it is not. */
    @GameTest(template = "empty", batch = "firmages_1_pick_block", timeoutTicks = 60)
    public static void locateListsALoadedLaunchControl(GameTestHelper h) {
        BlockPos rel = new BlockPos(1, 1, 1);
        h.setBlock(rel, LaunchRegistry.LAUNCH_CONTROL.get().defaultBlockState());
        BlockPos at = h.absolutePos(rel);
        h.succeedWhen(() -> {
            List<LocateCommands.Found> near = LocateCommands.find(h.getLevel(), Vec3.atCenterOf(at), 16);
            h.assertTrue(near.stream().anyMatch(f -> f.hit().kind().equals("launch") && f.hit().x() == at.getX() && f.hit().z() == at.getZ()
                && f.hit().y() == at.getY() + 1 && f.hit().distance() == 0), "the control is listed at distance 0, found " + near.stream().map(LocateCommands.Found::hit).toList());
            Vec3 far = Vec3.atCenterOf(at).add(LocateRules.MAX_RADIUS + 100, 0, 0);
            List<LocateCommands.Found> away = LocateCommands.find(h.getLevel(), far, LocateRules.MAX_RADIUS);
            h.assertTrue(away.stream().noneMatch(f -> f.hit().x() == at.getX() && f.hit().z() == at.getZ()), "outside the radius it is not listed");
        });
    }
}
