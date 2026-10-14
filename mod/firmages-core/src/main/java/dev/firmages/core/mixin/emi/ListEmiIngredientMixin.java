package dev.firmages.core.mixin.emi;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.firmages.core.compat.emi.FirmagesEmiPlugin;
import dev.firmages.core.compat.emi.ShownMembers;
import dev.firmages.core.immersion.EmiAgeView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * SPEC §21.11: an item list in a recipe shows only the members of the player's Ages ({@link ShownMembers}). Client
 * only; EMI is not on a server.
 */
@Mixin(value = ListEmiIngredient.class, remap = false)
public abstract class ListEmiIngredientMixin {
    @Shadow @Final private List<EmiStack> fullList;

    @Unique private volatile ShownMembers firmages$shown;

    @Inject(method = "getEmiStacks", at = @At("HEAD"), cancellable = true)
    private void firmages$shownMembers(CallbackInfoReturnable<List<EmiStack>> cir) {
        EmiAgeView view = FirmagesEmiPlugin.view();
        ShownMembers shown = firmages$shown;
        if (shown == null || shown.view() != view) {
            shown = ShownMembers.of(view, fullList);
            firmages$shown = shown;
        }
        cir.setReturnValue(shown.stacks());
    }
}
