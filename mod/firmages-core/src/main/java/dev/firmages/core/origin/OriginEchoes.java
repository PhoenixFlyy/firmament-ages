package dev.firmages.core.origin;

import com.mojang.math.Transformation;
import dev.firmages.core.FirmagesCore;
import dev.firmages.core.mixin.DisplayAccessor;
import dev.firmages.core.shrine.ShrineDataLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Origin echoes (SPEC §16.2, arena v2): above each of the nine islands the signature item of its Age
 * ({@code firmages_shrine/offerings.json}) floats and turns slowly, a vanilla item display entity (full bright, 1.5
 * times the size, tag {@code firmages.origin_echo}). Each echo has a fixed UUID, so it exists once: it is spawned
 * when its island's entities are loaded and it is missing, and its item follows the offerings file. While players are
 * in The Origin the server turns each one by a twelfth every second; the client interpolates in between.
 */
public final class OriginEchoes {
    /** Scoreboard tag of the echo displays. */
    public static final String TAG = "firmages.origin_echo";
    /** Ticks between two rotation steps (also the interpolation time). */
    static final int STEP_TICKS = 20;
    private static final float SCALE = 1.5F;
    private static final int STEPS_PER_TURN = 12;

    private OriginEchoes() {}

    /** The echo display of island {@code i} always has this UUID. */
    public static UUID uuid(int i) {
        return UUID.nameUUIDFromBytes(("firmages:origin_echo/" + i).getBytes(StandardCharsets.UTF_8));
    }

    /** Where the echo of island {@code i} floats. */
    public static BlockPos position(int i) {
        return OriginArena.islandTop(i).above(2);
    }

    /** The signature item of Age {@code i}, if the offerings name a registered item. */
    public static Optional<Item> signature(int i) {
        return Optional.ofNullable(ShrineDataLoader.current().offerings().get("age_" + i)).map(ResourceLocation::tryParse)
            .flatMap(BuiltInRegistries.ITEM::getOptional).filter(it -> it != Items.AIR);
    }

    /** Spawns missing echoes and turns the others one step. @return the echoes present after the tick */
    public static int tick(ServerLevel level) {
        int present = 0;
        long step = level.getGameTime() / STEP_TICKS;
        for (int i = 0; i < OriginArena.ISLANDS; i++) {
            Optional<Item> item = signature(i);
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
                a.firmages$setViewRange(4.0F); // the islands lie 45 to 75 blocks out
                a.firmages$setTransformation(transformation(step + i));
                d.getSlot(0).set(new ItemStack(item.get()));
                if (!level.addFreshEntity(d)) continue;
                FirmagesCore.LOGGER.info("The Origin: echo of age_{} ({}) placed at {}", i, BuiltInRegistries.ITEM.getKey(item.get()), at.toShortString());
                e = d;
            } else if (e instanceof Display.ItemDisplay d) {
                if (!d.getSlot(0).get().is(item.get())) d.getSlot(0).set(new ItemStack(item.get()));
                DisplayAccessor a = (DisplayAccessor) d;
                a.firmages$setTransformation(transformation(step + i));
                a.firmages$setInterpolationDuration(STEP_TICKS);
                a.firmages$setInterpolationDelay(0);
            }
            if (e instanceof Display.ItemDisplay) present++;
        }
        return present;
    }

    /** Scale 1.5, turned by {@code step} twelfths of a full turn around the vertical axis. */
    static Transformation transformation(long step) {
        float angle = (float) (Math.floorMod(step, STEPS_PER_TURN) * (2 * Math.PI / STEPS_PER_TURN));
        return new Transformation(new Vector3f(), new Quaternionf().rotationY(angle), new Vector3f(SCALE, SCALE, SCALE), new Quaternionf());
    }
}
