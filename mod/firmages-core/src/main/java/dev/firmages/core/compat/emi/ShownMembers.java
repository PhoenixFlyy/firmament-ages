package dev.firmages.core.compat.emi;

import dev.emi.emi.api.stack.EmiStack;
import dev.firmages.core.immersion.EmiAgeView;

import java.util.List;

/**
 * The members of an EMI item list ({@code ListEmiIngredient}) that the player's Age shows (SPEC §21.11). EMI 1.1.24
 * drops disabled members from a tag but keeps them in an item list [verified, javap: {@code getEmiStacks} returns the
 * full list], so a recipe slot cycled through locked stacks. The unlocked members are computed once per EMI reload
 * ({@link FirmagesEmiPlugin#view()} changes on each) and kept with the view they were computed for.
 *
 * <p>A list whose members are all locked is returned whole: EMI then sees a fully disabled input and drops the
 * recipe, as before. A list without locked members is returned as is.
 */
public record ShownMembers(EmiAgeView view, List<EmiStack> stacks) {
    public static ShownMembers of(EmiAgeView view, List<EmiStack> all) {
        List<EmiStack> kept = all.stream().filter(s -> !FirmagesEmiPlugin.locked(view, s)).toList();
        return new ShownMembers(view, kept.isEmpty() || kept.size() == all.size() ? all : kept);
    }
}
