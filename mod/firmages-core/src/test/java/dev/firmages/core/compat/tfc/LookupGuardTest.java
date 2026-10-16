package dev.firmages.core.compat.tfc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** SPEC §42: the re-entry guard of TFC's heat and food lookups. */
class LookupGuardTest {
    /**
     * The H3 crash in miniature: building a "stack" looks its definition up, and matching the definition builds the
     * stacks of the tag (an uncached item array). Without the guard this recursion never ends.
     */
    private static final class World {
        final List<String> tag = List.of("tfc:metal/block/copper", "tfc:metal/block/bronze");
        int built;

        String build(String item) {   // the ItemStack constructor with TFC's hook
            built++;
            return LookupGuard.lookup(() -> definitionOf(item));
        }

        String definitionOf(String item) {   // HeatDefinition.matches -> Ingredient.getItems -> new ItemStack
            List<String> stacks = new ArrayList<>();
            for (String member : tag) {
                build(member);
                stacks.add(member);
            }
            return stacks.contains(item) ? "heat:block" : null;
        }
    }

    @Test
    void tagDefinitionResolvesWithoutRecursion() {
        World w = new World();
        assertEquals("heat:block", w.build("tfc:metal/block/copper"));
        assertEquals(3, w.built, "the outer stack and the two tag stacks, each built once");
        assertEquals(0, LookupGuard.depth());
    }

    @Test
    void nestedLookupGetsNothingOuterGetsItsValue() {
        AtomicReference<String> inner = new AtomicReference<>("unset");
        String outer = LookupGuard.lookup(() -> {
            inner.set(LookupGuard.lookup(() -> "inner"));
            return "outer";
        });
        assertEquals("outer", outer);
        assertNull(inner.get());
    }

    @Test
    void depthResetsAfterAnException() {
        assertThrows(IllegalStateException.class, () -> LookupGuard.lookup(() -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals(0, LookupGuard.depth());
        assertEquals("again", LookupGuard.lookup(() -> "again"));
    }

    @Test
    void threadsDoNotShareTheGuard() throws InterruptedException {
        AtomicReference<String> other = new AtomicReference<>();
        String outer = LookupGuard.lookup(() -> {
            Thread t = new Thread(() -> other.set(LookupGuard.lookup(() -> "other thread")));
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "main";
        });
        assertEquals("main", outer);
        assertEquals("other thread", other.get());
    }
}
