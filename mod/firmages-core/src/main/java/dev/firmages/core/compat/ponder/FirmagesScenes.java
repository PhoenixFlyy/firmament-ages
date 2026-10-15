package dev.firmages.core.compat.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The pack's own Ponder storyboards (SPEC §22.5). Schematics: {@code assets/firmages/ponder/<scene>.nbt}, written by
 * {@code dev/gen_ponder_schematics.py} (the coordinates below follow its plans). Texts: {@code firmages.ponder.<scene>.*}
 * in {@code en_us.json}, numbered in the order of the {@code text(...)} calls. Ponder looks from the north-west, so
 * pointers aim at north and west faces. Create's scene builder adds the pulley animation and kinetic speeds.
 */
final class FirmagesScenes {
    private FirmagesScenes() {}

    /** "Silo Hatch" on {@code ad_astra:launch_pad}: shaft, pad, two leaves sliding apart, the rocket rising through. */
    static void siloHatch(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("silo_hatch", "A Silo Hatch over the Launch Pad");
        scene.configureBasePlate(0, 0, 14);
        scene.scaleSceneView(0.55f);
        scene.setSceneOffsetY(-1);
        scene.showBasePlate();
        scene.idle(10);

        // the earth behind the shaft (east and south); the near sides stay cut away
        Selection back = util.select().fromTo(10, 1, 0, 13, 6, 13).add(util.select().fromTo(0, 1, 10, 9, 6, 13));
        scene.world().showSection(back, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80).text("Sink a shaft into the ground, eight deep at least. Shore its walls; the rock remembers every cut.")
            .pointAt(util.vector().blockSurface(util.grid().at(10, 4, 6), Direction.WEST)).placeNearTarget().attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(util.select().fromTo(5, 1, 5, 7, 1, 7), Direction.DOWN);
        scene.idle(10);
        ElementLink<WorldSectionElement> rocket = scene.world().showIndependentSection(util.select().fromTo(6, 2, 6, 6, 6, 6), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(60).text("Set the launch pad at the bottom, and the rocket on it.")
            .pointAt(util.vector().blockSurface(util.grid().at(6, 1, 6), Direction.UP)).placeNearTarget();
        scene.idle(70);

        Selection deck = util.select().fromTo(0, 7, 0, 13, 7, 13).substract(util.select().fromTo(3, 7, 3, 9, 7, 9));
        scene.world().showSection(deck, Direction.DOWN);
        scene.idle(10);
        ElementLink<WorldSectionElement> west = scene.world().showIndependentSection(util.select().fromTo(3, 7, 3, 5, 7, 9), Direction.DOWN);
        ElementLink<WorldSectionElement> east = scene.world().showIndependentSection(util.select().fromTo(6, 7, 3, 9, 7, 9), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90).text("Glue two leaves over the shaft. Each rides a gantry carriage and slides into a pocket under the rim.")
            .pointAt(util.vector().topOf(util.grid().at(4, 7, 6))).placeNearTarget().attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(50).text("One redstone link opens the hatch.")
            .pointAt(util.vector().topOf(util.grid().at(6, 7, 6))).placeNearTarget();
        scene.idle(20);
        scene.world().moveSection(west, util.vector().of(-3, 0, 0), 40);
        scene.world().moveSection(east, util.vector().of(4, 0, 0), 40);
        scene.idle(50);

        scene.overlay().showText(70).text("A delayed link wakes the pad. Ten heartbeats later the rocket rises.")
            .pointAt(util.vector().topOf(util.grid().at(6, 6, 6))).placeNearTarget().attachKeyFrame();
        scene.idle(80);
        scene.world().moveSection(rocket, util.vector().of(0, 16, 0), 60);
        scene.idle(50);
        scene.world().hideIndependentSection(rocket, Direction.UP);
        scene.idle(15);
        scene.overlay().showText(60).text("Ride it up through your own sky.")
            .pointAt(util.vector().topOf(util.grid().at(6, 7, 6))).placeNearTarget();
        scene.idle(70);

        scene.world().moveSection(west, util.vector().of(3, 0, 0), 40);
        scene.world().moveSection(east, util.vector().of(-4, 0, 0), 40);
        scene.idle(50);
        scene.markAsFinished();
    }

    /** "Portcullis" on {@code create:rope_pulley}: a grate drops from the wall slot, a lever on the gearshift lifts it. */
    static void portcullis(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("portcullis", "A Portcullis on a Rope Pulley");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.85f);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos pulley = util.grid().at(4, 7, 4);
        BlockPos gearshift = util.grid().at(7, 7, 4);
        Selection grate = util.select().fromTo(3, 4, 4, 5, 6, 4);
        Selection wall = util.select().fromTo(0, 1, 3, 8, 6, 5).substract(grate);
        Selection driveIn = util.select().position(8, 7, 4).add(util.select().position(gearshift));
        Selection driveOut = util.select().fromTo(4, 7, 4, 6, 7, 4);

        scene.world().showSection(wall, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(60).text("Leave a slot in the wall above your gate, as tall as the gate.")
            .pointAt(util.vector().blockSurface(util.grid().at(4, 5, 3), Direction.NORTH)).placeNearTarget().attachKeyFrame();
        scene.idle(70);

        ElementLink<WorldSectionElement> bars = scene.world().showIndependentSection(grate, Direction.DOWN);
        scene.world().showSection(util.select().fromTo(4, 7, 4, 8, 7, 4).add(util.select().position(7, 7, 3)), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70).text("Glue an iron grate together and hang it in the slot under a rope pulley.")
            .pointAt(util.vector().topOf(pulley)).placeNearTarget();
        scene.idle(80);

        scene.world().setKineticSpeed(driveIn, 32);
        scene.world().setKineticSpeed(driveOut, 32);
        scene.world().movePulley(pulley, 3, 40);
        scene.world().moveSection(bars, util.vector().of(0, -3, 0), 40);
        scene.idle(45);
        scene.world().setKineticSpeed(driveIn, 0);
        scene.world().setKineticSpeed(driveOut, 0);
        scene.overlay().showText(60).text("Turn the pulley one way and the grate drops. The gate is shut.")
            .pointAt(util.vector().blockSurface(util.grid().at(4, 2, 4), Direction.NORTH)).placeNearTarget().attachKeyFrame();
        scene.idle(70);

        scene.world().toggleRedstonePower(util.select().fromTo(7, 7, 3, 7, 7, 4));
        scene.effects().indicateRedstone(util.grid().at(7, 7, 3));
        scene.idle(10);
        scene.world().setKineticSpeed(driveIn, 32);
        scene.world().setKineticSpeed(driveOut, -32);
        scene.world().movePulley(pulley, -3, 40);
        scene.world().moveSection(bars, util.vector().of(0, 3, 0), 40);
        scene.idle(45);
        scene.world().setKineticSpeed(driveIn, 0);
        scene.world().setKineticSpeed(driveOut, 0);
        scene.overlay().showText(70).text("Throw the lever on the gearshift and the pulley turns back. The grate rises into the wall.")
            .pointAt(util.vector().blockSurface(util.grid().at(7, 7, 3), Direction.NORTH)).placeNearTarget();
        scene.idle(80);

        scene.overlay().showText(60).text("Pair two redstone links and the gate obeys from across the yard.")
            .pointAt(util.vector().topOf(gearshift)).placeNearTarget();
        scene.idle(70);
        scene.markAsFinished();
    }
}
