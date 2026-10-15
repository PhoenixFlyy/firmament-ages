package dev.firmages.core.boss;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Firmament Ward: a consecrated shard's blessing; star-fall passes through whoever carries it (StarFallEntity). */
public final class FirmamentWardEffect extends MobEffect {
    public FirmamentWardEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xF4F0FF);
    }
}
