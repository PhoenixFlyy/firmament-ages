package dev.firmages.core.compat.tfc;

import java.util.function.Supplier;

/**
 * Re-entry guard for TFC's heat and food definition lookups on item stack construction (SPEC §42).
 *
 * <p>TFC 4.2.11 sets the heat and food components of every new stack ({@code ItemStackHooks.onModifyItemStackComponents},
 * called from the {@code ItemStack} constructor) by matching the stack against the definitions. A definition whose
 * ingredient holds a tag ({@code tfc/item_heat/<metal>/block.json}: {@code [#c:storage_blocks/<metal>, exposed, weathered,
 * oxidized]}, a NeoForge compound) builds that tag's stacks to test it ({@code Ingredient.getItems}) whenever the
 * ingredient's item array is not cached; each of those stacks runs the same lookup again and meets the same
 * definition with the array still unbuilt, until the stack overflows. ModernFix's tag fast path hides this except while
 * a reload is running, so the pack crashed only after {@code /reload} (round H3, copy boot 7).
 *
 * <p>The guard lets one lookup per thread run: a stack built inside a running lookup (only the throw-away stacks of an
 * ingredient's item array) gets no default heat or food component, and the outer lookup finishes with the right
 * definition. Pure Java, tested in {@code LookupGuardTest}.
 */
public final class LookupGuard {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private LookupGuard() {}

    /** The lookup's result, or null when this thread is already inside a guarded lookup. */
    public static <T> T lookup(Supplier<T> lookup) {
        int[] depth = DEPTH.get();
        if (depth[0] > 0) return null;
        depth[0]++;
        try {
            return lookup.get();
        } finally {
            depth[0]--;
        }
    }

    /** Guarded lookups running on this thread (0 or 1; for tests). */
    static int depth() {
        return DEPTH.get()[0];
    }
}
