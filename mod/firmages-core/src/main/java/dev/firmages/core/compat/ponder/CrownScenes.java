package dev.firmages.core.compat.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.firmages.core.crown.LensMountBlock;
import dev.firmages.core.crown.LensMountBlockEntity;
import dev.firmages.core.crown.StarAnvilBlock;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The Lens Crown's Ponder storyboards (SPEC §27.11): "The Lens Crown" and "A Chord Library" on the Star Anvil, "Chords"
 * on the Lens Mount. Schematics {@code assets/firmages/ponder/lens_crown|chords|chord_library.nbt} from
 * {@code dev/gen_ponder_schematics.py}: the crown's bottom layer at y1 from x/z 1 to 9, the anvil at (5,1,5), the
 * Stone, Bronze and Iron Lens on the north sockets (3|5|7, 2, 1). Texts: {@code firmages.ponder.<scene>.*}.
 */
final class CrownScenes {
    private static final BlockPos ANVIL = new BlockPos(5, 1, 5);
    private static final BlockPos[] MOUNTS = {new BlockPos(3, 2, 1), new BlockPos(5, 2, 1), new BlockPos(7, 2, 1)};
    private static final BlockPos[] LINES = {new BlockPos(3, 2, 0), new BlockPos(5, 2, 0), new BlockPos(7, 2, 0)};

    private CrownScenes() {}

    private static Selection pillarsAndCaps(SceneBuildingUtil util) {
        Selection s = util.select().position(1, 2, 1);
        for (int[] c : new int[][] {{1, 1}, {1, 9}, {9, 1}, {9, 9}}) {
            s = s.add(util.select().fromTo(c[0], 2, c[1], c[0], 3, c[1]));
        }
        return s;
    }

    private static Selection sockets(SceneBuildingUtil util) {
        return util.select().fromTo(1, 2, 1, 9, 2, 9).substract(pillarsAndCaps(util));
    }

    /** Swings the lens of mount {@code m} in (or out): the swing value, the redstone flag and the blockstate. */
    private static void swing(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos m, boolean in) {
        scene.world().modifyBlockEntityNBT(util.select().position(m), LensMountBlockEntity.class, tag -> {
            tag.putInt("swing", in ? 10 : 0);
            tag.putBoolean("powered", in);
        });
        scene.world().modifyBlock(m, s -> s.hasProperty(LensMountBlock.IN) ? s.setValue(LensMountBlock.IN, in) : s, false);
    }

    /** "The Lens Crown" on {@code firmages:star_anvil}: ring, anvil, pillars and caps, sockets, the ghost. */
    static void lensCrown(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("lens_crown", "The Lens Crown");
        scene.configureBasePlate(0, 0, 11);
        scene.scaleSceneView(0.7f);
        scene.showBasePlate();
        scene.idle(10);

        Selection ring = util.select().fromTo(1, 1, 1, 9, 1, 9).substract(util.select().position(ANVIL));
        scene.world().showSection(ring, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80).text("Lay a ring of smooth rock or rock bricks, 9 by 9. The middle stays free for pipes and hoppers.")
            .pointAt(util.vector().topOf(util.grid().at(1, 1, 5))).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(util.select().position(ANVIL), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(60).text("The Star Anvil sits in its centre.")
            .pointAt(util.vector().topOf(ANVIL)).placeNearTarget();
        scene.idle(70);

        scene.world().showSection(pillarsAndCaps(util), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70).text("Raise a pillar on each corner and cap it with storm glass.")
            .pointAt(util.vector().blockSurface(util.grid().at(1, 3, 1), Direction.WEST)).placeNearTarget().attachKeyFrame();
        scene.idle(80);

        scene.world().showSection(sockets(util), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90).text("Twelve sockets stand on the ring, three to a side. Rock will do; a lens mount there carries a lens.")
            .pointAt(util.vector().blockSurface(MOUNTS[1], Direction.NORTH)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80).text("Right-click the anvil with an empty hand. The crown shows what it still lacks.")
            .pointAt(util.vector().topOf(ANVIL)).placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    /** "Chords" on {@code firmages:lens_mount}: set a lens, redstone swings it in, the exact chord forges, a roof pauses, night speeds up. */
    static void chords(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("chords", "Chords");
        scene.configureBasePlate(0, 0, 11);
        scene.scaleSceneView(0.7f);
        scene.showBasePlate();
        Selection crown = util.select().fromTo(1, 1, 1, 9, 3, 9);
        scene.world().showSection(crown, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(80).text("Right-click a mount with a lens to set it. Sneak and right-click with an empty hand to take it back.")
            .pointAt(util.vector().blockSurface(MOUNTS[0], Direction.NORTH)).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(util.select().position(LINES[0]), Direction.SOUTH);
        scene.idle(10);
        scene.world().toggleRedstonePower(util.select().position(LINES[0]));
        scene.effects().indicateRedstone(LINES[0]);
        swing(scene, util, MOUNTS[0], true);
        scene.overlay().showText(80).text("While a mount gets a redstone signal, its lens swings in toward the anvil. It counts once it is fully in.")
            .pointAt(util.vector().blockSurface(LINES[0], Direction.NORTH)).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        for (int i = 1; i < 3; i++) {
            scene.world().showSection(util.select().position(LINES[i]), Direction.SOUTH);
            scene.idle(5);
            scene.world().toggleRedstonePower(util.select().position(LINES[i]));
            scene.effects().indicateRedstone(LINES[i]);
            swing(scene, util, MOUNTS[i], true);
        }
        scene.world().modifyBlock(ANVIL, s -> s.hasProperty(StarAnvilBlock.ACTIVE) ? s.setValue(StarAnvilBlock.ACTIVE, true) : s, false);
        scene.overlay().showText(90).text("Every forging needs its exact chord. Swing in its lenses and no others; one lens too many makes another chord.")
            .pointAt(util.vector().topOf(ANVIL)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        scene.world().showSection(util.select().fromTo(4, 4, 4, 6, 4, 6), Direction.DOWN);
        scene.world().modifyBlock(ANVIL, s -> s.hasProperty(StarAnvilBlock.ACTIVE) ? s.setValue(StarAnvilBlock.ACTIVE, false) : s, false);
        scene.idle(10);
        scene.overlay().showText(80).text("The anvil forges only under the open sky. A roof pauses it, and nothing is lost.")
            .pointAt(util.vector().topOf(util.grid().at(5, 4, 5))).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        scene.world().hideSection(util.select().fromTo(4, 4, 4, 6, 4, 6), Direction.UP);
        scene.world().modifyBlock(ANVIL, s -> s.hasProperty(StarAnvilBlock.ACTIVE) ? s.setValue(StarAnvilBlock.ACTIVE, true) : s, false);
        scene.idle(10);
        scene.overlay().showText(70).text("Under the night sky it forges twice as fast.")
            .pointAt(util.vector().topOf(ANVIL)).placeNearTarget();
        scene.idle(80);
        scene.markAsFinished();
    }

    /** "A Chord Library" on {@code firmages:star_anvil}: pattern provider, comparator, link decoder, output. */
    static void chordLibrary(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("chord_library", "A Chord Library");
        scene.configureBasePlate(0, 0, 11);
        scene.scaleSceneView(0.7f);
        scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(1, 1, 1, 9, 3, 9).substract(util.select().fromTo(4, 1, 5, 7, 1, 5)), Direction.DOWN);
        scene.idle(20);

        scene.world().showSection(util.select().position(4, 1, 5), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90).text("A pattern provider in blocking mode pushes one set of items and fluid per pattern into the anvil.")
            .pointAt(util.vector().topOf(util.grid().at(4, 1, 5))).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        scene.world().showSection(util.select().fromTo(6, 1, 5, 7, 1, 5), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90).text("A comparator on the anvil gives the signal of the chord the waiting recipe needs.")
            .pointAt(util.vector().topOf(util.grid().at(6, 1, 5))).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        for (BlockPos l : LINES) scene.world().showSection(util.select().position(l), Direction.SOUTH);
        scene.idle(10);
        scene.overlay().showText(90).text("Decode the signal into the mount lines. One redstone link frequency per lens is enough.")
            .pointAt(util.vector().blockSurface(LINES[1], Direction.NORTH)).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        for (BlockPos m : MOUNTS) swing(scene, util, m, true);
        scene.effects().indicateSuccess(ANVIL);
        scene.overlay().showText(80).text("A hopper or an import bus under the anvil takes the result. The library runs on its own.")
            .pointAt(util.vector().blockSurface(util.grid().at(5, 0, 5), Direction.WEST)).placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }
}
