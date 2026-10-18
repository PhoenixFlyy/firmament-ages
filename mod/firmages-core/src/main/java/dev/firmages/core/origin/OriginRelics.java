package dev.firmages.core.origin;

import com.mojang.math.Transformation;
import dev.firmages.core.mixin.DisplayAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * The relics of the nine Ages in the vitrines of the grand arena's arrival platform (round G3, SPEC §16.5): inside
 * each consecrated glass cell the Age's signature item ({@link OriginEchoes#signature}) turns slowly, an item display
 * with a fixed UUID (tag {@code firmages.origin_relic}), so it exists once and nobody can take it (no loot, no frame).
 * Spawned like the echoes when the vitrine's entities are loaded; only in a grand arena.
 */
public final class OriginRelics {
    public static final String TAG = "firmages.origin_relic";
    private static final float SCALE = 0.6F;
    private static final int STEPS_PER_TURN = 12;

    private OriginRelics() {}

    public static UUID uuid(int i) {
        return UUID.nameUUIDFromBytes(("firmages:origin_relic/" + i).getBytes(StandardCharsets.UTF_8));
    }

    /** The glass cell of vitrine {@code i}. */
    public static BlockPos position(int i) {
        int[] v = GrandArenaPlan.vitrine(i);
        return new BlockPos(v[0], v[1], v[2]);
    }

    /** Spawns missing relics and turns the others. @return the relics present */
    public static int tick(ServerLevel level) {
        if (OriginArena.layout() != OriginLayout.GRAND) return 0;
        int present = 0;
        long step = level.getGameTime() / OriginEchoes.STEP_TICKS;
        for (int i = 0; i < OriginArena.ISLANDS; i++) {
            Optional<Item> item = OriginEchoes.signature(i);
            BlockPos at = position(i);
            if (item.isEmpty() || !level.isLoaded(at) || !level.areEntitiesLoaded(ChunkPos.asLong(at))) continue;
            Entity e = level.getEntity(uuid(i));
            if (e == null) {
                Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(level);
                if (d == null) continue;
                d.setUUID(uuid(i));
                d.moveTo(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 0, 0);
                d.addTag(TAG);
                DisplayAccessor a = (DisplayAccessor) d;
                a.firmages$setBillboard(Display.BillboardConstraints.FIXED);
                a.firmages$setBrightness(Brightness.FULL_BRIGHT);
                a.firmages$setTransformation(transformation(step + i));
                d.getSlot(0).set(new ItemStack(item.get()));
                if (!level.addFreshEntity(d)) continue;
                e = d;
            } else if (e instanceof Display.ItemDisplay d) {
                if (!d.getSlot(0).get().is(item.get())) d.getSlot(0).set(new ItemStack(item.get()));
                DisplayAccessor a = (DisplayAccessor) d;
                a.firmages$setTransformation(transformation(step + i));
                a.firmages$setInterpolationDuration(OriginEchoes.STEP_TICKS);
                a.firmages$setInterpolationDelay(0);
            }
            if (e instanceof Display.ItemDisplay) present++;
        }
        return present;
    }

    static Transformation transformation(long step) {
        float angle = (float) (Math.floorMod(step, STEPS_PER_TURN) * (2 * Math.PI / STEPS_PER_TURN));
        return new Transformation(new Vector3f(), new Quaternionf().rotationY(angle), new Vector3f(SCALE, SCALE, SCALE), new Quaternionf());
    }
}
