package dev.firmages.core.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.firmages.core.client.immersion.GatedKey;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The key gate on the key mapping itself (SPEC §18.10). Mods read their keys in four ways that the mapping answers:
 * {@code consumeClick} in a tick (Psi, Ad Astra, Theurgy, Occultism), {@code isDown} in a tick or in an
 * {@code InputEvent.Key} handler (Psi, Draconic Evolution's {@code KeyInputHandler}, Create Jetpack's flightlib),
 * {@code matches}/{@code matchesMouse} with the pressed key (FTB Chunks, Supplementaries, AE2 screens) [verified, javap].
 * A gated mapping answers false to all four and drops its pending clicks, so presses made while it was gated do not
 * fire when its Age opens. The flag is set by {@code KeyGateClient}; the vanilla methods are unchanged otherwise
 * (NeoForge 21.1.252 {@code KeyMapping}: {@code isDown} is {@code isDown && isConflictContextAndModifierActive()},
 * {@code consumeClick} counts {@code clickCount} down). Readers of the raw key code ({@code getKey().getValue()}), of
 * NeoForge's {@code isActiveAndMatches} and Mekanism's {@code MekKeyBinding} (overrides {@code isDown}, acts in its
 * {@code setDown} callbacks) are not reached here; the screen gate covers what they open.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements GatedKey {
    @Shadow private int clickCount;
    @Unique private boolean firmages$gated;

    @Override
    public boolean firmages$gated() {
        return firmages$gated;
    }

    @Override
    public void firmages$setGated(boolean gated) {
        firmages$gated = gated;
    }

    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean firmages$isDown(boolean original) {
        return original && !firmages$gated;
    }

    @ModifyReturnValue(method = "consumeClick", at = @At("RETURN"))
    private boolean firmages$consumeClick(boolean original) {
        if (!firmages$gated) return original;
        clickCount = 0;
        return false;
    }

    @ModifyReturnValue(method = "matches", at = @At("RETURN"))
    private boolean firmages$matches(boolean original) {
        return original && !firmages$gated;
    }

    @ModifyReturnValue(method = "matchesMouse", at = @At("RETURN"))
    private boolean firmages$matchesMouse(boolean original) {
        return original && !firmages$gated;
    }
}
