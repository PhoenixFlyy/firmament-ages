package dev.firmages.core.immersion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §18.2: TFC's size and weight line, and the locked-item trim. */
class TooltipTrimTest {
    private static final Set<String> LOCKED = Set.of("Item Locked", "Recipe Locked", "Item and Recipe Locked");

    @Test
    void sizeWeightLine() {
        // TFC 4.2.11 ItemSizeManager.addTooltipInfo: literal "⚖", translatable weight, " ⇲", translatable size
        assertTrue(TooltipTrim.isSizeWeight(List.of("tfc.enum.weight.light", "tfc.enum.size.small")));
        assertTrue(TooltipTrim.isSizeWeight(List.of("tfc.enum.size.huge", "tfc.enum.weight.very_heavy")));
        assertFalse(TooltipTrim.isSizeWeight(List.of("tfc.enum.size.small")), "a size alone is another line");
        assertFalse(TooltipTrim.isSizeWeight(List.of("tfc.tooltip.melts_into")));
        assertFalse(TooltipTrim.isSizeWeight(List.of()));
    }

    @Test
    void lockedKeepsNameAndPsBlock() {
        // Felix's screenshot: TFC lines before PS's block, another mod's line after it
        List<String> lines = List.of("Unknown Item", "Melts into 10 mB of Zinc", "Light Small", "", "Item Locked",
            "Requires a later Age", "Current stage: Dawn (2/28)", "Some Mod Line");
        assertEquals(List.of(0, 3, 4, 5, 6), TooltipTrim.lockedKeep(lines, LOCKED, null));
    }

    @Test
    void descriptionKeptOnlyWhenPresent() {
        List<String> with = List.of("Unknown Item", "", "Recipe Locked", "Stage: Bronze", "Current stage: Dawn", "The age of alloys", "x");
        assertEquals(List.of(0, 1, 2, 3, 4, 5), TooltipTrim.lockedKeep(with, LOCKED, "The age of alloys"));
        List<String> without = List.of("Unknown Item", "", "Recipe Locked", "Stage: Bronze", "Current stage: Dawn", "Melts into x");
        assertEquals(List.of(0, 1, 2, 3, 4), TooltipTrim.lockedKeep(without, LOCKED, "The age of alloys"));
    }

    @Test
    void withoutPsBlockOnlyTheName() {
        assertEquals(List.of(0), TooltipTrim.lockedKeep(List.of("Unknown Item", "Melts into x"), LOCKED, null));
        assertEquals(List.of(), TooltipTrim.lockedKeep(List.of(), LOCKED, null));
        // a short tooltip that ends inside the block
        assertEquals(List.of(0, 1, 2), TooltipTrim.lockedKeep(List.of("Unknown Item", "", "Item Locked"), LOCKED, null));
    }
}
