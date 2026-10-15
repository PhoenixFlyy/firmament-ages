package dev.firmages.core.crown;

import dev.firmages.core.text.Numbers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Player text of the Lens Crown (SPEC §27.11), shared by the Jade lines (client, from the synced anvil) and the
 * actionbar at the anvil (server). Common code: no client classes.
 */
public final class CrownText {
    private CrownText() {}

    public static MutableComponent lensName(int index) {
        return Component.translatable("item.firmages.lens." + CrownRegistry.LENS_NAMES.get(index));
    }

    public static MutableComponent chordName(String chordId) {
        return Component.translatable(Chord.langKey(chordId));
    }

    /** "Chord: Dawn Triad". */
    public static MutableComponent chordLine(String chordId) {
        return Component.translatable("firmages.crown.chord", chordName(chordId));
    }

    /** "The crown lacks three blocks." */
    public static MutableComponent lacks(int missing) {
        return missing == 1 ? Component.translatable("firmages.crown.lacks_one")
            : Component.translatable("firmages.crown.lacks", Numbers.words(Math.max(0, missing)));
    }

    /** What the anvil does, or why it does not ("Forging Starlit Iron, 45 %", "The sky is hidden", ...). */
    public static MutableComponent state(CrownRules.Reason reason, int missing, int lens, boolean swingOut, ItemStack result, int percent) {
        return switch (reason) {
            case DISABLED -> Component.translatable("firmages.crown.reason.disabled");
            case UNFORMED -> lacks(missing);
            case NO_RECIPE -> Component.translatable("firmages.crown.reason.no_recipe");
            case WRONG_CHORD -> lens < 0 ? Component.translatable("firmages.crown.reason.no_recipe")
                : Component.translatable(swingOut ? "firmages.crown.reason.swing_out" : "firmages.crown.reason.swing_in", lensName(lens));
            case NO_FLUID -> Component.translatable("firmages.crown.reason.no_fluid");
            case OUTPUT_FULL -> Component.translatable("firmages.crown.reason.output_full");
            case SKY_HIDDEN -> Component.translatable("firmages.crown.reason.sky_hidden");
            case FORGING -> Component.translatable("firmages.crown.forging", result.getHoverName(), percent);
        };
    }

    public static MutableComponent state(StarAnvilBlockEntity be) {
        return state(be.reason(), be.missing(), be.reasonLens(), be.swingOut(), be.pendingResult(), be.percent());
    }

    /** "Bronze Lens, in" / "Bronze Lens, out" / "No lens". */
    public static MutableComponent mount(LensMountBlockEntity be) {
        int idx = be.lensIndex();
        if (idx < 0) return Component.translatable("firmages.crown.mount.empty");
        return Component.translatable(be.fullyIn() ? "firmages.crown.mount.in" : be.swing() > 0 ? "firmages.crown.mount.swinging"
            : "firmages.crown.mount.out", lensName(idx));
    }
}
