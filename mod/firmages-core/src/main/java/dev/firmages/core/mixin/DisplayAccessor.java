package dev.firmages.core.mixin;

import com.mojang.math.Transformation;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The private setters of vanilla display entities, for the floating relics of The Origin's islands
 * ({@code origin/OriginEchoes}): the same fields {@code /summon} and {@code /data merge} set from NBT.
 */
@Mixin(Display.class)
public interface DisplayAccessor {
    @Invoker("setTransformation")
    void firmages$setTransformation(Transformation transformation);

    @Invoker("setTransformationInterpolationDuration")
    void firmages$setInterpolationDuration(int ticks);

    /** Sent even when unchanged, so each call starts a new interpolation on the client. */
    @Invoker("setTransformationInterpolationDelay")
    void firmages$setInterpolationDelay(int ticks);

    @Invoker("setBillboardConstraints")
    void firmages$setBillboard(Display.BillboardConstraints billboard);

    @Invoker("setBrightnessOverride")
    void firmages$setBrightness(Brightness brightness);

    @Invoker("setViewRange")
    void firmages$setViewRange(float range);
}
