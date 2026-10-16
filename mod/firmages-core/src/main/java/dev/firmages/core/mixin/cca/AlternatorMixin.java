package dev.firmages.core.mixin.cca;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.firmages.core.balance.FuelShare;
import dev.firmages.core.compat.create.AlternatorFuel;
import dev.firmages.core.config.ServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Felix H1A (SPEC §34.1): the Crafts and Additions alternator gives FE only for the fuelled share of its kinetic
 * network. Create Crafts and Additions 1.7.2 [verified, javap]: {@code AlternatorBlockEntity#tick} (server side) feeds
 * {@code internalProduceEnergy(getEnergyProductionRate((int) getSpeed()))}; the static rate is scaled here by
 * {@link AlternatorFuel#share}. The goggle line keeps the unscaled rate. Targets by name, so the build needs no Crafts
 * and Additions jar; applied only when it is loaded.
 */
@Pseudo
@Mixin(targets = "com.mrh0.createaddition.blocks.alternator.AlternatorBlockEntity", remap = false)
public abstract class AlternatorMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
        target = "Lcom/mrh0/createaddition/blocks/alternator/AlternatorBlockEntity;getEnergyProductionRate(I)I"))
    private int firmages$fuelledOnly(int rate) {
        if (!ServerConfig.alternatorFuelOnly()) return rate;
        return FuelShare.scaledRate(rate, AlternatorFuel.share((KineticBlockEntity) (Object) this));
    }
}
